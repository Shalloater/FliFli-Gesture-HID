package com.example.joyvolumeremote;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Exercises the real activity and surface; the transport is replaced without claiming a radio link. */
public class TouchKeyboardUiTest extends InstrumentationTestCase {
    private static final class RecordingPinning extends ScreenPinningProtection {
        boolean requested;
        RecordingPinning(Activity activity) { super(activity, result -> {}); }
        @Override void request() { requested = true; }
        @Override void stop() { requested = false; }
    }
    private static final class RecordingService extends HidRemoteService {
        final List<byte[]> reports = new ArrayList<>();
        RecordingService(Context context) { attachBaseContext(context); }
        @Override public boolean sendTouchState(byte[] report) {
            reports.add(report == null ? null : report.clone());
            return true;
        }
    }

    public void testPortraitTouchSurfaceSendsMappedContactsAndExits() throws Exception { exercise(false); }
    public void testLandscapeTouchSurfaceSendsMappedContactsAndExits() throws Exception { exercise(true); }
    public void testPortraitSafeAreaUsesFullWidthAndMapsEntireTarget() throws Exception { exercise(false, true); }
    public void testLandscapeSafeAreaIsLargestThreeByFourInsideSystemEdges() throws Exception { exercise(true, true); }

    public void testDoubleBackExitsButAContinuedTouchCancelsConfirmation() throws Exception {
        Activity activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        Field service = MainActivity.class.getDeclaredField("hidService"); service.setAccessible(true);
        Field pinning = MainActivity.class.getDeclaredField("screenPinningProtection"); pinning.setAccessible(true);
        Field surface = MainActivity.class.getDeclaredField("touchSurface"); surface.setAccessible(true);
        Method show = MainActivity.class.getDeclaredMethod("showRemotePage", BluetoothDevice.class, CustomKeyboardConfig.class);
        show.setAccessible(true);
        Object original = service.get(activity);
        Object originalPinning = pinning.get(activity);
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = "back_test"; config.type = KeyboardDefinitions.KEYBOARD_TYPE_TOUCH_PASSTHROUGH;
        config.controlMode = KeyboardDefinitions.CONTROL_MODE_PORTRAIT; config.backgroundImageUri = "";
        try {
            getInstrumentation().runOnMainSync(() -> {
                try {
                    service.set(activity, new RecordingService(activity));
                    pinning.set(activity, new RecordingPinning(activity));
                    show.invoke(activity, null, config);
                }
                catch (Exception e) { throw new AssertionError(e); }
            });
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> {
                try {
                    ((MainActivity) activity).onBackPressed();
                    TouchPassthroughView view = (TouchPassthroughView) surface.get(activity);
                    assertNotNull(view);
                    // An intervening real touch must disarm the first Back.
                    pinchEvent(view, MotionEvent.ACTION_DOWN, new float[]{.5f, .5f});
                    ((MainActivity) activity).onBackPressed();
                    assertSame(view, surface.get(activity));
                    ((MainActivity) activity).onBackPressed();
                    assertNull(surface.get(activity));
                } catch (Exception e) { throw new AssertionError(e); }
            });
        } finally {
            getInstrumentation().runOnMainSync(() -> {
                try { service.set(activity, original); pinning.set(activity, originalPinning); }
                catch (Exception e) { throw new AssertionError(e); }
                activity.finish();
            });
        }
    }

    private void exercise(boolean landscape) throws Exception {
        exercise(landscape, false);
    }

    private void exercise(boolean landscape, boolean safe) throws Exception {
        Activity activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        Field serviceField = MainActivity.class.getDeclaredField("hidService"); serviceField.setAccessible(true);
        Field pinningField = MainActivity.class.getDeclaredField("screenPinningProtection"); pinningField.setAccessible(true);
        Field surfaceField = MainActivity.class.getDeclaredField("touchSurface"); surfaceField.setAccessible(true);
        Method show = MainActivity.class.getDeclaredMethod("showRemotePage", BluetoothDevice.class, CustomKeyboardConfig.class);
        show.setAccessible(true);
        HidRemoteService original = (HidRemoteService) serviceField.get(activity);
        Object originalPinning = pinningField.get(activity);
        RecordingPinning pinning = new RecordingPinning(activity);
        RecordingService recording = new RecordingService(activity);
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = "touch_ui_test"; config.name = "Touch UI test";
        config.type = KeyboardDefinitions.KEYBOARD_TYPE_TOUCH_PASSTHROUGH;
        config.controlMode = landscape ? KeyboardDefinitions.CONTROL_MODE_LANDSCAPE : KeyboardDefinitions.CONTROL_MODE_PORTRAIT;
        config.backgroundImageUri = "";
        config.safeTouchArea = safe;
        try {
            getInstrumentation().runOnMainSync(() -> {
                try { serviceField.set(activity, recording); pinningField.set(activity, pinning); show.invoke(activity, null, config); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            getInstrumentation().waitForIdleSync();
            TouchPassthroughView surface = (TouchPassthroughView) surfaceField.get(activity);
            for (int i = 0; i < 20 && (landscape != (surface.getWidth() > surface.getHeight())); i++) {
                Thread.sleep(100); getInstrumentation().waitForIdleSync();
            }
            assertTrue(surface.isShown());
            assertFalse("Ordinary fullscreen must not request screen pinning by default", pinning.requested);
            assertEquals(landscape, surface.getWidth() > surface.getHeight());
            Field areaField = TouchPassthroughView.class.getDeclaredField("area"); areaField.setAccessible(true);
            TouchArea area = (TouchArea) areaField.get(surface);
            if (safe) {
                assertTrue(Math.abs(area.width * 4 - area.height * 3) <= 3);
                if (!landscape) assertEquals(surface.getWidth(), area.width);
                else {
                    android.graphics.Rect reserved = SystemGestureProtection.reservedEdges(surface);
                    assertTrue(area.height >= surface.getHeight() - reserved.top - reserved.bottom - 2);
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                assertEquals(1, surface.getSystemGestureExclusionRects().size());
                assertEquals(new android.graphics.Rect(0, 0, surface.getWidth(), surface.getHeight()),
                        surface.getSystemGestureExclusionRects().get(0));
            }
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            getInstrumentation().waitForIdleSync();
            assertSame("Back must not exit touch passthrough", surface, surfaceField.get(activity));
            Thread.sleep(3500); // Let the Back hint and orientation settle.
            saveScreenshot(activity, (safe ? "safe-" : "touch-") + (landscape ? "landscape" : "portrait"));
            getInstrumentation().runOnMainSync(() -> {
                try { serviceField.set(activity, recording); } catch (Exception e) { throw new AssertionError(e); }
                MotionEvent down = MotionEvent.obtain(0, 1, MotionEvent.ACTION_DOWN,
                        area.left + (area.width - 1) * .25f, area.top + (area.height - 1) * .75f, 0);
                try { surface.onTouchEvent(down); } finally { down.recycle(); }
            });
            byte[] report = recording.reports.get(recording.reports.size() - 1);
            assertEquals(1, TouchState.mask(report));
            assertEquals(8192, coordinate(report, 2));
            assertEquals(24575, coordinate(report, 4));
            getInstrumentation().waitForIdleSync();
            saveScreenshot(activity, (safe ? "safe-" : "touch-") + (landscape ? "landscape-held" : "portrait-held"));
            if (safe) {
                getInstrumentation().runOnMainSync(() -> pointEvent(surface, MotionEvent.ACTION_UP,
                        area.left + (area.width - 1) * .25f, area.top + (area.height - 1) * .75f));
                for (boolean top : new boolean[]{true, false}) {
                    float edge = top ? area.top : area.top + area.height - 1;
                    float x = area.left + (area.width - 1) * .5f;
                    float outside = edge + (top ? -30 : 30), inside = edge + (top ? 80 : -80);
                    getInstrumentation().runOnMainSync(() -> {
                        recording.reports.clear();
                        pointEvent(surface, MotionEvent.ACTION_DOWN, x, outside);
                        assertTrue("A down outside the area must remain local", recording.reports.isEmpty());
                        pointEvent(surface, MotionEvent.ACTION_MOVE, x, inside);
                        byte[] first = recording.reports.get(0);
                        assertEquals(1, TouchState.mask(first));
                        assertEquals(top ? 0 : 32767, coordinate(first, 4));
                        assertEquals(16384, coordinate(first, 2));
                        pointEvent(surface, MotionEvent.ACTION_UP, x, inside);
                        assertEquals(0, TouchState.mask(recording.reports.get(recording.reports.size() - 1)));
                    });
                }
            }
            getInstrumentation().runOnMainSync(() -> {
                pinchEvent(surface, MotionEvent.ACTION_DOWN, new float[]{.15f, .5f});
                pinchEvent(surface, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        new float[]{.15f, .5f, .85f, .35f});
                assertNull("Second finger releases remote touch", recording.reports.get(recording.reports.size() - 1));
                pinchEvent(surface, MotionEvent.ACTION_MOVE, new float[]{.48f, .5f, .52f, .48f});
            });
            getInstrumentation().waitForIdleSync();
            assertNull(recording.reports.get(recording.reports.size() - 1));
            assertNull(surfaceField.get(activity));
            assertFalse("Exiting releases app-owned screen pinning", pinning.requested);
            if (android.os.Build.VERSION.SDK_INT >= 29) assertTrue(surface.getSystemGestureExclusionRects().isEmpty());
        } finally {
            getInstrumentation().runOnMainSync(() -> {
                try { serviceField.set(activity, original); pinningField.set(activity, originalPinning); }
                catch (Exception e) { throw new AssertionError(e); }
                activity.finish();
            });
        }
    }

    private void saveScreenshot(Activity activity, String name) throws Exception {
        getInstrumentation().getUiAutomation().waitForIdle(200, 3000);
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream stream = new FileOutputStream(new File(activity.getFilesDir(), name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { screenshot.recycle(); }
    }

    private int coordinate(byte[] report, int offset) { return (report[offset] & 255) | ((report[offset + 1] & 255) << 8); }

    private void pointEvent(TouchPassthroughView surface, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), action, x, y, 0);
        try { surface.onTouchEvent(event); } finally { event.recycle(); }
    }

    private void pinchEvent(TouchPassthroughView surface, int action, float[] coords) {
        int count = coords.length / 2;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] points = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            points[i] = new MotionEvent.PointerCoords();
            points[i].x = coords[i * 2] * (surface.getWidth() - 1);
            points[i].y = coords[i * 2 + 1] * (surface.getHeight() - 1);
            points[i].pressure = 1; points[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), action, count,
                properties, points, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { surface.onTouchEvent(event); } finally { event.recycle(); }
    }
}
