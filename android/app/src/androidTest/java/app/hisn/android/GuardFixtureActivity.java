package app.hisn.android;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Harmless, network-free screen shipped only in the optional instrumentation APK. */
public final class GuardFixtureActivity extends Activity {
    static final String WORD = "hisnfixtureword";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        renderFixture();
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        renderFixture();
    }

    private void renderFixture() {
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        // Only this harmless test window stays awake for the real schedule boundary.
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Hisn harmless protection test");
        layout.addView(title);
        String mode = getIntent().getStringExtra("mode");
        if ("visual-normal".equals(mode) || "visual-video".equals(mode) || "visual-secure".equals(mode)) {
            if ("visual-secure".equals(mode)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            final boolean moving = "visual-video".equals(mode);
            android.view.View canvas = new android.view.View(this) {
                private final android.graphics.Paint paint = new android.graphics.Paint();
                @Override protected void onDraw(android.graphics.Canvas target) {
                    target.drawColor(android.graphics.Color.rgb(80, 120, 160));
                    paint.setColor(android.graphics.Color.rgb(40, 75, 100));
                    float x = moving ? (android.os.SystemClock.uptimeMillis() / 8) % Math.max(1, getWidth()) : getWidth() / 2f;
                    target.drawCircle(x, getHeight() / 2f, getWidth() / 5f, paint);
                    if (moving) postInvalidateDelayed(100);
                }
            };
            canvas.setContentDescription("Harmless synthetic visual fixture: shapes only");
            setContentView(canvas);
            return;
        }
        if ("editable".equals(mode) || "password".equals(mode)) {
            EditText input = new EditText(this);
            if ("password".equals(mode)) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setText(WORD);
            input.setShowSoftInputOnFocus(false);
            layout.addView(input);
            input.requestFocus();
        } else if ("wide".equals(mode)) {
            int height = Math.max(1, Math.round(6 * getResources().getDisplayMetrics().density));
            for (int i = 0; i < 80; i++) {
                TextView label = new TextView(this);
                label.setText(i == 74 ? WORD : "ordinary");
                label.setTextSize(3);
                layout.addView(label, new LinearLayout.LayoutParams(-1, height));
            }
        } else {
            TextView label = new TextView(this);
            if ("long".equals(mode)) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < 400; i++) text.append("ordinary ");
                label.setText(text.append(WORD));
            } else if ("adult-en".equals(mode)) label.setText("Watch free porn videos");
            else if ("adult-ar".equals(mode)) label.setText("تحميل محتوى إباحي مجانا");
            else if ("medical".equals(mode)) label.setText("Free sexual health education and addiction recovery support");
            else if ("adult-compact-en".equals(mode)) label.setText("Watch p.o.r.n videos");
            else if ("adult-compact-ar".equals(mode)) label.setText("شاهد إ.ب.ا.ح.ي.ة مجانا");
            else if ("adult-label-en".equals(mode)) label.setText("Porn videos");
            else if ("adult-label-ar".equals(mode)) label.setText("محتوى إباحي");
            else if ("protective".equals(mode)) label.setText("Download a free porn blocker");
            else if ("recovery".equals(mode)) label.setText("Watch porn addiction recovery resources");
            else label.setText("word".equals(mode) ? WORD : "A harmless ordinary page.");
            layout.addView(label);
        }
        setContentView(layout);
    }
}
