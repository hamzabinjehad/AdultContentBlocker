package app.hisn.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;

/** One bounded UDP exchange with one explicitly configured resolver; no fallback. */
public final class UdpDnsTransport implements DnsExchange.Transport {
    public interface SocketProtector {
        /** Android's VPN adapter must call VpnService.protect here before connecting. */
        boolean protect(DatagramSocket socket) throws IOException;
    }

    private final InetSocketAddress resolver;
    private final int timeoutMillis;
    private final SocketProtector protector;

    public UdpDnsTransport(InetSocketAddress resolver, int timeoutMillis, SocketProtector protector) {
        if (resolver == null || resolver.isUnresolved() || resolver.getPort() == 0
                || timeoutMillis < 1 || timeoutMillis > 10000 || protector == null) {
            throw new IllegalArgumentException("Invalid resolver configuration");
        }
        this.resolver = resolver;
        this.timeoutMillis = timeoutMillis;
        this.protector = protector;
    }

    @Override public byte[] exchange(byte[] query) throws IOException {
        if (query == null || query.length < 12 || query.length > 4096) {
            throw new IOException("Invalid request size");
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            if (!protector.protect(socket)) throw new IOException("Could not protect resolver socket");
            socket.connect(resolver);
            socket.setSoTimeout(timeoutMillis);
            socket.send(new DatagramPacket(query, query.length));
            byte[] buffer = new byte[4097];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            if (reply.getLength() > 4096) throw new IOException("DNS reply too large");
            return Arrays.copyOf(buffer, reply.getLength());
        }
    }
}
