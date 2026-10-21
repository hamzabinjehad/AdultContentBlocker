package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;

import app.hisn.core.DailyAppSchedule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Debug-only persistence/commitment checks in fresh, uniquely named preferences.
 *
 * Never obtains the shared PolicyStore, changes a real rule or permission, or
 * starts an owner commitment. Nothing runs until an explicit debug fixture
 * calls run(). Test preference files are removed even when an assertion fails.
 */
public final class SchedulePolicyChecks {
    private static final String ONE = "app.hisn.fixture.scheduleone";
    private static final String TWO = "app.hisn.fixture.scheduletwo";
    private static final String THREE = "app.hisn.fixture.schedulethree";
    private static final String PERMANENT = "app.hisn.fixture.permanent";
    private static final String SCANNED = "app.hisn.fixture.scanned";

    private SchedulePolicyChecks() { }

    public static void run(Context context) {
        if (context == null) throw new IllegalArgumentException("Missing fixture context");
        String stem = "hisn_schedule_policy_check_" + UUID.randomUUID().toString();
        String normalName = stem + "_normal";
        String corruptName = stem + "_corrupt";
        try {
            checkNormalStore(context, normalName);
            checkCorruptStore(context, corruptName);
        } finally {
            // These exact generated names are the only preference files touched
            // by the checks; never clear the owner's hisn_policy preferences.
            boolean normalCleared = context.getSharedPreferences(normalName, Context.MODE_PRIVATE).edit().clear().commit();
            boolean corruptCleared = context.getSharedPreferences(corruptName, Context.MODE_PRIVATE).edit().clear().commit();
            boolean normalDeleted = context.deleteSharedPreferences(normalName);
            boolean corruptDeleted = context.deleteSharedPreferences(corruptName);
            require(normalCleared && corruptCleared && normalDeleted && corruptDeleted,
                    "Cannot clean isolated schedule test preferences");
        }
    }

    private static void checkNormalStore(Context context, String name) {
        PolicyStore store = new PolicyStore(context, name);
        checkSchedule(store.snapshot().schedule, false, 1320, 420, apps(), "fresh disabled schedule");
        require(!store.snapshot().hasConfiguredRestrictions(), "Fresh store must not imply a restriction");
        require(!store.snapshot().adultWordsEnabled && store.snapshot().blockedApps.isEmpty()
                && store.snapshot().scannedApps.isEmpty(), "Fresh choices remain empty");

        store.updateSchedule(new DailyAppSchedule(true, 1320, 420, apps()));
        require(!store.snapshot().hasConfiguredRestrictions(), "Enabled empty schedule must not qualify as protection");
        expectRejected(() -> store.startCommitment(7), "Empty schedule allowed a commitment");
        require(store.remainingMillis() == 0, "Rejected empty commitment must leave no time remaining");

        DailyAppSchedule chosen = new DailyAppSchedule(true, 1320, 420, apps(ONE, TWO));
        store.updateSchedule(chosen);
        require(store.snapshot().hasConfiguredRestrictions(), "Configured schedule qualifies as a restriction");
        PolicyStore restored = new PolicyStore(context, name);
        checkSchedule(restored.snapshot().schedule, true, 1320, 420, apps(ONE, TWO), "chosen schedule persists");

        // Exercise both old editors: adding ordinary rules or toggling the
        // explicit adult flag must never silently replace a chosen schedule.
        restored.update("fixture-schedule.invalid", "fixtureword", apps(PERMANENT), apps(SCANNED), true);
        checkSchedule(restored.snapshot().schedule, true, 1320, 420, apps(ONE, TWO), "explicit-flag editor retains schedule");
        restored.update("fixture-schedule.invalid\nsecond-fixture.invalid", "fixtureword\nsecondword",
                apps(PERMANENT), apps(SCANNED));
        require(restored.snapshot().adultWordsEnabled, "Legacy editor must preserve adult flag");
        checkSchedule(restored.snapshot().schedule, true, 1320, 420, apps(ONE, TWO), "legacy editor retains schedule");
        PolicyStore committed = new PolicyStore(context, name);
        checkChosenRules(committed, apps(PERMANENT));
        checkSchedule(committed.snapshot().schedule, true, 1320, 420, apps(ONE, TWO), "legacy updates persist schedule");
        committed.startCommitment(7);
        require(committed.remainingMillis() > 6L * 86400000L, "Isolated seven-day commitment starts");

        rejectWithoutMutation(context, name, committed, DailyAppSchedule.disabled(), "disable protected schedule");
        rejectWithoutMutation(context, name, committed,
                new DailyAppSchedule(true, 1321, 420, apps(ONE, TWO)), "narrow start by one minute");
        rejectWithoutMutation(context, name, committed,
                new DailyAppSchedule(true, 1320, 419, apps(ONE, TWO)), "narrow end by one minute");
        rejectWithoutMutation(context, name, committed,
                new DailyAppSchedule(true, 1320, 420, apps(ONE)), "remove protected schedule app");
        rejectWithoutMutation(context, name, committed,
                new DailyAppSchedule(true, 420, 1320, apps(ONE, TWO)), "shift to non-covering interval");

        committed.updateSchedule(new DailyAppSchedule(true, 1260, 480, apps(ONE, TWO)));
        checkSchedule(committed.snapshot().schedule, true, 1260, 480, apps(ONE, TWO), "wider schedule accepted during commitment");
        committed.updateSchedule(new DailyAppSchedule(true, 1260, 480, apps(ONE, TWO, THREE)));
        checkSchedule(committed.snapshot().schedule, true, 1260, 480, apps(ONE, TWO, THREE), "additional scheduled app accepted");
        checkChosenRules(committed, apps(PERMANENT));
        PolicyStore expanded = new PolicyStore(context, name);
        checkSchedule(expanded.snapshot().schedule, true, 1260, 480, apps(ONE, TWO, THREE), "expanded schedule persists");
        require(expanded.remainingMillis() > 6L * 86400000L, "Reload retains isolated commitment");

        // Stronger permanent blocking can replace scheduled coverage. Keep all
        // existing permanent/scanned/domain/word choices when making that move.
        Set<String> permanent = apps(PERMANENT, ONE, TWO, THREE);
        expanded.update(expanded.domainText(), expanded.wordText(), permanent, expanded.snapshot().scannedApps);
        require(expanded.snapshot().adultWordsEnabled, "Permanent editor retains adult flag");
        expanded.updateSchedule(DailyAppSchedule.disabled());
        checkChosenRules(expanded, permanent);
        checkSchedule(expanded.snapshot().schedule, false, 1320, 420, apps(), "permanent coverage permits disabling schedule");
        PolicyStore finalReload = new PolicyStore(context, name);
        checkChosenRules(finalReload, permanent);
        checkSchedule(finalReload.snapshot().schedule, false, 1320, 420, apps(), "effective coverage replacement persists");
        require(finalReload.remainingMillis() > 6L * 86400000L, "Coverage replacement does not end commitment");
        expectRejected(() -> finalReload.update(finalReload.domainText(), finalReload.wordText(), apps(PERMANENT),
                finalReload.snapshot().scannedApps), "Permanent replacement coverage could be removed during commitment");
        checkChosenRules(finalReload, permanent);
    }

    private static void rejectWithoutMutation(Context context, String name, PolicyStore store,
            DailyAppSchedule attempted, String label) {
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        DailyAppSchedule previous = store.snapshot().schedule;
        expectRejected(() -> store.updateSchedule(attempted), label);
        checkSchedule(store.snapshot().schedule, previous.enabled, previous.startMinute, previous.endMinute,
                previous.apps, label + " retains in-memory coverage");
        require(before.equals(prefs.getAll()), label + " modified stored preferences");
        checkChosenRules(store, apps(PERMANENT));
    }

    private static void checkCorruptStore(Context context, String name) {
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        require(prefs.edit().putString("domains", "retained-fixture.invalid").putString("words", "retainedword")
                .putStringSet("apps", apps(PERMANENT)).putStringSet("scans", apps(SCANNED)).putBoolean("adult_words", true)
                .putBoolean("schedule_enabled", true).putInt("schedule_start", 60).putInt("schedule_end", 60)
                .putStringSet("schedule_apps", apps(ONE)).commit(), "Cannot prepare isolated corrupt schedule fixture");
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        expectRejected(() -> new PolicyStore(context, name), "Corrupt equal schedule endpoints must reject load");
        require(before.equals(prefs.getAll()), "Failed corrupt schedule load changed stored rules");
        require(prefs.edit().putString("schedule_start", "invalid-type").putInt("schedule_end", 120).commit(),
                "Cannot prepare isolated corrupt schedule type");
        Map<String, ?> wrongTypeBefore = new HashMap<>(prefs.getAll());
        expectRejected(() -> new PolicyStore(context, name), "Corrupt schedule field type must reject load");
        require(wrongTypeBefore.equals(prefs.getAll()), "Failed wrong-type load changed stored rules");
    }

    private static void checkChosenRules(PolicyStore store, Set<String> permanent) {
        require(store.snapshot().domains.blockedRules().equals(apps("fixture-schedule.invalid", "second-fixture.invalid")),
                "Chosen domains must remain unchanged");
        require(store.snapshot().words.configuredRules().equals(apps("fixtureword", "secondword")),
                "Chosen custom words must remain unchanged");
        require(store.snapshot().blockedApps.equals(permanent), "Permanent apps must remain unchanged");
        require(store.snapshot().scannedApps.equals(apps(SCANNED)), "Scanned apps must remain unchanged");
        require(store.snapshot().adultWordsEnabled, "Adult detection flag must remain enabled");
    }

    private static void checkSchedule(DailyAppSchedule actual, boolean enabled, int start, int end,
            Set<String> selected, String label) {
        require(actual.enabled == enabled && actual.startMinute == start && actual.endMinute == end
                && actual.apps.equals(selected), label);
    }

    private static void expectRejected(Runnable operation, String label) {
        try { operation.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError(label);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Set<String> apps(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
