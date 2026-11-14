package app.hisn.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The Android side of the cross-language hostname contract. The fixture is
 * assembled from the signed seed (terms, never-keyword and exemptions) and the
 * shared `blocklist/terms/host_cases.json` cases by `test-core.sh`.
 */
public final class HostTermPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: HostTermPolicyTest <fixture.tsv>");
        List<String> tokens = new ArrayList<>(), substrings = new ArrayList<>();
        List<String> never = new ArrayList<>(), exempt = new ArrayList<>(), cases = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", 3);
            switch (parts[0]) {
                case "token": tokens.add(parts[1]); break;
                case "substring": substrings.add(parts[1]); break;
                case "never": never.add(parts[1]); break;
                case "exempt": exempt.add(parts[1]); break;
                case "case":
                    if (parts.length != 3) throw new IOException("bad case");
                    cases.add(parts[1] + "\t" + parts[2]);
                    break;
                default: throw new IOException("unknown fixture row");
            }
        }
        check(tokens.size() > 30 && substrings.size() > 30, "the signed seed carries both term kinds");
        check(!never.isEmpty() && !exempt.isEmpty(), "the signed seed carries its exemptions");
        HostTermPolicy policy = new HostTermPolicy(tokens, substrings, never, exempt);
        check(policy.tokenCount() == tokens.size(), "token count");
        check(policy.substringCount() == substrings.size(), "substring count");

        int blocked = 0, allowed = 0;
        for (String entry : cases) {
            String[] split = entry.split("\t", 2);
            boolean expected = "1".equals(split[0]);
            boolean actual = policy.matches(split[1]);
            check(actual == expected, "shared host case: " + split[1] + " expected " + expected);
            if (expected) blocked++; else allowed++;
        }
        check(blocked >= 15 && allowed >= 20, "the shared fixture has both halves");

        // The safety cases in prose, independent of the fixture file.
        check(policy.matches("PORNHUB.COM."), "case and trailing dot");
        check(policy.matches("essex-porn.com"), "an innocent token does not immunise the rest");
        check(!policy.matches("essex.gov.uk"), "Essex County Council");
        check(!policy.matches("sussex.ac.uk"), "University of Sussex");
        check(!policy.matches("analytics.google.com"), "anal within analytics");
        check(!policy.matches("tasks.office.com"), "sks within tasks");
        check(!policy.matches("docs.google.com"), "cum within document");
        check(!policy.matches("therapist.com"), "rapist by accident");
        check(!policy.matches("shop.koss.com"), "exempt host and its subdomains");
        check(policy.matches("jins-sex.example"), "the exemption is for jins.com, not every host with the token");
        check(policy.matches("milford-milfs.example"), "an innocent token does not immunise the rest");
        check(!policy.matches("ci.milford.ct.us"), "Milford: the innocent token is removed before milf");
        check(!policy.matches("pornic.fr"), "Pornic: the innocent token is removed before porn");
        check(!policy.matches("example.com"), "the plainest negative");
        check(!policy.matches(""), "empty host");
        check(!policy.matches(null), "null host");

        // The cache answers the same question and does not change the verdict.
        boolean before = policy.matches("PORNHUB.COM.");
        boolean after = policy.matches("PORNHUB.COM.");
        check(before && after, "a repeated question keeps its verdict");

        // The ASCII path must give the reference path's verdict. Hosts are built from the
        // signed terms, the never-keyword words and ordinary labels, with the shapes the
        // variants exist for: trailing digits, held keys, glued words and several innocent
        // words in one host.
        List<String> parts = new ArrayList<>();
        for (String term : tokens) if (term.matches("[a-z0-9]+")) parts.add(term);
        for (String term : substrings) if (term.matches("[a-z0-9]+")) parts.add(term);
        for (String term : never) if (term.matches("[a-z0-9]+")) parts.add(term);
        String[] plain = {"cdn", "static", "mail", "shop", "news", "tube", "arab", "x", "a1", "the", "my"};
        for (String word : plain) parts.add(word);
        String[] joins = {"", "-", ".", "_", "--"};
        String[] tails = {"", "1", "123", "0", "x"};
        java.util.Random random = new java.util.Random(20261114);
        int compared = 0, differentialBlocked = 0;
        for (int round = 0; round < 60000; round++) {
            StringBuilder host = new StringBuilder();
            int pieces = 1 + random.nextInt(4);
            for (int piece = 0; piece < pieces; piece++) {
                String word = parts.get(random.nextInt(parts.size()));
                if (random.nextInt(6) == 0 && word.length() > 1) {
                    // A held key: one character repeated three to five times.
                    int at = random.nextInt(word.length());
                    StringBuilder held = new StringBuilder(word.substring(0, at));
                    for (int i = 0, copies = 3 + random.nextInt(3); i < copies; i++) held.append(word.charAt(at));
                    word = held.append(word.substring(at + 1)).toString();
                }
                if (random.nextInt(8) == 0) word = word.toUpperCase(java.util.Locale.ROOT);
                host.append(word).append(tails[random.nextInt(tails.length)]);
                if (piece + 1 < pieces) host.append(joins[random.nextInt(joins.length)]);
            }
            host.append(random.nextInt(5) == 0 ? ".co.uk" : ".com");
            if (random.nextInt(20) == 0) host.append('.');
            String name = host.toString();
            boolean reference = policy.matchesReference(name);
            check(policy.matches(name) == reference, "ASCII path differs from the reference: " + name);
            compared++;
            if (reference) differentialBlocked++;
        }
        for (String entry : cases) {
            String name = entry.split("\t", 2)[1];
            check(policy.matches(name) == policy.matchesReference(name), "shared case differs: " + name);
        }
        check(differentialBlocked > 5000 && compared - differentialBlocked > 5000,
                "the generated hosts exercise both verdicts");

        // A malformed policy is refused, never silently emptied.
        try {
            new HostTermPolicy(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            throw new AssertionError("empty host policy accepted");
        } catch (IllegalArgumentException expected) { }
        List<String> bad = new ArrayList<>();
        bad.add("");
        try {
            new HostTermPolicy(bad, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            throw new AssertionError("empty term accepted");
        } catch (IllegalArgumentException expected) { }

        System.out.println("HostTermPolicy tests passed: " + blocked + " blocked, " + allowed
                + " allowed shared cases, " + policy.tokenCount() + " tokens, "
                + policy.substringCount() + " substrings, " + compared + " generated hosts agree with the reference");
    }
}
