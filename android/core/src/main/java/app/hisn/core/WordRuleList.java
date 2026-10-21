package app.hisn.core;

import java.util.ArrayList;
import java.util.List;

/** Parses a bounded list of literal rules, one per line. */
public final class WordRuleList {
    private static final int MAX_INPUT_CODEPOINTS = 65536;
    private static final int MAX_LINES = 1000;

    private WordRuleList() { }

    public static WordMatcher compile(String input) {
        if (input == null || input.codePointCount(0, input.length()) > MAX_INPUT_CODEPOINTS) {
            throw new IllegalArgumentException("Rule list too long");
        }
        String[] lines = input.split("\\R", -1);
        if (lines.length > MAX_LINES) throw new IllegalArgumentException("Too many lines");
        List<String> rules = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) rules.add(line.trim());
        }
        if (rules.isEmpty()) throw new IllegalArgumentException("No rules");
        return new WordMatcher(rules);
    }
}
