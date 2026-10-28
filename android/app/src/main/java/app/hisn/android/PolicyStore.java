package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import app.hisn.core.CommitmentClock;
import app.hisn.core.DailyAppSchedule;
import app.hisn.core.DomainPolicy;
import app.hisn.core.PolicyChangeError;
import app.hisn.core.SavedSettings;
import app.hisn.core.WordMatcher;
import app.hisn.core.WordRuleList;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stores chosen rules only. Never receives screen text or browsing history. */
public final class PolicyStore {
    @android.annotation.SuppressLint("StaticFieldLeak") // Holds only the application context.
    private static PolicyStore shared;
    public static synchronized PolicyStore get(Context context) {
        if (shared == null) shared = new PolicyStore(context);
        return shared;
    }
    /** Most apps one list may hold. */
    public static final int MAX_APPS = 200;

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
            if (input == null) throw new IllegalArgumentException("Invalid packages");
            if (input.size() > MAX_APPS) throw new PolicyChangeError.Invalid(PolicyChangeError.TOO_MANY_APPS, "Too many apps");
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

    /** The second copy's file name is the main name with this suffix. */
    static final String COPY_SUFFIX = "_copy";
    private static final String TAG = "HisnSettings";
    private final Context context;
    private final SharedPreferences prefs;
    private final SharedPreferences copy;
    private final File copyFile;
    // The last values saved to the main file, and whether the copy still lacks them (retried each minute).
    private Map<String, Object> saved;
    private boolean copyBehind;
    private long copyRetryAt;
    private volatile Snapshot snapshot;
    private CommitmentClock commitment;
    // Saved with the rules: the commitment could not be read and runs for the longest possible time,
    // and the main file was replaced by its copy and the owner has not yet confirmed the rules.
    private boolean commitmentUnknown, restored;

    public PolicyStore(Context context) {
        this(context, "hisn_policy");
    }

    /**
     * Only this class writes these two files. A damaged main file is replaced by its intact copy; when
     * neither can be trusted although settings were saved, the commitment is kept as unknown for the
     * longest time one can last instead of reading as a new installation (SettingsIntegrity). Clearing
     * app data in Android settings removes both files and starts again with no rules and no commitment.
     */
    PolicyStore(Context context, String name) {
        Context application = context.getApplicationContext();
        // Instrumentation/package contexts need not expose an Application.
        this.context = application == null ? context : application;
        prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        copy = context.getSharedPreferences(name + COPY_SUFFIX, Context.MODE_PRIVATE);
        // The copy's file in Android's preferences folder exists even when it reads as empty. If this
        // path ever differed, only two files damaged at once would go undetected, as before seals;
        // the check after the first save below logs that.
        copyFile = new File(new File(context.getDataDir(), "shared_prefs"), name + COPY_SUFFIX + ".xml");
        try {
            int boot = boot();
            // Reading waits for the load, which also restores the backup of an interrupted write.
            Map<String, ?> main = prefs.getAll(), second = copy.getAll();
            // Intact but invalid settings throw here and are left unchanged (SavedSettings.load).
            SavedSettings.Load load = SavedSettings.load(main, second, copyFile.exists(), boot,
                    SystemClock.elapsedRealtime(), PolicyStore::valid);
            snapshot = rules(load.rules);
            commitment = load.clock;
            commitmentUnknown = load.unknown;
            restored = load.restored;
            // Repair, seal or mirror at once, so the next start finds two intact files.
            if (load.rewrite) persist(snapshot, commitment.checkpoint(SystemClock.elapsedRealtime(), boot), commitmentUnknown, restored);
            else checkpoint();
            if (!copyBehind && !copyFile.exists()) android.util.Log.e(TAG, "Settings copy not found where expected");
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Cannot read protection settings", invalid);
        }
    }

    /** Reload through a fresh store when another component updates settings. */
    public Snapshot snapshot() { return snapshot; }

    /** True while a commitment runs whose saved end could not be read; it ends within 90 days at most. */
    public synchronized boolean commitmentUnknown() { return commitmentUnknown && remainingMillis() > 0; }

    /** True after the main settings file was replaced by its copy, until the owner confirms the rules. */
    public synchronized boolean restoredFromCopy() { return restored; }

    public synchronized void confirmRestored() {
        if (restored) persist(snapshot, commitment.checkpoint(SystemClock.elapsedRealtime(), boot()), commitmentUnknown, false);
    }

    public synchronized long remainingMillis() {
        long now = SystemClock.elapsedRealtime();
        int boot = boot();
        retryCopy(now);
        long remaining = commitment.remaining(now, boot);
        // Save progress at most once a minute so a restart loses little counted time.
        if (commitment.remainingAtCheckpoint > 0 && (boot != commitment.bootAtCheckpoint
                || now - commitment.elapsedAtCheckpoint >= 60000)) {
            try { checkpoint(); } catch (RuntimeException unsaved) { /* Keep the last saved progress. */ }
        }
        return remaining;
    }

    /** Saves commitment progress. With no commitment running there is nothing to count or save. */
    public synchronized void checkpoint() {
        retryCopy(SystemClock.elapsedRealtime());
        if (commitment.remainingAtCheckpoint == 0) return;
        persist(snapshot, commitment.checkpoint(SystemClock.elapsedRealtime(), boot()), commitmentUnknown, restored);
    }

    public synchronized void startCommitment(int days) {
        if (days != 1 && days != 7 && days != 30 && days != 90) throw new IllegalArgumentException("Invalid duration");
        if (!snapshot.hasConfiguredRestrictions())
            throw new PolicyChangeError.Refused(PolicyChangeError.NEEDS_RULES, "Choose restrictions before a commitment");
        long duration = days * 86400000L;
        if (duration < remainingMillis())
            throw new PolicyChangeError.Refused(PolicyChangeError.COMMITMENT, "Cannot shorten commitment");
        // A chosen commitment has a known end again.
        persist(snapshot, new CommitmentClock(duration, SystemClock.elapsedRealtime(), boot()), false, restored);
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
            throw new PolicyChangeError.Refused(PolicyChangeError.COMMITMENT, "Commitment prevents weakening rules");
        }
        persist(next, commitment.checkpoint(SystemClock.elapsedRealtime(), boot()), commitmentUnknown, restored);
    }

    /**
     * Writes every value and its seal to the main file, then the same to the copy. Only a failed main
     * write refuses the change. A failed copy write is logged and retried each minute; until it
     * succeeds, a damaged main file can only be detected, not restored.
     */
    private void persist(Snapshot rules, CommitmentClock clock, boolean unknown, boolean restoredNotice) {
        Map<String, Object> values = SavedSettings.values(String.join("\n", rules.domains.blockedRules()),
                String.join("\n", rules.words.configuredRules()), rules.blockedApps, rules.scannedApps,
                rules.adultWordsEnabled, rules.visualEnabled, rules.shortVideoEnabled, rules.schedule, clock, unknown, restoredNotice);
        if (!write(prefs, values)) throw saveFailed("Cannot save settings");
        saved = values;
        snapshot = rules;
        commitment = clock;
        commitmentUnknown = unknown;
        restored = restoredNotice;
        mirror(SystemClock.elapsedRealtime());
    }

    private void mirror(long now) {
        boolean behind = !write(copy, saved);
        if (behind && !copyBehind) android.util.Log.w(TAG, "Settings copy not saved; retrying");
        copyBehind = behind;
        copyRetryAt = now + 60000;
    }

    private void retryCopy(long now) {
        if (copyBehind && saved != null && now >= copyRetryAt) mirror(now);
    }

    // clear() first, so the file holds exactly the sealed values.
    private static boolean write(SharedPreferences target, Map<String, Object> values) {
        SharedPreferences.Editor editor = target.edit().clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else {
                Set<String> names = new LinkedHashSet<>();
                for (Object name : (Set<?>) value) names.add((String) name);
                editor.putStringSet(entry.getKey(), names);
            }
        }
        return editor.commit();
    }

    private static Snapshot rules(Map<String, ?> values) {
        SavedSettings saved = SavedSettings.rules(values);
        return new Snapshot(saved.domains, saved.words, saved.apps, saved.scans, saved.adultWords, saved.schedule, saved.visual,
                saved.shortVideo);
    }

    private static boolean valid(Map<String, ?> values) { rules(values); return true; }

    private static PolicyChangeError.Refused saveFailed(String message) {
        return new PolicyChangeError.Refused(PolicyChangeError.SAVE_FAILED, message);
    }

    public String domainText() { return String.join("\n", snapshot.domains.blockedRules()); }
    public String wordText() { return String.join("\n", snapshot.words.configuredRules()); }

    private int boot() {
        int value = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (value < 0) throw new IllegalStateException("Boot counter unavailable");
        return value;
    }
}
