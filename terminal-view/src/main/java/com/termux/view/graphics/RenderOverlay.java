package com.termux.view.graphics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.termux.view.text.RowLayout;

public final class RenderOverlay {

    private final Paint mLinePaint = new Paint();
    private final Paint mFillPaint = new Paint();
    private final Paint mTextPaint = new Paint();
    private final RectF mRect = new RectF();

    public boolean showClusters;
    public boolean showBidi;
    public boolean showMinifont;
    public boolean showCursor;

    public RenderOverlay() {
        mTextPaint.setTextSize(24);
        mTextPaint.setAntiAlias(true);
    }

    public void draw(Canvas canvas, RowLayout[] layouts, int columns, float cellW, float cellH,
                     int cursorRow, int cursorCol, boolean cursorVisible, int topRow, int rowCount) {
        if (!showClusters && !showBidi && !showMinifont && !showCursor) return;

        float y = cellH;
        for (int i = 0; i < rowCount; i++) {
            int row = topRow + i;
            RowLayout layout = layouts != null && i < layouts.length ? layouts[i] : null;
            if (layout != null) {
                drawRowOverlays(canvas, layout, columns, y, cellW, cellH,
                    row == cursorRow && cursorVisible, cursorCol);
            }
            y += cellH;
        }
    }

    private void drawRowOverlays(Canvas c, RowLayout layout, int cols, float y, float cw, float ch,
                                 boolean isCursor, int cursorCol) {
        for (int col = 0; col < cols; col++) {
            float x = col * cw;
            mRect.set(x, y - ch, x + cw, y);

            if (showClusters && layout.isFragment[col]) {
                mFillPaint.setColor(0x20FF8800);
                c.drawRect(mRect, mFillPaint);
                mLinePaint.setColor(0x80FF8800);
                mLinePaint.setStrokeWidth(1f);
                c.drawRect(mRect, mLinePaint);
            }

            if (showBidi && layout.isRtlColumn[col]) {
                mFillPaint.setColor(0x200088FF);
                c.drawRect(mRect, mFillPaint);
                mLinePaint.setColor(0x800088FF);
                mLinePaint.setStrokeWidth(1f);
                c.drawRect(mRect, mLinePaint);
            }

            if (showMinifont && layout.isMinifont[col]) {
                mFillPaint.setColor(0x2000FF00);
                c.drawRect(mRect, mFillPaint);
                mLinePaint.setColor(0x8000FF00);
                mLinePaint.setStrokeWidth(1f);
                c.drawRect(mRect, mLinePaint);
            }

            if (showCursor && isCursor && col == cursorCol) {
                mLinePaint.setColor(0xFFFF0000);
                mLinePaint.setStrokeWidth(2f);
                c.drawRect(mRect, mLinePaint);
            }
        }
    }
}
