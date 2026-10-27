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
import android.graphics.Color;
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
import java.util.Collections;
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

    private static final int PAGE_CONNECTION = 1;
    private static final int PAGE_KEYBOARDS = 0;
    private static final int PAGE_SETTINGS = 2;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter bluetoothAdapter;
    private HidRemoteService hidService;
    private KeyboardConfigStore keyboardStore;
    private AppPreferences appearance;
    private GestureDemoCatalog gestureDemos;
    private GesturePreviewDialog gesturePreview;
    private SignalPickerDialog signalPicker;
    private GestureBindingEditorDialog bindingEditor;
    private AlertDialog choiceDialog;
    private boolean serviceBound = false;

    private TextView connectionTitleView;
    private TextView connectionSubtitleView;
    private LinearLayout pairedListLayout;
    private IconView connectionStatusIcon;
    private IconView bottomConnectionIcon;
    private boolean showingRemotePage = false;
    private boolean showingSettingsPage = false;
    private boolean showingKeyboardPage = false;
    private boolean editingKeyboard = false;
    private boolean wasConnected = false;
    private boolean wasBluetoothOn = false;
    private CustomKeyboardConfig activeKeyboardConfig;
    private TouchPassthroughView touchSurface;
    private ScrollView keyboardEditorScroll;
    private String keyboardEditorId;
    private Object backNavigationCallback;
    private boolean touchBarsHidden;
    private static final long TOUCH_BACK_CONFIRM_MS = 2000;
    private long touchBackDeadline;
    private ScreenPinningProtection screenPinningProtection;
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
            updateStatus(getString(R.string.ui_keyboard_service_disconnected_reopen_the_app));
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
        final String demoId;

        PickerItem(String title, String subtitle, String chipText, int iconType, int iconColor, boolean enabled) {
            this(title, subtitle, chipText, iconType, iconColor, enabled, null);
        }

        PickerItem(String title, String subtitle, String chipText, int iconType, int iconColor, boolean enabled, String demoId) {
            this.title = title;
            this.subtitle = subtitle;
            this.chipText = chipText;
            this.iconType = iconType;
            this.iconColor = iconColor;
            this.enabled = enabled;
            this.demoId = demoId;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPreferences.localized(base));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", showingSettingsPage ? PAGE_SETTINGS : isConnectionPageVisible() ? PAGE_CONNECTION : PAGE_KEYBOARDS);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (showingRemotePage && isTouchKeyboard(activeKeyboardConfig)) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (touchBackDeadline > now) exitRemotePage();
            else {
                touchBackDeadline = now + TOUCH_BACK_CONFIRM_MS;
                Toast.makeText(this, R.string.touch_back_again, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (showingRemotePage) exitRemotePage();
        else if (editingKeyboard) showSettingsPage();
        else if (!showingKeyboardPage) showKeyboardsPage();
        else super.onBackPressed();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        screenPinningProtection = new ScreenPinningProtection(this, result -> {
            if (result == ScreenPinningProtection.Result.PENDING || result == ScreenPinningProtection.Result.ACTIVE) return;
            if (result == ScreenPinningProtection.Result.STOP_FAILED || (showingRemotePage && isTouchKeyboard(activeKeyboardConfig))) {
                Toast.makeText(this, result == ScreenPinningProtection.Result.STOP_FAILED ? R.string.touch_pinning_stop_failed
                        : R.string.touch_pinning_inactive,
                        Toast.LENGTH_LONG).show();
            }
        });
        backNavigationCallback = SystemGestureProtection.registerBackCallback(this, this::onBackPressed);
        appearance = new AppPreferences(this);
        gestureDemos = new GestureDemoCatalog(this);
        keyboardStore = new KeyboardConfigStore(this);
        keyboardStore.ensureKeyboardConfigsInitialized();
        int page = savedInstanceState == null ? PAGE_KEYBOARDS : savedInstanceState.getInt("page", PAGE_KEYBOARDS);
        if (page == PAGE_SETTINGS) showSettingsPage();
        else if (page == PAGE_CONNECTION) showSetupPage();
        else showKeyboardsPage();

        if (Build.VERSION.SDK_INT < HidRemoteService.MIN_SUPPORTED_API) {
            updateStatus(getString(R.string.ui_android_9_or_later_is_required));
            return;
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            updateStatus(getString(R.string.ui_this_device_has_no_bluetooth_adapter_162));
            return;
        }

        requestBluetoothPermissionsIfNeeded();
        if (hasBluetoothConnectPermission()) startAndBindHidService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= HidRemoteService.MIN_SUPPORTED_API && bluetoothAdapter != null && hasBluetoothConnectPermission()) {
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
        if (showingRemotePage && isTouchKeyboard(activeKeyboardConfig)) enterRemoteFullscreen();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (screenPinningProtection != null) screenPinningProtection.onWindowFocusChanged(focused);
        if (!focused) touchBackDeadline = 0;
        if (focused && showingRemotePage && isTouchKeyboard(activeKeyboardConfig)) enterRemoteFullscreen();
    }

    @Override
    protected void onPause() {
        touchBackDeadline = 0;
        if (touchSurface != null) touchSurface.releaseTouches();
        super.onPause();
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
        if (bindingEditor != null) bindingEditor.dismiss();
        if (gesturePreview != null) gesturePreview.dismiss();
        if (signalPicker != null) signalPicker.dismiss();
        if (choiceDialog != null) choiceDialog.dismiss();
        if (screenPinningProtection != null) screenPinningProtection.stop();
        SystemGestureProtection.unregisterBackCallback(this, backNavigationCallback);
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
                updateStatus(getString(R.string.ui_bluetooth_permission_granted_starting_service));
                startAndBindHidService();
            } else {
                updateStatus(getString(R.string.ui_allow_nearby_devices_permission_in_system_settings_to_connect));
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
            updateStatus(getString(R.string.ui_starting));
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
        editingKeyboard = false;
        mainHandler.removeCallbacks(restoreSetupOrientationRunnable);
        exitRemoteFullscreen();
        allowScreenSleep();
        applyOrientationForSetup();
        showingRemotePage = false;
        showingSettingsPage = false;
        showingKeyboardPage = false;
        connectionTitleView = null;
        connectionSubtitleView = null;

        LinearLayout content = createPageContent();

        addPageHeading(content, getString(R.string.connection));
        addConnectionCard(content);
        TextView systemSettings = makePrimaryTextButton(getString(R.string.pair_device), v -> {
            if (!hasBluetoothConnectPermission()) requestBluetoothPermissionsIfNeeded();
            else if (!isBluetoothEnabled()) toggleBluetooth();
            else startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });
        content.addView(systemSettings, spacedParams());
        if (hasConnectedHost()) content.addView(makeDangerTextButton(getString(R.string.ui_disconnect), v -> disconnectHost()), spacedParams());

        LinearLayout pairedCard = createCardLayout(dp(24), 3f);
        pairedCard.setOrientation(LinearLayout.VERTICAL);
        pairedCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView pairedTitle = new TextView(this);
        pairedTitle.setText(getString(R.string.ui_paired_devices));
        setTextSizeSp(pairedTitle, 13);
        pairedTitle.setTextColor(Color.rgb(18, 28, 47));
        pairedTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerRow.addView(pairedTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        View refreshButton = makeIconButton(IconView.ICON_REFRESH, v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (HidRemoteService.IS_BLE_COMPAT && hidService != null) hidService.connectHidProfile();
            refreshPairedDevices(true);
        });
        refreshButton.setContentDescription(getString(R.string.refresh));
        LinearLayout.LayoutParams refreshIconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        refreshIconParams.leftMargin = dp(6);
        headerRow.addView(refreshButton, refreshIconParams);

        pairedCard.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout listShell = new LinearLayout(this);
        listShell.setOrientation(LinearLayout.VERTICAL);
        listShell.setBackgroundColor(Color.WHITE);
        listShell.setPadding(0, dp(2), 0, dp(2));
        listShell.setClipToOutline(true);

        pairedListLayout = new LinearLayout(this);
        pairedListLayout.setOrientation(LinearLayout.VERTICAL);

        listShell.addView(pairedListLayout, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams listShellParams = new LinearLayout.LayoutParams(-1, -2);
        listShellParams.topMargin = dp(12);
        pairedCard.addView(listShell, listShellParams);
        content.addView(pairedCard, new LinearLayout.LayoutParams(-1, -2));
        setContentView(buildScaffold(content, PAGE_CONNECTION));
        updateStatus(hidService != null ? hidService.getStatus() : getString(R.string.ui_starting));
        refreshPairedDevices();
    }

    private void showKeyboardsPage() {
        showKeyboardsPage(true);
    }

    private void showKeyboardsPage(boolean applySetupOrientation) {
        editingKeyboard = false;
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
        connectionStatusIcon = null;
        pairedListLayout = null;

        LinearLayout content = createPageContent();
        addPageHeading(content, getString(R.string.home));

        BluetoothDevice connected = hidService == null ? null : hidService.getConnectedHost();
        addConnectionCard(content);
        addCardHeading(content, getString(R.string.your_keyboards));

        LinearLayout keyboardCard = createCardLayout(dp(24), 3f);
        keyboardCard.setOrientation(LinearLayout.VERTICAL);
        keyboardCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        listWrap.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardCard.addView(listWrap, listParams);

        List<CustomKeyboardConfig> configs = keyboardStore.loadKeyboardConfigs();
        if (configs.isEmpty()) {
            addPlainInfoRow(listWrap, getString(R.string.empty_keyboards));
        } else {
            for (int i = 0; i < configs.size(); i++) {
                addKeyboardRunRow(listWrap, configs.get(i), connected, i < configs.size() - 1);
            }
        }

        LinearLayout.LayoutParams keyboardCardParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardCardParams.bottomMargin = dp(12);
        content.addView(keyboardCard, keyboardCardParams);

        TextView addHint = makePrimaryTextButton(getString(R.string.add_keyboard), v -> showKeyboardTypePicker());
        content.addView(addHint, new LinearLayout.LayoutParams(-1, -2));

        setContentView(buildScaffold(content, PAGE_KEYBOARDS));
        updateStatus(hidService == null ? null : hidService.getStatus());
    }

    private void showSettingsPage() {
        editingKeyboard = false;
        mainHandler.removeCallbacks(restoreSetupOrientationRunnable);
        exitRemoteFullscreen();
        allowScreenSleep();
        applyOrientationForSetup();
        showingRemotePage = false;
        showingSettingsPage = true;
        showingKeyboardPage = false;
        connectionTitleView = null;
        connectionSubtitleView = null;
        connectionStatusIcon = null;
        pairedListLayout = null;

        LinearLayout content = createPageContent();
        addPageHeading(content, getString(R.string.settings));
        LinearLayout preferences = createCardLayout(dp(20), 0);
        preferences.setOrientation(LinearLayout.VERTICAL);
        preferences.setPadding(dp(16), dp(8), dp(16), dp(8));
        String[] skins = getResources().getStringArray(R.array.skin_names);
        addConfigRow(preferences, getString(R.string.skin), skins[appearance.skin()], null, v -> showSkinPicker(), true);
        int languageIndex = java.util.Arrays.asList(AppPreferences.LANGUAGES).indexOf(appearance.language());
        addConfigRow(preferences, getString(R.string.language), AppPreferences.LANGUAGE_NAMES[Math.max(0, languageIndex)],
                null, v -> showLanguagePicker(), false);
        content.addView(preferences, spacedParams());


        LinearLayout keyboardsCard = createCardLayout(dp(24), 3f);
        keyboardsCard.setOrientation(LinearLayout.VERTICAL);
        keyboardsCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = new TextView(this);
        heading.setText(getString(R.string.ui_custom_keyboards));
        setTextSizeSp(heading, 13);
        heading.setTextColor(Color.rgb(18, 28, 47));
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView addButton = makeSmallTextButton(getString(R.string.ui_add), v -> showKeyboardTypePicker());
        headerRow.addView(addButton, new LinearLayout.LayoutParams(-2, -2));
        keyboardsCard.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout keyboardListWrap = new LinearLayout(this);
        keyboardListWrap.setOrientation(LinearLayout.VERTICAL);
        keyboardListWrap.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams keyboardListParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardListParams.topMargin = dp(12);
        keyboardsCard.addView(keyboardListWrap, keyboardListParams);

        List<CustomKeyboardConfig> configs = keyboardStore.loadKeyboardConfigs();
        if (configs.isEmpty()) {
            addPlainInfoRow(keyboardListWrap, getString(R.string.ui_no_keyboards_yet_tap_add_to_create_one));
        } else {
            for (int i = 0; i < configs.size(); i++) {
                addKeyboardEditRow(keyboardListWrap, configs.get(i), i < configs.size() - 1);
            }
        }

        LinearLayout.LayoutParams keyboardsParams = new LinearLayout.LayoutParams(-1, -2);
        keyboardsParams.bottomMargin = dp(12);
        content.addView(keyboardsCard, keyboardsParams);

        setContentView(buildScaffold(content, PAGE_SETTINGS));
    }

    private void makeInteractive(View view) {
        view.setFocusable(true);
        view.setMinimumHeight(dp(48));
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        if (Build.VERSION.SDK_INT >= 23) {
            view.setForeground(getDrawable(value.resourceId));
        } else {
            // View foregrounds were added in API 23. Keep existing card backgrounds on Android 5.
            view.setOnTouchListener((v, event) -> {
                v.setAlpha(event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN ? 0.65f : 1f);
                return false;
            });
        }
    }

    private LinearLayout.LayoutParams spacedParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(20);
        return params;
    }

    private void addPageHeading(LinearLayout content, String title) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(28);
        heading.setTextColor(Color.rgb(24, 31, 43));
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        content.addView(heading, spacedParams());
    }

    private void addConnectionCard(LinearLayout content) {
        LinearLayout card = createCardLayout(dp(20), 0);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        connectionStatusIcon = new IconView(this, connectionIconType(), appearance.accent(), 2);
        card.addView(connectionStatusIcon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(16), 0, dp(8), 0);
        connectionTitleView = new TextView(this);
        connectionTitleView.setTextSize(17);
        connectionTitleView.setTextColor(Color.rgb(24, 31, 43));
        connectionTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        connectionTitleView.setMaxLines(2);
        connectionTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        connectionSubtitleView = new TextView(this);
        connectionSubtitleView.setTextSize(13);
        connectionSubtitleView.setTextColor(Color.rgb(91, 102, 121));
        connectionSubtitleView.setPadding(0, dp(6), 0, 0);
        connectionSubtitleView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        text.addView(connectionTitleView);
        text.addView(connectionSubtitleView);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (showingKeyboardPage) {
            card.addView(new IconView(this, IconView.ICON_CHEVRON, appearance.accent(), 2), new LinearLayout.LayoutParams(dp(16), dp(16)));
            makeInteractive(card);
            card.setOnClickListener(v -> showSetupPage());
        }
        content.addView(card, spacedParams());
    }

    private void showSkinPicker() {
        List<PickerItem> items = new ArrayList<>();
        String[] names = getResources().getStringArray(R.array.skin_names);
        for (int i = 0; i < names.length; i++) {
            items.add(new PickerItem(names[i], null, null, IconView.ICON_KEYBOARD, AppPreferences.color(i), true));
        }
        showStyledChoiceDialog(getString(R.string.skin), getString(R.string.skin_hint), items, appearance.skin(), which -> {
            appearance.setSkin(which);
            showSettingsPage();
        });
    }

    private void showLanguagePicker() {
        List<PickerItem> items = new ArrayList<>();
        for (String name : AppPreferences.LANGUAGE_NAMES) {
            items.add(new PickerItem(name, null, null, IconView.ICON_SETTINGS, appearance.accent(), true));
        }
        showStyledChoiceDialog(getString(R.string.language), null, items,
                java.util.Arrays.asList(AppPreferences.LANGUAGES).indexOf(appearance.language()), which -> {
                    appearance.setLanguage(AppPreferences.LANGUAGES[which]);
                    recreate();
                });
    }

    private void showKeyboardTypePicker() {
        List<PickerItem> types = new ArrayList<>();
        types.add(new PickerItem(getString(R.string.ui_gesture_keyboard), getString(R.string.ui_ready_to_create_and_use), getString(R.string.ui_available),
                IconView.ICON_GESTURE_KEYBOARD, appearance.accent(), true));
        types.add(new PickerItem(getString(R.string.ui_computer_keyboard), getString(R.string.ui_landscape_qwerty_layout), getString(R.string.ui_available),
                IconView.ICON_KEYBOARD, appearance.accent(), true));
        types.add(new PickerItem(getString(R.string.touch_passthrough), getString(HidRemoteService.IS_BLE_COMPAT
                ? R.string.signal_touch_ble : R.string.touch_type_hint), HidRemoteService.IS_BLE_COMPAT ? null : getString(R.string.ui_available),
                IconView.ICON_GESTURE_KEYBOARD, appearance.accent(), !HidRemoteService.IS_BLE_COMPAT));
        showStyledChoiceDialog(getString(R.string.ui_choose_keyboard_type), null, types, -1, which -> {
            String type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
            if (which == 1) type = KEYBOARD_TYPE_COMPUTER;
            if (which == 2) type = KEYBOARD_TYPE_TOUCH_PASSTHROUGH;
            showKeyboardEditor(keyboardStore.createNewKeyboardDraft(type), true);
        });
    }

    private void showKeyboardEditor(CustomKeyboardConfig draft, boolean isNew) {
        int previousScroll = editingKeyboard && draft.id != null && draft.id.equals(keyboardEditorId)
                && keyboardEditorScroll != null ? keyboardEditorScroll.getScrollY() : 0;
        editingKeyboard = true;
        keyboardStore.normalizeKeyboardConfig(draft);
        keyboardEditorId = draft.id;
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
        connectionStatusIcon = null;
        pairedListLayout = null;

        LinearLayout content = createPageContent();

        LinearLayout titleCard = createCardLayout(dp(24), 3f);
        titleCard.setOrientation(LinearLayout.HORIZONTAL);
        titleCard.setGravity(Gravity.CENTER_VERTICAL);
        titleCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout titleTextWrap = new LinearLayout(this);
        titleTextWrap.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(isNew ? getString(R.string.ui_new_keyboard) : getString(R.string.ui_edit_keyboard));
        setTextSizeSp(title, 14);
        title.setTextColor(Color.rgb(18, 28, 47));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView subtitle = new TextView(this);
        subtitle.setText(draft.name);
        setTextSizeSp(subtitle, 10.5f);
        subtitle.setTextColor(Color.rgb(91, 102, 121));
        subtitle.setPadding(0, dp(5), 0, 0);
        titleTextWrap.addView(title, new LinearLayout.LayoutParams(-1, -2));
        titleTextWrap.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        titleCard.addView(titleTextWrap, new LinearLayout.LayoutParams(0, -2, 1f));
        titleCard.addView(makeSmallTextButton(getString(R.string.ui_back), v -> showSettingsPage()), new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = dp(12);
        content.addView(titleCard, titleParams);

        LinearLayout baseCard = createCardLayout(dp(24), 3f);
        baseCard.setOrientation(LinearLayout.VERTICAL);
        baseCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        addCardHeading(baseCard, getString(R.string.ui_general));
        LinearLayout baseList = makeInnerList();
        addConfigRow(baseList, getString(R.string.ui_name), draft.name, null, v -> showKeyboardNameDialog(draft, isNew), true);
        addConfigRow(baseList, getString(R.string.ui_type), keyboardTypeLabel(draft.type), null, null, false);
        baseCard.addView(baseList, makeInnerListParams());
        LinearLayout.LayoutParams baseParams = new LinearLayout.LayoutParams(-1, -2);
        baseParams.bottomMargin = dp(12);
        content.addView(baseCard, baseParams);

        if (isGestureKeyboard(draft) || isTouchKeyboard(draft)) {
            LinearLayout backgroundCard = createCardLayout(dp(24), 3f);
            backgroundCard.setOrientation(LinearLayout.VERTICAL);
            backgroundCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            addCardHeading(backgroundCard, getString(R.string.ui_background));
            LinearLayout backgroundList = makeInnerList();
            addConfigRow(backgroundList, getString(R.string.ui_full_screen_background), keyboardBackgroundLabel(draft), null,
                    v -> showKeyboardBackgroundPicker(draft, isNew), false);
            backgroundCard.addView(backgroundList, makeInnerListParams());
            LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(-1, -2);
            backgroundParams.bottomMargin = dp(12);
            content.addView(backgroundCard, backgroundParams);
        }

        LinearLayout vibrationCard = createCardLayout(dp(24), 3f);
        vibrationCard.setOrientation(LinearLayout.VERTICAL);
        vibrationCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        addCardHeading(vibrationCard, getString(R.string.ui_haptics));
        LinearLayout vibrationList = makeInnerList();
        addConfigRow(vibrationList, getString(R.string.ui_vibration), draft.vibrationEnabled ? getString(R.string.ui_vibrate_on_input) : getString(R.string.ui_no_vibration_on_input),
                draft.vibrationEnabled ? getString(R.string.ui_on) : getString(R.string.ui_off),
                v -> {
                    draft.vibrationEnabled = !draft.vibrationEnabled;
                    showKeyboardEditor(draft, isNew);
                }, true);
        addConfigRow(vibrationList, getString(R.string.ui_vibration_strength), vibrationLevelLabel(draft.vibrationLevel), null,
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
            addCardHeading(actionCard, getString(R.string.ui_gestures));

            TextView actionHint = new TextView(this);
            actionHint.setText(getString(R.string.ui_add_a_tap_or_swipe_then_choose_the_signal_it_sends)
                    + "\n" + getString(R.string.demo_hold_hint) + "\n" + getString(R.string.touch_exit_hint));
            setTextSizeSp(actionHint, 10.5f);
            actionHint.setTextColor(Color.rgb(91, 102, 121));
            actionHint.setPadding(0, dp(5), 0, 0);
            actionCard.addView(actionHint, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout actionList = makeInnerList();
            if (draft.bindings.isEmpty()) {
                addPlainInfoRow(actionList, getString(R.string.ui_no_gestures_yet_add_a_tap_or_swipe));
            } else {
                for (int i = 0; i < draft.bindings.size(); i++) {
                    addGestureBindingRow(actionList, draft, draft.bindings.get(i), isNew, i < draft.bindings.size() - 1);
                }
            }
            actionCard.addView(actionList, makeInnerListParams());

            LinearLayout.LayoutParams addActionParams = new LinearLayout.LayoutParams(-1, -2);
            addActionParams.topMargin = dp(12);
            actionCard.addView(makeSmallTextButton(getString(R.string.ui_add_action), v -> showGesturePicker(draft, isNew)), addActionParams);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
            actionParams.bottomMargin = dp(12);
            content.addView(actionCard, actionParams);
        } else if (isTouchKeyboard(draft)) {
            LinearLayout touchCard = createCardLayout(dp(24), 0);
            touchCard.setOrientation(LinearLayout.VERTICAL);
            touchCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            addCardHeading(touchCard, getString(R.string.ui_layout));
            LinearLayout touchList = makeInnerList();
            addConfigRow(touchList, getString(R.string.ui_orientation), touchOrientationLabel(draft), null,
                    v -> showTouchOrientationPicker(draft, isNew), true);
            addConfigRow(touchList, getString(R.string.ui_output), getString(R.string.touch_output), null, null, true);
            addConfigRow(touchList, getString(R.string.touch_safe_area), getString(R.string.touch_safe_area_hint),
                    getString(draft.safeTouchArea ? R.string.ui_on : R.string.ui_off), v -> {
                        draft.safeTouchArea = !draft.safeTouchArea;
                        showKeyboardEditor(draft, isNew);
                    }, true);
            addConfigRow(touchList, getString(R.string.touch_pinning), getString(R.string.touch_pinning_hint),
                    getString(draft.screenPinning ? R.string.ui_on : R.string.ui_off), v -> {
                        draft.screenPinning = !draft.screenPinning;
                        showKeyboardEditor(draft, isNew);
                    }, false);
            touchCard.addView(touchList, makeInnerListParams());
            TextView explanation = new TextView(this);
            explanation.setText(getString(R.string.touch_mapping_hint));
            explanation.setTextColor(Color.rgb(91, 102, 121));
            explanation.setTextSize(12);
            explanation.setPadding(0, dp(12), 0, 0);
            touchCard.addView(explanation);
            content.addView(touchCard, spacedParams());
        } else {
            LinearLayout layoutCard = createCardLayout(dp(24), 3f);
            layoutCard.setOrientation(LinearLayout.VERTICAL);
            layoutCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            addCardHeading(layoutCard, getString(R.string.ui_layout));
            LinearLayout layoutList = makeInnerList();
            addConfigRow(layoutList, getString(R.string.ui_orientation), remoteOrientationLabel(draft.type), null, null, true);
            addConfigRow(layoutList, getString(R.string.ui_output), fixedKeyboardSignalLabel(draft.type), null, null, false);
            layoutCard.addView(layoutList, makeInnerListParams());
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.bottomMargin = dp(12);
            content.addView(layoutCard, layoutParams);
        }

        TextView saveButton = makePrimaryTextButton(getString(R.string.ui_done), v -> finishKeyboardEditor(draft));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, -2);
        saveParams.bottomMargin = dp(10);
        content.addView(saveButton, saveParams);

        if (!isNew) {
            TextView deleteButton = makeDangerTextButton(getString(R.string.ui_delete_keyboard), v -> confirmDeleteKeyboard(draft));
            content.addView(deleteButton, new LinearLayout.LayoutParams(-1, -2));
        }

        View scaffold = buildScaffold(content, PAGE_SETTINGS);
        ScrollView scroll = keyboardEditorScroll;
        if (previousScroll > 0) {
            scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                    scroll.scrollTo(0, previousScroll);
                    return true;
                }
            });
        }
        setContentView(scaffold);
    }

    private void showTouchOrientationPicker(CustomKeyboardConfig draft, boolean isNew) {
        List<PickerItem> items = new ArrayList<>();
        for (int resource : new int[]{R.string.ui_portrait, R.string.ui_landscape}) {
            items.add(new PickerItem(getString(resource), null, null, IconView.ICON_DEVICE, appearance.accent(), true));
        }
        showStyledChoiceDialog(getString(R.string.ui_orientation), null, items,
                CONTROL_MODE_LANDSCAPE.equals(draft.controlMode) ? 1 : 0, which -> {
                    draft.controlMode = which == 1 ? CONTROL_MODE_LANDSCAPE : CONTROL_MODE_PORTRAIT;
                    showKeyboardEditor(draft, isNew);
                });
    }

    private void showKeyboardNameDialog(CustomKeyboardConfig draft, boolean isNew) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(draft.name);
        input.setSelection(input.getText().length());
        input.setPadding(dp(16), dp(10), dp(16), dp(10));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.ui_keyboard_name))
                .setView(input)
                .setPositiveButton(getString(R.string.ui_ok), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) draft.name = name;
                    showKeyboardEditor(draft, isNew);
                })
                .setNegativeButton(getString(R.string.ui_cancel), null)
                .show();
    }

    private void showKeyboardBackgroundPicker(CustomKeyboardConfig draft, boolean isNew) {
        List<PickerItem> items = new ArrayList<>();
        boolean hasImage = hasCustomBackground(draft);
        items.add(new PickerItem(getString(R.string.ui_black_background), getString(R.string.ui_default_background_for_gesture_input), hasImage ? null : getString(R.string.ui_selected),
                IconView.ICON_DEVICE, Color.rgb(18, 28, 47), true));
        items.add(new PickerItem(getString(R.string.ui_choose_image), hasImage ? getString(R.string.ui_replace_current_image) : getString(R.string.ui_choose_an_image_for_the_full_screen_background),
                hasImage ? getString(R.string.ui_set) : null, IconView.ICON_DEVICE, appearance.accent(), true));
        showStyledChoiceDialog(getString(R.string.ui_background), null, items, hasImage ? 1 : 0, which -> {
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
            Toast.makeText(this, getString(R.string.ui_no_image_picker_available), Toast.LENGTH_SHORT).show();
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
                if (flags != 0) getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
            Toast.makeText(this, getString(R.string.ui_could_not_read_image), Toast.LENGTH_SHORT).show();
            showKeyboardEditor(draft, isNew);
            return;
        }

        enterRemoteFullscreen();
        allowScreenSleep();
        if (isTouchKeyboard(draft)) applyOrientationForRemote(draft);
        else applyOrientationForSetup();
        showingRemotePage = false;
        showingSettingsPage = true;
        showingKeyboardPage = false;
        connectionTitleView = null;
        connectionSubtitleView = null;
        connectionStatusIcon = null;
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
        title.setText(getString(R.string.ui_crop_background));
        setTextSizeSp(title, 14);
        title.setTextColor(Color.rgb(18, 28, 47));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.ui_preview_matches_the_keyboard_aspect_ratio));
        setTextSizeSp(subtitle, 10.5f);
        subtitle.setTextColor(Color.rgb(91, 102, 121));
        subtitle.setPadding(0, dp(5), 0, 0);
        titleTextWrap.addView(title, new LinearLayout.LayoutParams(-1, -2));
        titleTextWrap.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        titleCard.addView(titleTextWrap, new LinearLayout.LayoutParams(0, -2, 1f));
        titleCard.addView(makeSmallTextButton(getString(R.string.ui_cancel), v -> {
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
        buttonRow.addView(makeSmallTextButton(getString(R.string.ui_cancel), v -> {
            cropView.release();
            showKeyboardEditor(draft, isNew);
        }), cancelParams);
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(0, -2, 1f);
        useParams.leftMargin = dp(6);
        buttonRow.addView(makePrimaryTextButton(getString(R.string.ui_use), v -> saveCroppedKeyboardBackground(draft, isNew, cropView)), useParams);

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
                Toast.makeText(this, getString(R.string.ui_could_not_crop_image), Toast.LENGTH_SHORT).show();
                return;
            }

            File dir = new File(getFilesDir(), "keyboard_backgrounds");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, getString(R.string.ui_could_not_save_background), Toast.LENGTH_SHORT).show();
                return;
            }
            File output = new File(dir, draft.id + "_background.jpg");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, stream);
            }
            draft.backgroundImageUri = Uri.fromFile(output).toString();
            keyboardStore.upsertKeyboardConfig(draft);
            cropView.release();
            Toast.makeText(this, getString(R.string.ui_background_saved), Toast.LENGTH_SHORT).show();
            showKeyboardEditor(draft, isNew);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.ui_could_not_save_background), Toast.LENGTH_SHORT).show();
        } finally {
            if (cropped != null && !cropped.isRecycled()) cropped.recycle();
        }
    }

    private void showGesturePicker(CustomKeyboardConfig draft, boolean isNew) {
        pickTrigger(draft, null, null, selected -> showNewActionPicker(draft, selected, isNew));
    }

    private void pickTrigger(CustomKeyboardConfig draft, GestureBinding ignored, GestureBinding current,
            GestureBindingEditorDialog.GestureSelected selected) {
        List<PickerItem> tapItems = new ArrayList<>();
        List<int[]> tapValues = new ArrayList<>();
        for (int fingers = 1; fingers <= 3; fingers++) {
            for (int taps = 1; taps <= 3; taps++) {
                tapItems.add(new PickerItem(fingerLabel(fingers) + tapCountLabel(taps), getString(R.string.ui_tap_gesture), null,
                        IconView.ICON_GESTURE_KEYBOARD, appearance.accent(), true, GestureBinding.tapKey(fingers, taps, TAP_AREA_FULL)));
                tapValues.add(new int[]{fingers, taps});
            }
        }
        List<PickerItem> motions = new ArrayList<>();
        List<GestureBinding> motionValues = new ArrayList<>();
        for (int fingers = 1; fingers <= 3; fingers++) {
            for (String motion : MOTION_GESTURES) {
                if (fingers > 1 && (MOTION_CIRCLE_CLOCKWISE.equals(motion) || MOTION_CIRCLE_COUNTERCLOCKWISE.equals(motion))) continue;
                GestureBinding binding = new GestureBinding();
                binding.kind = GESTURE_KIND_MOTION;
                binding.fingers = fingers;
                binding.tapCount = 0;
                binding.area = TAP_AREA_FULL;
                binding.motion = motion;
                binding.actionId = "none";
                boolean configured = GestureRules.hasBindingConflict(draft.bindings, binding, ignored);
                motions.add(new PickerItem(gestureTitle(binding), configured ? getString(R.string.ui_already_configured) : getString(R.string.ui_full_screen_gesture),
                        configured ? getString(R.string.ui_configured) : null, IconView.ICON_GESTURE_KEYBOARD, appearance.accent(), !configured, binding.key()));
                motionValues.add(binding);
            }
        }
        List<List<PickerItem>> groups = new ArrayList<>();
        groups.add(tapItems); groups.add(motions);
        int[] selectedIndices = {-1, -1};
        int initialPage = current != null && GESTURE_KIND_MOTION.equals(current.kind) ? 1 : 0;
        if (current != null) {
            if (initialPage == 0) selectedIndices[0] = (current.fingers - 1) * 3 + current.tapCount - 1;
            else for (int i = 0; i < motionValues.size(); i++) if (motionValues.get(i).key().equals(current.key())) selectedIndices[1] = i;
        }
        showChoiceDialog(getString(current == null ? R.string.ui_add_action : R.string.binding_choose_trigger),
                getString(R.string.demo_hold_hint), groups, selectedIndices, initialPage, new PickerSelectionHandler[]{which -> {
            int[] value = tapValues.get(which);
            pickTapArea(draft, ignored, current, value[0], value[1], selected);
        }, which -> selected.accept(motionValues.get(which))},
                new String[]{getString(R.string.ui_tap), getString(R.string.ui_swipe)});
    }

    private void pickTapArea(CustomKeyboardConfig draft, GestureBinding ignored, GestureBinding current, int fingers, int taps,
            GestureBindingEditorDialog.GestureSelected selected) {
        List<PickerItem> items = new ArrayList<>();
        int selectedIndex = -1;
        for (int i = 0; i < TAP_AREAS.length; i++) {
            String area = TAP_AREAS[i];
            boolean conflict = hasTapAreaConflict(draft, fingers, taps, area, ignored);
            if (current != null && GESTURE_KIND_TAP.equals(current.kind) && current.fingers == fingers
                    && current.tapCount == taps && area.equals(current.area)) selectedIndex = i;
            items.add(new PickerItem(tapAreaLabel(area), conflict ? getString(R.string.ui_overlaps_another_region) : getString(R.string.ui_trigger_area),
                    conflict ? getString(R.string.ui_conflict) : null, IconView.ICON_DEVICE, appearance.accent(), !conflict, GestureBinding.tapKey(fingers, taps, area)));
        }
        showStyledChoiceDialog(fingerLabel(fingers) + tapCountLabel(taps) + getString(R.string.ui_region), getString(R.string.demo_hold_hint), items, selectedIndex, which -> {
            String area = TAP_AREAS[which];
            GestureBinding binding = new GestureBinding();
            binding.kind = GESTURE_KIND_TAP;
            binding.fingers = fingers;
            binding.tapCount = taps;
            binding.area = area;
            binding.motion = "";
            binding.actionId = "none";
            selected.accept(binding);
        });
    }

    private void showNewActionPicker(CustomKeyboardConfig draft, GestureBinding binding, boolean isNew) {
        showSignalPicker(binding, value -> {
            binding.actionId = value;
            draft.bindings.add(binding);
            showKeyboardEditor(draft, isNew);
        });
    }

    private void showSignalPicker(GestureBinding binding, SignalPickerDialog.OnSelected selected) {
        if (signalPicker != null) signalPicker.dismiss();
        SignalPickerDialog dialog = new SignalPickerDialog(this, appearance, gestureDemos, binding.actionId, gestureTitle(binding), selected);
        signalPicker = dialog;
        dialog.setOnDismissListener(d -> { if (signalPicker == dialog) signalPicker = null; });
        dialog.show();
    }

    private void showBindingEditor(CustomKeyboardConfig draft, GestureBinding original, boolean isNew) {
        if (bindingEditor != null) bindingEditor.dismiss();
        GestureBindingEditorDialog dialog = new GestureBindingEditorDialog(this, appearance, gestureDemos, original,
                new GestureBindingEditorDialog.Listener() {
                    @Override public String title(GestureBinding binding) { return gestureTitle(binding); }
                    @Override public void chooseGesture(GestureBinding current, GestureBindingEditorDialog.GestureSelected selected) {
                        pickTrigger(draft, original, current, selected);
                    }
                    @Override public void chooseSignal(GestureBinding current, SignalPickerDialog.OnSelected selected) {
                        showSignalPicker(current, selected);
                    }
                    @Override public void preview(String id) { showGesturePreview(id); }
                    @Override public int save(GestureBinding edited) {
                        int index = draft.bindings.indexOf(original);
                        if (index < 0) return R.string.binding_missing;
                        if (GestureRules.hasBindingConflict(draft.bindings, edited, original)) return R.string.binding_conflict;
                        try { SignalProgram.parse(edited.actionId); }
                        catch (IllegalArgumentException e) { return R.string.signal_invalid; }
                        draft.bindings.set(index, edited.copy());
                        showKeyboardEditor(draft, isNew);
                        return 0;
                    }
                });
        bindingEditor = dialog;
        dialog.setOnDismissListener(d -> {
            if (bindingEditor != dialog) return;
            bindingEditor = null;
            if (gesturePreview != null) gesturePreview.dismiss();
            if (choiceDialog != null) choiceDialog.dismiss();
            if (signalPicker != null) signalPicker.dismiss();
        });
        dialog.show();
    }

    private void showGesturePreview(String id) {
        GestureDemoCatalog.Demo demo = gestureDemos.find(id);
        if (demo == null) { Toast.makeText(this, R.string.demo_failed, Toast.LENGTH_SHORT).show(); return; }
        if (gesturePreview != null) gesturePreview.dismiss();
        String title = demo.action ? gestureTitle(demo.binding()) : ActionLabels.label(this, HidAction.byId(id));
        GesturePreviewDialog dialog = new GesturePreviewDialog(this, appearance, gestureDemos, demo, title);
        gesturePreview = dialog;
        dialog.setOnDismissListener(d -> { if (gesturePreview == dialog) gesturePreview = null; });
        dialog.show();
    }

    private View gestureThumbnail(String id) {
        GestureDemoCatalog.Demo demo = gestureDemos.find(id);
        if (demo == null) return makeIconBubble(34, appearance.tint(), IconView.ICON_GESTURE_KEYBOARD, appearance.accent(), 1.6f);
        android.widget.ImageView image = new android.widget.ImageView(this);
        image.setImageDrawable(new GestureThumbnailDrawable(demo, appearance));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(40)));
        return image;
    }

    private void showStyledChoiceDialog(
            String title,
            String subtitle,
            List<PickerItem> items,
            int selectedIndex,
            PickerSelectionHandler selectionHandler
    ) {
        List<List<PickerItem>> groups = new ArrayList<>(); groups.add(items);
        showChoiceDialog(title, subtitle, groups, selectedIndex, new PickerSelectionHandler[]{selectionHandler}, null);
    }

    private void showChoiceDialog(String title, String subtitle, List<List<PickerItem>> groups, int selectedIndex,
            PickerSelectionHandler[] handlers, String[] tabLabels) {
        int[] selectedIndices = new int[groups.size()]; java.util.Arrays.fill(selectedIndices, -1); selectedIndices[0] = selectedIndex;
        showChoiceDialog(title, subtitle, groups, selectedIndices, 0, handlers, tabLabels);
    }

    private void showChoiceDialog(String title, String subtitle, List<List<PickerItem>> groups, int[] selectedIndices, int initialPage,
            PickerSelectionHandler[] handlers, String[] tabLabels) {
        final AlertDialog[] dialogRef = new AlertDialog[1];

        LinearLayout dialogCard = createCardLayout(dp(24), 3f);
        dialogCard.setOrientation(LinearLayout.VERTICAL);
        dialogCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        dialogCard.setClipChildren(true);
        dialogCard.setClipToPadding(true);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        setTextSizeSp(titleView, 14);
        titleView.setTextColor(Color.rgb(18, 28, 47));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dialogCard.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            setTextSizeSp(subtitleView, 10.5f);
            subtitleView.setTextColor(Color.rgb(91, 102, 121));
            subtitleView.setPadding(0, dp(5), 0, 0);
            dialogCard.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout list = makeInnerList();
        list.setBackground(null);
        list.setPadding(0, dp(2), 0, dp(2));
        populatePickerRows(list, groups.get(initialPage), selectedIndices[initialPage], handlers[initialPage], dialogRef);

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

        if (tabLabels != null) {
            PickerTabs tabs = new PickerTabs(this, tabLabels, true, index -> {
                populatePickerRows(list, groups.get(index), selectedIndices[index], handlers[index], dialogRef);
                scrollView.scrollTo(0, 0);
            });
            tabs.setSelectedIndex(initialPage);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, -2);
            tabParams.topMargin = dp(8);
            dialogCard.addView(tabs, tabParams);
        }

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int count = 0;
        for (List<PickerItem> group : groups) count = Math.max(count, group.size());
        int estimatedListHeight = count * dp(58) + Math.max(0, count - 1) * dp(1) + dp(4);
        int maxListHeight = Math.min(dp(390), Math.max(dp(tabLabels == null ? 220 : 180), screenHeight - dp(tabLabels == null ? 320 : 368)));
        boolean needsScroll = estimatedListHeight > maxListHeight;
        listViewport.addView(scrollView, new FrameLayout.LayoutParams(-1, needsScroll ? -1 : -2));

        LinearLayout.LayoutParams viewportParams = new LinearLayout.LayoutParams(-1, needsScroll ? maxListHeight : -2);
        viewportParams.topMargin = dp(12);
        dialogCard.addView(listViewport, viewportParams);

        TextView cancelButton = makeSmallTextButton(getString(R.string.ui_cancel), v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, -2);
        cancelParams.topMargin = dp(12);
        dialogCard.addView(cancelButton, cancelParams);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialogRef[0] = dialog;
        if (choiceDialog != null) choiceDialog.dismiss();
        choiceDialog = dialog;
        dialog.setOnDismissListener(d -> { if (choiceDialog == dialog) choiceDialog = null; });
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

    private void populatePickerRows(LinearLayout list, List<PickerItem> items, int selectedIndex,
            PickerSelectionHandler handler, AlertDialog[] dialogRef) {
        list.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            addPickerRow(list, items.get(i), i, i == selectedIndex, handler, dialogRef);
            if (i < items.size() - 1) list.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
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
            makeInteractive(row);
        row.setOnClickListener(v -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                selectionHandler.onSelect(index);
            });
        } else {
            row.setAlpha(0.52f);
        }

        row.setSelected(selected);
        // Configured/conflicting gestures remain unselectable, but their long-press explanation is available.
        row.setEnabled(item.enabled || item.demoId != null);
        int iconColor = item.enabled ? item.iconColor : Color.rgb(150, 162, 184);
        row.addView(item.demoId == null ? makeIconBubble(34, appearance.tint(), item.iconType, iconColor, 1.6f) : gestureThumbnail(item.demoId));
        if (item.demoId != null) row.setOnLongClickListener(v -> { showGesturePreview(item.demoId); return true; });

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(10);

        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        titleView.setEnabled(item.enabled);
        setTextSizeSp(titleView, 12.5f);
        titleView.setTextColor(selected ? appearance.accent() : Color.rgb(20, 29, 48));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setSingleLine(false);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(item.subtitle == null ? "" : item.subtitle);
        subtitleView.setEnabled(item.enabled);
        subtitleView.setVisibility(item.subtitle == null ? View.GONE : View.VISIBLE);
        setTextSizeSp(subtitleView, 10);
        subtitleView.setTextColor(Color.rgb(91, 102, 121));
        subtitleView.setPadding(0, dp(5), 0, 0);
        subtitleView.setSingleLine(false);

        textWrap.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap, textParams);

        String chipText = selected ? getString(R.string.ui_selected) : item.chipText;
        if (chipText != null) {
            row.addView(makeStatusChip(chipText, selected || getString(R.string.ui_available).equals(chipText)), new LinearLayout.LayoutParams(-2, -2));
        }

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void finishKeyboardEditor(CustomKeyboardConfig draft) {
        draft.name = draft.name == null ? "" : draft.name.trim();
        if (draft.name.isEmpty()) {
            Toast.makeText(this, getString(R.string.ui_enter_a_keyboard_name_first), Toast.LENGTH_SHORT).show();
            return;
        }
        keyboardStore.upsertKeyboardConfig(draft);
        showSettingsPage();
    }

    private void confirmDeleteKeyboard(CustomKeyboardConfig draft) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.ui_delete_keyboard))
                .setMessage(getString(R.string.ui_bluetooth_pairing_and_other_keyboards_will_be_kept))
                .setPositiveButton(getString(R.string.ui_delete), (dialog, which) -> {
                    keyboardStore.deleteKeyboardConfig(draft.id);
                    Toast.makeText(this, getString(R.string.ui_keyboard_deleted), Toast.LENGTH_SHORT).show();
                    showSettingsPage();
                })
                .setNegativeButton(getString(R.string.ui_cancel), null)
                .show();
    }

    private void showRemotePage(BluetoothDevice device, CustomKeyboardConfig keyboardConfig) {
        touchBackDeadline = 0;
        if (isTouchKeyboard(keyboardConfig) && HidRemoteService.IS_BLE_COMPAT) {
            Toast.makeText(this, R.string.signal_touch_ble, Toast.LENGTH_LONG).show();
            return;
        }
        keepScreenAwake();
        showingRemotePage = true;
        showingSettingsPage = false;
        showingKeyboardPage = false;
        activeKeyboardConfig = keyboardConfig == null ? keyboardStore.createDefaultKeyboardFromLegacy() : keyboardConfig.copy();
        applyOrientationForRemote(activeKeyboardConfig);
        enterRemoteFullscreen();
        connectionTitleView = null;
        connectionSubtitleView = null;
        connectionStatusIcon = null;
        pairedListLayout = null;

        if (isTouchKeyboard(activeKeyboardConfig)) {
            TouchPassthroughView surface = new TouchPassthroughView(this, appearance.primary(), activeKeyboardConfig.vibrationEnabled,
                    activeKeyboardConfig.vibrationLevel);
            touchSurface = surface;
            surface.setSafeAreaEnabled(activeKeyboardConfig.safeTouchArea);
            surface.setBackgroundImageUri(activeKeyboardConfig.backgroundImageUri);
            surface.setListener(report -> {
                // Detach/focus callbacks from an old surface must never end a newly opened session.
                if (touchSurface != surface || !showingRemotePage) return false;
                if (report != null && TouchState.mask(report) != 0) touchBackDeadline = 0;
                boolean accepted = hidService != null && hidService.sendTouchState(report);
                if (!accepted && report != null) Toast.makeText(this, R.string.touch_connection_required, Toast.LENGTH_SHORT).show();
                return accepted;
            });
            surface.setExitListener(this::exitRemotePage);
            setContentView(surface);
            enterRemoteFullscreen();
            if (activeKeyboardConfig.screenPinning) mainHandler.post(() -> {
                if (touchSurface == surface && showingRemotePage && isTouchKeyboard(activeKeyboardConfig) && activeKeyboardConfig.screenPinning) {
                    screenPinningProtection.request();
                }
            });
        } else if (isComputerKeyboard(activeKeyboardConfig)) {
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
        root.setBackgroundColor(Color.rgb(247, 248, 250));
        getWindow().setStatusBarColor(Color.rgb(247, 248, 250));
        getWindow().setNavigationBarColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ScrollView scrollView = new ScrollView(this);
        if (editingKeyboard) keyboardEditorScroll = scrollView;
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipChildren(false);
        scrollView.setClipToPadding(false);
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        View bottomNav = buildBottomNavigation(selectedPage);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, -2);
        navParams.setMargins(0, 0, 0, 0);
        root.addView(bottomNav, navParams);

        return root;
    }


    private LinearLayout createPageContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(20) + Math.max(0, (getResources().getDisplayMetrics().widthPixels - dp(640)) / 2);
        content.setPadding(horizontal, dp(24), horizontal, dp(24));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        return content;
    }

    private LinearLayout createCardLayout(int radiusPx, float elevationDp) {
        LinearLayout card = new LinearLayout(this);
        applySurfaceStyle(card, Math.min(radiusPx, dp(20)), surfaceFillColor(), surfaceStrokeColor(), 0);
        return card;
    }

    private void applySurfaceStyle(View view, int radiusPx, int fillColor, int strokeColor, float elevationDp) {
        view.setBackground(roundedRect(fillColor, radiusPx, strokeColor, 1));
    }

    private int surfaceFillColor() {
        return Color.argb(246, 255, 255, 255);
    }

    private int surfaceStrokeColor() {
        return Color.argb(20, 24, 38, 66);
    }


    private View makeIconButton(int iconType, View.OnClickListener listener) {
        FrameLayout button = new FrameLayout(this);
        button.setBackground(roundedRect(Color.rgb(248, 250, 255), dp(14), Color.rgb(233, 239, 248), 1));
        button.setOnClickListener(listener);
        makeInteractive(button);
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
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));

        nav.addView(makeTabItem(getString(R.string.home), IconView.ICON_HOME, selectedPage == PAGE_KEYBOARDS, v -> showKeyboardsPage()),
                new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(makeTabItem(getString(R.string.connection), connectionIconType(), selectedPage == PAGE_CONNECTION, v -> showSetupPage()),
                new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(makeTabItem(getString(R.string.settings), IconView.ICON_SETTINGS, selectedPage == PAGE_SETTINGS, v -> showSettingsPage()),
                new LinearLayout.LayoutParams(0, -2, 1f));
        return nav;
    }

    private View makeTabItem(String label, int iconType, boolean selected, View.OnClickListener listener) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);
        tab.setPadding(dp(6), dp(4), dp(6), 0);
        tab.setOnClickListener(listener);
        makeInteractive(tab);
        tab.setSelected(selected);
        tab.setContentDescription(label);
        tab.setMinimumHeight(dp(56));
        tab.setBackground(roundedRect(selected ? appearance.tint() : Color.TRANSPARENT, dp(16), Color.TRANSPARENT, 0));

        int activeColor = appearance.accent();
        int inactiveColor = Color.rgb(116, 129, 156);
        int tint = selected ? activeColor : inactiveColor;

        IconView iconView = new IconView(this, iconType, tint, 1.8f);
        if (iconType == IconView.ICON_LINK || iconType == IconView.ICON_LINK_OFF) {
            bottomConnectionIcon = iconView;
        }
        tab.addView(iconView, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView textView = new TextView(this);
        textView.setText(label);
        setTextSizeSp(textView, 10.5f);
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
        setTextSizeSp(chip, 9.5f);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setTextColor(active ? appearance.accent() : Color.rgb(117, 129, 155));
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        chip.setBackground(roundedRect(
                active ? appearance.tint() : appearance.tint(),
                dp(12),
                active ? appearance.tint() : Color.rgb(232, 237, 245),
                1
        ));
        return chip;
    }

    private TextView makeSmallTextButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        setTextSizeSp(button, 11);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(appearance.accent());
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
        button.setBackground(roundedRect(appearance.tint(), dp(14), appearance.tint(), 1));
        button.setOnClickListener(listener);
        makeInteractive(button);
        return button;
    }

    private TextView makePrimaryTextButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        setTextSizeSp(button, 12.5f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(appearance.accent());
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(roundedRect(appearance.primary(), dp(18), appearance.primary(), 0));
        button.setOnClickListener(listener);
        makeInteractive(button);
        return button;
    }

    private TextView makeDangerTextButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        setTextSizeSp(button, 12.5f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(Color.rgb(220, 60, 60));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(roundedRect(Color.rgb(255, 245, 245), dp(18), Color.rgb(255, 220, 220), 1));
        button.setOnClickListener(listener);
        makeInteractive(button);
        return button;
    }

    private void addCardHeading(LinearLayout card, String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        setTextSizeSp(heading, 13);
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
        if (listener != null) {
            makeInteractive(row);
            row.setOnClickListener(listener);
        }

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        setTextSizeSp(titleView, 12.5f);
        titleView.setTextColor(Color.rgb(20, 29, 48));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle == null ? "" : subtitle);
        setTextSizeSp(subtitleView, 10);
        subtitleView.setTextColor(Color.rgb(91, 102, 121));
        subtitleView.setPadding(0, dp(5), 0, 0);
        subtitleView.setSingleLine(false);

        textWrap.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap);

        if (chipText != null) {
            row.addView(makeStatusChip(chipText, getString(R.string.ui_on).equals(chipText) || getString(R.string.ui_available).equals(chipText)), new LinearLayout.LayoutParams(-2, -2));
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
        setTextSizeSp(row, 12);
        row.setTextColor(Color.rgb(91, 102, 121));
        row.setLineSpacing(0f, 1.12f);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addKeyboardRunRow(LinearLayout parent, CustomKeyboardConfig config, BluetoothDevice connected, boolean addSeparator) {
        LinearLayout row = makeKeyboardSummaryRow(config);
        makeInteractive(row);
        row.setOnClickListener(v -> {
            if (!hasConnectedHost()) {
                showSetupPage();
                Toast.makeText(this, getString(R.string.ui_connect_a_paired_device_in_connection_first), Toast.LENGTH_SHORT).show();
                return;
            }
            launchKeyboardRun(connected, config);
        });
        row.addView(makeStatusChip(connected == null ? getString(R.string.ui_disconnected) : getString(R.string.ui_start), connected != null), new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (addSeparator) parent.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void launchKeyboardRun(BluetoothDevice device, CustomKeyboardConfig config) {
        if (hidService == null) {
            updateStatus(getString(R.string.ui_keep_bluetooth_on_and_wait_for_the_keyboard_service));
            startAndBindHidService();
            return;
        }
        showRemotePage(device, config);
    }

    private void addKeyboardEditRow(LinearLayout parent, CustomKeyboardConfig config, boolean addSeparator) {
        LinearLayout row = makeKeyboardSummaryRow(config);
        makeInteractive(row);
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

        row.addView(makeIconBubble(38, appearance.tint(), isComputerKeyboard(config) ? IconView.ICON_KEYBOARD : IconView.ICON_GESTURE_KEYBOARD, appearance.accent(), 1.7f));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(10);

        TextView nameView = new TextView(this);
        nameView.setText(config.name);
        setTextSizeSp(nameView, 12.5f);
        nameView.setTextColor(Color.rgb(20, 29, 48));
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nameView.setMaxLines(2);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView summaryView = new TextView(this);
        summaryView.setText(keyboardSummary(config));
        setTextSizeSp(summaryView, 10);
        summaryView.setTextColor(Color.rgb(91, 102, 121));
        summaryView.setPadding(0, dp(5), 0, 0);
        summaryView.setMaxLines(3);

        textWrap.addView(nameView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(summaryView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap, textParams);
        return row;
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
        makeInteractive(row);
        row.setOnClickListener(v -> showBindingEditor(draft, binding, isNew));
        row.setOnLongClickListener(v -> { showGesturePreview(binding.key()); return true; });
        row.addView(gestureThumbnail(binding.key()));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);

        textParams.leftMargin = dp(10);

        TextView titleView = new TextView(this);
        titleView.setText(gestureTitle(binding));
        setTextSizeSp(titleView, 12.5f);
        titleView.setTextColor(Color.rgb(20, 29, 48));
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(ActionLabels.binding(this, binding.actionId));
        setTextSizeSp(subtitleView, 10);
        subtitleView.setTextColor(Color.rgb(91, 102, 121));
        subtitleView.setPadding(0, dp(5), 0, 0);

        textWrap.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textWrap.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textWrap, textParams);

        TextView deleteButton = makeSmallTextButton(getString(R.string.ui_delete), v -> {
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

    private String keyboardTypeLabel(String type) {
        if (KEYBOARD_TYPE_TOUCH_PASSTHROUGH.equals(type)) return getString(R.string.touch_passthrough);
        return KEYBOARD_TYPE_COMPUTER.equals(type) ? getString(R.string.ui_computer_keyboard) : getString(R.string.ui_gesture_keyboard);
    }

    private boolean isGestureKeyboard(CustomKeyboardConfig config) {
        return config == null || KEYBOARD_TYPE_FULLSCREEN_GESTURE.equals(config.type);
    }

    private boolean isComputerKeyboard(CustomKeyboardConfig config) {
        return config != null && KEYBOARD_TYPE_COMPUTER.equals(config.type);
    }

    private boolean isTouchKeyboard(CustomKeyboardConfig config) {
        return config != null && KEYBOARD_TYPE_TOUCH_PASSTHROUGH.equals(config.type);
    }

    private String touchOrientationLabel(CustomKeyboardConfig config) {
        return getString(CONTROL_MODE_LANDSCAPE.equals(config.controlMode) ? R.string.ui_landscape : R.string.ui_portrait);
    }

    private boolean isLandscapeRemoteType(String type) {
        return KEYBOARD_TYPE_COMPUTER.equals(type);
    }

    private String remoteOrientationLabel(String type) {
        return isLandscapeRemoteType(type) ? getString(R.string.ui_landscape) : getString(R.string.ui_portrait);
    }

    private String fixedKeyboardSignalLabel(String type) {
        return getString(R.string.ui_standard_hid_keyboard_modifiers_and_up_to_6_keys);
    }

    private String vibrationLevelLabel(int level) {
        switch (level) {
            case VIBRATION_LIGHT:
                return getString(R.string.ui_light);
            case VIBRATION_STRONG:
                return getString(R.string.ui_strong);
            case VIBRATION_MEDIUM:
            default:
                return getString(R.string.ui_medium);
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
        GestureBinding candidate = new GestureBinding(); candidate.kind = GESTURE_KIND_TAP;
        candidate.fingers = fingers; candidate.tapCount = tapCount; candidate.area = area;
        return GestureRules.hasBindingConflict(config.bindings, candidate, ignoredBinding);
    }

    private String gestureTitle(GestureBinding binding) {
        if (binding == null) return getString(R.string.ui_unknown_gesture);
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
                return getString(R.string.ui_two_fingers);
            case 3:
                return getString(R.string.ui_three_fingers);
            case 1:
            default:
                return getString(R.string.ui_one_finger);
        }
    }

    private String tapCountLabel(int tapCount) {
        switch (tapCount) {
            case 2:
                return getString(R.string.ui_double_tap);
            case 3:
                return getString(R.string.ui_triple_tap);
            case 1:
            default:
                return getString(R.string.ui_single_tap);
        }
    }

    private String tapAreaLabel(String area) {
        if (TAP_AREA_LEFT.equals(area)) return getString(R.string.ui_left_half);
        if (TAP_AREA_RIGHT.equals(area)) return getString(R.string.ui_right_half);
        if (TAP_AREA_TOP.equals(area)) return getString(R.string.ui_top_half);
        if (TAP_AREA_BOTTOM.equals(area)) return getString(R.string.ui_bottom_half);
        return getString(R.string.ui_full_screen);
    }

    private String motionLabel(String motion) {
        if (MOTION_SWIPE_UP.equals(motion)) return getString(R.string.ui_swipe_up);
        if (MOTION_SWIPE_DOWN.equals(motion)) return getString(R.string.ui_swipe_down);
        if (MOTION_SWIPE_LEFT.equals(motion)) return getString(R.string.ui_swipe_left);
        if (MOTION_SWIPE_RIGHT.equals(motion)) return getString(R.string.ui_swipe_right);
        if (MOTION_CIRCLE_CLOCKWISE.equals(motion)) return getString(R.string.ui_clockwise_circle);
        if (MOTION_CIRCLE_COUNTERCLOCKWISE.equals(motion)) return getString(R.string.ui_counterclockwise_circle);
        return getString(R.string.ui_swipe);
    }

    private String keyboardSummary(CustomKeyboardConfig config) {
        String vibration = config.vibrationEnabled ? getString(R.string.ui_vibration_123) + vibrationLevelLabel(config.vibrationLevel) : getString(R.string.ui_vibration_off);

        if (isTouchKeyboard(config)) {
            return getString(R.string.touch_passthrough) + " · " + touchOrientationLabel(config) + " · " + vibration;
        }
        if (isComputerKeyboard(config)) {
            return keyboardTypeLabel(config.type) + getString(R.string.ui_qwerty) + remoteOrientationLabel(config.type);
        }
        return keyboardTypeLabel(config.type) + " · " + config.bindings.size() + getString(R.string.ui_gestures_126) + vibration;
    }

    private String keyboardBackgroundLabel(CustomKeyboardConfig config) {
        return hasCustomBackground(config) ? getString(R.string.ui_custom_image) : getString(R.string.ui_solid_black);
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
        if (showingRemotePage && isTouchKeyboard(activeKeyboardConfig)) {
            touchBarsHidden = true;
            SystemGestureProtection.hideBars(getWindow());
        }
    }

    private void exitRemoteFullscreen() {
        if (screenPinningProtection != null) screenPinningProtection.stop();
        if (touchBarsHidden) {
            SystemGestureProtection.restoreBars(getWindow());
            touchBarsHidden = false;
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
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

    private void applyOrientationForRemote(CustomKeyboardConfig config) {
        boolean landscape = isLandscapeRemoteType(config.type)
                || (isTouchKeyboard(config) && CONTROL_MODE_LANDSCAPE.equals(config.controlMode));
        setRequestedOrientation(landscape
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

    private void setTextSizeSp(TextView textView, float sizeDp) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(12f, sizeDp + 1.5f));
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
            if (HidRemoteService.IS_BLE_COMPAT && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), REQ_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                && (!HidRemoteService.IS_BLE_COMPAT || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED));
    }

    private void toggleBluetooth() {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermissionsIfNeeded();
            updateStatus(getString(R.string.ui_grant_nearby_devices_permission_then_try_again));
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
            updateStatus(getString(R.string.ui_bluetooth_permission_is_required));
        }
    }

    private void refreshPairedDevices() {
        refreshPairedDevices(false);
    }

    private void refreshPairedDevices(boolean userInitiated) {
        if (pairedListLayout == null) return;
        pairedListLayout.removeAllViews();

        if (!hasBluetoothConnectPermission()) {
            addInfoRow(getString(R.string.ui_grant_bluetooth_permission_first));
            finishPairedRefresh(userInitiated, getString(R.string.ui_refresh_failed), getString(R.string.ui_grant_bluetooth_permission_first_131));
            return;
        }
        if (bluetoothAdapter == null) {
            addInfoRow(getString(R.string.ui_no_bluetooth_adapter_available));
            finishPairedRefresh(userInitiated, getString(R.string.ui_refresh_failed), getString(R.string.ui_no_bluetooth_adapter_available_134));
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                addInfoRow(getString(R.string.ui_bluetooth_is_off));
                finishPairedRefresh(userInitiated, getString(R.string.ui_refresh_failed), getString(R.string.ui_bluetooth_is_off_136));
                return;
            }
            if (hidService == null) {
                addInfoRow(getString(R.string.ui_starting_keyboard_service));
                finishPairedRefresh(userInitiated, getString(R.string.ui_refreshing), getString(R.string.ui_starting_keyboard_service_138));
                return;
            }
            Set<BluetoothDevice> bondedDevices = hidService.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                addInfoRow(HidRemoteService.IS_BLE_COMPAT
                        ? "请先在阅读器端搜索并配对这台手机的 BLE 键盘，再等待连接页显示键盘已就绪。"
                        : getString(R.string.ui_no_paired_devices_pair_from_bluetooth_settings_on_your_computer_tv_or_));
                finishPairedRefresh(userInitiated, getString(R.string.ui_refreshed), getString(R.string.ui_no_paired_devices_found));
                return;
            }

            List<BluetoothDevice> devices = new ArrayList<>(bondedDevices);
            Collections.sort(devices, (left, right) -> safeDeviceName(left).compareToIgnoreCase(safeDeviceName(right)));

            for (int i = 0; i < devices.size(); i++) {
                if (i > 0) {
                    pairedListLayout.addView(makeSeparator(), new LinearLayout.LayoutParams(-1, dp(1)));
                }
                pairedListLayout.addView(makeDeviceRow(devices.get(i)), new LinearLayout.LayoutParams(-1, -2));
            }
            finishPairedRefresh(userInitiated, getString(R.string.ui_refreshed), getString(R.string.ui_found) + devices.size() + getString(R.string.ui_paired_devices_144));
        } catch (SecurityException e) {
            addInfoRow(getString(R.string.ui_cannot_read_paired_devices_without_bluetooth_permission));
            finishPairedRefresh(userInitiated, getString(R.string.ui_refresh_failed), getString(R.string.ui_bluetooth_permission_missing));
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
        makeInteractive(row);
        row.setOnClickListener(v -> connectToHost(device));

        boolean isConnected = hidService != null && device.equals(hidService.getConnectedHost());
        int deviceIconColor = isConnected ? appearance.accent() : Color.rgb(160, 170, 190);
        row.addView(makeIconBubble(38, appearance.tint(), resolveDeviceIconType(device), deviceIconColor, 1.8f));

        TextView nameView = new TextView(this);
        nameView.setText(safeDeviceName(device));
        setTextSizeSp(nameView, 12.5f);
        nameView.setTextColor(isConnected ? Color.rgb(19, 28, 47) : Color.rgb(120, 130, 150));
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nameView.setMaxLines(2);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(10);
        row.addView(nameView, nameParams);

        if (isConnected) {
            row.addView(makeStatusChip(getString(R.string.ui_connected), true), new LinearLayout.LayoutParams(-2, -2));
        } else {
            IconView chevron = new IconView(this, IconView.ICON_CHEVRON, Color.rgb(120, 134, 160), 1.5f);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(12), dp(12)));
        }
        return row;
    }

    private void connectToHost(BluetoothDevice device) {
        if (hidService == null) {
            updateStatus(getString(R.string.ui_keep_bluetooth_on_and_wait_for_the_keyboard_service));
            startAndBindHidService();
            return;
        }
        hidService.connectToHost(device);
    }

    private void disconnectHost() {
        if (hidService == null) {
            showSetupPage();
            updateStatus(getString(R.string.ui_returned_to_connection));
            refreshPairedDevices();
            return;
        }
        hidService.disconnectHost();
        showSetupPage();
        updateStatus(hidService.getStatus());
        refreshPairedDevices();
    }

    private void exitRemotePage() {
        touchBackDeadline = 0;
        if (touchSurface != null) { touchSurface.releaseTouches(); touchSurface = null; }
        activeKeyboardConfig = null;
        showKeyboardsPage(false);
        mainHandler.post(restoreSetupOrientationRunnable);
    }

    private void sendActionForTrigger(String triggerId) {
        if (hidService == null) {
            updateStatus(getString(R.string.ui_keyboard_service_unavailable_retry_from_connection));
            startAndBindHidService();
            return;
        }
        GestureBinding binding = findBindingByKey(activeKeyboardConfig, triggerId);
        try {
            hidService.sendProgram(SignalProgram.parse(binding == null ? "none" : binding.actionId));
        } catch (IllegalArgumentException e) {
            updateStatus(getString(R.string.signal_invalid));
        }
    }

    private void sendKeyboardState(int modifierMask, int[] keyboardUsages) {
        if (hidService == null) {
            updateStatus(getString(R.string.ui_keyboard_service_unavailable_retry_from_connection));
            startAndBindHidService();
            return;
        }
        hidService.sendKeyboardState(modifierMask, keyboardUsages);
    }

    private void addInfoRow(String text) {
        if (pairedListLayout == null) return;
        TextView row = new TextView(this);
        row.setText(text);
        setTextSizeSp(row, 12);
        row.setTextColor(Color.rgb(91, 102, 121));
        row.setLineSpacing(0f, 1.12f);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        pairedListLayout.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void updateStatus(String status) {
        refreshStatusCards();
        if (connectionTitleView == null || connectionSubtitleView == null) return;

        if (!hasBluetoothConnectPermission()) {
            connectionTitleView.setText(getString(R.string.ui_bluetooth_permission_required));
            connectionSubtitleView.setText(getString(R.string.ui_allow_nearby_devices_in_system_settings));
            return;
        }
        if (bluetoothAdapter == null) {
            connectionTitleView.setText(getString(R.string.ui_bluetooth_unavailable));
            connectionSubtitleView.setText(getString(R.string.ui_this_device_has_no_bluetooth_adapter));
            return;
        }

        if (HidRemoteService.IS_BLE_COMPAT) {
            connectionTitleView.setText(getString(R.string.app_name) + " · BLE HID");
            connectionSubtitleView.setText(status == null ? getString(R.string.ui_starting) : status);
            return;
        }

        BluetoothDevice connected = hidService == null ? null : hidService.getConnectedHost();
        if (connected != null) {
            connectionTitleView.setText(safeDeviceName(connected));
            connectionSubtitleView.setText(getString(R.string.connected_hint));
            return;
        }

        if (status != null && status.startsWith("正在连接：")) {
            connectionTitleView.setText(getString(R.string.ui_connecting));
            connectionSubtitleView.setText(status.substring("正在连接：".length()));
            return;
        }

        // The service keeps its existing diagnostic strings; present an actionable localized error.
        if (status != null && (status.contains("失败") || status.startsWith("没有蓝牙连接权限"))) {
            connectionTitleView.setText(getString(R.string.connection_error));
            connectionSubtitleView.setText(getString(R.string.connection_error_hint));
            return;
        }

        connectionTitleView.setText(getString(R.string.disconnected));
        connectionSubtitleView.setText(getString(!isBluetoothEnabled() ? R.string.bluetooth_off
                : showingKeyboardPage ? R.string.connect_hint : R.string.select_paired_hint));
    }

    private int connectionIconType() {
        return hasConnectedHost() ? IconView.ICON_LINK : IconView.ICON_LINK_OFF;
    }

    private void refreshStatusCards() {
        if (bottomConnectionIcon != null) bottomConnectionIcon.setIconType(connectionIconType());
        if (connectionStatusIcon != null) {
            connectionStatusIcon.setIconType(connectionIconType());
            connectionStatusIcon.setTintColor(hasConnectedHost() ? appearance.accent() : Color.rgb(160, 170, 190));
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

        String name = safeDeviceName(device).toLowerCase(java.util.Locale.ROOT);
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
        if (device == null) return getString(R.string.ui_unknown_device);
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? getString(R.string.ui_unnamed_device) : name;
        } catch (SecurityException e) {
            return getString(R.string.ui_bluetooth_device);
        }
    }

    private String safeDeviceAddress(BluetoothDevice device) {
        if (device == null) return "";
        try {
            return device.getAddress();
        } catch (SecurityException e) {
            return getString(R.string.ui_address_unavailable);
        }
    }

}
