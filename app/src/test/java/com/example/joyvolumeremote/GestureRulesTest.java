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
}
