package app.hisn.core;

import java.net.IDN;
import java.util.Locale;

/** One hostname grammar for domain rules, DNS enforcement and the read-only site check. */
public final class Hostname {
    /** Canonical ASCII hostname, or throws for input that cannot be a domain name. */
    public static String normalize(String value) {
        if (value == null || value.isEmpty() || value.length() > 1024
                || !value.equals(value.trim())) throw new IllegalArgumentException("Invalid hostname");
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

    /** True when the value is a canonicalizable hostname. */
    public static boolean valid(String value) {
        try { normalize(value); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private Hostname() { }
}
