package app.hisn.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Measures the Android core matcher on the shipped seed: DNS decisions and screen-text
 * scans, as thread CPU time and allocated bytes per call. Run by
 * script/bench_android_core.sh; not part of test-core.sh, because timings are local
 * evidence and a shared runner is not a benchmark. Allocation figures are exact; CPU
 * time moves with machine load. Lives in the core package to reach package-private API.
 */
public final class CoreBench {
    static volatile int sink;
    static byte[] query(String host) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0x12); o.write(0x34); o.write(1); o.write(0); o.write(0); o.write(1);
        for (int i = 0; i < 6; i++) o.write(0);
        for (String l : host.split("\\.")) { o.write(l.length()); o.write(l.getBytes(StandardCharsets.US_ASCII), 0, l.length()); }
        o.write(0); o.write(0); o.write(1); o.write(0); o.write(1);
        return o.toByteArray();
    }
    interface Body { void run(int i); }
    static double time(String name, int n, int rounds, Body b) {
        return time(name, n, rounds, () -> b);
    }
    static double time(String name, int n, int rounds, Supplier<Body> setup) {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        double[] all = new double[rounds];
        long[] allocated = new long[rounds];
        for (int r = 0; r < rounds; r++) {
            Body body = setup.get();
            long a0 = mx.getThreadAllocatedBytes(id), t = mx.getCurrentThreadCpuTime();
            for (int i = 0; i < n; i++) body.run(i);
            all[r] = (mx.getCurrentThreadCpuTime() - t) / (double) n;
            allocated[r] = (mx.getThreadAllocatedBytes(id) - a0) / n;
        }
        Arrays.sort(all);
        Arrays.sort(allocated);
        System.out.printf("%-40s %9.0f ns cpu/op  %9d B alloc/op%n",
                name, all[rounds / 2], allocated[rounds / 2]);
        return all[rounds / 2];
    }

    private static void dns(String name, byte[][] queries, Supplier<DnsPolicy> factory) {
        time(name, queries.length, 5, () -> {
            DnsPolicy policy = factory.get();
            return i -> sink += policy.evaluate(queries[i]).action.ordinal();
        });
    }

    private static boolean scanScreen(AdultTextPolicy policy, String[] labels) {
        VisibleTextSample sample = new VisibleTextSample(true);
        AdultTextPolicy.ScreenScan scan = policy.screen();
        for (int at = 0; at < labels.length; at++) {
            sample.append(labels[at]);
            if ((at + 1) % LabelScan.CHECK_INTERVAL == 0 && scan.blocksDecisiveLine(sample)) break;
        }
        return scan.blocksAnyLine(sample);
    }
    public static void main(String[] a) throws Exception {
        String seedPath = a[0], digest = a[1]; int count = Integer.parseInt(a[2]);
        byte[] seedBytes = Files.readAllBytes(Paths.get(seedPath));
        double[] loads = new double[7]; DomainSeed seed = null;
        for (int i = 0; i < 7; i++) { long t = System.nanoTime(); seed = DomainSeed.load(new ByteArrayInputStream(seedBytes), digest, count); loads[i] = (System.nanoTime() - t) / 1e6; }
        Arrays.sort(loads);
        System.out.printf("%-46s %10.1f ms%n", "seed load (" + seed.domainCount + " domains)", loads[3]);
        List<String> tokens = new ArrayList<>(), subs = new ArrayList<>(), never = new ArrayList<>(), exempt = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(a[3]), StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue; String[] p = line.split("\t", 3);
            switch (p[0]) { case "token": tokens.add(p[1]); break; case "substring": subs.add(p[1]); break; case "never": never.add(p[1]); break; default: exempt.add(p[1]); }
        }
        final DomainSeed fseed = seed;
        List<AdultTextPolicy.Rule> rules = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(a[4]), StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue; String[] p = line.split("\t", 3);
            rules.add(new AdultTextPolicy.Rule(p[2], "neg".equals(p[1]) ? -Math.abs(Integer.parseInt(p[0])) : Integer.parseInt(p[0]), "ar-latn".equals(p[1])));
        }
        long t0 = System.nanoTime();
        AdultTextPolicy text = new AdultTextPolicy(rules);
        System.out.printf("%-46s %10.1f ms%n", "text policy compile", (System.nanoTime() - t0) / 1e6);

        // Hosts: fresh unique allowed names (the uncached path), as CDNs and trackers produce.
        Random rnd = new Random(7);
        String[] words = {"cdn","static","img","api","edge","video","mail","news","shop","cloud","assets","data","media","app","web","login","store","update","sync","analytics","tasks","photos","maps","play","docs"};
        final int N = 8000;
        final String[] fresh = new String[N]; final byte[][] freshQ = new byte[N][];
        for (int i = 0; i < N; i++) {
            fresh[i] = words[rnd.nextInt(words.length)] + "-" + Integer.toString(rnd.nextInt(1 << 30), 36) + "." + words[rnd.nextInt(words.length)] + (i % 97) + "." + words[rnd.nextInt(words.length)] + "site.com";
            freshQ[i] = query(fresh[i]);
        }
        final List<String> listed = new ArrayList<>();
        int k = 0; for (String s : seed.policy.blockedRules()) { if (k++ % 12 == 0) listed.add(s); if (listed.size() >= N) break; }
        final byte[][] listedQ = new byte[listed.size()][]; final byte[][] subQ = new byte[listed.size()][];
        for (int i = 0; i < listedQ.length; i++) { listedQ[i] = query(listed.get(i)); String s = "www.a" + i + "." + listed.get(i); subQ[i] = s.length() > 250 ? listedQ[i] : query(s); }
        final String[] idn = new String[2000]; final byte[][] idnQ = new byte[2000][];
        for (int i = 0; i < idn.length; i++) { idn[i] = "xn--mgbh0fb" + ".site" + i + ".com"; idnQ[i] = query(idn[i]); }

        Supplier<DnsPolicy> buildDns = () -> {
            DomainPolicy personal = new DomainPolicy(Collections.emptyList(), Collections.emptySet());
            HostTermPolicy terms = new HostTermPolicy(tokens, subs, never, exempt);
            HostDecision.Policy policy = new HostDecision.Policy(personal,
                    DomainPolicy.fromCanonical(fseed.policy.blockedRules()), true, 1, terms);
            return new DnsPolicy(policy::enforce);
        };
        List<String> literalRules = new ArrayList<>();
        for (int i = 0; i < 1000; i++) literalRules.add("fixture rule" + i);
        WordMatcher literal = new WordMatcher(literalRules);
        for (int warm = 0; warm < 2; warm++) {
            System.out.println(warm == 0 ? "--- warm-up" : "--- measured");
            dns("dns: fresh allowed host (uncached)", freshQ, buildDns);
            dns("dns: listed host (uncached)", listedQ, buildDns);
            dns("dns: subdomain of listed (uncached)", subQ, buildDns);
            dns("dns: punycode host (uncached)", idnQ, buildDns);
            DnsPolicy cachedP = buildDns.get();
            for (int i = 0; i < 256; i++) cachedP.evaluate(freshQ[i]);
            time("dns: repeated host (cached)", N, 5,
                    i -> sink += cachedP.evaluate(freshQ[i & 255]).action.ordinal());
            final String[] labels = {"Settings", "Search or type URL", "Watch later", "Download free music and videos now",
                "Home", "Subscriptions", "تحميل التطبيق مجانا", "شاهد الحلقة الجديدة الآن", "Free shipping on orders over $50 — shop the new collection today",
                "Sign in to continue", "Watch free porn videos", "Trending", "12 new messages", "Stream the match live tonight for free"};
            time("text: one UI label blocksPromotion", N, 5, i -> sink += text.blocksPromotion(labels[i % labels.length]) ? 1 : 0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 120; i++) sb.append(labels[i % 10]).append(' ').append(i).append('\n');
            final String screen = sb.toString();
            time("text: 120-line screen blocksAnyLine", 300, 5, i -> sink += text.blocksAnyLine(screen) ? 1 : 0);
            final String[] screenLabels = screen.split("\n");
            time("text: 120-line guard checkpoint", 300, 5, i -> sink += scanScreen(text, screenLabels) ? 1 : 0);
            final String earlyHit = "Fixture rule999\n" + screen;
            time("words: 1000 rules, first label match", 300, 5, i -> sink += literal.hasMatch(earlyHit) ? 1 : 0);
            time("words: 1000 rules, benign screen", 300, 5, i -> sink += literal.hasMatch(screen) ? 1 : 0);
        }
    }
}
