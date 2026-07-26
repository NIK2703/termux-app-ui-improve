package com.termux.view;

import android.graphics.Bitmap;

import com.termux.view.graphics.MinifontMetrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CacheGenerationTest {

    @Test
    public void font_generation_starts_at_zero() {
        RenderHarness h = new RenderHarness(40, 10);
        assertEquals(0, h.renderer.getFontGeneration());
    }

    @Test
    public void bump_font_generation_increments() {
        RenderHarness h = new RenderHarness(40, 10);
        assertEquals(1, h.renderer.bumpFontGeneration());
        assertEquals(2, h.renderer.bumpFontGeneration());
    }

    @Test
    public void bump_metrics_generation_increments() {
        RenderHarness h = new RenderHarness(40, 10);
        assertEquals(1, h.renderer.bumpMetricsGeneration());
    }

    @Test
    public void bump_minifont_generation_increments() {
        RenderHarness h = new RenderHarness(40, 10);
        assertEquals(1, h.renderer.bumpMinifontGeneration());
    }

    @Test
    public void bump_fallback_generation_increments() {
        RenderHarness h = new RenderHarness(40, 10);
        assertEquals(1, h.renderer.bumpFallbackGeneration());
    }

    @Test
    public void renderer_still_renders_after_bump_all_generations() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Hello\n");
        h.renderer.bumpFontGeneration();
        h.renderer.bumpMetricsGeneration();
        h.renderer.bumpMinifontGeneration();
        h.renderer.bumpFallbackGeneration();
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }

    @Test
    public void renderer_still_renders_minifont_after_bump() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u2500\u2501\u2502\n");
        h.renderer.bumpMinifontGeneration();
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }

    @Test
    public void renderer_still_renders_emoji_after_bump() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\uD83D\uDE00\n");
        h.renderer.bumpFontGeneration();
        h.renderer.bumpFallbackGeneration();
        Bitmap b = h.renderToBitmap();
        assertNotNull(b);
    }

    @Test
    public void minifont_metrics_cached_and_reused() {
        RenderHarness h = new RenderHarness(40, 10);
        h.renderer.bumpMinifontGeneration();
        h.renderer.getOrCreateMinifontMetrics(26, 15);
        MinifontMetrics first = h.renderer.getOrCreateMinifontMetrics(26, 15);
        assertEquals(26, first.cellWidth);
        assertEquals(15, first.cellHeight);
        assertEquals(1, first.generation);
        h.renderer.bumpMinifontGeneration();
        MinifontMetrics second = h.renderer.getOrCreateMinifontMetrics(26, 15);
        assertEquals(2, second.generation);
    }

    @Test
    public void incremental_equals_full_after_bump() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Hello World\nTest line 2\n");
        h.renderer.bumpFontGeneration();
        h.renderer.bumpMinifontGeneration();
        h.renderer.bumpFallbackGeneration();
        h.renderer.bumpMetricsGeneration();
        DifferentialDamageTest.DiffResult r =
            DifferentialDamageTest.compareBitmaps(h.renderToBitmap(), h.renderFullRepaint());
        assertTrue(DifferentialDamageTest.diffMessage(r), r.equal);
    }
}
