package com.example.joyvolumeremote;

import static com.example.joyvolumeremote.KeyboardDefinitions.*;

final class GestureRules {
    private GestureRules() {}
    static boolean tapRegionsOverlap(String first, String second) {
        float[] a = areaBounds(first);
        float[] b = areaBounds(second);
        return a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1];
    }

    static boolean tapAreaContains(String area, float xRatio, float yRatio) {
        float[] bounds = areaBounds(area);
        return xRatio >= bounds[0] && xRatio <= bounds[2] && yRatio >= bounds[1] && yRatio <= bounds[3];
    }

    static float[] areaBounds(String area) {
        if (TAP_AREA_LEFT.equals(area)) return new float[]{0f, 0f, 0.5f, 1f};
        if (TAP_AREA_RIGHT.equals(area)) return new float[]{0.5f, 0f, 1f, 1f};
        if (TAP_AREA_TOP.equals(area)) return new float[]{0f, 0f, 1f, 0.5f};
        if (TAP_AREA_BOTTOM.equals(area)) return new float[]{0f, 0.5f, 1f, 1f};
        return new float[]{0f, 0f, 1f, 1f};
    }

}
