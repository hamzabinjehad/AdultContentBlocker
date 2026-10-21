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
import app.hisn.core.VisibleTextSample;

/** Explicitly selected apps only. Does not consume event text or retain screen content. */
public final class TextGuardService extends AccessibilityService {
    public static volatile boolean connected;
    public static volatile boolean lastInspectionPartial;
    // Ephemeral outcome only: never screen text, matched rules or app identities.
    static volatile String inspectionOutcome = "not-run";
    static volatile long inspectedScreens;
    static volatile long wordInterventions;
    static volatile long cooldownEnforcements;
    static volatile long periodicInspections;
    static volatile long scheduleInterventions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingPackage;
    private long lastIntervention;
    private boolean inspectPending;
    private int rootRetries;
    private Set<String> excluded;
    private final Runnable inspect = this::inspectWindow;
    private final Runnable heartbeat = () -> {
        rootRetries = 5;
        periodicInspections++;
        inspectWindow();
    };
    private boolean pendingWordReason;
    private boolean pendingScheduleReason;
    private final Runnable showRestriction = () -> {
        try {
            startActivity(new Intent(this, RestrictionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra("word", pendingWordReason).putExtra("schedule", pendingScheduleReason));
        } catch (RuntimeException unavailable) {
            lastInspectionPartial = true;
            inspectionOutcome = "restriction-screen-unavailable";
        }
    };

    @Override protected void onServiceConnected() {
        connected = true;
        try {
            excluded = ChosenApps.excluded(this);
            inspectCurrentSelectedWindow();
        } catch (RuntimeException unavailable) {
            lastInspectionPartial = true;
            inspectionOutcome = "setup-unavailable";
        }
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
        CharSequence pkg = event.getPackageName();
        if (pkg == null || excluded == null) return;
        String name = pkg.toString();
        if (excluded.contains(name)) {
            // Background/stale events from Settings, the launcher or our own
            // window must not cancel a selected foreground window's queued check.
            // inspectWindow still checks root ownership before reading any text.
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
            if (root.getPackageName() == null || !name.contentEquals(root.getPackageName())) {
                inspectionOutcome = getPackageName().contentEquals(root.getPackageName())
                        ? "root-changed-hisn" : "root-changed-other";
                retryRoot(name);
                return;
            }
            PolicyStore.Snapshot rules = PolicyStore.get(this).snapshot();
            if (rules.blockedApps.contains(name)) { intervene(false); return; }
            java.time.LocalTime local = java.time.LocalTime.now();
            if (rules.schedule.blocks(name, local.getHour() * 60 + local.getMinute())) {
                intervene(false, true); return;
            }
            if (!rules.scannedApps.contains(name) || !rules.hasWordProtection()) {
                // A static allowed screen must still be blocked when its daily window begins.
                if (rules.schedule.isConfigured() && rules.schedule.apps.contains(name)) scheduleHeartbeat();
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
            lastInspectionPartial = true;
            inspectionOutcome = "inspection-failed";
        } finally { if (root != null) root.recycle(); }
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
        VisibleTextSample text = new VisibleTextSample();
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
        long now = SystemClock.elapsedRealtime();
        inspectionOutcome = schedule ? "schedule-intervention" : word ? "word-intervention" : "app-intervention";
        if (word) wordInterventions++;
        if (schedule) scheduleInterventions++;
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(inspect);
        inspectPending = false;
        pendingPackage = null;
        // Never debounce enforcement: reopening during the warning cooldown
        // must still leave the restricted app immediately.
        performGlobalAction(GLOBAL_ACTION_HOME);
        if (now - lastIntervention < 1500) { cooldownEnforcements++; return; }
        lastIntervention = now;
        pendingWordReason = word;
        pendingScheduleReason = schedule;
        handler.removeCallbacks(showRestriction);
        // HOME is asynchronous. Launching immediately lets its pending task
        // transition cover the explanation screen on Samsung Android 16.
        handler.postDelayed(showRestriction, 350);
    }

    @Override public void onInterrupt() {
        handler.removeCallbacks(inspect);
        handler.removeCallbacks(showRestriction);
        handler.removeCallbacks(heartbeat);
        inspectPending = false;
        pendingPackage = null;
    }

    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        onInterrupt();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        connected = false;
        onInterrupt();
        super.onDestroy();
    }
}
