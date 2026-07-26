package com.termux.view.font;

import android.os.Build;

public final class ScriptClassifier {
    public static final int SCRIPT_COMMON = 0;
    public static final int SCRIPT_CJK = 1;
    public static final int SCRIPT_HANGUL = 2;
    public static final int SCRIPT_ARABIC = 3;
    public static final int SCRIPT_HEBREW = 4;
    public static final int SCRIPT_THAI = 5;
    public static final int SCRIPT_DEVANAGARI = 6;
    public static final int SCRIPT_JAPANESE = 7;

    public static int scriptForCodePoint(int cp) {
        if (Build.VERSION.SDK_INT >= 24) {
            return icuScript(cp);
        }
        return legacyScript(cp);
    }

    private static int icuScript(int cp) {
        try {
            Class<?> uscript = Class.forName("android.icu.lang.UScript");
            int code = (Integer) uscript.getMethod("getScript", int.class).invoke(null, cp);

            int USCRIPT_ARABIC = uscript.getField("ARABIC").getInt(null);
            int USCRIPT_HEBREW = uscript.getField("HEBREW").getInt(null);
            int USCRIPT_THAI = uscript.getField("THAI").getInt(null);
            int USCRIPT_DEVANAGARI = uscript.getField("DEVANAGARI").getInt(null);
            int USCRIPT_HANGUL = uscript.getField("HANGUL").getInt(null);
            int USCRIPT_HAN = uscript.getField("HAN").getInt(null);
            int USCRIPT_HIRAGANA = uscript.getField("HIRAGANA").getInt(null);
            int USCRIPT_KATAKANA = uscript.getField("KATAKANA").getInt(null);

            if (code == USCRIPT_ARABIC) return SCRIPT_ARABIC;
            if (code == USCRIPT_HEBREW) return SCRIPT_HEBREW;
            if (code == USCRIPT_THAI) return SCRIPT_THAI;
            if (code == USCRIPT_DEVANAGARI) return SCRIPT_DEVANAGARI;
            if (code == USCRIPT_HANGUL) return SCRIPT_HANGUL;
            if (code == USCRIPT_HAN) return SCRIPT_CJK;
            if (code == USCRIPT_HIRAGANA || code == USCRIPT_KATAKANA) return SCRIPT_JAPANESE;

            return SCRIPT_COMMON;
        } catch (Exception e) {
            return legacyScript(cp);
        }
    }

    private static int legacyScript(int cp) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        if (b == null) return SCRIPT_COMMON;
        if (b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || b == Character.UnicodeBlock.CJK_COMPATIBILITY
            || b == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS
            || b == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT
            || b == Character.UnicodeBlock.KANGXI_RADICALS) return SCRIPT_CJK;
        if (b == Character.UnicodeBlock.HANGUL_SYLLABLES
            || b == Character.UnicodeBlock.HANGUL_JAMO
            || b == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) return SCRIPT_HANGUL;
        if (b == Character.UnicodeBlock.ARABIC) return SCRIPT_ARABIC;
        if (b == Character.UnicodeBlock.HEBREW) return SCRIPT_HEBREW;
        if (b == Character.UnicodeBlock.THAI) return SCRIPT_THAI;
        if (b == Character.UnicodeBlock.DEVANAGARI) return SCRIPT_DEVANAGARI;
        if (b == Character.UnicodeBlock.HIRAGANA
            || b == Character.UnicodeBlock.KATAKANA
            || b == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS) return SCRIPT_JAPANESE;
        return SCRIPT_COMMON;
    }
}
