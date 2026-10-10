package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

final class BackgroundCropView extends View {
    private static final int MODE_NONE = 0;
    private static final int MODE_DRAG = 1;
    private static final int MODE_ZOOM = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF destination = new RectF();
    private Bitmap sourceBitmap;
    private float scale = 1f;
    private float minScale = 1f;
    private float maxScale = 6f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float lastX = 0f;
    private float lastY = 0f;
    private float lastDistance = 0f;
    private int mode = MODE_NONE;
    private boolean initialized = false;

    BackgroundCropView(Context context, Bitmap sourceBitmap) {
        super(context);
        this.sourceBitmap = sourceBitmap;
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!initialized || w != oldw || h != oldh) {
            resetTransform(w, h);
        }
    }

    private void resetTransform(int viewWidth, int viewHeight) {
        if (sourceBitmap == null || sourceBitmap.isRecycled() || viewWidth <= 0 || viewHeight <= 0) return;
        float bitmapWidth = sourceBitmap.getWidth();
        float bitmapHeight = sourceBitmap.getHeight();
        minScale = Math.max(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
        maxScale = Math.max(minScale * 5f, minScale + 0.01f);
        scale = minScale;
        offsetX = (viewWidth - bitmapWidth * scale) * 0.5f;
        offsetY = (viewHeight - bitmapHeight * scale) * 0.5f;
        initialized = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        if (sourceBitmap == null || sourceBitmap.isRecycled()) return;
        destination.set(
                offsetX,
                offsetY,
                offsetX + sourceBitmap.getWidth() * scale,
                offsetY + sourceBitmap.getHeight() * scale
        );
        canvas.drawBitmap(sourceBitmap, null, destination, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (sourceBitmap == null || sourceBitmap.isRecycled()) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mode = MODE_DRAG;
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    mode = MODE_ZOOM;
                    lastDistance = pointerDistance(event);
                    lastX = pointerFocusX(event);
                    lastY = pointerFocusY(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_ZOOM && event.getPointerCount() >= 2) {
                    float focusX = pointerFocusX(event);
                    float focusY = pointerFocusY(event);
                    float distance = pointerDistance(event);
                    if (lastDistance > 0f && distance > 0f) {
                        float nextScale = clamp(scale * (distance / lastDistance), minScale, maxScale);
                        float factor = nextScale / scale;
                        offsetX = focusX - (focusX - offsetX) * factor;
                        offsetY = focusY - (focusY - offsetY) * factor;
                        scale = nextScale;
                    }
                    offsetX += focusX - lastX;
                    offsetY += focusY - lastY;
                    lastDistance = distance;
                    lastX = focusX;
                    lastY = focusY;
                    clampOffsets();
                    invalidate();
                } else if (mode == MODE_DRAG && event.getPointerCount() == 1) {
                    float x = event.getX();
                    float y = event.getY();
                    offsetX += x - lastX;
                    offsetY += y - lastY;
                    lastX = x;
                    lastY = y;
                    clampOffsets();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() - 1 <= 1) {
                    mode = MODE_DRAG;
                    int index = event.getActionIndex() == 0 ? 1 : 0;
                    if (index < event.getPointerCount()) {
                        lastX = event.getX(index);
                        lastY = event.getY(index);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = MODE_NONE;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return true;
    }

    Bitmap createCroppedBitmap(int targetWidth, int targetHeight) {
        if (sourceBitmap == null || sourceBitmap.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return null;
        Bitmap output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);
        float scaleX = targetWidth / (float) getWidth();
        float scaleY = targetHeight / (float) getHeight();
        RectF outputDestination = new RectF(
                offsetX * scaleX,
                offsetY * scaleY,
                (offsetX + sourceBitmap.getWidth() * scale) * scaleX,
                (offsetY + sourceBitmap.getHeight() * scale) * scaleY
        );
        canvas.drawBitmap(sourceBitmap, null, outputDestination, paint);
        return output;
    }

    void release() {
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
        sourceBitmap = null;
    }

    private void clampOffsets() {
        if (sourceBitmap == null || sourceBitmap.isRecycled()) return;
        float scaledWidth = sourceBitmap.getWidth() * scale;
        float scaledHeight = sourceBitmap.getHeight() * scale;
        if (scaledWidth <= getWidth()) {
            offsetX = (getWidth() - scaledWidth) * 0.5f;
        } else {
            offsetX = clamp(offsetX, getWidth() - scaledWidth, 0f);
        }
        if (scaledHeight <= getHeight()) {
            offsetY = (getHeight() - scaledHeight) * 0.5f;
        } else {
            offsetY = clamp(offsetY, getHeight() - scaledHeight, 0f);
        }
    }

    private float pointerDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float pointerFocusX(MotionEvent event) {
        float sum = 0f;
        int count = Math.max(1, event.getPointerCount());
        for (int i = 0; i < count; i++) sum += event.getX(i);
        return sum / count;
    }

    private float pointerFocusY(MotionEvent event) {
        float sum = 0f;
        int count = Math.max(1, event.getPointerCount());
        for (int i = 0; i < count; i++) sum += event.getY(i);
        return sum / count;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }
}
