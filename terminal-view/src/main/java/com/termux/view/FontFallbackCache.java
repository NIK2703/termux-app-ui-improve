package com.termux.view;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.util.LruCache;

import com.termux.view.font.FontFallbackConfig;
import com.termux.view.font.ScriptClassifier;
import com.termux.view.text.EmojiData;

public final class FontFallbackCache {
    private final FontFallbackConfig mConfig;
    private final Paint mProbe = new Paint();
    private final Typeface mEmoji;
    private final Typeface[] mChain;

    private final LruCache<Long, Typeface> mCodepointCache = new LruCache<>(4096);
    public int cacheHitCount;
    public int cacheMissCount;

    public FontFallbackCache(FontFallbackConfig config) {
        this.mConfig = config;
        mEmoji = Typeface.create("sans-serif-emoji", Typeface.NORMAL);
        mChain = new Typeface[]{
            config.userNormal,
            mEmoji,
            Typeface.MONOSPACE,
        };
        if (Build.VERSION.SDK_INT >= 21) {
            mProbe.setFontFeatureSettings("liga 0, clig 0, calt 0");
        }
        if (config.locale != null) {
            try {
                mProbe.setTextLocale(config.locale);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public Typeface forCodePoint(int codePoint) {
        int script = ScriptClassifier.scriptForCodePoint(codePoint);
        return forCodePoint(codePoint, script);
    }

    public Typeface forCodePoint(int codePoint, int script) {
        long key = ((long) mConfig.fontGeneration << 32) | ((long) codePoint << 8) | script;
        Typeface cached = mCodepointCache.get(key);
        if (cached != null) { cacheHitCount++; return cached; }
        cacheMissCount++;

        Typeface result;

        if (isEmojiCodepoint(codePoint)) {
            result = mEmoji;
        } else if (isComplexScript(script)) {
            result = mChain[0];
        } else if (Build.VERSION.SDK_INT >= 23) {
            result = probeChain(codePoint);
            if (result == null) result = mChain[0];
        } else {
            result = mChain[0];
        }

        mCodepointCache.put(key, result);
        return result;
    }

    private Typeface probeChain(int codePoint) {
        String s = new String(Character.toChars(codePoint));
        for (Typeface tf : mChain) {
            mProbe.setTypeface(tf);
            if (mProbe.hasGlyph(s)) return tf;
        }
        return null;
    }

    private static boolean isEmojiCodepoint(int cp) {
        return EmojiData.isEmojiPresentation(cp)
            || EmojiData.isEmojiModifierBase(cp)
            || EmojiData.isSkinToneModifier(cp)
            || EmojiData.isRegionalIndicator(cp);
    }

    public void resetStats() {
        cacheHitCount = 0;
        cacheMissCount = 0;
    }

    public int cacheSize() { return mCodepointCache.size(); }

    private static boolean isComplexScript(int script) {
        return script == ScriptClassifier.SCRIPT_ARABIC
            || script == ScriptClassifier.SCRIPT_HEBREW
            || script == ScriptClassifier.SCRIPT_THAI
            || script == ScriptClassifier.SCRIPT_DEVANAGARI;
    }
}
