package com.example.joyvolumeremote;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

final class KeyboardDefinitions {
    private KeyboardDefinitions() {}
    static final String CONTROL_MODE_LANDSCAPE = "landscape";
    static final String CONTROL_MODE_PORTRAIT = "portrait";
    static final String CONTROL_MODE_FULLSCREEN = "fullscreen";
    static final String KEYBOARD_TYPE_FULLSCREEN_GESTURE = "fullscreen_gesture";
    static final String KEYBOARD_TYPE_COMPUTER = "computer_keyboard";
    static final String GESTURE_KIND_TAP = "tap";
    static final String GESTURE_KIND_MOTION = "motion";
    static final String TAP_AREA_FULL = "full";
    static final String TAP_AREA_LEFT = "left";
    static final String TAP_AREA_RIGHT = "right";
    static final String TAP_AREA_TOP = "top";
    static final String TAP_AREA_BOTTOM = "bottom";
    static final String MOTION_SWIPE_UP = "swipe_up";
    static final String MOTION_SWIPE_DOWN = "swipe_down";
    static final String MOTION_SWIPE_LEFT = "swipe_left";
    static final String MOTION_SWIPE_RIGHT = "swipe_right";
    static final String MOTION_CIRCLE_CLOCKWISE = "circle_clockwise";
    static final String MOTION_CIRCLE_COUNTERCLOCKWISE = "circle_counterclockwise";
    static final int VIBRATION_LIGHT = 0;
    static final int VIBRATION_MEDIUM = 1;
    static final int VIBRATION_STRONG = 2;
    static final String TRIGGER_LANDSCAPE_TOP = "landscape_top";
    static final String TRIGGER_LANDSCAPE_BOTTOM = "landscape_bottom";
    static final String TRIGGER_PORTRAIT_LEFT = "portrait_left";
    static final String TRIGGER_PORTRAIT_RIGHT = "portrait_right";
    static final String TRIGGER_FULLSCREEN_SINGLE = "fullscreen_single";
    static final String TRIGGER_FULLSCREEN_DOUBLE = "fullscreen_double";
    static final String TRIGGER_SWIPE_UP = "swipe_up";
    static final String TRIGGER_SWIPE_DOWN = "swipe_down";
    static final String TRIGGER_SWIPE_LEFT = "swipe_left";
    static final String TRIGGER_SWIPE_RIGHT = "swipe_right";
    static final String[] GESTURE_TRIGGER_IDS = {
            TRIGGER_FULLSCREEN_SINGLE,
            TRIGGER_FULLSCREEN_DOUBLE,
            TRIGGER_SWIPE_UP,
            TRIGGER_SWIPE_DOWN,
            TRIGGER_SWIPE_LEFT,
            TRIGGER_SWIPE_RIGHT,
            TRIGGER_LANDSCAPE_TOP,
            TRIGGER_LANDSCAPE_BOTTOM,
            TRIGGER_PORTRAIT_LEFT,
            TRIGGER_PORTRAIT_RIGHT
    };
    static final String[] TAP_AREAS = {
            TAP_AREA_FULL,
            TAP_AREA_LEFT,
            TAP_AREA_RIGHT,
            TAP_AREA_TOP,
            TAP_AREA_BOTTOM
    };
    static final String[] MOTION_GESTURES = {
            MOTION_SWIPE_UP,
            MOTION_SWIPE_DOWN,
            MOTION_SWIPE_LEFT,
            MOTION_SWIPE_RIGHT,
            MOTION_CIRCLE_CLOCKWISE,
            MOTION_CIRCLE_COUNTERCLOCKWISE
    };
}
