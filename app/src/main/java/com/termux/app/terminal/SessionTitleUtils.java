package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

public final class SessionTitleUtils {
    private SessionTitleUtils() {}

    /** Returns the display name of a session by the rule: name if non-empty, else title, else the default resource. */
    public static String resolveDisplayName(@NonNull Context context,
            @Nullable String sessionName, @Nullable String title) {
        if (sessionName != null && !sessionName.isEmpty()) return sessionName;
        if (title != null && !title.isEmpty()) return title;
        return context.getString(R.string.session_default_title);
    }
}
