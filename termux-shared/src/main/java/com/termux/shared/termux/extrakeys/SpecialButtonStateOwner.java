package com.termux.shared.termux.extrakeys;

public interface SpecialButtonStateOwner {
    int getButtonTextColor();
    int getButtonActiveTextColor();
    int getButtonBackgroundColor();
    int getButtonActiveBackgroundColor();
    void invalidateView();
}
