package com.example.joyvolumeremote;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Edits a private draft and commits only when Done is pressed. */
final class SignalPickerDialog extends Dialog {
    interface OnSelected { void accept(String value); }
    private final AppPreferences appearance;
    private final OnSelected onSelected;
    private final String gesture;
    private final GestureDemoCatalog gestureDemos;
    private GesturePreviewDialog gesturePreview;
    private final List<List<String>> steps = new ArrayList<>();
    private PickerTabs modeTabs, categoryTabs;
    private final Map<String, TextView> keyButtons = new LinkedHashMap<>();
    private SignalProgram.Mode mode = SignalProgram.Mode.SINGLE;
    private int activeStep, category, gapMs = SignalProgram.DEFAULT_GAP_MS;
    private boolean stepChord;
    private LinearLayout root, candidates, sequenceTools, catalog;
    private ScrollView candidateScroll;
    private CheckBox chordToggle;
    private TextView hint, error, done, gapButton, undoButton, clearButton;

    SignalPickerDialog(Context context, AppPreferences appearance, GestureDemoCatalog gestureDemos, String value, String gesture, OnSelected onSelected) {
        super(context);
        this.appearance = appearance;
        this.onSelected = onSelected;
        this.gesture = gesture;
        this.gestureDemos = gestureDemos;
        try {
            SignalProgram existing = SignalProgram.parse(value);
            mode = existing.mode;
            gapMs = existing.gapMs;
            for (List<String> step : existing.steps) steps.add(new ArrayList<>(step));
            if ("none".equals(value) || value == null) steps.clear();
        } catch (IllegalArgumentException e) {
            // A corrupt value remains untouched unless the user explicitly replaces it.
        }
        if (steps.isEmpty()) steps.add(new ArrayList<>());
        stepChord = steps.get(0).size() > 1;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = column();
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackground(shape(Color.WHITE, Color.rgb(224, 228, 235), 22));
        TextView title = text(getContext().getString(R.string.ui_choose_output_signal), 19, true);
        root.addView(title);
        TextView subtitle = text(gesture, 12, false);
        subtitle.setTextColor(Color.rgb(91, 102, 121));
        root.addView(subtitle);

        modeTabs = new PickerTabs(getContext(), new String[]{getContext().getString(R.string.signal_single),
                getContext().getString(R.string.signal_chord), getContext().getString(R.string.signal_sequence)}, true,
                index -> changeMode(SignalProgram.Mode.values()[index]));
        LinearLayout.LayoutParams modesParams = new LinearLayout.LayoutParams(-1, -2);
        modesParams.topMargin = dp(10);
        HorizontalScrollView modeScroll = new HorizontalScrollView(getContext());
        modeScroll.setHorizontalScrollBarEnabled(false);
        modeScroll.setFillViewport(true);
        modeScroll.addView(modeTabs);
        root.addView(modeScroll, modesParams);
        // Keep the mode and confirmation controls reachable even with large system text.
        ScrollView editorScroll = new ScrollView(getContext());
        editorScroll.setFillViewport(false);
        LinearLayout details = column();
        editorScroll.addView(details);
        root.addView(editorScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        hint = text("", 12, false);
        hint.setTextColor(Color.rgb(91, 102, 121));
        hint.setPadding(0, dp(10), 0, dp(8));
        details.addView(hint);

        LinearLayout candidateHeading = row();
        candidateHeading.addView(text(getContext().getString(R.string.signal_candidate), 12, true), new LinearLayout.LayoutParams(0, -2, 1));
        undoButton = compactButton(getContext().getString(R.string.signal_undo), v -> undo());
        candidateHeading.addView(undoButton);
        clearButton = compactButton(getContext().getString(R.string.signal_clear), v -> {
            steps.clear(); steps.add(new ArrayList<>()); activeStep = 0; refresh();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-2, -2);
        clearParams.leftMargin = dp(6);
        candidateHeading.addView(clearButton, clearParams);
        details.addView(candidateHeading);
        candidates = column();
        candidateScroll = new ScrollView(getContext());
        candidateScroll.setFillViewport(false);
        candidateScroll.setScrollbarFadingEnabled(false);
        candidateScroll.addView(candidates);
        candidateScroll.setNestedScrollingEnabled(true);
        LinearLayout.LayoutParams candidateParams = new LinearLayout.LayoutParams(-1, -2);
        candidateParams.topMargin = dp(6);
        details.addView(candidateScroll, candidateParams);

        sequenceTools = row();
        android.content.res.Configuration configuration = getContext().getResources().getConfiguration();
        boolean narrow = configuration.screenWidthDp < 360 || configuration.fontScale > 1.15f;
        if (narrow) sequenceTools.setOrientation(LinearLayout.VERTICAL);
        chordToggle = new CheckBox(getContext());
        chordToggle.setText(R.string.signal_step_chord);
        chordToggle.setTextSize(12);
        chordToggle.setTextColor(appearance.accent());
        chordToggle.setMinimumHeight(dp(48));
        chordToggle.setButtonTintList(android.content.res.ColorStateList.valueOf(appearance.accent()));
        chordToggle.setOnCheckedChangeListener((view, checked) -> {
            stepChord = checked;
            if (!checked && current().size() > 1) {
                String first = current().get(0); current().clear(); current().add(first); refresh();
            }
        });
        sequenceTools.addView(chordToggle, narrow ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1));
        sequenceTools.addView(button(getContext().getString(R.string.signal_add_step), v -> addStep()));
        details.addView(sequenceTools);
        gapButton = button("", v -> {
            int[] gaps = {0, 100, 250, 500, 1000, 2000};
            int next = 0;
            while (next < gaps.length && gaps[next] <= gapMs) next++;
            gapMs = gaps[next % gaps.length]; refresh();
        });
        details.addView(gapButton);

        HorizontalScrollView tabsScroll = new HorizontalScrollView(getContext());
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setFillViewport(true);
        categoryTabs = new PickerTabs(getContext(), new String[]{getContext().getString(R.string.signal_keyboard),
                getContext().getString(R.string.signal_android), getContext().getString(R.string.signal_media),
                getContext().getString(R.string.signal_touch)}, false, index -> {
            category = index; buildCatalog(); refresh();
        });
        tabsScroll.addView(categoryTabs);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, -2);
        tabsParams.topMargin = dp(14);
        details.addView(tabsScroll, tabsParams);
        catalog = column();
        catalog.setPadding(dp(8), dp(8), dp(8), dp(8));
        catalog.setBackground(shape(Color.rgb(245, 247, 250), Color.rgb(226, 231, 237), 14));
        LinearLayout.LayoutParams catalogParams = new LinearLayout.LayoutParams(-1, -2);
        catalogParams.topMargin = dp(10);
        catalogParams.bottomMargin = dp(8);
        details.addView(catalog, catalogParams);
        error = text("", 12, false);
        error.setTextColor(Color.rgb(173, 45, 45));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        root.addView(error);

        LinearLayout footer = row();
        addWeighted(footer, button(getContext().getString(R.string.ui_cancel), v -> dismiss()));
        done = button(getContext().getString(R.string.ui_done), v -> commit());
        done.setBackground(actionBackground(appearance.primary(), 16));
        addWeighted(footer, done);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(10);
        root.addView(footer, footerParams);
        setContentView(root);
        buildCatalog();
        refresh();
    }

    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(.28f);
            android.util.DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            window.setLayout(Math.min(metrics.widthPixels - dp(20), dp(1000)), (int) (metrics.heightPixels * .91f));
        }
    }

    private List<String> current() { return steps.get(activeStep); }

    private void changeMode(SignalProgram.Mode target) {
        if (mode == target) return;
        List<String> keep = new ArrayList<>(current());
        if (target != SignalProgram.Mode.SEQUENCE) {
            steps.clear(); steps.add(keep); activeStep = 0;
            if (target == SignalProgram.Mode.SINGLE && keep.size() > 1) {
                String first = keep.get(0); keep.clear(); keep.add(first);
            }
        }
        mode = target;
        stepChord = current().size() > 1;
        refresh();
    }

    private void select(String id) {
        boolean append = mode == SignalProgram.Mode.CHORD || (mode == SignalProgram.Mode.SEQUENCE && stepChord);
        List<String> next = new ArrayList<>(current());
        if (!append) next.clear();
        if (append && next.contains(id)) next.remove(id);
        else next.add(id);
        try {
            if (!next.isEmpty()) SignalProgram.validateStep(next);
        } catch (IllegalArgumentException e) {
            showError(R.string.signal_chord_limit); return;
        }
        current().clear(); current().addAll(next); refresh();
    }

    private void addStep() {
        if (current().isEmpty()) { showError(R.string.signal_select_first); return; }
        if (steps.size() >= SignalProgram.MAX_STEPS) { showError(R.string.signal_step_limit); return; }
        steps.add(new ArrayList<>()); activeStep = steps.size() - 1; stepChord = false;
        refresh();
        candidateScroll.post(() -> candidateScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void undo() {
        if (!current().isEmpty()) current().remove(current().size() - 1);
        else if (mode == SignalProgram.Mode.SEQUENCE && steps.size() > 1) {
            steps.remove(activeStep); activeStep = Math.max(0, activeStep - 1); stepChord = current().size() > 1;
        }
        refresh();
    }

    private void refresh() {
        if (hint == null) return;
        errorTextClear();
        hint.setText(mode == SignalProgram.Mode.SINGLE ? R.string.signal_single_hint
                : mode == SignalProgram.Mode.CHORD ? R.string.signal_chord_hint : R.string.signal_sequence_hint);
        modeTabs.setSelectedIndex(mode.ordinal());
        categoryTabs.setSelectedIndex(category);
        candidates.removeAllViews();
        for (int i = 0; i < steps.size(); i++) {
            final int index = i;
            LinearLayout row = row();
            List<String> step = steps.get(i);
            String label = step.isEmpty() ? getContext().getString(R.string.signal_empty) : ActionLabels.step(getContext(), step);
            TextView selected = text((mode == SignalProgram.Mode.SEQUENCE ? (i + 1) + ".  " : "") + label, 14, !step.isEmpty());
            selected.setMinHeight(dp(48));
            selected.setPadding(dp(12), dp(10), dp(12), dp(10));
            selected.setGravity(Gravity.CENTER_VERTICAL);
            selected.setTextColor(step.isEmpty() ? Color.rgb(115, 126, 143) : Color.rgb(24, 31, 43));
            StateListDrawable fieldStates = new StateListDrawable();
            fieldStates.addState(new int[]{android.R.attr.state_focused}, shape(appearance.tint(), appearance.accent(), 10));
            fieldStates.addState(new int[]{android.R.attr.state_pressed}, shape(appearance.tint(), appearance.accent(), 10));
            fieldStates.addState(new int[]{}, shape(Color.rgb(250, 251, 253), index == activeStep
                    ? appearance.primary() : Color.rgb(220, 226, 234), 10));
            selected.setBackground(fieldStates);
            if (mode == SignalProgram.Mode.SEQUENCE) {
                selected.setSelected(index == activeStep);
                selected.setFocusable(true);
                selected.setOnClickListener(v -> { activeStep = index; stepChord = current().size() > 1; refresh(); });
            }
            row.addView(selected, new LinearLayout.LayoutParams(0, -2, 1));
            if (mode == SignalProgram.Mode.SEQUENCE && steps.size() > 1) {
                TextView remove = compactButton("×", v -> {
                    steps.remove(index);
                    if (activeStep > index) activeStep--;
                    activeStep = Math.min(activeStep, steps.size() - 1);
                    stepChord = current().size() > 1; refresh();
                });
                remove.setContentDescription(getContext().getString(R.string.signal_remove_step, i + 1));
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(36), -2);
                removeParams.leftMargin = dp(6);
                row.addView(remove, removeParams);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rowParams.topMargin = dp(6);
            candidates.addView(row, rowParams);
        }
        int maxCandidate = getContext().getResources().getConfiguration().screenHeightDp < 650 ? 112 : 176;
        int dialogWidth = Math.min(getContext().getResources().getDisplayMetrics().widthPixels - dp(20), dp(1000));
        int candidateWidth = Math.max(dp(80), dialogWidth - dp(32) - (mode == SignalProgram.Mode.SEQUENCE ? dp(42) : 0));
        int candidateHeight = 0;
        for (int i = 0; i < candidates.getChildCount(); i++) {
            TextView selected = (TextView) ((LinearLayout) candidates.getChildAt(i)).getChildAt(0);
            selected.measure(View.MeasureSpec.makeMeasureSpec(candidateWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            candidateHeight += selected.getMeasuredHeight();
            if (i > 0) candidateHeight += dp(6);
        }
        LinearLayout.LayoutParams candidateParams = new LinearLayout.LayoutParams(-1, Math.min(dp(maxCandidate), Math.max(dp(48), candidateHeight)));
        candidateParams.topMargin = dp(6);
        candidateScroll.setLayoutParams(candidateParams);
        sequenceTools.setVisibility(mode == SignalProgram.Mode.SEQUENCE ? View.VISIBLE : View.GONE);
        gapButton.setVisibility(mode == SignalProgram.Mode.SEQUENCE ? View.VISIBLE : View.GONE);
        chordToggle.setChecked(stepChord);
        gapButton.setText(getContext().getString(R.string.signal_gap, gapMs));
        for (Map.Entry<String, TextView> entry : keyButtons.entrySet()) mark(entry.getValue(), current().contains(entry.getKey()));
        boolean valid = true;
        for (List<String> step : steps) valid &= !step.isEmpty();
        done.setEnabled(valid); done.setAlpha(valid ? 1f : .4f);
        boolean hasSelection = steps.size() > 1 || !current().isEmpty();
        undoButton.setEnabled(hasSelection); clearButton.setEnabled(hasSelection);
        undoButton.setAlpha(hasSelection ? 1f : .4f); clearButton.setAlpha(hasSelection ? 1f : .4f);
    }

    private void errorTextClear() {
        if (error != null) { error.setText(""); error.setVisibility(View.GONE); }
    }

    private void showError(int resource) { error.setText(resource); error.setVisibility(View.VISIBLE); }

    private void commit() {
        try {
            SignalProgram program = new SignalProgram(mode, steps, gapMs);
            onSelected.accept(program.encode()); dismiss();
        } catch (IllegalArgumentException e) { showError(R.string.signal_select_first); }
    }

    private void buildCatalog() {
        catalog.removeAllViews(); keyButtons.clear();
        if (category == 0) {
            TextView scrollHint = text(getContext().getString(R.string.signal_keyboard_hint), 11, false);
            scrollHint.setPadding(dp(4), dp(8), dp(4), dp(8)); catalog.addView(scrollHint);
            HorizontalScrollView horizontal = new HorizontalScrollView(getContext());
            horizontal.setHorizontalScrollBarEnabled(true);
            LinearLayout keyboard = column();
            String[][] rows = {
                    {"esc", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12"},
                    {"grave", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "minus", "equal", "backspace"},
                    {"tab", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "left_bracket", "right_bracket", "backslash"},
                    {"caps_lock", "a", "s", "d", "f", "g", "h", "j", "k", "l", "semicolon", "quote", "enter"},
                    {"shift", "z", "x", "c", "v", "b", "n", "m", "comma", "period", "slash", "right_shift"},
                    {"ctrl", "meta", "alt", "space", "right_alt", "right_meta", "right_ctrl"}
            };
            int keyboardWidth = dp(742);
            for (String[] ids : rows) {
                LinearLayout line = row();
                for (String suffix : ids) {
                    String id = "kb_" + suffix;
                    TextView key = signalButton(id);
                    String label = HidAction.byId(id).label;
                    key.setText(label);
                    key.setTextSize(suffix.length() > 4 ? 11 : 13);
                    float weight = suffix.equals("space") ? 6 : suffix.equals("caps_lock") || suffix.equals("shift")
                            || suffix.equals("right_shift") || suffix.equals("enter") || suffix.equals("backspace") ? 1.8f : 1;
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, weight);
                    params.setMargins(dp(2), dp(2), dp(2), dp(2)); line.addView(key, params);
                }
                keyboard.addView(line, new LinearLayout.LayoutParams(keyboardWidth, -2));
            }
            horizontal.addView(keyboard); catalog.addView(horizontal);
            grid(new String[]{"kb_insert", "kb_home", "kb_page_up", "kb_delete", "kb_end", "kb_page_down",
                    "kb_print_screen", "kb_scroll_lock", "kb_pause", null, "kb_arrow_up", null,
                    "kb_arrow_left", "kb_arrow_down", "kb_arrow_right"}, 3);
            LinearLayout.LayoutParams noneParams = new LinearLayout.LayoutParams(-1, -2);
            noneParams.setMargins(dp(2), dp(2), dp(2), dp(2));
            catalog.addView(signalButton("none"), noneParams);
        } else if (category == 1) {
            grid(new String[]{"android_home", "android_back", "android_search", "kb_volume_up", "kb_volume_down", "kb_volume_mute", "kb_power"}, 2);
        } else if (category == 2) {
            grid(new String[]{"consumer_play_pause", "consumer_prev", "consumer_next", "consumer_volume_up", "consumer_volume_down", "consumer_mute"}, 2);
        } else {
            if (HidRemoteService.IS_BLE_COMPAT) notice(R.string.signal_touch_ble);
            TextView demoHint = text(getContext().getString(R.string.demo_hold_hint), 11, false);
            demoHint.setTextColor(Color.rgb(91, 102, 121)); demoHint.setPadding(dp(4), 0, dp(4), dp(8));
            catalog.addView(demoHint);
            List<String> gestures = new ArrayList<>();
            for (HidAction.ActionDefinition action : HidAction.all())
                if (action.outputType == HidAction.OutputType.TOUCH) gestures.add(action.id);
            grid(gestures.toArray(new String[0]), 2);
        }
    }

    private void notice(int resource) {
        TextView notice = text(getContext().getString(resource), 12, false);
        notice.setPadding(dp(10), dp(10), dp(10), dp(10));
        notice.setBackground(shape(appearance.tint(), appearance.tint(), 12));
        catalog.addView(notice);
    }

    private void grid(String[] ids, int columns) {
        for (int start = 0; start < ids.length; start += columns) {
            LinearLayout line = row();
            for (int i = 0; i < columns; i++) {
                if (start + i < ids.length && ids[start + i] != null) {
                    TextView key = signalButton(ids[start + i]);
                    if (category == 3 && HidRemoteService.IS_BLE_COMPAT) { key.setEnabled(false); key.setAlpha(.4f); }
                    addWeighted(line, key);
                } else line.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
            }
            catalog.addView(line);
        }
    }

    private TextView signalButton(String id) {
        TextView button = button(ActionLabels.pickerLabel(getContext(), HidAction.byId(id)), v -> select(id));
        button.setMinHeight(dp(48));
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setElevation(dp(1));
        button.setContentDescription(ActionLabels.label(getContext(), HidAction.byId(id)));
        if (HidAction.byId(id).outputType == HidAction.OutputType.TOUCH) {
            GestureDemoCatalog.Demo demo = gestureDemos.find(id);
            if (demo != null) {
                GestureThumbnailDrawable thumbnail = new GestureThumbnailDrawable(demo, appearance);
                thumbnail.setBounds(0, 0, dp(27), dp(34));
                button.setCompoundDrawables(thumbnail, null, null, null);
                button.setCompoundDrawablePadding(dp(6)); button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            }
            button.setOnLongClickListener(v -> {
                if (demo == null) { android.widget.Toast.makeText(getContext(), R.string.demo_failed, android.widget.Toast.LENGTH_SHORT).show(); return true; }
                if (gesturePreview != null) gesturePreview.dismiss();
                GesturePreviewDialog dialog = new GesturePreviewDialog(getContext(), appearance, gestureDemos, demo,
                        ActionLabels.label(getContext(), HidAction.byId(id)));
                gesturePreview = dialog;
                dialog.setOnDismissListener(d -> { if (gesturePreview == dialog) gesturePreview = null; });
                dialog.show(); return true;
            });
        }
        keyButtons.put(id, button); return button;
    }

    @Override protected void onStop() {
        if (gesturePreview != null) gesturePreview.dismiss();
        super.onStop();
    }

    private LinearLayout column() { LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(getContext()); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.rgb(24, 31, 43));
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }
    private TextView button(String label, View.OnClickListener listener) {
        TextView view = text(label, 12, true); view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48)); view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setTextColor(appearance.accent()); view.setFocusable(true);
        view.setBackground(actionBackground(appearance.tint(), 12));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        view.setOnClickListener(listener); return view;
    }
    private TextView compactButton(String label, View.OnClickListener listener) {
        TextView view = button(label, listener);
        view.setMinHeight(dp(32));
        view.setPadding(dp(10), dp(4), dp(10), dp(4));
        view.setBackground(actionBackground(appearance.tint(), 8));
        return view;
    }
    private RippleDrawable actionBackground(int fill, int radius) {
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(appearance.primary()),
                shape(fill, fill, radius), null);
    }
    private void mark(TextView view, boolean selected) {
        view.setSelected(selected);
        int fill = selected ? appearance.primary() : Color.WHITE;
        int stroke = selected ? appearance.accent() : Color.rgb(226, 231, 237);
        StateListDrawable backgrounds = new StateListDrawable();
        backgrounds.addState(new int[]{android.R.attr.state_pressed}, shape(appearance.primary(), appearance.accent(), 6));
        backgrounds.addState(new int[]{android.R.attr.state_focused}, shape(appearance.tint(), appearance.accent(), 6));
        backgrounds.addState(new int[]{}, shape(fill, stroke, 6));
        view.setBackground(backgrounds);
    }
    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(radius)); shape.setStroke(dp(1), stroke); return shape;
    }
    private void addWeighted(LinearLayout row, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2)); row.addView(view, params);
    }
    private int dp(float value) { return (int) (value * getContext().getResources().getDisplayMetrics().density + .5f); }
}
