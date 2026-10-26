package app.hisn.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public final class DnsPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static byte[] query(String name, int type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = {0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0};
        out.write(header, 0, header.length);
        for (String label : name.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            out.write(bytes.length);
            out.write(bytes, 0, bytes.length);
        }
        out.write(0); out.write(0); out.write(type); out.write(0); out.write(1);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        DnsPolicy policy = new DnsPolicy(new DomainPolicy(Collections.singleton("blocked.example"),
                Collections.singleton("safe.blocked.example")));
        byte[] packet = query("www.blocked.example", 28);
        DnsPolicy.Result blocked = policy.evaluate(packet);
        check(blocked.action == DnsPolicy.Action.BLOCK, "AAAA block");
        check(blocked.response != packet && blocked.response[0] == 0x12 && blocked.response[1] == 0x34,
                "response preserves transaction without mutating request");
        check((blocked.response[2] & 0xff) == 0x81 && (blocked.response[3] & 0xff) == 3, "NXDOMAIN response");
        check(policy.evaluate(query("safe.blocked.example", 1)).action == DnsPolicy.Action.FORWARD, "allow override");
        check(policy.evaluate(query("notblocked.example", 1)).action == DnsPolicy.Action.FORWARD, "label boundary");
        byte[] extra = query("blocked.example", 1); extra[11] = 1;
        check(policy.evaluate(extra).action == DnsPolicy.Action.UNSUPPORTED, "missing OPT record");
        byte[] edns = java.util.Arrays.copyOf(packet, packet.length + 17);
        edns[11] = 1;
        int opt = packet.length;
        edns[opt + 2] = 41;
        edns[opt + 3] = 0x10;
        edns[opt + 7] = (byte) 0x80;
        edns[opt + 10] = 6;
        edns[opt + 12] = 12; // EDNS padding, two bytes.
        edns[opt + 14] = 2;
        DnsPolicy.Result ednsBlock = policy.evaluate(edns);
        check(ednsBlock.action == DnsPolicy.Action.BLOCK, "EDNS query blocked");
        check(ednsBlock.response.length == packet.length + 11, "client options removed");
        check(ednsBlock.response[opt + 7] == (byte) 0x80, "DO preserved");
        check(ednsBlock.response[opt + 3] == 4 && (ednsBlock.response[opt + 4] & 255) == 208,
                "response UDP limit");
        byte[] badOption = edns.clone(); badOption[opt + 14] = 3;
        check(policy.evaluate(badOption).action == DnsPolicy.Action.UNSUPPORTED, "option overrun rejected");
        byte[] badVersion = edns.clone(); badVersion[opt + 6] = 1;
        check(policy.evaluate(badVersion).action == DnsPolicy.Action.UNSUPPORTED, "unknown EDNS version");
        for (int length = 0; length < edns.length; length++) {
            check(policy.evaluate(java.util.Arrays.copyOf(edns, length)).action == DnsPolicy.Action.UNSUPPORTED,
                    "every truncated EDNS packet rejected");
        }
        java.util.Random random = new java.util.Random(1729);
        for (int i = 0; i < 20000; i++) {
            byte[] arbitrary = new byte[random.nextInt(600)];
            random.nextBytes(arbitrary);
            policy.evaluate(arbitrary); // Malformed input must never crash the parser.
        }
        byte[] compressed = query("blocked.example", 1); compressed[12] = (byte) 0xc0;
        check(policy.evaluate(compressed).action == DnsPolicy.Action.UNSUPPORTED, "compression unsupported");
        byte[] truncated = java.util.Arrays.copyOf(packet, packet.length - 1);
        check(policy.evaluate(truncated).action == DnsPolicy.Action.UNSUPPORTED, "truncated query");
        byte[] response = packet.clone(); response[2] |= (byte) 0x80;
        check(policy.evaluate(response).action == DnsPolicy.Action.UNSUPPORTED, "response is not a query");
        byte[] otherClass = packet.clone(); otherClass[otherClass.length - 1] = 3;
        check(policy.evaluate(otherClass).action == DnsPolicy.Action.UNSUPPORTED, "non-IN class");
        check(policy.evaluate(null).action == DnsPolicy.Action.UNSUPPORTED, "null input");
        byte[] failure = policy.failureResponse(query("safe.example", 1));
        check((failure[3] & 15) == 2 && (failure[2] & 0x80) != 0, "resolver failure is SERVFAIL");
        check(policy.failureResponse(new byte[3]) == null, "no reply fabricated for invalid query");

        // Names no domain rule can equal are forwarded, but a blocked hostname suffix still blocks.
        for (String name : new String[] {"_sip._tcp.example.org", "_dmarc.safe.blocked.example",
                "host_name.example.org", "_dns.resolver.arpa", "_tcp.local", "printer", "localhost", "a._b"}) {
            check(policy.evaluate(query(name, 33)).action == DnsPolicy.Action.FORWARD, "forward " + name);
        }
        for (String name : new String[] {"_sip._tcp.blocked.example", "s1._domainkey.www.blocked.example",
                "host_name.blocked.example", "_8443._https.blocked.example"}) {
            check(policy.evaluate(query(name, 16)).action == DnsPolicy.Action.BLOCK, "suffix block " + name);
        }
        check(policy.evaluate(query("_srv._tcp.192.0.2.1", 33)).action == DnsPolicy.Action.UNSUPPORTED,
                "numeric suffix stays unsupported");
        check(policy.evaluate(query("bad*name.example", 1)).action == DnsPolicy.Action.UNSUPPORTED,
                "invalid multi-label hostname stays unsupported");
        byte[] root = {0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 1};
        check(policy.evaluate(root).action == DnsPolicy.Action.FORWARD, "root NS query forwarded");
        check((policy.failureResponse(query("_sip._tcp.example.org", 33))[3] & 15) == 2,
                "forwarded SRV gets SERVFAIL on resolver failure");
        DnsExchange srv = new DnsExchange(policy, request -> {
            byte[] reply = request.clone(); reply[2] = (byte) 0x81; reply[3] = (byte) 0x80; return reply;
        });
        DnsExchange.Resolution answer = srv.resolveDetailed(query("_sip._tcp.example.org", 33));
        check(!answer.blocked && (answer.answer[3] & 15) == 0, "SRV reply passes the reply guard");
        check(srv.resolveDetailed(query("printer", 1)).answer != null, "single-label reply passes the reply guard");
        byte[] srvQuery = query("_sip._tcp.example.org", 33);
        check(policy.blocksReply(DnsReplyGuardTest.alias(srvQuery, null, "_sip._tcp.www.blocked.example", 5)),
                "underscore alias target under a blocked domain is blocked");
        check(!policy.blocksReply(DnsReplyGuardTest.alias(srvQuery, null, "_sip._tcp.other.example", 5)),
                "underscore alias target under an allowed domain passes");
        System.out.println("DnsPolicy tests passed");
    }
}
