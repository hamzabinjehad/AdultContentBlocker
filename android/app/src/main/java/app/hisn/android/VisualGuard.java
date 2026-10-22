package app.hisn.android;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import app.hisn.core.VisualFrameGate;
import java.util.Set;

/** Opt-in, selected-window sampling. Never persists pixels or bypasses secure surfaces. */
final class VisualGuard implements AutoCloseable {
    enum State { OFF, UNSUPPORTED, WAITING, LOADING, CHECKING, SAMPLED, UNREADABLE, INPUT, ERROR }
    static volatile State state = State.OFF;
    static volatile long samples;
    static volatile long interventions;
    static volatile long captures;
    static volatile long captureRequests;
    static volatile long captureFailures;
    static volatile long discarded;
    static volatile int lastCaptureError;
    static volatile String eligibility = "OFF";
    private final AccessibilityService service;
    private final Runnable block;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VisualFrameGate gate = new VisualFrameGate();
    private final Set<String> excluded;
    private VisualClassifier classifier;
    private boolean loaded, busy, closed;
    private long retryAfter;
    private VisualFrameGate.Frame pendingFrame;
    private final Runnable captureTimeout = this::fail;
    private final Runnable tick = this::poll;

    VisualGuard(AccessibilityService service, Runnable block) {
        this.service = service;
        this.block = block;
        excluded = ChosenApps.excluded(service);
        handler.post(tick);
    }

    void windowChanged() { gate.invalidate(); }

    private static final class Target {
        final String app;
        final int window;
        Target(String app, int window) { this.app = app; this.window = window; }
    }

    private Target selectedWindow() {
        AccessibilityNodeInfo root = null, input = null;
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(service).snapshot();
            if (!rules.visualEnabled) { eligibility = "OFF"; return null; }
            root = service.getRootInActiveWindow();
            if (root == null || root.getPackageName() == null) { eligibility = "NO_ROOT"; return null; }
            String app = root.getPackageName().toString();
            if (excluded.contains(app)) { eligibility = "EXCLUDED"; return null; }
            if (!rules.scannedApps.contains(app)) { eligibility = "NOT_SELECTED"; return null; }
            // Do not capture an app while an input field is focused, including passwords.
            input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (input != null && (input.isEditable() || input.isPassword())) {
                state = State.INPUT;
                eligibility = "INPUT";
                return null;
            }
            eligibility = "SELECTED";
            return new Target(app, root.getWindowId());
        } finally {
            if (input != null) input.recycle();
            if (root != null) root.recycle();
        }
    }

    private void poll() {
        if (closed) return;
        handler.postDelayed(tick, 2000);
        try {
            if (!PolicyStore.get(service).snapshot().visualEnabled) {
                resetEngine(); state = State.OFF; return;
            }
            if (Build.VERSION.SDK_INT < 34) { state = State.UNSUPPORTED; return; }
            if (busy) return;
            state = State.WAITING;
            Target target = selectedWindow();
            if (target == null) { gate.invalidate(); return; }
            if (SystemClock.uptimeMillis() < retryAfter) { state = State.ERROR; return; }
            if (classifier == null) {
                state = State.LOADING;
                busy = true;
                classifier = new VisualClassifier(service, verdict -> {
                    if (closed) return;
                    loaded = "ready".equals(verdict);
                    busy = false;
                    if (!loaded) fail();
                });
                return;
            }
            if (!loaded) return;
            busy = true;
            state = State.CHECKING;
            VisualFrameGate.Frame frame = gate.begin(target.app, target.window, SystemClock.uptimeMillis());
            pendingFrame = frame;
            handler.postDelayed(captureTimeout, 10000);
            captureRequests++;
            service.takeScreenshotOfWindow(target.window, service.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                    try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
                        if (closed || pendingFrame != frame) return;
                        handler.removeCallbacks(captureTimeout);
                        captures++;
                        if (!current(frame)) {
                            discarded++; pendingFrame = null; busy = false; state = State.WAITING; return;
                        }
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                        if (hardware == null) { busy = false; state = State.UNREADABLE; return; }
                        Bitmap copy = null, scaled = null;
                        try {
                            if ((long) hardware.getWidth() * hardware.getHeight() > 16777216) {
                                busy = false; state = State.UNREADABLE; return;
                            }
                            copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
                            double scale = Math.min(1.0, 384.0 / Math.max(copy.getWidth(), copy.getHeight()));
                            scaled = Bitmap.createScaledBitmap(copy, Math.max(1, (int) (copy.getWidth() * scale)),
                                    Math.max(1, (int) (copy.getHeight() * scale)), true);
                            classifier.inspect(scaled, verdict -> decided(frame, verdict));
                        } finally {
                            if (scaled != null && scaled != copy) scaled.recycle();
                            if (copy != null) copy.recycle();
                            hardware.recycle();
                        }
                    } catch (RuntimeException | OutOfMemoryError unavailable) { busy = false; fail(); }
                }
                @Override public void onFailure(int error) {
                    if (!closed && pendingFrame == frame) {
                        handler.removeCallbacks(captureTimeout);
                        pendingFrame = null;
                        captureFailures++;
                        lastCaptureError = error;
                        busy = false; state = State.UNREADABLE;
                    }
                }
            });
        } catch (RuntimeException unavailable) { busy = false; fail(); }
    }

    private boolean current(VisualFrameGate.Frame frame) {
        Target target = selectedWindow();
        return target != null && gate.accepts(frame, target.app, target.window, true, SystemClock.uptimeMillis());
    }

    private void decided(VisualFrameGate.Frame frame, String verdict) {
        if (closed || pendingFrame != frame) return;
        pendingFrame = null;
        busy = false;
        try {
            if (!current(frame)) { discarded++; state = State.WAITING; return; }
            Target target = selectedWindow();
            verdict = VisualTestHooks.verdict(service.getPackageName(), target == null ? null : target.app,
                    verdict, SystemClock.uptimeMillis());
            if (!"block".equals(verdict) && !"allow".equals(verdict)) { fail(); return; }
            samples++;
            state = State.SAMPLED;
            if ("block".equals(verdict)) {
                gate.invalidate();
                interventions++;
                block.run();
            }
        } catch (RuntimeException unavailable) { fail(); }
    }

    private void fail() {
        resetEngine();
        retryAfter = SystemClock.uptimeMillis() + 30000;
        state = State.ERROR;
    }

    private void resetEngine() {
        handler.removeCallbacks(captureTimeout);
        pendingFrame = null;
        gate.invalidate();
        if (classifier != null) classifier.close();
        classifier = null;
        loaded = false;
        busy = false;
    }

    @Override public void close() {
        VisualTestHooks.clear();
        closed = true;
        handler.removeCallbacksAndMessages(null);
        resetEngine();
        state = State.OFF;
    }
}
