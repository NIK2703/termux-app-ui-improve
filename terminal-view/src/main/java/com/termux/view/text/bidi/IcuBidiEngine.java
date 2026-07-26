package com.termux.view.text.bidi;

import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(24)
public final class IcuBidiEngine extends AbstractBidiEngine {

    @Override
    protected byte[] computeCharLevels(BidiInput input) {
        char[] text = input.text;
        int textStart = input.textStart;
        int textCount = input.textCount;
        int clusterCount = input.clusterCount;

        char[] subText;
        if (textStart == 0 && textCount == text.length) {
            subText = text;
        } else {
            subText = new char[textCount];
            System.arraycopy(text, textStart, subText, 0, textCount);
        }

        android.icu.text.Bidi bidi = new android.icu.text.Bidi();
        bidi.setPara(subText, android.icu.text.Bidi.LTR, null);

        byte[] levels = new byte[clusterCount];
        for (int i = 0; i < textCount && i < clusterCount; i++) {
            int level = bidi.getLevelAt(i);
            levels[i] = (byte) level;
        }

        if (!bidi.isMixed()) {
            return new byte[clusterCount];
        }

        return levels;
    }
}
