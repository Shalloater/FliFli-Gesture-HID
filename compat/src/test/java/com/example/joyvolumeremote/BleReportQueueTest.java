package com.example.joyvolumeremote;

import org.junit.Test;
import static org.junit.Assert.*;

public class BleReportQueueTest {
    @Test public void programsAreAdmittedAtomicallyAndCompleteOnlyAfterTheLastRelease() {
        BleReportQueue queue = new BleReportQueue();
        java.util.List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.parse("signal:1:SEQUENCE:250:kb_ctrl,kb_c;kb_ctrl,kb_v"));
        assertTrue(queue.addProgram(frames, "copy paste"));
        for (int i = 0; i < 3; i++) assertNull(queue.acknowledge().completedAction);
        assertEquals("copy paste", queue.acknowledge().completedAction);
        assertNull(queue.peek());
        for (int i = 0; i < BleReportQueue.CAPACITY - 3; i++) assertTrue(queue.addState(1, new byte[8]));
        assertFalse(queue.addProgram(frames, "too large"));
        int count = 0;
        while (queue.acknowledge() != null) count++;
        assertEquals(BleReportQueue.CAPACITY - 3, count);
    }
    @Test public void rapidGesturesKeepPressReleasePairsInOrderUntilAcknowledged() {
        BleReportQueue queue = new BleReportQueue();
        byte[] down = BleHidReports.keyboard(0, new int[]{0x4e});
        byte[] up = BleHidReports.keyboard(0, new int[]{0x4b});
        assertTrue(queue.addProgram(SignalReports.compile(SignalProgram.single("kb_page_down")), "down"));
        assertTrue(queue.addProgram(SignalReports.compile(SignalProgram.single("kb_page_up")), "up"));
        assertSame(queue.peek(), queue.peek());
        assertArrayEquals(down, queue.peek().value);
        assertEquals(35, queue.acknowledge().holdMs);
        assertArrayEquals(new byte[8], queue.peek().value);
        assertEquals("down", queue.acknowledge().completedAction);
        assertArrayEquals(up, queue.acknowledge().value);
        assertEquals("up", queue.acknowledge().completedAction);
        assertNull(queue.peek());
    }
    @Test public void insufficientCapacityNeverAdmitsAPressWithoutItsRelease() {
        BleReportQueue queue = new BleReportQueue();
        for (int i = 0; i < BleReportQueue.CAPACITY - 1; i++) assertTrue(queue.addState(1, new byte[8]));
        assertFalse(queue.addProgram(SignalReports.compile(SignalProgram.single("kb_a")), "action"));
        int count = 0;
        while (queue.acknowledge() != null) count++;
        assertEquals(BleReportQueue.CAPACITY - 1, count);
    }
    @Test public void disconnectDropsPendingInputAndSnapshotsCallerBuffers() {
        BleReportQueue queue = new BleReportQueue();
        byte[] state = {1, 0, 4, 0, 0, 0, 0, 0};
        assertTrue(queue.addState(1, state));
        state[0] = 0;
        assertEquals(1, queue.peek().value[0]);
        queue.clear();
        assertNull(queue.peek());
        assertTrue(queue.addState(1, new byte[8]));
        assertArrayEquals(new byte[8], queue.peek().value);
    }
}
