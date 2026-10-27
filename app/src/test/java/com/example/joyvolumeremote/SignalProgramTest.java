package com.example.joyvolumeremote;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class SignalProgramTest {
    @Test public void existingBindingsRemainReadableAndLegacyShortcutsBecomeEditableChords() {
        assertEquals("kb_volume_down", SignalProgram.parse("kb_volume_down").encode());
        SignalProgram ctrlC = SignalProgram.parse("kb_ctrl_c");
        assertEquals(SignalProgram.Mode.CHORD, ctrlC.mode);
        assertEquals(Arrays.asList("kb_ctrl", "kb_c"), ctrlC.steps.get(0));
        assertEquals(ctrlC.encode(), SignalProgram.parse(ctrlC.encode()).encode());
    }

    @Test public void sequenceRoundTripsMixedSingleAndChordStepsWithTiming() {
        SignalProgram program = new SignalProgram(SignalProgram.Mode.SEQUENCE,
                Arrays.asList(Arrays.asList("kb_ctrl", "kb_c"), Collections.singletonList("kb_tab"),
                        Arrays.asList("kb_ctrl", "kb_v")), 250);
        SignalProgram loaded = SignalProgram.parse(program.encode());
        assertEquals(program.steps, loaded.steps);
        assertEquals(250, loaded.gapMs);
        assertEquals(SignalProgram.Mode.SEQUENCE, loaded.mode);
    }

    @Test public void snapshotsPreventAnOpenEditorFromMutatingASavedProgram() {
        List<String> step = new ArrayList<>(Collections.singletonList("kb_a"));
        SignalProgram program = new SignalProgram(SignalProgram.Mode.SINGLE, Collections.singletonList(step), 100);
        step.clear();
        assertEquals("kb_a", program.encode());
        assertThrows(UnsupportedOperationException.class, () -> program.steps.get(0).clear());
    }

    @Test public void invalidProgramsFailBeforeAnyReportsCanBeSent() {
        for (String value : new String[]{"unknown", "signal:2:CHORD:100:kb_a", "signal:1:CHORD:100:",
                "signal:1:SINGLE:100:kb_a,kb_b", "signal:1:CHORD:100:kb_a;kb_b",
                "signal:1:SEQUENCE:-1:kb_a", "signal:1:SEQUENCE:2001:kb_a",
                "signal:1:CHORD:100:consumer_next,consumer_prev", "signal:1:CHORD:100:touch_up,kb_a",
                "signal:1:CHORD:100:none,kb_a", "signal:1:CHORD:100:kb_a,kb_a",
                "signal:1:CHORD:100:kb_a,kb_b,kb_c,kb_d,kb_e,kb_f,kb_g"}) {
            assertThrows(value, IllegalArgumentException.class, () -> SignalProgram.parse(value));
        }
    }

    @Test public void sequenceLimitAndKeyboardRolloverAreEnforcedWithoutTruncation() {
        List<List<String>> steps = new ArrayList<>();
        for (int i = 0; i < SignalProgram.MAX_STEPS; i++) steps.add(Collections.singletonList("kb_a"));
        new SignalProgram(SignalProgram.Mode.SEQUENCE, steps, 0);
        steps.add(Collections.singletonList("kb_a"));
        assertThrows(IllegalArgumentException.class, () -> new SignalProgram(SignalProgram.Mode.SEQUENCE, steps, 0));
        SignalProgram.validateStep(Arrays.asList("kb_ctrl", "kb_shift", "kb_a", "kb_b", "kb_c", "kb_d", "kb_e", "kb_f"));
    }

    @Test public void catalogIdsAreUniqueAndAllKeyboardUsagesFitTheDescriptor() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (HidAction.ActionDefinition action : HidAction.all()) {
            assertTrue(action.id, ids.add(action.id));
            for (int usage : action.keyboardUsages) assertTrue(usage >= 4 && usage <= 0x81);
        }
    }

    @Test public void edgeGestureBindingsRoundTripAndCanBeMixedWithKeyboardSequenceSteps() {
        String[] gestures = {"touch_top_left_down", "touch_left_edge_right", "touch_right_edge_left",
                "touch_bottom_left_up", "touch_bottom_center_up", "touch_bottom_right_up", "touch_top_right_diagonal",
                "touch_top_right_down", "touch_left_edge_up", "touch_left_edge_down", "touch_right_edge_up", "touch_right_edge_down"};
        List<List<String>> steps = new ArrayList<>();
        for (String id : gestures) {
            assertEquals(HidAction.OutputType.TOUCH, HidAction.byId(id).outputType);
            assertEquals(id, SignalProgram.parse(id).encode());
            assertThrows(IllegalArgumentException.class, () -> SignalProgram.validateStep(Arrays.asList(id, "kb_a")));
            steps.add(Collections.singletonList(id));
        }
        steps.add(Arrays.asList("kb_ctrl", "kb_c"));
        SignalProgram sequence = new SignalProgram(SignalProgram.Mode.SEQUENCE, steps, 250);
        assertEquals(steps, SignalProgram.parse(sequence.encode()).steps);
        List<SignalReports.Frame> frames = SignalReports.compile(sequence);
        assertEquals(gestures.length * 15 + 2, frames.size());
        assertEquals(SignalReports.KEYBOARD, frames.get(frames.size() - 2).reportId);
    }
}
