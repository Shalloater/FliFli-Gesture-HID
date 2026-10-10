package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;
import static com.example.joyvolumeremote.GestureRules.*;

    final class KeyPadView extends View {
        private static final float SWIPE_THRESHOLD_DP = 56f;
        private static final float PINCH_THRESHOLD_DP = 42f;
        private static final float MULTI_TAP_DISTANCE_DP = 64f;
        private static final float TAP_SLOP_DP = 28f;
        private static final float CIRCLE_MIN_DIAMETER_DP = 54f;
        private static final float CIRCLE_MIN_ROTATION_RAD = 4.7f;

        private final Handler tapHandler = new Handler(Looper.getMainLooper());
        private final ArrayList<GestureBinding> gestureBindings = new ArrayList<>();
        private final ArrayList<Float> pathXs = new ArrayList<>();
        private final ArrayList<Float> pathYs = new ArrayList<>();
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final RectF backgroundDest = new RectF();
        private Bitmap backgroundBitmap;
        private TriggerListener listener;
        private Runnable disconnectListener;
        private float downX;
        private float downY;
        private float lastX;
        private float lastY;
        private float pendingTapX;
        private float pendingTapY;
        private float initialPinchDistance;
        private Runnable pendingTap;
        private int pendingTapFingers = 0;
        private int pendingTapCount = 0;
        private int maxPointerCount = 1;
        private boolean trackingPinch = false;
        private boolean pinchHandled = false;
        private boolean vibrationEnabled = true;
        private int vibrationLevel = VIBRATION_MEDIUM;

        public KeyPadView(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setBackgroundColor(Color.BLACK);
        }

        public void setBackgroundImageUri(String rawUri) {
            clearBackgroundBitmap();
            if (rawUri != null && !rawUri.trim().isEmpty()) {
                backgroundBitmap = loadScaledBackgroundBitmap(rawUri.trim());
            }
            invalidate();
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
            gestureBindings.clear();
            if (bindings == null) return;
            for (GestureBinding binding : bindings) {
                gestureBindings.add(binding.copy());
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (backgroundBitmap == null || backgroundBitmap.isRecycled()) return;
            float viewWidth = getWidth();
            float viewHeight = getHeight();
            float bitmapWidth = backgroundBitmap.getWidth();
            float bitmapHeight = backgroundBitmap.getHeight();
            if (viewWidth <= 0f || viewHeight <= 0f || bitmapWidth <= 0f || bitmapHeight <= 0f) return;

            float scale = Math.max(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
            float drawWidth = bitmapWidth * scale;
            float drawHeight = bitmapHeight * scale;
            float left = (viewWidth - drawWidth) * 0.5f;
            float top = (viewHeight - drawHeight) * 0.5f;
            backgroundDest.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawBitmap(backgroundBitmap, null, backgroundDest, backgroundPaint);
        }

        private Bitmap loadScaledBackgroundBitmap(String rawUri) {
            try {
                Uri uri = Uri.parse(rawUri);
                int targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
                int targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = openBackgroundInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

                int sampleSize = 1;
                while ((bounds.outWidth / sampleSize) > targetWidth * 2
                        || (bounds.outHeight / sampleSize) > targetHeight * 2) {
                    sampleSize *= 2;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try (InputStream input = openBackgroundInputStream(uri)) {
                    return BitmapFactory.decodeStream(input, null, options);
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        private InputStream openBackgroundInputStream(Uri uri) throws Exception {
            if ("file".equals(uri.getScheme())) {
                return new FileInputStream(new File(uri.getPath()));
            }
            return getContext().getContentResolver().openInputStream(uri);
        }

        private void clearBackgroundBitmap() {
            if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
                backgroundBitmap.recycle();
            }
            backgroundBitmap = null;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    beginGesture(event);
                    trackingPinch = false;
                    pinchHandled = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    maxPointerCount = Math.max(maxPointerCount, event.getPointerCount());
                    if (event.getPointerCount() >= 2) {
                        initialPinchDistance = distanceBetweenFirstTwoPointers(event);
                        trackingPinch = true;
                        pinchHandled = false;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    maxPointerCount = Math.max(maxPointerCount, event.getPointerCount());
                    updateLastCentroid(event);
                    if (event.getPointerCount() == 1 && maxPointerCount == 1) {
                        appendPathPoint(lastX, lastY);
                    }
                    if (trackingPinch && event.getPointerCount() >= 2 && !pinchHandled) {
                        float currentDistance = distanceBetweenFirstTwoPointers(event);
                        float threshold = PINCH_THRESHOLD_DP * getResources().getDisplayMetrics().density;
                        if (initialPinchDistance - currentDistance > threshold) {
                            pinchHandled = true;
                            cancelPendingTap();
                            vibrate(HapticFeedbackConstants.LONG_PRESS);
                            if (disconnectListener != null) disconnectListener.run();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    maxPointerCount = Math.max(maxPointerCount, event.getPointerCount());
                    updateLastCentroid(event);
                    return true;
                case MotionEvent.ACTION_UP:
                    updateLastCentroid(event);
                    if (maxPointerCount == 1) appendPathPoint(lastX, lastY);
                    if (!pinchHandled) {
                        handleCompletedGesture();
                    }
                    trackingPinch = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelPendingTap();
                    trackingPinch = false;
                    pinchHandled = false;
                    return true;
            }
            return true;
        }

        private void beginGesture(MotionEvent event) {
            maxPointerCount = event.getPointerCount();
            float[] centroid = centroid(event);
            downX = centroid[0];
            downY = centroid[1];
            lastX = downX;
            lastY = downY;
            pathXs.clear();
            pathYs.clear();
            appendPathPoint(downX, downY);
        }

        private void updateLastCentroid(MotionEvent event) {
            float[] centroid = centroid(event);
            lastX = centroid[0];
            lastY = centroid[1];
        }

        private float[] centroid(MotionEvent event) {
            int count = Math.max(1, event.getPointerCount());
            float x = 0f;
            float y = 0f;
            for (int i = 0; i < count; i++) {
                x += event.getX(i);
                y += event.getY(i);
            }
            return new float[]{x / count, y / count};
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

        private void handleCompletedGesture() {
            float dx = lastX - downX;
            float dy = lastY - downY;
            String circleMotion = detectCircleMotion();
            if (circleMotion != null) {
                cancelPendingTap();
                sendMotion(1, circleMotion);
                return;
            }

            if (isSwipe(dx, dy)) {
                cancelPendingTap();
                sendMotion(maxPointerCount, resolveSwipeMotion(dx, dy));
                return;
            }

            float tapSlop = TAP_SLOP_DP * getResources().getDisplayMetrics().density;
            if (Math.abs(dx) <= tapSlop && Math.abs(dy) <= tapSlop) {
                handleTap(maxPointerCount, lastX, lastY);
            }
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

        private void handleTap(int fingers, float x, float y) {
            float maxDistance = MULTI_TAP_DISTANCE_DP * getResources().getDisplayMetrics().density;
            boolean continuesPending = pendingTap != null
                    && pendingTapFingers == fingers
                    && distance(x, y, pendingTapX, pendingTapY) <= maxDistance;

            if (!continuesPending) {
                cancelPendingTap();
                pendingTapFingers = fingers;
                pendingTapCount = 1;
            } else {
                tapHandler.removeCallbacks(pendingTap);
                pendingTapCount++;
            }

            pendingTapX = x;
            pendingTapY = y;
            if (pendingTapCount >= 3) {
                int tapCount = pendingTapCount;
                cancelPendingTap();
                emitTap(fingers, tapCount, x, y);
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

        private float distanceBetweenFirstTwoPointers(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private String detectCircleMotion() {
            if (maxPointerCount != 1 || pathXs.size() < 8) return null;
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
        protected void onDetachedFromWindow() {
            cancelPendingTap();
            clearBackgroundBitmap();
            super.onDetachedFromWindow();
        }

interface TriggerListener {
    void onTriggered(String triggerId);
}
}
