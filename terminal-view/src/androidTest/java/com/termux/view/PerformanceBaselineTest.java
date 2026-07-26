package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;

import com.termux.terminal.TerminalEmulator;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

public final class PerformanceBaselineTest {

    private static final int TEXT_SIZE = 15;
    private static final int WARMUP_RUNS = 10;
    private static final int MEASURE_RUNS = 30;

    private static long measureRenderUs(RenderHarness h) {
        long start = System.nanoTime();
        Bitmap b = h.renderToBitmap();
        long elapsed = System.nanoTime() - start;
        assertNotNull(b);
        return elapsed / 1000;
    }

    private static long[] measureRender(RenderHarness h) {
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int r = 0; r < WARMUP_RUNS + MEASURE_RUNS; r++) {
            long t = measureRenderUs(h);
            if (r >= WARMUP_RUNS) {
                sum += t;
                if (t < min) min = t;
                if (t > max) max = t;
            }
        }
        return new long[]{sum / MEASURE_RUNS, min, max};
    }

    private static String fmt(long[] stats) {
        return String.format(Locale.US, "avg=%dus min=%dus max=%dus", stats[0], stats[1], stats[2]);
    }

    private static void printCacheStats(TerminalRenderer r, String label) {
        FontFallbackCache cache = r.mFontFallback;
        int hits = cache.cacheHitCount;
        int misses = cache.cacheMissCount;
        int total = hits + misses;
        double hitRate = total > 0 ? 100.0 * hits / total : 0;
        System.out.println(label + " cache: hit=" + hits + " miss=" + misses
            + " size=" + cache.cacheSize() + " rate=" + String.format(Locale.US, "%.1f%%", hitRate));
    }

    @Test
    public void baseline_ascii() {
        RenderHarness h = new RenderHarness(80, 25);
        h.feed("Hello World\nThis is line 2\nAnd line 3\n");
        h.feed("The quick brown fox jumps over the lazy dog\n");
        long[] stats = measureRender(h);
        System.out.println("baseline_ascii: " + fmt(stats));
        printCacheStats(h.renderer, "ascii");
        assertTrue("ASCII render too slow: " + stats[0], stats[0] < 20000);
    }

    @Test
    public void baseline_cjk() {
        RenderHarness h = new RenderHarness(80, 25);
        h.feed("\u4E2D\u56FD\u6B22\u8FCE\u60A8\n");
        h.feed("\u7F8E\u56FD \u82F1\u56FD \u6CD5\u56FD \u5FB7\u56FD\n".repeat(5));
        long[] stats = measureRender(h);
        System.out.println("baseline_cjk: " + fmt(stats));
        printCacheStats(h.renderer, "cjk");
        assertTrue("CJK render too slow: " + stats[0], stats[0] < 50000);
    }

    @Test
    public void baseline_emoji() {
        RenderHarness h = new RenderHarness(80, 25);
        h.feed("\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04\n");
        h.feed("\uD83D\uDE05\uD83D\uDE06\uD83D\uDE07\uD83D\uDE08\uD83D\uDE09\n".repeat(5));
        long[] stats = measureRender(h);
        System.out.println("baseline_emoji: " + fmt(stats));
        printCacheStats(h.renderer, "emoji");
        assertTrue("Emoji render too slow: " + stats[0], stats[0] < 50000);
    }

    @Test
    public void baseline_box_drawing() {
        RenderHarness h = new RenderHarness(80, 25);
        h.feed("\u250C\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510\n");
        h.feed("\u2502  \u2502\n".repeat(10));
        h.feed("\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518\n");
        long[] stats = measureRender(h);
        System.out.println("baseline_box: " + fmt(stats));
        printCacheStats(h.renderer, "box");
        assertTrue("Box render too slow: " + stats[0], stats[0] < 30000);
    }

    @Test
    public void baseline_sgr() {
        RenderHarness h = new RenderHarness(80, 25);
        h.feed("\u001B[31mRed\u001B[0m \u001B[32;1mGreen Bold\u001B[0m \u001B[44mBlue BG\u001B[0m\n");
        h.feed("\u001B[4mUnderline\u001B[0m \u001B[9mStrikethrough\u001B[0m \u001B[53mOverline\u001B[0m\n");
        h.feed("\u001B[1;31mBold Red\u001B[0m \u001B[3;32mItalic Green\u001B[0m\n".repeat(5));
        long[] stats = measureRender(h);
        System.out.println("baseline_sgr: " + fmt(stats));
        printCacheStats(h.renderer, "sgr");
        assertTrue("SGR render too slow: " + stats[0], stats[0] < 50000);
    }

    @Test
    public void baseline_mixed() {
        RenderHarness h = new RenderHarness(80, 25);
        for (int i = 0; i < 5; i++) {
            h.feed("ASCII text with \u4E2D\u56FD and \uD83D\uDE00 emoji\n");
            h.feed("\u2500\u2501\u2502 box \u05D0\u05D1\u05D2 RTL\n");
        }
        long[] stats = measureRender(h);
        System.out.println("baseline_mixed: " + fmt(stats));
        printCacheStats(h.renderer, "mixed");
        assertTrue("Mixed render too slow: " + stats[0], stats[0] < 80000);
    }

    @Test
    public void baseline_scrolled() {
        RenderHarness h = new RenderHarness(80, 25);
        for (int i = 0; i < 100; i++) {
            h.feed("Scroll line " + i + " with \u4E2D\u56FD and \uD83D\uDE00\n");
        }
        long[] stats = measureRender(h);
        System.out.println("baseline_scrolled: " + fmt(stats));
        printCacheStats(h.renderer, "scrolled");
        assertTrue("Scrolled render too slow: " + stats[0], stats[0] < 80000);
    }

    @Test
    public void cache_hit_rate_is_high() {
        RenderHarness h = new RenderHarness(80, 25);
        h.renderer.mFontFallback.resetStats();
        h.feed("\u4E2D\u56FD\u6B22\u8FCE\u60A8 \uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\n");
        h.feed("\u250C\u2500\u2500\u2510\u2502  \u2502\u2514\u2500\u2500\u2518\n");
        for (int i = 0; i < 5; i++) {
            h.renderToBitmap();
        }
        FontFallbackCache cache = h.renderer.mFontFallback;
        int total = cache.cacheHitCount + cache.cacheMissCount;
        double hitRate = total > 0 ? 100.0 * cache.cacheHitCount / total : 0;
        System.out.println("cache hit rate after 5 renders: "
            + String.format(Locale.US, "%.1f%%", hitRate)
            + " (hit=" + cache.cacheHitCount + " miss=" + cache.cacheMissCount + ")");
        assertTrue("Cache hit rate too low: " + hitRate, hitRate > 50);
    }

    @Test
    public void full_repaint_vs_incremental_ratio() {
        RenderHarness h = new RenderHarness(80, 25);
        for (int i = 0; i < 20; i++) {
            h.feed("Mixed content line " + i + " with \u4E2D\u56FD and \uD83D\uDE00\n");
        }
        long incAvg = measureRender(h)[0];
        long fullSum = 0;
        for (int r = 0; r < WARMUP_RUNS; r++) {
            h.renderFullRepaint();
        }
        for (int r = 0; r < MEASURE_RUNS; r++) {
            long start = System.nanoTime();
            Bitmap b = h.renderFullRepaint();
            fullSum += System.nanoTime() - start;
            assertNotNull(b);
        }
        long fullAvg = fullSum / (MEASURE_RUNS * 1000);
        long ratio = incAvg > 0 ? fullAvg / incAvg : -1;
        System.out.println("incremental avg=" + incAvg + "us full_repaint avg=" + fullAvg
            + "us ratio=" + ratio + "x");
        assertTrue("Cold repaint should finish within 2s", fullAvg < 2000000);
    }
}
