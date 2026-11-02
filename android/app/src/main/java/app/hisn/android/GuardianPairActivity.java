package app.hisn.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;

import app.hisn.core.GuardianPairing;
import app.hisn.core.GuardianPolicy;

/**
 * Manual pairing screen: paste the guardian laptop's hisn:// code, compare the
 * six digits, then copy the response back. This is the fallback until a camera
 * QR scanner is available; it sends no browsing data and talks to no server.
 */
public final class GuardianPairActivity extends Activity {
    private EditText offerInput;
    private TextView status;
    private TextView responseView;
    private Button copyResponse;
    private LinearLayout policySection;
    private EditText policyInput;
    private TextView policyStatus;
    private String responseJson;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 24));

        content.addView(Ui.pageTitle(this, getString(R.string.guardian_pair_title)));
        content.addView(Ui.body(this, getString(R.string.guardian_pair_body)), Ui.margins(this, 0, 8));

        offerInput = Ui.input(this, R.string.guardian_pair_offer_hint, true);
        offerInput.setMinLines(2);
        offerInput.setMaxLines(5);
        content.addView(offerInput, Ui.margins(this, 10, 0));

        Button pair = Ui.button(this, R.string.guardian_pair_start, Ui.ButtonKind.PRIMARY);
        content.addView(pair, Ui.buttonParams(this));
        pair.setOnClickListener(view -> pair());

        status = Ui.text(this, "", 15, R.color.text_secondary);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status, Ui.margins(this, 8, 0));

        responseView = Ui.text(this, "", 12, R.color.text_tertiary);
        responseView.setTextIsSelectable(true);
        responseView.setVisibility(View.GONE);
        content.addView(responseView, Ui.margins(this, 6, 0));

        copyResponse = Ui.button(this, R.string.guardian_pair_copy, Ui.ButtonKind.TONAL);
        copyResponse.setEnabled(false);
        content.addView(copyResponse, Ui.buttonParams(this));
        copyResponse.setOnClickListener(view -> {
            if (responseJson == null) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Hisn pairing response", responseJson));
                Toast.makeText(this, R.string.guardian_pair_copied, Toast.LENGTH_SHORT).show();
            }
        });

        policySection = new LinearLayout(this);
        policySection.setOrientation(LinearLayout.VERTICAL);
        policySection.setVisibility(View.GONE);
        policySection.addView(Ui.strong(this, getString(R.string.guardian_policy_title)),
                Ui.margins(this, 0, 12));
        policySection.addView(Ui.body(this, getString(R.string.guardian_policy_body)));
        policyInput = Ui.input(this, R.string.guardian_policy_hint, true);
        policyInput.setMinLines(3);
        policyInput.setMaxLines(8);
        String policyPrefill = getIntent().getStringExtra("policy");
        String policyB64 = getIntent().getStringExtra("policy_b64");
        if (policyB64 != null) {
            policyPrefill = new String(Base64.decode(policyB64, Base64.DEFAULT), StandardCharsets.UTF_8);
        }
        if (policyPrefill != null) policyInput.setText(policyPrefill);
        policySection.addView(policyInput, Ui.margins(this, 8, 0));
        Button applyPolicy = Ui.button(this, R.string.guardian_policy_apply, Ui.ButtonKind.TONAL);
        policySection.addView(applyPolicy, Ui.buttonParams(this));
        policyStatus = Ui.text(this, "", 14, R.color.text_secondary);
        policyStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        policySection.addView(policyStatus, Ui.margins(this, 6, 0));
        applyPolicy.setOnClickListener(view -> applyPolicy());
        content.addView(policySection);
        prefillFrom(getIntent());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        prefillFrom(intent);
    }

    private void prefillFrom(Intent intent) {
        String offer = intent.getStringExtra("offer");
        String encoded = intent.getStringExtra("offer_b64");
        if (encoded != null) {
            offer = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        }
        if (offer != null) offerInput.setText(offer);
        String policy = intent.getStringExtra("policy");
        String policyB64 = intent.getStringExtra("policy_b64");
        if (policyB64 != null) {
            policy = new String(Base64.decode(policyB64, Base64.DEFAULT), StandardCharsets.UTF_8);
        }
        if (policy != null) policyInput.setText(policy);
        responseJson = null;
        copyResponse.setEnabled(false);
        responseView.setVisibility(View.GONE);
        policySection.setVisibility(View.GONE);
        status.setText("");
    }

    private void pair() {
        String text = offerInput.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus(R.string.guardian_pair_invalid);
            return;
        }
        try {
            GuardianPairing.Offer offer = GuardianPairing.parseOffer(text);
            GuardianIdentity.Keys keys = GuardianIdentity.loadOrCreate(this);
            responseJson = GuardianPairing.buildResponse(keys.privateKey, keys.publicKey, offer);
            String sas = GuardianPairing.deviceSas(keys.privateKey, keys.publicKey, offer);
            getSharedPreferences("hisn_guardian", MODE_PRIVATE).edit()
                    .putString("guardian_public", offer.guardianB64)
                    .putLong("paired_at", System.currentTimeMillis() / 1000L)
                    .apply();
            setStatusText(getString(R.string.guardian_pair_sas, sas) + "\n"
                    + getString(R.string.guardian_pair_device,
                    GuardianPairing.deviceId(keys.publicKey)));
            responseView.setText(R.string.guardian_pair_ready);
            responseView.setVisibility(View.VISIBLE);
            copyResponse.setEnabled(true);
            policySection.setVisibility(View.VISIBLE);
        } catch (RuntimeException failure) {
            responseJson = null;
            copyResponse.setEnabled(false);
            responseView.setVisibility(View.GONE);
            policySection.setVisibility(View.GONE);
            setStatus(R.string.guardian_pair_invalid);
        }
    }

    private void applyPolicy() {
        String text = policyInput.getText().toString().trim();
        if (text.isEmpty()) {
            policyStatus.setText(R.string.guardian_policy_invalid);
            return;
        }
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("hisn_guardian", MODE_PRIVATE);
            String publicB64 = prefs.getString("guardian_public", null);
            if (publicB64 == null) {
                policyStatus.setText(R.string.guardian_policy_pair_first);
                return;
            }
            GuardianIdentity.Keys keys = GuardianIdentity.loadOrCreate(this);
            long now = System.currentTimeMillis() / 1000L;
            GuardianPolicy.Verified verified = GuardianPolicy.verify(
                    GuardianPairing.loadPublicB64(publicB64), text,
                    GuardianPairing.deviceId(keys.publicKey), now);
            long last = prefs.getLong("policy_counter", 0L);
            if (verified.counter <= last) {
                policyStatus.setText(R.string.guardian_policy_old);
                return;
            }
            GuardianPolicyApplier.Result applied = GuardianPolicyApplier.apply(this, verified.rulesJson);
            if (applied.ok) {
                prefs.edit().putLong("policy_counter", verified.counter).apply();
                policyStatus.setText(getString(R.string.guardian_policy_applied, applied.reason));
            } else {
                policyStatus.setText(getString(R.string.guardian_policy_not_applied, applied.reason));
            }
        } catch (RuntimeException failure) {
            policyStatus.setText(R.string.guardian_policy_invalid);
        }
    }

    private void setStatus(int string) { setStatusText(getString(string)); }

    private void setStatusText(String text) { status.setText(text); }
}
