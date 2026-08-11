package com.termux.app.terminal.io;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;

import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    // ── Context-aware layout switching ──────────────────────────────
    /** Process name → extra-keys JSON string, from the "extra-keys-context" property. */
    private Map<String, String> mContextLayouts = new HashMap<>();
    /** The context name currently active (null = default layout). */
    @Nullable private String mCurrentContext;

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                    TermuxTerminalViewClient termuxTerminalViewClient,
                                    TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
        parseContextMap();
    }

    /**
     * Re-read the {@code extra-keys} and {@code extra-keys-style} properties from disk and rebuild
     * the {@link ExtraKeysInfo}. Call this after those properties change at runtime so the panel
     * updates without restarting the app.
     */
    public void reloadExtraKeys() {
        setExtraKeys();
        parseContextMap();
    }

    /**
     * Route extra-key input to the live active pager page rather than the cached
     * {@code mTerminalView}. The cached pointer is normally kept in sync by
     * {@link TermuxActivity#onTerminalPageSelected(int)}, but it can stay {@code null} in a window
     * where {@code onPageSelected} is not re-fired (e.g. the very first session being added to an
     * otherwise-empty pager: {@code setCurrentItem(0, false)} is a no-op and does not emit a page
     * change). Without this fallback every extra-keys press would hit the {@code null} guard and be
     * silently dropped — the "buttons don't send" symptom. Falling back to the activity's active
     * page resolves the correct, bound view instead.
     */
    @Override
    @Nullable
    protected TerminalView getTerminalViewForInput() {
        TerminalView active = mActivity.getActiveTerminalView();
        return active != null ? active : super.getTerminalViewForInput();
    }


    /**
     * Set the terminal extra keys and style.
     */
    private void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, mActivity.getString(com.termux.R.string.msg_extra_keys_load_failed, TermuxPropertyConstants.KEY_EXTRA_KEYS, e.toString()), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, mActivity.getString(com.termux.R.string.msg_extra_keys_create_failed), true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getActiveTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

    // ── Context-aware layout switching ──────────────────────────────

    /**
     * Parse the {@code extra-keys-context} property into a process-name → JSON layout map.
     * Called once at construction and again on {@link #reloadExtraKeys()}.
     *
     * <p>Expected property format (JSON object):</p>
     * <pre>
     * {
     *   "vim": "[ [ {key:'ESC', ...}, ... ] ]",
     *   "python": "[ [ {key:'Ctrl-C', ...}, ... ] ]"
     * }
     * </pre>
     */
    private void parseContextMap() {
        mContextLayouts.clear();
        try {
            Object raw = mActivity.getProperties().getInternalPropertyValue(
                    TermuxPropertyConstants.KEY_EXTRA_KEYS_CONTEXT, true);
            if (raw == null) return;

            JSONObject json = new JSONObject(raw.toString());
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String processName = keys.next();
                String layoutJson = json.getString(processName);
                if (processName != null && !processName.isEmpty() && layoutJson != null) {
                    mContextLayouts.put(processName.toLowerCase(), layoutJson);
                }
            }
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Failed to parse extra-keys-context property: ", e);
            mContextLayouts.clear();
        }
    }

    /**
     * Called by {@link com.termux.shared.termux.extrakeys.ExtraKeysContextWatcher} when the
     * foreground process of the active terminal session changes.
     *
     * @param processName the comm name of the new foreground process,
     *                    or {@code null} if the shell itself is foreground
     */
    public void onForegroundProcessChanged(@Nullable String processName) {
        if (!isContextSwitchingEnabled()) return;

        String contextName = resolveContextForProcess(processName);
        if (contextName == null) {
            applyDefaultLayout();
        } else if (!contextName.equals(mCurrentContext)) {
            applyContextLayout(contextName, processName);
        }
        // If contextName equals mCurrentContext, no-op (already showing this layout)
    }

    /**
     * Find the context key that matches the given process name.
     * Supports exact match first, then prefix match (e.g. "python3" → "python").
     *
     * @return the context key, or {@code null} if no match
     */
    @Nullable
    private String resolveContextForProcess(@Nullable String processName) {
        if (processName == null || mContextLayouts.isEmpty()) return null;

        String lower = processName.toLowerCase();

        // Exact match
        if (mContextLayouts.containsKey(lower)) return lower;

        // Prefix match: "python3" → "python", "vi" → "vim" (longest prefix wins)
        String bestMatch = null;
        for (String key : mContextLayouts.keySet()) {
            if (lower.startsWith(key) || key.startsWith(lower)) {
                if (bestMatch == null || key.length() > bestMatch.length()) {
                    bestMatch = key;
                }
            }
        }
        return bestMatch;
    }

    /**
     * Load and apply an ExtraKeys layout for the given context name.
     */
    private void applyContextLayout(@NonNull String contextName, @NonNull String processName) {
        String contextLayoutJson = mContextLayouts.get(contextName);
        if (contextLayoutJson == null) {
            Logger.logError(LOG_TAG, "Context layout not found for: " + contextName);
            return;
        }

        try {
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(
                    TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap displayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            ExtraKeysInfo previousInfo = mExtraKeysInfo;
            ExtraKeysInfo newInfo = new ExtraKeysInfo(contextLayoutJson, extraKeysStyle,
                    ExtraKeysConstants.CONTROL_CHARS_ALIASES);

            // Avoid redundant reload if the matrix is structurally identical.
            if (!ExtraKeysInfo.isSameLayout(mExtraKeysInfo, newInfo)) {
                mExtraKeysInfo = newInfo;
                // Update the toolbar height for the new layout row count.
                mActivity.setTerminalToolbarHeight();
                if (mActivity.getExtraKeysView() != null && mExtraKeysInfo != null) {
                    mActivity.getExtraKeysView().reload(newInfo,
                            mActivity.getTerminalToolbarDefaultHeight());
                }
                Logger.logDebug(LOG_TAG, "Switched extra-keys to context \""
                        + contextName + "\" for process: " + processName);
            }
            mCurrentContext = contextName;
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG,
                    "Failed to create ExtraKeysInfo for context \"" + contextName + "\": ", e);
        }
    }

    /**
     * Restore the default extra-keys layout (re-reads the {@code extra-keys} property).
     */
    private void applyDefaultLayout() {
        if (mCurrentContext == null) return; // Already at default

        ExtraKeysInfo previousInfo = mExtraKeysInfo;
        setExtraKeys(); // Re-reads the "extra-keys" property from disk

        // Avoid redundant reload if the layout hasn't actually changed.
        if (!ExtraKeysInfo.isSameLayout(previousInfo, mExtraKeysInfo)) {
            mActivity.setTerminalToolbarHeight();
            if (mActivity.getExtraKeysView() != null && mExtraKeysInfo != null) {
                mActivity.getExtraKeysView().reload(mExtraKeysInfo,
                        mActivity.getTerminalToolbarDefaultHeight());
            }
            Logger.logDebug(LOG_TAG, "Restored default extra-keys layout");
        }
        mCurrentContext = null;
    }

    /** @return {@code true} if context-aware switching is configured and non-empty. */
    public boolean isContextSwitchingEnabled() {
        return !mContextLayouts.isEmpty();
    }

    /** @return unmodifiable view of the process→layout map (for testing/debugging). */
    @NonNull
    public Map<String, String> getContextLayouts() {
        return mContextLayouts;
    }

    /** @return the currently active context name, or {@code null} for the default layout. */
    @Nullable
    public String getCurrentContext() {
        return mCurrentContext;
    }
}
