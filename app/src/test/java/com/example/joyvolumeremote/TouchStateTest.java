package com.example.joyvolumeremote;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchStateTest {
    @Test public void proportionalMappingReachesCornersAndClampsOutsideTheSurface() {
        assertEquals(0, TouchState.coordinate(-10, 1080));
        assertEquals(0, TouchState.coordinate(0, 1080));
        assertEquals(32767, TouchState.coordinate(1079, 1080));
        assertEquals(32767, TouchState.coordinate(1200, 1080));
        assertEquals(16384, TouchState.coordinate(539.5f, 1080));
        assertEquals(0, TouchState.coordinate(5, 0));
        assertEquals(0, TouchState.coordinate(Float.NaN, 1080));
    }

    @Test public void aspectRatiosMapIndependentlyByWidthAndHeight() {
        TouchState state = new TouchState();
        state.press(42, 250, 1500, 1001, 2001);
        byte[] report = state.report();
        assertEquals(8192, coordinate(report, 0, false));
        assertEquals(24575, coordinate(report, 0, true));
    }

    @Test public void anotherPointerCannotMoveOrReplaceTheSingleContact() {
        TouchState state = new TouchState();
        assertTrue(state.press(2, 10, 10, 101, 101));
        byte[] single = state.report();
        assertFalse(state.press(9, 90, 90, 101, 101));
        state.move(9, 0, 0, 101, 101);
        state.lift(9);
        assertArrayEquals(single, state.report());
    }

    @Test public void aNewStrokeCanUseADifferentPointerIdAfterRelease() {
        TouchState state = new TouchState();
        state.press(8, 10, 20, 101, 101); state.lift(8); state.frameSent();
        assertTrue(state.press(12, 30, 40, 101, 101));
        assertEquals(1, TouchState.mask(state.report()));
        assertEquals(0, state.report()[1]);
        assertEquals(9830, coordinate(state.report(), 0, false));
        state.clear();
        assertEquals(0, TouchState.mask(state.report()));
        assertEquals(0, state.report()[12]);
    }

    @Test public void snapshotsAreIndependentAndPreserveFinalCoordinatesOnLift() {
        TouchState state = new TouchState();
        state.press(0, 50, 25, 101, 101);
        byte[] before = state.report();
        state.move(0, 100, 100, 101, 101); state.lift(0);
        assertEquals(16384, coordinate(before, 0, false));
        assertEquals(32767, coordinate(state.report(), 0, false));
        assertEquals(32767, coordinate(state.report(), 0, true));
        assertEquals(0, TouchState.mask(state.report()));
        assertThrows(IllegalArgumentException.class, () -> TouchState.mask(new byte[7]));
    }

    @Test public void onlyTheSingleContactAndItsLiftAreValid() {
        TouchState state = new TouchState();
        state.press(4, 20, 20, 101, 101);
        assertEquals(1, state.report()[12]); state.frameSent();
        state.lift(4);
        byte[] lift = state.report();
        assertEquals(1, lift[12]); assertEquals(0, lift[0]); assertEquals(0, TouchState.mask(lift));
        state.frameSent(); assertEquals(0, state.report()[12]);
        state.clear(); state.press(20, 50, 50, 101, 101);
        assertEquals(1, state.report()[12]); assertEquals(0, state.report()[1]);
        for (int i = 6; i < 12; i++) assertEquals("Reserved record stays empty", 0, state.report()[i]);
    }

    @Test public void wireValidationRejectsAdditionalActiveContacts() {
        byte[] report = TouchState.singleContact(true, 100, 100);
        report[6] = 3;
        assertThrows(IllegalArgumentException.class, () -> TouchState.mask(report));
        report[6] = 0; report[12] = 2;
        assertThrows(IllegalArgumentException.class, () -> TouchState.mask(report));
    }

    static int coordinate(byte[] report, int slot, boolean y) {
        int offset = slot * 6 + (y ? 4 : 2);
        return (report[offset] & 255) | ((report[offset + 1] & 255) << 8);
    }
}
