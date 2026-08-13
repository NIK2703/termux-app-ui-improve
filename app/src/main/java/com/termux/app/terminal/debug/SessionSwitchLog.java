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
 * point also appends to {@code <filesDir>/ek_session_switch.log}. The file is exposed to
 * debugging via {@code adb root} / {@code run-as} without needing logcat at all.
 */
public final class SessionSwitchLog {

    private static int sReentrantGuard = 0;

    private SessionSwitchLog() {
    }

    public static void log(Context context, String message) {
        if (context == null || message == null) return;
        // Loop guard: any code path that writes while holding the file must not recurse.
        if (sReentrantGuard > 8) return;
        sReentrantGuard++;
        PrintWriter pw = null;
        try {
            File file = new File(context.getFilesDir(), "ek_session_switch.log");
            pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8));
            pw.println(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + message);
            pw.flush();
        } catch (Exception ignored) {
            // Logging must never break the app.
        } finally {
            if (pw != null) pw.close();
            sReentrantGuard--;
        }
    }
}