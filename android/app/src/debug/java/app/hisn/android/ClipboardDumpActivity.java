package app.hisn.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Shell-only clipboard reader for the pairing-screen acceptance run. */
public final class ClipboardDumpActivity extends Activity {
    private boolean written;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new TextView(this));
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || written) return;
        written = true;
        String text = "";
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence value = clip.getItemAt(0).coerceToText(this);
                if (value != null) text = value.toString();
            }
        }
        try (FileOutputStream out = openFileOutput("clipboard.txt", MODE_PRIVATE)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        finish();
    }
}
