package com.termux.app.terminal.debug;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Minimal file-based logger for the session-name extra-keys auto-switch chain.
 *
 * The device logd pipeline may be unavailable (emulator/RAM corruption), so every decision
 * point also appends to {@code /sdcard/Download/ek_session_switch.log} (mirrors
 * {@code TermuxInstaller.DEBUG_LOG_PATH}). If scoped storage blocks that write, the entry
 * falls back to {@code <filesDir>/ek_session_switch.log}, which is exposed to debugging via
 * {@code adb root} / {@code run-as} without needing logcat at all.
 */
public final class SessionSwitchLog {

    private static int sReentrantGuard = 0;

    private SessionSwitchLog() {
    }

    public static void log(Context context, String message) {
        if (message == null) return;
        // Loop guard: any code path that writes while holding the file must not recurse.
        if (sReentrantGuard > 8) return;
        sReentrantGuard++;
        try {
            // Primary: public Downloads folder (mirrors TermuxInstaller.DEBUG_LOG_PATH).
            File primary = new File("/storage/emulated/0/Download/ek_session_switch.log");
            if (appendLine(primary, message)) return;
            // Fallback: app-private storage, readable via adb root / run-as.
            if (context != null) {
                appendLine(new File(context.getFilesDir(), "ek_session_switch.log"), message);
            }
        } catch (Exception ignored) {
            // Logging must never break the app.
        } finally {
            sReentrantGuard--;
        }
    }

    /** Append {@code message} to {@code file}; non-throwing, returns true on success. */
    private static boolean appendLine(File file, String message) {
        if (file == null) return false;
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8));
            pw.println(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + message);
            pw.flush();
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (pw != null) pw.close();
        }
    }
}