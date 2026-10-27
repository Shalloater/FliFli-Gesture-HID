package com.example.joyvolumeremote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class KeyboardEditorScrollTest extends InstrumentationTestCase {
    public void testVibrationTogglesAndStrengthPreserveScrollAndPersist() throws Exception {
        Activity activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        SharedPreferences preferences = activity.getSharedPreferences("editor_scroll_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        KeyboardConfigStore store = new KeyboardConfigStore(preferences);
        store.ensureKeyboardConfigsInitialized();
        CustomKeyboardConfig draft = store.loadKeyboardConfigs().get(0).copy();
        Field storeField = MainActivity.class.getDeclaredField("keyboardStore"); storeField.setAccessible(true);
        Field scrollField = MainActivity.class.getDeclaredField("keyboardEditorScroll"); scrollField.setAccessible(true);
        Method show = MainActivity.class.getDeclaredMethod("showKeyboardEditor", CustomKeyboardConfig.class, boolean.class); show.setAccessible(true);
        Object original = storeField.get(activity);
        try {
            getInstrumentation().runOnMainSync(() -> {
                try { storeField.set(activity, store); show.invoke(activity, draft, false); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            settle();
            ScrollView first = (ScrollView) scrollField.get(activity);
            getInstrumentation().runOnMainSync(() -> {
                View title = findText(first, activity.getString(R.string.ui_vibration));
                assertNotNull(title);
                int[] controlLocation = new int[2], scrollLocation = new int[2];
                title.getLocationOnScreen(controlLocation); first.getLocationOnScreen(scrollLocation);
                first.scrollTo(0, controlLocation[1] - scrollLocation[1] - 120);
            });
            settle();
            assertTrue(first.getScrollY() > 100);
            for (int resource : new int[]{R.string.ui_vibration, R.string.ui_vibration, R.string.ui_vibration_strength,
                    R.string.ui_vibration_strength, R.string.ui_vibration_strength}) {
                ScrollView before = (ScrollView) scrollField.get(activity);
                int previousY = before.getScrollY();
                getInstrumentation().runOnMainSync(() -> {
                    View row = findText(before, activity.getString(resource));
                    while (row != null && !row.hasOnClickListeners()) row = row.getParent() instanceof View ? (View) row.getParent() : null;
                    assertNotNull(row); assertTrue(row.performClick());
                });
                settle();
                ScrollView after = (ScrollView) scrollField.get(activity);
                assertNotSame(before, after);
                assertEquals("Editor must not jump when changing vibration", previousY, after.getScrollY());
            }
            CustomKeyboardConfig saved = store.loadKeyboardConfigs().get(0);
            assertEquals(draft.vibrationEnabled, saved.vibrationEnabled);
            assertEquals(draft.vibrationLevel, saved.vibrationLevel);
            CustomKeyboardConfig different = store.createNewKeyboardDraft(KeyboardDefinitions.KEYBOARD_TYPE_TOUCH_PASSTHROUGH);
            getInstrumentation().runOnMainSync(() -> {
                try { show.invoke(activity, different, true); } catch (Exception e) { throw new AssertionError(e); }
            });
            settle();
            assertEquals("Opening a different keyboard starts at the top", 0, ((ScrollView) scrollField.get(activity)).getScrollY());
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            settle();
            Field editing = MainActivity.class.getDeclaredField("editingKeyboard"); editing.setAccessible(true);
            assertFalse("System Back remains available outside touch passthrough", editing.getBoolean(activity));
        } finally {
            getInstrumentation().runOnMainSync(() -> {
                try { storeField.set(activity, original); } catch (Exception e) { throw new AssertionError(e); }
                activity.finish();
            });
            preferences.edit().clear().commit();
        }
    }

    private void settle() throws Exception { getInstrumentation().waitForIdleSync(); Thread.sleep(80); getInstrumentation().waitForIdleSync(); }
    private View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = findText(((ViewGroup) view).getChildAt(i), text);
            if (result != null) return result;
        }
        return null;
    }
}
