package app.hisn.android;

import android.graphics.Bitmap;
import android.media.FaceDetector;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A fast, model-free face-presence pre-check for "All faces" mode.
 *
 * <p>It never decides the final result: the packaged BlazeFace/gender engine still runs and its
 * answer wins. This only lets the cover appear as soon as a frontal face is visible, instead of
 * waiting for the WebView, adult model and face model chain. A false positive shows the cover a
 * little early and is removed by the normal clean result; a miss changes nothing. The bitmap is
 * transient and recycled on the worker.</p>
 */
final class PersonFastPresence implements AutoCloseable {
    private static final int EDGE = 256;
    private static final float MIN_CONFIDENCE = 0.30f;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hisn-face-presence");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean closed;

    interface Result { void found(boolean face); }

    void check(Bitmap frame, Result result) {
        if (closed || frame == null || !running.compareAndSet(false, true)) {
            result.found(false);
            return;
        }
        Bitmap scaled = null;
        try {
            double scale = Math.min(1.0, EDGE / (double) Math.max(frame.getWidth(), frame.getHeight()));
            // Android's FaceDetector requires an even input width. Portrait captures often
            // scale to an odd width; passing one silently disabled this early cover path.
            int width = Math.max(2, ((int) (frame.getWidth() * scale)) & ~1);
            int height = Math.max(2, (int) (frame.getHeight() * scale));
            scaled = Bitmap.createScaledBitmap(frame, width, height, false);
            final Bitmap input = scaled.copy(Bitmap.Config.RGB_565, false);
            if (input == null) { running.set(false); result.found(false); return; }
            worker.execute(() -> {
                boolean found = false;
                try {
                    FaceDetector detector = new FaceDetector(input.getWidth(), input.getHeight(), 1);
                    FaceDetector.Face[] faces = new FaceDetector.Face[1];
                    int count = detector.findFaces(input, faces);
                    found = count > 0 && faces[0] != null && faces[0].confidence() >= MIN_CONFIDENCE;
                } catch (Throwable unavailable) {
                    // Linkage or vendor failure is a miss; the full engine still decides.
                } finally {
                    input.recycle();
                }
                final boolean answer = found;
                running.set(false);
                main.post(() -> { if (!closed) result.found(answer); });
            });
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            running.set(false);
            result.found(false);
        } finally {
            if (scaled != null) scaled.recycle();
        }
    }

    @Override public void close() {
        closed = true;
        running.set(false);
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
