package com.termux.view.text.bidi;

public interface BidiEngine {
    BidiResult compute(BidiInput input, int columns);
}
