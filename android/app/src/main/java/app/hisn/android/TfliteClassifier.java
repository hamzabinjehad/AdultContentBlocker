package app.hisn.android;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Native TFLite copy of the bundled NSFWJS MobileNetV2, adult inspection only.
 *
 * <p>The model is derived from the same pinned weights as the WebView engine and uses the same
 * 0.70/0.85 Porn/Hentai thresholds and the same centred near-miss second look. Preprocessing is NSFWJS's own bilinear 224×224 with
 * alignCorners and 0..1 normalization. A tall or wide screen is stretched by that resize, so a block
 * must also be supported by a second view of the same region that keeps its shape. The interpreter and preprocessing run on one worker; the
 * callback returns to the main thread. No pixels are retained after the frame is processed.</p>
 */
final class TfliteClassifier implements VisualRuntime {
    static final int SIZE = 224;
    /** Scores this far over the block threshold act without a second capture. */
    static final float STRONG = 0.90f, STRONG_SUM = 0.95f;
    /** region.js NEAR_PORN/NEAR_HENTAI, NEAR_SUM and CROP_FRACTION. */
    static final float NEAR = 0.45f, NEAR_SUM = 0.60f, CROP_FRACTION = 0.68f;
    /** region.js AGREE_SUM: Porn + Hentai the shape-keeping view needs before a block stands. */
    static final float AGREE_SUM = 0.30f;
    static final String MODEL_SHA256 = "01215075f6e759bac8931310ed95665295bd662eff547f27a33982bcf2cb4d46";
    private static final String BACKEND = "tflite-cpu";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hisn-tflite");
        thread.setDaemon(true);
        return thread;
    });
    private final Context context;
    private final Consumer<String> loaded;
    /** One 224×224 float input buffer, reused on the single worker and zeroed after each frame. */
    private final ByteBuffer input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder());
    private final FloatBuffer inputFloats = input.asFloatBuffer();
    private final int[] sourceX = new int[SIZE];
    private final float[] fractionX = new float[SIZE];
    private static final float[] ZEROS = new float[SIZE * SIZE * 3];
    private final float[][] output = new float[1][5];
    private volatile Interpreter interpreter;
    private volatile boolean ready, closed;

    TfliteClassifier(Context context, Consumer<String> loaded) {
        this.context = context.getApplicationContext() == null ? context : context.getApplicationContext();
        this.loaded = loaded;
        worker.execute(this::load);
    }

    private void load() {
        try {
            // The model is stored uncompressed, so mapping avoids a 9.8 MB copy and lets the
            // interpreter read the weights from the APK page cache.
            ByteBuffer weights = map();
            if (!MODEL_SHA256.equals(sha256(weights))) throw new IOException("TFLite model digest mismatch");
            weights.rewind();
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            Interpreter created = new Interpreter(weights, options);
            if (closed) { created.close(); return; }
            interpreter = created;
            ready = true;
            main.post(() -> { if (!closed) loaded.accept("ready"); });
        } catch (Throwable unavailable) {
            main.post(() -> { if (!closed) loaded.accept("unknown"); });
        }
    }

    private ByteBuffer map() throws IOException {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd("nsfw_mobilenet_v2.tflite");
             FileInputStream stream = descriptor.createInputStream();
             FileChannel channel = stream.getChannel()) {
            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY,
                    descriptor.getStartOffset(), descriptor.getDeclaredLength());
            mapped.load();
            return mapped;
        } catch (IOException | RuntimeException unmappable) {
            // A compressed asset or an unusual storage backend cannot be mapped; read it instead.
            byte[] model = read(context.getAssets().open("nsfw_mobilenet_v2.tflite"));
            ByteBuffer copy = ByteBuffer.allocateDirect(model.length).order(ByteOrder.nativeOrder());
            copy.put(model);
            copy.rewind();
            return copy;
        }
    }

    @Override public void inspect(Bitmap frame, Consumer<VisualResult> result) {
        inspect(frame, null, result);
    }

    /** screen is the uncropped input when frame is a picture isolated by the guard. */
    void inspect(Bitmap frame, Bitmap screen, Consumer<VisualResult> result) {
        if (!ready || closed) { result.accept(new VisualResult("unknown", false)); return; }
        int width = frame.getWidth(), height = frame.getHeight();
        int[] source = pixels(frame);
        int[] context = screen == null ? null : pixels(screen);
        int screenWidth = screen == null ? 0 : screen.getWidth();
        int screenHeight = screen == null ? 0 : screen.getHeight();
        worker.execute(() -> {
            VisualResult value;
            try {
                value = classify(source, width, height);
                if (context != null && "block".equals(value.verdict)) {
                    float[] surrounding = scores(context, screenWidth, screenHeight,
                            0, 0, screenWidth, screenHeight, false);
                    if (surrounding == null) value = new VisualResult("unknown", false);
                    else value = new VisualResult(surrounding[3] + surrounding[1] >= AGREE_SUM
                            ? "block" : "allow", false);
                }
            }
            catch (Throwable unavailable) { value = new VisualResult("unknown", false); }
            finally {
                Arrays.fill(source, 0);
                if (context != null) Arrays.fill(context, 0);
                inputFloats.clear();
                inputFloats.put(ZEROS);
            }
            final VisualResult answer = value;
            main.post(() -> { if (!closed) result.accept(answer); });
        });
    }

    private static int[] pixels(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }

    /** Same rule as the WebView path (decisions.js adultDecision and region.js nearMiss): a full
     * frame block, or an uncertain full frame whose centred crop blocks. A crop block is never
     * strong, so the guard always takes a second look before acting on it.
     *
     * <p>The model's square input stretches a 9:20 phone screen to more than twice its width, and
     * ordinary photos (a pet, a cup, a face) then score as explicit. So the region that blocked is
     * looked at once more with its shape kept: a clean second view cancels the block, and only a
     * block both views agree on is strong.</p> */
    private VisualResult classify(int[] source, int width, int height) {
        float[] full = scores(source, width, height, 0, 0, width, height, false);
        if (full == null) return new VisualResult("unknown", false);
        float porn = full[3], hentai = full[1];
        if (blocks(porn, hentai)) {
            float[] shaped = width == height ? full : scores(source, width, height, 0, 0, width, height, true);
            if (shaped == null) return new VisualResult("unknown", false);
            if (shaped[3] + shaped[1] < AGREE_SUM) return new VisualResult("allow", false);
            boolean strong = (porn >= STRONG || hentai >= STRONG || porn + hentai >= STRONG_SUM)
                    && blocks(shaped[3], shaped[1]);
            return new VisualResult("block", false, strong);
        }
        if (porn >= NEAR || hentai >= NEAR || porn + hentai >= NEAR_SUM) {
            int cropWidth = Math.max(1, Math.round(width * CROP_FRACTION));
            int cropHeight = Math.max(1, Math.round(height * CROP_FRACTION));
            int left = (width - cropWidth) / 2, top = (height - cropHeight) / 2;
            float[] crop = scores(source, width, height, left, top, cropWidth, cropHeight, false);
            if (crop != null && blocks(crop[3], crop[1])) {
                float[] shaped = cropWidth == cropHeight ? crop
                        : scores(source, width, height, left, top, cropWidth, cropHeight, true);
                if (shaped != null && shaped[3] + shaped[1] >= AGREE_SUM) return new VisualResult("block", false, false);
            }
        }
        return new VisualResult("allow", false);
    }

    private static boolean blocks(float porn, float hentai) {
        return porn >= 0.70f || hentai >= 0.70f || porn + hentai >= 0.85f;
    }

    /** Model scores (Drawing, Hentai, Neutral, Porn, Sexy) for one region of the frame, or null.
     * With keepShape the region is scaled evenly and centred on mid-grey instead of stretched. */
    private float[] scores(int[] source, int width, int height, int left, int top, int regionWidth,
            int regionHeight, boolean keepShape) {
        int longer = Math.max(regionWidth, regionHeight);
        int drawWidth = keepShape ? Math.max(1, Math.round(SIZE * regionWidth / (float) longer)) : SIZE;
        int drawHeight = keepShape ? Math.max(1, Math.round(SIZE * regionHeight / (float) longer)) : SIZE;
        int offsetX = (SIZE - drawWidth) / 2, offsetY = (SIZE - drawHeight) / 2;
        for (int x = 0; x < drawWidth; x++) {
            float sx = left + (drawWidth < 2 ? 0 : x * (regionWidth - 1) / (drawWidth - 1f));
            sourceX[x] = (int) sx;
            fractionX[x] = sx - sourceX[x];
        }
        inputFloats.clear();
        for (int y = 0; y < SIZE; y++) {
            int dy = y - offsetY;
            boolean paddedRow = dy < 0 || dy >= drawHeight;
            float sy = top + (drawHeight < 2 ? 0 : dy * (regionHeight - 1) / (drawHeight - 1f));
            int y0 = Math.max(top, Math.min((int) sy, height - 1));
            int row0 = y0 * width, row1 = Math.min(y0 + 1, height - 1) * width;
            float fy = Math.max(0, sy - y0);
            for (int x = 0; x < SIZE; x++) {
                int dx = x - offsetX;
                if (paddedRow || dx < 0 || dx >= drawWidth) {
                    inputFloats.put(0.5f);
                    inputFloats.put(0.5f);
                    inputFloats.put(0.5f);
                    continue;
                }
                int x0 = sourceX[dx], x1 = Math.min(x0 + 1, width - 1);
                float fx = fractionX[dx];
                int p00 = source[row0 + x0], p10 = source[row0 + x1];
                int p01 = source[row1 + x0], p11 = source[row1 + x1];
                inputFloats.put(channel(p00, p10, p01, p11, fx, fy, 16));
                inputFloats.put(channel(p00, p10, p01, p11, fx, fy, 8));
                inputFloats.put(channel(p00, p10, p01, p11, fx, fy, 0));
            }
        }
        input.rewind();
        Interpreter current = interpreter;
        if (current == null) return null;
        current.run(input, output);
        return output[0].clone();
    }

    private static float channel(int p00, int p10, int p01, int p11, float fx, float fy, int shift) {
        float top = ((p00 >> shift) & 255) * (1 - fx) + ((p10 >> shift) & 255) * fx;
        float bottom = ((p01 >> shift) & 255) * (1 - fx) + ((p11 >> shift) & 255) * fx;
        return (top * (1 - fy) + bottom * fy) / 255f;
    }

    private static byte[] read(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = source.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        }
    }

    static String sha256(ByteBuffer bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            bytes.rewind();
            StringBuilder text = new StringBuilder();
            for (byte b : digest.digest()) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    @Override public String actualBackend() { return BACKEND; }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        ready = false;
        worker.execute(() -> {
            Interpreter current = interpreter;
            interpreter = null;
            if (current != null) current.close();
        });
        worker.shutdown();
    }
}
