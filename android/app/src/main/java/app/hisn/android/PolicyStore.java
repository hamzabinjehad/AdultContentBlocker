package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import app.hisn.core.CommitmentClock;
import app.hisn.core.DailyAppSchedule;
import app.hisn.core.DomainPolicy;
import app.hisn.core.UninstallProtection;
import app.hisn.core.WordMatcher;
import app.hisn.core.WordRuleList;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Stores chosen rules only. Never receives screen text or browsing history. */
public final class PolicyStore {
    @android.annotation.SuppressLint("StaticFieldLeak") // Holds only the application context.
    private static PolicyStore shared;
    public static synchronized PolicyStore get(Context context) {
        if (shared == null) shared = new PolicyStore(context);
        return shared;
    }
    public static final class Snapshot {
        public final DomainPolicy domains;
        public final WordMatcher words;
        public final Set<String> blockedApps;
        public final Set<String> scannedApps;
        public final boolean adultWordsEnabled;
        public final boolean visualEnabled;
        public final boolean shortVideoEnabled;
        public final DailyAppSchedule schedule;

        Snapshot(String domainText, String wordText, Set<String> apps, Set<String> scans) {
            this(domainText, wordText, apps, scans, false);
        }
        Snapshot(String domainText, String wordText, Set<String> apps, Set<String> scans, boolean adultWordsEnabled) {
            this(domainText, wordText, apps, scans, adultWordsEnabled, DailyAppSchedule.disabled());
        }
        Snapshot(String domainText, String wordText, Set<String> apps, Set<String> scans, boolean adultWordsEnabled,
                DailyAppSchedule schedule) {
            this(domainText, wordText, apps, scans, adultWordsEnabled, schedule, false);
        }
        Snapshot(String domainText, String wordText, Set<String> apps, Set<String> scans, boolean adultWordsEnabled,
                DailyAppSchedule schedule, boolean visualEnabled) {
            this(domainText, wordText, apps, scans, adultWordsEnabled, schedule, visualEnabled, false);
        }
        Snapshot(String domainText, String wordText, Set<String> apps, Set<String> scans, boolean adultWordsEnabled,
                DailyAppSchedule schedule, boolean visualEnabled, boolean shortVideoEnabled) {
            if (schedule == null) throw new IllegalArgumentException("Missing daily schedule");
            if (domainText.length() > 65536 || wordText.length() > 65536) throw new IllegalArgumentException("Rules too long");
            Set<String> names = lines(domainText);
            if (names.size() > 1000) throw new IllegalArgumentException("Too many domain rules");
            domains = new DomainPolicy(names, Collections.emptySet());
            words = wordText.trim().isEmpty() ? new WordMatcher(Collections.emptySet()) : WordRuleList.compile(wordText);
            // Canonicalization can expand Unicode words and IDN hostnames. Stored
            // rules must fit the same limits when a fresh store reads them back.
            if (String.join("\n", domains.blockedRules()).length() > 65536
                    || String.join("\n", words.configuredRules()).length() > 65536) {
                throw new IllegalArgumentException("Normalized rules too long");
            }
            blockedApps = packages(apps);
            scannedApps = packages(scans);
            this.adultWordsEnabled = adultWordsEnabled;
            this.visualEnabled = visualEnabled;
            this.shortVideoEnabled = shortVideoEnabled;
            this.schedule = schedule;
        }

        public boolean hasWordProtection() { return adultWordsEnabled || !words.configuredRules().isEmpty(); }
        public boolean hasConfiguredRestrictions() {
            return !domains.blockedRules().isEmpty() || !blockedApps.isEmpty() || shortVideoEnabled
                    || schedule.isConfigured() || (!scannedApps.isEmpty() && (hasWordProtection() || visualEnabled));
        }

        private static Set<String> packages(Set<String> input) {
            if (input == null || input.size() > 200) throw new IllegalArgumentException("Invalid packages");
            Set<String> result = new LinkedHashSet<>();
            for (String name : input) {
                if (name == null || name.length() > 255 || !name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
                    throw new IllegalArgumentException("Invalid package");
                }
                result.add(name);
            }
            return Collections.unmodifiableSet(result);
        }

        private static Set<String> lines(String text) {
            Set<String> result = new LinkedHashSet<>();
            for (String line : text.split("\\R")) if (!line.trim().isEmpty()) result.add(line.trim());
            return result;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private volatile Snapshot snapshot;
    private CommitmentClock commitment;
    private boolean uninstallLock;

    public PolicyStore(Context context) {
        this(context, "hisn_policy");
    }

    PolicyStore(Context context, String name) {
        Context application = context.getApplicationContext();
        // Instrumentation/package contexts need not expose an Application.
        this.context = application == null ? context : application;
        prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        try {
            snapshot = new Snapshot(prefs.getString("domains", ""), prefs.getString("words", ""),
                    prefs.getStringSet("apps", Collections.emptySet()), prefs.getStringSet("scans", Collections.emptySet()),
                    prefs.getBoolean("adult_words", false), new DailyAppSchedule(
                            prefs.getBoolean("schedule_enabled", false), prefs.getInt("schedule_start", 1320),
                            prefs.getInt("schedule_end", 420), prefs.getStringSet("schedule_apps", Collections.emptySet())),
                    prefs.getBoolean("visual_enabled", false), prefs.getBoolean("short_video", false));
            commitment = new CommitmentClock(prefs.getLong("remaining", 0), prefs.getLong("elapsed", 0), prefs.getInt("boot", boot()));
            uninstallLock = prefs.getBoolean("uninstall_lock", false);
            checkpoint();
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Cannot read protection settings", invalid);
        }
    }

    /** Reload through a fresh store when another component updates settings. */
    public Snapshot snapshot() { return snapshot; }

    public synchronized long remainingMillis() {
        long now = SystemClock.elapsedRealtime();
        int boot = boot();
        long remaining = commitment.remaining(now, boot);
        // Save progress at most once a minute so a restart loses little counted time.
        if (commitment.remainingAtCheckpoint > 0 && (boot != commitment.bootAtCheckpoint
                || now - commitment.elapsedAtCheckpoint >= 60000)) {
            try { checkpoint(); } catch (RuntimeException unsaved) { /* Keep the last saved progress. */ }
        }
        return remaining;
    }

    public synchronized void checkpoint() {
        CommitmentClock next = commitment.checkpoint(SystemClock.elapsedRealtime(), boot());
        if (!prefs.edit().putLong("remaining", next.remainingAtCheckpoint).putLong("elapsed", next.elapsedAtCheckpoint)
                .putInt("boot", next.bootAtCheckpoint).commit()) throw new IllegalStateException("Cannot save commitment");
        commitment = next;
    }

    /** Opt-in "keep Hisn installed" switch used by the device-owner guard. */
    public synchronized boolean uninstallLock() { return uninstallLock; }

    /**
     * Turn the persistent uninstall guard on or off.
     *
     * Enabling is always allowed. Disabling waits for any commitment to end;
     * otherwise a person could start a commitment, switch the guard off, and
     * uninstall Hisn while the saved rules remained ostensibly locked. The
     * managed device-owner flag itself is still released separately by the
     * platform controller, only when no commitment and no lock remain.
     */
    public synchronized void updateUninstallLock(boolean enabled) {
        if (!enabled && !UninstallProtection.mayChangeLock(remainingMillis()))
            throw new IllegalStateException("Commitment prevents disabling uninstall protection");
        if (!prefs.edit().putBoolean("uninstall_lock", enabled).commit())
            throw new IllegalStateException("Cannot save uninstall protection");
        uninstallLock = enabled;
    }

    public synchronized void startCommitment(int days) {
        if (days != 1 && days != 7 && days != 30 && days != 90) throw new IllegalArgumentException("Invalid duration");
        if (!snapshot.hasConfiguredRestrictions()) throw new IllegalStateException("Choose restrictions before a commitment");
        long duration = days * 86400000L;
        if (duration < remainingMillis()) throw new IllegalStateException("Cannot shorten commitment");
        CommitmentClock next = new CommitmentClock(duration, SystemClock.elapsedRealtime(), boot());
        if (!prefs.edit().putLong("remaining", duration).putLong("elapsed", next.elapsedAtCheckpoint)
                .putInt("boot", next.bootAtCheckpoint).commit()) throw new IllegalStateException("Cannot save commitment");
        commitment = next;
    }

    public synchronized void update(String domains, String words, Set<String> apps, Set<String> scans) {
        update(domains, words, apps, scans, snapshot.adultWordsEnabled);
    }

    public synchronized void update(String domains, String words, Set<String> apps, Set<String> scans, boolean adultWordsEnabled) {
        save(new Snapshot(domains, words, apps, scans, adultWordsEnabled, snapshot.schedule, snapshot.visualEnabled,
                snapshot.shortVideoEnabled));
    }

    public synchronized void updateVisual(boolean enabled) {
        save(new Snapshot(domainText(), wordText(), snapshot.blockedApps, snapshot.scannedApps,
                snapshot.adultWordsEnabled, snapshot.schedule, enabled, snapshot.shortVideoEnabled));
    }

    /** Shorts, Reels and TikTok: turning it on always works; off waits for a commitment to end. */
    public synchronized void updateShortVideo(boolean enabled) {
        save(new Snapshot(domainText(), wordText(), snapshot.blockedApps, snapshot.scannedApps,
                snapshot.adultWordsEnabled, snapshot.schedule, snapshot.visualEnabled, enabled));
    }

    /** Other rule editors never reset a schedule; only this explicit editor changes it. */
    public synchronized void updateSchedule(DailyAppSchedule schedule) {
        save(new Snapshot(domainText(), wordText(), snapshot.blockedApps, snapshot.scannedApps,
                snapshot.adultWordsEnabled, schedule, snapshot.visualEnabled, snapshot.shortVideoEnabled));
    }

    private void save(Snapshot next) {
        Snapshot previous = snapshot;
        if (remainingMillis() > 0 && (!next.domains.blockedRules().containsAll(previous.domains.blockedRules())
                || !next.words.configuredRules().containsAll(previous.words.configuredRules())
                || !next.blockedApps.containsAll(previous.blockedApps)
                || !next.scannedApps.containsAll(previous.scannedApps)
                || (previous.adultWordsEnabled && !next.adultWordsEnabled)
                || (previous.visualEnabled && !next.visualEnabled)
                || (previous.shortVideoEnabled && !next.shortVideoEnabled)
                || !next.schedule.preserves(previous.schedule, next.blockedApps))) {
            throw new IllegalStateException("Commitment prevents weakening rules");
        }
        if (!prefs.edit().putString("domains", String.join("\n", next.domains.blockedRules()))
                .putString("words", String.join("\n", next.words.configuredRules()))
                .putStringSet("apps", next.blockedApps).putStringSet("scans", next.scannedApps)
                .putBoolean("adult_words", next.adultWordsEnabled)
                .putBoolean("visual_enabled", next.visualEnabled)
                .putBoolean("short_video", next.shortVideoEnabled)
                .putBoolean("schedule_enabled", next.schedule.enabled)
                .putInt("schedule_start", next.schedule.startMinute).putInt("schedule_end", next.schedule.endMinute)
                .putStringSet("schedule_apps", next.schedule.apps).commit()) {
            throw new IllegalStateException("Cannot save rules");
        }
        snapshot = next;
        checkpoint();
    }

    public String domainText() { return String.join("\n", snapshot.domains.blockedRules()); }
    public String wordText() { return String.join("\n", snapshot.words.configuredRules()); }

    private int boot() {
        int value = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (value < 0) throw new IllegalStateException("Boot counter unavailable");
        return value;
    }
}
