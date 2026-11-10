package app.hisn.core;

public final class RulePreviewTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        RulePreview.Report domains = RulePreview.of(
                "Example.com\nhttps://bad.example/path\nBücher.example\nxn--bcher-kva.example\n\n"
                        + "1.2.3.4\nbad..example\nexample.com.\n",
                RulePreview.Kind.DOMAIN, 1000);
        check(domains.accepted.contains("example.com") && domains.accepted.contains("xn--bcher-kva.example"),
                "canonical domains");
        check(domains.duplicates == 2, "domain duplicates after normalization");
        check(domains.rejected == 3, "invalid URL, IP and empty label");
        check(domains.acceptedText().equals("example.com\nxn--bcher-kva.example"), "accepted set");

        RulePreview.Report words = RulePreview.of("Word\nword\nWORD\nإباحيّ\nإباحي\n", RulePreview.Kind.WORD, 1000);
        check(words.accepted.size() == 2, "word canonical duplicates: " + words.accepted);
        check(words.duplicates == 3, "word duplicates");
        check(words.rejected == 0, "valid words");
        check(RulePreview.of("", RulePreview.Kind.DOMAIN, 1000).accepted.isEmpty(), "empty list");

        try { RulePreview.of("a\nb\nc", RulePreview.Kind.DOMAIN, 2); throw new AssertionError("line limit"); }
        catch (IllegalArgumentException expected) { }
        try { RulePreview.of("x".repeat(65537), RulePreview.Kind.DOMAIN, 1000); throw new AssertionError("codepoint limit"); }
        catch (IllegalArgumentException expected) { }
        try { RulePreview.of("x", RulePreview.Kind.DOMAIN, -1); throw new AssertionError("negative limit"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("RulePreview tests passed (Unicode/duplicates/invalid lines and hard limits)");
    }
}
