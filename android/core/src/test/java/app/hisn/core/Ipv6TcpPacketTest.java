package app.hisn.core;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static app.hisn.core.DnsTcpServerTest.ACK;
import static app.hisn.core.DnsTcpServerTest.EMPTY;
import static app.hisn.core.DnsTcpServerTest.RST;
import static app.hisn.core.DnsTcpServerTest.SYN;
import static app.hisn.core.DnsTcpServerTest.check;
import static app.hisn.core.DnsTcpServerTest.frame;
import static app.hisn.core.DnsTcpServerTest.put16;
import static app.hisn.core.DnsTcpServerTest.put32;
import static app.hisn.core.DnsTcpServerTest.u16;
import static app.hisn.core.DnsTcpServerTest.u32;

public final class Ipv6TcpPacketTest {
    private static final byte[] SERVER = {(byte) 0xfd, 0, 1, 0x11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2};
    private static final byte[] PEER = {(byte) 0xfd, 0, 1, 0x11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    private static final int PORT = 45000;

    public static void main(String[] args) {
        parsingAndReply();
        isolatedFlows();
        System.out.println("IPv6 TCP DNS tests passed (checksums, endpoint validation, resets and mixed-family flow isolation)");
    }

    private static void parsingAndReply() {
        byte[] syn = packet(PEER, 53, 90, 0, SYN, EMPTY);
        Ipv6TcpPacket request = Ipv6TcpPacket.parse(syn, SERVER);
        check(request != null && request.sourcePort == PORT && request.destinationPort == 53, "IPv6 TCP ports");
        check(request.sequence == 90 && request.mss == 1220, "IPv6 sequence and default MSS");
        check(TcpDnsPacket.parse(syn, DnsTcpServerTest.SERVER, SERVER, 53) instanceof Ipv6TcpPacket,
                "Dispatcher missed IPv6 TCP");
        check(DnsPacketKind.classify(syn, DnsTcpServerTest.SERVER, SERVER) == DnsPacketKind.TCP_DNS_UNSUPPORTED,
                "Dropped TCP DNS would not be recorded");

        byte[] data = {1, 2, 3, 4, 5};
        byte[] frame = packet(PEER, 53, 90, 20, ACK, data);
        byte[] scratch = Arrays.copyOf(frame, 1501);
        Arrays.fill(scratch, frame.length, scratch.length, (byte) -1);
        TcpDnsPacket buffered = TcpDnsPacket.parse(scratch, frame.length, DnsTcpServerTest.SERVER, SERVER, 53);
        check(buffered != null, "Receive-buffer TCP parse failed");
        check(DnsPacketKind.classify(scratch, frame.length, DnsTcpServerTest.SERVER, SERVER)
                == DnsPacketKind.TCP_DNS_UNSUPPORTED, "Diagnostic read beyond the frame");
        check(TcpDnsPacket.parse(scratch, frame.length - 1, DnsTcpServerTest.SERVER, SERVER, 53) == null,
                "Parsed a short frame using trailing scratch bytes");
        Arrays.fill(scratch, (byte) 0);
        check(buffered.endpoint.equals(request.endpoint) && Arrays.equals(buffered.payload, data),
                "TCP retained its reusable receive buffer");
        byte[] reply = request.response(24, 91, ACK, 8192, data);
        check(reply[0] == 0x60 && reply[6] == 6 && u16(reply, 4) == reply.length - 40, "IPv6 reply header");
        check(Arrays.equals(Arrays.copyOfRange(reply, 8, 24), SERVER)
                && Arrays.equals(Arrays.copyOfRange(reply, 24, 40), PEER), "Reply addresses not reversed");
        check(u16(reply, 40) == 53 && u16(reply, 42) == PORT, "Reply ports not reversed");
        check(Arrays.equals(Arrays.copyOfRange(reply, 60, reply.length), data), "Odd-length payload changed");
        check(checksum(reply) == 0, "Bad IPv6 reply checksum");

        byte[] wrong = SERVER.clone();
        wrong[15] = 3;
        check(Ipv6TcpPacket.parse(syn, wrong) == null, "Accepted unrelated endpoint");
        syn[56] ^= 1;
        check(Ipv6TcpPacket.parse(syn, SERVER) == null, "Accepted corrupt checksum");
        syn[56] ^= 1;
        syn[6] = 44;
        check(Ipv6TcpPacket.parse(syn, SERVER) == null
                && DnsPacketKind.classify(syn, DnsTcpServerTest.SERVER, SERVER) == DnsPacketKind.FRAGMENT,
                "Fragment misidentified as a TCP segment");
        check(Ipv6TcpPacket.parse(Arrays.copyOf(syn, 51), SERVER) == null, "Accepted truncated IPv6 header");

        byte[] tls = packet(PEER, 853, 41, 0, SYN, EMPTY);
        check(Ipv6TcpPacket.parse(tls, SERVER) == null, "Accepted a Private DNS probe as port 53");
        byte[] refusal = DnsTcpServer.refuse(Ipv6TcpPacket.parse(tls, SERVER, 853));
        check((refusal[53] & 255) == (RST | ACK) && u32(refusal, 48) == 42
                && u16(refusal, 40) == 853 && checksum(refusal) == 0, "Invalid IPv6 Private DNS reset");
    }

    private static void isolatedFlows() {
        AtomicLong sequence = new AtomicLong(100);
        DnsTcpServer server = new DnsTcpServer(() -> sequence.getAndAdd(100));
        byte[] peer2 = PEER.clone();
        peer2[3] = 0x12;
        byte[] peer1SynAck = server.receive(parse(PEER, 10, 0, SYN, EMPTY), 0).packets.get(0);
        byte[] peer2SynAck = server.receive(parse(peer2, 20, 0, SYN, EMPTY), 0).packets.get(0);
        byte[] ipv4SynAck = server.receive(
                DnsTcpServerTest.packet(PORT, 30, 0, SYN, 8192, EMPTY, 1200), 0).packets.get(0);
        check(u32(peer1SynAck, 44) == 100 && u32(peer2SynAck, 44) == 200 && u32(ipv4SynAck, 24) == 300,
                "Flow key collided for peers sharing a source port");
        byte[] query = DnsPolicyTest.query("blocked.example", 1);
        DnsTcpServer.Request first = server.receive(parse(PEER, 11, 101, ACK, frame(query)), 1).requests.get(0);
        DnsTcpServer.Request second = server.receive(parse(peer2, 21, 201, ACK, frame(query)), 1).requests.get(0);
        DnsTcpServer.Request ipv4 = server.receive(
                DnsTcpServerTest.packet(PORT, 31, 301, ACK, 8192, frame(query), 1200), 1).requests.get(0);
        byte[] blocked = new DnsPolicy(host -> DomainPolicy.Decision.BLOCK).evaluate(query).response;
        byte[] output = server.complete(second, blocked, 2).packets.get(0);
        check(Arrays.equals(Arrays.copyOfRange(output, 24, 40), peer2), "Delivered to the wrong peer");
        check(Arrays.equals(Arrays.copyOfRange(output, 60, output.length), frame(blocked)), "Wrong DNS reply");
        check(checksum(output) == 0 && server.isPending(first) && server.isPending(ipv4),
                "IPv6 answer affected another connection");
        server.invalidate();
        check(!server.isPending(first) && !server.isPending(ipv4), "Network change left old flows active");
    }

    private static Ipv6TcpPacket parse(byte[] peer, long seq, long ack, int flags, byte[] payload) {
        return Ipv6TcpPacket.parse(packet(peer, 53, seq, ack, flags, payload), SERVER);
    }

    private static byte[] packet(byte[] peer, int destinationPort, long seq, long ack, int flags, byte[] payload) {
        byte[] packet = new byte[60 + payload.length];
        packet[0] = 0x60;
        packet[6] = 6;
        packet[7] = 64;
        put16(packet, 4, packet.length - 40);
        System.arraycopy(peer, 0, packet, 8, 16);
        System.arraycopy(SERVER, 0, packet, 24, 16);
        put16(packet, 40, PORT);
        put16(packet, 42, destinationPort);
        put32(packet, 44, seq);
        put32(packet, 48, ack);
        packet[52] = 0x50;
        packet[53] = (byte) flags;
        put16(packet, 54, 8192);
        System.arraycopy(payload, 0, packet, 60, payload.length);
        put16(packet, 56, checksum(packet));
        return packet;
    }

    private static int checksum(byte[] packet) {
        int length = packet.length - 40;
        int sum = length + 6;
        for (int i = 8; i < 40; i += 2) sum += u16(packet, i);
        return Ipv4DnsPacket.checksum(packet, 40, length, sum);
    }
}
