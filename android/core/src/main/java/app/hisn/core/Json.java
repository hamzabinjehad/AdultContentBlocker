package app.hisn.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, bounded JSON for signed update manifests and the portable personal-rule
 * document. It rejects duplicate keys, trailing input, comments, malformed escapes and
 * nesting that is deeper or larger than the schemas allow. The parser is small on
 * purpose: these documents are verified byte-for-byte before they are read.
 */
public final class Json {
    public static final int MAX_DEPTH = 32;
    public static final int MAX_ENTRIES = 100_000;
    public static final int MAX_STRING = 1_000_000;

    public static final class Invalid extends IllegalArgumentException {
        public Invalid(String message) { super(message); }
    }

    public static Object parse(String text) {
        if (text == null) throw new Invalid("missing json");
        Parser parser = new Parser(text);
        Object value = parser.value(0);
        parser.space();
        if (!parser.atEnd()) throw new Invalid("trailing input");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new Invalid("not an object");
        return (Map<String, Object>) value;
    }

    /** Deterministic serialization for the add-only rule document; keys keep insertion order. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) throw new Invalid("too deep");
        if (value == null) { out.append("null"); return; }
        if (value instanceof String) { quote((String) value, out); return; }
        if (value instanceof Boolean || value instanceof Long || value instanceof Integer) {
            out.append(value); return;
        }
        if (value instanceof Double) {
            double number = (Double) value;
            if (!Double.isFinite(number)) throw new Invalid("non-finite number");
            out.append(number); return;
        }
        if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) throw new Invalid("non-string key");
                if (!first) out.append(',');
                first = false;
                quote((String) entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out, depth + 1);
            }
            out.append('}');
            return;
        }
        if (value instanceof List) {
            out.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) out.append(',');
                first = false;
                write(item, out, depth + 1);
            }
            out.append(']');
            return;
        }
        throw new Invalid("unsupported value");
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int at;

        Parser(String text) { this.text = text; }

        boolean atEnd() { return at >= text.length(); }

        void space() {
            while (at < text.length()) {
                char c = text.charAt(at);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') at++;
                else break;
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw new Invalid("too deep");
            space();
            if (atEnd()) throw new Invalid("missing value");
            char c = text.charAt(at);
            switch (c) {
                case '{': return object(depth + 1);
                case '[': return array(depth + 1);
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw new Invalid("unexpected character");
            }
        }

        private Map<String, Object> object(int depth) {
            at++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            space();
            if (peek('}')) { at++; return map; }
            while (true) {
                space();
                if (!peek('"')) throw new Invalid("object key");
                String key = string();
                if (map.containsKey(key)) throw new Invalid("duplicate key");
                space();
                if (!peek(':')) throw new Invalid("missing colon");
                at++;
                map.put(key, value(depth));
                if (map.size() > MAX_ENTRIES) throw new Invalid("too many entries");
                space();
                if (peek('}')) { at++; return map; }
                if (!peek(',')) throw new Invalid("missing comma");
                at++;
            }
        }

        private List<Object> array(int depth) {
            at++; // [
            List<Object> list = new ArrayList<>();
            space();
            if (peek(']')) { at++; return list; }
            while (true) {
                list.add(value(depth));
                if (list.size() > MAX_ENTRIES) throw new Invalid("too many entries");
                space();
                if (peek(']')) { at++; return list; }
                if (!peek(',')) throw new Invalid("missing comma");
                at++;
            }
        }

        private String string() {
            at++; // "
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw new Invalid("control character");
                if (c != '\\') { out.append(c); continue; }
                if (at >= text.length()) throw new Invalid("truncated escape");
                char escape = text.charAt(at++);
                switch (escape) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (at + 4 > text.length()) throw new Invalid("truncated unicode");
                        String digits = text.substring(at, at + 4);
                        if (!digits.matches("[0-9a-fA-F]{4}")) throw new Invalid("unicode escape");
                        out.append((char) Integer.parseInt(digits, 16));
                        at += 4;
                        break;
                    default: throw new Invalid("invalid escape");
                }
                if (out.length() > MAX_STRING) throw new Invalid("string too long");
            }
            throw new Invalid("unterminated string");
        }

        private Object number() {
            int start = at;
            if (peek('-')) at++;
            if (atEnd()) throw new Invalid("number");
            if (peek('0')) {
                at++;
                if (!atEnd() && Character.isDigit(text.charAt(at))) throw new Invalid("leading zero");
            } else {
                if (!Character.isDigit(text.charAt(at))) throw new Invalid("number");
                while (!atEnd() && Character.isDigit(text.charAt(at))) at++;
            }
            boolean decimal = false;
            if (!atEnd() && text.charAt(at) == '.') {
                decimal = true;
                at++;
                if (atEnd() || !Character.isDigit(text.charAt(at))) throw new Invalid("fraction");
                while (!atEnd() && Character.isDigit(text.charAt(at))) at++;
            }
            if (!atEnd() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                decimal = true;
                at++;
                if (!atEnd() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++;
                if (atEnd() || !Character.isDigit(text.charAt(at))) throw new Invalid("exponent");
                while (!atEnd() && Character.isDigit(text.charAt(at))) at++;
            }
            String raw = text.substring(start, at);
            try {
                if (!decimal) return Long.parseLong(raw);
            } catch (NumberFormatException overflow) {
                // Fall through to a double only for values outside the long range.
            }
            try { return Double.parseDouble(raw); }
            catch (NumberFormatException invalid) { throw new Invalid("number"); }
        }

        private void literal(String expected) {
            if (!text.startsWith(expected, at)) throw new Invalid("literal");
            at += expected.length();
        }

        private boolean peek(char c) { return at < text.length() && text.charAt(at) == c; }
    }

    private Json() { }
}
