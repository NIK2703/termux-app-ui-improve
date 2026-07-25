package com.termux.view.text;

import com.termux.terminal.TerminalRow;
import com.termux.terminal.WcWidth;
import com.termux.view.graphics.Minifont;

public final class RowLayout {

    public final int[] codePoints;
    public final int[] widths;
    public final int[] charStarts;
    public final int[] charCounts;
    public final int[] codePointCounts;
    public final boolean[] isMinifont;
    public final boolean[] isWide;
    public final boolean[] isFragment;
    public final boolean[] hasVariationSelector;
    public final boolean[] isEmojiCluster;
    public final int columnCount;

    private static final int VS15 = 0xFE0E;
    private static final int VS16 = 0xFE0F;
    private static final int ZWJ = 0x200D;
    private static final int REGIONAL_INDICATOR_START = 0x1F1E6;
    private static final int REGIONAL_INDICATOR_END = 0x1F1FF;
    private static final int SKIN_TONE_START = 0x1F3FB;
    private static final int SKIN_TONE_END = 0x1F3FF;

    public RowLayout(TerminalRow row, int columns) {
        this.columnCount = columns;
        this.codePoints = new int[columns];
        this.widths = new int[columns];
        this.charStarts = new int[columns];
        this.charCounts = new int[columns];
        this.codePointCounts = new int[columns];
        this.isMinifont = new boolean[columns];
        this.isWide = new boolean[columns];
        this.isFragment = new boolean[columns];
        this.hasVariationSelector = new boolean[columns];
        this.isEmojiCluster = new boolean[columns];

        char[] text = row.mText;
        int spaceUsed = row.getSpaceUsed();

        int col = 0;
        int charIdx = 0;
        while (col < columns && charIdx < spaceUsed) {
            char c = text[charIdx];
            boolean isHigh = Character.isHighSurrogate(c);
            int cp = isHigh ? Character.toCodePoint(c, text[charIdx + 1]) : c;
            int cpChars = isHigh ? 2 : 1;
            int wcWidth = WcWidth.width(cp);

            if (wcWidth > 0) {
                int startIdx = charIdx;
                int cpCount = 1;
                boolean vsSeen = false;
                charIdx += cpChars;

                boolean afterZwj = false;
                while (charIdx < spaceUsed) {
                    char nc = text[charIdx];
                    boolean ncHigh = Character.isHighSurrogate(nc);
                    int ncp = ncHigh ? Character.toCodePoint(nc, text[charIdx + 1]) : nc;
                    int ncw = WcWidth.width(ncp);

                    if (ncp == ZWJ) {
                        charIdx += ncHigh ? 2 : 1;
                        cpCount++;
                        afterZwj = true;
                        continue;
                    }

                    if (afterZwj) {
                        charIdx += ncHigh ? 2 : 1;
                        cpCount++;
                        afterZwj = false;
                        continue;
                    }

                    if (ncw > 0) break;

                    if (ncp == VS15 || ncp == VS16) vsSeen = true;
                    charIdx += ncHigh ? 2 : 1;
                    cpCount++;
                }

                int clusterChars = charIdx - startIdx;

                codePoints[col] = cp;
                widths[col] = wcWidth;
                charStarts[col] = startIdx;
                charCounts[col] = clusterChars;
                codePointCounts[col] = cpCount;
                hasVariationSelector[col] = vsSeen;
                isEmojiCluster[col] = !Minifont.isLocalGraphic(cp) && (cp >= 0x1F000 || isRegionalOrSkinTone(cp));

                if (wcWidth == 2 && col + 1 < columns) {
                    isWide[col] = true;
                    isFragment[col] = false;
                    codePoints[col + 1] = 0;
                    widths[col + 1] = 0;
                    charStarts[col + 1] = -1;
                    charCounts[col + 1] = 0;
                    codePointCounts[col + 1] = 0;
                    isMinifont[col + 1] = false;
                    isWide[col + 1] = true;
                    isFragment[col + 1] = true;
                    hasVariationSelector[col + 1] = false;
                    isEmojiCluster[col + 1] = false;
                } else {
                    isWide[col] = false;
                    isFragment[col] = false;
                }

                isMinifont[col] = Minifont.isLocalGraphic(cp);
                col += wcWidth;
            } else {
                charIdx += cpChars;
            }
        }
    }

    private static boolean isRegionalOrSkinTone(int cp) {
        return (cp >= REGIONAL_INDICATOR_START && cp <= REGIONAL_INDICATOR_END)
            || (cp >= SKIN_TONE_START && cp <= SKIN_TONE_END);
    }
}
