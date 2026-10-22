package app.hisn.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
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
        classifier = new VisualClassifier(this, loaded -> {
            if (!"ready".equals(loaded)) { status.setText("FAIL: model unavailable"); return; }
            Bitmap frame = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
            frame.eraseColor(Color.rgb(80, 120, 160));
            classifier.inspect(frame, verdict -> status.setText("allow".equals(verdict)
                    ? "PASS: actual bundled model allowed a fixed benign frame. No screen captured; no policy changed."
                    : "FAIL: benign frame verdict " + verdict));
            frame.recycle();
        });
    }
    @Override protected void onDestroy() {
        if (classifier != null) classifier.close();
        super.onDestroy();
    }
}
