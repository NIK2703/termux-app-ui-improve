package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.system.Os;
import android.util.Pair;
import android.view.WindowManager;

import com.termux.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.installer.AbiUtils;
import com.termux.installer.BootstrapManifest;
import com.termux.installer.Sha256;
import com.termux.installer.TarGzExtractor;
import com.termux.installer.TermuxBootstrapState;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

public final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";

    private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 50000;
    private static final long MAX_SINGLE_ENTRY_SIZE = 256L * 1024 * 1024;
    private static final int MAX_COMPRESSION_RATIO = 200;

    private static final AtomicBoolean sInstallInProgress = new AtomicBoolean(false);

    public static boolean isBootstrapInstalled() {
        File prefix = TERMUX_PREFIX_DIR;
        if (!prefix.isDirectory()) return false;
        File binDir = new File(prefix, "bin");
        if (!binDir.isDirectory()) return false;
        String[] children = binDir.list();
        return children != null && children.length > 0;
    }

    public static boolean isPrefixValid() {
        return isBootstrapInstalled();
    }

    public static void cleanupInterruptedInstall() {
        File filesDir = TERMUX_PREFIX_DIR.getParentFile();
        if (filesDir == null || !filesDir.isDirectory()) return;
        File[] candidates = filesDir.listFiles((d, name) ->
            name.startsWith("usr.tmp-") || name.startsWith("usr.old-"));
        if (candidates == null) return;
        for (File f : candidates) {
            Logger.logInfo(LOG_TAG, "Cleaning up stale: " + f.getName());
            deleteRecursive(f);
        }
    }

    public interface InstallProgressListener {
        void onProgress(String stage, int percent);
    }

    public interface RestoreListener {
        void onProgress(String message);
        void onCompleted();
        void onFailed(String message);
    }

    public static void installBootstrapFromZipFile(Context context, File zipFile,
            InstallProgressListener listener) throws IOException, BootstrapException {
        if (!sInstallInProgress.compareAndSet(false, true)) {
            throw new BootstrapException("Bootstrap install already in progress");
        }
        try {
            installBootstrapFromZipFileLocked(context, zipFile, listener);
        } finally {
            sInstallInProgress.set(false);
        }
    }

    private static void installBootstrapFromZipFileLocked(Context context, File zipFile,
            InstallProgressListener listener) throws IOException, BootstrapException {
        if (!zipFile.isFile()) {
            throw new IOException(context.getString(com.termux.R.string.error_bootstrap_zip_not_found, zipFile.getAbsolutePath()));
        }

        if (listener != null) listener.onProgress(context.getString(com.termux.R.string.bootstrap_install_progress_verify_sha), 0);

        String sha256 = Sha256.hexOfFile(zipFile);
        Logger.logInfo(LOG_TAG, "Zip SHA-256: " + sha256);

        if (listener != null) listener.onProgress(context.getString(com.termux.R.string.bootstrap_install_progress_verify_manifest), 5);

        BootstrapManifest manifest = BootstrapManifest.fromZip(zipFile);
        AbiUtils.validateBootstrapArch(manifest.arch);

        File filesDir = context.getFilesDir();
        String tmpName = "usr.tmp-" + UUID.randomUUID().toString();
        File tempDir = new File(filesDir, tmpName);
        File backupDir = null;

        try {
            if (!tempDir.mkdirs()) {
                throw new IOException(context.getString(com.termux.R.string.error_bootstrap_create_temp_dir, tempDir));
            }

            if (listener != null) listener.onProgress(context.getString(com.termux.R.string.bootstrap_install_progress_extract), 10);

            extractZipFile(context, zipFile, tempDir, listener);

            if (listener != null) listener.onProgress(context.getString(com.termux.R.string.bootstrap_install_progress_finalize), 90);

            File prefixDir = TERMUX_PREFIX_DIR;
            if (prefixDir.exists()) {
                String backupName = "usr.old-" + UUID.randomUUID().toString();
                backupDir = new File(filesDir, backupName);
                if (!prefixDir.renameTo(backupDir)) {
                    throw new IOException(context.getString(com.termux.R.string.error_bootstrap_rename_prefix));
                }
            }

            if (!tempDir.renameTo(prefixDir)) {
                if (backupDir != null && backupDir.exists()) {
                    backupDir.renameTo(prefixDir);
                }
                throw new IOException(context.getString(com.termux.R.string.error_bootstrap_rename_temp));
            }

            if (backupDir != null) {
                deleteRecursive(backupDir);
            }

            Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");

            if (listener != null) listener.onProgress(context.getString(com.termux.R.string.bootstrap_install_progress_write_variant), 95);

            try {
                TermuxBootstrapState.writeVariantMarker(context, manifest.variant);
                TermuxBootstrapState.setInstalledVariant(context, manifest.variant);
            } catch (IOException e) {
                Logger.logError(LOG_TAG, "Failed to write variant marker: " + e.getMessage());
            }

            if (listener != null) listener.onProgress(context.getString(com.termux.R.string.bootstrap_install_progress_done), 100);

        } catch (Exception e) {
            deleteRecursive(tempDir);
            if (backupDir != null) deleteRecursive(backupDir);
            throw e;
        }
    }

    private static void extractZipFile(Context context, File zipFile, File destDir, InstallProgressListener listener)
            throws IOException, BootstrapException {
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipFile);
            int totalEntries = zip.size();
            int doneEntries = 0;
            long totalUncompressed = 0;
            List<Pair<String, String>> symlinks = new ArrayList<>();
            Set<String> seenNames = new HashSet<>();

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.isEmpty() || name.contains("..") || name.startsWith("/")) {
                    throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_invalid_zip_entry, name));
                }

                if (!seenNames.add(name)) {
                    throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_duplicate_zip_entry, name));
                }

                if (entry.isDirectory()) {
                    if (name.equals("SYMLINKS.txt") || name.equals("BOOTSTRAP_INFO")) {
                        throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_directory_metadata_entry, name));
                    }
                    File dir = safeChildFile(context, destDir, name);
                    if (!dir.isDirectory() && !dir.mkdirs()) {
                        throw new IOException(context.getString(com.termux.R.string.error_bootstrap_failed_mkdir, dir));
                    }
                    applyDirectoryMode(dir, entry);
                    doneEntries++;
                    continue;
                }

                if (entry.getSize() > MAX_SINGLE_ENTRY_SIZE) {
                    throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_entry_too_large, name, entry.getSize()));
                }

                long compressedSize = entry.getCompressedSize();
                long uncompressedSize = entry.getSize();
                if (uncompressedSize > 0 && compressedSize > 0
                        && uncompressedSize / compressedSize > MAX_COMPRESSION_RATIO) {
                    throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_compression_ratio, name));
                }

                totalUncompressed += uncompressedSize;
                if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                    throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_total_size_exceeded));
                }

                if (doneEntries > MAX_ENTRIES) {
                    throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_too_many_entries));
                }

                int unixMode = getUnixModeReflective(entry);
                int fileType = unixMode & android.system.OsConstants.S_IFMT;
                if (fileType == android.system.OsConstants.S_IFLNK) {
                    InputStream in = zip.getInputStream(entry);
                    String target;
                    try {
                        byte[] buf = new byte[4096];
                        ByteArrayOutputStream bout = new ByteArrayOutputStream();
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            bout.write(buf, 0, n);
                        }
                        target = new String(bout.toByteArray(), "UTF-8").trim();
                    } finally {
                        in.close();
                    }
                    String linkPath = destDir.getAbsolutePath() + "/" + name;
                    File linkFile = new File(linkPath);
                    File parent = linkFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException(context.getString(com.termux.R.string.error_bootstrap_symlink_parent_dir, parent));
                    }
                    validateSymlinkTarget(context, destDir, name, target);
                    try {
                        Os.symlink(target, linkPath);
                    } catch (Exception e) {
                        throw new IOException(context.getString(com.termux.R.string.error_bootstrap_symlink_create, name, target), e);
                    }
                    doneEntries++;
                    continue;
                }

                if (name.equals("SYMLINKS.txt")) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] parts = line.split("←");
                        if (parts.length != 2)
                            throw new BootstrapException(context.getString(com.termux.R.string.error_bootstrap_symlink_line, line));
                        String target = parts[0];
                        String linkPath = destDir.getAbsolutePath() + "/" + parts[1];
                        File linkFile = new File(linkPath);
                        File parent = linkFile.getParentFile();
                        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                            throw new IOException(context.getString(com.termux.R.string.error_bootstrap_symlink_parent_dir, parent));
                        }
                        validateSymlinkTarget(context, destDir, parts[1], target);
                        symlinks.add(Pair.create(target, linkPath));
                    }
                    doneEntries++;
                    continue;
                }

                if (name.equals("BOOTSTRAP_INFO")) {
                    doneEntries++;
                    continue;
                }

                if (unixMode != 0) {
                    if (fileType != 0 && fileType != android.system.OsConstants.S_IFREG) {
                        throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_unsupported_file_type, name));
                    }
                }

                File outFile = safeChildFile(context, destDir, name);
                File parent = outFile.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException(context.getString(com.termux.R.string.error_bootstrap_failed_mkdir, parent));
                }
                copyZipEntryToFile(zip, entry, outFile);
                applyPermissions(outFile, entry, name);

                doneEntries++;
                if (listener != null && totalEntries > 0) {
                    listener.onProgress(context.getString(com.termux.R.string.bootstrap_install_progress_extract), 10 + (int) (80L * doneEntries / totalEntries));
                }
            }

            for (Pair<String, String> symlink : symlinks) {
                try {
                    Os.symlink(symlink.first, symlink.second);
                } catch (Exception e) {
                    throw new IOException(context.getString(com.termux.R.string.error_bootstrap_symlink_create, symlink.first, symlink.second), e);
                }
            }
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void validateSymlinkTarget(Context context, File extractRoot, String linkName, String target)
            throws IOException {
        if (target.isEmpty()) {
            throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_symlink_target_empty, linkName));
        }
        if (target.startsWith("/")) {
            throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_symlink_absolute, target));
        }
        File linkFile = new File(extractRoot, linkName);
        File parent = linkFile.getParentFile();
        if (parent == null) {
            throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_symlink_no_parent, linkName));
        }
        File resolved = new File(parent, target);
        String rootPath = extractRoot.getCanonicalPath() + File.separator;
        String resolvedPath = resolved.getCanonicalPath();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new SecurityException(context.getString(com.termux.R.string.error_bootstrap_symlink_escapes_root, linkName, target));
        }
    }

    private static File safeChildFile(Context context, File base, String name) throws IOException {
        File child = new File(base, name);
        String basePath = base.getCanonicalPath() + File.separator;
        String childPath = child.getCanonicalPath();
        if (!childPath.startsWith(basePath)) {
            throw new IOException(context.getString(com.termux.R.string.error_bootstrap_path_traversal, name));
        }
        return child;
    }

    private static void copyZipEntryToFile(ZipFile zip, ZipEntry entry, File outFile) throws IOException {
        try (InputStream in = new BufferedInputStream(zip.getInputStream(entry));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            out.flush();
        }
    }

    private static void applyPermissions(File file, ZipEntry entry, String name) {
        int mode = getUnixModeReflective(entry) & 0777;
        mode &= ~(android.system.OsConstants.S_ISUID | android.system.OsConstants.S_ISGID);
        if (mode == 0) {
            mode = (name.startsWith("bin/") || name.startsWith("libexec/")
                || name.startsWith("lib/apt/apt-helper") || name.startsWith("lib/apt/methods/"))
                ? 0755 : 0644;
        }
        try {
            Os.chmod(file.getAbsolutePath(), mode);
        } catch (Exception ignored) {}
    }

    private static void applyDirectoryMode(File dir, ZipEntry entry) {
        int mode = getUnixModeReflective(entry) & 0777;
        if (mode == 0) mode = 0755;
        try {
            Os.chmod(dir.getAbsolutePath(), mode);
        } catch (Exception ignored) {}
    }

    private static int getUnixModeReflective(ZipEntry entry) {
        try {
            return (int) ZipEntry.class.getMethod("getUnixMode").invoke(entry);
        } catch (Exception e) {
            return 0;
        }
    }

    public static class BootstrapException extends Exception {
        public BootstrapException(String message) { super(message); }
        public BootstrapException(String message, Throwable cause) { super(message, cause); }
    }

    public static void installFromTarGz(Context context, Uri tarGzUri, RestoreListener listener) {
        Handler handler = new Handler(Looper.getMainLooper());
        RestoreListener uiListener = wrapOnMainThread(listener, handler);
        new Thread(() -> installFromTarGzInternal(context, tarGzUri, uiListener),
            "termux-tar-restore").start();
    }

    private static RestoreListener wrapOnMainThread(RestoreListener l, Handler h) {
        return new RestoreListener() {
            @Override public void onProgress(String msg) { h.post(() -> l.onProgress(msg)); }
            @Override public void onCompleted() { h.post(l::onCompleted); }
            @Override public void onFailed(String msg) { h.post(() -> l.onFailed(msg)); }
        };
    }

    private static void installFromTarGzInternal(Context context, Uri uri, RestoreListener listener) {
        if (!sInstallInProgress.compareAndSet(false, true)) {
            listener.onFailed(context.getString(com.termux.R.string.bootstrap_restore_error_in_progress));
            return;
        }
        File tempDir = null;
        File oldDir = null;
        boolean success = false;
        try {
            listener.onProgress(context.getString(com.termux.R.string.bootstrap_restore_progress_starting));
            File prefixDir = TERMUX_PREFIX_DIR;
            File filesDir = prefixDir.getParentFile();
            if (filesDir == null) filesDir = context.getFilesDir();
            if (!filesDir.isDirectory() && !filesDir.mkdirs())
                throw new IOException(context.getString(com.termux.R.string.error_bootstrap_create_files_dir));

            cleanupInterruptedInstall();
            if (isBootstrapInstalled())
                throw new IOException(context.getString(com.termux.R.string.error_bootstrap_already_installed));

            String uuid = UUID.randomUUID().toString();
            tempDir = new File(filesDir, "usr.tmp-" + uuid);
            oldDir = new File(filesDir, "usr.old-" + uuid);
            deleteRecursive(tempDir);
            deleteRecursive(oldDir);
            if (!tempDir.mkdirs()) throw new IOException(context.getString(com.termux.R.string.error_bootstrap_create_temp_dir2));

            listener.onProgress(context.getString(com.termux.R.string.bootstrap_restore_progress_extracting));

            long[] lastUi = {0};
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException(context.getString(com.termux.R.string.error_bootstrap_open_backup_uri));
                TarGzExtractor.extract(in, tempDir, prefixDir,
                    (name, eb, tb, ec) -> {
                        long now = SystemClock.uptimeMillis();
                        if (now - lastUi[0] > 200) {
                            lastUi[0] = now;
                            int mbytes = (int)(tb / (1024 * 1024));
                            listener.onProgress(context.getString(
                                com.termux.R.string.backup_restore_progress_restoring, ec, mbytes));
                        }
                    });
            }

            listener.onProgress(context.getString(com.termux.R.string.bootstrap_restore_progress_validating));
            File binDir = new File(tempDir, "bin");
            if (!binDir.isDirectory()) throw new IOException(context.getString(com.termux.R.string.error_bootstrap_no_bin_dir));
            String[] children = binDir.list();
            if (children == null || children.length == 0)
                throw new IOException(context.getString(com.termux.R.string.error_bootstrap_bin_empty));

            String variant = Build.VERSION.SDK_INT >= 26 ? "apt-android-7" : "apt-android-5";

            listener.onProgress(context.getString(com.termux.R.string.bootstrap_restore_progress_writing_variant));
            try { TermuxBootstrapState.writeVariantMarker(context, variant); }
            catch (IOException e) { Logger.logError(LOG_TAG, "Failed to write marker: " + e.getMessage()); }
            TermuxBootstrapState.setInstalledVariant(context, variant);

            if (prefixDir.exists()) {
                if (!prefixDir.renameTo(oldDir))
                    throw new IOException(context.getString(com.termux.R.string.error_bootstrap_rename_prefix2));
            }
            if (!tempDir.renameTo(prefixDir)) {
                if (oldDir.exists()) oldDir.renameTo(prefixDir);
                throw new IOException(context.getString(com.termux.R.string.error_bootstrap_rename_temp2));
            }
            success = true;
            listener.onProgress(context.getString(com.termux.R.string.bootstrap_restore_progress_complete));
            listener.onCompleted();

            if (oldDir.exists()) {
                File old = oldDir;
                new Thread(() -> deleteRecursive(old), "termux-old-delete").start();
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "tar.gz restore failed: " + e.getMessage());
            if (!success && oldDir != null && oldDir.exists() && TERMUX_PREFIX_DIR != null
                    && !TERMUX_PREFIX_DIR.exists()) {
                try { oldDir.renameTo(TERMUX_PREFIX_DIR); } catch (Throwable ignored) {}
            }
            listener.onFailed(context.getString(com.termux.R.string.bootstrap_restore_error_failed) + ": " + e.getMessage());
        } finally {
            if (!success) deleteRecursive(tempDir);
            sInstallInProgress.set(false);
        }
    }

    public static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        String bootstrapErrorMessage;
        Error filesDirectoryAccessibleError;

        filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
        boolean isFilesDirectoryAccessible = filesDirectoryAccessibleError == null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PackageUtils.isCurrentUserThePrimaryUser(activity)) {
            bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_not_primary_user_message,
                MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            Logger.logError(LOG_TAG, "isFilesDirectoryAccessible: " + isFilesDirectoryAccessible);
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.exitAppWithErrorMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage);
            return;
        }

        if (!isFilesDirectoryAccessible) {
            bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError);
            if (PackageUtils.isAppInstalledOnExternalStorage(activity) &&
                !TermuxConstants.TERMUX_FILES_DIR_PATH.equals(activity.getFilesDir().getAbsolutePath().replaceAll("^/data/user/0/", "/data/data/"))) {
                bootstrapErrorMessage += "\n\n" + activity.getString(R.string.bootstrap_error_installed_on_portable_sd,
                    MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            }
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.showMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage, null);
            return;
        }

        if (isBootstrapInstalled()) {
            whenDone.run();
            return;
        }

        final ProgressDialog progress = ProgressDialog.show(activity, null,
            activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread() {
            @Override
            public void run() {
                try {
                    Logger.logInfo(LOG_TAG, "Installing " + TermuxConstants.TERMUX_APP_NAME + " bootstrap packages.");

                    Error error;

                    error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true);
                    if (error != null) { showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error)); return; }

                    error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    if (error != null) { showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error)); return; }

                    error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
                    if (error != null) { showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error)); return; }

                    error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
                    if (error != null) { showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error)); return; }

                    Logger.logInfo(LOG_TAG, "Extracting bootstrap zip to prefix staging directory \"" + TERMUX_STAGING_PREFIX_DIR_PATH + "\".");

                    final byte[] buffer = new byte[8096];
                    final List<Pair<String, String>> symlinks = new ArrayList<>(50);
                    final byte[] zipBytes = loadZipBytes(activity);
                    try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                        ZipEntry zipEntry;
                        while ((zipEntry = zipInput.getNextEntry()) != null) {
                            if (zipEntry.getName().equals("SYMLINKS.txt")) {
                                BufferedReader r = new BufferedReader(new InputStreamReader(zipInput));
                                String line;
                                while ((line = r.readLine()) != null) {
                                    String[] parts = line.split("←");
                                    if (parts.length != 2) throw new RuntimeException(activity.getString(com.termux.R.string.error_bootstrap_symlink_line, line));
                                    String oldPath = parts[0];
                                    String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
                                    symlinks.add(Pair.create(oldPath, newPath));
                                    error = ensureDirectoryExists(new File(newPath).getParentFile());
                                    if (error != null) { showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error)); return; }
                                }
                            } else {
                                String zipEntryName = zipEntry.getName();
                                File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);
                                boolean isDirectory = zipEntry.isDirectory();
                                error = ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());
                                if (error != null) { showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error)); return; }
                                if (!isDirectory) {
                                    try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                        int readBytes;
                                        while ((readBytes = zipInput.read(buffer)) != -1)
                                            outStream.write(buffer, 0, readBytes);
                                    }
                                    if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") ||
                                        zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods")) {
                                        Os.chmod(targetFile.getAbsolutePath(), 0700);
                                    }
                                }
                            }
                        }
                    }

                    if (symlinks.isEmpty()) throw new RuntimeException(activity.getString(com.termux.R.string.error_bootstrap_no_symlinks));
                    for (Pair<String, String> symlink : symlinks) {
                        Os.symlink(symlink.first, symlink.second);
                    }

                    Logger.logInfo(LOG_TAG, "Moving termux prefix staging to prefix directory.");
                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException(activity.getString(com.termux.R.string.error_bootstrap_move_prefix));
                    }
                    Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");
                    TermuxShellEnvironment.writeEnvironmentToFile(activity);
                    activity.runOnUiThread(whenDone);
                } catch (final Exception e) {
                    showBootstrapErrorDialog(activity, whenDone,
                        Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));
                } finally {
                    activity.runOnUiThread(() -> {
                        try { progress.dismiss(); } catch (RuntimeException ignored) {}
                    });
                }
            }
        }.start();
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Logger.logErrorExtended(LOG_TAG, "Bootstrap Error:\n" + message);
        sendBootstrapCrashReportNotification(activity, message);
        activity.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(activity)
                    .setTitle(R.string.bootstrap_error_title)
                    .setMessage(R.string.bootstrap_error_body)
                    .setNegativeButton(R.string.bootstrap_error_abort, (d, which) -> { d.dismiss(); activity.finish(); })
                    .setPositiveButton(R.string.bootstrap_error_try_again, (d, which) -> {
                        d.dismiss();
                        FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                        TermuxInstaller.setupBootstrapIfNeeded(activity, whenDone);
                    }).create().show();
            } catch (WindowManager.BadTokenException ignored) {}
        });
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        TermuxCrashUtils.sendCrashReportNotification(activity, LOG_TAG,
            activity.getString(com.termux.R.string.notification_bootstrap_crash_title), null,
            activity.getString(com.termux.R.string.notification_bootstrap_crash_body, message),
            true, false, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES, true);
    }

    static void setupStorageSymlinks(final Context context) {
        final String tag = "termux-storage";
        new Thread() {
            public void run() {
                try {
                    Error error;
                    File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;
                    error = FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                    if (error != null) {
                        Logger.logErrorAndShowToast(context, tag, error.getMessage());
                        return;
                    }
                    File sharedDir = Environment.getExternalStorageDirectory();
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());
                    Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());
                    Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());
                    Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());
                    Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());
                    Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());
                    Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());
                    Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS).getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Os.symlink(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS).getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());
                    }
                    File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null) {
                        for (int i = 0; i < dirs.length; i++) {
                            if (dirs[i] == null) continue;
                            Os.symlink(dirs[i].getAbsolutePath(), new File(storageDir, "external-" + i).getAbsolutePath());
                        }
                    }
                    dirs = context.getExternalMediaDirs();
                    if (dirs != null) {
                        for (int i = 0; i < dirs.length; i++) {
                            if (dirs[i] == null) continue;
                            Os.symlink(dirs[i].getAbsolutePath(), new File(storageDir, "media-" + i).getAbsolutePath());
                        }
                    }
                    Logger.logInfo(tag, "Storage symlinks created successfully.");
                } catch (Exception e) {
                    Logger.logErrorAndShowToast(context, tag, e.getMessage());
                }
            }
        }.start();
    }

    private static Error ensureDirectoryExists(File directory) {
        return FileUtils.createDirectoryFile(directory.getAbsolutePath());
    }

    private static boolean sNativeAvailable;
    static {
        try {
            System.loadLibrary("termux-bootstrap");
            sNativeAvailable = true;
        } catch (Throwable t) {
            sNativeAvailable = false;
        }
    }

    private static byte[] loadZipBytes(Context context) {
        if (!sNativeAvailable) {
            throw new RuntimeException(context.getString(com.termux.R.string.error_bootstrap_embedded_lib));
        }
        return getZip();
    }

    private static native byte[] getZip();

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        f.delete();
    }
}
