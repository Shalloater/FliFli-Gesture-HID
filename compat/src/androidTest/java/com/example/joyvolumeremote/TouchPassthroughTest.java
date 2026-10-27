package com.example.joyvolumeremote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

/** Uses Android MotionEvents and persisted configuration; no Bluetooth connection is fabricated. */
public class TouchPassthroughTest extends InstrumentationTestCase {
    private Activity activity;
    private TouchPassthroughView surface;
    private final List<byte[]> reports = new ArrayList<>();

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().runOnMainSync(() -> {
            surface = new TouchPassthroughView(activity, 0xffb9e9da, false, 1);
            surface.setListener(report -> { reports.add(report == null ? null : report.clone()); return true; });
            FrameLayout parent = new FrameLayout(activity);
            parent.addView(surface, new FrameLayout.LayoutParams(-1, -1));
            activity.setContentView(parent);
        });
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(reports::clear);
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(activity::finish);
        super.tearDown();
    }

    public void testAdditionalFingerCancelsInputUntilAllLiftAndNextStrokeWorks() {
        int[] exits = {0};
        getInstrumentation().runOnMainSync(() -> surface.setExitListener(() -> exits[0]++));
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.25f, .5f});
        assertEquals(1, TouchState.mask(last()));
        event(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.25f, .5f, .75f, .5f});
        assertNull("An additional finger releases the single remote touch", last());
        int releasedAt = reports.size();
        event(MotionEvent.ACTION_MOVE, new int[]{4, 9}, new float[]{.1f, .5f, .9f, .5f});
        event(MotionEvent.ACTION_MOVE, new int[]{9, 4}, new float[]{.5f, .9f, .5f, .1f});
        event(MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{9, 4}, new float[]{.5f, .9f, .5f, .1f});
        event(MotionEvent.ACTION_MOVE, new int[]{9}, new float[]{.5f, .6f});
        event(MotionEvent.ACTION_UP, new int[]{9}, new float[]{.5f, .9f});
        assertEquals(releasedAt, reports.size());
        assertEquals(0, exits[0]);
        event(MotionEvent.ACTION_DOWN, new int[]{12}, new float[]{.5f, .5f});
        assertEquals(1, TouchState.mask(last()));
        event(MotionEvent.ACTION_UP, new int[]{12}, new float[]{.5f, .5f});
        assertEquals(0, TouchState.mask(last()));
        assertEquals(1, last()[12]);
        for (byte[] report : reports) if (report != null) {
            assertTrue(report[12] <= 1);
            for (int i = 6; i < 12; i++) assertEquals(0, report[i]);
        }
    }

    public void testCancelFocusLossAndDetachAbortHeldTouches() {
        event(MotionEvent.ACTION_DOWN, new int[]{1}, new float[]{.4f, .6f});
        event(MotionEvent.ACTION_CANCEL, new int[]{1}, new float[]{.4f, .6f});
        assertNull(last());
        event(MotionEvent.ACTION_DOWN, new int[]{1}, new float[]{.4f, .6f});
        getInstrumentation().runOnMainSync(() -> surface.onWindowFocusChanged(false));
        assertNull(last());
        event(MotionEvent.ACTION_DOWN, new int[]{1}, new float[]{.4f, .6f});
        getInstrumentation().runOnMainSync(() -> activity.setContentView(new FrameLayout(activity)));
        assertNull(last());
    }

    public void testSafeAreaMapsItsInnerBoundaryAndIgnoresThePhysicalTopEdge() throws Exception {
        getInstrumentation().runOnMainSync(() -> surface.setSafeAreaEnabled(true));
        getInstrumentation().waitForIdleSync();
        java.lang.reflect.Field field = TouchPassthroughView.class.getDeclaredField("area"); field.setAccessible(true);
        TouchArea area = (TouchArea) field.get(surface);
        assertEquals(surface.getWidth(), area.width);
        assertTrue(area.top > 0);
        reports.clear();
        event(MotionEvent.ACTION_DOWN, new int[]{1}, new float[]{.5f, 0f});
        event(MotionEvent.ACTION_UP, new int[]{1}, new float[]{.5f, .1f});
        assertTrue("Physical top edge must not create a remote contact", reports.isEmpty());
        event(MotionEvent.ACTION_DOWN, new int[]{1}, new float[]{(float) area.left / (surface.getWidth() - 1),
                (float) area.top / (surface.getHeight() - 1)});
        assertEquals(1, TouchState.mask(last()));
        assertEquals(0, coordinate(last(), 0, false)); assertEquals(0, coordinate(last(), 0, true));
        event(MotionEvent.ACTION_MOVE, new int[]{1}, new float[]{(float) (area.left + area.width - 1) / (surface.getWidth() - 1),
                (float) (area.top + area.height - 1) / (surface.getHeight() - 1)});
        assertEquals(32767, coordinate(last(), 0, false)); assertEquals(32767, coordinate(last(), 0, true));
        event(MotionEvent.ACTION_UP, new int[]{1}, new float[]{.5f, .5f});
        assertEquals(0, TouchState.mask(last()));
    }

    public void testTwoFingerPinchReleasesRemoteContactBeforeExitingOnce() {
        int[] exits = {0};
        getInstrumentation().runOnMainSync(() -> surface.setExitListener(() -> exits[0]++));
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.15f, .5f});
        event(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.15f, .5f, .85f, .35f});
        assertNull("Remote touch must be released before the exit pinch", last());
        int releasedAt = reports.size();
        event(MotionEvent.ACTION_MOVE, new int[]{9, 4}, new float[]{.52f, .48f, .48f, .5f});
        assertEquals(1, exits[0]);
        event(MotionEvent.ACTION_MOVE, new int[]{9, 4}, new float[]{.51f, .49f, .49f, .5f});
        assertEquals(1, exits[0]);
        assertEquals("Exit motion is never forwarded", releasedAt, reports.size());
    }

    public void testGestureKeyboardUsesTwoFingerExitOnceWithoutForwardingIt() {
        int[] exits = {0};
        KeyPadView[] gesture = {null};
        getInstrumentation().runOnMainSync(() -> {
            gesture[0] = new KeyPadView(activity);
            gesture[0].setDisconnectListener(() -> exits[0]++);
            activity.setContentView(gesture[0]);
        });
        getInstrumentation().waitForIdleSync();
        eventOn(gesture[0], MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.15f, .5f});
        eventOn(gesture[0], MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.15f, .5f, .85f, .35f});
        eventOn(gesture[0], MotionEvent.ACTION_MOVE, new int[]{4, 9}, new float[]{.48f, .5f, .52f, .48f});
        assertEquals(1, exits[0]);
        eventOn(gesture[0], MotionEvent.ACTION_MOVE, new int[]{4, 9}, new float[]{.49f, .5f, .51f, .49f});
        assertEquals(1, exits[0]);
    }

    public void testComputerKeyboardUsesTwoFingerExitAndReleasesHeldKeys() {
        int[] exits = {0};
        int[][] held = {new int[0]};
        ComputerKeyboardView[] keyboard = {null};
        getInstrumentation().runOnMainSync(() -> {
            keyboard[0] = new ComputerKeyboardView(activity);
            keyboard[0].setVibrationEnabled(false);
            keyboard[0].setStateListener((modifiers, usages) -> held[0] = usages);
            keyboard[0].setDisconnectListener(() -> exits[0]++);
            activity.setContentView(keyboard[0]);
        });
        getInstrumentation().waitForIdleSync();
        eventOn(keyboard[0], MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.15f, .5f});
        eventOn(keyboard[0], MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.15f, .5f, .85f, .35f});
        assertTrue("Ordinary keyboard touches still press keys", held[0].length > 0);
        eventOn(keyboard[0], MotionEvent.ACTION_MOVE, new int[]{4, 9}, new float[]{.48f, .5f, .52f, .48f});
        getInstrumentation().waitForIdleSync();
        assertEquals(1, exits[0]);
        assertEquals(0, held[0].length);
        eventOn(keyboard[0], MotionEvent.ACTION_MOVE, new int[]{4, 9}, new float[]{.49f, .5f, .51f, .49f});
        getInstrumentation().waitForIdleSync();
        assertEquals(1, exits[0]);
    }

    public void testParallelMotionAndSmallPinchDoNotExit() {
        int[] exits = {0};
        getInstrumentation().runOnMainSync(() -> surface.setExitListener(() -> exits[0]++));
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.2f, .5f});
        event(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.2f, .5f, .8f, .5f});
        event(MotionEvent.ACTION_MOVE, new int[]{4, 9}, new float[]{.3f, .5f, .9f, .5f});
        event(MotionEvent.ACTION_MOVE, new int[]{9, 4}, new float[]{.89f, .5f, .31f, .5f});
        assertEquals(0, exits[0]);
    }

    public void testGestureKeyboardTwoFingerTapBindingStillWorks() throws Exception {
        int[] taps = {0}, exits = {0};
        KeyPadView[] gesture = {null};
        getInstrumentation().runOnMainSync(() -> {
            gesture[0] = new KeyPadView(activity);
            GestureBinding tap = new GestureBinding();
            tap.kind = KeyboardDefinitions.GESTURE_KIND_TAP; tap.fingers = 2; tap.tapCount = 1;
            tap.area = KeyboardDefinitions.TAP_AREA_FULL; tap.actionId = "kb_a";
            gesture[0].setGestureBindings(java.util.Collections.singletonList(tap));
            gesture[0].setTriggerListener(key -> taps[0]++);
            gesture[0].setDisconnectListener(() -> exits[0]++);
            activity.setContentView(gesture[0]);
        });
        getInstrumentation().waitForIdleSync();
        eventOn(gesture[0], MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.2f, .5f});
        eventOn(gesture[0], MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.2f, .5f, .8f, .5f});
        eventOn(gesture[0], MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.2f, .5f, .8f, .5f});
        eventOn(gesture[0], MotionEvent.ACTION_UP, new int[]{4}, new float[]{.2f, .5f});
        Thread.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 100);
        getInstrumentation().waitForIdleSync();
        assertEquals(1, taps[0]);
        assertEquals(0, exits[0]);
    }

    public void testThreeFingerTapBindingStillWorksWithoutAnInwardPinch() throws Exception {
        int[] taps = {0};
        KeyPadView[] gesture = {null};
        getInstrumentation().runOnMainSync(() -> {
            gesture[0] = new KeyPadView(activity);
            GestureBinding tap = new GestureBinding();
            tap.kind = KeyboardDefinitions.GESTURE_KIND_TAP; tap.fingers = 3; tap.tapCount = 1;
            tap.area = KeyboardDefinitions.TAP_AREA_FULL; tap.actionId = "kb_a";
            gesture[0].setGestureBindings(java.util.Collections.singletonList(tap));
            gesture[0].setTriggerListener(key -> taps[0]++);
            activity.setContentView(gesture[0]);
        });
        getInstrumentation().waitForIdleSync();
        eventOn(gesture[0], MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.2f, .5f});
        eventOn(gesture[0], MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.2f, .5f, .8f, .4f});
        eventOn(gesture[0], MotionEvent.ACTION_POINTER_DOWN | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9, 12}, new float[]{.2f, .5f, .8f, .4f, .8f, .6f});
        eventOn(gesture[0], MotionEvent.ACTION_POINTER_UP | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9, 12}, new float[]{.2f, .5f, .8f, .4f, .8f, .6f});
        eventOn(gesture[0], MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{4, 9}, new float[]{.2f, .5f, .8f, .4f});
        eventOn(gesture[0], MotionEvent.ACTION_UP, new int[]{4}, new float[]{.2f, .5f});
        Thread.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 100);
        getInstrumentation().waitForIdleSync();
        assertEquals(1, taps[0]);
    }

    public void testCustomImageBackgroundRendersForBothKeyboardTypes() throws Exception {
        java.io.File file = new java.io.File(activity.getCacheDir(), "touch-background-test.png");
        android.graphics.Bitmap pattern = android.graphics.Bitmap.createBitmap(20, 40, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(pattern);
        canvas.drawColor(0xff285c80);
        android.graphics.Paint paint = new android.graphics.Paint(); paint.setColor(0xffb9e9da);
        canvas.drawRect(0, 20, 20, 40, paint);
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            assertTrue(pattern.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream));
        } finally { pattern.recycle(); }
        try {
            getInstrumentation().runOnMainSync(() -> {
                for (TouchBackgroundView view : new TouchBackgroundView[]{new KeyPadView(activity), new TouchPassthroughView(activity)}) {
                    view.setBackgroundImageUri(android.net.Uri.fromFile(file).toString());
                    view.layout(0, 0, 100, 200);
                    android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(100, 200, android.graphics.Bitmap.Config.ARGB_8888);
                    try {
                        view.draw(new android.graphics.Canvas(output));
                        assertEquals(0xff285c80, output.getPixel(50, 20));
                        assertEquals(0xffb9e9da, output.getPixel(50, 180));
                    } finally { output.recycle(); }
                }
            });
        } finally { assertTrue(file.delete()); }
    }

    public void testTouchTypeOrientationAndBackgroundPersistWithoutChangingExistingBindings() {
        Context context = getInstrumentation().getTargetContext();
        android.content.SharedPreferences preferences = context.getSharedPreferences("touch_passthrough_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        try {
            KeyboardConfigStore store = new KeyboardConfigStore(preferences);
            store.ensureKeyboardConfigsInitialized();
            List<CustomKeyboardConfig> before = store.loadKeyboardConfigs();
            CustomKeyboardConfig touch = store.createNewKeyboardDraft(KeyboardDefinitions.KEYBOARD_TYPE_TOUCH_PASSTHROUGH);
            touch.controlMode = KeyboardDefinitions.CONTROL_MODE_LANDSCAPE;
            touch.screenPinning = true;
            touch.backgroundImageUri = "file:///test/background.jpg";
            store.upsertKeyboardConfig(touch);
            List<CustomKeyboardConfig> after = store.loadKeyboardConfigs();
            CustomKeyboardConfig loaded = after.get(after.size() - 1);
            assertEquals(KeyboardDefinitions.KEYBOARD_TYPE_TOUCH_PASSTHROUGH, loaded.type);
            assertEquals(KeyboardDefinitions.CONTROL_MODE_LANDSCAPE, loaded.controlMode);
            assertEquals(touch.backgroundImageUri, loaded.backgroundImageUri);
            assertTrue(loaded.screenPinning);
            assertTrue(loaded.copy().screenPinning);
            assertTrue(loaded.safeTouchArea);
            assertTrue(loaded.copy().safeTouchArea);
            assertFalse(before.get(0).screenPinning);
            assertEquals(before.get(0).bindings.get(0).actionId, after.get(0).bindings.get(0).actionId);
            assertEquals(0, loaded.bindings.size());
        } finally { preferences.edit().clear().commit(); }
    }

    public void testOutsideTopAndBottomPathsStartAtTheirFirstBoundaryIntersection() throws Exception {
        getInstrumentation().runOnMainSync(() -> surface.setSafeAreaEnabled(true));
        java.lang.reflect.Field field = TouchPassthroughView.class.getDeclaredField("area"); field.setAccessible(true);
        TouchArea area = (TouchArea) field.get(surface);
        float x1 = area.left + (area.width - 1) * .25f, x2 = area.left + (area.width - 1) * .75f;
        float bottom = area.top + area.height - 1;
        for (boolean top : new boolean[]{true, false}) {
            float edge = top ? area.top : bottom;
            float outside = edge + (top ? -80 : 80), inside = edge + (top ? 80 : -80);
            getInstrumentation().runOnMainSync(reports::clear);
            pixelEvent(MotionEvent.ACTION_DOWN, x1, outside);
            assertTrue("Outside down must not create a remote touch", reports.isEmpty());
            pixelEvent(MotionEvent.ACTION_MOVE, x2, inside);
            byte[] first = reports.get(0);
            assertEquals(1, TouchState.mask(first));
            assertEquals(16384, coordinate(first, 0, false));
            assertEquals(top ? 0 : 32767, coordinate(first, 0, true));
            assertTrue(top ? coordinate(last(), 0, true) > 0 : coordinate(last(), 0, true) < 32767);
            pixelEvent(MotionEvent.ACTION_UP, x2, inside);
            assertEquals(0, TouchState.mask(last()));
        }
    }

    public void testOutsideTapAndPathsThatNeverEnterAreIgnored() throws Exception {
        getInstrumentation().runOnMainSync(() -> surface.setSafeAreaEnabled(true));
        java.lang.reflect.Field field = TouchPassthroughView.class.getDeclaredField("area"); field.setAccessible(true);
        TouchArea area = (TouchArea) field.get(surface);
        getInstrumentation().runOnMainSync(reports::clear);
        pixelEvent(MotionEvent.ACTION_DOWN, 200, area.top - 80);
        pixelEvent(MotionEvent.ACTION_UP, 200, area.top - 80);
        pixelEvent(MotionEvent.ACTION_DOWN, 100, area.top - 80);
        pixelEvent(MotionEvent.ACTION_MOVE, 800, area.top - 20);
        pixelEvent(MotionEvent.ACTION_UP, 800, area.top - 20);
        assertTrue(reports.isEmpty());
    }

    public void testBatchedOutsidePathUsesTheFirstHistoricalEntryAndReentryDoesNotRestart() throws Exception {
        getInstrumentation().runOnMainSync(() -> surface.setSafeAreaEnabled(true));
        java.lang.reflect.Field field = TouchPassthroughView.class.getDeclaredField("area"); field.setAccessible(true);
        TouchArea area = (TouchArea) field.get(surface);
        float x = area.left + (area.width - 1) * .5f;
        getInstrumentation().runOnMainSync(reports::clear);
        pixelEvent(MotionEvent.ACTION_DOWN, x, area.top - 100);
        getInstrumentation().runOnMainSync(() -> {
            MotionEvent move = MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, x, area.top + 40, 0);
            move.addBatch(20, x, area.top - 40, 1, 1, 0);
            try { surface.onTouchEvent(move); } finally { move.recycle(); }
        });
        assertEquals(0, coordinate(reports.get(0), 0, true));
        assertTrue("Historical inside sample must be forwarded", reports.stream().anyMatch(r -> coordinate(r, 0, true) > 0));
        int before = reports.size();
        pixelEvent(MotionEvent.ACTION_MOVE, x, area.top + 50);
        assertEquals("An active stroke resumes without another down", before + 1, reports.size());
        pixelEvent(MotionEvent.ACTION_UP, x, area.top + 50);
    }

    private void pixelEvent(int action, float x, float y) {
        getInstrumentation().runOnMainSync(() -> {
            MotionEvent event = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), action, x, y, 0);
            try { surface.onTouchEvent(event); } finally { event.recycle(); }
        });
    }

    private byte[] last() { return reports.get(reports.size() - 1); }

    private void event(int action, int[] ids, float[] coordinates) {
        eventOn(surface, action, ids, coordinates);
    }

    private void eventOn(android.view.View target, int action, int[] ids, float[] coordinates) {
        getInstrumentation().runOnMainSync(() -> {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
            MotionEvent.PointerCoords[] points = new MotionEvent.PointerCoords[ids.length];
            for (int i = 0; i < ids.length; i++) {
                properties[i] = new MotionEvent.PointerProperties();
                properties[i].id = ids[i]; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                points[i] = new MotionEvent.PointerCoords();
                points[i].x = coordinates[i * 2] * (target.getWidth() - 1);
                points[i].y = coordinates[i * 2 + 1] * (target.getHeight() - 1);
                points[i].pressure = 1; points[i].size = 1;
            }
            MotionEvent event = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), action, ids.length,
                    properties, points, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            try { target.onTouchEvent(event); } finally { event.recycle(); }
        });
    }

    private int coordinate(byte[] report, int slot, boolean y) {
        int offset = slot * 6 + (y ? 4 : 2);
        return (report[offset] & 255) | ((report[offset + 1] & 255) << 8);
    }
}
