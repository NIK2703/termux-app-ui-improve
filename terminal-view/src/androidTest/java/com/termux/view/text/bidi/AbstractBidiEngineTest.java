package com.termux.view.text.bidi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AbstractBidiEngineTest {

    private static class MockEngine extends AbstractBidiEngine {
        private final byte[] levels;

        MockEngine(byte[] levels) {
            this.levels = levels;
        }

        @Override
        protected byte[] computeCharLevels(BidiInput input) {
            return levels;
        }
    }

    private BidiInput makeInput(String text, int baseDir) {
        BidiInput input = new BidiInput();
        input.text = text.toCharArray();
        input.textStart = 0;
        input.textCount = text.length();
        input.clusterCount = text.length();
        input.clusterWidth = new int[text.length()];
        input.clusterLogicalBaseColumn = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            input.clusterWidth[i] = 1;
            input.clusterLogicalBaseColumn[i] = i;
        }
        input.baseDirection = baseDir;
        return input;
    }

    @Test
    public void identity_no_bidi() {
        MockEngine engine = new MockEngine(new byte[]{0, 0, 0, 0, 0});
        BidiInput input = makeInput("hello", BidiDirection.AUTO_LTR);
        BidiResult result = engine.compute(input, 5);
        assertFalse(result.hasBidi);
        assertEquals(5, result.clusterCount);
        for (int i = 0; i < 5; i++) {
            assertEquals(i, result.visualToLogicalColumn[i]);
            assertEquals(i, result.logicalToVisualColumn[i]);
        }
    }

    @Test
    public void all_rtl_no_reorder() {
        MockEngine engine = new MockEngine(new byte[]{1, 1, 1});
        BidiInput input = makeInput("\u05D0\u05D1\u05D2", BidiDirection.AUTO_LTR);
        BidiResult result = engine.compute(input, 3);
        assertTrue(result.hasBidi);
        assertEquals(2, result.visualClusterOrder[0]);
        assertEquals(1, result.visualClusterOrder[1]);
        assertEquals(0, result.visualClusterOrder[2]);
    }

    @Test
    public void mixed_ltr_rtl() {
        byte[] levels = new byte[]{0, 0, 0, 1, 1, 1, 1, 0, 0, 0};
        MockEngine engine = new MockEngine(levels);
        BidiInput input = makeInput("abc\u05D0\u05D1\u05D2\u05D3def", BidiDirection.AUTO_LTR);
        BidiResult result = engine.compute(input, 10);
        assertTrue(result.hasBidi);
    }

    @Test
    public void expand_wide_clusters() {
        byte[] levels = new byte[]{1, 1};
        MockEngine engine = new MockEngine(levels);
        BidiInput input = new BidiInput();
        input.text = "\u05D0\u05D1".toCharArray();
        input.textStart = 0;
        input.textCount = 2;
        input.clusterCount = 2;
        input.clusterWidth = new int[]{1, 2};
        input.clusterLogicalBaseColumn = new int[]{0, 1};
        input.baseDirection = BidiDirection.AUTO_LTR;
        BidiResult result = engine.compute(input, 5);
        assertNotNull(result.visualToLogicalColumn);
        assertNotNull(result.logicalToVisualColumn);
    }

    @Test
    public void identity_for_single_char() {
        MockEngine engine = new MockEngine(new byte[]{0});
        BidiInput input = makeInput("x", BidiDirection.AUTO_LTR);
        BidiResult result = engine.compute(input, 1);
        assertFalse(result.hasBidi);
    }

    @Test
    public void has_mixed_levels_true_when_rtl() {
        assertTrue(hasMixedLevels(new byte[]{1, 1, 1}));
    }

    @Test
    public void has_mixed_levels_true_when_mixed() {
        assertTrue(hasMixedLevels(new byte[]{0, 1, 0}));
    }

    @Test
    public void has_mixed_levels_false_when_all_zero() {
        assertFalse(hasMixedLevels(new byte[]{0, 0, 0}));
    }

    @Test
    public void has_mixed_levels_false_when_single() {
        assertFalse(hasMixedLevels(new byte[]{0}));
    }

    private static boolean hasMixedLevels(byte[] levels) {
        if (levels.length <= 1) return false;
        byte first = levels[0];
        for (int i = 1; i < levels.length; i++) {
            if (levels[i] != first) return true;
        }
        for (byte level : levels) {
            if ((level & 1) == 1) return true;
        }
        return false;
    }
}
