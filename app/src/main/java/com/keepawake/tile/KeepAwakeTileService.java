package com.keepawake.tile;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** The Quick Settings button. Tap to toggle Keep Awake on/off. */
public class KeepAwakeTileService extends TileService {

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        if (KeepAwakeService.isRunning()) {
            KeepAwakeService.stop(this);
            setTileState(false, true);
            return;
        }

        // Won't start until battery usage is set to Unlimited: open the app to set it up.
        if (!KeepAwakeService.hasUnlimitedBackground(this)) {
            openSetup();
            return;
        }

        KeepAwakeService.start(this);
        setTileState(true, true);
    }

    @SuppressWarnings("deprecation")
    private void openSetup() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void updateTile() {
        setTileState(KeepAwakeService.isRunning(),
                KeepAwakeService.hasUnlimitedBackground(this));
    }

    private void setTileState(boolean on, boolean ready) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.tile_label));
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(!ready ? "Tap to set up" : on ? "On" : "Off");
        }
        tile.updateTile();
    }
}
