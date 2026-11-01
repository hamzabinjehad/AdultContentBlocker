package app.hisn.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Which Hisn layer, if any, covers a browser's page content on this device.
 *
 * <p>Pure so the three answers stay testable and the UI cannot claim coverage the
 * layers do not provide. A browser is covered when Hisn blocks it, when the
 * accessibility guard inspects it, or when the DNS layer is resolving traffic.
 * The DNS answer is about name resolution only: a browser with its own VPN, Tor
 * or private DNS resolver can leave that path, which is why the app states the
 * scope next to the list instead of calling the browser fully protected.
 */
public final class BrowserCoverage {
    public enum Layer {
        /** Refused by the app block: the browser cannot be opened at all. */
        BLOCKED,
        /** Selected for text and image inspection, with or without the DNS layer. */
        INSPECTED,
        /** Only the DNS layer covers it, while that layer is running. */
        DOMAINS,
        /** No layer covers it: this is the browser the owner should block or inspect. */
        NONE
    }

    /** The strongest layer that currently covers {@code packageName}. */
    public static Layer layer(String packageName, Set<String> blocked, Set<String> inspected,
            boolean inspectionActive, boolean domainsRunning) {
        if (blocked.contains(packageName)) return Layer.BLOCKED;
        if (inspectionActive && inspected.contains(packageName)) return Layer.INSPECTED;
        return domainsRunning ? Layer.DOMAINS : Layer.NONE;
    }

    /** Installed browsers with no covering layer, in the order they were given. */
    public static List<String> withoutCoverage(Collection<String> browsers, Set<String> blocked,
            Set<String> inspected, boolean inspectionActive, boolean domainsRunning) {
        List<String> result = new ArrayList<>();
        for (String browser : browsers) {
            if (layer(browser, blocked, inspected, inspectionActive, domainsRunning) == Layer.NONE) {
                result.add(browser);
            }
        }
        return result;
    }

    private BrowserCoverage() { }
}
