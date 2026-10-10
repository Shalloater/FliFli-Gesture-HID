package com.example.joyvolumeremote;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.BatteryManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQ_ENABLE_BLUETOOTH = 1002;
    private static final int REQ_PICK_KEYBOARD_BACKGROUND = 1003;
    private static final String PREFS_NAME = "pager_settings";
    private static final String PREF_VIBRATION_ENABLED = "vibration_enabled";
    private static final String PREF_CONTROL_MODE = "control_mode";
    private static final String PREF_ACTION_PREFIX = "action_";
    private static final String PREF_KEYBOARDS_JSON = "custom_keyboards_json";
    private static final String PREF_NEXT_KEYBOARD_ID = "next_keyboard_id";
    private static final String CONTROL_MODE_LANDSCAPE = "landscape";
    private static final String CONTROL_MODE_PORTRAIT = "portrait";
    private static final String CONTROL_MODE_FULLSCREEN = "fullscreen";
    private static final String STICK_CLICK_MODE_TAP = "stick_tap";
    private static final String STICK_CLICK_MODE_BUTTON = "stick_button";
    private static final String KEYBOARD_TYPE_FULLSCREEN_GESTURE = "fullscreen_gesture";
    private static final String KEYBOARD_TYPE_COMPUTER = "computer_keyboard";
    private static final String KEYBOARD_TYPE_GAMEPAD = "gamepad";
    private static final String KEYBOARD_TYPE_JOYCON_PAIR = "joycon_pair";
    private static final String KEYBOARD_TYPE_JOYCON_LEFT = "joycon_left";
    private static final String KEYBOARD_TYPE_JOYCON_RIGHT = "joycon_right";
    private static final String GESTURE_KIND_TAP = "tap";
    private static final String GESTURE_KIND_MOTION = "motion";
    private static final String TAP_AREA_FULL = "full";
    private static final String TAP_AREA_LEFT = "left";
    private static final String TAP_AREA_RIGHT = "right";
    private static final String TAP_AREA_TOP = "top";
    private static final String TAP_AREA_BOTTOM = "bottom";
    private static final String MOTION_SWIPE_UP = "swipe_up";
    private static final String MOTION_SWIPE_DOWN = "swipe_down";
    private static final String MOTION_SWIPE_LEFT = "swipe_left";
    private static final String MOTION_SWIPE_RIGHT = "swipe_right";
    private static final String MOTION_CIRCLE_CLOCKWISE = "circle_clockwise";
    private static final String MOTION_CIRCLE_COUNTERCLOCKWISE = "circle_counterclockwise";
    private static final int PAGE_CONNECTION = 0;
    private static final int PAGE_KEYBOARDS = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final int VIBRATION_LIGHT = 0;
    private static final int VIBRATION_MEDIUM = 1;
    private static final int VIBRATION_STRONG = 2;
    private static final String TRIGGER_LANDSCAPE_TOP = "landscape_top";
    private static final String TRIGGER_LANDSCAPE_BOTTOM = "landscape_bottom";
    private static final String TRIGGER_PORTRAIT_LEFT = "portrait_left";
    private static final String TRIGGER_PORTRAIT_RIGHT = "portrait_right";
    private static final String TRIGGER_FULLSCREEN_SINGLE = "fullscreen_single";
    private static final String TRIGGER_FULLSCREEN_DOUBLE = "fullscreen_double";
    private static final String TRIGGER_SWIPE_UP = "swipe_up";
    private static final String TRIGGER_SWIPE_DOWN = "swipe_down";
    private static final String TRIGGER_SWIPE_LEFT = "swipe_left";
    private static final String TRIGGER_SWIPE_RIGHT = "swipe_right";
    private static final String[] GESTURE_TRIGGER_IDS = {
            TRIGGER_FULLSCREEN_SINGLE,
            TRIGGER_FULLSCREEN_DOUBLE,
            TRIGGER_SWIPE_UP,
            TRIGGER_SWIPE_DOWN,
            TRIGGER_SWIPE_LEFT,
            TRIGGER_SWIPE_RIGHT,
            TRIGGER_LANDSCAPE_TOP,
            TRIGGER_LANDSCAPE_BOTTOM,
            TRIGGER_PORTRAIT_LEFT,
            TRIGGER_PORTRAIT_RIGHT
    };
    private static final String[] TAP_AREAS = {
            TAP_AREA_FULL,
            TAP_AREA_LEFT,
            TAP_AREA_RIGHT,
            TAP_AREA_TOP,
            TAP_AREA_BOTTOM
    };
    private static final String[] MOTION_GESTURES = {
            MOTION_SWIPE_UP,
            MOTION_SWIPE_DOWN,
            MOTION_SWIPE_LEFT,
            MOTION_SWIPE_RIGHT,
            MOTION_CIRCLE_CLOCKWISE,
            MOTION_CIRCLE_COUNTERCLOCKWISE
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter bluetoothAdapter;
    private HidRemoteService hidService;
    private SharedPreferences preferences;
    private boolean serviceBound = false;

    private TextView connectionTitleView;
    private TextView connectionSubtitleView;
    private TextView bluetoothStateView;
    private LinearLayout pairedListLayout;
    private IconView statusBtIcon;
    private IconView quickBtIcon;
    private boolean showingRemotePage = false;
    private boolean showingSettingsPage = false;
    private boolean showingKeyboardPage = false;
    private boolean wasConnected = false;
    private boolean wasBluetoothOn = false;
    private boolean awaitingModeReconnect = false;
    private CustomKeyboardConfig activeKeyboardConfig;
    private CustomKeyboardConfig pendingRemoteConfig;
    private CustomKeyboardConfig pendingBackgroundDraft;
    private BluetoothDevice pendingRemoteDevice;
    private boolean pendingBackgroundIsNew;
    private Runnable refreshFeedbackResetRunnable;
    private final Runnable restoreSetupOrientationRunnable = () -> {
        if (!showingRemotePage) {
            applyOrientationForSetup();
        }
    };

    private final HidRemoteService.StateListener stateListener =
            () -> mainHandler.post(this::syncUiFromService);

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            hidService = ((HidRemoteService.LocalBinder) binder).getService();
            serviceBound = true;
            hidService.addStateListener(stateListener);
            hidService.connectHidProfile();
            syncUiFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (hidService != null) hidService.removeStateListener(stateListener);
            hidService = null;
            serviceBound = false;
            showSetupPage();
            updateStatus("HID 后台服务已断开，请重新打开 App。");
        }
    };

    private static final class CustomKeyboardConfig {
        String id;
        String name;
        String type;
        String controlMode;
        String stickClickMode;
        String backgroundImageUri;
        boolean vibrationEnabled;
        int vibrationLevel;
        final List<GestureBinding> bindings = new ArrayList<>();

        CustomKeyboardConfig copy() {
            CustomKeyboardConfig copy = new CustomKeyboardConfig();
            copy.id = id;
            copy.name = name;
            copy.type = type;
            copy.controlMode = controlMode;
            copy.stickClickMode = stickClickMode;
            copy.backgroundImageUri = backgroundImageUri;
            copy.vibrationEnabled = vibrationEnabled;
            copy.vibrationLevel = vibrationLevel;
            for (GestureBinding binding : bindings) {
                copy.bindings.add(binding.copy());
            }
            return copy;
        }
    }

    private static final class GestureBinding {
        String kind;
        int fingers;
        int tapCount;
        String area;
        String motion;
        String actionId;

        GestureBinding copy() {
            GestureBinding copy = new GestureBinding();
            copy.kind = kind;
            copy.fingers = fingers;
            copy.tapCount = tapCount;
            copy.area = area;
            copy.motion = motion;
            copy.actionId = actionId;
            return copy;
        }

        String key() {
            if (GESTURE_KIND_TAP.equals(kind)) {
                return tapKey(fingers, tapCount, area);
            }
            return motionKey(fingers, motion);
        }

        static String tapKey(int fingers, int tapCount, String area) {
            return GESTURE_KIND_TAP + ":" + fingers + ":" + tapCount + ":" + area;
        }

        static String motionKey(int fingers, String motion) {
            return GESTURE_KIND_MOTION + ":" + fingers + ":" + motion;
        }
    }

    private interface PickerSelectionHandler {
        void onSelect(int index);
    }

    private static final class PickerItem {
        final String title;
        final String subtitle;
        final String chipText;
        final int iconType;
        final int iconColor;
        final boolean enabled;

        PickerItem(String title, String subtitle, String chipText, int iconType, int iconColor, boolean enabled) {
            this.title = title;
            this.subtitle = subtitle;
            this.chipText = chipText;
            this.iconType = iconType;
            this.iconColor = iconColor;
            this.enabled = enabled;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureKeyboardConfigsInitialized();
        showSetupPage();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            updateStatus("此方案需要 Android 9.0 或以上，因为系统从 API 28 开始提供 HID Device 接口。");
            return;
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            updateStatus("这台设备没有可用的蓝牙适配器。");
            return;
        }

        requestBluetoothPermissionsIfNeeded();
        if (hasBluetoothConnectPermission()) startAndBindHidService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bluetoothAdapter != null && hasBluetoothConnectPermission()) {
            startAndBindHidService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hidService != null) hidService.connectHidProfile();
        boolean btOn = isBluetoothEnabled();
        if (btOn != wasBluetoothOn && isConnectionPageVisible()) {
            wasBluetoothOn = btOn;
            showSetupPage();
            return;
        }
        wasBluetoothOn = btOn;
        syncUiFromService();
    }

    @Override
    protected void onStop() {
        if (serviceBound && hidService != null) {
            hidService.removeStateListener(stateListener);
            unbindService(serviceConnection);
            serviceBound = false;
            hidService = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BLUETOOTH && hidService != null) {
            hidService.connectHidProfile();
            syncUiFromService();
            return;
        }
        if (requestCode == REQ_PICK_KEYBOARD_BACKGROUND) {
            handleKeyboardBackgroundPicked(resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLUETOOTH_PERMISSIONS) {
            if (hasBluetoothConnectPermission()) {
                updateStatus("蓝牙权限已授予，正在启动后台连接服务...");
                startAndBindHidService();
            } else {
                updateStatus("没有蓝牙连接权限，无法作为蓝牙输入设备工作。请在系统设置里授予“附近设备”权限。");
            }
        }
    }

    private void startAndBindHidService() {
        Intent intent = new Intent(this, HidRemoteService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        if (!serviceBound) bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void syncUiFromService() {
        if (hidService == null) {
            updateStatus("初始化中...");
            if (isConnectionPageVisible()) refreshPairedDevices();
            return;
        }

        BluetoothDevice connected = hidService.getConnectedHost();
        boolean isConnected = connected != null;

        if (awaitingModeReconnect) {
            if (isConnected && pendingRemoteDevice != null && connected.equals(pendingRemoteDevice) && pendingRemoteConfig != null) {
                awaitingModeReconnect = false;
                BluetoothDevice targetDevice = pendingRemoteDevice;
                CustomKeyboardConfig targetConfig = pendingRemoteConfig;
                pendingRemoteDevice = null;
                pendingRemoteConfig = null;
                showRemotePage(targetDevice, targetConfig);
                return;
            }
            if (!isConnected && !showingRemotePage) {
                showKeyboardsPage(false);
            }
        }

        if (!isConnected && showingRemotePage) {
            showKeyboardsPage();
        } else if (isConnected && !wasConnected && isConnectionPageVisible()) {
            showSetupPage();
        } else if (wasConnected && !isConnected && isConnectionPageVisible()) {
            showSetupPage();
        } else if (wasConnected != isConnected && showingKeyboardPage) {
            showKeyboardsPage();
        }

        wasConnected = isConnected;

        updateStatus(hidService.getStatus());
        if (isConnectionPageVisible()) refreshPairedDevices();
    }

    private boolean isConnectionPageVisible() {
        return !showingRemotePage && !showingSettingsPage && !showingKeyboardPage;
    }

    private void showSetupPage() {
        mainHandler.removeCallbacks(restoreSetupOrientationRunnable);
        exitRemoteFullscreen();
        allowScreenSleep();
        applyOrientationForSetup();
        showingRemotePage = false;
        showingSettingsPage = false;
        showingKeyboardPage = false;
        connectionTitleView = null;
        connectionSubtitleView = null;
        bluetoothStateView = null;

        LinearLayout content = createPageContent();

        LinearLayout statusCard = createCardLayout(dp(24), 3f);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setMinimumHeight(dp(100));
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        BluetoothDevice connectedForStatus = hidService == null ? null : hidService.getConnectedHost();
        int statusIconColor = connectedForStatus != null ? Color.rgb(36, 122, 255) : Color.rgb(160, 170, 190);
        int statusIconType = connectedForStatus == null
                ? IconView.ICON_BLUETOOTH
                : resolveDeviceIconType(connectedForStatus);
        FrameLayout btBubble = new FrameLayout(this);
        btBubble.setBackground(roundedRect(Color.rgb(245, 247, 252), dp(30), Color.rgb(245, 247, 252), 0));
        statusBtIcon = new IconView(this, statusIconType, statusIconColor, 2.4f);
        btBubble.addView(statusBtIcon, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        btBubble.setLayoutParams(new FrameLayout.LayoutParams(dp(60), dp(60)));
        statusCard.addView(btBubble);

        LinearLayout statusTextWrap = new LinearLayout(this);
        statusTextWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(0, -2, 1f);
        statusTextParams.leftMargin = dp(12);

        connectionTitleView = new TextView(this);
        connectionTitleView.setText("未连接设备");
        setTextSizeDp(connectionTitleView, 14);
        connectionTitleView.setTextColor(Color.rgb(19, 29, 49));
        connectionTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        connectionSubtitleView = new TextView(this);
        connectionSubtitleView.setText("请选择已配对设备并建立连接");
        setTextSizeDp(connectionSubtitleView, 10.5f);
        connectionSubtitleView.setTextColor(Color.rgb(123, 137, 165));
        connectionSubtitleView.setLineSpacing(0f, 1.12f);
        connectionSubtitleView.setPadding(0, dp(5), 0, 0);

        statusTextWrap.addView(connectionTitleView, new LinearLayout.LayoutParams(-1, -2));
        statusTextWrap.addView(connectionSubtitleView, new LinearLayout.LayoutParams(-1, -2));
        statusCard.addView(statusTextWrap, statusTextParams);

        LinearLayout.LayoutParams statusCardParams = new LinearLayout.LayoutParams(-1, -2);
        statusCardParams.bottomMargin = dp(14);
        content.addView(statusCard, statusCardParams);

        if (hasConnectedHost()) {
            TextView disconnectBtn = new TextView(this);
            disconnectBtn.setText("断开连接");
            setTextSizeDp(disconnectBtn, 12.5f);
            disconnectBtn.setTextColor(Color.rgb(220, 60, 60));
            disconnectBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            disconnectBtn.setGravity(Gravity.CENTER);
            disconnectBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
            disconnectBtn.setBackground(roundedRect(Color.rgb(255, 245, 245), dp(16), Color.rgb(255, 220, 220), 1));
            disconnectBtn.setOnClickListener(v -> disconnectHost());
            LinearLayout.LayoutParams disconnectParams = new LinearLayout.LayoutParams(-1, -2);
            disconnectParams.bottomMargin = dp(14);
            content.addView(disconnectBtn, disconnectParams);
        }

        LinearLayout pairedCard = createCardLayout(dp(24), 3f);
        pairedCard.setOrientation(LinearLayout.VERTICAL);
        pairedCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView pairedTitle = new TextView(this);
        pairedTitle.setText("已配对设备");
        setTextSizeDp(pairedTitle, 13);
        pairedTitle.setTextColor(Color.rgb(18, 28, 47));
        pairedTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerRow.addView(pairedTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        View bluetoothSettingsButton = makeIconButton(IconView.ICON_BLUETOOTH, v ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        headerRow.addView(bluetoothSettingsButton, new LinearLayout.LayoutParams(dp(30), dp(30)));

        View refreshButton = makeIconButton(IconView.ICON_REFRESH, v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            refreshPairedDevices(true);
        });
        LinearLayout.LayoutParams refreshIconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        refreshIconParams.leftMargin = dp(6);
        headerRow.addView(refreshButton, refreshIconParams);

        pairedCard.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout listShell = new LinearLayout(this);
        listShell.setOrientation(LinearLayout.VERTICAL);
        listShell.setBackground(roundedRect(Color.WHITE, dp(20), Color.rgb(234, 239, 248), 1));
        listShell.setPadding(0, dp(2), 0, dp(2));
        listShell.setClipToOutline(true);

        pairedListLayout = new LinearLayout(this);
        pairedListLayout.setOrientation(LinearLayout.VERTICAL);

        ScrollView listScrollView = new ScrollView(this);
        listScrollView.setFillViewport(false);
        listScrollView.setVerticalScrollBarEnabled(false);
        listScrollView.addView(pairedListLayout, new ScrollView.LayoutParams(-1, -2));
        listShell.addView(listScrollView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams listShellParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        listShellParams.topMargin = dp(12);
        pairedCard.addView(listShell, listShellParams);

        content.addView(pairedCard, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(buildScaffoldNoScroll(content, PAGE_CONNECTION));
        updateStatus(hidService != null ? hidService.getStatus() : "初始化中...");
        refreshPairedDevices();
    }

    private void showKeyboardsPage() {
        showKeyboardsPage(true);
    }

    private void showKeyboardsPage(boolean applySetupOrientation) {
        mainHandler.removeCallbacks(restoreSetupOrientationRunnable);
        exitRemoteFullscreen();
        allowScreenSleep();
        if (applySetupOrientation) {
            applyOrientationForSetup();
        }
        showingRemotePage = false;
        showingSettingsPage = false;
        showingKeyboardPage = true;
        connectionTitleView = null;
        connectionSubtitleView = null;
        bluetoothStateView = null;
        statusBtIcon = null;
        quickBtIcon = null;
        pairedListLayout = null;

        LinearLayout content = createPageContent();

        BluetoothDevice connected = hidService == null ? null : hidService.getConnectedHost();
        if (connected != null) {
            LinearLayout statusCard = createCardLayout(dp(24), 3f);
            statusCard.setOrientation(LinearLayout.HORIZONTAL);
            statusCard.setGravity(Gravity.CENTER_VERTICAL);
            statusCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            statusCard.addView(makeIconBubble(40, Color.rgb(245, 247, 252),
                    resolveDeviceIconType(connected), Color.rgb(36, 122, 255), 1.8f));

            LinearLayout statusTextWrap = new LinearLayout(this);
            statusTextWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(0, -2, 1f);
            statusTextParams.leftMargin = dp(10);

            TextView statusTitle = new TextView(this);
            statusTitle.setText("已连接 " + safeDeviceName(connected));
            setTextSizeDp(statusTitle, 13);
            statusTitle.setTextColor(Color.rgb(18, 28, 47));
            statusTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

            TextView statusSubtitle = new TextView(this);
            statusSubtitle.setText("选择下方键盘开始发送 HID 输入");
            setTextSizeDp(statusSubtitle, 10.5f);
            statusSubtitle.setTextColor(Color.rgb(123, 137, 165));
            statusSubtitle.setPadding(0, dp(5), 0, 0);

            statusTextWrap.addView(statusTitle, new LinearLayout.LayoutParams(-1, -2));
            statusTextWrap.addView(statusSubtitle, new LinearLayout.LayoutParams(-1, -2));
            statusCard.addView(statusTextWrap, statusTextParams);

            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
            statusParams.bottomMargin = dp(12);
            content.addView(statusCard, statusParams);
        }

        LinearLayout keyboardCard = createCardLayout(dp(24), 3f);
        keyboardCard.setOrientation(LinearLayout.VERTICAL);
        keyboardCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        listWrap.setBackground(roundedRect(Color.WHITE, dp(18), Color.rgb(234, 239, 248), 1));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardCard.addView(listWrap, listParams);

        List<CustomKeyboardConfig> configs = loadKeyboardConfigs();
        if (configs.isEmpty()) {
            addPlainInfoRow(listWrap, "还没有自定义键盘。请到设置中新增键盘。");
        } else {
            for (int i = 0; i < configs.size(); i++) {
                addKeyboardRunRow(listWrap, configs.get(i), connected, i < configs.size() - 1);
            }
        }

        LinearLayout.LayoutParams keyboardCardParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardCardParams.bottomMargin = dp(12);
        content.addView(keyboardCard, keyboardCardParams);

        TextView addHint = makePrimaryTextButton("到设置新增键盘", v -> showSettingsPage());
        content.addView(addHint, new LinearLayout.LayoutParams(-1, -2));

        setContentView(buildScaffold(content, PAGE_KEYBOARDS));
    }

    private void showSettingsPage() {
        mainHandler.removeCallbacks(restoreSetupOrientationRunnable);
        exitRemoteFullscreen();
        allowScreenSleep();
        applyOrientationForSetup();
        showingRemotePage = false;
        showingSettingsPage = true;
        showingKeyboardPage = false;
        connectionTitleView = null;
        connectionSubtitleView = null;
        bluetoothStateView = null;
        statusBtIcon = null;
        quickBtIcon = null;
        pairedListLayout = null;

        LinearLayout content = createPageContent();

        LinearLayout keyboardsCard = createCardLayout(dp(24), 3f);
        keyboardsCard.setOrientation(LinearLayout.VERTICAL);
        keyboardsCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = new TextView(this);
        heading.setText("自定义键盘");
        setTextSizeDp(heading, 13);
        heading.setTextColor(Color.rgb(18, 28, 47));
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView addButton = makeSmallTextButton("新增", v -> showKeyboardTypePicker());
        headerRow.addView(addButton, new LinearLayout.LayoutParams(-2, -2));
        keyboardsCard.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout keyboardListWrap = new LinearLayout(this);
        keyboardListWrap.setOrientation(LinearLayout.VERTICAL);
        keyboardListWrap.setBackground(roundedRect(Color.WHITE, dp(18), Color.rgb(234, 239, 248), 1));
        LinearLayout.LayoutParams keyboardListParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardListParams.topMargin = dp(12);
        keyboardsCard.addView(keyboardListWrap, keyboardListParams);

        List<CustomKeyboardConfig> configs = loadKeyboardConfigs();
        if (configs.isEmpty()) {
            addPlainInfoRow(keyboardListWrap, "还没有自定义键盘。点击右上角新增。");
        } else {
            for (int i = 0; i < configs.size(); i++) {
                addKeyboardEditRow(keyboardListWrap, configs.get(i), i < configs.size() - 1);
            }
        }

        LinearLayout.LayoutParams keyboardsParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardsParams.bottomMargin = dp(12);
        content.addView(keyboardsCard, keyboardsParams);

        LinearLayout typeCard = createCardLayout(dp(24), 3f);
        typeCard.setOrientation(LinearLayout.VERTICAL);
        typeCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView typeHeading = new TextView(this);
        typeHeading.setText("键盘种类");
        setTextSizeDp(typeHeading, 13);
        typeHeading.setTextColor(Color.rgb(18, 28, 47));
        typeHeading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        typeCard.addView(typeHeading, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout typeList = new LinearLayout(this);
        typeList.setOrientation(LinearLayout.VERTICAL);
        typeList.setBackground(roundedRect(Color.WHITE, dp(18), Color.rgb(234, 239, 248), 1));
        LinearLayout.LayoutParams typeListParams = new LinearLayout.LayoutParams(-1, -2);
        typeListParams.topMargin = dp(12);
        typeCard.addView(typeList, typeListParams);

        addKeyboardTypeRow(typeList, "全屏手势键盘", "可用", true);
        addKeyboardTypeRow(typeList, "电脑键盘", "可用", false);

        content.addView(typeCard, new LinearLayout.LayoutParams(-1, -2));

        setContentView(buildScaffold(content, PAGE_SETTINGS));
    }

    private void showKeyboardTypePicker() {
        List<PickerItem> types = new ArrayList<>();
        types.add(new PickerItem("全屏手势键盘", "当前可创建和运行", "可用",
                IconView.ICON_KEYBOARD, Color.rgb(36, 122, 255), true));
        types.add(new PickerItem("电脑键盘", "横屏 QWERTY 键盘布局", "可用",
                IconView.ICON_KEYBOARD, Color.rgb(36, 122, 255), true));
        showStyledChoiceDialog("选择键盘种类", null, types, -1, which -> {
            String type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
            if (which == 1) type = KEYBOARD_TYPE_COMPUTER;
            showKeyboardEditor(createNewKeyboardDraft(type), true);
        });
    }

    private void showKeyboardEditor(CustomKeyboardConfig draft, boolean isNew) {
        normalizeKeyboardConfig(draft);
        upsertKeyboardConfig(draft);
        mainHandler.removeCallbacks(restoreSetupOrientationRunnable);
        exitRemoteFullscreen();
        allowScreenSleep();
        applyOrientationForSetup();
        showingRemotePage = false;
        showingSettingsPage = true;
        showingKeyboardPage = false;
        connectionTitleView = null;
        connectionSubtitleView = null;
        bluetoothStateView = null;
        statusBtIcon = null;
        quickBtIcon = null;
        pairedListLayout = null;

        LinearLayout content = createPageContent();

        LinearLayout titleCard = createCardLayout(dp(24), 3f);
        titleCard.setOrientation(LinearLayout.HORIZONTAL);
        titleCard.setGravity(Gravity.CENTER_VERTICAL);
        titleCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout titleTextWrap = new LinearLayout(this);
        titleTextWrap.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(isNew ? "新增键盘" : "编辑键盘");
        setTextSizeDp(title, 14);
        title.setTextColor(Color.rgb(18, 28, 47));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView subtitle = new TextView(this);
        subtitle.setText(draft.name);
        setTextSizeDp(subtitle, 10.5f);
        subtitle.setTextColor(Color.rgb(123, 137, 165));
        subtitle.setPadding(0, dp(5), 0, 0);
        titleTextWrap.addView(title, new LinearLayout.LayoutParams(-1, -2));
        titleTextWrap.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        titleCard.addView(titleTextWrap, new LinearLayout.LayoutParams(0, -2, 1f));
        titleCard.addView(makeSmallTextButton("返回", v -> showSettingsPage()), new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = dp(12);
        content.addView(titleCard, titleParams);

        LinearLayout baseCard = createCardLayout(dp(24), 3f);
        baseCard.setOrientation(LinearLayout.VERTICAL);
        baseCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        addCardHeading(baseCard, "基础配置");
        LinearLayout baseList = makeInnerList();
        addConfigRow(baseList, "名称", draft.name, null, v -> showKeyboardNameDialog(draft, isNew), true);
        addConfigRow(baseList, "种类", keyboardTypeLabel(draft.type), null, null, false);
        baseCard.addView(baseList, makeInnerListParams());
        LinearLayout.LayoutParams baseParams = new LinearLayout.LayoutParams(-1, -2);
        baseParams.bottomMargin = dp(12);
        content.addView(baseCard, baseParams);

        if (isGestureKeyboard(draft)) {
            LinearLayout backgroundCard = createCardLayout(dp(24), 3f);
            backgroundCard.setOrientation(LinearLayout.VERTICAL);
            backgroundCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            addCardHeading(backgroundCard, "背景图案");
            LinearLayout backgroundList = makeInnerList();
            addConfigRow(backgroundList, "全屏背景", keyboardBackgroundLabel(draft), null,
                    v -> showKeyboardBackgroundPicker(draft, isNew), false);
            backgroundCard.addView(backgroundList, makeInnerListParams());
            LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(-1, -2);
            backgroundParams.bottomMargin = dp(12);
            content.addView(backgroundCard, backgroundParams);
        }

        LinearLayout vibrationCard = createCardLayout(dp(24), 3f);
        vibrationCard.setOrientation(LinearLayout.VERTICAL);
        vibrationCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        addCardHeading(vibrationCard, "交互震动");
        LinearLayout vibrationList = makeInnerList();
        addConfigRow(vibrationList, "震动反馈", draft.vibrationEnabled ? "手势触发时震动" : "手势触发时不震动",
                draft.vibrationEnabled ? "开启" : "关闭",
                v -> {
                    draft.vibrationEnabled = !draft.vibrationEnabled;
                    showKeyboardEditor(draft, isNew);
                }, true);
        addConfigRow(vibrationList, "震动级别", vibrationLevelLabel(draft.vibrationLevel), null,
                v -> {
                    draft.vibrationLevel = (draft.vibrationLevel + 1) % 3;
                    showKeyboardEditor(draft, isNew);
                }, false);
        vibrationCard.addView(vibrationList, makeInnerListParams());
        LinearLayout.LayoutParams vibrationParams = new LinearLayout.LayoutParams(-1, -2);
        vibrationParams.bottomMargin = dp(12);
        content.addView(vibrationCard, vibrationParams);

        if (isGestureKeyboard(draft)) {
            LinearLayout actionCard = createCardLayout(dp(24), 3f);
            actionCard.setOrientation(LinearLayout.VERTICAL);
            actionCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            addCardHeading(actionCard, "手势动作");

            TextView actionHint = new TextView(this);
            actionHint.setText("按需添加点按或滑动手势，再为每个手势选择要发送的信号");
            setTextSizeDp(actionHint, 10.5f);
            actionHint.setTextColor(Color.rgb(123, 137, 165));
            actionHint.setPadding(0, dp(5), 0, 0);
            actionCard.addView(actionHint, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout actionList = makeInnerList();
            if (draft.bindings.isEmpty()) {
                addPlainInfoRow(actionList, "还没有手势动作。请添加点按或滑动动作。");
            } else {
                for (int i = 0; i < draft.bindings.size(); i++) {
                    addGestureBindingRow(actionList, draft, draft.bindings.get(i), isNew, i < draft.bindings.size() - 1);
                }
            }
            actionCard.addView(actionList, makeInnerListParams());

            LinearLayout addGestureRow = new LinearLayout(this);
            addGestureRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams addTapParams = new LinearLayout.LayoutParams(0, -2, 1f);
            addTapParams.topMargin = dp(12);
            addTapParams.rightMargin = dp(6);
            addGestureRow.addView(makeSmallTextButton("新增点按", v -> showTapGesturePicker(draft, isNew)), addTapParams);
            LinearLayout.LayoutParams addMotionParams = new LinearLayout.LayoutParams(0, -2, 1f);
            addMotionParams.topMargin = dp(12);
            addMotionParams.leftMargin = dp(6);
            addGestureRow.addView(makeSmallTextButton("新增滑动", v -> showMotionGesturePicker(draft, isNew)), addMotionParams);
            actionCard.addView(addGestureRow, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
            actionParams.bottomMargin = dp(12);
            content.addView(actionCard, actionParams);
        } else {
            LinearLayout layoutCard = createCardLayout(dp(24), 3f);
            layoutCard.setOrientation(LinearLayout.VERTICAL);
            layoutCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            addCardHeading(layoutCard, "操作布局");
            LinearLayout layoutList = makeInnerList();
            addConfigRow(layoutList, "使用方向", remoteOrientationLabel(draft.type), null, null, true);
            addConfigRow(layoutList, "发送信号", fixedKeyboardSignalLabel(draft.type), null, null, false);
            layoutCard.addView(layoutList, makeInnerListParams());
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.bottomMargin = dp(12);
            content.addView(layoutCard, layoutParams);
        }

        TextView saveButton = makePrimaryTextButton("完成", v -> finishKeyboardEditor(draft));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, -2);
        saveParams.bottomMargin = dp(10);
        content.addView(saveButton, saveParams);

        if (!isNew) {
            TextView deleteButton = makeDangerTextButton("删除键盘", v -> confirmDeleteKeyboard(draft));
            content.addView(deleteButton, new LinearLayout.LayoutParams(-1, -2));
        }

        setContentView(buildScaffold(content, PAGE_SETTINGS));
    }

    private void showKeyboardNameDialog(CustomKeyboardConfig draft, boolean isNew) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(draft.name);
        input.setSelection(input.getText().length());
        input.setPadding(dp(16), dp(10), dp(16), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("键盘名称")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) draft.name = name;
                    showKeyboardEditor(draft, isNew);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showKeyboardBackgroundPicker(CustomKeyboardConfig draft, boolean isNew) {
        List<PickerItem> items = new ArrayList<>();
        boolean hasImage = hasCustomBackground(draft);
        items.add(new PickerItem("纯黑背景", "默认背景，适合手势盲操", hasImage ? null : "已选",
                IconView.ICON_DEVICE, Color.rgb(18, 28, 47), true));
        items.add(new PickerItem("从图片选择", hasImage ? "替换当前自定义图片" : "选择一张图片作为全屏背景",
                hasImage ? "已设置" : null, IconView.ICON_DEVICE, Color.rgb(36, 122, 255), true));
        showStyledChoiceDialog("背景图案", null, items, hasImage ? 1 : 0, which -> {
            if (which == 0) {
                draft.backgroundImageUri = "";
                showKeyboardEditor(draft, isNew);
            } else {
                openKeyboardBackgroundPicker(draft, isNew);
            }
        });
    }

    private void openKeyboardBackgroundPicker(CustomKeyboardConfig draft, boolean isNew) {
        pendingBackgroundDraft = draft;
        pendingBackgroundIsNew = isNew;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_PICK_KEYBOARD_BACKGROUND);
        } catch (Exception e) {
            pendingBackgroundDraft = null;
            Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleKeyboardBackgroundPicked(int resultCode, Intent data) {
        CustomKeyboardConfig draft = pendingBackgroundDraft;
        boolean isNew = pendingBackgroundIsNew;
        pendingBackgroundDraft = null;
        pendingBackgroundIsNew = false;

        if (draft == null) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
            }
            showKeyboardBackgroundCropper(draft, isNew, uri);
            return;
        }
        showKeyboardEditor(draft, isNew);
    }

    private void showKeyboardBackgroundCropper(CustomKeyboardConfig draft, boolean isNew, Uri sourceUri) {
        Bitmap sourceBitmap = loadBitmapForCrop(sourceUri);
        if (sourceBitmap == null) {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show();
            showKeyboardEditor(draft, isNew);
            return;
        }

        enterRemoteFullscreen();
        allowScreenSleep();
        applyOrientationForSetup();
        showingRemotePage = false;
        showingSettingsPage = true;
        showingKeyboardPage = false;
        connectionTitleView = null;
        connectionSubtitleView = null;
        bluetoothStateView = null;
        statusBtIcon = null;
        quickBtIcon = null;
        pairedListLayout = null;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout titleCard = createCardLayout(dp(24), 3f);
        titleCard.setOrientation(LinearLayout.HORIZONTAL);
        titleCard.setGravity(Gravity.CENTER_VERTICAL);
        titleCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout titleTextWrap = new LinearLayout(this);
        titleTextWrap.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("裁剪背景");
        setTextSizeDp(title, 14);
        title.setTextColor(Color.rgb(18, 28, 47));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView subtitle = new TextView(this);
        subtitle.setText("预览比例与全屏键盘一致");
        setTextSizeDp(subtitle, 10.5f);
        subtitle.setTextColor(Color.rgb(123, 137, 165));
        subtitle.setPadding(0, dp(5), 0, 0);
        titleTextWrap.addView(title, new LinearLayout.LayoutParams(-1, -2));
        titleTextWrap.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        titleCard.addView(titleTextWrap, new LinearLayout.LayoutParams(0, -2, 1f));
        titleCard.addView(makeSmallTextButton("取消", v -> {
            sourceBitmap.recycle();
            showKeyboardEditor(draft, isNew);
        }), new LinearLayout.LayoutParams(-2, -2));

        BackgroundCropView cropView = new BackgroundCropView(this, sourceBitmap);
        root.addView(cropView, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        titleParams.setMargins(dp(16), getStatusBarHeight() + dp(12), dp(16), 0);
        root.addView(titleCard, titleParams);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(dp(16), 0, dp(16), getNavigationBarHeight() + dp(16));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        cancelParams.rightMargin = dp(6);
        buttonRow.addView(makeSmallTextButton("取消", v -> {
            cropView.release();
            showKeyboardEditor(draft, isNew);
        }), cancelParams);
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(0, -2, 1f);
        useParams.leftMargin = dp(6);
        buttonRow.addView(makePrimaryTextButton("使用", v -> saveCroppedKeyboardBackground(draft, isNew, cropView)), useParams);

        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(buttonRow, buttonParams);

        setContentView(root);
    }

    private Bitmap loadBitmapForCrop(Uri uri) {
        if (uri == null) return null;
        try {
            int[] fullscreenSize = getFullscreenDisplaySize();
            int targetWidth = fullscreenSize[0];
            int targetHeight = fullscreenSize[1];

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sampleSize = 1;
            while ((bounds.outWidth / sampleSize) > targetWidth * 3
                    || (bounds.outHeight / sampleSize) > targetHeight * 3) {
                sampleSize *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveCroppedKeyboardBackground(CustomKeyboardConfig draft, boolean isNew, BackgroundCropView cropView) {
        Bitmap cropped = null;
        try {
            int targetWidth = Math.max(1, cropView.getWidth());
            int targetHeight = Math.max(1, cropView.getHeight());
            if (targetWidth <= 1 || targetHeight <= 1) {
                int[] fullscreenSize = getFullscreenDisplaySize();
                targetWidth = fullscreenSize[0];
                targetHeight = fullscreenSize[1];
            }
            cropped = cropView.createCroppedBitmap(targetWidth, targetHeight);
            if (cropped == null) {
                Toast.makeText(this, "裁剪失败", Toast.LENGTH_SHORT).show();
                return;
            }

            File dir = new File(getFilesDir(), "keyboard_backgrounds");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "背景保存失败", Toast.LENGTH_SHORT).show();
                return;
            }
            File output = new File(dir, draft.id + "_background.jpg");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, stream);
            }
            draft.backgroundImageUri = Uri.fromFile(output).toString();
            upsertKeyboardConfig(draft);
            cropView.release();
            Toast.makeText(this, "背景图案已设置", Toast.LENGTH_SHORT).show();
            showKeyboardEditor(draft, isNew);
        } catch (Exception e) {
            Toast.makeText(this, "背景保存失败", Toast.LENGTH_SHORT).show();
        } finally {
            if (cropped != null && !cropped.isRecycled()) cropped.recycle();
        }
    }

    private void showTapGesturePicker(CustomKeyboardConfig draft, boolean isNew) {
        List<PickerItem> items = new ArrayList<>();
        List<int[]> values = new ArrayList<>();
        for (int fingers = 1; fingers <= 3; fingers++) {
            for (int taps = 1; taps <= 3; taps++) {
                items.add(new PickerItem(fingerLabel(fingers) + tapCountLabel(taps), "点按动作", null,
                        IconView.ICON_LINK, Color.rgb(36, 122, 255), true));
                values.add(new int[]{fingers, taps});
            }
        }
        showStyledChoiceDialog("选择点按动作", null, items, -1, which -> {
            int[] value = values.get(which);
            showTapAreaPicker(draft, isNew, value[0], value[1]);
        });
    }

    private void showTapAreaPicker(CustomKeyboardConfig draft, boolean isNew, int fingers, int taps) {
        List<PickerItem> items = new ArrayList<>();
        for (int i = 0; i < TAP_AREAS.length; i++) {
            String area = TAP_AREAS[i];
            boolean conflict = hasTapAreaConflict(draft, fingers, taps, area, null);
            items.add(new PickerItem(tapAreaLabel(area), conflict ? "与已有区域重叠" : "触发区域",
                    conflict ? "冲突" : null, IconView.ICON_DEVICE, Color.rgb(36, 122, 255), !conflict));
        }
        showStyledChoiceDialog(fingerLabel(fingers) + tapCountLabel(taps) + "的位置", null, items, -1, which -> {
            String area = TAP_AREAS[which];
            GestureBinding binding = new GestureBinding();
            binding.kind = GESTURE_KIND_TAP;
            binding.fingers = fingers;
            binding.tapCount = taps;
            binding.area = area;
            binding.motion = "";
            binding.actionId = "none";
            showActionPicker(draft, binding, isNew, true);
        });
    }

    private void showMotionGesturePicker(CustomKeyboardConfig draft, boolean isNew) {
        List<PickerItem> items = new ArrayList<>();
        List<GestureBinding> candidates = new ArrayList<>();
        for (int fingers = 1; fingers <= 3; fingers++) {
            for (String motion : MOTION_GESTURES) {
                if (fingers > 1 && (MOTION_CIRCLE_CLOCKWISE.equals(motion) || MOTION_CIRCLE_COUNTERCLOCKWISE.equals(motion))) {
                    continue;
                }
                GestureBinding binding = new GestureBinding();
                binding.kind = GESTURE_KIND_MOTION;
                binding.fingers = fingers;
                binding.tapCount = 0;
                binding.area = TAP_AREA_FULL;
                binding.motion = motion;
                binding.actionId = "none";
                boolean configured = findBindingByKey(draft, binding.key()) != null;
                items.add(new PickerItem(gestureTitle(binding), configured ? "已经配置过" : "全屏触发",
                        configured ? "已配置" : null, IconView.ICON_LINK, Color.rgb(36, 122, 255), !configured));
                candidates.add(binding);
            }
        }
        showStyledChoiceDialog("选择滑动动作", null, items, -1, which ->
                showActionPicker(draft, candidates.get(which), isNew, true));
    }

    private void showActionPicker(CustomKeyboardConfig draft, GestureBinding binding, boolean isNew, boolean appendNew) {
        List<HidAction.ActionDefinition> actions = HidAction.all();
        List<PickerItem> items = new ArrayList<>();
        int selectedIndex = 0;
        String selectedActionId = binding.actionId == null ? "none" : binding.actionId;

        for (int i = 0; i < actions.size(); i++) {
            HidAction.ActionDefinition action = actions.get(i);
            items.add(new PickerItem(action.label, action.category, null,
                    pickerIconForAction(action), pickerColorForAction(action), true));
            if (action.id.equals(selectedActionId)) selectedIndex = i;
        }

        showStyledChoiceDialog(gestureTitle(binding), "选择发送信号", items, selectedIndex, which -> {
            binding.actionId = actions.get(which).id;
            if (appendNew) {
                draft.bindings.add(binding);
            }
            showKeyboardEditor(draft, isNew);
        });
    }

    private void showStyledChoiceDialog(
            String title,
            String subtitle,
            List<PickerItem> items,
            int selectedIndex,
            PickerSelectionHandler selectionHandler
    ) {
        final AlertDialog[] dialogRef = new AlertDialog[1];

        LinearLayout dialogCard = createCardLayout(dp(24), 3f);
        dialogCard.setOrientation(LinearLayout.VERTICAL);
        dialogCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        dialogCard.setClipChildren(true);
        dialogCard.setClipToPadding(true);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        setTextSizeDp(titleView, 14);
        titleView.setTextColor(Color.rgb(18, 28, 47));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dialogCard.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            setTextSizeDp(subtitleView, 10.5f);
            subtitleView.setTextColor(Color.rgb(123, 137, 165));
            subtitleView.setPadding(0, dp(5), 0, 0);
            dialogCard.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout list = makeInnerList();
        list.setBackground(null);
        list.setPadding(0, dp(2), 0, dp(2));
        for (int i = 0; i < items.size(); i++) {
            addPickerRow(list, items.get(i), i, i == selectedIndex, selectionHandler, dialogRef);
            if (i < items.size() - 1) {
                list.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }

        FrameLayout listViewport = new FrameLayout(this);
        listViewport.setBackground(roundedRect(Color.WHITE, dp(18), Color.rgb(234, 239, 248), 1));
        listViewport.setClipChildren(true);
        listViewport.setClipToPadding(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            listViewport.setClipToOutline(true);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipChildren(true);
        scrollView.setClipToPadding(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.addView(list, new ScrollView.LayoutParams(-1, -2));

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int estimatedListHeight = items.size() * dp(58) + Math.max(0, items.size() - 1) * dp(1) + dp(4);
        int maxListHeight = Math.min(dp(390), Math.max(dp(220), screenHeight - dp(320)));
        boolean needsScroll = estimatedListHeight > maxListHeight;
        listViewport.addView(scrollView, new FrameLayout.LayoutParams(-1, needsScroll ? -1 : -2));

        LinearLayout.LayoutParams viewportParams = new LinearLayout.LayoutParams(-1, needsScroll ? maxListHeight : -2);
        viewportParams.topMargin = dp(12);
        dialogCard.addView(listViewport, viewportParams);

        TextView cancelButton = makeSmallTextButton("取消", v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, -2);
        cancelParams.topMargin = dp(12);
        dialogCard.addView(cancelButton, cancelParams);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialogRef[0] = dialog;
        dialog.setView(dialogCard, 0, 0, 0, 0);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            WindowManager.LayoutParams attributes = window.getAttributes();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            attributes.width = Math.min(screenWidth - dp(36), dp(440));
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(attributes);
        }
    }

    private void addPickerRow(
            LinearLayout parent,
            PickerItem item,
            int index,
            boolean selected,
            PickerSelectionHandler selectionHandler,
            AlertDialog[] dialogRef
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        if (item.enabled) {
            row.setOnClickListener(v -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                selectionHandler.onSelect(index);
            });
        } else {
            row.setAlpha(0.52f);
        }

        int iconColor = item.enabled ? item.iconColor : Color.rgb(150, 162, 184);
        row.addView(makeIconBubble(34, Color.rgb(245, 247, 252), item.iconType, iconColor, 1.6f));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(10);

        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        setTextSizeDp(titleView, 12.5f);
        titleView.setTextColor(selected ? Color.rgb(36, 122, 255) : Color.rgb(20, 29, 48));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setSingleLine(false);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(item.subtitle == null ? "" : item.subtitle);
        setTextSizeDp(subtitleView, 10);
        subtitleView.setTextColor(Color.rgb(123, 137, 165));
        subtitleView.setPadding(0, dp(5), 0, 0);
        subtitleView.setSingleLine(false);

        textWrap.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap, textParams);

        String chipText = selected ? "已选" : item.chipText;
        if (chipText != null) {
            row.addView(makeStatusChip(chipText, selected || "可用".equals(chipText)), new LinearLayout.LayoutParams(-2, -2));
        }

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private int pickerIconForAction(HidAction.ActionDefinition action) {
        if (action == null) return IconView.ICON_HEXAGON;
        if ("系统".equals(action.category)) return IconView.ICON_DEVICE;
        switch (action.outputType) {
            case KEYBOARD:
                return IconView.ICON_KEYBOARD;
            case CONSUMER:
                return IconView.ICON_PLAY;
            case GAMEPAD:
                return IconView.ICON_HEXAGON;
            case NONE:
            default:
                return IconView.ICON_CHEVRON;
        }
    }

    private int pickerColorForAction(HidAction.ActionDefinition action) {
        if (action == null) return Color.rgb(111, 126, 154);
        if ("系统".equals(action.category)) return Color.rgb(36, 122, 255);
        switch (action.outputType) {
            case KEYBOARD:
                return Color.rgb(36, 122, 255);
            case CONSUMER:
                return Color.rgb(22, 155, 114);
            case GAMEPAD:
                return Color.rgb(126, 101, 216);
            case NONE:
            default:
                return Color.rgb(111, 126, 154);
        }
    }

    private void finishKeyboardEditor(CustomKeyboardConfig draft) {
        draft.name = draft.name == null ? "" : draft.name.trim();
        if (draft.name.isEmpty()) {
            Toast.makeText(this, "请先填写键盘名称", Toast.LENGTH_SHORT).show();
            return;
        }
        upsertKeyboardConfig(draft);
        showSettingsPage();
    }

    private void confirmDeleteKeyboard(CustomKeyboardConfig draft) {
        new AlertDialog.Builder(this)
                .setTitle("删除键盘")
                .setMessage("删除后不会影响蓝牙配对和其他键盘配置。")
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteKeyboardConfig(draft.id);
                    Toast.makeText(this, "键盘已删除", Toast.LENGTH_SHORT).show();
                    showSettingsPage();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRemotePage(BluetoothDevice device, CustomKeyboardConfig keyboardConfig) {
        keepScreenAwake();
        showingRemotePage = true;
        showingSettingsPage = false;
        showingKeyboardPage = false;
        activeKeyboardConfig = keyboardConfig == null ? createDefaultKeyboardFromLegacy() : keyboardConfig.copy();
        applyOrientationForRemote(activeKeyboardConfig.type);
        enterRemoteFullscreen();
        connectionTitleView = null;
        connectionSubtitleView = null;
        bluetoothStateView = null;
        statusBtIcon = null;
        quickBtIcon = null;
        pairedListLayout = null;

        if (isComputerKeyboard(activeKeyboardConfig)) {
            ComputerKeyboardView computerKeyboardView = new ComputerKeyboardView(this);
            computerKeyboardView.setStateListener(this::sendKeyboardState);
            computerKeyboardView.setDisconnectListener(this::exitRemotePage);
            computerKeyboardView.setVibrationEnabled(activeKeyboardConfig.vibrationEnabled);
            computerKeyboardView.setVibrationLevel(activeKeyboardConfig.vibrationLevel);
            setContentView(computerKeyboardView);
        } else if (isControllerKeyboard(activeKeyboardConfig)) {
            GamepadView gamepadView = new GamepadView(this, activeKeyboardConfig.type);
            gamepadView.setStateListener(this::sendGamepadState);
            gamepadView.setDisconnectListener(this::exitRemotePage);
            gamepadView.setVibrationEnabled(activeKeyboardConfig.vibrationEnabled);
            gamepadView.setVibrationLevel(activeKeyboardConfig.vibrationLevel);
            gamepadView.setStickClickMode(activeKeyboardConfig.stickClickMode);
            gamepadView.setStickClickModeListener(mode -> {
                if (activeKeyboardConfig == null) return;
                activeKeyboardConfig.stickClickMode = mode;
                upsertKeyboardConfig(activeKeyboardConfig);
            });
            gamepadView.setConnectedDeviceLabel(safeDeviceName(device));
            gamepadView.refreshSystemStatus();
            setContentView(gamepadView);
        } else {
            KeyPadView keyPadView = new KeyPadView(this);
            keyPadView.setTriggerListener(this::sendActionForTrigger);
            keyPadView.setDisconnectListener(this::exitRemotePage);
            keyPadView.setVibrationEnabled(activeKeyboardConfig.vibrationEnabled);
            keyPadView.setVibrationLevel(activeKeyboardConfig.vibrationLevel);
            keyPadView.setGestureBindings(activeKeyboardConfig.bindings);
            keyPadView.setBackgroundImageUri(activeKeyboardConfig.backgroundImageUri);
            setContentView(keyPadView);
        }
    }

    private View buildScaffold(LinearLayout content, int selectedPage) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 249, 255));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipChildren(false);
        scrollView.setClipToPadding(false);
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        View bottomNav = buildBottomNavigation(selectedPage);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, -2);
        navParams.setMargins(dp(16), 0, dp(16), dp(12) + getNavigationBarHeight());
        root.addView(bottomNav, navParams);

        return root;
    }

    private View buildScaffoldNoScroll(LinearLayout content, int selectedPage) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 249, 255));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        View bottomNav = buildBottomNavigation(selectedPage);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, -2);
        navParams.setMargins(dp(16), 0, dp(16), dp(12) + getNavigationBarHeight());
        root.addView(bottomNav, navParams);

        return root;
    }

    private LinearLayout createPageContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), getStatusBarHeight() + dp(16), dp(20), dp(20));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        return content;
    }

    private LinearLayout createCardLayout(int radiusPx, float elevationDp) {
        LinearLayout card = new ShadowCardLayout(this);
        applySurfaceStyle(card, radiusPx, surfaceFillColor(), surfaceStrokeColor(), elevationDp + 2f);
        return card;
    }

    private void applySurfaceStyle(View view, int radiusPx, int fillColor, int strokeColor, float elevationDp) {
        if (view instanceof ShadowCardLayout) {
            ((ShadowCardLayout) view).applyCardStyle(radiusPx, fillColor, strokeColor, elevationDp);
        } else {
            view.setBackground(roundedRect(fillColor, radiusPx, strokeColor, 1));
        }
    }

    private int surfaceFillColor() {
        return Color.argb(246, 255, 255, 255);
    }

    private int surfaceStrokeColor() {
        return Color.argb(20, 24, 38, 66);
    }

    private View makeQuickStatusCard(String title, int iconType, int iconColor, View.OnClickListener listener) {
        LinearLayout card = createCardLayout(dp(20), 2f);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setMinimumHeight(dp(80));
        card.setOnClickListener(listener);

        card.addView(makeIconBubble(36, Color.rgb(245, 247, 252), iconType, iconColor, 2.0f));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(10);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        setTextSizeDp(titleView, 12.5f);
        titleView.setTextColor(Color.rgb(21, 30, 48));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setSingleLine(true);

        TextView subtitleView = new TextView(this);
        subtitleView.setText("--");
        setTextSizeDp(subtitleView, 10);
        subtitleView.setTextColor(Color.rgb(123, 137, 165));
        subtitleView.setPadding(0, dp(4), 0, 0);
        subtitleView.setSingleLine(true);

        textWrap.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        card.addView(textWrap, textParams);

        IconView chevron = new IconView(this, IconView.ICON_CHEVRON, Color.rgb(120, 134, 160), 1.5f);
        card.addView(chevron, new LinearLayout.LayoutParams(dp(12), dp(12)));
        card.setTag(subtitleView);
        return card;
    }

    private View makeIconButton(int iconType, View.OnClickListener listener) {
        FrameLayout button = new FrameLayout(this);
        button.setBackground(roundedRect(Color.rgb(248, 250, 255), dp(14), Color.rgb(233, 239, 248), 1));
        button.setOnClickListener(listener);
        button.addView(makeIconBubble(30, Color.TRANSPARENT, iconType, Color.rgb(111, 126, 154), 1.7f),
                new FrameLayout.LayoutParams(-1, -1));
        return button;
    }

    private FrameLayout makeIconBubble(int sizeDp, int fillColor, int iconType, int iconColor, float strokeDp) {
        FrameLayout bubble = new FrameLayout(this);
        bubble.setBackground(roundedRect(fillColor, dp(sizeDp / 2f), fillColor, 0));
        IconView iconView = new IconView(this, iconType, iconColor, strokeDp);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                sizeDp >= 72 ? dp(30) : dp(20),
                sizeDp >= 72 ? dp(30) : dp(20),
                Gravity.CENTER
        );
        bubble.addView(iconView, iconParams);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        bubble.setLayoutParams(params);
        return bubble;
    }

    private View buildBottomNavigation(int selectedPage) {
        LinearLayout nav = createCardLayout(dp(24), 4f);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));

        nav.addView(makeTabItem("连接", IconView.ICON_LINK, selectedPage == PAGE_CONNECTION, v -> showSetupPage()),
                new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(makeTabItem("键盘", IconView.ICON_KEYBOARD, selectedPage == PAGE_KEYBOARDS, v -> showKeyboardsPage()),
                new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(makeTabItem("设置", IconView.ICON_HEXAGON, selectedPage == PAGE_SETTINGS, v -> showSettingsPage()),
                new LinearLayout.LayoutParams(0, -2, 1f));
        return nav;
    }

    private View makeTabItem(String label, int iconType, boolean selected, View.OnClickListener listener) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);
        tab.setPadding(dp(6), dp(4), dp(6), 0);
        tab.setOnClickListener(listener);

        int activeColor = Color.rgb(36, 122, 255);
        int inactiveColor = Color.rgb(116, 129, 156);
        int tint = selected ? activeColor : inactiveColor;

        IconView iconView = new IconView(this, iconType, tint, 1.8f);
        tab.addView(iconView, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView textView = new TextView(this);
        textView.setText(label);
        setTextSizeDp(textView, 10.5f);
        textView.setTextColor(tint);
        textView.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        textView.setPadding(0, dp(5), 0, 0);
        tab.addView(textView, new LinearLayout.LayoutParams(-2, -2));

        View indicator = new View(this);
        indicator.setBackground(roundedRect(activeColor, dp(3), activeColor, 0));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(22), dp(3));
        indicatorParams.topMargin = dp(7);
        indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        tab.addView(indicator, indicatorParams);

        return tab;
    }

    private TextView makeStatusChip(String text, boolean active) {
        TextView chip = new TextView(this);
        chip.setText(text);
        setTextSizeDp(chip, 9.5f);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setTextColor(active ? Color.rgb(36, 122, 255) : Color.rgb(117, 129, 155));
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        chip.setBackground(roundedRect(
                active ? Color.rgb(238, 246, 255) : Color.rgb(245, 247, 252),
                dp(12),
                active ? Color.rgb(211, 228, 255) : Color.rgb(232, 237, 245),
                1
        ));
        return chip;
    }

    private TextView makeSmallTextButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        setTextSizeDp(button, 11);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(Color.rgb(36, 122, 255));
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
        button.setBackground(roundedRect(Color.rgb(238, 246, 255), dp(14), Color.rgb(211, 228, 255), 1));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView makePrimaryTextButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        setTextSizeDp(button, 12.5f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(roundedRect(Color.rgb(36, 122, 255), dp(18), Color.rgb(36, 122, 255), 0));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView makeDangerTextButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        setTextSizeDp(button, 12.5f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(Color.rgb(220, 60, 60));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(roundedRect(Color.rgb(255, 245, 245), dp(18), Color.rgb(255, 220, 220), 1));
        button.setOnClickListener(listener);
        return button;
    }

    private void addCardHeading(LinearLayout card, String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        setTextSizeDp(heading, 13);
        heading.setTextColor(Color.rgb(18, 28, 47));
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout makeInnerList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(roundedRect(Color.WHITE, dp(18), Color.rgb(234, 239, 248), 1));
        return list;
    }

    private LinearLayout.LayoutParams makeInnerListParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        return params;
    }

    private void addConfigRow(
            LinearLayout parent,
            String title,
            String subtitle,
            String chipText,
            View.OnClickListener listener,
            boolean addSeparator
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (listener != null) row.setOnClickListener(listener);

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        setTextSizeDp(titleView, 12.5f);
        titleView.setTextColor(Color.rgb(20, 29, 48));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle == null ? "" : subtitle);
        setTextSizeDp(subtitleView, 10);
        subtitleView.setTextColor(Color.rgb(123, 137, 165));
        subtitleView.setPadding(0, dp(5), 0, 0);
        subtitleView.setSingleLine(false);

        textWrap.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap);

        if (chipText != null) {
            row.addView(makeStatusChip(chipText, "开启".equals(chipText) || "可用".equals(chipText)), new LinearLayout.LayoutParams(-2, -2));
        } else if (listener != null) {
            IconView chevron = new IconView(this, IconView.ICON_CHEVRON, Color.rgb(120, 134, 160), 1.5f);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(12), dp(12)));
        }

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (addSeparator) parent.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void addPlainInfoRow(LinearLayout parent, String text) {
        TextView row = new TextView(this);
        row.setText(text);
        setTextSizeDp(row, 12);
        row.setTextColor(Color.rgb(123, 137, 165));
        row.setLineSpacing(0f, 1.12f);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addKeyboardRunRow(LinearLayout parent, CustomKeyboardConfig config, BluetoothDevice connected, boolean addSeparator) {
        LinearLayout row = makeKeyboardSummaryRow(config);
        row.setOnClickListener(v -> {
            if (connected == null) {
                Toast.makeText(this, "请先在连接页连接一个已配对设备", Toast.LENGTH_SHORT).show();
                return;
            }
            launchKeyboardRun(connected, config);
        });
        row.addView(makeStatusChip(connected == null ? "未连接" : "运行", connected != null), new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (addSeparator) parent.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void launchKeyboardRun(BluetoothDevice device, CustomKeyboardConfig config) {
        if (hidService == null) {
            updateStatus("HID 后台服务还没准备好。请保持蓝牙打开并等待几秒。");
            startAndBindHidService();
            return;
        }
        CustomKeyboardConfig targetConfig = config == null ? createDefaultKeyboardFromLegacy() : config.copy();
        if (!isControllerKeyboard(targetConfig)) {
            awaitingModeReconnect = false;
            pendingRemoteDevice = null;
            pendingRemoteConfig = null;
            showRemotePage(device, targetConfig);
            return;
        }
        awaitingModeReconnect = true;
        pendingRemoteDevice = device;
        pendingRemoteConfig = targetConfig;
        updateStatus("正在切换为标准手柄模式并重新连接...");
        if (!hidService.switchHidModeAndReconnect(device, true)) {
            awaitingModeReconnect = false;
            pendingRemoteDevice = null;
            pendingRemoteConfig = null;
        }
    }

    private void addKeyboardEditRow(LinearLayout parent, CustomKeyboardConfig config, boolean addSeparator) {
        LinearLayout row = makeKeyboardSummaryRow(config);
        row.setOnClickListener(v -> showKeyboardEditor(config.copy(), false));
        IconView chevron = new IconView(this, IconView.ICON_CHEVRON, Color.rgb(120, 134, 160), 1.5f);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(12), dp(12)));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (addSeparator) parent.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private LinearLayout makeKeyboardSummaryRow(CustomKeyboardConfig config) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        row.addView(makeIconBubble(38, Color.rgb(245, 247, 252), IconView.ICON_KEYBOARD, Color.rgb(36, 122, 255), 1.7f));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(10);

        TextView nameView = new TextView(this);
        nameView.setText(config.name);
        setTextSizeDp(nameView, 12.5f);
        nameView.setTextColor(Color.rgb(20, 29, 48));
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nameView.setSingleLine(true);

        TextView summaryView = new TextView(this);
        summaryView.setText(keyboardSummary(config));
        setTextSizeDp(summaryView, 10);
        summaryView.setTextColor(Color.rgb(123, 137, 165));
        summaryView.setPadding(0, dp(5), 0, 0);
        summaryView.setSingleLine(true);

        textWrap.addView(nameView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(summaryView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap, textParams);
        return row;
    }

    private void addKeyboardTypeRow(LinearLayout parent, String title, String chip, boolean addSeparator) {
        addConfigRow(parent, title, "可用".equals(chip) ? "当前可创建和运行" : "后续扩展类型", chip, null, addSeparator);
    }

    private void addGestureBindingRow(
            LinearLayout parent,
            CustomKeyboardConfig draft,
            GestureBinding binding,
            boolean isNew,
            boolean addSeparator
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setOnClickListener(v -> showActionPicker(draft, binding, isNew, false));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);

        TextView titleView = new TextView(this);
        titleView.setText(gestureTitle(binding));
        setTextSizeDp(titleView, 12.5f);
        titleView.setTextColor(Color.rgb(20, 29, 48));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(HidAction.labelForId(binding.actionId));
        setTextSizeDp(subtitleView, 10);
        subtitleView.setTextColor(Color.rgb(123, 137, 165));
        subtitleView.setPadding(0, dp(5), 0, 0);

        textWrap.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap, textParams);

        TextView deleteButton = makeSmallTextButton("删除", v -> {
            draft.bindings.remove(binding);
            showKeyboardEditor(draft, isNew);
        });
        deleteButton.setTextColor(Color.rgb(220, 60, 60));
        deleteButton.setBackground(roundedRect(Color.rgb(255, 245, 245), dp(14), Color.rgb(255, 220, 220), 1));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-2, -2);
        deleteParams.leftMargin = dp(8);
        row.addView(deleteButton, deleteParams);

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (addSeparator) parent.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private HidAction.ActionDefinition getActionForTrigger(String gestureKey) {
        GestureBinding binding = findBindingByKey(activeKeyboardConfig, gestureKey);
        return binding == null ? HidAction.byId("none") : HidAction.byId(binding.actionId);
    }

    private String getDefaultActionId(String triggerId) {
        switch (triggerId) {
            case TRIGGER_LANDSCAPE_TOP:
            case TRIGGER_PORTRAIT_LEFT:
            case TRIGGER_FULLSCREEN_DOUBLE:
                return "kb_volume_up";
            case TRIGGER_LANDSCAPE_BOTTOM:
            case TRIGGER_PORTRAIT_RIGHT:
            case TRIGGER_FULLSCREEN_SINGLE:
                return "kb_volume_down";
            case TRIGGER_SWIPE_UP:
                return "kb_volume_down";
            case TRIGGER_SWIPE_DOWN:
                return "kb_volume_up";
            case TRIGGER_SWIPE_LEFT:
                return "kb_volume_down";
            case TRIGGER_SWIPE_RIGHT:
                return "kb_volume_up";
            default:
                return "none";
        }
    }

    private void ensureKeyboardConfigsInitialized() {
        if (preferences == null || preferences.contains(PREF_KEYBOARDS_JSON)) return;
        List<CustomKeyboardConfig> configs = new ArrayList<>();
        configs.add(hasLegacyKeyboardPreferences()
                ? createDefaultKeyboardFromLegacy()
                : createReaderPagingKeyboard());
        saveKeyboardConfigs(configs);
        preferences.edit().putInt(PREF_NEXT_KEYBOARD_ID, 2).apply();
    }

    private boolean hasLegacyKeyboardPreferences() {
        if (preferences == null) return false;
        if (preferences.contains(PREF_CONTROL_MODE) || preferences.contains(PREF_VIBRATION_ENABLED)) {
            return true;
        }
        for (String triggerId : GESTURE_TRIGGER_IDS) {
            if (preferences.contains(PREF_ACTION_PREFIX + triggerId)) return true;
        }
        return false;
    }

    private List<CustomKeyboardConfig> loadKeyboardConfigs() {
        List<CustomKeyboardConfig> configs = new ArrayList<>();
        if (preferences == null) {
            configs.add(createDefaultKeyboardFromLegacy());
            return configs;
        }

        String raw = preferences.getString(PREF_KEYBOARDS_JSON, "");
        if (raw == null || raw.trim().isEmpty()) {
            configs.add(createDefaultKeyboardFromLegacy());
            saveKeyboardConfigs(configs);
            return configs;
        }

        boolean parseFailed = false;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                CustomKeyboardConfig config = parseKeyboardConfig(array.getJSONObject(i));
                if (config != null) configs.add(config);
            }
        } catch (Exception ignored) {
            configs.clear();
            parseFailed = true;
        }

        if (parseFailed) {
            configs.add(createDefaultKeyboardFromLegacy());
            saveKeyboardConfigs(configs);
        }
        return configs;
    }

    private void saveKeyboardConfigs(List<CustomKeyboardConfig> configs) {
        if (preferences == null) return;
        JSONArray array = new JSONArray();
        for (CustomKeyboardConfig config : configs) {
            array.put(toJson(config));
        }
        preferences.edit().putString(PREF_KEYBOARDS_JSON, array.toString()).apply();
    }

    private CustomKeyboardConfig parseKeyboardConfig(JSONObject object) {
        if (object == null) return null;
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = object.optString("id", "");
        config.name = object.optString("name", "全屏手势键盘");
        config.type = object.optString("type", KEYBOARD_TYPE_FULLSCREEN_GESTURE);
        config.controlMode = object.optString("controlMode", CONTROL_MODE_FULLSCREEN);
        config.stickClickMode = object.optString("stickClickMode", STICK_CLICK_MODE_TAP);
        config.backgroundImageUri = object.optString("backgroundImageUri", "");
        config.vibrationEnabled = object.optBoolean("vibrationEnabled", true);
        config.vibrationLevel = object.optInt("vibrationLevel", VIBRATION_MEDIUM);

        JSONArray bindingsArray = object.optJSONArray("bindings");
        if (bindingsArray != null) {
            for (int i = 0; i < bindingsArray.length(); i++) {
                GestureBinding binding = parseGestureBinding(bindingsArray.optJSONObject(i));
                if (binding != null) config.bindings.add(binding);
            }
        } else {
            JSONObject actionsObject = object.optJSONObject("actions");
            if (actionsObject != null) {
                addLegacyBindings(config, actionsObject);
            }
        }
        normalizeKeyboardConfig(config);
        return config;
    }

    private JSONObject toJson(CustomKeyboardConfig config) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", config.id);
            object.put("name", config.name);
            object.put("type", config.type);
            object.put("controlMode", config.controlMode);
            object.put("stickClickMode", config.stickClickMode);
            object.put("backgroundImageUri", config.backgroundImageUri == null ? "" : config.backgroundImageUri);
            object.put("vibrationEnabled", config.vibrationEnabled);
            object.put("vibrationLevel", config.vibrationLevel);
            JSONArray bindingsArray = new JSONArray();
            for (GestureBinding binding : config.bindings) {
                bindingsArray.put(toJson(binding));
            }
            object.put("bindings", bindingsArray);
        } catch (Exception ignored) {
        }
        return object;
    }

    private GestureBinding parseGestureBinding(JSONObject object) {
        if (object == null) return null;
        GestureBinding binding = new GestureBinding();
        binding.kind = object.optString("kind", GESTURE_KIND_TAP);
        binding.fingers = object.optInt("fingers", 1);
        binding.tapCount = object.optInt("tapCount", 1);
        binding.area = object.optString("area", TAP_AREA_FULL);
        binding.motion = object.optString("motion", "");
        binding.actionId = object.optString("actionId", "none");
        normalizeGestureBinding(binding);
        return binding;
    }

    private JSONObject toJson(GestureBinding binding) {
        JSONObject object = new JSONObject();
        try {
            object.put("kind", binding.kind);
            object.put("fingers", binding.fingers);
            object.put("tapCount", binding.tapCount);
            object.put("area", binding.area);
            object.put("motion", binding.motion);
            object.put("actionId", binding.actionId);
        } catch (Exception ignored) {
        }
        return object;
    }

    private void normalizeKeyboardConfig(CustomKeyboardConfig config) {
        if (config.id == null || config.id.trim().isEmpty()) config.id = allocateKeyboardId();
        if (config.name == null || config.name.trim().isEmpty()) config.name = "全屏手势键盘";
        if (!isKnownKeyboardType(config.type)) config.type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
        if (!CONTROL_MODE_LANDSCAPE.equals(config.controlMode)
                && !CONTROL_MODE_PORTRAIT.equals(config.controlMode)
                && !CONTROL_MODE_FULLSCREEN.equals(config.controlMode)) {
            config.controlMode = CONTROL_MODE_FULLSCREEN;
        }
        if (!STICK_CLICK_MODE_TAP.equals(config.stickClickMode)
                && !STICK_CLICK_MODE_BUTTON.equals(config.stickClickMode)) {
            config.stickClickMode = STICK_CLICK_MODE_TAP;
        }
        if (config.backgroundImageUri == null) config.backgroundImageUri = "";
        if (config.vibrationLevel < VIBRATION_LIGHT || config.vibrationLevel > VIBRATION_STRONG) {
            config.vibrationLevel = VIBRATION_MEDIUM;
        }
        for (GestureBinding binding : config.bindings) {
            normalizeGestureBinding(binding);
        }
    }

    private void normalizeGestureBinding(GestureBinding binding) {
        if (!GESTURE_KIND_MOTION.equals(binding.kind)) binding.kind = GESTURE_KIND_TAP;
        if (binding.fingers < 1 || binding.fingers > 3) binding.fingers = 1;
        if (GESTURE_KIND_TAP.equals(binding.kind)) {
            if (binding.tapCount < 1 || binding.tapCount > 3) binding.tapCount = 1;
            if (!isKnownTapArea(binding.area)) binding.area = TAP_AREA_FULL;
            binding.motion = "";
        } else {
            binding.tapCount = 0;
            binding.area = TAP_AREA_FULL;
            if (!isKnownMotion(binding.motion)) binding.motion = MOTION_SWIPE_UP;
            if (binding.fingers > 1 && (MOTION_CIRCLE_CLOCKWISE.equals(binding.motion) || MOTION_CIRCLE_COUNTERCLOCKWISE.equals(binding.motion))) {
                binding.fingers = 1;
            }
        }
        if (binding.actionId == null || binding.actionId.trim().isEmpty()) binding.actionId = "none";
    }

    private void addLegacyBindings(CustomKeyboardConfig config, JSONObject actionsObject) {
        if (config == null || actionsObject == null) return;
        if (CONTROL_MODE_LANDSCAPE.equals(config.controlMode)) {
            addLegacyTapBinding(config, TRIGGER_LANDSCAPE_TOP, TAP_AREA_TOP, actionsObject);
            addLegacyTapBinding(config, TRIGGER_LANDSCAPE_BOTTOM, TAP_AREA_BOTTOM, actionsObject);
        } else if (CONTROL_MODE_PORTRAIT.equals(config.controlMode)) {
            addLegacyTapBinding(config, TRIGGER_PORTRAIT_LEFT, TAP_AREA_LEFT, actionsObject);
            addLegacyTapBinding(config, TRIGGER_PORTRAIT_RIGHT, TAP_AREA_RIGHT, actionsObject);
        } else {
            addLegacyTapBinding(config, TRIGGER_FULLSCREEN_SINGLE, TAP_AREA_FULL, actionsObject);
            addLegacyTapBinding(config, TRIGGER_FULLSCREEN_DOUBLE, TAP_AREA_FULL, actionsObject);
        }
        addLegacyMotionBinding(config, TRIGGER_SWIPE_UP, MOTION_SWIPE_UP, actionsObject);
        addLegacyMotionBinding(config, TRIGGER_SWIPE_DOWN, MOTION_SWIPE_DOWN, actionsObject);
        addLegacyMotionBinding(config, TRIGGER_SWIPE_LEFT, MOTION_SWIPE_LEFT, actionsObject);
        addLegacyMotionBinding(config, TRIGGER_SWIPE_RIGHT, MOTION_SWIPE_RIGHT, actionsObject);
    }

    private void addLegacyTapBinding(CustomKeyboardConfig config, String triggerId, String area, JSONObject actionsObject) {
        String actionId = actionsObject.optString(triggerId, getDefaultActionId(triggerId));
        if ("none".equals(actionId)) return;
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_TAP;
        binding.fingers = 1;
        binding.tapCount = TRIGGER_FULLSCREEN_DOUBLE.equals(triggerId) ? 2 : 1;
        binding.area = area;
        binding.motion = "";
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    private void addLegacyMotionBinding(CustomKeyboardConfig config, String triggerId, String motion, JSONObject actionsObject) {
        String actionId = actionsObject.optString(triggerId, getDefaultActionId(triggerId));
        if ("none".equals(actionId)) return;
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_MOTION;
        binding.fingers = 1;
        binding.tapCount = 0;
        binding.area = TAP_AREA_FULL;
        binding.motion = motion;
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    private boolean isKnownTapArea(String area) {
        for (String knownArea : TAP_AREAS) {
            if (knownArea.equals(area)) return true;
        }
        return false;
    }

    private boolean isKnownMotion(String motion) {
        for (String knownMotion : MOTION_GESTURES) {
            if (knownMotion.equals(motion)) return true;
        }
        return false;
    }

    private CustomKeyboardConfig createDefaultKeyboardFromLegacy() {
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = "keyboard_1";
        config.name = "默认手势键盘";
        config.type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
        config.controlMode = preferences == null
                ? CONTROL_MODE_FULLSCREEN
                : preferences.getString(PREF_CONTROL_MODE, CONTROL_MODE_FULLSCREEN);
        config.stickClickMode = STICK_CLICK_MODE_TAP;
        config.vibrationEnabled = preferences == null || preferences.getBoolean(PREF_VIBRATION_ENABLED, true);
        config.vibrationLevel = VIBRATION_MEDIUM;
        config.backgroundImageUri = "";
        JSONObject legacyActions = new JSONObject();
        try {
            for (String triggerId : GESTURE_TRIGGER_IDS) {
                String actionId = preferences == null
                        ? getDefaultActionId(triggerId)
                        : preferences.getString(PREF_ACTION_PREFIX + triggerId, getDefaultActionId(triggerId));
                legacyActions.put(triggerId, actionId);
            }
        } catch (Exception ignored) {
        }
        addLegacyBindings(config, legacyActions);
        normalizeKeyboardConfig(config);
        return config;
    }

    private CustomKeyboardConfig createReaderPagingKeyboard() {
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = "keyboard_1";
        config.name = "阅读器翻页";
        config.type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
        config.controlMode = CONTROL_MODE_FULLSCREEN;
        config.stickClickMode = STICK_CLICK_MODE_TAP;
        config.vibrationEnabled = false;
        config.vibrationLevel = VIBRATION_MEDIUM;
        config.backgroundImageUri = "";

        addDefaultTapBinding(config, 1, 1, "kb_volume_down");
        addDefaultTapBinding(config, 1, 2, "kb_volume_up");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_UP, "kb_volume_down");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_DOWN, "kb_volume_up");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_LEFT, "kb_volume_down");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_RIGHT, "kb_volume_up");
        addDefaultMotionBinding(config, 2, MOTION_SWIPE_DOWN, "kb_power");
        addDefaultMotionBinding(config, 2, MOTION_SWIPE_UP, "kb_power");

        normalizeKeyboardConfig(config);
        return config;
    }

    private void addDefaultTapBinding(CustomKeyboardConfig config, int fingers, int tapCount, String actionId) {
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_TAP;
        binding.fingers = fingers;
        binding.tapCount = tapCount;
        binding.area = TAP_AREA_FULL;
        binding.motion = "";
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    private void addDefaultMotionBinding(CustomKeyboardConfig config, int fingers, String motion, String actionId) {
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_MOTION;
        binding.fingers = fingers;
        binding.tapCount = 0;
        binding.area = TAP_AREA_FULL;
        binding.motion = motion;
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    private CustomKeyboardConfig createNewKeyboardDraft(String type) {
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = allocateKeyboardId();
        config.name = nextKeyboardName(type);
        config.type = type;
        config.controlMode = CONTROL_MODE_FULLSCREEN;
        config.stickClickMode = STICK_CLICK_MODE_TAP;
        config.vibrationEnabled = true;
        config.vibrationLevel = VIBRATION_MEDIUM;
        config.backgroundImageUri = "";
        normalizeKeyboardConfig(config);
        return config;
    }

    private String allocateKeyboardId() {
        int nextId = preferences == null ? 1 : preferences.getInt(PREF_NEXT_KEYBOARD_ID, 1);
        if (preferences != null) {
            preferences.edit().putInt(PREF_NEXT_KEYBOARD_ID, nextId + 1).apply();
        }
        return "keyboard_" + nextId;
    }

    private String nextKeyboardName(String type) {
        return keyboardTypeLabel(type) + " " + Math.max(1, loadKeyboardConfigs().size() + 1);
    }

    private void upsertKeyboardConfig(CustomKeyboardConfig savedConfig) {
        normalizeKeyboardConfig(savedConfig);
        List<CustomKeyboardConfig> configs = loadKeyboardConfigs();
        boolean replaced = false;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).id.equals(savedConfig.id)) {
                configs.set(i, savedConfig.copy());
                replaced = true;
                break;
            }
        }
        if (!replaced) configs.add(savedConfig.copy());
        saveKeyboardConfigs(configs);
    }

    private void deleteKeyboardConfig(String keyboardId) {
        List<CustomKeyboardConfig> configs = loadKeyboardConfigs();
        for (int i = configs.size() - 1; i >= 0; i--) {
            if (configs.get(i).id.equals(keyboardId)) configs.remove(i);
        }
        saveKeyboardConfigs(configs);
    }

    private String keyboardTypeLabel(String type) {
        if (KEYBOARD_TYPE_COMPUTER.equals(type)) return "电脑键盘";
        if (KEYBOARD_TYPE_GAMEPAD.equals(type)) return "标准手柄";
        if (KEYBOARD_TYPE_JOYCON_PAIR.equals(type)) return "Joy-Con";
        if (KEYBOARD_TYPE_JOYCON_LEFT.equals(type)) return "Joy-Con (L)";
        if (KEYBOARD_TYPE_JOYCON_RIGHT.equals(type)) return "Joy-Con (R)";
        return "全屏手势键盘";
    }

    private boolean isKnownKeyboardType(String type) {
        return KEYBOARD_TYPE_FULLSCREEN_GESTURE.equals(type)
                || KEYBOARD_TYPE_COMPUTER.equals(type);
    }

    private boolean isGestureKeyboard(CustomKeyboardConfig config) {
        return config == null || KEYBOARD_TYPE_FULLSCREEN_GESTURE.equals(config.type);
    }

    private boolean isControllerKeyboard(CustomKeyboardConfig config) {
        return config != null && (
                KEYBOARD_TYPE_GAMEPAD.equals(config.type)
                        || KEYBOARD_TYPE_JOYCON_PAIR.equals(config.type)
                        || KEYBOARD_TYPE_JOYCON_LEFT.equals(config.type)
                        || KEYBOARD_TYPE_JOYCON_RIGHT.equals(config.type)
        );
    }

    private boolean isComputerKeyboard(CustomKeyboardConfig config) {
        return config != null && KEYBOARD_TYPE_COMPUTER.equals(config.type);
    }

    private boolean isLandscapeControllerType(String type) {
        return KEYBOARD_TYPE_GAMEPAD.equals(type) || KEYBOARD_TYPE_JOYCON_PAIR.equals(type);
    }

    private boolean isLandscapeRemoteType(String type) {
        return KEYBOARD_TYPE_COMPUTER.equals(type) || isLandscapeControllerType(type);
    }

    private String remoteOrientationLabel(String type) {
        return isLandscapeRemoteType(type) ? "横屏使用" : "竖屏使用";
    }

    private String fixedKeyboardSignalLabel(String type) {
        if (KEYBOARD_TYPE_COMPUTER.equals(type)) {
            return "标准 HID Keyboard：修饰键 + 最多 6 个普通键";
        }
        return "标准 HID Gamepad：16 按钮、方向帽、双摇杆、双扳机";
    }

    private String vibrationLevelLabel(int level) {
        switch (level) {
            case VIBRATION_LIGHT:
                return "轻";
            case VIBRATION_STRONG:
                return "强";
            case VIBRATION_MEDIUM:
            default:
                return "中";
        }
    }

    private GestureBinding findBindingByKey(CustomKeyboardConfig config, String key) {
        if (config == null || key == null) return null;
        for (GestureBinding binding : config.bindings) {
            if (key.equals(binding.key())) return binding;
        }
        return null;
    }

    private boolean hasTapAreaConflict(
            CustomKeyboardConfig config,
            int fingers,
            int tapCount,
            String area,
            GestureBinding ignoredBinding
    ) {
        if (config == null) return false;
        for (GestureBinding binding : config.bindings) {
            if (binding == ignoredBinding) continue;
            if (!GESTURE_KIND_TAP.equals(binding.kind)) continue;
            if (binding.fingers != fingers || binding.tapCount != tapCount) continue;
            if (tapRegionsOverlap(binding.area, area)) return true;
        }
        return false;
    }

    private static boolean tapRegionsOverlap(String first, String second) {
        float[] a = areaBounds(first);
        float[] b = areaBounds(second);
        return a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1];
    }

    private static boolean tapAreaContains(String area, float xRatio, float yRatio) {
        float[] bounds = areaBounds(area);
        return xRatio >= bounds[0] && xRatio <= bounds[2] && yRatio >= bounds[1] && yRatio <= bounds[3];
    }

    private static float[] areaBounds(String area) {
        if (TAP_AREA_LEFT.equals(area)) return new float[]{0f, 0f, 0.5f, 1f};
        if (TAP_AREA_RIGHT.equals(area)) return new float[]{0.5f, 0f, 1f, 1f};
        if (TAP_AREA_TOP.equals(area)) return new float[]{0f, 0f, 1f, 0.5f};
        if (TAP_AREA_BOTTOM.equals(area)) return new float[]{0f, 0.5f, 1f, 1f};
        return new float[]{0f, 0f, 1f, 1f};
    }

    private String gestureTitle(GestureBinding binding) {
        if (binding == null) return "未知手势";
        if (GESTURE_KIND_TAP.equals(binding.kind)) {
            return fingerLabel(binding.fingers)
                    + tapCountLabel(binding.tapCount)
                    + " · "
                    + tapAreaLabel(binding.area);
        }
        return fingerLabel(binding.fingers) + motionLabel(binding.motion);
    }

    private String fingerLabel(int fingers) {
        switch (fingers) {
            case 2:
                return "双指";
            case 3:
                return "三指";
            case 1:
            default:
                return "单指";
        }
    }

    private String tapCountLabel(int tapCount) {
        switch (tapCount) {
            case 2:
                return "双击";
            case 3:
                return "三击";
            case 1:
            default:
                return "单击";
        }
    }

    private String tapAreaLabel(String area) {
        if (TAP_AREA_LEFT.equals(area)) return "左半屏";
        if (TAP_AREA_RIGHT.equals(area)) return "右半屏";
        if (TAP_AREA_TOP.equals(area)) return "上半屏";
        if (TAP_AREA_BOTTOM.equals(area)) return "下半屏";
        return "全屏";
    }

    private String motionLabel(String motion) {
        if (MOTION_SWIPE_UP.equals(motion)) return "上滑";
        if (MOTION_SWIPE_DOWN.equals(motion)) return "下滑";
        if (MOTION_SWIPE_LEFT.equals(motion)) return "左滑";
        if (MOTION_SWIPE_RIGHT.equals(motion)) return "右滑";
        if (MOTION_CIRCLE_CLOCKWISE.equals(motion)) return "顺时针画圈";
        if (MOTION_CIRCLE_COUNTERCLOCKWISE.equals(motion)) return "逆时针画圈";
        return "滑动";
    }

    private String keyboardSummary(CustomKeyboardConfig config) {
        String vibration = config.vibrationEnabled ? "震动 " + vibrationLevelLabel(config.vibrationLevel) : "震动关闭";
        if (isControllerKeyboard(config)) {
            return keyboardTypeLabel(config.type) + " · 固定手柄布局 · " + remoteOrientationLabel(config.type);
        }
        if (isComputerKeyboard(config)) {
            return keyboardTypeLabel(config.type) + " · QWERTY 布局 · " + remoteOrientationLabel(config.type);
        }
        return keyboardTypeLabel(config.type) + " · " + config.bindings.size() + " 个手势 · " + vibration;
    }

    private String keyboardBackgroundLabel(CustomKeyboardConfig config) {
        return hasCustomBackground(config) ? "自定义图片" : "纯黑";
    }

    private boolean hasCustomBackground(CustomKeyboardConfig config) {
        return config != null
                && config.backgroundImageUri != null
                && !config.backgroundImageUri.trim().isEmpty();
    }

    private View makeSeparator() {
        View separator = new View(this);
        separator.setBackgroundColor(Color.rgb(237, 241, 248));
        return separator;
    }

    private void enterRemoteFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitRemoteFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void keepScreenAwake() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void allowScreenSleep() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void applyOrientationForSetup() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void applyOrientationForRemote(String type) {
        setRequestedOrientation(isLandscapeRemoteType(type)
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private GradientDrawable roundedRect(int fillColor, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setTextSizeDp(TextView textView, float sizeDp) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(sizeDp));
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : dp(12);
    }

    private int getNavigationBarHeight() {
        int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : 0;
    }

    private int[] getFullscreenDisplaySize() {
        DisplayMetrics metrics = new DisplayMetrics();
        try {
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        } catch (Exception ignored) {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        if (width > height) {
            int swapped = width;
            width = height;
            height = swapped;
        }
        return new int[]{width, height};
    }

    private void requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> permissions = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), REQ_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void toggleBluetooth() {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermissionsIfNeeded();
            updateStatus("请先授予“附近设备/蓝牙”权限，然后再点一次。");
            return;
        }
        try {
            if (bluetoothAdapter == null) return;
            if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();
            } else {
                bluetoothAdapter.enable();
                startAndBindHidService();
            }
            mainHandler.postDelayed(() -> {
                if (isConnectionPageVisible()) {
                    showSetupPage();
                }
            }, 300);
        } catch (SecurityException e) {
            updateStatus("操作蓝牙失败：缺少蓝牙连接权限。");
        }
    }

    private void refreshPairedDevices() {
        refreshPairedDevices(false);
    }

    private void refreshPairedDevices(boolean userInitiated) {
        if (pairedListLayout == null) return;
        pairedListLayout.removeAllViews();

        if (!hasBluetoothConnectPermission()) {
            addInfoRow("请先授予蓝牙连接权限。");
            finishPairedRefresh(userInitiated, "刷新失败", "请先授予蓝牙连接权限");
            return;
        }
        if (bluetoothAdapter == null) {
            addInfoRow("没有可用蓝牙适配器。");
            finishPairedRefresh(userInitiated, "刷新失败", "没有可用蓝牙适配器");
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                addInfoRow("蓝牙未打开。");
                finishPairedRefresh(userInitiated, "刷新失败", "蓝牙未打开");
                return;
            }
            if (hidService == null) {
                addInfoRow("HID 后台服务启动中...");
                finishPairedRefresh(userInitiated, "刷新中", "HID 后台服务启动中");
                return;
            }
            Set<BluetoothDevice> bondedDevices = hidService.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                addInfoRow("还没有已配对设备。请先在电脑、电视或平板的蓝牙设置中完成配对。");
                finishPairedRefresh(userInitiated, "刷新完成", "未找到已配对设备");
                return;
            }

            List<BluetoothDevice> devices = new ArrayList<>(bondedDevices);
            devices.sort((left, right) -> safeDeviceName(left).compareToIgnoreCase(safeDeviceName(right)));

            for (int i = 0; i < devices.size(); i++) {
                if (i > 0) {
                    pairedListLayout.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
                }
                pairedListLayout.addView(makeDeviceRow(devices.get(i)), new LinearLayout.LayoutParams(-1, -2));
            }
            finishPairedRefresh(userInitiated, "刷新完成", "已找到 " + devices.size() + " 台已配对设备");
        } catch (SecurityException e) {
            addInfoRow("读取已配对设备失败：缺少蓝牙权限。");
            finishPairedRefresh(userInitiated, "刷新失败", "缺少蓝牙权限，读取失败");
            return;
        }
    }

    private void finishPairedRefresh(boolean userInitiated, String title, String message) {
        refreshStatusCards();
        if (userInitiated) {
            showRefreshFeedback(title, message);
        }
    }

    private void showRefreshFeedback(String title, String message) {
        Toast.makeText(this, title + "：" + message, Toast.LENGTH_SHORT).show();
        if (connectionTitleView == null || connectionSubtitleView == null || !isConnectionPageVisible()) {
            return;
        }

        if (refreshFeedbackResetRunnable != null) {
            mainHandler.removeCallbacks(refreshFeedbackResetRunnable);
        }
        connectionTitleView.setText(title);
        connectionSubtitleView.setText(message);
        connectionSubtitleView.announceForAccessibility(title + "，" + message);

        refreshFeedbackResetRunnable = () -> {
            if (isConnectionPageVisible()) {
                updateStatus(hidService != null ? hidService.getStatus() : null);
            }
        };
        mainHandler.postDelayed(refreshFeedbackResetRunnable, 1600);
    }

    private View makeDeviceRow(BluetoothDevice device) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(56));
        row.setOnClickListener(v -> connectToHost(device));

        boolean isConnected = hidService != null && device.equals(hidService.getConnectedHost());
        int deviceIconColor = isConnected ? Color.rgb(36, 122, 255) : Color.rgb(160, 170, 190);
        row.addView(makeIconBubble(38, Color.rgb(245, 247, 252), resolveDeviceIconType(device), deviceIconColor, 1.8f));

        TextView nameView = new TextView(this);
        nameView.setText(safeDeviceName(device));
        setTextSizeDp(nameView, 12.5f);
        nameView.setTextColor(isConnected ? Color.rgb(19, 28, 47) : Color.rgb(120, 130, 150));
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nameView.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(10);
        row.addView(nameView, nameParams);

        if (isConnected) {
            row.addView(makeStatusChip("已连接", true), new LinearLayout.LayoutParams(-2, -2));
        } else {
            IconView chevron = new IconView(this, IconView.ICON_CHEVRON, Color.rgb(120, 134, 160), 1.5f);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(12), dp(12)));
        }
        return row;
    }

    private void connectToHost(BluetoothDevice device) {
        if (hidService == null) {
            updateStatus("HID 后台服务还没准备好。请保持蓝牙打开并等待几秒。");
            startAndBindHidService();
            return;
        }
        hidService.setPreferredHidMode(false);
        hidService.connectToHost(device);
    }

    private void disconnectHost() {
        awaitingModeReconnect = false;
        pendingRemoteDevice = null;
        pendingRemoteConfig = null;
        if (hidService == null) {
            showSetupPage();
            updateStatus("已返回连接页。");
            refreshPairedDevices();
            return;
        }
        hidService.disconnectHost();
        showSetupPage();
        updateStatus(hidService.getStatus());
        refreshPairedDevices();
    }

    private void exitRemotePage() {
        awaitingModeReconnect = false;
        pendingRemoteDevice = null;
        pendingRemoteConfig = null;
        activeKeyboardConfig = null;
        showKeyboardsPage(false);
        mainHandler.post(restoreSetupOrientationRunnable);
    }

    private void sendActionForTrigger(String triggerId) {
        if (hidService == null) {
            updateStatus("HID 后台服务未连接，请返回连接页重试。");
            startAndBindHidService();
            return;
        }
        hidService.sendAction(getActionForTrigger(triggerId));
    }

    private void sendGamepadState(int buttons, int hat, int lx, int ly, int rx, int ry, int lt, int rt) {
        if (hidService == null) {
            updateStatus("HID 后台服务未连接，请返回连接页重试。");
            startAndBindHidService();
            return;
        }
        hidService.sendGamepadState(buttons, hat, lx, ly, rx, ry, lt, rt);
    }

    private void sendKeyboardState(int modifierMask, int[] keyboardUsages) {
        if (hidService == null) {
            updateStatus("HID 后台服务未连接，请返回连接页重试。");
            startAndBindHidService();
            return;
        }
        hidService.sendKeyboardState(modifierMask, keyboardUsages);
    }

    private void addInfoRow(String text) {
        if (pairedListLayout == null) return;
        TextView row = new TextView(this);
        row.setText(text);
        setTextSizeDp(row, 12);
        row.setTextColor(Color.rgb(123, 137, 165));
        row.setLineSpacing(0f, 1.12f);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        pairedListLayout.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void updateStatus(String status) {
        refreshStatusCards();
        if (connectionTitleView == null || connectionSubtitleView == null) return;

        if (!hasBluetoothConnectPermission()) {
            connectionTitleView.setText("需要蓝牙权限");
            connectionSubtitleView.setText("请在系统设置中授予附近设备权限");
            return;
        }
        if (bluetoothAdapter == null) {
            connectionTitleView.setText("无可用蓝牙");
            connectionSubtitleView.setText("当前设备没有可用的蓝牙适配器");
            return;
        }

        BluetoothDevice connected = hidService == null ? null : hidService.getConnectedHost();
        if (connected != null) {
            connectionTitleView.setText(safeDeviceName(connected));
            connectionSubtitleView.setText("已连接，可到底部键盘页选择自定义键盘运行");
            return;
        }

        if (status != null && status.startsWith("正在连接：")) {
            connectionTitleView.setText("正在连接设备");
            connectionSubtitleView.setText(status.substring("正在连接：".length()));
            return;
        }

        if (status != null && (
                status.startsWith("没有蓝牙连接权限")
                        || status.startsWith("操作蓝牙失败")
                        || status.startsWith("读取已配对设备失败")
                        || status.startsWith("这台设备没有可用")
                        || status.startsWith("此方案需要")
        )) {
            connectionTitleView.setText("连接不可用");
            connectionSubtitleView.setText(status);
            return;
        }

        if (status != null && (
                status.startsWith("请先打开蓝牙")
                        || status.startsWith("蓝牙已经打开")
                        || status.startsWith("蓝牙已打开")
                        || status.startsWith("蓝牙已关闭")
                        || status.startsWith("HID 后台服务启动中")
        )) {
            connectionTitleView.setText("未连接设备");
            connectionSubtitleView.setText(status);
            return;
        }

        connectionTitleView.setText("未连接设备");
        connectionSubtitleView.setText("请选择已配对设备并建立连接");
    }

    private void refreshStatusCards() {
        boolean btOn = isBluetoothEnabled();
        if (bluetoothStateView != null) {
            bluetoothStateView.setText(btOn ? "已打开" : "已关闭");
        }
        if (statusBtIcon != null) {
            statusBtIcon.setTintColor(hasConnectedHost() ? Color.rgb(36, 122, 255) : Color.rgb(160, 170, 190));
        }
        if (quickBtIcon != null) {
            quickBtIcon.setTintColor(btOn ? Color.rgb(36, 122, 255) : Color.rgb(160, 170, 190));
        }
    }

    private boolean isBluetoothEnabled() {
        if (bluetoothAdapter == null || !hasBluetoothConnectPermission()) return false;
        try {
            return bluetoothAdapter.isEnabled();
        } catch (SecurityException e) {
            return false;
        }
    }

    private boolean hasConnectedHost() {
        return hidService != null && hidService.getConnectedHost() != null;
    }

    private int resolveDeviceIconType(BluetoothDevice device) {
        if (device == null) return IconView.ICON_DEVICE;
        try {
            BluetoothClass bluetoothClass = device.getBluetoothClass();
            if (bluetoothClass != null) {
                int deviceClass = bluetoothClass.getDeviceClass();
                if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
                        || deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) {
                    return IconView.ICON_HEADPHONES;
                }
                if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
                        || deviceClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO) {
                    return IconView.ICON_SPEAKER;
                }
                if (deviceClass == BluetoothClass.Device.WEARABLE_WRIST_WATCH) {
                    return IconView.ICON_WATCH;
                }
            }
        } catch (SecurityException ignored) {
        }

        String name = safeDeviceName(device).toLowerCase();
        if (name.contains("head") || name.contains("pods") || name.contains("bud") || name.contains("run")) {
            return IconView.ICON_HEADPHONES;
        }
        if (name.contains("speaker") || name.contains("box")) {
            return IconView.ICON_SPEAKER;
        }
        if (name.contains("watch") || name.contains("band")) {
            return IconView.ICON_WATCH;
        }
        return IconView.ICON_DEVICE;
    }

    private String safeDeviceName(BluetoothDevice device) {
        if (device == null) return "未知设备";
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "未命名设备" : name;
        } catch (SecurityException e) {
            return "蓝牙设备";
        }
    }

    private String safeDeviceAddress(BluetoothDevice device) {
        if (device == null) return "";
        try {
            return device.getAddress();
        } catch (SecurityException e) {
            return "地址不可读";
        }
    }

    private static class IconView extends View {
        static final int ICON_BLUETOOTH = 1;

        static final int ICON_SETTINGS = 3;
        static final int ICON_REFRESH = 4;
        static final int ICON_CHEVRON = 5;
        static final int ICON_HEADPHONES = 6;
        static final int ICON_SPEAKER = 7;
        static final int ICON_WATCH = 8;
        static final int ICON_DEVICE = 9;
        static final int ICON_LINK = 10;
        static final int ICON_HEXAGON = 11;
        static final int ICON_PLAY = 12;
        static final int ICON_KEYBOARD = 13;

        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int iconType;

        IconView(Context context, int iconType, int tintColor, float strokeDp) {
            super(context);
            this.iconType = iconType;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setColor(tintColor);
            strokePaint.setStrokeWidth(getResources().getDisplayMetrics().density * strokeDp);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(tintColor);
        }

        void setTintColor(int color) {
            strokePaint.setColor(color);
            fillPaint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            switch (iconType) {
                case ICON_BLUETOOTH:
                    drawBluetooth(canvas, w, h);
                    break;
                case ICON_SETTINGS:
                    drawSettings(canvas, w, h);
                    break;
                case ICON_REFRESH:
                    drawRefresh(canvas, w, h);
                    break;
                case ICON_CHEVRON:
                    drawChevron(canvas, w, h);
                    break;
                case ICON_HEADPHONES:
                    drawHeadphones(canvas, w, h);
                    break;
                case ICON_SPEAKER:
                    drawSpeaker(canvas, w, h);
                    break;
                case ICON_WATCH:
                    drawWatch(canvas, w, h);
                    break;
                case ICON_LINK:
                    drawLink(canvas, w, h);
                    break;
                case ICON_HEXAGON:
                    drawHexagon(canvas, w, h);
                    break;
                case ICON_PLAY:
                    drawPlay(canvas, w, h);
                    break;
                case ICON_KEYBOARD:
                    drawKeyboard(canvas, w, h);
                    break;
                default:
                    drawDevice(canvas, w, h);
                    break;
            }
        }

        private void drawBluetooth(Canvas canvas, float w, float h) {
            float cx = w * 0.48f;
            Path path = new Path();
            path.moveTo(cx, h * 0.14f);
            path.lineTo(cx, h * 0.86f);
            path.moveTo(cx, h * 0.14f);
            path.lineTo(w * 0.76f, h * 0.33f);
            path.lineTo(cx, h * 0.5f);
            path.lineTo(w * 0.76f, h * 0.67f);
            path.lineTo(cx, h * 0.86f);
            path.moveTo(w * 0.26f, h * 0.31f);
            path.lineTo(cx, h * 0.5f);
            path.lineTo(w * 0.26f, h * 0.69f);
            canvas.drawPath(path, strokePaint);
        }

        private void drawSettings(Canvas canvas, float w, float h) {
            float cx = w / 2f;
            float cy = h / 2f;
            float outer = Math.min(w, h) * 0.3f;
            float inner = Math.min(w, h) * 0.14f;
            canvas.drawCircle(cx, cy, outer, strokePaint);
            canvas.drawCircle(cx, cy, inner, strokePaint);
            for (int i = 0; i < 6; i++) {
                double angle = Math.toRadians(i * 60d);
                float startX = cx + (float) Math.cos(angle) * outer;
                float startY = cy + (float) Math.sin(angle) * outer;
                float endX = cx + (float) Math.cos(angle) * (outer + Math.min(w, h) * 0.1f);
                float endY = cy + (float) Math.sin(angle) * (outer + Math.min(w, h) * 0.1f);
                canvas.drawLine(startX, startY, endX, endY, strokePaint);
            }
        }

        private void drawRefresh(Canvas canvas, float w, float h) {
            RectF oval = new RectF(w * 0.2f, h * 0.2f, w * 0.8f, h * 0.8f);
            float startAngle = 46f;
            float sweepAngle = 258f;
            canvas.drawArc(oval, startAngle, sweepAngle, false, strokePaint);

            float endAngle = startAngle + sweepAngle;
            float rx = oval.width() / 2f;
            float ry = oval.height() / 2f;
            float cx = oval.centerX();
            float cy = oval.centerY();
            double theta = Math.toRadians(endAngle);

            float tipX = cx + rx * (float) Math.cos(theta);
            float tipY = cy + ry * (float) Math.sin(theta);
            float tangentX = -rx * (float) Math.sin(theta);
            float tangentY = ry * (float) Math.cos(theta);
            float tangentLength = (float) Math.sqrt(tangentX * tangentX + tangentY * tangentY);
            if (tangentLength == 0f) tangentLength = 1f;
            tangentX /= tangentLength;
            tangentY /= tangentLength;

            float arrowSize = Math.min(w, h) * 0.18f;
            float backX = tipX - tangentX * arrowSize;
            float backY = tipY - tangentY * arrowSize;
            float normalX = -tangentY;
            float normalY = tangentX;

            Path arrow = new Path();
            arrow.moveTo(tipX, tipY);
            arrow.lineTo(backX + normalX * arrowSize * 0.46f, backY + normalY * arrowSize * 0.46f);
            arrow.moveTo(tipX, tipY);
            arrow.lineTo(backX - normalX * arrowSize * 0.46f, backY - normalY * arrowSize * 0.46f);
            canvas.drawPath(arrow, strokePaint);
        }

        private void drawChevron(Canvas canvas, float w, float h) {
            Path path = new Path();
            path.moveTo(w * 0.34f, h * 0.2f);
            path.lineTo(w * 0.64f, h * 0.5f);
            path.lineTo(w * 0.34f, h * 0.8f);
            canvas.drawPath(path, strokePaint);
        }

        private void drawHeadphones(Canvas canvas, float w, float h) {
            RectF band = new RectF(w * 0.22f, h * 0.18f, w * 0.78f, h * 0.76f);
            canvas.drawArc(band, 200, 140, false, strokePaint);
            canvas.drawRoundRect(new RectF(w * 0.18f, h * 0.5f, w * 0.32f, h * 0.8f), w * 0.06f, w * 0.06f, strokePaint);
            canvas.drawRoundRect(new RectF(w * 0.68f, h * 0.5f, w * 0.82f, h * 0.8f), w * 0.06f, w * 0.06f, strokePaint);
        }

        private void drawSpeaker(Canvas canvas, float w, float h) {
            canvas.drawRoundRect(new RectF(w * 0.32f, h * 0.16f, w * 0.68f, h * 0.84f), w * 0.08f, w * 0.08f, strokePaint);
            canvas.drawCircle(w * 0.5f, h * 0.36f, Math.min(w, h) * 0.06f, fillPaint);
            canvas.drawCircle(w * 0.5f, h * 0.66f, Math.min(w, h) * 0.09f, strokePaint);
        }

        private void drawWatch(Canvas canvas, float w, float h) {
            canvas.drawRoundRect(new RectF(w * 0.32f, h * 0.18f, w * 0.68f, h * 0.82f), w * 0.12f, w * 0.12f, strokePaint);
            canvas.drawLine(w * 0.42f, h * 0.08f, w * 0.58f, h * 0.08f, strokePaint);
            canvas.drawLine(w * 0.42f, h * 0.92f, w * 0.58f, h * 0.92f, strokePaint);
        }

        private void drawDevice(Canvas canvas, float w, float h) {
            canvas.drawRoundRect(new RectF(w * 0.28f, h * 0.14f, w * 0.72f, h * 0.86f), w * 0.14f, w * 0.14f, strokePaint);
            canvas.drawCircle(w * 0.5f, h * 0.74f, Math.min(w, h) * 0.04f, fillPaint);
        }

        private void drawLink(Canvas canvas, float w, float h) {
            RectF left = new RectF(w * 0.12f, h * 0.38f, w * 0.54f, h * 0.78f);
            RectF right = new RectF(w * 0.46f, h * 0.22f, w * 0.88f, h * 0.62f);
            canvas.drawRoundRect(left, w * 0.18f, w * 0.18f, strokePaint);
            canvas.drawRoundRect(right, w * 0.18f, w * 0.18f, strokePaint);
            canvas.drawLine(w * 0.4f, h * 0.58f, w * 0.6f, h * 0.42f, strokePaint);
        }

        private void drawHexagon(Canvas canvas, float w, float h) {
            Path hex = new Path();
            for (int i = 0; i < 6; i++) {
                double angle = Math.toRadians(60d * i - 30d);
                float x = w / 2f + (float) Math.cos(angle) * w * 0.34f;
                float y = h / 2f + (float) Math.sin(angle) * h * 0.34f;
                if (i == 0) {
                    hex.moveTo(x, y);
                } else {
                    hex.lineTo(x, y);
                }
            }
            hex.close();
            canvas.drawPath(hex, strokePaint);
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.08f, fillPaint);
        }

        private void drawPlay(Canvas canvas, float w, float h) {
            Path path = new Path();
            path.moveTo(w * 0.34f, h * 0.24f);
            path.lineTo(w * 0.74f, h * 0.5f);
            path.lineTo(w * 0.34f, h * 0.76f);
            path.close();
            canvas.drawPath(path, fillPaint);
        }

        private void drawKeyboard(Canvas canvas, float w, float h) {
            RectF body = new RectF(w * 0.12f, h * 0.24f, w * 0.88f, h * 0.76f);
            canvas.drawRoundRect(body, w * 0.08f, w * 0.08f, strokePaint);
            float top = h * 0.4f;
            float bottom = h * 0.6f;
            for (int row = 0; row < 2; row++) {
                float y = row == 0 ? top : bottom;
                canvas.drawLine(w * 0.25f, y, w * 0.32f, y, strokePaint);
                canvas.drawLine(w * 0.42f, y, w * 0.49f, y, strokePaint);
                canvas.drawLine(w * 0.59f, y, w * 0.66f, y, strokePaint);
                canvas.drawLine(w * 0.74f, y, w * 0.79f, y, strokePaint);
            }
        }

    }

    private static class BackgroundCropView extends View {
        private static final int MODE_NONE = 0;
        private static final int MODE_DRAG = 1;
        private static final int MODE_ZOOM = 2;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final RectF destination = new RectF();
        private Bitmap sourceBitmap;
        private float scale = 1f;
        private float minScale = 1f;
        private float maxScale = 6f;
        private float offsetX = 0f;
        private float offsetY = 0f;
        private float lastX = 0f;
        private float lastY = 0f;
        private float lastDistance = 0f;
        private int mode = MODE_NONE;
        private boolean initialized = false;

        BackgroundCropView(Context context, Bitmap sourceBitmap) {
            super(context);
            this.sourceBitmap = sourceBitmap;
            setBackgroundColor(Color.BLACK);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (!initialized || w != oldw || h != oldh) {
                resetTransform(w, h);
            }
        }

        private void resetTransform(int viewWidth, int viewHeight) {
            if (sourceBitmap == null || sourceBitmap.isRecycled() || viewWidth <= 0 || viewHeight <= 0) return;
            float bitmapWidth = sourceBitmap.getWidth();
            float bitmapHeight = sourceBitmap.getHeight();
            minScale = Math.max(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
            maxScale = Math.max(minScale * 5f, minScale + 0.01f);
            scale = minScale;
            offsetX = (viewWidth - bitmapWidth * scale) * 0.5f;
            offsetY = (viewHeight - bitmapHeight * scale) * 0.5f;
            initialized = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);
            if (sourceBitmap == null || sourceBitmap.isRecycled()) return;
            destination.set(
                    offsetX,
                    offsetY,
                    offsetX + sourceBitmap.getWidth() * scale,
                    offsetY + sourceBitmap.getHeight() * scale
            );
            canvas.drawBitmap(sourceBitmap, null, destination, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (sourceBitmap == null || sourceBitmap.isRecycled()) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    mode = MODE_DRAG;
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        mode = MODE_ZOOM;
                        lastDistance = pointerDistance(event);
                        lastX = pointerFocusX(event);
                        lastY = pointerFocusY(event);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mode == MODE_ZOOM && event.getPointerCount() >= 2) {
                        float focusX = pointerFocusX(event);
                        float focusY = pointerFocusY(event);
                        float distance = pointerDistance(event);
                        if (lastDistance > 0f && distance > 0f) {
                            float nextScale = clamp(scale * (distance / lastDistance), minScale, maxScale);
                            float factor = nextScale / scale;
                            offsetX = focusX - (focusX - offsetX) * factor;
                            offsetY = focusY - (focusY - offsetY) * factor;
                            scale = nextScale;
                        }
                        offsetX += focusX - lastX;
                        offsetY += focusY - lastY;
                        lastDistance = distance;
                        lastX = focusX;
                        lastY = focusY;
                        clampOffsets();
                        invalidate();
                    } else if (mode == MODE_DRAG && event.getPointerCount() == 1) {
                        float x = event.getX();
                        float y = event.getY();
                        offsetX += x - lastX;
                        offsetY += y - lastY;
                        lastX = x;
                        lastY = y;
                        clampOffsets();
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerCount() - 1 <= 1) {
                        mode = MODE_DRAG;
                        int index = event.getActionIndex() == 0 ? 1 : 0;
                        if (index < event.getPointerCount()) {
                            lastX = event.getX(index);
                            lastY = event.getY(index);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mode = MODE_NONE;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return true;
        }

        Bitmap createCroppedBitmap(int targetWidth, int targetHeight) {
            if (sourceBitmap == null || sourceBitmap.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return null;
            Bitmap output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.BLACK);
            float scaleX = targetWidth / (float) getWidth();
            float scaleY = targetHeight / (float) getHeight();
            RectF outputDestination = new RectF(
                    offsetX * scaleX,
                    offsetY * scaleY,
                    (offsetX + sourceBitmap.getWidth() * scale) * scaleX,
                    (offsetY + sourceBitmap.getHeight() * scale) * scaleY
            );
            canvas.drawBitmap(sourceBitmap, null, outputDestination, paint);
            return output;
        }

        void release() {
            if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
                sourceBitmap.recycle();
            }
            sourceBitmap = null;
        }

        private void clampOffsets() {
            if (sourceBitmap == null || sourceBitmap.isRecycled()) return;
            float scaledWidth = sourceBitmap.getWidth() * scale;
            float scaledHeight = sourceBitmap.getHeight() * scale;
            if (scaledWidth <= getWidth()) {
                offsetX = (getWidth() - scaledWidth) * 0.5f;
            } else {
                offsetX = clamp(offsetX, getWidth() - scaledWidth, 0f);
            }
            if (scaledHeight <= getHeight()) {
                offsetY = (getHeight() - scaledHeight) * 0.5f;
            } else {
                offsetY = clamp(offsetY, getHeight() - scaledHeight, 0f);
            }
        }

        private float pointerDistance(MotionEvent event) {
            if (event.getPointerCount() < 2) return 0f;
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private float pointerFocusX(MotionEvent event) {
            float sum = 0f;
            int count = Math.max(1, event.getPointerCount());
            for (int i = 0; i < count; i++) sum += event.getX(i);
            return sum / count;
        }

        private float pointerFocusY(MotionEvent event) {
            float sum = 0f;
            int count = Math.max(1, event.getPointerCount());
            for (int i = 0; i < count; i++) sum += event.getY(i);
            return sum / count;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected void onDetachedFromWindow() {
            release();
            super.onDetachedFromWindow();
        }
    }

    public interface GamepadStateListener {
        void onStateChanged(int buttons, int hat, int lx, int ly, int rx, int ry, int lt, int rt);
    }

    public interface KeyboardStateListener {
        void onStateChanged(int modifierMask, int[] keyboardUsages);
    }

    private class ComputerKeyboardView extends View {
        private static final int KEY_A = 0x04;
        private static final int KEY_1 = 0x1E;
        private static final int KEY_ENTER = 0x28;
        private static final int KEY_ESC = 0x29;
        private static final int KEY_BACKSPACE = 0x2A;
        private static final int KEY_TAB = 0x2B;
        private static final int KEY_SPACE = 0x2C;
        private static final int KEY_MINUS = 0x2D;
        private static final int KEY_EQUAL = 0x2E;
        private static final int KEY_LEFT_BRACKET = 0x2F;
        private static final int KEY_RIGHT_BRACKET = 0x30;
        private static final int KEY_BACKSLASH = 0x31;
        private static final int KEY_SEMICOLON = 0x33;
        private static final int KEY_QUOTE = 0x34;
        private static final int KEY_GRAVE = 0x35;
        private static final int KEY_COMMA = 0x36;
        private static final int KEY_DOT = 0x37;
        private static final int KEY_SLASH = 0x38;
        private static final int KEY_CAPS_LOCK = 0x39;
        private static final int KEY_F1 = 0x3A;
        private static final int KEY_DELETE = 0x4C;
        private static final int KEY_RIGHT = 0x4F;
        private static final int KEY_LEFT = 0x50;
        private static final int KEY_DOWN = 0x51;
        private static final int KEY_UP = 0x52;
        private static final int MOD_LEFT_CTRL = 0x01;
        private static final int MOD_LEFT_SHIFT = 0x02;
        private static final int MOD_LEFT_ALT = 0x04;
        private static final int MOD_LEFT_GUI = 0x08;
        private static final int MOD_RIGHT_SHIFT = 0x20;
        private static final int MOD_RIGHT_ALT = 0x40;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF exitRect = new RectF();
        private final List<KeyboardRow> rows = new ArrayList<>();
        private final List<KeyboardKey> keys = new ArrayList<>();
        private KeyboardStateListener stateListener;
        private Runnable disconnectListener;
        private boolean vibrationEnabled = true;
        private int vibrationLevel = VIBRATION_MEDIUM;
        private int lastModifierMask = -1;
        private int[] lastUsages = new int[0];
        private boolean pendingExitTap = false;

        ComputerKeyboardView(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setBackgroundColor(Color.rgb(8, 10, 14));
            strokePaint.setStyle(Paint.Style.STROKE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            buildKeyboardRows();
        }

        void setStateListener(KeyboardStateListener stateListener) {
            this.stateListener = stateListener;
            emitState(0, new int[0]);
        }

        void setDisconnectListener(Runnable disconnectListener) {
            this.disconnectListener = disconnectListener;
        }

        void setVibrationEnabled(boolean vibrationEnabled) {
            this.vibrationEnabled = vibrationEnabled;
        }

        void setVibrationLevel(int vibrationLevel) {
            this.vibrationLevel = vibrationLevel;
        }

        private void buildKeyboardRows() {
            rows.clear();
            rows.add(row(
                    key("Esc", KEY_ESC, 1.15f),
                    key("F1", KEY_F1, 0.92f),
                    key("F2", KEY_F1 + 1, 0.92f),
                    key("F3", KEY_F1 + 2, 0.92f),
                    key("F4", KEY_F1 + 3, 0.92f),
                    key("F5", KEY_F1 + 4, 0.92f),
                    key("F6", KEY_F1 + 5, 0.92f),
                    key("F7", KEY_F1 + 6, 0.92f),
                    key("F8", KEY_F1 + 7, 0.92f),
                    key("F9", KEY_F1 + 8, 0.92f),
                    key("F10", KEY_F1 + 9, 0.92f),
                    key("F11", KEY_F1 + 10, 0.92f),
                    key("F12", KEY_F1 + 11, 0.92f),
                    key("Del", KEY_DELETE, 1.05f)
            ));
            rows.add(row(
                    key("`", KEY_GRAVE),
                    key("1", KEY_1),
                    key("2", KEY_1 + 1),
                    key("3", KEY_1 + 2),
                    key("4", KEY_1 + 3),
                    key("5", KEY_1 + 4),
                    key("6", KEY_1 + 5),
                    key("7", KEY_1 + 6),
                    key("8", KEY_1 + 7),
                    key("9", KEY_1 + 8),
                    key("0", KEY_1 + 9),
                    key("-", KEY_MINUS),
                    key("=", KEY_EQUAL),
                    key("Back", KEY_BACKSPACE, 1.65f)
            ));
            rows.add(row(
                    key("Tab", KEY_TAB, 1.38f),
                    letter("Q"),
                    letter("W"),
                    letter("E"),
                    letter("R"),
                    letter("T"),
                    letter("Y"),
                    letter("U"),
                    letter("I"),
                    letter("O"),
                    letter("P"),
                    key("[", KEY_LEFT_BRACKET),
                    key("]", KEY_RIGHT_BRACKET),
                    key("\\", KEY_BACKSLASH, 1.25f)
            ));
            rows.add(row(
                    key("Caps", KEY_CAPS_LOCK, 1.65f),
                    letter("A"),
                    letter("S"),
                    letter("D"),
                    letter("F"),
                    letter("G"),
                    letter("H"),
                    letter("J"),
                    letter("K"),
                    letter("L"),
                    key(";", KEY_SEMICOLON),
                    key("'", KEY_QUOTE),
                    key("Enter", KEY_ENTER, 1.9f)
            ));
            rows.add(row(
                    modifier("Shift", MOD_LEFT_SHIFT, 2.05f),
                    letter("Z"),
                    letter("X"),
                    letter("C"),
                    letter("V"),
                    letter("B"),
                    letter("N"),
                    letter("M"),
                    key(",", KEY_COMMA),
                    key(".", KEY_DOT),
                    key("/", KEY_SLASH),
                    modifier("Shift", MOD_RIGHT_SHIFT, 2.05f)
            ));
            rows.add(row(
                    modifier("Ctrl", MOD_LEFT_CTRL, 1.25f),
                    modifier("Win", MOD_LEFT_GUI, 1.18f),
                    modifier("Alt", MOD_LEFT_ALT, 1.18f),
                    key("Space", KEY_SPACE, 5.2f),
                    modifier("Alt", MOD_RIGHT_ALT, 1.18f),
                    key("Left", KEY_LEFT, 1.08f),
                    key("Down", KEY_DOWN, 1.08f),
                    key("Up", KEY_UP, 1.08f),
                    key("Right", KEY_RIGHT, 1.08f)
            ));
        }

        private KeyboardRow row(KeyboardKey... rowKeys) {
            KeyboardRow row = new KeyboardRow();
            for (KeyboardKey key : rowKeys) row.keys.add(key);
            return row;
        }

        private KeyboardKey letter(String label) {
            return key(label, KEY_A + (label.charAt(0) - 'A'));
        }

        private KeyboardKey key(String label, int usage) {
            return key(label, usage, 1f);
        }

        private KeyboardKey key(String label, int usage, float weight) {
            return new KeyboardKey(label, usage, 0, weight);
        }

        private KeyboardKey modifier(String label, int modifierMask, float weight) {
            return new KeyboardKey(label, 0, modifierMask, weight);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            layoutKeyboard(w, h);
        }

        private void layoutKeyboard(float w, float h) {
            keys.clear();
            float sidePadding = Math.max(dp(8), w * 0.018f);
            float bottomPadding = Math.max(dp(8), h * 0.025f);
            float topPadding = Math.max(dp(8), h * 0.025f);
            float exitHeight = Math.max(dp(30), Math.min(dp(38), h * 0.085f));
            float exitWidth = Math.max(dp(82), Math.min(dp(112), w * 0.15f));
            exitRect.set(
                    (w - exitWidth) * 0.5f,
                    topPadding,
                    (w + exitWidth) * 0.5f,
                    topPadding + exitHeight
            );

            float rowGap = Math.max(dp(4), Math.min(dp(8), h * 0.012f));
            float keyGap = Math.max(dp(3), Math.min(dp(7), w * 0.006f));
            float keyboardTop = exitRect.bottom + Math.max(dp(8), h * 0.025f);
            float keyboardHeight = h - keyboardTop - bottomPadding;
            float rowHeight = (keyboardHeight - rowGap * (rows.size() - 1)) / rows.size();
            if (rowHeight < dp(28)) {
                rowGap = Math.max(dp(2), rowGap * 0.55f);
                keyGap = Math.max(dp(2), keyGap * 0.65f);
                keyboardTop = exitRect.bottom + Math.max(dp(4), h * 0.012f);
                keyboardHeight = h - keyboardTop - Math.max(dp(4), bottomPadding * 0.6f);
                rowHeight = Math.max(dp(24), (keyboardHeight - rowGap * (rows.size() - 1)) / rows.size());
            }

            float y = keyboardTop;
            float availableWidth = w - sidePadding * 2f;
            for (KeyboardRow row : rows) {
                float totalWeight = row.totalWeight();
                float totalGap = keyGap * Math.max(0, row.keys.size() - 1);
                float unit = Math.max(1f, (availableWidth - totalGap) / totalWeight);
                float x = sidePadding;
                for (KeyboardKey key : row.keys) {
                    float keyWidth = unit * key.weight;
                    key.rect.set(x, y, x + keyWidth, y + rowHeight);
                    keys.add(key);
                    x += keyWidth + keyGap;
                }
                y += rowHeight + rowGap;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(8, 10, 14));
            for (KeyboardKey key : keys) drawKey(canvas, key);
            drawExit(canvas);
        }

        private void drawKey(Canvas canvas, KeyboardKey key) {
            RectF rect = key.rect;
            if (rect.isEmpty()) return;
            float radius = Math.min(rect.height(), rect.width()) * 0.16f;
            int fillColor;
            if (key.pressed) {
                fillColor = key.isModifier()
                        ? Color.rgb(92, 128, 198)
                        : Color.rgb(78, 118, 205);
            } else if (key.isModifier() || key.weight > 1.1f) {
                fillColor = Color.rgb(39, 45, 58);
            } else {
                fillColor = Color.rgb(27, 32, 43);
            }
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fillColor);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            strokePaint.setStrokeWidth(dp(key.pressed ? 1.5f : 1f));
            strokePaint.setColor(key.pressed
                    ? Color.argb(150, 180, 212, 255)
                    : Color.argb(70, 210, 220, 238));
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
            drawCenteredText(canvas, key.label, rect.centerX(), rect.centerY(),
                    keyTextSize(key), key.pressed ? Color.WHITE : Color.rgb(226, 232, 242));
        }

        private float keyTextSize(KeyboardKey key) {
            float maxSize = Math.min(dp(14), key.rect.height() * 0.34f);
            float fitSize = key.rect.width() / Math.max(1.6f, key.label.length() * 0.66f);
            return Math.max(dp(6), Math.min(maxSize, fitSize));
        }

        private void drawExit(Canvas canvas) {
            float radius = exitRect.height() * 0.5f;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(215, 242, 245, 250));
            canvas.drawRoundRect(exitRect, radius, radius, fillPaint);
            strokePaint.setStrokeWidth(dp(1f));
            strokePaint.setColor(Color.argb(90, 255, 255, 255));
            canvas.drawRoundRect(exitRect, radius, radius, strokePaint);
            drawCenteredText(canvas, "退出", exitRect.centerX(), exitRect.centerY(),
                    Math.min(dp(13), exitRect.height() * 0.36f), Color.rgb(34, 43, 59));
        }

        private void drawCenteredText(Canvas canvas, String text, float cx, float cy, float size, int color) {
            textPaint.setColor(color);
            textPaint.setTextSize(size);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) * 0.5f, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                int index = event.getActionIndex();
                if (exitRect.contains(event.getX(index), event.getY(index))) {
                    releaseAllKeys();
                    pendingExitTap = true;
                    return true;
                }
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                pendingExitTap = false;
                releaseAllKeys();
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }

            if (pendingExitTap) {
                int index = event.getActionIndex();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                    boolean confirmed = exitRect.contains(event.getX(index), event.getY(index));
                    pendingExitTap = false;
                    releaseAllKeys();
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (confirmed && disconnectListener != null) {
                        post(disconnectListener);
                    }
                    performClick();
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    boolean stillInside = false;
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (exitRect.contains(event.getX(i), event.getY(i))) {
                            stillInside = true;
                            break;
                        }
                    }
                    if (!stillInside) {
                        pendingExitTap = false;
                    }
                    return true;
                }
            }

            computeStateFromTouches(event);
            if (action == MotionEvent.ACTION_UP) {
                performClick();
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private void computeStateFromTouches(MotionEvent event) {
            int action = event.getActionMasked();
            int liftedIndex = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                    ? event.getActionIndex()
                    : -1;
            for (KeyboardKey key : keys) key.pressed = false;

            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == liftedIndex) continue;
                float x = event.getX(i);
                float y = event.getY(i);
                if (exitRect.contains(x, y)) continue;
                KeyboardKey touchedKey = findKey(x, y);
                if (touchedKey != null) touchedKey.pressed = true;
            }

            int modifierMask = 0;
            int[] usageSlots = new int[6];
            int usageCount = 0;
            for (KeyboardKey key : keys) {
                if (!key.pressed) continue;
                if (key.isModifier()) {
                    modifierMask |= key.modifierMask;
                } else if (key.usage != 0 && !containsUsage(usageSlots, usageCount, key.usage) && usageCount < usageSlots.length) {
                    usageSlots[usageCount++] = key.usage;
                }
            }
            int[] usages = new int[usageCount];
            System.arraycopy(usageSlots, 0, usages, 0, usageCount);
            emitState(modifierMask, usages);
            invalidate();
        }

        private KeyboardKey findKey(float x, float y) {
            for (KeyboardKey key : keys) {
                if (key.rect.contains(x, y)) return key;
            }
            return null;
        }

        private boolean containsUsage(int[] usages, int count, int usage) {
            for (int i = 0; i < count; i++) {
                if (usages[i] == usage) return true;
            }
            return false;
        }

        private void releaseAllKeys() {
            for (KeyboardKey key : keys) key.pressed = false;
            emitState(0, new int[0]);
            invalidate();
        }

        private void emitState(int modifierMask, int[] keyboardUsages) {
            if (modifierMask == lastModifierMask && sameUsages(keyboardUsages, lastUsages)) return;
            if (vibrationEnabled && (modifierMask != 0 || keyboardUsages.length > 0)) {
                performHapticFeedback(triggerFeedbackConstant());
            }
            lastModifierMask = modifierMask;
            lastUsages = copyUsages(keyboardUsages);
            if (stateListener != null) stateListener.onStateChanged(modifierMask, copyUsages(keyboardUsages));
        }

        private boolean sameUsages(int[] left, int[] right) {
            if (left == right) return true;
            if (left == null || right == null || left.length != right.length) return false;
            for (int i = 0; i < left.length; i++) {
                if (left[i] != right[i]) return false;
            }
            return true;
        }

        private int[] copyUsages(int[] source) {
            if (source == null || source.length == 0) return new int[0];
            int[] copy = new int[source.length];
            System.arraycopy(source, 0, copy, 0, source.length);
            return copy;
        }

        private int triggerFeedbackConstant() {
            switch (vibrationLevel) {
                case VIBRATION_LIGHT:
                    return HapticFeedbackConstants.CLOCK_TICK;
                case VIBRATION_STRONG:
                    return HapticFeedbackConstants.LONG_PRESS;
                case VIBRATION_MEDIUM:
                default:
                    return HapticFeedbackConstants.VIRTUAL_KEY;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            releaseAllKeys();
            super.onDetachedFromWindow();
        }

        private class KeyboardRow {
            final List<KeyboardKey> keys = new ArrayList<>();

            float totalWeight() {
                float total = 0f;
                for (KeyboardKey key : keys) total += key.weight;
                return total;
            }
        }

        private class KeyboardKey {
            final String label;
            final int usage;
            final int modifierMask;
            final float weight;
            final RectF rect = new RectF();
            boolean pressed;

            KeyboardKey(String label, int usage, int modifierMask, float weight) {
                this.label = label;
                this.usage = usage;
                this.modifierMask = modifierMask;
                this.weight = weight;
            }

            boolean isModifier() {
                return modifierMask != 0;
            }
        }
    }

    private class GamepadView extends View {
        private static final int STATUS_ICON_LINK = 1;
        private static final int STATUS_ICON_BOLT = 2;
        private static final int STATUS_ICON_BATTERY = 3;
        private static final int HAT_UP = 0;
        private static final int HAT_UP_RIGHT = 1;
        private static final int HAT_RIGHT = 2;
        private static final int HAT_DOWN_RIGHT = 3;
        private static final int HAT_DOWN = 4;
        private static final int HAT_DOWN_LEFT = 5;
        private static final int HAT_LEFT = 6;
        private static final int HAT_UP_LEFT = 7;
        private static final int HAT_NEUTRAL = 8;
        private static final int AXIS_CENTER = 128;
        private static final long STICK_TAP_PULSE_MS = 70L;

        private final String controllerType;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF body = new RectF();
        private final RectF leftBody = new RectF();
        private final RectF rightBody = new RectF();
        private final RectF exitRect = new RectF();
        private final RectF leftShoulder = new RectF();
        private final RectF rightShoulder = new RectF();
        private final RectF leftTrigger = new RectF();
        private final RectF rightTrigger = new RectF();
        private final RectF leftStickDefault = new RectF();
        private final RectF rightStickDefault = new RectF();
        private final RectF leftStick = new RectF();
        private final RectF rightStick = new RectF();
        private final RectF leftStickClickRect = new RectF();
        private final RectF rightStickClickRect = new RectF();
        private final RectF dpad = new RectF();
        private final RectF statusRect = new RectF();
        private final RectF statusConnectedRect = new RectF();
        private final RectF statusModeRect = new RectF();
        private final RectF statusBatteryRect = new RectF();
        private final RectF vibrationToggleRect = new RectF();
        private final RectF vibrationThumbRect = new RectF();
        private final RectF stickModeRect = new RectF();
        private final RectF centerHomeRect = new RectF();
        private final RectF centerViewRect = new RectF();
        private final RectF centerMenuRect = new RectF();
        private final List<GamepadButton> buttons = new ArrayList<>();
        private final Paint softShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private GamepadStateListener stateListener;
        private Runnable disconnectListener;
        private boolean vibrationEnabled = true;
        private int vibrationLevel = VIBRATION_MEDIUM;
        private int lastButtons = -1;
        private int lastHat = -1;
        private int lastLx = -1;
        private int lastLy = -1;
        private int lastRx = -1;
        private int lastRy = -1;
        private int lastLt = -1;
        private int lastRt = -1;
        private boolean pendingExitTap = false;
        private float leftStickVisualX = 0f;
        private float leftStickVisualY = 0f;
        private float rightStickVisualX = 0f;
        private float rightStickVisualY = 0f;
        private long homePulseStartMs = 0L;
        private boolean standardUiActive = false;
        private int activeButtonsMask = 0;
        private float leftTriggerProgress = 0f;
        private float rightTriggerProgress = 0f;
        private float leftStickPressProgress = 0f;
        private float rightStickPressProgress = 0f;
        private float leftStickRenderX = 0f;
        private float leftStickRenderY = 0f;
        private float rightStickRenderX = 0f;
        private float rightStickRenderY = 0f;
        private float leftTriggerRenderProgress = 0f;
        private float rightTriggerRenderProgress = 0f;
        private float leftStickPressRenderProgress = 0f;
        private float rightStickPressRenderProgress = 0f;
        private float vibrationToggleRender = 1f;
        private String connectedDeviceLabel = "My Controller";
        private String batteryValue = "--%";
        private String protocolValue = "标准手柄";
        private String rumbleDebugValue = "未收到";
        private String stickClickMode = STICK_CLICK_MODE_TAP;
        private int leftStickPointerId = -1;
        private int rightStickPointerId = -1;
        private float leftStickDownX = 0f;
        private float leftStickDownY = 0f;
        private float rightStickDownX = 0f;
        private float rightStickDownY = 0f;
        private long leftStickDownTimeMs = 0L;
        private long rightStickDownTimeMs = 0L;
        private boolean leftStickClickCandidate = false;
        private boolean rightStickClickCandidate = false;
        private int pendingStickTapButtons = 0;
        private int currentTouchButtons = 0;
        private int currentTouchHat = HAT_NEUTRAL;
        private int currentTouchLx = AXIS_CENTER;
        private int currentTouchLy = AXIS_CENTER;
        private int currentTouchRx = AXIS_CENTER;
        private int currentTouchRy = AXIS_CENTER;
        private int currentTouchLt = 0;
        private int currentTouchRt = 0;
        private StickClickModeListener stickClickModeListener;
        private final Runnable stickTapReleaseRunnable = new Runnable() {
            @Override
            public void run() {
                pendingStickTapButtons = 0;
                emitCurrentTouchState();
            }
        };

        interface StickClickModeListener {
            void onStickClickModeChanged(String mode);
        }

        GamepadView(Context context, String controllerType) {
            super(context);
            this.controllerType = controllerType;
            setFocusable(true);
            setClickable(true);
            setBackgroundColor(Color.rgb(246, 244, 240));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }

        void setStateListener(GamepadStateListener stateListener) {
            this.stateListener = stateListener;
            emitState(0, HAT_NEUTRAL, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, 0, 0);
        }

        void setConnectedDeviceLabel(String label) {
            connectedDeviceLabel = label == null || label.trim().isEmpty() ? "My Controller" : label;
        }

        void setDisconnectListener(Runnable disconnectListener) {
            this.disconnectListener = disconnectListener;
        }

        void setVibrationEnabled(boolean vibrationEnabled) {
            this.vibrationEnabled = vibrationEnabled;
            vibrationToggleRender = vibrationEnabled ? 1f : 0f;
            updateVibrationThumbRect(false);
        }

        void setVibrationLevel(int vibrationLevel) {
            this.vibrationLevel = vibrationLevel;
        }

        void setStickClickMode(String stickClickMode) {
            this.stickClickMode = STICK_CLICK_MODE_BUTTON.equals(stickClickMode)
                    ? STICK_CLICK_MODE_BUTTON
                    : STICK_CLICK_MODE_TAP;
            invalidate();
        }

        void setStickClickModeListener(StickClickModeListener listener) {
            this.stickClickModeListener = listener;
        }

        void refreshSystemStatus() {
            batteryValue = readBatteryPercentLabel();
            if (hidService != null) {
                rumbleDebugValue = hidService.getLastRumbleDebugText();
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            layoutControls(w, h);
        }

        private void layoutControls(float w, float h) {
            buttons.clear();
            standardUiActive = KEYBOARD_TYPE_GAMEPAD.equals(controllerType);
            if (standardUiActive) {
                exitRect.set(w * 0.885f, h * 0.874f, w * 0.975f, h * 0.948f);
            } else {
                exitRect.set(w * 0.45f, h * 0.035f, w * 0.55f, h * 0.105f);
            }

            if (KEYBOARD_TYPE_GAMEPAD.equals(controllerType)) {
                layoutStandardGamepad(w, h);
            } else if (KEYBOARD_TYPE_JOYCON_PAIR.equals(controllerType)) {
                layoutJoyconPair(w, h);
            } else if (KEYBOARD_TYPE_JOYCON_LEFT.equals(controllerType)) {
                layoutSingleJoycon(w, h, true);
            } else {
                layoutSingleJoycon(w, h, false);
            }
        }

        private void layoutStandardGamepad(float w, float h) {
            body.set(w * 0.02f, h * 0.03f, w * 0.98f, h * 0.97f);
            leftBody.setEmpty();
            rightBody.setEmpty();

            float shoulderW = w * 0.092f;
            float shoulderH = h * 0.098f;
            float shoulderGap = w * 0.016f;
            leftShoulder.set(w * 0.026f, h * 0.055f, w * 0.026f + shoulderW, h * 0.055f + shoulderH);
            leftTrigger.set(leftShoulder.right + shoulderGap, leftShoulder.top, leftShoulder.right + shoulderGap + shoulderW, leftShoulder.bottom);
            rightTrigger.set(w * 0.785f, leftShoulder.top, w * 0.785f + shoulderW, leftShoulder.bottom);
            rightShoulder.set(rightTrigger.right + shoulderGap, leftShoulder.top, rightTrigger.right + shoulderGap + shoulderW, leftShoulder.bottom);

            float statusW = w * 0.60f;
            float statusH = h * 0.112f;
            statusRect.set((w - statusW) * 0.5f, h * 0.050f, (w + statusW) * 0.5f, h * 0.050f + statusH);
            float quarter = statusRect.width() / 4f;
            statusConnectedRect.set(statusRect.left, statusRect.top, statusRect.left + quarter, statusRect.bottom);
            statusModeRect.set(statusConnectedRect.right, statusRect.top, statusConnectedRect.right + quarter, statusRect.bottom);
            statusBatteryRect.set(statusModeRect.right, statusRect.top, statusModeRect.right + quarter, statusRect.bottom);

            float stickR = Math.min(w, h) * 0.129f;
            setCircle(leftStickDefault, w * 0.160f, h * 0.415f, stickR);
            setCircle(rightStickDefault, w * 0.726f, h * 0.718f, stickR);
            leftStick.set(leftStickDefault);
            rightStick.set(rightStickDefault);
            float dpadSize = Math.min(w * 0.148f, h * 0.315f);
            dpad.set(w * 0.273f - dpadSize * 0.5f, h * 0.700f - dpadSize * 0.5f,
                    w * 0.273f + dpadSize * 0.5f, h * 0.700f + dpadSize * 0.5f);

            centerViewRect.set(w * 0.355f, h * 0.485f, w * 0.458f, h * 0.585f);
            centerHomeRect.set(w * 0.462f, h * 0.485f, w * 0.538f, h * 0.602f);
            centerMenuRect.set(w * 0.542f, h * 0.485f, w * 0.645f, h * 0.585f);

            float faceR = Math.min(w, h) * 0.064f;
            addButton("Y", HidAction.GAMEPAD_BUTTON_Y, w * 0.839f, h * 0.296f, faceR, Color.WHITE);
            addButton("X", HidAction.GAMEPAD_BUTTON_X, w * 0.785f, h * 0.414f, faceR, Color.WHITE);
            addButton("B", HidAction.GAMEPAD_BUTTON_B, w * 0.895f, h * 0.414f, faceR, Color.WHITE);
            addButton("A", HidAction.GAMEPAD_BUTTON_A, w * 0.839f, h * 0.531f, faceR, Color.WHITE);

            float toggleW = w * 0.182f;
            float toggleH = h * 0.080f;
            vibrationToggleRect.set((w - toggleW) * 0.5f, h * 0.832f, (w + toggleW) * 0.5f, h * 0.832f + toggleH);
            stickModeRect.set(w * 0.392f, h * 0.734f, w * 0.608f, h * 0.805f);
            float clickButtonSize = Math.min(w, h) * 0.049f;
            setCircle(leftStickClickRect, w * 0.160f, h * 0.590f, clickButtonSize);
            setCircle(rightStickClickRect, w * 0.726f, h * 0.560f, clickButtonSize);
            updateVibrationThumbRect(false);
        }

        private void layoutJoyconPair(float w, float h) {
            leftBody.set(w * 0.045f, h * 0.10f, w * 0.43f, h * 0.92f);
            rightBody.set(w * 0.57f, h * 0.10f, w * 0.955f, h * 0.92f);
            body.setEmpty();
            leftShoulder.set(w * 0.08f, h * 0.055f, w * 0.38f, h * 0.15f);
            leftTrigger.set(w * 0.08f, h * 0.16f, w * 0.38f, h * 0.24f);
            rightShoulder.set(w * 0.62f, h * 0.055f, w * 0.92f, h * 0.15f);
            rightTrigger.set(w * 0.62f, h * 0.16f, w * 0.92f, h * 0.24f);

            float r = Math.min(w, h) * 0.105f;
            setCircle(leftStick, w * 0.22f, h * 0.43f, r);
            setCircle(rightStick, w * 0.79f, h * 0.66f, r);
            setCircle(dpad, w * 0.29f, h * 0.68f, Math.min(w, h) * 0.12f);
            addFaceButtons(w * 0.72f, h * 0.38f, Math.min(w, h) * 0.058f, Math.min(w, h) * 0.088f);
            addButton("-", HidAction.GAMEPAD_BUTTON_MINUS, w * 0.33f, h * 0.28f, Math.min(w, h) * 0.035f, Color.rgb(232, 236, 245));
            addButton("+", HidAction.GAMEPAD_BUTTON_PLUS, w * 0.67f, h * 0.28f, Math.min(w, h) * 0.035f, Color.rgb(232, 236, 245));
            addButton("□", HidAction.GAMEPAD_BUTTON_CAPTURE, w * 0.19f, h * 0.78f, Math.min(w, h) * 0.034f, Color.rgb(232, 236, 245));
            addButton("⌂", HidAction.GAMEPAD_BUTTON_HOME, w * 0.86f, h * 0.50f, Math.min(w, h) * 0.034f, Color.rgb(232, 236, 245));
        }

        private void layoutSingleJoycon(float w, float h, boolean left) {
            float joyW = Math.min(w * 0.62f, h * 0.38f);
            float joyH = Math.min(h * 0.90f, joyW * 2.95f);
            float cx = w * 0.5f;
            body.set(cx - joyW * 0.5f, h * 0.05f, cx + joyW * 0.5f, h * 0.05f + joyH);
            leftBody.setEmpty();
            rightBody.setEmpty();

            float r = joyW * 0.18f;
            float clusterR = joyW * 0.12f;
            if (left) {
                leftShoulder.set(body.left + joyW * 0.16f, body.top + joyH * 0.015f, body.right - joyW * 0.16f, body.top + joyH * 0.075f);
                leftTrigger.set(body.left + joyW * 0.16f, body.top + joyH * 0.085f, body.right - joyW * 0.16f, body.top + joyH * 0.14f);
                rightShoulder.setEmpty();
                rightTrigger.setEmpty();
                setCircle(leftStick, cx, body.top + joyH * 0.30f, r);
                rightStick.setEmpty();
                setCircle(dpad, cx, body.top + joyH * 0.61f, joyW * 0.20f);
                addButton("-", HidAction.GAMEPAD_BUTTON_MINUS, body.right - joyW * 0.26f, body.top + joyH * 0.19f, joyW * 0.065f, Color.rgb(232, 236, 245));
                addButton("□", HidAction.GAMEPAD_BUTTON_CAPTURE, cx, body.top + joyH * 0.78f, joyW * 0.07f, Color.rgb(232, 236, 245));
            } else {
                leftShoulder.setEmpty();
                leftTrigger.setEmpty();
                rightShoulder.set(body.left + joyW * 0.16f, body.top + joyH * 0.015f, body.right - joyW * 0.16f, body.top + joyH * 0.075f);
                rightTrigger.set(body.left + joyW * 0.16f, body.top + joyH * 0.085f, body.right - joyW * 0.16f, body.top + joyH * 0.14f);
                leftStick.setEmpty();
                dpad.setEmpty();
                setCircle(rightStick, cx, body.top + joyH * 0.64f, r);
                addFaceButtons(cx, body.top + joyH * 0.33f, clusterR, joyW * 0.17f);
                addButton("+", HidAction.GAMEPAD_BUTTON_PLUS, body.left + joyW * 0.26f, body.top + joyH * 0.19f, joyW * 0.065f, Color.rgb(232, 236, 245));
                addButton("⌂", HidAction.GAMEPAD_BUTTON_HOME, cx, body.top + joyH * 0.80f, joyW * 0.07f, Color.rgb(232, 236, 245));
            }
            addButton("SL", HidAction.GAMEPAD_BUTTON_SL, body.left + joyW * 0.18f, body.top + joyH * 0.92f, joyW * 0.08f, Color.rgb(232, 236, 245));
            addButton("SR", HidAction.GAMEPAD_BUTTON_SR, body.right - joyW * 0.18f, body.top + joyH * 0.92f, joyW * 0.08f, Color.rgb(232, 236, 245));
        }

        private void addFaceButtons(float cx, float cy, float radius, float spread) {
            addButton("X", HidAction.GAMEPAD_BUTTON_X, cx, cy - spread, radius, Color.rgb(245, 248, 255));
            addButton("B", HidAction.GAMEPAD_BUTTON_B, cx, cy + spread, radius, Color.rgb(245, 248, 255));
            addButton("Y", HidAction.GAMEPAD_BUTTON_Y, cx - spread, cy, radius, Color.rgb(245, 248, 255));
            addButton("A", HidAction.GAMEPAD_BUTTON_A, cx + spread, cy, radius, Color.rgb(245, 248, 255));
        }

        private void addButton(String label, int bit, float cx, float cy, float radius, int color) {
            buttons.add(new GamepadButton(label, bit, cx, cy, radius, color));
        }

        private void setCircle(RectF rect, float cx, float cy, float radius) {
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (standardUiActive) {
                drawStandardGamepad(canvas);
                return;
            }
            canvas.drawColor(Color.rgb(10, 12, 18));
            if (!body.isEmpty()) {
                int bodyColor = Color.rgb(32, 35, 44);
                if (KEYBOARD_TYPE_JOYCON_LEFT.equals(controllerType)) bodyColor = Color.rgb(36, 156, 196);
                if (KEYBOARD_TYPE_JOYCON_RIGHT.equals(controllerType)) bodyColor = Color.rgb(226, 58, 82);
                drawBody(canvas, body, bodyColor);
            }
            if (!leftBody.isEmpty()) drawBody(canvas, leftBody, Color.rgb(36, 156, 196));
            if (!rightBody.isEmpty()) drawBody(canvas, rightBody, Color.rgb(226, 58, 82));
            drawShoulders(canvas);
            if (!leftStick.isEmpty()) drawStick(canvas, leftStick, "L", leftStickVisualX, leftStickVisualY);
            if (!rightStick.isEmpty()) drawStick(canvas, rightStick, "R", rightStickVisualX, rightStickVisualY);
            if (!dpad.isEmpty()) drawDpad(canvas, dpad);
            for (GamepadButton button : buttons) drawButton(canvas, button);
            drawExit(canvas);
        }

        private void drawStandardGamepad(Canvas canvas) {
            updateStandardUiAnimation();
            canvas.drawColor(Color.rgb(247, 245, 241));
            drawAmbientBackground(canvas);
            drawStatusPanel(canvas);
            drawShoulderCard(canvas, leftShoulder, "LB", isButtonActive(HidAction.GAMEPAD_BUTTON_L1), 0f);
            drawShoulderCard(canvas, leftTrigger, "LT", isButtonActive(HidAction.GAMEPAD_BUTTON_L2), leftTriggerRenderProgress);
            drawShoulderCard(canvas, rightTrigger, "RT", isButtonActive(HidAction.GAMEPAD_BUTTON_R2), rightTriggerRenderProgress);
            drawShoulderCard(canvas, rightShoulder, "RB", isButtonActive(HidAction.GAMEPAD_BUTTON_R1), 0f);
            drawStandardStick(canvas, leftStick, leftStickRenderX, leftStickRenderY, leftStickPressRenderProgress);
            drawStandardStick(canvas, rightStick, rightStickRenderX, rightStickRenderY, rightStickPressRenderProgress);
            drawStandardDpad(canvas, dpad, lastHat);
            drawCenterButton(canvas, centerViewRect, "View", false, isButtonActive(HidAction.GAMEPAD_BUTTON_MINUS));
            drawHomeButton(canvas, centerHomeRect, isButtonActive(HidAction.GAMEPAD_BUTTON_HOME));
            drawCenterButton(canvas, centerMenuRect, "Menu", true, isButtonActive(HidAction.GAMEPAD_BUTTON_PLUS));
            drawStickModeToggle(canvas);
            if (STICK_CLICK_MODE_BUTTON.equals(stickClickMode)) {
                drawStickClickButton(canvas, leftStickClickRect, "L3", isButtonActive(HidAction.GAMEPAD_BUTTON_L3));
                drawStickClickButton(canvas, rightStickClickRect, "R3", isButtonActive(HidAction.GAMEPAD_BUTTON_R3));
            }
            for (GamepadButton button : buttons) drawStandardFaceButton(canvas, button);
            drawVibrationToggle(canvas);
            drawExitStandard(canvas);
            if (homePulseStartMs > 0L && SystemClock.uptimeMillis() - homePulseStartMs < 220L) {
                postInvalidateOnAnimation();
            }
        }

        private void drawBody(Canvas canvas, RectF rect, int color) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(color);
            canvas.drawRoundRect(rect, rect.width() * 0.16f, rect.width() * 0.16f, fillPaint);
            strokePaint.setColor(Color.argb(80, 255, 255, 255));
            strokePaint.setStrokeWidth(dp(1.2f));
            canvas.drawRoundRect(rect, rect.width() * 0.16f, rect.width() * 0.16f, strokePaint);
        }

        private void drawAmbientBackground(Canvas canvas) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(26, 219, 214, 205));
            canvas.drawCircle(getWidth() * 0.16f, getHeight() * 0.26f, Math.min(getWidth(), getHeight()) * 0.16f, fillPaint);
            canvas.drawCircle(getWidth() * 0.80f, getHeight() * 0.78f, Math.min(getWidth(), getHeight()) * 0.18f, fillPaint);
        }

        private void drawStatusPanel(Canvas canvas) {
            drawSoftRaisedCard(canvas, statusRect, 1f, dp(30));
            float dividerTop = statusRect.top + statusRect.height() * 0.22f;
            float dividerBottom = statusRect.bottom - statusRect.height() * 0.22f;
            strokePaint.setColor(Color.argb(28, 70, 74, 82));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawLine(statusConnectedRect.right, dividerTop, statusConnectedRect.right, dividerBottom, strokePaint);
            canvas.drawLine(statusModeRect.right, dividerTop, statusModeRect.right, dividerBottom, strokePaint);
            canvas.drawLine(statusBatteryRect.right, dividerTop, statusBatteryRect.right, dividerBottom, strokePaint);
            drawStatusBlock(canvas, statusConnectedRect, "已连接", "设备 : " + connectedDeviceLabel, STATUS_ICON_LINK, Color.rgb(115, 121, 130));
            drawStatusBlock(canvas, statusModeRect, "通用协议", protocolValue, STATUS_ICON_BOLT, Color.rgb(151, 153, 157));
            drawStatusBlock(canvas, statusBatteryRect, "电量", batteryValue, STATUS_ICON_BATTERY, Color.rgb(143, 145, 150));
            RectF rumbleRect = new RectF(statusBatteryRect.right, statusRect.top, statusRect.right, statusRect.bottom);
            drawStatusBlock(canvas, rumbleRect, "振动包", rumbleDebugValue, STATUS_ICON_BOLT, Color.rgb(221, 146, 64));
        }

        private void drawStatusBlock(Canvas canvas, RectF rect, String title, String subtitle, int iconType, int iconColor) {
            float iconCx = rect.left + rect.width() * 0.19f;
            float iconCy = rect.centerY();
            drawStatusIcon(canvas, iconCx, iconCy, rect.height() * 0.19f, iconType, iconColor);
            labelPaint.setTextAlign(Paint.Align.LEFT);
            drawLabelText(canvas, title, rect.left + rect.width() * 0.31f, rect.top + rect.height() * 0.43f, rect.height() * 0.20f, Color.rgb(92, 94, 98), true);
            drawLabelText(canvas, subtitle, rect.left + rect.width() * 0.31f, rect.top + rect.height() * 0.69f, rect.height() * 0.17f, Color.rgb(120, 122, 127), false);
            labelPaint.setTextAlign(Paint.Align.CENTER);
        }

        private void drawStatusIcon(Canvas canvas, float cx, float cy, float size, int type, int color) {
            strokePaint.setColor(color);
            strokePaint.setStrokeWidth(dp(1.7f));
            if (type == STATUS_ICON_LINK) {
                RectF left = new RectF(cx - size * 1.25f, cy - size * 0.55f, cx - size * 0.15f, cy + size * 0.55f);
                RectF right = new RectF(cx + size * 0.15f, cy - size * 0.55f, cx + size * 1.25f, cy + size * 0.55f);
                canvas.drawArc(left, 315, 220, false, strokePaint);
                canvas.drawArc(right, 135, 220, false, strokePaint);
            } else if (type == STATUS_ICON_BOLT) {
                Path bolt = new Path();
                bolt.moveTo(cx + size * 0.04f, cy - size * 1.08f);
                bolt.lineTo(cx - size * 0.52f, cy - size * 0.04f);
                bolt.lineTo(cx - size * 0.08f, cy - size * 0.04f);
                bolt.lineTo(cx - size * 0.28f, cy + size * 1.06f);
                bolt.lineTo(cx + size * 0.56f, cy - size * 0.18f);
                bolt.lineTo(cx + size * 0.12f, cy - size * 0.18f);
                bolt.close();
                fillPaint.setColor(color);
                canvas.drawPath(bolt, fillPaint);
            } else {
                RectF phone = new RectF(cx - size * 0.48f, cy - size * 0.88f, cx + size * 0.48f, cy + size * 0.88f);
                canvas.drawRoundRect(phone, size * 0.16f, size * 0.16f, strokePaint);
                canvas.drawLine(cx - size * 0.18f, cy + size * 0.62f, cx + size * 0.18f, cy + size * 0.62f, strokePaint);
            }
        }

        private void drawShoulderCard(Canvas canvas, RectF rect, String label, boolean pressed, float progress) {
            drawSoftRaisedCard(canvas, rect, pressed ? 0.97f : 1f, dp(18));
            float translate = pressed ? dp(2f) : 0f;
            float cx = rect.centerX();
            float cy = rect.centerY() + translate;
            drawText(canvas, label, cx, cy - rect.height() * 0.10f, rect.height() * 0.28f, Color.rgb(163, 165, 171));
            strokePaint.setColor(Color.rgb(182, 184, 188));
            strokePaint.setStrokeWidth(dp(2f));
            float lineW = rect.width() * (progress > 0f ? (0.18f + 0.16f * progress) : 0.12f);
            canvas.drawLine(cx - lineW * 0.5f, cy + rect.height() * 0.23f, cx + lineW * 0.5f, cy + rect.height() * 0.23f, strokePaint);
        }

        private void drawStandardStick(Canvas canvas, RectF rect, float axisX, float axisY, float pressProgress) {
            float radius = rect.width() * 0.5f;
            float outerR = radius * 1.02f;
            float knobTravel = radius * 0.31f;
            float knobCx = rect.centerX() + axisX * knobTravel;
            float knobCy = rect.centerY() + axisY * knobTravel;
            float knobRadius = radius * (0.50f - 0.016f * pressProgress);

            softShadowPaint.setColor(Color.argb(24, 151, 147, 141));
            canvas.drawCircle(rect.centerX(), rect.centerY() + dp(9f), outerR * 1.01f, softShadowPaint);
            fillPaint.setColor(Color.rgb(252, 251, 248));
            canvas.drawCircle(rect.centerX(), rect.centerY(), outerR, fillPaint);
            strokePaint.setColor(Color.argb(24, 141, 140, 137));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawCircle(rect.centerX(), rect.centerY(), outerR, strokePaint);

            float ringRadius = radius * 0.72f;
            strokePaint.setColor(Color.rgb(208, 205, 199));
            strokePaint.setStrokeWidth(dp(5.2f));
            canvas.drawCircle(rect.centerX(), rect.centerY(), ringRadius, strokePaint);
            drawStickMarkers(canvas, rect.centerX(), rect.centerY(), outerR, axisX, axisY);

            fillPaint.setColor(Color.rgb(250, 249, 246));
            canvas.drawCircle(knobCx, knobCy + pressProgress * dp(1.6f), knobRadius, fillPaint);
            strokePaint.setColor(Color.argb(24, 155, 154, 150));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawCircle(knobCx, knobCy + pressProgress * dp(1.6f), knobRadius, strokePaint);
            drawStickDots(canvas, knobCx, knobCy + pressProgress * dp(1.6f), knobRadius);
        }

        private void drawStickMarkers(Canvas canvas, float cx, float cy, float radius, float axisX, float axisY) {
            drawTriangleMarker(canvas, cx, cy - radius * 0.94f, 0f, axisY < -0.14f ? 1f : 0f);
            drawTriangleMarker(canvas, cx + radius * 0.94f, cy, 90f, axisX > 0.14f ? 1f : 0f);
            drawTriangleMarker(canvas, cx, cy + radius * 0.94f, 180f, axisY > 0.14f ? 1f : 0f);
            drawTriangleMarker(canvas, cx - radius * 0.94f, cy, 270f, axisX < -0.14f ? 1f : 0f);
        }

        private void drawTriangleMarker(Canvas canvas, float cx, float cy, float angle, float active) {
            float size = dp(6f);
            Path path = new Path();
            path.moveTo(0f, -size * 0.55f);
            path.lineTo(size * 0.45f, size * 0.45f);
            path.lineTo(-size * 0.45f, size * 0.45f);
            path.close();
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);
            fillPaint.setColor(blendColor(Color.rgb(206, 205, 202), Color.rgb(166, 165, 164), active));
            canvas.drawPath(path, fillPaint);
            canvas.restore();
        }

        private void drawStickDots(Canvas canvas, float cx, float cy, float radius) {
            fillPaint.setColor(Color.argb(30, 180, 177, 172));
            float gap = radius * 0.20f;
            float dotR = Math.max(1f, radius * 0.028f);
            for (int row = -3; row <= 3; row++) {
                for (int col = -3; col <= 3; col++) {
                    float dx = col * gap;
                    float dy = row * gap;
                    if (dx * dx + dy * dy <= radius * radius * 0.46f) {
                        canvas.drawCircle(cx + dx, cy + dy, dotR, fillPaint);
                    }
                }
            }
        }

        private void drawStandardDpad(Canvas canvas, RectF rect, int hat) {
            float cx = rect.centerX();
            float cy = rect.centerY();
            float size = Math.min(rect.width(), rect.height());
            float armLong = size * 0.92f;
            float armShort = size * 0.40f;
            RectF vertical = new RectF(cx - armShort * 0.5f, cy - armLong * 0.5f, cx + armShort * 0.5f, cy + armLong * 0.5f);
            RectF horizontal = new RectF(cx - armLong * 0.5f, cy - armShort * 0.5f, cx + armLong * 0.5f, cy + armShort * 0.5f);

            softShadowPaint.setColor(Color.argb(22, 150, 146, 140));
            canvas.drawRoundRect(new RectF(vertical.left, vertical.top + dp(8f), vertical.right, vertical.bottom + dp(8f)),
                    armShort * 0.42f, armShort * 0.42f, softShadowPaint);
            canvas.drawRoundRect(new RectF(horizontal.left, horizontal.top + dp(8f), horizontal.right, horizontal.bottom + dp(8f)),
                    armShort * 0.42f, armShort * 0.42f, softShadowPaint);

            fillPaint.setColor(Color.rgb(252, 251, 248));
            canvas.drawRoundRect(vertical, armShort * 0.42f, armShort * 0.42f, fillPaint);
            canvas.drawRoundRect(horizontal, armShort * 0.42f, armShort * 0.42f, fillPaint);
            strokePaint.setColor(Color.argb(18, 130, 128, 125));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawRoundRect(vertical, armShort * 0.42f, armShort * 0.42f, strokePaint);
            canvas.drawRoundRect(horizontal, armShort * 0.42f, armShort * 0.42f, strokePaint);

            drawDpadPressOverlay(canvas, cx, cy - size * 0.315f, armShort * 0.76f, armLong * 0.34f,
                    hat == HAT_UP || hat == HAT_UP_LEFT || hat == HAT_UP_RIGHT, 0f);
            drawDpadPressOverlay(canvas, cx, cy + size * 0.315f, armShort * 0.76f, armLong * 0.34f,
                    hat == HAT_DOWN || hat == HAT_DOWN_LEFT || hat == HAT_DOWN_RIGHT, 180f);
            drawDpadPressOverlay(canvas, cx - size * 0.315f, cy, armLong * 0.34f, armShort * 0.76f,
                    hat == HAT_LEFT || hat == HAT_UP_LEFT || hat == HAT_DOWN_LEFT, 270f);
            drawDpadPressOverlay(canvas, cx + size * 0.315f, cy, armLong * 0.34f, armShort * 0.76f,
                    hat == HAT_RIGHT || hat == HAT_UP_RIGHT || hat == HAT_DOWN_RIGHT, 90f);
        }

        private void drawDpadPressOverlay(Canvas canvas, float cx, float cy, float width, float height, boolean pressed, float angle) {
            RectF overlay = new RectF(cx - width * 0.5f, cy - height * 0.5f, cx + width * 0.5f, cy + height * 0.5f);
            if (pressed) {
                fillPaint.setColor(Color.argb(26, 212, 210, 206));
                canvas.drawRoundRect(overlay, Math.min(width, height) * 0.42f, Math.min(width, height) * 0.42f, fillPaint);
            }
            drawTriangleMarker(canvas, cx, cy - Math.max(width, height) * 0.08f, angle, pressed ? 1f : 0.30f);
        }

        private void drawCenterButton(Canvas canvas, RectF rect, String label, boolean menu, boolean pressed) {
            drawSoftRaisedCard(canvas, rect, pressed ? 0.97f : 1f, rect.height() * 0.48f);
            float iconCx = rect.left + rect.width() * 0.28f;
            float cy = rect.centerY() + (pressed ? dp(2f) : 0f);
            detailPaint.setColor(Color.rgb(164, 165, 170));
            detailPaint.setStrokeWidth(dp(2f));
            if (menu) {
                canvas.drawLine(iconCx - dp(6f), cy - dp(5f), iconCx + dp(6f), cy - dp(5f), detailPaint);
                canvas.drawLine(iconCx - dp(6f), cy, iconCx + dp(6f), cy, detailPaint);
                canvas.drawLine(iconCx - dp(6f), cy + dp(5f), iconCx + dp(6f), cy + dp(5f), detailPaint);
            } else {
                RectF back = new RectF(iconCx - dp(9f), cy - dp(7f), iconCx + dp(1f), cy + dp(3f));
                RectF front = new RectF(iconCx - dp(1f), cy - dp(3f), iconCx + dp(9f), cy + dp(7f));
                strokePaint.setColor(Color.rgb(170, 171, 176));
                strokePaint.setStrokeWidth(dp(1.5f));
                canvas.drawRoundRect(back, dp(2f), dp(2f), strokePaint);
                canvas.drawRoundRect(front, dp(2f), dp(2f), strokePaint);
            }
            drawLabelText(canvas, label, rect.left + rect.width() * 0.64f, cy + dp(1f), rect.height() * 0.30f, Color.rgb(138, 139, 144), false);
        }

        private void drawHomeButton(Canvas canvas, RectF rect, boolean pressed) {
            float elapsed = homePulseStartMs > 0L ? (SystemClock.uptimeMillis() - homePulseStartMs) / 180f : 2f;
            float pulse = elapsed <= 1f ? (1f - elapsed) : 0f;
            if (pulse > 0f) {
                fillPaint.setColor(Color.argb((int) (pulse * 28f), 143, 199, 126));
                canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * (0.72f + 0.18f * (1f - pulse)), fillPaint);
            }
            drawSoftRaisedCard(canvas, rect, pressed ? 0.97f : 1f, rect.width() * 0.5f);
            drawXboxGlyph(canvas, rect.centerX(), rect.centerY() + (pressed ? dp(1.5f) : 0f), rect.width() * 0.22f,
                    blendColor(Color.rgb(122, 199, 94), Color.rgb(132, 214, 102), pressed ? 0.6f : 0f));
        }

        private void drawXboxGlyph(Canvas canvas, float cx, float cy, float radius, int color) {
            strokePaint.setColor(color);
            strokePaint.setStrokeWidth(dp(2.4f));
            strokePaint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, radius * 1.02f, strokePaint);
            canvas.drawLine(cx, cy - radius * 0.92f, cx - radius * 0.56f, cy + radius * 0.08f, strokePaint);
            canvas.drawLine(cx, cy - radius * 0.92f, cx + radius * 0.56f, cy + radius * 0.08f, strokePaint);
            canvas.drawLine(cx - radius * 0.56f, cy + radius * 0.08f, cx - radius * 0.18f, cy + radius * 0.70f, strokePaint);
            canvas.drawLine(cx + radius * 0.56f, cy + radius * 0.08f, cx + radius * 0.18f, cy + radius * 0.70f, strokePaint);
            strokePaint.setStyle(Paint.Style.FILL);
        }

        private void drawStandardFaceButton(Canvas canvas, GamepadButton button) {
            boolean pressed = isButtonActive(button.bit);
            float scale = pressed ? 0.96f : 1f;
            float translateY = pressed ? dp(2f) : 0f;
            float shadowOffset = pressed ? dp(3f) : dp(8f);
            float radius = button.radius * scale;
            softShadowPaint.setColor(Color.argb(22, 145, 141, 135));
            canvas.drawCircle(button.cx, button.cy + shadowOffset, radius * 1.04f, softShadowPaint);
            fillPaint.setColor(Color.rgb(252, 251, 248));
            canvas.drawCircle(button.cx, button.cy + translateY, radius, fillPaint);
            strokePaint.setColor(Color.argb(20, 126, 124, 120));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawCircle(button.cx, button.cy + translateY, radius, strokePaint);
            int labelColor = faceButtonColor(button.label, pressed);
            drawText(canvas, button.label, button.cx, button.cy + translateY, radius * 0.76f, labelColor);
        }

        private int faceButtonColor(String label, boolean pressed) {
            if ("A".equals(label)) return pressed ? Color.rgb(104, 190, 76) : Color.rgb(126, 197, 98);
            if ("B".equals(label)) return pressed ? Color.rgb(242, 110, 70) : Color.rgb(232, 126, 92);
            if ("X".equals(label)) return pressed ? Color.rgb(92, 144, 235) : Color.rgb(114, 157, 230);
            return pressed ? Color.rgb(240, 188, 44) : Color.rgb(232, 193, 74);
        }

        private void drawVibrationToggle(Canvas canvas) {
            drawSoftRaisedCard(canvas, vibrationToggleRect, 1f, vibrationToggleRect.height() * 0.5f);
            drawToggleIcon(canvas, vibrationToggleRect.left + vibrationToggleRect.width() * 0.14f, vibrationToggleRect.centerY());
            drawLabelText(canvas, "震动反馈", vibrationToggleRect.left + vibrationToggleRect.width() * 0.41f,
                    vibrationToggleRect.centerY(), vibrationToggleRect.height() * 0.30f, Color.rgb(142, 143, 147), false);
            fillPaint.setColor(blendColor(Color.rgb(234, 234, 232), Color.rgb(199, 214, 255), vibrationToggleRender));
            RectF track = new RectF(vibrationToggleRect.right - vibrationToggleRect.width() * 0.26f,
                    vibrationToggleRect.top + vibrationToggleRect.height() * 0.23f,
                    vibrationToggleRect.right - vibrationToggleRect.width() * 0.06f,
                    vibrationToggleRect.bottom - vibrationToggleRect.height() * 0.23f);
            canvas.drawRoundRect(track, track.height() * 0.5f, track.height() * 0.5f, fillPaint);
            fillPaint.setColor(Color.WHITE);
            canvas.drawCircle(vibrationThumbRect.centerX(), vibrationThumbRect.centerY(), vibrationThumbRect.width() * 0.5f, fillPaint);
            strokePaint.setColor(Color.argb(20, 120, 120, 126));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawCircle(vibrationThumbRect.centerX(), vibrationThumbRect.centerY(), vibrationThumbRect.width() * 0.5f, strokePaint);
        }

        private void drawToggleIcon(Canvas canvas, float cx, float cy) {
            strokePaint.setColor(Color.rgb(167, 169, 174));
            strokePaint.setStrokeWidth(dp(2f));
            canvas.drawLine(cx - dp(7f), cy, cx - dp(7f), cy + dp(1f), strokePaint);
            canvas.drawLine(cx - dp(2f), cy - dp(5f), cx - dp(2f), cy + dp(5f), strokePaint);
            canvas.drawLine(cx + dp(3f), cy - dp(8f), cx + dp(3f), cy + dp(8f), strokePaint);
            canvas.drawLine(cx + dp(8f), cy - dp(3f), cx + dp(8f), cy + dp(3f), strokePaint);
        }

        private void drawStickModeToggle(Canvas canvas) {
            drawSoftRaisedCard(canvas, stickModeRect, 1f, stickModeRect.height() * 0.5f);
            String title = STICK_CLICK_MODE_BUTTON.equals(stickClickMode) ? "摇杆按键" : "点按摇杆";
            String subtitle = STICK_CLICK_MODE_BUTTON.equals(stickClickMode) ? "L3 / R3 独立按钮" : "单击摇杆触发";
            drawLabelText(canvas, title, stickModeRect.centerX(), stickModeRect.top + stickModeRect.height() * 0.38f,
                    stickModeRect.height() * 0.26f, Color.rgb(96, 98, 103), true);
            drawLabelText(canvas, subtitle, stickModeRect.centerX(), stickModeRect.top + stickModeRect.height() * 0.68f,
                    stickModeRect.height() * 0.20f, Color.rgb(142, 145, 150), false);
        }

        private void drawStickClickButton(Canvas canvas, RectF rect, String label, boolean pressed) {
            drawSoftRaisedCard(canvas, rect, pressed ? 0.96f : 1f, rect.width() * 0.5f);
            drawText(canvas, label, rect.centerX(), rect.centerY() + (pressed ? dp(1.6f) : 0f),
                    rect.height() * 0.28f, pressed ? Color.rgb(120, 122, 126) : Color.rgb(160, 162, 167));
        }

        private void drawExitStandard(Canvas canvas) {
            drawSoftRaisedCard(canvas, exitRect, 1f, exitRect.height() * 0.5f);
            drawText(canvas, "退出", exitRect.centerX(), exitRect.centerY(), exitRect.height() * 0.28f, Color.rgb(158, 160, 165));
        }

        private void drawSoftRaisedCard(Canvas canvas, RectF rect, float scale, float radius) {
            float cx = rect.centerX();
            float cy = rect.centerY();
            float halfW = rect.width() * 0.5f * scale;
            float halfH = rect.height() * 0.5f * scale;
            RectF scaled = new RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
            softShadowPaint.setColor(Color.argb((int) (22 * scale), 156, 153, 147));
            canvas.drawRoundRect(new RectF(scaled.left, scaled.top + dp(9f) * scale, scaled.right, scaled.bottom + dp(9f) * scale),
                    radius, radius, softShadowPaint);
            fillPaint.setColor(Color.rgb(252, 251, 248));
            canvas.drawRoundRect(scaled, radius, radius, fillPaint);
            strokePaint.setColor(Color.argb(18, 130, 128, 125));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawRoundRect(scaled, radius, radius, strokePaint);
        }

        private void drawLabelText(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) {
            labelPaint.setTextSize(size);
            labelPaint.setColor(color);
            labelPaint.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            Paint.FontMetrics metrics = labelPaint.getFontMetrics();
            canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) * 0.5f, labelPaint);
        }

        private void updateStandardUiAnimation() {
            leftStickRenderX = animateValue(leftStickRenderX, leftStickVisualX, 0.26f);
            leftStickRenderY = animateValue(leftStickRenderY, leftStickVisualY, 0.26f);
            rightStickRenderX = animateValue(rightStickRenderX, rightStickVisualX, 0.26f);
            rightStickRenderY = animateValue(rightStickRenderY, rightStickVisualY, 0.26f);
            leftTriggerRenderProgress = animateValue(leftTriggerRenderProgress, leftTriggerProgress, 0.22f);
            rightTriggerRenderProgress = animateValue(rightTriggerRenderProgress, rightTriggerProgress, 0.22f);
            leftStickPressRenderProgress = animateValue(leftStickPressRenderProgress, leftStickPressProgress, 0.28f);
            rightStickPressRenderProgress = animateValue(rightStickPressRenderProgress, rightStickPressProgress, 0.28f);
            vibrationToggleRender = animateValue(vibrationToggleRender, vibrationEnabled ? 1f : 0f, 0.20f);
            updateVibrationThumbRect(false);
            if (needsAnimationFrame()) {
                postInvalidateOnAnimation();
            }
        }

        private boolean needsAnimationFrame() {
            return Math.abs(leftStickRenderX - leftStickVisualX) > 0.01f
                    || Math.abs(leftStickRenderY - leftStickVisualY) > 0.01f
                    || Math.abs(rightStickRenderX - rightStickVisualX) > 0.01f
                    || Math.abs(rightStickRenderY - rightStickVisualY) > 0.01f
                    || Math.abs(leftTriggerRenderProgress - leftTriggerProgress) > 0.01f
                    || Math.abs(rightTriggerRenderProgress - rightTriggerProgress) > 0.01f
                    || Math.abs(leftStickPressRenderProgress - leftStickPressProgress) > 0.01f
                    || Math.abs(rightStickPressRenderProgress - rightStickPressProgress) > 0.01f
                    || Math.abs(vibrationToggleRender - (vibrationEnabled ? 1f : 0f)) > 0.01f;
        }

        private float animateValue(float current, float target, float factor) {
            return current + (target - current) * factor;
        }

        private void updateVibrationThumbRect(boolean animated) {
            RectF track = new RectF(vibrationToggleRect.right - vibrationToggleRect.width() * 0.26f,
                    vibrationToggleRect.top + vibrationToggleRect.height() * 0.23f,
                    vibrationToggleRect.right - vibrationToggleRect.width() * 0.06f,
                    vibrationToggleRect.bottom - vibrationToggleRect.height() * 0.23f);
            float radius = track.height() * 0.46f;
            float minX = track.left + radius + dp(2f);
            float maxX = track.right - radius - dp(2f);
            float centerX = minX + (maxX - minX) * vibrationToggleRender;
            float centerY = track.centerY();
            vibrationThumbRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            if (animated) invalidate();
        }

        private boolean isButtonActive(int bit) {
            return (activeButtonsMask & bit) != 0;
        }

        private int blendColor(int startColor, int endColor, float progress) {
            float p = clamp(progress, 0f, 1f);
            int sr = Color.red(startColor);
            int sg = Color.green(startColor);
            int sb = Color.blue(startColor);
            int er = Color.red(endColor);
            int eg = Color.green(endColor);
            int eb = Color.blue(endColor);
            return Color.rgb(
                    Math.round(sr + (er - sr) * p),
                    Math.round(sg + (eg - sg) * p),
                    Math.round(sb + (eb - sb) * p)
            );
        }

        private String readBatteryPercentLabel() {
            try {
                BatteryManager batteryManager = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
                if (batteryManager == null) return "--%";
                int value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                return value > 0 ? value + "%" : "--%";
            } catch (Exception ignored) {
                return "--%";
            }
        }

        private void drawShoulders(Canvas canvas) {
            drawPill(canvas, leftShoulder, "L", Color.rgb(52, 57, 70));
            drawPill(canvas, leftTrigger, "ZL", Color.rgb(40, 44, 56));
            if (!KEYBOARD_TYPE_JOYCON_LEFT.equals(controllerType)) {
                drawPill(canvas, rightShoulder, "R", Color.rgb(52, 57, 70));
                drawPill(canvas, rightTrigger, "ZR", Color.rgb(40, 44, 56));
            }
        }

        private void drawPill(Canvas canvas, RectF rect, String label, int color) {
            if (rect.isEmpty()) return;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(color);
            canvas.drawRoundRect(rect, rect.height() * 0.5f, rect.height() * 0.5f, fillPaint);
            drawText(canvas, label, rect.centerX(), rect.centerY(), Math.max(10f, rect.height() * 0.32f), Color.rgb(235, 240, 250));
        }

        private void drawStick(Canvas canvas, RectF rect, String label, float axisX, float axisY) {
            float radius = rect.width() * 0.5f;
            float travel = radius * 0.34f;
            float knobCx = rect.centerX() + axisX * travel;
            float knobCy = rect.centerY() + axisY * travel;
            float knobRadius = radius * 0.58f;

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.rgb(18, 21, 28));
            canvas.drawCircle(rect.centerX(), rect.centerY(), radius, fillPaint);
            fillPaint.setColor(Color.argb(110, 120, 132, 158));
            canvas.drawCircle(rect.centerX(), rect.centerY(), radius * 0.18f, fillPaint);
            strokePaint.setColor(Color.argb(55, 255, 255, 255));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawLine(rect.centerX(), rect.centerY(), knobCx, knobCy, strokePaint);
            fillPaint.setColor(Color.rgb(44, 50, 62));
            canvas.drawCircle(knobCx, knobCy, knobRadius, fillPaint);
            fillPaint.setColor(Color.argb(80, 255, 255, 255));
            canvas.drawCircle(knobCx - knobRadius * 0.18f, knobCy - knobRadius * 0.22f, knobRadius * 0.32f, fillPaint);
            strokePaint.setColor(Color.argb(90, 255, 255, 255));
            strokePaint.setStrokeWidth(dp(1.5f));
            canvas.drawCircle(rect.centerX(), rect.centerY(), radius, strokePaint);
            strokePaint.setColor(Color.argb(85, 15, 18, 24));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawCircle(knobCx, knobCy, knobRadius, strokePaint);
            drawText(canvas, label, knobCx, knobCy, radius * 0.28f, Color.rgb(220, 226, 238));
        }

        private void drawDpad(Canvas canvas, RectF rect) {
            float cx = rect.centerX();
            float cy = rect.centerY();
            float r = rect.width() * 0.5f;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.rgb(24, 28, 37));
            canvas.drawCircle(cx, cy, r, fillPaint);
            fillPaint.setColor(Color.rgb(235, 240, 250));
            float arm = r * 0.36f;
            float len = r * 0.72f;
            canvas.drawRoundRect(new RectF(cx - arm, cy - len, cx + arm, cy + len), arm * 0.35f, arm * 0.35f, fillPaint);
            canvas.drawRoundRect(new RectF(cx - len, cy - arm, cx + len, cy + arm), arm * 0.35f, arm * 0.35f, fillPaint);
        }

        private void drawButton(Canvas canvas, GamepadButton button) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(button.color);
            canvas.drawCircle(button.cx, button.cy, button.radius, fillPaint);
            strokePaint.setColor(Color.argb(75, 18, 26, 42));
            strokePaint.setStrokeWidth(dp(1f));
            canvas.drawCircle(button.cx, button.cy, button.radius, strokePaint);
            drawText(canvas, button.label, button.cx, button.cy, Math.max(9f, button.radius * 0.62f), Color.rgb(24, 31, 45));
        }

        private void drawExit(Canvas canvas) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(180, 245, 247, 252));
            canvas.drawRoundRect(exitRect, exitRect.height() * 0.5f, exitRect.height() * 0.5f, fillPaint);
            drawText(canvas, "退出", exitRect.centerX(), exitRect.centerY(), Math.max(10f, exitRect.height() * 0.34f), Color.rgb(42, 52, 70));
        }

        private void drawText(Canvas canvas, String text, float cx, float cy, float size, int color) {
            textPaint.setColor(color);
            textPaint.setTextSize(size);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) * 0.5f, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                int index = event.getActionIndex();
                float downX = event.getX(index);
                float downY = event.getY(index);
                int pointerId = event.getPointerId(index);
                if (exitRect.contains(event.getX(index), event.getY(index))) {
                    emitState(0, HAT_NEUTRAL, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, 0, 0);
                    pendingExitTap = true;
                    return true;
                }
                if (standardUiActive && vibrationToggleRect.contains(downX, downY)) {
                    vibrationEnabled = !vibrationEnabled;
                    updateVibrationThumbRect(true);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    return true;
                }
                if (standardUiActive) {
                    if (stickModeRect.contains(downX, downY)) {
                        stickClickMode = STICK_CLICK_MODE_BUTTON.equals(stickClickMode)
                                ? STICK_CLICK_MODE_TAP
                                : STICK_CLICK_MODE_BUTTON;
                        if (stickClickModeListener != null) {
                            stickClickModeListener.onStickClickModeChanged(stickClickMode);
                        }
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        invalidate();
                        return true;
                    }
                }
                if (!leftStick.isEmpty() && isInsideCircle(leftStick, downX, downY, 1.55f) && leftStickPointerId == -1) {
                    leftStickPointerId = pointerId;
                    leftStickDownX = downX;
                    leftStickDownY = downY;
                    leftStickDownTimeMs = SystemClock.uptimeMillis();
                    leftStickClickCandidate = STICK_CLICK_MODE_TAP.equals(stickClickMode);
                }
                if (!rightStick.isEmpty() && isInsideCircle(rightStick, downX, downY, 1.55f) && rightStickPointerId == -1) {
                    rightStickPointerId = pointerId;
                    rightStickDownX = downX;
                    rightStickDownY = downY;
                    rightStickDownTimeMs = SystemClock.uptimeMillis();
                    rightStickClickCandidate = STICK_CLICK_MODE_TAP.equals(stickClickMode);
                }
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                pendingExitTap = false;
                removeCallbacks(stickTapReleaseRunnable);
                clearStickTracking();
                resetCurrentTouchState();
                emitState(0, HAT_NEUTRAL, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, 0, 0);
                return true;
            }
            if (pendingExitTap) {
                int index = event.getActionIndex();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                    boolean confirmed = exitRect.contains(event.getX(index), event.getY(index));
                    pendingExitTap = false;
                    emitState(0, HAT_NEUTRAL, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, 0, 0);
                    if (confirmed && disconnectListener != null) {
                        post(disconnectListener);
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    boolean stillInside = false;
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (exitRect.contains(event.getX(i), event.getY(i))) {
                            stillInside = true;
                            break;
                        }
                    }
                    if (!stillInside) {
                        pendingExitTap = false;
                    }
                    return true;
                }
            }
            if (action == MotionEvent.ACTION_MOVE) {
                refreshStickClickCandidates(event);
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                int index = event.getActionIndex();
                pendingStickTapButtons |= handleStickPointerUp(event, index);
            }
            computeStateFromTouches(event);
            return true;
        }

        private void computeStateFromTouches(MotionEvent event) {
            int action = event.getActionMasked();
            int liftedIndex = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                    ? event.getActionIndex()
                    : -1;
            int buttonsMask = 0;
            int hat = HAT_NEUTRAL;
            int lx = AXIS_CENTER;
            int ly = AXIS_CENTER;
            int rx = AXIS_CENTER;
            int ry = AXIS_CENTER;
            int lt = 0;
            int rt = 0;

            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == liftedIndex) continue;
                float x = event.getX(i);
                float y = event.getY(i);
                if (exitRect.contains(x, y)) continue;
                boolean onLeftStickPointer = !leftStick.isEmpty() && ownsLeftStickPointer(event, i);
                boolean onRightStickPointer = !rightStick.isEmpty() && ownsRightStickPointer(event, i);
                boolean stickOwnedPointer = onLeftStickPointer || onRightStickPointer;

                if (!stickOwnedPointer && leftShoulder.contains(x, y)) buttonsMask |= HidAction.GAMEPAD_BUTTON_L1;
                if (!stickOwnedPointer && leftTrigger.contains(x, y)) {
                    buttonsMask |= HidAction.GAMEPAD_BUTTON_L2;
                    lt = 255;
                }
                if (!stickOwnedPointer && standardUiActive && containsExpanded(leftStickClickRect, x, y, dp(10f))
                        && STICK_CLICK_MODE_BUTTON.equals(stickClickMode)) {
                    buttonsMask |= HidAction.GAMEPAD_BUTTON_L3;
                }
                if (!stickOwnedPointer && rightShoulder.contains(x, y)) buttonsMask |= HidAction.GAMEPAD_BUTTON_R1;
                if (!stickOwnedPointer && rightTrigger.contains(x, y)) {
                    buttonsMask |= HidAction.GAMEPAD_BUTTON_R2;
                    rt = 255;
                }
                if (!stickOwnedPointer && standardUiActive && containsExpanded(rightStickClickRect, x, y, dp(10f))
                        && STICK_CLICK_MODE_BUTTON.equals(stickClickMode)) {
                    buttonsMask |= HidAction.GAMEPAD_BUTTON_R3;
                }
                if (onLeftStickPointer) {
                    int[] axis = axisFromCircle(leftStick, x, y);
                    lx = axis[0];
                    ly = axis[1];
                }
                if (onRightStickPointer) {
                    int[] axis = axisFromCircle(rightStick, x, y);
                    rx = axis[0];
                    ry = axis[1];
                }
                if (!stickOwnedPointer && !dpad.isEmpty() && isInsideDpadControl(x, y)) {
                    hat = hatFromPoint(dpad, x, y);
                }
                if (!stickOwnedPointer) {
                    if (standardUiActive) {
                        if (containsExpanded(centerViewRect, x, y, dp(10f))) {
                            buttonsMask |= HidAction.GAMEPAD_BUTTON_MINUS;
                        }
                        if (containsExpanded(centerMenuRect, x, y, dp(10f))) {
                            buttonsMask |= HidAction.GAMEPAD_BUTTON_PLUS;
                        }
                        if (containsExpanded(centerHomeRect, x, y, dp(10f))) {
                            buttonsMask |= HidAction.GAMEPAD_BUTTON_HOME;
                        }
                    }
                    for (GamepadButton button : buttons) {
                        if (distance(x, y, button.cx, button.cy) <= button.radius * 1.25f) {
                            buttonsMask |= button.bit;
                        }
                    }
                }
            }
            currentTouchButtons = buttonsMask;
            currentTouchHat = hat;
            currentTouchLx = lx;
            currentTouchLy = ly;
            currentTouchRx = rx;
            currentTouchRy = ry;
            currentTouchLt = lt;
            currentTouchRt = rt;
            if (pendingStickTapButtons != 0) {
                removeCallbacks(stickTapReleaseRunnable);
                postDelayed(stickTapReleaseRunnable, STICK_TAP_PULSE_MS);
            }
            emitCurrentTouchState();
        }

        private int[] axisFromCircle(RectF rect, float x, float y) {
            float radius = rect.width() * 0.5f;
            float dx = clamp((x - rect.centerX()) / radius, -1f, 1f);
            float dy = clamp((y - rect.centerY()) / radius, -1f, 1f);
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.08f) {
                return new int[]{AXIS_CENTER, AXIS_CENTER};
            }
            if (length > 1f) {
                dx /= length;
                dy /= length;
            }
            return new int[]{
                    Math.round(AXIS_CENTER + dx * 127f),
                    Math.round(AXIS_CENTER + dy * 127f)
            };
        }

        private int hatFromPoint(RectF rect, float x, float y) {
            float dx = x - rect.centerX();
            float dy = y - rect.centerY();
            float dead = rect.width() * 0.14f;
            if (Math.abs(dx) < dead && Math.abs(dy) < dead) return HAT_NEUTRAL;
            double angle = Math.atan2(dy, dx);
            double eighth = Math.PI / 4d;
            int sector = (int) Math.round(angle / eighth);
            if (sector < 0) sector += 8;
            switch (sector % 8) {
                case 0:
                    return HAT_RIGHT;
                case 1:
                    return HAT_DOWN_RIGHT;
                case 2:
                    return HAT_DOWN;
                case 3:
                    return HAT_DOWN_LEFT;
                case 4:
                    return HAT_LEFT;
                case 5:
                    return HAT_UP_LEFT;
                case 6:
                    return HAT_UP;
                default:
                    return HAT_UP_RIGHT;
            }
        }

        private boolean ownsLeftStickPointer(MotionEvent event, int index) {
            return leftStickPointerId != -1
                    && event.getPointerId(index) == leftStickPointerId;
        }

        private boolean ownsRightStickPointer(MotionEvent event, int index) {
            return rightStickPointerId != -1
                    && event.getPointerId(index) == rightStickPointerId;
        }

        private void refreshStickClickCandidates(MotionEvent event) {
            float cancelDistance = dp(18f);
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointerId = event.getPointerId(i);
                float x = event.getX(i);
                float y = event.getY(i);
                if (pointerId == leftStickPointerId && leftStickClickCandidate
                        && distance(x, y, leftStickDownX, leftStickDownY) > cancelDistance) {
                    leftStickClickCandidate = false;
                }
                if (pointerId == rightStickPointerId && rightStickClickCandidate
                        && distance(x, y, rightStickDownX, rightStickDownY) > cancelDistance) {
                    rightStickClickCandidate = false;
                }
            }
        }

        private int handleStickPointerUp(MotionEvent event, int index) {
            int pointerId = event.getPointerId(index);
            long now = SystemClock.uptimeMillis();
            float tapDurationMs = 190f;
            int tapButtons = 0;
            if (pointerId == leftStickPointerId) {
                if (leftStickClickCandidate && now - leftStickDownTimeMs <= tapDurationMs) {
                    tapButtons |= HidAction.GAMEPAD_BUTTON_L3;
                }
                leftStickClickCandidate = false;
                leftStickPointerId = -1;
            }
            if (pointerId == rightStickPointerId) {
                if (rightStickClickCandidate && now - rightStickDownTimeMs <= tapDurationMs) {
                    tapButtons |= HidAction.GAMEPAD_BUTTON_R3;
                }
                rightStickClickCandidate = false;
                rightStickPointerId = -1;
            }
            return tapButtons;
        }

        private void clearStickTracking() {
            removeCallbacks(stickTapReleaseRunnable);
            leftStickPointerId = -1;
            rightStickPointerId = -1;
            leftStickClickCandidate = false;
            rightStickClickCandidate = false;
            pendingStickTapButtons = 0;
        }

        private void resetCurrentTouchState() {
            currentTouchButtons = 0;
            currentTouchHat = HAT_NEUTRAL;
            currentTouchLx = AXIS_CENTER;
            currentTouchLy = AXIS_CENTER;
            currentTouchRx = AXIS_CENTER;
            currentTouchRy = AXIS_CENTER;
            currentTouchLt = 0;
            currentTouchRt = 0;
        }

        private void emitCurrentTouchState() {
            emitState(currentTouchButtons | pendingStickTapButtons, currentTouchHat,
                    currentTouchLx, currentTouchLy, currentTouchRx, currentTouchRy, currentTouchLt, currentTouchRt);
        }

        private void emitState(int buttons, int hat, int lx, int ly, int rx, int ry, int lt, int rt) {
            if (buttons == lastButtons && hat == lastHat && lx == lastLx && ly == lastLy
                    && rx == lastRx && ry == lastRy && lt == lastLt && rt == lastRt) {
                return;
            }
            int previousButtons = lastButtons;
            if (vibrationEnabled && ((buttons != 0 && lastButtons == 0) || (hat != HAT_NEUTRAL && lastHat == HAT_NEUTRAL))) {
                performHapticFeedback(triggerFeedbackConstant());
            }
            lastButtons = buttons;
            lastHat = hat;
            lastLx = lx;
            lastLy = ly;
            lastRx = rx;
            lastRy = ry;
            lastLt = lt;
            lastRt = rt;
            activeButtonsMask = buttons;
            leftStickVisualX = axisToVisual(lx);
            leftStickVisualY = axisToVisual(ly);
            rightStickVisualX = axisToVisual(rx);
            rightStickVisualY = axisToVisual(ry);
            leftTriggerProgress = clamp(lt / 255f, 0f, 1f);
            rightTriggerProgress = clamp(rt / 255f, 0f, 1f);
            leftStickPressProgress = (buttons & HidAction.GAMEPAD_BUTTON_L3) != 0 ? 1f : 0f;
            rightStickPressProgress = (buttons & HidAction.GAMEPAD_BUTTON_R3) != 0 ? 1f : 0f;
            if ((buttons & HidAction.GAMEPAD_BUTTON_HOME) != 0 && (previousButtons & HidAction.GAMEPAD_BUTTON_HOME) == 0) {
                homePulseStartMs = SystemClock.uptimeMillis();
            }
            if (stateListener != null) stateListener.onStateChanged(buttons, hat, lx, ly, rx, ry, lt, rt);
            invalidate();
        }

        private float axisToVisual(int value) {
            return clamp((value - AXIS_CENTER) / 127f, -1f, 1f);
        }

        private int triggerFeedbackConstant() {
            switch (vibrationLevel) {
                case VIBRATION_LIGHT:
                    return HapticFeedbackConstants.CLOCK_TICK;
                case VIBRATION_STRONG:
                    return HapticFeedbackConstants.LONG_PRESS;
                case VIBRATION_MEDIUM:
                default:
                    return HapticFeedbackConstants.VIRTUAL_KEY;
            }
        }

        private boolean isInsideCircle(RectF rect, float x, float y, float scale) {
            return distance(x, y, rect.centerX(), rect.centerY()) <= rect.width() * 0.5f * scale;
        }

        private boolean isInsideDpadControl(float x, float y) {
            if (!standardUiActive) {
                return isInsideCircle(dpad, x, y, 1.15f);
            }
            float cx = dpad.centerX();
            float cy = dpad.centerY();
            float size = Math.min(dpad.width(), dpad.height());
            float armLong = size * 0.92f;
            float armShort = size * 0.40f;
            RectF vertical = new RectF(cx - armShort * 0.5f, cy - armLong * 0.5f, cx + armShort * 0.5f, cy + armLong * 0.5f);
            RectF horizontal = new RectF(cx - armLong * 0.5f, cy - armShort * 0.5f, cx + armLong * 0.5f, cy + armShort * 0.5f);
            return vertical.contains(x, y)
                    || horizontal.contains(x, y)
                    || distance(x, y, cx, cy) <= size * 0.20f;
        }

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private boolean containsExpanded(RectF rect, float x, float y, float expand) {
            return x >= rect.left - expand && x <= rect.right + expand
                    && y >= rect.top - expand && y <= rect.bottom + expand;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(stickTapReleaseRunnable);
            resetCurrentTouchState();
            emitState(0, HAT_NEUTRAL, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, 0, 0);
            super.onDetachedFromWindow();
        }

        private class GamepadButton {
            final String label;
            final int bit;
            final float cx;
            final float cy;
            final float radius;
            final int color;

            GamepadButton(String label, int bit, float cx, float cy, float radius, int color) {
                this.label = label;
                this.bit = bit;
                this.cx = cx;
                this.cy = cy;
                this.radius = radius;
                this.color = color;
            }
        }
    }

    public interface TriggerListener {
        void onTriggered(String triggerId);
    }

    public static class KeyPadView extends View {
        private static final float SWIPE_THRESHOLD_DP = 56f;
        private static final float PINCH_THRESHOLD_DP = 42f;
        private static final float MULTI_TAP_DISTANCE_DP = 64f;
        private static final float TAP_SLOP_DP = 28f;
        private static final float CIRCLE_MIN_DIAMETER_DP = 54f;
        private static final float CIRCLE_MIN_ROTATION_RAD = 4.7f;

        private final Handler tapHandler = new Handler(Looper.getMainLooper());
        private final ArrayList<GestureBinding> gestureBindings = new ArrayList<>();
        private final ArrayList<Float> pathXs = new ArrayList<>();
        private final ArrayList<Float> pathYs = new ArrayList<>();
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final RectF backgroundDest = new RectF();
        private Bitmap backgroundBitmap;
        private TriggerListener listener;
        private Runnable disconnectListener;
        private float downX;
        private float downY;
        private float lastX;
        private float lastY;
        private float pendingTapX;
        private float pendingTapY;
        private float initialPinchDistance;
        private Runnable pendingTap;
        private int pendingTapFingers = 0;
        private int pendingTapCount = 0;
        private int maxPointerCount = 1;
        private boolean trackingPinch = false;
        private boolean pinchHandled = false;
        private boolean vibrationEnabled = true;
        private int vibrationLevel = VIBRATION_MEDIUM;

        public KeyPadView(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setBackgroundColor(Color.BLACK);
        }

        public void setBackgroundImageUri(String rawUri) {
            clearBackgroundBitmap();
            if (rawUri != null && !rawUri.trim().isEmpty()) {
                backgroundBitmap = loadScaledBackgroundBitmap(rawUri.trim());
            }
            invalidate();
        }

        public void setTriggerListener(TriggerListener listener) {
            this.listener = listener;
        }

        public void setDisconnectListener(Runnable disconnectListener) {
            this.disconnectListener = disconnectListener;
        }

        public void setVibrationEnabled(boolean vibrationEnabled) {
            this.vibrationEnabled = vibrationEnabled;
        }

        public void setVibrationLevel(int vibrationLevel) {
            this.vibrationLevel = vibrationLevel;
        }

        public void setGestureBindings(List<GestureBinding> bindings) {
            gestureBindings.clear();
            if (bindings == null) return;
            for (GestureBinding binding : bindings) {
                gestureBindings.add(binding.copy());
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (backgroundBitmap == null || backgroundBitmap.isRecycled()) return;
            float viewWidth = getWidth();
            float viewHeight = getHeight();
            float bitmapWidth = backgroundBitmap.getWidth();
            float bitmapHeight = backgroundBitmap.getHeight();
            if (viewWidth <= 0f || viewHeight <= 0f || bitmapWidth <= 0f || bitmapHeight <= 0f) return;

            float scale = Math.max(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
            float drawWidth = bitmapWidth * scale;
            float drawHeight = bitmapHeight * scale;
            float left = (viewWidth - drawWidth) * 0.5f;
            float top = (viewHeight - drawHeight) * 0.5f;
            backgroundDest.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawBitmap(backgroundBitmap, null, backgroundDest, backgroundPaint);
        }

        private Bitmap loadScaledBackgroundBitmap(String rawUri) {
            try {
                Uri uri = Uri.parse(rawUri);
                int targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
                int targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = openBackgroundInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

                int sampleSize = 1;
                while ((bounds.outWidth / sampleSize) > targetWidth * 2
                        || (bounds.outHeight / sampleSize) > targetHeight * 2) {
                    sampleSize *= 2;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try (InputStream input = openBackgroundInputStream(uri)) {
                    return BitmapFactory.decodeStream(input, null, options);
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        private InputStream openBackgroundInputStream(Uri uri) throws Exception {
            if ("file".equals(uri.getScheme())) {
                return new FileInputStream(new File(uri.getPath()));
            }
            return getContext().getContentResolver().openInputStream(uri);
        }

        private void clearBackgroundBitmap() {
            if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
                backgroundBitmap.recycle();
            }
            backgroundBitmap = null;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    beginGesture(event);
                    trackingPinch = false;
                    pinchHandled = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    maxPointerCount = Math.max(maxPointerCount, event.getPointerCount());
                    if (event.getPointerCount() >= 2) {
                        initialPinchDistance = distanceBetweenFirstTwoPointers(event);
                        trackingPinch = true;
                        pinchHandled = false;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    maxPointerCount = Math.max(maxPointerCount, event.getPointerCount());
                    updateLastCentroid(event);
                    if (event.getPointerCount() == 1 && maxPointerCount == 1) {
                        appendPathPoint(lastX, lastY);
                    }
                    if (trackingPinch && event.getPointerCount() >= 2 && !pinchHandled) {
                        float currentDistance = distanceBetweenFirstTwoPointers(event);
                        float threshold = PINCH_THRESHOLD_DP * getResources().getDisplayMetrics().density;
                        if (initialPinchDistance - currentDistance > threshold) {
                            pinchHandled = true;
                            cancelPendingTap();
                            vibrate(HapticFeedbackConstants.LONG_PRESS);
                            if (disconnectListener != null) disconnectListener.run();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    maxPointerCount = Math.max(maxPointerCount, event.getPointerCount());
                    updateLastCentroid(event);
                    return true;
                case MotionEvent.ACTION_UP:
                    updateLastCentroid(event);
                    if (maxPointerCount == 1) appendPathPoint(lastX, lastY);
                    if (!pinchHandled) {
                        handleCompletedGesture();
                    }
                    trackingPinch = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelPendingTap();
                    trackingPinch = false;
                    pinchHandled = false;
                    return true;
            }
            return true;
        }

        private void beginGesture(MotionEvent event) {
            maxPointerCount = event.getPointerCount();
            float[] centroid = centroid(event);
            downX = centroid[0];
            downY = centroid[1];
            lastX = downX;
            lastY = downY;
            pathXs.clear();
            pathYs.clear();
            appendPathPoint(downX, downY);
        }

        private void updateLastCentroid(MotionEvent event) {
            float[] centroid = centroid(event);
            lastX = centroid[0];
            lastY = centroid[1];
        }

        private float[] centroid(MotionEvent event) {
            int count = Math.max(1, event.getPointerCount());
            float x = 0f;
            float y = 0f;
            for (int i = 0; i < count; i++) {
                x += event.getX(i);
                y += event.getY(i);
            }
            return new float[]{x / count, y / count};
        }

        private void appendPathPoint(float x, float y) {
            if (!pathXs.isEmpty()) {
                float lastPathX = pathXs.get(pathXs.size() - 1);
                float lastPathY = pathYs.get(pathYs.size() - 1);
                if (distance(x, y, lastPathX, lastPathY) < getResources().getDisplayMetrics().density * 4f) {
                    return;
                }
            }
            pathXs.add(x);
            pathYs.add(y);
        }

        private void handleCompletedGesture() {
            float dx = lastX - downX;
            float dy = lastY - downY;
            String circleMotion = detectCircleMotion();
            if (circleMotion != null) {
                cancelPendingTap();
                sendMotion(1, circleMotion);
                return;
            }

            if (isSwipe(dx, dy)) {
                cancelPendingTap();
                sendMotion(maxPointerCount, resolveSwipeMotion(dx, dy));
                return;
            }

            float tapSlop = TAP_SLOP_DP * getResources().getDisplayMetrics().density;
            if (Math.abs(dx) <= tapSlop && Math.abs(dy) <= tapSlop) {
                handleTap(maxPointerCount, lastX, lastY);
            }
        }

        private boolean isSwipe(float dx, float dy) {
            float threshold = SWIPE_THRESHOLD_DP * getResources().getDisplayMetrics().density;
            return Math.abs(dx) >= threshold || Math.abs(dy) >= threshold;
        }

        private String resolveSwipeMotion(float dx, float dy) {
            if (Math.abs(dx) > Math.abs(dy)) {
                return dx > 0 ? MOTION_SWIPE_RIGHT : MOTION_SWIPE_LEFT;
            }
            return dy > 0 ? MOTION_SWIPE_DOWN : MOTION_SWIPE_UP;
        }

        private void handleTap(int fingers, float x, float y) {
            float maxDistance = MULTI_TAP_DISTANCE_DP * getResources().getDisplayMetrics().density;
            boolean continuesPending = pendingTap != null
                    && pendingTapFingers == fingers
                    && distance(x, y, pendingTapX, pendingTapY) <= maxDistance;

            if (!continuesPending) {
                cancelPendingTap();
                pendingTapFingers = fingers;
                pendingTapCount = 1;
            } else {
                tapHandler.removeCallbacks(pendingTap);
                pendingTapCount++;
            }

            pendingTapX = x;
            pendingTapY = y;
            if (pendingTapCount >= 3) {
                int tapCount = pendingTapCount;
                cancelPendingTap();
                emitTap(fingers, tapCount, x, y);
                return;
            }

            pendingTap = () -> {
                int tapCount = pendingTapCount;
                int tapFingers = pendingTapFingers;
                float tapX = pendingTapX;
                float tapY = pendingTapY;
                pendingTap = null;
                pendingTapCount = 0;
                pendingTapFingers = 0;
                emitTap(tapFingers, tapCount, tapX, tapY);
            };
            tapHandler.postDelayed(pendingTap, ViewConfiguration.getDoubleTapTimeout());
        }

        private void emitTap(int fingers, int tapCount, float x, float y) {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            float xRatio = x / getWidth();
            float yRatio = y / getHeight();
            for (GestureBinding binding : gestureBindings) {
                if (!GESTURE_KIND_TAP.equals(binding.kind)) continue;
                if (binding.fingers != fingers || binding.tapCount != tapCount) continue;
                if (MainActivity.tapAreaContains(binding.area, xRatio, yRatio)) {
                    sendBinding(binding.key());
                    return;
                }
            }
        }

        private void sendMotion(int fingers, String motion) {
            String key = GestureBinding.motionKey(fingers, motion);
            if (hasBinding(key)) sendBinding(key);
        }

        private boolean hasBinding(String key) {
            for (GestureBinding binding : gestureBindings) {
                if (key.equals(binding.key())) return true;
            }
            return false;
        }

        private void sendBinding(String gestureKey) {
            vibrate(triggerFeedbackConstant());
            if (listener != null && gestureKey != null) listener.onTriggered(gestureKey);
        }

        private void cancelPendingTap() {
            if (pendingTap != null) {
                tapHandler.removeCallbacks(pendingTap);
                pendingTap = null;
            }
            pendingTapCount = 0;
            pendingTapFingers = 0;
        }

        private float distanceBetweenFirstTwoPointers(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private String detectCircleMotion() {
            if (maxPointerCount != 1 || pathXs.size() < 8) return null;
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int i = 0; i < pathXs.size(); i++) {
                float x = pathXs.get(i);
                float y = pathYs.get(i);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
            float width = maxX - minX;
            float height = maxY - minY;
            float minDiameter = CIRCLE_MIN_DIAMETER_DP * getResources().getDisplayMetrics().density;
            if (width < minDiameter || height < minDiameter) return null;
            float ratio = Math.max(width, height) / Math.max(1f, Math.min(width, height));
            if (ratio > 2.2f) return null;

            float cx = (minX + maxX) / 2f;
            float cy = (minY + maxY) / 2f;
            float totalAngle = 0f;
            float previousAngle = (float) Math.atan2(pathYs.get(0) - cy, pathXs.get(0) - cx);
            for (int i = 1; i < pathXs.size(); i++) {
                float angle = (float) Math.atan2(pathYs.get(i) - cy, pathXs.get(i) - cx);
                float delta = angle - previousAngle;
                while (delta > Math.PI) delta -= (float) (Math.PI * 2d);
                while (delta < -Math.PI) delta += (float) (Math.PI * 2d);
                totalAngle += delta;
                previousAngle = angle;
            }

            if (Math.abs(totalAngle) < CIRCLE_MIN_ROTATION_RAD) return null;
            return totalAngle > 0f ? MOTION_CIRCLE_CLOCKWISE : MOTION_CIRCLE_COUNTERCLOCKWISE;
        }

        private void vibrate(int feedbackConstant) {
            if (vibrationEnabled) performHapticFeedback(feedbackConstant);
        }

        private int triggerFeedbackConstant() {
            switch (vibrationLevel) {
                case VIBRATION_LIGHT:
                    return HapticFeedbackConstants.CLOCK_TICK;
                case VIBRATION_STRONG:
                    return HapticFeedbackConstants.LONG_PRESS;
                case VIBRATION_MEDIUM:
                default:
                    return HapticFeedbackConstants.VIRTUAL_KEY;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            cancelPendingTap();
            clearBackgroundBitmap();
            super.onDetachedFromWindow();
        }
    }

    private class ShadowCardLayout extends LinearLayout {
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF shadowRect = new RectF();
        private final Rect shadowPadding = new Rect();
        private final Rect contentPadding = new Rect();
        private GradientDrawable backgroundDrawable;
        private float cornerRadius;
        private boolean updatingPadding = false;

        ShadowCardLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClipChildren(false);
            setClipToPadding(false);
        }

        void applyCardStyle(int radiusPx, int fillColor, int strokeColor, float elevationDp) {
            float shadowBlur = dp(Math.max(7f, Math.min(10f, elevationDp * 1.25f)));
            float shadowOffsetY = dp(Math.max(1.2f, Math.min(2.2f, elevationDp * 0.28f)));

            int sidePad = (int) Math.ceil(shadowBlur * 0.45f);
            int topPad = (int) Math.ceil(shadowBlur * 0.25f);
            int bottomPad = (int) Math.ceil(shadowBlur * 0.45f + shadowOffsetY);

            cornerRadius = radiusPx;
            shadowPadding.set(sidePad, topPad, sidePad, bottomPad);
            backgroundDrawable = roundedRect(fillColor, radiusPx, strokeColor, 1);
            shadowPaint.setColor(Color.argb(255, 255, 255, 255));
            shadowPaint.setShadowLayer(shadowBlur, 0f, shadowOffsetY, Color.argb(14, 18, 26, 42));
            applyCombinedPadding();
            invalidate();
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            if (updatingPadding) {
                super.setPadding(left, top, right, bottom);
                return;
            }
            contentPadding.set(left, top, right, bottom);
            applyCombinedPadding();
        }

        private void applyCombinedPadding() {
            updatingPadding = true;
            super.setPadding(
                    shadowPadding.left + contentPadding.left,
                    shadowPadding.top + contentPadding.top,
                    shadowPadding.right + contentPadding.right,
                    shadowPadding.bottom + contentPadding.bottom
            );
            updatingPadding = false;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (backgroundDrawable != null) {
                shadowRect.set(
                        shadowPadding.left,
                        shadowPadding.top,
                        getWidth() - shadowPadding.right,
                        getHeight() - shadowPadding.bottom
                );
                canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint);
                backgroundDrawable.setBounds(
                        (int) shadowRect.left,
                        (int) shadowRect.top,
                        (int) shadowRect.right,
                        (int) shadowRect.bottom
                );
                backgroundDrawable.draw(canvas);
            }
            super.dispatchDraw(canvas);
        }
    }
}
