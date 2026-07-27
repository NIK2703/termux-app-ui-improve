package com.termux.shared.termux.extrakeys;

import android.content.res.ColorStateList;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** The {@link Class} that maintains a state of a {@link SpecialButton} */
public class SpecialButtonState {

    /** If special button has been created for the view. */
    boolean isCreated = false;
    /** If special button is active. */
    boolean isActive = false;
    /** If special button is locked due to long hold on it and should not be deactivated if its
     * state is read. */
    boolean isLocked = false;
    /** If special button is currently held down in {@link ExtraKeysView.SpecialButtonMode#HOLD} mode
     * and should stay active (and not be deactivated when its state is read) until released. */
    boolean isHolding = false;

    List<MaterialButton> buttons = new ArrayList<>();

    SpecialButtonStateOwner mOwner;

    /**
     * Initialize a {@link SpecialButtonState} to maintain state of a {@link SpecialButton}.
     *
     * @param owner The {@link SpecialButtonStateOwner} that owns this state and provides colors.
     */
    public SpecialButtonState(SpecialButtonStateOwner owner) {
        mOwner = owner;
    }

    /** Set {@link #isCreated}. */
    public void setIsCreated(boolean value) {
        isCreated = value;
    }

    /** Set {@link #isActive}. */
    public void setIsActive(boolean value) {
        isActive = value;
        for (MaterialButton button : buttons) {
            button.setTextColor(value ? mOwner.getButtonActiveTextColor() : mOwner.getButtonTextColor());
            button.setBackgroundTintList(ColorStateList.valueOf(value ? mOwner.getButtonActiveBackgroundColor() : mOwner.getButtonBackgroundColor()));
        }
        mOwner.invalidateView();
    }

    /** Set {@link #isLocked}. */
    public void setIsLocked(boolean value) {
        isLocked = value;
    }

    /** Set {@link #isHolding}. */
    public void setIsHolding(boolean value) {
        isHolding = value;
    }

}
