package com.example.joyvolumeremote;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchAspectRatioTest {
    @Test public void decimalRatiosRoundTripAndSwapWithoutLosingPrecision() {
        TouchAspectRatio ratio = TouchAspectRatio.parse(" 09.000:19.500 ");
        assertEquals("9:19.5", ratio.encode()); assertEquals("19.5:9", ratio.swapped().encode());
        assertEquals(9 / 19.5, ratio.value, .000001);
        assertEquals("3:4", TouchAspectRatio.orDefault(null).encode());
        assertEquals("3:4", TouchAspectRatio.orDefault("broken").encode());
    }

    @Test public void invalidAndExtremeDimensionsAreRejected() {
        for (String input : new String[]{"", "0:4", "-3:4", "NaN:4", "3:Infinity", "1:1000", "1001:1000",
                "3:4:5", "1e2:5", "3.1234:4", ".5:1", "1:0.01"}) {
            try { TouchAspectRatio.parse(input); fail(input); } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void everyPresetAndCustomRatioFitsTheLargestSafeArea() {
        for (String encoded : new String[]{"3:4", "2:3", "4:5", "5:8", "9:16", "9:18", "9:19.5", "9:20", "1:1", "4:3", "16:10", "16:9", "7.3:12.5", "1:10", "10:1"}) {
            TouchAspectRatio ratio = TouchAspectRatio.parse(encoded);
            for (int width : new int[]{320, 720, 1080, 2400}) for (int height : new int[]{640, 1280, 2400}) {
                TouchArea area = TouchArea.fit(width, height, 30, 50, true, ratio);
                assertTrue(area.width <= width); assertTrue(area.height <= height - 80);
                assertTrue(area.width == width || area.height == height - 80);
                assertEquals(width, area.left * 2 + area.width, 1);
                assertEquals(height - 80, (area.top - 30) * 2 + area.height, 1);
                assertTrue(Math.abs(area.width - area.height * ratio.value) <= Math.max(1, ratio.value / 2));
                assertTrue(area.contains(area.left, area.top));
                assertEquals(1f, area.entryFraction(area.left, area.top - 10, area.left, area.top), .0001f);
            }
        }
    }

    @Test public void phoneRatiosUseMoreOfAPhoneScreenAndDisabledAreaStaysFullScreen() {
        TouchArea reader = TouchArea.fit(1080, 2400, 100, 100, true);
        TouchArea phone = TouchArea.fit(1080, 2400, 100, 100, true, TouchAspectRatio.parse("9:20"));
        assertEquals(990, phone.width); assertEquals(2200, phone.height); assertTrue(phone.height > reader.height);
        TouchArea full = TouchArea.fit(1080, 2400, 100, 100, false, TouchAspectRatio.parse("9:20"));
        assertEquals(1080, full.width); assertEquals(2400, full.height);
    }
}
