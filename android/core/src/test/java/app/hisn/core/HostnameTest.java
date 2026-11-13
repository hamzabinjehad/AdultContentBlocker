package app.hisn.core;

import java.util.Random;

/**
 * The ASCII fast path in {@link Hostname#normalize} must return exactly what the IDN
 * path returns for every input, including every rejection. This differential test
 * feeds both paths the same corpus: canonical names, IDN and uppercase spellings,
 * trailing and repeated dots, hyphen edges, IP literals and a deterministic fuzz run
 * over the characters a DNS question or a saved rule can carry.
 */
public final class HostnameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String[] corpus = {
                "www.example.com", "example.com", "a.b", "0.0", "1.2.3.4", "001.002.003.004",
                "example", "localhost", ".example", "example.", "example..com", "a..b",
                "-bad.example", "bad-.example", "under_score.example", "spaces in.example",
                " leading.example", "trailing.example ", "da.sh", "ab.cd.ef",
                "WWW.Example.COM", "PORNHUB.COM.", "xn--mgbh0fb.example", "xn--bcher-kva.example",
                "xn--badpuny.example", "m\u00fcnchen.example", "\u0645\u062b\u0627\u0644.xxx",
                "full\u3002width.example", "full\uff0ewidth.example", "full\uff61width.example",
                "a.very-long-label-that-is-exactly-sixty-three-characters-long.example",
                "a.very-long-label-that-is-exactly-sixty-four-characters-long1.example",
                "tab\t.example", "newline\n.example", "emoji\ud83d\ude00.example",
        };
        for (String value : corpus) compare(value);

        // Every valid canonical shape, and the neighbors the fast path must refuse.
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < 63; i++) label.append(i % 2 == 0 ? 'a' : 'b');
        compare(label + ".example");
        compare(label + "c.example");
        compare("a." + label);
        compare("0.abc");
        compare("123.456");
        compare("abc.123");
        compare("a-b.c-d.e-f");
        compare("a--b.example");
        compare("xn--a.example");
        compare("xn-.example");
        compare("a.xn--p1ai");

        Random random = new Random(0x4f525448L);
        String alphabet = "abcxyzABCXYZ0129.-_ \t\u3002\uff0e\u00fc\u0645\u062b";
        for (int i = 0; i < 300000; i++) {
            int length = 1 + random.nextInt(80);
            StringBuilder value = new StringBuilder(length);
            for (int j = 0; j < length; j++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            compare(value.toString());
        }

        // The documented rejections are the same on both paths.
        for (String invalid : new String[]{null, "", "   ", "a", "127.0.0.1", "256.1.1.1",
                "1.2.3.4.5", "example.", "example..com", "-bad.example", "bad-.example", "under_score.example"}) {
            check(!Hostname.valid(invalid), "accepted invalid host: " + invalid);
        }
        check(Hostname.normalize("www.Example.COM.").equals("www.example.com"), "uppercase and trailing dot");
        check(Hostname.normalize("full\u3002width.example").equals("full.width.example"), "fullwidth dot");
        System.out.println("Hostname tests passed (ASCII fast path equals IDN path on "
                + (corpus.length + 300000) + " inputs, same rejections)");
    }

    private static void compare(String value) {
        String fast, slow;
        boolean fastThrew = false, slowThrew = false;
        try { fast = Hostname.normalize(value); } catch (IllegalArgumentException invalid) { fast = null; fastThrew = true; }
        try { slow = Hostname.normalizeSlow(value); } catch (IllegalArgumentException invalid) { slow = null; slowThrew = true; }
        // normalize() performs the leading/trailing whitespace guard before either path.
        boolean guarded = value != null && !value.isEmpty() && value.length() <= 1024
                && value.equals(value.trim());
        if (!guarded) {
            check(fastThrew, "guarded input accepted: " + printable(value));
            return;
        }
        if (fastThrew != slowThrew) {
            throw new AssertionError("paths disagree on rejection: " + printable(value)
                    + " fast=" + fastThrew + " slow=" + slowThrew);
        }
        if (fastThrew) return;
        check(fast.equals(slow), "paths disagree: " + printable(value) + " fast=" + fast + " slow=" + slow);
        check(Hostname.normalize(value).equals(fast), "unstable result: " + printable(value));
    }

    private static String printable(String value) {
        return value == null ? "null" : value.replace("\t", "\\t").replace("\n", "\\n");
    }
}
