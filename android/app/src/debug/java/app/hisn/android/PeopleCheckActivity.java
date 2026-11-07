package app.hisn.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.TextView;

/** Debug-only real face-model smoke test on a pinned public-domain image. No screen capture. */
public final class PeopleCheckActivity extends Activity {
    private VisualClassifier classifier;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(32, 96, 32, 32);
        setContentView(status);
        if (!"app.hisn.android.visualcheck".equals(getPackageName())) {
            report(status, "FAIL: isolated package required");
            return;
        }
        Bitmap frame = BitmapFactory.decodeResource(getResources(), R.drawable.astronaut);
        if (frame == null) { report(status, "FAIL: face fixture missing"); return; }
        String people = getIntent().getStringExtra("people");
        if (!PersonBlur.on(people)) people = PersonBlur.ALL;
        classifier = new VisualClassifier(this, "wasm", people, loaded -> {
            if (!"ready".equals(loaded)) { report(status, "FAIL: face model unavailable"); return; }
            classifier.inspect(frame, result -> {
                boolean pass = "allow".equals(result.verdict) && result.personBlur;
                report(status, (pass ? "PASS" : "FAIL") + ": " + classifier.actualBackend()
                        + " verdict=" + result.verdict + " face=" + result.personBlur);
                frame.recycle();
            });
        });
    }

    private void report(TextView status, String text) {
        getSharedPreferences("hisn_people_check", MODE_PRIVATE).edit().putString("result", text).commit();
        status.setText(text);
    }

    @Override protected void onDestroy() {
        if (classifier != null) classifier.close();
        super.onDestroy();
    }
}
