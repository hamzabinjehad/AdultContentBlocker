package app.hisn.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public final class DnsReplyGuardTest {
    private static byte[] name(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : value.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            out.write(bytes.length); out.write(bytes, 0, bytes.length);
        }
        out.write(0);
        return out.toByteArray();
    }

    static byte[] alias(byte[] question, String owner, String target, int type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = question.clone();
        header[2] = (byte) 0x81; header[7] = 1;
        out.write(header, 0, header.length);
        byte[] ownerBytes = owner == null ? new byte[]{(byte) 0xc0, 12} : name(owner);
        out.write(ownerBytes, 0, ownerBytes.length);
        out.write(type >>> 8); out.write(type); out.write(0); out.write(1);
        out.write(0); out.write(0); out.write(0); out.write(30);
        byte[] targetBytes = name(target);
        int length = targetBytes.length + (type == 65 ? 2 : 0);
        out.write(length >>> 8); out.write(length);
        if (type == 65) { out.write(0); out.write(0); }
        out.write(targetBytes, 0, targetBytes.length);
        return out.toByteArray();
    }

    private static void invalid(DnsPolicy policy, byte[] data) throws Exception {
        try { policy.blocksReply(data); throw new AssertionError("Bad DNS structure accepted"); }
        catch (IOException expected) { }
    }

    static byte[] addressAnswer(byte[] question) {
        byte[] record = {(byte) 0xc0, 12, 0, 1, 0, 1, 0, 0, 0, 30, 0, 4, (byte) 192, 0, 2, 1};
        byte[] answer = Arrays.copyOf(question, question.length + record.length);
        answer[2] = (byte) 0x81;
        answer[3] = (byte) 0x80;
        answer[7] = 1;
        System.arraycopy(record, 0, answer, question.length, record.length);
        return answer;
    }

    public static void main(String[] args) throws Exception {
        DnsPolicy policy = new DnsPolicy(new DomainPolicy(Collections.singleton("blocked.example"), Collections.emptySet()));
        byte[] query = DnsPolicyTest.query("safe.example", 1);
        byte[] address = addressAnswer(query);
        if (policy.blocksReply(address)) throw new AssertionError("Ordinary address reply blocked");
        byte[] malformedAddress = Arrays.copyOf(address, address.length - 1);
        malformedAddress[query.length + 11] = 3;
        invalid(policy, malformedAddress);
        byte[] cname = alias(query, null, "blocked.example", 5);
        if (!policy.blocksReply(cname)) throw new AssertionError("CNAME escape");
        if (!policy.blocksReply(alias(query, null, "blocked.example", 65))) throw new AssertionError("HTTPS alias escape");
        byte[] dnameQuery = DnsPolicyTest.query("www.safe.example", 1);
        if (!policy.blocksReply(alias(dnameQuery, "safe.example", "blocked.example", 39))) throw new AssertionError("DNAME escape");
        if (policy.blocksReply(alias(query, "unrelated.example", "blocked.example", 5))) throw new AssertionError("Unrelated alias blocked");
        DnsExchange exchange = new DnsExchange(policy, message -> alias(message, null, "blocked.example", 5));
        DnsExchange.Resolution resolved = exchange.resolveDetailed(query);
        if (!resolved.blocked || (resolved.answer[3] & 15) != 3 || resolved.answer[0] != query[0]) {
            throw new AssertionError("Alias intervention did not return client NXDOMAIN");
        }
        for (int size = 0; size < cname.length; size++) invalid(policy, java.util.Arrays.copyOf(cname, size));
        byte[] loop = cname.clone();
        int owner = query.length;
        loop[owner] = (byte) (0xc0 | (owner >>> 8)); loop[owner + 1] = (byte) owner;
        invalid(policy, loop);
        byte[] cycle = alias(query, null, "safe.example", 5);
        invalid(policy, cycle);
        java.util.Random random = new java.util.Random(853);
        for (int i = 0; i < 20000; i++) {
            byte[] data = new byte[random.nextInt(4100)]; random.nextBytes(data);
            try { policy.blocksReply(data); } catch (IOException expected) { }
        }
        System.out.println("DnsReplyGuard tests passed (CNAME, DNAME, HTTPS aliases, unrelated records and malformed replies)");
    }
}
