package com.termux.view.text.bidi;

public final class LegacyBidiEngine extends AbstractBidiEngine {

    @Override
    protected byte[] computeCharLevels(BidiInput input) {
        char[] text = input.text;
        int textStart = input.textStart;
        int textCount = input.textCount;
        int clusterCount = input.clusterCount;

        if (!java.text.Bidi.requiresBidi(text, textStart, textCount)) {
            return new byte[clusterCount];
        }

        java.text.Bidi bidi = new java.text.Bidi(text, textStart, null, 0, textCount,
            java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);

        if (!bidi.isMixed()) {
            return new byte[clusterCount];
        }

        byte[] levels = new byte[clusterCount];
        int runCount = bidi.getRunCount();
        for (int run = 0; run < runCount; run++) {
            byte level = (byte) bidi.getRunLevel(run);
            int runStart = bidi.getRunStart(run);
            int runLimit = bidi.getRunLimit(run);
            for (int i = runStart; i < runLimit; i++) {
                if (i < clusterCount) {
                    levels[i] = level;
                }
            }
        }
        return levels;
    }
}
