package com.example.joyvolumeremote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A versioned value in the existing actionId field; legacy IDs need no migration. */
final class SignalProgram {
    enum Mode { SINGLE, CHORD, SEQUENCE }
    static final int MAX_STEPS = 16;
    static final int DEFAULT_GAP_MS = 100;
    final Mode mode;
    final List<List<String>> steps;
    final int gapMs;

    SignalProgram(Mode mode, List<List<String>> steps, int gapMs) {
        if (mode == null || steps == null || steps.isEmpty() || steps.size() > MAX_STEPS
                || gapMs < 0 || gapMs > 2000 || (mode != Mode.SEQUENCE && steps.size() != 1)) {
            throw new IllegalArgumentException("Invalid signal program");
        }
        List<List<String>> copy = new ArrayList<>();
        for (List<String> step : steps) {
            validateStep(step);
            if (mode == Mode.SINGLE && step.size() != 1) throw new IllegalArgumentException("Single signal required");
            copy.add(Collections.unmodifiableList(new ArrayList<>(step)));
        }
        this.mode = mode;
        this.steps = Collections.unmodifiableList(copy);
        this.gapMs = gapMs;
    }

    static void validateStep(List<String> step) {
        if (step == null || step.isEmpty() || step.size() > 15) throw new IllegalArgumentException("Empty or oversized step");
        Set<Integer> keys = new LinkedHashSet<>();
        int consumers = 0;
        Set<String> ids = new LinkedHashSet<>();
        for (String id : step) {
            if (!HidAction.isKnown(id) || !ids.add(id)) throw new IllegalArgumentException("Unknown or duplicate signal");
            HidAction.ActionDefinition action = HidAction.byId(id);
            if ((action.outputType == HidAction.OutputType.NONE || action.outputType == HidAction.OutputType.TOUCH) && step.size() != 1) {
                throw new IllegalArgumentException("Exclusive signal");
            }
            if (action.outputType == HidAction.OutputType.CONSUMER && ++consumers > 1) throw new IllegalArgumentException("One consumer usage per report");
            for (int key : action.keyboardUsages) keys.add(key);
        }
        if (keys.size() > 6) throw new IllegalArgumentException("Six keyboard usages per report");
    }

    static SignalProgram parse(String value) {
        if (value == null || value.equals("none")) return single("none");
        if (!value.startsWith("signal:1:")) {
            if (!HidAction.isKnown(value)) throw new IllegalArgumentException("Unknown signal");
            switch (value) {
                case "kb_ctrl_c": return legacyChord("kb_ctrl", "kb_c");
                case "kb_ctrl_v": return legacyChord("kb_ctrl", "kb_v");
                case "kb_ctrl_z": return legacyChord("kb_ctrl", "kb_z");
                case "kb_shift_tab": return legacyChord("kb_shift", "kb_tab");
                default: return single(value);
            }
        }
        if (value.length() > 8192) throw new IllegalArgumentException("Oversized program");
        String[] parts = value.split(":", -1);
        if (parts.length != 5) throw new IllegalArgumentException("Invalid program encoding");
        Mode mode = Mode.valueOf(parts[2]);
        int gap = Integer.parseInt(parts[3]);
        List<List<String>> steps = new ArrayList<>();
        for (String encoded : parts[4].split(";", -1)) {
            List<String> step = new ArrayList<>();
            Collections.addAll(step, encoded.split(",", -1));
            steps.add(step);
        }
        return new SignalProgram(mode, steps, gap);
    }

    static SignalProgram single(String id) {
        return new SignalProgram(Mode.SINGLE, Collections.singletonList(Collections.singletonList(id)), DEFAULT_GAP_MS);
    }

    private static SignalProgram legacyChord(String first, String second) {
        List<String> step = new ArrayList<>();
        step.add(first); step.add(second);
        return new SignalProgram(Mode.CHORD, Collections.singletonList(step), DEFAULT_GAP_MS);
    }

    String encode() {
        if (mode == Mode.SINGLE) return steps.get(0).get(0);
        StringBuilder result = new StringBuilder("signal:1:" + mode + ":" + gapMs + ":");
        for (List<String> step : steps) {
            if (result.charAt(result.length() - 1) != ':') result.append(';');
            for (int i = 0; i < step.size(); i++) {
                if (i > 0) result.append(',');
                result.append(step.get(i));
            }
        }
        return result.toString();
    }
}
