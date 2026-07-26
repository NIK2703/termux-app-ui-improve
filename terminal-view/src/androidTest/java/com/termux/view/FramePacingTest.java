package com.termux.view;

import android.graphics.Bitmap;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertNotNull;

public class FramePacingTest {

    @Test
    public void burst_ascii_output_renders_stable() {
        RenderHarness h = new RenderHarness(80, 25);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("This is line ").append(i).append(" of burst output test\n");
        }
        h.feed(sb.toString());
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }

    @Test
    public void burst_random_emoji_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        int[] emoji = {0x1F600, 0x1F601, 0x1F602, 0x1F603, 0x1F604,
                       0x1F605, 0x1F606, 0x1F607, 0x1F609, 0x1F60A};
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random(42);
        for (int i = 0; i < 500; i++) {
            int cp = emoji[rnd.nextInt(emoji.length)];
            sb.appendCodePoint(cp);
            if (i % 40 == 39) sb.append('\n');
        }
        h.feed(sb.toString());
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }

    @Test
    public void burst_cjk_output_renders() {
        RenderHarness h = new RenderHarness(40, 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.appendCodePoint(0x4E00 + (i % 100));
            if (i % 20 == 19) sb.append('\n');
        }
        h.feed(sb.toString());
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }

    @Test
    public void incremental_vs_full_after_burst() {
        RenderHarness h = new RenderHarness(80, 25);
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            h.feed("Rapid output line " + i + " with \u4E2D\u56FD and \uD83D\uDE00\n");
        }
        long feedTime = System.nanoTime() - start;
        assertNotNull(h.renderToBitmap());
        System.out.println("Feed 500 lines took " + (feedTime / 1_000_000) + "ms");

        DifferentialDamageTest.DiffResult r =
            DifferentialDamageTest.compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertNotNull(r);
    }

    @Test
    public void rapid_sgr_burst_renders() {
        RenderHarness h = new RenderHarness(80, 25);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            String[] colors = {"31", "32", "33", "34", "35", "36", "37"};
            String c = colors[i % colors.length];
            sb.append("\u001B[").append(c).append("mColor ").append(c).append("\u001B[0m ");
            if (i % 10 == 9) sb.append('\n');
        }
        h.feed(sb.toString());
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }

    @Test
    public void burst_minifont_renders() {
        RenderHarness h = new RenderHarness(40, 20);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("\u2500\u2501\u2502\u2503\u250C\u2510\u2514\u2518");
            if (i % 5 == 4) sb.append('\n');
        }
        h.feed(sb.toString());
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }
}
