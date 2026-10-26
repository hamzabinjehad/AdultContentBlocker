package app.hisn.core;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, DNS-only TCP endpoint for the VPN's virtual IPv4 DNS address.
 *
 * This is deliberately not a general TCP stack: no IP fragments, out-of-order
 * buffering, TCP Fast Open, window scaling, timestamps or SACK are negotiated.
 * Every public operation is serialized; resolver work must happen outside this
 * object and return through complete/fail. All clocks are monotonic milliseconds.
 */
public final class DnsTcpServer {
    private static final long MASK = 0xffff_ffffL;
    private static final int MAX_FLOWS = 16;
    private static final int MAX_DNS = 4096;
    private static final int RECEIVE_CAPACITY = 8192;
    private static final int MAX_QUEUED = 4;
    private static final long HANDSHAKE_MS = 10_000;
    private static final long FRAME_MS = 10_000;
    private static final long REQUEST_MS = 20_000;
    private static final long IDLE_MS = 60_000;
    private static final long TRANSMIT_MS = 25_000;
    private static final int MAX_RETRIES = 4;

    public static final class Request {
        public final byte[] dns;
        private final Flow owner;
        private Request(Flow owner, byte[] dns) {
            this.owner = owner;
            this.dns = dns;
        }
    }

    public static final class Result {
        public final List<byte[]> packets;
        public final List<Request> requests;
        private Result(List<byte[]> packets, List<Request> requests) {
            this.packets = Collections.unmodifiableList(packets);
            this.requests = Collections.unmodifiableList(requests);
        }
    }

    private static final class Output {
        final List<byte[]> packets = new ArrayList<>();
        final List<Request> requests = new ArrayList<>();
        Result result() { return new Result(packets, requests); }
    }

    private static final class Pending {
        long sequence;
        int flags;
        byte[] payload;
        final long firstSent;
        long lastSent;
        int retries;
        Pending(long sequence, int flags, byte[] payload, long now) {
            this.sequence = sequence;
            this.flags = flags;
            this.payload = payload;
            firstSent = lastSent = now;
        }
        int length() {
            return payload.length + ((flags & Ipv4TcpPacket.SYN) != 0 ? 1 : 0)
                    + ((flags & Ipv4TcpPacket.FIN) != 0 ? 1 : 0);
        }
    }

    private static final class Flow {
        final Ipv4TcpPacket template;
        final long clientSyn;
        final long serverSyn;
        final long created;
        final int mss;
        final byte[] receive = new byte[RECEIVE_CAPACITY];
        final ArrayDeque<byte[]> queued = new ArrayDeque<>();
        long receiveNext;
        long sendNext;
        long sendUnacknowledged;
        long lastProgress;
        long frameStarted = -1;
        long requestStarted;
        int used;
        int queuedBytes;
        int peerWindow;
        boolean established;
        boolean receivedFin;
        boolean sentFin;
        Request active;
        Pending pending;
        byte[] response;
        int responseOffset;
        Flow(Ipv4TcpPacket packet, long initial, long now) {
            template = packet;
            clientSyn = packet.sequence;
            serverSyn = initial;
            receiveNext = add(packet.sequence, 1);
            sendUnacknowledged = initial;
            sendNext = add(initial, 1);
            created = lastProgress = now;
            mss = Math.max(1, Math.min(1200, packet.mss));
            peerWindow = packet.window;
        }
        int advertisedWindow() {
            return Math.max(0, RECEIVE_CAPACITY - used - queuedBytes);
        }
    }

    private final java.util.function.LongSupplier sequenceSource;
    private final Map<Long, Flow> flows = new LinkedHashMap<>();
    private boolean closed;

    public DnsTcpServer() {
        SecureRandom random = new SecureRandom();
        sequenceSource = () -> random.nextInt() & MASK;
    }

    DnsTcpServer(java.util.function.LongSupplier sequenceSource) {
        if (sequenceSource == null) throw new IllegalArgumentException("Missing sequence source");
        this.sequenceSource = sequenceSource;
    }

    public synchronized boolean isPending(Request request) { return current(request) != null; }

    /** RFC 9293 reset for a connection this endpoint never accepts, such as port 853.
     * Returns null for an incoming RST, which must never be answered. */
    public static byte[] refuse(Ipv4TcpPacket packet) {
        if (packet == null || (packet.flags & Ipv4TcpPacket.RST) != 0) return null;
        Output output = new Output();
        resetUnknown(packet, output);
        return output.packets.get(0);
    }

    public synchronized Result receive(Ipv4TcpPacket packet, long nowMillis) {
        Output output = new Output();
        if (closed || packet == null) return output.result();
        Flow flow = flows.get(packet.flowId);
        if (flow == null) {
            if ((packet.flags & Ipv4TcpPacket.RST) != 0) return output.result();
            if ((packet.flags & ~0xc0) != Ipv4TcpPacket.SYN || packet.payload.length != 0
                    || flows.size() >= MAX_FLOWS) {
                resetUnknown(packet, output);
                return output.result();
            }
            flow = new Flow(packet, sequenceSource.getAsLong() & MASK, nowMillis);
            flows.put(packet.flowId, flow);
            flow.pending = new Pending(flow.serverSyn, Ipv4TcpPacket.SYN | Ipv4TcpPacket.ACK,
                    new byte[0], nowMillis);
            transmit(flow, flow.pending, output);
            return output.result();
        }

        // An old duplicate SYN must not replace an established connection.
        if ((packet.flags & ~0xc0) == Ipv4TcpPacket.SYN && packet.sequence == flow.clientSyn
                && packet.payload.length == 0) {
            if (!flow.established) transmit(flow, flow.pending, output);
            else acknowledge(flow, output);
            return output.result();
        }
        if ((packet.flags & Ipv4TcpPacket.RST) != 0) {
            // Only an exact in-window sequence can tear down this small endpoint.
            if (packet.sequence == flow.receiveNext) flows.remove(packet.flowId);
            else acknowledge(flow, output);
            return output.result();
        }
        if ((packet.flags & Ipv4TcpPacket.SYN) != 0 || (packet.flags & Ipv4TcpPacket.ACK) == 0) {
            acknowledge(flow, output);
            return output.result();
        }
        if (!flow.established) {
            if (packet.sequence != flow.receiveNext || packet.acknowledgement != flow.sendNext) {
                acknowledge(flow, output);
                return output.result();
            }
            flow.established = true;
        }
        // Reject acknowledgements of bytes never sent; do not accept their data.
        if (difference(packet.acknowledgement, flow.sendNext) > 0) {
            acknowledge(flow, output);
            return output.result();
        }
        if (difference(packet.sequence, flow.receiveNext) > 0) {
            acknowledge(flow, output);
            return output.result();
        }
        // A duplicate sequence may acknowledge old data, but must not inject a
        // stale window update or cause a queued DNS request to execute twice.
        if (packet.sequence == flow.receiveNext) flow.peerWindow = packet.window;
        acceptAcknowledgement(flow, packet.acknowledgement, nowMillis);
        if (flow.sentFin && flow.pending == null) {
            flows.remove(packet.flowId);
            return output.result();
        }

        long overlap = -difference(packet.sequence, flow.receiveNext);
        int skip = (int) Math.min(overlap, packet.payload.length);
        int fresh = packet.payload.length - skip;
        boolean needsAck = packet.payload.length != 0 || (packet.flags & Ipv4TcpPacket.FIN) != 0;
        if (fresh != 0) {
            if (flow.receivedFin || fresh > flow.advertisedWindow()
                    || fresh > RECEIVE_CAPACITY - flow.used) {
                reset(flow, output);
                return output.result();
            }
            if (flow.used == 0) flow.frameStarted = nowMillis;
            System.arraycopy(packet.payload, skip, flow.receive, flow.used, fresh);
            flow.used += fresh;
            flow.receiveNext = add(flow.receiveNext, fresh);
            flow.lastProgress = nowMillis;
            if (!extractFrames(flow, nowMillis, output)) {
                reset(flow, output);
                return output.result();
            }
        }
        if ((packet.flags & Ipv4TcpPacket.FIN) != 0) {
            long finSequence = add(packet.sequence, packet.payload.length);
            if (!flow.receivedFin && finSequence == flow.receiveNext) {
                if (flow.used != 0) {
                    reset(flow, output);
                    return output.result();
                }
                flow.receivedFin = true;
                flow.receiveNext = add(flow.receiveNext, 1);
                flow.lastProgress = nowMillis;
            }
        }
        int before = output.packets.size();
        pump(flow, nowMillis, output);
        if (needsAck && before == output.packets.size()) acknowledge(flow, output);
        return output.result();
    }

    public synchronized Result complete(Request request, byte[] dnsReply, long nowMillis) {
        Output output = new Output();
        Flow flow = current(request);
        if (flow == null) return output.result();
        if (dnsReply == null || dnsReply.length < 12 || dnsReply.length > MAX_DNS) {
            reset(flow, output);
            return output.result();
        }
        flow.active = null;
        flow.response = new byte[dnsReply.length + 2];
        flow.response[0] = (byte) (dnsReply.length >>> 8);
        flow.response[1] = (byte) dnsReply.length;
        System.arraycopy(dnsReply, 0, flow.response, 2, dnsReply.length);
        flow.responseOffset = 0;
        flow.lastProgress = nowMillis;
        pump(flow, nowMillis, output);
        return output.result();
    }

    public synchronized Result fail(Request request, long nowMillis) {
        Output output = new Output();
        Flow flow = current(request);
        if (flow != null) reset(flow, output);
        return output.result();
    }

    /** Periodic maintenance; call about once per second while the VPN runs. */
    public synchronized Result poll(long nowMillis) {
        Output output = new Output();
        if (closed) return output.result();
        Iterator<Flow> iterator = flows.values().iterator();
        while (iterator.hasNext()) {
            Flow flow = iterator.next();
            Pending pending = flow.pending;
            if ((!flow.established && elapsed(nowMillis, flow.created) >= HANDSHAKE_MS)
                    || (flow.frameStarted >= 0 && elapsed(nowMillis, flow.frameStarted) >= FRAME_MS)
                    || (flow.active != null && elapsed(nowMillis, flow.requestStarted) >= REQUEST_MS)
                    || elapsed(nowMillis, flow.lastProgress) >= IDLE_MS
                    || (pending != null && elapsed(nowMillis, pending.firstSent) >= TRANSMIT_MS)) {
                output.packets.add(flow.template.response(flow.sendNext, flow.receiveNext,
                        Ipv4TcpPacket.RST | Ipv4TcpPacket.ACK, 0, new byte[0]));
                iterator.remove();
                continue;
            }
            if (pending != null && elapsed(nowMillis, pending.lastSent)
                    >= (1000L << Math.min(3, pending.retries))) {
                if (pending.retries >= MAX_RETRIES) {
                    output.packets.add(flow.template.response(flow.sendNext, flow.receiveNext,
                            Ipv4TcpPacket.RST | Ipv4TcpPacket.ACK, 0, new byte[0]));
                    iterator.remove();
                    continue;
                }
                // A zero window never causes a busy retry loop. The fixed
                // transmit/idle deadline still eventually releases the flow.
                if (pending.payload.length == 0 || flow.peerWindow > 0) {
                    transmit(flow, pending, output);
                    pending.lastSent = nowMillis;
                    pending.retries++;
                }
            }
        }
        return output.result();
    }

    /** Drop flow generations on network changes without terminating the endpoint. */
    public synchronized void invalidate() { flows.clear(); }

    public synchronized void close() {
        closed = true;
        flows.clear();
    }

    private Flow current(Request request) {
        if (closed || request == null) return null;
        Flow flow = flows.get(request.owner.template.flowId);
        return flow == request.owner && flow.active == request ? flow : null;
    }

    private boolean extractFrames(Flow flow, long now, Output output) {
        int consumed = 0;
        while (flow.used - consumed >= 2) {
            int length = ((flow.receive[consumed] & 255) << 8) | (flow.receive[consumed + 1] & 255);
            if (length < 12 || length > MAX_DNS) return false;
            if (flow.used - consumed < length + 2) break;
            byte[] dns = Arrays.copyOfRange(flow.receive, consumed + 2, consumed + 2 + length);
            consumed += length + 2;
            if (flow.active == null && flow.response == null && flow.pending == null) {
                start(flow, dns, now, output);
            } else {
                if (flow.queued.size() >= MAX_QUEUED) return false;
                flow.queued.addLast(dns);
                flow.queuedBytes += dns.length + 2;
            }
        }
        if (consumed != 0) {
            flow.used -= consumed;
            System.arraycopy(flow.receive, consumed, flow.receive, 0, flow.used);
            // A new trailing partial frame starts a new fixed deadline; old
            // partial bytes never renew theirs merely because more arrive.
            flow.frameStarted = flow.used == 0 ? -1 : now;
        }
        return true;
    }

    private void start(Flow flow, byte[] dns, long now, Output output) {
        flow.active = new Request(flow, dns);
        flow.requestStarted = now;
        output.requests.add(flow.active);
    }

    private void pump(Flow flow, long now, Output output) {
        if (!flow.established || flow.pending != null) return;
        if (flow.response != null) {
            if (flow.responseOffset == flow.response.length) {
                flow.response = null;
                flow.responseOffset = 0;
            } else {
                if (flow.peerWindow == 0) return;
                int size = Math.min(Math.min(flow.mss, flow.peerWindow),
                        flow.response.length - flow.responseOffset);
                byte[] part = Arrays.copyOfRange(flow.response, flow.responseOffset,
                        flow.responseOffset + size);
                flow.responseOffset += size;
                int flags = Ipv4TcpPacket.ACK;
                if (flow.responseOffset == flow.response.length) flags |= Ipv4TcpPacket.PSH;
                send(flow, flags, part, now, output);
                return;
            }
        }
        if (flow.active == null && !flow.queued.isEmpty()) {
            byte[] dns = flow.queued.removeFirst();
            flow.queuedBytes -= dns.length + 2;
            start(flow, dns, now, output);
        }
        if (flow.receivedFin && flow.active == null && flow.queued.isEmpty()
                && flow.response == null && !flow.sentFin && flow.peerWindow > 0) {
            flow.sentFin = true;
            send(flow, Ipv4TcpPacket.FIN | Ipv4TcpPacket.ACK, new byte[0], now, output);
        }
    }

    private void send(Flow flow, int flags, byte[] payload, long now, Output output) {
        Pending pending = new Pending(flow.sendNext, flags, payload, now);
        flow.pending = pending;
        flow.sendNext = add(flow.sendNext, pending.length());
        transmit(flow, pending, output);
    }

    private void transmit(Flow flow, Pending pending, Output output) {
        if (pending == null) return;
        byte[] payload = pending.payload;
        int flags = pending.flags;
        if (payload.length != 0 && payload.length > flow.peerWindow) {
            if (flow.peerWindow == 0) return;
            payload = Arrays.copyOf(payload, flow.peerWindow);
            flags &= ~(Ipv4TcpPacket.FIN | Ipv4TcpPacket.PSH);
        }
        output.packets.add(flow.template.response(pending.sequence, flow.receiveNext,
                flags, flow.advertisedWindow(), payload));
    }

    private void acceptAcknowledgement(Flow flow, long acknowledgement, long now) {
        if (difference(acknowledgement, flow.sendUnacknowledged) <= 0) return;
        flow.sendUnacknowledged = acknowledgement;
        flow.lastProgress = now;
        Pending pending = flow.pending;
        if (pending == null) return;
        int accepted = (int) difference(acknowledgement, pending.sequence);
        if (accepted >= pending.length()) {
            flow.pending = null;
        } else if (accepted > 0 && (pending.flags & Ipv4TcpPacket.SYN) == 0) {
            int dataAccepted = Math.min(accepted, pending.payload.length);
            pending.payload = Arrays.copyOfRange(pending.payload, dataAccepted, pending.payload.length);
            pending.sequence = acknowledgement;
            pending.lastSent = now;
        }
    }

    private void acknowledge(Flow flow, Output output) {
        output.packets.add(flow.template.response(flow.sendNext, flow.receiveNext,
                Ipv4TcpPacket.ACK, flow.advertisedWindow(), new byte[0]));
    }

    private void reset(Flow flow, Output output) {
        flows.remove(flow.template.flowId);
        output.requests.removeIf(request -> request.owner == flow);
        output.packets.add(flow.template.response(flow.sendNext, flow.receiveNext,
                Ipv4TcpPacket.RST | Ipv4TcpPacket.ACK, 0, new byte[0]));
    }

    private static void resetUnknown(Ipv4TcpPacket packet, Output output) {
        boolean ack = (packet.flags & Ipv4TcpPacket.ACK) != 0;
        long sequence = ack ? packet.acknowledgement : 0;
        long acknowledgement = ack ? 0 : add(packet.sequence, packet.payload.length
                + ((packet.flags & Ipv4TcpPacket.SYN) != 0 ? 1 : 0)
                + ((packet.flags & Ipv4TcpPacket.FIN) != 0 ? 1 : 0));
        output.packets.add(packet.response(sequence, acknowledgement,
                Ipv4TcpPacket.RST | (ack ? 0 : Ipv4TcpPacket.ACK), 0, new byte[0]));
    }

    private static long add(long value, long count) { return (value + count) & MASK; }

    /** Signed serial-number distance; all live windows are far below 2^31. */
    private static long difference(long a, long b) { return (int) (a - b); }

    private static long elapsed(long now, long then) { return Math.max(0, now - then); }
}
