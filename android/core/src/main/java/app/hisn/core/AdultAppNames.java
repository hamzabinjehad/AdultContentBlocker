package app.hisn.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Recognises an installed adult app by what it already says about itself, so a clone under a
 * new package id is caught without a hand-kept list. Two signals, either enough:
 *
 * <ul>
 * <li>its name is adult text by the same {@link AdultTextPolicy} that blocks pages ("Porn
 * videos", "سكس"); protective names ("Porn blocker", "Porn addiction recovery") are not;</li>
 * <li>its package id is a reversed adult website: {@code com.xvideos.app} is {@code xvideos.com},
 * and the published adult list, an adult top-level domain or an adult host term blocks that
 * site. The owner's own blocked sites are not a signal: blocking youtube.com must not block
 * the YouTube app.</li>
 * </ul>
 */
public final class AdultAppNames {
    private static final Pattern LABEL = Pattern.compile("[a-z0-9-]{1,63}");
    private static final Pattern TOP_LEVEL = Pattern.compile("[a-z]{2,10}");

    private AdultAppNames() { }

    /** The websites a package id names: its first two segments reversed ("com.x.app" → "x.com"),
     * and the first three when the second is a country's second level ("uk.co.x" → "x.co.uk"). */
    public static List<String> hosts(String packageName) {
        List<String> hosts = new ArrayList<>();
        if (packageName == null) return hosts;
        String[] parts = packageName.toLowerCase(java.util.Locale.ROOT).replace('_', '-').split("\\.");
        if (parts.length < 2 || !TOP_LEVEL.matcher(parts[0]).matches()) return hosts;
        for (String part : parts) if (!LABEL.matcher(part).matches()) return hosts;
        hosts.add(parts[1] + "." + parts[0]);
        if (parts.length >= 3 && (parts[1].equals("co") || parts[1].equals("com") || parts[1].equals("net")
                || parts[1].equals("org"))) {
            hosts.add(parts[2] + "." + parts[1] + "." + parts[0]);
        }
        return hosts;
    }

    /** Whether a site check counts: only the shared adult signals, never the owner's own list. */
    public static boolean adultSite(HostDecision.Result result) {
        return result != null && adultSite(result.outcome, result.source);
    }

    public static boolean adultSite(HostDecision.Outcome outcome, HostDecision.Source source) {
        return outcome == HostDecision.Outcome.BLOCKED
                && (source == HostDecision.Source.PUBLISHED_LIST || source == HostDecision.Source.ADULT_TLD
                    || source == HostDecision.Source.HOST_TERM);
    }

    /** Whether an app's name is adult text. Null policy (still loading): not known, so false. */
    public static boolean adultName(AdultTextPolicy policy, CharSequence label) {
        if (policy == null || label == null) return false;
        String text = label.toString().trim();
        if (text.isEmpty() || text.length() > 200) return false;
        return policy.blocksAnyLine(text);
    }
}
