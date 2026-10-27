package com.example.joyvolumeremote;

import java.math.BigDecimal;

/** A portable width:height value, independent of the phone's orientation. */
final class TouchAspectRatio {
    static final String DEFAULT = "3:4";
    static final String[] PRESETS = {"3:4", "2:3", "4:5", "5:8", "9:16", "9:18",
            "9:19.5", "9:20", "1:1", "4:3", "16:10", "16:9"};
    final String width, height;
    final double value;

    private TouchAspectRatio(BigDecimal width, BigDecimal height) {
        this.width = width.stripTrailingZeros().toPlainString();
        this.height = height.stripTrailingZeros().toPlainString();
        value = width.doubleValue() / height.doubleValue();
    }

    static TouchAspectRatio parse(String encoded) {
        if (encoded == null || encoded.length() > 32) throw new IllegalArgumentException("Invalid ratio");
        String[] parts = encoded.split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid ratio");
        BigDecimal w = component(parts[0]), h = component(parts[1]);
        double ratio = w.doubleValue() / h.doubleValue();
        if (ratio < .1 || ratio > 10) throw new IllegalArgumentException("Ratio out of range");
        return new TouchAspectRatio(w, h);
    }

    private static BigDecimal component(String input) {
        String text = input.trim();
        if (!text.matches("[0-9]{1,4}(\\.[0-9]{1,3})?")) throw new IllegalArgumentException("Invalid dimension");
        BigDecimal value = new BigDecimal(text);
        if (value.compareTo(new BigDecimal("0.1")) < 0 || value.compareTo(new BigDecimal("1000")) > 0)
            throw new IllegalArgumentException("Dimension out of range");
        return value;
    }

    static TouchAspectRatio orDefault(String encoded) {
        try { return parse(encoded); }
        catch (IllegalArgumentException invalid) { return parse(DEFAULT); }
    }

    String encode() { return width + ":" + height; }
    String label() { return width + " : " + height; }
    TouchAspectRatio swapped() { return parse(height + ":" + width); }
}
