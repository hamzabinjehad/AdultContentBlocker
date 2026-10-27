package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import app.hisn.core.CommitmentClock;
import app.hisn.core.DailyAppSchedule;
import app.hisn.core.DomainPolicy;
import app.hisn.core.PolicyChangeError;
import app.hisn.core.SettingsIntegrity;
import app.hisn.core.WordMatcher;
import app.hisn.core.WordRuleList;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
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
            this.schedule = schedule;
        }

        public boolean hasWordProtection() { return adultWordsEnabled || !words.configuredRules().isEmpty(); }
        public boolean hasConfiguredRestrictions() {
            return !domains.blockedRules().isEmpty() || !blockedApps.isEmpty()
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
    private static final String UNKNOWN = "commitment_unknown", RESTORED = "restored_from_copy";
    private final Context context;
    private final SharedPreferences prefs;
    private final SharedPreferences copy;
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
        try {
            int boot = boot();
            // Reading waits for the load, which also restores the backup of an interrupted write.
            Map<String, ?> main = prefs.getAll(), second = copy.getAll();
            // Android's preferences folder. If this path ever differed, a file would look absent, which
            // only loses the detection of a damaged file with no intact copy, as before seals existed.
            File folder = new File(context.getDataDir(), "shared_prefs");
            SettingsIntegrity.Source source = SettingsIntegrity.choose(main, new File(folder, name + ".xml").exists(),
                    second, new File(folder, name + COPY_SUFFIX + ".xml").exists(), values -> readable(values, boot));
            long now = SystemClock.elapsedRealtime();
            switch (source) {
                case FRESH:
                    snapshot = rules(Collections.emptyMap());
                    commitment = new CommitmentClock(0, now, boot);
                    break;
                case UNKNOWN:
                    // Keep whatever rules still read; the commitment cannot have more than the longest time left.
                    snapshot = rulesReadable(main) ? rules(main)
                            : rulesReadable(second) ? rules(second) : rules(Collections.emptyMap());
                    commitment = new CommitmentClock(CommitmentClock.LONGEST, now, boot);
                    commitmentUnknown = true;
                    break;
                default:
                    Map<String, ?> trusted = source == SettingsIntegrity.Source.COPY ? second : main;
                    snapshot = rules(trusted);
                    commitment = clock(trusted, boot);
                    commitmentUnknown = flag(trusted, UNKNOWN);
                    restored = source == SettingsIntegrity.Source.COPY || flag(trusted, RESTORED);
            }
            // Repair, seal or mirror at once, so the next start finds two intact files.
            if (source != SettingsIntegrity.Source.MAIN || !main.equals(second))
                persist(snapshot, commitment.checkpoint(now, boot), commitmentUnknown, restored);
            else checkpoint();
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
        save(new Snapshot(domains, words, apps, scans, adultWordsEnabled, snapshot.schedule, snapshot.visualEnabled));
    }

    public synchronized void updateVisual(boolean enabled) {
        save(new Snapshot(domainText(), wordText(), snapshot.blockedApps, snapshot.scannedApps,
                snapshot.adultWordsEnabled, snapshot.schedule, enabled));
    }

    /** Other rule editors never reset a schedule; only this explicit editor changes it. */
    public synchronized void updateSchedule(DailyAppSchedule schedule) {
        save(new Snapshot(domainText(), wordText(), snapshot.blockedApps, snapshot.scannedApps,
                snapshot.adultWordsEnabled, schedule, snapshot.visualEnabled));
    }

    private void save(Snapshot next) {
        Snapshot previous = snapshot;
        if (remainingMillis() > 0 && (!next.domains.blockedRules().containsAll(previous.domains.blockedRules())
                || !next.words.configuredRules().containsAll(previous.words.configuredRules())
                || !next.blockedApps.containsAll(previous.blockedApps)
                || !next.scannedApps.containsAll(previous.scannedApps)
                || (previous.adultWordsEnabled && !next.adultWordsEnabled)
                || (previous.visualEnabled && !next.visualEnabled)
                || !next.schedule.preserves(previous.schedule, next.blockedApps))) {
            throw new PolicyChangeError.Refused(PolicyChangeError.COMMITMENT, "Commitment prevents weakening rules");
        }
        persist(next, commitment.checkpoint(SystemClock.elapsedRealtime(), boot()), commitmentUnknown, restored);
    }

    /**
     * Writes every value and its seal to the main file, then the same to the copy. Only a failed main
     * write refuses the change: the copy is mirrored again on the next start if it fell behind.
     */
    private void persist(Snapshot rules, CommitmentClock clock, boolean unknown, boolean restoredNotice) {
        Map<String, Object> values = new HashMap<>();
        values.put("domains", String.join("\n", rules.domains.blockedRules()));
        values.put("words", String.join("\n", rules.words.configuredRules()));
        values.put("apps", rules.blockedApps);
        values.put("scans", rules.scannedApps);
        values.put("adult_words", rules.adultWordsEnabled);
        values.put("visual_enabled", rules.visualEnabled);
        values.put("schedule_enabled", rules.schedule.enabled);
        values.put("schedule_start", rules.schedule.startMinute);
        values.put("schedule_end", rules.schedule.endMinute);
        values.put("schedule_apps", rules.schedule.apps);
        values.put("remaining", clock.remainingAtCheckpoint);
        values.put("elapsed", clock.elapsedAtCheckpoint);
        values.put("boot", clock.bootAtCheckpoint);
        values.put(UNKNOWN, unknown);
        values.put(RESTORED, restoredNotice);
        values.put(SettingsIntegrity.SEAL, SettingsIntegrity.seal(values));
        if (!write(prefs, values)) throw saveFailed("Cannot save settings");
        write(copy, values);
        snapshot = rules;
        commitment = clock;
        commitmentUnknown = unknown;
        restored = restoredNotice;
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
            else editor.putStringSet(entry.getKey(), names(values, entry.getKey()));
        }
        return editor.commit();
    }

    private static Snapshot rules(Map<String, ?> values) {
        return new Snapshot(text(values, "domains"), text(values, "words"), names(values, "apps"), names(values, "scans"),
                flag(values, "adult_words"), new DailyAppSchedule(flag(values, "schedule_enabled"),
                        number(values, "schedule_start", 1320), number(values, "schedule_end", 420), names(values, "schedule_apps")),
                flag(values, "visual_enabled"));
    }

    // Values saved before 0.5.4 may lack a field; the defaults are the ones those versions read.
    private static CommitmentClock clock(Map<String, ?> values, int boot) {
        Object remaining = values.get("remaining"), elapsed = values.get("elapsed");
        if ((remaining != null && !(remaining instanceof Long)) || (elapsed != null && !(elapsed instanceof Long)))
            throw new IllegalArgumentException("Invalid commitment");
        return new CommitmentClock(remaining == null ? 0 : (Long) remaining, elapsed == null ? 0 : (Long) elapsed,
                number(values, "boot", boot));
    }

    private static boolean readable(Map<String, ?> values, int boot) {
        try { rules(values); clock(values, boot); flag(values, UNKNOWN); flag(values, RESTORED); return true; }
        catch (RuntimeException invalid) { return false; }
    }

    private static boolean rulesReadable(Map<String, ?> values) {
        try { rules(values); return true; } catch (RuntimeException invalid) { return false; }
    }

    private static String text(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        throw new IllegalArgumentException("Invalid " + key);
    }

    private static Set<String> names(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return Collections.emptySet();
        if (!(value instanceof Set)) throw new IllegalArgumentException("Invalid " + key);
        Set<String> names = new LinkedHashSet<>();
        for (Object name : (Set<?>) value) {
            if (!(name instanceof String)) throw new IllegalArgumentException("Invalid " + key);
            names.add((String) name);
        }
        return names;
    }

    private static boolean flag(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        throw new IllegalArgumentException("Invalid " + key);
    }

    private static int number(Map<String, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Integer) return (Integer) value;
        throw new IllegalArgumentException("Invalid " + key);
    }

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
