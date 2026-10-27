package com.example.joyvolumeremote;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GestureRulesTest {
    @Test
    public void fullScreenContainsEveryPoint() {
        assertTrue(GestureRules.tapAreaContains(KeyboardDefinitions.TAP_AREA_FULL, 0f, 0f));
        assertTrue(GestureRules.tapAreaContains(KeyboardDefinitions.TAP_AREA_FULL, 1f, 1f));
    }

    @Test
    public void disjointHalfScreensDoNotOverlap() {
        assertFalse(GestureRules.tapRegionsOverlap(
                KeyboardDefinitions.TAP_AREA_LEFT,
                KeyboardDefinitions.TAP_AREA_RIGHT
        ));
        assertFalse(GestureRules.tapRegionsOverlap(
                KeyboardDefinitions.TAP_AREA_TOP,
                KeyboardDefinitions.TAP_AREA_BOTTOM
        ));
    }

    @Test public void editingIgnoresItselfButStillRejectsOtherOverlappingTaps() {
        GestureBinding original = tap("left", 1), other = tap("right", 1);
        java.util.List<GestureBinding> bindings = java.util.Arrays.asList(original, other);
        assertFalse(GestureRules.hasBindingConflict(bindings, original.copy(), original));
        assertTrue(GestureRules.hasBindingConflict(bindings, tap("full", 1), original));
        assertTrue(GestureRules.hasBindingConflict(bindings, tap("right", 1), original));
        assertFalse(GestureRules.hasBindingConflict(bindings, tap("full", 2), original));
        assertTrue(GestureRules.hasBindingConflict(bindings, original.copy(), null));
    }

    @Test public void motionConflictsMatchFingerCountAndDirection() {
        GestureBinding original = motion(1, "swipe_up"), other = motion(2, "swipe_down");
        java.util.List<GestureBinding> bindings = java.util.Arrays.asList(original, other);
        assertFalse(GestureRules.hasBindingConflict(bindings, original.copy(), original));
        assertTrue(GestureRules.hasBindingConflict(bindings, other.copy(), original));
        assertFalse(GestureRules.hasBindingConflict(bindings, motion(1, "swipe_down"), original));
        assertFalse(GestureRules.hasBindingConflict(bindings, tap("full", 1), original));
    }

    private GestureBinding tap(String area, int count) {
        GestureBinding binding = new GestureBinding(); binding.kind = KeyboardDefinitions.GESTURE_KIND_TAP;
        binding.fingers = 1; binding.tapCount = count; binding.area = area; return binding;
    }
    private GestureBinding motion(int fingers, String direction) {
        GestureBinding binding = new GestureBinding(); binding.kind = KeyboardDefinitions.GESTURE_KIND_MOTION;
        binding.fingers = fingers; binding.motion = direction; return binding;
    }
}
