package app.hisn.android;

import android.content.Context;
import android.content.Intent;

/**
 * The one way into the voluntary breathing pause, shared by the dashboard, the launcher shortcut
 * and the Quick Settings tile. Nothing is blocked by it: the person asked for a moment, and the
 * pause screen only breathes with them and counts the pause on this phone.
 */
public final class PauseEntry {
    private PauseEntry() { }

    public static Intent intent(Context context) {
        // Each edition has its own breathing screen: Unrot's cyclic sighing, Hisn's istighfār.
        if (!Edition.islamic) {
            return new Intent(context, BreatheActivity.class).setAction(Intent.ACTION_VIEW);
        }
        return new Intent(context, HisnBreatheActivity.class).setAction(Intent.ACTION_VIEW);
    }
}
