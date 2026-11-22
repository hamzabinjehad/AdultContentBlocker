package app.hisn.android;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * A Quick Settings tile that opens the voluntary breathing pause from any app. An urge rarely
 * starts inside Hisn, so the way to a pause sits where the person already is: one swipe down.
 *
 * <p>Android 7.0 added tiles. On 6.x the manifest entry is ignored and this class never loads.
 * The tile has no on/off state: it is an action, and it never changes a rule.
 */
@SuppressLint("NewApi") // Only bound by Android 7.0 and later, which own the tile API.
public final class PauseTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.pause_voluntary_action));
        tile.setContentDescription(getString(R.string.pause_voluntary_summary));
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(getString(R.string.pause_tile_subtitle));
        tile.updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        // A locked phone asks for the unlock first, so the pause opens on top, not behind it.
        if (isLocked()) {
            unlockAndRun(this::openPause);
        } else {
            openPause();
        }
    }

    private void openPause() {
        Intent intent = PauseEntry.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                startLegacy(intent);
            }
        } catch (RuntimeException unavailable) {
            // A shade that refuses the launch leaves the tile as it was; nothing else changes.
        }
    }

    // The Intent form is the only one before Android 14; openPause() calls it only there.
    @SuppressWarnings("deprecation")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void startLegacy(Intent intent) {
        startActivityAndCollapse(intent);
    }
}
