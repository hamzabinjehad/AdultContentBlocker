package app.hisn.core;

import java.util.Arrays;

public final class DnsUdpTruncationTest {
    public static void main(String[] args) throws Exception {
        DnsPolicy policy = new DnsPolicy(host -> DomainPolicy.Decision.ALLOW);
        byte[] plain = DnsPolicyTest.query("size.example", 1);
        byte[] original = plain.clone();
        check(policy.udpResponseLimit(plain) == 512, "Default UDP limit");
        byte[] tc = policy.truncatedResponse(plain);
        check((tc[2] & 0x82) == 0x82 && (tc[3] & 15) == 0, "Truncation flags");
        check(tc.length == plain.length && tc[7] == 0 && tc[9] == 0 && tc[11] == 0, "Invented/truncated records");
        check(Arrays.equals(plain, original), "Input mutated");
        check(!policy.blocksReply(tc), "Malformed truncated response");
        for (int size : new int[]{0, 128, 512, 1200, 1232, 4096, 65535}) {
            byte[] query = Arrays.copyOf(plain, plain.length + 11);
            query[11] = 1; query[plain.length + 2] = 41;
            query[plain.length + 3] = (byte) (size >>> 8); query[plain.length + 4] = (byte) size;
            int expected = Math.max(512, Math.min(1232, size));
            check(policy.udpResponseLimit(query) == expected, "EDNS limit not clamped");
            byte[] response = policy.truncatedResponse(query);
            check(response.length == plain.length + 11 && (response[2] & 2) != 0, "EDNS truncation malformed");
            check(!policy.blocksReply(response), "EDNS response record bounds");
        }
        check(policy.udpResponseLimit(null) == 0 && policy.truncatedResponse(new byte[2]) == null,
                "Unsupported request gained a fabricated response");
        System.out.println("DnsUdpTruncation tests passed (512/EDNS limits, TC flags, intact questions, no reflected record bytes)");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
