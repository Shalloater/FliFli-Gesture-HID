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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A private edit draft: neither picker mutates the saved binding before Done. */
final class GestureBindingEditorDialog extends Dialog {
    interface GestureSelected { void accept(GestureBinding binding); }
    interface Listener {
        String title(GestureBinding binding);
        void chooseGesture(GestureBinding current, GestureSelected selected);
        void chooseSignal(GestureBinding current, SignalPickerDialog.OnSelected selected);
        void preview(String id);
        int save(GestureBinding binding);
    }
    private final AppPreferences appearance;
    private final GestureDemoCatalog demos;
    private final Listener listener;
    private GestureBinding working;
    private LinearLayout root, fields;
    private TextView error;

    GestureBindingEditorDialog(Context context, AppPreferences appearance, GestureDemoCatalog demos,
            GestureBinding original, Listener listener) {
        super(context); this.appearance = appearance; this.demos = demos; this.listener = listener;
        working = original.copy();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = column(); root.setPadding(dp(16), dp(16), dp(16), dp(14));
        root.setBackground(shape(Color.WHITE, 22, Color.WHITE));
        root.addView(text(getContext().getString(R.string.binding_edit_title), 18, true));
        TextView hint = text(getContext().getString(R.string.binding_edit_hint), 12, false);
        hint.setTextColor(Color.rgb(91, 102, 121)); hint.setPadding(0, dp(8), 0, dp(14)); root.addView(hint);
        ScrollView scroll = new ScrollView(getContext()); scroll.setFillViewport(false);
        fields = column(); scroll.addView(fields);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        error = text("", 12, false); error.setTextColor(Color.rgb(173, 45, 45));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); error.setVisibility(View.GONE); root.addView(error);
        LinearLayout footer = row();
        TextView cancel = button(getContext().getString(R.string.ui_cancel), v -> dismiss(), false);
        TextView done = button(getContext().getString(R.string.ui_done), v -> {
            int message = listener.save(working.copy());
            if (message == 0) dismiss();
            else { error.setText(message); error.setVisibility(View.VISIBLE); fitWindow(); }
        }, true);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1); left.rightMargin = dp(4);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1); right.leftMargin = dp(4);
        footer.addView(cancel, left); footer.addView(done, right);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2); footerParams.topMargin = dp(14);
        root.addView(footer, footerParams);
        setContentView(root); refresh();
    }

    private void refresh() {
        fields.removeAllViews(); error.setVisibility(View.GONE);
        addField(getContext().getString(R.string.binding_trigger), listener.title(working), working.key(), v ->
                listener.chooseGesture(working.copy(), selected -> {
                    if (!isShowing()) return;
                    String signal = working.actionId;
                    working = selected.copy(); working.actionId = signal; refresh();
                }));
        String signalDemo = null;
        try {
            SignalProgram program = SignalProgram.parse(working.actionId);
            if (program.steps.size() == 1 && program.steps.get(0).size() == 1) {
                String id = program.steps.get(0).get(0);
                if (HidAction.byId(id).outputType == HidAction.OutputType.TOUCH) signalDemo = id;
            }
        } catch (IllegalArgumentException ignored) { /* The signal row lets the user repair an invalid saved value. */ }
        addField(getContext().getString(R.string.ui_output), ActionLabels.binding(getContext(), working.actionId), signalDemo, v ->
                listener.chooseSignal(working.copy(), selected -> {
                    if (!isShowing()) return;
                    working.actionId = selected; refresh();
                }));
        fitWindow();
    }

    private void addField(String label, String value, String demoId, View.OnClickListener click) {
        LinearLayout field = row(); field.setPadding(dp(12), dp(12), dp(12), dp(12)); field.setMinimumHeight(dp(76));
        field.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(appearance.primary()),
                shape(Color.rgb(250, 251, 253), 14, Color.rgb(226, 231, 237)), null));
        field.setFocusable(true); field.setOnClickListener(click);
        if (demoId != null) field.setOnLongClickListener(v -> { listener.preview(demoId); return true; });
        GestureDemoCatalog.Demo demo = demoId == null ? null : demos.find(demoId);
        if (demo != null) {
            ImageView image = new ImageView(getContext()); image.setImageDrawable(new GestureThumbnailDrawable(demo, appearance));
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(32), dp(40)); imageParams.rightMargin = dp(10);
            field.addView(image, imageParams);
        }
        LinearLayout copy = column();
        TextView heading = text(label, 11, false); heading.setTextColor(Color.rgb(105, 117, 139)); copy.addView(heading);
        TextView selected = text(value, 14, true); selected.setPadding(0, dp(5), 0, 0); copy.addView(selected);
        field.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 23, false); arrow.setTextColor(appearance.accent()); arrow.setPadding(dp(8), 0, 0, 0);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); field.addView(arrow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (fields.getChildCount() > 0) params.topMargin = dp(10);
        fields.addView(field, params);
    }

    @Override protected void onStart() { super.onStart(); fitWindow(); }
    private void fitWindow() {
        Window window = getWindow(); if (window == null || root == null) return;
        android.util.DisplayMetrics display = getContext().getResources().getDisplayMetrics();
        int width = Math.min(display.widthPixels - dp(28), dp(440));
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.setDimAmount(.28f);
        window.setLayout(width, Math.min(root.getMeasuredHeight(), (int) (display.heightPixels * .86f)));
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(getContext()); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView text(String label, int size, boolean bold) {
        TextView view = new TextView(getContext()); view.setText(label); view.setTextSize(size); view.setTextColor(Color.rgb(24, 31, 43));
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    private TextView button(String label, View.OnClickListener listener, boolean primary) {
        TextView view = text(label, 13, true); view.setTextColor(appearance.accent()); view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48)); view.setPadding(dp(12), dp(10), dp(12), dp(10));
        int fill = primary ? appearance.primary() : appearance.tint();
        view.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(appearance.primary()), shape(fill, 14, fill), null));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
        view.setFocusable(true); view.setOnClickListener(listener); return view;
    }
    private GradientDrawable shape(int fill, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), border); return drawable;
    }
    private int dp(float value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
}
