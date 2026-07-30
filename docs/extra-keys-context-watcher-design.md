# ExtraKeys Context Watcher — Design Document

## Problem

When the user runs different programs in Termux (e.g. `vim`, `python`, `htop`, a shell prompt), they need different ExtraKeys layouts tailored to each program. Currently, ExtraKeys is a single, static layout defined in `termux.properties` via `extra-keys`. Switching layouts requires manual editing and `reload`.

## Solution Overview

Introduce `ExtraKeysContextWatcher` — a lightweight polling mechanism that monitors the foreground process of the active terminal session and switches the ExtraKeys layout automatically when a configured context match is detected.

## Core Classes

### 1. `ExtraKeysContextWatcher` (termux-shared)

```java
package com.termux.shared.termux.extrakeys;

/**
 * Polls the foreground process of a TerminalSession and triggers a callback
 * when the process name changes to a configured context.
 *
 * All I/O runs on a background ScheduledExecutorService — no blocking on UI thread.
 * Callbacks are delivered on the main thread via Handler.
 */
public class ExtraKeysContextWatcher {

    public interface OnContextChangeListener {
        /**
         * Called on the main thread when the foreground process context changes.
         *
         * @param contextName  The matched context key from extra-keys-context map,
         *                     or null if the current process does not match any context.
         * @param processName  The raw /proc/<pid>/comm value (e.g. "vim", "bash").
         */
        void onContextChanged(@Nullable String contextName, @NonNull String processName);
    }

    /**
     * @param session        The TerminalSession whose foreground process to monitor.
     * @param listener       Callback invoked on the main thread on context change.
     * @param pollIntervalMs Polling interval in ms (default 500-1000).
     */
    public ExtraKeysContextWatcher(@NonNull TerminalSession session,
                                   @NonNull OnContextChangeListener listener,
                                   long pollIntervalMs);

    /** Start polling. Idempotent: if already running, does nothing. */
    public void start();

    /** Stop polling. Idempotent. */
    public void stop();

    /** @return true if the watcher is currently polling. */
    public boolean isRunning();

    /**
     * Update the TerminalSession being watched. Called when the user switches tabs
     * (the active session changes). Triggers an immediate re-check.
     */
    public void setTerminalSession(@Nullable TerminalSession session);

    /**
     * Force an immediate foreground-process check. Posts result to the listener.
     * Useful after tab switch or session create to avoid waiting for the next poll.
     */
    public void requestImmediateCheck();

    // ----- Foreground process detection -----

    /**
     * Resolve the foreground PID from a shell PID.
     *
     * Reads /proc/<shellPid>/stat, extracts field 8 (tpgid).
     * If tpgid > 0 and differs from shellPid, the foreground PID is tpgid.
     * Otherwise the shell itself is at the prompt.
     *
     * @return The foreground PID, or -1 if unreadable.
     */
    private static int resolveForegroundPid(int shellPid);

    /**
     * Read /proc/<pid>/comm to get the process name (trimmed to 15 chars by kernel).
     *
     * @return The process name, or null if unreadable.
     */
    @Nullable
    private static String readProcessName(int pid);

    /**
     * Check if this process name matches any key in the extra-keys-context map.
     * Uses simple substring matching: "vim" matches "vim", "nvim" does NOT match "vim".
     * Also supports glob-style if we add it later.
     *
     * @return The matching context key, or null if no match.
     */
    @Nullable
    private static String matchContext(@NonNull String processName,
                                       @NonNull Map<String, String> contextMap);
}
```

### 2. Foreground Process Detection Algorithm

The watcher uses standard POSIX terminal semantics:

```
/proc/<shellPid>/stat
  field 1  — pid
  field 2  — comm (process name)
  field 8  — tpgid (controlling terminal's foreground process group)

Algorithm:
  1. Read /proc/<shellPid>/stat
  2. Parse field 8 (tpgid)
  3. if tpgid <= 0 → shell is idle at prompt
  4. if tpgid == shellPid → shell is at prompt
  5. if tpgid != shellPid → a subprocess (tpgid) is the foreground process
  6. Read /proc/<tpgid>/comm to get the process name
```

This is the same mechanism used by `ps`, `top`, and every POSIX `tcgetpgrp()` call.

**Performance**: Each poll is 2 small file reads from procfs (~200 bytes total). The kernel serializes directly from `task_struct`. At 500ms intervals this is negligible (<0.1ms CPU per poll). Running on a background thread means zero impact on UI thread jank.

### 3. `extra-keys-context` Property

New property in `termux.properties`:

```properties
# JSON map of process-name → extra-keys JSON layout
# When the foreground process matches a key, that layout is loaded.
# If the process doesn't match any key, the default extra-keys is used.
extra-keys-context={"vim":"[['ESC','/',{key:':',popup:'!'},'i','o','a','u'],['TAB','CTRL','SHIFT','LEFT','DOWN','UP','RIGHT']]","python":"[['ESC','TAB',{key:'(',popup:')'},'%','#','\'','\"'],['CTRL','ALT','SHIFT','LEFT','DOWN','UP','RIGHT']]"}

# Polling interval in ms (default: 800)
extra-keys-context-poll-interval=800
```

### 4. Comparison with Existing Matrix (Avoid Redundant Reloads)

```java
/**
 * Compare two ExtraKeysInfo matrices to determine if a reload is needed.
 * ExtraKeysView.reload() is expensive (removeAllViews + addView per button),
 * so we skip it when the target layout equals the current one.
 */
public static boolean isSameLayout(@Nullable ExtraKeysInfo current,
                                   @Nullable ExtraKeysInfo candidate) {
    if (current == candidate) return true;
    if (current == null || candidate == null) return false;
    ExtraKeyButton[][] a = current.getMatrix();
    ExtraKeyButton[][] b = candidate.getMatrix();
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
        if (!Arrays.equals(a[i], b[i])) return false;
    }
    return true;
}
```

### 5. Integration Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        TermuxActivity                           │
│                                                                 │
│  ┌────────────────────┐      ┌──────────────────────────────┐   │
│  │  onResume()         │── ──▶│  mExtraKeysContextWatcher    │   │
│  │                     │      │   .start()                   │   │
│  │  onPause()          │── ──▶│   .stop()                    │   │
│  │                     │      │                              │   │
│  │  onTerminalPage     │      │  ┌──────────────────┐        │   │
│  │  Selected(pos)      │── ──▶│  │ Background       │        │   │
│  │                     │      │  │ ScheduledExecutor│        │   │
│  │                     │      │  │ (single thread)  │        │   │
│  └────────────────────┘      │  └────────┬─────────┘        │   │
│                               │           │ every N ms        │   │
│  ┌────────────────────┐      │           ▼                  │   │
│  │ TermuxTerminal      │      │  read /proc/pid/stat         │   │
│  │ ExtraKeys           │      │  read /proc/tpgid/comm       │   │
│  │                     │      │  match against context map   │   │
│  │  .reloadExtraKeys   │◀────│  if changed → post callback  │   │
│  │  ForContext(name)   │      └──────────────────────────────┘   │
│  └────────────────────┘                                         │
│         │                                                        │
│         ▼                                                        │
│  ┌────────────────────┐                                         │
│  │ ExtraKeysView      │                                         │
│  │  .reload(info, h)  │  (only if layout actually changed)     │
│  └────────────────────┘                                         │
└─────────────────────────────────────────────────────────────────┘
```

### 6. Where to Place the Watcher

**Decision: Manage in `TermuxActivity`, core logic in `ExtraKeysContextWatcher`.**

| Approach | Pros | Cons |
|----------|------|------|
| **TermuxActivity** (chosen) | Natural lifecycle host; has access to TerminalSession, ExtraKeysView, properties | Adds responsibility to already-large class |
| **TermuxTerminalExtraKeys** | Already handles extra-keys logic | No lifecycle awareness; no access to onPause/onResume |
| **Separate component** | Clean separation | Needs bridge to Activity lifecycle anyway |

The watcher instance is created in `TermuxActivity.setTerminalToolbarView()` and its lifecycle is managed by the activity's `onResume()`/`onPause()`.

### 7. Lifecycle Management

```java
// In TermuxActivity:

private ExtraKeysContextWatcher mExtraKeysContextWatcher;

@Override
protected void onResume() {
    super.onResume();
    // ... existing code ...
    if (mExtraKeysContextWatcher != null && !mExtraKeysContextWatcher.isRunning()) {
        mExtraKeysContextWatcher.start();
    }
}

@Override
protected void onPause() {
    super.onPause();
    // ... existing code ...
    if (mExtraKeysContextWatcher != null) {
        mExtraKeysContextWatcher.stop();
    }
}
```

### 8. Tab Switching Integration

When a tab switch happens via `SessionPagerManager.onTerminalPageSelected()`, the watcher must be re-pointed at the new session:

```java
// In SessionPagerManager.onTerminalPageSelected() or TermuxActivity:
mExtraKeysContextWatcher.setTerminalSession(selected);
mExtraKeysContextWatcher.requestImmediateCheck(); // non-blocking
```

This is triggered from `SessionPagerManager.onTerminalPageSelected()` which already runs per-page bookkeeping. We hook into the same place where `mActivity.setTerminalView(pageView)` is called.

### 9. Performance Assessment

**Polling `/proc` every 500ms**: ✅ Acceptable on Android UI thread **only if** the I/O runs on a background thread (which it does — `ScheduledExecutorService`).

- File read of `/proc/<pid>/stat`: ~40 bytes, kernel copies from `task_struct` directly → ~0.01ms
- File read of `/proc/<pid>/comm`: ~20 bytes → <0.01ms
- JSON map lookup: O(n) where n = number of defined contexts, typically <10
- **Total CPU per poll**: < 0.1ms on the executor thread
- **Memory per poll**: zero allocations (string parsing reuses existing paths)

At 500ms intervals, that's 120 polls/minute with negligible overhead. The background executor ensures zero impact on UI thread frame timing.

### 10. Lazy Loading

The watcher is only instantiated when `extra-keys-context` is non-null and non-empty:

```java
// In TermuxActivity.setTerminalToolbarView():
String contextJson = (String) mProperties.getPropertyValue(
    TermuxPropertyConstants.KEY_EXTRA_KEYS_CONTEXT, true);
if (contextJson != null && !contextJson.isEmpty() && !"{}".equals(contextJson)) {
    long pollInterval = getContextPollInterval(); // from properties or default 800
    mExtraKeysContextWatcher = new ExtraKeysContextWatcher(
        getCurrentSession(),
        this::onExtraKeysContextChanged,
        pollInterval);
}
```

### 11. Key Code Flow

```
1. User edits termux.properties, adds:
   extra-keys-context={"vim":"[[...]]","python":"[[...]]"}

2. Termux restarts or user runs termux-reload-settings

3. TermuxActivity.setTerminalToolbarView() reads property
   → If non-empty, creates ExtraKeysContextWatcher
   → Watcher calls requestImmediateCheck()

4. Watcher background thread reads:
   /proc/<shellPid>/stat → tpgid=12345
   /proc/12345/comm  → "vim"

5. Matches "vim" in context map
   → Fetches the "vim" extra-keys JSON
   → Compares with current ExtraKeysInfo matrix
   → If different: creates new ExtraKeysInfo, calls
     TermuxTerminalExtraKeys.onContextChanged("vim", "vim")
     → TermuxTerminalExtraKeys.reloadExtraKeysForContext("vim")
        → reloadExtraKeys() (re-reads properties)
        → If context matches, override extra-keys with context layout
        → mExtraKeysView.reload(newInfo, heightPx)

6. User exits vim → shell at prompt
   → /proc/<shellPid>/stat → tpgid == shellPid
   → Process name "bash"
   → Does not match any context key
   → Callback with contextName=null
   → Restores default extra-keys layout

7. User runs python → foreground process "python3"
   → Matches "python" in context map
   → Switches to python layout
```

### 12. Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `termux-shared/.../extrakeys/ExtraKeysContextWatcher.java` | CREATE | Core polling watcher |
| `termux-shared/.../extrakeys/ExtraKeysInfo.java` | MODIFY | Add `isSameLayout()` static helper |
| `termux-shared/.../properties/TermuxPropertyConstants.java` | MODIFY | Add `KEY_EXTRA_KEYS_CONTEXT`, `KEY_EXTRA_KEYS_CONTEXT_POLL_INTERVAL` |
| `app/.../TermuxTerminalExtraKeys.java` | MODIFY | Add `reloadExtraKeysForContext()`, `onContextChanged()` |
| `app/.../TermuxActivity.java` | MODIFY | Create/start/stop watcher, lifecycle, tab-switch hook |
