package com.example.joyvolumeremote;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchReportQueueTest {
    @Test public void rapidMovesCoalesceWithoutReplacingPressOrLift() {
        TouchReportQueue queue = new TouchReportQueue();
        TouchState state = new TouchState();
        state.press(9, 0, 0, 101, 101);
        assertTrue(queue.offer(state.report()));
        for (int i = 1; i <= 100; i++) {
            state.move(9, i, i, 101, 101);
            assertTrue(queue.offer(state.report()));
        }
        state.lift(9); assertTrue(queue.offer(state.report()));
        assertEquals(0, TouchStateTest.coordinate(queue.poll(), 0, false));
        byte[] lastMove = queue.poll();
        assertEquals(1, TouchState.mask(lastMove));
        assertEquals(32767, TouchStateTest.coordinate(lastMove, 0, false));
        assertEquals(0, TouchState.mask(queue.poll()));
        assertEquals("All-up marker closes the gesture", 0, queue.poll()[12]);
        assertNull(queue.poll());
    }

    @Test public void repeatedTapsRetainTheirPressLiftAndEndOrder() {
        TouchReportQueue queue = new TouchReportQueue();
        TouchState state = new TouchState();
        state.press(0, 10, 10, 101, 101); queue.offer(state.report());
        state.lift(0); queue.offer(state.report());
        state.press(2, 50, 50, 101, 101); queue.offer(state.report());
        state.lift(2); queue.offer(state.report());
        for (int mask : new int[]{1, 0, 0, 1, 0, 0}) assertEquals(mask, TouchState.mask(queue.poll()));
        assertNull(queue.poll());
    }

    @Test public void overflowRejectsANewFrameWithoutErasingPreviousTransitions() {
        TouchReportQueue queue = new TouchReportQueue();
        TouchState state = new TouchState();
        int cycles = (TouchReportQueue.CAPACITY - 1) / 3;
        for (int i = 0; i < cycles * 2; i++) {
            if (i % 2 == 0) state.press(0, 20, 20, 101, 101); else state.lift(0);
            assertTrue(queue.offer(state.report()));
        }
        state.press(0, 20, 20, 101, 101); assertTrue(queue.offer(state.report()));
        state.lift(0); assertFalse(queue.offer(state.report()));
        for (int i = 0; i < cycles; i++) for (int mask : new int[]{1, 0, 0}) assertEquals(mask, TouchState.mask(queue.poll()));
        assertEquals(1, TouchState.mask(queue.poll()));
        assertNull(queue.poll());
        queue.clear();
        state.press(0, 20, 20, 101, 101);
        assertTrue(queue.offer(state.report())); assertEquals(1, TouchState.mask(queue.poll()));
    }

    @Test public void queuedBuffersAreCopiedAndClearDropsThePreviousSession() {
        TouchReportQueue queue = new TouchReportQueue();
        TouchState state = new TouchState(); state.press(4, 20, 20, 101, 101);
        byte[] report = state.report(); queue.offer(report); report[0] = 0;
        assertEquals(1, TouchState.mask(queue.poll()));
        state.move(4, 30, 30, 101, 101); queue.offer(state.report());
        queue.clear();
        assertNull(queue.poll());
        assertTrue(queue.offer(state.report()));
        assertEquals(1, TouchState.mask(queue.poll()));
    }
}
