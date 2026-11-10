package app.hisn.android;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.hisn.core.AdultTextPolicy;
import app.hisn.core.DomainSeed;
import app.hisn.core.HostTermPolicy;
import app.hisn.core.SignedManifest;

/**
 * Downloads and activates one signed published list generation. The large artifact
 * is fetched only after the small manifest verifies and is newer than the held
 * version. All validation and index construction happen off the main thread; only
 * the final pointer swap makes the generation visible.
 */
final class AdultListUpdater {
    static final String BASE = "https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists";
    static final long MIN_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L;
    static final int JOB_ID = 7319;
    private static final long MAX_MANIFEST = SignedManifest.MAX_MANIFEST_BYTES;
    private static final long MAX_SIGNATURE = 512;
    private static final long MAX_DOMAINS = SignedManifest.MAX_ARTIFACT_BYTES;
    private static final String PREFS = "hisn_list_update";
    private static final String LAST_CHECK = "last_check";
    private static final String LAST_ERROR = "last_error";
    private static final String GENERATION = "generation";
    private static final String VERIFIED_AT = "verified_at";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hisn-list-update");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler main = new Handler(Looper.getMainLooper());

    interface Listener { void done(Result result); }

    static final class Result {
        final boolean updated;
        final int generation;
        final String error;

        Result(boolean updated, int generation, String error) {
            this.updated = updated;
            this.generation = generation;
            this.error = error;
        }
    }

    static void check(Context context, boolean manual, Listener listener) {
        Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        worker.execute(() -> {
            Result result;
            try { result = run(app, manual); }
            catch (RuntimeException | IOException failure) {
                result = fail(app, friendly(failure));
            }
            Result delivered = result;
            main.post(() -> listener.done(delivered));
        });
    }

    /** Keep one bounded background check scheduled; the job performs the same gated check. */
    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        try {
            if (scheduler.getPendingJob(JOB_ID) != null) return;
            scheduler.schedule(new JobInfo.Builder(JOB_ID,
                    new ComponentName(context, HostListUpdateJobService.class))
                    .setPeriodic(24 * 60 * 60 * 1000L)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresCharging(false)
                    .build());
        } catch (RuntimeException unavailable) {
            android.util.Log.w("HisnList", "List update job was not accepted", unavailable);
        }
    }

    static long lastChecked(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_CHECK, 0);
    }

    static int installedGeneration(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(GENERATION, HostListStore.floor(context));
    }

    static long verifiedAt(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(VERIFIED_AT, 0);
    }

    static String lastError(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_ERROR, "");
    }

    private static Result run(Context context, boolean manual) throws IOException {
        long now = System.currentTimeMillis();
        if (!manual && now - lastChecked(context) < MIN_INTERVAL_MILLIS) {
            return new Result(false, installedGeneration(context), "");
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(LAST_CHECK, now).apply();

        byte[] manifestBytes = fetch(BASE + "/manifest.json", MAX_MANIFEST);
        String signature = new String(fetch(BASE + "/manifest.json.sig", MAX_SIGNATURE),
                StandardCharsets.US_ASCII);
        SignedManifest manifest = SignedManifest.verify(manifestBytes, signature,
                hex(HostListStore.PINNED_KEY));
        int held = HostListStore.floor(context);
        if (manifest.version < held) return fail(context, "rollback");
        if (manifest.version == held) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(GENERATION, manifest.version).putString(LAST_ERROR, "").apply();
            return new Result(false, manifest.version, "");
        }

        byte[] domains = fetch(BASE + "/" + SignedManifest.DOMAINS, manifest.domains().bytes);
        if (domains.length != manifest.domains().bytes
                || !HostListStore.sha256(domains).equals(manifest.domains().sha256)) {
            return fail(context, "artifact");
        }
        // Parse and index before any file becomes visible to the DNS service.
        DomainSeed seed = DomainSeed.load(new ByteArrayInputStream(domains),
                manifest.domains().sha256, manifest.coreDomainCount);
        if (seed.domainCount < SignedManifest.MIN_DOMAINS) return fail(context, "coverage");

        // Terms travel with the domain list as one signed generation. Both
        // policies are compiled before anything becomes visible, and either a
        // damaged terms file or a version mismatch rejects the whole update so
        // the previous coherent generation keeps enforcing.
        byte[] terms = null;
        HostTermPolicy hostTerms = null;
        AdultTextPolicy textPolicy = null;
        if (manifest.terms() != null) {
            terms = fetch(BASE + "/" + SignedManifest.TERMS, manifest.terms().bytes);
            if (terms.length != manifest.terms().bytes
                    || !HostListStore.sha256(terms).equals(manifest.terms().sha256)) {
                return fail(context, "artifact");
            }
            try {
                hostTerms = AdultPolicyAssets.loadHostPolicy(new ByteArrayInputStream(terms),
                        manifest.terms().sha256, manifest.version);
                textPolicy = AdultPolicyAssets.load(new ByteArrayInputStream(terms),
                        manifest.terms().sha256, manifest.version);
            } catch (Exception invalidTerms) {
                return fail(context, "terms");
            }
        }

        File parent = new File(context.getFilesDir(), HostListStore.DIRECTORY);
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create list directory");
        File staging = new File(parent, "staging-" + manifest.version);
        delete(staging);
        if (!staging.mkdirs()) throw new IOException("Cannot stage list");
        try {
            write(new File(staging, HostListStore.MANIFEST), manifestBytes);
            write(new File(staging, HostListStore.SIGNATURE), signature.trim().getBytes(StandardCharsets.US_ASCII));
            write(new File(staging, HostListStore.DOMAINS), domains);
            if (terms != null) write(new File(staging, SignedManifest.TERMS), terms);
            File target = HostListStore.directory(context, manifest.version);
            delete(target);
            if (!staging.renameTo(target)) throw new IOException("Cannot activate list");
            // The pointer is the commit point. Until it moves, the old generation is
            // still the one every reader resolves.
            HostListStore.writePointer(context, manifest.version, HostListStore.sha256(manifestBytes));
            HostListStore.writeFloor(context, manifest.version);
            // Publish the index built off-thread; the next DNS query must not re-parse 4 MB.
            HostListStore.adopt(manifest.version, seed.policy, hostTerms, textPolicy);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(GENERATION, manifest.version).putString(LAST_ERROR, "")
                    .putLong(VERIFIED_AT, System.currentTimeMillis()).apply();
            cleanup(parent, manifest.version);
            return new Result(true, manifest.version, "");
        } finally {
            delete(staging);
        }
    }

    private static Result fail(Context context, String error) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_ERROR, error).apply();
        return new Result(false, installedGeneration(context), error);
    }

    private static String friendly(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isEmpty()) return failure.getClass().getSimpleName();
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    private static byte[] fetch(String address, long limit) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-store");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            long declared = connection.getContentLengthLong();
            if (declared > limit) throw new IOException("Too large");
            try (InputStream input = connection.getInputStream()) {
                return HostListStore.read(input, limit);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.getFD().sync();
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

    /** Keep the active generation and one predecessor; a damaged pointer never strands the app. */
    private static void cleanup(File parent, int active) {
        File[] children = parent.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (!name.startsWith("gen-")) continue;
            int version;
            try { version = Integer.parseInt(name.substring(4)); }
            catch (NumberFormatException ignored) { continue; }
            if (version != active && version != active - 1) delete(child);
        }
    }

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private AdultListUpdater() { }
}
