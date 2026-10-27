package com.example.joyvolumeremote;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchAreaTest {
    @Test public void portraitUsesFullWidthWithoutArbitraryMargins() {
        TouchArea area = TouchArea.fit(1080, 2400, 100, 100, true);
        assertEquals(0, area.left); assertEquals(1080, area.width);
        assertEquals(1440, area.height); assertEquals(480, area.top);
        assertTrue(area.contains(0, 480)); assertTrue(area.contains(1079, 1919));
        assertFalse(area.contains(500, 400));
    }
    @Test public void heightLimitedAreaUsesAllAvailableSafeHeight() {
        TouchArea area = TouchArea.fit(2400, 1080, 20, 60, true);
        assertEquals(750, area.width); assertEquals(1000, area.height);
        assertEquals(825, area.left); assertEquals(20, area.top);
    }
    @Test public void aspectRatioAndContainmentHoldAcrossSmallAndLargeWindows() {
        for (int w : new int[]{100, 320, 720, 1080, 1600, 2400}) for (int h : new int[]{200, 640, 1280, 2400}) {
            TouchArea area = TouchArea.fit(w, h, 20, 20, true);
            assertTrue(area.width <= w); assertTrue(area.height <= h - 40);
            assertTrue(Math.abs(area.width * 4 - area.height * 3) <= 3);
            assertTrue(area.width == w || area.height >= h - 42);
        }
    }
    @Test public void disabledAreaRetainsTheEntireSurface() {
        TouchArea area = TouchArea.fit(1080, 2400, 100, 100, false);
        assertEquals(0, area.left); assertEquals(0, area.top);
        assertEquals(1080, area.width); assertEquals(2400, area.height);
    }

    @Test public void outsidePathsBeginAtTheExactTopOrBottomBoundary() {
        TouchArea area = TouchArea.fit(1080, 2400, 100, 100, true);
        assertEquals(.5f, area.entryFraction(300, 400, 300, 560), .00001f);
        assertEquals(.5f, area.entryFraction(300, 1999, 300, 1839), .00001f);
        assertEquals(0f, area.entryFraction(300, 480, 300, 560), 0f);
        assertTrue(Float.isNaN(area.entryFraction(300, 400, 300, 450)));
    }

    @Test public void diagonalAndSparsePathsUseTheirFirstIntersection() {
        TouchArea area = TouchArea.fit(2400, 1080, 20, 60, true);
        assertEquals(.5f, area.entryFraction(800, 10, 850, 30), .00001f);
        float entry = area.entryFraction(1000, 0, 1000, 1079);
        assertEquals(20f, 1079 * entry, .0001f);
        assertTrue(Float.isNaN(area.entryFraction(800, 10, 800, 900)));
        assertTrue(Float.isNaN(area.entryFraction(800, 10, 790, 0)));
    }
}
