package com.example.joyvolumeremote;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Coalesce only movement frames; contact presses and lifts must retain their order. */
final class TouchReportQueue {
    static final int CAPACITY = 64;
    private static final class Entry {
        final byte[] report;
        final boolean transition;
        Entry(byte[] report, boolean transition) { this.report = report.clone(); this.transition = transition; }
    }
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private int lastMask;
    private byte[] lastAccepted;

    boolean offer(byte[] report) {
        int mask = TouchState.mask(report);
        if (Arrays.equals(report, lastAccepted)) return true;
        boolean transition = mask != lastMask || (lastAccepted != null && lastAccepted[12] == 0 && report[12] != 0);
        Entry tail = queue.peekLast();
        if (!transition && tail != null && !tail.transition) queue.removeLast();
        boolean endGesture = mask == 0 && report[12] != 0;
        if (queue.size() > CAPACITY - (endGesture ? 2 : 1)) return false;
        queue.addLast(new Entry(report, transition));
        if (endGesture) queue.addLast(new Entry(TouchState.endFrame(report), true));
        lastMask = mask;
        lastAccepted = report.clone();
        return true;
    }

    byte[] poll() { Entry entry = queue.poll(); return entry == null ? null : entry.report; }
    void clear() { queue.clear(); lastMask = 0; lastAccepted = null; }
}
