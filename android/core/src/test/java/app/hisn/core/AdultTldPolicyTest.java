package app.hisn.core;

/** The adult-TLD rail is a suffix rule, not a list entry. */
public final class AdultTldPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AdultTldPolicy policy = new AdultTldPolicy();
        for (String host : new String[]{"new.porn", "cdn.new.xxx", "brand.adult",
                "a.b.sex", "NEW.PORN.", " porn ", "xxx"}) {
            check(policy.matches(host), "guarded TLD: " + host);
        }
        for (String host : new String[]{"example.com", "example.cam", "notporn.com",
                "porn.example.com", "xxx.example.com", "foo.xxx.example.com",
                "", null, "xn--porn.example"}) {
            check(!policy.matches(host), "not guarded: " + host);
        }
        check(AdultTldPolicy.TLDS.size() == 4, "exactly the four sponsored adult TLDs");
        check(AdultTldPolicy.isGuardedTld("PORN"), "case-insensitive TLD check");
        check(!AdultTldPolicy.isGuardedTld("cam"), "cam is generic and deliberately excluded");
        System.out.println("AdultTldPolicy tests passed: .xxx/.porn/.sex/.adult, final label only");
    }
}
