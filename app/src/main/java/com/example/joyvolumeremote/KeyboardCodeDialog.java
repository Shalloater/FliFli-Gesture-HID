package com.example.joyvolumeremote;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import static com.example.joyvolumeremote.KeyboardDefinitions.*;

/** Import never writes preferences until the user confirms the decoded preview. */
final class KeyboardCodeDialog extends KeyboardSettingsDialog {
    interface OnImported { void accept(CustomKeyboardConfig config); }
    private final CustomKeyboardConfig source;
    private final OnImported listener;
    private EditText input;
    private TextView error, summary, proceed;
    private KeyboardConfigCode.Decoded decoded;

    KeyboardCodeDialog(Context context, AppPreferences appearance, CustomKeyboardConfig source, OnImported listener) {
        super(context, appearance, source == null ? R.string.config_code_import : R.string.config_code_export,
                source == null ? R.string.config_code_import_hint : R.string.config_code_export_hint);
        this.source = source == null ? null : source.copy(); this.listener = listener;
    }

    @Override void buildContent() {
        error = text("", 12, false); error.setTextColor(Color.rgb(173, 45, 45));
        error.setPadding(0, dp(10), 0, dp(4)); error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (source != null) buildExport(); else buildImport();
    }

    private void buildExport() {
        final String code;
        try { code = KeyboardConfigCode.encode(source); }
        catch (IllegalArgumentException invalid) {
            error.setText(R.string.config_code_export_error); error.setVisibility(View.VISIBLE); body.addView(error);
            action(button(getContext().getString(R.string.config_code_close), false, v -> dismiss())); return;
        }
        TextView name = text(source.name, 15, true); name.setPadding(0, 0, 0, dp(12)); body.addView(name);
        TextView output = text(code, 12, false); output.setTextIsSelectable(true); output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(12), dp(12), dp(12), dp(12)); output.setContentDescription(getContext().getString(R.string.config_code));
        output.setBackground(shape(Color.rgb(250, 251, 253), Color.rgb(216, 223, 234), 12)); body.addView(output);
        action(button(getContext().getString(R.string.config_code_close), false, v -> dismiss()));
        action(button(getContext().getString(R.string.config_code_copy), true, v -> {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText(getContext().getString(R.string.config_code), code));
            Toast.makeText(getContext(), R.string.config_code_copied, Toast.LENGTH_SHORT).show();
        }));
    }

    private void buildImport() {
        input = new EditText(getContext()); input.setTextSize(12); input.setTypeface(Typeface.MONOSPACE);
        input.setTextColor(Color.rgb(24, 31, 43)); input.setHint(R.string.config_code_input);
        input.setContentDescription(getContext().getString(R.string.config_code_input));
        input.setGravity(android.view.Gravity.TOP); input.setMinLines(3); input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(KeyboardConfigCode.MAX_CODE_CHARS + 1)});
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(shape(Color.rgb(250, 251, 253), Color.rgb(216, 223, 234), 12)); body.addView(input);
        TextView paste = button(getContext().getString(R.string.config_code_paste), false, v -> paste());
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(-1, -2); pasteParams.topMargin = dp(8); body.addView(paste, pasteParams);
        summary = text("", 14, false); summary.setPadding(dp(12), dp(12), dp(12), dp(12));
        summary.setBackground(shape(appearance.tint(), Color.TRANSPARENT, 12)); summary.setVisibility(View.GONE);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2); summaryParams.topMargin = dp(12); body.addView(summary, summaryParams);
        body.addView(error);
        action(button(getContext().getString(R.string.ui_cancel), false, v -> dismiss()));
        proceed = button(getContext().getString(R.string.config_code_preview), true, v -> {
            if (decoded == null) readPreview();
            else { listener.accept(decoded.config.copy()); dismiss(); }
        }); action(proceed);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                decoded = null; summary.setVisibility(View.GONE); error.setVisibility(View.GONE); proceed.setText(R.string.config_code_preview);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
    }

    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence value = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).getText();
        if (value == null || value.length() == 0 || value.length() > KeyboardConfigCode.MAX_CODE_CHARS) {
            error.setText(R.string.config_code_clipboard_error); error.setVisibility(View.VISIBLE); return;
        }
        input.setText(value); input.setSelection(input.length());
    }

    private void readPreview() {
        try {
            decoded = KeyboardConfigCode.decode(input.getText().toString());
            CustomKeyboardConfig config = decoded.config;
            if (HidRemoteService.IS_BLE_COMPAT) {
                if (KEYBOARD_TYPE_TOUCH_PASSTHROUGH.equals(config.type)) throw new IllegalArgumentException("Unsupported transport");
                for (GestureBinding binding : config.bindings) for (java.util.List<String> step : SignalProgram.parse(binding.actionId).steps)
                    for (String id : step) if (HidAction.byId(id).outputType == HidAction.OutputType.TOUCH)
                        throw new IllegalArgumentException("Unsupported touch signal");
            }
            String type = getContext().getString(KEYBOARD_TYPE_TOUCH_PASSTHROUGH.equals(config.type) ? R.string.touch_passthrough
                    : KEYBOARD_TYPE_COMPUTER.equals(config.type) ? R.string.ui_computer_keyboard : R.string.ui_gesture_keyboard);
            String details = config.name + "\n" + type;
            if (KEYBOARD_TYPE_FULLSCREEN_GESTURE.equals(config.type)) details += "\n" + getContext().getString(R.string.config_code_action_count, config.bindings.size());
            if (KEYBOARD_TYPE_TOUCH_PASSTHROUGH.equals(config.type)) details += "\n" + getContext().getString(R.string.touch_ratio)
                    + "  " + TouchAspectRatio.parse(config.touchAspectRatio).label();
            details += "\n\n" + getContext().getString(R.string.config_code_add_new);
            if (decoded.backgroundOmitted) details += "\n" + getContext().getString(R.string.config_code_background);
            summary.setText(details); summary.setVisibility(View.VISIBLE); error.setVisibility(View.GONE);
            proceed.setText(R.string.config_code_confirm); fitWindow();
        } catch (IllegalArgumentException invalid) {
            decoded = null; error.setText(R.string.config_code_invalid); error.setVisibility(View.VISIBLE); fitWindow();
        }
    }
}
