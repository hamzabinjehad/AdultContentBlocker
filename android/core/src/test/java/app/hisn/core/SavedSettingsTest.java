package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Values shaped as Android's SharedPreferences.getAll() returns them: HashMap, HashSet, boxed types. */
public final class SavedSettingsTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final int BOOT = 7;
    private static final long NOW = 50000;
    // Stands in for the app's limits: at most two apps.
    private static final Predicate<Map<String, ?>> VALID = values -> SavedSettings.rules(values).apps.size() <= 2;

    private static Set<String> set(String... items) { return new HashSet<>(Arrays.asList(items)); }

    private static Map<String, Object> saved(long remaining, boolean unknown, boolean restored, String... apps) {
        // As Android returns them: a fresh HashMap and HashSet copies of what was put.
        Map<String, Object> values = SavedSettings.values("blocked.example", "word", set(apps), set("com.example.scan"),
                true, false, true, new DailyAppSchedule(true, 1200, 360, set("com.example.night")),
                new CommitmentClock(remaining, 40000, BOOT), unknown, restored);
        Map<String, Object> android = new HashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            android.put(entry.getKey(), value instanceof Set ? new HashSet<>((Set<?>) value) : value);
        }
        return android;
    }

    private static SavedSettings.Load load(Map<String, ?> main, Map<String, ?> copy, boolean copyOnDisk) {
        return SavedSettings.load(main, copy, copyOnDisk, BOOT, NOW, VALID);
    }

    private static void refused(Map<String, ?> main, Map<String, ?> copy, String label) {
        try { load(main, copy, true); throw new AssertionError(label); }
        catch (IllegalArgumentException expected) { }
    }

    public static void main(String[] args) {
        Map<String, Object> none = Collections.emptyMap();
        Map<String, Object> good = saved(3600000, false, false, "com.example.one");

        // Every saved value is read back with its type.
        SavedSettings.Load intact = load(good, new HashMap<>(good), true);
        check(intact.source == SettingsIntegrity.Source.MAIN && !intact.rewrite, "Two intact files load without a write");
        SavedSettings rules = SavedSettings.rules(intact.rules);
        check(rules.domains.equals("blocked.example") && rules.words.equals("word") && rules.adultWords && !rules.visual
                && rules.apps.equals(set("com.example.one")) && rules.scans.equals(set("com.example.scan")), "Rules read back");
        check(rules.shortVideo, "Short-video blocking reads back");
        Map<String, Object> older = new HashMap<String, Object>(intact.rules);
        older.remove("short_video");
        check(!SavedSettings.rules(older).shortVideo, "Settings saved before short-video blocking read it as off");
        check(rules.schedule.enabled && rules.schedule.startMinute == 1200 && rules.schedule.endMinute == 360
                && rules.schedule.apps.equals(set("com.example.night")), "Schedule read back");
        check(intact.clock.remainingAtCheckpoint == 3600000 && intact.clock.elapsedAtCheckpoint == 40000
                && intact.clock.bootAtCheckpoint == BOOT && !intact.unknown && !intact.restored, "Commitment read back");
        SavedSettings.Load flagged = load(saved(1000, true, true, "com.example.one"), none, true);
        check(flagged.source == SettingsIntegrity.Source.MAIN && flagged.unknown && flagged.restored && flagged.rewrite,
                "Saved unknown and restored flags kept; the missing copy is written again");

        // New installation or cleared app data: nothing, and saved at once.
        SavedSettings.Load fresh = load(none, none, false);
        check(fresh.source == SettingsIntegrity.Source.FRESH && fresh.rules.isEmpty() && fresh.clock.remainingAtCheckpoint == 0
                && !fresh.unknown && !fresh.restored && fresh.rewrite, "Fresh start");

        // A damaged main file is replaced by its copy, with a notice.
        SavedSettings.Load restored = load(none, good, true);
        check(restored.source == SettingsIntegrity.Source.COPY && restored.restored && restored.rewrite
                && restored.clock.remainingAtCheckpoint == 3600000, "Copy restores");
        check(load(good, saved(7200000, false, false), true).rewrite, "A copy behind the main file is mirrored");

        // Both damaged: rules that still read are kept, the commitment runs for the longest time.
        Map<String, Object> tampered = new HashMap<>(good);
        tampered.put("remaining", 0L);
        SavedSettings.Load unknown = load(tampered, none, true);
        check(unknown.source == SettingsIntegrity.Source.UNKNOWN && unknown.unknown && !unknown.restored && unknown.rewrite
                && unknown.clock.remainingAtCheckpoint == CommitmentClock.LONGEST && unknown.clock.elapsedAtCheckpoint == NOW
                && SavedSettings.rules(unknown.rules).apps.equals(set("com.example.one")), "Unknown keeps readable rules");
        Map<String, Object> garbled = new HashMap<>(tampered);
        garbled.put("apps", "com.example.one");
        check(load(garbled, none, true).rules.isEmpty(), "Unknown with unreadable rules has none");
        check(load(none, none, true).source == SettingsIntegrity.Source.UNKNOWN, "Both unreadable is unknown, not fresh");

        // Intact but not valid, for example after a later version lowers a limit: refused, never replaced.
        Map<String, Object> tooMany = saved(0, false, false, "com.example.one", "com.example.two", "com.example.three");
        refused(tooMany, new HashMap<>(tooMany), "Intact settings over a limit refused");
        refused(tooMany, none, "Intact settings over a limit, no copy, refused");
        refused(tampered, tooMany, "Damaged main beside an intact but invalid copy refused");
        // A sealed value of the wrong type is intact but not readable: refused too.
        Map<String, Object> retyped = saved(0, false, false, "com.example.one");
        retyped.put("remaining", 5);
        retyped.put(SettingsIntegrity.SEAL, SettingsIntegrity.seal(retyped));
        refused(retyped, none, "Commitment saved as an int");
        Map<String, Object> flagText = new HashMap<>(retyped);
        flagText.put("remaining", 5L);
        flagText.put(SavedSettings.UNKNOWN, "true");
        flagText.put(SettingsIntegrity.SEAL, SettingsIntegrity.seal(flagText));
        refused(flagText, none, "Flag saved as text");

        // Hisn 0.5.3 settings: no seal, no copy, fewer fields. Loaded and sealed; invalid ones refused, as before.
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("domains", "old.example");
        legacy.put("apps", set("com.example.one"));
        legacy.put("remaining", 86400000L);
        legacy.put("elapsed", 1000L);
        legacy.put("boot", 3);
        SavedSettings.Load upgrade = load(legacy, none, false);
        check(upgrade.source == SettingsIntegrity.Source.UNSEALED && upgrade.rewrite && !upgrade.unknown && !upgrade.restored
                && upgrade.clock.remainingAtCheckpoint == 86400000L && upgrade.clock.bootAtCheckpoint == 3
                && SavedSettings.rules(upgrade.rules).schedule.startMinute == 1320, "Upgrade from 0.5.3");
        Map<String, Object> legacyInvalid = new HashMap<>(legacy);
        legacyInvalid.put("remaining", 5);
        try { load(legacyInvalid, none, false); throw new AssertionError("Invalid 0.5.3 settings loaded"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("SavedSettings tests passed (typed round trip, fresh, copy, unknown, intact-but-invalid refused, 0.5.3 upgrade)");
    }
}
