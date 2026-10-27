package com.example.joyvolumeremote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real pointer long-presses, backed by isolated keyboard preferences, with native screenshots. */
public class GestureDemoUiTest extends InstrumentationTestCase {
    private Activity activity;
    private CustomKeyboardConfig draft;
    private SharedPreferences preferences;
    private Object originalStore;

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        preferences = activity.getSharedPreferences("demo_ui_qa", Context.MODE_PRIVATE); preferences.edit().clear().commit();
        KeyboardConfigStore store = new KeyboardConfigStore(preferences); store.ensureKeyboardConfigsInitialized();
        draft = store.loadKeyboardConfigs().get(0);
        originalStore = field(activity, "keyboardStore");
        getInstrumentation().runOnMainSync(() -> {
            try { setField(activity, "keyboardStore", store); editor(); } catch (Exception e) { throw new AssertionError(e); }
        });
        settle();
    }
    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            try { setField(activity, "keyboardStore", originalStore); } catch (Exception e) { throw new AssertionError(e); }
            activity.finish();
        });
        preferences.edit().clear().commit(); super.tearDown();
    }

    public void testLongPressRoutesAndThumbnailsInRealWindows() throws Exception {
        String bindingTitle = title(draft.bindings.get(0));
        TextView binding = find(activity.getWindow().getDecorView(), bindingTitle);
        reveal(binding); screenshot("binding-thumbnails");
        String before = preferences.getString("custom_keyboards_json", "");
        longPress(binding);
        preview(activity, "existing-action-demo", .52f, draft.bindings.get(0).key());
        back(); assertEquals(before, preferences.getString("custom_keyboards_json", ""));

        getInstrumentation().runOnMainSync(() -> { try { call("showGesturePicker", new Class<?>[]{CustomKeyboardConfig.class, boolean.class}, draft, false); } catch (Exception e) { throw new AssertionError(e); } });
        settle(); Dialog choices = (Dialog) field(activity, "choiceDialog");
        String twoDouble = activity.getString(R.string.ui_two_fingers) + activity.getString(R.string.ui_double_tap);
        TextView tap = find(choices.getWindow().getDecorView(), twoDouble);
        reveal(tap); screenshot("action-picker-thumbnails");
        longPress(tap); preview(activity, "two-finger-demo", .52f, "tap:2:2:full"); back(); assertTrue(choices.isShowing());
        clickRow(tap); settle();
        Dialog areas = (Dialog) field(activity, "choiceDialog");
        TextView left = find(areas.getWindow().getDecorView(), activity.getString(R.string.ui_left_half));
        reveal(left); screenshot("area-thumbnails");
        longPress(left); preview(activity, "left-area-demo", .52f, "tap:2:2:left"); back(); assertTrue(areas.isShowing());
        getInstrumentation().runOnMainSync(areas::dismiss);

        getInstrumentation().runOnMainSync(() -> { try { call("showGesturePicker", new Class<?>[]{CustomKeyboardConfig.class, boolean.class}, draft, false); } catch (Exception e) { throw new AssertionError(e); } });
        settle(); choices = (Dialog) field(activity, "choiceDialog");
        click(find(choices.getWindow().getDecorView(), activity.getString(R.string.ui_swipe))); settle();
        String circle = activity.getString(R.string.ui_one_finger) + activity.getString(R.string.ui_clockwise_circle);
        TextView circleRow = find(choices.getWindow().getDecorView(), circle);
        reveal(circleRow); screenshot("swipe-picker-thumbnails");
        longPress(circleRow); preview(activity, "circle-demo", 1.1f, "motion:1:circle_clockwise"); back();
        String configured = activity.getString(R.string.ui_one_finger) + activity.getString(R.string.ui_swipe_up);
        TextView disabled = find(choices.getWindow().getDecorView(), configured);
        assertFalse(disabled.isEnabled());
        longPress(disabled); preview(activity, "configured-action-demo", .9f, "motion:1:swipe_up"); back(); assertTrue(choices.isShowing());
        Dialog closeChoices = choices; getInstrumentation().runOnMainSync(closeChoices::dismiss);

        binding = find(activity.getWindow().getDecorView(), bindingTitle); clickRow(binding); settle();
        Dialog bindingEditor = (Dialog) field(activity, "bindingEditor");
        clickRow(find(bindingEditor.getWindow().getDecorView(), activity.getString(R.string.ui_output))); settle();
        SignalPickerDialog picker = (SignalPickerDialog) field(activity, "signalPicker");
        getInstrumentation().runOnMainSync(() -> { try { ((PickerTabs) field(picker, "categoryTabs")).getChildAt(3).performClick(); } catch (Exception e) { throw new AssertionError(e); } });
        settle();
        TextView signal = (TextView) ((Map<?,?>) field(picker, "keyButtons")).get("touch_top_right_down");
        assertNotNull(signal.getCompoundDrawables()[0]); reveal(signal); screenshot("touch-signal-thumbnails");
        longPress(signal); preview(picker, "signal-demo", .9f, "touch_top_right_down"); back(); assertTrue(picker.isShowing());
        getInstrumentation().runOnMainSync(picker::dismiss);
        assertEquals(before, preferences.getString("custom_keyboards_json", ""));
    }

    public void testResponsiveDemoAndTheme() throws Exception {
        SharedPreferences appearance = activity.getSharedPreferences("appearance", Context.MODE_PRIVATE);
        boolean hadSkin = appearance.contains("skin"); int skin = appearance.getInt("skin", 0);
        try {
            for (int theme : new int[]{2, 3}) {
                appearance.edit().putInt("skin", theme).commit();
                getInstrumentation().runOnMainSync(() -> { try { call("showGesturePreview", new Class<?>[]{String.class}, "tap:3:3:left"); } catch (Exception e) { throw new AssertionError(e); } });
                GesturePreviewDialog dialog = preview(activity, "responsive-" + theme, 1.24f, "tap:3:3:left");
                View play = (View) field(dialog, "play"), close = findDescription(dialog.getWindow().getDecorView(), activity.getString(R.string.demo_close));
                Rect visible = new Rect(); assertTrue(play.getGlobalVisibleRect(visible)); assertEquals(play.getHeight(), visible.height());
                assertNotNull(close); assertTrue(close.getGlobalVisibleRect(visible)); assertEquals(close.getHeight(), visible.height());
                View stage = (View) field(dialog, "stage");
                assertTrue(stage.getGlobalVisibleRect(visible)); assertEquals("Entire animation should be visible", stage.getHeight(), visible.height());
                back();
            }
        } finally {
            if (hadSkin) appearance.edit().putInt("skin", skin).commit(); else appearance.edit().remove("skin").commit();
        }
    }

    private GesturePreviewDialog preview(Object owner, String name, float time, String expectedId) throws Exception {
        GesturePreviewDialog dialog = (GesturePreviewDialog) field(owner, "gesturePreview"); assertNotNull(dialog);
        assertEquals(expectedId, ((GestureDemoCatalog.Demo) field(dialog, "demo")).id);
        for (int i = 0; i < 100 && !(Boolean) field(dialog, "ready"); i++) { Thread.sleep(100); getInstrumentation().waitForIdleSync(); }
        assertTrue("Demo ready", (Boolean) field(dialog, "ready"));
        getInstrumentation().runOnMainSync(() -> { try {
            if ((Boolean) field(dialog, "playing")) ((TextView) field(dialog, "play")).performClick();
            ((SeekBar) field(dialog, "progress")).setProgress(Math.round(time * 1000));
        } catch (Exception e) { throw new AssertionError(e); } });
        CountDownLatch done = new CountDownLatch(1);
        WebView web = (WebView) field(dialog, "webView");
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript("Demo.seek(" + time + ")", value -> done.countDown()));
        assertTrue(done.await(5, TimeUnit.SECONDS)); settle(); screenshot(name); return dialog;
    }
    private void longPress(TextView view) throws Exception {
        reveal(view); Rect bounds = new Rect(); assertTrue(view.getGlobalVisibleRect(bounds));
        int[] location = new int[2]; view.getLocationOnScreen(location);
        float x = location[0] + view.getWidth() / 2f, y = location[1] + view.getHeight() / 2f;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { getInstrumentation().sendPointerSync(down); } finally { down.recycle(); }
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 180);
        MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { getInstrumentation().sendPointerSync(up); } finally { up.recycle(); }
        settle();
    }
    private void reveal(View view) throws Exception {
        assertNotNull(view); getInstrumentation().runOnMainSync(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true)); settle();
    }
    private void clickRow(View view) throws Exception {
        assertNotNull(view); while (!view.hasOnClickListeners() && view.getParent() instanceof View) view = (View) view.getParent();
        assertTrue(view.hasOnClickListeners()); click(view);
    }
    private void click(View view) throws Exception { assertNotNull(view); reveal(view); getInstrumentation().runOnMainSync(view::performClick); settle(); }
    private void back() throws Exception { getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); settle(); }
    private void editor() throws Exception { call("showKeyboardEditor", new Class<?>[]{CustomKeyboardConfig.class, boolean.class}, draft, false); }
    private String title(GestureBinding binding) throws Exception { return (String) call("gestureTitle", new Class<?>[]{GestureBinding.class}, binding); }
    private Object call(String name, Class<?>[] types, Object... args) throws Exception { Method method = MainActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(activity, args); }
    private Object field(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private void setField(Object object, String name, Object value) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    private TextView find(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) { TextView found=find(((ViewGroup)root).getChildAt(i),label); if(found!=null)return found; }
        return null;
    }
    private View findDescription(View root, String label) {
        if (label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=findDescription(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private void screenshot(String name) throws Exception {
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        String size = activity.getResources().getDisplayMetrics().widthPixels < 900 ? "small-" : "";
        try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"demo-"+size+name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        finally{bitmap.recycle();}
    }
    private void settle() throws Exception { Thread.sleep(180); getInstrumentation().waitForIdleSync(); }
}
