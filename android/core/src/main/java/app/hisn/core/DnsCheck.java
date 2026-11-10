package app.hisn.core;

/** The decision a route-check completion may publish, separated from Android and its sockets. */
public final class DnsCheck {
    public enum Outcome { PASSED, STALE, FAILED }

    /**
     * @param passed            the probe itself succeeded
     * @param observed          the probe's DNS traffic was seen in this tunnel
     * @param networkUnchanged  the network and failure epochs did not move during the probe
     * @param policyUnchanged   the saved layer identity did not move during the probe
     * @param sessionUnchanged  the tunnel service session did not restart during the probe
     */
    public static Outcome outcome(boolean passed, boolean observed, boolean networkUnchanged,
            boolean policyUnchanged, boolean sessionUnchanged) {
        // A late result from an older policy or service cannot certify the current one.
        if (!policyUnchanged || !sessionUnchanged) return Outcome.STALE;
        if (!passed || !observed) return Outcome.FAILED;
        return networkUnchanged ? Outcome.PASSED : Outcome.STALE;
    }

    private DnsCheck() { }
}
