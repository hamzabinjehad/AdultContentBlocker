package app.hisn.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/** Shell-only fixed benign model smoke test. Does not capture a screen or modify owner settings. */
public final class VisualModelCheckActivity extends Activity {
    private VisualClassifier classifier;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView status = new TextView(this);
        status.setText("Loading bundled visual model (no screen capture)...");
        status.setTextSize(20);
        setContentView(status);
        String check = getIntent().getStringExtra("check");
        if (check != null && !"app.hisn.android.visualcheck".equals(getPackageName())) {
            report(status, "FAIL: fault injection requires the isolated package");
            return;
        }
        classifier = new VisualClassifier(this, loaded -> {
            if (!"ready".equals(loaded)) { report(status, "FAIL: model unavailable"); return; }
            if ("init-fallback".equals(check) && !"cpu".equals(classifier.actualBackend())) {
                report(status, "FAIL: initialization did not recover on CPU"); return;
            }
            if ("frame-fallback".equals(check)) { checkFrameFailure(status); return; }
            Bitmap frame = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
            frame.eraseColor(Color.rgb(80, 120, 160));
            classifier.inspect(frame, verdict -> report(status, "allow".equals(verdict)
                    ? "PASS: " + (check == null ? "model smoke" : check) + "; actual backend " + classifier.actualBackend()
                        + "; actual model allowed fixed benign frame; no screen captured or policy changed"
                    : "FAIL: benign frame verdict " + verdict));
            frame.recycle();
        });
        if ("init-fallback".equals(check)) injectFailure(classifier, status);
    }

    private void checkFrameFailure(TextView status) {
        VisualClassifier failed = classifier;
        Bitmap frame = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
        frame.eraseColor(Color.rgb(80, 120, 160));
        int[] callbacks = {0};
        failed.inspect(frame, verdict -> {
            callbacks[0]++;
            if (!"unknown".equals(verdict)) { report(status, "FAIL: failed frame did not remain unknown"); return; }
            failed.close();
            classifier = new VisualClassifier(this, loaded -> {
                if (!"ready".equals(loaded) || !"cpu".equals(classifier.actualBackend())) {
                    report(status, "FAIL: future session did not recover on CPU"); return;
                }
                Bitmap next = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
                next.eraseColor(Color.rgb(80, 120, 160));
                classifier.inspect(next, actual -> new Handler(Looper.getMainLooper()).postDelayed(() ->
                        report(status, callbacks[0] == 1 && "allow".equals(actual)
                                ? "PASS: frame-fallback; failed frame unknown once; fresh CPU session allowed a new benign frame"
                                : "FAIL: stale callback or CPU classification error"), 500));
                next.recycle();
            });
        });
        frame.recycle();
        injectFailure(failed, status);
    }

    private void injectFailure(VisualClassifier target, TextView status) {
        try {
            // Exercise our private failure handler, not an Android hidden API or a fabricated model verdict.
            java.lang.reflect.Method failed = VisualClassifier.class.getDeclaredMethod("failed");
            failed.setAccessible(true);
            failed.invoke(target);
        } catch (ReflectiveOperationException unavailable) { report(status, "FAIL: fault injection unavailable"); }
    }

    private void report(TextView status, String result) {
        getSharedPreferences("hisn_model_check", MODE_PRIVATE).edit().putString("result", result).commit();
        status.setText(result);
    }
    @Override protected void onDestroy() {
        if (classifier != null) classifier.close();
        super.onDestroy();
    }
}
