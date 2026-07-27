package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.installer.TermuxBootstrapState;
import com.termux.shared.errors.Error;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class TermuxSettingsBackupUtils {

    private static final String LOG_TAG = "TermuxSettingsBackupUtils";

    public static final String BACKUP_FORMAT_VERSION = "1";
    public static final Error CANCELLED_ERROR = new Error("__SETTINGS_CANCELLED__");

    public static final String MIME_TYPE_ZIP = "application/zip";
    public static final String DEFAULT_FILENAME_PREFIX = "termux-settings-backup-";

    public interface ResultListener {
        void onResult(Error error);
    }

    public interface ProgressCallback {
        void onProgress(String stage);
    }

    private static final String[] EXCLUDED_APP_KEYS = {
        "current_session",
        "last_notification_id",
        "app_shell_number_since_boot",
        "terminal_session_number_since_boot"
    };

    private static final String PREFERENCES_APP = "app";
    private static final String PREFERENCES_UI = "ui";
    private static final String PREFERENCES_BOOTSTRAP = "bootstrap";

    private TermuxSettingsBackupUtils() {}

    /** Backup all app settings to a ZIP OutputStream. */
    public static void exportSettings(Context context, OutputStream out,
                                      ResultListener listener,
                                      ProgressCallback progress,
                                      AtomicBoolean cancelled) {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out))) {
            if (isCancelled(cancelled)) { listener.onResult(CANCELLED_ERROR); return; }
            report(progress, "manifest");
            writeManifest(zos);

            if (isCancelled(cancelled)) { listener.onResult(CANCELLED_ERROR); return; }
            report(progress, "preferences");
            writePrefsEntry(zos, PREFERENCES_APP,
                context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
                EXCLUDED_APP_KEYS);
            writePrefsEntry(zos, PREFERENCES_UI,
                context.getSharedPreferences("termux_prefs", Context.MODE_PRIVATE),
                null);
            writePrefsEntry(zos, PREFERENCES_BOOTSTRAP,
                context.getSharedPreferences("termux_bootstrap_state", Context.MODE_PRIVATE),
                null);

            if (isCancelled(cancelled)) { listener.onResult(CANCELLED_ERROR); return; }
            report(progress, "config files");
            writeFileEntry(zos, "config/colors.properties", TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE_PATH);
            writeFileEntry(zos, "config/colors.light.properties", TermuxConstants.TERMUX_COLOR_LIGHT_PROPERTIES_FILE_PATH);
            writeFileEntry(zos, "config/colors.dark.properties", TermuxConstants.TERMUX_COLOR_DARK_PROPERTIES_FILE_PATH);
            writeFileEntry(zos, "config/font.ttf", TermuxConstants.TERMUX_FONT_FILE_PATH);
            writeFileEntry(zos, "config/termux.float.properties", TermuxConstants.TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH);

            if (isCancelled(cancelled)) { listener.onResult(CANCELLED_ERROR); return; }
            report(progress, "bootstrap marker");
            writeFileEntry(zos, "marker/bootstrap_variant",
                TermuxConstants.TERMUX_CONFIG_PREFIX_DIR_PATH + "/bootstrap_variant");

            listener.onResult(null);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Settings export failed", e);
            listener.onResult(new Error(e.getMessage(), e));
        }
    }

    /** Restore all app settings from a ZIP InputStream. */
    public static void importSettings(Context context, InputStream in,
                                      ResultListener listener,
                                      ProgressCallback progress,
                                      AtomicBoolean cancelled) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry entry;
            boolean manifestSeen = false;

            while ((entry = zis.getNextEntry()) != null) {
                if (isCancelled(cancelled)) { listener.onResult(CANCELLED_ERROR); return; }
                String name = entry.getName();
                report(progress, name);

                if (name.equals("manifest.properties")) {
                    Properties manifest = new Properties();
                    manifest.load(zis);
                    String version = manifest.getProperty("format.version");
                    if (!BACKUP_FORMAT_VERSION.equals(version)) {
                        listener.onResult(new Error(
                            context.getString(com.termux.R.string.error_settings_backup_format_version, version)));
                        return;
                    }
                    manifestSeen = true;
                } else if (name.startsWith("preferences/")) {
                    String basename = name.substring("preferences/".length());
                    String prefName = basename.endsWith(".properties")
                        ? basename.substring(0, basename.length() - ".properties".length())
                        : basename;
                    SharedPreferences prefs = prefsForName(context, prefName);
                    if (prefs != null) {
                        readPrefsEntry(prefs, zis);
                    }
                } else if (name.startsWith("config/")) {
                    String fileName = name.substring("config/".length());
                    String destPath = configDestPath(fileName);
                    if (destPath != null) {
                        writeFileFromStream(destPath, zis);
                    }
                } else if (name.startsWith("marker/")) {
                    String fileName = name.substring("marker/".length());
                    if ("bootstrap_variant".equals(fileName)) {
                        String variant = readLine(zis);
                        if (variant != null && !variant.isEmpty()) {
                            TermuxBootstrapState.writeVariantMarker(context, variant);
                            TermuxBootstrapState.setInstalledVariant(context, variant);
                        }
                    }
                }
                zis.closeEntry();
            }

            if (!manifestSeen) {
                listener.onResult(new Error(
                    context.getString(com.termux.R.string.error_settings_backup_missing_manifest)));
                return;
            }
            listener.onResult(null);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Settings import failed", e);
            listener.onResult(new Error(e.getMessage(), e));
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static void writeManifest(ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry("manifest.properties"));
        try {
            Properties p = new Properties();
            p.setProperty("format.version", BACKUP_FORMAT_VERSION);
            p.store(zos, null);
        } finally {
            zos.closeEntry();
        }
    }

    private static void writePrefsEntry(ZipOutputStream zos, String name,
                                        SharedPreferences prefs, String[] excludeKeys) throws IOException {
        String entryName = "preferences/" + name + ".properties";
        zos.putNextEntry(new ZipEntry(entryName));
        try {
            Properties props = new Properties();
            Map<String, ?> all = prefs.getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String key = e.getKey();
                if (excludeKeys != null && contains(excludeKeys, key)) continue;
                Object val = e.getValue();
                if (val == null) continue;
                String suffix = typeSuffix(val);
                props.setProperty(key + ":" + suffix, valueToString(val));
            }
            props.store(zos, null);
        } finally {
            zos.closeEntry();
        }
    }

    private static void readPrefsEntry(SharedPreferences prefs, InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(in);
        SharedPreferences.Editor editor = prefs.edit();
        for (String fullKey : props.stringPropertyNames()) {
            int colonIdx = fullKey.lastIndexOf(':');
            if (colonIdx < 0) continue;
            String key = fullKey.substring(0, colonIdx);
            String type = fullKey.substring(colonIdx + 1);
            String value = props.getProperty(fullKey);
            putTypedValue(editor, key, type, value);
        }
        editor.commit();
    }

    @SuppressWarnings("unchecked")
    private static void putTypedValue(SharedPreferences.Editor editor, String key, String type, String value) {
        try {
            switch (type) {
                case "bool":
                    editor.putBoolean(key, Boolean.parseBoolean(value));
                    break;
                case "int":
                    editor.putInt(key, Integer.parseInt(value));
                    break;
                case "long":
                    editor.putLong(key, Long.parseLong(value));
                    break;
                case "float":
                    editor.putFloat(key, Float.parseFloat(value));
                    break;
                case "string":
                    editor.putString(key, value);
                    break;
                case "stringset":
                    Set<String> set = new LinkedHashSet<>();
                    StringBuilder buf = new StringBuilder();
                    boolean escaped = false;
                    for (int i = 0; i < value.length(); i++) {
                        char c = value.charAt(i);
                        if (escaped) {
                            buf.append(c);
                            escaped = false;
                        } else if (c == '\\') {
                            escaped = true;
                        } else if (c == ',') {
                            set.add(buf.toString());
                            buf.setLength(0);
                        } else {
                            buf.append(c);
                        }
                    }
                    set.add(buf.toString());
                    editor.putStringSet(key, set);
                    break;
            }
        } catch (Exception ex) {
            Logger.logError(LOG_TAG, "Failed to restore key '" + key + "' (type=" + type + "): " + ex.getMessage());
        }
    }

    private static String typeSuffix(Object val) {
        if (val instanceof Boolean) return "bool";
        if (val instanceof Integer) return "int";
        if (val instanceof Long) return "long";
        if (val instanceof Float) return "float";
        if (val instanceof String) return "string";
        if (val instanceof Set) return "stringset";
        return "string";
    }

    @SuppressWarnings("unchecked")
    private static String valueToString(Object val) {
        if (val instanceof Set) {
            Set<String> set = (Set<String>) val;
            StringBuilder sb = new StringBuilder();
            for (String s : set) {
                if (sb.length() > 0) sb.append(',');
                sb.append(s.replace("\\", "\\\\").replace(",", "\\,"));
            }
            return sb.toString();
        }
        return String.valueOf(val);
    }

    private static void writeFileEntry(ZipOutputStream zos, String entryName, String filePath) throws IOException {
        File f = new File(filePath);
        if (!f.isFile()) return;
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) >= 0) {
                zos.write(buf, 0, n);
            }
        } finally {
            zos.closeEntry();
        }
    }

    private static void writeFileFromStream(String destPath, InputStream in) throws IOException {
        File f = new File(destPath);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent);
        }
        try (FileOutputStream fos = new FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') baos.write(b);
        }
        String s = baos.toString("UTF-8").trim();
        return s.isEmpty() ? null : s;
    }

    private static SharedPreferences prefsForName(Context context, String name) {
        switch (name) {
            case PREFERENCES_APP:
                return context.getSharedPreferences(
                    TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE);
            case PREFERENCES_UI:
                return context.getSharedPreferences("termux_prefs", Context.MODE_PRIVATE);
            case PREFERENCES_BOOTSTRAP:
                return context.getSharedPreferences("termux_bootstrap_state", Context.MODE_PRIVATE);
            default:
                return null;
        }
    }

    private static String configDestPath(String fileName) {
        switch (fileName) {
            case "colors.properties":
                return TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE_PATH;
            case "colors.light.properties":
                return TermuxConstants.TERMUX_COLOR_LIGHT_PROPERTIES_FILE_PATH;
            case "colors.dark.properties":
                return TermuxConstants.TERMUX_COLOR_DARK_PROPERTIES_FILE_PATH;
            case "font.ttf":
                return TermuxConstants.TERMUX_FONT_FILE_PATH;
            case "termux.float.properties":
                return TermuxConstants.TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH;
            default:
                return null;
        }
    }

    private static boolean isCancelled(AtomicBoolean cancelled) {
        return cancelled != null && cancelled.get();
    }

    private static void report(ProgressCallback cb, String stage) {
        if (cb != null) cb.onProgress(stage);
    }

    private static boolean contains(String[] arr, String key) {
        for (String s : arr) if (s.equals(key)) return true;
        return false;
    }
}
