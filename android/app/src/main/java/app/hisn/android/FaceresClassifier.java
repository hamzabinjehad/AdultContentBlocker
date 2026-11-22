package app.hisn.android;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/** Native FaceRes women/men estimate for one face box, the WebView engine's FaceRes run natively.
 *
 * <p>The model is the gender head of Human's FaceRes (HSE-asavchenko/HSE_FaceRec_tf, Apache 2.0),
 * converted by android/visual-eval/convert-faceres.py from the pinned TensorFlow.js files without
 * changing a weight. Preprocessing repeats Human's: the face box is cut from the square-padded
 * frame and resized to the 256 px detector size (tf.image.cropAndResize, bilinear, zero outside
 * the frame), then resized to 224 px (resizeBilinear, no corner alignment), as 0..255 RGB floats.
 * Not thread-safe: {@link HybridRuntime} calls it on one worker thread.</p>
 */
final class FaceresClassifier implements AutoCloseable {
    static final String ASSET = "faceres_gender.tflite";
    static final String MODEL_SHA256 = "7989d229160cf19fa9773d47f658ae0b3bf39bb776c74f1e2a3a0107c580ff86";
    private static final int DETECTOR = 256, SIZE = 224;
    private final Interpreter interpreter;
    private final ByteBuffer input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder());
    private final FloatBuffer inputFloats = input.asFloatBuffer();
    private final float[] crop = new float[DETECTOR * DETECTOR * 3];
    private final float[] resized = new float[SIZE * SIZE * 3];
    private final int[] leftColumns = new int[DETECTOR], rightColumns = new int[DETECTOR];
    private final float[] columnWeights = new float[DETECTOR];
    private final float[][] output = new float[1][1];

    FaceresClassifier(Context context) throws IOException {
        ByteBuffer weights = map(context);
        if (!MODEL_SHA256.equals(TfliteClassifier.sha256(weights))) throw new IOException("FaceRes model digest mismatch");
        weights.rewind();
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(weights, options);
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

    /** Probability that the face in the box is a man. pixels is the width x height ARGB frame
     * the face engine saw; the box is in that frame's normalized coordinates (0..1). */
    float maleProbability(int[] pixels, int width, int height, float x, float y, float w, float h) {
        int edge = Math.max(width, height);
        int padX = (edge - width) / 2, padY = (edge - height) / 2;
        // The engine pads the frame to a square before detection; the box lives in that square.
        float y1 = (padY + y * height) / edge, y2 = (padY + (y + h) * height) / edge;
        float x1 = (padX + x * width) / edge, x2 = (padX + (x + w) * width) / edge;
        try {
            cropFace(pixels, width, height, edge, padX, padY, x1, y1, x2, y2);
            resizeCrop();
            inputFloats.rewind();
            inputFloats.put(resized);
            input.rewind();
            interpreter.run(input, output);
            return output[0][0];
        } finally {
            Arrays.fill(crop, 0);
            Arrays.fill(resized, 0);
            inputFloats.rewind();
            inputFloats.put(resized);
        }
    }

    /** tf.image.cropAndResize to DETECTOR x DETECTOR, with zero extrapolation. */
    private void cropFace(int[] pixels, int width, int height, int edge, int padX, int padY,
            float x1, float y1, float x2, float y2) {
        // Each row uses the same source columns and interpolation weights.
        for (int cx = 0; cx < DETECTOR; cx++) {
            float sx = x1 * (edge - 1) + cx * (x2 - x1) * (edge - 1) / (DETECTOR - 1f);
            if (sx < 0 || sx > edge - 1) {
                leftColumns[cx] = rightColumns[cx] = -1;
                columnWeights[cx] = 0;
            } else {
                int left = (int) sx;
                leftColumns[cx] = paddedIndex(left - padX, width);
                rightColumns[cx] = paddedIndex(Math.min(left + 1, edge - 1) - padX, width);
                columnWeights[cx] = sx - left;
            }
        }
        for (int cy = 0; cy < DETECTOR; cy++) {
            float sy = y1 * (edge - 1) + cy * (y2 - y1) * (edge - 1) / (DETECTOR - 1f);
            // The crop is already zero in extrapolated rows.
            if (sy < 0 || sy > edge - 1) continue;
            int top = (int) sy;
            int topRow = paddedIndex(top - padY, height) * width;
            int bottomRow = paddedIndex(Math.min(top + 1, edge - 1) - padY, height) * width;
            float fy = sy - top;
            for (int cx = 0; cx < DETECTOR; cx++) {
                int left = leftColumns[cx], right = rightColumns[cx];
                int topLeft = sample(pixels, topRow, left), topRight = sample(pixels, topRow, right);
                int bottomLeft = sample(pixels, bottomRow, left), bottomRight = sample(pixels, bottomRow, right);
                float fx = columnWeights[cx];
                int at = (cy * DETECTOR + cx) * 3;
                crop[at] = interpolate(topLeft, topRight, bottomLeft, bottomRight, 16, fx, fy);
                crop[at + 1] = interpolate(topLeft, topRight, bottomLeft, bottomRight, 8, fx, fy);
                crop[at + 2] = interpolate(topLeft, topRight, bottomLeft, bottomRight, 0, fx, fy);
            }
        }
    }

    /** resizeBilinear without corner alignment or half-pixel centres. */
    private void resizeCrop() {
        float scale = DETECTOR / (float) SIZE;
        int index = 0;
        for (int oy = 0; oy < SIZE; oy++) {
            float sy = oy * scale;
            int top = (int) sy, bottom = Math.min(top + 1, DETECTOR - 1);
            float fy = sy - top;
            for (int ox = 0; ox < SIZE; ox++) {
                float sx = ox * scale;
                int left = (int) sx, right = Math.min(left + 1, DETECTOR - 1);
                float fx = sx - left;
                for (int c = 0; c < 3; c++) {
                    float a = crop[(top * DETECTOR + left) * 3 + c], b = crop[(top * DETECTOR + right) * 3 + c];
                    float d = crop[(bottom * DETECTOR + left) * 3 + c], e = crop[(bottom * DETECTOR + right) * 3 + c];
                    float upper = a + (b - a) * fx, lower = d + (e - d) * fx;
                    resized[index++] = upper + (lower - upper) * fy;
                }
            }
        }
    }

    private static int paddedIndex(int index, int size) {
        return index >= 0 && index < size ? index : -1;
    }

    private static int sample(int[] pixels, int row, int column) {
        return row >= 0 && column >= 0 ? pixels[row + column] : 0;
    }

    private static float interpolate(int topLeft, int topRight, int bottomLeft, int bottomRight,
            int shift, float fx, float fy) {
        float a = (topLeft >> shift) & 255, b = (topRight >> shift) & 255;
        float d = (bottomLeft >> shift) & 255, e = (bottomRight >> shift) & 255;
        float upper = a + (b - a) * fx, lower = d + (e - d) * fx;
        return upper + (lower - upper) * fy;
    }

    @Override public void close() { interpreter.close(); }
}
