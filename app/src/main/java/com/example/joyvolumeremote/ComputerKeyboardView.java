package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

    final class ComputerKeyboardView extends View {
        private static final int KEY_A = 0x04;
        private static final int KEY_1 = 0x1E;
        private static final int KEY_ENTER = 0x28;
        private static final int KEY_ESC = 0x29;
        private static final int KEY_BACKSPACE = 0x2A;
        private static final int KEY_TAB = 0x2B;
        private static final int KEY_SPACE = 0x2C;
        private static final int KEY_MINUS = 0x2D;
        private static final int KEY_EQUAL = 0x2E;
        private static final int KEY_LEFT_BRACKET = 0x2F;
        private static final int KEY_RIGHT_BRACKET = 0x30;
        private static final int KEY_BACKSLASH = 0x31;
        private static final int KEY_SEMICOLON = 0x33;
        private static final int KEY_QUOTE = 0x34;
        private static final int KEY_GRAVE = 0x35;
        private static final int KEY_COMMA = 0x36;
        private static final int KEY_DOT = 0x37;
        private static final int KEY_SLASH = 0x38;
        private static final int KEY_CAPS_LOCK = 0x39;
        private static final int KEY_F1 = 0x3A;
        private static final int KEY_DELETE = 0x4C;
        private static final int KEY_RIGHT = 0x4F;
        private static final int KEY_LEFT = 0x50;
        private static final int KEY_DOWN = 0x51;
        private static final int KEY_UP = 0x52;
        private static final int MOD_LEFT_CTRL = 0x01;
        private static final int MOD_LEFT_SHIFT = 0x02;
        private static final int MOD_LEFT_ALT = 0x04;
        private static final int MOD_LEFT_GUI = 0x08;
        private static final int MOD_RIGHT_SHIFT = 0x20;
        private static final int MOD_RIGHT_ALT = 0x40;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF exitRect = new RectF();
        private final List<KeyboardRow> rows = new ArrayList<>();
        private final List<KeyboardKey> keys = new ArrayList<>();
        private KeyboardStateListener stateListener;
        private Runnable disconnectListener;
        private boolean vibrationEnabled = true;
        private int vibrationLevel = VIBRATION_MEDIUM;
        private int lastModifierMask = -1;
        private int[] lastUsages = new int[0];
        private boolean pendingExitTap = false;

        ComputerKeyboardView(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setBackgroundColor(Color.rgb(8, 10, 14));
            strokePaint.setStyle(Paint.Style.STROKE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            buildKeyboardRows();
        }

        void setStateListener(KeyboardStateListener stateListener) {
            this.stateListener = stateListener;
            emitState(0, new int[0]);
        }

        void setDisconnectListener(Runnable disconnectListener) {
            this.disconnectListener = disconnectListener;
        }

        void setVibrationEnabled(boolean vibrationEnabled) {
            this.vibrationEnabled = vibrationEnabled;
        }

        void setVibrationLevel(int vibrationLevel) {
            this.vibrationLevel = vibrationLevel;
        }

        private void buildKeyboardRows() {
            rows.clear();
            rows.add(row(
                    key("Esc", KEY_ESC, 1.15f),
                    key("F1", KEY_F1, 0.92f),
                    key("F2", KEY_F1 + 1, 0.92f),
                    key("F3", KEY_F1 + 2, 0.92f),
                    key("F4", KEY_F1 + 3, 0.92f),
                    key("F5", KEY_F1 + 4, 0.92f),
                    key("F6", KEY_F1 + 5, 0.92f),
                    key("F7", KEY_F1 + 6, 0.92f),
                    key("F8", KEY_F1 + 7, 0.92f),
                    key("F9", KEY_F1 + 8, 0.92f),
                    key("F10", KEY_F1 + 9, 0.92f),
                    key("F11", KEY_F1 + 10, 0.92f),
                    key("F12", KEY_F1 + 11, 0.92f),
                    key("Del", KEY_DELETE, 1.05f)
            ));
            rows.add(row(
                    key("`", KEY_GRAVE),
                    key("1", KEY_1),
                    key("2", KEY_1 + 1),
                    key("3", KEY_1 + 2),
                    key("4", KEY_1 + 3),
                    key("5", KEY_1 + 4),
                    key("6", KEY_1 + 5),
                    key("7", KEY_1 + 6),
                    key("8", KEY_1 + 7),
                    key("9", KEY_1 + 8),
                    key("0", KEY_1 + 9),
                    key("-", KEY_MINUS),
                    key("=", KEY_EQUAL),
                    key("Back", KEY_BACKSPACE, 1.65f)
            ));
            rows.add(row(
                    key("Tab", KEY_TAB, 1.38f),
                    letter("Q"),
                    letter("W"),
                    letter("E"),
                    letter("R"),
                    letter("T"),
                    letter("Y"),
                    letter("U"),
                    letter("I"),
                    letter("O"),
                    letter("P"),
                    key("[", KEY_LEFT_BRACKET),
                    key("]", KEY_RIGHT_BRACKET),
                    key("\\", KEY_BACKSLASH, 1.25f)
            ));
            rows.add(row(
                    key("Caps", KEY_CAPS_LOCK, 1.65f),
                    letter("A"),
                    letter("S"),
                    letter("D"),
                    letter("F"),
                    letter("G"),
                    letter("H"),
                    letter("J"),
                    letter("K"),
                    letter("L"),
                    key(";", KEY_SEMICOLON),
                    key("'", KEY_QUOTE),
                    key("Enter", KEY_ENTER, 1.9f)
            ));
            rows.add(row(
                    modifier("Shift", MOD_LEFT_SHIFT, 2.05f),
                    letter("Z"),
                    letter("X"),
                    letter("C"),
                    letter("V"),
                    letter("B"),
                    letter("N"),
                    letter("M"),
                    key(",", KEY_COMMA),
                    key(".", KEY_DOT),
                    key("/", KEY_SLASH),
                    modifier("Shift", MOD_RIGHT_SHIFT, 2.05f)
            ));
            rows.add(row(
                    modifier("Ctrl", MOD_LEFT_CTRL, 1.25f),
                    modifier("Win", MOD_LEFT_GUI, 1.18f),
                    modifier("Alt", MOD_LEFT_ALT, 1.18f),
                    key("Space", KEY_SPACE, 5.2f),
                    modifier("Alt", MOD_RIGHT_ALT, 1.18f),
                    key("Left", KEY_LEFT, 1.08f),
                    key("Down", KEY_DOWN, 1.08f),
                    key("Up", KEY_UP, 1.08f),
                    key("Right", KEY_RIGHT, 1.08f)
            ));
        }

        private KeyboardRow row(KeyboardKey... rowKeys) {
            KeyboardRow row = new KeyboardRow();
            for (KeyboardKey key : rowKeys) row.keys.add(key);
            return row;
        }

        private KeyboardKey letter(String label) {
            return key(label, KEY_A + (label.charAt(0) - 'A'));
        }

        private KeyboardKey key(String label, int usage) {
            return key(label, usage, 1f);
        }

        private KeyboardKey key(String label, int usage, float weight) {
            return new KeyboardKey(label, usage, 0, weight);
        }

        private KeyboardKey modifier(String label, int modifierMask, float weight) {
            return new KeyboardKey(label, 0, modifierMask, weight);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            layoutKeyboard(w, h);
        }

        private void layoutKeyboard(float w, float h) {
            keys.clear();
            float sidePadding = Math.max(dp(8), w * 0.018f);
            float bottomPadding = Math.max(dp(8), h * 0.025f);
            float topPadding = Math.max(dp(8), h * 0.025f);
            float exitHeight = Math.max(dp(30), Math.min(dp(38), h * 0.085f));
            float exitWidth = Math.max(dp(82), Math.min(dp(112), w * 0.15f));
            exitRect.set(
                    (w - exitWidth) * 0.5f,
                    topPadding,
                    (w + exitWidth) * 0.5f,
                    topPadding + exitHeight
            );

            float rowGap = Math.max(dp(4), Math.min(dp(8), h * 0.012f));
            float keyGap = Math.max(dp(3), Math.min(dp(7), w * 0.006f));
            float keyboardTop = exitRect.bottom + Math.max(dp(8), h * 0.025f);
            float keyboardHeight = h - keyboardTop - bottomPadding;
            float rowHeight = (keyboardHeight - rowGap * (rows.size() - 1)) / rows.size();
            if (rowHeight < dp(28)) {
                rowGap = Math.max(dp(2), rowGap * 0.55f);
                keyGap = Math.max(dp(2), keyGap * 0.65f);
                keyboardTop = exitRect.bottom + Math.max(dp(4), h * 0.012f);
                keyboardHeight = h - keyboardTop - Math.max(dp(4), bottomPadding * 0.6f);
                rowHeight = Math.max(dp(24), (keyboardHeight - rowGap * (rows.size() - 1)) / rows.size());
            }

            float y = keyboardTop;
            float availableWidth = w - sidePadding * 2f;
            for (KeyboardRow row : rows) {
                float totalWeight = row.totalWeight();
                float totalGap = keyGap * Math.max(0, row.keys.size() - 1);
                float unit = Math.max(1f, (availableWidth - totalGap) / totalWeight);
                float x = sidePadding;
                for (KeyboardKey key : row.keys) {
                    float keyWidth = unit * key.weight;
                    key.rect.set(x, y, x + keyWidth, y + rowHeight);
                    keys.add(key);
                    x += keyWidth + keyGap;
                }
                y += rowHeight + rowGap;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(8, 10, 14));
            for (KeyboardKey key : keys) drawKey(canvas, key);
            drawExit(canvas);
        }

        private void drawKey(Canvas canvas, KeyboardKey key) {
            RectF rect = key.rect;
            if (rect.isEmpty()) return;
            float radius = Math.min(rect.height(), rect.width()) * 0.16f;
            int fillColor;
            if (key.pressed) {
                fillColor = key.isModifier()
                        ? Color.rgb(92, 128, 198)
                        : Color.rgb(78, 118, 205);
            } else if (key.isModifier() || key.weight > 1.1f) {
                fillColor = Color.rgb(39, 45, 58);
            } else {
                fillColor = Color.rgb(27, 32, 43);
            }
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fillColor);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            strokePaint.setStrokeWidth(dp(key.pressed ? 1.5f : 1f));
            strokePaint.setColor(key.pressed
                    ? Color.argb(150, 180, 212, 255)
                    : Color.argb(70, 210, 220, 238));
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
            drawCenteredText(canvas, key.label, rect.centerX(), rect.centerY(),
                    keyTextSize(key), key.pressed ? Color.WHITE : Color.rgb(226, 232, 242));
        }

        private float keyTextSize(KeyboardKey key) {
            float maxSize = Math.min(dp(14), key.rect.height() * 0.34f);
            float fitSize = key.rect.width() / Math.max(1.6f, key.label.length() * 0.66f);
            return Math.max(dp(6), Math.min(maxSize, fitSize));
        }

        private void drawExit(Canvas canvas) {
            float radius = exitRect.height() * 0.5f;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(215, 242, 245, 250));
            canvas.drawRoundRect(exitRect, radius, radius, fillPaint);
            strokePaint.setStrokeWidth(dp(1f));
            strokePaint.setColor(Color.argb(90, 255, 255, 255));
            canvas.drawRoundRect(exitRect, radius, radius, strokePaint);
            drawCenteredText(canvas, getContext().getString(R.string.ui_exit), exitRect.centerX(), exitRect.centerY(),
                    Math.min(dp(13), exitRect.height() * 0.36f), Color.rgb(34, 43, 59));
        }

        private void drawCenteredText(Canvas canvas, String text, float cx, float cy, float size, int color) {
            textPaint.setColor(color);
            textPaint.setTextSize(size);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) * 0.5f, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                int index = event.getActionIndex();
                if (exitRect.contains(event.getX(index), event.getY(index))) {
                    releaseAllKeys();
                    pendingExitTap = true;
                    return true;
                }
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                pendingExitTap = false;
                releaseAllKeys();
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }

            if (pendingExitTap) {
                int index = event.getActionIndex();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                    boolean confirmed = exitRect.contains(event.getX(index), event.getY(index));
                    pendingExitTap = false;
                    releaseAllKeys();
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (confirmed && disconnectListener != null) {
                        post(disconnectListener);
                    }
                    performClick();
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    boolean stillInside = false;
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (exitRect.contains(event.getX(i), event.getY(i))) {
                            stillInside = true;
                            break;
                        }
                    }
                    if (!stillInside) {
                        pendingExitTap = false;
                    }
                    return true;
                }
            }

            computeStateFromTouches(event);
            if (action == MotionEvent.ACTION_UP) {
                performClick();
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private void computeStateFromTouches(MotionEvent event) {
            int action = event.getActionMasked();
            int liftedIndex = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                    ? event.getActionIndex()
                    : -1;
            for (KeyboardKey key : keys) key.pressed = false;

            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == liftedIndex) continue;
                float x = event.getX(i);
                float y = event.getY(i);
                if (exitRect.contains(x, y)) continue;
                KeyboardKey touchedKey = findKey(x, y);
                if (touchedKey != null) touchedKey.pressed = true;
            }

            int modifierMask = 0;
            int[] usageSlots = new int[6];
            int usageCount = 0;
            for (KeyboardKey key : keys) {
                if (!key.pressed) continue;
                if (key.isModifier()) {
                    modifierMask |= key.modifierMask;
                } else if (key.usage != 0 && !containsUsage(usageSlots, usageCount, key.usage) && usageCount < usageSlots.length) {
                    usageSlots[usageCount++] = key.usage;
                }
            }
            int[] usages = new int[usageCount];
            System.arraycopy(usageSlots, 0, usages, 0, usageCount);
            emitState(modifierMask, usages);
            invalidate();
        }

        private KeyboardKey findKey(float x, float y) {
            for (KeyboardKey key : keys) {
                if (key.rect.contains(x, y)) return key;
            }
            return null;
        }

        private boolean containsUsage(int[] usages, int count, int usage) {
            for (int i = 0; i < count; i++) {
                if (usages[i] == usage) return true;
            }
            return false;
        }

        private void releaseAllKeys() {
            for (KeyboardKey key : keys) key.pressed = false;
            emitState(0, new int[0]);
            invalidate();
        }

        private void emitState(int modifierMask, int[] keyboardUsages) {
            if (modifierMask == lastModifierMask && sameUsages(keyboardUsages, lastUsages)) return;
            if (vibrationEnabled && (modifierMask != 0 || keyboardUsages.length > 0)) {
                performHapticFeedback(triggerFeedbackConstant());
            }
            lastModifierMask = modifierMask;
            lastUsages = copyUsages(keyboardUsages);
            if (stateListener != null) stateListener.onStateChanged(modifierMask, copyUsages(keyboardUsages));
        }

        private boolean sameUsages(int[] left, int[] right) {
            if (left == right) return true;
            if (left == null || right == null || left.length != right.length) return false;
            for (int i = 0; i < left.length; i++) {
                if (left[i] != right[i]) return false;
            }
            return true;
        }

        private int[] copyUsages(int[] source) {
            if (source == null || source.length == 0) return new int[0];
            int[] copy = new int[source.length];
            System.arraycopy(source, 0, copy, 0, source.length);
            return copy;
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
            releaseAllKeys();
            super.onDetachedFromWindow();
        }

        private class KeyboardRow {
            final List<KeyboardKey> keys = new ArrayList<>();

            float totalWeight() {
                float total = 0f;
                for (KeyboardKey key : keys) total += key.weight;
                return total;
            }
        }

        private class KeyboardKey {
            final String label;
            final int usage;
            final int modifierMask;
            final float weight;
            final RectF rect = new RectF();
            boolean pressed;

            KeyboardKey(String label, int usage, int modifierMask, float weight) {
                this.label = label;
                this.usage = usage;
                this.modifierMask = modifierMask;
                this.weight = weight;
            }

            boolean isModifier() {
                return modifierMask != 0;
            }
        }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
interface KeyboardStateListener {
    void onStateChanged(int modifierMask, int[] keyboardUsages);
}
}
