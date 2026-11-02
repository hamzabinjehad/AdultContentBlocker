package app.hisn.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shell-only, debug-only fixture setup for the managed-mode acceptance run.
 *
 * It only tightens choices: enabling managed lockdown, strict mode, blocked-app
 * suspension or adding blocked apps. It never releases managed mode or removes
 * a blocked app during an active commitment. Absent from release builds.
 */
public final class ManagedTestSetupActivity extends Activity {
    static final String TAG = "HisnManagedSetup";
    static final String FILE = "managed-test-setup.json";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String report = "FAIL";
        try {
            PolicyStore store = PolicyStore.get(this);
            if (getIntent().getBooleanExtra("release", false)) {
                // The release path is only valid when no commitment and no keep
                // switch remain; this harness does not create a commitment.
                store.updateManagedChoice(false, false, false, false, false);
                DnsVpnService.setResumeRequested(this, false);
                store.updateSafeSearch(false);
                String released = UninstallProtectionController.releaseManagedMode(this);
                report = "RELEASE result=" + released
                        + " owner=" + (UninstallProtectionController.read(this).deviceOwner)
                        + " keep=" + store.uninstallLock()
                        + " lockdown=" + store.managedLockdown()
                        + " suspend=" + store.suspendBlocked();
                Log.i(TAG, report);
                try (FileOutputStream out = openFileOutput(FILE, MODE_PRIVATE)) {
                    out.write(report.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
                finish();
                return;
            }
            String add = getIntent().getStringExtra("block");
            if (add != null && !add.trim().isEmpty()) {
                Set<String> apps = new LinkedHashSet<>(store.snapshot().blockedApps);
                for (String name : add.split(",")) {
                    if (!name.trim().isEmpty()) apps.add(name.trim());
                }
                store.update(String.join("\n", store.snapshot().domains.blockedRules()),
                        String.join("\n", store.snapshot().words.configuredRules()),
                        apps, store.snapshot().scannedApps);
            }
            boolean keep = getIntent().getBooleanExtra("keep", store.uninstallLock());
            boolean install = getIntent().getBooleanExtra("install", store.installLock());
            boolean lockdown = getIntent().getBooleanExtra("lockdown", store.managedLockdown());
            boolean strict = getIntent().getBooleanExtra("strict", store.managedStrict());
            boolean suspend = getIntent().getBooleanExtra("suspend", store.suspendBlocked());
            store.updateManagedChoice(keep, install, lockdown, strict, suspend);
            if (getIntent().hasExtra("safe_search")) {
                store.updateSafeSearch(getIntent().getBooleanExtra("safe_search", false));
            }
            UninstallProtectionController.Result result = UninstallProtectionController.sync(this);
            report = "PASS ok=" + result.ok + " reason=" + result.reason
                    + " lockdown=" + store.managedLockdown()
                    + " strict=" + store.managedStrict()
                    + " suspend=" + store.suspendBlocked()
                    + " keep=" + store.uninstallLock()
                    + " blocked=" + store.snapshot().blockedApps.size();
        } catch (RuntimeException failure) {
            report = "FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        Log.i(TAG, report);
        try (FileOutputStream out = openFileOutput(FILE, MODE_PRIVATE)) {
            out.write(report.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { /* The log and status screen still carry the result. */ }
        finish();
    }
}
