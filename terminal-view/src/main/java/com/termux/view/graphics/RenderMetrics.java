package com.termux.view.graphics;

import android.graphics.Paint;
import android.graphics.Typeface;

public final class RenderMetrics {

    public final int cellWidth;
    public final int cellHeight;
    public final int baseline;
    public final int ascent;
    public final int descent;
    public final int leading;
    public final int fontLineSpacingAndAscent;

    public final float lightStem;
    public final float heavyStem;

    public final float underlineThickness;
    public final float underlinePosition;

    public final float overlineThickness;
    public final float overlinePosition;

    public final float strikethroughThickness;
    public final float strikethroughPosition;

    public final float curlyAmplitude;
    public final float curlyPeriod;

    public final float brailleDotRadius;
    public final float brailleHSpacing;
    public final float brailleVSpacing;

    public RenderMetrics(int textSize, Typeface typeface) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(typeface);
        p.setTextSize(textSize);

        Paint.FontMetricsInt fm = p.getFontMetricsInt();
        ascent = -fm.ascent;
        descent = fm.descent;
        leading = fm.leading;
        cellHeight = (int) Math.ceil(fm.descent - fm.ascent + fm.leading);
        baseline = ascent;
        fontLineSpacingAndAscent = cellHeight + ascent;

        float fontWidth = p.measureText("X");
        cellWidth = Math.round(fontWidth);

        int dim = Math.min(cellWidth, cellHeight);
        lightStem = Math.max(1f, dim / 16f);
        heavyStem = Math.max(lightStem * 2f, lightStem + 1f);

        underlineThickness = Math.max(1f, textSize / 16f);
        underlinePosition = cellHeight + Math.max(underlineThickness, cellHeight * 0.125f);

        overlineThickness = underlineThickness;
        overlinePosition = underlineThickness * 0.5f;

        strikethroughThickness = underlineThickness;
        strikethroughPosition = cellHeight * 0.8f;

        curlyAmplitude = Math.max(underlineThickness * 2f, cellHeight * 0.08f);
        curlyPeriod = Math.max(underlineThickness * 6f, cellHeight * 0.35f);

        float cellMin = Math.min(cellWidth, cellHeight);
        brailleDotRadius = Math.max(0.8f, cellMin / 16f);
        brailleHSpacing = cellWidth / 3f;
        brailleVSpacing = cellHeight / 5f;
    }
}
