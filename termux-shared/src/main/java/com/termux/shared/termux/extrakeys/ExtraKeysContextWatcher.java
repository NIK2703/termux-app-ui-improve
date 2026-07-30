package com.termux.shared.termux.extrakeys;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors the foreground process of a {@link TerminalSession} by polling
 * /proc/​&lt;shellPid&gt;/stat and triggers a callback when the foreground
 * process changes (e.g. user runs vim, python, htop, etc.).
 *
 * <p>All file I/O runs on a background {@link ScheduledExecutorService}.
 * The {@link OnContextChangeListener} callback is always dispatched on the
 * main thread via a {@link Handler}.</p>
 *
 * <p>Lifecycle: call {@link #start()} when the activity is resumed and
 * {@link #stop()} when paused. Call {@link #destroy()} when the activity
 * is destroyed.</p>
 */
public final class ExtraKeysContextWatcher {

    private static final String LOG_TAG = "ExtraKeysContextWatcher";

    /** Default polling interval in milliseconds. */
    public static final long DEFAULT_POLL_INTERVAL_MS = 800;

    /** Minimum allowed polling interval to prevent excessive CPU usage. */
    public static final long MIN_POLL_INTERVAL_MS = 200;

    /** Index of tpgid (foreground process group) in /proc/​&lt;pid&gt;/stat. */
    private static final int STAT_FIELD_TPGID = 8;

    private final AtomicReference<TerminalSession> mSessionRef;
    private final OnContextChangeListener mListener;
    private final long mPollIntervalMs;
    private final ScheduledExecutorService mExecutor;
    private final Handler mMainHandler;

    private volatile ScheduledFuture<?> mPollFuture;
    private volatile int mLastForegroundPid;
    private volatile String mLastProcessName;

    /**
     * Callback interface for foreground process changes.
     */
    public interface OnContextChangeListener {
        /**
         * Called on the main thread when the foreground process changes.
         *
         * @param processName the comm name of the new foreground process,
         *                    or {@code null} if the shell itself is foreground
         */
        void onForegroundProcessChanged(@Nullable String processName);
    }

    /**
     * @param session       the active terminal session to monitor
     * @param listener      callback invoked on main thread when context changes
     * @param pollIntervalMs polling interval; clamped to [MIN_POLL_INTERVAL_MS, ∞)
     */
    public ExtraKeysContextWatcher(
            @NonNull TerminalSession session,
            @NonNull OnContextChangeListener listener,
            long pollIntervalMs) {
        mSessionRef = new AtomicReference<>(session);
        mListener = listener;
        mPollIntervalMs = Math.max(pollIntervalMs, MIN_POLL_INTERVAL_MS);
        mExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ExtraKeysCtxWatcher");
            t.setDaemon(true);
            return t;
        });
        mMainHandler = new Handler(Looper.getMainLooper());
        mLastForegroundPid = 0;
        mLastProcessName = null;
    }

    /** Convenience constructor with default interval. */
    public ExtraKeysContextWatcher(
            @NonNull TerminalSession session,
            @NonNull OnContextChangeListener listener) {
        this(session, listener, DEFAULT_POLL_INTERVAL_MS);
    }

    /** Start polling. Safe to call multiple times. */
    public synchronized void start() {
        if (mPollFuture != null && !mPollFuture.isDone()) return;
        mLastForegroundPid = 0;
        mLastProcessName = null;
        mPollFuture = mExecutor.scheduleWithFixedDelay(
                this::pollForegroundProcess,
                mPollIntervalMs, mPollIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** Stop polling. Safe to call if not running. */
    public synchronized void stop() {
        if (mPollFuture != null) {
            mPollFuture.cancel(false);
            mPollFuture = null;
        }
    }

    /** @return {@code true} if polling is currently active */
    public boolean isRunning() {
        ScheduledFuture<?> f = mPollFuture;
        return f != null && !f.isDone();
    }

    /**
     * Switch monitoring to a different session (e.g. tab switch).
     * Resets cached state and triggers an immediate re-check.
     */
    public void setTerminalSession(@NonNull TerminalSession session) {
        mSessionRef.set(session);
        mLastForegroundPid = 0;
        mLastProcessName = null;
        requestImmediateCheck();
    }

    /** Trigger an immediate foreground process check (on background thread). */
    public void requestImmediateCheck() {
        if (!isRunning()) return;
        mExecutor.execute(this::pollForegroundProcess);
    }

    /** Must be called when the activity is destroyed to release resources. */
    public void destroy() {
        stop();
        mExecutor.shutdownNow();
    }

    // ── Core polling logic ──────────────────────────────────────────

    private void pollForegroundProcess() {
        TerminalSession session = mSessionRef.get();
        if (session == null) return;

        int shellPid = session.getPid();
        if (shellPid <= 0) return;

        int foregroundPid = resolveForegroundPid(shellPid);
        if (foregroundPid <= 0 || foregroundPid == shellPid) {
            // Shell is foreground — no subprocess running
            if (mLastForegroundPid != 0 || mLastProcessName != null) {
                mLastForegroundPid = 0;
                mLastProcessName = null;
                dispatchContextChanged(null);
            }
            return;
        }

        if (foregroundPid == mLastForegroundPid) return;

        String processName = readProcessName(foregroundPid);
        mLastForegroundPid = foregroundPid;
        mLastProcessName = processName;
        dispatchContextChanged(processName);
    }

    /**
     * Read /proc/​&lt;pid&gt;/stat and extract the tpgid field (8th field).
     * tpgid is the foreground process group of the controlling terminal.
     */
    static int resolveForegroundPid(int pid) {
        String stat = readProcFile("/proc/" + pid + "/stat");
        if (stat == null) return -1;
        return parseTpgidFromStat(stat);
    }

    /**
     * Read /proc/​&lt;pid&gt;/comm to get the process name (max 16 chars).
     */
    static String readProcessName(int pid) {
        String comm = readProcFile("/proc/" + pid + "/comm");
        if (comm != null) {
            comm = comm.trim();
            if (!comm.isEmpty()) return comm;
        }
        // Fallback: try cmdline
        String cmdline = readProcFile("/proc/" + pid + "/cmdline");
        if (cmdline != null) {
            int nul = cmdline.indexOf('\0');
            if (nul > 0) cmdline = cmdline.substring(0, nul);
            cmdline = cmdline.trim();
            if (!cmdline.isEmpty()) {
                // Return basename only
                int slash = cmdline.lastIndexOf('/');
                return slash >= 0 ? cmdline.substring(slash + 1) : cmdline;
            }
        }
        return null;
    }

    /** Read a proc file; returns null on any I/O error. */
    private static String readProcFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path), 256)) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Parse the tpgid (field 8) from /proc/​&lt;pid&gt;/stat.
     * The format is: pid (comm) state ppid pgrp session tty_nr tpgid ...
     * Field 8 (tpgid) is at the 7th space after the closing paren.
     */
    static int parseTpgidFromStat(String stat) {
        // Skip past the comm field which is inside parens
        int closeParen = stat.lastIndexOf(')');
        if (closeParen < 0) return -1;
        // After ") S" (state), count 6 more fields to reach tpgid (field 8)
        int pos = closeParen + 2; // skip ") "
        for (int i = 0; i < 6; i++) {
            int space = stat.indexOf(' ', pos);
            if (space < 0) return -1;
            pos = space + 1;
        }
        int space = stat.indexOf(' ', pos);
        String field = (space > 0) ? stat.substring(pos, space) : stat.substring(pos);
        try {
            return Integer.parseInt(field.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void dispatchContextChanged(@Nullable final String processName) {
        mMainHandler.post(() -> mListener.onForegroundProcessChanged(processName));
    }
}
