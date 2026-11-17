package app.hisn.core;

import java.util.Arrays;

public final class AdultAppNamesTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Adult app names: " + label); }
    private static AdultTextPolicy.Rule rule(String phrase, int weight) { return new AdultTextPolicy.Rule(phrase, weight); }

    public static void main(String[] args) {
        check(AdultAppNames.hosts("com.xvideos.app").equals(Arrays.asList("xvideos.com")), "reversed package names its site");
        check(AdultAppNames.hosts("uk.co.example.player").equals(Arrays.asList("co.uk", "example.co.uk")),
                "a country's second level is read too");
        check(AdultAppNames.hosts("a").isEmpty() && AdultAppNames.hosts(null).isEmpty() && AdultAppNames.hosts("com.x$y.app").isEmpty(),
                "malformed package ids name no site");
        check(AdultAppNames.hosts("com.my_app.player").equals(Arrays.asList("my-app.com")), "underscores become hyphens");

        check(AdultAppNames.adultSite(HostDecision.Outcome.BLOCKED, HostDecision.Source.PUBLISHED_LIST)
                && AdultAppNames.adultSite(HostDecision.Outcome.BLOCKED, HostDecision.Source.ADULT_TLD)
                && AdultAppNames.adultSite(HostDecision.Outcome.BLOCKED, HostDecision.Source.HOST_TERM), "shared adult signals count");
        check(!AdultAppNames.adultSite(HostDecision.Outcome.BLOCKED, HostDecision.Source.PERSONAL_BLOCK),
                "the owner's own blocked site never blocks its app");
        check(!AdultAppNames.adultSite(HostDecision.Outcome.NOT_LISTED, HostDecision.Source.NONE)
                && !AdultAppNames.adultSite(HostDecision.Outcome.UNAVAILABLE, HostDecision.Source.NONE)
                && !AdultAppNames.adultSite((HostDecision.Result) null), "an unlisted or unknown site is not adult");

        AdultTextPolicy policy = new AdultTextPolicy(Arrays.asList(rule("porn", 8), rule("سكس", 8), rule("hentai", 8),
                rule("porn addiction", -8), rule("sex education", -6), rule("blocker", -8)));
        for (String name : new String[] {"Porn Videos", "Hentai", "سكس", "Porn"}) {
            check(AdultAppNames.adultName(policy, name), "adult app name: " + name);
        }
        for (String name : new String[] {"Video Player", "XPlayer", "Porn Blocker", "Porn addiction recovery", "", "  "}) {
            check(!AdultAppNames.adultName(policy, name), "ordinary or protective name: " + name);
        }
        check(!AdultAppNames.adultName(null, "Porn"), "names are not judged before the vocabulary loads");
        System.out.println("AdultAppNames tests passed (reversed sites, shared signals only, adult and protective names)");
    }
}
