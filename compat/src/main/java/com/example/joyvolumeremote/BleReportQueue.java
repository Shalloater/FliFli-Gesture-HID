package com.example.joyvolumeremote;

import java.util.ArrayDeque;

/** A press and its release are admitted together and retained until the GATT send callback. */
final class BleReportQueue {
    static final int CAPACITY = 64;
    static final class Report {
        final int id;
        final byte[] value;
        final int holdMs;
        final String completedAction;

        Report(int id, byte[] value, int holdMs, String completedAction) {
            this.id = id;
            this.value = value.clone();
            this.holdMs = holdMs;
            this.completedAction = completedAction;
        }
    }
    private final ArrayDeque<Report> reports = new ArrayDeque<>();

    boolean addProgram(java.util.List<SignalReports.Frame> frames, String label) {
        if (frames.isEmpty() || reports.size() + frames.size() > CAPACITY) return false;
        for (int i = 0; i < frames.size(); i++) {
            SignalReports.Frame frame = frames.get(i);
            reports.add(new Report(frame.reportId, frame.data, frame.delayAfterMs, i == frames.size() - 1 ? label : null));
        }
        return true;
    }

    boolean addState(int id, byte[] value) {
        if (reports.size() >= CAPACITY) return false;
        reports.add(new Report(id, value, 0, null));
        return true;
    }

    Report peek() { return reports.peek(); }
    Report acknowledge() { return reports.poll(); }
    void clear() { reports.clear(); }
}
