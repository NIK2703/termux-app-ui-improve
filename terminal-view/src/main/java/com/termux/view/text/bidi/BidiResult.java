package com.termux.view.text.bidi;

public final class BidiResult {
    public boolean hasBidi;
    public int clusterCount;
    public byte[] clusterLevels;
    public int[] visualClusterOrder;
    public int[] visualToLogicalColumn;
    public int[] logicalToVisualColumn;
    public boolean[] clusterRtl;
    public boolean[] visualColumnRtl;
    public int baseLevel;
}
