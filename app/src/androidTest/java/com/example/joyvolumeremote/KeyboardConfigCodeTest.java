package com.example.joyvolumeremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import static com.example.joyvolumeremote.KeyboardDefinitions.*;

public class KeyboardConfigCodeTest extends InstrumentationTestCase {
    private SharedPreferences preferences;
    private KeyboardConfigStore store;
    @Override protected void setUp() throws Exception {
        super.setUp(); preferences = getInstrumentation().getTargetContext().getSharedPreferences("config_code_qa", Context.MODE_PRIVATE);
        preferences.edit().clear().commit(); store = new KeyboardConfigStore(preferences); store.ensureKeyboardConfigsInitialized();
    }
    @Override protected void tearDown() throws Exception { preferences.edit().clear().commit(); super.tearDown(); }

    public void testPortableRoundTripPreservesAllSettingsAndMixedSignalSequence() throws Exception {
        CustomKeyboardConfig original = sample(KEYBOARD_TYPE_FULLSCREEN_GESTURE);
        GestureBinding binding = new GestureBinding(); binding.kind = GESTURE_KIND_TAP; binding.fingers = 2;
        binding.tapCount = 2; binding.area = TAP_AREA_RIGHT; binding.motion = "";
        binding.actionId = new SignalProgram(SignalProgram.Mode.SEQUENCE,
                Arrays.asList(Arrays.asList("kb_ctrl", "kb_c"), Arrays.asList("touch_top_right_down")), 250).encode();
        original.bindings.add(binding);
        String code = KeyboardConfigCode.encode(original);
        KeyboardConfigCode.Decoded decoded = KeyboardConfigCode.decode("\n" + code.substring(0, 25) + "\n" + code.substring(25) + "\t");
        assertTrue(decoded.backgroundOmitted); assertEquals("", decoded.config.id); assertEquals("", decoded.config.backgroundImageUri);
        assertEquals(original.name, decoded.config.name); assertEquals(original.controlMode, decoded.config.controlMode);
        assertEquals("9:19.5", decoded.config.touchAspectRatio); assertTrue(decoded.config.screenPinning);
        assertTrue(decoded.config.safeTouchArea); assertFalse(decoded.config.vibrationEnabled); assertEquals(VIBRATION_STRONG, decoded.config.vibrationLevel);
        assertEquals(binding.key(), decoded.config.bindings.get(0).key()); assertEquals(binding.actionId, decoded.config.bindings.get(0).actionId);
    }

    public void testImportAddsFreshIdEvenWithStaleCounterAndKeepsExistingKeyboard() throws Exception {
        String existing = preferences.getString("custom_keyboards_json", "");
        preferences.edit().putInt("next_keyboard_id", 1).commit();
        CustomKeyboardConfig decoded = KeyboardConfigCode.decode(KeyboardConfigCode.encode(sample(KEYBOARD_TYPE_TOUCH_PASSTHROUGH))).config;
        assertEquals(existing, preferences.getString("custom_keyboards_json", ""));
        CustomKeyboardConfig imported = store.importKeyboardConfig(decoded);
        assertFalse("keyboard_1".equals(imported.id)); assertEquals(2, store.loadKeyboardConfigs().size());
        assertEquals("keyboard_1", store.loadKeyboardConfigs().get(0).id);
        assertEquals(imported.id, store.loadKeyboardConfigs().get(1).id);
        assertEquals("9:19.5", store.loadKeyboardConfigs().get(1).touchAspectRatio);
        assertEquals("", store.loadKeyboardConfigs().get(1).backgroundImageUri);
    }

    public void testLegacyConfigurationsDefaultToThreeByFourAndCopiesKeepCustomRatio() throws Exception {
        JSONObject object = KeyboardConfigStore.toJson(sample(KEYBOARD_TYPE_TOUCH_PASSTHROUGH));
        object.remove("touchAspectRatio"); preferences.edit().putString("custom_keyboards_json", new JSONArray().put(object).toString()).commit();
        CustomKeyboardConfig legacy = store.loadKeyboardConfigs().get(0); assertEquals("3:4", legacy.touchAspectRatio);
        legacy.touchAspectRatio = "7.3:12.5"; store.upsertKeyboardConfig(legacy.copy());
        assertEquals("7.3:12.5", store.loadKeyboardConfigs().get(0).touchAspectRatio);
    }

    public void testMalformedUnsupportedAndOversizedCodesFailWithoutWritingPreferences() throws Exception {
        String before = preferences.getString("custom_keyboards_json", "");
        String code = KeyboardConfigCode.encode(sample(KEYBOARD_TYPE_COMPUTER));
        rejects(null); rejects(""); rejects(code.replace("FLIFLI:1:", "FLIFLI:2:")); rejects(code.substring(0, code.length() - 12));
        rejects(code + "!"); rejects(new String(new char[KeyboardConfigCode.MAX_CODE_CHARS + 1]).replace('\0', 'x'));
        JSONObject payload = payload(); payload.put("schema", 2); rejects(pack(payload.toString()));
        payload = payload(); payload.put("padding", new String(new char[270000]).replace('\0', 'x')); rejects(pack(payload.toString()));
        assertEquals(before, preferences.getString("custom_keyboards_json", ""));
    }

    public void testUnknownSignalsInvalidRatiosAndConflictingBindingsAreRejected() throws Exception {
        JSONObject payload = payload(); JSONObject keyboard = payload.getJSONObject("keyboard");
        keyboard.put("touchAspectRatio", "1:0"); rejects(pack(payload.toString()));
        keyboard.put("touchAspectRatio", "3:4"); keyboard.put("vibrationLevel", 1.5); rejects(pack(payload.toString()));
        keyboard.put("vibrationLevel", 1); keyboard.put("safeTouchArea", "true"); rejects(pack(payload.toString()));
        keyboard.put("safeTouchArea", true); keyboard.put("type", "unknown"); rejects(pack(payload.toString()));
        keyboard.put("type", KEYBOARD_TYPE_FULLSCREEN_GESTURE);
        JSONObject binding = new JSONObject().put("kind", "tap").put("fingers", 1).put("tapCount", 1)
                .put("area", "left").put("motion", "").put("actionId", "unknown");
        keyboard.put("bindings", new JSONArray().put(binding)); rejects(pack(payload.toString()));
        binding.put("actionId", "kb_a"); JSONObject overlap = new JSONObject(binding.toString()).put("area", "top");
        keyboard.put("bindings", new JSONArray().put(binding).put(overlap)); rejects(pack(payload.toString()));
    }

    private CustomKeyboardConfig sample(String type) {
        CustomKeyboardConfig config = store.createNewKeyboardDraft(type); config.name = "Reader 配置";
        config.controlMode = CONTROL_MODE_LANDSCAPE; config.backgroundImageUri = "content://private/device-background";
        config.vibrationEnabled = false; config.vibrationLevel = VIBRATION_STRONG; config.screenPinning = true;
        config.safeTouchArea = true; config.touchAspectRatio = "9:19.5"; return config;
    }
    private JSONObject payload() throws Exception {
        return new JSONObject().put("schema", 1).put("backgroundOmitted", false)
                .put("keyboard", KeyboardConfigStore.toJson(sample(KEYBOARD_TYPE_TOUCH_PASSTHROUGH)));
    }
    private String pack(String value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(value.getBytes(StandardCharsets.UTF_8)); }
        return KeyboardConfigCode.PREFIX + Base64.encodeToString(bytes.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
    private void rejects(String value) {
        try { KeyboardConfigCode.decode(value); fail("Invalid code accepted"); } catch (IllegalArgumentException expected) { }
    }
}
