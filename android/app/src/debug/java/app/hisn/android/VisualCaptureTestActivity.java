package app.hisn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import java.util.Collections;

/** Shell-only controller. It refuses to configure or arm the owner's ordinary app. */
public final class VisualCaptureTestActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        handleIntent();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent();
    }

    private void handleIntent() {
        String action = getIntent().getStringExtra("action");
        try {
            if (!"app.hisn.android.visualcheck".equals(getPackageName()))
                throw new IllegalStateException("Isolated test package required");
            PolicyStore store = PolicyStore.get(this);
            if ("configure".equals(action)) {
                if (store.remainingMillis() != 0 || !store.snapshot().blockedApps.isEmpty()
                        || !store.domainText().isEmpty() || !store.wordText().isEmpty())
                    throw new IllegalStateException("Test copy must have no other restrictions");
                store.update("", "", Collections.emptySet(), Collections.singleton(getPackageName() + ".test"), false);
                store.updateVisual(true);
            } else if ("stop".equals(action)) {
                VisualTestHooks.clear();
                store.updateVisual(false);
            } else if ("force-once".equals(action)) {
                if (!getIntent().getBooleanExtra("confirmSynthetic", false)
                        || !VisualTestHooks.arm(getPackageName(), SystemClock.uptimeMillis()))
                    throw new IllegalStateException("Explicit synthetic-test confirmation required");
            } else if ("clear-force".equals(action)) VisualTestHooks.clear();
            else if ("settings".equals(action)) {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } else if (!"status".equals(action)) throw new IllegalArgumentException("Unknown test action");
            report("OK " + action + " CONNECTED " + TextGuardService.connected
                    + " ENABLED " + store.snapshot().visualEnabled + " STATE " + VisualGuard.state
                    + " CAPTURES " + VisualGuard.captures + " SAMPLES " + VisualGuard.samples
                    + " REQUESTS " + VisualGuard.captureRequests
                    + " ELIGIBILITY " + VisualGuard.eligibility
                    + " INTERVENTIONS " + VisualGuard.interventions + " DISCARDED " + VisualGuard.discarded
                    + " CAPTURE_FAILURES " + VisualGuard.captureFailures + " CAPTURE_ERROR " + VisualGuard.lastCaptureError);
        } catch (RuntimeException failure) {
            report("FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void report(String result) {
        result += " NONCE " + getIntent().getStringExtra("probeNonce");
        getSharedPreferences("hisn_visual_probe", MODE_PRIVATE).edit().putString("result", result).commit();
        TextView text = new TextView(this);
        text.setText("Hisn Visual Test\n\nOnly the separate harmless fixture is selected for screenshots. "
                + "No camera, microphone, VPN, or real commitment is used. Screenshots are processed locally and not saved.\n\n"
                + "Enable only Hisn Visual Test in accessibility settings to run capture acceptance checks. "
                + "Leave your existing Hisn service unchanged.\n\n" + result);
        text.setTextSize(18);
        text.setPadding(32, 96, 32, 32);
        setContentView(text);
    }
}
