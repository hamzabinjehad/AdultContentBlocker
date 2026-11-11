package app.hisn.android;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import app.hisn.core.GuardWindows;
import app.hisn.core.InspectionHealth;
import app.hisn.core.PolicyReceipt;
import app.hisn.core.InterventionRepeat;
import app.hisn.core.LabelScan;
import app.hisn.core.ProtectionLock;
import app.hisn.core.SelfProtectionScreens;
import app.hisn.core.SelfProtectionScreens.Guard;
import app.hisn.core.ShortVideoSurfaces;
import app.hisn.core.VisibleTextSample;

/** Explicitly selected apps only, in every application window on screen (split screen, pop-up and
 * picture-in-picture included). Event text is inspected transiently for an immediate word match;
 * screen content is never retained. */
public final class TextGuardService extends AccessibilityService {
    public static volatile boolean connected;
    /** MainActivity asks a live service instance to re-run its idempotent setup after an update. */
    static final String ACTION_REARM = "app.hisn.android.action.REARM_GUARD";
    /** Increments for every guard instance; a receipt from an earlier one is stale. */
    public static volatile int serviceSession;
    /** Setup ran in this service instance; reset when Android unbinds it. */
    private boolean started;
    /** The rearm receiver is registered in onCreate and must be removed in onDestroy. */
    private boolean rearmRegistered;
    /** Setup finished and events are handled; the boot shield waits for this. */
    static volatile boolean ready;
    /** Set by an incomplete or failed inspection; cleared by a later complete one. */
    public static final InspectionHealth inspection = new InspectionHealth();
    // Ephemeral outcome only: never screen text, matched rules or app identities.
    static volatile String inspectionOutcome = "not-run";
    static volatile long inspectedScreens;
    static volatile long wordInterventions;
    static volatile long cooldownEnforcements;
    static volatile long periodicInspections;
    static volatile long scheduleInterventions;
    static volatile long timeLimitInterventions;
    static volatile long shortVideoInterventions;
    static volatile long holdEnforcements;
    static volatile long homeFailures;
    static volatile long inspectionFailures;
    static volatile long selfProtectionInterventions;
    static volatile long repeatHoldOffs;
    /** Debug-only timing of the last full tree scan, in milliseconds. */
    static volatile long lastInspectionMillis;
    /** Word/adult matches made from the accessibility event itself, before a tree walk. */
    static volatile long fastTextInterventions;
    /** Feedback interrupts do not stop protection; used by the harmless live regression. */
    static volatile long feedbackInterrupts;
    /** A full tree scan is forced at least this often while a selected window stays on screen. */
    private static final long FORCED_TEXT_SCAN_MILLIS = 3000;
    /** A scan that has not reported in this long is a stuck Binder walk, not a slow screen. */
    private static final long SCAN_TIMEOUT_MILLIS = 20000;
    /** While the signed adult vocabulary is still loading, retry the text pass quickly. */
    private static final long ADULT_LOAD_RETRY_MILLIS = 250;
    /** After a word or image block, reopening the same app within this period returns Home again. */
    static final long BLOCK_HOLD_MILLIS = 60000;

    /** One receipt path: every scan result carries the monotonic time it happened. */
    private void recordInspection(InspectionHealth.Outcome outcome) {
        inspection.record(outcome, -1);
    }

    /**
     * Record a current app/text decision with the policy, session and scan generation it
     * belongs to. The text-list component is part of the layer identity, so a vocabulary
     * change cannot inherit the previous pass's receipt.
     */
    private void recordInspection(InspectionHealth.Outcome outcome, PolicyReceipt.Kind kind) {
        PolicyReceipt receipt = null;
        if (outcome == InspectionHealth.Outcome.COMPLETE && kind != null) {
            try {
                receipt = new PolicyReceipt(PolicyEvidence.text(PolicyStore.get(this)), serviceSession,
                        scanGeneration, kind, PolicyReceipt.Result.PASSED,
                        SystemClock.elapsedRealtime());
            } catch (RuntimeException unreadable) {
                // No receipt is better than one for unknown inputs; the outcome still records.
            }
        }
        inspection.record(outcome, receipt);
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingPackage;
    private long lastIntervention;
    private boolean inspectPending, discoverPending;
    private String heldPackage;
    private long heldUntil;
    private boolean heldWord, heldVisual;
    private int rootRetries;
    private Set<String> excluded;
    private VisualGuard visual;
    private View guardOverlay;
    private WindowManager guardOverlayManager;
    /** A window Home left on screen (pop-up, refused PiP dismiss) is not acted on again at once. */
    private final InterventionRepeat repeat = new InterventionRepeat();
    /** The selected windows of the last inspection; a windows-changed event with the same set
     * leaves them to the heartbeat and app events instead of inspecting again. */
    private String inspectedKey;
    private final Runnable inspect = this::inspectWindow;
    private final Runnable discover = this::inspectCurrentSelectedWindow;
    private final Runnable checkpoint = this::checkpointCommitment;
    private final Runnable selfCheck = this::checkSelfProtection;
    private final Runnable heartbeat = () -> {
        rootRetries = 5;
        periodicInspections++;
        inspectWindow();
    };
    private boolean pendingWordReason;
    private boolean pendingScheduleReason;
    private boolean pendingLimitReason;
    private boolean pendingVisualReason;
    private boolean pendingShortVideoReason;
    /** Set by a selected app's event; cleared only after a tree scan actually ran. */
    private boolean textDirty = true;
    private long lastTextScanAt;
    /** The tree walk runs here, not on the accessibility service's main thread. Android resolves
     * every getChild() across a Binder, so a large screen can otherwise stall the UI for a second. */
    private ExecutorService scanner = newScanner();
    /** Main-thread watchdog for the scan in flight; replaced for a new executor after a timeout. */
    private Runnable scanTimeout;
    private volatile boolean destroyed;
    /** The scan in flight (0 for none) and the next id; a stale worker result is ignored. */
    private int activeScan, nextScanId;
    /** A window-state change makes a scan result for the old screen unusable. */
    private int scanGeneration;
    /** Android usage totals are system-maintained and change slowly; one read per app per 15 s
     *  bounds the query cost while a limited app is on screen. */
    private static final long LIMIT_CACHE_MILLIS = 15000;
    private final java.util.HashMap<String, Long> limitUsed = new java.util.HashMap<>();
    private long limitCacheAt;
    private int selfRetries;
    private boolean selfCheckQueued;
    private java.util.List<String> selfNames;
    /** What the last self-protection check found, until the shield page is launched. */
    private Guard pendingGuard = Guard.NONE;
    private final Runnable showRestriction = () -> {
        try {
            startActivity(new Intent(this, RestrictionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra("word", pendingWordReason).putExtra("schedule", pendingScheduleReason)
                    .putExtra("limit", pendingLimitReason)
                    .putExtra("visual", pendingVisualReason).putExtra("shortvideo", pendingShortVideoReason));
        } catch (RuntimeException unavailable) {
            recordInspection(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "restriction-screen-unavailable";
        }
    };
    /** Direct build: the full-screen page that replaces the closed settings screen. */
    private final Runnable showShield = () -> {
        Guard guard = pendingGuard;
        pendingGuard = Guard.NONE;
        removeGuardOverlay();
        if (guard == Guard.NONE) return;
        Intent shield = new Intent(this, ShieldActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(ShieldActivity.EXTRA_GUARD, guard.name());
        try {
            startActivity(shield);
        } catch (RuntimeException unavailable) {
            // Never leave a closed screen with no explanation.
            try {
                startActivity(new Intent(this, RestrictionActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        .putExtra("selfprotect", true));
            } catch (RuntimeException alsoUnavailable) {
                recordInspection(InspectionHealth.Outcome.INCOMPLETE);
                inspectionOutcome = "shield-screen-unavailable";
            }
        }
    };

    private final BroadcastReceiver rearm = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            connected = true;
            startGuard();
            if (visual == null) visual = newVisualGuard();
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(ACTION_REARM);
        // The flagged overload exists only on API 33+; the package-scoped intent is not exported.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rearm, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(rearm, filter);
        rearmRegistered = true;
        // The system created this service because it is bound. Android does not
        // always repeat onServiceConnected after an update or rebind, and waiting
        // for a callback that never comes left `connected` false while the service
        // was in fact running. Prove the live instance here.
        connected = true;
        startGuard();
    }

    @Override protected void onServiceConnected() {
        connected = true;
        startGuard();
    }

    /**
     * Sets the guard up once. Android normally calls {@link #onServiceConnected}, but a service that
     * the system rebound after an accessibility toggle has been seen alive and bound without a fresh
     * callback, which left {@code connected} false and the guard unarmed while the service was in
     * fact receiving events. The first event therefore retries the setup instead of trusting the
     * callback alone; every step here is idempotent.
     */
    private void startGuard() {
        if (started) return;
        started = true;
        serviceSession++;
        handler.removeCallbacks(checkpoint);
        handler.post(checkpoint);
        try {
            // The uninstall guard needs nothing else, so it is armed before image setup.
            excluded = ChosenApps.excluded(this);
            if (PolicyStore.get(this).snapshot().adultWordsEnabled) AdultPolicyAssets.ensureLoaded(this);
            textDirty = true;
            ready = true;
            // Keeps the boot shield's pre-unlock copy current. A running
            // protection lock arms the removal guard even when the switch is off.
            try {
                PolicyStore store = PolicyStore.get(this);
                BootShieldService.mirror(this, ProtectionLock.bootShieldArmed(
                        BuildConfig.SELF_PROTECTION, store.uninstallLock(), store.remainingMillis()));
            } catch (RuntimeException unreadable) {
                // Leave the previous pre-unlock copy alone: unreadable settings are
                // not evidence that protection was turned off.
            }
            // A removal screen opened before Android bound this service is left now.
            if (BuildConfig.SELF_PROTECTION) { selfRetries = 3; handler.post(selfCheck); }
            if (visual != null) visual.close();
            visual = newVisualGuard();
            inspectCurrentSelectedWindow();
        } catch (RuntimeException unavailable) {
            recordInspection(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "setup-unavailable";
        }
    }

    private VisualGuard newVisualGuard() {
        return new VisualGuard(this, window -> intervene(window.app, window.id, window.active, false, false, true, false, false));
    }

    /** Saves commitment progress once a minute so a restart loses little counted time. */
    private void checkpointCommitment() {
        try { PolicyStore.get(this).checkpoint(); } catch (RuntimeException unsaved) { /* Retry next minute. */ }
        ScreenGray.reassert(this);
        enforceTimedRules();
        handler.postDelayed(checkpoint, 60000);
    }

    /** A scheduled window or a daily time limit can begin or be reached while a
     *  static app is on screen and produce no event. One metadata-only sweep a
     *  minute closes that gap. */
    private void enforceTimedRules() {
        if (excluded == null || inspectPending) return;
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            if (!rules.schedule.isConfigured() && !rules.timeLimits.configured()) return;
            List<GuardWindows.Window> windows = selectedWindows(this, excluded, app -> isSelected(rules, app));
            if (windows == null || windows.isEmpty()) return;
            pendingPackage = windows.get(0).app;
            rootRetries = 5;
            inspectPending = true;
            handler.removeCallbacks(heartbeat);
            handler.post(inspect);
        } catch (RuntimeException unavailable) {
            recordInspection(InspectionHealth.Outcome.INCOMPLETE);
        }
    }

    /** Rebinding, or a window opening (picture-in-picture, split screen), should not require an event
     * from the app itself. Metadata only until a selected app's window is found. */
    private void inspectCurrentSelectedWindow() {
        discoverPending = false;
        if (excluded == null || inspectPending) return;
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            List<GuardWindows.Window> windows = selectedWindows(this, excluded, app -> isSelected(rules, app));
            if (windows == null || windows.isEmpty() || GuardWindows.key(windows).equals(inspectedKey)) return;
            pendingPackage = windows.get(0).app;
            textDirty = true;
            rootRetries = 5;
            inspectPending = true;
            handler.removeCallbacks(heartbeat);
            handler.post(inspect);
        } catch (RuntimeException unavailable) {
            recordInspection(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "windows-unavailable";
        }
    }

    /** Application windows of selected apps, active first (rule and tests in core {@link GuardWindows}),
     * or null when no window could be read at all. Reads only each window root's package name. */
    static List<GuardWindows.Window> selectedWindows(AccessibilityService service, Set<String> excluded,
            Predicate<String> selected) {
        List<GuardWindows.Window> all = new ArrayList<>();
        for (AccessibilityWindowInfo window : service.getWindows()) {
            boolean application = window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION;
            AccessibilityNodeInfo root = application ? window.getRoot() : null;
            try {
                all.add(new GuardWindows.Window(window.getId(), packageOf(root), application, window.isActive(),
                        window.isFocused(), window.isInPictureInPictureMode()));
            } finally { if (root != null) root.recycle(); }
        }
        if (all.isEmpty()) {
            // No window list from Android: only the active window, as before 0.5.5.
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) return null;
            try {
                all.add(new GuardWindows.Window(root.getWindowId(), packageOf(root), true, true, true, false));
            } finally { root.recycle(); }
        }
        return GuardWindows.select(all, selected, excluded);
    }

    /** That window's root, only while it still belongs to the same app; the caller releases it. */
    static AccessibilityNodeInfo windowRoot(AccessibilityService service, GuardWindows.Window target) {
        AccessibilityNodeInfo root = null;
        for (AccessibilityWindowInfo window : service.getWindows()) {
            if (window.getId() == target.id) { root = window.getRoot(); break; }
        }
        if (root == null) {
            root = service.getRootInActiveWindow();
            if (root != null && root.getWindowId() != target.id) { root.recycle(); root = null; }
        }
        if (root != null && !target.app.equals(packageOf(root))) { root.recycle(); root = null; }
        return root;
    }

    private static String packageOf(AccessibilityNodeInfo root) {
        return root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
    }

    private static boolean hasApp(List<GuardWindows.Window> windows, String app) {
        for (GuardWindows.Window window : windows) if (window.app.equals(app)) return true;
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // An event proves this instance is alive even when the bind callback was not repeated.
        // Restore a flag cleared by a temporary unbind as well: a live event is stronger
        // evidence than the last lifecycle callback.
        if (!connected) connected = true;
        if (!started) startGuard();
        if (connected && visual == null) visual = newVisualGuard();
        // Only a window change discards an in-flight image sample. Scrolls and content changes
        // in feeds and videos are constant; the frame gate still requires the same app and
        // window and a fresh sample before acting.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (visual != null) visual.windowChanged();
            // A scan of the previous screen must not decide for the new one.
            scanGeneration++;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // A window opened, closed or moved: look for selected apps' windows once things settle.
            // A queued inspection, and the heartbeat, cover every window anyway.
            if (connected && !discoverPending && !inspectPending) {
                discoverPending = true;
                handler.postDelayed(discover, 250);
            }
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String name = pkg.toString();
        if (BuildConfig.SELF_PROTECTION && SelfProtectionScreens.watches(name)) {
            // A new window is checked at once, first from the event's own text (no tree walk),
            // then from the window with a few short retries while Android swaps the active root.
            // Content changes are throttled, never postponed: an animating screen still gets checked.
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (leaveIfTamperEvent(name, event)) return;
                handler.removeCallbacks(selfCheck);
                selfRetries = 3;
                handler.post(selfCheck);
            } else if (!selfCheckQueued) {
                // Settings draws App info before its window-state event arrives, so content
                // changes are often the first signal; retry while the root is still the old window.
                selfCheckQueued = true;
                selfRetries = 5;
                handler.postDelayed(selfCheck, 20);
            }
        }
        if (excluded == null) return;
        if (excluded.contains(name)) {
            // Background/stale events from Settings, the launcher or our own
            // window must not cancel a selected window's queued check.
            // inspectWindow still checks window ownership before reading any text.
            return;
        }
        if (name.equals(heldPackage) && SystemClock.elapsedRealtime() < heldUntil
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            holdEnforcements++;
            pendingPackage = name;
            intervene(name, event.getWindowId(), true, heldWord, false, heldVisual, false, true);
            return;
        }
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            if (!isSelected(rules, name)) return;
            // The event itself often carries the label that just appeared. Matching it before a
            // tree walk turns a visible word into a block immediately; the full scan below is the
            // fallback for text the event does not carry.
            if (quickTextMatch(rules, name, event)) {
                // A queued event from a window the person already left is not evidence. Only the
                // selected window in use may act before the walk; anything else waits for it.
                GuardWindows.Window target = activeWindow(name, event.getWindowId());
                if (target != null) {
                    pendingPackage = name;
                    recordInspection(InspectionHealth.Outcome.COMPLETE, PolicyReceipt.Kind.TEXT_SCAN);
                    if (intervene(target.app, target.id, true, true, false, false, false, false))
                        fastTextInterventions++;
                    else {
                        // The same window was acted on moments ago; keep the periodic check alive.
                        textDirty = true;
                        scheduleHeartbeat();
                    }
                    return;
                }
                textDirty = true;
                scheduleHeartbeat(50);
            }
            // A video frame or animation carries no text. Re-reading the whole tree for every one
            // of those would make the guard compete with the app; the forced pass every few seconds
            // still covers text that appears without a text-bearing event.
            textDirty |= eventCarriesText(event)
                    || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED;
            // Scrolling or changing content in a selected window brings the next image sample forward.
            if (visual != null && rules.visualEnabled && rules.scannedApps.contains(name)) visual.activity();
            pendingPackage = name;
            inspectionOutcome = "scheduled";
            handler.removeCallbacks(heartbeat);
            // Continuous feeds must not keep postponing the same pending inspection.
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handler.removeCallbacks(inspect);
                rootRetries = 5;
                inspectPending = true;
                handler.post(inspect);
            } else if (!inspectPending) {
                rootRetries = 5;
                inspectPending = true;
                // Content changes are checked quickly; a window-state change is immediate.
                handler.postDelayed(inspect, 50);
            }
        } catch (RuntimeException unavailable) {
            recordInspection(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "settings-unavailable";
        }
    }

    private void inspectWindow() {
        inspectPending = false;
        String name = pendingPackage;
        if (name == null || excluded.contains(name)) return;
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            List<GuardWindows.Window> windows = selectedWindows(this, excluded, app -> isSelected(rules, app));
            inspectedKey = windows == null ? null : GuardWindows.key(windows);
            if (windows == null) {
                inspectionOutcome = "no-root";
                if (!retryRoot(name)) recordInspection(InspectionHealth.Outcome.INCOMPLETE);
                return;
            }
            if (windows.isEmpty()) {
                inspectionOutcome = "root-changed-other";
                retryRoot(name);
                return;
            }
            if (!hasApp(windows, name)) {
                // A stale event from selected app A must not cancel the checks of selected app B
                // still on screen. Retarget by metadata only.
                pendingPackage = name = windows.get(0).app;
                rootRetries = 5;
            }
            // A window acted on moments ago and still on screen though not in use is left for now,
            // so other selected windows are still checked and Home is not pressed in a loop.
            long now = SystemClock.elapsedRealtime();
            List<GuardWindows.Window> held = new ArrayList<>();
            for (GuardWindows.Window window : windows) {
                if (repeat.wait(window.app, window.id, window.active, now) > 0) held.add(window);
            }
            windows.removeAll(held);
            if (windows.isEmpty()) {
                repeatHoldOffs++;
                inspectionOutcome = "repeat-held";
                scheduleHeartbeat();
                return;
            }
            // Blocked, scheduled and nothing-to-read decisions are complete inspections too.
            for (GuardWindows.Window window : windows) {
                if (rules.blockedApps.contains(window.app)) {
                    recordInspection(InspectionHealth.Outcome.COMPLETE, PolicyReceipt.Kind.APP_DECISION);
                    intervene(window.app, window.id, window.active, false, false, false, false, false);
                    return;
                }
            }
            for (GuardWindows.Window window : windows) {
                if (limitReached(rules, window.app)) {
                    recordInspection(InspectionHealth.Outcome.COMPLETE, PolicyReceipt.Kind.APP_DECISION);
                    intervene(window.app, window.id, window.active, false, false, false, true, false);
                    return;
                }
            }
            java.time.LocalTime local = java.time.LocalTime.now();
            int minute = local.getHour() * 60 + local.getMinute();
            java.time.DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
            List<GuardWindows.Window> scanned = new ArrayList<>();
            boolean scheduledOnScreen = false;
            for (GuardWindows.Window window : windows) {
                if (rules.schedule.blocks(window.app, minute, day)) {
                    recordInspection(InspectionHealth.Outcome.COMPLETE, PolicyReceipt.Kind.APP_DECISION);
                    intervene(window.app, window.id, window.active, false, true, false, false, false);
                    return;
                }
                if (rules.scannedApps.contains(window.app) && rules.hasWordProtection()) scanned.add(window);
                scheduledOnScreen |= rules.schedule.selects(window.app, minute, day);
            }
            boolean shortVideoOnScreen = false;
            if (rules.shortVideoEnabled) {
                for (GuardWindows.Window window : windows) {
                    if (!ShortVideoSurfaces.watches(window.app)) continue;
                    shortVideoOnScreen = true;
                    if (ShortVideoSurfaces.wholeApp(window.app)) {
                        interveneShortVideo(true);
                        return;
                    }
                    AccessibilityNodeInfo surface = windowRoot(this, window);
                    if (surface == null) continue;
                    try {
                        if (showsShortVideo(surface, window.app)) {
                            interveneShortVideo(false);
                            return;
                        }
                    } finally { surface.recycle(); }
                }
            }
            if (scanned.isEmpty()) {
                recordInspection(InspectionHealth.Outcome.COMPLETE, PolicyReceipt.Kind.APP_DECISION);
                // A static allowed screen must still be blocked when its daily window begins,
                // and a feed must be rechecked while it can move into Shorts or Reels.
                if (scheduledOnScreen || !held.isEmpty() || shortVideoOnScreen) scheduleHeartbeat();
                return;
            }
            long scanNow = SystemClock.uptimeMillis();
            if (!textDirty && scanNow - lastTextScanAt < FORCED_TEXT_SCAN_MILLIS) {
                // Nothing in this window changed since the last tree pass. The event fast path and
                // every selected event already ask for another scan when text appears; a static
                // screen is still re-read every few seconds as a safety net.
                scheduleHeartbeat();
                return;
            }
            if (activeScan != 0) { textDirty = true; return; }
            if (rules.adultWordsEnabled) AdultPolicyAssets.ensureLoaded(this);
            app.hisn.core.AdultTextPolicy adult = AdultPolicyAssets.policy();
            boolean adultLoading = rules.adultWordsEnabled && adult == null
                    && AdultPolicyAssets.state == AdultPolicyAssets.State.LOADING;
            textDirty = false;
            lastTextScanAt = scanNow;
            // One 1,024-node budget shared by every window, so split screen costs no more than one app.
            // Small windows (PiP, then other inactive ones) are read first and hand what they leave
            // to the window in use (rule and tests in core GuardWindows.scanOrder).
            scanned = GuardWindows.scanOrder(scanned);
            // Android resolves window roots through the service connection; fetch them here, on
            // the main thread, and let the scanner walk those snapshots off the UI thread.
            List<WindowScan> roots = new ArrayList<>();
            boolean partial = rules.adultWordsEnabled && adult == null;
            try {
                for (GuardWindows.Window window : scanned) {
                    AccessibilityNodeInfo root = windowRoot(this, window);
                    if (root == null) { partial = true; continue; }
                    roots.add(new WindowScan(window, root));
                }
            } catch (RuntimeException unavailable) {
                // A root read can fail after earlier windows were obtained; the outer catch
                // records the failure, so release what this attempt already holds first.
                for (WindowScan root : roots) root.root.recycle();
                throw unavailable;
            }
            if (roots.isEmpty()) {
                recordInspection(InspectionHealth.Outcome.INCOMPLETE);
                inspectionOutcome = "no-readable-roots";
                textDirty = true;
                scheduleHeartbeat();
                return;
            }
            final int scanId = ++nextScanId;
            final int generation = scanGeneration;
            final String scanName = name;
            final PolicyStore.Snapshot scanRules = rules;
            // Capture the policy and service session at submission; the worker may finish after
            // a rule edit, a vocabulary change or a rebind, and must then discard its result.
            final long scanIdentity = PolicyEvidence.text(scanRules.revision);
            final int scanSession = serviceSession;
            final app.hisn.core.AdultTextPolicy scanAdult = adult;
            final List<WindowScan> scanRoots = roots;
            final boolean scanPartial = partial;
            activeScan = scanId;
            Runnable timeout = () -> scanTimedOut(scanId);
            scanTimeout = timeout;
            handler.postDelayed(timeout, SCAN_TIMEOUT_MILLIS);
            try {
                scanner.execute(() -> scanText(scanId, generation, scanName, scanRules, scanAdult, scanRoots,
                        scanPartial, adultLoading, scanIdentity, scanSession));
            } catch (RuntimeException unavailable) {
                clearScanTimeout();
                activeScan = 0;
                for (WindowScan root : scanRoots) root.root.recycle();
                throw unavailable; // The outer catch records the failure and retries.
            }
        } catch (RuntimeException unavailable) {
            // No unverified decision when a node changes or settings are unavailable.
            // Keep rechecking this app, more slowly, instead of going silent.
            recordInspection(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "inspection-failed";
            inspectedKey = null;
            textDirty = true;
            inspectionFailures++;
            if (name.equals(pendingPackage)) {
                handler.removeCallbacks(heartbeat);
                handler.postDelayed(heartbeat, 5000);
            }
        }
    }

    private static ExecutorService newScanner() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hisn-text-scan");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Removes the watchdog for a finished, replaced or abandoned scan. Main thread only. */
    private void clearScanTimeout() {
        if (scanTimeout != null) {
            handler.removeCallbacks(scanTimeout);
            scanTimeout = null;
        }
    }

    /** A stuck tree walk must not leave the text layer silent: replace its executor and retry. */
    private void scanTimedOut(int scanId) {
        if (scanId != activeScan) return;
        scanTimeout = null;
        activeScan = 0;
        scanGeneration++;
        textDirty = true;
        recordInspection(InspectionHealth.Outcome.INCOMPLETE);
        inspectionOutcome = "inspection-timeout";
        inspectionFailures++;
        scanner.shutdownNow();
        scanner = newScanner();
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, 500);
    }

    /**
     * The tree walk itself. Runs on the single scanner thread: every child fetch crosses a Binder,
     * so a large screen would otherwise stall the service's main thread (and the app being read).
     * The verdict and frames are posted back to the main thread; nothing is acted on here.
     */
    private void scanText(int scanId, int generation, String name, PolicyStore.Snapshot rules,
            app.hisn.core.AdultTextPolicy adult, List<WindowScan> roots, boolean partial,
            boolean adultLoading, long scanIdentity, int scanSession) {
        boolean read = false, matched = false, failed = false;
        GuardWindows.Window matchedWindow = null;
        int screens = 0, unread = 0;
        long started = SystemClock.uptimeMillis();
        try {
            int left = LabelScan.NODE_BUDGET;
            for (int index = 0; index < roots.size(); index++) {
                WindowScan window = roots.get(index);
                Nodes nodes = new Nodes();
                // LabelScan.sample takes ownership of this root and recycles it whether it returns
                // or throws. Record that before the call so the cleanup below cannot double-recycle.
                unread = index + 1;
                VisibleTextSample sample = LabelScan.sample(nodes, window.root,
                        GuardWindows.share(left, roots.size() - index),
                        (partialSample, visited) -> matchesPromotion(rules, adult, partialSample.text()));
                left -= nodes.obtained;
                screens++;
                partial |= sample.isPartial();
                String text = sample.text();
                read |= !text.isEmpty();
                if (matches(rules, adult, text)) { matched = true; matchedWindow = window.window; break; }
            }
        } catch (RuntimeException unavailable) {
            // The outer catch below cannot see this thread; report the failure on the main thread.
            failed = true;
        } catch (Error unavailable) {
            // Never leave the guard waiting on a worker that died: post the failure, then let the
            // executor replace the thread.
            failed = true;
            throw unavailable;
        } finally {
            lastInspectionMillis = SystemClock.uptimeMillis() - started;
            // Roots past the one LabelScan took are still ours to release.
            for (int i = unread; i < roots.size(); i++) roots.get(i).root.recycle();
            if (failed) handler.post(() -> scanFailed(scanId));
        }
        if (failed) return;
        final boolean scanPartial = partial, scanRead = read, scanMatched = matched;
        final GuardWindows.Window scanWindow = matchedWindow;
        final int scanScreens = screens;
        handler.post(() -> scanFinished(scanId, generation, scanPartial, scanRead, scanMatched, scanWindow,
                scanScreens, adultLoading, scanIdentity, scanSession));
    }

    /** A window root fetched on the main thread and handed to the scanner thread. */
    private static final class WindowScan {
        final GuardWindows.Window window;
        final AccessibilityNodeInfo root;
        WindowScan(GuardWindows.Window window, AccessibilityNodeInfo root) {
            this.window = window;
            this.root = root;
        }
    }

    /** Applies a completed worker scan on the main thread, where the guard may act. */
    private void scanFinished(int scanId, int generation, boolean partial, boolean read, boolean matched,
            GuardWindows.Window window, int screens, boolean adultLoading, long scanIdentity,
            int scanSession) {
        if (scanId != activeScan) return; // A newer scan, or an interrupt, replaced this one.
        clearScanTimeout();
        activeScan = 0;
        if (destroyed) return;
        inspectedScreens += screens;
        if (generation != scanGeneration) {
            // The screen changed while it was being read; its decision cannot be applied.
            textDirty = true;
            scheduleHeartbeat(50);
            return;
        }
        // The policy or the service instance may have changed while the worker was reading.
        // A result from that older context must not certify or act on the new one.
        boolean policyCurrent;
        try {
            policyCurrent = scanSession == serviceSession
                    && PolicyEvidence.text(PolicyStore.get(this)) == scanIdentity;
        } catch (RuntimeException unreadable) {
            policyCurrent = false;
        }
        if (!policyCurrent) {
            textDirty = true;
            scheduleHeartbeat(50);
            return;
        }
        if (matched && window != null) {
            // A positive match is a complete decision even when a checkpoint stopped the walk.
            try {
                PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
                List<GuardWindows.Window> current = selectedWindows(this, excluded, app -> isSelected(rules, app));
                if (current == null || GuardWindows.find(current, window.id, window.app) == null) {
                    textDirty = true;
                    scheduleHeartbeat(250);
                    return;
                }
            } catch (RuntimeException unavailable) {
                textDirty = true;
                scheduleHeartbeat(250);
                return;
            }
            recordScan(false, true, scanIdentity, scanSession, generation);
            if (!intervene(window.app, window.id, window.active, true, false, false, false, false)) {
                // Repeat hold-off: the screen stayed and was already acted on, so keep reading it.
                textDirty = true;
                scheduleHeartbeat();
            }
            return;
        }
        recordScan(partial, read, scanIdentity, scanSession, generation);
        inspectionOutcome = read ? "no-word-match" : "no-readable-leaves";
        if (adultLoading) {
            // The signed adult vocabulary was still being verified: keep the screen dirty and
            // try again in a quarter second rather than waiting for the full heartbeat.
            textDirty = true;
            scheduleHeartbeat(ADULT_LOAD_RETRY_MILLIS);
            return;
        }
        // Bounded recheck only while a selected app has a window on screen. Once none is
        // left, the heartbeat stops after bounded retries. A change seen during the scan runs
        // another pass instead of waiting for the heartbeat.
        scheduleHeartbeat(textDirty ? 50 : 1000);
    }

    /**
     * A captured scan's own receipt. A pass with no readable text is an app-rule decision,
     * not a text inspection; a partial pass records the failure without evidence.
     */
    private void recordScan(boolean partial, boolean read, long identity, int session, int generation) {
        if (partial) {
            inspection.record(InspectionHealth.Outcome.INCOMPLETE, (PolicyReceipt) null);
            return;
        }
        inspection.record(InspectionHealth.Outcome.COMPLETE, new PolicyReceipt(identity, session, generation,
                read ? PolicyReceipt.Kind.TEXT_SCAN : PolicyReceipt.Kind.APP_DECISION,
                PolicyReceipt.Result.PASSED, SystemClock.elapsedRealtime()));
    }

    private void scanFailed(int scanId) {
        if (scanId != activeScan) return;
        clearScanTimeout();
        activeScan = 0;
        if (destroyed) return;
        recordInspection(InspectionHealth.Outcome.INCOMPLETE);
        inspectionOutcome = "inspection-failed";
        inspectedKey = null;
        textDirty = true;
        inspectionFailures++;
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, 5000);
    }

    /** The selected, active window with this id and app, or null when the event is stale. */
    private GuardWindows.Window activeWindow(String app, int id) {
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            List<GuardWindows.Window> windows = selectedWindows(this, excluded,
                    packageName -> isSelected(rules, packageName));
            GuardWindows.Window window = windows == null ? null : GuardWindows.find(windows, id, app);
            return window != null && window.active ? window : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** Whether an event names any text, so a tree pass could find something new. */
    private static boolean eventCarriesText(AccessibilityEvent event) {
        if (event.getContentDescription() != null) return true;
        java.util.List<CharSequence> text = event.getText();
        if (text == null) return false;
        for (CharSequence line : text) if (line != null && line.length() > 0) return true;
        return false;
    }

    /**
     * Checks the label(s) the accessibility event already carries, before any tree walk. This is
     * the same local matching as the full scan, on a bounded, transient copy that is discarded
     * here. The event's source is only asked whether it is visible; no screen state is retained.
     */
    private boolean quickTextMatch(PolicyStore.Snapshot rules, String name, AccessibilityEvent event) {
        if (!rules.scannedApps.contains(name) || !rules.hasWordProtection()) return false;
        if (event.isPassword()) return false;
        StringBuilder text = new StringBuilder();
        java.util.List<CharSequence> lines = event.getText();
        if (lines != null) {
            for (CharSequence line : lines) {
                if (line == null) continue;
                if (text.length() + line.length() > 8192) break;
                text.append(line).append('\n');
            }
        }
        CharSequence description = event.getContentDescription();
        if (description != null && text.length() + description.length() <= 8192) text.append(description);
        // A content-change event from a node that is already gone or scrolled off screen is not
        // evidence. No source means the event still names its window, as for a window state change.
        AccessibilityNodeInfo source = null;
        try {
            source = event.getSource();
            if (source != null) {
                CharSequence type = source.getClassName();
                if (source.isPassword() || source.isEditable()
                        || (type != null && type.toString().contains("EditText"))) return false;
                if (!source.isVisibleToUser()) return false;
                // Some apps put the changed label only on the source node, not in the event list.
                CharSequence sourceText = source.getText();
                if (sourceText != null && text.length() + sourceText.length() <= 8192) text.append('\n').append(sourceText);
                CharSequence sourceDescription = source.getContentDescription();
                if (sourceDescription != null && text.length() + sourceDescription.length() <= 8192)
                    text.append('\n').append(sourceDescription);
            }
        } catch (RuntimeException unavailable) {
            return false;
        } finally { if (source != null) source.recycle(); }
        if (text.length() == 0) return false;
        String sample = text.toString();
        if (!rules.adultWordsEnabled) return !rules.words.matches(sample).isEmpty();
        AdultPolicyAssets.ensureLoaded(this);
        return matchesPromotion(rules, AdultPolicyAssets.policy(), sample);
    }

    /** A verdict that is safe before the whole screen has been read: a literal rule, or adult
     * promotion with a clear signal. A bare "Porn" label waits for the complete sample. */
    private static boolean matchesPromotion(PolicyStore.Snapshot rules, app.hisn.core.AdultTextPolicy adult,
            String text) {
        if (text.isEmpty()) return false;
        if (!rules.words.matches(text).isEmpty()) return true;
        return rules.adultWordsEnabled && adult != null && adult.blocksPromotion(text);
    }

    private static boolean matches(PolicyStore.Snapshot rules, app.hisn.core.AdultTextPolicy adult, String text) {
        if (text.isEmpty()) return false;
        if (!rules.words.matches(text).isEmpty()) return true;
        if (!rules.adultWordsEnabled || adult == null) return false;
        // Each label is judged alone, so a medical/news label elsewhere in the UI never
        // exempts an unrelated adult-promotion label; it only stops a bare term such as a
        // "Porn" tag chip from blocking by itself. Custom rules remain independent.
        return adult.blocksAnyLine(text);
    }

    private static boolean isSelected(PolicyStore.Snapshot rules, String name) {
        return rules.blockedApps.contains(name) || rules.scannedApps.contains(name)
                || rules.timeLimits.monitored(name)
                || rules.schedule.selects(name, minuteOfDay(), dayOfWeek())
                || (rules.shortVideoEnabled && ShortVideoSurfaces.watches(name));
    }

    /** Whether Android's on-device foreground record has reached this app's saved daily limit.
     *  Without the Usage access special permission the answer is no, so the status page can show
     *  the missing step instead of a limit that is not being enforced. */
    private boolean limitReached(PolicyStore.Snapshot rules, String app) {
        if (app == null || excluded == null || excluded.contains(app) || !rules.timeLimits.monitored(app)) return false;
        if (!AppUsage.hasAccess(this)) return false;
        long now = SystemClock.elapsedRealtime();
        if (now - limitCacheAt > LIMIT_CACHE_MILLIS) {
            limitUsed.clear();
            limitCacheAt = now;
        }
        Long used = limitUsed.get(app);
        if (used == null) {
            used = AppUsage.usedToday(this, app);
            limitUsed.put(app, used);
        }
        return rules.timeLimits.exceeded(app, used);
    }

    private static int minuteOfDay() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.getHour() * 60 + now.getMinute();
    }

    private static java.time.DayOfWeek dayOfWeek() {
        return java.time.LocalDate.now().getDayOfWeek();
    }

    /** A visible Shorts or Reels player, found by its view ID; no text is read. */
    private static boolean showsShortVideo(AccessibilityNodeInfo root, String app) {
        for (String id : ShortVideoSurfaces.surfaceViewIds(app)) {
            java.util.List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(id);
            if (found == null) continue;
            boolean visible = false;
            for (AccessibilityNodeInfo node : found) {
                if (node.isVisibleToUser()) visible = true;
                node.recycle();
            }
            if (visible) return true;
        }
        return false;
    }

    /**
     * Leave a short-video feed. Inside YouTube or Instagram, BACK leaves Shorts or Reels and
     * keeps the rest of the app usable; a whole short-video app goes HOME like any blocked app.
     */
    private void interveneShortVideo(boolean wholeApp) {
        long now = SystemClock.elapsedRealtime();
        inspectionOutcome = "short-video-intervention";
        shortVideoInterventions++;
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(inspect);
        inspectPending = false;
        pendingPackage = null;
        if (!performGlobalAction(wholeApp ? GLOBAL_ACTION_HOME : GLOBAL_ACTION_BACK)) {
            homeFailures++;
            inspectionOutcome += "-leave-failed";
            performGlobalAction(wholeApp ? GLOBAL_ACTION_BACK : GLOBAL_ACTION_HOME);
        }
        if (now - lastIntervention < 1500) { cooldownEnforcements++; return; }
        lastIntervention = now;
        pendingWordReason = false;
        pendingScheduleReason = false;
        pendingLimitReason = false;
        pendingVisualReason = false;
        pendingShortVideoReason = true;
        handler.removeCallbacks(showRestriction);
        handler.postDelayed(showRestriction, 350);
    }

    /**
     * The removal guard is armed by the explicit keep switch or by any running
     * protection lock. Unreadable settings keep the explicit switch's value but
     * cannot prove a lock; the dashboard reports the unreadable state instead of
     * inventing protection.
     */
    private static boolean screenGuardKeep(PolicyStore store) {
        boolean keep = store.uninstallLock();
        try { return keep || ProtectionLock.locked(store.remainingMillis()); }
        catch (RuntimeException unreadable) { return keep; }
    }

    /**
     * Direct builds only: leave Hisn's uninstall, App info, admin-deactivation or
     * accessibility-toggle screen, a new app's install confirmation, or the power
     * menu's Safe Mode prompt, while the owner's switches are on. Text of watched
     * system windows is read only then, and never retained.
     */
    private void checkSelfProtection() {
        selfCheckQueued = false;
        if (!BuildConfig.SELF_PROTECTION) return;
        AccessibilityNodeInfo root = null;
        try {
            PolicyStore store = PolicyStore.get(this);
            boolean keep = screenGuardKeep(store), install = store.installLock();
            if (!SelfProtectionScreens.active(keep, install)) return;
            root = getRootInActiveWindow();
            String name = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
            if (!SelfProtectionScreens.watches(name)) {
                // The window-state event can arrive before Android makes that window the active root.
                if (selfRetries-- > 0) handler.postDelayed(selfCheck, 30);
                return;
            }
            // Watched, but not by a switch that is on: no tree walk for a screen
            // the guard could not act on anyway.
            if (!SelfProtectionScreens.watches(name, keep, install)) return;
            AccessibilityNodeInfo sampleRoot = root;
            root = null; // LabelScan.sample takes ownership and releases the root.
            Nodes nodes = new Nodes();
            String text = LabelScan.sample(nodes, sampleRoot, LabelScan.NODE_BUDGET).text();
            Guard guard = SelfProtectionScreens.classify(name, text, selfNames(), keep, install);
            if (guard == Guard.NONE) return;
            leaveTamperScreen(guard);
        } catch (RuntimeException unavailable) {
            recordInspection(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "self-protection-unavailable";
        } finally { if (root != null) root.recycle(); }
    }

    /** Event text alone (a dialog's title and message) is often enough to act before any tree walk. */
    private boolean leaveIfTamperEvent(String name, AccessibilityEvent event) {
        try {
            PolicyStore store = PolicyStore.get(this);
            boolean keep = screenGuardKeep(store), install = store.installLock();
            if (!SelfProtectionScreens.active(keep, install)) return false;
            StringBuilder text = new StringBuilder();
            for (CharSequence line : event.getText()) if (line != null) text.append(line).append('\n');
            if (event.getContentDescription() != null) text.append(event.getContentDescription());
            Guard guard = SelfProtectionScreens.classify(name, text.toString(), selfNames(), keep, install);
            if (guard == Guard.NONE) return false;
            leaveTamperScreen(guard);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private java.util.List<String> selfNames() {
        if (selfNames == null) {
            java.util.List<String> names = new java.util.ArrayList<>(SelfProtectionScreens.LABELS);
            names.add(getApplicationInfo().loadLabel(getPackageManager()).toString());
            names.add(getPackageName());
            selfNames = names;
        }
        return selfNames;
    }

    private void leaveTamperScreen(Guard guard) {
        handler.removeCallbacks(selfCheck);
        selfCheckQueued = false;
        selfProtectionInterventions++;
        inspectionOutcome = "self-protection-intervention";
        // BACK cancels a dialog or leaves App info; HOME then leaves Settings entirely.
        performGlobalAction(GLOBAL_ACTION_BACK);
        // Put a touch-blocking accessibility overlay over the Settings window before
        // leaving it. This closes the gap where a second tap could reach Uninstall or
        // Force stop while the Home transition and ShieldActivity are starting.
        showGuardOverlay();
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) homeFailures++;
        pendingGuard = guard;
        long now = SystemClock.elapsedRealtime();
        if (now - lastIntervention < 1500) cooldownEnforcements++;
        else lastIntervention = now;
        handler.removeCallbacks(showShield);
        // Show the shield quickly, but leave the home transition enough time to
        // settle; an immediate start was covered by that transition on Samsung.
        handler.postDelayed(showShield, 100);
    }

    private void showGuardOverlay() {
        if (Build.VERSION.SDK_INT < 30 || guardOverlay != null) return;
        try {
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (manager == null) return;
            TextView view = new TextView(this);
            view.setText(R.string.shield_overlay);
            view.setTextColor(0xffffffff);
            view.setTextSize(20);
            view.setGravity(Gravity.CENTER);
            view.setBackgroundColor(0xe6000000);
            view.setClickable(true);
            view.setOnClickListener(click -> { });
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.setFitInsetsTypes(WindowInsets.Type.systemBars());
            params.gravity = Gravity.TOP | Gravity.START;
            manager.addView(view, params);
            guardOverlay = view;
            guardOverlayManager = manager;
        } catch (RuntimeException unavailable) {
            // The 100 ms ShieldActivity path remains as the fallback.
        }
    }

    private void removeGuardOverlay() {
        if (guardOverlay == null || guardOverlayManager == null) return;
        try { guardOverlayManager.removeView(guardOverlay); }
        catch (RuntimeException alreadyGone) { }
        guardOverlay = null;
        guardOverlayManager = null;
    }

    private void scheduleHeartbeat() { scheduleHeartbeat(1000); }

    private void scheduleHeartbeat(long delay) {
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, delay);
    }

    private boolean retryRoot(String expectedPackage) {
        // Window-state events can precede Android's window transition.
        // Retry only this selected package; never inspect a different app's text.
        if (rootRetries-- > 0 && expectedPackage.equals(pendingPackage)) {
            inspectPending = true;
            handler.postDelayed(inspect, 150);
            return true;
        }
        return false;
    }

    /** Leaf labels, plus a card or button's own label when nothing inside it has text or an
     * input field (rule and tests in core {@link LabelScan}). Counts the nodes it obtains, so the
     * windows inspected together share one budget. */
    private static final class Nodes implements LabelScan.Tree<AccessibilityNodeInfo> {
        int obtained = 1; // The root.
        @Override public boolean isPrivate(AccessibilityNodeInfo node) {
            CharSequence type = node.getClassName();
            return node.isPassword() || node.isEditable() || (type != null && type.toString().contains("EditText"));
        }
        @Override public boolean isVisible(AccessibilityNodeInfo node) { return node.isVisibleToUser(); }
        @Override public int childCount(AccessibilityNodeInfo node) { return node.getChildCount(); }
        @Override public AccessibilityNodeInfo child(AccessibilityNodeInfo node, int index) {
            obtained++;
            return node.getChild(index);
        }
        @Override public CharSequence text(AccessibilityNodeInfo node) { return node.getText(); }
        @Override public CharSequence description(AccessibilityNodeInfo node) { return node.getContentDescription(); }
        @Override public void release(AccessibilityNodeInfo node) { node.recycle(); }
    }

    /** Acts on the offending app: closes its window when that is picture-in-picture, else Home.
     * Not again for a while on the same window while Home has left it on screen and out of use
     * (rule and tests in core InterventionRepeat); reopening a held app always acts. */
    private boolean intervene(String app, int window, boolean active, boolean word, boolean schedule,
            boolean visualReason, boolean limit, boolean held) {
        long now = SystemClock.elapsedRealtime();
        if (!held && repeat.wait(app, window, active, now) > 0) { repeatHoldOffs++; return false; }
        // This screen is about to be left: a scan still reading it cannot decide for the next one.
        clearScanTimeout();
        activeScan = 0;
        scanGeneration++;
        if (app != null) repeat.acted(app, window, now);
        inspectionOutcome = held ? "hold-intervention" : schedule ? "schedule-intervention"
                : limit ? "time-limit-intervention"
                : word ? "word-intervention" : visualReason ? "visual-intervention" : "app-intervention";
        if (word && !held) wordInterventions++;
        if (schedule) scheduleInterventions++;
        if (limit) timeLimitInterventions++;
        if ((word || visualReason) && !held) {
            // The hold starts at the block itself; reopening does not extend it.
            String blocked = app != null ? app : activePackage();
            if (blocked != null && excluded != null && !excluded.contains(blocked)) {
                heldPackage = blocked;
                heldUntil = now + BLOCK_HOLD_MILLIS;
                heldWord = word;
                heldVisual = visualReason;
            }
        }
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(inspect);
        inspectPending = false;
        pendingPackage = null;
        // Never debounce enforcement: reopening during the warning cooldown
        // must still leave the restricted app immediately.
        if (dismissPictureInPicture(app, window)) inspectionOutcome += "-pip-dismissed";
        else if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
            // For example under screen pinning. BACK at least leaves the current screen.
            homeFailures++;
            inspectionOutcome += "-home-failed";
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
        if (now - lastIntervention < 1500) { cooldownEnforcements++; return true; }
        lastIntervention = now;
        pendingWordReason = word;
        pendingScheduleReason = schedule;
        pendingLimitReason = limit;
        pendingVisualReason = visualReason;
        pendingShortVideoReason = false;
        handler.removeCallbacks(showRestriction);
        // HOME is asynchronous. Launching immediately lets its pending task
        // transition cover the explanation screen on Samsung Android 16.
        handler.postDelayed(showRestriction, 350);
        return true;
    }

    /** A picture-in-picture window of the offending app is closed with Android's own dismiss action,
     * leaving the app in use alone (Home does not close it). False when the window is not one or
     * Android refuses; Home then applies, as for any other window. */
    private boolean dismissPictureInPicture(String app, int id) {
        if (app == null || id < 0) return false;
        try {
            for (AccessibilityWindowInfo window : getWindows()) {
                if (window.getId() != id) continue;
                if (!window.isInPictureInPictureMode()) return false;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) return false;
                try {
                    return app.equals(packageOf(root)) && root.performAction(AccessibilityNodeInfo.ACTION_DISMISS);
                } finally { root.recycle(); }
            }
        } catch (RuntimeException unavailable) { /* Home instead. */ }
        return false;
    }

    private String activePackage() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            return packageOf(root);
        } catch (RuntimeException unavailable) {
            return null;
        } finally { if (root != null) root.recycle(); }
    }

    @Override public void onInterrupt() {
        // Android interrupts feedback here, not the service connection. In particular,
        // AccessibilityManager.interrupt() can be called by another app. Stopping the
        // heartbeat would leave a static selected screen unchecked until its next event.
        // Hisn has no speech or vibration to cancel; keep existing protection running.
        feedbackInterrupts++;
        if (connected && started && !destroyed && excluded != null) {
            textDirty = true;
            inspectedKey = null;
            if (!discoverPending) {
                discoverPending = true;
                handler.post(discover);
            }
        }
    }

    /** Release inspection work only when Android actually disconnects or destroys the service. */
    private void stopGuardChecks() {
        clearScanTimeout();
        activeScan = 0;
        scanGeneration++;
        if (visual != null) visual.close();
        visual = null;
        handler.removeCallbacks(inspect);
        handler.removeCallbacks(discover);
        handler.removeCallbacks(showRestriction);
        handler.removeCallbacks(showShield);
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(selfCheck);
        selfCheckQueued = false;
        pendingGuard = Guard.NONE;
        inspectPending = false;
        discoverPending = false;
        pendingPackage = null;
        inspectedKey = null;
        textDirty = true;
    }

    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        ready = false;
        started = false;
        handler.removeCallbacks(checkpoint);
        stopGuardChecks();
        // true asks Android to call onRebind when this accessibility service is
        // bound again. Without it a rebound instance stayed alive with `connected`
        // false until a screen event happened to arrive.
        return true;
    }

    @Override public void onRebind(Intent intent) {
        super.onRebind(intent);
        connected = true;
        startGuard();
    }

    @Override public void onDestroy() {
        destroyed = true;
        clearScanTimeout();
        activeScan = 0;
        scanner.shutdownNow();
        removeGuardOverlay();
        if (rearmRegistered) {
            try { unregisterReceiver(rearm); } catch (RuntimeException alreadyGone) { /* the process is ending */ }
            rearmRegistered = false;
        }
        connected = false;
        ready = false;
        started = false;
        handler.removeCallbacks(checkpoint);
        stopGuardChecks();
        super.onDestroy();
    }
}
