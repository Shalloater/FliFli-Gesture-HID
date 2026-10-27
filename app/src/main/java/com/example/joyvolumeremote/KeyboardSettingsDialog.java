package com.example.joyvolumeremote;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared bounded, scrollable settings sheet with always-accessible footer actions. */
abstract class KeyboardSettingsDialog extends Dialog {
    final AppPreferences appearance;
    LinearLayout body, footer;
    private LinearLayout root;
    private final int title, hint;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener resize = this::resizeToAvailableSpace;
    private int lastWidth, lastHeight, contentHeight;

    KeyboardSettingsDialog(Context context, AppPreferences appearance, int title, int hint) {
        super(context); this.appearance = appearance; this.title = title; this.hint = hint;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = column(); root.setPadding(dp(16), dp(16), dp(16), dp(14));
        root.setBackground(shape(Color.WHITE, Color.WHITE, 22));
        root.addView(text(getContext().getString(title), 18, true));
        TextView note = text(getContext().getString(hint), 12, false);
        note.setTextColor(Color.rgb(91, 102, 121)); note.setPadding(0, dp(8), 0, dp(14)); root.addView(note);
        ScrollView scroll = new ScrollView(getContext()); scroll.setFillViewport(false);
        body = column(); scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new LinearLayout(getContext()); footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2); footerParams.topMargin = dp(12);
        root.addView(footer, footerParams); buildContent(); setContentView(root);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); getWindow().setDimAmount(.28f);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    abstract void buildContent();

    final void action(TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (footer.getChildCount() > 0) params.leftMargin = dp(8);
        footer.addView(button, params);
    }

    final TextView button(String label, boolean primary, View.OnClickListener click) {
        TextView view = text(label, 13, true); view.setTextColor(appearance.accent());
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(48)); view.setPadding(dp(10), dp(10), dp(10), dp(10));
        view.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(appearance.primary()),
                shape(primary ? appearance.primary() : appearance.tint(), Color.TRANSPARENT, 14), null));
        view.setFocusable(true); view.setOnClickListener(click);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
        return view;
    }

    final TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(getContext()); text.setText(value); text.setTextSize(size);
        text.setTextColor(Color.rgb(24, 31, 43));
        if (bold) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }
    final LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(LinearLayout.VERTICAL); return view; }
    final GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), stroke); return drawable;
    }
    final int dp(float value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }

    @Override protected void onStart() {
        super.onStart(); getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(resize); fitWindow();
    }
    @Override protected void onStop() {
        if (getWindow() != null) getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(resize);
        super.onStop();
    }
    final void fitWindow() {
        Window window = getWindow(); if (window == null || root == null) return;
        android.util.DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - dp(28), dp(440));
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        contentHeight = root.getMeasuredHeight(); resizeToAvailableSpace();
    }
    private void resizeToAvailableSpace() {
        Window window = getWindow(); if (window == null || root == null || contentHeight == 0) return;
        android.util.DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - dp(28), dp(440));
        android.graphics.Rect visible = new android.graphics.Rect();
        window.getDecorView().getWindowVisibleDisplayFrame(visible);
        int availableHeight = visible.height() > 0 ? visible.height() - dp(24) : metrics.heightPixels;
        int height = Math.min(contentHeight, Math.min((int) (metrics.heightPixels * .86f), Math.max(dp(160), availableHeight)));
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width; lastHeight = height; window.setLayout(width, height);
        }
    }
}
