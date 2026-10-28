package app.hisn.core;

/** What the ongoing DNS notification says. It never reads as filtering while the session
 * is starting, degraded, or open to a strict Private DNS bypass. */
public enum DnsNotice {
    STARTING, FILTERING, DEGRADED, PRIVATE_DNS_BYPASS, STOPPED;

    public static DnsNotice of(SetupGuide.Network network, boolean privateDnsStrict) {
        if (network == null) throw new IllegalArgumentException("Missing network state");
        switch (network) {
            case STARTING: return STARTING;
            case RUNNING:
            case DEGRADED:
                // A bypass of the whole tunnel outweighs failed or unsupported requests inside it.
                if (privateDnsStrict) return PRIVATE_DNS_BYPASS;
                return network == SetupGuide.Network.RUNNING ? FILTERING : DEGRADED;
            default: return STOPPED;
        }
    }
}
