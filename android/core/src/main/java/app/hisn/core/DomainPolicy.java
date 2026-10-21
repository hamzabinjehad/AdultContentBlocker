package app.hisn.core;

import java.net.IDN;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Domain-only policy; this does not capture or block network traffic. */
public final class DomainPolicy {
    public enum Decision { BLOCK, ALLOW, INVALID }
    private static final int MAX_RULES = 250000;
    private final Set<String> blocked;
    private final Set<String> allowed;

    public Set<String> blockedRules() { return blocked; }

    public DomainPolicy(Iterable<String> blocked, Iterable<String> allowed) {
        this.blocked = compile(blocked);
        this.allowed = compile(allowed);
    }

    /** Invalid input is distinct from a valid name that is allowed. */
    public Decision evaluate(String hostname) {
        final String name;
        try {
            name = normalize(hostname);
        } catch (IllegalArgumentException invalid) {
            return Decision.INVALID;
        }
        return !containsParent(allowed, name) && containsParent(blocked, name)
                ? Decision.BLOCK : Decision.ALLOW;
    }

    /** Convenience for callers that already handle invalid names separately. */
    public boolean blocks(String hostname) {
        return evaluate(hostname) == Decision.BLOCK;
    }

    private static Set<String> compile(Iterable<String> rules) {
        if (rules == null) throw new IllegalArgumentException("Missing rules");
        Set<String> result = new HashSet<>();
        int count = 0;
        for (String rule : rules) {
            if (++count > MAX_RULES) throw new IllegalArgumentException("Too many rules");
            result.add(normalize(rule));
        }
        return Collections.unmodifiableSet(result);
    }

    private static boolean containsParent(Set<String> rules, String name) {
        String current = name;
        while (true) {
            if (rules.contains(current)) return true;
            int dot = current.indexOf('.');
            if (dot < 0) return false;
            current = current.substring(dot + 1);
        }
    }

    private static String normalize(String value) {
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
        if (numeric) throw new IllegalArgumentException("IP addresses are not domain rules");
        return ascii;
    }
}
