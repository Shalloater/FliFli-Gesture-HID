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
    private static final int GAMEPAD_REPORT_ID = 3;
    private static final int GAMEPAD_OUTPUT_REPORT_ID = 4;
    private static final int GAMEPAD_AXIS_CENTER = 128;
    private static final int HID_MODE_COMBO = 0;
    private static final int HID_MODE_GAMEPAD_ONLY = 1;
    private static final int SWITCH_STAGE_NONE = 0;
    private static final int SWITCH_STAGE_WAIT_DISCONNECT = 1;
    private static final int SWITCH_STAGE_WAIT_UNREGISTER = 2;
    private static final int SWITCH_STAGE_WAIT_REGISTER = 3;
    private static final int SWITCH_STAGE_WAIT_RECONNECT = 4;

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

            0x05, 0x01,
            0x09, 0x05,
            (byte) 0xA1, 0x01,
            (byte) 0x85, 0x03,

            0x05, 0x09,
            0x19, 0x01,
            0x29, 0x14,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            (byte) 0x95, 0x14,
            (byte) 0x81, 0x02,

            0x75, 0x04,
            (byte) 0x95, 0x01,
            (byte) 0x81, 0x03,

            0x65, 0x00,
            0x09, 0x30,
            0x09, 0x31,
            0x09, 0x32,
            0x09, 0x33,
            0x15, (byte) 0x81,
            0x25, 0x7F,
            0x75, 0x08,
            (byte) 0x95, 0x04,
            (byte) 0x81, 0x02,

            (byte) 0x85, 0x04,
            0x05, 0x0F,
            0x09, 0x21,
            (byte) 0xA1, 0x02,
            0x09, (byte) 0x97,
            0x09, 0x70,
            0x15, 0x00,
            0x26, (byte) 0xFF, 0x00,
            0x75, 0x08,
            (byte) 0x95, 0x02,
            (byte) 0x91, 0x02,
            (byte) 0xC0,
            (byte) 0xC0
    };

    private static final byte[] GAMEPAD_ONLY_HID_REPORT_DESCRIPTOR = new byte[]{
            0x05, 0x01,
            0x09, 0x05,
            (byte) 0xA1, 0x01,
            (byte) 0x85, 0x03,

            0x05, 0x09,
            0x19, 0x01,
            0x29, 0x14,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            (byte) 0x95, 0x14,
            (byte) 0x81, 0x02,
            0x75, 0x04,
            (byte) 0x95, 0x01,
            (byte) 0x81, 0x03,

            0x65, 0x00,
            0x05, 0x01,
            0x09, 0x30,
            0x09, 0x31,
            0x09, 0x32,
            0x09, 0x33,
            0x15, (byte) 0x81,
            0x25, 0x7F,
            0x75, 0x08,
            (byte) 0x95, 0x04,
            (byte) 0x81, 0x02,

            0x05, 0x0F,
            0x09, 0x21,
            (byte) 0x85, 0x04,
            (byte) 0xA1, 0x02,
            0x09, (byte) 0x97,
            0x09, 0x70,
            0x15, 0x00,
            0x26, (byte) 0xFF, 0x00,
            0x75, 0x08,
            (byte) 0x95, 0x02,
            (byte) 0x91, 0x02,
            (byte) 0xC0,
            (byte) 0xC0
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
    private int requestedHidMode = HID_MODE_COMBO;
    private int registeredHidMode = -1;
    private int switchStage = SWITCH_STAGE_NONE;
    private BluetoothDevice reconnectTargetDevice;
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
                    registeredHidMode = requestedHidMode;
                    setStatus("自定义输入设备已注册，可发送键盘/媒体键/手柄按键。");
                    if (switchStage == SWITCH_STAGE_WAIT_REGISTER && reconnectTargetDevice != null) {
                        switchStage = SWITCH_STAGE_WAIT_RECONNECT;
                        connectToHost(reconnectTargetDevice);
                    } else {
                        switchStage = SWITCH_STAGE_NONE;
                    }
                } else {
                    registeredHidMode = -1;
                    connectedHost = null;
                    setStatus("HID 设备已注销，正在尝试重新注册...");
                    if (switchStage == SWITCH_STAGE_WAIT_UNREGISTER || switchStage == SWITCH_STAGE_WAIT_REGISTER) {
                        switchStage = SWITCH_STAGE_WAIT_REGISTER;
                        if (!shuttingDown) {
                            mainHandler.postDelayed(HidRemoteService.this::registerHidApp, 200);
                        }
                    } else if (!shuttingDown) {
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
                    if (switchStage == SWITCH_STAGE_WAIT_DISCONNECT) {
                        switchStage = SWITCH_STAGE_WAIT_UNREGISTER;
                        if (hidDevice != null && hasBluetoothConnectPermission()) {
                            try {
                                hidAppRegistered = false;
                                hidDevice.unregisterApp();
                                setStatus("已断开旧连接，正在重注册输入模式...");
                                return;
                            } catch (SecurityException e) {
                                setStatus("重注册输入模式失败：缺少蓝牙权限。");
                                switchStage = SWITCH_STAGE_NONE;
                            }
                        }
                    } else if (switchStage == SWITCH_STAGE_WAIT_RECONNECT && reconnectTargetDevice != null) {
                        connectToHost(reconnectTargetDevice);
                        return;
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

    public String getLastRumbleDebugText() {
        if (lastRumbleData == null || lastRumbleData.length == 0) {
            return "未收到";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(lastOutputSource).append(" ID ").append(lastOutputReportId).append(" : ");
        int limit = Math.min(lastRumbleData.length, 8);
        for (int i = 0; i < limit; i++) {
            if (i > 0) builder.append(' ');
            builder.append(String.format("%02X", lastRumbleData[i] & 0xFF));
        }
        if (lastRumbleData.length > limit) {
            builder.append(" ...");
        }
        return builder.toString();
    }

    public void setPreferredHidMode(boolean gamepadOnly) {
        int desiredMode = gamepadOnly ? HID_MODE_GAMEPAD_ONLY : HID_MODE_COMBO;
        requestedHidMode = desiredMode;
        if (hidDevice == null) {
            connectHidProfile();
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            return;
        }
        if (!hidAppRegistered) {
            registerHidApp();
            return;
        }
        if (registeredHidMode == desiredMode) {
            return;
        }
        try {
            sendAllReleasedState();
            hidAppRegistered = false;
            connectedHost = null;
            hidDevice.unregisterApp();
            setStatus(gamepadOnly ? "正在切换为纯手柄模式..." : "正在切换为组合输入模式...");
        } catch (SecurityException e) {
            setStatus("切换输入模式失败：缺少蓝牙权限。");
        }
    }

    public boolean switchHidModeAndReconnect(BluetoothDevice device, boolean gamepadOnly) {
        if (device == null) {
            setStatus("没有可用于切换模式的目标设备。");
            return false;
        }
        requestedHidMode = gamepadOnly ? HID_MODE_GAMEPAD_ONLY : HID_MODE_COMBO;
        reconnectTargetDevice = device;
        if (hidDevice == null) {
            switchStage = SWITCH_STAGE_WAIT_REGISTER;
            connectHidProfile();
            return true;
        }
        if (!hasBluetoothConnectPermission()) {
            setStatus("切换输入模式失败：缺少蓝牙权限。");
            return false;
        }
        if (connectedHost != null && connectedHost.equals(device)) {
            switchStage = SWITCH_STAGE_WAIT_DISCONNECT;
            disconnectHost();
            return true;
        }
        if (hidAppRegistered) {
            switchStage = SWITCH_STAGE_WAIT_UNREGISTER;
            try {
                sendAllReleasedState();
                hidAppRegistered = false;
                hidDevice.unregisterApp();
                setStatus(gamepadOnly ? "正在切换为纯手柄模式..." : "正在切换为组合输入模式...");
                return true;
            } catch (SecurityException e) {
                setStatus("切换输入模式失败：缺少蓝牙权限。");
                switchStage = SWITCH_STAGE_NONE;
                return false;
            }
        }
        switchStage = SWITCH_STAGE_WAIT_REGISTER;
        registerHidApp();
        return true;
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
                case GAMEPAD:
                    sendGamepadAction(action);
                    break;
                default:
                    setStatus("当前手势未绑定任何输出。");
                    break;
            }
        } catch (SecurityException e) {
            setStatus("发送失败：缺少蓝牙权限。");
        }
    }

    public void sendGamepadState(int buttons, int hat, int lx, int ly, int rx, int ry, int lt, int rt) {
        if (hidDevice == null || connectedHost == null || !hasBluetoothConnectPermission()) {
            setStatus("尚未连接目标设备。先配对并连接电脑、电视、平板等 HID 主机。");
            return;
        }
        try {
            hidDevice.sendReport(connectedHost, GAMEPAD_REPORT_ID,
                    buildGamepadReport(buttons, hat, lx, ly, rx, ry, lt, rt));
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

    private void sendGamepadAction(HidAction.ActionDefinition action) {
        byte[] press = buildGamepadReport(action.gamepadButtons, 8,
                GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER, 0, 0);
        byte[] release = buildGamepadReport(0, 8,
                GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER, 0, 0);
        hidDevice.sendReport(connectedHost, GAMEPAD_REPORT_ID, press);
        setStatus("已发送：" + action.label);
        mainHandler.postDelayed(() -> sendRelease(GAMEPAD_REPORT_ID, release), 35);
    }

    private byte[] buildGamepadReport(int buttons, int hat, int lx, int ly, int rx, int ry, int lt, int rt) {
        int reportButtons = mapInternalButtonsToReportButtons(buttons, hat, lt, rt);
        return new byte[]{
                (byte) (reportButtons & 0xFF),
                (byte) ((reportButtons >> 8) & 0xFF),
                (byte) ((reportButtons >> 16) & 0x0F),
                (byte) toSignedAxisByte(lx),
                (byte) toSignedAxisByte(ly),
                (byte) toSignedAxisByte(rx),
                (byte) toSignedAxisByte(ry)
        };
    }

    private int toSignedAxisByte(int value) {
        int clamped = clamp(value, 0, 255);
        return clamp(clamped - GAMEPAD_AXIS_CENTER, -127, 127);
    }

    private int mapInternalButtonsToReportButtons(int buttons, int hat, int lt, int rt) {
        int reportButtons = 0;
        if ((buttons & HidAction.GAMEPAD_BUTTON_A) != 0) reportButtons |= 1 << 0;
        if ((buttons & HidAction.GAMEPAD_BUTTON_B) != 0) reportButtons |= 1 << 1;
        if ((buttons & HidAction.GAMEPAD_BUTTON_X) != 0) reportButtons |= 1 << 2;
        if ((buttons & HidAction.GAMEPAD_BUTTON_Y) != 0) reportButtons |= 1 << 3;
        if ((buttons & HidAction.GAMEPAD_BUTTON_L1) != 0) reportButtons |= 1 << 4;
        if ((buttons & HidAction.GAMEPAD_BUTTON_R1) != 0) reportButtons |= 1 << 5;
        if ((buttons & HidAction.GAMEPAD_BUTTON_L2) != 0 || lt > 0) reportButtons |= 1 << 6;
        if ((buttons & HidAction.GAMEPAD_BUTTON_R2) != 0 || rt > 0) reportButtons |= 1 << 7;
        if ((buttons & HidAction.GAMEPAD_BUTTON_MINUS) != 0) reportButtons |= 1 << 8;
        if ((buttons & HidAction.GAMEPAD_BUTTON_PLUS) != 0) reportButtons |= 1 << 9;
        if ((buttons & HidAction.GAMEPAD_BUTTON_L3) != 0) reportButtons |= 1 << 10;
        if ((buttons & HidAction.GAMEPAD_BUTTON_R3) != 0) reportButtons |= 1 << 11;
        int dpadButtons = buttonsFromHat(hat == 8 ? deriveHatFromButtons(buttons) : hat);
        if ((buttons & HidAction.GAMEPAD_BUTTON_DPAD_UP) != 0
                || (dpadButtons & HidAction.GAMEPAD_BUTTON_DPAD_UP) != 0) {
            reportButtons |= 1 << 12;
        }
        if ((buttons & HidAction.GAMEPAD_BUTTON_DPAD_DOWN) != 0
                || (dpadButtons & HidAction.GAMEPAD_BUTTON_DPAD_DOWN) != 0) {
            reportButtons |= 1 << 13;
        }
        if ((buttons & HidAction.GAMEPAD_BUTTON_DPAD_LEFT) != 0
                || (dpadButtons & HidAction.GAMEPAD_BUTTON_DPAD_LEFT) != 0) {
            reportButtons |= 1 << 14;
        }
        if ((buttons & HidAction.GAMEPAD_BUTTON_DPAD_RIGHT) != 0
                || (dpadButtons & HidAction.GAMEPAD_BUTTON_DPAD_RIGHT) != 0) {
            reportButtons |= 1 << 15;
        }
        if ((buttons & HidAction.GAMEPAD_BUTTON_HOME) != 0) reportButtons |= 1 << 16;
        if ((buttons & HidAction.GAMEPAD_BUTTON_CAPTURE) != 0) reportButtons |= 1 << 17;
        if ((buttons & HidAction.GAMEPAD_BUTTON_SL) != 0) reportButtons |= 1 << 18;
        if ((buttons & HidAction.GAMEPAD_BUTTON_SR) != 0) reportButtons |= 1 << 19;
        return reportButtons;
    }

    private int buttonsFromHat(int hat) {
        switch (hat) {
            case 0:
                return HidAction.GAMEPAD_BUTTON_DPAD_UP;
            case 1:
                return HidAction.GAMEPAD_BUTTON_DPAD_UP | HidAction.GAMEPAD_BUTTON_DPAD_RIGHT;
            case 2:
                return HidAction.GAMEPAD_BUTTON_DPAD_RIGHT;
            case 3:
                return HidAction.GAMEPAD_BUTTON_DPAD_RIGHT | HidAction.GAMEPAD_BUTTON_DPAD_DOWN;
            case 4:
                return HidAction.GAMEPAD_BUTTON_DPAD_DOWN;
            case 5:
                return HidAction.GAMEPAD_BUTTON_DPAD_DOWN | HidAction.GAMEPAD_BUTTON_DPAD_LEFT;
            case 6:
                return HidAction.GAMEPAD_BUTTON_DPAD_LEFT;
            case 7:
                return HidAction.GAMEPAD_BUTTON_DPAD_LEFT | HidAction.GAMEPAD_BUTTON_DPAD_UP;
            default:
                return 0;
        }
    }

    private int deriveHatFromButtons(int buttons) {
        boolean up = (buttons & HidAction.GAMEPAD_BUTTON_DPAD_UP) != 0;
        boolean down = (buttons & HidAction.GAMEPAD_BUTTON_DPAD_DOWN) != 0;
        boolean left = (buttons & HidAction.GAMEPAD_BUTTON_DPAD_LEFT) != 0;
        boolean right = (buttons & HidAction.GAMEPAD_BUTTON_DPAD_RIGHT) != 0;
        if (up && right) return 1;
        if (right && down) return 3;
        if (down && left) return 5;
        if (left && up) return 7;
        if (up) return 0;
        if (right) return 2;
        if (down) return 4;
        if (left) return 6;
        return 8;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
            if (registeredHidMode == HID_MODE_COMBO) {
                hidDevice.sendReport(connectedHost, KEYBOARD_REPORT_ID, buildKeyboardReport(0, null));
                hidDevice.sendReport(connectedHost, CONSUMER_REPORT_ID, new byte[]{0x00, 0x00});
            }
            hidDevice.sendReport(connectedHost, GAMEPAD_REPORT_ID,
                    buildGamepadReport(0, 8, GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER,
                            GAMEPAD_AXIS_CENTER, GAMEPAD_AXIS_CENTER, 0, 0));
        } catch (SecurityException ignored) {
        }
    }

    private void registerHidApp() {
        if (hidDevice == null || !hasBluetoothConnectPermission() || shuttingDown) {
            return;
        }
        boolean gamepadOnly = requestedHidMode == HID_MODE_GAMEPAD_ONLY;
        byte subclass = gamepadOnly
                ? (byte) BluetoothHidDevice.SUBCLASS2_GAMEPAD
                : (byte) (BluetoothHidDevice.SUBCLASS1_COMBO | BluetoothHidDevice.SUBCLASS2_GAMEPAD);
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                gamepadOnly ? "Bluetooth Gamepad" : "Bluetooth Input Device",
                gamepadOnly ? "Standard HID gamepad" : "Bluetooth keyboard, media control and standard gamepad",
                "OpenAI",
                subclass,
                gamepadOnly ? GAMEPAD_ONLY_HID_REPORT_DESCRIPTOR : COMBO_HID_REPORT_DESCRIPTOR
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
        registeredHidMode = -1;
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
        } else if (reportId == GAMEPAD_OUTPUT_REPORT_ID
                || looksLikeSimpleRumblePacket(data)) {
            cancelPhoneRumble();
            setStatus("已接收主机停止震动报告（" + source + "，ID " + (reportId & 0xFF) + "）。");
        }
    }

    private boolean looksLikeSimpleRumblePacket(byte[] data) {
        return data != null && data.length >= 2 && data.length <= 8;
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
                .setContentTitle("自定义蓝牙输入")
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
