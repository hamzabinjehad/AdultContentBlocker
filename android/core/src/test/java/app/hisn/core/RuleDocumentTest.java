package app.hisn.core;

import java.nio.charset.StandardCharsets;

public final class RuleDocumentTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    public static void main(String[] args) {
        String exported = RuleDocument.export("Example.com\nbad..example\n", "Word\nإباحيّ\n");
        RuleDocument.Result parsed = RuleDocument.parse(bytes(exported));
        check(parsed.blocks.equals(java.util.Collections.singletonList("example.com")), "domain round trip");
        check(parsed.words.size() == 2, "word round trip");
        check(parsed.platform.equals("android"), "platform");

        String browser = "{\"schema\":\"hisn.rules\",\"version\":1,"
                + "\"customBlocks\":[\"b.example\",\"bad..example\",\"1.2.3.4\"],"
                + "\"customTerms\":[\"ticket\",\"abc\"],"
                + "\"allowlist\":[\"github.com\"],\"textAllow\":[\"news.example\"]}";
        RuleDocument.Result imported = RuleDocument.parse(bytes(browser));
        check(imported.blocks.equals(java.util.Collections.singletonList("b.example")), "invalid hosts skipped");
        check(imported.skippedHosts == 2, "skipped host count");
        check(imported.skippedWords == 0, "Android keeps short literal words");
        check(imported.unsupported.contains("allowlist") && imported.unsupported.contains("textAllow"),
                "unsupported lists reported");
        check(imported.platform.equals("browser"), "browser platform");

        String merged = RuleDocument.merge("existing.example\n", imported.blocks);
        check(merged.equals("existing.example\nb.example"), "add-only merge");

        for (String text : new String[]{
                "{}",
                "{\"schema\":\"other\",\"version\":1}",
                "{\"schema\":\"hisn.rules\",\"version\":2}",
                "{\"schema\":\"hisn.rules\",\"version\":1,\"customBlocks\":\"example.com\"}",
                "{\"schema\":\"hisn.rules\",\"version\":1,\"customBlocks\":[1]}",
                "[]", "null", "{\"schema\":\"hisn.rules\",\"version\":1,\"customBlocks\":[\"a\"],\"customBlocks\":[\"b\"]}"}) {
            try { RuleDocument.parse(bytes(text)); throw new AssertionError("accepted: " + text); }
            catch (RuleDocument.Invalid expected) { }
        }
        try { RuleDocument.parse(new byte[RuleDocument.MAX_BYTES + 1]); throw new AssertionError("oversized"); }
        catch (RuleDocument.Invalid expected) { check(expected.reason.equals("size"), "size reason"); }
        String tooMany = "{\"schema\":\"hisn.rules\",\"version\":1,\"customBlocks\":["
                + "\"x.example\",".repeat(1000) + "\"y.example\"]}";
        try { RuleDocument.parse(bytes(tooMany)); throw new AssertionError("too many rules"); }
        catch (RuleDocument.Invalid expected) { }
        check(RuleDocument.parse(bytes(exported)).unsupported.isEmpty(), "clean file has no warnings");
        System.out.println("RuleDocument tests passed (subset, add-only merge, strict schema/limits, warnings)");
    }
}
