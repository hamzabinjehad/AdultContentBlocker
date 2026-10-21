package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;

public final class WordMatcherTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        WordMatcher matcher = new WordMatcher(Arrays.asList("sex", "adult video", "إباحِيّ"));
        check(matcher.matches("Essex and sextant").isEmpty(), "whole-word boundaries");
        check(matcher.matches("A SEX education page").contains("sex"), "case normalization");
        check(matcher.matches("adult,   video").contains("adult video"), "phrase separators");
        check(matcher.matches("إباحي").contains("إباحي"), "Arabic combining marks");
        check(matcher.matches("ｓｅｘ").contains("sex"), "Unicode width normalization");
        check(matcher.matches("s\u200bex").contains("sex"), "zero-width characters");
        check(matcher.matches("safe words").isEmpty(), "benign text");
        try {
            new WordMatcher(Collections.singleton("sex")).matches("a".repeat(65536) + " sex");
            throw new AssertionError("oversized text silently accepted");
        } catch (IllegalArgumentException expected) { }
        check(new WordMatcher(Collections.singleton("sex")).matches("a".repeat(65532) + " sex").contains("sex"), "match at inspection limit");
        try {
            new WordMatcher(Collections.singleton("---"));
            throw new AssertionError("empty rule accepted");
        } catch (IllegalArgumentException expected) { }
        WordMatcher list = WordRuleList.compile(" sex \n\nadult video\r\nإباحي");
        check(list.matches("An adult, video").contains("adult video"), "multiline rules");
        check(list.matches("Essex").isEmpty(), "list preserves word boundaries");
        expectInvalid("  \n ");
        expectInvalid("---\nvalid");
        expectInvalid("x".repeat(129));
        expectInvalid("\ufb03".repeat(43));
        WordMatcher expanded = WordRuleList.compile("\ufb03".repeat(42));
        String canonical = String.join("\n", expanded.configuredRules());
        check(canonical.equals("ffi".repeat(42)), "compatibility ligatures expand during normalization");
        check(WordRuleList.compile(canonical).configuredRules().equals(expanded.configuredRules()),
                "accepted expanded rules survive serialization and reload");
        expectInvalid("x\n".repeat(1000));
        expectInvalid("x".repeat(65537));
        java.util.List<String> indexedRules = Arrays.asList("adult", "adult video", "video", "a", "a a", "إباحي", "example", "test", "other", "safe phrase");
        WordMatcher indexed = new WordMatcher(indexedRules);
        java.util.Random random = new java.util.Random(18);
        String[] tokens = {"adult", "video", "safe", "phrase", "a", "إباحِيّ", "test", "Essex", "other", "example"};
        for (int i = 0; i < 1000; i++) {
            StringBuilder sample = new StringBuilder();
            for (int j = 0; j < 40; j++) sample.append(tokens[random.nextInt(tokens.length)]).append(j % 2 == 0 ? ", " : " ");
            String normalized = " " + WordMatcher.normalize(sample.toString()) + " ";
            java.util.List<String> expected = new java.util.ArrayList<>();
            for (String candidate : indexedRules) if (normalized.contains(" " + WordMatcher.normalize(candidate) + " ")) expected.add(WordMatcher.normalize(candidate));
            check(indexed.matches(sample.toString()).equals(expected), "indexed matching preserves literal boundaries and rule order");
        }
        java.util.List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) many.add("rule" + i);
        check(new WordMatcher(many).matches("rule999 rule0 notrule1").equals(Arrays.asList("rule0", "rule999")), "large rule set");
        System.out.println("WordMatcher tests passed");
    }

    private static void expectInvalid(String rules) {
        try {
            WordRuleList.compile(rules);
            throw new AssertionError("invalid list accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
