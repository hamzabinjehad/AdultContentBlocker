package app.hisn.android;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tighten-only application of authenticated version-2 policy rules.
 *
 * <p>The caller must verify the signature, device binding, expiry and counter
 * first. This class never removes a blocked app/domain and never turns a
 * managed choice off.
 */
final class GuardianPolicyApplier {
    static final class Result {
        final boolean ok;
        final String reason;
        final boolean owner;
        final boolean lockdown;
        final boolean strict;
        final boolean suspend;
        final Set<String> blockedApps;
        final Set<String> blockedDomains;
        final Set<String> suspendedApps;

        Result(boolean ok, String reason, boolean owner, boolean lockdown, boolean strict,
               boolean suspend, Set<String> blockedApps, Set<String> blockedDomains,
               Set<String> suspendedApps) {
            this.ok = ok;
            this.reason = reason;
            this.owner = owner;
            this.lockdown = lockdown;
            this.strict = strict;
            this.suspend = suspend;
            this.blockedApps = blockedApps;
            this.blockedDomains = blockedDomains;
            this.suspendedApps = suspendedApps;
        }

        String summarize() {
            return "ok=" + ok + " reason=" + reason + " owner=" + owner
                    + " lockdown=" + lockdown + " strict=" + strict
                    + " suspend=" + suspend + " blockedApps=" + blockedApps.size()
                    + " blockedDomains=" + blockedDomains.size();
        }
    }

    static Result apply(Context context, byte[] rulesBytes) {
        try {
            JSONObject rules = new JSONObject(new String(rulesBytes, StandardCharsets.UTF_8));
            PolicyStore store = PolicyStore.get(context);
            boolean keep = store.uninstallLock() || rules.optBoolean("keep_installed", false);
            boolean lockdown = store.managedLockdown() || rules.optBoolean("lockdown", false);
            boolean strict = store.managedStrict() || rules.optBoolean("strict", false);
            boolean suspend = store.suspendBlocked() || rules.optBoolean("suspend_blocked", false);

            Set<String> apps = new LinkedHashSet<>(store.snapshot().blockedApps);
            JSONArray blocked = rules.optJSONArray("blocked_apps");
            if (blocked != null) {
                for (int i = 0; i < blocked.length(); i++) apps.add(blocked.getString(i));
            }
            Set<String> domains = new LinkedHashSet<>(store.snapshot().domains.blockedRules());
            JSONArray blockedDomains = rules.optJSONArray("blocked_domains");
            List<String> incomingDomains = new ArrayList<>();
            if (blockedDomains != null) {
                for (int i = 0; i < blockedDomains.length(); i++) {
                    String domain = blockedDomains.getString(i);
                    incomingDomains.add(domain);
                    domains.add(domain);
                }
            }
            String expectedHash = rules.optString("blocked_domains_sha256", null);
            if (expectedHash != null) {
                String actual = app.hisn.core.DomainListHash.sha256(incomingDomains);
                if (!actual.equalsIgnoreCase(expectedHash)) {
                    return new Result(false, "domain-hash-mismatch", false, false, false, false,
                            new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
                }
            }
            if ((blocked != null && blocked.length() > 0)
                    || (blockedDomains != null && blockedDomains.length() > 0)) {
                store.updateFromGuardian(String.join("\n", domains),
                        String.join("\n", store.snapshot().words.configuredRules()),
                        apps, store.snapshot().scannedApps);
            }
            store.updateManagedChoice(keep, store.installLock(), lockdown, strict, suspend);
            UninstallProtectionController.Result applied = UninstallProtectionController.sync(context);
            UninstallProtectionController.Status status = UninstallProtectionController.read(context);
            return new Result(applied.ok, applied.reason, status.deviceOwner,
                    status.managedLockdown, status.managedStrict, status.suspendBlocked,
                    apps, domains, status.suspendedApps);
        } catch (JSONException | RuntimeException invalid) {
            return new Result(false, "policy-apply-failed", false, false, false, false,
                    new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    private GuardianPolicyApplier() { }
}
