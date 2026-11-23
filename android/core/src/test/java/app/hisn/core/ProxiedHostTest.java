package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;

/** Proxy hostnames that spell a site in one label are read back to that site. */
public final class ProxiedHostTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        check("www.example.com".equals(ProxiedHost.original("www-example-com.translate.goog")), "translate proxy");
        check("en.m.wikipedia.org".equals(ProxiedHost.original("en-m-wikipedia-org.translate.goog")), "deep name");
        check("my-site.example.org".equals(ProxiedHost.original("my--site-example-org.translate.goog")),
                "doubled hyphen is a hyphen");
        check("a-b-c.example".equals(ProxiedHost.original("a--b--c-example.cdn.ampproject.org")), "AMP cache");
        // "xn--abc.com" becomes "xn----abc-com", whose third and fourth characters are "--", so it is wrapped.
        check("xn--abc.com".equals(ProxiedHost.original("0-xn----abc-com-0.translate.goog")), "wrapped punycode name");
        check("0-example.com".equals(ProxiedHost.original("0--example-com.translate.goog")),
                "an unwrapped name that starts with 0- is kept");
        // A Unicode original travels as one punycode label.
        String unicode = java.net.IDN.toASCII("bücher-de");
        check(Hostname.normalize("bücher.de").equals(ProxiedHost.original(unicode + ".translate.goog")),
                "Unicode original read back to its punycode host");

        for (String other : new String[] {null, "translate.goog", "www.translate.goog.example",
                "a.b.translate.goog", "example.translate.goog", "-.translate.goog",
                "cdn.ampproject.org", "www.example.com", "example-com.translate.goog.evil.example"}) {
            check(ProxiedHost.original(other) == null, "not a readable proxy name: " + other);
        }

        // The composed decision applies every layer to the proxied copy.
        DomainPolicy published = new DomainPolicy(Arrays.asList("listed.example"), Collections.emptyList());
        HostTermPolicy terms = new HostTermPolicy(Collections.singleton("adultword"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        HostDecision.Policy policy = new HostDecision.Policy(new DomainPolicy(Arrays.asList("personal.example"),
                Collections.emptyList()), published, true, 1, terms);
        check(policy.evaluate("www-listed-example.translate.goog").source == HostDecision.Source.PUBLISHED_LIST,
                "listed site through Translate");
        check(policy.evaluate("listed-example.cdn.ampproject.org").blocked(), "listed site through the AMP cache");
        check(policy.evaluate("personal-example.translate.goog").source == HostDecision.Source.PERSONAL_BLOCK,
                "personal block through Translate");
        check(policy.evaluate("new-site-xxx.translate.goog").source == HostDecision.Source.ADULT_TLD, "adult TLD");
        check(policy.evaluate("adultword-example.translate.goog").blocked(), "hostname term through Translate");
        check(policy.enforce("www-listed-example.translate.goog") == DomainPolicy.Decision.BLOCK, "DNS path");
        check(policy.enforce("en-wikipedia-org.translate.goog") == DomainPolicy.Decision.ALLOW,
                "ordinary translated page stays open");
        check(policy.enforce("translate.goog") == DomainPolicy.Decision.ALLOW
                && policy.enforce("cdn.ampproject.org") == DomainPolicy.Decision.ALLOW, "the proxies themselves stay open");
        System.out.println("ProxiedHost tests passed (Translate and AMP cache names, wrapping, Unicode, composed layers)");
    }
}
