package app.hisn.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import app.hisn.core.DnsExchange;
import app.hisn.core.DnsPolicy;
import app.hisn.core.DomainPolicy;
import app.hisn.core.DomainSeed;
import app.hisn.core.WordRuleList;
import app.hisn.core.VisibleTextSample;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Device checks without enabling a VPN, touching other apps, or public DNS traffic. */
public final class CoreDeviceTestRunner extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            DomainSeed seed;
            try (InputStream input = getTargetContext().getAssets().open("domains_core.txt")) {
                seed = DomainSeed.load(input, BuildConfig.DOMAIN_SEED_SHA256, BuildConfig.DOMAIN_SEED_COUNT);
            }
            if (seed.domainCount < 50000 || seed.policy.blocks("example.com")) throw new AssertionError("Seed check failed");
            if (WordRuleList.compile("example\nإباحِيّ").matches("إباحي").isEmpty()) throw new AssertionError("Arabic rule check failed");
            if (!WordRuleList.compile("sex").matches("Essex").isEmpty()) throw new AssertionError("Word boundary check failed");
            DnsPolicy policy = new DnsPolicy(new DomainPolicy(Collections.singleton("blocked.example"), Collections.emptyList()));
            DnsExchange exchange = new DnsExchange(policy, query -> { throw new AssertionError("Blocked request escaped"); });
            byte[] response = exchange.resolve(query("blocked.example"));
            if ((response[3] & 15) != 3) throw new AssertionError("Local DNS block failed");
            testSavedRules();
            testAdultPolicy();
            testAdultCommitment();
            testVisualCommitment();
            testUninstallGuard();
            testLegacyEmptyCommitment();
            testDamagedSettings();
            VisibleTextSample sample = new VisibleTextSample();
            sample.append("blocked " + "ordinary ".repeat(1000));
            if (WordRuleList.compile("blocked").matches(sample.text()).isEmpty())
                throw new AssertionError("Long text inspection failed");
            sample.append("safe ".repeat(10000));
            if (!sample.isPartial() || sample.text().length() > 32768)
                throw new AssertionError("Inspection budget failed");
            results.putString("stream", "\nPASS: verified APK seed loaded " + seed.domainCount
                    + " domains; Arabic matching, boundaries, local DNS block, saved rules, Unicode persistence, long-text sampling, verified adult terms, visual settings, commitment, damaged-settings and invalid-settings, and uninstall-guard checks passed. No VPN was enabled; no accessibility service, admin or device-owner policy was changed.\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable failure) {
            StringBuilder detail = new StringBuilder();
            for (Throwable cause = failure; cause != null && detail.length() < 2048; cause = cause.getCause())
                detail.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage()).append('\n');
            results.putString("stream", "\nFAIL: " + detail);
            finish(Activity.RESULT_CANCELED, results);
        }
    }

    private void testVisualCommitment() {
        String name = "hisn_visual_test_" + java.util.UUID.randomUUID();
        android.content.Context context = getTargetContext();
        try {
            PolicyStore store = new PolicyStore(context, name);
            if (store.snapshot().visualEnabled) throw new AssertionError("Visual must default off");
            store.updateVisual(true);
            if (store.snapshot().hasConfiguredRestrictions()) throw new AssertionError("Visual needs selected apps");
            java.util.Set<String> apps = java.util.Collections.singleton("fixture.visual");
            store.update("", "", java.util.Collections.emptySet(), apps);
            store.updateSchedule(app.hisn.core.DailyAppSchedule.disabled());
            if (!new PolicyStore(context, name).snapshot().visualEnabled)
                throw new AssertionError("Other editors or reload erased visual preference");
            store.startCommitment(7);
            try { store.updateVisual(false); throw new AssertionError("Visual disabled during commitment"); }
            catch (IllegalStateException expected) { }
            try {
                store.update("", "", java.util.Collections.emptySet(), java.util.Collections.emptySet());
                throw new AssertionError("Visual app removed during commitment");
            } catch (IllegalStateException expected) { }
            PolicyStore.Snapshot restored = new PolicyStore(context, name).snapshot();
            if (!restored.visualEnabled || !restored.scannedApps.equals(apps))
                throw new AssertionError("Rejected update damaged visual rules");
        } finally { forget(context, name); }
    }

    private void testUninstallGuard() {
        String preferenceName = "hisn_uninstall_test_" + java.util.UUID.randomUUID();
        android.content.Context context = getTargetContext();
        try {
            PolicyStore store = new PolicyStore(context, preferenceName);
            if (store.uninstallLock()) throw new AssertionError("Uninstall lock defaulted on");
            store.updateUninstallLock(true);
            if (!new PolicyStore(context, preferenceName).uninstallLock())
                throw new AssertionError("Uninstall lock did not persist");
            store.update("blocked.example", "", Collections.emptySet(), Collections.emptySet());
            store.startCommitment(1);
            try {
                store.updateUninstallLock(false);
                throw new AssertionError("Commitment allowed disabling uninstall protection");
            } catch (IllegalStateException expected) { }
            if (!store.uninstallLock()) throw new AssertionError("Rejected change cleared uninstall lock");
            UninstallProtectionController.Status status = UninstallProtectionController.read(context);
            if (status.state() == null) throw new AssertionError("Uninstall status unavailable");
            if (!status.deviceOwner) {
                // The normal, non-managed installation must never claim that
                // Android is blocking uninstall. A managed test device is read
                // but never changed by this runner.
                if (status.uninstallBlocked
                        || status.state() == app.hisn.core.UninstallProtection.State.PROTECTED)
                    throw new AssertionError("Unmanaged install claimed uninstall protection");
                UninstallProtectionController.Result result = UninstallProtectionController.sync(context);
                if (!result.ok || !"not-managed".equals(result.reason))
                    throw new AssertionError("Unmanaged sync was not a no-op: " + result.reason);
            }
            if (!UninstallProtectionController.provisioningCommand(context)
                    .contains(context.getPackageName()))
                throw new AssertionError("Provisioning command lost the package name");
        } finally {
            context.getSharedPreferences(preferenceName, android.content.Context.MODE_PRIVATE)
                    .edit().clear().commit();
        }
    }

    private void testAdultPolicy() throws Exception {
        app.hisn.core.AdultTextPolicy policy;
        try (InputStream input = getTargetContext().getAssets().open("terms.json")) {
            policy = AdultPolicyAssets.load(input, BuildConfig.TERMS_SEED_SHA256);
        }
        if (policy.ruleCount() < 4000 || !policy.blocks("Watch free porn videos now")
                || !policy.blocks("شاهد سكس مجانا")
                || !policy.blocks("شاهد سکسی")
                || !policy.blocks("Watch nudephotos")
                || !policy.blocks("Watch p0rnhub")
                || policy.blocks("Free sexual health education and addiction recovery support")
                || policy.blocks("شاهد سكسونيا"))
            throw new AssertionError("Actual adult-text seed classification failed");
        try (InputStream input = getTargetContext().getAssets().open("terms.json")) {
            try {
                AdultPolicyAssets.load(input, "0".repeat(64));
                throw new AssertionError("Tampered terms metadata accepted");
            } catch (java.io.IOException expected) { }
        }
        if (WordRuleList.compile("porn").matches("porn addiction recovery").isEmpty())
            throw new AssertionError("Custom literal rule lost independence");
    }

    private void testAdultCommitment() {
        String preferenceName = "hisn_adult_test_" + android.os.SystemClock.elapsedRealtime();
        try {
            PolicyStore store = new PolicyStore(getTargetContext(), preferenceName);
            if (store.snapshot().adultWordsEnabled) throw new AssertionError("Upgrade opt-in defaults to true");
            try {
                store.startCommitment(7);
                throw new AssertionError("Empty commitment accepted");
            } catch (IllegalStateException expected) { }
            store.update("", "", Collections.emptySet(), Collections.emptySet(), true);
            if (store.snapshot().hasConfiguredRestrictions()) throw new AssertionError("Unselected policy marked configured");
            store.update("", "", Collections.emptySet(), Collections.singleton("com.example.fixture"));
            if (!store.snapshot().adultWordsEnabled) throw new AssertionError("Legacy update erased built-in opt-in");
            store.startCommitment(7);
            try {
                store.update("", "", Collections.emptySet(), Collections.singleton("com.example.fixture"), false);
                throw new AssertionError("Commitment allowed disabling built-in checks");
            } catch (IllegalStateException expected) { }
            PolicyStore restored = new PolicyStore(getTargetContext(), preferenceName);
            if (!restored.snapshot().adultWordsEnabled || !restored.snapshot().hasConfiguredRestrictions())
                throw new AssertionError("Built-in policy did not persist");
        } finally { forget(getTargetContext(), preferenceName); }
    }

    private void testLegacyEmptyCommitment() {
        String name = "hisn_legacy_test_" + android.os.SystemClock.elapsedRealtime();
        android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences(name, android.content.Context.MODE_PRIVATE);
        try {
            int boot = android.provider.Settings.Global.getInt(getTargetContext().getContentResolver(),
                    android.provider.Settings.Global.BOOT_COUNT, -1);
            if (boot < 0 || !prefs.edit().putLong("remaining", 3600000)
                    .putLong("elapsed", android.os.SystemClock.elapsedRealtime()).putInt("boot", boot).commit())
                throw new AssertionError("Legacy fixture setup failed");
            PolicyStore migrated = new PolicyStore(getTargetContext(), name);
            if (migrated.remainingMillis() <= 0 || migrated.remainingMillis() > 3600000
                    || migrated.snapshot().hasConfiguredRestrictions() || migrated.snapshot().adultWordsEnabled)
                throw new AssertionError("Legacy empty commitment migration changed protection");
            migrated.update("blocked.example", "", Collections.emptySet(), Collections.emptySet());
            migrated.startCommitment(30);
            try {
                migrated.startCommitment(7);
                throw new AssertionError("Migrated commitment shortened");
            } catch (IllegalStateException expected) { }
        } finally { forget(getTargetContext(), name); }
    }

    private void testSavedRules() {
        String preferenceName = "hisn_test_" + android.os.SystemClock.elapsedRealtime();
        try {
            PolicyStore store = new PolicyStore(getTargetContext(), preferenceName);
            store.update("b\u00fccher.example", "\ufb03".repeat(42), Collections.emptySet(), Collections.emptySet());
            PolicyStore unicodeRestored = new PolicyStore(getTargetContext(), preferenceName);
            if (!unicodeRestored.snapshot().domains.blocks("b\u00fccher.example")
                    || !unicodeRestored.wordText().equals("ffi".repeat(42))) {
                throw new AssertionError("Canonical Unicode rules did not reload");
            }
            expectRejectedRules(store, "b\u00fccher.example", "\ufb03".repeat(43));
            StringBuilder expandedWords = new StringBuilder();
            for (int i = 0; i < 700; i++) {
                if (i > 0) expandedWords.append('\n');
                expandedWords.append("\ufb03".repeat(40)).append(i);
            }
            if (expandedWords.length() > 65536 || String.join("\n", WordRuleList.compile(expandedWords.toString())
                    .configuredRules()).length() <= 65536) {
                throw new AssertionError("Word expansion fixture does not exercise the saved-list limit");
            }
            expectRejectedRules(store, "b\u00fccher.example", expandedWords.toString());
            StringBuilder expandedDomains = new StringBuilder();
            String label = "\u00e9".repeat(12);
            for (int i = 0; i < 1000; i++) {
                if (i > 0) expandedDomains.append('\n');
                expandedDomains.append(label).append('.').append(label).append('.')
                        .append(label).append('.').append(label).append(i).append(".example");
            }
            DomainPolicy canonicalDomains = new DomainPolicy(java.util.Arrays.asList(expandedDomains.toString().split("\n")),
                    Collections.emptySet());
            if (expandedDomains.length() > 65536 || String.join("\n", canonicalDomains.blockedRules()).length() <= 65536) {
                throw new AssertionError("IDN expansion fixture does not exercise the saved-list limit");
            }
            expectRejectedRules(store, expandedDomains.toString(), "example");
            PolicyStore unchanged = new PolicyStore(getTargetContext(), preferenceName);
            if (!unchanged.domainText().equals(unicodeRestored.domainText())
                    || !unchanged.wordText().equals(unicodeRestored.wordText())) {
                throw new AssertionError("Rejected Unicode rules changed saved settings");
            }
            store.update("blocked.example", "example", Collections.singleton("com.example.fixture"), Collections.emptySet());
            store.startCommitment(7);
            try {
                store.update("", "", Collections.emptySet(), Collections.emptySet());
                throw new AssertionError("Commitment allowed removal");
            } catch (IllegalStateException expected) { }
            store.update("blocked.example\nmore.example", "example\nother", Collections.singleton("com.example.fixture"), Collections.emptySet());
            PolicyStore restored = new PolicyStore(getTargetContext(), preferenceName);
            if (!restored.snapshot().domains.blocks("more.example") || restored.remainingMillis() <= 0) {
                throw new AssertionError("Policy did not persist");
            }
        } finally {
            forget(getTargetContext(), preferenceName);
        }
    }

    /**
     * Android reads an unparseable preferences file as empty, like a new installation. A damaged main
     * file is replaced by its copy; with both damaged the commitment stays active as unknown; deleting
     * both, as clearing app data does, starts again with nothing.
     */
    private void testDamagedSettings() throws java.io.IOException {
        android.content.Context context = getTargetContext();
        String name = "hisn_damage_test_" + java.util.UUID.randomUUID();
        try {
            PolicyStore store = new PolicyStore(context, name);
            if (store.remainingMillis() != 0 || store.commitmentUnknown() || store.restoredFromCopy())
                throw new AssertionError("New settings started with a commitment or notice");
            store.update("blocked.example", "", Collections.singleton("com.example.fixture"), Collections.emptySet());
            store.startCommitment(1);
            corrupt(context, name);
            PolicyStore restored = new PolicyStore(context, name);
            if (!restored.restoredFromCopy() || restored.commitmentUnknown() || restored.remainingMillis() <= 0
                    || restored.remainingMillis() > 86400000L || !restored.snapshot().domains.blocks("blocked.example"))
                throw new AssertionError("Corrupt main file was not replaced by its copy");
            restored.confirmRestored();
            if (new PolicyStore(context, name).restoredFromCopy()) throw new AssertionError("Confirmation was not saved");
            corrupt(context, name);
            corrupt(context, name + PolicyStore.COPY_SUFFIX);
            PolicyStore unknown = new PolicyStore(context, name);
            if (!unknown.commitmentUnknown() || unknown.remainingMillis() <= 86400000L)
                throw new AssertionError("Unreadable commitment was not kept active");
            unknown.update("blocked.example", "", Collections.emptySet(), Collections.emptySet());
            try {
                unknown.update("", "", Collections.emptySet(), Collections.emptySet());
                throw new AssertionError("Unknown commitment allowed weakening");
            } catch (IllegalStateException expected) { }
            if (!new PolicyStore(context, name).commitmentUnknown()) throw new AssertionError("Unknown commitment was not saved");
            // Intact settings that a stricter version cannot accept are refused and left as they are.
            forget(context, name);
            java.util.Set<String> apps = new java.util.HashSet<>();
            for (int i = 0; i <= PolicyStore.MAX_APPS; i++) apps.add("com.example.app" + i);
            java.util.Map<String, Object> tooMany = app.hisn.core.SavedSettings.values("", "", apps, Collections.emptySet(),
                    false, false, app.hisn.core.DailyAppSchedule.disabled(), new app.hisn.core.CommitmentClock(0, 0, 0), false, false);
            for (String file : new String[]{name, name + PolicyStore.COPY_SUFFIX}) {
                android.content.SharedPreferences.Editor editor = context.getSharedPreferences(file, 0).edit();
                for (java.util.Map.Entry<String, Object> entry : tooMany.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) editor.putString(entry.getKey(), (String) value);
                    else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                    else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                    else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
                    else { @SuppressWarnings("unchecked") java.util.Set<String> set = (java.util.Set<String>) value; editor.putStringSet(entry.getKey(), set); }
                }
                if (!editor.commit()) throw new AssertionError("Fixture not saved");
            }
            try { new PolicyStore(context, name); throw new AssertionError("Intact but invalid settings loaded"); }
            catch (IllegalStateException expected) { }
            if (!context.getSharedPreferences(name, 0).getAll().equals(tooMany))
                throw new AssertionError("Refused settings were changed");
        } finally { forget(context, name); }
        try {
            PolicyStore cleared = new PolicyStore(context, name);
            if (cleared.remainingMillis() != 0 || cleared.commitmentUnknown() || cleared.snapshot().hasConfiguredRestrictions())
                throw new AssertionError("Deleted settings did not start again");
        } finally { forget(context, name); }
    }

    // Deleting is the only public way to drop the cached file; then an unparseable file takes its place.
    private static void corrupt(android.content.Context context, String name) throws java.io.IOException {
        context.deleteSharedPreferences(name);
        java.io.File file = new java.io.File(new java.io.File(context.getDataDir(), "shared_prefs"), name + ".xml");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            output.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n<long name=".getBytes(StandardCharsets.UTF_8));
        }
    }

    // Instrumentation runs under the target UID. Tests use only generated names, never the real
    // hisn_policy preferences or the owner's commitment, and delete both files afterwards.
    private static void forget(android.content.Context context, String name) {
        context.deleteSharedPreferences(name);
        context.deleteSharedPreferences(name + PolicyStore.COPY_SUFFIX);
    }

    private static void expectRejectedRules(PolicyStore store, String domains, String words) {
        try {
            store.update(domains, words, Collections.emptySet(), Collections.emptySet());
            throw new AssertionError("Unreloadable canonical rules were accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static byte[] query(String name) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] header = {0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0};
        bytes.write(header, 0, header.length);
        for (String label : name.split("\\.")) {
            byte[] value = label.getBytes(StandardCharsets.US_ASCII);
            bytes.write(value.length); bytes.write(value, 0, value.length);
        }
        bytes.write(0); bytes.write(0); bytes.write(1); bytes.write(0); bytes.write(1);
        return bytes.toByteArray();
    }
}
