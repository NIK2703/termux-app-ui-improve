package com.termux.view.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.LruCache;
import android.util.SparseArray;

public final class Minifont {

    private static final LruCache<Long, Bitmap> sMaskCache = new LruCache<Long, Bitmap>(1024) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getAllocationByteCount();
        }
    };

    private static final Paint sTintPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final SparseArray<ColorFilter> sColorFilters = new SparseArray<>();

    private static ColorFilter getColorFilter(int color) {
        ColorFilter filter = sColorFilters.get(color);
        if (filter == null) {
            filter = new PorterDuffColorFilter(color | 0xFF000000, PorterDuff.Mode.SRC_IN);
            sColorFilters.put(color, filter);
        }
        return filter;
    }

    // Existing ranges
    private static final int SEXTANT_START = 0x1FB00;
    private static final int SEXTANT_END = 0x1FB3B;
    private static final int OCTANT_START = 0x1CD00;
    private static final int OCTANT_END = 0x1CDE5;
    private static final int DIAGONAL_START = 0x1FBD0;
    private static final int DIAGONAL_END = 0x1FBDF;
    private static final int FILL_PATTERN_START = 0x1FB95;
    private static final int FILL_PATTERN_END = 0x1FB99;

    // Missing ranges added
    private static final int LEGACY_POLY_START = 0x1FB3C;
    private static final int LEGACY_POLY_END = 0x1FB67;
    private static final int LEGACY_POLY2_START = 0x1FB68;
    private static final int LEGACY_POLY2_END = 0x1FB6F;
    private static final int HORIZ_EIGHTH_START = 0x1FB70;
    private static final int HORIZ_EIGHTH_END = 0x1FB7B;
    private static final int CORNER_EIGHTH_START = 0x1FB7C;
    private static final int CORNER_EIGHTH_END = 0x1FB81;
    private static final int VERT_STRIP_START = 0x1FB82;
    private static final int VERT_STRIP_END = 0x1FB86;
    private static final int RIGHT_STRIP_START = 0x1FB87;
    private static final int RIGHT_STRIP_END = 0x1FB8B;
    private static final int TRANS_QUAD_START = 0x1FB8C;
    private static final int TRANS_QUAD_END = 0x1FB94;
    private static final int DIAMOND_START = 0x1FB9A;
    private static final int DIAMOND_END = 0x1FB9F;
    private static final int MIDDLE_DIAG_START = 0x1FBA0;
    private static final int MIDDLE_DIAG_END = 0x1FBAE;
    private static final int SEP_QUADRANT_START = 0x1CC21;
    private static final int SEP_QUADRANT_END = 0x1CC2F;
    private static final int TWELFTH_CIRCLE_START = 0x1CC30;
    private static final int TWELFTH_CIRCLE_END = 0x1CC3F;
    private static final int BOX_EXT1_START = 0x1CC1B;
    private static final int BOX_EXT1_END = 0x1CC1E;
    private static final int DOUBLE_DIAG_START = 0x1CC1F;
    private static final int DOUBLE_DIAG_END = 0x1CC20;
    private static final int SEP_SEXTANT_START = 0x1CE51;
    private static final int SEP_SEXTANT_END = 0x1CE8F;
    private static final int SIXTEENTH_START = 0x1CE90;
    private static final int SIXTEENTH_END = 0x1CEAF;
    private static final int FILL_CHAR_START = 0x1CC40;
    private static final int FILL_CHAR_END = 0x1CC47;
    private static final int DOUBLE_DIAG_MID_START = 0x1CE09;
    private static final int DOUBLE_DIAG_MID_END = 0x1CE0A;
    private static final int BOX_EXT2_START = 0x1CE16;
    private static final int BOX_EXT2_END = 0x1CE19;
    private static final int CENTRE_QUARTER_START = 0x1FBE4;
    private static final int CENTRE_QUARTER_END = 0x1FBE7;

    // Paint reused for dash/slope drawing in mask generation
    private static final Paint sDashPaint = new Paint();
    static {
        sDashPaint.setStyle(Paint.Style.STROKE);
        sDashPaint.setAntiAlias(false);
    }

    private static boolean inRange(int cp, int start, int end) {
        return cp >= start && cp <= end;
    }

    public static boolean isLocalGraphic(int codePoint) {
        if (codePoint >= 0x2500 && codePoint <= 0x257F) return true;
        if (codePoint >= 0x2580 && codePoint <= 0x259F) return true;
        if (codePoint >= 0x23BA && codePoint <= 0x23BD) return true;
        if (codePoint == 0x23B8 || codePoint == 0x23B9) return true;
        if (codePoint >= 0x25E2 && codePoint <= 0x25E5) return true;
        if (inRange(codePoint, SEXTANT_START, SEXTANT_END)) return true;
        if (inRange(codePoint, OCTANT_START, OCTANT_END)) return true;
        if (inRange(codePoint, DIAGONAL_START, DIAGONAL_END)) return true;
        if (inRange(codePoint, FILL_PATTERN_START, FILL_PATTERN_END)) return true;
        if (inRange(codePoint, LEGACY_POLY_START, LEGACY_POLY_END)) return true;
        if (inRange(codePoint, LEGACY_POLY2_START, LEGACY_POLY2_END)) return true;
        if (inRange(codePoint, HORIZ_EIGHTH_START, HORIZ_EIGHTH_END)) return true;
        if (inRange(codePoint, CORNER_EIGHTH_START, CORNER_EIGHTH_END)) return true;
        if (inRange(codePoint, VERT_STRIP_START, VERT_STRIP_END)) return true;
        if (inRange(codePoint, RIGHT_STRIP_START, RIGHT_STRIP_END)) return true;
        if (inRange(codePoint, TRANS_QUAD_START, TRANS_QUAD_END)) return true;
        if (inRange(codePoint, DIAMOND_START, DIAMOND_END)) return true;
        if (inRange(codePoint, MIDDLE_DIAG_START, MIDDLE_DIAG_END)) return true;
        if (codePoint == 0x1FBAF) return true;
        if (codePoint == 0x1FBBD) return true;
        if (codePoint >= 0x1FBBE && codePoint <= 0x1FBBF) return true;
        if (codePoint >= 0x1FBCE && codePoint <= 0x1FBCF) return true;
        if (inRange(codePoint, CENTRE_QUARTER_START, CENTRE_QUARTER_END)) return true;
        if (inRange(codePoint, SEP_QUADRANT_START, SEP_QUADRANT_END)) return true;
        if (inRange(codePoint, BOX_EXT1_START, BOX_EXT1_END)) return true;
        if (inRange(codePoint, DOUBLE_DIAG_START, DOUBLE_DIAG_END)) return true;
        if (inRange(codePoint, TWELFTH_CIRCLE_START, TWELFTH_CIRCLE_END)) return true;
        if (inRange(codePoint, DOUBLE_DIAG_MID_START, DOUBLE_DIAG_MID_END)) return true;
        if (inRange(codePoint, BOX_EXT2_START, BOX_EXT2_END)) return true;
        if (inRange(codePoint, SEP_SEXTANT_START, SEP_SEXTANT_END)) return true;
        if (inRange(codePoint, SIXTEENTH_START, SIXTEENTH_END)) return true;
        if (inRange(codePoint, FILL_CHAR_START, FILL_CHAR_END)) return true;
        return false;
    }

    public static void draw(Canvas canvas, int codePoint, int left, int top, int width, int height, int color) {
        Bitmap mask = getMask(codePoint, width, height);
        if (mask == null) return;
        sTintPaint.setColorFilter(getColorFilter(color));
        canvas.drawBitmap(mask, null, new Rect(left, top, left + width, top + height), sTintPaint);
    }

    private static Bitmap getMask(int codePoint, int width, int height) {
        long key = (((long) codePoint) << 32) | (((long) width) << 16) | (height & 0xFFFFL);
        Bitmap cached = sMaskCache.get(key);
        if (cached != null) return cached;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setColor(0xFFFFFFFF);
        p.setAntiAlias(false);

        if (codePoint >= 0x2500 && codePoint <= 0x257F) {
            drawBoxDrawing(c, codePoint, width, height, p);
        } else if ((codePoint >= 0x256D && codePoint <= 0x2570)) {
            p.setAntiAlias(true);
            drawArc(c, codePoint, width, height, p);
        } else if ((codePoint >= 0x2571 && codePoint <= 0x2573)) {
            p.setAntiAlias(true);
            drawBoxDiagonal(c, codePoint, width, height, p);
        } else if (codePoint >= 0x2580 && codePoint <= 0x259F) {
            drawBlockElement(c, codePoint, width, height, p);
        } else if (codePoint >= 0x23BA && codePoint <= 0x23BD) {
            drawScanline(c, codePoint, width, height, p);
        } else if (codePoint == 0x23B8 || codePoint == 0x23B9) {
            drawVertBoxLine(c, codePoint, width, height, p);
        } else if (codePoint >= 0x25E2 && codePoint <= 0x25E5) {
            drawTriangle(c, codePoint, width, height, p);
        } else if (inRange(codePoint, SEXTANT_START, SEXTANT_END)) {
            drawSextant(c, codePoint - SEXTANT_START + 1, width, height, p);
        } else if (inRange(codePoint, OCTANT_START, OCTANT_END)) {
            drawOctantFromTable(c, codePoint, width, height, p);
        } else if (inRange(codePoint, DIAGONAL_START, DIAGONAL_END)) {
            p.setAntiAlias(true);
            drawDiagonal(c, codePoint - DIAGONAL_START, width, height, p);
        } else if (inRange(codePoint, FILL_PATTERN_START, FILL_PATTERN_END)) {
            drawFillPattern(c, codePoint, width, height, p);
        } else if (inRange(codePoint, LEGACY_POLY_START, LEGACY_POLY_END)) {
            drawLegacyPoly(c, codePoint, width, height, p);
        } else if (inRange(codePoint, LEGACY_POLY2_START, LEGACY_POLY2_END)) {
            drawLegacyPoly2(c, codePoint, width, height, p);
        } else if (inRange(codePoint, HORIZ_EIGHTH_START, HORIZ_EIGHTH_END)) {
            drawHorizEighth(c, codePoint, width, height, p);
        } else if (inRange(codePoint, CORNER_EIGHTH_START, CORNER_EIGHTH_END)) {
            drawCornerEighth(c, codePoint, width, height, p);
        } else if (inRange(codePoint, VERT_STRIP_START, VERT_STRIP_END)) {
            drawVertStrip(c, codePoint, width, height, p);
        } else if (inRange(codePoint, RIGHT_STRIP_START, RIGHT_STRIP_END)) {
            drawRightStrip(c, codePoint, width, height, p);
        } else if (inRange(codePoint, TRANS_QUAD_START, TRANS_QUAD_END)) {
            drawTransQuad(c, codePoint, width, height, p);
        } else if (inRange(codePoint, DIAMOND_START, DIAMOND_END)) {
            drawDiamond(c, codePoint, width, height, p);
        } else if (inRange(codePoint, MIDDLE_DIAG_START, MIDDLE_DIAG_END)) {
            p.setAntiAlias(true);
            drawMiddleDiagonal(c, codePoint, width, height, p);
        } else if (codePoint == 0x1FBAF) {
            drawFbaf(c, width, height, p);
        } else if (codePoint == 0x1FBBD) {
            drawNegativeCross(c, width, height, p);
        } else if (codePoint >= 0x1FBBE && codePoint <= 0x1FBBF) {
            drawNegativeDiag(c, codePoint, width, height, p);
        } else if (codePoint >= 0x1FBCE && codePoint <= 0x1FBCF) {
            drawTwoThirdsBlock(c, codePoint, width, height, p);
        } else if (inRange(codePoint, CENTRE_QUARTER_START, CENTRE_QUARTER_END)) {
            drawCentreQuarter(c, codePoint, width, height, p);
        } else if (inRange(codePoint, SEP_QUADRANT_START, SEP_QUADRANT_END)) {
            drawSeparatedQuadrant(c, codePoint, width, height, p);
        } else if (inRange(codePoint, BOX_EXT1_START, BOX_EXT1_END)) {
            drawBoxExt1(c, codePoint, width, height, p);
        } else if (inRange(codePoint, DOUBLE_DIAG_START, DOUBLE_DIAG_END)) {
            p.setAntiAlias(true);
            drawDoubleDiag(c, codePoint, width, height, p);
        } else if (inRange(codePoint, TWELFTH_CIRCLE_START, TWELFTH_CIRCLE_END)) {
            p.setAntiAlias(true);
            drawTwelfthCircle(c, codePoint, width, height, p);
        } else if (inRange(codePoint, DOUBLE_DIAG_MID_START, DOUBLE_DIAG_MID_END)) {
            p.setAntiAlias(true);
            drawDoubleDiagMid(c, codePoint, width, height, p);
        } else if (inRange(codePoint, BOX_EXT2_START, BOX_EXT2_END)) {
            drawBoxExt2(c, codePoint, width, height, p);
        } else if (inRange(codePoint, SEP_SEXTANT_START, SEP_SEXTANT_END)) {
            drawSeparatedSextant(c, codePoint, width, height, p);
        } else if (inRange(codePoint, SIXTEENTH_START, SIXTEENTH_END)) {
            drawSixteenth(c, codePoint, width, height, p);
        } else if (inRange(codePoint, FILL_CHAR_START, FILL_CHAR_END)) {
            drawFillChar(c, codePoint, width, height, p);
        }

        sMaskCache.put(key, bmp);
        return bmp;
    }

    // ---- helpers ----

    private static void fillRect(Canvas c, int w, int h,
                                  int xdenom, int ydenom,
                                  int xb1, int yb1, int xb2, int yb2, Paint p) {
        int x1 = w * xb1 / xdenom;
        int y1 = h * yb1 / ydenom;
        int x2 = w * xb2 / xdenom;
        int y2 = h * yb2 / ydenom;
        if (x2 <= x1) x2 = x1 + 1;
        if (y2 <= y1) y2 = y1 + 1;
        c.drawRect(x1, y1, x2, y2, p);
    }

    private static void fillPoly(Canvas c, int w, int h,
                                  int xdenom, int ydenom,
                                  int[] coords, Paint p) {
        Path path = new Path();
        path.moveTo(w * coords[0] / xdenom, h * coords[1] / ydenom);
        for (int i = 2; i < coords.length && coords[i] != -1; i += 2) {
            path.lineTo(w * coords[i] / xdenom, h * coords[i + 1] / ydenom);
        }
        path.close();
        c.drawPath(path, p);
    }

    private static int lineWidth(int fontDim) {
        return Math.max(fontDim / 5, 1);
    }

    // ---- Box Drawing (0x2500..0x257F) ----

    private static void drawBoxDrawing(Canvas c, int cp, int w, int h, Paint p) {
        if (cp >= 0x2504 && cp <= 0x250B) { drawDashBox(c, cp, w, h, p); return; }
        if (cp >= 0x254C && cp <= 0x254F) { drawDashBox(c, cp, w, h, p); return; }
        int[] rows = BoxDrawing.TABLE.get(cp);
        if (rows != null) {
            drawBoxGlyph(c, rows, w, h, p);
        } else if (cp >= 0x256D && cp <= 0x2570) {
            p.setAntiAlias(true);
            drawArc(c, cp, w, h, p);
        } else if (cp >= 0x2571 && cp <= 0x2573) {
            p.setAntiAlias(true);
            drawBoxDiagonal(c, cp, w, h, p);
        }
    }

    private static void drawDashBox(Canvas c, int cp, int w, int h, Paint p) {
        int lw = lineWidth(Math.min(w, h));
        int v = cp - 0x2500;
        boolean vertical = (v & 2) != 0;
        boolean heavy = (v & 1) != 0;
        int size = vertical ? h : w;
        int segments;
        if ((v >> 2) == 1) segments = 3;
        else if ((v >> 2) == 2) segments = 4;
        else segments = 2;

        float segLen;
        switch (segments) {
            case 3: segLen = size / 8f; break;
            case 4: segLen = size / 11f; break;
            default: segLen = size / 5f; break;
        }
        lw = heavy ? Math.max(lw + 2, lw * 2) : lw;
        float[] intervals = {segLen * 2, segLen};
        sDashPaint.setStrokeWidth(lw);
        sDashPaint.setPathEffect(new DashPathEffect(intervals, 0));
        sDashPaint.setColor(0xFFFFFFFF);
        sDashPaint.setStrokeCap(Paint.Cap.BUTT);
        float cx = (w & 1) != 0 ? w / 2f + 0.5f : w / 2f;
        float cy = (h & 1) != 0 ? h / 2f + 0.5f : h / 2f;
        if (vertical) {
            c.drawLine(cx, 0, cx, h, sDashPaint);
        } else {
            c.drawLine(0, cy, w, cy, sDashPaint);
        }
        sDashPaint.setPathEffect(null);
    }

    private static void drawBoxGlyph(Canvas c, int[] rows, int w, int h, Paint p) {
        for (int r = 0; r < 5; r++) {
            int rowBits = rows[r];
            if (rowBits == 0) continue;
            int y0 = r * h / 5;
            int y1 = (r + 1) * h / 5;
            if (y1 <= y0) continue;
            for (int col = 0; col < 5; col++) {
                if ((rowBits & (1 << (4 - col))) == 0) continue;
                int x0 = col * w / 5;
                int x1 = (col + 1) * w / 5;
                if (x1 <= x0) continue;
                c.drawRect(x0, y0, x1, y1, p);
            }
        }
    }

    // ---- Block Elements (0x2580..0x259F) ----

    private static void drawBlockElement(Canvas c, int cp, int w, int h, Paint p) {
        int hw = w / 2;
        int hh = h / 2;
        switch (cp) {
            case 0x2580: c.drawRect(0, 0, w, hh, p); break;
            case 0x2581: c.drawRect(0, h - h / 8, w, h, p); break;
            case 0x2582: c.drawRect(0, h - h / 4, w, h, p); break;
            case 0x2583: c.drawRect(0, h - h * 3 / 8, w, h, p); break;
            case 0x2584: c.drawRect(0, hh, w, h, p); break;
            case 0x2585: c.drawRect(0, h - h * 5 / 8, w, h, p); break;
            case 0x2586: c.drawRect(0, h - h * 3 / 4, w, h, p); break;
            case 0x2587: c.drawRect(0, h - h * 7 / 8, w, h, p); break;
            case 0x2588: c.drawRect(0, 0, w, h, p); break;
            case 0x2589: c.drawRect(0, 0, w - w / 4, h, p); break;
            case 0x258A: c.drawRect(0, 0, w - hw, h, p); break;
            case 0x258B: c.drawRect(0, 0, w - hw - w / 4, h, p); break;
            case 0x258C: c.drawRect(0, 0, hw, h, p); break;
            case 0x258D: c.drawRect(w - hw - w / 4, 0, w, h, p); break;
            case 0x258E: c.drawRect(w - hw, 0, w, h, p); break;
            case 0x258F: c.drawRect(w - w / 4, 0, w, h, p); break;
            case 0x2590: c.drawRect(hw, 0, w, h, p); break;
            case 0x2591: drawDither(c, w, h, 4, p); break;
            case 0x2592: drawDither(c, w, h, 2, p); break;
            case 0x2593: drawDither(c, w, h, 1, p); break;
            case 0x2594: c.drawRect(0, 0, w, Math.max(1, h / 8), p); break;
            case 0x2595: c.drawRect(w - Math.max(1, w / 8), 0, w, h, p); break;
            case 0x2596: c.drawRect(0, hh, hw, h, p); break;
            case 0x2597: c.drawRect(hw, hh, w, h, p); break;
            case 0x2598: c.drawRect(0, 0, hw, hh, p); break;
            case 0x2599: c.drawRect(0, 0, hw, h, p); c.drawRect(hw, hh, w, h, p); break;
            case 0x259A: c.drawRect(0, 0, hw, hh, p); c.drawRect(hw, hh, w, h, p); break;
            case 0x259B: c.drawRect(0, 0, w, hh, p); c.drawRect(0, hh, hw, h, p); break;
            case 0x259C: c.drawRect(0, 0, hw, h, p); c.drawRect(hw, 0, w, hh, p); break;
            case 0x259D: c.drawRect(hw, 0, w, hh, p); break;
            case 0x259E: c.drawRect(hw, 0, w, hh, p); c.drawRect(0, hh, hw, h, p); break;
            case 0x259F: c.drawRect(0, 0, hw, h, p); c.drawRect(hw, 0, w, hh, p); break;
        }
    }

    private static void drawDither(Canvas c, int w, int h, int step, Paint p) {
        for (int y = 0; y < h; y += step)
            for (int x = ((y / step) & 1) * step; x < w; x += step * 2)
                c.drawRect(x, y, x + step, y + step, p);
    }

    // ---- Scanlines (0x23BA..0x23BD) ----

    private static void drawScanline(Canvas c, int cp, int w, int h, Paint p) {
        int scanH = Math.max(1, h / 9);
        switch (cp) {
            case 0x23BA: c.drawRect(0, 0, w, scanH, p); break;
            case 0x23BB: c.drawRect(0, h / 3 - scanH / 2, w, h / 3 + scanH / 2, p); break;
            case 0x23BC: c.drawRect(0, h * 2 / 3 - scanH / 2, w, h * 2 / 3 + scanH / 2, p); break;
            case 0x23BD: c.drawRect(0, h - scanH, w, h, p); break;
        }
    }

    // ---- Vertical Box Lines (0x23B8, 0x23B9) ----

    private static void drawVertBoxLine(Canvas c, int cp, int w, int h, Paint p) {
        int hw2 = w / 2;
        if (cp == 0x23B8) {
            c.drawRect(0, 0, hw2, h, p);
        } else {
            c.drawRect(w - hw2, 0, w, h, p);
        }
    }

    // ---- Triangles (0x25E2..0x25E5) ----

    private static void drawTriangle(Canvas c, int cp, int w, int h, Paint p) {
        Path path = new Path();
        switch (cp) {
            case 0x25E2: // lower right
                path.moveTo(0, h); path.lineTo(w, 0); path.lineTo(w, h); path.close();
                break;
            case 0x25E3: // lower left
                path.moveTo(0, 0); path.lineTo(w, h); path.lineTo(0, h); path.close();
                break;
            case 0x25E4: // upper left
                path.moveTo(0, 0); path.lineTo(w, 0); path.lineTo(0, h); path.close();
                break;
            case 0x25E5: // upper right
                path.moveTo(0, 0); path.lineTo(w, 0); path.lineTo(w, h); path.close();
                break;
        }
        c.drawPath(path, p);
    }

    // ---- Arcs (0x256D..0x2570) ----

    private static void drawArc(Canvas c, int cp, int w, int h, Paint p) {
        float thickness = Math.max(1f, Math.min(w, h) / 8f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(thickness);
        p.setStrokeCap(Paint.Cap.BUTT);
        RectF rect = new RectF(thickness / 2f, thickness / 2f, w - thickness / 2f, h - thickness / 2f);
        switch (cp) {
            case 0x256D: c.drawArc(rect, 270, 90, false, p); break;
            case 0x256E: c.drawArc(rect, 270, -90, false, p); break;
            case 0x256F: c.drawArc(rect, 90, 90, false, p); break;
            case 0x2570: c.drawArc(rect, 90, -90, false, p); break;
        }
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Box Diagonals (0x2571..0x2573) ----

    private static void drawBoxDiagonal(Canvas c, int cp, int w, int h, Paint p) {
        float thickness = Math.max(1f, Math.min(w, h) / 6f);
        p.setStrokeWidth(thickness);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        switch (cp) {
            case 0x2571: c.drawLine(w, 0, 0, h, p); break;
            case 0x2572: c.drawLine(0, 0, w, h, p); break;
            case 0x2573:
                c.drawLine(w, 0, 0, h, p);
                c.drawLine(0, 0, w, h, p);
                break;
        }
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Sextants (U+1FB00..U+1FB3B) ----

    private static void drawSextant(Canvas c, int bits, int w, int h, Paint p) {
        if (w < 2 || h < 3) return;
        for (int r = 0; r < 3; r++) {
            int y0 = r * h / 3;
            int y1 = (r + 1) * h / 3;
            if (y1 <= y0) y1 = y0 + 1;
            if ((bits & 0b01) != 0)
                c.drawRect(0, y0, w / 2, y1, p);
            if ((bits & 0b10) != 0)
                c.drawRect(w / 2, y0, w, y1, p);
            bits >>= 2;
        }
    }

    // ---- Octant table (U+1CD00..U+1CDE5) ----

    private static void drawOctantFromTable(Canvas c, int cp, int w, int h, Paint p) {
        int idx = cp - OCTANT_START;
        if (idx < 0 || idx >= sOctantTable.length) return;
        int bits = sOctantTable[idx];
        drawOctantBits(c, bits, w, h, p);
    }

    private static void drawOctantBits(Canvas c, int bits, int w, int h, Paint p) {
        if (w < 2 || h < 4) return;
        for (int r = 0; r < 4; r++) {
            int y0 = r * h / 4;
            int y1 = (r + 1) * h / 4;
            if (y1 <= y0) y1 = y0 + 1;
            if ((bits & 0b01) != 0)
                c.drawRect(0, y0, w / 2, y1, p);
            if ((bits & 0b10) != 0)
                c.drawRect(w / 2, y0, w, y1, p);
            bits >>= 2;
        }
    }

    // ---- Diagonals (U+1FBD0..U+1FBDF) ----

    private static void drawDiagonal(Canvas c, int index, int w, int h, Paint p) {
        float lw = Math.max(1f, Math.min(w, h) / 5f);
        p.setStrokeWidth(lw);
        p.setStyle(Paint.Style.FILL);
        Path path = new Path();
        switch (index) {
            case 0: path.moveTo(0, 0); path.lineTo(w, h); path.lineTo(w, 0); path.close(); break;
            case 1: path.moveTo(0, 0); path.lineTo(w, h); path.lineTo(0, h); path.close(); break;
            case 2: path.moveTo(w, 0); path.lineTo(0, h); path.lineTo(0, 0); path.close(); break;
            case 3: path.moveTo(w, 0); path.lineTo(0, h); path.lineTo(w, h); path.close(); break;
            case 4: path.moveTo(0, 0); path.lineTo(w, h); path.lineTo(0, h); path.close(); break;
            case 5: path.moveTo(0, 0); path.lineTo(w, h); path.lineTo(w, 0); path.close(); break;
            case 6: path.moveTo(w, 0); path.lineTo(0, h); path.lineTo(0, 0); path.close(); break;
            case 7: path.moveTo(w, 0); path.lineTo(0, h); path.lineTo(w, h); path.close(); break;
            case 8: path.moveTo(0, h/2); path.lineTo(w, h/2); path.lineTo(w, h); path.lineTo(0, h); path.close(); break;
            case 9: path.moveTo(0, 0); path.lineTo(w, 0); path.lineTo(w, h/2); path.lineTo(0, h/2); path.close(); break;
            case 10: path.moveTo(w/2, 0); path.lineTo(w/2, h); path.lineTo(w, h); path.lineTo(w, 0); path.close(); break;
            case 11: path.moveTo(0, 0); path.lineTo(w/2, 0); path.lineTo(w/2, h); path.lineTo(0, h); path.close(); break;
            case 12: path.moveTo(0, 0); path.lineTo(w, h); path.close(); p.setStyle(Paint.Style.STROKE); break;
            case 13: path.moveTo(w, 0); path.lineTo(0, h); path.close(); p.setStyle(Paint.Style.STROKE); break;
            case 14: path.moveTo(0, 0); path.lineTo(w, 0); path.lineTo(w, h/2); path.lineTo(0, h); path.close(); break;
            case 15: path.moveTo(0, h/2); path.lineTo(w, 0); path.lineTo(w, h); path.lineTo(0, h/2); path.close(); break;
        }
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Fill Patterns (U+1FB95..U+1FB99) ----

    private static void drawFillPattern(Canvas c, int cp, int w, int h, Paint p) {
        int step = Math.max(2, w / 6);
        switch (cp) {
            case 0x1FB95:
                for (int y = 0; y < h; y += step)
                    for (int x = ((y / step) & 1) * step; x < w; x += step * 2)
                        c.drawRect(x, y, x + step, y + step, p);
                break;
            case 0x1FB96:
                for (int y = 0; y < h; y += step)
                    for (int x = ((y / step + 1) & 1) * step; x < w; x += step * 2)
                        c.drawRect(x, y, x + step, y + step, p);
                break;
            case 0x1FB97:
                for (int x = 0; x < w; x += step * 2)
                    c.drawRect(x, 0, x + step, h, p);
                break;
            case 0x1FB98:
                for (int y = 0; y < h; y += step * 2)
                    c.drawRect(0, y, w, y + step, p);
                break;
            case 0x1FB99:
                for (int y = step * 2; y < h; y += step * 4)
                    for (int x = step * 2; x < w; x += step * 4)
                        c.drawRect(x, y, x + step, y + step, p);
                break;
        }
    }

    // ---- Legacy polygons U+1FB3C..U+1FB67 ----

    private static void drawLegacyPoly(Canvas c, int cp, int w, int h, Paint p) {
        int v = cp - LEGACY_POLY_START;
        if (v < 0 || v >= sLegacyPoly.length) return;
        fillPoly(c, w, h, 2, 3, sLegacyPoly[v], p);
    }

    private static final int[][] sLegacyPoly = {
        { 0, 2,  1, 3,  0, 3,  -1 },                /* 3c */
        { 0, 2,  2, 3,  0, 3,  -1 },                /* 3d */
        { 0, 1,  1, 3,  0, 3,  -1 },                /* 3e */
        { 0, 1,  2, 3,  0, 3,  -1 },                /* 3f */
        { 0, 0,  1, 3,  0, 3,  -1 },                /* 40 */
        { 0, 1,  1, 0,  2, 0,  2, 3,  0, 3,  -1 },  /* 41 */
        { 0, 1,  2, 0,  2, 3,  0, 3,  -1 },         /* 42 */
        { 0, 2,  1, 0,  2, 0,  2, 3,  0, 3,  -1 },  /* 43 */
        { 0, 2,  2, 0,  2, 3,  0, 3,  -1 },         /* 44 */
        { 0, 3,  1, 0,  2, 0,  2, 3,  -1 },         /* 45 */
        { 0, 2,  2, 1,  2, 3,  0, 3,  -1 },         /* 46 */
        { 1, 3,  2, 2,  2, 3,  -1 },                /* 47 */
        { 0, 3,  2, 2,  2, 3,  -1 },                /* 48 */
        { 1, 3,  2, 1,  2, 3,  -1 },                /* 49 */
        { 0, 3,  2, 1,  2, 3,  -1 },                /* 4a */
        { 1, 3,  2, 0,  2, 3,  -1 },                /* 4b */
        { 0, 0,  1, 0,  2, 1,  2, 3,  0, 3,  -1 },  /* 4c */
        { 0, 0,  2, 1,  2, 3,  0, 3,  -1 },         /* 4d */
        { 0, 0,  1, 0,  2, 2,  2, 3,  0, 3,  -1 },  /* 4e */
        { 0, 0,  2, 2,  2, 3,  0, 3,  -1 },         /* 4f */
        { 0, 0,  1, 0,  2, 3,  0, 3,  -1 },         /* 50 */
        { 0, 1,  2, 2,  2, 3,  0, 3,  -1 },         /* 51 */
        { 0, 0,  2, 0,  2, 3,  1, 3,  0, 2,  -1 },  /* 52 */
        { 0, 0,  2, 0,  2, 3,  0, 2,  -1 },         /* 53 */
        { 0, 0,  2, 0,  2, 3,  1, 3,  0, 1,  -1 },  /* 54 */
        { 0, 0,  2, 0,  2, 3,  0, 1,  -1 },         /* 55 */
        { 0, 0,  2, 0,  2, 3,  1, 3,  -1 },         /* 56 */
        { 0, 0,  1, 0,  0, 1,  -1 },                /* 57 */
        { 0, 0,  2, 0,  0, 1,  -1 },                /* 58 */
        { 0, 0,  1, 0,  0, 2,  -1 },                /* 59 */
        { 0, 0,  2, 0,  0, 2,  -1 },                /* 5a */
        { 0, 0,  1, 0,  0, 3,  -1 },                /* 5b */
        { 0, 0,  2, 0,  2, 1,  0, 2,  -1 },         /* 5c */
        { 0, 0,  2, 0,  2, 2,  1, 3,  0, 3,  -1 },  /* 5d */
        { 0, 0,  2, 0,  2, 2,  0, 3,  -1 },         /* 5e */
        { 0, 0,  2, 0,  2, 1,  1, 3,  0, 3,  -1 },  /* 5f */
        { 0, 0,  2, 0,  2, 1,  0, 3,  -1 },         /* 60 */
        { 0, 0,  2, 0,  1, 3,  0, 3,  -1 },         /* 61 */
        { 1, 0,  2, 0,  2, 1,  -1 },                /* 62 */
        { 0, 0,  2, 0,  2, 1,  -1 },                /* 63 */
        { 1, 0,  2, 0,  2, 2,  -1 },                /* 64 */
        { 0, 0,  2, 0,  2, 2,  -1 },                /* 65 */
        { 1, 0,  2, 0,  2, 3,  -1 },                /* 66 */
        { 0, 0,  2, 0,  2, 2,  0, 1,  -1 },         /* 67 */
    };

    // ---- Legacy polygons U+1FB68..U+1FB6F ----

    private static void drawLegacyPoly2(Canvas c, int cp, int w, int h, Paint p) {
        int v = cp - LEGACY_POLY2_START;
        if (v < 0 || v >= sLegacyPoly2.length) return;
        fillPoly(c, w, h, 2, 2, sLegacyPoly2[v], p);
    }

    private static final int[][] sLegacyPoly2 = {
        { 0, 0,  2, 0,  2, 2,  0, 2,  1, 1,  -1 },  /* 68 */
        { 0, 0,  1, 1,  2, 0,  2, 2,  0, 2,  -1 },  /* 69 */
        { 0, 0,  2, 0,  1, 1,  2, 2,  0, 2,  -1 },  /* 6a */
        { 0, 0,  2, 0,  2, 2,  1, 1,  0, 2,  -1 },  /* 6b */
        { 0, 0,  1, 1,  0, 2,  -1 },                /* 6c */
        { 0, 0,  2, 0,  1, 1,  -1 },                /* 6d */
        { 1, 1,  2, 0,  2, 2,  -1 },                /* 6e */
        { 1, 1,  2, 2,  0, 2,  -1 },                /* 6f */
    };

    // ---- Horizontal eighths U+1FB70..U+1FB75 (top blocks) ----

    private static void drawHorizEighth(Canvas c, int cp, int w, int h, Paint p) {
        int v = cp - HORIZ_EIGHTH_START + 1;
        fillRect(c, w, h, 8, 1, 0, 0, v, 1, p);
    }

    // ---- Vertical eighths U+1FB76..U+1FB7B (left blocks) ----

    private static void drawVertEighth(Canvas c, int cp, int w, int h, Paint p) {
        int v = cp - 0x1FB76 + 1;
        fillRect(c, w, h, 1, 8, 0, v, 1, v + 1, p);
    }

    // ---- Corner eighths U+1FB7C..U+1FB81 ----

    private static void drawCornerEighth(Canvas c, int cp, int w, int h, Paint p) {
        switch (cp) {
            case 0x1FB7C:
                fillRect(c, w, h, 1, 8, 0, 7, 1, 8, p);
                fillRect(c, w, h, 8, 1, 0, 0, 1, 1, p);
                break;
            case 0x1FB7D:
                fillRect(c, w, h, 1, 8, 0, 0, 1, 1, p);
                fillRect(c, w, h, 8, 1, 0, 0, 1, 1, p);
                break;
            case 0x1FB7E:
                fillRect(c, w, h, 1, 8, 0, 0, 1, 1, p);
                fillRect(c, w, h, 8, 1, 7, 0, 8, 1, p);
                break;
            case 0x1FB7F:
                fillRect(c, w, h, 1, 8, 0, 7, 1, 8, p);
                fillRect(c, w, h, 8, 1, 7, 0, 8, 1, p);
                break;
            case 0x1FB80:
                fillRect(c, w, h, 1, 8, 0, 0, 1, 1, p);
                fillRect(c, w, h, 1, 8, 0, 7, 1, 8, p);
                break;
            case 0x1FB81:
                fillRect(c, w, h, 1, 8, 0, 0, 1, 1, p);
                fillRect(c, w, h, 1, 8, 0, 2, 1, 3, p);
                fillRect(c, w, h, 1, 8, 0, 4, 1, 5, p);
                fillRect(c, w, h, 1, 8, 0, 7, 1, 8, p);
                break;
        }
    }

    // ---- Vertical strips U+1FB82..U+1FB86 (top-anchored left rect) ----

    private static void drawVertStrip(Canvas c, int cp, int w, int h, Paint p) {
        int v = cp - VERT_STRIP_START + 2;
        if (v >= 4) v++;
        fillRect(c, w, h, 1, 8, 0, 0, 1, v, p);
    }

    // ---- Right strips U+1FB87..U+1FB8B (right-anchored top rect) ----

    private static void drawRightStrip(Canvas c, int cp, int w, int h, Paint p) {
        int v = cp - RIGHT_STRIP_START + 2;
        if (v >= 4) v++;
        fillRect(c, w, h, 8, 1, 8 - v, 0, 8, 1, p);
    }

    // ---- Translucent quadrants U+1FB8C..U+1FB94 ----

    private static void drawTransQuad(Canvas c, int cp, int w, int h, Paint p) {
        Paint tp = new Paint();
        tp.setColor(0x7FFFFFFF);
        tp.setAntiAlias(false);
        switch (cp) {
            case 0x1FB8C: fillRect(c, w, h, 2, 1, 0, 0, 1, 1, tp); break;
            case 0x1FB8D: fillRect(c, w, h, 2, 1, 1, 0, 2, 1, tp); break;
            case 0x1FB8E: fillRect(c, w, h, 1, 2, 0, 0, 1, 1, tp); break;
            case 0x1FB8F: fillRect(c, w, h, 1, 2, 0, 1, 1, 2, tp); break;
            case 0x1FB90: fillRect(c, w, h, 1, 1, 0, 0, 1, 1, tp); break;
            case 0x1FB91:
                fillRect(c, w, h, 1, 2, 0, 0, 1, 1, p);
                fillRect(c, w, h, 1, 2, 0, 1, 1, 2, tp);
                break;
            case 0x1FB92:
                fillRect(c, w, h, 1, 2, 0, 1, 1, 2, p);
                fillRect(c, w, h, 1, 2, 0, 0, 1, 1, tp);
                break;
            case 0x1FB93: break;
            case 0x1FB94:
                fillRect(c, w, h, 2, 1, 1, 0, 2, 1, p);
                fillRect(c, w, h, 2, 1, 0, 0, 1, 1, tp);
                break;
        }
    }

    // ---- Diamonds U+1FB9A..U+1FB9F ----

    private static void drawDiamond(Canvas c, int cp, int w, int h, Paint p) {
        Paint tp = new Paint();
        tp.setColor(0x7FFFFFFF);
        tp.setAntiAlias(false);
        switch (cp) {
            case 0x1FB9A:
                fillPoly(c, w, h, 1, 1, new int[]{0, 0, 1, 0, 0, 1, 1, 1, -1}, p);
                break;
            case 0x1FB9B:
                fillPoly(c, w, h, 1, 1, new int[]{0, 0, 1, 1, 1, 0, 0, 1, -1}, p);
                break;
            case 0x1FB9C:
                fillPoly(c, w, h, 1, 1, new int[]{0, 0, 1, 0, 0, 1, -1}, tp);
                break;
            case 0x1FB9D:
                fillPoly(c, w, h, 1, 1, new int[]{0, 0, 1, 0, 1, 1, -1}, tp);
                break;
            case 0x1FB9E:
                fillPoly(c, w, h, 1, 1, new int[]{0, 1, 1, 0, 1, 1, -1}, tp);
                break;
            case 0x1FB9F:
                fillPoly(c, w, h, 1, 1, new int[]{0, 0, 1, 1, 0, 1, -1}, tp);
                break;
        }
    }

    // ---- Middle diagonals U+1FBA0..U+1FBAE ----

    private static void drawMiddleDiagonal(Canvas c, int cp, int w, int h, Paint p) {
        int lw = lineWidth(Math.min(w, h));
        float cx = (w & 1) != 0 ? w / 2f + 0.5f : w / 2f;
        int v = sMiddleDiagMap[cp - MIDDLE_DIAG_START];
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.SQUARE);
        p.setStrokeWidth(lw);
        if ((v & 1) != 0) {
            c.save();
            c.clipRect(0, 0, w, h);
            c.drawLine(cx, 0, cx - w, h, p);
            c.restore();
        }
        if ((v & 2) != 0) {
            c.save();
            c.clipRect(0, 0, w, h);
            c.drawLine(cx, 0, cx + w, h, p);
            c.restore();
        }
        if ((v & 4) != 0) {
            c.save();
            c.clipRect(0, 0, w, h);
            c.drawLine(cx - w, 0, cx, h, p);
            c.restore();
        }
        if ((v & 8) != 0) {
            c.save();
            c.clipRect(0, 0, w, h);
            c.drawLine(cx + w, 0, cx, h, p);
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static final int[] sMiddleDiagMap = {
        0b0001, 0b0010, 0b0100, 0b1000, 0b0101, 0b1010, 0b1100, 0b0011,
        0b1001, 0b0110, 0b1110, 0b1101, 0b1011, 0b0111, 0b1111
    };

    // ---- 1FBAF ----

    private static void drawFbaf(Canvas c, int w, int h, Paint p) {
        int lw = lineWidth(Math.min(w, h));
        fillRect(c, w, h, 1, 3, 0, 1, 1, 2, p);
        c.drawRect(w / 2 - lw / 2, 0, w / 2 + lw / 2, h, p);
    }

    // ---- Negative cross U+1FBBD ----

    private static void drawNegativeCross(Canvas c, int w, int h, Paint p) {
        c.drawRect(0, 0, w, h, p);
        Paint clear = new Paint();
        clear.setColor(0);
        clear.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clear.setStrokeWidth(lineWidth(Math.min(w, h)));
        clear.setStyle(Paint.Style.STROKE);
        clear.setStrokeCap(Paint.Cap.SQUARE);
        int dx = (int)clear.getStrokeWidth() / 2;
        c.save();
        c.clipRect(-dx, 0, w + dx, h);
        c.drawLine(w, 0, 0, h, clear);
        c.drawLine(0, 0, w, h, clear);
        c.restore();
    }

    // ---- Negative diagonal U+1FBBE..U+1FBBF ----

    private static void drawNegativeDiag(Canvas c, int cp, int w, int h, Paint p) {
        c.drawRect(0, 0, w, h, p);
        Paint clear = new Paint();
        clear.setColor(0);
        clear.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clear.setStyle(Paint.Style.STROKE);
        clear.setStrokeCap(Paint.Cap.SQUARE);
        clear.setStrokeWidth(lineWidth(Math.min(w, h)));
        float cx = (w & 1) != 0 ? w / 2f + 0.5f : w / 2f;
        int map = (cp == 0x1FBBE) ? 0b1000 : 0b1111;
        if ((map & 1) != 0) { c.save(); c.clipRect(0, 0, w, h); c.drawLine(cx, 0, cx - w, h, clear); c.restore(); }
        if ((map & 2) != 0) { c.save(); c.clipRect(0, 0, w, h); c.drawLine(cx, 0, cx + w, h, clear); c.restore(); }
        if ((map & 4) != 0) { c.save(); c.clipRect(0, 0, w, h); c.drawLine(cx - w, 0, cx, h, clear); c.restore(); }
        if ((map & 8) != 0) { c.save(); c.clipRect(0, 0, w, h); c.drawLine(cx + w, 0, cx, h, clear); c.restore(); }
    }

    // ---- Two-thirds block U+1FBCE..U+1FBCF ----

    private static void drawTwoThirdsBlock(Canvas c, int cp, int w, int h, Paint p) {
        int twoThirds = w * 2 / 3;
        if ((cp & 1) != 0) {
            c.drawRect(0, 0, w - twoThirds, h, p);
        } else {
            c.drawRect(0, 0, twoThirds, h, p);
        }
    }

    // ---- Centre quarter blocks U+1FBE4..U+1FBE7 ----

    private static void drawCentreQuarter(Canvas c, int cp, int w, int h, Paint p) {
        switch (cp) {
            case 0x1FBE4:
                fillRect(c, w, h, 4, 1, 1, 0, 2, 1, p);
                break;
            case 0x1FBE5:
                fillRect(c, w, h, 4, 1, 1, 0, 2, 1, p);
                break;
            case 0x1FBE6:
                drawOctantBits(c, 0b0001_0100, w, h, p);
                break;
            case 0x1FBE7:
                drawOctantBits(c, 0b0010_1000, w, h, p);
                break;
        }
    }

    // ---- Separated quadrants U+1CC21..U+1CC2F ----

    private static void drawSeparatedQuadrant(Canvas c, int cp, int w, int h, Paint p) {
        int pattern = (cp - SEP_QUADRANT_START + 1) & 0xF;
        int halfW = Math.max(1, w / 2);
        int halfH = Math.max(1, h / 2);
        int gap = Math.max(1, Math.min(halfW, halfH) / 4);
        if ((pattern & 1) != 0)
            c.drawRect(0, 0, halfW, halfH, p);
        if ((pattern & 2) != 0)
            c.drawRect(halfW + gap, 0, w, halfH, p);
        if ((pattern & 4) != 0)
            c.drawRect(0, halfH + gap, halfW, h, p);
        if ((pattern & 8) != 0)
            c.drawRect(halfW + gap, halfH + gap, w, h, p);
    }

    // ---- Box drawing extensions U+1CC1B..U+1CC1E ----

    private static void drawBoxExt1(Canvas c, int cp, int w, int h, Paint p) {
        int lw = lineWidth(Math.min(w, h));
        int halfH = h / 2;
        switch (cp) {
            case 0x1CC1B:
                c.drawRect(0, halfH - lw / 2, w, halfH + lw / 2, p);
                c.drawRect(w - lw, 0, w, halfH + lw / 2, p);
                break;
            case 0x1CC1C:
                c.drawRect(0, halfH - lw / 2, w, halfH + lw / 2, p);
                c.drawRect(w - lw, halfH - lw / 2, w, h, p);
                break;
            case 0x1CC1D:
                c.drawRect(0, 0, w, lw, p);
                c.drawRect(0, 0, lw, halfH, p);
                break;
            case 0x1CC1E:
                c.drawRect(0, h - lw, w, h, p);
                c.drawRect(0, halfH, lw, h, p);
                break;
        }
    }

    // ---- Double diagonals U+1CC1F..U+1CC20 ----

    private static void drawDoubleDiag(Canvas c, int cp, int w, int h, Paint p) {
        int lw = lineWidth(Math.min(w, h));
        int spacing = Math.min(lw * 3, h / 2);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw);
        int x1 = w, y0 = (cp == 0x1CC20) ? h : 0;
        int y1 = (cp == 0x1CC20) ? 0 : h;
        c.save();
        c.clipRect(0, 0, w, h);
        c.drawLine(0, y0 - spacing, x1, y1 - spacing, p);
        c.drawLine(0, y0 + spacing, x1, y1 + spacing, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Twelfth circles U+1CC30..U+1CC3F ----

    private static void drawTwelfthCircle(Canvas c, int cp, int w, int h, Paint p) {
        int v = cp - TWELFTH_CIRCLE_START;
        boolean quarter = (v == 5 || v == 6 || v == 9 || v == 10);
        int dx, dy, r;
        if (quarter) {
            int idx = (v == 5) ? 0 : (v == 6) ? 1 : (v == 9) ? 2 : 3;
            dx = (idx == 0 || idx == 2) ? 0 : -2;
            dy = (idx == 0 || idx == 1) ? 0 : -2;
            r = 1;
        } else {
            int col = 2 - (v & 3);
            int row = 2 - (v >> 2);
            dx = col;
            dy = row;
            r = 2;
        }
        float lw = lineWidth(Math.min(w, h));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw);
        p.setAntiAlias(true);
        c.save();
        c.clipRect(0, 0, w, h);
        float cx = w * (2 - dx) / 4f;
        float cy = h * (2 - dy) / 4f;
        float radius = r * w / 4f - lw;
        c.drawCircle(cx, cy, radius, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Double diagonal middle U+1CE09..U+1CE0A ----

    private static void drawDoubleDiagMid(Canvas c, int cp, int w, int h, Paint p) {
        int lw = lineWidth(Math.min(w, h));
        float cx = (w & 1) != 0 ? w / 2f + 0.5f : w / 2f;
        float cy = (h & 1) != 0 ? h / 2f + 0.5f : h / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw);
        c.save();
        c.clipRect(0, 0, w, h);
        if ((cp & 1) != 0) {
            c.drawLine(0, h, cx, cy, p);
            c.drawLine(cx, cy, w, h, p);
            c.drawLine(0, cy, cx, 0, p);
            c.drawLine(cx, 0, w, cy, p);
        } else {
            c.drawLine(0, 0, cx, cy, p);
            c.drawLine(cx, cy, w, 0, p);
            c.drawLine(0, cy, cx, h, p);
            c.drawLine(cx, h, w, cy, p);
        }
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Box drawing EXT2 U+1CE16..U+1CE19 ----

    private static void drawBoxExt2(Canvas c, int cp, int w, int h, Paint p) {
        int lw = lineWidth(Math.min(w, h));
        int halfW = w / 2;
        boolean top = (cp & 1) == 0;
        boolean left = cp >= 0x1CE18;
        int sy = top ? 0 : h - lw;
        if (top) {
            c.drawRect(halfW - lw / 2, sy + lw, lw, h - lw, p);
        } else {
            c.drawRect(halfW - lw / 2, 0, lw, sy, p);
        }
        if (left) {
            c.drawRect(0, sy, halfW + lw / 2, lw, p);
        } else {
            c.drawRect(halfW - lw / 2, sy, w - halfW + lw / 2, lw, p);
        }
    }

    // ---- Separated sextants U+1CE51..U+1CE8F ----

    private static void drawSeparatedSextant(Canvas c, int cp, int w, int h, Paint p) {
        int idx = cp - SEP_SEXTANT_START;
        if (idx < 0 || idx > 62) return;
        int bits = idx + 1;
        if (bits >= 0x15) bits++;
        if (bits >= 0x2A) bits++;
        int halfW = Math.max(1, w / 2);
        int thirdH = Math.max(1, h / 3);
        int gap = Math.max(1, Math.min(halfW, thirdH) / 4);
        int[] yOffsets = {0, thirdH + gap, 2 * (thirdH + gap)};
        int[] xOffsets = {0, halfW + gap};
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 2; col++) {
                if ((bits & 1) == 0) { bits >>= 1; continue; }
                int x0 = xOffsets[col];
                int y0 = yOffsets[r];
                int x1 = (col == 0) ? halfW : w;
                int y1 = (r == 2) ? h : (r + 1) * thirdH + r * gap;
                c.drawRect(x0, y0, x1, y1, p);
                bits >>= 1;
            }
        }
    }

    // ---- Sixteenths U+1CE90..U+1CEAF ----

    private static void drawSixteenth(Canvas c, int cp, int w, int h, Paint p) {
        int idx = cp - SIXTEENTH_START;
        if (idx < 0 || idx >= sSixteenthTable.length) return;
        int value = sSixteenthTable[idx];
        int qw = w / 4;
        int qh = h / 4;
        int ew = w % 4;
        int eh = h % 4;
        int[] ws = {qw, qw + (ew > 2 ? 1 : 0), qw + (ew > 0 ? 1 : 0), qw + (ew > 1 ? 1 : 0)};
        int[] hs = {qh, qh + (eh > 2 ? 1 : 0), qh + (eh > 0 ? 1 : 0), qh + (eh > 1 ? 1 : 0)};
        int y0 = 0;
        for (int r = 0; r < 4; r++) {
            int x0 = 0;
            for (int col = 0; col < 4; col++) {
                if ((value & 1) != 0)
                    c.drawRect(x0, y0, x0 + ws[col], y0 + hs[r], p);
                value >>= 1;
                x0 += ws[col];
            }
            y0 += hs[r];
        }
    }

    // ---- Fill characters U+1CC40..U+1CC47 ----

    private static void drawFillChar(Canvas c, int cp, int w, int h, Paint p) {
        int step = Math.max(2, Math.min(w, h) / 6);
        switch (cp) {
            case 0x1CC40:
                for (int y = step; y < h; y += step * 3)
                    c.drawRect(0, y, w, y + step, p);
                break;
            case 0x1CC41:
                for (int x = step; x < w; x += step * 3)
                    c.drawRect(x, 0, x + step, h, p);
                break;
            case 0x1CC42:
                for (int x = 0; x < w; x += step * 2)
                    c.drawRect(x, 0, x + step, h, p);
                for (int y = 0; y < h; y += step * 2)
                    c.drawRect(0, y, w, y + step, p);
                break;
            case 0x1CC43: {
                Paint dp = new Paint();
                dp.setColor(0xFFFFFFFF);
                dp.setStrokeWidth(step);
                dp.setAntiAlias(false);
                for (int i = -w; i < w + h; i += step * 2) {
                    dp.setColor(0xFFFFFFFF);
                    c.drawLine(i, 0, i - h, h, dp);
                    dp.setColor(0x7FFFFFFF);
                    c.drawLine(i + step, 0, i + step - h, h, dp);
                }
                break;
            }
            case 0x1CC44:
                for (int x = 0; x < w; x += step)
                    c.drawRect(x, 0, x + step / 2, h, p);
                break;
            case 0x1CC45:
                for (int y = 0; y < h; y += step)
                    c.drawRect(0, y, w, y + step / 2, p);
                break;
            case 0x1CC46:
                for (int y = 0; y < h; y += step * 2)
                    for (int x = (y / step % 2) * step; x < w; x += step * 4)
                        c.drawRect(x, y, x + step / 2, y + step / 2, p);
                break;
            case 0x1CC47:
                for (int y = 0; y < h; y += step * 2)
                    for (int x = step + (y / step % 2) * step; x < w; x += step * 4)
                        c.drawRect(x, y, x + step / 2, y + step / 2, p);
                break;
        }
    }

    // ---- Octant lookup table ----

    private static final int[] sOctantTable = {
        0b0000_0100, 0b0000_0110, 0b0000_0111, 0b0000_1000, 0b0000_1001, 0b0000_1011, 0b0000_1100, 0b0000_1101,
        0b0000_1110, 0b0001_0000, 0b0001_0001, 0b0001_0010, 0b0001_0011, 0b0001_0101, 0b0001_0110, 0b0001_0111,
        0b0001_1000, 0b0001_1001, 0b0001_1010, 0b0001_1011, 0b0001_1100, 0b0001_1101, 0b0001_1110, 0b0001_1111,
        0b0010_0000, 0b0010_0001, 0b0010_0010, 0b0010_0011, 0b0010_0100, 0b0010_0101, 0b0010_0110, 0b0010_0111,
        0b0010_1001, 0b0010_1010, 0b0010_1011, 0b0010_1100, 0b0010_1101, 0b0010_1110, 0b0010_1111, 0b0011_0000,
        0b0011_0001, 0b0011_0010, 0b0011_0011, 0b0011_0100, 0b0011_0101, 0b0011_0110, 0b0011_0111, 0b0011_1000,
        0b0011_1001, 0b0011_1010, 0b0011_1011, 0b0011_1100, 0b0011_1101, 0b0011_1110, 0b0100_0001, 0b0100_0010,
        0b0100_0011, 0b0100_0100, 0b0100_0101, 0b0100_0110, 0b0100_0111, 0b0100_1000, 0b0100_1001, 0b0100_1010,
        0b0100_1011, 0b0100_1100, 0b0100_1101, 0b0100_1110, 0b0100_1111, 0b0101_0001, 0b0101_0010, 0b0101_0011,
        0b0101_0100, 0b0101_0110, 0b0101_0111, 0b0101_1000, 0b0101_1001, 0b0101_1011, 0b0101_1100, 0b0101_1101,
        0b0101_1110, 0b0110_0000, 0b0110_0001, 0b0110_0010, 0b0110_0011, 0b0110_0100, 0b0110_0101, 0b0110_0110,
        0b0110_0111, 0b0110_1000, 0b0110_1001, 0b0110_1010, 0b0110_1011, 0b0110_1100, 0b0110_1101, 0b0110_1110,
        0b0110_1111, 0b0111_0000, 0b0111_0001, 0b0111_0010, 0b0111_0011, 0b0111_0100, 0b0111_0101, 0b0111_0110,
        0b0111_0111, 0b0111_1000, 0b0111_1001, 0b0111_1010, 0b0111_1011, 0b0111_1100, 0b0111_1101, 0b0111_1110,
        0b0111_1111, 0b1000_0001, 0b1000_0010, 0b1000_0011, 0b1000_0100, 0b1000_0101, 0b1000_0110, 0b1000_0111,
        0b1000_1000, 0b1000_1001, 0b1000_1010, 0b1000_1011, 0b1000_1100, 0b1000_1101, 0b1000_1110, 0b1000_1111,
        0b1001_0000, 0b1001_0001, 0b1001_0010, 0b1001_0011, 0b1001_0100, 0b1001_0101, 0b1001_0110, 0b1001_0111,
        0b1001_1000, 0b1001_1001, 0b1001_1010, 0b1001_1011, 0b1001_1100, 0b1001_1101, 0b1001_1110, 0b1001_1111,
        0b1010_0001, 0b1010_0010, 0b1010_0011, 0b1010_0100, 0b1010_0110, 0b1010_0111, 0b1010_1000, 0b1010_1001,
        0b1010_1011, 0b1010_1100, 0b1010_1101, 0b1010_1110, 0b1011_0000, 0b1011_0001, 0b1011_0010, 0b1011_0011,
        0b1011_0100, 0b1011_0101, 0b1011_0110, 0b1011_0111, 0b1011_1000, 0b1011_1001, 0b1011_1010, 0b1011_1011,
        0b1011_1100, 0b1011_1101, 0b1011_1110, 0b1011_1111, 0b1100_0001, 0b1100_0010, 0b1100_0011, 0b1100_0100,
        0b1100_0101, 0b1100_0110, 0b1100_0111, 0b1100_1000, 0b1100_1001, 0b1100_1010, 0b1100_1011, 0b1100_1100,
        0b1100_1101, 0b1100_1110, 0b1100_1111, 0b1101_0000, 0b1101_0001, 0b1101_0010, 0b1101_0011, 0b1101_0100,
        0b1101_0101, 0b1101_0110, 0b1101_0111, 0b1101_1000, 0b1101_1001, 0b1101_1010, 0b1101_1011, 0b1101_1100,
        0b1101_1101, 0b1101_1110, 0b1101_1111, 0b1110_0000, 0b1110_0001, 0b1110_0010, 0b1110_0011, 0b1110_0100,
        0b1110_0101, 0b1110_0110, 0b1110_0111, 0b1110_1000, 0b1110_1001, 0b1110_1010, 0b1110_1011, 0b1110_1100,
        0b1110_1101, 0b1110_1110, 0b1110_1111, 0b1111_0001, 0b1111_0010, 0b1111_0011, 0b1111_0100, 0b1111_0110,
        0b1111_0111, 0b1111_1000, 0b1111_1001, 0b1111_1011, 0b1111_1101, 0b1111_1110,
    };

    // ---- Sixteenth table U+1CE90..U+1CEAF ----

    private static final int[] sSixteenthTable = {
        0b0000_0000_0000_0001, 0b0000_0000_0000_0010, 0b0000_0000_0000_0100, 0b0000_0000_0000_1000,
        0b0000_0000_0001_0000, 0b0000_0000_0010_0000, 0b0000_0000_0100_0000, 0b0000_0000_1000_0000,
        0b0000_0001_0000_0000, 0b0000_0010_0000_0000, 0b0000_0100_0000_0000, 0b0000_1000_0000_0000,
        0b0001_0000_0000_0000, 0b0010_0000_0000_0000, 0b0100_0000_0000_0000, 0b1000_0000_0000_0000,
        0b1100_0000_0000_0000, 0b1110_0000_0000_0000, 0b0111_0000_0000_0000, 0b0011_0000_0000_0000,
        0b0001_0001_0000_0000, 0b0001_0001_0001_0000, 0b0000_0001_0001_0001, 0b0000_0000_0001_0001,
        0b0000_0000_0000_0011, 0b0000_0000_0000_0111, 0b0000_0000_0000_1110, 0b0000_0000_0000_1100,
        0b0000_0000_1000_1000, 0b0000_1000_1000_1000, 0b1000_1000_1000_0000, 0b1000_1000_0000_0000,
    };

    private Minifont() {}
}
