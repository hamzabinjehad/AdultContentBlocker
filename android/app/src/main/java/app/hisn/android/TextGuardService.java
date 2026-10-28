package app.hisn.android;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Set;
import app.hisn.core.SelfProtectionScreens;
import app.hisn.core.ShortVideoSurfaces;
import app.hisn.core.VisibleTextSample;

/** Explicitly selected apps only. Does not consume event text or retain screen content. */
public final class TextGuardService extends AccessibilityService {
    public static volatile boolean connected;
    /** Setup finished and events are handled; the boot shield waits for this. */
    static volatile boolean ready;
    public static volatile boolean lastInspectionPartial;
    // Ephemeral outcome only: never screen text, matched rules or app identities.
    static volatile String inspectionOutcome = "not-run";
    static volatile long inspectedScreens;
    static volatile long wordInterventions;
    static volatile long cooldownEnforcements;
    static volatile long periodicInspections;
    static volatile long scheduleInterventions;
    static volatile long shortVideoInterventions;
    static volatile long holdEnforcements;
    static volatile long homeFailures;
    static volatile long inspectionFailures;
    static volatile long selfProtectionInterventions;
    /** After a word or image block, reopening the same app within this period returns Home again. */
    static final long BLOCK_HOLD_MILLIS = 60000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingPackage;
    private long lastIntervention;
    private boolean inspectPending;
    private String heldPackage;
    private long heldUntil;
    private boolean heldWord, heldVisual;
    private int rootRetries;
    private Set<String> excluded;
    private VisualGuard visual;
    private final Runnable inspect = this::inspectWindow;
    private final Runnable checkpoint = this::checkpointCommitment;
    private final Runnable selfCheck = this::checkSelfProtection;
    private final Runnable heartbeat = () -> {
        rootRetries = 5;
        periodicInspections++;
        inspectWindow();
    };
    private boolean pendingWordReason;
    private boolean pendingScheduleReason;
    private boolean pendingVisualReason;
    private boolean pendingShortVideoReason;
    private boolean pendingSelfProtectionReason;
    private int selfRetries;
    private boolean selfCheckQueued;
    private java.util.List<String> selfNames;
    private final Runnable showRestriction = () -> {
        try {
            startActivity(new Intent(this, RestrictionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra("word", pendingWordReason).putExtra("schedule", pendingScheduleReason)
                    .putExtra("visual", pendingVisualReason).putExtra("shortvideo", pendingShortVideoReason)
                    .putExtra("selfprotect", pendingSelfProtectionReason));
        } catch (RuntimeException unavailable) {
            lastInspectionPartial = true;
            inspectionOutcome = "restriction-screen-unavailable";
        }
    };

    @Override protected void onServiceConnected() {
        connected = true;
        handler.removeCallbacks(checkpoint);
        handler.post(checkpoint);
        try {
            // The uninstall guard needs nothing else, so it is armed before image setup.
            excluded = ChosenApps.excluded(this);
            ready = true;
            // Keeps the boot shield's pre-unlock copy current for installs that predate it.
            BootShieldService.mirror(this, PolicyStore.get(this).uninstallLock());
            // A removal screen opened before Android bound this service is left now.
            if (BuildConfig.SELF_PROTECTION) { selfRetries = 3; handler.post(selfCheck); }
            if (visual != null) visual.close();
            visual = new VisualGuard(this, () -> intervene(false, false, true));
            inspectCurrentSelectedWindow();
        } catch (RuntimeException unavailable) {
            lastInspectionPartial = true;
            inspectionOutcome = "setup-unavailable";
        }
    }

    /** Saves commitment progress once a minute so a restart loses little counted time. */
    private void checkpointCommitment() {
        try { PolicyStore.get(this).checkpoint(); } catch (RuntimeException unsaved) { /* Retry next minute. */ }
        handler.postDelayed(checkpoint, 60000);
    }

    /** Rebinding should not require a fresh foreground-window event. Metadata only until selected. */
    private void inspectCurrentSelectedWindow() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null) return;
            String name = root.getPackageName().toString();
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            if (!excluded.contains(name) && isSelected(rules, name)) {
                pendingPackage = name;
                rootRetries = 5;
                inspectPending = true;
                handler.post(inspect);
            }
        } finally { if (root != null) root.recycle(); }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (connected && visual == null) visual = new VisualGuard(this, () -> intervene(false, false, true));
        // Only a window change discards an in-flight image sample. Scrolls and content changes
        // in feeds and videos are constant; the frame gate still requires the same app and
        // window and a fresh sample before acting.
        if (visual != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) visual.windowChanged();
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
            // window must not cancel a selected foreground window's queued check.
            // inspectWindow still checks root ownership before reading any text.
            return;
        }
        if (name.equals(heldPackage) && SystemClock.elapsedRealtime() < heldUntil
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            holdEnforcements++;
            pendingPackage = name;
            intervene(heldWord, false, heldVisual, true);
            return;
        }
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            if (!isSelected(rules, name)) return;
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
                handler.postDelayed(inspect, 150);
            }
        } catch (RuntimeException unavailable) {
            lastInspectionPartial = true;
            inspectionOutcome = "settings-unavailable";
        }
    }

    private void inspectWindow() {
        inspectPending = false;
        String name = pendingPackage;
        if (name == null || excluded.contains(name)) return;
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) {
                inspectionOutcome = "no-root";
                if (!retryRoot(name)) lastInspectionPartial = true;
                return;
            }
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            String actual = root.getPackageName() == null ? null : root.getPackageName().toString();
            if (!name.equals(actual)) {
                // A stale event from selected app A must not cancel a static
                // foreground app B's schedule heartbeat. Retarget by metadata
                // only, and only when B is itself explicitly selected.
                if (actual != null && !excluded.contains(actual) && isSelected(rules, actual)) {
                    pendingPackage = name = actual;
                    rootRetries = 5;
                } else {
                    inspectionOutcome = getPackageName().equals(actual) ? "root-changed-hisn" : "root-changed-other";
                    retryRoot(name);
                    return;
                }
            }
            if (rules.blockedApps.contains(name)) { intervene(false); return; }
            java.time.LocalTime local = java.time.LocalTime.now();
            if (rules.schedule.blocks(name, local.getHour() * 60 + local.getMinute())) {
                intervene(false, true); return;
            }
            boolean shortVideoWatched = rules.shortVideoEnabled && ShortVideoSurfaces.watches(name);
            if (shortVideoWatched && (ShortVideoSurfaces.wholeApp(name) || showsShortVideo(root, name))) {
                interveneShortVideo(ShortVideoSurfaces.wholeApp(name));
                return;
            }
            if (!rules.scannedApps.contains(name) || !rules.hasWordProtection()) {
                // A static allowed screen must still be blocked when its daily window begins,
                // and a feed must be rechecked when the person moves into Shorts or Reels.
                if ((rules.schedule.isConfigured() && rules.schedule.apps.contains(name)) || shortVideoWatched) scheduleHeartbeat();
                return;
            }
            if (rules.adultWordsEnabled) AdultPolicyAssets.ensureLoaded(this);
            VisibleTextSample sample = leafSample(root);
            inspectedScreens++;
            app.hisn.core.AdultTextPolicy adult = AdultPolicyAssets.policy();
            lastInspectionPartial = sample.isPartial() || (rules.adultWordsEnabled && adult == null);
            String text = sample.text();
            inspectionOutcome = text.isEmpty() ? "no-readable-leaves" : "no-word-match";
            boolean matches = !text.isEmpty() && !rules.words.matches(text).isEmpty();
            if (!matches && rules.adultWordsEnabled && adult != null) {
                // Do not let a medical/news label elsewhere in the UI exempt an
                // unrelated adult-promotion label. Custom rules remain independent.
                for (String leaf : text.split("\n")) {
                    if (adult.blocks(leaf)) { matches = true; break; }
                }
            }
            if (matches) intervene(true);
            else {
                // Bounded recheck only while this selected package owns the active root.
                // Moving to another app stops the heartbeat after bounded root retries.
                scheduleHeartbeat();
            }
        } catch (RuntimeException unavailable) {
            // No unverified decision when a node changes or settings are unavailable.
            // Keep rechecking this app, more slowly, instead of going silent.
            lastInspectionPartial = true;
            inspectionOutcome = "inspection-failed";
            inspectionFailures++;
            if (name.equals(pendingPackage)) {
                handler.removeCallbacks(heartbeat);
                handler.postDelayed(heartbeat, 5000);
            }
        } finally { if (root != null) root.recycle(); }
    }

    private static boolean isSelected(PolicyStore.Snapshot rules, String name) {
        return rules.blockedApps.contains(name) || rules.scannedApps.contains(name)
                || (rules.schedule.isConfigured() && rules.schedule.apps.contains(name))
                || (rules.shortVideoEnabled && ShortVideoSurfaces.watches(name));
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
        pendingVisualReason = false;
        pendingShortVideoReason = true;
        pendingSelfProtectionReason = false;
        handler.removeCallbacks(showRestriction);
        handler.postDelayed(showRestriction, 350);
    }

    /**
     * Direct builds only: leave Hisn's uninstall, App info, admin-deactivation or
     * accessibility-toggle screen, or the power menu's Safe Mode prompt, while the owner
     * keeps the keep-installed switch on. Text of watched system windows is read only
     * then, and never retained.
     */
    private void checkSelfProtection() {
        selfCheckQueued = false;
        if (!BuildConfig.SELF_PROTECTION) return;
        AccessibilityNodeInfo root = null;
        try {
            if (!SelfProtectionScreens.active(PolicyStore.get(this).uninstallLock())) return;
            root = getRootInActiveWindow();
            String name = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
            if (!SelfProtectionScreens.watches(name)) {
                // The window-state event can arrive before Android makes that window the active root.
                if (selfRetries-- > 0) handler.postDelayed(selfCheck, 30);
                return;
            }
            String text = leafSample(root).text();
            if (!isTamper(name, text)) return;
            leaveTamperScreen();
        } catch (RuntimeException unavailable) {
            lastInspectionPartial = true;
            inspectionOutcome = "self-protection-unavailable";
        } finally { if (root != null) root.recycle(); }
    }

    /** Event text alone (a dialog's title and message) is often enough to act before any tree walk. */
    private boolean leaveIfTamperEvent(String name, AccessibilityEvent event) {
        try {
            if (!SelfProtectionScreens.active(PolicyStore.get(this).uninstallLock())) return false;
            StringBuilder text = new StringBuilder();
            for (CharSequence line : event.getText()) if (line != null) text.append(line).append('\n');
            if (event.getContentDescription() != null) text.append(event.getContentDescription());
            if (!isTamper(name, text.toString())) return false;
            leaveTamperScreen();
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private boolean isTamper(String name, String text) {
        if (selfNames == null) {
            java.util.List<String> names = new java.util.ArrayList<>(SelfProtectionScreens.LABELS);
            names.add(getApplicationInfo().loadLabel(getPackageManager()).toString());
            names.add(getPackageName());
            selfNames = names;
        }
        return SelfProtectionScreens.isTamperScreen(name, text, selfNames)
                || SelfProtectionScreens.isSafeModePrompt(name, text);
    }

    private void leaveTamperScreen() {
        handler.removeCallbacks(selfCheck);
        selfCheckQueued = false;
        selfProtectionInterventions++;
        inspectionOutcome = "self-protection-intervention";
        // BACK cancels an uninstall dialog; HOME then leaves Settings entirely.
        performGlobalAction(GLOBAL_ACTION_BACK);
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) homeFailures++;
        long now = SystemClock.elapsedRealtime();
        if (now - lastIntervention < 1500) { cooldownEnforcements++; return; }
        lastIntervention = now;
        pendingWordReason = false;
        pendingScheduleReason = false;
        pendingVisualReason = false;
        pendingShortVideoReason = false;
        pendingSelfProtectionReason = true;
        handler.removeCallbacks(showRestriction);
        handler.postDelayed(showRestriction, 350);
    }

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, 1000);
    }

    private boolean retryRoot(String expectedPackage) {
        // Window-state events can precede Android's active-root transition.
        // Retry only this selected package; never inspect a different app's text.
        if (rootRetries-- > 0 && expectedPackage.equals(pendingPackage)) {
            inspectPending = true;
            handler.postDelayed(inspect, 150);
            return true;
        }
        return false;
    }

    /** Conservative leaf-only scan avoids parent labels that duplicate child input values. */
    static VisibleTextSample leafSample(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        VisibleTextSample text = new VisibleTextSample(true);
        int visited = 0;
        int scheduled = 1;
        try {
            while (!queue.isEmpty() && visited++ < 512 && !text.isFull()) {
                AccessibilityNodeInfo node = queue.removeFirst();
                try {
                    CharSequence type = node.getClassName();
                    if (node.isPassword() || node.isEditable() || !node.isVisibleToUser()
                            || (type != null && type.toString().contains("EditText"))) continue;
                    int count = node.getChildCount();
                    if (count > 0) {
                        for (int i = 0; i < count; i++) {
                            if (scheduled >= 512) { text.markPartial(); break; }
                            scheduled++;
                            AccessibilityNodeInfo child = node.getChild(i);
                            if (child != null) queue.addLast(child);
                            else text.markPartial();
                        }
                    } else {
                        CharSequence value = node.getText();
                        if (value == null || value.length() == 0) value = node.getContentDescription();
                        text.append(value);
                    }
                } finally { node.recycle(); }
            }
        } finally {
            if (!queue.isEmpty()) text.markPartial();
            while (!queue.isEmpty()) queue.removeFirst().recycle();
        }
        return text;
    }

    private void intervene(boolean word) {
        intervene(word, false);
    }

    private void intervene(boolean word, boolean schedule) {
        intervene(word, schedule, false);
    }

    private void intervene(boolean word, boolean schedule, boolean visualReason) {
        intervene(word, schedule, visualReason, false);
    }

    private void intervene(boolean word, boolean schedule, boolean visualReason, boolean held) {
        long now = SystemClock.elapsedRealtime();
        inspectionOutcome = held ? "hold-intervention" : schedule ? "schedule-intervention"
                : word ? "word-intervention" : "app-intervention";
        if (word && !held) wordInterventions++;
        if (schedule) scheduleInterventions++;
        if ((word || visualReason) && !held) {
            // The hold starts at the block itself; reopening does not extend it.
            String app = pendingPackage != null ? pendingPackage : activePackage();
            if (app != null && excluded != null && !excluded.contains(app)) {
                heldPackage = app;
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
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
            // For example under screen pinning. BACK at least leaves the current screen.
            homeFailures++;
            inspectionOutcome += "-home-failed";
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
        if (now - lastIntervention < 1500) { cooldownEnforcements++; return; }
        lastIntervention = now;
        pendingWordReason = word;
        pendingScheduleReason = schedule;
        pendingVisualReason = visualReason;
        pendingShortVideoReason = false;
        pendingSelfProtectionReason = false;
        handler.removeCallbacks(showRestriction);
        // HOME is asynchronous. Launching immediately lets its pending task
        // transition cover the explanation screen on Samsung Android 16.
        handler.postDelayed(showRestriction, 350);
    }

    private String activePackage() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            return root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
        } catch (RuntimeException unavailable) {
            return null;
        } finally { if (root != null) root.recycle(); }
    }

    @Override public void onInterrupt() {
        if (visual != null) visual.close();
        visual = null;
        handler.removeCallbacks(inspect);
        handler.removeCallbacks(showRestriction);
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(selfCheck);
        selfCheckQueued = false;
        inspectPending = false;
        pendingPackage = null;
    }

    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        ready = false;
        handler.removeCallbacks(checkpoint);
        onInterrupt();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        connected = false;
        ready = false;
        handler.removeCallbacks(checkpoint);
        onInterrupt();
        super.onDestroy();
    }
}
