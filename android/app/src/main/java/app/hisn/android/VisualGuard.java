package app.hisn.android;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import app.hisn.core.GuardWindows;
import app.hisn.core.VisualCadence;
import app.hisn.core.VisualDuty;
import app.hisn.core.VisualFrameGate;
import app.hisn.core.VisualHealth;
import app.hisn.core.VisualHealth.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Opt-in sampling of selected apps' windows (split screen, pop-up and picture-in-picture included),
 * one window and one inference at a time. Never persists pixels or bypasses secure surfaces. */
final class VisualGuard implements AutoCloseable {
    /** Acts on the app whose window was flagged. */
    interface Block { void run(String app, int window); }

    /** Keeps the latest failure on the dashboard until a later sample succeeds. */
    static final VisualHealth health = new VisualHealth();
    static volatile long samples;
    static volatile long interventions;
    static volatile long captures;
    static volatile long captureRequests;
    static volatile long captureFailures;
    static volatile long discarded;
    static volatile int lastCaptureError;
    static volatile String eligibility = "OFF";
    private final AccessibilityService service;
    private final Block block;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VisualFrameGate gate = new VisualFrameGate();
    private final VisualDuty duty = new VisualDuty();
    private final VisualCadence cadence = new VisualCadence();
    private final PowerManager power;
    private final Set<String> excluded;
    private VisualClassifier classifier;
    private boolean loaded, busy, closed, listening;
    private long retryAfter, nextPoll;
    private int lastWindow = -1;
    private VisualFrameGate.Frame pendingFrame;
    private final Runnable captureTimeout = this::fail;
    private final Runnable tick = this::poll;
    /** The screen coming on brings the next poll forward; while it is off, polls are slow and never sample. */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (!closed) soon(); }
    };

    VisualGuard(AccessibilityService service, Block block) {
        this.service = service;
        this.block = block;
        power = service.getSystemService(PowerManager.class);
        excluded = ChosenApps.excluded(service);
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                service.registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED);
                listening = true;
            } catch (RuntimeException unavailable) { /* Polls still notice the screen within OFF_MILLIS. */ }
        }
        schedule(0);
    }

    void windowChanged() { gate.invalidate(); }

    /** A selected window scrolled or changed: sample sooner, never sooner than the cadence allows. */
    void activity() {
        if (closed) return;
        cadence.activity(SystemClock.uptimeMillis());
        soon();
    }

    private void soon() {
        long now = SystemClock.uptimeMillis(), wait = cadence.earliest(true, now);
        if (now + wait < nextPoll) schedule(wait);
    }

    private void schedule(long delay) {
        handler.removeCallbacks(tick);
        nextPoll = SystemClock.uptimeMillis() + delay;
        handler.postDelayed(tick, delay);
    }

    /** After a sample ends, a scrolling screen need not wait for the full interval. */
    private void sampleEnded() {
        if (!closed && cadence.busyScreen(SystemClock.uptimeMillis())) soon();
    }

    private boolean interactive() {
        // Unknown power state: keep the previous behaviour rather than silently stopping checks.
        return power == null || power.isInteractive();
    }

    /** Selected apps' windows that may be captured now, active first (rule in core GuardWindows).
     * A window with a focused editable or password field is skipped, including when it is the only one. */
    private List<GuardWindows.Window> eligibleWindows() {
        List<GuardWindows.Window> eligible = new ArrayList<>();
        PolicyStore.Snapshot rules = PolicyStore.get(service).snapshot();
        if (!rules.visualEnabled) { eligibility = "OFF"; return eligible; }
        List<GuardWindows.Window> windows = TextGuardService.selectedWindows(service, excluded, rules.scannedApps::contains);
        if (windows == null) { eligibility = "NO_ROOT"; return eligible; }
        if (windows.isEmpty()) { eligibility = "NOT_SELECTED"; return eligible; }
        for (GuardWindows.Window window : windows) if (capturable(window)) eligible.add(window);
        if (eligible.isEmpty()) {
            health.report(State.INPUT);
            eligibility = "INPUT";
        } else eligibility = "SELECTED";
        return eligible;
    }

    /** False while an input field of that window is focused, including passwords, or when it is gone. */
    private boolean capturable(GuardWindows.Window window) {
        AccessibilityNodeInfo root = null, input = null;
        try {
            root = TextGuardService.windowRoot(service, window);
            if (root == null) return false;
            input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            return input == null || !(input.isEditable() || input.isPassword());
        } finally {
            if (input != null) input.recycle();
            if (root != null) root.recycle();
        }
    }

    private void poll() {
        if (closed) return;
        boolean interactive = interactive();
        long now = SystemClock.uptimeMillis();
        schedule(cadence.interval(interactive, now));
        try {
            if (!PolicyStore.get(service).snapshot().visualEnabled) {
                resetEngine(); health.report(State.OFF); return;
            }
            if (Build.VERSION.SDK_INT < 34) { health.report(State.UNSUPPORTED); return; }
            if (busy) return;
            health.report(State.WAITING);
            // Nothing is sampled while the screen is off; an idle model is freed after a few minutes.
            GuardWindows.Window target = interactive ? GuardWindows.after(eligibleWindows(), lastWindow) : null;
            switch (duty.next(interactive, target != null, classifier != null, SystemClock.elapsedRealtime())) {
                case RELEASE: resetEngine(); gate.invalidate(); return;
                case PAUSE: gate.invalidate(); return;
                default: break;
            }
            if (now < retryAfter) { health.report(State.ERROR); return; }
            if (classifier == null) {
                health.report(State.LOADING);
                busy = true;
                classifier = new VisualClassifier(service, verdict -> {
                    if (closed) return;
                    loaded = "ready".equals(verdict);
                    busy = false;
                    if (!loaded) fail();
                    else soon();
                });
                return;
            }
            if (!loaded) return;
            busy = true;
            health.report(State.CHECKING);
            // Several selected windows take turns, one capture and one inference at a time.
            lastWindow = target.id;
            cadence.started(now);
            VisualFrameGate.Frame frame = gate.begin(target.app, target.id, now);
            pendingFrame = frame;
            handler.postDelayed(captureTimeout, 10000);
            captureRequests++;
            service.takeScreenshotOfWindow(target.id, service.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                    try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
                        if (closed || pendingFrame != frame) return;
                        handler.removeCallbacks(captureTimeout);
                        captures++;
                        if (!current(frame, target)) {
                            discarded++; pendingFrame = null; busy = false; health.report(State.WAITING);
                            sampleEnded();
                            return;
                        }
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                        if (hardware == null) { busy = false; health.report(State.UNREADABLE); return; }
                        Bitmap copy = null, scaled = null;
                        try {
                            if ((long) hardware.getWidth() * hardware.getHeight() > 16777216) {
                                busy = false; health.report(State.UNREADABLE); return;
                            }
                            copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
                            double scale = Math.min(1.0, 384.0 / Math.max(copy.getWidth(), copy.getHeight()));
                            scaled = Bitmap.createScaledBitmap(copy, Math.max(1, (int) (copy.getWidth() * scale)),
                                    Math.max(1, (int) (copy.getHeight() * scale)), true);
                            long signature = signature(scaled);
                            classifier.inspect(scaled, verdict -> decided(frame, target, signature, verdict));
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
                        busy = false; health.report(State.UNREADABLE);
                        sampleEnded();
                    }
                }
            });
        } catch (RuntimeException unavailable) { busy = false; fail(); }
    }

    /** A coarse 16 x 16 summary that only tells whether the frame changed (VisualCadence.signature). */
    private static long signature(Bitmap frame) {
        int[] grid = new int[256];
        int width = frame.getWidth(), height = frame.getHeight();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) grid[y * 16 + x] = frame.getPixel(x * width / 16, y * height / 16);
        }
        long signature = VisualCadence.signature(grid);
        java.util.Arrays.fill(grid, 0);
        return signature;
    }

    private boolean current(VisualFrameGate.Frame frame, GuardWindows.Window target) {
        // A sample finishing after the screen went off, or after its window closed, changed app,
        // focused an input or stopped being selected, is discarded, never acted on.
        if (!interactive() || GuardWindows.find(eligibleWindows(), target.id, target.app) == null) return false;
        return gate.accepts(frame, target.app, target.id, true, SystemClock.uptimeMillis());
    }

    private void decided(VisualFrameGate.Frame frame, GuardWindows.Window target, long signature, String verdict) {
        if (closed || pendingFrame != frame) return;
        pendingFrame = null;
        busy = false;
        try {
            if (!current(frame, target)) { discarded++; health.report(State.WAITING); return; }
            verdict = VisualTestHooks.verdict(service.getPackageName(), target.app, verdict, SystemClock.uptimeMillis());
            if (!"block".equals(verdict) && !"allow".equals(verdict)) { fail(); return; }
            samples++;
            cadence.finished(target.id, signature);
            health.report(State.SAMPLED);
            if ("block".equals(verdict)) {
                gate.invalidate();
                interventions++;
                block.run(target.app, target.id);
            }
        } catch (RuntimeException unavailable) { fail(); }
        finally { sampleEnded(); }
    }

    private void fail() {
        resetEngine();
        retryAfter = SystemClock.uptimeMillis() + 30000;
        health.report(State.ERROR);
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
        if (listening) {
            try { service.unregisterReceiver(screenOn); } catch (RuntimeException alreadyGone) { /* Nothing to do. */ }
            listening = false;
        }
        resetEngine();
        health.report(State.OFF);
    }
}
