package com.termux.shared.termux.extrakeys;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.ArrayMap;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates touch/gesture/repeat handling for extra keys buttons.
 * Used by {@link ExtraKeysView} via the {@link Host} interface
 * so that all touch behavior (swipe, long-press repeat, HOLD mode, haptic feedback) is identical.
 */
public class ExtraKeysTouchHandler {

    // ── Runtime swipe state ──
    private float mTouchDownX;
    private float mTouchDownY;
    @Nullable
    private ExtraKeysView.SwipeDirection mRuntimeSwipeDirection;

    @Nullable
    private ExtraKeyButton mGestureActiveButton;
    @Nullable
    private MaterialButton mGestureActiveMaterialButton;
    @Nullable
    private View mGestureActiveView;

    private int mLongPressCount;
    @Nullable
    private ScheduledFuture<?> mRepetitiveFuture;
    private final ScheduledExecutorService mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    @Nullable
    private Handler mHandler;
    @Nullable
    private SpecialButtonsLongHoldRunnable mSpecialButtonsLongHoldRunnable;

    // ── Configuration ──
    private final Context mContext;
    private final int mSwipeThreshold;
    private final List<String> mRepetitiveKeys;
    private final ExtraKeysView.SpecialButtonMode mSpecialButtonMode;
    private final int mLongPressTimeout;
    private final int mLongPressRepeatDelay;

    // ── Host ──
    private final Host mHost;

    /** Host interface for the view that owns this handler. */
    public interface Host {
        @Nullable ExtraKeysView.IExtraKeysView getClient();
        @NonNull ArrayMap<SpecialButton, SpecialButtonState> getSpecialButtons();
        @Nullable Set<String> getSpecialButtonsKeys();
        @NonNull ColorStateList getButtonNormalTint();
        @NonNull ColorStateList getButtonActiveTint();
        int getLongPressTimeout();
        int getLongPressRepeatDelay();
        void invalidateHostView();
        void onSpecialButtonToggled(@NonNull ExtraKeyButton buttonInfo, @NonNull SpecialButtonState state);
        void onAnyExtraKeyButtonClick(View view, @NonNull ExtraKeyButton buttonInfo, MaterialButton button);
    }

    /**
     * Default implementation of {@link #onAnyExtraKeyButtonClick} that matches ExtraKeysView logic:
     * toggles special button state for special buttons, dispatches to client for regular buttons.
     */
    public static void defaultOnAnyExtraKeyButtonClick(Host host, int longPressCount,
                                                       ExtraKeysView.SpecialButtonMode mode,
                                                       View view, @NonNull ExtraKeyButton buttonInfo,
                                                       MaterialButton button) {
        Set<String> keys = host.getSpecialButtonsKeys();
        if (keys != null && keys.contains(buttonInfo.getKey())) {
            if (longPressCount > 0) return;
            if (mode == ExtraKeysView.SpecialButtonMode.HOLD) return;
            SpecialButtonState state = host.getSpecialButtons().get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null) return;
            state.setIsActive(!state.isActive);
            if (!state.isActive) state.setIsLocked(false);
            host.onSpecialButtonToggled(buttonInfo, state);
        } else {
            if (host.getClient() != null) {
                host.getClient().onExtraKeyButtonClick(view, buttonInfo, button);
            }
        }
    }

    public ExtraKeysTouchHandler(Context context, int swipeThreshold, List<String> repetitiveKeys,
                                  ExtraKeysView.SpecialButtonMode specialButtonMode,
                                  int longPressTimeout, int longPressRepeatDelay, Host host) {
        mContext = context;
        mSwipeThreshold = swipeThreshold;
        mRepetitiveKeys = repetitiveKeys;
        mSpecialButtonMode = specialButtonMode;
        mLongPressTimeout = longPressTimeout;
        mLongPressRepeatDelay = longPressRepeatDelay;
        mHost = host;
    }

    // ── Public API ──

    @Nullable
    public ExtraKeysView.SwipeDirection getRuntimeSwipeDirection() {
        return mRuntimeSwipeDirection;
    }

    @Nullable
    public View getGestureActiveView() {
        return mGestureActiveView;
    }

    public View.OnTouchListener createOnTouchListener(ExtraKeyButton buttonInfo, MaterialButton button) {
        return (view, event) -> handleTouchEvent(view, event, buttonInfo, button);
    }

    public void onDetachedFromWindow() {
        stopScheduledExecutors();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        mScheduledExecutor.shutdownNow();
    }

    public void stopAll() {
        stopScheduledExecutors();
    }

    public static ExtraKeyButton getSwipeExtraKeyButton(ExtraKeyButton buttonInfo,
                                                         ExtraKeysView.SwipeDirection direction) {
        if (buttonInfo == null || direction == null) return null;
        switch (direction) {
            case UP:    return buttonInfo.getSwipeUp();
            case DOWN:  return buttonInfo.getSwipeDown();
            case LEFT:  return buttonInfo.getSwipeLeft();
            case RIGHT: return buttonInfo.getSwipeRight();
        }
        return null;
    }

    /**
     * Expose haptic feedback so the host view's OnClickListener can call it for taps.
     * Matches ExtraKeysView.performExtraKeyButtonHapticFeedback().
     */
    public void performButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button, boolean isGesture) {
        performHapticFeedback(view, buttonInfo, button, isGesture);
    }

    // ── Internal ──

    private boolean handleTouchEvent(View view, MotionEvent event,
                                      ExtraKeyButton buttonInfo, MaterialButton button) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownX = event.getX();
                mTouchDownY = event.getY();
                mRuntimeSwipeDirection = null;

                setButtonTint(button, mHost.getButtonActiveTint());

                if (mSpecialButtonMode == ExtraKeysView.SpecialButtonMode.HOLD
                        && isSpecialButton(buttonInfo)) {
                    SpecialButtonState holdState = mHost.getSpecialButtons().get(
                            SpecialButton.valueOf(buttonInfo.getKey()));
                    if (holdState != null) {
                        holdState.setIsActive(true);
                        holdState.setIsHolding(true);
                    }
                    return true;
                }
                startScheduledExecutors(view, buttonInfo, button);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mRuntimeSwipeDirection != null) return true;

                float dx = event.getX() - mTouchDownX;
                float dy = event.getY() - mTouchDownY;
                ExtraKeysView.SwipeDirection swipeDir = detectDirection(dx, dy);
                if (swipeDir != null && getSwipeExtraKeyButton(buttonInfo, swipeDir) != null) {
                    mRuntimeSwipeDirection = swipeDir;
                    mHost.invalidateHostView();
                    stopScheduledExecutors();
                    setButtonTint(button, mHost.getButtonNormalTint());

                    if (mSpecialButtonMode == ExtraKeysView.SpecialButtonMode.HOLD
                            && isSpecialButton(buttonInfo)) {
                        endSpecialButtonHold(buttonInfo);
                    }

                    ExtraKeyButton swipeBtn = getSwipeExtraKeyButton(buttonInfo, swipeDir);
                    mGestureActiveButton = swipeBtn;
                    mGestureActiveMaterialButton = button;
                    mGestureActiveView = view;
                    mHost.onAnyExtraKeyButtonClick(view, swipeBtn, button);
                    performHapticFeedback(view, swipeBtn, button, true);

                    if (mRepetitiveKeys != null && mRepetitiveKeys.contains(swipeBtn.getKey())) {
                        mLongPressCount = 0;
                        mRepetitiveFuture = mScheduledExecutor.scheduleWithFixedDelay(() -> {
                            mLongPressCount++;
                            if (mHost.getClient() != null) {
                                mHost.getClient().onExtraKeyButtonClick(view, swipeBtn, button);
                            }
                        }, mLongPressTimeout, mLongPressRepeatDelay, TimeUnit.MILLISECONDS);
                    }
                    return true;
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                mRuntimeSwipeDirection = null;
                mHost.invalidateHostView();
                setButtonTint(button, mHost.getButtonNormalTint());
                stopScheduledExecutors();

                if (mGestureActiveButton != null && mHost.getClient() != null) {
                    mHost.getClient().onExtraKeyButtonGestureRelease(
                            mGestureActiveView != null ? mGestureActiveView : view,
                            mGestureActiveButton,
                            mGestureActiveMaterialButton != null ? mGestureActiveMaterialButton : button);
                }
                mGestureActiveButton = null;
                mGestureActiveMaterialButton = null;
                mGestureActiveView = null;

                if (mSpecialButtonMode == ExtraKeysView.SpecialButtonMode.HOLD
                        && isSpecialButton(buttonInfo)) {
                    endSpecialButtonHold(buttonInfo);
                }
                return true;

            case MotionEvent.ACTION_UP:
                setButtonTint(button, mHost.getButtonNormalTint());
                stopScheduledExecutors();

                if (mRuntimeSwipeDirection != null) {
                    mRuntimeSwipeDirection = null;
                    mHost.invalidateHostView();
                    if (mGestureActiveButton != null && mHost.getClient() != null) {
                        mHost.getClient().onExtraKeyButtonGestureRelease(
                                mGestureActiveView, mGestureActiveButton, mGestureActiveMaterialButton);
                    }
                    mGestureActiveButton = null;
                    mGestureActiveMaterialButton = null;
                    mGestureActiveView = null;
                    if (mSpecialButtonMode == ExtraKeysView.SpecialButtonMode.HOLD
                            && isSpecialButton(buttonInfo)) {
                        endSpecialButtonHold(buttonInfo);
                    }
                    return true;
                }

                if (mSpecialButtonMode == ExtraKeysView.SpecialButtonMode.HOLD
                        && isSpecialButton(buttonInfo)) {
                    endSpecialButtonHold(buttonInfo);
                    return true;
                }

                if (buttonInfo != null) {
                    float upDx = event.getX() - mTouchDownX;
                    float upDy = event.getY() - mTouchDownY;
                    ExtraKeysView.SwipeDirection upSwipe = detectDirection(upDx, upDy);
                    if (upSwipe != null) {
                        ExtraKeyButton swipeBtn = getSwipeExtraKeyButton(buttonInfo, upSwipe);
                        if (swipeBtn != null) {
                            mRuntimeSwipeDirection = upSwipe;
                            if (mSpecialButtonMode == ExtraKeysView.SpecialButtonMode.HOLD
                                    && isSpecialButton(buttonInfo)) {
                                endSpecialButtonHold(buttonInfo);
                            }
                            mHost.onAnyExtraKeyButtonClick(view, swipeBtn, button);
                            if (mHost.getClient() != null) {
                                mHost.getClient().onExtraKeyButtonGestureRelease(view, swipeBtn, button);
                            }
                            performHapticFeedback(view, swipeBtn, button, true);
                            return true;
                        }
                    }
                }

                if (mLongPressCount == 0) {
                    view.performClick();
                }
                return true;

            default:
                return true;
        }
    }

    private void startScheduledExecutors(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        stopScheduledExecutors();
        mLongPressCount = 0;
        if (mRepetitiveKeys != null && mRepetitiveKeys.contains(buttonInfo.getKey())) {
            if (mRepetitiveFuture != null) {
                mRepetitiveFuture.cancel(false);
                mRepetitiveFuture = null;
            }
            mRepetitiveFuture = mScheduledExecutor.scheduleWithFixedDelay(() -> {
                mLongPressCount++;
                if (mHost.getClient() != null) {
                    mHost.getClient().onExtraKeyButtonClick(view, buttonInfo, button);
                }
            }, mLongPressTimeout, mLongPressRepeatDelay, TimeUnit.MILLISECONDS);
        } else if (isSpecialButton(buttonInfo)) {
            SpecialButtonState state = mHost.getSpecialButtons().get(
                    SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null) return;
            if (mHandler == null)
                mHandler = new Handler(Looper.getMainLooper());
            mSpecialButtonsLongHoldRunnable = new SpecialButtonsLongHoldRunnable(state);
            mHandler.postDelayed(mSpecialButtonsLongHoldRunnable, mLongPressTimeout);
        }
    }

    private void stopScheduledExecutors() {
        if (mRepetitiveFuture != null) {
            mRepetitiveFuture.cancel(false);
            mRepetitiveFuture = null;
        }
        if (mSpecialButtonsLongHoldRunnable != null && mHandler != null) {
            mHandler.removeCallbacks(mSpecialButtonsLongHoldRunnable);
            mSpecialButtonsLongHoldRunnable = null;
        }
    }

    private void endSpecialButtonHold(ExtraKeyButton buttonInfo) {
        SpecialButtonState state = mHost.getSpecialButtons().get(
                SpecialButton.valueOf(buttonInfo.getKey()));
        if (state == null) return;
        if (state.isHolding) {
            state.setIsHolding(false);
            state.setIsActive(false);
        }
    }

    private boolean isSpecialButton(ExtraKeyButton button) {
        Set<String> keys = mHost.getSpecialButtonsKeys();
        return keys != null && keys.contains(button.getKey());
    }

    @Nullable
    private ExtraKeysView.SwipeDirection detectDirection(float dx, float dy) {
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        if (Math.max(absDx, absDy) < mSwipeThreshold) return null;
        if (absDx > absDy * 1.5f) {
            return dx > 0 ? ExtraKeysView.SwipeDirection.RIGHT : ExtraKeysView.SwipeDirection.LEFT;
        } else if (absDy > absDx * 1.5f) {
            return dy > 0 ? ExtraKeysView.SwipeDirection.DOWN : ExtraKeysView.SwipeDirection.UP;
        } else {
            return null;
        }
    }

    private void performHapticFeedback(View view, ExtraKeyButton buttonInfo,
                                        MaterialButton button, boolean isGesture) {
        int hapticMode = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_HAPTIC;
        TermuxAppSharedProperties props = TermuxAppSharedProperties.getProperties();
        if (props != null) {
            hapticMode = props.getExtraKeysHaptic();
        }
        if (hapticMode == TermuxPropertyConstants.IVALUE_EXTRA_KEYS_HAPTIC_OFF) return;
        if (!isGesture && hapticMode == TermuxPropertyConstants.IVALUE_EXTRA_KEYS_HAPTIC_GESTURES) return;

        if (mHost.getClient() != null) {
            if (mHost.getClient().performExtraKeyButtonHapticFeedback(view, buttonInfo, button))
                return;
        }

        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                if (Settings.Global.getInt(mContext.getContentResolver(),
                        "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }
        }
    }

    private void setButtonTint(MaterialButton button, ColorStateList tint) {
        if (button != null) {
            button.setBackgroundTintList(tint);
        }
    }

    // ── Inner class ──

    public class SpecialButtonsLongHoldRunnable implements Runnable {
        public final SpecialButtonState mState;

        public SpecialButtonsLongHoldRunnable(SpecialButtonState state) {
            mState = state;
        }

        public void run() {
            mState.setIsLocked(!mState.isActive);
            mState.setIsActive(!mState.isActive);
            mLongPressCount++;
        }
    }
}
