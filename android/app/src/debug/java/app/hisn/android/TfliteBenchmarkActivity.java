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

/** Debug-only native TFLite benchmark on supplied, labelled files. No capture or policy change. */
public final class TfliteBenchmarkActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final JSONArray results = new JSONArray();
    private final JSONObject report = new JSONObject();
    private TfliteClassifier classifier;
    private TextView status;
    private JSONArray cases;
    private File directory;
    private int repeats, interval, index;
    private long started, loadedAt;
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
            directory = new File(getFilesDir(), "visual_benchmark");
            JSONObject manifest = new JSONObject(new String(
                    readFile(new File(directory, "manifest.json"), 1048576), StandardCharsets.UTF_8));
            if (manifest.getInt("schema") != 1) throw new IllegalArgumentException("Unknown schema");
            cases = manifest.getJSONArray("cases");
            repeats = manifest.getInt("repeats");
            interval = manifest.getInt("interval_ms");
            if (cases.length() < 1 || cases.length() > 64 || repeats < 1 || repeats > 10
                    || cases.length() * repeats > 240 || interval < 1000 || interval > 5000)
                throw new IllegalArgumentException("Benchmark exceeds bounds");
            for (int i = 0; i < cases.length(); i++) {
                JSONObject item = cases.getJSONObject(i);
                if (!item.getString("id").matches("[a-zA-Z0-9_-]{1,64}")
                        || !("allow".equals(item.getString("expected")) || "block".equals(item.getString("expected")))
                        || !item.getString("sha256").matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid labelled case");
            }
            started = SystemClock.elapsedRealtime();
            report.put("schema", 1).put("version", BuildConfig.VERSION_NAME)
                    .put("engine", "native TFLite float32 MobileNetV2").put("requested_backend", "cpu")
                    .put("model_sha256", TfliteClassifier.MODEL_SHA256)
                    .put("device", android.os.Build.MODEL).put("android_sdk", android.os.Build.VERSION.SDK_INT)
                    .put("interval_ms", interval).put("repeats", repeats).put("case_count", cases.length())
                    .put("state", "loading");
            save();
            status.setText("Loading native TFLite model. Only supplied test files; no screenshots.");
            classifier = new TfliteClassifier(this, verdict -> {
                try {
                    if (!"ready".equals(verdict)) { finishReport("failed", "model unavailable"); return; }
                    loadedAt = SystemClock.elapsedRealtime();
                    report.put("actual_backend", classifier.actualBackend())
                            .put("model_load_ms", loadedAt - started).put("state", "running");
                    save();
                    next();
                } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
            });
        } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
    }

    private void next() throws Exception {
        if (complete) return;
        if (index == cases.length() * repeats) { finishReport("complete", null); return; }
        JSONObject item = cases.getJSONObject(index % cases.length());
        File image = new File(directory, item.getString("id") + ".png");
        if (image.length() < 1 || image.length() > 2097152) throw new IllegalArgumentException("Image size rejected");
        byte[] raw = readFile(image, 2097152);
        if (!hex(raw).equals(item.getString("sha256"))) throw new IllegalArgumentException("Image digest mismatch");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 384 || bounds.outHeight > 384)
            throw new IllegalArgumentException("Image dimensions rejected");
        Bitmap frame = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        if (frame == null) throw new IllegalArgumentException("Image decode failed");
        status.setText("Native TFLite benchmark\n\nSample " + (index + 1) + " / " + (cases.length() * repeats));
        long frameStart = SystemClock.elapsedRealtimeNanos();
        final JSONObject itemForResult = item;
        classifier.inspect(frame, verdict -> {
            try {
                double elapsed = (SystemClock.elapsedRealtimeNanos() - frameStart) / 1_000_000.0;
                results.put(new JSONObject().put("id", itemForResult.getString("id"))
                        .put("repeat", index / cases.length()).put("expected", itemForResult.getString("expected"))
                        .put("actual", verdict).put("roundtrip_ms", elapsed));
                index++;
                report.put("results", results);
                save();
                handler.postDelayed(() -> {
                    try { next(); } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
                }, Math.max(0, interval - (long) elapsed));
            } catch (Throwable failure) { finishReport("failed", failure.getClass().getSimpleName()); }
        });
        frame.recycle();
    }

    private void finishReport(String state, String error) {
        if (complete) return;
        complete = true;
        handler.removeCallbacksAndMessages(null);
        if (classifier != null) classifier.close();
        try {
            report.put("state", state).put("results", results).put("elapsed_ms", SystemClock.elapsedRealtime() - started)
                    .put("error", error == null ? JSONObject.NULL : error)
                    .put("limits", "Native TFLite roundtrip only; debug APK, supplied files, no capture. "
                            + "No accuracy or battery claim.");
            save();
        } catch (Exception ignored) { status.setText("FAIL: could not save report"); return; }
        status.setText("Native benchmark " + state + ".\n\nProcessed " + results.length() + " supplied samples."
                + (error == null ? "" : "\n" + error));
    }

    private void save() throws Exception {
        byte[] bytes = report.toString(2).getBytes(StandardCharsets.UTF_8);
        File temporary = new File(getFilesDir(), "tflite_benchmark_result.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (!temporary.renameTo(new File(getFilesDir(), "tflite_benchmark_result.json"))) {
            // A target that cannot be replaced by rename still receives the report.
            try (FileOutputStream output = new FileOutputStream(
                    new File(getFilesDir(), "tflite_benchmark_result.json"))) {
                output.write(bytes);
            }
            if (!temporary.delete()) temporary.deleteOnExit();
        }
    }

    /** Bounded read that works on Android 6, where java.nio.file.Files is not available. */
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
