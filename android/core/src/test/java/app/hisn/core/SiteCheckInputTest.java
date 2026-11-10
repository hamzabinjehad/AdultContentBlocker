package app.hisn.core;

public final class SiteCheckInputTest {
    private static void ok(String raw, String host) {
        String actual = SiteCheckInput.host(raw);
        if (!host.equals(actual)) throw new AssertionError(raw + ": expected " + host + ", got " + actual);
    }

    private static void bad(String raw) {
        try { SiteCheckInput.host(raw); throw new AssertionError(raw + " accepted"); }
        catch (SiteCheckInput.Invalid expected) { }
    }

    public static void main(String[] args) {
        ok("example.com", "example.com");
        ok(" WWW.Example.COM. ", "www.example.com");
        ok("https://example.com", "example.com");
        ok("HTTPS://www.example.com/path?q=1#x", "www.example.com");
        ok("http://example.com:8080/a", "example.com");
        ok("https://user:pass@example.com/a", "example.com");
        ok("https://bücher.example/a", "xn--bcher-kva.example");
        ok("//example.com/a", "example.com");
        ok("xn--bcher-kva.example", "xn--bcher-kva.example");

        bad(null);
        bad("");
        bad("   ");
        bad("ftp://example.com");
        bad("javascript:alert(1)");
        bad("example.com/path");
        bad("example.com?x=1");
        bad("user@example.com");
        bad("https://");
        bad("https:///path");
        bad("localhost");
        bad("1.2.3.4");
        bad("[::1]");
        bad("https://[::1]/");
        bad("bad..example");
        bad("exa mple.com");
        bad("example.com:notaport");
        System.out.println("SiteCheckInput tests passed (hostname and HTTP(S) boundaries)");
    }
}
