package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

/** Forwards one finger after it enters the control area; two-finger pinch exits locally. */
final class TouchPassthroughView extends TouchBackgroundView {
    interface Listener { boolean onTouchState(byte[] report); }
    private final TouchState state = new TouchState();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TwoFingerExitGesture exitGesture;
    private final int accent;
    private final boolean vibration;
    private final int vibrationLevel;
    private Listener listener;
    private Runnable exitListener;
    private boolean accepting, forwarding;
    private int pointerId = -1;
    private float previousX, previousY;
    private boolean safeAreaEnabled;
    private TouchArea area = TouchArea.fit(1, 1, 0, 0, false);
    private final RectF border = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    TouchPassthroughView(Context context) {
        this(context, new AppPreferences(context).primary(), true, KeyboardDefinitions.VIBRATION_MEDIUM);
    }

    TouchPassthroughView(Context context, int accent, boolean vibration, int vibrationLevel) {
        super(context);
        this.accent = accent;
        this.vibration = vibration;
        this.vibrationLevel = vibrationLevel;
        exitGesture = new TwoFingerExitGesture(getResources().getDisplayMetrics().density);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setClickable(true);
        setContentDescription(context.getString(R.string.touch_passthrough_surface));
    }

    void setListener(Listener listener) { this.listener = listener; }
    void setExitListener(Runnable listener) { exitListener = listener; }
    void setSafeAreaEnabled(boolean enabled) {
        safeAreaEnabled = enabled;
        updateArea();
    }

    private void updateArea() {
        updateArea(SystemGestureProtection.reservedEdges(this));
    }

    private void updateArea(android.graphics.Rect reserved) {
        TouchArea next = TouchArea.fit(getWidth(), getHeight(), reserved.top, reserved.bottom, safeAreaEnabled);
        if (area.left != next.left || area.top != next.top || area.width != next.width || area.height != next.height) {
            if (accepting) releaseTouches();
            area = next;
        }
        border.set(area.left, area.top, area.left + area.width - 1f, area.top + area.height - 1f);
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        TwoFingerExitGesture.Result exit = exitGesture.onTouchEvent(event);
        if (exit != TwoFingerExitGesture.Result.PASS) {
            if (exit == TwoFingerExitGesture.Result.BEGIN) {
                cancelForwarding();
            } else if (exit == TwoFingerExitGesture.Result.EXIT) {
                if (vibration) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (exitListener != null) exitListener.run();
            } else if (exit == TwoFingerExitGesture.Result.END && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) { releaseTouches(); return true; }
        if (action == MotionEvent.ACTION_DOWN) {
            state.clear();
            accepting = true;
            forwarding = false;
            pointerId = event.getPointerId(0);
            previousX = event.getX(); previousY = event.getY();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (event.getPointerCount() > 1) {
            // An additional finger cancels this stroke; none of its motion is sent remotely.
            // Keep feeding the exit recognizer until every finger has lifted.
            if (accepting) cancelForwarding();
            return true;
        }
        if (!accepting) {
            if (action == MotionEvent.ACTION_UP && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        int index = event.findPointerIndex(pointerId);
        if (index < 0) { cancelForwarding(); return true; }
        for (int i = 0; i < event.getHistorySize() && accepting; i++)
            forwardPoint(event.getHistoricalX(index, i), event.getHistoricalY(index, i));
        if (accepting) forwardPoint(event.getX(index), event.getY(index));
        if (action == MotionEvent.ACTION_UP) {
            if (forwarding) { state.lift(pointerId); sendState(); performClick(); }
            forwarding = false; accepting = false; pointerId = -1;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        invalidate();
        return true;
    }

    private void forwardPoint(float x, float y) {
        if (!forwarding) {
            float entry = area.entryFraction(previousX, previousY, x, y);
            if (!Float.isNaN(entry)) {
                float startX = previousX + (x - previousX) * entry;
                float startY = previousY + (y - previousY) * entry;
                state.press(pointerId, startX - area.left, startY - area.top, area.width, area.height);
                forwarding = true;
                if (!sendState()) return;
                if (vibration) performHapticFeedback(vibrationLevel == KeyboardDefinitions.VIBRATION_LIGHT
                        ? HapticFeedbackConstants.CLOCK_TICK : vibrationLevel == KeyboardDefinitions.VIBRATION_STRONG
                        ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.VIRTUAL_KEY);
                if (startX == x && startY == y) { previousX = x; previousY = y; return; }
            }
        }
        if (forwarding) {
            state.move(pointerId, x - area.left, y - area.top, area.width, area.height);
            sendState();
        }
        previousX = x; previousY = y;
    }

    private boolean sendState() {
        if (listener != null && !listener.onTouchState(state.report())) {
            state.clear(); accepting = false; forwarding = false; pointerId = -1;
            return false;
        }
        state.frameSent();
        return true;
    }

    private void cancelForwarding() {
        state.clear(); accepting = false; forwarding = false; pointerId = -1;
        if (listener != null) listener.onTouchState(null);
        invalidate();
    }

    void releaseTouches() {
        exitGesture.reset();
        cancelForwarding();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) releaseTouches();
        else SystemGestureProtection.excludeBackGestures(this, getWidth(), getHeight());
        super.onWindowFocusChanged(focused);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (oldWidth != 0 && oldHeight != 0) releaseTouches();
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateArea();
        SystemGestureProtection.excludeBackGestures(this, width, height);
    }

    @Override public android.view.WindowInsets onApplyWindowInsets(android.view.WindowInsets insets) {
        android.view.WindowInsets result = super.onApplyWindowInsets(insets);
        updateArea(SystemGestureProtection.reservedEdges(insets));
        return result;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (safeAreaEnabled) {
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(Color.argb(100, 0, 0, 0));
            borderPaint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
            canvas.drawRect(border, borderPaint);
            borderPaint.setColor(accent); borderPaint.setAlpha(190);
            borderPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
            canvas.drawRect(border, borderPaint);
        }
        byte[] report = state.report();
        if (TouchState.mask(report) == 0) return;
        int x = (report[2] & 255) | ((report[3] & 255) << 8);
        int y = (report[4] & 255) | ((report[5] & 255) << 8);
        float px = area.left + (float) x / TouchState.MAX_COORDINATE * (area.width - 1);
        float py = area.top + (float) y / TouchState.MAX_COORDINATE * (area.height - 1);
        paint.setColor(accent); paint.setAlpha(85);
        canvas.drawCircle(px, py, 23 * getResources().getDisplayMetrics().density, paint);
        paint.setAlpha(255);
        canvas.drawCircle(px, py, 5 * getResources().getDisplayMetrics().density, paint);
    }

    @Override protected void onDetachedFromWindow() {
        releaseTouches();
        SystemGestureProtection.excludeBackGestures(this, 0, 0);
        listener = null;
        exitListener = null;
        super.onDetachedFromWindow();
    }
}
