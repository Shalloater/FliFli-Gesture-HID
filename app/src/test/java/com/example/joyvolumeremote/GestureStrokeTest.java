package com.example.joyvolumeremote;

import org.junit.Test;
import static org.junit.Assert.*;

public class GestureStrokeTest {
    @Test public void staggeredFingersAndEitherLiftOrderDoNotCreateMotion() {
        for (boolean firstLast : new boolean[]{true, false}) {
            GestureStroke stroke = new GestureStroke();
            stroke.press(17, 100, 500); stroke.press(4, 900, 500);
            stroke.lift(firstLast ? 4 : 17); stroke.lift(firstLast ? 17 : 4);
            assertTrue(stroke.consistent()); assertEquals(2, stroke.fingers());
            assertEquals(500, stroke.startX(), 0); assertEquals(500, stroke.x(), 0);
            assertEquals(0, stroke.maxMovement(), 0);
        }
    }
    @Test public void parallelMotionKeepsItsDirectionWhenPointersChangeOrderOrLift() {
        GestureStroke stroke = new GestureStroke();
        stroke.press(4, 100, 800); stroke.press(9, 900, 800);
        stroke.move(9, 900, 400); stroke.move(4, 100, 400);
        stroke.lift(4); stroke.lift(9);
        assertEquals(0, stroke.x() - stroke.startX(), 0);
        assertEquals(-400, stroke.y() - stroke.startY(), 0);
        assertEquals(400, stroke.maxMovement(), 0);
    }
    @Test public void movementIsRememberedWhenAFingerReturnsToItsOrigin() {
        GestureStroke stroke = new GestureStroke(); stroke.press(8, 100, 100);
        stroke.move(8, 200, 100); stroke.move(8, 100, 100);
        assertEquals(100, stroke.maxMovement(), 0);
        assertEquals(stroke.startX(), stroke.x(), 0);
    }
    @Test public void threeFingerCenterRetainsReleasedContacts() {
        GestureStroke stroke = new GestureStroke();
        stroke.press(7, 100, 200); stroke.press(2, 700, 200); stroke.press(19, 700, 500);
        stroke.lift(7); stroke.lift(2); stroke.lift(19);
        assertTrue(stroke.consistent()); assertEquals(3, stroke.fingers());
        assertEquals(500, stroke.x(), 0); assertEquals(300, stroke.y(), 0);
    }
    @Test public void replacementAndExcessFingersCannotBecomeOrdinaryTaps() {
        GestureStroke stroke = new GestureStroke(); stroke.press(1, 10, 10); stroke.press(2, 20, 20);
        stroke.lift(1); stroke.press(3, 30, 30);
        assertFalse(stroke.consistent());
        stroke.clear();
        for (int id = 0; id < 4; id++) stroke.press(id, id * 10, id * 20);
        assertFalse(stroke.consistent());
        stroke.clear(); stroke.press(90, 10, 20);
        assertTrue(stroke.consistent()); assertEquals(1, stroke.fingers());
    }
}
