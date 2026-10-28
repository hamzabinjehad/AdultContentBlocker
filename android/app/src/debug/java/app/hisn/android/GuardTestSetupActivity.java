package app.hisn.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Shell-only, debug-only fixture setup without restarting the accessibility process. */
public final class GuardTestSetupActivity extends Activity {
    private static final String FIXTURE = "app.hisn.android.test";
    private static final String WORD = "hisnfixtureword";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode");
        try {
            if ("status".equals(mode)) {
                app.hisn.core.DailyAppSchedule schedule = PolicyStore.get(this).snapshot().schedule;
                report("CONNECTED " + TextGuardService.connected + " OUTCOME " + TextGuardService.inspectionOutcome
                        + " SCANS " + TextGuardService.inspectedScreens + " WORD_INTERVENTIONS " + TextGuardService.wordInterventions
                        + " COOLDOWN_ENFORCEMENTS " + TextGuardService.cooldownEnforcements
                        + " PERIODIC_INSPECTIONS " + TextGuardService.periodicInspections
                        + " SCHEDULE_INTERVENTIONS " + TextGuardService.scheduleInterventions
                        + " SCHEDULE_ENABLED " + schedule.enabled + " SCHEDULE_START " + schedule.startMinute
                        + " SCHEDULE_END " + schedule.endMinute + " SCHEDULE_APP_COUNT " + schedule.apps.size()
                        + " VPN " + DnsVpnService.state + " DNS_CHECK " + DnsVpnService.connectionCheck.state
                        + " DNS_LOCAL_BLOCK " + DnsVpnService.connectionCheck.localBlockPassed
                        + " DNS_TCP_CHECK " + DnsVpnService.connectionCheck.tcpPassed
                        + " TCP_QUERIES " + DnsVpnService.tcpQueries.get()
                        + " TCP_REPLIES " + DnsVpnService.tcpReplies.get()
                        + " UDP_TRUNCATED " + DnsVpnService.udpTruncated.get()
                        + " DNS_FAILURE " + DnsVpnService.connectionCheck.failure
                        + " DNS_ERROR " + DnsVpnService.lastDnsFailure
                        + " DNS_BLOCKED " + DnsVpnService.blocked.get()
                        + " DNS_FORWARDED " + DnsVpnService.forwarded.get()
                        + " DNS_ERRORS " + DnsVpnService.errors.get()
                        + " DNS_UNSUPPORTED " + DnsVpnService.unsupportedPackets.get()
                        + " DNS_MALFORMED " + DnsVpnService.malformedPackets.get()
                        + " NETWORK_CHANGES " + DnsVpnService.networkChanges.get()
                        + " NETWORK_MONITOR " + DnsVpnService.networkMonitoring
                        + " ADULT_ASSETS " + AdultPolicyAssets.state
                        + " " + DnsVpnService.browserCanaryStatus());
                return;
            }
            if ("restriction-screen".equals(mode)) {
                // Opens the restriction screen and its pause without needing a live guard intervention.
                startActivity(new android.content.Intent(this, RestrictionActivity.class)
                        .putExtra("word", getIntent().getBooleanExtra("word", false))
                        .putExtra("schedule", getIntent().getBooleanExtra("schedule", false))
                        .putExtra("visual", getIntent().getBooleanExtra("visual", false)));
                report("PASS " + mode);
                return;
            }
            if ("short-video-on".equals(mode) || "short-video-off".equals(mode)) {
                PolicyStore.get(this).updateShortVideo("short-video-on".equals(mode));
                report("PASS " + mode + " SHORT_VIDEO_INTERVENTIONS " + TextGuardService.shortVideoInterventions
                        + " CONNECTED " + TextGuardService.connected + " OUTCOME " + TextGuardService.inspectionOutcome);
                return;
            }
            if ("schedule-policy-test".equals(mode)) {
                SchedulePolicyChecks.run(this);
                report("PASS " + mode);
                return;
            }
            if ("adult-policy-test".equals(mode)) {
                try { AdultPolicyChecks.run(this); }
                catch (Exception failure) { throw new IllegalStateException("Synthetic APK adult-policy check failed", failure); }
                report("PASS " + mode);
                return;
            }
            if ("browser-canary-start".equals(mode) || "browser-canary-clear".equals(mode)) {
                if (!DnsVpnService.setBrowserCanary("browser-canary-start".equals(mode)))
                    throw new IllegalStateException("No live approved DNS session");
                report("PASS " + mode);
                return;
            }
            if ("network-start".equals(mode)) {
                if (android.net.VpnService.prepare(this) != null)
                    throw new IllegalStateException("Android VPN approval required");
                startForegroundService(new android.content.Intent(this, DnsVpnService.class));
                report("PASS network-start");
                return;
            }
            if ("network-check".equals(mode)) {
                if (!DnsVpnService.requestConnectionCheck()) throw new IllegalStateException("DNS session not ready for check");
                report("PASS network-check");
                return;
            }
            if ("network-local-tcp".equals(mode)) {
                report("PENDING network-local-tcp");
                new Thread(this::localTcpCheck, "hisn-local-tcp-fixture").start();
                return;
            }
            if ("network-change".equals(mode)) {
                if (!DnsVpnService.simulateNetworkChange()) throw new IllegalStateException("No live DNS session");
                report("PASS network-change");
                return;
            }
            if ("network-old-failure".equals(mode)) {
                if (!DnsVpnService.simulateOldNetworkFailure()) throw new IllegalStateException("No older network epoch");
                report("PASS network-old-failure");
                return;
            }
            PolicyStore store = PolicyStore.get(this);
            SharedPreferences backup = getSharedPreferences("hisn_live_fixture_backup", MODE_PRIVATE);
            if ("restore".equals(mode)) {
                if (!backup.getBoolean("active", false)) throw new IllegalStateException("No fixture backup");
                restore(store, backup);
            } else if ("setup".equals(mode) || "setup-adult".equals(mode) || "setup-schedule".equals(mode)) {
                if (!TextGuardService.connected) throw new IllegalStateException("Accessibility not connected");
                if (store.remainingMillis() != 0) throw new IllegalStateException("Real commitment active");
                if (backup.getBoolean("active", false)) throw new IllegalStateException("Previous fixture needs restoration");
                PolicyStore.Snapshot rules = store.snapshot();
                if (rules.blockedApps.contains(FIXTURE) || rules.scannedApps.contains(FIXTURE)
                        || rules.schedule.apps.contains(FIXTURE))
                    throw new IllegalStateException("Fixture has existing owner rules");
                boolean scheduleTest = "setup-schedule".equals(mode);
                if (scheduleTest && (rules.schedule.enabled || !rules.schedule.apps.isEmpty()))
                    throw new IllegalStateException("Owner has a saved schedule; no live schedule replacement allowed");
                if (!rules.words.matches("Hisn harmless protection test A harmless ordinary page ordinary").isEmpty())
                    throw new IllegalStateException("Existing word rules overlap harmless control text");
                if ("setup-adult".equals(mode) && (!rules.words.matches("Download a free porn blocker").isEmpty()
                        || !rules.words.matches("Watch porn addiction recovery resources").isEmpty()))
                    throw new IllegalStateException("Owner literal rules overlap built-in benign controls; rules will not be weakened for testing");
                if (!backup.edit().putBoolean("active", true).putString("domains", store.domainText())
                        .putString("words", store.wordText()).putStringSet("apps", rules.blockedApps)
                        .putStringSet("scans", rules.scannedApps).putBoolean("adult_words", rules.adultWordsEnabled)
                        .putBoolean("adult_test", "setup-adult".equals(mode)).putBoolean("schedule_test", scheduleTest)
                        .putInt("schedule_start", rules.schedule.startMinute).putInt("schedule_end", rules.schedule.endMinute)
                        .commit())
                    throw new IllegalStateException("Cannot save fixture backup");
                try {
                    if (scheduleTest) {
                        java.time.LocalTime time = java.time.LocalTime.now();
                        int start = (time.getHour() * 60 + time.getMinute() + 2) % 1440;
                        int end = (start + 3) % 1440;
                        if (!backup.edit().putInt("test_schedule_start", start).putInt("test_schedule_end", end).commit())
                            throw new IllegalStateException("Cannot save schedule test boundaries");
                        store.updateSchedule(new app.hisn.core.DailyAppSchedule(true, start, end, Collections.singleton(FIXTURE)));
                    } else {
                        Set<String> scans = new LinkedHashSet<>(rules.scannedApps);
                        scans.add(FIXTURE);
                        String words = store.wordText();
                        store.update(store.domainText(), words.isEmpty() ? WORD : words + "\n" + WORD, rules.blockedApps, scans,
                                rules.adultWordsEnabled || "setup-adult".equals(mode));
                        if (store.snapshot().adultWordsEnabled) AdultPolicyAssets.ensureLoaded(this);
                    }
                } catch (RuntimeException failure) { restore(store, backup); throw failure; }
            } else if ("block-app".equals(mode)) {
                if (!backup.getBoolean("active", false)) throw new IllegalStateException("No fixture backup");
                Set<String> apps = new LinkedHashSet<>(store.snapshot().blockedApps);
                apps.add(FIXTURE);
                store.update(store.domainText(), store.wordText(), apps, store.snapshot().scannedApps);
            } else throw new IllegalArgumentException("Unknown fixture action");
            report("PASS " + mode);
        } catch (RuntimeException | AssertionError failure) { report("FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); }
        finally { finish(); }
    }

    /** Fixed, harmless .invalid fixture through the actual app socket; no upstream lookup. */
    private void localTcpCheck() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(java.net.InetAddress.getByAddress(new byte[]{10, 111, 0, 2}), 53), 3000);
            socket.setSoTimeout(3000);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            bytes.write(new byte[]{0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0});
            for (String label : app.hisn.core.DnsTunnelProbe.BLOCKED_HOST.split("\\.")) {
                byte[] ascii = label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                bytes.write(ascii.length); bytes.write(ascii);
            }
            bytes.write(new byte[]{0, 0, 1, 0, 1});
            byte[] first = bytes.toByteArray(), second = first.clone(); second[1]++;
            java.io.DataOutputStream output = new java.io.DataOutputStream(socket.getOutputStream());
            for (byte[] query : new byte[][]{first, second}) { output.writeShort(query.length); output.write(query); }
            output.flush();
            java.io.DataInputStream input = new java.io.DataInputStream(socket.getInputStream());
            for (byte[] query : new byte[][]{first, second}) {
                int size = input.readUnsignedShort();
                if (size < 12 || size > 4096) throw new java.io.IOException("Frame bounds");
                byte[] reply = new byte[size]; input.readFully(reply);
                byte[] expected = new app.hisn.core.DnsPolicy(host -> app.hisn.core.DomainPolicy.Decision.BLOCK).evaluate(query).response;
                if (!java.util.Arrays.equals(reply, expected)) throw new java.io.IOException("Block mismatch");
            }
            report("PASS network-local-tcp");
        } catch (Exception failed) { report("FAIL network-local-tcp " + failed.getClass().getSimpleName()); }
    }

    private void restore(PolicyStore store, SharedPreferences backup) {
        // Never erase concurrent owner additions. Remove only our fixture additions,
        // and only when they did not already exist at setup time. A newly started
        // commitment still prevents weakening through PolicyStore.update.
        if (!backup.getBoolean("schedule_test", false)) {
            PolicyStore.Snapshot current = store.snapshot();
            Set<String> apps = new LinkedHashSet<>(current.blockedApps);
            Set<String> scans = new LinkedHashSet<>(current.scannedApps);
            apps.remove(FIXTURE);
            scans.remove(FIXTURE);
            Set<String> words = new LinkedHashSet<>(current.words.configuredRules());
            String previous = backup.getString("words", "");
            if (!new app.hisn.core.WordMatcher(previous.isEmpty() ? Collections.emptySet()
                    : java.util.Arrays.asList(previous.split("\n"))).configuredRules().contains(WORD)) words.remove(WORD);
            boolean adult = current.adultWordsEnabled;
            if (backup.getBoolean("adult_test", false) && !backup.getBoolean("adult_words", false)) adult = false;
            store.update(store.domainText(), String.join("\n", words), apps, scans, adult);
        } else {
            app.hisn.core.DailyAppSchedule schedule = store.snapshot().schedule;
            Set<String> remaining = new LinkedHashSet<>(schedule.apps);
            boolean fixtureOnly = remaining.size() == 1 && remaining.contains(FIXTURE);
            remaining.remove(FIXTURE);
            boolean originalTest = fixtureOnly && schedule.enabled
                    && schedule.startMinute == backup.getInt("test_schedule_start", -1)
                    && schedule.endMinute == backup.getInt("test_schedule_end", -1);
            // Restore the original disabled draft only if no owner edit occurred.
            // Otherwise remove only our selected fixture and preserve owner changes.
            store.updateSchedule(originalTest ? new app.hisn.core.DailyAppSchedule(false,
                    backup.getInt("schedule_start", 1320), backup.getInt("schedule_end", 420), Collections.emptySet())
                    : new app.hisn.core.DailyAppSchedule(schedule.enabled, schedule.startMinute, schedule.endMinute, remaining));
        }
        if (!backup.edit().clear().commit()) throw new IllegalStateException("Cannot clear fixture backup");
    }

    private void report(String value) {
        try (java.io.OutputStream out = openFileOutput("hisn_guard_test_status", MODE_PRIVATE)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot report fixture result", failure); }
    }
}
