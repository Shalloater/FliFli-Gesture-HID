package com.example.joyvolumeremote;

import java.io.ByteArrayOutputStream;
/** One forwarded finger, with a reserved empty HID record for Android slot compatibility. */
final class TouchState {
    static final int REPORT_ID = 3;
    // Android 10 needs a nonzero maximum slot index to recognize Linux protocol B.
    // This is a wire-format requirement; the second record is always empty.
    static final int HID_SLOT_CAPACITY = 2;
    static final int MAX_COORDINATE = 32767;
    static final int REPORT_LENGTH = HID_SLOT_CAPACITY * 6 + 1;
    static final byte[] DESCRIPTOR = descriptor();
    private int pointerId = -1;
    private int x, y;
    private boolean lifted;

    boolean press(int pointerId, float x, float y, int width, int height) {
        if (pointerId < 0) throw new IllegalArgumentException("Invalid pointer ID");
        if (this.pointerId != -1 && this.pointerId != pointerId) return false;
        this.pointerId = pointerId;
        lifted = false;
        move(pointerId, x, y, width, height);
        return true;
    }

    void move(int pointerId, float x, float y, int width, int height) {
        if (this.pointerId != pointerId) return;
        this.x = coordinate(x, width);
        this.y = coordinate(y, height);
    }

    void lift(int pointerId) {
        if (this.pointerId == pointerId) { this.pointerId = -1; lifted = true; }
    }

    void clear() { pointerId = -1; lifted = false; }
    void frameSent() { lifted = false; }

    static byte[] singleContact(boolean down, int x, int y) {
        TouchState state = new TouchState();
        state.x = Math.max(0, Math.min(MAX_COORDINATE, x));
        state.y = Math.max(0, Math.min(MAX_COORDINATE, y));
        state.lifted = !down;
        if (down) state.pointerId = 0;
        return state.report();
    }

    byte[] report() {
        byte[] report = new byte[REPORT_LENGTH];
        if (pointerId >= 0 || lifted) {
            report[0] = (byte) (pointerId >= 0 ? 3 : 0);
            report[2] = (byte) x; report[3] = (byte) (x >> 8);
            report[4] = (byte) y; report[5] = (byte) (y >> 8);
            report[12] = 1;
        }
        return report;
    }

    static byte[] endFrame(byte[] report) {
        mask(report);
        byte[] ended = report.clone();
        ended[0] = 0; ended[6] = 0; ended[12] = 0;
        return ended;
    }

    static int mask(byte[] report) {
        if (report == null || report.length != REPORT_LENGTH) throw new IllegalArgumentException("Invalid touch report");
        if (report[1] != 0 || (report[6] & 3) != 0 || (report[12] & 255) > 1)
            throw new IllegalArgumentException("Only a single touch contact is supported");
        return report[0] & 1;
    }

    static int coordinate(float point, int dimension) {
        if (dimension <= 1 || Float.isNaN(point)) return 0;
        float normalized = Math.max(0f, Math.min(1f, point / (dimension - 1f)));
        return Math.round(normalized * MAX_COORDINATE);
    }

    private static byte[] descriptor() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        append(bytes, 0x05, 0x0D, 0x09, 0x04, 0xA1, 0x01, 0x85, REPORT_ID);
        for (int i = 0; i < HID_SLOT_CAPACITY; i++) {
            append(bytes, 0x05, 0x0D, 0x09, 0x22, 0xA1, 0x02,
                    0x09, 0x42, 0x09, 0x32, 0x15, 0x00, 0x25, 0x01,
                    0x75, 0x01, 0x95, 0x02, 0x81, 0x02,
                    0x75, 0x06, 0x95, 0x01, 0x81, 0x03,
                    0x09, 0x51, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x01, 0x81, 0x02,
                    0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x00, 0x26, 0xFF, 0x7F,
                    0x75, 0x10, 0x95, 0x02, 0x81, 0x02, 0xC0);
        }
        append(bytes, 0x05, 0x0D, 0x09, 0x54, 0x15, 0x00, 0x25, HID_SLOT_CAPACITY,
                0x75, 0x08, 0x95, 0x01, 0x81, 0x02,
                0x09, 0x55, 0xB1, 0x02, 0xC0);
        return bytes.toByteArray();
    }

    private static void append(ByteArrayOutputStream bytes, int... values) {
        for (int value : values) bytes.write(value);
    }
}
