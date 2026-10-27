package com.example.joyvolumeremote;

import org.junit.Test;
import static org.junit.Assert.*;

public class BleHidReportsTest {
    @Test public void pageDownAndModifierUseEightBytePayloadWithoutReportId() {
        assertArrayEquals(new byte[]{2, 0, 0x4e, 0, 0, 0, 0, 0}, BleHidReports.keyboard(2, new int[]{0x4e}));
        assertArrayEquals(new byte[8], BleHidReports.keyboard(0, null));
    }
    @Test public void simultaneousKeysAreBoundedToSixSlots() {
        assertArrayEquals(new byte[]{0, 0, 4, 5, 6, 7, 8, 9}, BleHidReports.keyboard(0, new int[]{4,5,6,7,8,9,10}));
    }
    @Test public void mediaUsageIsLittleEndianAndReleaseIsZero() {
        assertArrayEquals(new byte[]{(byte) 0xcd, 0}, BleHidReports.consumer(0xcd));
        assertArrayEquals(new byte[]{(byte) 0xff, 3}, BleHidReports.consumer(0x3ff));
        assertArrayEquals(new byte[2], BleHidReports.consumer(0));
    }
    @Test public void longReportMapSupportsSequentialAttReadsAndRejectsInvalidOffsets() {
        byte[] map = BleHidReports.REPORT_MAP;
        assertTrue(map.length > 22);
        assertArrayEquals(java.util.Arrays.copyOfRange(map, 22, map.length), BleHidReports.atOffset(map, 22));
        assertArrayEquals(new byte[0], BleHidReports.atOffset(map, map.length));
        assertNull(BleHidReports.atOffset(map, map.length + 1));
        assertNull(BleHidReports.atOffset(map, -1));
    }
}
