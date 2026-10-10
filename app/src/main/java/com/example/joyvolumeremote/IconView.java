package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

final class IconView extends View {
    static final int ICON_BLUETOOTH = 1;

    static final int ICON_SETTINGS = 3;
    static final int ICON_REFRESH = 4;
    static final int ICON_CHEVRON = 5;
    static final int ICON_HEADPHONES = 6;
    static final int ICON_SPEAKER = 7;
    static final int ICON_WATCH = 8;
    static final int ICON_DEVICE = 9;
    static final int ICON_LINK = 10;
    static final int ICON_HEXAGON = 11;
    static final int ICON_PLAY = 12;
    static final int ICON_KEYBOARD = 13;

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int iconType;

    IconView(Context context, int iconType, int tintColor, float strokeDp) {
        super(context);
        this.iconType = iconType;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setColor(tintColor);
        strokePaint.setStrokeWidth(getResources().getDisplayMetrics().density * strokeDp);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(tintColor);
    }

    void setTintColor(int color) {
        strokePaint.setColor(color);
        fillPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        switch (iconType) {
            case ICON_BLUETOOTH:
                drawBluetooth(canvas, w, h);
                break;
            case ICON_SETTINGS:
                drawSettings(canvas, w, h);
                break;
            case ICON_REFRESH:
                drawRefresh(canvas, w, h);
                break;
            case ICON_CHEVRON:
                drawChevron(canvas, w, h);
                break;
            case ICON_HEADPHONES:
                drawHeadphones(canvas, w, h);
                break;
            case ICON_SPEAKER:
                drawSpeaker(canvas, w, h);
                break;
            case ICON_WATCH:
                drawWatch(canvas, w, h);
                break;
            case ICON_LINK:
                drawLink(canvas, w, h);
                break;
            case ICON_HEXAGON:
                drawHexagon(canvas, w, h);
                break;
            case ICON_PLAY:
                drawPlay(canvas, w, h);
                break;
            case ICON_KEYBOARD:
                drawKeyboard(canvas, w, h);
                break;
            default:
                drawDevice(canvas, w, h);
                break;
        }
    }

    private void drawBluetooth(Canvas canvas, float w, float h) {
        float cx = w * 0.48f;
        Path path = new Path();
        path.moveTo(cx, h * 0.14f);
        path.lineTo(cx, h * 0.86f);
        path.moveTo(cx, h * 0.14f);
        path.lineTo(w * 0.76f, h * 0.33f);
        path.lineTo(cx, h * 0.5f);
        path.lineTo(w * 0.76f, h * 0.67f);
        path.lineTo(cx, h * 0.86f);
        path.moveTo(w * 0.26f, h * 0.31f);
        path.lineTo(cx, h * 0.5f);
        path.lineTo(w * 0.26f, h * 0.69f);
        canvas.drawPath(path, strokePaint);
    }

    private void drawSettings(Canvas canvas, float w, float h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float outer = Math.min(w, h) * 0.3f;
        float inner = Math.min(w, h) * 0.14f;
        canvas.drawCircle(cx, cy, outer, strokePaint);
        canvas.drawCircle(cx, cy, inner, strokePaint);
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60d);
            float startX = cx + (float) Math.cos(angle) * outer;
            float startY = cy + (float) Math.sin(angle) * outer;
            float endX = cx + (float) Math.cos(angle) * (outer + Math.min(w, h) * 0.1f);
            float endY = cy + (float) Math.sin(angle) * (outer + Math.min(w, h) * 0.1f);
            canvas.drawLine(startX, startY, endX, endY, strokePaint);
        }
    }

    private void drawRefresh(Canvas canvas, float w, float h) {
        RectF oval = new RectF(w * 0.2f, h * 0.2f, w * 0.8f, h * 0.8f);
        float startAngle = 46f;
        float sweepAngle = 258f;
        canvas.drawArc(oval, startAngle, sweepAngle, false, strokePaint);

        float endAngle = startAngle + sweepAngle;
        float rx = oval.width() / 2f;
        float ry = oval.height() / 2f;
        float cx = oval.centerX();
        float cy = oval.centerY();
        double theta = Math.toRadians(endAngle);

        float tipX = cx + rx * (float) Math.cos(theta);
        float tipY = cy + ry * (float) Math.sin(theta);
        float tangentX = -rx * (float) Math.sin(theta);
        float tangentY = ry * (float) Math.cos(theta);
        float tangentLength = (float) Math.sqrt(tangentX * tangentX + tangentY * tangentY);
        if (tangentLength == 0f) tangentLength = 1f;
        tangentX /= tangentLength;
        tangentY /= tangentLength;

        float arrowSize = Math.min(w, h) * 0.18f;
        float backX = tipX - tangentX * arrowSize;
        float backY = tipY - tangentY * arrowSize;
        float normalX = -tangentY;
        float normalY = tangentX;

        Path arrow = new Path();
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(backX + normalX * arrowSize * 0.46f, backY + normalY * arrowSize * 0.46f);
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(backX - normalX * arrowSize * 0.46f, backY - normalY * arrowSize * 0.46f);
        canvas.drawPath(arrow, strokePaint);
    }

    private void drawChevron(Canvas canvas, float w, float h) {
        Path path = new Path();
        path.moveTo(w * 0.34f, h * 0.2f);
        path.lineTo(w * 0.64f, h * 0.5f);
        path.lineTo(w * 0.34f, h * 0.8f);
        canvas.drawPath(path, strokePaint);
    }

    private void drawHeadphones(Canvas canvas, float w, float h) {
        RectF band = new RectF(w * 0.22f, h * 0.18f, w * 0.78f, h * 0.76f);
        canvas.drawArc(band, 200, 140, false, strokePaint);
        canvas.drawRoundRect(new RectF(w * 0.18f, h * 0.5f, w * 0.32f, h * 0.8f), w * 0.06f, w * 0.06f, strokePaint);
        canvas.drawRoundRect(new RectF(w * 0.68f, h * 0.5f, w * 0.82f, h * 0.8f), w * 0.06f, w * 0.06f, strokePaint);
    }

    private void drawSpeaker(Canvas canvas, float w, float h) {
        canvas.drawRoundRect(new RectF(w * 0.32f, h * 0.16f, w * 0.68f, h * 0.84f), w * 0.08f, w * 0.08f, strokePaint);
        canvas.drawCircle(w * 0.5f, h * 0.36f, Math.min(w, h) * 0.06f, fillPaint);
        canvas.drawCircle(w * 0.5f, h * 0.66f, Math.min(w, h) * 0.09f, strokePaint);
    }

    private void drawWatch(Canvas canvas, float w, float h) {
        canvas.drawRoundRect(new RectF(w * 0.32f, h * 0.18f, w * 0.68f, h * 0.82f), w * 0.12f, w * 0.12f, strokePaint);
        canvas.drawLine(w * 0.42f, h * 0.08f, w * 0.58f, h * 0.08f, strokePaint);
        canvas.drawLine(w * 0.42f, h * 0.92f, w * 0.58f, h * 0.92f, strokePaint);
    }

    private void drawDevice(Canvas canvas, float w, float h) {
        canvas.drawRoundRect(new RectF(w * 0.28f, h * 0.14f, w * 0.72f, h * 0.86f), w * 0.14f, w * 0.14f, strokePaint);
        canvas.drawCircle(w * 0.5f, h * 0.74f, Math.min(w, h) * 0.04f, fillPaint);
    }

    private void drawLink(Canvas canvas, float w, float h) {
        RectF left = new RectF(w * 0.12f, h * 0.38f, w * 0.54f, h * 0.78f);
        RectF right = new RectF(w * 0.46f, h * 0.22f, w * 0.88f, h * 0.62f);
        canvas.drawRoundRect(left, w * 0.18f, w * 0.18f, strokePaint);
        canvas.drawRoundRect(right, w * 0.18f, w * 0.18f, strokePaint);
        canvas.drawLine(w * 0.4f, h * 0.58f, w * 0.6f, h * 0.42f, strokePaint);
    }

    private void drawHexagon(Canvas canvas, float w, float h) {
        Path hex = new Path();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60d * i - 30d);
            float x = w / 2f + (float) Math.cos(angle) * w * 0.34f;
            float y = h / 2f + (float) Math.sin(angle) * h * 0.34f;
            if (i == 0) {
                hex.moveTo(x, y);
            } else {
                hex.lineTo(x, y);
            }
        }
        hex.close();
        canvas.drawPath(hex, strokePaint);
        canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.08f, fillPaint);
    }

    private void drawPlay(Canvas canvas, float w, float h) {
        Path path = new Path();
        path.moveTo(w * 0.34f, h * 0.24f);
        path.lineTo(w * 0.74f, h * 0.5f);
        path.lineTo(w * 0.34f, h * 0.76f);
        path.close();
        canvas.drawPath(path, fillPaint);
    }

    private void drawKeyboard(Canvas canvas, float w, float h) {
        RectF body = new RectF(w * 0.12f, h * 0.24f, w * 0.88f, h * 0.76f);
        canvas.drawRoundRect(body, w * 0.08f, w * 0.08f, strokePaint);
        float top = h * 0.4f;
        float bottom = h * 0.6f;
        for (int row = 0; row < 2; row++) {
            float y = row == 0 ? top : bottom;
            canvas.drawLine(w * 0.25f, y, w * 0.32f, y, strokePaint);
            canvas.drawLine(w * 0.42f, y, w * 0.49f, y, strokePaint);
            canvas.drawLine(w * 0.59f, y, w * 0.66f, y, strokePaint);
            canvas.drawLine(w * 0.74f, y, w * 0.79f, y, strokePaint);
        }
    }

}
