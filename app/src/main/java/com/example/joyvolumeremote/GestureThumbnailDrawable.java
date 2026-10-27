package com.example.joyvolumeremote;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.List;

/** Native rendering of the approved 54×64 SVG thumbnail, with no WebView per row. */
final class GestureThumbnailDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Path> paths = new ArrayList<>();
    private final List<float[]> starts = new ArrayList<>();
    private final int accent, soft, tint;
    private int alpha = 255;

    GestureThumbnailDrawable(GestureDemoCatalog.Demo demo, AppPreferences appearance) {
        accent = appearance.accent(); soft = appearance.primary(); tint = appearance.tint();
        for (float[] xy : demo.paths) {
            Path path = new Path();
            path.moveTo(8 + xy[0] * 38, 4 + xy[1] * 55);
            for (int i = 2; i < xy.length; i += 2) path.lineTo(8 + xy[i] * 38, 4 + xy[i + 1] * 55);
            paths.add(path); starts.add(new float[]{8 + xy[0] * 38, 4 + xy[1] * 55});
        }
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float scale = Math.min(bounds.width() / 54f, bounds.height() / 64f);
        canvas.save();
        canvas.translate(bounds.left + (bounds.width() - 54 * scale) / 2, bounds.top + (bounds.height() - 64 * scale) / 2);
        canvas.scale(scale, scale);
        paint.setAlpha(alpha); paint.setStyle(Paint.Style.FILL); paint.setColor(tint); paint.setAlpha(alpha);
        canvas.drawRoundRect(8, 4, 46, 59, 7, 7, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(soft); paint.setAlpha(alpha); paint.setStrokeWidth(1);
        canvas.drawRoundRect(8, 4, 46, 59, 7, 7, paint);
        paint.setColor(accent); paint.setAlpha(alpha); paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        for (Path path : paths) canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (float[] start : starts) canvas.drawCircle(start[0], start[1], 3, paint);
        canvas.restore();
    }
    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
