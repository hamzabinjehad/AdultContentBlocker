package app.hisn.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Named, one-tap site presets for the block list.
 *
 * <p>The list is deliberately a set of named sites, not categories. A category would hide what is
 * actually blocked and would let one tap remove a rule the person cannot see. These presets only
 * edit the ordinary saved domain rules; they add no enforcement layer and no stored value.
 */
public final class DistractionSites {
    public enum State {
        /** No saved rule blocks this site. */
        NONE,
        /** The exact domain is a saved rule. */
        DIRECT,
        /** A saved parent domain blocks this site; the chip must not remove that wider rule. */
        COVERED
    }

    public static final class Site {
        public final String label;
        public final String domain;

        Site(String label, String domain) {
            this.label = label;
            this.domain = domain;
        }
    }

    private static final List<Site> COMMON = Collections.unmodifiableList(Arrays.asList(
            new Site("YouTube", "youtube.com"),
            new Site("Reddit", "reddit.com"),
            new Site("X", "x.com"),
            new Site("Instagram", "instagram.com"),
            new Site("Facebook", "facebook.com"),
            new Site("TikTok", "tiktok.com"),
            new Site("Twitch", "twitch.tv"),
            new Site("Netflix", "netflix.com"),
            new Site("Hacker News", "news.ycombinator.com"),
            new Site("Amazon", "amazon.com"),
            new Site("Pinterest", "pinterest.com"),
            new Site("LinkedIn", "linkedin.com")));

    private DistractionSites() { }

    /** The same named sites on every platform; the names are not categories. */
    public static List<Site> common() {
        return COMMON;
    }

    public static State state(Set<String> savedRules, String domain) {
        String host = requireDomain(domain);
        boolean direct = false, covered = false;
        for (String rule : savedRules) {
            if (host.equals(rule)) direct = true;
            else if (host.endsWith("." + rule)) covered = true;
        }
        return direct ? State.DIRECT : covered ? State.COVERED : State.NONE;
    }

    /** Adds the exact domain unless it, or a wider rule, already blocks it. */
    public static String add(Set<String> savedRules, String domain) {
        String host = requireDomain(domain);
        if (state(savedRules, host) != State.NONE) return join(savedRules, null, null);
        return join(savedRules, host, null);
    }

    /** Removes only an exact saved line; a wider rule is never removed by a chip. */
    public static String remove(Set<String> savedRules, String domain) {
        String host = requireDomain(domain);
        if (state(savedRules, host) != State.DIRECT) return join(savedRules, null, null);
        return join(savedRules, null, host);
    }

    /** Domain names in the app are lower-case ASCII with a dot; a chip cannot inject a wildcard. */
    private static String requireDomain(String domain) {
        String host = domain == null ? "" : domain.trim().toLowerCase(java.util.Locale.ROOT);
        if (!host.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")) {
            throw new IllegalArgumentException("Invalid site domain");
        }
        return host;
    }

    private static String join(Set<String> savedRules, String add, String remove) {
        List<String> rules = new ArrayList<>();
        for (String rule : savedRules) {
            if (rule == null || rule.isEmpty()) continue;
            if (remove != null && remove.equals(rule)) continue;
            if (!rules.contains(rule)) rules.add(rule);
        }
        if (add != null && !rules.contains(add)) rules.add(add);
        return String.join("\n", rules);
    }
}
