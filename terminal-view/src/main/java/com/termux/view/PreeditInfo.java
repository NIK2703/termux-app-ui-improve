package com.termux.view;

public final class PreeditInfo {
    public CharSequence text;
    public int cursor = -1;
    public int visibleRow = -1;
    public int startCol = 0;
    public boolean active;

    public void clear() {
        text = null;
        cursor = -1;
        visibleRow = -1;
        startCol = 0;
        active = false;
    }
}
