package com.termux.view.text.bidi;

import android.os.Build;

public final class BidiEngineFactory {

    private static volatile BidiEngine sEngine;

    private BidiEngineFactory() {}

    public static BidiEngine get() {
        if (sEngine == null) {
            synchronized (BidiEngineFactory.class) {
                if (sEngine == null) {
                    sEngine = createEngine();
                }
            }
        }
        return sEngine;
    }

    private static BidiEngine createEngine() {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Class<?> cls = Class.forName("com.termux.view.text.bidi.IcuBidiEngine");
                return (BidiEngine) cls.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return new LegacyBidiEngine();
            }
        }
        return new LegacyBidiEngine();
    }
}
