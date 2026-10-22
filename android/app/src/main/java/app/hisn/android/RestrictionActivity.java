package app.hisn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shows a reason category, never captured text, matched words or browsing history. */
public final class RestrictionActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int padding = Math.round(28 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText(R.string.restriction_title);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        TextView body = new TextView(this);
        body.setText(getIntent().getBooleanExtra("visual", false) ? R.string.restriction_visual
                : getIntent().getBooleanExtra("schedule", false) ? R.string.restriction_schedule
                : getIntent().getBooleanExtra("word", false) ? R.string.restriction_word : R.string.restriction_app);
        body.setTextSize(18);
        body.setGravity(Gravity.CENTER);
        layout.addView(body);
        Button home = new Button(this);
        home.setText(R.string.restriction_home);
        home.setOnClickListener(view -> goHome());
        layout.addView(home);
        setContentView(layout);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goHome);
        }
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

}
