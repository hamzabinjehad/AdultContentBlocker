package app.hisn.android;

import android.graphics.Bitmap;

import java.util.function.Consumer;

/** One local visual runtime. The callback runs on the main thread. */
interface VisualRuntime extends AutoCloseable {
    void inspect(Bitmap frame, Consumer<VisualResult> result);
    String actualBackend();
    @Override void close();
}
