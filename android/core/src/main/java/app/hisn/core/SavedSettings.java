package app.hisn.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Saved settings as Android's preferences store them: Strings, Booleans, Integers, Longs and sets of
 * Strings, keyed by name, sealed by {@link SettingsIntegrity}. Reading checks every type; what the
 * rules may contain (limits, package names, word syntax) is checked by the caller's predicate.
 */
public final class SavedSettings {
    public static final String UNKNOWN = "commitment_unknown", RESTORED = "restored_from_copy";
    public final String domains, words;
    public final Set<String> apps, scans;
    public final boolean adultWords, visual;
    public final DailyAppSchedule schedule;

    private SavedSettings(Map<String, ?> values) {
        domains = text(values, "domains");
        words = text(values, "words");
        apps = names(values, "apps");
        scans = names(values, "scans");
        adultWords = flag(values, "adult_words");
        visual = flag(values, "visual_enabled");
        schedule = new DailyAppSchedule(flag(values, "schedule_enabled"), number(values, "schedule_start", 1320),
                number(values, "schedule_end", 420), names(values, "schedule_apps"));
    }

    /** The rule fields of saved values. Missing fields read as the defaults of Hisn 0.5.3 and earlier. */
    public static SavedSettings rules(Map<String, ?> values) { return new SavedSettings(values); }

    /** Every value to save, sealed. */
    public static Map<String, Object> values(String domains, String words, Set<String> apps, Set<String> scans,
            boolean adultWords, boolean visual, DailyAppSchedule schedule, CommitmentClock clock, boolean unknown,
            boolean restored) {
        Map<String, Object> values = new HashMap<>();
        values.put("domains", domains);
        values.put("words", words);
        values.put("apps", apps);
        values.put("scans", scans);
        values.put("adult_words", adultWords);
        values.put("visual_enabled", visual);
        values.put("schedule_enabled", schedule.enabled);
        values.put("schedule_start", schedule.startMinute);
        values.put("schedule_end", schedule.endMinute);
        values.put("schedule_apps", schedule.apps);
        values.put("remaining", clock.remainingAtCheckpoint);
        values.put("elapsed", clock.elapsedAtCheckpoint);
        values.put("boot", clock.bootAtCheckpoint);
        values.put(UNKNOWN, unknown);
        values.put(RESTORED, restored);
        values.put(SettingsIntegrity.SEAL, SettingsIntegrity.seal(values));
        return values;
    }

    // Values saved before 0.5.4 may lack a field; the defaults are the ones those versions read.
    public static CommitmentClock clock(Map<String, ?> values, int boot) {
        Object remaining = values.get("remaining"), elapsed = values.get("elapsed");
        if ((remaining != null && !(remaining instanceof Long)) || (elapsed != null && !(elapsed instanceof Long)))
            throw new IllegalArgumentException("Invalid commitment");
        return new CommitmentClock(remaining == null ? 0 : (Long) remaining, elapsed == null ? 0 : (Long) elapsed,
                number(values, "boot", boot));
    }

    /** What a store loads at start, and whether it must save again at once to leave two intact files. */
    public static final class Load {
        public final SettingsIntegrity.Source source;
        /** The values to read rules from; empty for no rules. */
        public final Map<String, ?> rules;
        public final CommitmentClock clock;
        public final boolean unknown, restored, rewrite;
        Load(SettingsIntegrity.Source source, Map<String, ?> rules, CommitmentClock clock, boolean unknown,
                boolean restored, boolean rewrite) {
            this.source = source; this.rules = rules; this.clock = clock;
            this.unknown = unknown; this.restored = restored; this.rewrite = rewrite;
        }
    }

    /**
     * Decides what to load from the main values and the copy's values ({@link SettingsIntegrity#choose}).
     * {@code rulesValid} checks what saved rules may contain and may throw. Intact settings that are not
     * valid, for example after a later version tightens a limit, are refused rather than replaced: the
     * caller shows that settings are unavailable and changes nothing, as it does for invalid values
     * saved before seals.
     *
     * @throws IllegalArgumentException when saved values must not be loaded or replaced
     */
    public static Load load(Map<String, ?> main, Map<String, ?> copy, boolean copyOnDisk, int boot, long now,
            Predicate<Map<String, ?>> rulesValid) {
        Predicate<Map<String, ?>> readable = values -> {
            try {
                rules(values); clock(values, boot); flag(values, UNKNOWN); flag(values, RESTORED);
                return rulesValid.test(values);
            } catch (RuntimeException invalid) { return false; }
        };
        SettingsIntegrity.Source source = SettingsIntegrity.choose(main, copy, copyOnDisk, readable);
        boolean rewrite = source != SettingsIntegrity.Source.MAIN || !main.equals(copy);
        switch (source) {
            case FRESH:
                return new Load(source, Collections.emptyMap(), new CommitmentClock(0, now, boot), false, false, rewrite);
            case UNKNOWN: {
                // Nothing intact to protect. Keep whatever rules still read; the commitment cannot have
                // more than the longest time left.
                Predicate<Map<String, ?>> rulesReadable = values -> {
                    try { rules(values); return rulesValid.test(values); } catch (RuntimeException invalid) { return false; }
                };
                Map<String, ?> rules = rulesReadable.test(main) ? main : rulesReadable.test(copy) ? copy : Collections.emptyMap();
                return new Load(source, rules, new CommitmentClock(CommitmentClock.LONGEST, now, boot), true, false, rewrite);
            }
            case INVALID:
                throw new IllegalArgumentException("Intact settings are not valid");
            default: {
                Map<String, ?> trusted = source == SettingsIntegrity.Source.COPY ? copy : main;
                // UNSEALED values were never checked by a seal; invalid ones throw here, as before seals.
                rules(trusted);
                if (!rulesValid.test(trusted)) throw new IllegalArgumentException("Invalid settings");
                return new Load(source, trusted, clock(trusted, boot), flag(trusted, UNKNOWN),
                        source == SettingsIntegrity.Source.COPY || flag(trusted, RESTORED), rewrite);
            }
        }
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
}
