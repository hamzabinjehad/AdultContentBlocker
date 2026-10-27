package app.hisn.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Follows the one network that protected resolver sockets use: the system's default non-VPN network.
 * Each event returns true only when upstream connections must be dropped, because that network, its
 * validation or captive-portal state, or its link properties changed. Changes on a standby network,
 * such as mobile data kept up beside Wi-Fi, return false.
 *
 * With {@code reported}, Android names the default network itself (a best-matching callback, Android
 * 12 and later): the latest available network is the one in use, and events for others are ignored.
 * Otherwise every Internet network is reported and the one in use is inferred the way Android ranks
 * them: foreground before background, validated first, then Ethernet, Wi-Fi, other transports and
 * mobile data, then the earliest. A network whose capabilities are not known yet ranks last. An
 * inference can be wrong (for example a vendor switch to mobile data on slow Wi-Fi); connections on
 * a network that went away then fail by timeout and reconnect. The first report of a value is never
 * a change.
 */
public final class UnderlyingNetwork<K> {
    /** In Android's order of preference. */
    public enum Transport { ETHERNET, WIFI, OTHER, CELLULAR }

    private static final class State {
        final long order;
        Transport transport;
        boolean foreground;
        int mark = -1; // bit 0 validated, bit 1 captive portal; -1 until capabilities arrive
        Object link;
        State(long order) { this.order = order; }
    }

    private final boolean reported;
    private final Map<K, State> networks = new LinkedHashMap<>();
    private long arrivals;
    private K reportedDefault;
    // What the last event settled on; connections were opened on this network.
    private K inUse;
    private int inUseMark = -1;
    private Object inUseLink;

    public UnderlyingNetwork(boolean reported) { this.reported = reported; }

    public synchronized boolean available(K network) {
        if (network == null || (reported && network.equals(reportedDefault))) return false;
        if (reported) {
            // A best-matching callback reports a new default without losing the old one first.
            networks.keySet().retainAll(Collections.singleton(network));
            reportedDefault = network;
        }
        if (!networks.containsKey(network)) networks.put(network, new State(arrivals++));
        return settle();
    }

    /** {@code foreground} is false for a network kept up only in the background (Android 9+). */
    public synchronized boolean capabilities(K network, Transport transport, boolean validated, boolean captive,
            boolean foreground) {
        State state = networks.get(network);
        if (state == null || transport == null) return false;
        state.transport = transport;
        state.foreground = foreground;
        state.mark = (validated ? 1 : 0) | (captive ? 2 : 0);
        return settle();
    }

    /** {@code properties} must be an independent snapshot, compared with {@code equals}. */
    public synchronized boolean link(K network, Object properties) {
        State state = networks.get(network);
        if (state == null || properties == null) return false;
        state.link = properties;
        return settle();
    }

    public synchronized boolean lost(K network) {
        if (network == null || networks.remove(network) == null) return false;
        if (network.equals(reportedDefault)) reportedDefault = null;
        return settle();
    }

    /** Whether events for this network are followed (in reported mode, only the default one). */
    public synchronized boolean tracks(K network) { return networks.containsKey(network); }

    public synchronized K inUse() { return inUse; }

    public synchronized void clear() {
        networks.clear(); reportedDefault = null; inUse = null; inUseMark = -1; inUseLink = null;
    }

    private boolean settle() {
        K next = reported ? reportedDefault : best();
        State state = next == null ? null : networks.get(next);
        int mark = state == null ? -1 : state.mark;
        Object link = state == null ? null : state.link;
        boolean switched = !Objects.equals(next, inUse);
        boolean changed = switched || (inUseMark >= 0 && mark >= 0 && mark != inUseMark)
                || (inUseLink != null && link != null && !inUseLink.equals(link));
        if (switched) { inUseMark = -1; inUseLink = null; }
        inUse = next;
        if (mark >= 0) inUseMark = mark;
        if (link != null) inUseLink = link;
        return changed;
    }

    private K best() {
        K best = null;
        State chosen = null;
        for (Map.Entry<K, State> entry : networks.entrySet()) {
            if (chosen == null || rank(entry.getValue(), chosen) < 0) { best = entry.getKey(); chosen = entry.getValue(); }
        }
        return best;
    }

    // Negative when a is preferred over b.
    private static int rank(State a, State b) {
        boolean aKnown = a.transport != null, bKnown = b.transport != null;
        if (aKnown != bKnown) return aKnown ? -1 : 1;
        if (aKnown) {
            if (a.foreground != b.foreground) return a.foreground ? -1 : 1;
            boolean aValid = (a.mark & 1) != 0, bValid = (b.mark & 1) != 0;
            if (aValid != bValid) return aValid ? -1 : 1;
            if (a.transport != b.transport) return a.transport.compareTo(b.transport);
        }
        return Long.compare(a.order, b.order);
    }
}
