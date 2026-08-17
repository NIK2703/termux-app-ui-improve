package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.util.SparseArray;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    /** The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'. */
    final float mFontWidth;
    /** The {@link Paint#getFontSpacing()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    final int mFontLineSpacing;
    /** The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    private final int mFontAscent;
    /** The {@link #mFontLineSpacing} + {@link #mFontAscent}. */
    final int mFontLineSpacingAndAscent;

    /**
     * Cache of {@link Paint#measureText} results per code point. {@code bmpMeasures} covers the
     * BMP (0..0xFFFF) and is lazily filled; {@code supplementaryMeasures} covers the supplementary
     * planes (rare). This avoids calling the native Skia/FreeType measureText on every non-ASCII
     * character on every frame — previously the single hottest call in the renderer.
     * A value of 0.0f in {@code bmpMeasures} means "not yet measured" except for code point 0.
     */
    private final float[] bmpMeasures = new float[0x10000];
    private final SparseArray<Float> supplementaryMeasures = new SparseArray<>();

    /**
     * Reusable per-frame run list. The renderer splits each row into "runs" of equal style; instead
     * of drawing every run immediately (which issues a separate background {@link Paint#drawRect}
     * per run), runs are collected into these arrays once per row and drawn in two passes: all
     * background rectangles batched per color, then all text. This avoids per-run allocation and
     * collapses adjacent same-color backgrounds into a single {@link Path} draw.
     */
    private int[] mRunStartColumn;
    private int[] mRunWidthColumns;
    private int[] mRunStartChar;
    private int[] mRunCharCount;
    private float[] mRunMeasuredWidth;
    private long[] mRunStyle;
    private int[] mRunCursorColor;
    private int[] mRunCursorStyle;
    private boolean[] mRunReverseVideo;
    private boolean[] mRunFontWidthMismatch;
    private int mRunCount;
    private final SparseArray<Path> mBgBatches = new SparseArray<>();
    private final int[] mColorOut = new int[2];

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        // Pre-measure ASCII so the first paint does not pay for it; the rest is filled lazily.
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < 0x80; i++) {
            sb.setCharAt(0, (char) i);
            bmpMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    /** Measure the on-screen width of a code point, using the per-code-point cache. */
    private float measureCodePoint(int codePoint, char[] line, int index, int count) {
        if (codePoint < 0x10000) {
            float cached = bmpMeasures[codePoint];
            if (cached == 0f && codePoint != 0) {
                cached = mTextPaint.measureText(line, index, count);
                bmpMeasures[codePoint] = cached;
            }
            return cached;
        }
        Float cached = supplementaryMeasures.get(codePoint);
        if (cached == null) {
            cached = mTextPaint.measureText(line, index, count);
            supplementaryMeasures.put(codePoint, cached);
        }
        return cached;
    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     *
     * @param xOffset horizontal pixel offset applied to the glyph grid (centering the leftover space).
     * @param yOffset vertical pixel offset applied to the glyph grid (centering the leftover space).
     */
    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2,
                             float xOffset, float yOffset) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        // Paint the full terminal background on every frame. Without this, only cells whose
        // background differs from the default get a background rect drawn (see drawTextRun), so the
        // large default-background area stays transparent and shows whatever is behind the canvas
        // (the window DecorView, set separately by updateBackgroundColor()). That means a
        // color-scheme change applied via invalidate()/onScreenUpdated() would recolor the glyphs
        // but leave the pane background stale until the window background was separately updated.
        // Clearing the canvas here makes invalidate() a complete repaint, which is exactly what a
        // Dark<->System (or any) theme swap relies on to update without a full activity relaunch.
        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);
        else
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_BACKGROUND], PorterDuff.Mode.SRC);

        // Translate the whole grid so the leftover space (from glyphs that do not fit the
        // view) is split symmetrically around it. The drawTextRun() scale compensation block
        // (canvas.scale + left *= mes/runWidthColumns) composes with this translate, so
        // width-mismatched glyph runs keep landing exactly on their shifted cells.
        canvas.save();
        canvas.translate(xOffset, yOffset);

        ensureRunCapacity(columns);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            mRunCount = 0;
            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = measureCodePoint(codePoint, line, currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

                // Break the run whenever the font-width-mismatch flag changes, AND additionally
                // break on every mismatched code point so each such glyph is scaled individually
                // rather than averaged with its neighbours. Averaging across a run is what makes
                // some emoji get clipped (glyph wider than its cell) or squeezed (glyph narrower
                // than its cell).
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column != 0) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }
                        addRun(lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            lastRunStyle, cursorColor, cursorShape, reverseVideo || invertCursorTextColor || lastRunInsideSelection, lastRunFontWidthMismatch);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = false;
            if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                invertCursorTextColor = true;
            }
            addRun(lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                lastRunStyle, cursorColor, cursorShape, reverseVideo || invertCursorTextColor || lastRunInsideSelection, lastRunFontWidthMismatch);

            // Pass A: background rectangles, batched per color into a single Path draw each.
            // Mismatch runs (scaled glyphs) are skipped here and drawn in pass B with their scale.
            for (int i = 0; i < mRunCount; i++) {
                if (mRunFontWidthMismatch[i]) continue;
                resolveRunColors(mRunStyle[i], mRunReverseVideo[i], palette, mColorOut);
                final int backColor = mColorOut[1];
                if (backColor == palette[TextStyle.COLOR_INDEX_BACKGROUND]) continue;
                final float left = mRunStartColumn[i] * mFontWidth;
                final float right = left + mRunWidthColumns[i] * mFontWidth;
                addBgRect(backColor, left, heightOffset - mFontLineSpacingAndAscent + mFontAscent, right, heightOffset);
            }
            for (int i = 0; i < mBgBatches.size(); i++) {
                final Path p = mBgBatches.valueAt(i);
                mTextPaint.setColor(mBgBatches.keyAt(i));
                canvas.drawPath(p, mTextPaint);
                p.rewind();
            }

            // Pass B: text (and cursor, and any scaled background) drawn on top of the backgrounds.
            for (int i = 0; i < mRunCount; i++) {
                drawRunText(canvas, line, palette, heightOffset, mRunStartColumn[i], mRunWidthColumns[i], mRunStartChar[i],
                    mRunCharCount[i], mRunMeasuredWidth[i], mRunCursorColor[i], mRunCursorStyle[i], mRunStyle[i],
                    mRunReverseVideo[i], mRunFontWidthMismatch[i]);
            }
        }

        canvas.restore();
    }

    private void ensureRunCapacity(int columns) {
        if (mRunStartColumn == null || mRunStartColumn.length < columns) {
            mRunStartColumn = new int[columns];
            mRunWidthColumns = new int[columns];
            mRunStartChar = new int[columns];
            mRunCharCount = new int[columns];
            mRunMeasuredWidth = new float[columns];
            mRunStyle = new long[columns];
            mRunCursorColor = new int[columns];
            mRunCursorStyle = new int[columns];
            mRunReverseVideo = new boolean[columns];
            mRunFontWidthMismatch = new boolean[columns];
        }
    }

    private void addRun(int startColumn, int runWidthColumns, int startCharIndex, int runWidthChars, float measuredWidth,
                        long style, int cursorColor, int cursorStyle, boolean reverseVideo, boolean fontWidthMismatch) {
        ensureRunCapacity(mRunCount + 1);
        mRunStartColumn[mRunCount] = startColumn;
        mRunWidthColumns[mRunCount] = runWidthColumns;
        mRunStartChar[mRunCount] = startCharIndex;
        mRunCharCount[mRunCount] = runWidthChars;
        mRunMeasuredWidth[mRunCount] = measuredWidth;
        mRunStyle[mRunCount] = style;
        mRunCursorColor[mRunCount] = cursorColor;
        mRunCursorStyle[mRunCount] = cursorStyle;
        mRunReverseVideo[mRunCount] = reverseVideo;
        mRunFontWidthMismatch[mRunCount] = fontWidthMismatch;
        mRunCount++;
    }

    private void addBgRect(int color, float left, float top, float right, float bottom) {
        Path p = mBgBatches.get(color);
        if (p == null) {
            p = new Path();
            mBgBatches.put(color, p);
        }
        p.addRect(left, top, right, bottom, Path.Direction.CW);
    }

    /** Resolve a run's style into foreground/background colors (with bold + reverse-video handling). */
    private void resolveRunColors(long textStyle, boolean reverseVideo, int[] palette, int[] out) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }
        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }
        out[0] = foreColor;
        out[1] = backColor;
    }

    private void drawRunText(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                             int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                             long textStyle, boolean reverseVideo, boolean fontWidthMismatch) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (fontWidthMismatch && Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        // Background for mismatch (scaled) runs only; non-mismatch backgrounds are batched in pass A.
        if (fontWidthMismatch && backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling per xterm convention
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
        }

        if (savedMatrix) canvas.restore();
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
