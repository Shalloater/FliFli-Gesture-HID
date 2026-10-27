package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/** Shared background rendering for gesture and direct-touch surfaces. */
abstract class TouchBackgroundView extends View {
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF backgroundDest = new RectF();
    private Bitmap backgroundBitmap;
    TouchBackgroundView(Context context) { super(context); }

    public void setBackgroundImageUri(String rawUri) {
        clearBackgroundBitmap();
        if (rawUri != null && !rawUri.trim().isEmpty()) {
            backgroundBitmap = loadScaledBackgroundBitmap(rawUri.trim());
        }
        invalidate();
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

    @Override protected void onDetachedFromWindow() {
        clearBackgroundBitmap();
        super.onDetachedFromWindow();
    }
}
