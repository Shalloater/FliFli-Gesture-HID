package com.example.joyvolumeremote;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SignalReportsTest {
    @Test public void touchscreenDescriptorRetainsAndroidSlotProtocolAndMatchesThePayload() {
        // Validate the HID field contract, including inherited globals at Contact Count Maximum.
        int usagePage = 0, logicalMax = 0, reportSize = 0, reportCount = 0, reportId = 0, usage = 0;
        int inputBits = 0, featureBits = 0, countMaximum = 0, capacityMaximum = 0;
        byte[] descriptor = SignalReports.TOUCH_DESCRIPTOR;
        for (int offset = 0; offset < descriptor.length;) {
            int prefix = descriptor[offset++] & 255;
            int size = prefix & 3;
            if (size == 3) size = 4;
            int value = 0;
            for (int i = 0; i < size; i++) value |= (descriptor[offset++] & 255) << (8 * i);
            int type = (prefix >> 2) & 3, tag = prefix >> 4;
            if (type == 1) {
                switch (tag) {
                    case 0: usagePage = value; break;
                    case 2: logicalMax = value; break;
                    case 7: reportSize = value; break;
                    case 8: reportId = value; break;
                    case 9: reportCount = value; break;
                    default: break;
                }
            } else if (type == 2 && tag == 0) {
                usage = value;
            } else if (type == 0) {
                if (tag == 8 || tag == 11) {
                    assertEquals(SignalReports.TOUCH, reportId);
                    if (tag == 8) inputBits += reportSize * reportCount;
                    else featureBits += reportSize * reportCount;
                    if (usagePage == 0x0D && usage == 0x54 && tag == 8) countMaximum = logicalMax;
                    if (usagePage == 0x0D && usage == 0x55 && tag == 11) capacityMaximum = logicalMax;
                }
                usage = 0;
            }
        }
        assertTrue("Android requires ABS_MT_SLOT max > 0 to use the Linux slots protocol", capacityMaximum >= 2);
        assertEquals(SignalReports.TOUCH_CONTACT_CAPACITY, capacityMaximum);
        assertEquals(capacityMaximum, countMaximum);
        assertEquals(8, featureBits);
        assertEquals(SignalReports.touchPayload(true, 16384, 16384).length * 8, inputBits);
        assertEquals("Gestures still send only one finger", 1, TouchState.mask(SignalReports.touchPayload(true, 16384, 16384)));
    }

    @Test public void winLabelsRetainBothGuiModifierBitsAndExistingConfigurationIds() {
        assertEquals("Win", HidAction.byId("kb_meta").label);
        assertEquals("R Win", HidAction.byId("kb_right_meta").label);
        List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.parse("signal:1:CHORD:100:kb_meta,kb_d"));
        assertArrayEquals(new byte[]{8, 0, 7, 0, 0, 0, 0, 0}, frames.get(0).data);
        assertEquals(128, HidAction.byId("kb_right_meta").modifierMask);
    }

    @Test public void ctrlCIsOneSimultaneousPressFollowedByRelease() {
        List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.parse("kb_ctrl_c"));
        assertEquals(2, frames.size());
        assertArrayEquals(new byte[]{1, 0, 6, 0, 0, 0, 0, 0}, frames.get(0).data);
        assertEquals(35, frames.get(0).delayAfterMs);
        assertArrayEquals(new byte[8], frames.get(1).data);
    }

    @Test public void sequencesReleaseModifiersBeforeTheNextStepIncludingRepeatedKeys() {
        List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.parse("signal:1:SEQUENCE:250:kb_ctrl,kb_c;kb_c;kb_ctrl,kb_v"));
        assertEquals(6, frames.size());
        assertEquals(250, frames.get(1).delayAfterMs);
        assertArrayEquals(new byte[8], frames.get(1).data);
        assertArrayEquals(new byte[]{0, 0, 6, 0, 0, 0, 0, 0}, frames.get(2).data);
        assertArrayEquals(new byte[8], frames.get(3).data);
        assertEquals(1, frames.get(4).data[0]);
        assertEquals(0x19, frames.get(4).data[2]);
        assertArrayEquals(new byte[8], frames.get(5).data);
    }

    @Test public void keyboardAndConsumerAreBothHeldBeforeEitherIsReleased() {
        List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.parse("signal:1:CHORD:100:kb_ctrl,android_back"));
        assertEquals(4, frames.size());
        assertEquals(SignalReports.KEYBOARD, frames.get(0).reportId);
        assertEquals(0, frames.get(0).delayAfterMs);
        assertEquals(SignalReports.CONSUMER, frames.get(1).reportId);
        assertArrayEquals(new byte[]{0x24, 0x02}, frames.get(1).data);
        assertEquals(35, frames.get(1).delayAfterMs);
        assertArrayEquals(new byte[8], frames.get(2).data);
        assertArrayEquals(new byte[2], frames.get(3).data);
    }

    @Test public void allSwipesHaveContinuousCoordinatesAndLiftAtTheEndpoint() {
        for (String direction : new String[]{"up", "down", "left", "right"}) {
            List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.single("touch_" + direction));
            assertEquals(15, frames.size());
            int axis = direction.equals("left") || direction.equals("right") ? 2 : 4;
            boolean increasing = direction.equals("down") || direction.equals("right");
            int previous = coord(frames.get(0).data, axis);
            for (int i = 1; i < 13; i++) {
                SignalReports.Frame frame = frames.get(i);
                assertEquals(3, frame.reportId);
                assertEquals(3, frame.data[0]);
                assertEquals(TouchState.REPORT_LENGTH, frame.data.length);
                assertEquals(0, frame.data[1]);
                assertEquals(0, frame.data[6]);
                assertEquals(1, frame.data[12]);
                int next = coord(frame.data, axis);
                assertTrue(increasing ? next > previous : next < previous);
                previous = next;
            }
            assertEquals(0, frames.get(13).data[0]);
            assertEquals(previous, coord(frames.get(13).data, axis));
            assertEquals(0, frames.get(14).data[12]);
        }
    }

    @Test public void centerDoubleTapIncludesTwoSeparateContactsAndARealGap() {
        List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.single("touch_double_tap"));
        assertEquals(6, frames.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i % 3 == 0 ? 3 : 0, frames.get(i).data[0]);
            assertEquals(16384, coord(frames.get(i).data, 2));
            assertEquals(16384, coord(frames.get(i).data, 4));
        }
        assertEquals(90, frames.get(1).delayAfterMs + frames.get(2).delayAfterMs);
    }

    @Test public void readerEdgeGesturesStartInTheCorrectRegionsAndReleaseAtTheEnd() {
        String[] ids = {"touch_top_left_down", "touch_left_edge_right", "touch_right_edge_left",
                "touch_bottom_left_up", "touch_bottom_center_up", "touch_bottom_right_up", "touch_top_right_diagonal"};
        int max = TouchState.MAX_COORDINATE;
        for (int gesture = 0; gesture < ids.length; gesture++) {
            List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.single(ids[gesture]));
            assertEquals(ids[gesture], 15, frames.size());
            byte[] start = frames.get(0).data, end = frames.get(12).data;
            int sx = coord(start, 2), sy = coord(start, 4), ex = coord(end, 2), ey = coord(end, 4);
            if (gesture == 0) {
                assertTrue(sx > 0 && sx < max / 3); assertEquals(0, sy);
                assertEquals(sx, ex); assertTrue(ey > sy);
            } else if (gesture == 1 || gesture == 2) {
                assertEquals(gesture == 1 ? 0 : max, sx);
                assertTrue(sy > max / 3 && sy < max * 2 / 3);
                assertEquals(sy, ey); assertTrue(gesture == 1 ? ex > sx : ex < sx);
            } else if (gesture <= 5) {
                assertEquals(max, sy); assertTrue(ey < sy); assertEquals(sx, ex);
                int region = gesture - 3;
                assertTrue(sx >= max * region / 3 && sx <= max * (region + 1) / 3);
            } else {
                assertEquals(max, sx); assertEquals(0, sy); assertTrue(ex < sx && ey > sy);
            }
            int duration = 0, previousX = sx, previousY = sy;
            for (int frameIndex = 0; frameIndex <= 12; frameIndex++) {
                SignalReports.Frame frame = frames.get(frameIndex);
                assertEquals(SignalReports.TOUCH, frame.reportId);
                assertEquals(1, TouchState.mask(frame.data)); assertEquals(1, frame.data[12]);
                for (int reserved = 6; reserved < 12; reserved++) assertEquals(0, frame.data[reserved]);
                int x = coord(frame.data, 2), y = coord(frame.data, 4);
                assertTrue(x >= Math.min(sx, ex) && x <= Math.max(sx, ex));
                assertTrue(y >= Math.min(sy, ey) && y <= Math.max(sy, ey));
                assertTrue(ex >= sx ? x >= previousX : x <= previousX);
                assertTrue(ey >= sy ? y >= previousY : y <= previousY);
                previousX = x; previousY = y; duration += frame.delayAfterMs;
            }
            assertTrue(duration >= 100 && duration <= 500);
            byte[] lift = frames.get(13).data;
            assertEquals(0, TouchState.mask(lift)); assertEquals(ex, coord(lift, 2)); assertEquals(ey, coord(lift, 4));
            assertEquals(0, frames.get(14).data[12]);
        }
    }

    @Test public void sideAndTopRightGesturesUseOneFixedLength() {
        String[] ids = {"touch_top_right_down", "touch_left_edge_up", "touch_left_edge_down",
                "touch_right_edge_up", "touch_right_edge_down"};
        int edge = TouchState.MAX_COORDINATE, rightRegion = edge - edge / 6;
        for (String id : ids) {
            List<SignalReports.Frame> frames = SignalReports.compile(SignalProgram.single(id));
            assertEquals(15, frames.size());
            int sx = coord(frames.get(0).data, 2), sy = coord(frames.get(0).data, 4);
            int ex = coord(frames.get(12).data, 2), ey = coord(frames.get(12).data, 4);
            if (id.equals("touch_top_right_down")) {
                assertEquals(rightRegion, sx); assertEquals(0, sy); assertEquals(sx, ex); assertEquals(8192, ey);
            } else {
                assertEquals(id.startsWith("touch_left") ? 0 : edge, sx); assertEquals(16384, sy); assertEquals(sx, ex);
                assertEquals(id.endsWith("_up") ? 8192 : 24576, ey);
            }
            assertEquals(8192, Math.abs(ey - sy));
            int previousY = sy, duration = 0;
            for (int i = 0; i <= 12; i++) {
                SignalReports.Frame frame = frames.get(i);
                assertEquals(SignalReports.TOUCH, frame.reportId); assertEquals(1, TouchState.mask(frame.data));
                assertEquals(1, frame.data[12]); assertEquals(sx, coord(frame.data, 2));
                int y = coord(frame.data, 4);
                assertTrue(ey > sy ? y >= previousY : y <= previousY);
                assertTrue(y >= Math.min(sy, ey) && y <= Math.max(sy, ey));
                for (int reserved = 6; reserved < 12; reserved++) assertEquals(0, frame.data[reserved]);
                duration += frame.delayAfterMs; previousY = y;
            }
            assertEquals(208, duration);
            assertEquals(0, TouchState.mask(frames.get(13).data));
            assertEquals(ex, coord(frames.get(13).data, 2)); assertEquals(ey, coord(frames.get(13).data, 4));
            assertEquals(0, TouchState.mask(frames.get(14).data)); assertEquals(0, frames.get(14).data[12]);
        }
    }

    private int coord(byte[] data, int index) { return (data[index] & 255) | ((data[index + 1] & 255) << 8); }
}
