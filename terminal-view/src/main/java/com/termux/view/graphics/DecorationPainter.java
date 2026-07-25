package com.termux.view.graphics;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;

import com.termux.terminal.TextStyle;

public final class DecorationPainter {

    private static final Paint sPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static void drawPreText(Canvas canvas, float left, float right, float baseline,
                                    float cellTop, float cellHeight, float textSize, long style, int decoColor) {
        int effect = TextStyle.decodeEffect(style);
        int underlineVariant = TextStyle.decodeUnderlineVariant(style);

        if (underlineVariant == TextStyle.UNDERLINE_NONE
            && (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
            underlineVariant = TextStyle.UNDERLINE_SINGLE;
        }

        if (underlineVariant != TextStyle.UNDERLINE_NONE) {
            drawUnderline(canvas, left, right, baseline, cellHeight, textSize, underlineVariant, decoColor);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_OVERLINE) != 0) {
            drawOverline(canvas, left, right, cellTop, textSize, decoColor);
        }
    }

    public static void drawPostText(Canvas canvas, float left, float right, float baseline,
                                    float cellTop, float cellHeight, float textSize, long style, int decoColor) {
        int effect = TextStyle.decodeEffect(style);

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0) {
            drawStrikethrough(canvas, left, right, baseline, cellHeight, textSize, decoColor);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_BOXED) != 0) {
            drawBoxed(canvas, left, right, cellTop, cellHeight, textSize, decoColor);
        }
    }

    public static float computeThickness(float textSize) {
        return Math.max(1f, textSize / 16f);
    }

    private static void drawUnderline(Canvas canvas, float left, float right, float baseline,
                                       float cellHeight, float textSize, int variant, int color) {
        sPaint.setColor(color);
        float thickness = computeThickness(textSize);
        float underlineY = baseline + Math.max(thickness, cellHeight * 0.125f);

        switch (variant) {
            case TextStyle.UNDERLINE_SINGLE:
                sPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(left, underlineY, right, underlineY + thickness, sPaint);
                break;

            case TextStyle.UNDERLINE_DOUBLE: {
                sPaint.setStyle(Paint.Style.FILL);
                float gap = thickness;
                float y2 = underlineY + thickness + gap;
                float maxY = baseline + cellHeight - thickness;
                if (y2 > maxY) {
                    y2 = maxY;
                }
                canvas.drawRect(left, underlineY, right, underlineY + thickness, sPaint);
                canvas.drawRect(left, y2, right, y2 + thickness, sPaint);
                break;
            }

            case TextStyle.UNDERLINE_CURLY: {
                sPaint.setStyle(Paint.Style.STROKE);
                sPaint.setStrokeWidth(thickness);
                sPaint.setStrokeCap(Paint.Cap.ROUND);
                float amplitude = Math.max(thickness * 2f, cellHeight * 0.08f);
                float period = Math.max(thickness * 6f, cellHeight * 0.35f);
                float centerY = underlineY + thickness;

                Path path = new Path();
                path.moveTo(left, centerY);
                float x = left;
                while (x < right) {
                    float x1 = Math.min(x + period, right);
                    float mid = (x + x1) / 2f;
                    path.quadTo((x + mid) / 2f, centerY - amplitude, mid, centerY);
                    path.quadTo((mid + x1) / 2f, centerY + amplitude, x1, centerY);
                    x = x1;
                }
                canvas.drawPath(path, sPaint);
                sPaint.setStrokeCap(Paint.Cap.BUTT);
                break;
            }

            case TextStyle.UNDERLINE_DOTTED: {
                sPaint.setStyle(Paint.Style.STROKE);
                sPaint.setStrokeWidth(thickness);
                sPaint.setStrokeCap(Paint.Cap.ROUND);
                float dot = thickness;
                float gap = thickness * 2f;
                sPaint.setPathEffect(new DashPathEffect(new float[]{dot, gap}, 0f));
                Path path = new Path();
                path.moveTo(left, underlineY);
                path.lineTo(right, underlineY);
                canvas.drawPath(path, sPaint);
                sPaint.setPathEffect(null);
                sPaint.setStrokeCap(Paint.Cap.BUTT);
                break;
            }

            case TextStyle.UNDERLINE_DASHED: {
                sPaint.setStyle(Paint.Style.STROKE);
                sPaint.setStrokeWidth(thickness);
                sPaint.setStrokeCap(Paint.Cap.BUTT);
                float dash = thickness * 4f;
                float gap = thickness * 2f;
                sPaint.setPathEffect(new DashPathEffect(new float[]{dash, gap}, 0f));
                Path path = new Path();
                path.moveTo(left, underlineY);
                path.lineTo(right, underlineY);
                canvas.drawPath(path, sPaint);
                sPaint.setPathEffect(null);
                break;
            }
        }

        sPaint.setStyle(Paint.Style.FILL);
    }

    private static void drawOverline(Canvas canvas, float left, float right, float cellTop,
                                     float textSize, int color) {
        sPaint.setColor(color);
        sPaint.setStyle(Paint.Style.FILL);
        float thickness = computeThickness(textSize);
        canvas.drawRect(left, cellTop + thickness * 0.5f, right, cellTop + thickness * 1.5f, sPaint);
    }

    private static void drawStrikethrough(Canvas canvas, float left, float right, float baseline,
                                           float cellHeight, float textSize, int color) {
        sPaint.setColor(color);
        sPaint.setStyle(Paint.Style.FILL);
        float thickness = computeThickness(textSize);
        float y = baseline - cellHeight * 0.2f;
        canvas.drawRect(left, y, right, y + thickness, sPaint);
    }

    private static void drawBoxed(Canvas canvas, float left, float right, float cellTop,
                                   float cellHeight, float textSize, int color) {
        sPaint.setColor(color);
        sPaint.setStyle(Paint.Style.STROKE);
        sPaint.setStrokeWidth(computeThickness(textSize));
        canvas.drawRect(left, cellTop, right, cellTop + cellHeight, sPaint);
        sPaint.setStyle(Paint.Style.FILL);
    }

    private DecorationPainter() {}
}
