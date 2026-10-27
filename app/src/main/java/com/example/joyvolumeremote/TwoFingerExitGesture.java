package com.example.joyvolumeremote;

import android.view.MotionEvent;

/** Recognizes a deliberate inward pinch while tracking the same two pointer IDs. */
final class TwoFingerExitGesture {
    enum Result { PASS, BEGIN, BLOCK, EXIT, END }
    private final int[] ids = new int[2];
    private final float minimumReduction;
    private boolean tracking, armed;
    private float initialSpan;

    TwoFingerExitGesture(float density) { minimumReduction = 48f * density; }

    Result onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) { reset(); return Result.PASS; }
        if (!tracking && action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2) {
            for (int i = 0; i < 2; i++) ids[i] = event.getPointerId(i);
            tracking = true;
            armed = true;
            initialSpan = span(event);
            return Result.BEGIN;
        }
        if (!tracking) return Result.PASS;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            reset(); return Result.END;
        }
        if (action == MotionEvent.ACTION_POINTER_UP || event.getPointerCount() != 2) armed = false;
        if (armed && action == MotionEvent.ACTION_MOVE) {
            float current = span(event);
            if (current >= 0 && initialSpan - current >= minimumReduction && current <= initialSpan * .65f) {
                armed = false;
                return Result.EXIT;
            }
        }
        return Result.BLOCK;
    }

    void reset() { tracking = false; armed = false; }

    private float span(MotionEvent event) {
        int first = event.findPointerIndex(ids[0]), second = event.findPointerIndex(ids[1]);
        if (first < 0 || second < 0) return -1;
        return distance(event, first, second);
    }

    private float distance(MotionEvent event, int first, int second) {
        return (float) Math.hypot(event.getX(first) - event.getX(second), event.getY(first) - event.getY(second));
    }
}
