package com.keepawake.tile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.quicksettings.TileService;

/**
 * Setup screen. Keep Awake can't be switched on until battery usage for the
 * app is set to Unlimited (unrestricted background use).
 */
public class MainActivity extends Activity {

    private AlertDialog dialog;
    private boolean tileOffered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check every time the user comes back from Settings.
        if (dialog != null) dialog.dismiss();
        TileService.requestListeningState(this,
                new ComponentName(this, KeepAwakeTileService.class));

        if (KeepAwakeService.hasUnlimitedBackground(this)) {
            showReady();
        } else {
            showNeedsUnlimited();
        }
    }

    private void showNeedsUnlimited() {
        dialog = new AlertDialog.Builder(this)
                .setTitle("Allow unlimited background use")
                .setMessage("Keep Awake needs unlimited battery / background use so Android "
                        + "doesn't shut it down while it's keeping your screen on.\n\n"
                        + "Tap Allow, then choose \"Allow\". If you see a list instead, "
                        + "set Battery to \"Unlimited\" (or \"Unrestricted\").")
                .setPositiveButton("Allow", (d, w) -> requestUnlimited())
                .setNeutralButton("Battery settings", (d, w) -> openAppBatterySettings())
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void requestUnlimited() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            openAppBatterySettings();
        }
    }

    private void openAppBatterySettings() {
        // The app's info page, where Battery -> Unlimited lives on most phones.
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void showReady() {
        // Android 13+: offer to add the tile to Quick Settings directly (once).
        if (Build.VERSION.SDK_INT >= 33 && !tileOffered) {
            tileOffered = true;
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            sbm.requestAddTileService(
                    new ComponentName(this, KeepAwakeTileService.class),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_tile),
                    getMainExecutor(),
                    result -> { });
        }

        dialog = new AlertDialog.Builder(this)
                .setTitle("You're all set")
                .setMessage("Unlimited background use is on.\n\n"
                        + "If the button isn't there yet: swipe down twice, tap the pencil "
                        + "icon, and drag \"Keep Awake\" into your Quick Settings.\n\n"
                        + "Tap it to keep the screen on indefinitely. Tap again (or use "
                        + "\"Turn off\" in the notification) to go back to normal.")
                .setPositiveButton("Got it", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }
}
