package app.hisn.core;

import java.net.IDN;
import java.util.Locale;

/** One hostname grammar for domain rules, DNS enforcement and the read-only site check. */
public final class Hostname {
    /** Canonical ASCII hostname, or throws for input that cannot be a domain name. */
    public static String normalize(String value) {
        if (value == null || value.isEmpty() || value.length() > 1024
                || !value.equals(value.trim())) throw new IllegalArgumentException("Invalid hostname");
        // DNS questions and the signed seed are already lowercase ASCII, so the IDN
        // nameprep pass would return them unchanged at several hundred nanoseconds per
        // name. The fast path accepts only the exact spellings normalize would return
        // as-is; everything else (Unicode, uppercase, trailing dot, hyphen edges) takes
        // the original path below, which keeps the verdict identical.
        if (isCanonicalAscii(value)) return value;
        return normalizeSlow(value);
    }

    /** The reference path: IDN nameprep, case folding and label validation. */
    static String normalizeSlow(String value) {
        String input = value.replace('\u3002', '.').replace('\uff0e', '.').replace('\uff61', '.');
        if (input.endsWith(".")) input = input.substring(0, input.length() - 1);
        if (input.isEmpty() || input.endsWith(".")) throw new IllegalArgumentException("Invalid hostname");
        String ascii = IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (ascii.length() > 253 || !ascii.contains(".")) throw new IllegalArgumentException("Invalid hostname");
        String[] labels = ascii.split("\\.", -1);
        boolean numeric = true;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) throw new IllegalArgumentException("Invalid label");
            for (int i = 0; i < label.length(); i++) {
                if (label.charAt(i) < '0' || label.charAt(i) > '9') numeric = false;
            }
        }
        if (numeric) throw new IllegalArgumentException("IP addresses are not domain names");
        return ascii;
    }

    /** True only for a lowercase ASCII LDH name that {@link #normalizeSlow} would return unchanged. */
    private static boolean isCanonicalAscii(String value) {
        int length = value.length();
        if (length < 3 || length > 253) return false;
        boolean dot = false, numeric = true;
        int labelLength = 0;
        char first = 0;
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c == '.') {
                if (labelLength == 0 || first == '-' || value.charAt(i - 1) == '-') return false;
                dot = true;
                labelLength = 0;
                first = 0;
                continue;
            }
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) return false;
            if (c < '0' || c > '9') numeric = false;
            if (labelLength == 0) first = c;
            if (++labelLength > 63) return false;
        }
        return dot && labelLength > 0 && first != '-' && value.charAt(length - 1) != '-' && !numeric;
    }

    /** True when the value is a canonicalizable hostname. */
    public static boolean valid(String value) {
        try { normalize(value); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private Hostname() { }
}
