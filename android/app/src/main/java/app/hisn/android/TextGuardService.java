package app.hisn.android;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import app.hisn.core.GuardWindows;
import app.hisn.core.InspectionHealth;
import app.hisn.core.InterventionRepeat;
import app.hisn.core.LabelScan;
import app.hisn.core.VisibleTextSample;

/** Explicitly selected apps only, in every application window on screen (split screen, pop-up and
 * picture-in-picture included). Does not consume event text or retain screen content. */
public final class TextGuardService extends AccessibilityService {
    public static volatile boolean connected;
    /** Set by an incomplete or failed inspection; cleared by a later complete one. */
    public static final InspectionHealth inspection = new InspectionHealth();
    // Ephemeral outcome only: never screen text, matched rules or app identities.
    static volatile String inspectionOutcome = "not-run";
    static volatile long inspectedScreens;
    static volatile long wordInterventions;
    static volatile long cooldownEnforcements;
    static volatile long periodicInspections;
    static volatile long scheduleInterventions;
    static volatile long holdEnforcements;
    static volatile long homeFailures;
    static volatile long inspectionFailures;
    static volatile long repeatHoldOffs;
    /** After a word or image block, reopening the same app within this period returns Home again. */
    static final long BLOCK_HOLD_MILLIS = 60000;
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
    /** A window Home left on screen (pop-up, refused PiP dismiss) is not acted on again at once. */
    private final InterventionRepeat repeat = new InterventionRepeat();
    /** The selected windows of the last inspection; a windows-changed event with the same set
     * leaves them to the heartbeat and app events instead of inspecting again. */
    private String inspectedKey;
    private final Runnable inspect = this::inspectWindow;
    private final Runnable discover = this::inspectCurrentSelectedWindow;
    private final Runnable checkpoint = this::checkpointCommitment;
    private final Runnable heartbeat = () -> {
        rootRetries = 5;
        periodicInspections++;
        inspectWindow();
    };
    private boolean pendingWordReason;
    private boolean pendingScheduleReason;
    private boolean pendingVisualReason;
    private final Runnable showRestriction = () -> {
        try {
            startActivity(new Intent(this, RestrictionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra("word", pendingWordReason).putExtra("schedule", pendingScheduleReason)
                    .putExtra("visual", pendingVisualReason));
        } catch (RuntimeException unavailable) {
            inspection.record(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "restriction-screen-unavailable";
        }
    };

    @Override protected void onServiceConnected() {
        connected = true;
        handler.removeCallbacks(checkpoint);
        handler.post(checkpoint);
        try {
            if (visual != null) visual.close();
            visual = newVisualGuard();
            excluded = ChosenApps.excluded(this);
            inspectCurrentSelectedWindow();
        } catch (RuntimeException unavailable) {
            inspection.record(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "setup-unavailable";
        }
    }

    private VisualGuard newVisualGuard() {
        return new VisualGuard(this, window -> intervene(window.app, window.id, window.active, false, false, true, false));
    }

    /** Saves commitment progress once a minute so a restart loses little counted time. */
    private void checkpointCommitment() {
        try { PolicyStore.get(this).checkpoint(); } catch (RuntimeException unsaved) { /* Retry next minute. */ }
        ScreenGray.reassert(this);
        handler.postDelayed(checkpoint, 60000);
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
            rootRetries = 5;
            inspectPending = true;
            handler.removeCallbacks(heartbeat);
            handler.post(inspect);
        } catch (RuntimeException unavailable) {
            inspection.record(InspectionHealth.Outcome.INCOMPLETE);
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
        if (connected && visual == null) visual = newVisualGuard();
        // Only a window change discards an in-flight image sample. Scrolls and content changes
        // in feeds and videos are constant; the frame gate still requires the same app and
        // window and a fresh sample before acting.
        if (visual != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) visual.windowChanged();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // A window opened, closed or moved: look for selected apps' windows once things settle.
            // A queued inspection, and the heartbeat, cover every window anyway.
            if (connected && !discoverPending && !inspectPending) {
                discoverPending = true;
                handler.postDelayed(discover, 500);
            }
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null || excluded == null) return;
        String name = pkg.toString();
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
            intervene(name, event.getWindowId(), true, heldWord, false, heldVisual, true);
            return;
        }
        try {
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            if (!isSelected(rules, name)) return;
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
                handler.postDelayed(inspect, 150);
            }
        } catch (RuntimeException unavailable) {
            inspection.record(InspectionHealth.Outcome.INCOMPLETE);
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
                if (!retryRoot(name)) inspection.record(InspectionHealth.Outcome.INCOMPLETE);
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
                    inspection.record(InspectionHealth.Outcome.COMPLETE);
                    intervene(window.app, window.id, window.active, false, false, false, false);
                    return;
                }
            }
            java.time.LocalTime local = java.time.LocalTime.now();
            int minute = local.getHour() * 60 + local.getMinute();
            List<GuardWindows.Window> scanned = new ArrayList<>();
            boolean scheduledOnScreen = false;
            for (GuardWindows.Window window : windows) {
                if (rules.schedule.blocks(window.app, minute)) {
                    inspection.record(InspectionHealth.Outcome.COMPLETE);
                    intervene(window.app, window.id, window.active, false, true, false, false);
                    return;
                }
                if (rules.scannedApps.contains(window.app) && rules.hasWordProtection()) scanned.add(window);
                scheduledOnScreen |= rules.schedule.isConfigured() && rules.schedule.apps.contains(window.app);
            }
            if (scanned.isEmpty()) {
                inspection.record(InspectionHealth.Outcome.COMPLETE);
                // A static allowed screen must still be blocked when its daily window begins.
                if (scheduledOnScreen || !held.isEmpty()) scheduleHeartbeat();
                return;
            }
            if (rules.adultWordsEnabled) AdultPolicyAssets.ensureLoaded(this);
            app.hisn.core.AdultTextPolicy adult = AdultPolicyAssets.policy();
            boolean partial = rules.adultWordsEnabled && adult == null, read = false;
            // One 512-node budget shared by every window, so split screen costs no more than one app.
            // Small windows (PiP, then other inactive ones) are read first and hand what they leave
            // to the window in use (rule and tests in core GuardWindows.scanOrder).
            scanned = GuardWindows.scanOrder(scanned);
            int left = LabelScan.NODE_BUDGET;
            for (int i = 0; i < scanned.size(); i++) {
                GuardWindows.Window window = scanned.get(i);
                AccessibilityNodeInfo root = windowRoot(this, window);
                if (root == null) { partial = true; continue; }
                Nodes nodes = new Nodes();
                VisibleTextSample sample = LabelScan.sample(nodes, root, GuardWindows.share(left, scanned.size() - i));
                left -= nodes.obtained;
                inspectedScreens++;
                partial |= sample.isPartial();
                String text = sample.text();
                read |= !text.isEmpty();
                if (matches(rules, adult, text)) {
                    inspection.record(partial ? InspectionHealth.Outcome.INCOMPLETE : InspectionHealth.Outcome.COMPLETE);
                    intervene(window.app, window.id, window.active, true, false, false, false);
                    return;
                }
            }
            inspection.record(partial ? InspectionHealth.Outcome.INCOMPLETE : InspectionHealth.Outcome.COMPLETE);
            inspectionOutcome = read ? "no-word-match" : "no-readable-leaves";
            // Bounded recheck only while a selected app has a window on screen. Once none is
            // left, the heartbeat stops after bounded retries.
            scheduleHeartbeat();
        } catch (RuntimeException unavailable) {
            // No unverified decision when a node changes or settings are unavailable.
            // Keep rechecking this app, more slowly, instead of going silent.
            inspection.record(InspectionHealth.Outcome.INCOMPLETE);
            inspectionOutcome = "inspection-failed";
            inspectedKey = null;
            inspectionFailures++;
            if (name.equals(pendingPackage)) {
                handler.removeCallbacks(heartbeat);
                handler.postDelayed(heartbeat, 5000);
            }
        }
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
                || (rules.schedule.isConfigured() && rules.schedule.apps.contains(name));
    }

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, 1000);
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

    /** Leaf labels, plus a card or button's own description when nothing inside it has text or an
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
            boolean visualReason, boolean held) {
        long now = SystemClock.elapsedRealtime();
        if (!held && repeat.wait(app, window, active, now) > 0) { repeatHoldOffs++; return false; }
        if (app != null) repeat.acted(app, window, now);
        inspectionOutcome = held ? "hold-intervention" : schedule ? "schedule-intervention"
                : word ? "word-intervention" : visualReason ? "visual-intervention" : "app-intervention";
        if (word && !held) wordInterventions++;
        if (schedule) scheduleInterventions++;
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
        pendingVisualReason = visualReason;
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
        if (visual != null) visual.close();
        visual = null;
        handler.removeCallbacks(inspect);
        handler.removeCallbacks(discover);
        handler.removeCallbacks(showRestriction);
        handler.removeCallbacks(heartbeat);
        inspectPending = false;
        discoverPending = false;
        pendingPackage = null;
        inspectedKey = null;
    }

    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        handler.removeCallbacks(checkpoint);
        onInterrupt();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        connected = false;
        handler.removeCallbacks(checkpoint);
        onInterrupt();
        super.onDestroy();
    }
}
