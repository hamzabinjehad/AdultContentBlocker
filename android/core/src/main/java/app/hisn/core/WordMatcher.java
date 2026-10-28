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

    /** Canonical configured rules, for comparing settings during a commitment. */
    public Set<String> configuredRules() { return rules; }

    public WordMatcher(Iterable<String> input) {
        Set<String> compiled = new LinkedHashSet<>();
        for (String raw : input) {
            if (raw == null || raw.codePointCount(0, raw.length()) > MAX_RULE_CODEPOINTS) {
                throw new IllegalArgumentException("Invalid word or phrase");
            }
            String value = normalize(raw);
            if (value.isEmpty()) throw new IllegalArgumentException("Empty word or phrase");
            if (value.codePointCount(0, value.length()) > MAX_RULE_CODEPOINTS) {
                throw new IllegalArgumentException("Normalized word or phrase too long");
            }
            compiled.add(value);
            if (compiled.size() > MAX_RULES) throw new IllegalArgumentException("Too many rules");
        }
        rules = Collections.unmodifiableSet(compiled);
        orderedRules = Collections.unmodifiableList(new ArrayList<>(compiled));
        index = orderedRules.size() > 8 ? buildIndex(orderedRules) : null;
    }

    /** Returns matching configured rules; never logs or retains the input text. */
    public List<String> matches(String input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        if (input.codePointCount(0, input.length()) > MAX_TEXT_CODEPOINTS) {
            throw new IllegalArgumentException("Text exceeds the inspection limit");
        }
        String text = " " + normalize(input) + " ";
        if (index != null) return indexedMatches(text);
        List<String> matches = new ArrayList<>();
        for (String rule : rules) {
            if (text.contains(" " + rule + " ")) matches.add(rule);
        }
        return matches;
    }

    private List<String> indexedMatches(String text) {
        boolean[] found = new boolean[orderedRules.size()];
        Node node = index;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (node != index && !node.edges.containsKey(c)) node = node.failure;
            Node next = node.edges.get(c);
            node = next == null ? index : next;
            for (int output : node.outputs) found[output] = true;
        }
        List<String> matches = new ArrayList<>();
        for (int i = 0; i < found.length; i++) if (found[i]) matches.add(orderedRules.get(i));
        return matches;
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
