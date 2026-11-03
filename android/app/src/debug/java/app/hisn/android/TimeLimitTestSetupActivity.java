package app.hisn.android;

import android.app.Activity;
import android.os.Bundle;

import java.util.LinkedHashMap;
import java.util.Map;

import app.hisn.core.AppTimeLimit;

/**
 * Debug-only setup for device tests and screenshots. It writes one daily time
 * limit into the real Hisn policy so the accessibility guard can be exercised
 * against a disposable fixture package. Never present in a release build;
 * check-release.py refuses an APK that contains this class.
 *
 * <pre>
 * adb shell am start -n app.hisn.android/.TimeLimitTestSetupActivity \
 *     --es target app.hisn.android.test --ei minutes 5
 * </pre>
 */
public final class TimeLimitTestSetupActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String target = getIntent().getStringExtra("target");
            int minutes = getIntent().getIntExtra("minutes", 5);
            Map<String, Integer> limits = new LinkedHashMap<>();
            limits.put(target, minutes);
            PolicyStore.get(this).updateTimeLimits(new AppTimeLimit(limits));
            setResult(RESULT_OK);
        } catch (RuntimeException refused) {
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
