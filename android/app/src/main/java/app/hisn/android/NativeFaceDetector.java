package app.hisn.android;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import app.hisn.core.BlazeFace;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Finds faces natively with MediaPipe's BlazeFace "back" model, the same detector the WebView
 * engine runs through TensorFlow.js, without the WebView, its WASM runtime or the text bridge.
 *
 * <p>The model is {@code face_detection_back.tflite} from google/mediapipe v0.7.12
 * (Apache 2.0), unchanged and pinned by SHA-256. The frame is padded to a centred black square and
 * resized to 256 px (bilinear), as -1..1 RGB floats, as the WebView engine prepares it; decoding
 * is {@link BlazeFace}. Not thread-safe: {@link HybridRuntime} calls it on one worker thread.</p>
 */
final class NativeFaceDetector implements AutoCloseable {
    static final String ASSET = "blazeface_back.tflite";
    static final String MODEL_SHA256 = "e376cf6b168d5ece8a3cedb94acc4eb168a136aede125ccc3d903ef38f5beda8";
    private static final int SIZE = BlazeFace.SIZE;
    private final Interpreter interpreter;
    private final ByteBuffer input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder());
    private final FloatBuffer inputFloats = input.asFloatBuffer();
    private final float[][][] regressors = new float[1][BlazeFace.ANCHORS][BlazeFace.COORDS];
    private final float[][][] logits = new float[1][BlazeFace.ANCHORS][1];
    private final Map<Integer, Object> outputs = new HashMap<>();
    private final float[] flatRegressors = new float[BlazeFace.ANCHORS * BlazeFace.COORDS];
    private final float[] flatLogits = new float[BlazeFace.ANCHORS];
    private final int[] x0 = new int[SIZE], x1 = new int[SIZE];
    private final float[] fx = new float[SIZE];
    private final boolean[] padX = new boolean[SIZE];

    NativeFaceDetector(Context context) throws IOException {
        ByteBuffer weights = map(context);
        if (!MODEL_SHA256.equals(TfliteClassifier.sha256(weights))) throw new IOException("BlazeFace model digest mismatch");
        weights.rewind();
        Interpreter.Options options = new Interpreter.Options();
        // Two threads: the adult model runs at the same time and the phone must stay responsive.
        options.setNumThreads(2);
        Interpreter created = new Interpreter(weights, options);
        try {
            Tensor in = created.getInputTensor(0);
            if (in.dataType() != DataType.FLOAT32 || !Arrays.equals(in.shape(), new int[]{1, SIZE, SIZE, 3})) {
                throw new IOException("BlazeFace input shape");
            }
            if (created.getOutputTensorCount() != 2) throw new IOException("BlazeFace output count");
            // Outputs are told apart by shape, so a reordered export cannot swap boxes and scores.
            for (int i = 0; i < 2; i++) {
                int[] shape = created.getOutputTensor(i).shape();
                if (Arrays.equals(shape, new int[]{1, BlazeFace.ANCHORS, BlazeFace.COORDS})) outputs.put(i, regressors);
                else if (Arrays.equals(shape, new int[]{1, BlazeFace.ANCHORS, 1})) outputs.put(i, logits);
            }
            if (outputs.size() != 2 || !outputs.containsValue(regressors) || !outputs.containsValue(logits)) {
                throw new IOException("BlazeFace output shapes");
            }
        } catch (IOException | RuntimeException wrong) {
            created.close();
            throw wrong;
        }
        interpreter = created;
    }

    private static ByteBuffer map(Context context) throws IOException {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(ASSET);
             FileInputStream stream = descriptor.createInputStream();
             FileChannel channel = stream.getChannel()) {
            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY,
                    descriptor.getStartOffset(), descriptor.getDeclaredLength());
            mapped.load();
            return mapped;
        }
    }

    /** Face boxes in the width x height ARGB frame: normalized x, y, w, h repeated, at most ten.
     * With closer, a long frame is also looked at in square slices (see {@link BlazeFace#regions}),
     * which finds smaller faces at one model run per slice. Reads pixels; does not keep them. */
    float[] detect(int[] pixels, int width, int height, boolean closer) {
        if (pixels == null || width < 1 || height < 1 || pixels.length < width * height) {
            throw new IllegalArgumentException("frame");
        }
        try {
            java.util.List<float[]> found = new java.util.ArrayList<>();
            for (BlazeFace.Region region : BlazeFace.regions(width, height, closer)) {
                fill(pixels, width, height, region);
                input.rewind();
                interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);
                for (int a = 0; a < BlazeFace.ANCHORS; a++) {
                    System.arraycopy(regressors[0][a], 0, flatRegressors, a * BlazeFace.COORDS, BlazeFace.COORDS);
                    flatLogits[a] = logits[0][a][0];
                }
                found.addAll(BlazeFace.inFrame(BlazeFace.detect(flatRegressors, flatLogits, BlazeFace.MIN_SCORE), region));
            }
            return BlazeFace.merge(found, width, height, BlazeFace.MIN_SCORE);
        } finally {
            // Nothing derived from the frame outlives the call.
            inputFloats.clear();
            for (int i = 0; i < SIZE * SIZE * 3; i++) inputFloats.put(0f);
            Arrays.fill(flatRegressors, 0);
        }
    }

    /** Bilinear resize of one square region of the frame to SIZE, black outside the frame, as -1..1. */
    private void fill(int[] pixels, int width, int height, BlazeFace.Region region) {
        for (int x = 0; x < SIZE; x++) {
            float sx = region.sourceX(x);
            padX[x] = sx < -0.5f || sx > width - 0.5f;
            float clamped = Math.max(0f, Math.min(width - 1f, sx));
            x0[x] = (int) clamped;
            x1[x] = Math.min(x0[x] + 1, width - 1);
            fx[x] = clamped - x0[x];
        }
        inputFloats.clear();
        for (int y = 0; y < SIZE; y++) {
            float sy = region.sourceY(y);
            boolean padY = sy < -0.5f || sy > height - 0.5f;
            float clamped = Math.max(0f, Math.min(height - 1f, sy));
            int y0 = (int) clamped, y1 = Math.min(y0 + 1, height - 1);
            float fy = clamped - y0;
            int row0 = y0 * width, row1 = y1 * width;
            for (int x = 0; x < SIZE; x++) {
                if (padY || padX[x]) {
                    inputFloats.put(-1f); inputFloats.put(-1f); inputFloats.put(-1f);
                    continue;
                }
                int p00 = pixels[row0 + x0[x]], p10 = pixels[row0 + x1[x]];
                int p01 = pixels[row1 + x0[x]], p11 = pixels[row1 + x1[x]];
                for (int shift = 16; shift >= 0; shift -= 8) {
                    float top = ((p00 >> shift) & 255) * (1 - fx[x]) + ((p10 >> shift) & 255) * fx[x];
                    float bottom = ((p01 >> shift) & 255) * (1 - fx[x]) + ((p11 >> shift) & 255) * fx[x];
                    inputFloats.put((top * (1 - fy) + bottom * fy) / 127.5f - 1f);
                }
            }
        }
    }

    @Override public void close() { interpreter.close(); }
}
