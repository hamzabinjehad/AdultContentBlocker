package app.hisn.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Debug-only, read-only exporter for the managed uninstall state.
 *
 * The shell test cannot read a status TextView, and calling DevicePolicyManager
 * from the test process would not prove what the app itself sees. This activity
 * runs in the app, reads the same controller the UI uses, writes one JSON line
 * to private storage and finishes. It never applies, clears or releases policy.
 *
 * Guarded by android.permission.DUMP in the debug manifest (shell holds it) and
 * absent from release builds.
 */
public final class ManagedStatusActivity extends Activity {
    static final String TAG = "HisnManagedStatus";
    static final String FILE = "managed-status.json";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        JSONObject json = new JSONObject();
        try {
            UninstallProtectionController.Status status = UninstallProtectionController.read(this);
            json.put("package", getPackageName());
            json.put("versionName", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            json.put("versionCode", getPackageManager().getPackageInfo(getPackageName(), 0).versionCode);
            json.put("deviceOwner", status.deviceOwner);
            json.put("adminActive", status.adminActive);
            json.put("uninstallBlocked", status.uninstallBlocked);
            json.put("keepInstalled", status.keepInstalled);
            json.put("remainingMillis", status.remainingMillis);
            json.put("state", status.state().name());
            json.put("mayRelease", status.mayRelease());
            if (status.error != null) json.put("error", status.error);
        } catch (Exception failure) {
            try { json.put("error", failure.getClass().getSimpleName()); }
            catch (Exception ignored) { /* JSONObject cannot fail on a simple string. */ }
        }
        String line = json.toString();
        Log.i(TAG, line);
        try (FileOutputStream out = openFileOutput(FILE, MODE_PRIVATE)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (Exception writeFailure) {
            // The shell test reads the file; a write failure must be visible.
            Log.w(TAG, "could not persist status", writeFailure);
        }
        finish();
    }
}
