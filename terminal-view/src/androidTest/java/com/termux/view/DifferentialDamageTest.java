package com.termux.view;

import android.graphics.Bitmap;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DifferentialDamageTest {

    static final class DiffResult {
        final boolean equal;
        final Bitmap diff;
        final int firstDiffX;
        final int firstDiffY;

        DiffResult(boolean equal, Bitmap diff, int firstDiffX, int firstDiffY) {
            this.equal = equal;
            this.diff = diff;
            this.firstDiffX = firstDiffX;
            this.firstDiffY = firstDiffY;
        }
    }

    static DiffResult compareBitmaps(Bitmap a, Bitmap b) {
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        int w = a.getWidth();
        int h = a.getHeight();
        int[] pa = new int[w * h];
        int[] pb = new int[w * h];
        a.getPixels(pa, 0, w, 0, 0, w, h);
        b.getPixels(pb, 0, w, 0, 0, w, h);
        int firstDiff = -1;
        int diffCount = 0;
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) {
                if (firstDiff < 0) firstDiff = i;
                diffCount++;
            }
        }
        if (diffCount == 0) {
            return new DiffResult(true, null, -1, -1);
        }
        int diffX = firstDiff % w;
        int diffY = firstDiff / w;
        Bitmap diffBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] diffPixels = new int[w * h];
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) {
                diffPixels[i] = 0xFFFF0000;
            } else {
                diffPixels[i] = pa[i];
            }
        }
        diffBitmap.setPixels(diffPixels, 0, w, 0, 0, w, h);
        return new DiffResult(false, diffBitmap, diffX, diffY);
    }

    static String diffMessage(DiffResult r) {
        return String.format(Locale.US, "Pixel diff at (%d,%d)", r.firstDiffX, r.firstDiffY);
    }

    @Test
    public void incremental_matches_full_repaint_ascii() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Hello World\nThis is line 2\nAnd line 3\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_cjk() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Hello \u4E2D\u56FD\n\u6B22\u8FCE Java\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_box_drawing() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u250C\u2500\u2500\u2510\n\u2502  \u2502\n\u2514\u2500\u2500\u2518\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_rtl() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u05D0\u05D1\u05D2 hello\n\u05D3\u05D4\u05D5 world\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_emoji() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_sgr() {
        RenderHarness h = new RenderHarness(80, 10);
        h.feed("\u001B[31mRed\u001B[0m \u001B[32;1mGreen Bold\u001B[0m \u001B[44mBlue BG\u001B[0m\n");
        h.feed("\u001B[4mUnderline\u001B[0m \u001B[9mStrikethrough\u001B[0m\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_scrolled() {
        RenderHarness h = new RenderHarness(40, 5);
        for (int i = 0; i < 20; i++) {
            h.feed("Scroll line " + i + "\n");
        }
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_cursor_move() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("First line\n");
        h.feed("\u001B[3;5H"); // move to row 3 col 5
        h.feed("Middle");
        h.feed("\u001B[5;10H"); // move to row 5 col 10
        h.feed("End");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_mixed() {
        RenderHarness h = new RenderHarness(60, 10);
        h.feed("ASCII text\n");
        h.feed("\u4E2D\u56FD\u6B22\u8FCE\n");
        h.feed("\u2500\u2501\u2502\u2503\u250C\u2510\n");
        h.feed("\u05D0\u05D1\u05D2 hello \u05D3\u05D4\n");
        h.feed("\uD83D\uDE00 grin \uD83D\uDE42 smirk\n");
        h.feed("\u001B[31mRed\u001B[32mGreen\u001B[34mBlue\u001B[0m\n");
        h.feed("\u001B[4mUnder\u001B[0m \u001B[1mBold\u001B[0m\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_resize() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Hello World\n");
        h.terminal.resize(80, 10, RenderHarness.CELL_W, RenderHarness.CELL_H);
        h.feed("After resize wider text here\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }

    @Test
    public void incremental_matches_full_repaint_resize_taller() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Line 1\nLine 2\nLine 3\n");
        h.terminal.resize(40, 20, RenderHarness.CELL_W, RenderHarness.CELL_H);
        h.feed("After taller resize\n");
        DiffResult r = compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(diffMessage(r), r.equal);
    }
}
