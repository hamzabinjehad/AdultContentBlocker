package app.hisn.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounds-checks a reply and follows reachable CNAME, DNAME and HTTPS/SVCB aliases. */
final class DnsReplyGuard {
    static boolean blocks(byte[] reply, DnsPolicy.Rules rules) throws IOException {
        if (reply == null || reply.length < 17 || reply.length > 4096 || u16(reply, 4) != 1) throw invalid();
        Name question = name(reply, 12);
        int cursor = question.end + 4;
        if (cursor > reply.length) throw invalid();
        int count = u16(reply, 6) + u16(reply, 8) + u16(reply, 10);
        if (count > 256) throw invalid();
        Map<String, String> aliases = new HashMap<>();
        Map<String, String> subtrees = new HashMap<>();
        for (int i = 0; i < count; i++) {
            Name owner = name(reply, cursor);
            cursor = owner.end;
            if (cursor + 10 > reply.length) throw invalid();
            int type = u16(reply, cursor);
            int clazz = u16(reply, cursor + 2);
            int length = u16(reply, cursor + 8);
            cursor += 10;
            int end = cursor + length;
            if (end > reply.length) throw invalid();
            if (clazz == 1 && (type == 5 || type == 39)) {
                Name target = name(reply, cursor);
                if (target.end != end || target.value.isEmpty() || owner.value.isEmpty()) throw invalid();
                Map<String, String> map = type == 39 ? subtrees : aliases;
                String old = map.put(owner.value, target.value);
                if (old != null && !old.equals(target.value)) throw invalid();
            } else if (clazz == 1 && (type == 64 || type == 65)) {
                if (length < 3) throw invalid();
                Name target = name(reply, cursor + 2);
                if (target.end > end) throw invalid();
                if (u16(reply, cursor) == 0 && !target.value.isEmpty()) {
                    String old = aliases.put(owner.value, target.value);
                    if (old != null && !old.equals(target.value)) throw invalid();
                }
            } else if (clazz == 1 && ((type == 1 && length != 4) || (type == 28 && length != 16))) {
                throw invalid();
            }
            cursor = end;
        }
        if (cursor != reply.length) throw invalid();
        Set<String> seen = new HashSet<>();
        String current = question.value;
        for (int depth = 0; depth < 32; depth++) {
            if (!seen.add(current)) throw invalid();
            if (rules.evaluate(current) == DomainPolicy.Decision.BLOCK) return true;
            String next = aliases.get(current);
            if (next == null) {
                // Prefer the longest DNAME owner suffix.
                int dot = current.indexOf('.');
                while (dot >= 0) {
                    String replacement = subtrees.get(current.substring(dot + 1));
                    if (replacement != null) { next = current.substring(0, dot + 1) + replacement; break; }
                    dot = current.indexOf('.', dot + 1);
                }
            }
            if (next == null) return false;
            if (next.length() > 253) throw invalid();
            current = next;
        }
        throw invalid();
    }

    private static Name name(byte[] data, int start) throws IOException {
        int cursor = start, end = -1, wireLength = 1;
        StringBuilder name = new StringBuilder();
        for (int hops = 0; hops < 128; hops++) {
            if (cursor < 0 || cursor >= data.length) throw invalid();
            int size = data[cursor] & 255;
            if ((size & 0xc0) == 0xc0) {
                if (cursor + 1 >= data.length) throw invalid();
                int target = ((size & 63) << 8) | (data[cursor + 1] & 255);
                if (target < 12 || target >= cursor) throw invalid();
                if (end < 0) end = cursor + 2;
                cursor = target;
            } else {
                cursor++;
                if (size == 0) return new Name(name.toString().toLowerCase(Locale.ROOT), end < 0 ? cursor : end);
                wireLength += size + 1;
                if (size > 63 || wireLength > 255 || cursor + size > data.length) throw invalid();
                if (name.length() > 0) name.append('.');
                for (int i = 0; i < size; i++) {
                    int value = data[cursor++] & 255;
                    if (value < 33 || value > 126 || value == '.') throw invalid();
                    name.append((char) value);
                }
            }
        }
        throw invalid();
    }

    private static int u16(byte[] data, int offset) { return ((data[offset] & 255) << 8) | (data[offset + 1] & 255); }
    private static IOException invalid() { return new IOException("Invalid or unsupported DNS reply structure"); }
    private static final class Name {
        final String value; final int end;
        Name(String value, int end) { this.value = value; this.end = end; }
    }
}
