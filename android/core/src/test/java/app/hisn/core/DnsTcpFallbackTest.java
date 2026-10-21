package app.hisn.core;

import java.util.Arrays;

/** Whole supported fallback path with a valid 4096-byte TXT reply; no network I/O. */
public final class DnsTcpFallbackTest {
    public static void main(String[] args) throws Exception {
        DnsPolicy policy = new DnsPolicy(host -> DomainPolicy.Decision.ALLOW);
        DnsExchange exchange = new DnsExchange(policy, DnsTcpFallbackTest::largeReply);
        byte[] query = DnsPolicyTest.query("large.example", 16);
        byte[] answer = exchange.resolve(query);
        check(answer.length == 4096, "Maximum bounded reply not preserved");
        check(answer.length > policy.udpResponseLimit(query), "Expected UDP truncation");
        byte[] truncated = policy.truncatedResponse(query);
        check((truncated[2] & 2) != 0 && truncated.length == query.length && truncated[7] == 0,
                "TC retry must not contain a cut resource record");
        DnsTcpServerTest.Client client = new DnsTcpServerTest.Client(79);
        DnsTcpServer.Request request = DnsTcpServerTest.only(client.send(DnsTcpServerTest.frame(query)).requests);
        byte[] tcpAnswer = exchange.resolve(request.dns);
        check(Arrays.equals(answer, tcpAnswer), "Transport changed validated DNS reply");
        check(client.drain(client.server.complete(request, tcpAnswer, ++client.now), answer, 79).isEmpty(),
                "Unexpected duplicate work after segmented reply");
        client.server.close();
        System.out.println("DnsTcpFallback tests passed (4096-byte validated reply, UDP TC retry, many MSS segments, sequence wrap)");
    }

    private static byte[] largeReply(byte[] query) {
        byte[] reply = Arrays.copyOf(query, 4096);
        reply[2] = (byte) 0x81; reply[3] = (byte) 0x80; reply[7] = 1;
        int p = query.length;
        byte[] record = {(byte) 0xc0, 12, 0, 16, 0, 1, 0, 0, 0, 60, 0, 0};
        System.arraycopy(record, 0, reply, p, record.length);
        int dataSize = reply.length - p - record.length;
        reply[p + 10] = (byte) (dataSize >>> 8); reply[p + 11] = (byte) dataSize;
        p += record.length;
        while (p < reply.length) {
            int chars = Math.min(255, reply.length - p - 1);
            reply[p++] = (byte) chars;
            Arrays.fill(reply, p, p + chars, (byte) 'a');
            p += chars;
        }
        return reply;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
