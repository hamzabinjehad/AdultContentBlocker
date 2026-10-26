package app.hisn.core;

import java.util.Arrays;

/** Offline DNS decision prototype. It does not open sockets or route packets. */
public final class DnsPolicy {
    public interface Rules { DomainPolicy.Decision evaluate(String hostname); }
    public enum Action { BLOCK, FORWARD, UNSUPPORTED }

    public static final class Result {
        public final Action action;
        /** Only present for a BLOCK decision. */
        public final byte[] response;
        final int questionEnd;
        private Result(Action action, byte[] response, int questionEnd) {
            this.action = action;
            this.response = response;
            this.questionEnd = questionEnd;
        }
    }

    private final Rules domains;

    public DnsPolicy(DomainPolicy domains) {
        this(domains == null ? null : domains::evaluate);
    }

    public DnsPolicy(Rules domains) {
        if (domains == null) throw new IllegalArgumentException("Missing domain policy");
        this.domains = domains;
    }

    /** Handles one uncompressed, standard, single-question IN query with an optional EDNS(0) OPT.
     * Anything else is explicitly unsupported, not treated as safe or blocked. */
    public Result evaluate(byte[] query) {
        if (query == null || query.length < 17 || query.length > 4096) return unsupported();
        int flags = read16(query, 2);
        int additional = read16(query, 10);
        if ((flags & ~0x0130) != 0 || read16(query, 4) != 1 || read16(query, 6) != 0
                || read16(query, 8) != 0 || additional > 1) return unsupported();
        int offset = 12;
        StringBuilder name = new StringBuilder();
        int wireLength = 1;
        while (true) {
            if (offset >= query.length) return unsupported();
            int size = query[offset++] & 0xff;
            if (size == 0) break;
            if (size > 63 || offset + size > query.length) return unsupported();
            wireLength += size + 1;
            if (wireLength > 255) return unsupported();
            if (name.length() > 0) name.append('.');
            for (int i = 0; i < size; i++) {
                int b = query[offset++] & 0xff;
                if (b < 33 || b > 126 || b == '.') return unsupported();
                name.append((char) b);
            }
        }
        if (offset + 4 > query.length || read16(query, offset + 2) != 1) return unsupported();
        int questionEnd = offset + 4;
        if (additional == 0) {
            if (questionEnd != query.length || query.length > 512) return unsupported();
        } else if (!validOpt(query, questionEnd)) {
            return unsupported();
        }
        DomainPolicy.Decision decision = decide(name.toString());
        if (decision == DomainPolicy.Decision.INVALID) return unsupported();
        if (decision == DomainPolicy.Decision.ALLOW) return new Result(Action.FORWARD, null, questionEnd);
        return new Result(Action.BLOCK, response(query, questionEnd, 3), questionEnd);
    }

    /** A supported query whose resolver failed receives SERVFAIL, never an unfiltered fallback. */
    public byte[] failureResponse(byte[] query) {
        Result parsed = evaluate(query);
        return parsed.action == Action.UNSUPPORTED ? null : response(query, parsed.questionEnd, 2);
    }

    public byte[] blockedResponse(byte[] query) {
        Result parsed = evaluate(query);
        return parsed.action == Action.UNSUPPORTED ? null : response(query, parsed.questionEnd, 3);
    }

    /** Preserve only a validated question/OPT and request a TCP retry, never cut record bytes. */
    public byte[] truncatedResponse(byte[] query) {
        Result parsed = evaluate(query);
        if (parsed.action == Action.UNSUPPORTED) return null;
        byte[] truncated = response(query, parsed.questionEnd, 0);
        truncated[2] |= 2;
        return truncated;
    }

    /** Client-advertised UDP ceiling, capped below the unfragmented tunnel MTU. */
    public int udpResponseLimit(byte[] query) {
        Result parsed = evaluate(query);
        if (parsed.action == Action.UNSUPPORTED) return 0;
        return read16(query, 10) == 0 ? 512
                : Math.max(512, Math.min(1232, read16(query, parsed.questionEnd + 3)));
    }

    // Alias targets get the same underscore/single-label handling as questions.
    boolean blocksReply(byte[] reply) throws java.io.IOException { return DnsReplyGuard.blocks(reply, this::decide); }

    private DomainPolicy.Decision decide(String name) {
        DomainPolicy.Decision decision = domains.evaluate(name);
        return decision == DomainPolicy.Decision.INVALID ? nonHostname(name) : decision;
    }

    /** No domain rule can equal a root, single-label or underscore (SRV, DKIM, DDR) name, so
     * those are forwarded. The hostname after the last underscore label is still checked:
     * _sip._tcp.blocked.example stays blocked. A malformed suffix remains unsupported. */
    private DomainPolicy.Decision nonHostname(String name) {
        int underscore = name.lastIndexOf('_');
        if (underscore < 0) return name.indexOf('.') < 0 ? DomainPolicy.Decision.ALLOW : DomainPolicy.Decision.INVALID;
        int dot = name.indexOf('.', underscore);
        if (dot < 0 || name.indexOf('.', dot + 1) < 0) return DomainPolicy.Decision.ALLOW;
        return domains.evaluate(name.substring(dot + 1));
    }

    private static byte[] response(byte[] query, int questionEnd, int code) {
        int additional = read16(query, 10);
        int flags = read16(query, 2);
        // Do not echo client cookies, padding, or other request-only OPT options.
        byte[] response = Arrays.copyOf(query, questionEnd + (additional == 1 ? 11 : 0));
        if (additional == 1) {
            Arrays.fill(response, questionEnd, response.length, (byte) 0);
            response[questionEnd + 2] = 41;
            int payload = Math.max(512, Math.min(1232, read16(query, questionEnd + 3)));
            response[questionEnd + 3] = (byte) (payload >>> 8);
            response[questionEnd + 4] = (byte) payload;
            response[questionEnd + 7] = (byte) (query[questionEnd + 7] & 0x80);
        }
        // Local error response; preserve recursion desired/checking disabled. No answers.
        int responseFlags = 0x8000 | code | (flags & 0x0110);
        response[2] = (byte) (responseFlags >>> 8);
        response[3] = (byte) responseFlags;
        return response;
    }

    private static boolean validOpt(byte[] query, int offset) {
        if (offset + 11 > query.length || query[offset] != 0 || read16(query, offset + 1) != 41
                || query[offset + 5] != 0 || query[offset + 6] != 0) return false;
        int end = offset + 11 + read16(query, offset + 9);
        if (end != query.length) return false;
        int cursor = offset + 11;
        while (cursor < end) {
            if (cursor + 4 > end) return false;
            int size = read16(query, cursor + 2);
            cursor += 4;
            if (size > end - cursor) return false;
            cursor += size;
        }
        return true;
    }

    private static int read16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static Result unsupported() { return new Result(Action.UNSUPPORTED, null, 0); }
}
