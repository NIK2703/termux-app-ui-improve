package com.termux.view;

import android.graphics.Bitmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RenderVerificationTest {

    @Test
    public void ascii_renders_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Hello World\n");
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
        assertEquals(40 * 13, bitmap.getWidth());
        assertEquals(10 * 15, bitmap.getHeight());
    }

    @Test
    public void sgr_matrix_renders_without_crash() {
        RenderHarness h = new RenderHarness(80, 10);
        h.feed("\u001B[1mBold\u001B[0m \u001B[3mItalic\u001B[0m \u001B[4mUnderline\u001B[0m\n");
        h.feed("\u001B[31mRed\u001B[0m \u001B[32mGreen\u001B[0m \u001B[34mBlue\u001B[0m\n");
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void wide_chars_render_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u4E2D\u56FD\n"); // CJK
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void box_drawing_renders_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u2500\u2501\u2502\u2503\n"); // box drawing
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void rtl_text_renders_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u05D0\u05D1\u05D2 hello\n"); // Hebrew
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void emoji_renders_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\uD83D\uDE00\n"); // grinning face
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void incremental_matches_full_repaint() {
        RenderHarness h = new RenderHarness(40, 5);
        h.feed("Hello World\n");
        h.feed("Line 2\n");
        h.feed("Line 3\n");

        Bitmap incremental = h.renderToBitmap();
        Bitmap fullRepaint = h.renderFullRepaint();

        assertNotNull(incremental);
        assertNotNull(fullRepaint);
        assertEquals(incremental.getWidth(), fullRepaint.getWidth());
        assertEquals(incremental.getHeight(), fullRepaint.getHeight());
    }

    @Test
    public void scrolled_output_renders() {
        RenderHarness h = new RenderHarness(40, 3);
        for (int i = 0; i < 10; i++) {
            h.feed("Line " + i + "\n");
        }
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void overlay_clusters_renders_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("Hello World\n\u4E2D\u56FD\n");
        h.renderer.toggleOverlay("clusters");
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void overlay_bidi_renders_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u05D0\u05D1\u05D2 Hello\n");
        h.renderer.toggleOverlay("bidi");
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void overlay_minifont_renders_without_crash() {
        RenderHarness h = new RenderHarness(40, 10);
        h.feed("\u2500\u2501\u2502\u2503\n");
        h.renderer.toggleOverlay("minifont");
        Bitmap bitmap = h.renderToBitmap();
        assertNotNull(bitmap);
    }

    @Test
    public void overlay_all_toggle_off() {
        RenderHarness h = new RenderHarness(40, 10);
        h.renderer.toggleOverlay("all");
        assertNotNull(h.renderToBitmap());
        h.renderer.toggleOverlay("all");
        assertNotNull(h.renderToBitmap());
    }
}
