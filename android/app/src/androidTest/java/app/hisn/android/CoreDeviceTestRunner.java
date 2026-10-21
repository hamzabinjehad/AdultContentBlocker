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
            testLegacyEmptyCommitment();
            VisibleTextSample sample = new VisibleTextSample();
            sample.append("blocked " + "ordinary ".repeat(1000));
            if (WordRuleList.compile("blocked").matches(sample.text()).isEmpty())
                throw new AssertionError("Long text inspection failed");
            sample.append("safe ".repeat(10000));
            if (!sample.isPartial() || sample.text().length() > 32768)
                throw new AssertionError("Inspection budget failed");
            results.putString("stream", "\nPASS: verified APK seed loaded " + seed.domainCount
                    + " domains; Arabic matching, boundaries, local DNS block, saved rules, Unicode persistence, long-text sampling, verified adult terms and commitment checks passed. No VPN was enabled.\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable failure) {
            StringBuilder detail = new StringBuilder();
            for (Throwable cause = failure; cause != null && detail.length() < 2048; cause = cause.getCause())
                detail.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage()).append('\n');
            results.putString("stream", "\nFAIL: " + detail);
            finish(Activity.RESULT_CANCELED, results);
        }
    }

    private void testAdultPolicy() throws Exception {
        app.hisn.core.AdultTextPolicy policy;
        try (InputStream input = getTargetContext().getAssets().open("terms.json")) {
            policy = AdultPolicyAssets.load(input, BuildConfig.TERMS_SEED_SHA256);
        }
        if (policy.ruleCount() < 4000 || !policy.blocks("Watch free porn videos now")
                || !policy.blocks("شاهد سكس مجانا")
                || policy.blocks("Free sexual health education and addiction recovery support"))
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
        } finally {
            getTargetContext().getSharedPreferences(preferenceName, android.content.Context.MODE_PRIVATE).edit().clear().commit();
        }
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
        } finally { prefs.edit().clear().commit(); }
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
            // Instrumentation runs under the target UID. Use only this generated
            // test file, never the real hisn_policy preferences or user commitment.
            getTargetContext().getSharedPreferences(preferenceName, android.content.Context.MODE_PRIVATE).edit().clear().commit();
        }
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
