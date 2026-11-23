package app.hisn.core;

/** The address bar's text is read to one site, or to nothing. */
public final class AddressBarHostTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void host(String bar, String expected) {
        String actual = AddressBarHost.host(bar);
        check(expected == null ? actual == null : expected.equals(actual),
                "bar \"" + bar + "\" read as " + actual + ", expected " + expected);
    }

    public static void main(String[] args) {
        host("example.com", "example.com");
        host("example.com/path/page?x=1#top", "example.com");
        host("https://www.Example.com/", "www.example.com");
        host("http://user:pass@example.com:8080/x", "example.com");
        host("  m.example.co.uk/watch  ", "m.example.co.uk");
        host("www-example-com.translate.goog/?_x_tr_sl=auto", "www-example-com.translate.goog");
        host("bücher.de/", "xn--bcher-kva.de");
        host("EXAMPLE.COM.", "example.com");
        // A typed search, a browser page or an address that names no site.
        for (String none : new String[] {null, "", "   ", "adult video", "search terms here", "localhost",
                "localhost:3000/x", "192.168.1.1", "http://10.0.0.1/admin", "[::1]:80", "chrome://settings",
                "about:blank", "file:///sdcard/x.html", "content://media/x", "javascript:alert(1)",
                "ftp://example.com", "data:text/html,hi", "word", "-bad-.com"}) {
            host(none, null);
        }
        // A blocked site read from the bar meets the same composed decision as DNS.
        HostDecision.Policy policy = new HostDecision.Policy(
                new DomainPolicy(java.util.Arrays.asList("blocked.example"), java.util.Collections.emptyList()),
                new DomainPolicy(java.util.Collections.emptyList(), java.util.Collections.emptyList()), true, 1, null);
        check(policy.enforce(AddressBarHost.host("m.blocked.example/video/1")) == DomainPolicy.Decision.BLOCK,
                "subdomain of a blocked site");
        check(policy.enforce(AddressBarHost.host("blocked-example.translate.goog/")) == DomainPolicy.Decision.BLOCK,
                "blocked site through Translate");
        check(policy.enforce(AddressBarHost.host("news.example/")) == DomainPolicy.Decision.ALLOW, "other site");
        System.out.println("AddressBarHost tests passed (schemes, paths, ports, user info, IDN, searches and browser pages)");
    }
}
