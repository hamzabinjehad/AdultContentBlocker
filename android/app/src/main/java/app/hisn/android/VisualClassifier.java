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

/** Network-isolated local model, one bounded frame at a time. Main-thread API. */
final class VisualClassifier implements AutoCloseable {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private Consumer<String> pending;
    private String request;
    private long sequence;
    private boolean ready;
    private final Runnable timeout = () -> finish("unknown");

    @android.annotation.SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    VisualClassifier(Context context, Consumer<String> loaded) {
        pending = loaded;
        request = "load";
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
                if ("https".equals(request.getUrl().getScheme()) && "hisn.invalid".equals(request.getUrl().getHost())
                        && ("/visual/android.html".equals(path) || "/visual/android.js".equals(path)
                        || "/visual/engine.bundle.js".equals(path))) {
                    try {
                        return new WebResourceResponse(path.endsWith("html") ? "text/html" : "text/javascript",
                                "UTF-8", context.getAssets().open(path.substring(1)));
                    } catch (java.io.IOException unavailable) { /* return a closed response */ }
                }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                        java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                ready = false;
                finish("unknown");
                close();
                return true;
            }
        });
        web.addJavascriptInterface(new ResultBridge(), "HisnResult");
        handler.postDelayed(timeout, 60000);
        web.loadUrl("https://hisn.invalid/visual/android.html");
    }

    private final class ResultBridge {
        @JavascriptInterface public void result(String id, String verdict) {
            // No captured bytes, URLs or model scores cross back into application state.
            handler.post(() -> {
                if (web == null || request == null || !request.equals(id)) return;
                if ("load".equals(id)) ready = "ready".equals(verdict);
                finish("ready".equals(verdict) || "allow".equals(verdict) || "block".equals(verdict) ? verdict : "unknown");
            });
        }
    }

    void inspect(Bitmap frame, Consumer<String> result) {
        if (!ready || web == null || pending != null || frame.getWidth() > 384 || frame.getHeight() > 384) {
            result.accept("unknown");
            return;
        }
        int width = frame.getWidth(), height = frame.getHeight();
        int[] pixels = new int[width * height];
        byte[] rgba = new byte[pixels.length * 4];
        try {
            frame.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int i = 0; i < pixels.length; i++) {
                rgba[i * 4] = (byte) (pixels[i] >> 16);
                rgba[i * 4 + 1] = (byte) (pixels[i] >> 8);
                rgba[i * 4 + 2] = (byte) pixels[i];
                rgba[i * 4 + 3] = (byte) 255;
            }
            pending = result;
            request = "frame_" + ++sequence;
            handler.postDelayed(timeout, 15000);
            // id is generated locally; Base64 contains no JavaScript delimiters.
            web.evaluateJavascript("window.hisnInspect('" + request + "'," + width + "," + height + ",'"
                    + Base64.encodeToString(rgba, Base64.NO_WRAP) + "')", null);
        } finally {
            Arrays.fill(pixels, 0);
            Arrays.fill(rgba, (byte) 0);
        }
    }

    private void finish(String verdict) {
        handler.removeCallbacks(timeout);
        Consumer<String> callback = pending;
        pending = null;
        request = null;
        if (callback != null) callback.accept(verdict);
    }

    @Override public void close() {
        handler.removeCallbacksAndMessages(null);
        pending = null;
        request = null;
        ready = false;
        if (web != null) {
            web.removeJavascriptInterface("HisnResult");
            web.stopLoading();
            web.destroy();
            web = null;
        }
    }
}
