package app.hisn.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Direct builds only: after a reboot, Android starts accessibility services seconds (on a busy
 * boot, minutes) after unlock, which leaves Hisn's uninstall screens unguarded. While the owner
 * keeps Hisn installed, this covers the screen from BOOT_COMPLETED for at least a minute and
 * until the guard is running. A cap stops a switched-off accessibility service from holding the
 * phone. Needs "Display over other apps"; without it the boot gap stays open.
 *
 * Settings and the package installer hide other apps' overlays, so the shield cannot cover
 * them; it covers the launcher (icons, long-press uninstall) while the guard comes up. It starts
 * at LOCKED_BOOT_COMPLETED from a device-protected copy of the switch, waits behind the lock
 * screen, and appears on unlock, because BOOT_COMPLETED reaches apps many seconds later.
 */
public final class BootShieldService extends Service {
    static final long MIN_MILLIS = 60000;
    static final long MAX_MILLIS = 180000;
    private static final String CHANNEL = "boot_shield";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View shield;
    private TextView countdown;
    private long startedAt;

    private BroadcastReceiver unlockWait;

    /** Copies the keep-installed switch to storage readable before the first unlock. */
    static void mirror(Context context, boolean keepInstalled) {
        if (!BuildConfig.SELF_PROTECTION) return;
        bootPrefs(context).edit().putBoolean("armed", keepInstalled).apply();
    }

    private static SharedPreferences bootPrefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("boot_shield", MODE_PRIVATE);
    }

    /** Called from the boot receivers; a no-op unless the shield can and should run. */
    static void startIfNeeded(Context context) {
        if (!BuildConfig.SELF_PROTECTION || !Settings.canDrawOverlays(context)) return;
        try {
            if (!bootPrefs(context).getBoolean("armed", false)) return;
            context.startForegroundService(new Intent(context, BootShieldService.class));
        } catch (RuntimeException unavailable) { /* The accessibility guard still starts on its own. */ }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.boot_shield_title), NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_hisn).setContentTitle(getString(R.string.boot_shield_title))
                .setContentText(getString(R.string.boot_shield_body)).build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(3, notification);
        }
        if (shield != null || unlockWait != null) return START_NOT_STICKY;
        if (!getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            show();
        } else {
            // Never cover the lock screen: the PIN must stay reachable. The minute starts at unlock.
            unlockWait = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent unlocked) {
                    unregisterReceiver(this);
                    unlockWait = null;
                    if (shield == null) show();
                }
            };
            registerReceiver(unlockWait, new IntentFilter(Intent.ACTION_USER_PRESENT));
        }
        return START_NOT_STICKY;
    }

    private void show() {
        startedAt = SystemClock.elapsedRealtime();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.rgb(16, 20, 28));
        int pad = (int) (32 * getResources().getDisplayMetrics().density);
        int under = (int) (200 * getResources().getDisplayMetrics().density);
        // The window extends past the screen bottom by "under"; padding keeps the text centred.
        layout.setPadding(pad, pad, pad, pad + under);
        TextView title = new TextView(this);
        title.setText(R.string.boot_shield_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        TextView body = new TextView(this);
        body.setText(R.string.boot_shield_body);
        body.setTextColor(Color.rgb(200, 204, 212));
        body.setTextSize(16);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, pad / 2, 0, pad / 2);
        countdown = new TextView(this);
        countdown.setTextColor(Color.WHITE);
        countdown.setTextSize(40);
        countdown.setGravity(Gravity.CENTER);
        layout.addView(title);
        layout.addView(body);
        layout.addView(countdown);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE);
        // Reach under the navigation bar too, so no launcher strip stays tappable.
        params.height = getResources().getDisplayMetrics().heightPixels + under;
        params.gravity = Gravity.TOP;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            getSystemService(WindowManager.class).addView(layout, params);
            shield = layout;
            tick.run();
        } catch (RuntimeException denied) {
            stopSelf();
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long elapsed = SystemClock.elapsedRealtime() - startedAt;
            boolean guarded = TextGuardService.connected && TextGuardService.ready;
            if ((elapsed >= MIN_MILLIS && guarded) || elapsed >= MAX_MILLIS) {
                stopSelf();
                return;
            }
            long left = Math.max(0, MIN_MILLIS - elapsed);
            countdown.setText(left > 0 ? String.valueOf((left + 999) / 1000)
                    : getString(R.string.boot_shield_waiting));
            handler.postDelayed(this, 250);
        }
    };

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        if (unlockWait != null) {
            try { unregisterReceiver(unlockWait); } catch (RuntimeException gone) { /* Already removed. */ }
            unlockWait = null;
        }
        if (shield != null) {
            try { getSystemService(WindowManager.class).removeView(shield); }
            catch (RuntimeException gone) { /* Already detached. */ }
            shield = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
