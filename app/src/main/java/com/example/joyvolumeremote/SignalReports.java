package com.example.joyvolumeremote;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Transport-independent press/release timeline. All reports use payloads without report IDs. */
final class SignalReports {
    static final int KEYBOARD = 1, CONSUMER = 2, TOUCH = 3;
    // A single Linux slot has range 0..0. Android requires a slot maximum > 0,
    // otherwise it assumes protocol A and loses unchanged protocol-B coordinates.
    // The extra wire record remains empty for both shortcuts and passthrough.
    static final int TOUCH_CONTACT_CAPACITY = TouchState.HID_SLOT_CAPACITY;
    static final byte[] TOUCH_DESCRIPTOR = TouchState.DESCRIPTOR;

    static final class Frame {
        final int reportId;
        final byte[] data;
        final int delayAfterMs;
        Frame(int reportId, byte[] data, int delayAfterMs) {
            this.reportId = reportId; this.data = data.clone(); this.delayAfterMs = delayAfterMs;
        }
    }

    static List<Frame> compile(SignalProgram program) {
        List<Frame> frames = new ArrayList<>();
        for (List<String> step : program.steps) {
            HidAction.ActionDefinition first = HidAction.byId(step.get(0));
            if (first.outputType == HidAction.OutputType.TOUCH) {
                touch(frames, first.id, program.gapMs);
                continue;
            }
            if (first.outputType == HidAction.OutputType.NONE) continue;
            int modifiers = 0, consumer = 0;
            boolean keyboard = false;
            Set<Integer> keys = new LinkedHashSet<>();
            for (String id : step) {
                HidAction.ActionDefinition action = HidAction.byId(id);
                keyboard |= action.outputType == HidAction.OutputType.KEYBOARD;
                modifiers |= action.modifierMask;
                for (int key : action.keyboardUsages) keys.add(key);
                if (action.outputType == HidAction.OutputType.CONSUMER) consumer = action.consumerUsage;
            }
            List<Frame> presses = new ArrayList<>();
            if (keyboard) {
                byte[] data = new byte[8]; data[0] = (byte) modifiers;
                int index = 2;
                for (int key : keys) data[index++] = (byte) key;
                presses.add(new Frame(KEYBOARD, data, 0));
            }
            if (consumer != 0) presses.add(new Frame(CONSUMER, new byte[]{(byte) consumer, (byte) (consumer >> 8)}, 0));
            for (int i = 0; i < presses.size(); i++) {
                Frame press = presses.get(i);
                frames.add(new Frame(press.reportId, press.data, i == presses.size() - 1 ? 35 : 0));
            }
            for (int i = 0; i < presses.size(); i++) {
                Frame press = presses.get(i);
                frames.add(new Frame(press.reportId, new byte[press.data.length], i == presses.size() - 1 ? program.gapMs : 0));
            }
        }
        return frames;
    }

    private static void touch(List<Frame> frames, String id, int gapMs) {
        int center = 16384, low = 8192, high = 24576;
        int edge = TouchState.MAX_COORDINATE;
        // The reader divides its top/bottom into thirds; use each region's center.
        int leftRegion = edge / 6, rightRegion = edge - leftRegion;
        int x1 = center, y1 = center, x2 = center, y2 = center;
        switch (id) {
            case "touch_up": y1 = high; y2 = low; break;
            case "touch_down": y1 = low; y2 = high; break;
            case "touch_left": x1 = high; x2 = low; break;
            case "touch_right": x1 = low; x2 = high; break;
            case "touch_top_left_down": x1 = x2 = leftRegion; y1 = 0; y2 = low; break;
            case "touch_left_edge_right": x1 = 0; x2 = low; break;
            case "touch_right_edge_left": x1 = edge; x2 = high; break;
            case "touch_bottom_left_up": x1 = x2 = leftRegion; y1 = edge; y2 = high; break;
            case "touch_bottom_center_up": y1 = edge; y2 = high; break;
            case "touch_bottom_right_up": x1 = x2 = rightRegion; y1 = edge; y2 = high; break;
            case "touch_top_right_diagonal": x1 = edge; y1 = 0; x2 = high; y2 = low; break;
            case "touch_top_right_down": x1 = x2 = rightRegion; y1 = 0; y2 = low; break;
            case "touch_left_edge_up": x1 = x2 = 0; y2 = low; break;
            case "touch_left_edge_down": x1 = x2 = 0; y2 = high; break;
            case "touch_right_edge_up": x1 = x2 = edge; y2 = low; break;
            case "touch_right_edge_down": x1 = x2 = edge; y2 = high; break;
            default: break;
        }
        boolean swipe = x1 != x2 || y1 != y2;
        int taps = id.equals("touch_double_tap") ? 2 : 1;
        for (int tap = 0; tap < taps; tap++) {
            frames.add(new Frame(TOUCH, touchPayload(true, x1, y1), swipe ? 16 : 45));
            if (swipe) for (int i = 1; i <= 12; i++) {
                frames.add(new Frame(TOUCH, touchPayload(true, x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12), 16));
            }
            byte[] lift = touchPayload(false, x2, y2);
            int gap = tap + 1 < taps ? 90 : gapMs;
            int liftDelay = Math.min(16, gap);
            frames.add(new Frame(TOUCH, lift, liftDelay));
            frames.add(new Frame(TOUCH, TouchState.endFrame(lift), gap - liftDelay));
        }
    }

    static byte[] touchPayload(boolean down, int x, int y) {
        return TouchState.singleContact(down, x, y);
    }

    private SignalReports() {}
}
