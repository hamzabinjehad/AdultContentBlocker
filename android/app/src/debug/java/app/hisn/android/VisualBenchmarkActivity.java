package app.hisn.android;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;

/** Local, supplied files only. No capture, synthetic verdicts, VPN, or policy changes. */
public final class VisualBenchmarkActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final JSONArray results = new JSONArray();
    private final JSONObject report = new JSONObject();
    private VisualClassifier classifier;
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
            String backend = getIntent().getStringExtra("backend");
            if (backend == null) backend = "auto";
            if (!"auto".equals(backend) && !"cpu".equals(backend) && !"webgl".equals(backend)) throw new IllegalArgumentException("Unsupported backend");
            directory = new File(getFilesDir(), "visual_benchmark");
            byte[] manifestBytes = Files.readAllBytes(new File(directory, "manifest.json").toPath());
            if (manifestBytes.length > 131072) throw new IllegalArgumentException("Manifest too large");
            JSONObject manifest = new JSONObject(new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8));
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
            MessageDigest engineDigest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = getAssets().open("visual/engine.bundle.js")) {
                byte[] chunk = new byte[32768];
                int count;
                while ((count = input.read(chunk)) != -1) engineDigest.update(chunk, 0, count);
            }
            String actualEngine = digestText(engineDigest.digest());
            if (!actualEngine.equals(manifest.getString("engine_sha256"))) throw new IllegalArgumentException("Engine digest mismatch");
            started = SystemClock.elapsedRealtime();
            report.put("schema", 1).put("manifest_sha256", hex(manifestBytes))
                    .put("version", BuildConfig.VERSION_NAME).put("model_engine", "bundled NSFWJS MobileNetV2 / tfjs")
                    .put("requested_backend", backend)
                    .put("engine_sha256", actualEngine)
                    .put("device", android.os.Build.MODEL).put("android_sdk", android.os.Build.VERSION.SDK_INT)
                    .put("webview_version", android.webkit.WebView.getCurrentWebViewPackage() == null ? JSONObject.NULL
                            : android.webkit.WebView.getCurrentWebViewPackage().versionName)
                    .put("interval_ms", interval).put("repeats", repeats).put("case_count", cases.length())
                    .put("before", resources()).put("state", "loading");
            save();
            status.setText("Loading local visual model. Test inputs stay on this device; no screenshots are taken.");
            classifier = new VisualClassifier(this, backend, verdict -> {
                try {
                    if (!"ready".equals(verdict)) { finishReport("failed", "model unavailable"); return; }
                    loadedAt = SystemClock.elapsedRealtime();
                    report.put("actual_backend", classifier.actualBackend());
                    report.put("model_load_ms", loadedAt - started);
                    next();
                } catch (Exception failure) { finishReport("failed", failure.getClass().getSimpleName()); }
            });
        } catch (Exception failure) { finishReport("failed", failure.getClass().getSimpleName()); }
    }

    private void next() throws Exception {
        if (complete) return;
        if (index == cases.length() * repeats) { finishReport("complete", null); return; }
        JSONObject item = cases.getJSONObject(index % cases.length());
        File image = new File(directory, item.getString("id") + ".png");
        if (image.length() < 1 || image.length() > 2097152) throw new IllegalArgumentException("Image size rejected");
        byte[] raw = Files.readAllBytes(image.toPath());
        if (!hex(raw).equals(item.getString("sha256"))) throw new IllegalArgumentException("Image digest mismatch");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 384 || bounds.outHeight > 384)
            throw new IllegalArgumentException("Image dimensions rejected");
        Bitmap frame = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        if (frame == null) throw new IllegalArgumentException("Image decode failed");
        status.setText("On-device visual benchmark\n\nSample " + (index + 1) + " / " + (cases.length() * repeats)
                + "\n\nOnly supplied test files are being processed. No accessibility or screen capture is used.");
        long frameStart = SystemClock.elapsedRealtimeNanos();
        classifier.inspect(frame, verdict -> {
            try {
                double elapsed = (SystemClock.elapsedRealtimeNanos() - frameStart) / 1000000.0;
                results.put(new JSONObject().put("id", item.getString("id")).put("repeat", index / cases.length())
                        .put("expected", item.getString("expected")).put("actual", verdict).put("roundtrip_ms", elapsed));
                index++;
                report.put("state", "running").put("results", results);
                if (index % cases.length() == 0) report.put("latest_resources", resources());
                save();
                handler.postDelayed(() -> {
                    try { next(); } catch (Exception failure) { finishReport("failed", failure.getClass().getSimpleName()); }
                }, Math.max(0, interval - (long) elapsed));
            } catch (Exception failure) { finishReport("failed", failure.getClass().getSimpleName()); }
        });
        frame.recycle();
    }

    private JSONObject resources() throws Exception {
        Debug.MemoryInfo memory = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memory);
        JSONObject value = new JSONObject().put("app_process_pss_kb_excludes_renderer", memory.getTotalPss())
                .put("app_process_cpu_ms_excludes_renderer", android.os.Process.getElapsedCpuTime())
                .put("thermal_status", android.os.Build.VERSION.SDK_INT >= 29
                        ? getSystemService(PowerManager.class).getCurrentThermalStatus() : JSONObject.NULL);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) value.put("plugged", battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1))
                .put("battery_level", battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1))
                .put("battery_temperature_tenths_c", battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
        return value;
    }

    private void finishReport(String state, String error) {
        if (complete) return;
        complete = true;
        handler.removeCallbacksAndMessages(null);
        if (classifier != null) classifier.close();
        try {
            report.put("state", state).put("results", results).put("elapsed_ms", SystemClock.elapsedRealtime() - started)
                    .put("after", resources()).put("error", error == null ? JSONObject.NULL : error)
                    .put("limits", "Classifier roundtrip only; app-process memory/CPU exclude WebView renderer. Charging/other apps confound battery. No battery-life or accuracy guarantee.");
            save();
        } catch (Exception ignored) { status.setText("FAIL: could not save benchmark report"); return; }
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText("Benchmark " + state + ".\n\nProcessed " + results.length()
                + " supplied test samples. No screenshots were taken or saved.\n\n" + (error == null ? "Results are ready for review." : error));
    }

    private void save() throws Exception {
        File temporary = new File(getFilesDir(), "visual_benchmark_result.tmp");
        Files.write(temporary.toPath(), report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.move(temporary.toPath(), new File(getFilesDir(), "visual_benchmark_result.json").toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String hex(byte[] bytes) throws Exception {
        return digestText(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String digestText(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }

    @Override protected void onDestroy() {
        if (!complete) finishReport("cancelled", "activity destroyed");
        if (classifier != null) classifier.close();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
