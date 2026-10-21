package kz.nurasql77.urbanreign;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Java-only launcher. The ISO is chosen and checked BEFORE NativeActivity starts,
 * so the NativeActivity/native startup thread cannot block the file picker.
 */
public final class LauncherActivity extends Activity {
    private static final int PICK_ISO_REQUEST = 7421;
    private static final String PREFS = "urban_reign_touch";
    private static final long MIN_ISO_BYTES = 16L * 1024L * 1024L;

    private TextView status;
    private Button startButton;
    private Uri selectedUri;
    private boolean validating;

    private void logStage(String message) {
        String line = "[Java][LauncherActivity] " + System.currentTimeMillis() + " " + message + "\n";
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "urban_native.log"), true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (Exception error) {
            android.util.Log.e("UrbanReignDiag", message, error);
        }
        android.util.Log.i("UrbanReignDiag", message);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        logStage("LauncherActivity onCreate entered");
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("iso_uri", null);
        if (saved != null) selectedUri = Uri.parse(saved);
        setContentView(createMenu());
        refreshStatus();
        logStage("Launcher menu displayed");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout createMenu() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(15, 18, 24));

        TextView title = new TextView(this);
        title.setText("URBAN REIGN");
        title.setTextColor(Color.rgb(242, 184, 74));
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Android port • ARM64");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(8);
        root.addView(subtitle, subtitleParams);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(28), 0, dp(20));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button chooseButton = new Button(this);
        chooseButton.setText("Choose Urban Reign ISO");
        chooseButton.setOnClickListener(v -> launchPicker());
        root.addView(chooseButton, buttonParams());

        startButton = new Button(this);
        startButton.setText("Start game");
        startButton.setOnClickListener(v -> startGame());
        LinearLayout.LayoutParams startParams = buttonParams();
        startParams.topMargin = dp(8);
        root.addView(startButton, startParams);

        Button diagnosticsButton = new Button(this);
        diagnosticsButton.setText("Show diagnostic log");
        diagnosticsButton.setOnClickListener(v -> showDiagnostics());
        LinearLayout.LayoutParams diagnosticsParams = buttonParams();
        diagnosticsParams.topMargin = dp(8);
        root.addView(diagnosticsButton, diagnosticsParams);

        TextView note = new TextView(this);
        note.setText("Select your own uncompressed Urban Reign (USA) ISO (SLUS-21209). Not a folder. If this APK was built without boot ELF / generated game code, Start will show a black screen — rebuild via Actions with boot_elf_url. Use Show diagnostic log after a failed start.");
        note.setTextColor(Color.rgb(185, 190, 200));
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(22);
        root.addView(note, noteParams);
        return root;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(-1, dp(54));
    }

    @Override
    protected void onResume() {
        super.onResume();
        String error = MainActivity.pendingLauncherError;
        if (error != null) {
            MainActivity.pendingLauncherError = null;
            validating = false;
            refreshStatus();
            showError("Game could not start", error);
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        if (validating) {
            startButton.setEnabled(false);
            return;
        }
        if (selectedUri == null) {
            status.setText("No disc image selected. Choose the ISO file first.");
            startButton.setEnabled(false);
        } else {
            status.setText("Selected file:\n" + displayName(selectedUri) + "\n\nPress Start game to verify the disc and launch it.");
            startButton.setEnabled(true);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String name = cursor.getString(column);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null ? "Selected ISO" : uri.getLastPathSegment();
    }

    private void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Urban Reign (USA) ISO"), PICK_ISO_REQUEST);
        } catch (RuntimeException error) {
            showError("File picker could not be opened", error.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ISO_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // The open-file descriptor remains valid for this launch even if access is not persistent.
        }

        try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (fd == null) throw new IOException("Android could not open the selected file.");
            if (fd.getStatSize() < MIN_ISO_BYTES) {
                throw new IOException("The file is too small or this provider does not expose a seekable local file. Download the ISO to device storage and select it again.");
            }
            selectedUri = uri;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("iso_uri", uri.toString()).apply();
            refreshStatus();
            Toast.makeText(this, "ISO selected. Press Start game to verify it.", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            showError("Cannot open this file", error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }

    private void startGame() {
        if (selectedUri == null || validating) {
            refreshStatus();
            return;
        }
        final Uri uri = selectedUri;
        logStage("Start game tapped; URI=" + uri);
        validating = true;
        status.setText("Checking the Urban Reign disc…\nThe menu stays open while the native checker starts.");
        startButton.setEnabled(false);

        // Loading the large native library can take time. Do it in the background, not before
        // the launcher screen appears, so Android never remains on the app-icon splash here.
        Thread checker = new Thread(() -> {
            String issue = null;
            try {
                logStage("ISO checker thread started");
                logStage("Before System.loadLibrary(urban) in launcher");
                System.loadLibrary("urban");
                logStage("System.loadLibrary(urban) succeeded in launcher");
                try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r")) {
                    if (fd == null) throw new IOException("Android could not reopen the selected ISO.");
                    if (fd.getStatSize() < MIN_ISO_BYTES) {
                        throw new IOException("The ISO is too small or is no longer accessible as a seekable local file.");
                    }
                    String path = "/proc/self/fd/" + fd.getFd();
                    String cache = new File(getFilesDir(), "game_data").getAbsolutePath();
                    logStage("Before nativeValidateIsoPath");
                    String validation = MainActivity.nativeValidateIsoPath(path, cache);
                    logStage("nativeValidateIsoPath returned; hasError=" + (validation != null && !validation.isEmpty()));
                    if (validation != null && !validation.isEmpty()) issue = validation;
                }
            } catch (Throwable error) {
                logStage("ISO checker failed: " + android.util.Log.getStackTraceString(error));
                issue = error.getMessage() == null ? error.toString() : error.getMessage();
            }

            final String validationIssue = issue;
            runOnUiThread(() -> {
                validating = false;
                if (isFinishing()) return;
                if (validationIssue != null && !validationIssue.isEmpty()) {
                    startButton.setEnabled(selectedUri != null);
                    status.setText("Disc check failed. Choose the correct Urban Reign (USA) ISO.");
                    showError("Cannot use this ISO", validationIssue);
                    return;
                }
                logStage("ISO validation passed; launching MainActivity");
                Intent intent = new Intent(LauncherActivity.this, MainActivity.class);
                intent.putExtra("iso_uri", uri.toString());
                startActivity(intent);
                logStage("startActivity(MainActivity) returned");
            });
        }, "urban-iso-check");
        checker.start();
    }

    private void showDiagnostics() {
        logStage("Diagnostic log dialog opened");
        File logFile = new File(getFilesDir(), "urban_native.log");
        String logText;
        try {
            if (!logFile.isFile() || logFile.length() == 0) {
                logText = "No native log has been saved yet.\n\n1. Press Start game.\n2. Wait for the black screen / Not responding message.\n3. Force-close the game, reopen Urban Reign, then tap Show diagnostic log.";
            } else {
                byte[] bytes = Files.readAllBytes(logFile.toPath());
                logText = new String(bytes, StandardCharsets.UTF_8);
                if (logText.length() > 14000) {
                    logText = "...(older log omitted)...\n" + logText.substring(logText.length() - 14000);
                }
            }
        } catch (Exception error) {
            logText = "Could not read native log: " + error;
        }

        TextView content = new TextView(this);
        content.setText(logText);
        content.setTextColor(Color.WHITE);
        content.setTextSize(12);
        content.setTextIsSelectable(true);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        new AlertDialog.Builder(this)
            .setTitle("Urban Reign diagnostic log")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear log", (dialog, which) -> {
                try {
                    Files.deleteIfExists(logFile.toPath());
                    Toast.makeText(this, "Log cleared.", Toast.LENGTH_SHORT).show();
                } catch (Exception error) {
                    showError("Could not clear log", error.toString());
                }
            })
            .show();
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message == null ? "Unknown error." : message)
            .setPositiveButton("OK", null)
            .show();
    }
}
