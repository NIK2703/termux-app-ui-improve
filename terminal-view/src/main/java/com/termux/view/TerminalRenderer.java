package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.util.SparseArray;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;
import com.termux.view.graphics.DecorationPainter;
import com.termux.view.graphics.Minifont;
import com.termux.view.text.RowLayout;

public final class TerminalRenderer {

    private static final int PAINT_NORMAL = 0;
    private static final int PAINT_BOLD = 1;
    private static final int PAINT_ITALIC = 2;
    private static final int PAINT_BOLD_ITALIC = 3;

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint[] mPaints = new Paint[4];
    private final float[][] mAsciiWidths = new float[4][128];
    final boolean mBoldRejected;
    private final SparseArray<Float> mNonAsciiAdvanceCache = new SparseArray<>();

    final float mFontWidth;
    final int mFontLineSpacing;
    private final int mFontAscent;
    final int mFontLineSpacingAndAscent;

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        Paint normalPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        normalPaint.setTypeface(typeface);
        normalPaint.setTextSize(textSize);
        normalPaint.setLetterSpacing(0f);

        mFontLineSpacing = (int) Math.ceil(normalPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(normalPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;

        float[] normalWidths = new float[128];
        char[] one = new char[1];
        for (int c = 32; c < 127; c++) {
            one[0] = (char) c;
            normalWidths[c] = normalPaint.measureText(one, 0, 1);
        }
        mFontWidth = normalWidths['X'];

        Typeface boldTf = Typeface.create(typeface, Typeface.BOLD);
        boolean hasRealBold = (boldTf.getStyle() & Typeface.BOLD) != 0;

        boolean rejectBold = false;
        if (hasRealBold) {
            Paint boldProbe = new Paint(normalPaint);
            boldProbe.setTypeface(boldTf);
            for (int c = 32; c < 127; c++) {
                float nw = normalWidths[c];
                if (nw <= 0.001f) continue;
                one[0] = (char) c;
                float bw = boldProbe.measureText(one, 0, 1);
                if (bw > nw * 1.10f + 0.5f) { rejectBold = true; break; }
            }
        }
        mBoldRejected = rejectBold;

        mPaints[PAINT_NORMAL] = normalPaint;
        mPaints[PAINT_BOLD] = createVariantPaint(normalPaint, typeface, true, false, rejectBold);
        mPaints[PAINT_ITALIC] = createVariantPaint(normalPaint, typeface, false, true, false);
        mPaints[PAINT_BOLD_ITALIC] = createVariantPaint(normalPaint, typeface, true, true, rejectBold);

        for (int pi = 0; pi < 4; pi++) {
            for (int c = 32; c < 127; c++) {
                one[0] = (char) c;
                mAsciiWidths[pi][c] = mPaints[pi].measureText(one, 0, 1);
            }
        }
    }

    private static int paintIndex(boolean bold, boolean italic) {
        if (bold && italic) return PAINT_BOLD_ITALIC;
        if (bold) return PAINT_BOLD;
        if (italic) return PAINT_ITALIC;
        return PAINT_NORMAL;
    }

    private float measureCluster(int pi, int cp, char[] text, int start, int length) {
        if (length >= 1 && length <= 2) {
            int key = (pi << 24) | (cp & 0xFFFFFF);
            int idx = mNonAsciiAdvanceCache.indexOfKey(key);
            if (idx >= 0) return mNonAsciiAdvanceCache.valueAt(idx);
            float adv = mPaints[pi].measureText(text, start, length);
            mNonAsciiAdvanceCache.put(key, adv);
            return adv;
        }
        return mPaints[pi].measureText(text, start, length);
    }

    private static Paint createVariantPaint(Paint src, Typeface base, boolean bold, boolean italic, boolean rejectBold) {
        Paint p = new Paint(src);
        int style = (bold ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
        Typeface tf = Typeface.create(base, style);
        int actual = tf.getStyle();

        boolean useFakeBold = bold && (rejectBold || (actual & Typeface.BOLD) == 0);
        boolean useFakeItalic = italic && (actual & Typeface.ITALIC) == 0;

        Typeface chosen;
        if (bold && !useFakeBold && italic && !useFakeItalic) {
            chosen = tf;
        } else if (bold && !useFakeBold) {
            chosen = Typeface.create(base, Typeface.BOLD);
        } else if (italic && !useFakeItalic) {
            chosen = Typeface.create(base, Typeface.ITALIC);
        } else {
            chosen = base;
        }

        p.setTypeface(chosen);
        p.setFakeBoldText(useFakeBold);
        p.setTextSkewX(useFakeItalic ? -0.35f : 0f);
        return p;
    }

    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2,
                             boolean focused) {
        final boolean globalReverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();
        final int defaultBg = palette[TextStyle.COLOR_INDEX_BACKGROUND];
        final int cursorColor = palette[TextStyle.COLOR_INDEX_CURSOR];

        int underlineColor = mEmulator.mUnderlineColor;
        if (underlineColor != TextStyle.COLOR_INDEX_FOREGROUND && (underlineColor & 0xff000000) != 0xff000000) {
            underlineColor = palette[underlineColor];
        }

        if (globalReverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);
        else
            canvas.drawColor(defaultBg, PorterDuff.Mode.SRC);

        float y = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            y += mFontLineSpacing;

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            RowLayout layout = new RowLayout(lineObject, columns);

            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : columns;
            }
            boolean isCursorRow = (row == cursorRow && cursorVisible);
            int cursorX = isCursorRow ? cursorCol : -1;

            drawRowBackground(canvas, lineObject, layout, columns, y, palette, defaultBg, globalReverseVideo, selx1, selx2, isCursorRow, cursorX, cursorShape, cursorColor, focused);

            drawRowText(canvas, lineObject, layout, columns, y, palette, defaultBg, globalReverseVideo, selx1, selx2, isCursorRow, cursorX, cursorShape, cursorColor, underlineColor, focused);

            drawRowCursorBlockOutline(canvas, isCursorRow, cursorX, cursorShape, layout, columns, y, cursorColor, focused);

            drawRowCursorSuffix(canvas, isCursorRow, cursorX, cursorShape, layout, columns, y, cursorColor);
        }
    }

    private void drawRowBackground(Canvas canvas, TerminalRow row, RowLayout layout, int columns, float y,
                                   int[] palette, int defaultBg, boolean globalReverseVideo,
                                   int selx1, int selx2, boolean isCursorRow, int cursorX, int cursorShape, int cursorColor,
                                   boolean focused) {
        int runStart = -1;
        int runColor = 0;

        for (int col = 0; col < columns; ) {
            boolean selected = col >= selx1 && col < selx2;
            boolean isBlockCursor = cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            boolean cursorBlock = isCursorRow && focused && isBlockCursor
                && (cursorX == col || (layout.isFragment[col] && cursorX == col - 1));
            int bg = resolveDisplayBg(row, layout, col, palette, defaultBg, globalReverseVideo, selected, cursorBlock, cursorColor);

            if (bg == defaultBg) {
                if (runStart >= 0) {
                    mPaints[PAINT_NORMAL].setColor(runColor);
                    canvas.drawRect(runStart * mFontWidth, y - mFontLineSpacingAndAscent + mFontAscent, col * mFontWidth, y, mPaints[PAINT_NORMAL]);
                    runStart = -1;
                }
            } else {
                if (runStart < 0) {
                    runStart = col;
                    runColor = bg;
                } else if (bg != runColor) {
                    mPaints[PAINT_NORMAL].setColor(runColor);
                    canvas.drawRect(runStart * mFontWidth, y - mFontLineSpacingAndAscent + mFontAscent, col * mFontWidth, y, mPaints[PAINT_NORMAL]);
                    runStart = col;
                    runColor = bg;
                }
            }

            int w = col < columns ? layout.widths[col] : 1;
            col += (w > 0 ? w : 1);
        }

        if (runStart >= 0) {
            mPaints[PAINT_NORMAL].setColor(runColor);
            canvas.drawRect(runStart * mFontWidth, y - mFontLineSpacingAndAscent + mFontAscent, columns * mFontWidth, y, mPaints[PAINT_NORMAL]);
        }
    }

    private int resolveDisplayBg(TerminalRow row, RowLayout layout, int col, int[] palette, int defaultBg,
                                 boolean globalReverseVideo, boolean selected, boolean cursorBlock, int cursorColor) {
        if (cursorBlock) return cursorColor;

        long style = row.getStyle(col);
        int rawBg = TextStyle.decodeBackColor(style);
        int bg = (rawBg & 0xff000000) != 0xff000000 ? palette[rawBg] : rawBg;

        int effect = TextStyle.decodeEffect(style);
        boolean swap = globalReverseVideo ^ ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0) ^ selected;
        if (!swap) return bg;

        int rawFg = TextStyle.decodeForeColor(style);
        int fg = (rawFg & 0xff000000) != 0xff000000 ? palette[rawFg] : rawFg;
        if ((effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0 && rawFg >= 0 && rawFg < 8) {
            fg = palette[rawFg + 8];
        }
        return fg;
    }

    private int resolveDisplayFg(long style, int[] palette, boolean globalReverseVideo, boolean reverseHere, boolean dim, boolean bold) {
        int rawFg = TextStyle.decodeForeColor(style);
        int fg = (rawFg & 0xff000000) != 0xff000000 ? palette[rawFg] : rawFg;
        if (bold && rawFg >= 0 && rawFg < 8) {
            fg = palette[rawFg + 8];
        }

        if (dim && !reverseHere) {
            int red = (0xFF & (fg >> 16));
            int green = (0xFF & (fg >> 8));
            int blue = (0xFF & fg);
            red = red * 2 / 3;
            green = green * 2 / 3;
            blue = blue * 2 / 3;
            fg = 0xFF000000 + (red << 16) + (green << 8) + blue;
        }

        if (reverseHere) {
            int rawBg = TextStyle.decodeBackColor(style);
            fg = (rawBg & 0xff000000) != 0xff000000 ? palette[rawBg] : rawBg;
        }

        return fg;
    }

    private void drawRowText(Canvas canvas, TerminalRow row, RowLayout layout, int columns, float y,
                             int[] palette, int defaultBg, boolean globalReverseVideo,
                             int selx1, int selx2, boolean isCursorRow, int cursorX, int cursorShape, int cursorColor,
                             int underlineColor, boolean focused) {
        char[] text = row.mText;
        long lastStyle = 0;
        boolean lastReverse = false;
        boolean lastCursorBlock = false;
        boolean lastMismatch = false;
        int lastStart = -1;
        int lastStartChar = -1;
        float lastMes = 0;
        int lastCharIdx = 0;

        for (int col = 0; col < columns; ) {
            int w = col < columns ? layout.widths[col] : 1;
            if (w <= 0) { col += 1; continue; }

            int cp = layout.codePoints[col];
            long style = row.getStyle(col);
            int eff = TextStyle.decodeEffect(style);
            boolean colBold = (eff & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
            boolean colItalic = (eff & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
            boolean selected = col >= selx1 && col < selx2;
            boolean isBlockCursor = cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            boolean atCursor = isCursorRow && (cursorX == col || (layout.isFragment[col] && cursorX == col - 1) || (w == 2 && cursorX == col + 1));
            boolean cursorBlock = atCursor && focused && isBlockCursor;
            boolean reverseHere = globalReverseVideo ^ ((style & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0) ^ selected;

            int charIdx = layout.charStarts[col];
            int charCount = layout.charCounts[col];

            int pi = paintIndex(colBold, colItalic);
            float measuredWidth = cp < 128 ? mAsciiWidths[pi][cp] : measureCluster(pi, cp, text, charIdx, charCount);
            boolean mismatch = Math.abs(measuredWidth / mFontWidth - w) > 0.01;

            boolean breakRun = false;
            if (col > 0 && (style != lastStyle || reverseHere != lastReverse || cursorBlock != lastCursorBlock || mismatch != lastMismatch)) {
                breakRun = true;
            }

            if (breakRun) {
                int runWidth = col - lastStart;
                drawTextRun(canvas, text, palette, y, lastStart, runWidth, lastStartChar, lastCharIdx - lastStartChar, lastMes, lastStyle, globalReverseVideo, lastReverse, lastCursorBlock, lastMismatch, underlineColor);
                lastMes = 0;
            }

            if (breakRun || lastStart < 0) {
                lastStart = col;
                lastStartChar = charIdx;
            }
            lastStyle = style;
            lastReverse = reverseHere;
            lastCursorBlock = cursorBlock;
            lastMismatch = mismatch;
            lastMes += measuredWidth;
            lastCharIdx = charIdx + charCount;

            int prevCol = col;
            col += w;
            while (col < columns && layout.widths[col] <= 0) col++;
        }

        int runWidth = columns - lastStart;
        drawTextRun(canvas, text, palette, y, lastStart, runWidth, lastStartChar, lastCharIdx - lastStartChar, lastMes, lastStyle, globalReverseVideo, lastReverse, lastCursorBlock, lastMismatch, underlineColor);
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y,
                             int startCol, int runColumns, int startChar, int runChars, float mes,
                             long style, boolean globalReverseVideo, boolean reverseHere, boolean cursorBlock, boolean mismatch,
                             int underlineColor) {
        int effect = TextStyle.decodeEffect(style);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;
        final boolean invisible = (effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0;

        int foreColor = resolveDisplayFg(style, palette, globalReverseVideo, reverseHere, dim, bold);

        float unscaledLeft = startCol * mFontWidth;
        float unscaledRight = (startCol + runColumns) * mFontWidth;

        Paint paint = mPaints[paintIndex(bold, italic)];
        paint.setColor(foreColor);

        boolean runMismatch = mismatch;
        float left = unscaledLeft;
        float scaleFactor = 1f;
        float centerShift = 0f;
        if (runMismatch) {
            float mesCols = mes / mFontWidth;
            if (Math.abs(mesCols - runColumns) > 0.01f) {
                if (mesCols > runColumns) {
                    scaleFactor = runColumns / mesCols;
                    canvas.save();
                    canvas.scale(scaleFactor, 1.f);
                    left *= mesCols / runColumns;
                } else {
                    centerShift = (runColumns - mesCols) * mFontWidth;
                    runMismatch = false;
                }
            } else {
                runMismatch = false;
            }
        }
        float invScale = runMismatch ? 1f / scaleFactor : 1f;

        if (!invisible) {
            float cellTop = y - mFontLineSpacingAndAscent;
            float cellHeight = mFontLineSpacingAndAscent - mFontAscent;
            float baseline = y - mFontLineSpacingAndAscent + mFontLineSpacing;
            int decoColor = underlineColor != TextStyle.COLOR_INDEX_FOREGROUND ? underlineColor : foreColor;
            DecorationPainter.drawPreText(canvas, unscaledLeft, unscaledRight, baseline, cellTop, cellHeight, mTextSize, style, decoColor);
        }

        if (!invisible) {
            boolean hasMinifont = false;
            int endChar = startChar + runChars;
            for (int i = startChar; i < endChar; ) {
                int cp = Character.codePointAt(text, i);
                if (Minifont.isLocalGraphic(cp)) { hasMinifont = true; break; }
                i += Character.charCount(cp);
            }

            float centerHalf = centerShift / 2f;
            if (!hasMinifont) {
                canvas.drawTextRun(text, startChar, runChars, startChar, runChars, left + centerHalf, y - mFontLineSpacingAndAscent, false, paint);
            } else {
                int segStartChar = startChar;
                int segStartCol = 0;
                int segChars = 0;
                int col = 0;
                float textLeft = left + centerHalf;
                int endIdx = startChar + runChars;

                for (int i = startChar; i < endIdx; ) {
                    int cp = Character.codePointAt(text, i);
                    int cpChars = Character.charCount(cp);
                    int cpWidth = WcWidth.width(cp);

                    if (Minifont.isLocalGraphic(cp)) {
                        if (segChars > 0) {
                            float x = textLeft + segStartCol * mFontWidth;
                            float adjustedLeft = runMismatch ? x * invScale : x;
                            canvas.drawTextRun(text, segStartChar, segChars, segStartChar, segChars, adjustedLeft, y - mFontLineSpacingAndAscent, false, paint);
                            segChars = 0;
                        }

                        float glyphLeft = textLeft + (segStartCol + col) * mFontWidth;
                        Minifont.draw(canvas, cp, Math.round(glyphLeft), Math.round(y - mFontLineSpacingAndAscent), Math.round(cpWidth * mFontWidth), mFontLineSpacing, foreColor);

                        int consumeEnd = i + cpChars;
                        while (consumeEnd < endIdx) {
                            int ncp = Character.codePointAt(text, consumeEnd);
                            if (WcWidth.width(ncp) > 0) break;
                            consumeEnd += Character.charCount(ncp);
                        }
                        segStartChar = consumeEnd;
                        segStartCol += col + cpWidth;
                        col = 0;
                        i = consumeEnd - cpChars;
                    } else {
                        col += cpWidth;
                        segChars += cpChars;
                    }
                    i += cpChars;
                }

                if (segChars > 0) {
                    float x = textLeft + segStartCol * mFontWidth;
                    float adjustedLeft = runMismatch ? x * invScale : x;
                    canvas.drawTextRun(text, segStartChar, segChars, segStartChar, segChars, adjustedLeft, y - mFontLineSpacingAndAscent, false, paint);
                }
            }
        }

        if (runMismatch) canvas.restore();

        if (!invisible) {
            float cellTop = y - mFontLineSpacingAndAscent;
            float cellHeight = mFontLineSpacingAndAscent - mFontAscent;
            float baseline = y - mFontLineSpacingAndAscent + mFontLineSpacing;
            int decoColor = underlineColor != TextStyle.COLOR_INDEX_FOREGROUND ? underlineColor : foreColor;
            DecorationPainter.drawPostText(canvas, unscaledLeft, unscaledRight, baseline, cellTop, cellHeight, mTextSize, style, decoColor);
        }
    }

    private int cursorWidth(RowLayout layout, int cursorX, int columns) {
        if (cursorX >= 0 && cursorX < columns && layout.isWide[cursorX]) return 2;
        return 1;
    }

    private void drawRowCursorBlockOutline(Canvas canvas, boolean isCursorRow, int cursorX, int cursorShape, RowLayout layout, int columns, float y, int cursorColor, boolean focused) {
        if (!isCursorRow) return;
        if (cursorShape != TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) return;
        if (focused) return;

        int w = cursorWidth(layout, cursorX, columns);
        mPaints[PAINT_NORMAL].setColor(cursorColor);
        float left = cursorX * mFontWidth;
        float top = y - mFontLineSpacingAndAscent + mFontAscent;
        float right = (cursorX + w) * mFontWidth;
        float bottom = y;
        canvas.drawRect(left, top, right, top + 1f, mPaints[PAINT_NORMAL]);
        canvas.drawRect(left, bottom - 1f, right, bottom, mPaints[PAINT_NORMAL]);
        canvas.drawRect(left, top, left + 1f, bottom, mPaints[PAINT_NORMAL]);
        canvas.drawRect(right - 1f, top, right, bottom, mPaints[PAINT_NORMAL]);
    }

    private void drawRowCursorSuffix(Canvas canvas, boolean isCursorRow, int cursorX, int cursorShape, RowLayout layout, int columns, float y, int cursorColor) {
        if (!isCursorRow) return;
        if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) return;

        int w = cursorWidth(layout, cursorX, columns);
        mPaints[PAINT_NORMAL].setColor(cursorColor);
        float left = cursorX * mFontWidth;
        float right = (cursorX + w) * mFontWidth;
        float rowTop = y - mFontLineSpacingAndAscent + mFontAscent;
        float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;

        if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) {
            cursorHeight /= 4.f;
            canvas.drawRect(left, y - cursorHeight, right, y, mPaints[PAINT_NORMAL]);
        } else if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) {
            canvas.drawRect(right - mFontWidth / 4f, rowTop, right, y, mPaints[PAINT_NORMAL]);
        } else if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_IBEAM) {
            float cx = (left + right) / 2f;
            float half = (right - left) / 8f;
            canvas.drawRect(cx - half, rowTop, cx + half, y, mPaints[PAINT_NORMAL]);
        }
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
