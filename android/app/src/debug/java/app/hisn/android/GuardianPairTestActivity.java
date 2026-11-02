package app.hisn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Shell-only trampoline that opens the non-exported product pairing screen. */
public final class GuardianPairTestActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent target = new Intent(this, GuardianPairActivity.class);
        target.putExtras(getIntent());
        startActivity(target);
        finish();
    }
}
