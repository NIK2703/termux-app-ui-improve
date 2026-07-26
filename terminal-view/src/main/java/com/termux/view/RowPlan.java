package com.termux.view;

import android.graphics.Picture;

import java.util.ArrayList;

public final class RowPlan {

    public static final int TYPE_TEXT = 1;
    public static final int TYPE_MINIFONT = 2;

    public long rowGeneration = Long.MIN_VALUE;
    public int bufferGeneration;
    public int columns;

    public int widthPx;
    public int heightPx;

    public int fontGeneration;
    public int paletteGeneration;
    public int blinkGeneration;

    public long selectionKey = Long.MIN_VALUE;

    public boolean hasBlink;

    public Picture picture;

    public final ArrayList<BackgroundRun> bgRuns = new ArrayList<>();
    public final ArrayList<DecorationPlanRun> preDecoRuns = new ArrayList<>();
    public final ArrayList<DecorationPlanRun> postDecoRuns = new ArrayList<>();
    public final ArrayList<GlyphSegment> segments = new ArrayList<>();

    public void clear() {
        rowGeneration = Long.MIN_VALUE;
        bufferGeneration = 0;
        columns = 0;
        widthPx = 0;
        heightPx = 0;
        fontGeneration = 0;
        paletteGeneration = 0;
        blinkGeneration = 0;
        selectionKey = Long.MIN_VALUE;
        hasBlink = false;
        picture = null;
        bgRuns.clear();
        preDecoRuns.clear();
        postDecoRuns.clear();
        segments.clear();
    }

    public boolean isValid(long rowGeneration, int bufferGeneration, int columns,
                           int fontGeneration, int paletteGeneration, int blinkGeneration,
                           long selectionKey, int widthPx, int heightPx) {
        if (this.rowGeneration != rowGeneration) return false;
        if (this.bufferGeneration != bufferGeneration) return false;
        if (this.columns != columns) return false;
        if (this.fontGeneration != fontGeneration) return false;
        if (this.paletteGeneration != paletteGeneration) return false;
        if (this.selectionKey != selectionKey) return false;
        if (this.widthPx != widthPx) return false;
        if (this.heightPx != heightPx) return false;
        if (hasBlink && this.blinkGeneration != blinkGeneration) return false;
        return true;
    }

    public static final class BackgroundRun {
        public final int startCol;
        public final int endCol;
        public final int color;

        public BackgroundRun(int startCol, int endCol, int color) {
            this.startCol = startCol;
            this.endCol = endCol;
            this.color = color;
        }
    }

    public static final class DecorationPlanRun {
        public final int startCol;
        public final int endCol;
        public final int color;
        public final int underlineVariant;
        public final boolean overline;
        public final boolean strikethrough;
        public final boolean boxed;

        public DecorationPlanRun(int startCol, int endCol, int color,
                                 int underlineVariant, boolean overline,
                                 boolean strikethrough, boolean boxed) {
            this.startCol = startCol;
            this.endCol = endCol;
            this.color = color;
            this.underlineVariant = underlineVariant;
            this.overline = overline;
            this.strikethrough = strikethrough;
            this.boxed = boxed;
        }
    }

    public static final class GlyphSegment {
        public int type;
        public int startCol;
        public int widthCols;
        public int charStart;
        public int charCount;
        public int firstCodePoint;
        public int paintId;
        public int color;
        public float scaleX = 1f;
        public float scaleY = 1f;
        public float drawOffset = 0f;
        public boolean isMirrored = false;
    }
}
