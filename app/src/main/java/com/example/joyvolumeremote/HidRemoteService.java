package com.example.joyvolumeremote;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HidRemoteService extends Service {
    public interface StateListener {
        void onHidStateChanged();
    }

    public class LocalBinder extends Binder {
        HidRemoteService getService() {
            return HidRemoteService.this;
        }
    }

    private static final String CHANNEL_ID = "joyvolume_remote_connection";
    private static final int NOTIFICATION_ID = 12;
    private static final String ACTION_STOP = "com.example.joyvolumeremote.STOP";
    private static final String ACTION_DISCONNECT = "com.example.joyvolumeremote.DISCONNECT";

    private static final int KEYBOARD_REPORT_ID = 1;
    private static final int CONSUMER_REPORT_ID = 2;

    private static final byte[] COMBO_HID_REPORT_DESCRIPTOR = new byte[]{
            0x05, 0x01,
            0x09, 0x06,
            (byte) 0xA1, 0x01,
            (byte) 0x85, 0x01,
            0x05, 0x07,
            0x19, (byte) 0xE0,
            0x29, (byte) 0xE7,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            (byte) 0x95, 0x08,
            (byte) 0x81, 0x02,
            0x75, 0x08,
            (byte) 0x95, 0x01,
            (byte) 0x81, 0x01,
            0x19, 0x00,
            0x2A, (byte) 0x81, 0x00,
            0x15, 0x00,
            0x26, (byte) 0x81, 0x00,
            0x75, 0x08,
            (byte) 0x95, 0x06,
            (byte) 0x81, 0x00,
            (byte) 0xC0,

            0x05, 0x0C,
            0x09, 0x01,
            (byte) 0xA1, 0x01,
            (byte) 0x85, 0x02,
            0x15, 0x00,
            0x26, (byte) 0xFF, 0x03,
            0x19, 0x00,
            0x2A, (byte) 0xFF, 0x03,
            0x75, 0x10,
            (byte) 0x95, 0x01,
            (byte) 0x81, 0x00,
            (byte) 0xC0,

    };

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArraySet<StateListener> listeners = new CopyOnWriteArraySet<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedHost;
    private Vibrator vibrator;
    private boolean hidAppRegistered = false;
    private boolean shuttingDown = false;
    private String status = "初始化中...";
    private byte[] lastRumbleData = new byte[0];
    private String lastOutputSource = "未收到";
    private int lastOutputReportId = -1;

    private final BluetoothProfile.ServiceListener hidServiceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = (BluetoothHidDevice) proxy;
                setStatus("HID 服务已就绪，正在注册自定义输入设备...");
                registerHidApp();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null;
                connectedHost = null;
                hidAppRegistered = false;
                setStatus("HID 服务已断开，正在等待系统重新连接...");
                if (!shuttingDown) {
                    mainHandler.postDelayed(HidRemoteService.this::connectHidProfile, 1200);
                }
            }
        }
    };

    private final BluetoothHidDevice.Callback hidCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            mainHandler.post(() -> {
                hidAppRegistered = registered;
                if (registered) {
                    setStatus("自定义输入设备已注册，可发送键盘和媒体键。");

                } else {
                    connectedHost = null;
                    setStatus("HID 设备已注销，正在尝试重新注册...");
                    if (!shuttingDown) {
                        mainHandler.postDelayed(HidRemoteService.this::registerHidApp, 1000);
                    }
                }
            });
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            mainHandler.post(() -> {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connectedHost = device;
                    setStatus("已连接：" + safeDeviceName(device) + " · 可发送自定义 HID 输入");
                } else if (state == BluetoothProfile.STATE_CONNECTING) {
                    setStatus("正在连接：" + safeDeviceName(device));
                } else if (state == BluetoothProfile.STATE_DISCONNECTING) {
                    setStatus("正在断开：" + safeDeviceName(device));
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    if (device != null && device.equals(connectedHost)) {
                        connectedHost = null;
                    }
                    setStatus("连接已断开。请重新选择已配对设备连接。");
                }
            });
        }

        @Override
        public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
            mainHandler.post(() -> handleOutputReport(device, reportId, data, "中断"));
        }

        @Override
        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            mainHandler.post(() -> handleOutputReport(device, id, data, "SetReport"));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        connectHidProfile();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shuttingDown = true;
            cleanupBluetooth();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnectHost();
        } else {
            connectHidProfile();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;
        mainHandler.removeCallbacksAndMessages(null);
        cleanupBluetooth();
        callbackExecutor.shutdownNow();
        super.onDestroy();
    }

    public void addStateListener(StateListener listener) {
        listeners.add(listener);
        listener.onHidStateChanged();
    }

    public void removeStateListener(StateListener listener) {
        listeners.remove(listener);
    }

    public String getStatus() {
        return status;
    }

    public boolean isHidAppRegistered() {
        return hidAppRegistered;
    }

    public BluetoothDevice getConnectedHost() {
        return connectedHost;
    }

    public Set<BluetoothDevice> getBondedDevices() {
        if (bluetoothAdapter == null || !hasBluetoothConnectPermission()) {
            return Collections.emptySet();
        }
        try {
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            return devices == null ? Collections.emptySet() : new HashSet<>(devices);
        } catch (SecurityException e) {
            setStatus("读取已配对设备失败：缺少蓝牙权限。");
            return Collections.emptySet();
        }
    }

    public void connectHidProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setStatus("此方案需要 Android 9.0 或以上。");
            return;
        }
        if (bluetoothAdapter == null) {
            setStatus("这台设备没有可用的蓝牙适配器。");
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            setStatus("请先授予“附近设备/蓝牙”权限。");
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                setStatus("请先打开蓝牙。");
                return;
            }
            if (hidDevice != null) {
                if (!hidAppRegistered) {
                    registerHidApp();
                }
                return;
            }
            setStatus("正在连接 HID 服务...");
            bluetoothAdapter.getProfileProxy(getApplicationContext(), hidServiceListener, BluetoothProfile.HID_DEVICE);
        } catch (SecurityException e) {
            setStatus("没有蓝牙连接权限：" + e.getMessage());
        }
    }

    public void connectToHost(BluetoothDevice device) {
        if (hidDevice == null) {
            setStatus("HID 服务还没准备好。请保持蓝牙打开并等待几秒。");
            connectHidProfile();
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            return;
        }
        try {
            setStatus("正在连接：" + safeDeviceName(device));
            boolean sent = hidDevice.connect(device);
            if (!sent) {
                setStatus("连接命令发送失败。请确认对方是支持 HID 的电脑、电视、平板等主机设备。");
            }
        } catch (SecurityException e) {
            setStatus("连接失败：缺少蓝牙权限。");
        }
    }

    public void disconnectHost() {
        if (hidDevice == null || connectedHost == null || !hasBluetoothConnectPermission()) {
            connectedHost = null;
            setStatus("已返回连接页。");
            return;
        }
        try {
            BluetoothDevice device = connectedHost;
            sendAllReleasedState();
            connectedHost = null;
            setStatus("正在断开：" + safeDeviceName(device));
            boolean sent = hidDevice.disconnect(device);
            if (!sent) {
                setStatus("断开命令发送失败，已返回连接页。必要时可在系统蓝牙设置中断开。");
            }
        } catch (SecurityException e) {
            setStatus("断开失败：缺少蓝牙权限。");
        }
    }

    public void sendAction(HidAction.ActionDefinition action) {
        if (action == null || action.outputType == HidAction.OutputType.NONE) {
            setStatus("当前手势未绑定任何输出。");
            return;
        }
        if (hidDevice == null || connectedHost == null || !hasBluetoothConnectPermission()) {
            setStatus("尚未连接目标设备。先配对并连接电脑、电视、平板等 HID 主机。");
            return;
        }

        try {
            switch (action.outputType) {
                case KEYBOARD:
                    sendKeyboardAction(action);
                    break;
                case CONSUMER:
                    sendConsumerAction(action);
                    break;
                default:
                    setStatus("当前手势未绑定任何输出。");
                    break;
            }
        } catch (SecurityException e) {
            setStatus("发送失败：缺少蓝牙权限。");
        }
    }

    public void sendKeyboardState(int modifierMask, int[] keyboardUsages) {
        if (hidDevice == null || connectedHost == null || !hasBluetoothConnectPermission()) {
            setStatus("尚未连接目标设备。先配对并连接电脑、电视、平板等 HID 主机。");
            return;
        }
        try {
            hidDevice.sendReport(connectedHost, KEYBOARD_REPORT_ID, buildKeyboardReport(modifierMask, keyboardUsages));
        } catch (SecurityException e) {
            setStatus("发送失败：缺少蓝牙权限。");
        }
    }

    public static Intent makeStopIntent(Context context) {
        Intent intent = new Intent(context, HidRemoteService.class);
        intent.setAction(ACTION_STOP);
        return intent;
    }

    private void sendKeyboardAction(HidAction.ActionDefinition action) {
        byte[] press = buildKeyboardReport(action.modifierMask, action.keyboardUsages);
        byte[] release = buildKeyboardReport(0, null);
        hidDevice.sendReport(connectedHost, KEYBOARD_REPORT_ID, press);
        setStatus("已发送：" + action.label);
        mainHandler.postDelayed(() -> sendRelease(KEYBOARD_REPORT_ID, release), 35);
    }

    private byte[] buildKeyboardReport(int modifierMask, int[] keyboardUsages) {
        byte[] report = new byte[8];
        report[0] = (byte) (modifierMask & 0xFF);
        if (keyboardUsages != null) {
            for (int i = 0; i < keyboardUsages.length && i < 6; i++) {
                report[2 + i] = (byte) keyboardUsages[i];
            }
        }
        return report;
    }

    private void sendConsumerAction(HidAction.ActionDefinition action) {
        byte[] press = new byte[]{
                (byte) (action.consumerUsage & 0xFF),
                (byte) ((action.consumerUsage >> 8) & 0xFF)
        };
        byte[] release = new byte[]{0x00, 0x00};
        hidDevice.sendReport(connectedHost, CONSUMER_REPORT_ID, press);
        setStatus("已发送：" + action.label);
        mainHandler.postDelayed(() -> sendRelease(CONSUMER_REPORT_ID, release), 35);
    }













    private void sendRelease(int reportId, byte[] release) {
        try {
            if (hidDevice != null && connectedHost != null && hasBluetoothConnectPermission()) {
                hidDevice.sendReport(connectedHost, reportId, release);
            }
        } catch (SecurityException ignored) {
        }
    }

    private void sendAllReleasedState() {
        if (hidDevice == null || connectedHost == null || !hasBluetoothConnectPermission()) {
            return;
        }
        try {
            hidDevice.sendReport(connectedHost, KEYBOARD_REPORT_ID, buildKeyboardReport(0, null));
            hidDevice.sendReport(connectedHost, CONSUMER_REPORT_ID, new byte[]{0x00, 0x00});
        } catch (SecurityException ignored) {
        }
    }

    private void registerHidApp() {
        if (hidDevice == null || !hasBluetoothConnectPermission() || shuttingDown) {
            return;
        }
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Bluetooth Input Device",
                "Bluetooth keyboard and media control",
                "OpenAI",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                COMBO_HID_REPORT_DESCRIPTOR
        );
        try {
            boolean sent = hidDevice.registerApp(sdp, null, null, callbackExecutor, hidCallback);
            if (!sent) {
                setStatus("注册命令发送失败，请确认系统支持 Bluetooth HID Device。");
            }
        } catch (SecurityException e) {
            setStatus("注册失败：缺少蓝牙权限。");
        }
    }

    private void cleanupBluetooth() {
        sendAllReleasedState();
        cancelPhoneRumble();
        lastRumbleData = new byte[0];
        lastOutputSource = "未收到";
        lastOutputReportId = -1;
        if (hidDevice != null) {
            try {
                hidDevice.unregisterApp();
                if (bluetoothAdapter != null) {
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice);
                }
            } catch (SecurityException ignored) {
            }
        }
        hidDevice = null;
        connectedHost = null;
        hidAppRegistered = false;
    }

    private void handleOutputReport(BluetoothDevice device, byte reportId, byte[] data, String source) {
        if (data == null) {
            data = new byte[0];
        }
        lastRumbleData = Arrays.copyOf(data, data.length);
        lastOutputSource = source;
        lastOutputReportId = reportId & 0xFF;
        notifyStateChanged();

        if (containsNonZeroByte(data)) {
            applyPhoneRumble(data);
            setStatus("已接收主机输出报告（" + source + "，ID " + (reportId & 0xFF) + "，" + data.length + " 字节）。");
        }
    }

    private boolean containsNonZeroByte(byte[] data) {
        if (data == null) return false;
        for (byte value : data) {
            if (value != 0) return true;
        }
        return false;
    }

    private void applyPhoneRumble(byte[] data) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        int strong = data.length > 0 ? data[0] & 0xFF : 0;
        int weak = data.length > 1 ? data[1] & 0xFF : strong;
        int amplitude = Math.max(strong, weak);
        if (amplitude <= 0) {
            cancelPhoneRumble();
            return;
        }
        int scaledAmplitude = Math.max(1, Math.min(255, amplitude));
        long durationMs = 120L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, scaledAmplitude));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void cancelPhoneRumble() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void setStatus(String newStatus) {
        status = newStatus;
        updateNotification();
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        for (StateListener listener : listeners) {
            listener.onHidStateChanged();
        }
    }

    private void startAsForeground() {
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent disconnectPendingIntent = PendingIntent.getService(
                this,
                1,
                new Intent(this, HidRemoteService.class).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                2,
                makeStopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("翻翻")
                .setContentText(status)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(R.drawable.ic_launcher, "断开", disconnectPendingIntent).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_launcher, "停止", stopPendingIntent).build())
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "蓝牙输入连接",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("保持蓝牙 HID 自定义输入设备在锁屏时继续连接");
        manager.createNotificationChannel(channel);
    }

    private String safeDeviceName(BluetoothDevice device) {
        if (device == null) {
            return "未知设备";
        }
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "未命名设备" : name;
        } catch (SecurityException e) {
            return "蓝牙设备";
        }
    }
}
