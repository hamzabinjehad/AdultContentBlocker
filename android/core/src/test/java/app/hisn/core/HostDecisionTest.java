package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;

public final class HostDecisionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static HostDecision.Policy policy(DomainPolicy published, boolean available, long version) {
        DomainPolicy personal = new DomainPolicy(Arrays.asList("personal.example", "bad.xxx"),
                Collections.singleton("allowed.example"));
        HostTermPolicy terms = new HostTermPolicy(Collections.singleton("adultword"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return new HostDecision.Policy(personal, published, available, version, terms);
    }

    public static void main(String[] args) {
        DomainPolicy published = new DomainPolicy(Arrays.asList("listed.example", "example.com"),
                Collections.emptyList());
        HostDecision.Policy policy = policy(published, true, 7);

        HostDecision.Result result = policy.evaluate("personal.example");
        check(result.blocked() && result.source == HostDecision.Source.PERSONAL_BLOCK,
                "personal block");
        check(result.generation == 7, "generation reported");
        result = policy.evaluate("bad.xxx");
        check(result.source == HostDecision.Source.PERSONAL_BLOCK, "personal block outranks TLD");
        result = policy.evaluate("new.xxx");
        check(result.source == HostDecision.Source.ADULT_TLD, "adult TLD");
        result = policy.evaluate("sub.adultword.example");
        check(result.source == HostDecision.Source.HOST_TERM, "hostname term");
        result = policy.evaluate("cdn.listed.example");
        check(result.source == HostDecision.Source.PUBLISHED_LIST, "published subdomain");
        result = policy.evaluate("www.example.com.");
        check(result.source == HostDecision.Source.PUBLISHED_LIST, "trailing dot and parent rule");
        check(result.host.equals("www.example.com"), "trailing dot normalized");
        result = policy.evaluate("allowed.example");
        check(result.outcome == HostDecision.Outcome.NOT_LISTED,
                "a domain allowlist entry is not a blocking exception");
        result = policy.evaluate("safe.example");
        check(result.outcome == HostDecision.Outcome.NOT_LISTED, "no match");

        // Unicode and punycode name the same host; the TLD rail still applies.
        result = policy.evaluate("مثال.xxx");
        check(result.source == HostDecision.Source.ADULT_TLD, "Unicode TLD host");
        check(result.host.startsWith("xn--"), "Unicode normalized to punycode");
        result = policy.evaluate("xn--mgbh0fb.example");
        check(result.answered() || result.outcome == HostDecision.Outcome.NOT_LISTED,
                "punycode input accepted");

        // Invalid and unavailable are not "allowed".
        for (String invalid : new String[]{null, "", "localhost", "1.2.3.4", "bad..example",
                ".example", "example.", " leading.example", "a".repeat(64) + ".example"}) {
            result = policy.evaluate(invalid);
            check(result.outcome == HostDecision.Outcome.INVALID, "invalid: " + invalid);
            check(result.enforcement() == DomainPolicy.Decision.INVALID, "invalid enforcement: " + invalid);
        }
        HostDecision.Policy unavailable = policy(null, false, 8);
        result = unavailable.evaluate("safe.example");
        check(result.outcome == HostDecision.Outcome.UNAVAILABLE, "unavailable list is not allowed");
        check(result.enforcement() == DomainPolicy.Decision.INVALID, "unavailable enforcement fails closed");
        result = unavailable.evaluate("new.xxx");
        check(result.source == HostDecision.Source.ADULT_TLD, "TLD works without the list");
        result = unavailable.evaluate("adultword.example");
        check(result.source == HostDecision.Source.HOST_TERM, "terms work without the list");

        // The same evaluator feeds enforcement.
        check(policy.evaluate("listed.example").enforcement() == DomainPolicy.Decision.BLOCK,
                "enforcement block");
        check(policy.evaluate("safe.example").enforcement() == DomainPolicy.Decision.ALLOW,
                "enforcement allow");
        try { new HostDecision.Policy(null, published, true, 0, null); throw new AssertionError("missing rules accepted"); }
        catch (IllegalArgumentException expected) { }
        try { new HostDecision.Policy(new DomainPolicy(Collections.emptyList(), Collections.emptyList()),
                published, true, -1, null); throw new AssertionError("negative generation accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("HostDecision tests passed (precedence, Unicode, trailing dot, invalid/unavailable fail closed)");
    }
}
