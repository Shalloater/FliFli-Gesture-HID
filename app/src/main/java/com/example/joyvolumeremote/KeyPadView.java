package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;
import static com.example.joyvolumeremote.GestureRules.*;

    final class KeyPadView extends TouchBackgroundView {
        private static final float SWIPE_THRESHOLD_DP = 56f;
        private static final float MULTI_TAP_DISTANCE_DP = 64f;
        private static final float TAP_SLOP_DP = 28f;
        private static final float CIRCLE_MIN_DIAMETER_DP = 54f;
        private static final float CIRCLE_MIN_ROTATION_RAD = 4.7f;

        private final Handler tapHandler = new Handler(Looper.getMainLooper());
        private final ArrayList<GestureBinding> gestureBindings = new ArrayList<>();
        private final ArrayList<Float> pathXs = new ArrayList<>();
        private final ArrayList<Float> pathYs = new ArrayList<>();
        private final TwoFingerExitGesture exitGesture;
        private final GestureStroke stroke = new GestureStroke();
        private boolean exitConsumed;
        private boolean tracking;
        private long strokeDownTime, pendingTapUpTime;
        private TriggerListener listener;
        private Runnable disconnectListener;
        private float downX;
        private float downY;
        private float lastX;
        private float lastY;
        private float pendingTapX;
        private float pendingTapY;
        private Runnable pendingTap;
        private int pendingTapFingers = 0;
        private int pendingTapCount = 0;
        private boolean vibrationEnabled = true;
        private int vibrationLevel = VIBRATION_MEDIUM;

        public KeyPadView(Context context) {
            super(context);
            exitGesture = new TwoFingerExitGesture(getResources().getDisplayMetrics().density);
            setFocusable(true);
            setClickable(true);
            setBackgroundColor(Color.BLACK);
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
            cancelPendingTap(); tracking = false; stroke.clear(); exitGesture.reset();
            gestureBindings.clear();
            if (bindings == null) return;
            for (GestureBinding binding : bindings) {
                gestureBindings.add(binding.copy());
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) exitConsumed = false;
            TwoFingerExitGesture.Result exit = exitGesture.onTouchEvent(event);
            if (exit == TwoFingerExitGesture.Result.EXIT) {
                exitConsumed = true;
                tracking = false; stroke.clear();
                cancelPendingTap();
                vibrate(HapticFeedbackConstants.LONG_PRESS);
                if (disconnectListener != null) disconnectListener.run();
            }
            if (exitConsumed) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (pendingTap != null) {
                        if (event.getEventTime() - pendingTapUpTime <= ViewConfiguration.getDoubleTapTimeout()) {
                            tapHandler.removeCallbacks(pendingTap);
                            tapHandler.postDelayed(pendingTap, ViewConfiguration.getLongPressTimeout());
                        } else flushPendingTap();
                    }
                    beginGesture(event);
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (!tracking) return true;
                    updateStroke(event);
                    int pressed = event.getActionIndex();
                    stroke.press(event.getPointerId(pressed), event.getX(pressed), event.getY(pressed));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (tracking) updateStroke(event);
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (!tracking) return true;
                    updateStroke(event);
                    stroke.lift(event.getPointerId(event.getActionIndex()));
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!tracking) return true;
                    updateStroke(event);
                    stroke.lift(event.getPointerId(event.getActionIndex()));
                    tracking = false;
                    handleCompletedGesture(event.getEventTime());
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    tracking = false; stroke.clear(); exitGesture.reset();
                    cancelPendingTap();
                    return true;
            }
            return true;
        }

        private void beginGesture(MotionEvent event) {
            tracking = true; strokeDownTime = event.getEventTime();
            stroke.clear(); stroke.press(event.getPointerId(0), event.getX(), event.getY());
            downX = event.getX(); downY = event.getY();
            lastX = downX;
            lastY = downY;
            pathXs.clear();
            pathYs.clear();
            appendPathPoint(downX, downY);
        }

        private void updateStroke(MotionEvent event) {
            for (int sample = 0; sample <= event.getHistorySize(); sample++) {
                boolean current = sample == event.getHistorySize();
                for (int i = 0; i < event.getPointerCount(); i++) stroke.move(event.getPointerId(i),
                        current ? event.getX(i) : event.getHistoricalX(i, sample),
                        current ? event.getY(i) : event.getHistoricalY(i, sample));
                downX = stroke.startX(); downY = stroke.startY();
                lastX = stroke.x(); lastY = stroke.y();
                if (stroke.fingers() == 1) appendPathPoint(lastX, lastY);
            }
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

        private void handleCompletedGesture(long upTime) {
            if (!stroke.consistent()) { flushPendingTap(); return; }
            float dx = lastX - downX;
            float dy = lastY - downY;
            String circleMotion = detectCircleMotion();
            if (circleMotion != null) {
                flushPendingTap();
                sendMotion(1, circleMotion);
                return;
            }

            if (isSwipe(dx, dy)) {
                flushPendingTap();
                sendMotion(stroke.fingers(), resolveSwipeMotion(dx, dy));
                return;
            }

            float tapSlop = TAP_SLOP_DP * getResources().getDisplayMetrics().density;
            if (stroke.maxMovement() <= tapSlop && upTime - strokeDownTime <= ViewConfiguration.getLongPressTimeout())
                handleTap(stroke.fingers(), stroke.startX(), stroke.startY(), upTime);
            else flushPendingTap();
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

        private void handleTap(int fingers, float x, float y, long upTime) {
            float maxDistance = MULTI_TAP_DISTANCE_DP * getResources().getDisplayMetrics().density;
            boolean continuesPending = pendingTap != null
                    && pendingTapFingers == fingers
                    && strokeDownTime - pendingTapUpTime <= ViewConfiguration.getDoubleTapTimeout()
                    && distance(x, y, pendingTapX, pendingTapY) <= maxDistance
                    && hasLaterTap(fingers, pendingTapCount, pendingTapX, pendingTapY, x, y);

            if (!continuesPending) {
                flushPendingTap();
                pendingTapFingers = fingers;
                pendingTapCount = 1;
            } else {
                tapHandler.removeCallbacks(pendingTap);
                pendingTapCount++;
            }

            if (!continuesPending) { pendingTapX = x; pendingTapY = y; }
            pendingTapUpTime = upTime;
            if (pendingTapCount >= 3 || !hasLaterTap(fingers, pendingTapCount, pendingTapX, pendingTapY, x, y)) {
                int tapCount = pendingTapCount;
                float tapX = pendingTapX, tapY = pendingTapY;
                cancelPendingTap();
                emitTap(fingers, tapCount, tapX, tapY);
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

        private boolean hasLaterTap(int fingers, int count, float firstX, float firstY, float x, float y) {
            if (getWidth() <= 0 || getHeight() <= 0) return false;
            for (GestureBinding binding : gestureBindings)
                if (GESTURE_KIND_TAP.equals(binding.kind) && binding.fingers == fingers && binding.tapCount > count
                        && tapAreaContains(binding.area, firstX / getWidth(), firstY / getHeight())
                        && tapAreaContains(binding.area, x / getWidth(), y / getHeight())) return true;
            return false;
        }

        private void flushPendingTap() {
            if (pendingTap == null) return;
            int fingers = pendingTapFingers, count = pendingTapCount;
            float x = pendingTapX, y = pendingTapY;
            cancelPendingTap();
            emitTap(fingers, count, x, y);
        }

        private void emitTap(int fingers, int tapCount, float x, float y) {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            float xRatio = x / getWidth();
            float yRatio = y / getHeight();
            for (GestureBinding binding : gestureBindings) {
                if (!GESTURE_KIND_TAP.equals(binding.kind)) continue;
                if (binding.fingers != fingers || binding.tapCount != tapCount) continue;
                if (tapAreaContains(binding.area, xRatio, yRatio)) {
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

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private String detectCircleMotion() {
            if (stroke.fingers() != 1 || pathXs.size() < 8) return null;
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
        public void onWindowFocusChanged(boolean focused) {
            if (!focused) { tracking = false; stroke.clear(); cancelPendingTap(); exitGesture.reset(); }
            super.onWindowFocusChanged(focused);
        }

        @Override
        protected void onDetachedFromWindow() {
            tracking = false; stroke.clear();
            cancelPendingTap();
            exitGesture.reset();
            super.onDetachedFromWindow();
        }

interface TriggerListener {
    void onTriggered(String triggerId);
}
}
