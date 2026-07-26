package com.termux.view.graphics;

import android.graphics.Color;

import com.termux.terminal.TextStyle;

public final class ResolvedColors {

    public int fg;
    public int bg;
    public int deco;
    public boolean skipGlyph;

    public static ResolvedColors resolve(long style, int decoColorIndex, int[] palette,
                                          int defaultBg, boolean globalReverseVideo,
                                          boolean selected, boolean cursorBlock, int cursorColor,
                                          boolean visualBellActive) {
        ResolvedColors r = new ResolvedColors();
        resolveInto(r, style, decoColorIndex, palette, defaultBg, globalReverseVideo,
            selected, cursorBlock, cursorColor, visualBellActive);
        return r;
    }

    public static void resolveInto(ResolvedColors out, long style, int decoColorIndex, int[] palette,
                                    int defaultBg, boolean globalReverseVideo,
                                    boolean selected, boolean cursorBlock, int cursorColor,
                                    boolean visualBellActive) {
        int eff = TextStyle.decodeEffect(style);
        boolean invisible = (eff & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0;
        boolean bold = (eff & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        boolean dim = (eff & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;
        boolean inverse = (eff & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0;

        int rawFg = TextStyle.decodeForeColor(style);
        int rawBg = TextStyle.decodeBackColor(style);

        out.fg = (rawFg & 0xff000000) != 0xff000000 ? palette[rawFg] : rawFg;
        out.bg = (rawBg & 0xff000000) != 0xff000000 ? palette[rawBg] : rawBg;

        out.skipGlyph = invisible;

        if (bold && rawFg >= 0 && rawFg < 8) {
            out.fg = palette[rawFg + 8];
        }

        if (dim) {
            out.fg = halve(out.fg);
        }

        if (globalReverseVideo ^ inverse) {
            int tmp = out.fg; out.fg = out.bg; out.bg = tmp;
        }

        if (selected) {
            int selFg = palette[TextStyle.COLOR_INDEX_SELECTION_FOREGROUND];
            int selBg = palette[TextStyle.COLOR_INDEX_SELECTION_BACKGROUND];
            if (selFg != palette[TextStyle.COLOR_INDEX_FOREGROUND] || selBg != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
                out.fg = selFg;
                out.bg = selBg;
            } else {
                int tmp = out.fg; out.fg = out.bg; out.bg = tmp;
            }
            out.skipGlyph = false;
        }

        if (cursorBlock) {
            out.fg = out.bg;
            out.bg = cursorColor;
            out.skipGlyph = false;
        }

        if (decoColorIndex == TextStyle.COLOR_INDEX_FOREGROUND) {
            out.deco = out.fg;
        } else {
            out.deco = ((decoColorIndex & 0xff000000) != 0xff000000) ? palette[decoColorIndex] : decoColorIndex;
        }

        if (visualBellActive) {
            int tmp = out.fg; out.fg = out.bg; out.bg = tmp;
        }
    }

    static int halve(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return Color.rgb(r >> 1, g >> 1, b >> 1);
    }

    public ResolvedColors() {}
}
