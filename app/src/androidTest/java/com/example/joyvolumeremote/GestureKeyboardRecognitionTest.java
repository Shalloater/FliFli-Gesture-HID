package com.example.joyvolumeremote;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.example.joyvolumeremote.KeyboardDefinitions.*;

/** Replays staggered fingers and real inter-tap delays through the production View. */
public class GestureKeyboardRecognitionTest extends InstrumentationTestCase {
    private Activity activity;
    private KeyPadView view;
    private final List<String> triggered = new ArrayList<>();
    private long downTime;

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().runOnMainSync(() -> {
            view = new KeyPadView(activity); view.setVibrationEnabled(false);
            view.setTriggerListener(triggered::add);
            activity.setContentView(view);
        });
        getInstrumentation().waitForIdleSync();
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(activity::finish);
        super.tearDown();
    }

    public void testTwoFingerSingleTapAcceptsEitherLiftOrder() throws Exception {
        GestureBinding tap = tap(2, 1, TAP_AREA_FULL); bind(tap);
        chord(false); chord(true); settle();
        assertEquals(Arrays.asList(tap.key(), tap.key()), triggered);
    }

    public void testDoubleTapCanSwapTheLastFingerAndOverlapTheOldTimeout() throws Exception {
        GestureBinding tap = tap(2, 2, TAP_AREA_FULL); bind(tap);
        chord(false);
        Thread.sleep(220);
        event(MotionEvent.ACTION_DOWN, new int[]{19}, new float[]{.2f, .5f});
        event(pointer(MotionEvent.ACTION_POINTER_DOWN, 1), new int[]{19, 3}, new float[]{.2f, .5f, .8f, .5f});
        Thread.sleep(160);
        event(pointer(MotionEvent.ACTION_POINTER_UP, 1), new int[]{19, 3}, new float[]{.2f, .5f, .8f, .5f});
        event(MotionEvent.ACTION_UP, new int[]{19}, new float[]{.2f, .5f});
        settle(); assertEquals(Arrays.asList(tap.key()), triggered);
    }

    public void testTapRegionUsesTheWholeChordRatherThanTheLastFinger() throws Exception {
        GestureBinding tap = tap(2, 1, TAP_AREA_LEFT); bind(tap);
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.15f, .5f});
        event(pointer(MotionEvent.ACTION_POINTER_DOWN, 1), new int[]{4, 9}, new float[]{.15f, .5f, .6f, .5f});
        event(pointer(MotionEvent.ACTION_POINTER_UP, 0), new int[]{4, 9}, new float[]{.15f, .5f, .6f, .5f});
        event(MotionEvent.ACTION_UP, new int[]{9}, new float[]{.6f, .5f});
        settle(); assertEquals(Arrays.asList(tap.key()), triggered);
    }

    public void testThreeFingerTapIsIndependentOfLiftOrder() throws Exception {
        GestureBinding tap = tap(3, 1, TAP_AREA_FULL); bind(tap);
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.15f, .5f});
        event(pointer(MotionEvent.ACTION_POINTER_DOWN, 1), new int[]{4, 9}, new float[]{.15f, .5f, .75f, .4f});
        event(pointer(MotionEvent.ACTION_POINTER_DOWN, 2), new int[]{4, 9, 2}, new float[]{.15f, .5f, .75f, .4f, .75f, .6f});
        event(pointer(MotionEvent.ACTION_POINTER_UP, 0), new int[]{4, 9, 2}, new float[]{.15f, .5f, .75f, .4f, .75f, .6f});
        event(pointer(MotionEvent.ACTION_POINTER_UP, 0), new int[]{9, 2}, new float[]{.75f, .4f, .75f, .6f});
        event(MotionEvent.ACTION_UP, new int[]{2}, new float[]{.75f, .6f});
        settle(); assertEquals(Arrays.asList(tap.key()), triggered);
    }

    public void testParallelTwoFingerSwipeKeepsItsVerticalDirection() throws Exception {
        GestureBinding swipe = new GestureBinding(); swipe.kind = GESTURE_KIND_MOTION;
        swipe.fingers = 2; swipe.motion = MOTION_SWIPE_UP; swipe.actionId = "kb_a"; bind(swipe);
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.1f, .8f});
        event(pointer(MotionEvent.ACTION_POINTER_DOWN, 1), new int[]{4, 9}, new float[]{.1f, .8f, .9f, .8f});
        event(MotionEvent.ACTION_MOVE, new int[]{9, 4}, new float[]{.9f, .55f, .1f, .55f});
        event(pointer(MotionEvent.ACTION_POINTER_UP, 1), new int[]{9, 4}, new float[]{.9f, .55f, .1f, .55f});
        event(MotionEvent.ACTION_UP, new int[]{9}, new float[]{.9f, .55f});
        settle(); assertEquals(Arrays.asList(swipe.key()), triggered);
    }

    public void testSingleOnlyBindingDoesNotLoseRapidRepeatedTaps() throws Exception {
        GestureBinding tap = tap(1, 1, TAP_AREA_FULL); bind(tap);
        for (int i = 0; i < 2; i++) {
            event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.5f, .5f});
            event(MotionEvent.ACTION_UP, new int[]{4}, new float[]{.5f, .5f});
        }
        settle(); assertEquals(Arrays.asList(tap.key(), tap.key()), triggered);
    }

    public void testDraggingOutAndBackCannotBecomeATap() throws Exception {
        bind(tap(1, 1, TAP_AREA_FULL));
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.5f, .5f});
        event(MotionEvent.ACTION_MOVE, new int[]{4}, new float[]{.8f, .5f});
        event(MotionEvent.ACTION_UP, new int[]{4}, new float[]{.5f, .5f});
        settle(); assertTrue(triggered.isEmpty());
    }

    public void testCancelAndDetachDoNotSendPendingTaps() throws Exception {
        bind(tap(2, 1, TAP_AREA_FULL), tap(2, 2, TAP_AREA_FULL));
        chord(false);
        event(MotionEvent.ACTION_CANCEL, new int[]{9}, new float[]{.8f, .5f});
        settle(); assertTrue(triggered.isEmpty());
        chord(false);
        getInstrumentation().runOnMainSync(() -> activity.setContentView(new FrameLayout(activity)));
        settle(); assertTrue(triggered.isEmpty());
    }

    public void testAllFingerAndTapCountsAcceptStaggeredContacts() throws Exception {
        for (int fingers = 1; fingers <= 3; fingers++) for (int count = 1; count <= 3; count++) {
            GestureBinding tap = tap(fingers, count, TAP_AREA_FULL); bind(tap);
            int before = triggered.size();
            for (int repeat = 0; repeat < count; repeat++) {
                List<Integer> ids = new ArrayList<>();
                List<Float> positions = new ArrayList<>();
                for (int finger = 0; finger < fingers; finger++) {
                    ids.add(new int[]{7, 19, 2}[finger]);
                    positions.add(new float[]{.2f, .8f, .5f}[finger]); positions.add(.5f);
                    listEvent(finger == 0 ? MotionEvent.ACTION_DOWN : pointer(MotionEvent.ACTION_POINTER_DOWN, finger), ids, positions);
                    Thread.sleep(15);
                }
                while (ids.size() > 1) {
                    int index = repeat % 2 == 0 ? 0 : ids.size() - 1;
                    listEvent(pointer(MotionEvent.ACTION_POINTER_UP, index), ids, positions);
                    ids.remove(index); positions.remove(index * 2 + 1); positions.remove(index * 2);
                }
                listEvent(MotionEvent.ACTION_UP, ids, positions);
                Thread.sleep(30);
            }
            settle();
            assertEquals("Finger/tap matrix " + fingers + "/" + count, before + 1, triggered.size());
            assertEquals(tap.key(), triggered.get(before));
        }
    }

    public void testHoldingCannotBeMistakenForATap() throws Exception {
        bind(tap(1, 1, TAP_AREA_FULL));
        event(MotionEvent.ACTION_DOWN, new int[]{7}, new float[]{.5f, .5f});
        Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 50);
        event(MotionEvent.ACTION_UP, new int[]{7}, new float[]{.5f, .5f});
        settle(); assertTrue(triggered.isEmpty());
    }

    private void listEvent(int action, List<Integer> ids, List<Float> coordinates) {
        int[] pointers = new int[ids.size()]; float[] positions = new float[coordinates.size()];
        for (int i = 0; i < ids.size(); i++) pointers[i] = ids.get(i);
        for (int i = 0; i < coordinates.size(); i++) positions[i] = coordinates.get(i);
        event(action, pointers, positions);
    }

    private GestureBinding tap(int fingers, int count, String area) {
        GestureBinding binding = new GestureBinding(); binding.kind = GESTURE_KIND_TAP;
        binding.fingers = fingers; binding.tapCount = count; binding.area = area; binding.actionId = "kb_a";
        return binding;
    }
    private void bind(GestureBinding... bindings) {
        getInstrumentation().runOnMainSync(() -> view.setGestureBindings(Arrays.asList(bindings)));
    }
    private void chord(boolean originalFingerLast) throws Exception {
        event(MotionEvent.ACTION_DOWN, new int[]{4}, new float[]{.2f, .5f});
        Thread.sleep(20);
        event(pointer(MotionEvent.ACTION_POINTER_DOWN, 1), new int[]{4, 9}, new float[]{.2f, .5f, .8f, .5f});
        Thread.sleep(20);
        event(pointer(MotionEvent.ACTION_POINTER_UP, originalFingerLast ? 1 : 0), new int[]{4, 9}, new float[]{.2f, .5f, .8f, .5f});
        event(MotionEvent.ACTION_UP, new int[]{originalFingerLast ? 4 : 9}, new float[]{originalFingerLast ? .2f : .8f, .5f});
    }
    private int pointer(int action, int index) { return action | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT); }
    private void event(int action, int[] ids, float[] coordinates) {
        getInstrumentation().runOnMainSync(() -> {
            long now = SystemClock.uptimeMillis();
            if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) downTime = now;
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
            MotionEvent.PointerCoords[] points = new MotionEvent.PointerCoords[ids.length];
            for (int i = 0; i < ids.length; i++) {
                properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i];
                properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                points[i] = new MotionEvent.PointerCoords(); points[i].x = coordinates[i * 2] * view.getWidth();
                points[i].y = coordinates[i * 2 + 1] * view.getHeight(); points[i].pressure = 1;
            }
            MotionEvent event = MotionEvent.obtain(downTime, now, action, ids.length, properties, points,
                    0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            try { view.onTouchEvent(event); } finally { event.recycle(); }
        });
    }
    private void settle() throws Exception { Thread.sleep(380); getInstrumentation().waitForIdleSync(); }
}
