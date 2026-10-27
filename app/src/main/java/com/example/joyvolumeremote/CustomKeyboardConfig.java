package com.example.joyvolumeremote;

import java.util.ArrayList;
import java.util.List;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

final class CustomKeyboardConfig {
    String id;
    String name;
    String type;
    String controlMode;
    String backgroundImageUri;
    boolean vibrationEnabled;
    int vibrationLevel;
    boolean screenPinning;
    boolean safeTouchArea;
    String touchAspectRatio = TouchAspectRatio.DEFAULT;
    final List<GestureBinding> bindings = new ArrayList<>();

    CustomKeyboardConfig copy() {
        CustomKeyboardConfig copy = new CustomKeyboardConfig();
        copy.id = id;
        copy.name = name;
        copy.type = type;
        copy.controlMode = controlMode;
        copy.backgroundImageUri = backgroundImageUri;
        copy.vibrationEnabled = vibrationEnabled;
        copy.vibrationLevel = vibrationLevel;
        copy.screenPinning = screenPinning;
        copy.safeTouchArea = safeTouchArea;
        copy.touchAspectRatio = touchAspectRatio;
        for (GestureBinding binding : bindings) {
            copy.bindings.add(binding.copy());
        }
        return copy;
    }
}
