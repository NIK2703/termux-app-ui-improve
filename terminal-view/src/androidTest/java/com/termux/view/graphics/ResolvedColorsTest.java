package com.termux.view.graphics;

import com.termux.terminal.TextStyle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResolvedColorsTest {

    private static final int DEFAULT_FG = 0xFF00FF00;
    private static final int DEFAULT_BG = 0xFF000000;
    private static final int CURSOR_COLOR = 0xFFFFFFFF;

    private final int[] palette = buildPalette();

    private static int[] buildPalette() {
        int[] p = new int[TextStyle.NUM_INDEXED_COLORS];
        for (int i = 0; i < p.length; i++) p[i] = 0xFF000000 | (i * 0x10101);
        p[TextStyle.COLOR_INDEX_FOREGROUND] = DEFAULT_FG;
        p[TextStyle.COLOR_INDEX_BACKGROUND] = DEFAULT_BG;
        p[TextStyle.COLOR_INDEX_CURSOR] = CURSOR_COLOR;
        p[TextStyle.COLOR_INDEX_SELECTION_FOREGROUND] = DEFAULT_FG;
        p[TextStyle.COLOR_INDEX_SELECTION_BACKGROUND] = DEFAULT_BG;
        return p;
    }

    private static final long TRUECOLOR_FG = 1L << 48;
    private static final long TRUECOLOR_BG = 1L << 24;

    private static long style(int fg, int bg, int effects) {
        long result = effects & 0xFFFFL;
        if ((0xff000000 & fg) == 0xff000000) {
            result |= TRUECOLOR_FG | ((fg & 0x00ffffffL) << 40L);
        } else {
            result |= (fg & 0b111111111L) << 40;
        }
        if ((0xff000000 & bg) == 0xff000000) {
            result |= TRUECOLOR_BG | ((bg & 0x00ffffffL) << 16L);
        } else {
            result |= (bg & 0b111111111L) << 16L;
        }
        return result;
    }

    @Test
    public void default_fg_bg() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND, 0),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        assertEquals(DEFAULT_FG, r.fg);
        assertEquals(DEFAULT_BG, r.bg);
        assertEquals(DEFAULT_FG, r.deco);
        assertFalse(r.skipGlyph);
    }

    @Test
    public void invisible_skip() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        assertTrue(r.skipGlyph);
    }

    @Test
    public void bold_makes_bright() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(0, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_BOLD),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        assertEquals(palette[0 + 8], r.fg);
    }

    @Test
    public void dim_halves_brightness() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_DIM),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        int half = ResolvedColors.halve(DEFAULT_FG);
        assertEquals(half, r.fg);
    }

    @Test
    public void inverse_swaps_fg_bg() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_INVERSE),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        assertEquals(DEFAULT_BG, r.fg);
        assertEquals(DEFAULT_FG, r.bg);
    }

    @Test
    public void global_reverse_video() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND, 0),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, true, false, false, CURSOR_COLOR, false);
        assertEquals(DEFAULT_BG, r.fg);
        assertEquals(DEFAULT_FG, r.bg);
    }

    @Test
    public void selection_inverts() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND, 0),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, true, false, CURSOR_COLOR, false);
        assertEquals(DEFAULT_BG, r.fg);
        assertEquals(DEFAULT_FG, r.bg);
    }

    @Test
    public void cursor_block_swaps_fg_bg() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND, 0),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, true, CURSOR_COLOR, false);
        assertEquals(DEFAULT_BG, r.fg);
        assertEquals(CURSOR_COLOR, r.bg);
    }

    @Test
    public void visual_bell_swaps_temporarily() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND, 0),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, true);
        assertEquals(DEFAULT_BG, r.fg);
        assertEquals(DEFAULT_FG, r.bg);
    }

    @Test
    public void deco_color_from_palette() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND, 0),
            1, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        assertEquals(palette[1], r.deco);
    }

    @Test
    public void deco_color_fg_when_index_is_fg() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND, 0),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        assertEquals(r.fg, r.deco);
    }

    @Test
    public void halves_correctness() {
        assertEquals(0x7F, ResolvedColors.halve(0x0000FF) & 0xFF);
        assertEquals(0x7F, ResolvedColors.halve(0x00FF00) >> 8 & 0xFF);
        assertEquals(0x7F, ResolvedColors.halve(0xFF0000) >> 16 & 0xFF);
    }

    @Test
    public void resolve_chain_bold_then_dim() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(1, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_DIM),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, false, false, CURSOR_COLOR, false);
        int bright = palette[1 + 8];
        int dimmed = ResolvedColors.halve(bright);
        assertEquals(dimmed, r.fg);
    }

    @Test
    public void selection_overrides_bold_dim_inverse() {
        ResolvedColors r = new ResolvedColors();
        ResolvedColors.resolveInto(r, style(0, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_INVERSE),
            TextStyle.COLOR_INDEX_FOREGROUND, palette, DEFAULT_BG, false, true, false, CURSOR_COLOR, false);
        // After bold(0→8) → inverse(fg↔bg) → selection invert(swap again):
        // fg = bold-bright-color (0xFF080808), bg = DEFAULT_BG (0xFF000000)
        assertEquals(palette[0 + 8], r.fg);
        assertEquals(DEFAULT_BG, r.bg);
    }
}
