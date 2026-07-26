package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MinifontDpiTest {

    private static final String BOX_CHARS = "\u2500\u2501\u2502\u2503\u250C\u2510\u2514\u2518\u252C\u2534\u253C";
    private static final String BLOCK_CHARS = "\u2580\u2584\u2588\u258C\u2590\u2591\u2592\u2593";
    private static final String BRAILLE = "\u2800\u2801\u283F\u28FF";
    private static final String LEGACY_POLY = "\uD83E\uDC3C\uD83E\uDC3D\uD83E\uDC3E\uD83E\uDC3F";
    private static final String OCTANTS = "\uD83E\uDD70\uD83E\uDD71\uD83E\uDD72\uD83E\uDD73";
    private static final String ALL_MINIFONT =
        BOX_CHARS + BLOCK_CHARS + BRAILLE + "\n" + LEGACY_POLY + OCTANTS + "\n";

    static int cellHeight(int textSize) {
        return Math.round(new TerminalRenderer(textSize, Typeface.MONOSPACE).getFontLineSpacing());
    }

    static Bitmap renderToBitmap(TerminalRenderer renderer,
                                  com.termux.terminal.TerminalEmulator terminal,
                                  int columns, int rows) {
        int h = rows * renderer.getFontLineSpacing();
        int w = Math.round(columns * renderer.getFontWidth());
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        renderer.render(terminal, canvas, 0, 0, -1, 0, 0, true);
        return bitmap;
    }

    static Bitmap renderMinifont(int textSize, int columns, int rows, String text) {
        TerminalRenderer r = new TerminalRenderer(textSize, Typeface.MONOSPACE);
        RenderHarness.MockTerminalOutput output = new RenderHarness.MockTerminalOutput();
        com.termux.terminal.TerminalEmulator terminal =
            new com.termux.terminal.TerminalEmulator(output, columns, rows,
                Math.round(r.getFontWidth()), r.getFontLineSpacing(), rows * 2, null);
        terminal.append(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        return renderToBitmap(r, terminal, columns, rows);
    }

    static int nonBgPixelCount(Bitmap b, int bgColor) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] pixels = new int[w * h];
        b.getPixels(pixels, 0, w, 0, 0, w, h);
        int count = 0;
        for (int p : pixels) {
            if (p != bgColor) count++;
        }
        return count;
    }

    static Bitmap renderFullRepaint(
            com.termux.terminal.TerminalEmulator terminal,
            int textSize, int columns, int rows) {
        TerminalRenderer fresh = new TerminalRenderer(textSize, Typeface.MONOSPACE);
        return renderToBitmap(fresh, terminal, columns, rows);
    }

    @Test
    public void size_8() {
        Bitmap b = renderMinifont(8, 40, 10, ALL_MINIFONT);
        assertNotNull(b);
        assertTrue(nonBgPixelCount(b, 0xFF000000) > 0);
    }

    @Test
    public void size_12() {
        Bitmap b = renderMinifont(12, 40, 10, ALL_MINIFONT);
        assertNotNull(b);
        assertTrue(nonBgPixelCount(b, 0xFF000000) > 0);
    }

    @Test
    public void size_16() {
        Bitmap b = renderMinifont(16, 40, 10, ALL_MINIFONT);
        assertNotNull(b);
        assertTrue(nonBgPixelCount(b, 0xFF000000) > 0);
    }

    @Test
    public void size_20() {
        Bitmap b = renderMinifont(20, 40, 10, ALL_MINIFONT);
        assertNotNull(b);
        assertTrue(nonBgPixelCount(b, 0xFF000000) > 0);
    }

    @Test
    public void size_24() {
        Bitmap b = renderMinifont(24, 40, 10, ALL_MINIFONT);
        assertNotNull(b);
        assertTrue(nonBgPixelCount(b, 0xFF000000) > 0);
    }

    @Test
    public void different_sizes_produce_different_output() {
        Bitmap b8 = renderMinifont(8, 40, 10, BOX_CHARS + "\n");
        Bitmap b16 = renderMinifont(16, 40, 10, BOX_CHARS + "\n");
        int p8 = nonBgPixelCount(b8, 0xFF000000);
        int p16 = nonBgPixelCount(b16, 0xFF000000);
        assertTrue("Size 8 pixel count=" + p8, p8 > 0);
        assertTrue("Size 16 pixel count=" + p16, p16 > 0);
        assertTrue("Sizes " + p8 + "/" + p16 + " should differ",
            Math.abs(p8 - p16) > Math.max(p8, p16) / 10);
    }

    @Test
    public void box_drawing_parity_size_12() {
        int textSize = 12;
        int cols = 40, rows = 10;
        TerminalRenderer r = new TerminalRenderer(textSize, Typeface.MONOSPACE);
        RenderHarness.MockTerminalOutput output = new RenderHarness.MockTerminalOutput();
        com.termux.terminal.TerminalEmulator terminal =
            new com.termux.terminal.TerminalEmulator(output, cols, rows,
                Math.round(r.getFontWidth()), r.getFontLineSpacing(), rows * 2, null);
        terminal.append(BOX_CHARS.getBytes(java.nio.charset.StandardCharsets.UTF_8), BOX_CHARS.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Bitmap inc = renderToBitmap(r, terminal, cols, rows);
        Bitmap full = renderFullRepaint(terminal, textSize, cols, rows);
        DifferentialDamageTest.DiffResult dr =
            DifferentialDamageTest.compareBitmaps(inc, full);
        assertTrue("Box drawing parity at 12px", dr.equal);
    }

    @Test
    public void braille_parity_size_16() {
        int textSize = 16;
        int cols = 40, rows = 10;
        TerminalRenderer r = new TerminalRenderer(textSize, Typeface.MONOSPACE);
        RenderHarness.MockTerminalOutput output = new RenderHarness.MockTerminalOutput();
        com.termux.terminal.TerminalEmulator terminal =
            new com.termux.terminal.TerminalEmulator(output, cols, rows,
                Math.round(r.getFontWidth()), r.getFontLineSpacing(), rows * 2, null);
        terminal.append(BRAILLE.getBytes(java.nio.charset.StandardCharsets.UTF_8), BRAILLE.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Bitmap inc = renderToBitmap(r, terminal, cols, rows);
        Bitmap full = renderFullRepaint(terminal, textSize, cols, rows);
        DifferentialDamageTest.DiffResult dr =
            DifferentialDamageTest.compareBitmaps(inc, full);
        assertTrue("Braille parity at 16px", dr.equal);
    }

    @Test
    public void horizontal_line_no_vertical_seams() {
        int textSize = 16;
        int cols = 10, rows = 3;
        String line = "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n";
        TerminalRenderer r = new TerminalRenderer(textSize, Typeface.MONOSPACE);
        RenderHarness.MockTerminalOutput output = new RenderHarness.MockTerminalOutput();
        com.termux.terminal.TerminalEmulator terminal =
            new com.termux.terminal.TerminalEmulator(output, cols, rows,
                Math.round(r.getFontWidth()), r.getFontLineSpacing(), rows * 2, null);
        terminal.append(line.getBytes(java.nio.charset.StandardCharsets.UTF_8), line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Bitmap inc = renderToBitmap(r, terminal, cols, rows);
        Bitmap full = renderFullRepaint(terminal, textSize, cols, rows);
        DifferentialDamageTest.DiffResult dr =
            DifferentialDamageTest.compareBitmaps(inc, full);
        assertTrue("Horizontal line seam parity at 16px", dr.equal);
    }

    @Test
    public void vertical_line_no_horizontal_seams() {
        int textSize = 16;
        int cols = 3, rows = 10;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows; i++) sb.append("\u2502\n");
        TerminalRenderer r = new TerminalRenderer(textSize, Typeface.MONOSPACE);
        RenderHarness.MockTerminalOutput output = new RenderHarness.MockTerminalOutput();
        com.termux.terminal.TerminalEmulator terminal =
            new com.termux.terminal.TerminalEmulator(output, cols, rows,
                Math.round(r.getFontWidth()), r.getFontLineSpacing(), rows * 2, null);
        terminal.append(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Bitmap inc = renderToBitmap(r, terminal, cols, rows);
        Bitmap full = renderFullRepaint(terminal, textSize, cols, rows);
        DifferentialDamageTest.DiffResult dr =
            DifferentialDamageTest.compareBitmaps(inc, full);
        assertTrue("Vertical line seam parity at 16px", dr.equal);
    }

    @Test
    public void legacy_poly_parity_size_20() {
        int textSize = 20;
        int cols = 40, rows = 10;
        TerminalRenderer r = new TerminalRenderer(textSize, Typeface.MONOSPACE);
        RenderHarness.MockTerminalOutput output = new RenderHarness.MockTerminalOutput();
        com.termux.terminal.TerminalEmulator terminal =
            new com.termux.terminal.TerminalEmulator(output, cols, rows,
                Math.round(r.getFontWidth()), r.getFontLineSpacing(), rows * 2, null);
        terminal.append(LEGACY_POLY.getBytes(java.nio.charset.StandardCharsets.UTF_8), LEGACY_POLY.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Bitmap inc = renderToBitmap(r, terminal, cols, rows);
        Bitmap full = renderFullRepaint(terminal, textSize, cols, rows);
        DifferentialDamageTest.DiffResult dr =
            DifferentialDamageTest.compareBitmaps(inc, full);
        assertTrue("Legacy poly parity at 20px", dr.equal);
    }
}
