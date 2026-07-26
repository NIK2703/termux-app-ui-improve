package com.termux.view.text.bidi;

public final class BidiInput {
    public char[] text;
    public int textStart;
    public int textCount;
    public int clusterCount;
    public int[] clusterCharStart;
    public int[] clusterCharCount;
    public int[] clusterWidth;
    public int[] clusterFirstCodePoint;
    public int[] clusterLogicalBaseColumn;
    public int baseDirection;
}
