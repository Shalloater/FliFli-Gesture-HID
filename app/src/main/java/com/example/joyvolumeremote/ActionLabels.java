package com.example.joyvolumeremote;

import android.content.Context;

/** Localizes presentation without changing persisted action IDs or HID reports. */
final class ActionLabels {
    private ActionLabels() {}

    static String label(Context context, HidAction.ActionDefinition action) {
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
            default: return action.label;
        }
        String label = context.getString(resource);
        if (action.id.startsWith("kb_volume_")) return label + " (Android)";
        if (action.id.startsWith("consumer_volume_") || action.id.equals("consumer_mute")) return label + " (Consumer)";
        return label;
    }

    static String category(Context context, HidAction.ActionDefinition action) {
        if (action.id.equals("kb_power")) return context.getString(R.string.ui_system);
        return context.getString(action.outputType == HidAction.OutputType.KEYBOARD ? R.string.action_keyboard
                : action.outputType == HidAction.OutputType.CONSUMER ? R.string.action_media : R.string.action_none);
    }
}
