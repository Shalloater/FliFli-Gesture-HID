package com.example.joyvolumeremote;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import static com.example.joyvolumeremote.KeyboardDefinitions.*;

public class KeyboardOptionsUiTest extends InstrumentationTestCase {
    private Activity activity;
    private KeyboardConfigStore store;
    private SharedPreferences preferences;
    private Object originalStore;
    private CustomKeyboardConfig config;

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        preferences = activity.getSharedPreferences("keyboard_options_ui_qa", Context.MODE_PRIVATE); preferences.edit().clear().commit();
        store = new KeyboardConfigStore(preferences); store.ensureKeyboardConfigsInitialized();
        config = store.createNewKeyboardDraft(KEYBOARD_TYPE_TOUCH_PASSTHROUGH); config.name = "Touch ratio QA";
        originalStore = field(activity, "keyboardStore");
        main(() -> { setField(activity, "keyboardStore", store); call("showKeyboardEditor", new Class<?>[]{CustomKeyboardConfig.class, boolean.class}, config, false); });
        settle();
    }
    @Override protected void tearDown() throws Exception {
        main(() -> { KeyboardSettingsDialog dialog = dialog(); if (dialog != null) dialog.dismiss();
            setField(activity, "keyboardStore", originalStore); activity.finish(); });
        getInstrumentation().waitForIdleSync(); preferences.edit().clear().commit(); super.tearDown();
    }

    public void testRatioPresetsCustomValidationAndCancelKeepOriginal() throws Exception {
        openRatio(); TouchRatioPickerDialog dialog = (TouchRatioPickerDialog) dialog();
        assertNotNull(find(dialog.getWindow().getDecorView(), "9 : 19.5"));
        assertNotNull(find(dialog.getWindow().getDecorView(), "16 : 9")); screenshot("ratio-presets");
        EditText width = (EditText) field(dialog, "width"), height = (EditText) field(dialog, "height");
        main(() -> { width.setText("0"); height.setText("4"); });
        assertFalse(((TextView) field(dialog, "done")).isEnabled());
        main(() -> { width.setText("9"); height.setText("19.5"); });
        assertTrue(((TextView) field(dialog, "done")).isEnabled()); screenshot("ratio-custom");
        main(() -> find(dialog.getWindow().getDecorView(), text(R.string.ui_cancel)).performClick()); settle();
        assertEquals("3:4", config.touchAspectRatio); assertEquals("3:4", stored(config.id).touchAspectRatio);
    }

    public void testRatioSaveReopenAndSwapPersistOnlyOnDone() throws Exception {
        openRatio(); TouchRatioPickerDialog dialog = (TouchRatioPickerDialog) dialog();
        main(() -> { ((EditText) field(dialog, "width")).setText("9"); ((EditText) field(dialog, "height")).setText("20");
            find(dialog.getWindow().getDecorView(), text(R.string.ui_done)).performClick(); }); settle();
        assertEquals("9:20", config.touchAspectRatio); assertEquals("9:20", stored(config.id).touchAspectRatio);
        openRatio(); TouchRatioPickerDialog reopened = (TouchRatioPickerDialog) dialog();
        assertEquals("20", ((EditText) field(reopened, "height")).getText().toString());
        main(() -> find(reopened.getWindow().getDecorView(), text(R.string.touch_ratio_swap)).performClick());
        assertEquals("20", ((EditText) field(reopened, "width")).getText().toString());
        main(() -> find(reopened.getWindow().getDecorView(), text(R.string.ui_done)).performClick()); settle();
        assertEquals("20:9", stored(config.id).touchAspectRatio);
    }

    public void testCustomInputsAndFooterStayReachableWithKeyboard() throws Exception {
        openRatio(); TouchRatioPickerDialog dialog = (TouchRatioPickerDialog) dialog();
        EditText height = (EditText) field(dialog, "height");
        main(() -> {
            ((android.widget.ScrollView) dialog.body.getParent()).fullScroll(View.FOCUS_DOWN);
            height.requestFocus();
            ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(height, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }); settle(); Thread.sleep(350); screenshot("ratio-custom-input");
        main(() -> {
            ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(height.getWindowToken(), 0);
            dialog.dismiss();
        });
    }

    public void testExportCopiesCodeAndImportPreviewCancelDoesNotWrite() throws Exception {
        main(() -> call("showKeyboardCodeExport", new Class<?>[]{CustomKeyboardConfig.class}, config)); settle();
        KeyboardCodeDialog export = (KeyboardCodeDialog) dialog(); screenshot("code-export");
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final android.content.ClipData[] previous = {null}; final String[] copied = {null};
        main(() -> { previous[0] = clipboard.getPrimaryClip();
            find(export.getWindow().getDecorView(), text(R.string.config_code_copy)).performClick();
            copied[0] = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            if (previous[0] != null) clipboard.setPrimaryClip(previous[0]);
            else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip();
            export.dismiss(); }); settle();
        assertEquals(config.name, KeyboardConfigCode.decode(copied[0]).config.name);
        String before = snapshot(); openImport(); KeyboardCodeDialog imported = (KeyboardCodeDialog) dialog();
        main(() -> { ((EditText) field(imported, "input")).setText(copied[0]); ((TextView) field(imported, "proceed")).performClick(); }); settle();
        assertNotNull(find(imported.getWindow().getDecorView(), text(R.string.config_code_confirm)));
        assertEquals(before, snapshot()); screenshot("code-import-preview");
        main(() -> find(imported.getWindow().getDecorView(), text(R.string.ui_cancel)).performClick()); settle();
        assertEquals(before, snapshot());
    }

    public void testConfirmedImportAddsANewKeyboardAndChangingCodeInvalidatesPreview() throws Exception {
        String code = KeyboardConfigCode.encode(config); int before = store.loadKeyboardConfigs().size();
        openImport(); KeyboardCodeDialog imported = (KeyboardCodeDialog) dialog();
        main(() -> { ((EditText) field(imported, "input")).setText(code); ((TextView) field(imported, "proceed")).performClick(); }); settle();
        main(() -> ((EditText) field(imported, "input")).setText("broken"));
        assertNull(field(imported, "decoded")); assertEquals(text(R.string.config_code_preview), ((TextView) field(imported, "proceed")).getText().toString());
        main(() -> ((TextView) field(imported, "proceed")).performClick());
        assertEquals(before, store.loadKeyboardConfigs().size()); assertEquals(View.VISIBLE, ((TextView) field(imported, "error")).getVisibility());
        main(() -> { ((EditText) field(imported, "input")).setText(code); ((TextView) field(imported, "proceed")).performClick(); }); settle();
        main(() -> ((TextView) field(imported, "proceed")).performClick()); settle();
        assertEquals(before + 1, store.loadKeyboardConfigs().size());
        CustomKeyboardConfig added = store.loadKeyboardConfigs().get(before);
        assertFalse(config.id.equals(added.id)); assertEquals(config.name, added.name); assertEquals(config.touchAspectRatio, added.touchAspectRatio);
        assertEquals(config.id, stored(config.id).id); screenshot("imported-keyboard");
    }

    public void testSelectedRatioMapsOutsideEntryAndLastPixelToRemoteEdges() throws Exception {
        List<byte[]> reports = new ArrayList<>();
        main(() -> {
            TouchPassthroughView view = new TouchPassthroughView(activity, 0xff604985, false, VIBRATION_LIGHT);
            view.layout(0, 0, 1080, 2400); view.setAspectRatio("4:3"); view.setSafeAreaEnabled(true);
            view.setListener(report -> { if (report != null) reports.add(report.clone()); return true; });
            TouchArea area = (TouchArea) field(view, "area"); assertEquals(1080, area.width); assertEquals(810, area.height);
            long time = SystemClock.uptimeMillis();
            touch(view, time, time, MotionEvent.ACTION_DOWN, 540, area.top - 30); assertTrue(reports.isEmpty());
            touch(view, time, time + 20, MotionEvent.ACTION_MOVE, 540, area.top + 30);
            assertEquals(0, coordinate(reports.get(0), 4));
            touch(view, time, time + 40, MotionEvent.ACTION_MOVE, area.left + area.width - 1, area.top + area.height - 1);
            byte[] end = reports.get(reports.size() - 1); assertEquals(TouchState.MAX_COORDINATE, coordinate(end, 2));
            assertEquals(TouchState.MAX_COORDINATE, coordinate(end, 4));
            touch(view, time, time + 60, MotionEvent.ACTION_UP, 1079, area.top + area.height - 1);
            assertEquals(0, TouchState.mask(reports.get(reports.size() - 1)));
        });
    }

    private void openRatio() throws Exception {
        main(() -> call("showTouchRatioPicker", new Class<?>[]{CustomKeyboardConfig.class, boolean.class}, config, false)); settle();
    }
    private void openImport() throws Exception {
        main(() -> { call("showSettingsPage", new Class<?>[]{});
            find(activity.getWindow().getDecorView(), text(R.string.config_code_import_short)).performClick(); }); settle();
    }
    private void touch(View view, long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0); view.onTouchEvent(event); event.recycle();
    }
    private int coordinate(byte[] report, int index) { return (report[index] & 255) | ((report[index + 1] & 255) << 8); }
    private CustomKeyboardConfig stored(String id) {
        for (CustomKeyboardConfig config : store.loadKeyboardConfigs()) if (id.equals(config.id)) return config;
        throw new AssertionError("Missing keyboard");
    }
    private String snapshot() { return preferences.getString("custom_keyboards_json", ""); }
    private KeyboardSettingsDialog dialog() { return (KeyboardSettingsDialog) field(activity, "keyboardOptionsDialog"); }
    private String text(int id) { return activity.getString(id); }
    private TextView find(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            TextView result = find(((ViewGroup) root).getChildAt(i), text); if (result != null) return result;
        }
        return null;
    }
    private Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void setField(Object owner, String name, Object value) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void call(String name, Class<?>[] types, Object... values) {
        try { Method method = activity.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(activity, values); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void main(Runnable action) { getInstrumentation().runOnMainSync(action); }
    private void settle() throws Exception { getInstrumentation().waitForIdleSync(); Thread.sleep(150); }
    private void screenshot(String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        File directory = new File(activity.getFilesDir(), "keyboard-options-qa"); directory.mkdirs();
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
        if (dialog() != null) {
            TextView footer = find(dialog().getWindow().getDecorView(), text(sourceFooter()));
            if (footer != null) main(() -> {
                Rect visible = new Rect(); assertTrue("Footer has no visible bounds", footer.getGlobalVisibleRect(visible));
                assertTrue("Footer clipped: " + visible + ", height=" + footer.getHeight(), visible.height() >= footer.getHeight() - 2);
            });
        }
    }
    private int sourceFooter() { return dialog() instanceof TouchRatioPickerDialog ? R.string.ui_done
            : field(dialog(), "source") == null ? R.string.ui_cancel : R.string.config_code_close; }
}
