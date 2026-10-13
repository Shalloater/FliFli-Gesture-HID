package com.example.joyvolumeremote;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

final class KeyboardConfigStore {
    private final SharedPreferences preferences;
    private final android.content.res.Resources resources;

    KeyboardConfigStore(Context context) {
        this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), context.getResources());
    }

    KeyboardConfigStore(SharedPreferences preferences) {
        this(preferences, null);
    }

    private KeyboardConfigStore(SharedPreferences preferences, android.content.res.Resources resources) {
        this.preferences = preferences;
        this.resources = resources;
    }

    private String text(int id, String fallback) {
        return resources == null ? fallback : resources.getString(id);
    }

    private static final String PREFS_NAME = "pager_settings";
    private static final String PREF_VIBRATION_ENABLED = "vibration_enabled";
    private static final String PREF_CONTROL_MODE = "control_mode";
    private static final String PREF_ACTION_PREFIX = "action_";
    private static final String PREF_KEYBOARDS_JSON = "custom_keyboards_json";
    private static final String PREF_NEXT_KEYBOARD_ID = "next_keyboard_id";
    private String getDefaultActionId(String triggerId) {
        switch (triggerId) {
            case TRIGGER_LANDSCAPE_TOP:
            case TRIGGER_PORTRAIT_LEFT:
            case TRIGGER_FULLSCREEN_DOUBLE:
                return "kb_volume_up";
            case TRIGGER_LANDSCAPE_BOTTOM:
            case TRIGGER_PORTRAIT_RIGHT:
            case TRIGGER_FULLSCREEN_SINGLE:
                return "kb_volume_down";
            case TRIGGER_SWIPE_UP:
                return "kb_volume_down";
            case TRIGGER_SWIPE_DOWN:
                return "kb_volume_up";
            case TRIGGER_SWIPE_LEFT:
                return "kb_volume_down";
            case TRIGGER_SWIPE_RIGHT:
                return "kb_volume_up";
            default:
                return "none";
        }
    }

    void ensureKeyboardConfigsInitialized() {
        if (preferences == null || preferences.contains(PREF_KEYBOARDS_JSON)) return;
        List<CustomKeyboardConfig> configs = new ArrayList<>();
        configs.add(hasLegacyKeyboardPreferences()
                ? createDefaultKeyboardFromLegacy()
                : createReaderPagingKeyboard());
        saveKeyboardConfigs(configs);
        preferences.edit().putInt(PREF_NEXT_KEYBOARD_ID, 2).apply();
    }

    private boolean hasLegacyKeyboardPreferences() {
        if (preferences == null) return false;
        if (preferences.contains(PREF_CONTROL_MODE) || preferences.contains(PREF_VIBRATION_ENABLED)) {
            return true;
        }
        for (String triggerId : GESTURE_TRIGGER_IDS) {
            if (preferences.contains(PREF_ACTION_PREFIX + triggerId)) return true;
        }
        return false;
    }

    List<CustomKeyboardConfig> loadKeyboardConfigs() {
        List<CustomKeyboardConfig> configs = new ArrayList<>();
        if (preferences == null) {
            configs.add(createDefaultKeyboardFromLegacy());
            return configs;
        }

        String raw = preferences.getString(PREF_KEYBOARDS_JSON, "");
        if (raw == null || raw.trim().isEmpty()) {
            configs.add(createDefaultKeyboardFromLegacy());
            saveKeyboardConfigs(configs);
            return configs;
        }

        boolean parseFailed = false;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                CustomKeyboardConfig config = parseKeyboardConfig(array.getJSONObject(i));
                if (config != null) configs.add(config);
            }
        } catch (Exception ignored) {
            configs.clear();
            parseFailed = true;
        }

        if (parseFailed) {
            configs.add(createDefaultKeyboardFromLegacy());
            saveKeyboardConfigs(configs);
        }
        return configs;
    }

    private void saveKeyboardConfigs(List<CustomKeyboardConfig> configs) {
        if (preferences == null) return;
        JSONArray array = new JSONArray();
        for (CustomKeyboardConfig config : configs) {
            array.put(toJson(config));
        }
        preferences.edit().putString(PREF_KEYBOARDS_JSON, array.toString()).apply();
    }

    private CustomKeyboardConfig parseKeyboardConfig(JSONObject object) {
        if (object == null) return null;
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = object.optString("id", "");
        config.name = object.optString("name", keyboardTypeLabel(KEYBOARD_TYPE_FULLSCREEN_GESTURE));
        config.type = object.optString("type", KEYBOARD_TYPE_FULLSCREEN_GESTURE);
        config.controlMode = object.optString("controlMode", CONTROL_MODE_FULLSCREEN);
        config.backgroundImageUri = object.optString("backgroundImageUri", "");
        config.vibrationEnabled = object.optBoolean("vibrationEnabled", true);
        config.vibrationLevel = object.optInt("vibrationLevel", VIBRATION_MEDIUM);

        JSONArray bindingsArray = object.optJSONArray("bindings");
        if (bindingsArray != null) {
            for (int i = 0; i < bindingsArray.length(); i++) {
                GestureBinding binding = parseGestureBinding(bindingsArray.optJSONObject(i));
                if (binding != null) config.bindings.add(binding);
            }
        } else {
            JSONObject actionsObject = object.optJSONObject("actions");
            if (actionsObject != null) {
                addLegacyBindings(config, actionsObject);
            }
        }
        normalizeKeyboardConfig(config);
        return config;
    }

    private JSONObject toJson(CustomKeyboardConfig config) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", config.id);
            object.put("name", config.name);
            object.put("type", config.type);
            object.put("controlMode", config.controlMode);
            object.put("backgroundImageUri", config.backgroundImageUri == null ? "" : config.backgroundImageUri);
            object.put("vibrationEnabled", config.vibrationEnabled);
            object.put("vibrationLevel", config.vibrationLevel);
            JSONArray bindingsArray = new JSONArray();
            for (GestureBinding binding : config.bindings) {
                bindingsArray.put(toJson(binding));
            }
            object.put("bindings", bindingsArray);
        } catch (Exception ignored) {
        }
        return object;
    }

    private GestureBinding parseGestureBinding(JSONObject object) {
        if (object == null) return null;
        GestureBinding binding = new GestureBinding();
        binding.kind = object.optString("kind", GESTURE_KIND_TAP);
        binding.fingers = object.optInt("fingers", 1);
        binding.tapCount = object.optInt("tapCount", 1);
        binding.area = object.optString("area", TAP_AREA_FULL);
        binding.motion = object.optString("motion", "");
        binding.actionId = object.optString("actionId", "none");
        normalizeGestureBinding(binding);
        return binding;
    }

    private JSONObject toJson(GestureBinding binding) {
        JSONObject object = new JSONObject();
        try {
            object.put("kind", binding.kind);
            object.put("fingers", binding.fingers);
            object.put("tapCount", binding.tapCount);
            object.put("area", binding.area);
            object.put("motion", binding.motion);
            object.put("actionId", binding.actionId);
        } catch (Exception ignored) {
        }
        return object;
    }

    void normalizeKeyboardConfig(CustomKeyboardConfig config) {
        if (config.id == null || config.id.trim().isEmpty()) config.id = allocateKeyboardId();
        if (config.name == null || config.name.trim().isEmpty()) config.name = keyboardTypeLabel(config.type);
        if (!isKnownKeyboardType(config.type)) config.type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
        if (!CONTROL_MODE_LANDSCAPE.equals(config.controlMode)
                && !CONTROL_MODE_PORTRAIT.equals(config.controlMode)
                && !CONTROL_MODE_FULLSCREEN.equals(config.controlMode)) {
            config.controlMode = CONTROL_MODE_FULLSCREEN;
        }
        if (config.backgroundImageUri == null) config.backgroundImageUri = "";
        if (config.vibrationLevel < VIBRATION_LIGHT || config.vibrationLevel > VIBRATION_STRONG) {
            config.vibrationLevel = VIBRATION_MEDIUM;
        }
        for (GestureBinding binding : config.bindings) {
            normalizeGestureBinding(binding);
        }
    }

    private void normalizeGestureBinding(GestureBinding binding) {
        if (!GESTURE_KIND_MOTION.equals(binding.kind)) binding.kind = GESTURE_KIND_TAP;
        if (binding.fingers < 1 || binding.fingers > 3) binding.fingers = 1;
        if (GESTURE_KIND_TAP.equals(binding.kind)) {
            if (binding.tapCount < 1 || binding.tapCount > 3) binding.tapCount = 1;
            if (!isKnownTapArea(binding.area)) binding.area = TAP_AREA_FULL;
            binding.motion = "";
        } else {
            binding.tapCount = 0;
            binding.area = TAP_AREA_FULL;
            if (!isKnownMotion(binding.motion)) binding.motion = MOTION_SWIPE_UP;
            if (binding.fingers > 1 && (MOTION_CIRCLE_CLOCKWISE.equals(binding.motion) || MOTION_CIRCLE_COUNTERCLOCKWISE.equals(binding.motion))) {
                binding.fingers = 1;
            }
        }
        if (binding.actionId == null || binding.actionId.trim().isEmpty()) binding.actionId = "none";
    }

    private void addLegacyBindings(CustomKeyboardConfig config, JSONObject actionsObject) {
        if (config == null || actionsObject == null) return;
        if (CONTROL_MODE_LANDSCAPE.equals(config.controlMode)) {
            addLegacyTapBinding(config, TRIGGER_LANDSCAPE_TOP, TAP_AREA_TOP, actionsObject);
            addLegacyTapBinding(config, TRIGGER_LANDSCAPE_BOTTOM, TAP_AREA_BOTTOM, actionsObject);
        } else if (CONTROL_MODE_PORTRAIT.equals(config.controlMode)) {
            addLegacyTapBinding(config, TRIGGER_PORTRAIT_LEFT, TAP_AREA_LEFT, actionsObject);
            addLegacyTapBinding(config, TRIGGER_PORTRAIT_RIGHT, TAP_AREA_RIGHT, actionsObject);
        } else {
            addLegacyTapBinding(config, TRIGGER_FULLSCREEN_SINGLE, TAP_AREA_FULL, actionsObject);
            addLegacyTapBinding(config, TRIGGER_FULLSCREEN_DOUBLE, TAP_AREA_FULL, actionsObject);
        }
        addLegacyMotionBinding(config, TRIGGER_SWIPE_UP, MOTION_SWIPE_UP, actionsObject);
        addLegacyMotionBinding(config, TRIGGER_SWIPE_DOWN, MOTION_SWIPE_DOWN, actionsObject);
        addLegacyMotionBinding(config, TRIGGER_SWIPE_LEFT, MOTION_SWIPE_LEFT, actionsObject);
        addLegacyMotionBinding(config, TRIGGER_SWIPE_RIGHT, MOTION_SWIPE_RIGHT, actionsObject);
    }

    private void addLegacyTapBinding(CustomKeyboardConfig config, String triggerId, String area, JSONObject actionsObject) {
        String actionId = actionsObject.optString(triggerId, getDefaultActionId(triggerId));
        if ("none".equals(actionId)) return;
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_TAP;
        binding.fingers = 1;
        binding.tapCount = TRIGGER_FULLSCREEN_DOUBLE.equals(triggerId) ? 2 : 1;
        binding.area = area;
        binding.motion = "";
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    private void addLegacyMotionBinding(CustomKeyboardConfig config, String triggerId, String motion, JSONObject actionsObject) {
        String actionId = actionsObject.optString(triggerId, getDefaultActionId(triggerId));
        if ("none".equals(actionId)) return;
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_MOTION;
        binding.fingers = 1;
        binding.tapCount = 0;
        binding.area = TAP_AREA_FULL;
        binding.motion = motion;
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    private boolean isKnownTapArea(String area) {
        for (String knownArea : TAP_AREAS) {
            if (knownArea.equals(area)) return true;
        }
        return false;
    }

    private boolean isKnownMotion(String motion) {
        for (String knownMotion : MOTION_GESTURES) {
            if (knownMotion.equals(motion)) return true;
        }
        return false;
    }

    CustomKeyboardConfig createDefaultKeyboardFromLegacy() {
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = "keyboard_1";
        config.name = keyboardTypeLabel(KEYBOARD_TYPE_FULLSCREEN_GESTURE);
        config.type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
        config.controlMode = preferences == null
                ? CONTROL_MODE_FULLSCREEN
                : preferences.getString(PREF_CONTROL_MODE, CONTROL_MODE_FULLSCREEN);
        config.vibrationEnabled = preferences == null || preferences.getBoolean(PREF_VIBRATION_ENABLED, true);
        config.vibrationLevel = VIBRATION_MEDIUM;
        config.backgroundImageUri = "";
        JSONObject legacyActions = new JSONObject();
        try {
            for (String triggerId : GESTURE_TRIGGER_IDS) {
                String actionId = preferences == null
                        ? getDefaultActionId(triggerId)
                        : preferences.getString(PREF_ACTION_PREFIX + triggerId, getDefaultActionId(triggerId));
                legacyActions.put(triggerId, actionId);
            }
        } catch (Exception ignored) {
        }
        addLegacyBindings(config, legacyActions);
        normalizeKeyboardConfig(config);
        return config;
    }

    private CustomKeyboardConfig createReaderPagingKeyboard() {
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = "keyboard_1";
        config.name = text(R.string.reader_keyboard, "阅读器翻页");
        config.type = KEYBOARD_TYPE_FULLSCREEN_GESTURE;
        config.controlMode = CONTROL_MODE_FULLSCREEN;
        config.vibrationEnabled = false;
        config.vibrationLevel = VIBRATION_MEDIUM;
        config.backgroundImageUri = "";

        addDefaultTapBinding(config, 1, 1, "kb_volume_down");
        addDefaultTapBinding(config, 1, 2, "kb_volume_up");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_UP, "kb_volume_down");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_DOWN, "kb_volume_up");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_LEFT, "kb_volume_down");
        addDefaultMotionBinding(config, 1, MOTION_SWIPE_RIGHT, "kb_volume_up");
        addDefaultMotionBinding(config, 2, MOTION_SWIPE_DOWN, "kb_power");
        addDefaultMotionBinding(config, 2, MOTION_SWIPE_UP, "kb_power");

        normalizeKeyboardConfig(config);
        return config;
    }

    private void addDefaultTapBinding(CustomKeyboardConfig config, int fingers, int tapCount, String actionId) {
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_TAP;
        binding.fingers = fingers;
        binding.tapCount = tapCount;
        binding.area = TAP_AREA_FULL;
        binding.motion = "";
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    private void addDefaultMotionBinding(CustomKeyboardConfig config, int fingers, String motion, String actionId) {
        GestureBinding binding = new GestureBinding();
        binding.kind = GESTURE_KIND_MOTION;
        binding.fingers = fingers;
        binding.tapCount = 0;
        binding.area = TAP_AREA_FULL;
        binding.motion = motion;
        binding.actionId = actionId;
        config.bindings.add(binding);
    }

    CustomKeyboardConfig createNewKeyboardDraft(String type) {
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = allocateKeyboardId();
        config.name = nextKeyboardName(type);
        config.type = type;
        config.controlMode = CONTROL_MODE_FULLSCREEN;
        config.vibrationEnabled = true;
        config.vibrationLevel = VIBRATION_MEDIUM;
        config.backgroundImageUri = "";
        normalizeKeyboardConfig(config);
        return config;
    }

    private String allocateKeyboardId() {
        int nextId = preferences == null ? 1 : preferences.getInt(PREF_NEXT_KEYBOARD_ID, 1);
        if (preferences != null) {
            preferences.edit().putInt(PREF_NEXT_KEYBOARD_ID, nextId + 1).apply();
        }
        return "keyboard_" + nextId;
    }

    private String nextKeyboardName(String type) {
        return keyboardTypeLabel(type) + " " + Math.max(1, loadKeyboardConfigs().size() + 1);
    }

    void upsertKeyboardConfig(CustomKeyboardConfig savedConfig) {
        normalizeKeyboardConfig(savedConfig);
        List<CustomKeyboardConfig> configs = loadKeyboardConfigs();
        boolean replaced = false;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).id.equals(savedConfig.id)) {
                configs.set(i, savedConfig.copy());
                replaced = true;
                break;
            }
        }
        if (!replaced) configs.add(savedConfig.copy());
        saveKeyboardConfigs(configs);
    }

    void deleteKeyboardConfig(String keyboardId) {
        List<CustomKeyboardConfig> configs = loadKeyboardConfigs();
        for (int i = configs.size() - 1; i >= 0; i--) {
            if (configs.get(i).id.equals(keyboardId)) configs.remove(i);
        }
        saveKeyboardConfigs(configs);
    }

    private String keyboardTypeLabel(String type) {
        if (KEYBOARD_TYPE_COMPUTER.equals(type)) return text(R.string.ui_computer_keyboard, "电脑键盘");
        return text(R.string.ui_gesture_keyboard, "全屏手势键盘");
    }

    private boolean isKnownKeyboardType(String type) {
        return KEYBOARD_TYPE_FULLSCREEN_GESTURE.equals(type)
                || KEYBOARD_TYPE_COMPUTER.equals(type);
    }

}
