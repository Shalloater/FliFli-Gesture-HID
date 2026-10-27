package com.example.joyvolumeremote;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Shape reflects the actual width:height, including custom and landscape ratios. */
final class AspectRatioDrawable extends Drawable {
    private final double ratio;
    private final int accent, tint;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    AspectRatioDrawable(TouchAspectRatio ratio, AppPreferences appearance) {
        this.ratio = ratio.value; accent = appearance.accent(); tint = appearance.tint();
    }
    @Override public void draw(Canvas canvas) {
        android.graphics.Rect bounds = getBounds();
        float padding = Math.min(bounds.width(), bounds.height()) * .1f;
        float width = (float) Math.min(bounds.width() - padding * 2, (bounds.height() - padding * 2) * ratio);
        float height = (float) (width / ratio);
        RectF screen = new RectF(bounds.exactCenterX() - width / 2, bounds.exactCenterY() - height / 2,
                bounds.exactCenterX() + width / 2, bounds.exactCenterY() + height / 2);
        float stroke = Math.max(1, Math.min(bounds.width(), bounds.height()) / 35f);
        float corner = Math.min(width, height) * .06f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(tint); canvas.drawRoundRect(screen, corner, corner, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setColor(accent);
        canvas.drawRoundRect(screen, corner, corner, paint);
        paint.setColor(Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)));
        paint.setStrokeWidth(stroke * .65f);
        canvas.drawLine(screen.left + width * .2f, screen.centerY(), screen.right - width * .2f, screen.centerY(), paint);
        canvas.drawLine(screen.centerX(), screen.top + height * .2f, screen.centerX(), screen.bottom - height * .2f, paint);
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
