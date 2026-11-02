package app.hisn.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import app.hisn.core.GuardianPairing;
import app.hisn.core.GuardianPolicy;

/**
 * Shell-only, debug-only runtime check for the Java pairing and policy code.
 *
 * <p>On API 31+ it uses an EC P-256 key inside AndroidKeyStore with sign and
 * key-agreement purposes; older runtimes fall back to a software P-256 key
 * wrapped by an AES-256-GCM Keystore key. This proves the wire format, JCA
 * paths and identity persistence on a running Android runtime. The product
 * QR/consent UI is still separate.
 */
public final class GuardianPairDebugActivity extends Activity {
    private static final String TAG = "HisnPairDebug";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String report = "FAIL";
        try {
            byte[] offerBytes = Base64.decode(getIntent().getStringExtra("offer_b64"), Base64.DEFAULT);
            GuardianPairing.Offer offer = GuardianPairing.parseOffer(
                    new String(offerBytes, StandardCharsets.UTF_8));
            GuardianIdentity.Keys keys = GuardianIdentity.loadOrCreate(this);
            String response = GuardianPairing.buildResponse(keys.privateKey, keys.publicKey, offer);
            String sas = GuardianPairing.deviceSas(keys.privateKey, keys.publicKey, offer);
            write("pairing-response.json", response);
            write("pairing-sas.txt", sas);
            report = "PASS sas=" + sas + " device="
                    + GuardianPairing.deviceId(keys.publicKey) + " identity=" + keys.mode;
            String policyB64 = getIntent().getStringExtra("policy_b64");
            if (policyB64 != null) {
                String policy = new String(Base64.decode(policyB64, Base64.DEFAULT),
                        StandardCharsets.UTF_8);
                long now = getIntent().getLongExtra("now", System.currentTimeMillis() / 1000L);
                GuardianPolicy.Verified verified = GuardianPolicy.verify(offer.guardian, policy,
                        GuardianPairing.deviceId(keys.publicKey), now);
                String rulesJson = new String(verified.rulesJson, StandardCharsets.UTF_8);
                write("pairing-policy.json", rulesJson);
                report += " policyCounter=" + verified.counter + " policyRules=" + rulesJson;

                GuardianPolicyApplier.Result applied = GuardianPolicyApplier.apply(this,
                        verified.rulesJson);
                try {
                    JSONObject outcome = new JSONObject();
                    outcome.put("owner", applied.owner);
                    outcome.put("lockdown", applied.lockdown);
                    outcome.put("strict", applied.strict);
                    outcome.put("suspend", applied.suspend);
                    outcome.put("sync", applied.ok);
                    outcome.put("reason", applied.reason);
                    outcome.put("blockedApps", new JSONArray(applied.blockedApps));
                    outcome.put("blockedDomains", new JSONArray(applied.blockedDomains));
                    outcome.put("suspendedApps", new JSONArray(applied.suspendedApps));
                    write("pairing-applied.json", outcome.toString());
                    report += " applied=" + applied.ok + "/" + applied.reason;
                } catch (org.json.JSONException invalid) {
                    throw new IllegalStateException("Cannot apply the verified rules", invalid);
                }
            }
        } catch (RuntimeException failure) {
            report = "FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        Log.i(TAG, report);
        write("pairing-result.txt", report);
        finish();
    }

    private void write(String name, String text) {
        try (FileOutputStream out = openFileOutput(name, MODE_PRIVATE)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { /* pairing-result.txt still holds the report. */ }
    }
}
