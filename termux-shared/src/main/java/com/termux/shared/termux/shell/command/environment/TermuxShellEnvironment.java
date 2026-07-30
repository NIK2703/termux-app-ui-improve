package com.termux.shared.termux.shell.command.environment;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.environment.AndroidShellEnvironment;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.ShellCommandShellEnvironment;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.TermuxShellUtils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * Environment for Termux.
 */
public class TermuxShellEnvironment extends AndroidShellEnvironment {

    private static final String LOG_TAG = "TermuxShellEnvironment";

    /** Environment variable for the termux {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH}. */
    public static final String ENV_PREFIX = "PREFIX";

    /** Cached runtime home dir path, resolved at init(). Falls back to compile-time constant. */
    private static String sResolvedHomeDirPath;

    /** Cached runtime bin dir path, resolved at init(). Falls back to compile-time constant. */
    private static String sResolvedBinDirPath;

    public TermuxShellEnvironment() {
        super();
        shellCommandShellEnvironment = new TermuxShellCommandShellEnvironment();
    }


    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void init(@NonNull Context currentPackageContext) {
        // Cache runtime home dir for use by getDefaultWorkingDirectoryPath() which has no Context.
        String filesDir = currentPackageContext.getFilesDir().getAbsolutePath();
        sResolvedHomeDirPath = filesDir + "/home";
        sResolvedBinDirPath = filesDir + "/usr/bin";
        TermuxAppShellEnvironment.setTermuxAppEnvironment(currentPackageContext);
    }

    /**
     * Resolve the actual $PREFIX path at runtime.
     * Prefers the cached sResolvedHomeDirPath (set from Application context in
     * {@link #init(Context)}) over context.getFilesDir(), because the passed context
     * may point to another package (e.g. com.termux) on debug/fork builds.
     * Falls back to the compile-time constant only when neither source helps.
     */
    static String resolvePrefixDirPath(@NonNull Context context) {
        String defaultPrefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        String filesDir;

        if (sResolvedHomeDirPath != null) {
            // Derive from the cached runtime home dir (set in init() from Application context).
            // sResolvedHomeDirPath = filesDir + "/home", so strip 5 chars for the files dir.
            if (sResolvedHomeDirPath.endsWith("/home")) {
                filesDir = sResolvedHomeDirPath.substring(0, sResolvedHomeDirPath.length() - 5);
                String runtimePrefix = filesDir + "/usr";
                if (!runtimePrefix.equals(defaultPrefix)) {
                    Logger.logDebug("TermuxShellEnvironment: runtime PREFIX=" + runtimePrefix + " (from sResolvedHomeDirPath, compile-time=" + defaultPrefix + ")");
                    return runtimePrefix;
                }
            }
        }

        // Fall back to the passed context (may or may not differ from compile-time).
        String runtimePrefix = context.getFilesDir().getAbsolutePath() + "/usr";
        if (!runtimePrefix.equals(defaultPrefix)) {
            Logger.logDebug("TermuxShellEnvironment: runtime PREFIX=" + runtimePrefix + " (from context, compile-time=" + defaultPrefix + ")");
            return runtimePrefix;
        }
        return defaultPrefix;
    }

    static String resolveEnvFilePath(@NonNull Context context) {
        return resolvePrefixDirPath(context) + "/etc/termux/termux.env";
    }

    static String resolveEnvTempFilePath(@NonNull Context context) {
        return resolvePrefixDirPath(context) + "/etc/termux/termux.env.tmp";
    }

    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void writeEnvironmentToFile(@NonNull Context currentPackageContext) {
        HashMap<String, String> environmentMap = new TermuxShellEnvironment().getEnvironment(currentPackageContext, false);
        String environmentString = ShellEnvironmentUtils.convertEnvironmentToDotEnvFile(environmentMap);

        // Write environment string to temp file and then move to final location since otherwise
        // writing may happen while file is being sourced/read
        String envTempPath = resolveEnvTempFilePath(currentPackageContext);
        String envFilePath = resolveEnvFilePath(currentPackageContext);
        Error error = FileUtils.writeTextToFile("termux.env.tmp", envTempPath,
            Charset.defaultCharset(), environmentString, false);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
            return;
        }

        error = FileUtils.moveRegularFile("termux.env.tmp", envTempPath, envFilePath, true);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
        }
    }

    /** Get shell environment for Termux. */
    @NonNull
    @Override
    public HashMap<String, String> getEnvironment(@NonNull Context currentPackageContext, boolean isFailSafe) {

        // Termux environment builds upon the Android environment
        HashMap<String, String> environment = super.getEnvironment(currentPackageContext, isFailSafe);

        HashMap<String, String> termuxAppEnvironment = TermuxAppShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxAppEnvironment != null)
            environment.putAll(termuxAppEnvironment);

        HashMap<String, String> termuxApiAppEnvironment = TermuxAPIShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxApiAppEnvironment != null)
            environment.putAll(termuxApiAppEnvironment);

        String prefixDirPath = resolvePrefixDirPath(currentPackageContext);
        String filesDir = prefixDirPath.substring(0, prefixDirPath.length() - 4); // strip "/usr"
        String homeDirPath = filesDir + "/home";
        String tmpDirPath = prefixDirPath + "/tmp";
        String binDirPath = prefixDirPath + "/bin";
        String libDirPath = prefixDirPath + "/lib";

        environment.put(ENV_HOME, homeDirPath);
        environment.put(ENV_PREFIX, prefixDirPath);

        // If failsafe is not enabled, then we keep default PATH and TMPDIR so that system binaries can be used
        if (!isFailSafe) {
            environment.put(ENV_TMPDIR, tmpDirPath);
            boolean needsSubstitution = !TermuxConstants.TERMUX_BOOTSTRAP_TARGET_PACKAGE_NAME.equals(currentPackageContext.getPackageName());
            if (TermuxBootstrap.isAppPackageVariantAPTAndroid5()) {
                // Termux in android 5/6 era shipped busybox binaries in applets directory
                environment.put(ENV_PATH, binDirPath + ":" + binDirPath + "/applets");
                environment.put(ENV_LD_LIBRARY_PATH, libDirPath);
            } else if (needsSubstitution) {
                // Package name differs from bootstrap target (e.g. com.termux.debug).
                // ELF RPATHs are hardcoded to the bootstrap prefix and cannot be byte-patched
                // when the old/new paths differ in length. Set LD_LIBRARY_PATH as fallback.
                environment.put(ENV_PATH, binDirPath);
                environment.put(ENV_LD_LIBRARY_PATH, libDirPath);
            } else {
                // Termux binaries on Android 7+ rely on DT_RUNPATH, so LD_LIBRARY_PATH should be unset by default
                environment.put(ENV_PATH, binDirPath);
                environment.remove(ENV_LD_LIBRARY_PATH);
            }
        }

        return environment;
    }


    /** Expose for debug logging. */
    public static String getResolvedHomeDirPath() {
        return sResolvedHomeDirPath;
    }

    /**
     * Sanitize a working directory path: if it points under the compile-time
     * Termux data dir (/data/data/com.termux/), remap it to the runtime package
     * data dir.  Also falls back to the runtime home dir when the path is null,
     * empty, or does not exist on disk.
     *
     * @param context     The calling package context (must be the actual app, not
     *                    a created package context for com.termux).
     * @param workingDir  The working directory to sanitize.
     * @return A safe absolute path under the runtime package files dir.
     */
    @NonNull
    public static String sanitizeWorkingDirectory(@NonNull Context context,
                                                  @Nullable String workingDir) {
        String runtimeFilesDir = context.getFilesDir().getAbsolutePath();
        String runtimeHome = runtimeFilesDir + "/home";
        String compileTimeFiles = TermuxConstants.TERMUX_FILES_DIR_PATH; // /data/data/com.termux/files
        String compileTimeUserFiles = "/data/user/0/" + TermuxConstants.TERMUX_PACKAGE_NAME + "/files";

        if (workingDir == null || workingDir.trim().isEmpty()) {
            Logger.logInfo(LOG_TAG, "sanitizeWorkingDirectory: null/empty -> " + runtimeHome);
            return runtimeHome;
        }

        String wd = new File(workingDir).getAbsolutePath();

        // Replace /data/data/com.termux/files/... with runtime files dir
        if (wd.startsWith(compileTimeFiles + "/") || wd.equals(compileTimeFiles)) {
            wd = runtimeFilesDir + wd.substring(compileTimeFiles.length());
            Logger.logInfo(LOG_TAG, "sanitizeWorkingDirectory: remapped /data/data/ path -> " + wd);
        }

        // Replace /data/user/0/com.termux/files/... with runtime files dir
        if (wd.startsWith(compileTimeUserFiles + "/") || wd.equals(compileTimeUserFiles)) {
            wd = runtimeFilesDir + wd.substring(compileTimeUserFiles.length());
            Logger.logInfo(LOG_TAG, "sanitizeWorkingDirectory: remapped /data/user/0/ path -> " + wd);
        }

        // Verify the result is accessible; fall back to runtime home if not
        File dir = new File(wd);
        if (dir.exists() && dir.isDirectory()) {
            return wd;
        }

        Logger.logInfo(LOG_TAG, "sanitizeWorkingDirectory: \"" + wd + "\" not accessible -> fallback " + runtimeHome);
        return runtimeHome;
    }

    @NonNull
    @Override
    public String getDefaultWorkingDirectoryPath() {
        if (sResolvedHomeDirPath != null) return sResolvedHomeDirPath;
        return TermuxConstants.TERMUX_HOME_DIR_PATH;
    }

    @NonNull
    @Override
    public String getDefaultBinPath() {
        if (sResolvedBinDirPath != null) return sResolvedBinDirPath;
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    }

    @NonNull
    @Override
    public String[] setupShellCommandArguments(@NonNull String executable, String[] arguments) {
        return TermuxShellUtils.setupShellCommandArguments(executable, arguments);
    }

}
