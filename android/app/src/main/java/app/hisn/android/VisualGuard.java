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
import app.hisn.core.VisualRetry;
import app.hisn.core.PolicyReceipt;
import app.hisn.core.VisualHealth;
import app.hisn.core.VisualHealth.State;
import app.hisn.core.VisualVerdictCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Opt-in sampling of selected apps' windows (split screen, pop-up and picture-in-picture included),
 * one window and one inference at a time. Never persists pixels or bypasses secure surfaces. */
final class VisualGuard implements AutoCloseable {
    /** Acts on the app whose window was flagged; false when that window was acted on moments ago. */
    interface Block { boolean run(GuardWindows.Window window); }

    /** Keeps the latest failure on the dashboard until a later sample succeeds. */
    static final VisualHealth health = new VisualHealth();
    static volatile long samples;
    /** Frames served from the last-verdict cache instead of running the model again. */
    static volatile long reused;
    static volatile long interventions;
    /** Matched people sent back to the previous page instead of being covered. */
    static volatile long personBacks;
    static volatile long captures;
    static volatile long captureRequests;
    /** Long edge of the frame handed to the model. The model resizes its input to 224, and this
     * keeps the encode and inference cost down. No accuracy comparison between this edge and the
     * earlier 384 exists, so the change is about latency, not recall. */
    static final int FRAME_EDGE = 224;
    /** Face mode keeps more pixels so a small face is not lost before the 128 px detector. */
    static final int PERSON_FRAME_EDGE = 384;
    /** The native presence pre-check only needs enough pixels to find a frontal face. */
    static final int FAST_FRAME_EDGE = 256;
    static volatile int lastFrameEdge = FRAME_EDGE;
    static volatile String backend = "none";
    static volatile long lastCaptureMillis, lastInferenceMillis, lastSampleMillis;
    static volatile long lastSampleAt;
    static volatile long captureFailures;
    /** Screenshot requests Android rejected for arriving inside its 333 ms per-window floor. */
    static volatile long rateLimited;
    static volatile long discarded;
    static volatile int lastCaptureError;
    static volatile String eligibility = "OFF";
    private final AccessibilityService service;
    private final Block block;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VisualFrameGate gate = new VisualFrameGate();
    private final VisualDuty duty = new VisualDuty();
    private final VisualCadence cadence = new VisualCadence();
    private final VisualRetry retry = new VisualRetry();
    private final VisualVerdictCache verdicts = new VisualVerdictCache();
    private final PowerManager power;
    private final Set<String> excluded;
    private final VisualOverlay overlay;
    private final PersonFastPresence fastPresence = new PersonFastPresence();
    private VisualRuntime classifier;
    /** The selected people cover mode; changing it reloads the runtime. */
    private String personMode;
    /** One downscaled copy kept only until the cover uses it; never written to disk. */
    private Bitmap pendingPersonFrame;
    /** The native runtime failed to load; use the packaged WebView engine for the rest of this instance. */
    private boolean nativeUnavailable;
    private boolean loaded, busy, closed, listening;
    private long retryAfter, nextPoll, captureBackoffUntil, lastCaptureRequestAt, lastPersonBackAt;
    /** A cover-before-checking is shown once per window, never once per sample. */
    private boolean provisionalCover;
    private int captureFailureStreak;
    private int lastWindow = -1;
    private VisualFrameGate.Frame pendingFrame;
    private long pendingStarted;
    private long pendingInferenceStart;
    /** Policy and guard session captured when this frame was submitted. */
    private long pendingPolicy = -1;
    private int pendingSession;
    private int loadGeneration;
    /** Discards a native pre-check whose window or guard instance has changed. */
    private int fastGeneration;
    private static final long CAPTURE_TIMEOUT_MILLIS = 5000;
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
        overlay = new VisualOverlay(service);
        personMode = PersonBlur.mode(service);
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                service.registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED);
                listening = true;
            } catch (RuntimeException unavailable) { /* Polls still notice the screen within OFF_MILLIS. */ }
        }
        schedule(0);
    }

    /** A window opened, closed or came forward: its first frame must not wait for the old
     * window's interval, and an in-flight frame for the old window must not decide anything. */
    void windowChanged() {
        gate.invalidate();
        // Do not hide the cover here: adding an accessibility overlay can itself produce a
        // window-state event. The next poll hides it when no selected window is eligible, and a
        // clean frame hides it when a selected app no longer contains a matching face.
        verdicts.clear();
        // A failure belongs to the old window; a new one is attempted at once.
        captureFailureStreak = 0;
        captureBackoffUntil = 0;
        provisionalCover = false;
        fastGeneration++;
        cadence.windowChanged(SystemClock.uptimeMillis());
        soon();
    }

    /** A selected window scrolled or changed: sample sooner, never sooner than the cadence allows. */
    void activity() {
        if (closed) return;
        // Scrolling or changed content is not a still frame: do not reuse its old verdict.
        verdicts.clear();
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
        List<Integer> ids = new ArrayList<>();
        for (GuardWindows.Window window : windows) ids.add(window.id);
        cadence.retain(ids);
        verdicts.retain(ids);
        if (windows.isEmpty()) { eligibility = "NOT_SELECTED"; return eligible; }
        boolean typing = false;
        for (GuardWindows.Window window : windows) {
            Boolean input = typing(window);
            if (input == null) continue; // Gone or changed app since listing.
            if (input) typing = true; else eligible.add(window);
        }
        if (eligible.isEmpty() && typing) {
            health.report(State.INPUT);
            eligibility = "INPUT";
        } else eligibility = eligible.isEmpty() ? "NO_ROOT" : "SELECTED";
        return eligible;
    }

    /** True while that window holds input focus and an input field in it is focused, including
     * passwords (rule in core GuardWindows.typing); null when it is gone. */
    private Boolean typing(GuardWindows.Window window) {
        AccessibilityNodeInfo root = null, input = null;
        try {
            root = TextGuardService.windowRoot(service, window);
            if (root == null) return null;
            input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            return GuardWindows.typing(window, input != null && (input.isEditable() || input.isPassword()));
        } finally {
            if (input != null) input.recycle();
            if (root != null) root.recycle();
        }
    }

    private void poll() {
        if (closed) return;
        boolean interactive = interactive();
        if (!interactive) overlay.hide();
        long now = SystemClock.uptimeMillis();
        cadence.polled(now);
        schedule(cadence.interval(interactive, now));
        try {
            if (!PolicyStore.get(service).snapshot().visualEnabled) {
                resetEngine(); health.report(State.OFF); return;
            }
            if (Build.VERSION.SDK_INT < 34) { health.report(State.UNSUPPORTED); return; }
            String wantedPeople = PersonBlur.mode(service);
            if (!wantedPeople.equals(personMode)) {
                // The WebView loads the face models with the mode; reload and do not reuse a
                // verdict or cover from the previous setting.
                personMode = wantedPeople;
                resetEngine();
            }
            if (busy) return;
            health.report(State.WAITING);
            // Nothing is sampled while the screen is off; while it is on the model stays warm.
            GuardWindows.Window target = interactive ? GuardWindows.after(eligibleWindows(), lastWindow) : null;
            if (target == null) overlay.hide();
            // A person who chose the full-cover response should not see the screen while
            // the first sample is still running. Shown once per window, cleared by the result.
            if (target != null && PersonBlur.on(personMode)
                    && PersonBlur.COVER.equals(PersonBlur.response(service)) && !provisionalCover) {
                overlay.showChecking();
                provisionalCover = true;
            }
            if (now < retryAfter) { health.report(State.ERROR); return; }
            switch (duty.next(interactive, target != null, classifier != null, SystemClock.elapsedRealtime())) {
                case RELEASE: resetEngine(); gate.invalidate(); return;
                case PRELOAD:
                    // Warm the WebView before a selected app comes forward. If one is already on
                    // screen, the same load ends with a sample instead of a three-second blank.
                    startLoading(target != null);
                    return;
                case PAUSE: gate.invalidate(); return;
                default: break;
            }
            if (classifier == null || !loaded) {
                // Do not make the first face wait for a three-second WebView/model load.
                // The native detector can cover from the first capture; the full engine
                // still starts immediately after and makes the final decision.
                if (PersonBlur.on(personMode) && target != null) {
                    startFastPresence(target);
                    return;
                }
                startLoading(target != null);
                return;
            }
            if (target == null) { gate.invalidate(); return; }
            if (now < captureBackoffUntil) { health.report(State.UNREADABLE); return; }
            busy = true;
            health.report(State.CHECKING);
            // Several selected windows take turns, one capture and one inference at a time.
            lastWindow = target.id;
            cadence.started(now);
            VisualFrameGate.Frame frame = gate.begin(target.app, target.id, now);
            pendingFrame = frame;
            pendingStarted = now;
            try { pendingPolicy = PolicyEvidence.visual(PolicyStore.get(service)); }
            catch (RuntimeException unavailable) { pendingPolicy = -1; }
            pendingSession = TextGuardService.serviceSession;
            lastCaptureRequestAt = now;
            handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MILLIS);
            captureRequests++;
            service.takeScreenshotOfWindow(target.id, service.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                    try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
                        if (closed || pendingFrame != frame) return;
                        handler.removeCallbacks(captureTimeout);
                        captures++;
                        captureFailureStreak = 0;
                        captureBackoffUntil = 0;
                        lastCaptureMillis = SystemClock.uptimeMillis() - pendingStarted;
                        if (!current(frame, target)) {
                            discarded++; pendingFrame = null; busy = false; clearPendingPersonFrame();
                            health.report(State.WAITING);
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
                            int edge = PersonBlur.on(personMode) ? PERSON_FRAME_EDGE : FRAME_EDGE;
                            lastFrameEdge = edge;
                            double scale = Math.min(1.0, edge / (double) Math.max(copy.getWidth(), copy.getHeight()));
                            scaled = Bitmap.createScaledBitmap(copy, Math.max(1, (int) (copy.getWidth() * scale)),
                                    Math.max(1, (int) (copy.getHeight() * scale)), true);
                            if (PersonBlur.on(personMode)) {
                                try {
                                    pendingPersonFrame = scaled.copy(Bitmap.Config.ARGB_8888, false);
                                } catch (RuntimeException | OutOfMemoryError noCopy) {
                                    pendingPersonFrame = null;
                                }
                            }
                            long signature = signature(scaled);
                            String cached = PersonBlur.on(personMode)
                                    ? null : verdicts.get(target.id, signature, SystemClock.uptimeMillis());
                            if (cached != null) {
                                cached(frame, target, signature, cached);
                                return;
                            }
                            if (PersonBlur.ALL.equals(personMode)) {
                                fastPresence.check(scaled, found -> {
                                    if (found && !closed && pendingFrame == frame) overlay.showChecking();
                                });
                            }
                            lastInferenceMillis = 0;
                            pendingInferenceStart = SystemClock.uptimeMillis();
                            classifier.inspect(scaled, result -> decided(frame, target, signature, result));
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
                        busy = false;
                        // Android's per-window screenshot floor is 333 ms. A window change can
                        // still ask sooner than the moving cadence; that rejection is a timing
                        // answer, not an unreadable window, so retry at the floor without the
                        // capture-failure backoff.
                        if (error == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                            rateLimited++;
                            lastCaptureError = error;
                            health.report(State.WAITING);
                            long wait = Math.max(VisualCadence.POLL_FLOOR_MILLIS,
                                    lastCaptureRequestAt + VisualCadence.FAST_MILLIS - SystemClock.uptimeMillis());
                            schedule(wait);
                            return;
                        }
                        if (error == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                            // Incognito and other FLAG_SECURE windows cannot return pixels. Cover
                            // the selected window instead of leaving the private content visible;
                            // text and DNS layers remain independent. Retry slowly: the window
                            // will stay protected until it is left or closed.
                            captureFailures++;
                            lastCaptureError = error;
                            overlay.showProtected();
                            health.report(State.PROTECTED);
                            long wait = Math.max(VisualCadence.POLL_FLOOR_MILLIS, 2000);
                            captureBackoffUntil = SystemClock.uptimeMillis() + wait;
                            schedule(wait);
                            return;
                        }
                        captureFailures++;
                        lastCaptureError = error;
                        // A protected or unreadable window will stay unreadable. Retry with a
                        // bounded backoff instead of asking Android for a screenshot four times
                        // a second; a window change clears it.
                        captureFailureStreak = Math.min(captureFailureStreak + 1, 4);
                        captureBackoffUntil = SystemClock.uptimeMillis() + Math.min(4000, 500L << captureFailureStreak);
                        health.report(State.UNREADABLE);
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

    /** Applies the cached verdict for an unchanged window without running the model again. */
    private void cached(VisualFrameGate.Frame frame, GuardWindows.Window target, long signature, String verdict) {
        if (closed || pendingFrame != frame) return;
        long now = SystemClock.uptimeMillis();
        lastInferenceMillis = 0;
        lastSampleMillis = pendingStarted == 0 ? 0 : now - pendingStarted;
        lastSampleAt = now;
        pendingStarted = 0;
        pendingInferenceStart = 0;
        pendingFrame = null;
        busy = false;
        try {
            // A policy or guard-instance change while the model ran invalidates the verdict.
            long currentPolicy;
            try { currentPolicy = PolicyEvidence.visual(PolicyStore.get(service)); }
            catch (RuntimeException unreadable) { currentPolicy = -1; }
            if (pendingPolicy < 0 || pendingPolicy != currentPolicy
                    || pendingSession != TextGuardService.serviceSession) {
                discarded++;
                health.report(State.WAITING);
                return;
            }
            if (!current(frame, target)) { discarded++; overlay.hide(); health.report(State.WAITING); return; }
            verdict = VisualTestHooks.verdict(service.getPackageName(), target.app, verdict, now);
            if (!"block".equals(verdict) && !"allow".equals(verdict)) { fail(); return; }
            reused++;
            cadence.finished(target.id, signature, now);
            long completedAt = SystemClock.elapsedRealtime();
            health.report(State.SAMPLED, completedAt, new PolicyReceipt(pendingPolicy, pendingSession,
                    target.id, PolicyReceipt.Kind.VISUAL_SAMPLE, PolicyReceipt.Result.PASSED, completedAt));
            if ("block".equals(verdict)) {
                overlay.hide();
                gate.invalidate();
                if (block.run(target)) interventions++;
            } else overlay.hide();
        } catch (RuntimeException unavailable) { fail(); }
        finally { sampleEnded(); }
    }

    private void decided(VisualFrameGate.Frame frame, GuardWindows.Window target, long signature,
            VisualResult result) {
        if (closed || pendingFrame != frame) return;
        Bitmap personFrame = pendingPersonFrame;
        pendingPersonFrame = null;
        long now = SystemClock.uptimeMillis();
        if (pendingInferenceStart != 0) lastInferenceMillis = now - pendingInferenceStart;
        lastSampleMillis = pendingStarted == 0 ? lastInferenceMillis : now - pendingStarted;
        lastSampleAt = now;
        pendingStarted = 0;
        pendingInferenceStart = 0;
        pendingFrame = null;
        busy = false;
        try {
            // A policy or guard-instance change while the model ran invalidates the verdict.
            long currentPolicy;
            try { currentPolicy = PolicyEvidence.visual(PolicyStore.get(service)); }
            catch (RuntimeException unreadable) { currentPolicy = -1; }
            if (pendingPolicy < 0 || pendingPolicy != currentPolicy
                    || pendingSession != TextGuardService.serviceSession) {
                discarded++;
                health.report(State.WAITING);
                return;
            }
            if (!current(frame, target)) {
                discarded++; overlay.hide(); health.report(State.WAITING); return;
            }
            String verdict = result.verdict;
            verdict = VisualTestHooks.verdict(service.getPackageName(), target.app, verdict, now);
            if (!"block".equals(verdict) && !"allow".equals(verdict)) {
                // Fail closed in people mode: a frame the face runtime could not answer must not
                // reveal a screen that was already covered or may contain a matching face.
                if (PersonBlur.on(personMode)) overlay.show(personFrame);
                fail();
                return;
            }
            samples++;
            retry.recovered();
            if (!PersonBlur.on(personMode)) verdicts.put(target.id, signature, verdict, now);
            cadence.finished(target.id, signature, now);
            long completedAt = SystemClock.elapsedRealtime();
            health.report(State.SAMPLED, completedAt, new PolicyReceipt(pendingPolicy, pendingSession,
                    target.id, PolicyReceipt.Kind.VISUAL_SAMPLE, PolicyReceipt.Result.PASSED, completedAt));
            if ("block".equals(verdict)) {
                overlay.hide();
                gate.invalidate();
                if (block.run(target)) interventions++;
            } else if (result.personBlur) {
                if (PersonBlur.BACK.equals(PersonBlur.response(service))) {
                    overlay.hide();
                    // One Back per page transition; a second matching page is handled on its
                    // next sample, so a slow browser is not sent back several times at once.
                    if (now - lastPersonBackAt >= 1200) {
                        lastPersonBackAt = now;
                        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                            personBacks++;
                        } else {
                            overlay.show(personFrame);
                        }
                    }
                } else {
                    overlay.show(personFrame);
                }
            } else {
                overlay.hide();
            }
        } catch (RuntimeException unavailable) { fail(); }
        finally {
            if (personFrame != null) personFrame.recycle();
            sampleEnded();
        }
    }

    private void clearPendingPersonFrame() {
        if (pendingPersonFrame != null) {
            pendingPersonFrame.recycle();
            pendingPersonFrame = null;
        }
    }

    /**
     * One native capture before the WebView exists. The detector is a presence gate, not the
     * final decision: it can only show the cover earlier. The full engine is started when the
     * capture finishes, so a miss changes nothing and a hit is re-checked normally.
     */
    private void startFastPresence(GuardWindows.Window target) {
        final int generation = ++fastGeneration;
        if (Build.VERSION.SDK_INT < 34) {
            finishFastPresence(generation);
            return;
        }
        busy = true;
        handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MILLIS);
        service.takeScreenshotOfWindow(target.id, service.getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback() {
            @Override public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                Bitmap hardware = null, copy = null, scaled = null;
                try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
                    if (closed || generation != fastGeneration) return;
                    handler.removeCallbacks(captureTimeout);
                    if (!interactive()
                            || GuardWindows.find(eligibleWindows(), target.id, target.app) == null) {
                        finishFastPresence(generation);
                        return;
                    }
                    hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                    if (hardware == null) { finishFastPresence(generation); return; }
                    if ((long) hardware.getWidth() * hardware.getHeight() > 16777216) {
                        finishFastPresence(generation);
                        return;
                    }
                    copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    double scale = Math.min(1.0, FAST_FRAME_EDGE
                            / (double) Math.max(copy.getWidth(), copy.getHeight()));
                    scaled = Bitmap.createScaledBitmap(copy,
                            Math.max(1, (int) (copy.getWidth() * scale)),
                            Math.max(1, (int) (copy.getHeight() * scale)), true);
                    fastPresence.check(scaled, found -> {
                        // In Women/Men mode this only buys time: the face is hidden while the
                        // gender model decides, and a non-matching face is revealed again.
                        if (found && !closed && generation == fastGeneration) overlay.showChecking();
                        finishFastPresence(generation);
                    });
                } catch (RuntimeException | OutOfMemoryError unavailable) {
                    finishFastPresence(generation);
                } finally {
                    if (scaled != null && scaled != copy) scaled.recycle();
                    if (copy != null) copy.recycle();
                    if (hardware != null) hardware.recycle();
                }
            }

            @Override public void onFailure(int error) {
                if (!closed && generation == fastGeneration) finishFastPresence(generation);
            }
        });
    }

    private void finishFastPresence(int generation) {
        if (closed || generation != fastGeneration) return;
        handler.removeCallbacks(captureTimeout);
        busy = false;
        startLoading(true);
    }

    /** Starts one WebView/model load. A generation token makes a reply from a runtime that was
     * replaced (screen off, window change, failure) unable to mark this one ready. */
    private void startLoading(boolean showLoading) {
        final int generation = ++loadGeneration;
        busy = true;
        health.report(showLoading ? State.LOADING : State.WAITING);
        final boolean faces = PersonBlur.on(personMode);
        final boolean[] nativeAttempt = {!faces && !nativeUnavailable};
        java.util.function.Consumer<String> onLoaded = verdict -> {
            if (closed || generation != loadGeneration) return;
            loaded = "ready".equals(verdict);
            busy = false;
            if (!loaded) {
                // A native model load failure must fall back to the packaged WebView engine; a
                // people-mode WebView load failure retries the same face-capable path.
                if (nativeAttempt[0]) nativeUnavailable = true;
                fail();
                return;
            }
            backend = classifier == null ? "none" : classifier.actualBackend();
            soon();
        };
        VisualRuntime runtime = null;
        if (faces) {
            try { runtime = new VisualClassifier(service, "wasm", personMode, onLoaded); }
            catch (RuntimeException | LinkageError unavailable) { runtime = null; }
        } else if (!nativeUnavailable) {
            try { runtime = new TfliteClassifier(service, onLoaded); }
            catch (RuntimeException | LinkageError unavailable) { nativeUnavailable = true; }
        }
        if (runtime == null && !faces) runtime = new VisualClassifier(service, onLoaded);
        if (runtime == null) { fail(); return; }
        classifier = runtime;
    }

    /** A failed load or frame: free the engine, then return after a bounded backoff, not a flat 30 s. */
    private void fail() {
        resetEngine(true);
        retryAfter = SystemClock.uptimeMillis() + retry.failed();
        health.report(State.ERROR);
    }

    private void resetEngine() { resetEngine(false); }

    private void resetEngine(boolean keepOverlay) {
        handler.removeCallbacks(captureTimeout);
        pendingFrame = null;
        clearPendingPersonFrame();
        if (!keepOverlay) overlay.hide();
        captureFailureStreak = 0;
        captureBackoffUntil = 0;
        pendingStarted = 0;
        pendingInferenceStart = 0;
        pendingPolicy = -1;
        fastGeneration++;
        loadGeneration++;
        gate.invalidate();
        verdicts.clear();
        if (classifier != null) classifier.close();
        classifier = null;
        loaded = false;
        busy = false;
        backend = "none";
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
        fastGeneration++;
        fastPresence.close();
        overlay.close();
        health.report(State.OFF);
    }
}
