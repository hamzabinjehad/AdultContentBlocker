package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;

public final class DomainPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DomainPolicy policy = new DomainPolicy(Arrays.asList("Example.COM", "bücher.example"),
                Collections.singletonList("safe.example.com"));
        check(policy.blocks("EXAMPLE.com."), "case and root dot");
        check(policy.blocks("www.example.com"), "subdomain");
        check(!policy.blocks("notexample.com"), "label boundary");
        check(!policy.blocks("safe.example.com") && !policy.blocks("a.safe.example.com"), "allow overrides block");
        check(policy.blocks("xn--bcher-kva.example"), "IDNA");
        check(policy.blocks("www。example.com"), "international separator");
        for (String invalid : Arrays.asList("https://example.com", "example.com:443", "example.com..", "192.0.2.1", "::1", " example.com")) {
            check(!policy.blocks(invalid), "invalid query " + invalid);
            check(policy.evaluate(invalid) == DomainPolicy.Decision.INVALID, "invalid status " + invalid);
            try {
                new DomainPolicy(Collections.singletonList(invalid), Collections.emptyList());
                throw new AssertionError("invalid rule accepted: " + invalid);
            } catch (IllegalArgumentException expected) { }
        }
        System.out.println("DomainPolicy tests passed");
    }
}
