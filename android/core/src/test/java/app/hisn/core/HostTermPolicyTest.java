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
                + policy.substringCount() + " substrings");
    }
}
