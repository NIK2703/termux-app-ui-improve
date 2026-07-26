package com.termux.view;

import android.graphics.Bitmap;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DecorationTest {

    @Test
    public void single_underline_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4mUnderlined\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void double_underline_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4:2mDouble\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void curly_underline_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4:3mCurly\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void dotted_underline_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4:4mDotted\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void dashed_underline_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4:5mDashed\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void strikethrough_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[9mStrikethrough\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void overline_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[53mOverline\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void boxed_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[51mBoxed\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void underline_plus_strikethrough_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4;9mUnderline+Strike\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void all_decorations_combined_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4;9;53mAll\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void underline_with_wide_char_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4m\u4E2D\u56FD\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void underline_with_minifont_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4m\u2500\u2501\u2502\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void underline_with_emoji_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4m\uD83D\uDE00\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void underline_with_rtl_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4m\u05D0\u05D1\u05D2\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void underline_color_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4m\u001B[58:5:1mRedUnderline\u001B[0m\n");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decoration_damage_parity() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4mUnderline\u001B[0m \u001B[9mStrike\u001B[0m \u001B[4:2mDouble\u001B[0m\n");
        DifferentialDamageTest.DiffResult r = DifferentialDamageTest.compareBitmaps(
            h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(DifferentialDamageTest.diffMessage(r), r.equal);
    }

    @Test
    public void decoration_wide_cjk_damage_parity() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4m\u4E2D\u56FD wide\u001B[0m\n");
        DifferentialDamageTest.DiffResult r = DifferentialDamageTest.compareBitmaps(
            h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(DifferentialDamageTest.diffMessage(r), r.equal);
    }
}
