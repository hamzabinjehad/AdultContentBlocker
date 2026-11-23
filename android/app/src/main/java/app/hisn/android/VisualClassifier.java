package app.hisn.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.function.Consumer;
import org.json.JSONObject;
import app.hisn.core.VisualExecutionPolicy;

/** Network-isolated local model, one bounded frame at a time. Main-thread API. */
final class VisualClassifier implements VisualRuntime {
    /** A frame that has not answered in this long is treated as a failed runtime, not left pending. */
    private static final long FRAME_TIMEOUT_MILLIS = 5000;
    private static final int MAX_FRAME_EDGE = 384;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private Consumer<VisualResult> pending;
    private Consumer<String> pendingLoad;
    private String request;
    private long sequence;
    private boolean ready;
    private String actualBackend;
    private final Context context;
    private final VisualExecutionPolicy execution;
    private final String personMode;
    /** True when the native runtime judges adult content and this WebView only looks for faces. */
    private final boolean facesOnly;
    /** "faceres" or "oarriaga": which model estimates women/men in the WebView engine; "native":
     * the WebView only finds faces and returns their boxes for the native FaceRes runtime. */
    private final String appearance;
    private final boolean boxes;
    /** Debug accuracy runs only: return each face's raw estimates in VisualResult.detail. */
    private final boolean evaluate;
    private static boolean skipGpuAfterFailure;
    private final Runnable timeout = this::failed;

    VisualClassifier(Context context, Consumer<String> loaded) {
        // The guard's WebView is unattached and has no GPU surface. Starting on WASM avoids a
        // failed WebGL attempt on every load; the engine still falls back to WebGL then CPU.
        this(context, "wasm", PersonBlur.OFF, loaded);
    }

    VisualClassifier(Context context, String backend, Consumer<String> loaded) {
        this(context, backend, PersonBlur.OFF, loaded);
    }

    VisualClassifier(Context context, String backend, String personMode, Consumer<String> loaded) {
        this(context, backend, personMode, false, loaded);
    }

    /** facesOnly: skip the adult model in the WebView (HybridRuntime runs it natively instead). */
    @android.annotation.SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    VisualClassifier(Context context, String backend, String personMode, boolean facesOnly, Consumer<String> loaded) {
        this(context, backend, personMode, facesOnly, "oarriaga", false, loaded);
    }

    @android.annotation.SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    VisualClassifier(Context context, String backend, String personMode, boolean facesOnly, String appearance,
            boolean evaluate, Consumer<String> loaded) {
        this.boxes = "native".equals(appearance) && facesOnly && PersonBlur.on(personMode);
        this.appearance = "faceres".equals(appearance) ? "faceres" : "oarriaga";
        this.evaluate = evaluate;
        execution = new VisualExecutionPolicy(backend, skipGpuAfterFailure);
        this.context = context;
        // Native women/men: the page looks for every face ("all") and the app judges each one.
        this.personMode = boxes ? PersonBlur.ALL : PersonBlur.on(personMode) ? personMode : PersonBlur.OFF;
        this.facesOnly = facesOnly && PersonBlur.on(personMode);
        pendingLoad = loaded;
        request = "load";
        try { openRuntime(); }
        catch (RuntimeException unavailable) {
            execution.close(); destroyRuntime(); throw unavailable;
        }
    }

    @android.annotation.SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void openRuntime() {
        destroyRuntime();
        ready = false;
        actualBackend = null;
        int token = execution.generation();
        web = new WebView(context);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        web.getSettings().setSaveFormData(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String path = request.getUrl().getPath();
                boolean wasm = path.matches("/visual/wasm/tfjs-backend-wasm(-simd|-threaded-simd)?\\.wasm");
                boolean model = path.matches("/visual/models/(blazeface|gender|faceres)\\.(json|bin)");
                if ("https".equals(request.getUrl().getScheme()) && "hisn.invalid".equals(request.getUrl().getHost())
                        && ("/visual/android.html".equals(path) || "/visual/android.js".equals(path)
                        || "/visual/region.js".equals(path)
                        || "/visual/engine.bundle.js".equals(path) || wasm || model)) {
                    try {
                        // instantiateStreaming requires the exact WASM media type;
                        // a correct body with text/plain is refused by the browser.
                        String type = path.endsWith(".html") ? "text/html" : wasm ? "application/wasm"
                                : path.endsWith(".json") ? "application/json"
                                : path.endsWith(".bin") ? "application/octet-stream" : "text/javascript";
                        return new WebResourceResponse(type, wasm || path.endsWith(".bin") ? null : "UTF-8",
                                context.getAssets().open(path.substring(1)));
                    } catch (java.io.IOException unavailable) { /* return a closed response */ }
                }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                        java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (execution.accepts(token) && view == web) failed();
                if (view == web) destroyRuntime();
                return true;
            }
        });
        web.addJavascriptInterface(new ResultBridge(token), "HisnResult");
        handler.postDelayed(timeout, 60000);
        web.loadUrl("https://hisn.invalid/visual/android.html?backend=" + execution.backend()
                + "&person=" + personMode + (facesOnly ? "&adult=off" : "")
                + ("faceres".equals(appearance) ? "&appearance=faceres" : "") + (evaluate ? "&eval=1" : "")
                + (boxes ? "&boxes=1" : ""));
    }

    @Override public String actualBackend() { return actualBackend; }

    private final class ResultBridge {
        private final int token;
        ResultBridge(int token) { this.token = token; }
        @JavascriptInterface public void result(String id, String answer) {
            // No captured bytes, URLs or model scores cross back into application state.
            handler.post(() -> {
                if (!execution.accepts(token) || web == null || request == null || !request.equals(id)) return;
                if ("load".equals(id)) {
                    ready = "ready:cpu".equals(answer) || "ready:webgl".equals(answer)
                            || "ready:wasm".equals(answer);
                    actualBackend = ready ? answer.substring(6) : null;
                    finishLoad(ready ? "ready" : "unknown");
                    return;
                }
                try {
                    JSONObject value = new JSONObject(answer);
                    String verdict = value.optString("v");
                    boolean person = value.optBoolean("p", false);
                    boolean strong = value.optBoolean("s", false);
                    if ("allow".equals(verdict) || "block".equals(verdict)) {
                        // Scores leave the WebView only for a debug accuracy run that asked for them.
                        org.json.JSONArray faces = evaluate ? value.optJSONArray("e") : null;
                        float[] found = boxes ? boxes(value.optJSONArray("b")) : null;
                        if (boxes && found == null) { failed(); return; }
                        // Matched faces' positions, for the face-only blur. Malformed reads as null,
                        // and the guard then covers the whole screen instead.
                        float[] matched = person ? boxes(value.optJSONArray("r")) : null;
                        finishFrame(new VisualResult(verdict, person, strong, faces == null ? null : faces.toString(),
                                found, matched));
                    } else failed();
                } catch (org.json.JSONException malformed) { failed(); }
            });
        }
    }

    /** At most 10 boxes of four finite numbers in 0..1, or null when the answer is malformed. */
    private static float[] boxes(org.json.JSONArray list) {
        if (list == null || list.length() > 10) return null;
        float[] out = new float[list.length() * 4];
        for (int i = 0; i < list.length(); i++) {
            org.json.JSONArray box = list.optJSONArray(i);
            if (box == null || box.length() != 4) return null;
            for (int j = 0; j < 4; j++) {
                double value = box.optDouble(j, Double.NaN);
                if (!(value >= 0 && value <= 1)) return null;
                out[i * 4 + j] = (float) value;
            }
        }
        return out;
    }

    @Override public void inspect(Bitmap frame, Consumer<VisualResult> result) {
        int width = frame.getWidth(), height = frame.getHeight();
        if (!canInspect(width, height)) {
            result.accept(new VisualResult("unknown", false));
            return;
        }
        int[] pixels = new int[width * height];
        try {
            frame.getPixels(pixels, 0, width, 0, 0, width, height);
            inspect(pixels, width, height, result);
        } finally {
            Arrays.fill(pixels, 0);
        }
    }

    private boolean canInspect(int width, int height) {
        return ready && web != null && pending == null
                && width > 0 && width <= MAX_FRAME_EDGE && height > 0 && height <= MAX_FRAME_EDGE;
    }

    /** Borrow the caller's pixels while encoding the frame. The caller owns and clears them. */
    void inspect(int[] pixels, int width, int height, Consumer<VisualResult> result) {
        if (!canInspect(width, height) || pixels.length != width * height) {
            result.accept(new VisualResult("unknown", false));
            return;
        }
        byte[] rgba = new byte[pixels.length * 4];
        try {
            for (int i = 0; i < pixels.length; i++) {
                rgba[i * 4] = (byte) (pixels[i] >> 16);
                rgba[i * 4 + 1] = (byte) (pixels[i] >> 8);
                rgba[i * 4 + 2] = (byte) pixels[i];
                rgba[i * 4 + 3] = (byte) 255;
            }
            // id is generated locally; Base64 contains no JavaScript delimiters.
            String id = "frame_" + ++sequence;
            String script = "window.hisnInspect('" + id + "'," + width + "," + height + ",'"
                    + Base64.encodeToString(rgba, Base64.NO_WRAP) + "')";
            pending = result;
            request = id;
            handler.postDelayed(timeout, FRAME_TIMEOUT_MILLIS);
            try { web.evaluateJavascript(script, null); }
            catch (RuntimeException | OutOfMemoryError unavailable) { failed(); }
        } finally {
            Arrays.fill(rgba, (byte) 0);
        }
    }

    private void finishLoad(String state) {
        handler.removeCallbacks(timeout);
        Consumer<String> callback = pendingLoad;
        pendingLoad = null;
        request = null;
        if (callback != null) callback.accept(state);
    }

    private void finishFrame(VisualResult result) {
        handler.removeCallbacks(timeout);
        Consumer<VisualResult> callback = pending;
        pending = null;
        request = null;
        if (callback != null) callback.accept(result);
    }

    private void failed() {
        boolean loading = "load".equals(request);
        handler.removeCallbacks(timeout);
        ready = false;
        boolean retry = execution.failed(loading);
        if (execution.skipGpuAfterFailure()) skipGpuAfterFailure = true;
        if (retry) {
            try { openRuntime(); }
            catch (RuntimeException unavailable) {
                if (loading) finishLoad("unknown");
                else finishFrame(new VisualResult("unknown", false));
            }
        } else if (loading) finishLoad("unknown");
        else finishFrame(new VisualResult("unknown", false));
    }

    @Override public void close() {
        execution.close();
        handler.removeCallbacksAndMessages(null);
        pending = null;
        pendingLoad = null;
        request = null;
        ready = false;
        destroyRuntime();
    }

    private void destroyRuntime() {
        if (web != null) {
            WebView previous = web;
            web = null;
            previous.removeJavascriptInterface("HisnResult");
            previous.stopLoading();
            previous.destroy();
        }
    }
}
