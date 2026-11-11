package app.hisn.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import app.hisn.core.AdultTextPolicy;
import app.hisn.core.DnsExchange;
import app.hisn.core.DnsPolicy;
import app.hisn.core.DomainPolicy;
import app.hisn.core.DomainSeed;
import app.hisn.core.HostTermPolicy;
import app.hisn.core.AppTimeLimit;
import app.hisn.core.LayerWatch;
import app.hisn.core.SeedFreshness;
import app.hisn.core.SignedManifest;
import app.hisn.core.WordRuleList;
import app.hisn.core.VisibleTextSample;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
            // The version-aware loader is what a downloaded generation uses; the
            // bundled seed must pass the same path before a device relies on it.
            try (InputStream terms = getTargetContext().getAssets().open("terms.json")) {
                HostTermPolicy host = AdultPolicyAssets.loadHostPolicy(terms,
                        BuildConfig.TERMS_SEED_SHA256, BuildConfig.DOMAIN_SEED_VERSION);
                if (!host.matches("sex.example")) throw new AssertionError("Host terms check failed");
            }
            try (InputStream terms = getTargetContext().getAssets().open("terms.json")) {
                AdultTextPolicy text = AdultPolicyAssets.load(terms,
                        BuildConfig.TERMS_SEED_SHA256, BuildConfig.DOMAIN_SEED_VERSION);
                if (!text.blocksPromotion("watch free porn")) throw new AssertionError("Text terms check failed");
            }
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
            testPolicyRevision();
            testCommitmentMutations();
            testTimeLimits();
            testUninstallGuard();
            testLegacyEmptyCommitment();
            testDamagedSettings();
            testLayerHealthAndSeedAge();
            testListGenerationFixture();
            VisibleTextSample sample = new VisibleTextSample();
            sample.append("blocked " + "ordinary ".repeat(1000));
            if (WordRuleList.compile("blocked").matches(sample.text()).isEmpty())
                throw new AssertionError("Long text inspection failed");
            sample.append("safe ".repeat(10000));
            if (!sample.isPartial() || sample.text().length() > 32768)
                throw new AssertionError("Inspection budget failed");
            results.putString("stream", "\nPASS: verified APK seed loaded " + seed.domainCount
                    + " domains; Arabic matching, boundaries, local DNS block, saved rules, Unicode persistence, long-text sampling, verified adult terms, visual settings, daily time limits, commitment, policy-version and copy-recovery, damaged-settings and invalid-settings, uninstall-guard, layer-watch expectation, seed-age checks, and signed fixture generation adoption and rollback passed. No VPN was enabled; no accessibility service, admin or device-owner policy was changed.\n");
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

    /** A policy version advances only for an effective change, survives reload/copy recovery,
     *  and is not moved by an identical save or by starting a commitment. */
    private void testPolicyRevision() {
        String name = "hisn_revision_test_" + java.util.UUID.randomUUID();
        android.content.Context context = getTargetContext();
        try {
            PolicyStore store = new PolicyStore(context, name);
            if (store.policyRevision() != 0) throw new AssertionError("Fresh policy has a version");
            store.update("first.example", "", Collections.emptySet(), Collections.emptySet());
            long first = store.policyRevision();
            if (first != 1) throw new AssertionError("First change did not create version 1");
            store.update("first.example", "", Collections.emptySet(), Collections.emptySet());
            if (store.policyRevision() != first) throw new AssertionError("Identical save advanced the version");
            store.update("second.example", "", Collections.emptySet(), Collections.emptySet());
            long second = store.policyRevision();
            if (second != first + 1) throw new AssertionError("Changed list did not advance the version");
            store.startCommitment(1);
            if (store.policyRevision() != second) throw new AssertionError("Commitment start advanced the version");
            store.updateUninstallLock(true);
            if (store.policyRevision() != second)
                throw new AssertionError("Removal choice changed the layer policy version");
            PolicyStore reloaded = new PolicyStore(context, name);
            if (reloaded.policyRevision() != second || !reloaded.uninstallLock())
                throw new AssertionError("Version did not survive reload");
            // Lose the main file; the intact copy must carry the same version and rules.
            context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().clear().commit();
            PolicyStore recovered = new PolicyStore(context, name);
            if (recovered.policyRevision() != second
                    || !recovered.snapshot().domains.blockedRules().contains("second.example"))
                throw new AssertionError("Copy recovery lost the policy version");
        } finally { forget(context, name); }
    }

    /** Every saved-rule editor is refused at the store boundary during a commitment,
     *  and a refused edit leaves both the rules and the policy version intact. */
    private void testCommitmentMutations() {
        String name = "hisn_mutation_test_" + java.util.UUID.randomUUID();
        android.content.Context context = getTargetContext();
        try {
            PolicyStore store = new PolicyStore(context, name);
            java.util.Set<String> blocked = java.util.Collections.singleton("com.example.blocked");
            java.util.Set<String> scanned = java.util.Collections.singleton("com.example.scanned");
            store.update("blocked.example", "word", blocked, scanned, true);
            store.updateVisual(true);
            store.updateShortVideo(true);
            store.updateSchedule(new app.hisn.core.DailyAppSchedule(true, 0, 1380,
                    java.util.Collections.emptySet(), true));
            store.updateTimeLimits(new AppTimeLimit(java.util.Collections.singletonMap(
                    "com.example.scanned", 30)));
            store.startCommitment(1);
            final long version = store.policyRevision();
            refused(() -> store.update("", "", java.util.Collections.emptySet(),
                    java.util.Collections.emptySet(), false), "domain/word/app removal");
            refused(() -> store.updateVisual(false), "visual switch off");
            refused(() -> store.updateShortVideo(false), "short-video switch off");
            refused(() -> store.updateSchedule(app.hisn.core.DailyAppSchedule.disabled()), "schedule off");
            refused(() -> store.updateTimeLimits(AppTimeLimit.none()), "time limits cleared");
            refused(() -> store.updateFromGuardian("", "", java.util.Collections.emptySet(),
                    java.util.Collections.emptySet()), "guardian weakening");
            if (store.policyRevision() != version) throw new AssertionError("Refused edit moved the policy version");
            PolicyStore.Snapshot reloaded = new PolicyStore(context, name).snapshot();
            if (!reloaded.adultWordsEnabled || !reloaded.visualEnabled || !reloaded.shortVideoEnabled
                    || !reloaded.schedule.enabled || !reloaded.timeLimits.configured()
                    || !reloaded.blockedApps.equals(blocked) || !reloaded.scannedApps.equals(scanned))
                throw new AssertionError("Refused edits damaged the saved rules");
        } finally { forget(context, name); }
    }

    private static void refused(Runnable action, String step) {
        try { action.run(); throw new AssertionError("Commitment allowed " + step); }
        catch (IllegalStateException expected) { }
    }

    /** Daily time limits survive a sealed-store reload, keep working across other
     *  editors, and are frozen by the protection lock like every other saved rule. */
    private void testTimeLimits() {
        String preferenceName = "hisn_limits_test_" + java.util.UUID.randomUUID();
        android.content.Context context = getTargetContext();
        try {
            PolicyStore store = new PolicyStore(context, preferenceName);
            AppTimeLimit limits = new AppTimeLimit(java.util.Collections.singletonMap("com.example.limited", 30));
            store.updateTimeLimits(limits);
            PolicyStore reloaded = new PolicyStore(context, preferenceName);
            if (!reloaded.snapshot().timeLimits.equals(limits))
                throw new AssertionError("Time limits did not persist");
            if (!reloaded.snapshot().hasConfiguredRestrictions())
                throw new AssertionError("Time limits do not count as configured rules");
            reloaded.updateVisual(true);
            reloaded.updateSchedule(app.hisn.core.DailyAppSchedule.disabled());
            if (!new PolicyStore(context, preferenceName).snapshot().timeLimits.equals(limits))
                throw new AssertionError("Another editor erased the time limits");
            reloaded.startCommitment(1);
            try {
                reloaded.updateTimeLimits(AppTimeLimit.none());
                throw new AssertionError("Time limits changed during a protection lock");
            } catch (IllegalStateException expected) { }
            if (!new PolicyStore(context, preferenceName).snapshot().timeLimits.equals(limits))
                throw new AssertionError("Rejected limit change damaged the saved limits");
        } finally { forget(context, preferenceName); }
    }

    private void testUninstallGuard() {
        String preferenceName = "hisn_uninstall_test_" + java.util.UUID.randomUUID();
        android.content.Context context = getTargetContext();
        try {
            PolicyStore store = new PolicyStore(context, preferenceName);
            if (store.uninstallLock()) throw new AssertionError("Uninstall lock defaulted on");
            if (store.installLock()) throw new AssertionError("Install lock defaulted on");
            if (store.managedLockdown()) throw new AssertionError("Managed lockdown defaulted on");
            store.updateUninstallLock(true);
            store.updateInstallLock(true);
            store.updateManagedLockdown(true);
            if (!new PolicyStore(context, preferenceName).uninstallLock())
                throw new AssertionError("Uninstall lock did not persist");
            if (!new PolicyStore(context, preferenceName).installLock())
                throw new AssertionError("Install lock did not persist");
            if (!new PolicyStore(context, preferenceName).managedLockdown())
                throw new AssertionError("Managed lockdown did not persist");
            store.update("blocked.example", "", Collections.emptySet(), Collections.emptySet());
            store.startCommitment(1);
            try {
                store.updateUninstallLock(false);
                throw new AssertionError("Commitment allowed disabling uninstall protection");
            } catch (IllegalStateException expected) { }
            try {
                store.updateInstallLock(false);
                throw new AssertionError("Commitment allowed disabling install protection");
            } catch (IllegalStateException expected) { }
            try {
                store.updateManagedLockdown(false);
                throw new AssertionError("Commitment allowed disabling managed lockdown");
            } catch (IllegalStateException expected) { }
            try {
                store.updateManagedChoice(false, false);
                throw new AssertionError("Commitment allowed clearing both managed choices");
            } catch (IllegalStateException expected) { }
            if (!store.uninstallLock() || !store.installLock() || !store.managedLockdown())
                throw new AssertionError("Rejected change cleared a managed choice");
            UninstallProtectionController.Status status = UninstallProtectionController.read(context);
            if (status.state() == null) throw new AssertionError("Uninstall status unavailable");
            if (status.restrictions.size() != 0 && !status.deviceOwner)
                throw new AssertionError("Unmanaged install reported held restrictions");
            if (!status.restrictionsMatch() && !status.deviceOwner)
                throw new AssertionError("Unmanaged install reported a restriction mismatch");
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

    /** Read-only checks for the 0.8.x watchdog expectations and the bundled-list age. */
    private void testLayerHealthAndSeedAge() {
        android.content.Context context = getTargetContext();
        Long age = SeedFreshness.ageDays(BuildConfig.DOMAIN_SEED_BUILT, System.currentTimeMillis());
        if (age == null || age < 0) throw new AssertionError("Bundled seed age is unreadable or negative");
        boolean keep = PolicyStore.get(context).uninstallLock();
        java.util.Set<LayerWatch.Layer> expected = LayerWatchdog.expected(context);
        boolean accessibility = BuildConfig.SELF_PROTECTION && keep;
        if (expected.contains(LayerWatch.Layer.ACCESSIBILITY) != accessibility)
            throw new AssertionError("Watchdog accessibility expectation disagrees with the keep switch");
        if (expected.contains(LayerWatch.Layer.DNS) != DnsVpnService.resumeRequested(context))
            throw new AssertionError("Watchdog DNS expectation disagrees with the resume flag");
        if (LayerWatchdog.running() == null) throw new AssertionError("Watchdog running set is null");
        if (!LayerWatchdog.lostSignature(context).isEmpty() && LayerWatchdog.lastCheckedAt(context) == 0)
            throw new AssertionError("A lost-layer signature has no recorded check time");
        if (LayerWatchdog.lastCheckedAt(context) < 0)
            throw new AssertionError("Watchdog last-check time is negative");
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
            try {
                // The 0.9.0 protection lock is absolute: an inherited lock
                // refuses additions too, even though it holds no rules.
                migrated.update("blocked.example", "", Collections.emptySet(), Collections.emptySet());
                throw new AssertionError("Migrated lock allowed adding rules");
            } catch (IllegalStateException expected) { }
            try {
                migrated.startCommitment(30);
                throw new AssertionError("Empty locked policy started a longer lock");
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
            try {
                // The protection lock freezes additions too, not only weakening.
                store.update("blocked.example\nmore.example", "example\nother",
                        Collections.singleton("com.example.fixture"), Collections.emptySet());
                throw new AssertionError("Commitment allowed adding rules");
            } catch (IllegalStateException expected) { }
            PolicyStore restored = new PolicyStore(getTargetContext(), preferenceName);
            if (!restored.snapshot().domains.blocks("blocked.example")
                    || restored.snapshot().domains.blocks("more.example")
                    || restored.remainingMillis() <= 0) {
                throw new AssertionError("Rejected additions changed the locked policy");
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
            try {
                // A lock read as unknown still freezes every rule in both directions.
                unknown.update("blocked.example", "", Collections.emptySet(), Collections.emptySet());
                throw new AssertionError("Unknown commitment allowed adding rules");
            } catch (IllegalStateException expected) { }
            // An identical save is not a rule change; it must not clear the unknown state.
            unknown.update("", "", Collections.emptySet(), Collections.emptySet());
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

    /**
     * A signed fixture generation exercises the same verification and atomic
     * adoption path as a downloaded list, including terms. It is deliberately
     * last: the fixture pointer is removed and the store invalidated afterwards.
     */
    private void testListGenerationFixture() throws Exception {
        android.content.Context context = getTargetContext();
        byte[] manifest = asset("manifest.json");
        String signature = new String(asset("manifest.json.sig"), StandardCharsets.US_ASCII).trim();
        byte[] domains = asset("domains_core.txt");
        byte[] terms = asset("terms.json");
        byte[] key = hex(new String(asset("public_key.hex"), StandardCharsets.US_ASCII).trim());

        AdultListUpdater.Result installed = AdultListUpdater.installForTest(
                context, manifest, signature, domains, terms, key);
        if (!installed.updated || installed.generation != 7) throw new AssertionError("Fixture generation not installed");
        HostListStore.Active active = HostListStore.active(context);
        if (active.generation != 7 || !active.policy.blocks("fixture0001.example")) {
            throw new AssertionError("Fixture domain list not active");
        }
        if (active.hostTerms == null || !active.hostTerms.matches("fixturehost.example")) {
            throw new AssertionError("Fixture host terms not active");
        }
        if (AdultPolicyAssets.policy() == null) throw new AssertionError("Fixture text terms not adopted");

        String badSignature = (signature.charAt(0) == '0' ? '1' : '0') + signature.substring(1);
        try {
            AdultListUpdater.installForTest(context, manifest, badSignature, domains, terms, key);
            throw new AssertionError("Fixture with a bad signature was accepted");
        } catch (SignedManifest.Invalid expected) { }

        byte[] badTerms = terms.clone();
        badTerms[0] ^= 1;
        AdultListUpdater.Result rejected = AdultListUpdater.installForTest(
                context, manifest, signature, domains, badTerms, key);
        if (rejected.updated || !"artifact".equals(rejected.error)) {
            throw new AssertionError("Fixture with damaged terms was not rejected");
        }
        if (HostListStore.active(context).generation != 7) {
            throw new AssertionError("A rejected update replaced the active generation");
        }

        deleteTree(new File(context.getFilesDir(), HostListStore.DIRECTORY));
        context.deleteSharedPreferences("hisn_list_update");
        HostListStore.invalidate();
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream input = getContext().getAssets().open("list-fixture/" + name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        }
    }

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
