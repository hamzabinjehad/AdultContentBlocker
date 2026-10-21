package app.hisn.core;

import java.io.IOException;
import java.security.SecureRandom;

/** Applies policy before forwarding a DNS message. No queries or answers are retained. */
public final class DnsExchange {
    public static final class Resolution {
        public final byte[] answer;
        public final boolean blocked;
        Resolution(byte[] answer, boolean blocked) { this.answer = answer; this.blocked = blocked; }
    }
    public interface Transport {
        byte[] exchange(byte[] query) throws IOException;
    }

    private final DnsPolicy policy;
    private final Transport transport;
    private final SecureRandom random = new SecureRandom();

    public DnsExchange(DnsPolicy policy, Transport transport) {
        if (policy == null || transport == null) throw new IllegalArgumentException("Missing dependency");
        this.policy = policy;
        this.transport = transport;
    }

    public byte[] resolve(byte[] query) throws IOException {
        return resolveDetailed(query).answer;
    }

    public Resolution resolveDetailed(byte[] query) throws IOException {
        if (query == null) throw new IOException("Missing DNS request");
        byte[] request = query.clone();
        DnsPolicy.Result result = policy.evaluate(request);
        if (result.action == DnsPolicy.Action.UNSUPPORTED) throw new IOException("Unsupported DNS request");
        if (result.action == DnsPolicy.Action.BLOCK) return new Resolution(result.response, true);
        byte originalHigh = request[0], originalLow = request[1];
        int upstreamId = random.nextInt(65536);
        request[0] = (byte) (upstreamId >>> 8);
        request[1] = (byte) upstreamId;
        // A transport must not be able to mutate the validation reference.
        byte[] response = transport.exchange(request.clone());
        if (response == null || response.length < result.questionEnd || response.length > 4096
                || response[0] != request[0] || response[1] != request[1]
                || (response[2] & 0xf8) != 0x80 || (response[2] & 2) != 0
                || response[4] != 0 || response[5] != 1) {
            throw new IOException("Invalid or truncated DNS reply");
        }
        for (int i = 12; i < result.questionEnd; i++) {
            if (response[i] != request[i]) throw new IOException("DNS question mismatch");
        }
        if (policy.blocksReply(response)) return new Resolution(policy.blockedResponse(query), true);
        byte[] answer = response.clone();
        answer[0] = originalHigh;
        answer[1] = originalLow;
        return new Resolution(answer, false);
    }
}
