package com.example.joyvolumeremote;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Independent Android 5+ BLE peripheral transport. Never loads BluetoothHidDevice. */
public class HidRemoteService extends Service {
    public static final int MIN_SUPPORTED_API = 21;
    public static final boolean IS_BLE_COMPAT = true;
    public interface StateListener { void onHidStateChanged(); }
    public class LocalBinder extends Binder {
        HidRemoteService getService() { return HidRemoteService.this; }
    }

    private static final String TAG = "FliFliBLE";
    private static final String CHANNEL = "flifli_ble_connection";
    private static final int NOTIFICATION_ID = 21;
    private static final String STOP = "com.example.joyvolumeremote.blecompat.STOP";
    private static final String DISCONNECT = "com.example.joyvolumeremote.blecompat.DISCONNECT";
    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<StateListener> listeners = new CopyOnWriteArraySet<>();
    private final BleReportQueue reports = new BleReportQueue();
    private final Set<BluetoothGattCharacteristic> subscribed = new HashSet<>();
    private final ArrayDeque<BluetoothGattService> pendingServices = new ArrayDeque<>();
    private BluetoothManager manager;
    private BluetoothAdapter adapter;
    private BluetoothGattServer server;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private BleGattProfile profile;
    private BluetoothDevice peer;
    private BluetoothDevice selectedHost;
    private BluetoothDevice pendingHost;
    private boolean connectionRequested;
    private String status = "BLE HID 实验版 · 初始化中";
    private boolean registered, advertising, startingAdvertising, sending, suspended, stopping, receiverRegistered;
    private int generation, sentActions;
    private final Runnable connectionTimeout = () -> {
        if (peer != null || pendingHost == null) return;
        cancelPendingConnection();
        showNoGattStatus("连接尝试超时");
    };
    private final Runnable notificationTimeout = () -> failConnection("发送失败：BLE 通知超时，已断开以避免按键卡住。请重新连接。");
    private final Runnable registrationTimeout = () -> {
        cleanupBluetooth();
        setStatus("注册失败：系统未完成 GATT 服务注册。请在连接页刷新重试。");
    };

    private final BroadcastReceiver bluetoothEvents = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    cleanupBluetooth();
                    setStatus("蓝牙已关闭。打开蓝牙后会重新开始 BLE 广播。");
                } else if (state == BluetoothAdapter.STATE_ON && !stopping) connectHidProfile();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                Log.i(TAG, "Bond state=" + bond + " device=" + device);
                if (peer == null) {
                    if (device != null && device.equals(selectedHost) && registered && pendingHost == null) {
                        showNoGattStatus("系统配对状态已更新");
                    }
                    return;
                }
                if (!peer.equals(device)) return;
                if (bond == BluetoothDevice.BOND_BONDED) updateReadyStatus();
                else if (bond == BluetoothDevice.BOND_NONE) failConnection("配对失败或已取消。请从阅读器重新配对手机。");
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, "System ACL connected device=" + device + " (not HID readiness)");
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        startAsForeground();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(bluetoothEvents, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(bluetoothEvents, filter);
        receiverRegistered = true;
        connectHidProfile();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopping = true;
            cleanupBluetooth();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && DISCONNECT.equals(intent.getAction())) disconnectHost();
        else connectHidProfile();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onDestroy() {
        stopping = true;
        if (receiverRegistered) unregisterReceiver(bluetoothEvents);
        cleanupBluetooth();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public void addStateListener(StateListener listener) { listeners.add(listener); listener.onHidStateChanged(); }
    public void removeStateListener(StateListener listener) { listeners.remove(listener); }
    public String getStatus() { return status; }
    public boolean isHidAppRegistered() { return registered; }
    public BluetoothDevice getConnectedHost() {
        // A GATT link alone is insufficient: input must be bonded, subscribed and not suspended.
        return keyboardReady() ? peer : null;
    }
    public Set<BluetoothDevice> getBondedDevices() {
        if (adapter == null || !hasPermissions()) return Collections.emptySet();
        try { return new HashSet<>(adapter.getBondedDevices()); }
        catch (SecurityException e) { setStatus("读取已配对设备失败：缺少蓝牙权限。"); return Collections.emptySet(); }
    }
    public static Intent makeStopIntent(Context context) { return new Intent(context, HidRemoteService.class).setAction(STOP); }

    public void connectHidProfile() {
        if (stopping) return;
        if (adapter == null) { setStatus("此手机没有蓝牙适配器，无法使用 BLE HID。"); return; }
        if (!hasPermissions()) { setStatus("请授予附近设备权限（连接和广播），然后在连接页重试。"); return; }
        try {
            if (!adapter.isEnabled()) { setStatus("蓝牙已关闭。请先打开蓝牙。"); return; }
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                    || !adapter.isMultipleAdvertisementSupported()) {
                setStatus("此手机不支持 BLE 外设广播。系统版本满足要求也无法使用此实验版。"); return;
            }
            if (server != null) {
                if (registered && peer == null && !advertising && !startingAdvertising) startAdvertising(true);
                return;
            }
            advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) { setStatus("BLE 广播不可用。请打开蓝牙后重试。"); return; }
            profile = new BleGattProfile();
            server = manager.openGattServer(this, callbacks(++generation));
            if (server == null) { setStatus("启动失败：系统未提供 GATT 外设服务。"); return; }
            int battery = batteryPercent();
            pendingServices.addAll(profile.services(battery));
            setStatus("BLE HID 实验版 · 正在注册键盘与媒体键服务…");
            handler.postDelayed(registrationTimeout, 10000);
            addNextService();
        } catch (SecurityException | IllegalStateException e) {
            cleanupBluetooth();
            setStatus("启动失败：" + e.getClass().getSimpleName() + " · " + e.getMessage());
        }
    }

    private void addNextService() {
        if (pendingServices.isEmpty()) {
            handler.removeCallbacks(registrationTimeout);
            registered = true;
            startAdvertising(true);
            if (connectionRequested) requestSelectedConnection();
        } else {
            try {
                if (!server.addService(pendingServices.peek())) {
                    cleanupBluetooth();
                    setStatus("注册失败：系统拒绝添加 BLE HID 服务。请刷新重试。");
                }
            } catch (SecurityException | IllegalStateException e) {
                cleanupBluetooth();
                setStatus("注册失败：" + e.getMessage());
            }
        }
    }

    private void startAdvertising(boolean includeName) {
        if (advertiser == null || advertising || startingAdvertising || stopping || peer != null) return;
        final int epoch = generation;
        startingAdvertising = true;
        advertiseCallback = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings settings) {
                handler.post(() -> {
                    if (epoch != generation || stopping || peer != null) return;
                    startingAdvertising = false;
                    advertising = true;
                    if (selectedHost != null) {
                        if (pendingHost == null) showNoGattStatus("正在广播");
                        return;
                    }
                    setStatus("BLE HID 实验版 · 正在广播。请从阅读器搜索并配对这台手机"
                            + (includeName ? "（" + phoneName() + "）" : "（手机名称过长，按蓝牙地址识别）") + "。已配对后也需由阅读器发起连接。");
                });
            }
            @Override public void onStartFailure(int code) {
                handler.post(() -> {
                    if (epoch != generation || stopping) return;
                    startingAdvertising = false;
                    if (code == ADVERTISE_FAILED_DATA_TOO_LARGE && includeName) { startAdvertising(false); return; }
                    setStatus("广播失败：" + advertiseError(code) + "。请在连接页刷新重试。");
                });
            }
        };
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setTimeout(0).build();
        AdvertiseData data = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(BleGattProfile.HID_UUID)).build();
        AdvertiseData response = new AdvertiseData.Builder().setIncludeDeviceName(includeName).build();
        try { advertiser.startAdvertising(settings, data, response, advertiseCallback); }
        catch (SecurityException | IllegalStateException e) {
            startingAdvertising = false;
            setStatus("广播失败：" + e.getMessage());
        }
    }

    public void connectToHost(BluetoothDevice device) {
        if (device == null) return;
        if (!hasPermissions()) { setStatus("请先授予附近设备权限。"); return; }
        if (peer != null && !device.equals(peer)) { setStatus("已有 BLE 连接。请先断开，再从另一台阅读器连接。"); return; }
        try {
            if (device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                setStatus("此配对记录仅支持经典蓝牙。请从阅读器搜索手机的 BLE 广播并重新配对。"); return;
            }
            if (peer != null) { updateReadyStatus(); return; }
            if (!device.equals(selectedHost)) cancelPendingConnection();
            selectedHost = device;
            connectionRequested = true;
            connectHidProfile();
            if (registered) requestSelectedConnection();
        } catch (SecurityException e) { setStatus("连接失败：缺少蓝牙权限。"); }
    }

    private void requestSelectedConnection() {
        if (server == null || !registered || selectedHost == null || peer != null || pendingHost != null) return;
        connectionRequested = false;
        try {
            pendingHost = selectedHost;
            // Register this server's connection attempt even when the system already holds a link.
            // A successful request still requires the GATT callback and the host's HID subscription.
            boolean accepted = server.connect(selectedHost, false);
            Log.i(TAG, "GATT connect requested device=" + selectedHost + " accepted=" + accepted);
            if (!accepted) {
                pendingHost = null;
                showNoGattStatus("系统未接受 BLE 连接请求");
                return;
            }
            setStatus("正在尝试 BLE 连接：" + deviceName(selectedHost)
                    + " · 请保持阅读器蓝牙页面打开，并从阅读器连接手机。系统显示已连接仍需完成键盘订阅。");
            handler.postDelayed(connectionTimeout, 15000);
        } catch (SecurityException | IllegalStateException e) {
            pendingHost = null;
            setStatus("BLE 连接请求失败：" + e.getMessage() + "。请从阅读器端重新连接。");
        }
    }

    private void cancelPendingConnection() {
        handler.removeCallbacks(connectionTimeout);
        if (server != null && pendingHost != null) {
            try { server.cancelConnection(pendingHost); }
            catch (SecurityException | IllegalStateException e) { Log.w(TAG, "Cannot cancel pending GATT connection", e); }
        }
        pendingHost = null;
        connectionRequested = false;
    }

    private void showNoGattStatus(String reason) {
        if (selectedHost == null || peer != null) return;
        String pairing;
        try { pairing = selectedHost.getBondState() == BluetoothDevice.BOND_BONDED ? "系统已配对" : "系统尚未完成配对"; }
        catch (SecurityException e) { pairing = "无法读取系统配对状态"; }
        setStatus(deviceName(selectedHost) + " · " + pairing + "，尚未建立本 App 的 BLE 键盘连接（" + reason
                + "）。请停止主线服务，从阅读器重新搜索连接；旧的经典蓝牙连接不能代替 BLE 键盘连接。");
    }

    public void disconnectHost() {
        // Closing the link also clears host input state; no queued report can reach a later connection.
        cleanupBluetooth();
        selectedHost = null;
        connectHidProfile();
    }

    public boolean sendTouchState(byte[] report) {
        setStatus(getString(R.string.signal_touch_ble));
        return false;
    }

    public void sendProgram(SignalProgram program) {
        java.util.List<SignalReports.Frame> frames = SignalReports.compile(program);
        if (frames.isEmpty()) { setStatus("当前手势未绑定输出。"); return; }
        for (SignalReports.Frame frame : frames) {
            if (frame.reportId == SignalReports.TOUCH) { setStatus(getString(R.string.signal_touch_ble)); return; }
            if (!reportReady(frame.reportId)) return;
        }
        if (!reports.addProgram(frames, ActionLabels.program(this, program))) {
            setStatus(getString(R.string.signal_queue_full)); return;
        }
        drainReports();
    }

    public void sendKeyboardState(int modifiers, int[] usages) {
        if (!reportReady(BleHidReports.KEYBOARD_ID)) return;
        if (!reports.addState(BleHidReports.KEYBOARD_ID, BleHidReports.keyboard(modifiers, usages))) {
            failConnection("发送失败：输入队列已满，已断开以避免按键卡住。请重新连接。"); return;
        }
        drainReports();
    }

    private boolean keyboardReady() {
        try { return peer != null && peer.getBondState() == BluetoothDevice.BOND_BONDED && !suspended
                && profile != null && subscribed.contains(keyboardCharacteristic()); }
        catch (SecurityException e) { return false; }
    }
    private BluetoothGattCharacteristic keyboardCharacteristic() {
        return profile.protocol.getValue()[0] == 0 ? profile.bootKeyboard : profile.keyboard;
    }
    private BluetoothGattCharacteristic reportCharacteristic(int id) {
        return id == BleHidReports.KEYBOARD_ID ? keyboardCharacteristic() : profile.consumer;
    }
    private boolean reportReady(int id) {
        if (!keyboardReady()) { setStatus("尚未就绪：请完成阅读器端配对、连接与键盘输入订阅。"); return false; }
        if (id == BleHidReports.CONSUMER_ID && profile.protocol.getValue()[0] == 0) {
            setStatus("阅读器使用 Boot 键盘模式，不支持媒体键。请改用方向键或 Page Up / Page Down。"); return false;
        }
        if (!subscribed.contains(reportCharacteristic(id))) {
            setStatus("阅读器未订阅此类输入。请尝试键盘翻页键或重新连接。"); return false;
        }
        return true;
    }

    private void drainReports() {
        if (sending || reports.peek() == null || server == null || peer == null) return;
        BleReportQueue.Report report = reports.peek();
        if (!keyboardReady() || !subscribed.contains(reportCharacteristic(report.id))) {
            failConnection("发送失败：阅读器输入订阅已失效，请重新连接。"); return;
        }
        BluetoothGattCharacteristic characteristic = reportCharacteristic(report.id);
        try {
            characteristic.setValue(report.value.clone());
            sending = true;
            boolean accepted;
            if (Build.VERSION.SDK_INT >= 33) accepted = server.notifyCharacteristicChanged(peer, characteristic, false, report.value.clone()) == BluetoothStatusCodes.SUCCESS;
            else accepted = server.notifyCharacteristicChanged(peer, characteristic, false);
            if (!accepted) { failConnection("发送失败：系统拒绝 BLE 通知，请重新连接。"); return; }
            handler.postDelayed(notificationTimeout, 3000);
        } catch (SecurityException | IllegalStateException e) { failConnection("发送失败：" + e.getMessage()); }
    }

    private BluetoothGattServerCallback callbacks(final int epoch) {
        return new BluetoothGattServerCallback() {
            private void dispatch(Runnable action) { handler.post(() -> { if (epoch == generation && !stopping) action.run(); }); }
            @Override public void onServiceAdded(int result, BluetoothGattService service) {
                dispatch(() -> {
                    if (result != BluetoothGatt.GATT_SUCCESS || pendingServices.isEmpty()
                            || !pendingServices.peek().getUuid().equals(service.getUuid())) {
                        cleanupBluetooth(); setStatus("注册失败：GATT 状态 " + result); return;
                    }
                    pendingServices.poll();
                    try { addNextService(); }
                    catch (SecurityException | IllegalStateException e) { cleanupBluetooth(); setStatus("注册失败：" + e.getMessage()); }
                });
            }
            @Override public void onConnectionStateChange(BluetoothDevice device, int result, int state) {
                dispatch(() -> {
                    Log.i(TAG, "GATT connection device=" + device + " status=" + result + " state=" + state
                            + " selected=" + selectedHost + " peer=" + peer);
                    if (state == BluetoothProfile.STATE_CONNECTED && result == BluetoothGatt.GATT_SUCCESS) {
                        if ((peer != null && !peer.equals(device)) || (selectedHost != null && !selectedHost.equals(device))) {
                            Log.w(TAG, "Rejecting GATT connection from a different device: " + device);
                            try { server.cancelConnection(device); } catch (SecurityException e) { setStatus("拒绝其他设备失败：缺少蓝牙权限。"); }
                            return;
                        }
                        handler.removeCallbacks(connectionTimeout);
                        pendingHost = null;
                        connectionRequested = false;
                        // Duplicate connected callbacks must not erase the host's subscriptions.
                        if (device.equals(peer)) { updateReadyStatus(); return; }
                        peer = device;
                        resetInputState();
                        stopAdvertising();
                        updateReadyStatus();
                        try {
                            if (device.getBondState() == BluetoothDevice.BOND_NONE && !device.createBond()) {
                                failConnection("配对失败：系统未接受配对请求，请从阅读器重新配对。");
                            }
                        } catch (SecurityException e) { failConnection("配对失败：缺少蓝牙权限。"); }
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED && device.equals(peer)) {
                        cleanupBluetooth();
                        connectHidProfile();
                        setStatus("BLE 连接已断开（GATT " + result + "）。请从阅读器重新连接；可在连接页刷新广播。");
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED && device.equals(pendingHost)) {
                        handler.removeCallbacks(connectionTimeout);
                        pendingHost = null;
                        showNoGattStatus("GATT 状态 " + result);
                    }
                });
            }
            @Override public void onCharacteristicReadRequest(BluetoothDevice device, int request, int offset, BluetoothGattCharacteristic c) {
                dispatch(() -> {
                    Log.i(TAG, "GATT read device=" + device + " uuid=" + c.getUuid() + " offset=" + offset);
                    if (c.getUuid().equals(BleGattProfile.uuid(0x2A19))) {
                        int battery = batteryPercent();
                        if (battery < 0) { respond(device, request, BluetoothGatt.GATT_FAILURE, offset, null); return; }
                        c.setValue(new byte[]{(byte) battery});
                    }
                    if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) respond(device, request, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null);
                    else read(device, request, offset, c.getValue());
                });
            }
            @Override public void onDescriptorReadRequest(BluetoothDevice device, int request, int offset, BluetoothGattDescriptor descriptor) {
                dispatch(() -> {
                    byte[] value = descriptor.getValue();
                    if (descriptor.getUuid().equals(BleGattProfile.CCC_UUID)) value = device.equals(peer) && subscribed.contains(descriptor.getCharacteristic())
                            ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    read(device, request, offset, value);
                });
            }
            @Override public void onDescriptorWriteRequest(BluetoothDevice device, int request, BluetoothGattDescriptor descriptor,
                    boolean prepared, boolean responseNeeded, int offset, byte[] value) {
                dispatch(() -> {
                    int result = validateWrite(device, prepared, offset);
                    if (result == BluetoothGatt.GATT_SUCCESS) {
                        if (!descriptor.getUuid().equals(BleGattProfile.CCC_UUID)) result = BluetoothGatt.GATT_WRITE_NOT_PERMITTED;
                        else if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) subscribed.add(descriptor.getCharacteristic());
                        else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) subscribed.remove(descriptor.getCharacteristic());
                        else result = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                    }
                    if (responseNeeded) respond(device, request, result, offset, null);
                    Log.i(TAG, "GATT descriptor write device=" + device + " uuid=" + descriptor.getUuid()
                            + " characteristic=" + descriptor.getCharacteristic().getUuid() + " value=" + Arrays.toString(value) + " result=" + result);
                    if (result == BluetoothGatt.GATT_SUCCESS) updateReadyStatus();
                });
            }
            @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int request, BluetoothGattCharacteristic c,
                    boolean prepared, boolean responseNeeded, int offset, byte[] value) {
                dispatch(() -> {
                    int result = validateWrite(device, prepared, offset);
                    boolean resetConnection = false;
                    if (result == BluetoothGatt.GATT_SUCCESS) {
                        if (c != profile.protocol && c != profile.control && c != profile.keyboardOutput && c != profile.bootOutput) result = BluetoothGatt.GATT_WRITE_NOT_PERMITTED;
                        else if (value == null || value.length != 1) result = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                        else if ((c == profile.protocol || c == profile.control) && value[0] != 0 && value[0] != 1) result = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                        else {
                            boolean changedMode = (c == profile.control || c == profile.protocol) && c.getValue()[0] != value[0];
                            resetConnection = changedMode && sending;
                            c.setValue(value.clone());
                            if (c == profile.control) suspended = value[0] == 0;
                            if (changedMode && !resetConnection) {
                                // Never deliver reports queued under another protocol or across suspend/resume.
                                reports.clear(); handler.removeCallbacks(notificationTimeout); sending = false;
                                profile.keyboard.setValue(new byte[8]); profile.bootKeyboard.setValue(new byte[8]); profile.consumer.setValue(new byte[2]);
                            }
                        }
                    }
                    if (responseNeeded) respond(device, request, result, offset, null);
                    if (resetConnection) {
                        failConnection("阅读器在发送中切换了输入模式，已断开以避免按键卡住。请重新连接。");
                        return;
                    }
                    if (result == BluetoothGatt.GATT_SUCCESS) updateReadyStatus();
                });
            }
            @Override public void onExecuteWrite(BluetoothDevice device, int request, boolean execute) {
                dispatch(() -> respond(device, request, execute ? BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED : BluetoothGatt.GATT_SUCCESS, 0, null));
            }
            @Override public void onNotificationSent(BluetoothDevice device, int result) {
                dispatch(() -> {
                    if (!device.equals(peer) || !sending) return;
                    handler.removeCallbacks(notificationTimeout);
                    if (result != BluetoothGatt.GATT_SUCCESS) { failConnection("发送失败：BLE 通知状态 " + result + "，请重新连接。"); return; }
                    BleReportQueue.Report report = reports.acknowledge();
                    if (report == null) { sending = false; return; }
                    if (report.completedAction != null) {
                        sentActions++;
                        setStatus("BLE 已发送：" + report.completedAction + " · " + deviceName(peer) + " · 累计 " + sentActions + " 次（翻页效果需在阅读器确认）");
                    }
                    handler.postDelayed(() -> {
                        if (epoch != generation) return;
                        sending = false;
                        drainReports();
                    }, report.holdMs);
                });
            }
        };
    }

    private int validateWrite(BluetoothDevice device, boolean prepared, int offset) {
        if (prepared) return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        if (offset != 0) return BluetoothGatt.GATT_INVALID_OFFSET;
        try {
            if (!device.equals(peer) || device.getBondState() != BluetoothDevice.BOND_BONDED) return BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION;
        } catch (SecurityException e) { return BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION; }
        return BluetoothGatt.GATT_SUCCESS;
    }
    private void read(BluetoothDevice device, int request, int offset, byte[] value) {
        byte[] part = BleHidReports.atOffset(value == null ? new byte[0] : value, offset);
        respond(device, request, part == null ? BluetoothGatt.GATT_INVALID_OFFSET : BluetoothGatt.GATT_SUCCESS, offset, part);
    }
    private void respond(BluetoothDevice device, int request, int result, int offset, byte[] value) {
        if (server == null) return;
        try {
            if (!server.sendResponse(device, request, result, offset, value)) setStatus("GATT 响应失败，请从阅读器重新连接。");
        } catch (SecurityException e) { setStatus("GATT 响应失败：缺少蓝牙权限。"); }
    }

    private void updateReadyStatus() {
        if (peer == null) return;
        if (suspended) setStatus("阅读器已暂停 HID 输入，等待恢复。");
        else if (keyboardReady()) setStatus("BLE 键盘已就绪：" + deviceName(peer) + " · 可从首页测试方向键或 Page Up / Page Down。");
        else {
            boolean bonded;
            try { bonded = peer.getBondState() == BluetoothDevice.BOND_BONDED; }
            catch (SecurityException e) { bonded = false; }
            setStatus("已建立 BLE 连接：" + deviceName(peer) + (bonded
                    ? " · 系统已配对，但阅读器尚未启用 BLE 键盘输入。请在阅读器端启用输入设备或重新连接。"
                    : " · 等待系统完成配对，请确认两端的配对提示。"));
        }
    }
    private void resetInputState() {
        subscribed.clear(); reports.clear(); sending = false; suspended = false;
        handler.removeCallbacks(notificationTimeout);
        if (profile != null) {
            profile.protocol.setValue(new byte[]{1}); profile.control.setValue(new byte[]{1});
            profile.keyboard.setValue(new byte[8]); profile.bootKeyboard.setValue(new byte[8]); profile.consumer.setValue(new byte[2]);
        }
    }
    private void failConnection(String reason) { cleanupBluetooth(); setStatus(reason); }
    private void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            try { advertiser.stopAdvertising(advertiseCallback); }
            catch (SecurityException | IllegalStateException e) { Log.w(TAG, "Cannot stop advertising", e); }
        }
        advertiseCallback = null; advertising = false; startingAdvertising = false;
    }
    private void cleanupBluetooth() {
        generation++;
        handler.removeCallbacks(registrationTimeout);
        cancelPendingConnection();
        stopAdvertising();
        if (server != null) {
            try { if (peer != null) server.cancelConnection(peer); server.close(); }
            catch (SecurityException e) { Log.w(TAG, "Cannot close GATT server", e); }
        }
        server = null; peer = null; registered = false; pendingServices.clear();
        resetInputState(); profile = null; advertiser = null;
    }
    private boolean hasPermissions() {
        return Build.VERSION.SDK_INT < 31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED);
    }
    private int batteryPercent() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return -1;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level < 0 || scale <= 0 ? -1 : Math.min(100, level * 100 / scale);
    }
    private String phoneName() {
        try { String name = adapter.getName(); return name == null ? "本机" : name; }
        catch (SecurityException e) { return "本机"; }
    }
    private String deviceName(BluetoothDevice device) {
        try { String name = device.getName(); return name == null || name.trim().isEmpty() ? device.getAddress() : name; }
        catch (SecurityException e) { return "阅读器"; }
    }
    private static String advertiseError(int code) {
        switch (code) {
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE: return "广播数据过长（1）";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS: return "系统广播资源已满，请关闭其他 BLE 应用（2）";
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED: return "系统报告广播已启动（3）";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR: return "蓝牙系统内部错误（4）";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED: return "硬件或系统不支持 BLE 广播（5）";
            default: return "广播错误 " + code;
        }
    }
    private void setStatus(String message) {
        status = message;
        Log.i(TAG, message);
        NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notifications != null && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) notifications.notify(NOTIFICATION_ID, notification());
        for (StateListener listener : listeners) listener.onHidStateChanged();
    }
    private void startAsForeground() {
        NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && notifications != null) notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "BLE HID 实验版", NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(NOTIFICATION_ID, notification());
    }
    private Notification notification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1, makeStopIntent(this), flags);
        PendingIntent disconnect = PendingIntent.getService(this, 2, new Intent(this, HidRemoteService.class).setAction(DISCONNECT), flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle(getString(R.string.compat_app_name))
                .setContentText(status).setStyle(new Notification.BigTextStyle().bigText(status)).setContentIntent(open).setOngoing(true)
                .addAction(R.drawable.ic_launcher, "断开 / 重新广播", disconnect).addAction(R.drawable.ic_launcher, "停止", stop).build();
    }
}
