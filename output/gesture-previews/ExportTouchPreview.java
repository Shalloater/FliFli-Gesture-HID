package com.example.joyvolumeremote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Export production touch coordinates for the independent SVG review package. */
public final class ExportTouchPreview {
    public static void main(String[] args) throws Exception {
        List<String> signals = new ArrayList<>();
        for (HidAction.ActionDefinition action : HidAction.all()) {
            if (action.outputType != HidAction.OutputType.TOUCH) continue;
            List<String> events = new ArrayList<>();
            int elapsed = 0;
            for (SignalReports.Frame frame : SignalReports.compile(SignalProgram.single(action.id))) {
                byte[] data = frame.data;
                events.add("{\"time\":" + elapsed + ",\"down\":" + ((data[0] & 1) != 0)
                        + ",\"x\":" + coordinate(data, 2) + ",\"y\":" + coordinate(data, 4) + "}");
                elapsed += frame.delayAfterMs;
            }
            signals.add("{\"id\":\"" + action.id + "\",\"durationMs\":" + elapsed
                    + ",\"events\":[" + String.join(",", events) + "]}");
        }
        Files.writeString(Path.of(args[0]), "[" + String.join(",", signals) + "]");
        System.out.println("Exported " + signals.size() + " production touch signals");
    }
    private static int coordinate(byte[] report, int offset) {
        return (report[offset] & 255) | ((report[offset + 1] & 255) << 8);
    }
}
