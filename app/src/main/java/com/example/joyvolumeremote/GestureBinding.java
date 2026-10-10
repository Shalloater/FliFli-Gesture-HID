package com.example.joyvolumeremote;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

final class GestureBinding {
    String kind;
    int fingers;
    int tapCount;
    String area;
    String motion;
    String actionId;

    GestureBinding copy() {
        GestureBinding copy = new GestureBinding();
        copy.kind = kind;
        copy.fingers = fingers;
        copy.tapCount = tapCount;
        copy.area = area;
        copy.motion = motion;
        copy.actionId = actionId;
        return copy;
    }

    String key() {
        if (GESTURE_KIND_TAP.equals(kind)) {
            return tapKey(fingers, tapCount, area);
        }
        return motionKey(fingers, motion);
    }

    static String tapKey(int fingers, int tapCount, String area) {
        return GESTURE_KIND_TAP + ":" + fingers + ":" + tapCount + ":" + area;
    }

    static String motionKey(int fingers, String motion) {
        return GESTURE_KIND_MOTION + ":" + fingers + ":" + motion;
    }
}
