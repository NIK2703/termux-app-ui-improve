package com.termux.view.text.bidi;

import java.text.Bidi;

public abstract class AbstractBidiEngine implements BidiEngine {

    @Override
    public BidiResult compute(BidiInput input, int columns) {
        BidiResult result = new BidiResult();
        result.clusterCount = input.clusterCount;

        byte[] levels = computeCharLevels(input);
        result.clusterLevels = levels;

        boolean hasBidi = hasMixedLevels(levels);
        result.hasBidi = hasBidi;

        if (!hasBidi) {
            buildIdentityResult(input, columns, result);
            return result;
        }

        result.visualClusterOrder = new int[input.clusterCount];
        result.visualToLogicalColumn = new int[columns];
        result.logicalToVisualColumn = new int[columns];
        result.visualColumnRtl = new boolean[columns];
        result.clusterRtl = new boolean[input.clusterCount];

        reorderClusters(input, result);
        expandClustersToColumns(input, result, columns);

        return result;
    }

    protected abstract byte[] computeCharLevels(BidiInput input);

    protected void reorderClusters(BidiInput in, BidiResult result) {
        int n = result.clusterCount;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Bidi.reorderVisually(result.clusterLevels, 0, order, 0, n);
        for (int i = 0; i < n; i++) {
            result.visualClusterOrder[i] = order[i];
        }
    }

    protected void expandClustersToColumns(BidiInput in, BidiResult result, int columns) {
        for (int i = 0; i < columns; i++) {
            result.visualToLogicalColumn[i] = i;
            result.logicalToVisualColumn[i] = i;
            result.visualColumnRtl[i] = false;
        }
        for (int i = 0; i < result.clusterCount; i++) {
            result.clusterRtl[i] = false;
        }

        int visCol = 0;
        for (int visCi = 0; visCi < result.clusterCount; visCi++) {
            int logCi = result.visualClusterOrder[visCi];
            int logCol = in.clusterLogicalBaseColumn[logCi];
            int width = in.clusterWidth[logCi];
            byte level = result.clusterLevels[logCi];
            boolean isRtl = (level & 1) == 1;

            for (int w = 0; w < width; w++) {
                int vc = visCol + w;
                int lc = logCol + w;
                if (vc < columns && lc < columns) {
                    result.visualToLogicalColumn[vc] = lc;
                    result.logicalToVisualColumn[lc] = vc;
                    result.visualColumnRtl[vc] = isRtl;
                }
            }
            result.clusterRtl[logCi] = isRtl;

            visCol += width;
        }
    }

    protected void buildIdentityResult(BidiInput in, int columns, BidiResult result) {
        result.hasBidi = false;
        result.clusterLevels = new byte[in.clusterCount];
        result.visualClusterOrder = new int[in.clusterCount];
        result.visualToLogicalColumn = new int[columns];
        result.logicalToVisualColumn = new int[columns];
        result.visualColumnRtl = new boolean[columns];
        result.clusterRtl = new boolean[in.clusterCount];

        for (int i = 0; i < in.clusterCount; i++) {
            result.visualClusterOrder[i] = i;
        }
        for (int i = 0; i < columns; i++) {
            result.visualToLogicalColumn[i] = i;
            result.logicalToVisualColumn[i] = i;
        }
    }

    private static boolean hasMixedLevels(byte[] levels) {
        if (levels.length <= 1) return false;
        byte first = levels[0];
        for (int i = 1; i < levels.length; i++) {
            if (levels[i] != first) return true;
        }
        for (byte level : levels) {
            if ((level & 1) == 1) return true;
        }
        return false;
    }
}
