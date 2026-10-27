package app.hisn.core;

import static app.hisn.core.UnderlyingNetwork.Transport.CELLULAR;
import static app.hisn.core.UnderlyingNetwork.Transport.ETHERNET;
import static app.hisn.core.UnderlyingNetwork.Transport.WIFI;

public final class UnderlyingNetworkTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        inferred();
        background();
        reported();
        System.out.println("UnderlyingNetwork tests passed (standby mobile data ignored, default switch, loss, validation, link, background and reported default)");
    }

    /** Android 8 to 11: every Internet network is reported; the one in use is ranked as Android does. */
    private static void inferred() {
        UnderlyingNetwork<String> network = new UnderlyingNetwork<>(false);
        check(network.available("wifi") && "wifi".equals(network.inUse()), "First network is a change");
        check(!network.capabilities("wifi", WIFI, true, false, true), "First capabilities are not a change");
        check(!network.link("wifi", "wifi-a"), "First link properties are not a change");
        // Mobile data kept up beside Wi-Fi: none of its events touch the Wi-Fi connections.
        check(!network.available("cell"), "Standby network appearing");
        check(!network.capabilities("cell", CELLULAR, true, false, true), "Standby capabilities");
        check(!network.link("cell", "cell-a"), "Standby link");
        check(!network.link("cell", "cell-b"), "Standby link change");
        check(!network.capabilities("cell", CELLULAR, false, false, true), "Standby validation lost");
        check(!network.capabilities("cell", CELLULAR, true, false, true), "Standby validation back");
        check(!network.lost("cell") && "wifi".equals(network.inUse()), "Standby network lost");
        check(!network.available("cell") && !network.capabilities("cell", CELLULAR, true, false, true), "Standby back");
        check(!network.capabilities("wifi", WIFI, true, false, true), "Repeated identical capabilities");
        check(!network.link("wifi", "wifi-a"), "Equal link snapshot");
        // The network in use changes: drop connections.
        check(network.link("wifi", "wifi-b"), "In-use link change");
        check(network.capabilities("wifi", WIFI, true, true, true), "In-use captive portal change");
        // Wi-Fi without validation loses to validated mobile data, as Android ranks it.
        check(network.capabilities("wifi", WIFI, false, false, true) && "cell".equals(network.inUse()), "Unvalidated Wi-Fi yields");
        check(!network.link("wifi", "wifi-c"), "Wi-Fi is now the standby network");
        check(network.capabilities("wifi", WIFI, true, false, true) && "wifi".equals(network.inUse()), "Validated Wi-Fi preferred again");
        check(network.lost("wifi") && "cell".equals(network.inUse()), "Wi-Fi lost: mobile data in use");
        check(!network.available("wifi"), "Wi-Fi back without capabilities ranks last");
        check(network.capabilities("wifi", WIFI, true, false, true) && "wifi".equals(network.inUse()), "Validated Wi-Fi back");
        check(!network.capabilities("eth", ETHERNET, true, false, true), "Unknown network ignored");
        check(!network.available("eth") && network.capabilities("eth", ETHERNET, true, false, true)
                && "eth".equals(network.inUse()), "Ethernet preferred over Wi-Fi");
        check(network.lost("eth") && network.lost("wifi") && network.lost("cell") && network.inUse() == null, "All lost");
        check(!network.lost("cell"), "Repeated loss");
        check(network.available("cell") && "cell".equals(network.inUse()), "Network after none");
        // Two networks with equal rank: the earlier one stays in use.
        check(!network.capabilities("cell", CELLULAR, true, false, true), "First capabilities");
        check(!network.available("cell2") && !network.capabilities("cell2", CELLULAR, true, false, true), "Equal rank keeps first");
        check(!network.link("cell2", "x") && !network.link("cell2", "y"), "Equal-rank standby link changes");
        network.clear();
        check(network.inUse() == null && !network.tracks("cell") && network.available("cell"), "Clear forgets everything");
    }

    /** Android 9 to 11: a network kept up only in the background is never the one in use. */
    private static void background() {
        UnderlyingNetwork<String> network = new UnderlyingNetwork<>(false);
        check(network.available("cell") && !network.capabilities("cell", CELLULAR, true, false, true), "Mobile data first");
        // Wi-Fi the owner kept without internet access is the default; mobile data drops to the background.
        check(!network.available("wifi") && !network.capabilities("wifi", WIFI, false, false, true)
                && "cell".equals(network.inUse()), "Unvalidated foreground Wi-Fi ranks below validated mobile data");
        check(network.capabilities("cell", CELLULAR, true, false, false) && "wifi".equals(network.inUse()),
                "Mobile data in the background yields to foreground Wi-Fi");
        check(!network.capabilities("cell", CELLULAR, false, false, false) && !network.link("cell", "c")
                && !network.lost("cell"), "Background network events are ignored");
        check(!network.link("wifi", "w") && network.link("wifi", "w2"), "In-use Wi-Fi link change");
    }

    /** Android 12 and later: a best-matching callback names the default network itself. */
    private static void reported() {
        UnderlyingNetwork<String> network = new UnderlyingNetwork<>(true);
        check(network.available("wifi") && "wifi".equals(network.inUse()), "Reported default");
        check(!network.capabilities("wifi", WIFI, true, false, true) && !network.link("wifi", "a"), "First values");
        check(!network.available("wifi"), "Same default again");
        check(!network.capabilities("cell", CELLULAR, true, false, true) && !network.link("cell", "x") && !network.lost("cell"),
                "Events for a network that is not the default are ignored");
        // Ranking is not applied: Android's choice stands, even unvalidated.
        check(network.capabilities("wifi", WIFI, false, false, true), "In-use validation change");
        check(network.available("cell") && "cell".equals(network.inUse()) && !network.tracks("wifi"), "Default switched");
        check(!network.lost("wifi"), "Old default lost later");
        check(!network.capabilities("cell", CELLULAR, true, false, true), "New default first capabilities");
        check(!network.link("cell", "b") && network.link("cell", "c"), "In-use link change");
        check(network.lost("cell") && network.inUse() == null, "Default lost");
        check(network.available("wifi") && "wifi".equals(network.inUse()), "Default after none");
    }
}
