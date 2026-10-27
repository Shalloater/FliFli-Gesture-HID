package com.example.joyvolumeremote;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import static com.example.joyvolumeremote.KeyboardDefinitions.*;

/** Versioned offline configuration, with bounded decompression and strict import validation. */
final class KeyboardConfigCode {
    static final String PREFIX = "FLIFLI:1:";
    static final int MAX_CODE_CHARS = 96 * 1024;
    private static final int MAX_JSON_BYTES = 256 * 1024;
    private static final int MAX_BINDINGS = 128;

    static final class Decoded {
        final CustomKeyboardConfig config;
        final boolean backgroundOmitted;
        Decoded(CustomKeyboardConfig config, boolean backgroundOmitted) {
            this.config = config; this.backgroundOmitted = backgroundOmitted;
        }
    }

    static String encode(CustomKeyboardConfig source) {
        try {
            JSONObject config = KeyboardConfigStore.toJson(source);
            config.remove("id"); config.remove("backgroundImageUri");
            config.put("touchAspectRatio", TouchAspectRatio.orDefault(source.touchAspectRatio).encode());
            readConfig(config);
            JSONObject payload = new JSONObject();
            payload.put("schema", 1); payload.put("keyboard", config);
            payload.put("backgroundOmitted", source.backgroundImageUri != null && !source.backgroundImageUri.isEmpty());
            byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
            if (json.length > MAX_JSON_BYTES) throw new IllegalArgumentException("Oversized configuration");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(json); }
            String code = PREFIX + Base64.encodeToString(bytes.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (code.length() > MAX_CODE_CHARS) throw new IllegalArgumentException("Oversized code");
            return code;
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid keyboard configuration", invalid); }
    }

    static Decoded decode(String input) {
        try {
            if (input == null || input.length() > MAX_CODE_CHARS) throw new IllegalArgumentException("Oversized code");
            String code = input.replaceAll("\\s", "");
            if (!code.startsWith(PREFIX)) throw new IllegalArgumentException("Unsupported code version");
            String encoded = code.substring(PREFIX.length());
            if (encoded.isEmpty() || !encoded.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid encoding");
            byte[] compressed = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = gzip.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_JSON_BYTES) throw new IllegalArgumentException("Oversized payload");
                    bytes.write(buffer, 0, count);
                }
            }
            String json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            org.json.JSONTokener tokener = new org.json.JSONTokener(json);
            Object value = tokener.nextValue();
            if (!(value instanceof JSONObject) || tokener.nextClean() != 0) throw new IllegalArgumentException("Invalid JSON");
            JSONObject payload = (JSONObject) value;
            if (integer(payload, "schema") != 1) throw new IllegalArgumentException("Unsupported schema");
            return new Decoded(readConfig(payload.getJSONObject("keyboard")), bool(payload, "backgroundOmitted"));
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid configuration code", invalid); }
    }

    private static CustomKeyboardConfig readConfig(JSONObject object) throws Exception {
        CustomKeyboardConfig config = new CustomKeyboardConfig();
        config.id = ""; config.backgroundImageUri = "";
        config.name = string(object, "name").trim();
        if (config.name.isEmpty() || config.name.length() > 160 || config.name.matches("(?s).*\\p{Cntrl}.*"))
            throw new IllegalArgumentException("Invalid name");
        config.type = string(object, "type");
        if (!Arrays.asList(KEYBOARD_TYPE_FULLSCREEN_GESTURE, KEYBOARD_TYPE_COMPUTER, KEYBOARD_TYPE_TOUCH_PASSTHROUGH).contains(config.type))
            throw new IllegalArgumentException("Unknown keyboard type");
        config.controlMode = string(object, "controlMode");
        if (!Arrays.asList(CONTROL_MODE_FULLSCREEN, CONTROL_MODE_PORTRAIT, CONTROL_MODE_LANDSCAPE).contains(config.controlMode))
            throw new IllegalArgumentException("Unknown orientation");
        config.vibrationEnabled = bool(object, "vibrationEnabled");
        config.vibrationLevel = integer(object, "vibrationLevel");
        if (config.vibrationLevel < VIBRATION_LIGHT || config.vibrationLevel > VIBRATION_STRONG)
            throw new IllegalArgumentException("Invalid vibration level");
        config.screenPinning = bool(object, "screenPinning");
        config.safeTouchArea = bool(object, "safeTouchArea");
        config.touchAspectRatio = TouchAspectRatio.parse(string(object, "touchAspectRatio")).encode();
        JSONArray bindings = object.getJSONArray("bindings");
        if (bindings.length() > MAX_BINDINGS) throw new IllegalArgumentException("Too many bindings");
        for (int i = 0; i < bindings.length(); i++) {
            JSONObject entry = bindings.getJSONObject(i);
            GestureBinding binding = new GestureBinding();
            binding.kind = string(entry, "kind"); binding.fingers = integer(entry, "fingers");
            binding.tapCount = integer(entry, "tapCount"); binding.area = string(entry, "area");
            binding.motion = string(entry, "motion"); binding.actionId = string(entry, "actionId");
            if (binding.fingers < 1 || binding.fingers > 3) throw new IllegalArgumentException("Invalid fingers");
            if (GESTURE_KIND_TAP.equals(binding.kind)) {
                if (binding.tapCount < 1 || binding.tapCount > 3 || !Arrays.asList(TAP_AREAS).contains(binding.area)
                        || !binding.motion.isEmpty()) throw new IllegalArgumentException("Invalid tap");
            } else if (GESTURE_KIND_MOTION.equals(binding.kind)) {
                if (binding.tapCount != 0 || !TAP_AREA_FULL.equals(binding.area) || !Arrays.asList(MOTION_GESTURES).contains(binding.motion)
                        || (binding.fingers != 1 && (MOTION_CIRCLE_CLOCKWISE.equals(binding.motion) || MOTION_CIRCLE_COUNTERCLOCKWISE.equals(binding.motion))))
                    throw new IllegalArgumentException("Invalid motion");
            } else throw new IllegalArgumentException("Unknown gesture");
            if (binding.actionId.length() > 8192) throw new IllegalArgumentException("Oversized signal");
            SignalProgram.parse(binding.actionId);
            if (GestureRules.hasBindingConflict(config.bindings, binding, null)) throw new IllegalArgumentException("Conflicting gestures");
            config.bindings.add(binding);
        }
        return config;
    }

    private static String string(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException("Expected string");
        return (String) value;
    }
    private static boolean bool(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Expected boolean");
        return (Boolean) value;
    }
    private static int integer(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue())
            throw new IllegalArgumentException("Expected integer");
        return ((Number) value).intValue();
    }
}
