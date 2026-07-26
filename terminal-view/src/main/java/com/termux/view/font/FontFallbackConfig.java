package com.termux.view.font;

import android.graphics.Typeface;

import java.util.Locale;

public final class FontFallbackConfig {
    public Typeface userNormal;
    public Locale locale;
    public int fontGeneration;

    public FontFallbackConfig(Typeface userNormal, Locale locale, int fontGeneration) {
        this.userNormal = userNormal;
        this.locale = locale;
        this.fontGeneration = fontGeneration;
    }
}
