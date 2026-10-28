package app.hisn.core;

public final class DnsNoticeTest {
    private static void expect(DnsNotice expected, SetupGuide.Network network, boolean strict) {
        DnsNotice actual = DnsNotice.of(network, strict);
        if (actual != expected) throw new AssertionError(network + "/" + strict + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        expect(DnsNotice.STARTING, SetupGuide.Network.STARTING, false);
        expect(DnsNotice.STARTING, SetupGuide.Network.STARTING, true);
        expect(DnsNotice.FILTERING, SetupGuide.Network.RUNNING, false);
        expect(DnsNotice.PRIVATE_DNS_BYPASS, SetupGuide.Network.RUNNING, true);
        expect(DnsNotice.DEGRADED, SetupGuide.Network.DEGRADED, false);
        expect(DnsNotice.PRIVATE_DNS_BYPASS, SetupGuide.Network.DEGRADED, true);
        expect(DnsNotice.STOPPED, SetupGuide.Network.OFF, false);
        expect(DnsNotice.STOPPED, SetupGuide.Network.ERROR, true);
        int filtering = 0;
        for (SetupGuide.Network network : SetupGuide.Network.values())
            for (boolean strict : new boolean[]{false, true})
                if (DnsNotice.of(network, strict) == DnsNotice.FILTERING) {
                    if (network != SetupGuide.Network.RUNNING || strict) throw new AssertionError("False filtering notice");
                    filtering++;
                }
        if (filtering != 1) throw new AssertionError("Filtering notice unreachable");
        try { DnsNotice.of(null, false); throw new AssertionError("Missing state accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("DnsNotice tests passed (filtering only while running without a Private DNS bypass)");
    }
}
