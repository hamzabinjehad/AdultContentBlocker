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

/** People mode with every model native: TFLite judges adult content while BlazeFace finds faces
 * and FaceRes estimates women/men on a second worker. The two run at the same time on the same
 * capture. The WebView engine, loaded faces-only, is used only when the native face models cannot
 * load (or for the non-native appearance settings of debug accuracy runs).
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
 * as a private copy, and only the newest such frame is kept. Face finding and FaceRes share
 * that frame's pixel extraction.</p>
 */
final class HybridRuntime implements VisualRuntime {
    /** Native FaceRes: a face is shown only when it leans the other way at least this strongly.
     * Same values and reasons as decisions.js FACERES_OPPOSITE (women 0.80, men 0.60). */
    static final float WOMEN_SHOW_MALE = 0.80f, MEN_SHOW_FEMALE = 0.60f;
    private final TfliteClassifier adult;
    /** The WebView face engine: only for non-native appearance settings, or as the fallback
     * when the native detector cannot load. Null while the native detector is in use. */
    private VisualClassifier faces;
    /** Native BlazeFace, on {@link #worker} with FaceRes. Null until loaded, or when unavailable. */
    private NativeFaceDetector detector;
    private FaceresClassifier faceres;
    private final ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String personMode;
    private final boolean evaluate;
    private Consumer<String> loaded;
    /** Set by the guard each sample: the face-only blur also checks each matched person's clothes. */
    private boolean bodies;

    void bodies(boolean on) { bodies = on; }
    private boolean adultReady, facesReady, faceresLoading, detectorLoading, closed;
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
        /** The people frame, kept only for the body check of the face-only blur. */
        int[] people;
        int peopleWidth, peopleHeight;
        Request(Consumer<VisualResult> result) { this.result = result; }

        void clearPeople() {
            if (people != null) Arrays.fill(people, 0);
            people = null;
        }

        void adult(VisualResult value) {
            if (closed || answered) return;
            adultResult = value;
            if ("block".equals(value.verdict)) { answered = true; clearPeople(); result.accept(value); return; }
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
            if (known && faceResult.personBlur && faceResult.blurBoxes != null && faceResult.blurBoxes.length > 0
                    && people != null) {
                // Face-only blur: look at each matched person's clothes before answering.
                int[] pixels = people;
                people = null;
                final VisualResult face = faceResult;
                adult.people(pixels, peopleWidth, peopleHeight, face.blurBoxes, found -> {
                    if (closed) return;
                    result.accept("block".equals(found.verdict) ? found
                            : new VisualResult("allow", true, false, face.detail, null, found.blurBoxes));
                });
                return;
            }
            clearPeople();
            result.accept(known ? new VisualResult("allow", faceResult.personBlur, false, faceResult.detail,
                    null, faceResult.blurBoxes)
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
        boolean nativeFaces = "native".equals(appearance);
        boolean gender = PersonBlur.WOMEN.equals(personMode) || PersonBlur.MEN.equals(personMode);
        detectorLoading = nativeFaces;
        faceresLoading = nativeFaces && gender;
        // One worker runs face finding and the women/men estimate back to back for each frame.
        worker = !nativeFaces ? null : Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hisn-people");
            thread.setDaemon(true);
            return thread;
        });
        adult = new TfliteClassifier(context, verdict -> loadedPart(true, verdict));
        try {
            if (nativeFaces) worker.execute(() -> loadNative(context, gender));
            else faces = createFaces(context, appearance);
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

    /** Loads the native face models on the worker, in parallel with the adult model. Falls back to
     * the WebView engine when the detector (or, for women/men, FaceRes) cannot load. */
    private void loadNative(Context context, boolean gender) {
        NativeFaceDetector found = null;
        FaceresClassifier estimate = null;
        try { found = new NativeFaceDetector(context); }
        catch (IOException | RuntimeException | LinkageError | OutOfMemoryError unavailable) { found = null; }
        if (gender) {
            try { estimate = new FaceresClassifier(context); }
            catch (IOException | RuntimeException | LinkageError | OutOfMemoryError unavailable) { estimate = null; }
        }
        final NativeFaceDetector detected = found;
        final FaceresClassifier judged = estimate;
        main.post(() -> {
            if (closed || loaded == null) {
                closeOnWorker(detected, judged);
                return;
            }
            detectorLoading = false;
            faceresLoading = false;
            faceres = judged;
            if (detected != null && (!gender || judged != null)) {
                detector = detected;
                facesReady = true;
                finishLoading();
                return;
            }
            // The WebView finds the faces instead: boxes for native FaceRes when it loaded, or the
            // release's small 64x64 estimator when it did not.
            closeOnWorker(detected, null);
            try { faces = createFaces(context, gender && judged != null ? "native" : "oarriaga"); }
            catch (RuntimeException | LinkageError unavailable) { loadedPart(false, "unknown"); }
        });
    }

    private void closeOnWorker(NativeFaceDetector found, FaceresClassifier estimate) {
        if (found == null && estimate == null) return;
        try {
            worker.execute(() -> {
                if (found != null) found.close();
                if (estimate != null) estimate.close();
            });
        } catch (RejectedExecutionException shutDown) { /* The worker is gone; nothing runs on them. */ }
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
        if (loaded != null && adultReady && facesReady && !faceresLoading && !detectorLoading) {
            backend = adult.actualBackend() + "+" + (faces != null ? faces.actualBackend() : "blazeface-tflite");
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
        if (closed || !adultReady || !facesReady || faceresLoading || detectorLoading) {
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
        if (bodies && frame.pixels != null) {
            request.people = frame.pixels.clone();
            request.peopleWidth = frame.width;
            request.peopleHeight = frame.height;
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
        if (detector != null) {
            findNatively(frame);
            return;
        }
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

    /** Native path: find faces, then (women/men) estimate each, on the worker; answer on main. */
    private void findNatively(FaceFrame frame) {
        int[] pixels = frame.takePixels();
        NativeFaceDetector found = detector;
        FaceresClassifier model = faceres;
        // The face-only blur also looks closer: a wrong small match costs one soft patch there,
        // where a full cover or Back would take the whole screen.
        boolean closer = bodies;
        try {
            worker.execute(() -> {
                VisualResult answer;
                try {
                    float[] boxes = found.detect(pixels, frame.width, frame.height, closer);
                    if (boxes.length == 0) answer = new VisualResult("allow", false, false, evaluate ? "[]" : null);
                    else if (model != null) answer = judge(model, pixels, frame.width, frame.height, boxes);
                    else answer = new VisualResult("allow", true, false, null, null, boxes);
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

    /** Women/men for each box the WebView found, on the FaceRes worker; the answer returns on main. */
    private void judgeNatively(FaceFrame frame, float[] boxes) {
        int[] pixels = frame.takePixels();
        FaceresClassifier model = faceres;
        try {
            worker.execute(() -> {
                VisualResult answer;
                try { answer = judge(model, pixels, frame.width, frame.height, boxes); }
                catch (Throwable failure) { answer = new VisualResult("unknown", false); }
                finally { Arrays.fill(pixels, 0); }
                final VisualResult result = answer;
                main.post(() -> finishFaces(frame, result));
            });
        } catch (RejectedExecutionException closing) {
            Arrays.fill(pixels, 0);
            finishFaces(frame, new VisualResult("unknown", false));
        }
    }

    /** The faces the selected setting covers, judged one by one with FaceRes. Worker thread only. */
    private VisualResult judge(FaceresClassifier model, int[] pixels, int width, int height, float[] boxes)
            throws org.json.JSONException {
        boolean covered = false;
        JSONArray detail = evaluate ? new JSONArray() : null;
        float[] matched = new float[boxes.length];
        int kept = 0;
        for (int i = 0; i + 3 < boxes.length; i += 4) {
            float male = model.maleProbability(pixels, width, height, boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3]);
            if (!(male >= 0f && male <= 1f)) throw new IllegalStateException("Invalid FaceRes score");
            boolean shown = PersonBlur.WOMEN.equals(personMode) ? male >= WOMEN_SHOW_MALE : 1 - male >= MEN_SHOW_FEMALE;
            if (detail != null) detail.put(new JSONObject().put("x", boxes[i]).put("y", boxes[i + 1])
                    .put("w", boxes[i + 2]).put("h", boxes[i + 3]).put("faceresMale", male));
            if (!shown) {
                covered = true;
                // Every matched face is judged: the face-only blur needs each position.
                System.arraycopy(boxes, i, matched, kept, 4);
                kept += 4;
            }
        }
        return new VisualResult("allow", covered, false, detail == null ? null : detail.toString(),
                null, Arrays.copyOf(matched, kept));
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
            closeOnWorker(detector, faceres);
            worker.shutdown();
        }
    }
}
