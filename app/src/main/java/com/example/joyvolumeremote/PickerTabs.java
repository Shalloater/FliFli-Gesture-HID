package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Underlined, single-selection tabs shared by the signal and gesture pickers. */
final class PickerTabs extends LinearLayout {
    interface Listener { void onSelected(int index); }
    private final AppPreferences appearance;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PickerTabs(Context context) {
        super(context);
        appearance = new AppPreferences(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.BOTTOM);
        setWillNotDraw(false);
    }

    PickerTabs(Context context, String[] labels, boolean equalWidth, Listener listener) {
        this(context);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Tab tab = new Tab(context, i);
            tab.setText(labels[i]);
            tab.setTextSize(13);
            tab.setSingleLine(true);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(48));
            tab.setMinWidth(dp(68));
            tab.setPadding(dp(equalWidth ? 8 : 12), dp(10), dp(equalWidth ? 8 : 12), dp(13));
            tab.setIncludeFontPadding(false);
            tab.setFocusable(true);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(appearance.tint()));
            states.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(appearance.tint()));
            states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            tab.setBackground(states);
            tab.setOnClickListener(view -> {
                setSelectedIndex(index);
                listener.onSelected(index);
            });
            addView(tab, equalWidth ? new LayoutParams(-2, -1, 1) : new LayoutParams(-2, -1));
        }
        setSelectedIndex(0);
    }

    void setSelectedIndex(int index) {
        for (int i = 0; i < getChildCount(); i++) {
            TextView tab = (TextView) getChildAt(i);
            boolean selected = i == index;
            boolean changed = tab.isSelected() != selected;
            tab.setSelected(selected);
            tab.setTextColor(selected ? appearance.accent() : Color.rgb(91, 102, 121));
            tab.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            tab.invalidate();
            if (selected && changed) tab.post(() -> {
                if (tab.isSelected() && getParent() instanceof android.widget.HorizontalScrollView) {
                    android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) getParent();
                    if (tab.getLeft() < scroll.getScrollX()) scroll.scrollTo(tab.getLeft(), 0);
                    else if (tab.getRight() > scroll.getScrollX() + scroll.getWidth())
                        scroll.scrollTo(tab.getRight() - scroll.getWidth(), 0);
                }
            });
        }
        invalidate();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        line.setColor(Color.rgb(226, 231, 237));
        canvas.drawRect(0, getHeight() - dp(1), getWidth(), getHeight(), line);
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).isSelected()) {
            android.view.View tab = getChildAt(i);
            line.setColor(appearance.accent());
            canvas.drawRoundRect(tab.getLeft() + dp(12), getHeight() - dp(3), tab.getRight() - dp(12), getHeight(),
                    dp(1.5f), dp(1.5f), line);
        }
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(1, getChildCount(), false,
                AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE));
    }

    private final class Tab extends TextView {
        private final int index;
        Tab(Context context, int index) { super(context); this.index = index; }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.app.ActionBar$Tab");
            info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, index, 1, false, isSelected()));
        }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
