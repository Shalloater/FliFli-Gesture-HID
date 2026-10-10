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
        for (GestureBinding binding : bindings) {
            copy.bindings.add(binding.copy());
        }
        return copy;
    }
}
