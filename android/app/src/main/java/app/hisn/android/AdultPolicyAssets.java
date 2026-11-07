package app.hisn.android;

import android.content.Context;

import app.hisn.core.AdultTextPolicy;
import app.hisn.core.HostTermPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Immutable on-device policy from the build-verified and APK-pinned terms seed. */
public final class AdultPolicyAssets {
    public enum State { IDLE, LOADING, READY, ERROR }
    public static volatile State state = State.IDLE;
    private static volatile AdultTextPolicy policy;

    public static AdultTextPolicy policy() { return policy; }

    public static synchronized void ensureLoaded(Context context) {
        if (state != State.IDLE) return;
        state = State.LOADING;
        Context application = context.getApplicationContext() == null ? context : context.getApplicationContext();
        new Thread(() -> {
            try (InputStream input = application.getAssets().open("terms.json")) {
                policy = load(input, BuildConfig.TERMS_SEED_SHA256);
                state = State.READY;
            } catch (Exception unavailable) {
                policy = null;
                state = State.ERROR;
            }
        }, "hisn-adult-terms").start();
    }

    /** Explicit retry only; invalid assets are never replaced by unverified data. */
    public static synchronized void retryLoading(Context context) {
        if (state == State.ERROR) state = State.IDLE;
        ensureLoaded(context);
    }

    /**
     * Read and verify the signed terms asset, then compile only the hostname
     * layer. The DNS service calls this synchronously before it starts
     * enforcing: a null return means the domain list is the only layer, never
     * an unverified keyword list. It reads the same pinned bytes as {@link #load}.
     */
    public static HostTermPolicy loadHostPolicy(Context context) throws Exception {
        Context application = context.getApplicationContext() == null ? context : context.getApplicationContext();
        try (InputStream input = application.getAssets().open("terms.json")) {
            return loadHostPolicy(input, BuildConfig.TERMS_SEED_SHA256);
        }
    }

    static AdultTextPolicy load(InputStream input, String expectedDigest) throws Exception {
        JSONObject data = readVerified(input, expectedDigest);
        JSONArray terms = data.getJSONArray("terms");
        JSONArray negatives = data.getJSONArray("negatives");
        if (terms.length() < 1 || terms.length() > 8192 || negatives.length() > 1000)
            throw new IOException("Invalid terms count");
        List<AdultTextPolicy.Rule> rules = new ArrayList<>();
        for (int i = 0; i < terms.length(); i++) {
            JSONObject term = terms.getJSONObject(i);
            String language = term.getString("l");
            // Franco-Arabic (Arabizi) is tagged so the policy applies its reviewed context rules.
            boolean arabizi = "ar-latn".equals(language);
            if ("en".equals(language) || "ar".equals(language) || arabizi)
                rules.add(new AdultTextPolicy.Rule(term.getString("t"), weight(term, false), arabizi));
        }
        for (int i = 0; i < negatives.length(); i++) {
            JSONObject term = negatives.getJSONObject(i);
            rules.add(new AdultTextPolicy.Rule(term.getString("t"), weight(term, true)));
        }
        // Reviewed local context additions, not extra signed-seed entries.
        rules.addAll(AdultTextPolicy.reviewedContextRules());
        return new AdultTextPolicy(rules);
    }

    static HostTermPolicy loadHostPolicy(InputStream input, String expectedDigest) throws Exception {
        JSONObject data = readVerified(input, expectedDigest);
        JSONArray hostTerms = data.getJSONArray("host_terms");
        if (hostTerms.length() < 1 || hostTerms.length() > HostTermPolicy.MAX_TERMS)
            throw new IOException("Invalid host term count");
        List<String> tokens = new ArrayList<>();
        List<String> substrings = new ArrayList<>();
        for (int i = 0; i < hostTerms.length(); i++) {
            JSONObject entry = hostTerms.getJSONObject(i);
            String term = entry.getString("t");
            String kind = entry.getString("kind");
            if (term.isEmpty() || term.length() > 253 || term.indexOf('\n') >= 0 || term.indexOf('\t') >= 0)
                throw new IOException("Invalid host term");
            if ("token".equals(kind)) tokens.add(term);
            else if ("substring".equals(kind)) substrings.add(term);
            else throw new IOException("Unknown host term kind");
        }
        List<String> never = strings(data.getJSONArray("never_keyword"), 5000);
        JSONArray exempt = data.optJSONArray("keyword_exempt_hosts");
        List<String> exemptHosts = exempt == null ? new ArrayList<>() : strings(exempt, 200);
        try {
            return new HostTermPolicy(tokens, substrings, never, exemptHosts);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid host term policy", invalid);
        }
    }

    private static List<String> strings(JSONArray array, int limit) throws Exception {
        if (array == null || array.length() > limit) throw new IOException("Invalid term list");
        List<String> out = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String value = array.getString(i);
            if (value.isEmpty() || value.length() > 253 || value.indexOf('\n') >= 0 || value.indexOf('\t') >= 0)
                throw new IOException("Invalid term");
            out.add(value);
        }
        return out;
    }

    /** The one digest/encoding/schema check every reader of this asset shares. */
    private static JSONObject readVerified(InputStream input, String expectedDigest) throws Exception {
        if (input == null || expectedDigest == null || !expectedDigest.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid terms metadata");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > 2 * 1024 * 1024) throw new IOException("Terms seed too large");
            bytes.write(buffer, 0, count);
        }
        byte[] raw = bytes.toByteArray();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest) hex.append(Character.forDigit((value >>> 4) & 15, 16))
                .append(Character.forDigit(value & 15, 16));
        if (!expectedDigest.contentEquals(hex)) throw new IOException("Terms digest mismatch");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        JSONObject data = new JSONObject(text);
        if (data.getInt("schema") != 1 || data.getInt("version") != BuildConfig.DOMAIN_SEED_VERSION)
            throw new IOException("Unsupported terms version");
        return data;
    }

    private static int weight(JSONObject term, boolean negative) throws Exception {
        double value = term.getDouble("w");
        if (!Double.isFinite(value) || value != Math.rint(value) || value == 0 || Math.abs(value) > 100
                || (negative ? value > 0 : value < 0)) throw new IOException("Invalid terms weight");
        return (int) value;
    }
    private AdultPolicyAssets() { }
}
