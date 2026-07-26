package com.termux.view.text;

import com.termux.terminal.TerminalRow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RowLayoutTest {

    private TerminalRow makeRow(int columns, String text) {
        TerminalRow row = new TerminalRow(columns, 0L);
        for (int i = 0; i < text.length(); i++) {
            row.setChar(i, text.charAt(i), 0L);
        }
        return row;
    }

    private TerminalRow makeRowWithCodePoints(int columns, int... cps) {
        TerminalRow row = new TerminalRow(columns, 0L);
        int col = 0;
        for (int i = 0; i < cps.length; i++) {
            row.setChar(col, cps[i], 0L);
            int w = com.termux.terminal.WcWidth.width(cps[i]);
            col += w;
        }
        return row;
    }

    @Test
    public void ascii() {
        TerminalRow row = makeRow(5, "hello");
        RowLayout layout = new RowLayout(row, 5);
        for (int i = 0; i < 5; i++) {
            assertEquals("hello".charAt(i), (char) layout.codePoints[i]);
            assertEquals(1, layout.widths[i]);
            assertFalse(layout.isWide[i] + " col " + i, layout.isWide[i]);
            assertFalse(layout.isFragment[i] + " col " + i, layout.isFragment[i]);
            assertFalse(layout.isMinifont[i] + " col " + i, layout.isMinifont[i]);
        }
    }

    @Test
    public void wide_char() {
        TerminalRow row = makeRowWithCodePoints(5, 0x4E00, 'a');
        RowLayout layout = new RowLayout(row, 5);
        assertEquals(0x4E00, layout.codePoints[0]);
        assertEquals(2, layout.widths[0]);
        assertTrue(layout.isWide[0]);
        assertFalse(layout.isFragment[0]);
        assertEquals(0, layout.codePoints[1]);
        assertEquals(0, layout.widths[1]);
        assertTrue(layout.isFragment[1]);
        assertEquals('a', layout.codePoints[2]);
        assertEquals(1, layout.widths[2]);
    }

    @Test
    public void two_wide_chars() {
        TerminalRow row = makeRowWithCodePoints(5, 0x4E00, 0x4E00, 'a');
        RowLayout layout = new RowLayout(row, 5);
        assertEquals(0x4E00, layout.codePoints[0]);
        assertTrue(layout.isWide[0]);
        assertEquals(0x4E00, layout.codePoints[2]);
        assertTrue(layout.isWide[2]);
        assertEquals('a', layout.codePoints[4]);
    }

    @Test
    public void minifont_detection() {
        int boxDrawH = 0x2500;
        TerminalRow row = makeRowWithCodePoints(3, boxDrawH, 'a', 'b');
        RowLayout layout = new RowLayout(row, 3);
        assertTrue(layout.isMinifont[0]);
        assertFalse(layout.isMinifont[1]);
        assertFalse(layout.isMinifont[2]);
    }

    @Test
    public void spaces_have_width_1() {
        TerminalRow row = new TerminalRow(10, 0L);
        RowLayout layout = new RowLayout(row, 10);
        for (int i = 0; i < 10; i++) {
            assertEquals(1, layout.widths[i]);
            assertFalse(layout.isWide[i]);
        }
    }

    @Test
    public void cluster_getter() {
        TerminalRow row = makeRowWithCodePoints(10, 'A', 'B', 0x4E00, 'C', 'D');
        RowLayout layout = new RowLayout(row, 10);

        RowLayout.Cluster c = layout.getCluster(0);
        assertEquals('A', c.firstCodePoint);
        assertEquals(0, c.startCol);
        assertEquals(1, c.widthColumns);
        assertFalse(c.isWide);
        assertFalse(c.isFragment);

        RowLayout.Cluster wide = layout.getCluster(2);
        assertEquals(0x4E00, wide.firstCodePoint);
        assertTrue(wide.isWide);

        RowLayout.Cluster frag = layout.getCluster(3);
        assertEquals(0, frag.firstCodePoint);
        assertEquals(-1, frag.charStart);
        assertTrue(frag.isFragment);
    }

    @Test
    public void rebuild_clears_previous_state() {
        TerminalRow row = makeRowWithCodePoints(10, 0x4E00, 'a');
        RowLayout layout = new RowLayout(row, 10);
        assertTrue(layout.isWide[0]);
        assertTrue(layout.isFragment[1]);

        row.setChar(0, 'x', 0L);
        row.setChar(1, 'y', 0L);
        layout.rebuild(row);
        assertFalse(layout.isWide[0]);
        assertFalse(layout.isFragment[0]);
        assertEquals('x', layout.codePoints[0]);
        assertEquals('y', layout.codePoints[1]);
    }
}
