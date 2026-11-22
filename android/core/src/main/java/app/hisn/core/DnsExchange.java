package app.hisn.core;

import java.io.IOException;
import java.security.SecureRandom;

/** Applies policy before forwarding a DNS message. No queries or answers are retained. */
public final class DnsExchange {
    public static final class Resolution {
        public final byte[] answer;
        public final boolean blocked;
        public final boolean rewrite;
        /** Offset just past the validated question, so the caller can read the client's
         * EDNS ceiling without parsing and evaluating the same query a second time. */
        public final int questionEnd;
        Resolution(byte[] answer, boolean blocked, int questionEnd) {
            this(answer, blocked, false, questionEnd);
        }
        Resolution(byte[] answer, boolean blocked, boolean rewrite, int questionEnd) {
            this.answer = answer;
            this.blocked = blocked;
            this.rewrite = rewrite;
            this.questionEnd = questionEnd;
        }
    }
    public interface Transport {
        byte[] exchange(byte[] query) throws IOException;
    }

    /** One validated request. Local answers are available before any transport is scheduled. */
    public static final class PreparedQuery {
        private final DnsExchange owner;
        private final DnsPolicy.Question question;
        private final DnsPolicy.Result result;

        private PreparedQuery(DnsExchange owner, DnsPolicy.Question question, DnsPolicy.Result result) {
            this.owner = owner;
            this.question = question;
            this.result = result;
        }

        public Resolution localAnswer() {
            return result.action == DnsPolicy.Action.FORWARD ? null : local(result);
        }

        /** Preserves only the already validated question and optional OPT. */
        public byte[] failureAnswer() {
            return DnsPolicy.response(question.wire, question.end, 2);
        }

        /** Preserves the question and asks the UDP client to retry over TCP. */
        public byte[] truncatedAnswer() {
            return DnsPolicy.truncated(question.wire, question.end);
        }

        public int udpResponseLimit() {
            return owner.policy.udpResponseLimit(question.wire, question.end);
        }
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
        PreparedQuery prepared = prepare(query);
        return resolve(prepared.question, prepared.result);
    }

    public PreparedQuery prepare(byte[] query) throws IOException {
        if (query == null) throw new IOException("Missing DNS request");
        if (query.length < 17 || query.length > 4096) throw new IOException("Unsupported DNS request");
        DnsPolicy.Question question = DnsPolicy.parse(query.clone());
        return new PreparedQuery(this, question, supportedResult(question));
    }

    /**
     * An allowed query may have waited for a resolver worker. Apply current rules again
     * before sending it upstream, using the same validated wire and question.
     */
    public Resolution resolvePrepared(PreparedQuery prepared) throws IOException {
        if (prepared == null || prepared.owner != this) {
            throw new IllegalArgumentException("DNS request belongs to a different exchange");
        }
        DnsPolicy.Question question = prepared.question;
        DnsPolicy.Result result = prepared.result;
        if (result.action == DnsPolicy.Action.FORWARD) result = supportedResult(question);
        return resolve(question, result);
    }

    private DnsPolicy.Result supportedResult(DnsPolicy.Question question) throws IOException {
        DnsPolicy.Result result = policy.evaluateQuestion(question);
        if (result.action == DnsPolicy.Action.UNSUPPORTED) throw new IOException("Unsupported DNS request");
        return result;
    }

    private static Resolution local(DnsPolicy.Result result) {
        return new Resolution(result.response, result.action == DnsPolicy.Action.BLOCK,
                result.action == DnsPolicy.Action.REWRITE, result.questionEnd);
    }

    private Resolution resolve(DnsPolicy.Question question, DnsPolicy.Result result) throws IOException {
        if (result.action != DnsPolicy.Action.FORWARD) return local(result);
        byte[] request = question.wire;
        byte[] outbound = request.clone();
        int upstreamId = random.nextInt(65536);
        byte high = (byte) (upstreamId >>> 8), low = (byte) upstreamId;
        outbound[0] = high;
        outbound[1] = low;
        // The transport sees its own buffer. Validate against the original question and ID.
        byte[] response = transport.exchange(outbound);
        if (response == null || response.length < result.questionEnd || response.length > 4096
                || response[0] != high || response[1] != low
                || (response[2] & 0xf8) != 0x80 || (response[2] & 2) != 0
                || response[4] != 0 || response[5] != 1) {
            throw new IOException("Invalid or truncated DNS reply");
        }
        for (int i = 12; i < result.questionEnd; i++) {
            if (response[i] != request[i]) throw new IOException("DNS question mismatch");
        }
        if (policy.blocksReply(response))
            return new Resolution(DnsPolicy.response(request, question.end, 3), true, result.questionEnd);
        byte[] answer = response.clone();
        answer[0] = request[0];
        answer[1] = request[1];
        return new Resolution(answer, false, result.questionEnd);
    }
}
