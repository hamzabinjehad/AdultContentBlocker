package app.hisn.core;

import static app.hisn.core.SettingsIntegrity.Source.COPY;
import static app.hisn.core.SettingsIntegrity.Source.FRESH;
import static app.hisn.core.SettingsIntegrity.Source.MAIN;
import static app.hisn.core.SettingsIntegrity.Source.UNKNOWN;
import static app.hisn.core.SettingsIntegrity.Source.UNSEALED;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class SettingsIntegrityTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static Map<String, Object> settings(long remaining) {
        Map<String, Object> values = new HashMap<>();
        values.put("domains", "blocked.example");
        values.put("words", "");
        values.put("apps", new HashSet<>(Arrays.asList("com.example.one", "com.example.two")));
        values.put("adult_words", true);
        values.put("schedule_start", 1320);
        values.put("remaining", remaining);
        values.put("elapsed", 5000L);
        values.put("boot", 7);
        return values;
    }

    private static Map<String, Object> sealed(Map<String, Object> values) {
        Map<String, Object> copy = new HashMap<>(values);
        copy.put(SettingsIntegrity.SEAL, SettingsIntegrity.seal(values));
        return copy;
    }

    public static void main(String[] args) {
        seals();
        choices();
        System.out.println("SettingsIntegrity tests passed (seal covers every value and type, damaged main file, copy, pre-seal upgrade, new install and unknown state)");
    }

    private static void seals() {
        Map<String, Object> values = settings(3600000);
        String seal = SettingsIntegrity.seal(values);
        check(seal.startsWith("1:") && seal.length() == 66, "Versioned SHA-256 seal");
        // Order of keys and set items does not matter; the seal entry itself is not covered.
        Map<String, Object> reordered = new TreeMap<>(Collections.reverseOrder());
        reordered.putAll(values);
        reordered.put("apps", new LinkedHashSet<>(Arrays.asList("com.example.two", "com.example.one")));
        reordered.put(SettingsIntegrity.SEAL, "anything");
        check(SettingsIntegrity.seal(reordered).equals(seal), "Seal independent of order");
        check(SettingsIntegrity.intact(sealed(values)), "Sealed values are intact");

        // Every change is detected: a value, its type, a missing or an extra key, a set item.
        String[][] changes = {{"domains", "other.example"}, {"remaining", "1"}, {"boot", "8"}};
        for (String[] change : changes) {
            Map<String, Object> damaged = sealed(values);
            Object old = damaged.get(change[0]);
            damaged.put(change[0], old instanceof String ? change[1] : old instanceof Long ? (Object) Long.valueOf(change[1]) : Integer.valueOf(change[1]));
            check(!SettingsIntegrity.intact(damaged), "Changed " + change[0]);
        }
        Map<String, Object> retyped = sealed(values);
        retyped.put("remaining", 3600000);
        check(!SettingsIntegrity.intact(retyped), "Long saved as int");
        Map<String, Object> missing = sealed(values);
        missing.remove("remaining");
        check(!SettingsIntegrity.intact(missing), "Missing commitment field");
        Map<String, Object> extra = sealed(values);
        extra.put("remaining_copy", 1L);
        check(!SettingsIntegrity.intact(extra), "Extra field");
        Map<String, Object> fewerApps = sealed(values);
        fewerApps.put("apps", Collections.singleton("com.example.one"));
        check(!SettingsIntegrity.intact(fewerApps), "Removed app");
        // Field boundaries cannot be shifted to keep the same bytes.
        Map<String, Object> joined = new HashMap<>();
        joined.put("a", "bc");
        Map<String, Object> split = new HashMap<>();
        split.put("ab", "c");
        check(!SettingsIntegrity.seal(joined).equals(SettingsIntegrity.seal(split)), "Key and value boundary");
        Map<String, Object> pair = new HashMap<>();
        pair.put("apps", new HashSet<>(Arrays.asList("a", "b")));
        Map<String, Object> single = new HashMap<>();
        single.put("apps", Collections.singleton("a\u0000\u0000\u0000\u0001b"));
        check(!SettingsIntegrity.seal(pair).equals(SettingsIntegrity.seal(single)), "Set item boundary");
        // A missing, wrongly typed or foreign seal, or a value that cannot be sealed, is not intact.
        check(!SettingsIntegrity.intact(values), "No seal");
        Map<String, Object> wrongType = new HashMap<>(values);
        wrongType.put(SettingsIntegrity.SEAL, 1);
        check(!SettingsIntegrity.intact(wrongType), "Seal of the wrong type");
        Map<String, Object> other = sealed(settings(1));
        other.put("remaining", 3600000L);
        check(!SettingsIntegrity.intact(other), "Seal of other values");
        Map<String, Object> floating = new HashMap<>(values);
        floating.put("ratio", 1.5f);
        floating.put(SettingsIntegrity.SEAL, "1:00");
        check(!SettingsIntegrity.intact(floating), "Unsealable value");
        check(!SettingsIntegrity.intact(Collections.emptyMap()), "Empty map");
    }

    private static void choices() {
        Map<String, Object> none = Collections.emptyMap();
        Map<String, Object> good = sealed(settings(3600000));
        Map<String, Object> older = sealed(settings(7200000));
        Map<String, Object> legacy = settings(3600000);
        Map<String, Object> tampered = sealed(settings(3600000));
        tampered.put("remaining", 0L);
        Predicate<Map<String, ?>> readable = values -> !Long.valueOf(-1).equals(values.get("remaining"));
        Map<String, Object> invalid = sealed(settings(-1));

        check(SettingsIntegrity.choose(none, none, false, readable) == FRESH, "New installation or cleared app data");
        check(SettingsIntegrity.choose(good, good, true, readable) == MAIN, "Both intact");
        check(SettingsIntegrity.choose(good, none, false, readable) == MAIN, "Copy not written yet");
        check(SettingsIntegrity.choose(good, none, true, readable) == MAIN, "Copy unreadable");
        check(SettingsIntegrity.choose(good, tampered, true, readable) == MAIN, "Copy damaged");
        check(SettingsIntegrity.choose(good, older, true, readable) == MAIN, "Main file is newer than its copy");

        // Android reads a corrupt file as empty; the intact copy shows what was saved.
        check(SettingsIntegrity.choose(none, good, true, readable) == COPY, "Unreadable or missing main file");
        check(SettingsIntegrity.choose(tampered, good, true, readable) == COPY, "Main values changed");
        check(SettingsIntegrity.choose(legacy, good, true, readable) == COPY, "Main file lost its seal");
        check(SettingsIntegrity.choose(invalid, good, true, readable) == COPY, "Main values invalid");

        // Settings from Hisn 0.5.3 have no seal and no copy yet. Invalid ones still refuse to load, as before.
        check(SettingsIntegrity.choose(legacy, none, false, readable) == UNSEALED, "Upgrade");
        check(SettingsIntegrity.choose(settings(-1), none, false, readable) == UNSEALED, "Invalid pre-seal values");

        // Settings were saved but nothing can be trusted: never read as a new installation.
        check(SettingsIntegrity.choose(none, none, true, readable) == UNKNOWN, "Both files unreadable");
        check(SettingsIntegrity.choose(tampered, none, false, readable) == UNKNOWN, "Sealed main damaged, copy never written");
        check(SettingsIntegrity.choose(tampered, tampered, true, readable) == UNKNOWN, "Both changed");
        check(SettingsIntegrity.choose(legacy, none, true, readable) == UNKNOWN, "Unsealed main beside an unreadable copy");
        check(SettingsIntegrity.choose(legacy, tampered, true, readable) == UNKNOWN, "Unsealed main, damaged copy");
        check(SettingsIntegrity.choose(invalid, invalid, true, readable) == UNKNOWN, "Sealed but invalid");
        check(SettingsIntegrity.choose(invalid, none, false, readable) == UNKNOWN, "Sealed but invalid, no copy");
    }
}
