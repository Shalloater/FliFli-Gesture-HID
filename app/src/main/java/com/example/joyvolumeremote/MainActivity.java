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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import static com.example.joyvolumeremote.KeyboardDefinitions.*;
import static com.example.joyvolumeremote.GestureRules.*;

public class MainActivity extends Activity {
    private static final int REQ_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQ_ENABLE_BLUETOOTH = 1002;
    private static final int REQ_PICK_KEYBOARD_BACKGROUND = 1003;

    private static final int PAGE_CONNECTION = 0;
    private static final int PAGE_KEYBOARDS = 1;
    private static final int PAGE_SETTINGS = 2;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter bluetoothAdapter;
    private HidRemoteService hidService;
    private KeyboardConfigStore keyboardStore;
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
    private CustomKeyboardConfig activeKeyboardConfig;
    private CustomKeyboardConfig pendingBackgroundDraft;
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
        keyboardStore = new KeyboardConfigStore(this);
        keyboardStore.ensureKeyboardConfigsInitialized();
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

        List<CustomKeyboardConfig> configs = keyboardStore.loadKeyboardConfigs();
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

        List<CustomKeyboardConfig> configs = keyboardStore.loadKeyboardConfigs();
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
            showKeyboardEditor(keyboardStore.createNewKeyboardDraft(type), true);
        });
    }

    private void showKeyboardEditor(CustomKeyboardConfig draft, boolean isNew) {
        keyboardStore.normalizeKeyboardConfig(draft);
        keyboardStore.upsertKeyboardConfig(draft);
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
            keyboardStore.upsertKeyboardConfig(draft);
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
        keyboardStore.upsertKeyboardConfig(draft);
        showSettingsPage();
    }

    private void confirmDeleteKeyboard(CustomKeyboardConfig draft) {
        new AlertDialog.Builder(this)
                .setTitle("删除键盘")
                .setMessage("删除后不会影响蓝牙配对和其他键盘配置。")
                .setPositiveButton("删除", (dialog, which) -> {
                    keyboardStore.deleteKeyboardConfig(draft.id);
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
        activeKeyboardConfig = keyboardConfig == null ? keyboardStore.createDefaultKeyboardFromLegacy() : keyboardConfig.copy();
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
        showRemotePage(device, config);
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

    private String keyboardTypeLabel(String type) {
        return KEYBOARD_TYPE_COMPUTER.equals(type) ? "电脑键盘" : "全屏手势键盘";
    }

    private boolean isGestureKeyboard(CustomKeyboardConfig config) {
        return config == null || KEYBOARD_TYPE_FULLSCREEN_GESTURE.equals(config.type);
    }

    private boolean isComputerKeyboard(CustomKeyboardConfig config) {
        return config != null && KEYBOARD_TYPE_COMPUTER.equals(config.type);
    }

    private boolean isLandscapeRemoteType(String type) {
        return KEYBOARD_TYPE_COMPUTER.equals(type);
    }

    private String remoteOrientationLabel(String type) {
        return isLandscapeRemoteType(type) ? "横屏使用" : "竖屏使用";
    }

    private String fixedKeyboardSignalLabel(String type) {
        return "标准 HID Keyboard：修饰键 + 最多 6 个普通键";
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
        hidService.connectToHost(device);
    }

    private void disconnectHost() {
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
