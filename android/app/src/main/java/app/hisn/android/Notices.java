package app.hisn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Third-party notices bundled as assets. Shown on request; nothing is downloaded. */
final class Notices {
    /** License texts for the bundled domain and word lists; check-notices.py keeps this complete. */
    private static final String[] LIST_FILES = {
            "licenses/StevenBlack-hosts-MIT.txt", "licenses/Sinfonietta-hostfiles-MIT.txt",
            "licenses/GPL-3.0.txt", "licenses/LDNOOBW-CC-BY-4.0.txt"};
    private static final int[] LIST_TITLES = {
            R.string.notice_mit_stevenblack, R.string.notice_mit_sinfonietta,
            R.string.notice_gpl, R.string.notice_cc_by};
    // Copied with the visual runtime from extension/visual by the copyAndroidVisual task.
    private static final String VISUAL_NOTICES = "visual/notices";
    private static final String VISUAL_LEGAL = "visual/engine.bundle.js.LEGAL.txt";

    static void show(Activity activity) {
        List<String> titles = new ArrayList<>();
        List<String> files = new ArrayList<>();
        titles.add(activity.getString(R.string.notice_sources));
        files.add(null);
        for (int i = 0; i < LIST_FILES.length; i++) {
            titles.add(activity.getString(LIST_TITLES[i]));
            files.add(LIST_FILES[i]);
        }
        try {
            String[] visual = activity.getAssets().list(VISUAL_NOTICES);
            if (visual != null) {
                Arrays.sort(visual);
                for (String name : visual) {
                    titles.add(activity.getString(R.string.notice_visual_file, name));
                    files.add(VISUAL_NOTICES + "/" + name);
                }
            }
        } catch (IOException unavailable) { /* The engine's own license file below still lists its code. */ }
        titles.add(activity.getString(R.string.notice_visual_file, VISUAL_LEGAL.substring(VISUAL_LEGAL.lastIndexOf('/') + 1)));
        files.add(VISUAL_LEGAL);
        new AlertDialog.Builder(activity).setTitle(R.string.notices_title)
                .setItems(titles.toArray(new String[0]), (dialog, index) ->
                        open(activity, titles.get(index), files.get(index)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void open(Activity activity, String title, String file) {
        TextView text = new TextView(activity);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding / 2, padding, padding);
        text.setTextIsSelectable(true);
        if (file == null) text.setText(R.string.notice_sources_body);
        else {
            try {
                text.setText(read(activity, file));
                // License texts stay in their original English, laid out left to right.
                text.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
                text.setTextDirection(View.TEXT_DIRECTION_LTR);
                text.setTypeface(Typeface.MONOSPACE);
                text.setTextSize(12);
            } catch (IOException | RuntimeException unavailable) {
                text.setText(R.string.notices_unavailable);
            }
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity).setTitle(title).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private static String read(Activity activity, String file) throws IOException {
        try (InputStream input = activity.getAssets().open(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            for (int read; (read = input.read(buffer)) > 0; ) bytes.write(buffer, 0, read);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private Notices() { }
}
