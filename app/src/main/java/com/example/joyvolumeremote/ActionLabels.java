package com.example.joyvolumeremote;

import android.content.Context;

/** Localizes presentation without changing persisted action IDs or HID reports. */
final class ActionLabels {
    private ActionLabels() {}

    static String label(Context context, HidAction.ActionDefinition action) {
        String label = pickerLabel(context, action);
        if (action.id.startsWith("kb_volume_")) return label + " (Android)";
        if (action.id.startsWith("consumer_volume_") || action.id.equals("consumer_mute")) return label + " (Consumer)";
        return label;
    }

    static String pickerLabel(Context context, HidAction.ActionDefinition action) {
        int resource;
        switch (action.id) {
            case "none": resource = R.string.action_none; break;
            case "kb_arrow_up": resource = R.string.action_up; break;
            case "kb_arrow_down": resource = R.string.action_down; break;
            case "kb_arrow_left": resource = R.string.action_left; break;
            case "kb_arrow_right": resource = R.string.action_right; break;
            case "kb_volume_up": case "consumer_volume_up": resource = R.string.action_volume_up; break;
            case "kb_volume_down": case "consumer_volume_down": resource = R.string.action_volume_down; break;
            case "kb_volume_mute": case "consumer_mute": resource = R.string.action_mute; break;
            case "kb_power": resource = R.string.action_power; break;
            case "consumer_play_pause": resource = R.string.action_play; break;
            case "consumer_next": resource = R.string.action_next; break;
            case "consumer_prev": resource = R.string.action_previous; break;
            case "android_home": resource = R.string.signal_home; break;
            case "android_back": resource = R.string.signal_back; break;
            case "android_search": resource = R.string.signal_search; break;
            case "touch_up": resource = R.string.ui_swipe_up; break;
            case "touch_down": resource = R.string.ui_swipe_down; break;
            case "touch_left": resource = R.string.ui_swipe_left; break;
            case "touch_right": resource = R.string.ui_swipe_right; break;
            case "touch_tap": resource = R.string.signal_center_tap; break;
            case "touch_double_tap": resource = R.string.signal_center_double; break;
            case "touch_top_left_down": resource = R.string.signal_top_left_down; break;
            case "touch_left_edge_right": resource = R.string.signal_left_edge_right; break;
            case "touch_right_edge_left": resource = R.string.signal_right_edge_left; break;
            case "touch_bottom_left_up": resource = R.string.signal_bottom_left_up; break;
            case "touch_bottom_center_up": resource = R.string.signal_bottom_center_up; break;
            case "touch_bottom_right_up": resource = R.string.signal_bottom_right_up; break;
            case "touch_top_right_diagonal": resource = R.string.signal_top_right_diagonal; break;
            case "touch_top_right_down": resource = R.string.signal_top_right_down; break;
            case "touch_left_edge_up": resource = R.string.signal_left_edge_up; break;
            case "touch_left_edge_down": resource = R.string.signal_left_edge_down; break;
            case "touch_right_edge_up": resource = R.string.signal_right_edge_up; break;
            case "touch_right_edge_down": resource = R.string.signal_right_edge_down; break;
            default: return action.label;
        }
        return context.getString(resource);
    }

    static String step(Context context, java.util.List<String> signals) {
        StringBuilder label = new StringBuilder();
        for (String id : signals) {
            if (label.length() > 0) label.append(" + ");
            label.append(label(context, HidAction.byId(id)));
        }
        return label.toString();
    }

    static String program(Context context, SignalProgram program) {
        StringBuilder label = new StringBuilder();
        for (java.util.List<String> signals : program.steps) {
            if (label.length() > 0) label.append(" → ");
            label.append(step(context, signals));
        }
        return label.toString();
    }

    static String binding(Context context, String value) {
        try { return program(context, SignalProgram.parse(value)); }
        catch (IllegalArgumentException e) { return context.getString(R.string.signal_invalid); }
    }

}
