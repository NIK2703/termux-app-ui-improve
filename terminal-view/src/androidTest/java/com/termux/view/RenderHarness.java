package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class RenderHarness {

    public static final int CELL_W = 13;
    public static final int CELL_H = 15;

    public final TerminalEmulator terminal;
    public final TerminalRenderer renderer;
    public final MockTerminalOutput output;
    public final int columns;
    public final int rows;

    public RenderHarness(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        this.output = new MockTerminalOutput();
        this.terminal = new TerminalEmulator(output, columns, rows, CELL_W, CELL_H, rows * 2, null);
        this.renderer = new TerminalRenderer(CELL_H, Typeface.MONOSPACE);
    }

    public void feed(byte[] data) {
        terminal.append(data, data.length);
    }

    public void feed(String utf8) {
        feed(utf8.getBytes(StandardCharsets.UTF_8));
    }

    public int currentWidth() {
        return terminal.mColumns * CELL_W;
    }

    public int currentHeight() {
        return terminal.mRows * CELL_H;
    }

    public Bitmap renderToBitmap() {
        return renderWithFocus(true);
    }

    public Bitmap renderWithFocus(boolean focused) {
        int w = currentWidth();
        int h = currentHeight();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        renderer.render(terminal, canvas, 0, 0, -1, 0, 0, focused);
        return bitmap;
    }

    public Bitmap renderFullRepaint() {
        return renderFullRepaintWithFocus(true);
    }

    public Bitmap renderFullRepaintWithFocus(boolean focused) {
        TerminalRenderer fresh = new TerminalRenderer(CELL_H, Typeface.MONOSPACE);
        int w = currentWidth();
        int h = currentHeight();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        fresh.render(terminal, canvas, 0, 0, -1, 0, 0, focused);
        return bitmap;
    }

    public static class MockTerminalOutput extends TerminalOutput {
        public ByteArrayOutputStream baos = new ByteArrayOutputStream();

        @Override public void write(byte[] data, int offset, int count) {
            baos.write(data, offset, count);
        }

        public String getOutputAndClear() {
            String s = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            baos.reset();
            return s;
        }

        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }
}
