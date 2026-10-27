package com.example.joyvolumeremote;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class TouchRatioPickerDialog extends KeyboardSettingsDialog {
    interface OnSelected { void accept(String ratio); }
    private final OnSelected listener;
    private TouchAspectRatio selected;
    private EditText width, height;
    private ImageView preview;
    private TextView value, error, done;
    private boolean updating;
    private final List<LinearLayout> presets = new ArrayList<>();

    TouchRatioPickerDialog(Context context, AppPreferences appearance, String ratio, OnSelected listener) {
        super(context, appearance, R.string.touch_ratio, R.string.touch_ratio_hint);
        selected = TouchAspectRatio.orDefault(ratio); this.listener = listener;
    }

    @Override void buildContent() {
        LinearLayout hero = new LinearLayout(getContext()); hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(12), dp(8), dp(12), dp(8)); hero.setBackground(shape(appearance.tint(), Color.TRANSPARENT, 14));
        preview = new ImageView(getContext()); preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(preview, new LinearLayout.LayoutParams(dp(64), dp(86)));
        LinearLayout copy = column(); copy.setPadding(dp(16), 0, 0, 0);
        value = text("", 21, true); copy.addView(value); copy.addView(text(getContext().getString(R.string.touch_ratio_preview), 12, false));
        hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(hero);
        TextView presetsTitle = text(getContext().getString(R.string.touch_ratio_presets), 13, true);
        presetsTitle.setPadding(0, dp(16), 0, dp(8)); body.addView(presetsTitle);
        for (int i = 0; i < TouchAspectRatio.PRESETS.length; i += 3) {
            LinearLayout row = new LinearLayout(getContext());
            for (int j = i; j < i + 3 && j < TouchAspectRatio.PRESETS.length; j++) {
                final String ratio = TouchAspectRatio.PRESETS[j];
                TouchAspectRatio aspect = TouchAspectRatio.parse(ratio);
                LinearLayout tile = column(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(4), dp(6), dp(4), dp(6));
                ImageView picture = new ImageView(getContext()); picture.setImageDrawable(new AspectRatioDrawable(aspect, appearance));
                picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(picture, new LinearLayout.LayoutParams(dp(48), dp(56)));
                TextView label = text(aspect.label(), 12, true); label.setGravity(Gravity.CENTER); tile.addView(label);
                int category = j < 3 ? R.string.touch_ratio_reader : j == 3 || j > 8 ? R.string.touch_ratio_tablet
                        : j == 8 ? R.string.touch_ratio_square : R.string.touch_ratio_phone;
                TextView caption = text(getContext().getString(category), 11, false); caption.setGravity(Gravity.CENTER);
                caption.setTextColor(Color.rgb(105, 117, 139)); tile.addView(caption);
                tile.setContentDescription(aspect.label() + ", " + caption.getText()); tile.setFocusable(true);
                tile.setOnClickListener(v -> setInputs(aspect));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1); if (j > i) params.leftMargin = dp(6);
                row.addView(tile, params); presets.add(tile);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(6); body.addView(row, params);
        }
        TextView customTitle = text(getContext().getString(R.string.touch_ratio_custom), 13, true);
        customTitle.setPadding(0, dp(12), 0, dp(8)); body.addView(customTitle);
        LinearLayout inputs = new LinearLayout(getContext()); inputs.setGravity(Gravity.CENTER_VERTICAL);
        width = dimension(R.string.touch_ratio_width); height = dimension(R.string.touch_ratio_height);
        inputs.addView(width, new LinearLayout.LayoutParams(0, -2, 1));
        TextView colon = text(":", 18, true); colon.setPadding(dp(10), 0, dp(10), 0); inputs.addView(colon);
        inputs.addView(height, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(inputs);
        TextView swap = button(getContext().getString(R.string.touch_ratio_swap), false, v -> {
            String first = width.getText().toString(); updating = true; width.setText(height.getText()); height.setText(first); updating = false; updatePreview();
        });
        LinearLayout.LayoutParams swapParams = new LinearLayout.LayoutParams(-1, -2); swapParams.topMargin = dp(8); body.addView(swap, swapParams);
        error = text(getContext().getString(R.string.touch_ratio_error), 12, false); error.setTextColor(Color.rgb(173, 45, 45));
        error.setPadding(0, dp(8), 0, 0); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(error);
        action(button(getContext().getString(R.string.ui_cancel), false, v -> dismiss()));
        done = button(getContext().getString(R.string.ui_done), true, v -> { listener.accept(selected.encode()); dismiss(); }); action(done);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!updating) updatePreview(); }
            @Override public void afterTextChanged(Editable text) { }
        };
        width.addTextChangedListener(watcher); height.addTextChangedListener(watcher); setInputs(selected);
    }

    private EditText dimension(int label) {
        EditText input = new EditText(getContext()); input.setSingleLine(true); input.setTextSize(16);
        input.setTextColor(Color.rgb(24, 31, 43)); input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        input.setHint(label); input.setContentDescription(getContext().getString(label));
        input.setBackground(shape(Color.rgb(250, 251, 253), Color.rgb(216, 223, 234), 10)); return input;
    }
    private void setInputs(TouchAspectRatio ratio) {
        updating = true; width.setText(ratio.width); height.setText(ratio.height); updating = false; updatePreview();
    }
    private void updatePreview() {
        try {
            selected = TouchAspectRatio.parse(width.getText() + ":" + height.getText());
            preview.setImageDrawable(new AspectRatioDrawable(selected, appearance)); value.setText(selected.label());
            error.setVisibility(View.GONE); done.setEnabled(true); done.setAlpha(1);
        } catch (IllegalArgumentException invalid) {
            error.setVisibility(View.VISIBLE); done.setEnabled(false); done.setAlpha(.45f);
        }
        for (int i = 0; i < presets.size(); i++) {
            boolean active = done.isEnabled() && Math.abs(selected.value - TouchAspectRatio.parse(TouchAspectRatio.PRESETS[i]).value) < .000001;
            LinearLayout tile = presets.get(i); tile.setSelected(active);
            tile.setBackground(shape(active ? appearance.primary() : Color.rgb(250, 251, 253), active ? appearance.accent() : Color.rgb(226, 231, 237), 12));
        }
    }
}
