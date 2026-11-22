package app.hisn.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Debug-only accuracy run: the production image check on supplied screen-sized files.
 *
 * <p>Each file uses VisualGuard's capture preparation: a scaled full screen, an optional
 * plain-background picture crop for the native adult check, and a larger screen for the face
 * engine when people cover is enabled. The manifest carries only ids
 * and digests; labels stay on the computer that scores the run. Each file is deleted as soon as it
 * has been judged, and the report holds verdicts and timings, never pixels.</p>
 */
public final class AccuracyRunActivity extends Activity {
    static final int MAX_CASES = 5000;
    static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    static final int MAX_EDGE = 4096;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final JSONArray results = new JSONArray();
    private final JSONObject report = new JSONObject();
    private VisualRuntime classifier;
    private TextView status;
    private JSONArray cases;
    private File directory;
    private String people;
    private int index;
    private long started;
    private boolean complete;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(32, 96, 32, 32);
        setContentView(status);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            if (!"app.hisn.android.visualcheck".equals(getPackageName()))
                throw new IllegalStateException("Isolated benchmark package required");
            directory = new File(getFilesDir(), "visual_accuracy");
            JSONObject manifest = new JSONObject(new String(
                    readFile(new File(directory, "manifest.json"), 4 * 1024 * 1024), StandardCharsets.UTF_8));
            if (manifest.getInt("schema") != 1) throw new IllegalArgumentException("Unknown schema");
            String mode = manifest.getString("mode");
            if ("adult".equals(mode)) people = PersonBlur.OFF;
            else if (mode.startsWith("people-") && PersonBlur.on(mode.substring(7))) people = mode.substring(7);
            else throw new IllegalArgumentException("Unknown mode");
            String requested = manifest.optString("appearance");
            String appearance = "native".equals(requested) || "faceres".equals(requested) ? requested : "oarriaga";
            boolean evaluate = manifest.optBoolean("evaluate", false);
            cases = manifest.getJSONArray("cases");
            if (cases.length() < 1 || cases.length() > MAX_CASES) throw new IllegalArgumentException("Run exceeds bounds");
            for (int i = 0; i < cases.length(); i++) {
                JSONObject item = cases.getJSONObject(i);
                if (!item.getString("id").matches("[a-zA-Z0-9_-]{1,96}")
                        || !item.getString("sha256").matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid case");
            }
            started = SystemClock.elapsedRealtime();
            report.put("schema", 1).put("version", BuildConfig.VERSION_NAME).put("mode", mode)
                    .put("appearance", appearance).put("evaluate", evaluate)
                    .put("device", android.os.Build.MODEL).put("android_sdk", android.os.Build.VERSION.SDK_INT)
                    .put("frame_edge", PersonBlur.on(people) ? VisualGuard.PERSON_FRAME_EDGE : VisualGuard.FRAME_EDGE)
                    .put("case_count", cases.length()).put("state", "loading");
            save();
            status.setText("Loading the image check. Only supplied test files; no screenshots.");
            java.util.function.Consumer<String> loaded = verdict -> {
                try {
                    if (!"ready".equals(verdict)) { finishReport("failed", "model unavailable"); return; }
                    report.put("actual_backend", classifier.actualBackend())
                            .put("model_load_ms", SystemClock.elapsedRealtime() - started).put("state", "running");
                    save();
                    next();
                } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
            };
            // The same runtime choice as VisualGuard: native adult check and an optional face engine.
            classifier = PersonBlur.on(people)
                    ? new HybridRuntime(this, people, appearance, evaluate, loaded)
                    : new TfliteClassifier(this, loaded);
        } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
    }

    private void next() throws Exception {
        if (complete) return;
        if (index == cases.length()) { finishReport("complete", null); return; }
        JSONObject item = cases.getJSONObject(index);
        String id = item.getString("id");
        File image = new File(directory, id + ".png");
        byte[] raw = readFile(image, MAX_FILE_BYTES);
        if (!hex(raw).equals(item.getString("sha256"))) throw new IllegalArgumentException("Image digest mismatch");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        if (bounds.outWidth < 16 || bounds.outHeight < 16 || bounds.outWidth > MAX_EDGE || bounds.outHeight > MAX_EDGE)
            throw new IllegalArgumentException("Image dimensions rejected");
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap copy = BitmapFactory.decodeByteArray(raw, 0, raw.length, decode);
        java.util.Arrays.fill(raw, (byte) 0);
        if (copy == null) throw new IllegalArgumentException("Image decode failed");
        if (!image.delete()) image.deleteOnExit();
        if (index % 10 == 0) status.setText("Accuracy run\n\n" + (index + 1) + " / " + cases.length());
        Bitmap screen = null, crop = null, faces = null;
        try {
            screen = VisualGuard.scaleFrame(copy, VisualGuard.FRAME_EDGE);
            crop = VisualGuard.adultPictureFrame(copy);
            if (PersonBlur.on(people)) faces = VisualGuard.scaleFrame(copy, VisualGuard.PERSON_FRAME_EDGE);
            inspect(id, screen, crop, faces);
        } finally {
            if (faces != null && faces != copy && faces != screen && faces != crop) faces.recycle();
            if (crop != null && crop != copy && crop != screen) crop.recycle();
            if (screen != null && screen != copy) screen.recycle();
            copy.recycle();
        }
    }

    private void inspect(String id, Bitmap screen, Bitmap crop, Bitmap faces) {
        long frameStart = SystemClock.elapsedRealtimeNanos();
        boolean cropped = crop != null;
        java.util.function.Consumer<VisualResult> judged = verdict -> {
            try {
                double elapsed = (SystemClock.elapsedRealtimeNanos() - frameStart) / 1_000_000.0;
                results.put(new JSONObject().put("id", id).put("actual", verdict.verdict)
                        .put("strong", verdict.strong).put("person", verdict.personBlur)
                        .put("cropped", cropped).put("roundtrip_ms", Math.round(elapsed * 10) / 10.0)
                        .put("faces", verdict.detail == null ? JSONObject.NULL : new JSONArray(verdict.detail)));
                index++;
                if (index % 25 == 0) { report.put("results", results); save(); }
                handler.post(() -> {
                    try { next(); } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
                });
            } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
        };
        Bitmap primary = cropped ? crop : screen;
        Bitmap context = cropped ? screen : null;
        if (faces == null) ((TfliteClassifier) classifier).inspect(primary, context, judged);
        else ((HybridRuntime) classifier).inspect(primary, context, faces, judged);
    }

    private void finishReport(String state, String error) {
        if (complete) return;
        complete = true;
        handler.removeCallbacksAndMessages(null);
        if (classifier != null) classifier.close();
        try {
            report.put("state", state).put("results", results).put("elapsed_ms", SystemClock.elapsedRealtime() - started)
                    .put("error", error == null ? JSONObject.NULL : error)
                    .put("limits", "Production preparation and runtime on supplied still files; no capture, "
                            + "no second capture, no battery claim.");
            save();
        } catch (Exception ignored) { status.setText("FAIL: could not save report"); return; }
        status.setText("Accuracy run " + state + ".\n\nJudged " + results.length() + " supplied files."
                + (error == null ? "" : "\n" + error));
    }

    private void save() throws Exception {
        byte[] bytes = report.toString().getBytes(StandardCharsets.UTF_8);
        File temporary = new File(getFilesDir(), "visual_accuracy_result.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        }
        File target = new File(getFilesDir(), "visual_accuracy_result.json");
        if (!temporary.renameTo(target)) {
            try (FileOutputStream output = new FileOutputStream(target)) { output.write(bytes); }
            if (!temporary.delete()) temporary.deleteOnExit();
        }
    }

    private static byte[] readFile(File file, int limit) throws IOException {
        long length = file.length();
        if (length < 1 || length > limit) throw new IOException("File size rejected");
        byte[] bytes = new byte[(int) length];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) throw new IOException("Unexpected end of file");
                offset += read;
            }
        }
        return bytes;
    }

    private static String hex(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        }
        return text.toString();
    }

    @Override protected void onDestroy() {
        if (!complete) finishReport("cancelled", "activity destroyed");
        if (classifier != null) classifier.close();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
