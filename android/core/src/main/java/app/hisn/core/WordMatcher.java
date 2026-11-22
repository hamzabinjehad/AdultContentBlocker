package app.hisn.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Literal, bounded matching for user-selected words and phrases.
 * This class does not access the screen or enforce a block. */
public final class WordMatcher {
    private static final int MAX_TEXT_CODEPOINTS = 65536;
    private static final int MAX_RULE_CODEPOINTS = 128;
    private static final int MAX_RULES = 1000;
    private final Set<String> rules;
    private final List<String> orderedRules;
    private final Node index;
    private final String[] smallPatterns;

    /** Canonical configured rules, for comparing settings during a commitment. */
    public Set<String> configuredRules() { return rules; }

    /** No configured rules: {@link #matches} can only return an empty list. */
    public boolean isEmpty() { return rules.isEmpty(); }

    public WordMatcher(Iterable<String> input) {
        Set<String> compiled = new LinkedHashSet<>();
        for (String raw : input) {
            compiled.add(canonicalRule(raw));
            if (compiled.size() > MAX_RULES) throw new IllegalArgumentException("Too many rules");
        }
        rules = Collections.unmodifiableSet(compiled);
        orderedRules = Collections.unmodifiableList(new ArrayList<>(compiled));
        index = orderedRules.size() > 8 ? buildIndex(orderedRules) : null;
        smallPatterns = index == null ? new String[orderedRules.size()] : null;
        if (smallPatterns != null) {
            for (int i = 0; i < smallPatterns.length; i++) smallPatterns[i] = " " + orderedRules.get(i) + " ";
        }
    }

    /** Validates a single editor entry using the same spelling the matcher saves. */
    public static String canonicalRule(String raw) {
        if (raw == null || raw.codePointCount(0, raw.length()) > MAX_RULE_CODEPOINTS) {
            throw new IllegalArgumentException("Invalid word or phrase");
        }
        String value = normalize(raw);
        if (value.isEmpty()) throw new IllegalArgumentException("Empty word or phrase");
        if (value.codePointCount(0, value.length()) > MAX_RULE_CODEPOINTS) {
            throw new IllegalArgumentException("Normalized word or phrase too long");
        }
        return value;
    }

    /** Returns matching configured rules; never logs or retains the input text. */
    public List<String> matches(String input) {
        String text = searchable(input);
        if (text == null) return Collections.emptyList();
        if (index != null) return indexedMatches(text);
        List<String> matches = null;
        for (int at = 0; at < smallPatterns.length; at++) {
            if (!text.contains(smallPatterns[at])) continue;
            if (matches == null) matches = new ArrayList<>();
            matches.add(orderedRules.get(at));
        }
        return matches == null ? Collections.emptyList() : matches;
    }

    /** Answers a blocking check and stops at the first whole-word or phrase match. */
    public boolean hasMatch(String input) {
        String text = searchable(input);
        if (text == null) return false;
        if (index == null) {
            for (String pattern : smallPatterns) if (text.contains(pattern)) return true;
            return false;
        }
        Node node = index;
        for (int at = 0; at < text.length(); at++) {
            node = advance(node, text.charAt(at));
            if (!node.outputs.isEmpty()) return true;
        }
        return false;
    }

    private String searchable(String input) {
        if (input == null || input.isEmpty()) return null;
        if (input.length() > MAX_TEXT_CODEPOINTS
                && input.codePointCount(0, input.length()) > MAX_TEXT_CODEPOINTS) {
            throw new IllegalArgumentException("Text exceeds the inspection limit");
        }
        if (rules.isEmpty()) return null;
        return " " + normalize(input) + " ";
    }

    private List<String> indexedMatches(String text) {
        boolean[] found = null;
        Node node = index;
        for (int i = 0; i < text.length(); i++) {
            node = advance(node, text.charAt(i));
            if (node.outputs.isEmpty()) continue;
            if (found == null) found = new boolean[orderedRules.size()];
            for (int output : node.outputs) found[output] = true;
        }
        if (found == null) return Collections.emptyList();
        List<String> matches = new ArrayList<>();
        for (int i = 0; i < found.length; i++) if (found[i]) matches.add(orderedRules.get(i));
        return matches;
    }

    private Node advance(Node node, char ch) {
        Node next = node.edges.get(ch);
        while (next == null && node != index) {
            node = node.failure;
            next = node.edges.get(ch);
        }
        return next == null ? index : next;
    }

    private static Node buildIndex(List<String> rules) {
        Node root = new Node();
        root.failure = root;
        for (int i = 0; i < rules.size(); i++) {
            Node node = root;
            String pattern = " " + rules.get(i) + " ";
            for (int j = 0; j < pattern.length(); j++) {
                char c = pattern.charAt(j);
                Node next = node.edges.get(c);
                if (next == null) { next = new Node(); node.edges.put(c, next); }
                node = next;
            }
            node.outputs.add(i);
        }
        ArrayDeque<Node> queue = new ArrayDeque<>();
        for (Node child : root.edges.values()) { child.failure = root; queue.addLast(child); }
        while (!queue.isEmpty()) {
            Node parent = queue.removeFirst();
            for (Map.Entry<Character, Node> edge : parent.edges.entrySet()) {
                Node fallback = parent.failure;
                while (fallback != root && !fallback.edges.containsKey(edge.getKey())) fallback = fallback.failure;
                Node match = fallback.edges.get(edge.getKey());
                Node child = edge.getValue();
                child.failure = match == null ? root : match;
                child.outputs.addAll(child.failure.outputs);
                queue.addLast(child);
            }
        }
        return root;
    }

    private static final class Node {
        final Map<Character, Node> edges = new HashMap<>();
        final List<Integer> outputs = new ArrayList<>();
        Node failure;
    }

    static String normalize(String input) {
        String quick = TextFolding.quickCanonical(input);
        return quick != null ? quick : normalizeSlow(input);
    }

    /** The reference path; {@link TextFolding#quickCanonical} must agree with it. */
    static String normalizeSlow(String input) {
        // NFD exposes the hamza/vowel marks that NFKC leaves attached, so both
        // user rules and page text fold أ/إ/آ and their family to bare alef.
        // The shared TextFolding step then covers the forms decomposition
        // cannot reach (taa marbuta, alef maqsura, Persian/Urdu letters and
        // Arabic-Indic digits), matching the signed list's compiler.
        String normalized = Normalizer.normalize(
                Normalizer.normalize(input, Normalizer.Form.NFKC), Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        boolean separator = true;
        for (int i = 0; i < normalized.length();) {
            int cp = normalized.codePointAt(i);
            i += Character.charCount(cp);
            // Ignore formatting controls, joining marks, tatweel and invisible
            // fillers, keeping words split only by visible punctuation or space.
            if (TextFolding.ignored(cp)) continue;
            cp = TextFolding.fold(cp);
            if (Character.isLetterOrDigit(cp)
                    || Character.getType(cp) == Character.COMBINING_SPACING_MARK) {
                out.appendCodePoint(cp);
                separator = false;
            } else if (!separator) {
                out.append(' ');
                separator = true;
            }
        }
        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ') out.setLength(length - 1);
        return Normalizer.normalize(out, Normalizer.Form.NFC);
    }
}
