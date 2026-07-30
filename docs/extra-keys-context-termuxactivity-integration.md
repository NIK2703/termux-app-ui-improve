# TermuxActivity Integration — ExtraKeys Context Watcher

This document specifies the exact code changes needed to wire the
`ExtraKeysContextWatcher` into `TermuxActivity`.

## Change 1: Add import

```java
// Near the existing imports (around line 88-89)
import com.termux.shared.termux.extrakeys.ExtraKeysContextWatcher;
import org.json.JSONObject;  // may already exist
```

## Change 2: Add field

```java
// After line 230: TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;
/**
 * Watches the foreground process of the active terminal session and
 * triggers automatic ExtraKeys layout switching.  Only non-null when
 * the {@code extra-keys-context} property is configured.
 */
@Nullable
private ExtraKeysContextWatcher mExtraKeysContextWatcher;
```

## Change 3: Create watcher in setTerminalToolbarView()

```java
// At the end of setTerminalToolbarView(), AFTER line ~1017
// (after extraKeysView.setButtonColors(...) — the last line of the method body)

// ── ExtraKeys context watcher ──
// Only created if the user configured extra-keys-context in termux.properties.
// Reads the context map and starts polling the foreground process so that
// the ExtraKeys layout switches automatically when a new program is launched.
if (mTermuxTerminalExtraKeys.isContextSwitchingEnabled()) {
    long pollInterval = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_CONTEXT_POLL_INTERVAL;
    Object intervalRaw = mProperties.getInternalPropertyValue(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_CONTEXT_POLL_INTERVAL, true);
    if (intervalRaw != null) {
        try {
            pollInterval = Long.parseLong(intervalRaw.toString().trim());
        } catch (NumberFormatException ignored) {}
    }
    mExtraKeysContextWatcher = new ExtraKeysContextWatcher(
            getCurrentSession(),
            (contextName, processName) -> {
                if (mTermuxTerminalExtraKeys != null) {
                    mTermuxTerminalExtraKeys.onForegroundProcessChanged(processName);
                }
            },
            pollInterval);
    mExtraKeysContextWatcher.start();
}
```

## Change 4: Lifecycle — onResume / onPause

```java
// In onResume(), AFTER line 689: mIsOnResumeAfterOnCreate = false;
if (mExtraKeysContextWatcher != null && !mExtraKeysContextWatcher.isRunning()) {
    mExtraKeysContextWatcher.setTerminalSession(getCurrentSession());
    mExtraKeysContextWatcher.start();
}
```

```java
// In onPause(), AFTER line 704: mIsPaused = true;
if (mExtraKeysContextWatcher != null) {
    mExtraKeysContextWatcher.stop();
}
```

## Change 5: Tab switch — re-point watcher at new session

```java
// In SessionPagerManager.onTerminalPageSelected(), AFTER line 525:
// mActivity.setTerminalView(pageView);
//
// Re-point the context watcher at the newly-selected session so the
// foreground process is monitored for the active tab, not the old one.
ExtraKeysContextWatcher contextWatcher = mActivity.getExtraKeysContextWatcher();
if (contextWatcher != null) {
    contextWatcher.setTerminalSession(selected);
}
```

## Change 6: Property reload — re-parse context map

```java
// Wherever reloadExtraKeys() is called (e.g. termux-reload-settings),
// also re-parse the context map and re-create the watcher if needed:
mTermuxTerminalExtraKeys.reloadExtraKeys();

// Re-evaluate the context watcher:
if (mTermuxTerminalExtraKeys.isContextSwitchingEnabled()) {
    if (mExtraKeysContextWatcher == null) {
        // Watcher was not previously configured — create it now.
        long pollInterval = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_CONTEXT_POLL_INTERVAL;
        Object intervalRaw = mProperties.getInternalPropertyValue(
                TermuxPropertyConstants.KEY_EXTRA_KEYS_CONTEXT_POLL_INTERVAL, true);
        if (intervalRaw != null) {
            try { pollInterval = Long.parseLong(intervalRaw.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        mExtraKeysContextWatcher = new ExtraKeysContextWatcher(
                getCurrentSession(),
                (contextName, processName) -> {
                    if (mTermuxTerminalExtraKeys != null) {
                        mTermuxTerminalExtraKeys.onForegroundProcessChanged(processName);
                    }
                },
                pollInterval);
        mExtraKeysContextWatcher.start();
    } else {
        // Watcher already running — just re-point at the current session
        // and force a re-check (the context map may have changed).
        mExtraKeysContextWatcher.setTerminalSession(getCurrentSession());
        mExtraKeysContextWatcher.requestImmediateCheck();
    }
} else {
    // Context switching removed — destroy the watcher.
    if (mExtraKeysContextWatcher != null) {
        mExtraKeysContextWatcher.destroy();
        mExtraKeysContextWatcher = null;
    }
}
```

## Change 7: onDestroy — clean up

```java
// In onDestroy(), after existing cleanup:
if (mExtraKeysContextWatcher != null) {
    mExtraKeysContextWatcher.destroy();
    mExtraKeysContextWatcher = null;
}
```

## Change 8: Getter for SessionPagerManager access

```java
// Add a public getter (near getTerminalView() around line 2363):
@Nullable
public ExtraKeysContextWatcher getExtraKeysContextWatcher() {
    return mExtraKeysContextWatcher;
}
```

## Summary of integration points

| Method | What happens |
|--------|-------------|
| `setTerminalToolbarView()` | Creates watcher if context is configured |
| `onResume()` | Starts/re-points watcher |
| `onPause()` | Stops watcher |
| `onDestroy()` | Destroys watcher |
| `SessionPagerManager.onTerminalPageSelected()` | Re-points watcher at new session |
| `reloadExtraKeys()` path | Re-parses context map, re-creates or destroys watcher |
