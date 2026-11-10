package app.hisn.android;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import app.hisn.core.DomainPolicy;
import app.hisn.core.DomainSeed;
import app.hisn.core.SignedManifest;

/**
 * The one place the app reads its published domain list. An installed generation is
 * preferred only when its signed manifest, artifact hash and parsed domain index all
 * verify; otherwise the APK's signed core seed answers and the caller can disclose
 * that fallback. A damaged install never becomes an empty list and never lowers the
 * rollback floor.
 */
final class HostListStore {
    static final String PINNED_KEY =
            "eb6751a0429413d0cfb24a778f7d6ecdd8436e573af94c325de5fcbd38e59ede";
    static final String DIRECTORY = "host-list";
    static final String POINTER = "active";
    static final String FLOOR = "floor";
    static final String MANIFEST = "manifest.json";
    static final String SIGNATURE = "manifest.json.sig";
    static final String DOMAINS = "domains_core.txt";
    private static final long MAX_MANIFEST = SignedManifest.MAX_MANIFEST_BYTES;
    private static final long MAX_DOMAINS = SignedManifest.MAX_ARTIFACT_BYTES;
    private static volatile Active cached;

    enum Source { INSTALLED, BUNDLED, DEGRADED }

    static final class Active {
        final DomainPolicy policy;
        final int generation;
        final Source source;
        final String error;

        Active(DomainPolicy policy, int generation, Source source, String error) {
            this.policy = policy;
            this.generation = generation;
            this.source = source;
            this.error = error;
        }

        boolean available() { return policy != null; }
    }

    static synchronized Active active(Context context) {
        Active held = cached;
        if (held != null) return held;
        int bundledVersion = BuildConfig.DOMAIN_SEED_VERSION;
        Active installed = installed(context);
        if (installed != null && installed.generation >= bundledVersion) {
            cached = installed;
            return installed;
        }
        Active bundled = bundled(context);
        if (bundled != null) {
            String fallback = installed != null ? "installed-older"
                    : pointer(context).isFile() ? "installed-damaged" : null;
            cached = new Active(bundled.policy, bundled.generation, Source.BUNDLED, fallback);
            return cached;
        }
        String reason = installed == null ? "no-list" : installed.error;
        cached = new Active(null, bundledVersion, Source.DEGRADED, reason);
        return cached;
    }

    static synchronized void invalidate() { cached = null; }

    /** Publish an already validated generation after the updater's atomic pointer move. */
    static synchronized void adopt(int generation, DomainPolicy policy) {
        cached = new Active(policy, generation, Source.INSTALLED, null);
    }

    /** The generation a receipt should name without paying to re-index the list. */
    static int generation(Context context) {
        Active held = cached;
        if (held != null) return held.generation;
        return Math.max(floor(context), BuildConfig.DOMAIN_SEED_VERSION);
    }

    static File directory(Context context, int generation) {
        return new File(new File(context.getFilesDir(), DIRECTORY), "gen-" + generation);
    }

    static File pointer(Context context) {
        return new File(new File(context.getFilesDir(), DIRECTORY), POINTER);
    }

    static File floorFile(Context context) {
        return new File(new File(context.getFilesDir(), DIRECTORY), FLOOR);
    }

    static int floor(Context context) {
        int stored = readInt(floorFile(context));
        return Math.max(stored, BuildConfig.DOMAIN_SEED_VERSION);
    }

    static void writeFloor(Context context, int generation) {
        File parent = floorFile(context).getParentFile();
        if (parent != null) parent.mkdirs();
        AtomicFile file = new AtomicFile(floorFile(context));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(Integer.toString(generation).getBytes(StandardCharsets.US_ASCII));
            out.write('\n');
            file.finishWrite(out);
        } catch (IOException failed) {
            if (out != null) file.failWrite(out);
            android.util.Log.w("HisnList", "Rollback floor not saved", failed);
        }
    }

    static void writePointer(Context context, int generation, String manifestSha) throws IOException {
        File dir = new File(context.getFilesDir(), DIRECTORY);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create list directory");
        AtomicFile file = new AtomicFile(pointer(context));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write((generation + "\n" + manifestSha + "\n").getBytes(StandardCharsets.US_ASCII));
            file.finishWrite(out);
        } catch (IOException failed) {
            if (out != null) file.failWrite(out);
            throw failed;
        }
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(64);
            for (byte value : hash) {
                out.append(Character.forDigit((value >>> 4) & 15, 16));
                out.append(Character.forDigit(value & 15, 16));
            }
            return out.toString();
        } catch (RuntimeException | java.security.NoSuchAlgorithmException unavailable) {
            return "";
        }
    }

    static byte[] read(File file, long limit) throws IOException {
        if (!file.isFile() || file.length() < 1 || file.length() > limit) throw new IOException("Invalid file");
        try (InputStream in = new FileInputStream(file)) { return read(in, limit); }
    }

    static byte[] read(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > limit) throw new IOException("File too large");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static Active installed(Context context) {
        int pointerGeneration = pointerGeneration(context);
        Active active = loadGeneration(context, pointerGeneration);
        if (active != null) return active;
        // Keep the last working generation usable when the pointer's files are damaged.
        File root = new File(context.getFilesDir(), DIRECTORY);
        File[] children = root.listFiles();
        if (children == null) return null;
        java.util.Arrays.sort(children, (a, b) ->
                Integer.compare(generationOf(b.getName()), generationOf(a.getName())));
        for (File child : children) {
            int generation = generationOf(child.getName());
            if (generation < 0 || generation == pointerGeneration) continue;
            active = loadGeneration(context, generation);
            if (active != null) {
                return new Active(active.policy, active.generation, Source.INSTALLED,
                        "previous-generation");
            }
        }
        return null;
    }

    private static int generationOf(String name) {
        if (!name.startsWith("gen-")) return -1;
        try { return Integer.parseInt(name.substring(4)); }
        catch (NumberFormatException invalid) { return -1; }
    }

    private static int pointerGeneration(Context context) {
        try {
            String[] pointer = readText(pointer(context)).trim().split("\\R");
            return pointer.length < 2 ? -1 : Integer.parseInt(pointer[0]);
        } catch (IOException | RuntimeException invalid) {
            return -1;
        }
    }

    private static Active loadGeneration(Context context, int generation) {
        if (generation < 1) return null;
        try {
            File dir = directory(context, generation);
            byte[] manifestBytes = read(new File(dir, MANIFEST), MAX_MANIFEST);
            String signature = new String(read(new File(dir, SIGNATURE), 512), StandardCharsets.US_ASCII);
            SignedManifest manifest = SignedManifest.verify(manifestBytes, signature,
                    hex(PINNED_KEY));
            if (manifest.version != generation) return null;
            byte[] domains = read(new File(dir, DOMAINS), MAX_DOMAINS);
            if (domains.length != manifest.domains().bytes
                    || !sha256(domains).equals(manifest.domains().sha256)) return null;
            DomainSeed seed = DomainSeed.load(new ByteArrayInputStream(domains),
                    manifest.domains().sha256, manifest.coreDomainCount);
            return new Active(seed.policy, generation, Source.INSTALLED, null);
        } catch (RuntimeException | IOException invalid) {
            return null;
        }
    }

    private static Active bundled(Context context) {
        try (InputStream input = context.getAssets().open("domains_core.txt")) {
            DomainSeed seed = DomainSeed.load(input, BuildConfig.DOMAIN_SEED_SHA256,
                    BuildConfig.DOMAIN_SEED_COUNT);
            return new Active(seed.policy, BuildConfig.DOMAIN_SEED_VERSION, Source.BUNDLED, null);
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    private static String readText(File file) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        try (InputStream in = atomic.openRead()) {
            return new String(read(in, 512), StandardCharsets.US_ASCII);
        }
    }

    private static int readInt(File file) {
        try { return Integer.parseInt(readText(file).trim()); }
        catch (IOException | RuntimeException unavailable) { return 0; }
    }

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private HostListStore() { }
}
