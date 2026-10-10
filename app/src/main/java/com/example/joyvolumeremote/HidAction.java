package com.example.joyvolumeremote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HidAction {
    enum OutputType {
        NONE,
        KEYBOARD,
        CONSUMER
    }

    static final int MOD_LEFT_CTRL = 0x01;
    static final int MOD_LEFT_SHIFT = 0x02;
    static final int MOD_LEFT_ALT = 0x04;
    static final int MOD_LEFT_GUI = 0x08;

    static final int KEY_A = 0x04;
    static final int KEY_B = 0x05;
    static final int KEY_C = 0x06;
    static final int KEY_V = 0x19;
    static final int KEY_Z = 0x1D;
    static final int KEY_ENTER = 0x28;
    static final int KEY_ESC = 0x29;
    static final int KEY_TAB = 0x2B;
    static final int KEY_SPACE = 0x2C;
    static final int KEY_PAGE_UP = 0x4B;
    static final int KEY_PAGE_DOWN = 0x4E;
    static final int KEY_RIGHT = 0x4F;
    static final int KEY_LEFT = 0x50;
    static final int KEY_DOWN = 0x51;
    static final int KEY_UP = 0x52;
    static final int KEY_F5 = 0x3E;
    static final int KEY_POWER = 0x66;
    static final int KEY_VOLUME_MUTE = 0x7F;
    static final int KEY_VOLUME_UP = 0x80;
    static final int KEY_VOLUME_DOWN = 0x81;

    static final int CONSUMER_PLAY_PAUSE = 0x00CD;
    static final int CONSUMER_SCAN_NEXT = 0x00B5;
    static final int CONSUMER_SCAN_PREV = 0x00B6;
    static final int CONSUMER_MUTE = 0x00E2;
    static final int CONSUMER_VOLUME_UP = 0x00E9;
    static final int CONSUMER_VOLUME_DOWN = 0x00EA;


    static final class ActionDefinition {
        final String id;
        final String category;
        final String label;
        final OutputType outputType;
        final int modifierMask;
        final int[] keyboardUsages;
        final int consumerUsage;

        ActionDefinition(
                String id,
                String category,
                String label,
                OutputType outputType,
                int modifierMask,
                int[] keyboardUsages,
                int consumerUsage
        ) {
            this.id = id;
            this.category = category;
            this.label = label;
            this.outputType = outputType;
            this.modifierMask = modifierMask;
            this.keyboardUsages = keyboardUsages == null ? new int[0] : keyboardUsages.clone();
            this.consumerUsage = consumerUsage;
        }
    }

    private static final List<ActionDefinition> ACTIONS;
    private static final Map<String, ActionDefinition> ACTIONS_BY_ID;

    static {
        List<ActionDefinition> actions = new ArrayList<>();

        actions.add(none("none", "禁用"));

        actions.add(keyboard("kb_a", "键盘", "A", 0, KEY_A));
        actions.add(keyboard("kb_b", "键盘", "B", 0, KEY_B));
        actions.add(keyboard("kb_enter", "键盘", "Enter", 0, KEY_ENTER));
        actions.add(keyboard("kb_space", "键盘", "Space", 0, KEY_SPACE));
        actions.add(keyboard("kb_tab", "键盘", "Tab", 0, KEY_TAB));
        actions.add(keyboard("kb_esc", "键盘", "Esc", 0, KEY_ESC));
        actions.add(keyboard("kb_page_up", "键盘", "Page Up", 0, KEY_PAGE_UP));
        actions.add(keyboard("kb_page_down", "键盘", "Page Down", 0, KEY_PAGE_DOWN));
        actions.add(keyboard("kb_arrow_up", "键盘", "方向上", 0, KEY_UP));
        actions.add(keyboard("kb_arrow_down", "键盘", "方向下", 0, KEY_DOWN));
        actions.add(keyboard("kb_arrow_left", "键盘", "方向左", 0, KEY_LEFT));
        actions.add(keyboard("kb_arrow_right", "键盘", "方向右", 0, KEY_RIGHT));
        actions.add(keyboard("kb_f5", "键盘", "F5", 0, KEY_F5));
        actions.add(keyboard("kb_volume_up", "键盘", "音量+（Android键）", 0, KEY_VOLUME_UP));
        actions.add(keyboard("kb_volume_down", "键盘", "音量-（Android键）", 0, KEY_VOLUME_DOWN));
        actions.add(keyboard("kb_volume_mute", "键盘", "静音（Android键）", 0, KEY_VOLUME_MUTE));
        actions.add(keyboard("kb_ctrl_c", "键盘", "Ctrl + C", MOD_LEFT_CTRL, KEY_C));
        actions.add(keyboard("kb_ctrl_v", "键盘", "Ctrl + V", MOD_LEFT_CTRL, KEY_V));
        actions.add(keyboard("kb_ctrl_z", "键盘", "Ctrl + Z", MOD_LEFT_CTRL, KEY_Z));
        actions.add(keyboard("kb_shift_tab", "键盘", "Shift + Tab", MOD_LEFT_SHIFT, KEY_TAB));

        actions.add(keyboard("kb_power", "系统", "电源键（实验）", 0, KEY_POWER));

        actions.add(consumer("consumer_volume_up", "媒体键", "音量+（Consumer）", CONSUMER_VOLUME_UP));
        actions.add(consumer("consumer_volume_down", "媒体键", "音量-（Consumer）", CONSUMER_VOLUME_DOWN));
        actions.add(consumer("consumer_mute", "媒体键", "静音（Consumer）", CONSUMER_MUTE));
        actions.add(consumer("consumer_play_pause", "媒体键", "播放/暂停", CONSUMER_PLAY_PAUSE));
        actions.add(consumer("consumer_next", "媒体键", "下一曲", CONSUMER_SCAN_NEXT));
        actions.add(consumer("consumer_prev", "媒体键", "上一曲", CONSUMER_SCAN_PREV));


        ACTIONS = Collections.unmodifiableList(actions);

        LinkedHashMap<String, ActionDefinition> byId = new LinkedHashMap<>();
        for (ActionDefinition action : ACTIONS) {
            byId.put(action.id, action);
        }
        ACTIONS_BY_ID = Collections.unmodifiableMap(byId);
    }

    private HidAction() {
    }

    static List<ActionDefinition> all() {
        return ACTIONS;
    }

    static ActionDefinition byId(String id) {
        ActionDefinition action = ACTIONS_BY_ID.get(id);
        return action != null ? action : ACTIONS_BY_ID.get("none");
    }

    static String labelForId(String id) {
        return byId(id).label;
    }

    private static ActionDefinition none(String id, String label) {
        return new ActionDefinition(id, "空动作", label, OutputType.NONE, 0, null, 0);
    }

    private static ActionDefinition keyboard(String id, String category, String label, int modifierMask, int... keys) {
        return new ActionDefinition(id, category, label, OutputType.KEYBOARD, modifierMask, keys, 0);
    }

    private static ActionDefinition consumer(String id, String category, String label, int usage) {
        return new ActionDefinition(id, category, label, OutputType.CONSUMER, 0, null, usage);
    }

}
