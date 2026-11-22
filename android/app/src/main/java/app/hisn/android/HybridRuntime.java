package app.hisn.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** People mode with the adult check kept native: TFLite judges adult content while the WebView
 * engine, loaded faces-only, looks for faces. The two run at the same time on the same capture.
 *
 * <p>Before this, people mode sent every frame through the WebView, so the adult check ran on
 * TensorFlow.js at the 384 px face frame instead of on the native model at 224 px with the
 * plain-background crop the guard uses otherwise. Now an adult verdict is the same whatever the
 * people setting, and an adult block answers as soon as TFLite does, without waiting for faces.</p>
 *
 * <p>Results merge on the main thread: a block from TFLite answers at once; otherwise the answer
 * waits for both, and either side being unknown makes the frame unknown. VisualGuard keeps an
 * existing cover on unknown and adds a new one only in Cover mode. The face pipeline takes one
 * frame at a time, so a frame that arrives
 * while an earlier face check is still running (possible only after an early block) waits for it
 * as a private copy, and only the newest such frame is kept. The WebView and native
 * FaceRes share that frame's pixel extraction.</p>
 */
final class HybridRuntime implements VisualRuntime {
    /** Native FaceRes: a face is shown only when it leans the other way at least this strongly.
     * Same values and reasons as decisions.js FACERES_OPPOSITE (women 0.80, men 0.60). */
    static final float WOMEN_SHOW_MALE = 0.80f, MEN_SHOW_FEMALE = 0.60f;
    private final TfliteClassifier adult;
    private VisualClassifier faces;
    private FaceresClassifier faceres;
    private final ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String personMode;
    private final boolean evaluate;
    private Consumer<String> loaded;
    private boolean adultReady, facesReady, faceresLoading, closed;
    private FaceFrame activeFaces, queuedFaces;
    private final FaceResultCache faceCache = new FaceResultCache();
    private String backend = "tflite-cpu+wasm";

    private final class FaceFrame {
        final int width, height;
        final Request request;
        final byte[] key;
        final long capturedAt;
        int[] pixels;

        FaceFrame(Bitmap frame, Request request) {
            this.request = request;
            capturedAt = SystemClock.uptimeMillis();
            width = frame.getWidth();
            height = frame.getHeight();
            pixels = new int[width * height];
            try {
                frame.getPixels(pixels, 0, width, 0, 0, width, height);
                key = FaceResultCache.fingerprint(pixels, width, height);
            }
            catch (RuntimeException | OutOfMemoryError unavailable) {
                clear();
                throw unavailable;
            }
        }

        int[] takePixels() {
            int[] current = pixels;
            pixels = null;
            return current;
        }

        void clear() {
            int[] current = takePixels();
            if (current != null) Arrays.fill(current, 0);
        }
    }

    /** One frame's pair of answers. */
    private final class Request {
        final Consumer<VisualResult> result;
        VisualResult adultResult, faceResult;
        boolean answered;
        Request(Consumer<VisualResult> result) { this.result = result; }

        void adult(VisualResult value) {
            if (closed || answered) return;
            adultResult = value;
            if ("block".equals(value.verdict)) { answered = true; result.accept(value); return; }
            finishIfBoth();
        }

        void faces(VisualResult value) {
            if (closed || answered) return;
            faceResult = value;
            finishIfBoth();
        }

        private void finishIfBoth() {
            if (adultResult == null || faceResult == null) return;
            answered = true;
            boolean known = "allow".equals(adultResult.verdict) && "allow".equals(faceResult.verdict);
            result.accept(known ? new VisualResult("allow", faceResult.personBlur, false, faceResult.detail)
                    : new VisualResult("unknown", false));
        }
    }

    HybridRuntime(Context context, String personMode, Consumer<String> loaded) {
        this(context, personMode, "oarriaga", false, loaded);
    }

    /** appearance and evaluate pass through to the faces-only WebView (see VisualClassifier). */
    HybridRuntime(Context context, String personMode, String appearance, boolean evaluate, Consumer<String> loaded) {
        this.loaded = loaded;
        this.personMode = personMode;
        this.evaluate = evaluate;
        faceresLoading = "native".equals(appearance)
                && (PersonBlur.WOMEN.equals(personMode) || PersonBlur.MEN.equals(personMode));
        worker = !faceresLoading ? null : Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hisn-faceres");
            thread.setDaemon(true);
            return thread;
        });
        adult = new TfliteClassifier(context, verdict -> loadedPart(true, verdict));
        try {
            faces = createFaces(context, worker == null && "native".equals(appearance) ? "oarriaga" : appearance);
            if (worker != null) worker.execute(() -> loadFaceres(context));
        } catch (RuntimeException | LinkageError unavailable) {
            close();
            throw unavailable;
        }
    }

    private VisualClassifier createFaces(Context context, String appearance) {
        // The native path supplies the evaluation record without loading the WebView's FaceRes.
        return new VisualClassifier(context, "wasm", personMode, true, appearance,
                evaluate && !"native".equals(appearance), verdict -> loadedPart(false, verdict));
    }

    /** Start native model initialization in parallel with both existing loaders. */
    private void loadFaceres(Context context) {
        FaceresClassifier model;
        try { model = new FaceresClassifier(context); }
        catch (IOException | RuntimeException | LinkageError | OutOfMemoryError unavailable) { model = null; }
        final FaceresClassifier found = model;
        main.post(() -> {
            if (closed || loaded == null) {
                if (found != null) found.close();
                return;
            }
            faceresLoading = false;
            faceres = found;
            if (faceres == null) {
                // The release ships the WebView's small 64x64 estimator as its fallback.
                facesReady = false;
                faces.close();
                try { faces = createFaces(context, "oarriaga"); }
                catch (RuntimeException | LinkageError unavailable) {
                    loadedPart(false, "unknown");
                    return;
                }
            }
            finishLoading();
        });
    }

    private void loadedPart(boolean native_, String verdict) {
        if (closed || loaded == null) return;
        if (!"ready".equals(verdict)) {
            Consumer<String> report = loaded;
            loaded = null;
            report.accept("unknown");
            return;
        }
        if (native_) adultReady = true; else facesReady = true;
        finishLoading();
    }

    private void finishLoading() {
        if (loaded != null && adultReady && facesReady && !faceresLoading) {
            backend = adult.actualBackend() + "+" + faces.actualBackend();
            Consumer<String> report = loaded;
            loaded = null;
            report.accept("ready");
        }
    }

    /** Fallback for callers with one frame: both checks see it. VisualGuard passes two. */
    @Override public void inspect(Bitmap frame, Consumer<VisualResult> result) {
        inspect(frame, frame, result);
    }

    /** adultFrame: the 224 px frame (with the plain-background crop) the adult model expects.
     * peopleFrame: the 384 px whole screen the face models expect. Both are read before returning. */
    void inspect(Bitmap adultFrame, Bitmap peopleFrame, Consumer<VisualResult> result) {
        inspect(adultFrame, null, peopleFrame, result);
    }

    /** adultContext, when present, is the uncropped view used to corroborate the adult crop. */
    void inspect(Bitmap adultFrame, Bitmap adultContext, Bitmap peopleFrame, Consumer<VisualResult> result) {
        if (closed || !adultReady || !facesReady || faceresLoading) {
            result.accept(new VisualResult("unknown", false));
            return;
        }
        Request request = new Request(result);
        adult.inspect(adultFrame, adultContext, request::adult);
        if (closed || request.answered) return;
        FaceFrame frame;
        try { frame = new FaceFrame(peopleFrame, request); }
        catch (RuntimeException | OutOfMemoryError unavailable) {
            request.faces(new VisualResult("unknown", false));
            return;
        }
        if (activeFaces == null && queuedFaces == null) {
            startFaces(frame);
        } else {
            FaceFrame dropped = queuedFaces;
            queuedFaces = frame;
            // A superseded frame never got a face check; it must not read as clean.
            if (dropped != null) {
                dropped.clear();
                dropped.request.faces(new VisualResult("unknown", false));
            }
        }
    }

    private void startFaces(FaceFrame frame) {
        if (frame.request.answered) { frame.clear(); return; }
        VisualResult cached = faceCache.get(frame.key, SystemClock.uptimeMillis());
        if (cached != null) {
            frame.clear();
            frame.request.faces(cached);
            return;
        }
        activeFaces = frame;
        try {
            faces.inspect(frame.pixels, frame.width, frame.height, found -> {
                if (faceres == null || frame.request.answered) {
                    finishFaces(frame, found);
                } else if (!"allow".equals(found.verdict) || found.faceBoxes == null) {
                    finishFaces(frame, new VisualResult("unknown", false));
                } else if (found.faceBoxes.length == 0) {
                    finishFaces(frame, new VisualResult("allow", false, false, evaluate ? "[]" : null));
                } else {
                    judgeNatively(frame, found.faceBoxes);
                }
            });
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            finishFaces(frame, new VisualResult("unknown", false));
        }
    }

    private void finishFaces(FaceFrame frame, VisualResult found) {
        frame.clear();
        if (closed || activeFaces != frame) return;
        activeFaces = null;
        faceCache.put(frame.key, found, frame.capturedAt);
        frame.request.faces(found);
        if (!closed && activeFaces == null && queuedFaces != null) {
            FaceFrame next = queuedFaces;
            queuedFaces = null;
            startFaces(next);
        }
    }

    /** Women/men for each box the WebView found, on the FaceRes worker; the answer returns on main. */
    private void judgeNatively(FaceFrame frame, float[] boxes) {
        int[] pixels = frame.takePixels();
        FaceresClassifier model = faceres;
        try {
            worker.execute(() -> {
                VisualResult answer;
                try {
                    boolean covered = false;
                    JSONArray detail = evaluate ? new JSONArray() : null;
                    for (int i = 0; i + 3 < boxes.length; i += 4) {
                        float male = model.maleProbability(pixels, frame.width, frame.height,
                                boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3]);
                        if (!(male >= 0f && male <= 1f)) throw new IllegalStateException("Invalid FaceRes score");
                        boolean shown = PersonBlur.WOMEN.equals(personMode)
                                ? male >= WOMEN_SHOW_MALE : 1 - male >= MEN_SHOW_FEMALE;
                        if (detail != null) detail.put(new JSONObject().put("x", boxes[i]).put("y", boxes[i + 1])
                                .put("w", boxes[i + 2]).put("h", boxes[i + 3]).put("faceresMale", male));
                        if (!shown) {
                            covered = true;
                            // One face that stays covered settles the screen; skip the rest.
                            if (detail == null) break;
                        }
                    }
                    answer = new VisualResult("allow", covered, false, detail == null ? null : detail.toString());
                } catch (Throwable failure) {
                    answer = new VisualResult("unknown", false);
                } finally {
                    Arrays.fill(pixels, 0);
                }
                final VisualResult result = answer;
                main.post(() -> finishFaces(frame, result));
            });
        } catch (RejectedExecutionException closing) {
            Arrays.fill(pixels, 0);
            finishFaces(frame, new VisualResult("unknown", false));
        }
    }

    @Override public String actualBackend() { return faceres == null ? backend : backend + "+faceres-tflite"; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        loaded = null;
        faceCache.clear();
        if (activeFaces != null) { activeFaces.clear(); activeFaces = null; }
        if (queuedFaces != null) { queuedFaces.clear(); queuedFaces = null; }
        adult.close();
        if (faces != null) faces.close();
        if (worker != null) {
            if (faceres != null) worker.execute(faceres::close);
            worker.shutdown();
        }
    }
}
