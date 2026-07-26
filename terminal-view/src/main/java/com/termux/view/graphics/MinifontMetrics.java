package com.termux.view.graphics;

import android.graphics.Color;

public final class MinifontMetrics {
    public final int cellWidth;
    public final int cellHeight;
    public final int lightStem;
    public final int heavyStem;
    public final boolean bold;
    public final int generation;

    public MinifontMetrics(int cellWidth, int cellHeight, int generation) {
        this(cellWidth, cellHeight, false, generation);
    }

    public MinifontMetrics(int cellWidth, int cellHeight, boolean bold, int generation) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.bold = bold;
        int light = Math.max(1, cellWidth / 5);
        int heavy = light + 2;
        if (bold) {
            light = (light * 3 + 1) / 2;
            heavy = (heavy * 3 + 1) / 2;
        }
        this.lightStem = light;
        this.heavyStem = heavy;
        this.generation = generation;
    }

    public static int snap(float v) {
        return Math.round(v);
    }

    public static float snapCenter(float v) {
        return Math.round(v) + 0.5f;
    }

    public static int blend(int fg, int bg, float alpha) {
        if (alpha >= 1f) return fg;
        if (alpha <= 0f) return bg;
        int r = Math.round(Color.red(fg) * alpha + Color.red(bg) * (1f - alpha));
        int g = Math.round(Color.green(fg) * alpha + Color.green(bg) * (1f - alpha));
        int b = Math.round(Color.blue(fg) * alpha + Color.blue(bg) * (1f - alpha));
        return Color.rgb(r, g, b);
    }
}
