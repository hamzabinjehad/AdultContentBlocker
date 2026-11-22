package app.hisn.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class DnsExchangeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void expectFailure(DnsExchange exchange, byte[] request) throws Exception {
        try {
            exchange.resolve(request);
            throw new AssertionError("Bad exchange accepted");
        } catch (IOException expected) { }
    }

    private static byte[] reply(byte[] request) {
        byte[] response = request.clone();
        response[2] = (byte) 0x81;
        response[3] = (byte) 0x80;
        return response;
    }

    public static void main(String[] args) throws Exception {
        DnsPolicy policy = new DnsPolicy(new DomainPolicy(Collections.singleton("blocked.example"), Collections.emptyList()));
        byte[] request = DnsPolicyTest.query("safe.example", 1);
        byte[] original = request.clone();
        DnsExchange blocked = new DnsExchange(policy, query -> { throw new AssertionError("Blocked request leaked"); });
        check((blocked.resolve(DnsPolicyTest.query("blocked.example", 1))[3] & 15) == 3, "local NXDOMAIN");
        expectFailure(blocked, new byte[4]);
        expectFailure(blocked, null);
        DnsExchange normal = new DnsExchange(policy, DnsExchangeTest::reply);
        byte[] result = normal.resolve(request);
        check(result[0] == original[0] && result[1] == original[1], "client ID restored");
        check(java.util.Arrays.equals(request, original), "caller request unchanged");
        java.util.concurrent.atomic.AtomicBoolean ruleEnabled = new java.util.concurrent.atomic.AtomicBoolean();
        DnsExchange queued = new DnsExchange(
                new DnsPolicy(host -> ruleEnabled.get() ? DomainPolicy.Decision.BLOCK : DomainPolicy.Decision.ALLOW),
                query -> { throw new AssertionError("A newly blocked queued name reached upstream"); });
        DnsExchange.PreparedQuery waiting = queued.prepare(request);
        check(waiting.localAnswer() == null, "an allowed query requires a resolver slot");
        request[0] ^= 1;
        ruleEnabled.set(true);
        DnsExchange.Resolution newBlock = queued.resolvePrepared(waiting);
        check(newBlock.blocked && newBlock.answer[0] == original[0], "queued query owns its wire and uses fresh rules");
        check(queued.prepare(request).localAnswer().blocked, "a fresh block has an inline local answer");
        check((waiting.failureAnswer()[3] & 15) == 2, "prepared failure retains its original question");
        request = original.clone();
        ruleEnabled.set(false);
        DnsExchange inFlight = new DnsExchange(
                new DnsPolicy(host -> ruleEnabled.get() ? DomainPolicy.Decision.BLOCK : DomainPolicy.Decision.ALLOW),
                query -> {
                    ruleEnabled.set(true);
                    return DnsReplyGuardTest.addressAnswer(query);
                });
        DnsExchange.Resolution changedDuringLookup = inFlight.resolveDetailed(request);
        check(changedDuringLookup.blocked && (changedDuringLookup.answer[3] & 15) == 3,
                "an ordinary address reply still checks rules changed during lookup");
        expectFailure(new DnsExchange(policy, query -> { byte[] r = reply(query); r[0] ^= 1; return r; }), request);
        expectFailure(new DnsExchange(policy, query -> { byte[] r = reply(query); r[13] ^= 1; return r; }), request);
        expectFailure(new DnsExchange(policy, query -> { byte[] r = reply(query); r[2] |= 2; return r; }), request);
        expectFailure(new DnsExchange(policy, query -> query), request);
        expectFailure(new DnsExchange(policy, query -> null), request);
        // A transport mutating its argument must not alter the expected question.
        expectFailure(new DnsExchange(policy, query -> { query[13] ^= 1; return reply(query); }), request);

        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (DatagramSocket server = new DatagramSocket(0, loopback)) {
            server.setSoTimeout(2000);
            Future<?> serving = executor.submit(() -> {
                try {
                    DatagramPacket incoming = new DatagramPacket(new byte[4096], 4096);
                    server.receive(incoming);
                    byte[] received = java.util.Arrays.copyOf(incoming.getData(), incoming.getLength());
                    byte[] response = reply(received);
                    server.send(new DatagramPacket(response, response.length, incoming.getSocketAddress()));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            });
            InetSocketAddress address = new InetSocketAddress(loopback, server.getLocalPort());
            DnsExchange live = new DnsExchange(policy, new UdpDnsTransport(address, 1000, socket -> true));
            check((live.resolve(request)[2] & 0x80) != 0, "local UDP exchange");
            serving.get(3, TimeUnit.SECONDS);
            expectFailure(new DnsExchange(policy, new UdpDnsTransport(address, 50, socket -> false)), request);
            // Bound server remains open but has no receiver: exchange must time out.
            expectFailure(new DnsExchange(policy, new UdpDnsTransport(address, 50, socket -> true)), request);
        } finally {
            executor.shutdownNow();
        }
        System.out.println("DnsExchange tests passed (loopback UDP, rejection, timeout and no-leak checks)");
    }
}
