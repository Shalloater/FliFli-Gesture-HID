package com.example.joyvolumeremote;

/** Largest centered 3:4 control area inside the vertical system-reserved edges. */
final class TouchArea {
    final int left, top, width, height;
    private TouchArea(int left, int top, int width, int height) {
        this.left = left; this.top = top; this.width = width; this.height = height;
    }

    static TouchArea fit(int width, int height, int topInset, int bottomInset, boolean safe) {
        width = Math.max(1, width); height = Math.max(1, height);
        if (!safe) return new TouchArea(0, 0, width, height);
        int top = Math.max(0, Math.min(topInset, height - 1));
        int bottom = Math.max(0, Math.min(bottomInset, height - top - 1));
        int availableHeight = Math.max(1, height - top - bottom);
        int areaWidth = Math.max(1, Math.min(width, (int) ((long) availableHeight * 3 / 4)));
        int areaHeight = Math.max(1, Math.min(availableHeight, Math.round(areaWidth * 4f / 3f)));
        return new TouchArea((width - areaWidth) / 2, top + (availableHeight - areaHeight) / 2, areaWidth, areaHeight);
    }

    boolean contains(float x, float y) {
        return x >= left && x <= left + width - 1 && y >= top && y <= top + height - 1;
    }

    /** First intersection along the sampled path, including paths that cross the whole area. */
    float entryFraction(float fromX, float fromY, float toX, float toY) {
        float enter = 0, leave = 1;
        float dx = toX - fromX, dy = toY - fromY;
        if (dx == 0) {
            if (fromX < left || fromX > left + width - 1) return Float.NaN;
        } else {
            float first = (left - fromX) / dx, last = (left + width - 1 - fromX) / dx;
            enter = Math.max(enter, Math.min(first, last));
            leave = Math.min(leave, Math.max(first, last));
        }
        if (dy == 0) {
            if (fromY < top || fromY > top + height - 1) return Float.NaN;
        } else {
            float first = (top - fromY) / dy, last = (top + height - 1 - fromY) / dy;
            enter = Math.max(enter, Math.min(first, last));
            leave = Math.min(leave, Math.max(first, last));
        }
        return enter <= leave ? enter : Float.NaN;
    }
}
