package app.hisn.core;

import java.net.IDN;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The Android side of the hostname keyword contract, the layer macOS and the
 * browser extension already have.
 *
 * A blocklist can only block a domain after someone has added it. A hostname
 * that visibly names adult material — `arab-sex-tube`, `sharmota123`,
 * `3ahira-arab` — is the threat model's "new domain, registered today" gap, and
 * this is the layer that closes it for apps whose traffic Hisn's DNS resolver
 * sees. It is the same rule as `macos/Hisn/BlocklistStore.swift` and
 * `extension/rules/dnr_keyword_rules.json`; `blocklist/terms/host_cases.json`
 * is the shared contract and {@code HostTermPolicyTest} asserts it.
 *
 * Safety is the whole design. A wrong verdict takes out an entire site, not one
 * page, so:
 *   * ambiguous terms are `token` terms and must equal a whole hostname label
 *     token (split on dot, hyphen and underscore, with a trailing digit run
 *     stripped), so `essex.gov.uk` never equals `sex`;
 *   * `never_keyword` tokens are REMOVED from the haystack before a substring
 *     test, so `milford.ct.us` does not match `milf` while
 *     `milford-milfs.example` still does;
 *   * `keyword_exempt_hosts` names a whole site and its subdomains, so JINS
 *     eyewear and the island of Kos stay reachable.
 *
 * Nothing here stores, reports or logs what it saw.
 */
public final class HostTermPolicy {
    /** Bounded like the signed payload the caller validates. */
    public static final int MAX_TERMS = 1000;
    private static final int MAX_HOST_LENGTH = 253;
    private static final String[] PREFIXES = {
            "\u0648\u0628\u0627\u0644", "\u0641\u0628\u0627\u0644", "\u0648\u0627\u0644",
            "\u0628\u0627\u0644", "\u0641\u0627\u0644", "\u0643\u0627\u0644", "\u0644\u0644",
            "\u0627\u0644", "\u0648", "\u0641", "\u0628", "\u0643", "\u0644"};
    private static final String[] SUFFIXES = {
            "\u0627\u062A", "\u0648\u0646", "\u064A\u0646", "\u0647\u0627",
            "\u0647\u0645", "\u0647\u0646", "\u0643", "\u064A"};
    private final Set<String> tokens;
    private final List<String> substrings;
    private final Set<String> neverKeyword;
    private final Set<String> exemptHosts;
    /** Repeated DNS questions skip folding and regex work; bounded so a hostile stream cannot grow it. */
    private final ConcurrentHashMap<String, Boolean> decisions = new ConcurrentHashMap<>();
    private static final int DECISION_CACHE_LIMIT = 8192;
    // Compiled once. String.replaceAll/matches compile their pattern on every call, and
    // decide() runs these for every token of every previously unseen hostname.
    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
    private static final Pattern TAIL_DIGITS = Pattern.compile("[0-9]+$");
    private static final Pattern REPEATS = Pattern.compile("(.)\\1{2,}");

    public HostTermPolicy(Collection<String> tokens, Collection<String> substrings,
            Collection<String> neverKeyword, Collection<String> exemptHosts) {
        this.tokens = normalizeSet(tokens, "token");
        List<String> built = new ArrayList<>();
        if (substrings != null) {
            for (String raw : substrings) {
                String term = normalizeTerm(raw);
                if (term.isEmpty()) throw new IllegalArgumentException("Empty host substring");
                built.add(term);
            }
        }
        if (built.size() > MAX_TERMS) throw new IllegalArgumentException("Too many host substrings");
        this.substrings = Collections.unmodifiableList(built);
        this.neverKeyword = normalizeSet(neverKeyword, "never keyword");
        this.exemptHosts = normalizeHostSet(exemptHosts);
        if (this.tokens.isEmpty() && this.substrings.isEmpty())
            throw new IllegalArgumentException("Host policy has no terms");
    }

    /** True when the hostname visibly names adult material, by the reviewed rules above. */
    public boolean matches(String hostname) {
        String key = hostname == null ? "" : hostname;
        Boolean cached = decisions.get(key);
        if (cached != null) return cached;
        boolean result = decide(hostname);
        if (decisions.size() >= DECISION_CACHE_LIMIT) decisions.clear();
        decisions.put(key, result);
        return result;
    }

    private boolean decide(String hostname) {
        String host = canonicalHost(hostname);
        if (host.isEmpty()) return false;
        if (isExempt(host)) return false;

        String folded = fold(host);
        if (folded.isEmpty()) return false;
        Set<String> found = new HashSet<>();
        for (String token : tokenize(folded)) found.addAll(variants(token));

        boolean innocent = false;
        for (String token : found) {
            if (neverKeyword.contains(token)) { innocent = true; continue; }
            if (tokens.contains(token)) return true;
        }

        // An innocent token does not immunise the rest of the host: only the
        // innocent spelling is removed, exactly as the Mac and the browser do.
        String haystack = folded;
        if (innocent) {
            for (String token : found) {
                if (neverKeyword.contains(token)) haystack = haystack.replace(token, "");
            }
        }
        for (String substring : substrings) {
            if (haystack.contains(substring)) return true;
        }
        return false;
    }

    public int tokenCount() { return tokens.size(); }
    public int substringCount() { return substrings.size(); }

    private boolean isExempt(String host) {
        // Only a handful of whole sites are exempt. Compare each one directly instead
        // of materializing every dotted suffix of every new hostname.
        for (String exempt : exemptHosts) {
            if (host.equals(exempt)) return true;
            int at = host.length() - exempt.length();
            if (at > 0 && host.charAt(at - 1) == '.'
                    && host.regionMatches(at, exempt, 0, exempt.length())) return true;
        }
        return false;
    }

    /**
     * Lowercase, IDN-decoded, trailing dot removed. DNS names are already ASCII
     * on the wire; decoding the punycode gives the Arabic-script spelling the
     * signed terms are written in, exactly as `Punycode.decodeHost` does on the
     * Mac.
     */
    static String canonicalHost(String value) {
        if (value == null) return "";
        // DNS names and the seed are lowercase ASCII; only a punycode label can change
        // under IDN.toUnicode. Returning the spelling nameprep would produce avoids a
        // full Unicode pass for the common case.
        if (isCanonicalAsciiHost(value)) return value;
        String host = value.trim();
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.length() > MAX_HOST_LENGTH) return "";
        try {
            host = IDN.toUnicode(host);
        } catch (IllegalArgumentException ignored) {
            // Bad punycode cannot name an adult host; leave it for the list.
        }
        return host.toLowerCase(Locale.ROOT).trim();
    }

    /** Lowercase ASCII LDH/underscore host with no punycode label, which toUnicode leaves alone. */
    private static boolean isCanonicalAsciiHost(String host) {
        int length = host.length();
        if (length < 1 || length > MAX_HOST_LENGTH || host.charAt(length - 1) == '.') return false;
        boolean labelStart = true;
        for (int i = 0; i < length; i++) {
            char c = host.charAt(i);
            if (c == '.') {
                if (labelStart) return false;
                labelStart = true;
                continue;
            }
            // A valid ACE label (xn--) decodes to a different string, so it takes the slow path.
            if (labelStart && c == 'x' && length - i >= 4
                    && host.charAt(i + 1) == 'n' && host.charAt(i + 2) == '-'
                    && host.charAt(i + 3) == '-') return false;
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_')) return false;
            labelStart = false;
        }
        return true;
    }

    private static Set<String> normalizeSet(Collection<String> values, String what) {
        Set<String> result = new HashSet<>();
        if (values == null) return Collections.unmodifiableSet(result);
        for (String raw : values) {
            String term = normalizeTerm(raw);
            if (term.isEmpty()) throw new IllegalArgumentException("Empty host " + what);
            result.add(term);
        }
        if (result.size() > MAX_TERMS) throw new IllegalArgumentException("Too many host " + what + "s");
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> normalizeHostSet(Collection<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) return Collections.unmodifiableSet(result);
        for (String raw : values) {
            String host = canonicalHost(raw);
            if (host.isEmpty()) throw new IllegalArgumentException("Empty exempt host");
            result.add(host);
        }
        return Collections.unmodifiableSet(result);
    }

    /** The shared orthographic folds, then lowercase — the spelling the terms are stored in. */
    private static String normalizeTerm(String value) {
        return fold(value == null ? "" : value);
    }

    static String fold(String value) {
        boolean ascii = true;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) >= 128) { ascii = false; break; }
        }
        if (ascii) {
            // NFKC/NFD/NFC and combining-mark removal are no-ops over ASCII.
            return value.toLowerCase(Locale.ROOT).trim();
        }
        StringBuilder folded = new StringBuilder(value.length());
        String nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC);
        for (int i = 0; i < nfkc.length();) {
            int point = nfkc.codePointAt(i);
            i += Character.charCount(point);
            if (TextFolding.ignored(point) || point == 0x0640) continue;
            folded.appendCodePoint(TextFolding.fold(point));
        }
        // Latin diacritics: decompose and drop the combining marks. Arabic
        // tashkeel was removed above, so this is a no-op for Arabic.
        String decomposed = COMBINING.matcher(
                Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        return Normalizer.normalize(decomposed, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).trim();
    }

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < text.length();) {
            int point = text.codePointAt(i);
            i += Character.charCount(point);
            if (Character.isLetterOrDigit(point)) {
                token.appendCodePoint(point);
            } else if (token.length() > 0) {
                out.add(token.toString());
                token.setLength(0);
            }
        }
        if (token.length() > 0) out.add(token.toString());
        return out;
    }

    /** Bounded affix stripping and keyboard-hold reduction; see `normalize.js`. */
    private static Set<String> variants(String token) {
        Set<String> out = new HashSet<>();
        out.add(token);
        for (String prefix : PREFIXES) {
            if (token.startsWith(prefix)) {
                String rest = token.substring(prefix.length());
                if (rest.codePointCount(0, rest.length()) >= 3) out.add(rest);
                break;
            }
        }
        for (String suffix : SUFFIXES) {
            if (token.endsWith(suffix)) {
                String rest = token.substring(0, token.length() - suffix.length());
                if (rest.codePointCount(0, rest.length()) >= 3) out.add(rest);
                break;
            }
        }
        String trimmed = TAIL_DIGITS.matcher(token).replaceAll("");
        if (!trimmed.equals(token) && trimmed.codePointCount(0, trimmed.length()) >= 3) out.add(trimmed);
        if (REPEATS.matcher(token).find()) {
            String one = REPEATS.matcher(token).replaceAll("$1");
            String two = REPEATS.matcher(token).replaceAll("$1$1");
            if (one.codePointCount(0, one.length()) >= 3) out.add(one);
            if (two.codePointCount(0, two.length()) >= 3) out.add(two);
        }
        return out;
    }
}
