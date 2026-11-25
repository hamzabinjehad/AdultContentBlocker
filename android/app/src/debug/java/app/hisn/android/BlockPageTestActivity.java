package app.hisn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Shell-only trampoline for the non-exported block page, so each of its variants can be looked
 * at without waiting for the guard to fire. Absent from release APKs.
 * adb shell am start -n PACKAGE/app.hisn.android.BlockPageTestActivity --ez word true
 */
public final class BlockPageTestActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent page = new Intent(this, BlockActivity.class);
        if (getIntent().getExtras() != null) page.putExtras(getIntent().getExtras());
        startActivity(page);
        finish();
    }
}
