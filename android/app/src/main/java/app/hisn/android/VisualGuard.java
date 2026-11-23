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
import java.util.Collections;
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
    /** First explicit frames a second capture did not repeat: the screen was uncovered, the app not closed. */
    static volatile long unconfirmed;
    /** Matching faces a second capture did not repeat: covered briefly, never sent back. */
    static volatile long personUnconfirmed;
    /** How long a first hit waits for the confirming capture before it counts as a new first hit. */
    static final long CONFIRM_MILLIS = 3000;
    static volatile long captures;
    static volatile long captureRequests;
    /** Long edge of the frame handed to the model. The model resizes its input to 224, and this
     * keeps the encode and inference cost down. No accuracy comparison between this edge and the
     * earlier 384 exists, so the change is about latency, not recall. */
    static final int FRAME_EDGE = 224;
    /** Face mode keeps more pixels so a small face is not lost before the 128 px detector. */
    static final int PERSON_FRAME_EDGE = 384;
    /** Women/men estimates: native FaceRes beside the faces-only WebView (HybridRuntime); the
     * all-WebView fallback uses the WebView's 64x64 classifier (decisions.js APPEARANCE_MODELS). */
    static final String APPEARANCE = "native", WEBVIEW_APPEARANCE = "oarriaga";
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
    private VisualRuntime classifier;
    /** The selected people cover mode; changing it reloads the runtime. */
    private String personMode;
    /** One downscaled copy kept only until the cover uses it; never written to disk. */
    private Bitmap pendingPersonFrame;
    /** The native runtime failed to load; use the packaged WebView engine for the rest of this instance. */
    private boolean nativeUnavailable;
    private boolean loaded, busy, closed, listening;
    private long retryAfter, nextPoll, captureBackoffUntil, lastCaptureRequestAt, lastPersonBackAt;
    private int captureFailureStreak;
    /** A window whose last frame looked explicit (or showed a matching face in Back mode) and is
     * covered until a second capture decides. -1 when none; ends at the matching deadline. */
    private int adultConfirmWindow = -1, personConfirmWindow = -1;
    private long adultConfirmUntil, personConfirmUntil;
    private int lastWindow = -1;
    /** The selected window responsible for the currently displayed cover. */
    private GuardWindows.Window covered;
    private VisualFrameGate.Frame pendingFrame;
    private long pendingStarted;
    private long pendingInferenceStart;
    /** Policy and guard session captured when this frame was submitted. */
    private long pendingPolicy = -1;
    private int pendingSession;
    private int loadGeneration;
    private static final long CAPTURE_TIMEOUT_MILLIS = 5000;
    private final Runnable captureTimeout = this::captureTimedOut;
    private final Runnable tick = this::poll;
    /** Prepares screenshots so the main thread stays free for accessibility events. */
    private final java.util.concurrent.ExecutorService shotWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "hisn-shot");
                thread.setDaemon(true);
                return thread;
            });
    /** The screen coming on brings the next poll forward; while it is off, polls are slow and never sample. */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (!closed) soon(); }
    };

    VisualGuard(AccessibilityService service, Block block) {
        SecureWindowApps.refresh();
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
        // A new screen may reuse the old window's id: treat it as unchecked and veil it now
        // (face-only blur), not on the next poll, until a screenshot has looked at it.
        faceOwner = null;
        veilNewScreen();
        // Do not hide the cover here: adding an accessibility overlay can itself produce a
        // window-state event. The next poll hides it when no selected window is eligible, and a
        // clean frame hides it when a selected app no longer contains a matching face.
        verdicts.clear();
        // A failure belongs to the old window; a new one is attempted at once.
        captureFailureStreak = 0;
        captureBackoffUntil = 0;
        clearConfirmations();
        cadence.windowChanged(SystemClock.uptimeMillis());
        soon();
    }

    /** A selected window scrolled or changed: sample sooner, never sooner than the cadence allows. */
    void activity() {
        if (closed) return;
        // The verdict cache checks the actual input pixels even when accessibility reports activity.
        cadence.activity(SystemClock.uptimeMillis());
        soon();
    }

    /** Scroll since the frame being checked was captured, for the window it came from. */
    private int captureWindow = -1, scrolledX, scrolledY;

    /** A selected window scrolled by (dx, dy) pixels. Blurred people move with the content right
     * away instead of waiting a third of a second for the next screenshot. When the distance is
     * unknown, the patches grow until that screenshot lands. */
    void scrolled(int window, int dx, int dy) {
        if (closed) return;
        boolean known = app.hisn.core.PeopleRegions.knownScroll(dx, dy);
        if (window == captureWindow && known) { scrolledX += dx; scrolledY += dy; }
        if (faceOwner != null && faceOwner.id == window) {
            // Face-only blur: patches follow the page and what scrolls into view is veiled until
            // checked. An unknown distance leaves the whole window unchecked.
            if (known) overlay.scrolled(-dx, -dy);
            else overlay.veilAll(bounds(window));
            return;
        }
        if (covered == null || covered.id != window || !overlay.facesShown()) return;
        if (known) overlay.shift(-dx, -dy);
        else overlay.moving();
    }

    /** Face-only blur: veils the active selected window right away when the screen changes. */
    private void veilNewScreen() {
        if (closed || covered != null || SystemClock.uptimeMillis() < retryAfter || !faceBlur()
                || !PersonBlur.on(personMode) || !interactive()) return;
        try {
            for (GuardWindows.Window window : eligibleWindows()) {
                if (!window.active) continue;
                android.graphics.Rect bounds = bounds(window.id);
                if (bounds == null) return;
                overlay.veilAll(bounds);
                faceOwner = window;
                return;
            }
        } catch (RuntimeException unavailable) { /* The next poll veils it instead. */ }
    }

    /** The window the face-only blur layer (patches and veils) belongs to; null when none. */
    private GuardWindows.Window faceOwner;

    /** Face-only blur: a screenshot of target was checked. Only what scrolled in since the
     * capture stays veiled. */
    private void faceChecked(GuardWindows.Window target) {
        android.graphics.Rect bounds = bounds(target.id);
        if (bounds == null) return;
        faceOwner = target;
        boolean since = captureWindow == target.id;
        overlay.checked(bounds, since ? -scrolledX : 0, since ? -scrolledY : 0);
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
        if (GuardPause.paused(service)) { eligibility = "PAUSED"; return eligible; }
        PolicyStore.Snapshot rules = PolicyStore.get(service).snapshot();
        if (!rules.visualEnabled) { eligibility = "OFF"; return eligible; }
        List<GuardWindows.Window> windows = TextGuardService.selectedWindows(service, excluded,
                name -> TextGuardService.scans(rules, name));
        if (windows == null) { eligibility = "NO_ROOT"; return eligible; }
        List<Integer> ids = new ArrayList<>();
        for (GuardWindows.Window window : windows) ids.add(window.id);
        cadence.retain(ids);
        verdicts.retain(ids);
        if (windows.isEmpty()) { eligibility = "NOT_SELECTED"; return eligible; }
        boolean typing = false;
        // No keyboard on screen means nobody is typing. Asking the app where its input focus is
        // waits on the app's own UI thread, which during a scroll can hold this thread (and every
        // accessibility event behind it) for over a second, so ask only when a keyboard shows.
        boolean keyboard = keyboardShown();
        for (GuardWindows.Window window : windows) {
            Boolean input = keyboard ? typing(window) : Boolean.FALSE;
            if (input == null) continue; // Gone or changed app since listing.
            if (input) typing = true; else eligible.add(window);
        }
        if (eligible.isEmpty() && typing) {
            health.report(State.INPUT);
            eligibility = "INPUT";
        } else eligibility = eligible.isEmpty() ? "NO_ROOT" : "SELECTED";
        return eligible;
    }

    /** A window awaiting its confirming capture goes first; otherwise windows take turns. */
    private GuardWindows.Window nextTarget(List<GuardWindows.Window> eligible, long now) {
        int waiting = now < adultConfirmUntil ? adultConfirmWindow
                : now < personConfirmUntil ? personConfirmWindow : -1;
        if (waiting != -1) for (GuardWindows.Window window : eligible) if (window.id == waiting) return window;
        return GuardWindows.after(eligible, lastWindow);
    }

    /** An explicit frame. A strong score, or the second explicit capture of the same window within
     * {@link #CONFIRM_MILLIS}, closes the app and starts the rest period. A first ordinary hit only
     * covers the screen and asks for the next capture at Android's floor, so a frame caught
     * mid-scroll, mid-transition or half-loaded cannot lock the person out on its own. */
    private void adultHit(GuardWindows.Window target, boolean strong, long now) {
        boolean confirmed = strong || (adultConfirmWindow == target.id && now < adultConfirmUntil);
        if (!confirmed) {
            adultConfirmWindow = target.id;
            adultConfirmUntil = now + CONFIRM_MILLIS;
            if (mayCover(target)) {
                covered = target;
                overlay.showConfirming();
            }
            confirmSoon(now);
            return;
        }
        adultConfirmWindow = -1;
        adultConfirmUntil = 0;
        hideCoverFor(target);
        gate.invalidate();
        if (block.run(target)) interventions++;
    }

    /** A clean frame for this window ends a pending adult confirmation without acting. */
    private void adultClear(GuardWindows.Window target) {
        if (adultConfirmWindow != target.id) return;
        adultConfirmWindow = -1;
        adultConfirmUntil = 0;
        unconfirmed++;
    }

    /** The confirming capture is taken as soon as Android allows one for this window. */
    private void confirmSoon(long now) {
        schedule(Math.max(VisualCadence.POLL_FLOOR_MILLIS,
                lastCaptureRequestAt + VisualCadence.FAST_MILLIS - now));
    }

    /** True while that window holds input focus and an input field in it is focused, including
     * passwords (rule in core GuardWindows.typing); null when it is gone. */
    private boolean keyboardShown() {
        try {
            for (android.view.accessibility.AccessibilityWindowInfo window : service.getWindows()) {
                if (window.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true;
            }
            return false;
        } catch (RuntimeException unknown) {
            return true; // Unknown: check focus the slow way, as before.
        }
    }

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
        if (!interactive) {
            hideCover();
            clearConfirmations();
        }
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
            if (busy && covered == null) return;
            if (!busy) health.report(State.WAITING);
            // Nothing is sampled while the screen is off; while it is on the model stays warm.
            List<GuardWindows.Window> eligible = interactive ? eligibleWindows() : Collections.emptyList();
            GuardWindows.Window target = nextTarget(eligible, now);
            if (covered != null && GuardWindows.find(eligible, covered.id, covered.app) == null) hideCover();
            if (faceOwner != null && GuardWindows.find(eligible, faceOwner.id, faceOwner.app) == null) hideCover();
            // Face-only blur: a window no screenshot has checked yet is veiled until one does.
            if (target != null && covered == null && now >= retryAfter && faceBlur() && PersonBlur.on(personMode)
                    && (faceOwner == null || faceOwner.id != target.id)) {
                android.graphics.Rect bounds = bounds(target.id);
                if (bounds != null) {
                    overlay.veilAll(bounds);
                    faceOwner = target;
                }
            }
            if (target == null) clearConfirmations();
            if (busy) return;
            // Cover-mode users can keep the initial screen hidden while the engine loads.
            if (!loaded && target != null && covered == null && PersonBlur.on(personMode)
                    && PersonBlur.COVER.equals(PersonBlur.response(service))) {
                covered = target;
                overlay.showChecking();
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
            // Read on the main thread now; the screenshot is prepared on the worker below.
            final boolean people = PersonBlur.on(personMode);
            final boolean faceBlur = people && faceBlur();
            final boolean hybrid = classifier instanceof HybridRuntime;
            final boolean nativeAdult = classifier instanceof TfliteClassifier || hybrid;
            captureWindow = target.id;
            scrolledX = 0;
            scrolledY = 0;
            service.takeScreenshotOfWindow(target.id, shotWorker, new AccessibilityService.TakeScreenshotCallback() {
                /** On the worker: the full-size copy and scaling take up to half a second on a slow
                 * phone. Off the main thread, scroll events keep arriving and blurred faces keep
                 * following the page while this runs. */
                @Override public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                    Shot shot;
                    try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
                        shot = Shot.prepare(buffer, screenshot.getColorSpace(), people, faceBlur, hybrid, nativeAdult);
                    } catch (Throwable unavailable) { shot = null; }
                    final Shot ready = shot;
                    if (!handler.post(() -> shotReady(frame, target, ready, people, faceBlur)) && ready != null) ready.recycle();
                }
                @Override public void onFailure(int error) {
                    handler.post(() -> failed(error));
                }
                private void failed(int error) {
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
                        if (error == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                                && currentWindow(frame, target) == null) {
                            discarded++;
                            health.report(State.WAITING);
                            sampleEnded();
                            return;
                        }
                        if (error == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                                && !SecureWindowApps.covered(service, target.app,
                                        PolicyStore.get(service).snapshot().scannedApps.contains(target.app))) {
                            // A bank, TAN app or password manager read only because every app is,
                            // or a private app the owner marked safe: leave it usable.
                            hideCoverFor(target);
                            long wait = Math.max(VisualCadence.POLL_FLOOR_MILLIS, 2000);
                            captureBackoffUntil = SystemClock.uptimeMillis() + wait;
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
                            covered = target;
                            overlay.showProtected(() -> {
                                long left = SecureWindowApps.allow(service, target.app);
                                if (left > 0) return left;
                                hideCoverFor(target);
                                return 0;
                            });
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
                        captureUnavailable();
                    }
                }
            });
        } catch (RuntimeException unavailable) { busy = false; fail(); }
    }

    /** Android can stall a screenshot independently of the model. Keep the loaded runtime
     * warm, discard the late callback, and retry capture after the same bounded backoff. */
    private void captureTimedOut() {
        if (closed || pendingFrame == null) return;
        pendingFrame = null;
        busy = false;
        clearPendingPersonFrame();
        captureFailures++;
        captureUnavailable();
    }

    private void captureUnavailable() {
        captureFailureStreak = Math.min(captureFailureStreak + 1, 4);
        captureBackoffUntil = SystemClock.uptimeMillis() + Math.min(4000, 500L << captureFailureStreak);
        health.report(State.UNREADABLE);
        sampleEnded();
    }

    /** The optional picture view. The adult check also receives the whole screen for context. */
    /** One screenshot made ready for the models, off the main thread. Owns its bitmaps. */
    static final class Shot {
        Bitmap scaled, crop, context, person;
        long signature, key;
        boolean unreadable;
        int edge;

        /** Only reached from the Android 14 screenshot callback. */
        @android.annotation.TargetApi(34)
        static Shot prepare(HardwareBuffer buffer, android.graphics.ColorSpace space, boolean people,
                boolean faceBlur, boolean hybrid, boolean nativeAdult) {
            Shot shot = new Shot();
            Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, space);
            if (hardware == null || (long) hardware.getWidth() * hardware.getHeight() > 16777216) {
                if (hardware != null) hardware.recycle();
                shot.unreadable = true;
                return shot;
            }
            Bitmap copy = null;
            try {
                copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
                shot.edge = people ? PERSON_FRAME_EDGE : FRAME_EDGE;
                shot.scaled = scaleFrame(copy, shot.edge);
                if (nativeAdult) shot.crop = adultPictureFrame(copy);
                shot.context = hybrid ? scaleFrame(copy, FRAME_EDGE) : shot.scaled;
                // The face-only blur keeps a small unblurred copy for its patches' colors;
                // the cover keeps only its 32-pixel blur.
                if (people) shot.person = faceBlur ? FaceBlurLayer.reduce(shot.scaled) : VisualOverlay.blur(shot.scaled);
                shot.signature = signature(shot.scaled);
                // The cache key covers the pixels of both inputs when a picture was isolated.
                shot.key = people ? 0 : fingerprint(shot.scaled, shot.crop);
                // Bitmaps the scaling returned unchanged are the copy itself; keep those.
                if (shot.crop == copy || shot.scaled == copy || shot.context == copy) copy = null;
                return shot;
            } catch (RuntimeException | OutOfMemoryError unavailable) {
                shot.recycle();
                throw unavailable;
            } finally {
                if (copy != null) copy.recycle();
                hardware.recycle();
            }
        }

        void recycle() {
            if (crop != null && crop != scaled && crop != context) crop.recycle();
            if (context != null && context != scaled) context.recycle();
            if (scaled != null) scaled.recycle();
            if (person != null) person.recycle();
            crop = context = scaled = person = null;
        }
    }

    private void shotReady(VisualFrameGate.Frame frame, GuardWindows.Window target, Shot shot,
            boolean people, boolean faceBlur) {
        try {
            if (closed || pendingFrame != frame) return;
            handler.removeCallbacks(captureTimeout);
            captures++;
            captureFailureStreak = 0;
            captureBackoffUntil = 0;
            lastCaptureMillis = SystemClock.uptimeMillis() - pendingStarted;
            if (currentWindow(frame, target) == null) {
                discarded++; pendingFrame = null; busy = false; clearPendingPersonFrame();
                health.report(State.WAITING);
                sampleEnded();
                return;
            }
            if (shot == null) { busy = false; fail(); return; }
            if (shot.unreadable) { unreadableFrame(); return; }
            lastFrameEdge = shot.edge;
            if (people) { pendingPersonFrame = shot.person; shot.person = null; }
            if (classifier instanceof HybridRuntime) ((HybridRuntime) classifier).bodies(faceBlur);
            Bitmap scaled = shot.scaled, crop = shot.crop, context = shot.context;
            long signature = shot.signature, key = shot.key;
            String cached = people ? null : verdicts.get(target.id, key, SystemClock.uptimeMillis());
            if (cached != null) {
                cached(frame, target, signature, cached);
                return;
            }
            lastInferenceMillis = 0;
            pendingInferenceStart = SystemClock.uptimeMillis();
            java.util.function.Consumer<VisualResult> callback =
                    result -> decided(frame, target, signature, key, result);
            if (classifier instanceof HybridRuntime) {
                ((HybridRuntime) classifier).inspect(crop == null ? context : crop,
                        crop == null ? null : context, scaled, callback);
            } else if (classifier instanceof TfliteClassifier) {
                ((TfliteClassifier) classifier).inspect(crop == null ? scaled : crop,
                        crop == null ? null : scaled, callback);
            } else {
                classifier.inspect(scaled, callback);
            }
        } catch (RuntimeException | OutOfMemoryError unavailable) { busy = false; fail(); }
        finally { if (shot != null) shot.recycle(); }
    }

    static Bitmap adultPictureFrame(Bitmap copy) {
        Bitmap picture = picture(copy);
        if (picture == null) return null;
        Bitmap scaled = null;
        try {
            scaled = scaleFrame(picture, FRAME_EDGE);
            return scaled;
        } finally {
            if (picture != scaled && picture != copy) picture.recycle();
        }
    }

    static Bitmap scaleFrame(Bitmap source, int edge) {
        double scale = Math.min(1.0, edge / (double) Math.max(source.getWidth(), source.getHeight()));
        return Bitmap.createScaledBitmap(source, Math.max(1, (int) (source.getWidth() * scale)),
                Math.max(1, (int) (source.getHeight() * scale)), true);
    }

    /** The picture inside the frame when it sits on a plain background (core ContentBox), or null. */
    private static Bitmap picture(Bitmap frame) {
        int width = frame.getWidth(), height = frame.getHeight();
        double sample = 96.0 / Math.max(width, height);
        if (sample >= 1) return null;
        int sampleWidth = Math.max(8, (int) Math.round(width * sample)), sampleHeight = Math.max(8, (int) Math.round(height * sample));
        Bitmap small = Bitmap.createScaledBitmap(frame, sampleWidth, sampleHeight, true);
        int[] pixels = new int[sampleWidth * sampleHeight];
        try {
            small.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);
            int[] box = app.hisn.core.ContentBox.find(pixels, sampleWidth, sampleHeight);
            if (box == null) return null;
            int left = Math.max(0, (int) Math.floor(box[0] * (double) width / sampleWidth));
            int top = Math.max(0, (int) Math.floor(box[1] * (double) height / sampleHeight));
            int right = Math.min(width, (int) Math.ceil(box[2] * (double) width / sampleWidth));
            int bottom = Math.min(height, (int) Math.ceil(box[3] * (double) height / sampleHeight));
            if (right - left < 16 || bottom - top < 16) return null;
            return Bitmap.createBitmap(frame, left, top, right - left, bottom - top);
        } finally {
            java.util.Arrays.fill(pixels, 0);
            if (small != frame) small.recycle();
        }
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

    /** Every pixel of the downscaled frame, for the verdict cache (VisualVerdictCache.fingerprint). */
    private static long fingerprint(Bitmap frame) {
        int width = frame.getWidth(), height = frame.getHeight();
        int[] pixels = new int[width * height];
        try {
            frame.getPixels(pixels, 0, width, 0, 0, width, height);
            return VisualVerdictCache.fingerprint(pixels, width);
        } finally {
            java.util.Arrays.fill(pixels, 0);
        }
    }

    private static long fingerprint(Bitmap screen, Bitmap crop) {
        long whole = fingerprint(screen);
        return crop == null ? whole : (whole * 0x100000001B3L) ^ fingerprint(crop);
    }

    /** A capture Android returned but that cannot be read: end this sample cleanly so the next
     * poll is not left waiting on a frame that will never decide. */
    private void unreadableFrame() {
        pendingFrame = null;
        busy = false;
        clearPendingPersonFrame();
        health.report(State.UNREADABLE);
        sampleEnded();
    }

    private GuardWindows.Window currentWindow(VisualFrameGate.Frame frame, GuardWindows.Window target) {
        // A sample finishing after the screen went off, or after its window closed, changed app,
        // focused an input or stopped being selected, is discarded, never acted on.
        if (!interactive() || !gate.accepts(frame, target.app, target.id, true, SystemClock.uptimeMillis())
                || !personMode.equals(PersonBlur.mode(service))) return null;
        return GuardWindows.find(eligibleWindows(), target.id, target.app);
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
            if (currentWindow(frame, target) == null) { discarded++; health.report(State.WAITING); return; }
            String actual = verdict;
            verdict = VisualTestHooks.verdict(service.getPackageName(), target.app, verdict, now);
            if (!"block".equals(verdict) && !"allow".equals(verdict)) { fail(); return; }
            reused++;
            cadence.finished(target.id, signature, now);
            long completedAt = SystemClock.elapsedRealtime();
            health.report(State.SAMPLED, completedAt, new PolicyReceipt(pendingPolicy, pendingSession,
                    target.id, PolicyReceipt.Kind.VISUAL_SAMPLE, PolicyReceipt.Result.PASSED, completedAt));
            // The same pixels again: a pending first hit on this window is confirmed by a still screen.
            if ("block".equals(verdict)) adultHit(target, !verdict.equals(actual), now);
            else { adultClear(target); hideCoverFor(target); }
        } catch (RuntimeException unavailable) { fail(); }
        finally { sampleEnded(); }
    }

    private void decided(VisualFrameGate.Frame frame, GuardWindows.Window target, long signature,
            long key, VisualResult result) {
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
            GuardWindows.Window live = currentWindow(frame, target);
            if (live == null) {
                discarded++; health.report(State.WAITING); return;
            }
            String verdict = result.verdict;
            verdict = VisualTestHooks.verdict(service.getPackageName(), target.app, verdict, now);
            // The fixture's one-shot synthetic verdict cannot repeat, so it acts like a strong score.
            boolean strong = result.strong || !verdict.equals(result.verdict);
            if (!"block".equals(verdict) && !"allow".equals(verdict)) {
                // Keep an existing cover. Cover mode also hides an unresolved first sample.
                if (PersonBlur.on(personMode) && PersonBlur.COVER.equals(PersonBlur.response(service))) {
                    showPeople(live, personFrame);
                }
                fail();
                return;
            }
            samples++;
            retry.recovered();
            if (!PersonBlur.on(personMode)) verdicts.put(target.id, key, verdict, now);
            cadence.finished(target.id, signature, now);
            long completedAt = SystemClock.elapsedRealtime();
            health.report(State.SAMPLED, completedAt, new PolicyReceipt(pendingPolicy, pendingSession,
                    target.id, PolicyReceipt.Kind.VISUAL_SAMPLE, PolicyReceipt.Result.PASSED, completedAt));
            if ("block".equals(verdict)) {
                adultHit(live, strong, now);
                return;
            }
            adultClear(target);
            if (result.personBlur) {
                personHit(live, personFrame, result.blurBoxes, now);
            } else {
                if (personConfirmWindow == target.id) {
                    personConfirmWindow = -1;
                    personConfirmUntil = 0;
                    personUnconfirmed++;
                }
                hideCoverFor(target);
                if (faceBlur()) faceChecked(live);
            }
        } catch (RuntimeException unavailable) { fail(); }
        finally {
            if (personFrame != null) personFrame.recycle();
            sampleEnded();
        }
    }

    private boolean faceBlur() {
        return PersonBlur.FACES.equals(PersonBlur.response(service));
    }

    private void personHit(GuardWindows.Window target, Bitmap personFrame, float[] regions, long now) {
        if (faceBlur()) {
            android.graphics.Rect bounds = regions == null || regions.length == 0 ? null : bounds(target.id);
            if (bounds != null && mayCover(target)) {
                covered = target;
                overlay.showFaces(bounds, regions, personFrame);
                // The page may have scrolled while the models ran: start where the people are now.
                if (captureWindow == target.id && (scrolledX != 0 || scrolledY != 0)) {
                    overlay.shift(-scrolledX, -scrolledY);
                }
                faceChecked(target);
                return;
            }
            // Where the people are is unknown: cover the whole screen rather than show them.
            Bitmap blurred = personFrame == null ? null : VisualOverlay.blur(personFrame);
            try { showPeople(target, blurred); }
            finally { if (blurred != null) blurred.recycle(); }
            return;
        }
        showPeople(target, personFrame);
        // Android Back is global. Only the application window receiving navigation can be sent back.
        if (!target.active || !target.focused || target.pip
                || !PersonBlur.BACK.equals(PersonBlur.response(service))) {
            if (personConfirmWindow == target.id) {
                personConfirmWindow = -1;
                personConfirmUntil = 0;
            }
            return;
        }
        if (personConfirmWindow != target.id || now >= personConfirmUntil) {
            personConfirmWindow = target.id;
            personConfirmUntil = now + CONFIRM_MILLIS;
            confirmSoon(now);
            return;
        }
        if (now - lastPersonBackAt < 1200) return;
        lastPersonBackAt = now;
        personConfirmWindow = -1;
        personConfirmUntil = 0;
        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) personBacks++;
    }

    /** The window's place on screen, or null when it is gone. */
    private android.graphics.Rect bounds(int id) {
        try {
            for (android.view.accessibility.AccessibilityWindowInfo window : service.getWindows()) {
                if (window.getId() != id) continue;
                android.graphics.Rect rect = new android.graphics.Rect();
                window.getBoundsInScreen(rect);
                return rect.isEmpty() ? null : rect;
            }
        } catch (RuntimeException unavailable) { /* Treated as unknown. */ }
        return null;
    }

    private boolean mayCover(GuardWindows.Window target) {
        return covered == null || (covered.id == target.id && covered.app.equals(target.app));
    }

    private void showPeople(GuardWindows.Window target, Bitmap snapshot) {
        if (!mayCover(target)) return;
        covered = target;
        overlay.show(snapshot);
    }

    /** A clean frame can only release the cover for its own window. */
    private void hideCoverFor(GuardWindows.Window cleared) {
        if (covered != null && covered.id == cleared.id && covered.app.equals(cleared.app)) hideCover();
    }

    private void hideCover() {
        covered = null;
        faceOwner = null;
        overlay.hide();
    }

    private void clearConfirmations() {
        adultConfirmWindow = personConfirmWindow = -1;
        adultConfirmUntil = personConfirmUntil = 0;
    }

    private void clearPendingPersonFrame() {
        if (pendingPersonFrame != null) {
            pendingPersonFrame.recycle();
            pendingPersonFrame = null;
        }
    }

    /** Starts one WebView/model load. A generation token makes a reply from a runtime that was
     * replaced (screen off, window change, failure) unable to mark this one ready. */
    private void startLoading(boolean showLoading) {
        final int generation = ++loadGeneration;
        busy = true;
        health.report(showLoading ? State.LOADING : State.WAITING);
        final boolean faces = PersonBlur.on(personMode);
        final boolean[] nativeAttempt = {!nativeUnavailable};
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
            // Native adult check plus a faces-only WebView. A failed native or hybrid load sets
            // nativeUnavailable, so the next attempt uses the all-WebView path.
            if (!nativeUnavailable) {
                // FaceRes estimates women/men: far fewer wrong covers than the 64x64 classifier
                // (docs/ANDROID_VISUAL_ACCURACY_2026-11-19.md).
                try { runtime = new HybridRuntime(service, personMode, APPEARANCE, false, onLoaded); }
                catch (RuntimeException | LinkageError unavailable) { nativeUnavailable = true; nativeAttempt[0] = false; }
            }
            if (runtime == null) {
                try { runtime = new VisualClassifier(service, "wasm", personMode, false, WEBVIEW_APPEARANCE, false, onLoaded); }
                catch (RuntimeException | LinkageError unavailable) { runtime = null; }
            }
        } else {
            if (!nativeUnavailable) {
                try { runtime = new TfliteClassifier(service, onLoaded); }
                catch (RuntimeException | LinkageError unavailable) { nativeUnavailable = true; nativeAttempt[0] = false; }
            }
            if (runtime == null) runtime = new VisualClassifier(service, onLoaded);
        }
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
        if (!keepOverlay) hideCover();
        captureFailureStreak = 0;
        captureBackoffUntil = 0;
        pendingStarted = 0;
        pendingInferenceStart = 0;
        pendingPolicy = -1;
        clearConfirmations();
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
        shotWorker.shutdown();
        handler.removeCallbacksAndMessages(null);
        if (listening) {
            try { service.unregisterReceiver(screenOn); } catch (RuntimeException alreadyGone) { /* Nothing to do. */ }
            listening = false;
        }
        resetEngine();
        overlay.close();
        health.report(State.OFF);
    }
}
