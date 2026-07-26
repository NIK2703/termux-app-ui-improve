package com.termux.view;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Cursor rendering tests: DECSCUSR styles, cluster types, focus/unfocus.
 */
public class CursorTest {

    @Test
    public void block_cursor_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[2 qHello"); // steady block
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void underline_cursor_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4 qHello"); // steady underline
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void bar_cursor_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[6 qHello"); // steady bar
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void focused_vs_unfocused_block_cursor_differs() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[2 qHello"); // steady block
        DifferentialDamageTest.DiffResult r = DifferentialDamageTest.compareBitmaps(
            h.renderWithFocus(true), h.renderWithFocus(false));
        assertFalse("Focused and unfocused block cursor should differ", r.equal);
    }

    @Test
    public void focused_vs_unfocused_underline_same() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4 qHello"); // steady underline
        DifferentialDamageTest.DiffResult r = DifferentialDamageTest.compareBitmaps(
            h.renderWithFocus(true), h.renderWithFocus(false));
        assertTrue("Underline cursor should be same focused and unfocused", r.equal);
    }

    @Test
    public void cursor_over_wide_char() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u4E2D\u56FD");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void cursor_over_emoji() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\uD83D\uDE00");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void cursor_over_minifont() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u2500\u2501");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void cursor_over_rtl() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u05D0\u05D1\u05D2");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decscusr_style_0_block() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[0 qHello");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decscusr_style_1_blink_block() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[1 qHello");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decscusr_style_2_steady_block() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[2 qHello");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decscusr_style_3_underline() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[3 qHello");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decscusr_style_4_steady_underline() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[4 qHello");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decscusr_style_5_bar() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[5 qHello");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void decscusr_style_6_steady_bar() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[6 qHello");
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void cursor_hidden_by_dectcem() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[?25lHello"); // hide cursor
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void cursor_reshown_by_dectcem() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[?25l\u001B[?25hHello"); // hide then show
        assertNotNull(h.renderToBitmap());
    }

    @Test
    public void cursor_style_changes_render_differently() {
        RenderHarness h1 = new RenderHarness(40, 10);
        h1.feed("\u001B[2 qHello"); // steady block

        RenderHarness h2 = new RenderHarness(40, 10);
        h2.feed("\u001B[4 qHello"); // steady underline

        DifferentialDamageTest.DiffResult r = DifferentialDamageTest.compareBitmaps(
            h1.renderToBitmap(), h2.renderToBitmap());
        assertFalse("Block and underline cursor should differ", r.equal);
    }

    @Test
    public void cursor_incremental_matches_full_with_block() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u001B[2 qHello World\nTest\n");
        DifferentialDamageTest.DiffResult r = DifferentialDamageTest.compareBitmaps(
            h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(r.equal);
    }
}
