package com.termux.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.termux.app.TermuxInstaller;
import com.termux.services.BootstrapDownloadService;
import com.termux.shared.termux.TermuxBootstrap;

import java.util.List;

import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

public class BootstrapSelectorActivity extends Activity {

    private static final int REQUEST_PICK_ZIP = 1001;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;
    private static final int REQUEST_PICK_TARGZ = 1003;

    private Spinner mSourceSpinner;
    private Button mDownloadButton;
    private Button mPickZipButton;
    private Button mRestoreButton;
    private Button mRetryButton;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private TextView mStatusText;

    private List<BootstrapSource> mSources;
    private BootstrapDownloadService mService;
    private boolean mBound;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            BootstrapDownloadService.LocalBinder lb = (BootstrapDownloadService.LocalBinder) binder;
            mService = lb.getService();
            mBound = true;
            mService.setListener(mServiceListener);
            updateUiFromState(mService.getState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    private final BootstrapDownloadService.Listener mServiceListener = state -> {
        runOnUiThread(() -> updateUiFromState(state));
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (TermuxInstaller.isBootstrapInstalled()) {
            finishWithSuccess();
            return;
        }

        setContentView(com.termux.R.layout.activity_bootstrap_selector);

        mSourceSpinner = findViewById(com.termux.R.id.source_spinner);
        mDownloadButton = findViewById(com.termux.R.id.download_button);
        mPickZipButton = findViewById(com.termux.R.id.pick_zip_button);
        mRestoreButton = findViewById(com.termux.R.id.restore_backup_button);
        mRetryButton = findViewById(com.termux.R.id.retry_button);
        mProgressBar = findViewById(com.termux.R.id.progress_bar);
        mProgressText = findViewById(com.termux.R.id.progress_text);
        mStatusText = findViewById(com.termux.R.id.status_text);

        loadSources();

        mDownloadButton.setOnClickListener(v -> startDownload());
        mPickZipButton.setOnClickListener(v -> pickLocalZip());
        mRestoreButton.setOnClickListener(v -> pickRestoreBackup());
        mRetryButton.setOnClickListener(v -> {
            mRetryButton.setVisibility(View.GONE);
            startDownload();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BootstrapDownloadService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            if (mService != null) mService.setListener(null);
            unbindService(mConnection);
            mBound = false;
        }
    }

    private void loadSources() {
        try {
            mSources = BootstrapSources.loadFromResources(this);
        } catch (Exception e) {
            mStatusText.setText(getString(com.termux.R.string.bootstrap_selector_error_load_sources) + ": " + e.getMessage());
            return;
        }
        ArrayAdapter<BootstrapSource> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, mSources);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSourceSpinner.setAdapter(adapter);
    }

    private void startDownload() {
        if (mSourceSpinner.getSelectedItem() == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
            return;
        }
        doDownload();
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void doDownload() {
        if (mSourceSpinner.getSelectedItem() == null) return;
        BootstrapSource source = (BootstrapSource) mSourceSpinner.getSelectedItem();
        if (hasNotificationPermission()) {
            BootstrapDownloadService.startDownload(this, source);
        } else {
            BootstrapDownloadService.startDownloadBackground(this, source);
        }
        if (!mBound) {
            Intent intent = new Intent(this, BootstrapDownloadService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void pickLocalZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/zip", "application/x-zip-compressed", "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_PICK_ZIP);
    }

    private void pickRestoreBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/gzip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/gzip", "application/x-gzip", "application/x-tar",
            "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_PICK_TARGZ);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            doDownload();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ZIP && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) confirmLocalZip(uri);
        } else if (requestCode == REQUEST_PICK_TARGZ && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) confirmRestoreBackup(uri);
        }
    }

    private void confirmLocalZip(Uri uri) {
        new AlertDialog.Builder(this)
            .setTitle(com.termux.R.string.bootstrap_selector_install_local_title)
            .setMessage(com.termux.R.string.bootstrap_selector_install_local_message)
            .setPositiveButton(com.termux.R.string.bootstrap_selector_install, (dialog, which) -> {
                takePersistableUriPermission(uri);
                if (hasNotificationPermission()) {
                    BootstrapDownloadService.installLocalUri(BootstrapSelectorActivity.this, uri);
                } else {
                    BootstrapDownloadService.installLocalUriBackground(BootstrapSelectorActivity.this, uri);
                }
            })
            .setNegativeButton(com.termux.R.string.bootstrap_selector_cancel, null)
            .show();
    }

    private void confirmRestoreBackup(Uri uri) {
        String name = null;
        try {
            android.database.Cursor c = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
                c.close();
            }
        } catch (Exception ignored) {}
        if (name == null) name = uri.getLastPathSegment();

        new AlertDialog.Builder(this)
            .setTitle(com.termux.R.string.bootstrap_selector_restore_title)
            .setMessage(getString(com.termux.R.string.bootstrap_selector_restore_message, name))
            .setPositiveButton(com.termux.R.string.bootstrap_selector_restore, (dialog, which) -> startRestore(uri))
            .setNegativeButton(com.termux.R.string.bootstrap_selector_cancel, null)
            .show();
    }

    private void startRestore(Uri uri) {
        setButtonsEnabled(false);
        mStatusText.setText(com.termux.R.string.bootstrap_selector_restoring);
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setIndeterminate(true);
        mProgressText.setVisibility(View.VISIBLE);
        mProgressText.setText(com.termux.R.string.bootstrap_selector_starting_restore);

        TermuxInstaller.installFromTarGz(this, uri, new TermuxInstaller.RestoreListener() {
            @Override public void onProgress(final String message) {
                runOnUiThread(() -> mProgressText.setText(message));
            }
            @Override public void onCompleted() {
                runOnUiThread(() -> {
                    TermuxBootstrap.initializeFromRuntime(BootstrapSelectorActivity.this,
                        com.termux.BuildConfig.TERMUX_PACKAGE_VARIANT);
                    finishWithSuccess();
                });
            }
            @Override public void onFailed(final String message) {
                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    mProgressBar.setVisibility(View.GONE);
                    mProgressText.setVisibility(View.GONE);
                    mProgressBar.setIndeterminate(false);
                    mStatusText.setText(com.termux.R.string.bootstrap_selector_restore_failed);
                    new AlertDialog.Builder(BootstrapSelectorActivity.this)
                        .setTitle(com.termux.R.string.bootstrap_selector_restore_failed)
                        .setMessage(message)
                        .setPositiveButton(com.termux.R.string.bootstrap_selector_ok, null)
                        .show();
                });
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        mDownloadButton.setEnabled(enabled);
        mPickZipButton.setEnabled(enabled);
        mRestoreButton.setEnabled(enabled);
        mSourceSpinner.setEnabled(enabled);
        if (enabled) mRetryButton.setVisibility(View.GONE);
    }

    private void takePersistableUriPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
    }

    private void updateUiFromState(BootstrapDownloadService.State state) {
        if (state == null) return;

        mStatusText.setText(state.statusMessage);
        mProgressText.setText(state.progressMessage);
        mProgressBar.setIndeterminate(state.indeterminate);
        mProgressBar.setProgress(state.percent);
        mProgressBar.setVisibility(state.isBusy() || state.failed ? View.VISIBLE : View.GONE);
        mProgressText.setVisibility(state.isBusy() || state.failed ? View.VISIBLE : View.GONE);

        boolean busy = state.isBusy();
        mDownloadButton.setEnabled(!busy);
        mPickZipButton.setEnabled(!busy);
        mSourceSpinner.setEnabled(!busy);
        mRetryButton.setVisibility(state.failed ? View.VISIBLE : View.GONE);

        if (state.success) {
            finishWithSuccess();
        }
    }

    private void finishWithSuccess() {
        setResult(RESULT_OK);
        finish();
    }
}
