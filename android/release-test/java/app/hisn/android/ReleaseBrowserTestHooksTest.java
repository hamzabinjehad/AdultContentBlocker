package app.hisn.android;

import app.hisn.core.BrowserCanary;
import app.hisn.core.DomainPolicy;

/** Runs the actual release hook implementation without Android dependencies. */
public final class ReleaseBrowserTestHooksTest {
    public static void main(String[] args) {
        BrowserTestHooks hooks = new BrowserTestHooks();
        assertOwnerPolicyUnchanged(hooks);
        for (int i = 0; i < 3; i++) {
            hooks.arm();
            assertOwnerPolicyUnchanged(hooks);
            hooks.clear();
            assertOwnerPolicyUnchanged(hooks);
        }
        System.out.println("Release BrowserTestHooks tests passed (arm/clear no-op, owner policy unchanged, status unavailable)");
    }

    private static void assertOwnerPolicyUnchanged(BrowserTestHooks hooks) {
        check("BROWSER_CANARY_UNAVAILABLE".equals(hooks.status()), "Release exposed canary status");
        for (String hostname : new String[]{BrowserCanary.HOST, "WWW.EXAMPLE.ORG.", "safe.example", null}) {
            for (DomainPolicy.Decision decision : DomainPolicy.Decision.values()) {
                int[] calls = {0};
                DomainPolicy.Decision actual = hooks.evaluate(hostname, ownerHost -> {
                    calls[0]++;
                    check(ownerHost == hostname, "Release changed hostname before owner evaluation");
                    return decision;
                });
                check(actual == decision && calls[0] == 1, "Release hook bypassed or changed owner decision");
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
