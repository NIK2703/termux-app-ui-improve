package com.termux.view.graphics;

import android.graphics.Color;

public final class ColorUtils {

    public static int dim(int fg, int bg) {
        int r = (fg >> 16) & 0xFF;
        int g = (fg >> 8) & 0xFF;
        int b = fg & 0xFF;
        return Color.rgb(r >> 1, g >> 1, b >> 1);
    }

    public static int contrastForeground(int bg) {
        int r = Color.red(bg), g = Color.green(bg), b = Color.blue(bg);
        float y = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        return y > 127f ? 0xFF000000 : 0xFFFFFFFF;
    }

    private ColorUtils() {}
}
