package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** SafeSearch CNAME answers stay local, preserve the question and never forward. */
public final class DnsPolicyRewriteTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static DnsPolicy policy() {
        DomainPolicy domains = new DomainPolicy(Collections.singleton("blocked.example"),
                Collections.singleton("safe.blocked.example"));
        return new DnsPolicy(domains::evaluate, SafeSearch::target);
    }

    public static void main(String[] args) throws Exception {
        DnsPolicy policy = policy();
        byte[] query = DnsPolicyTest.query("www.google.com", 1);
        DnsPolicy.Result result = policy.evaluate(query);
        check(result.action == DnsPolicy.Action.REWRITE, "www.google.com was not rewritten");
        check("forcesafesearch.google.com".equals(result.rewriteTarget), "Google target");
        check(result.response[6] == 0 && result.response[7] == 1, "one CNAME answer");
        check((result.response[2] & 0xff) == 0x81 && (result.response[3] & 0xff) == 0x80,
                "rewrite response flags");
        String text = new String(result.response, StandardCharsets.ISO_8859_1);
        check(text.contains("forcesafesearch") && text.contains("google"), "CNAME name missing");
        check(result.response[result.response.length - 1] == 0, "CNAME not terminated");
        check(result.response.length > query.length, "CNAME answer omitted");

        check(policy.evaluate(DnsPolicyTest.query("www.google.com", 28)).action
                == DnsPolicy.Action.REWRITE, "AAAA not rewritten");
        check(policy.evaluate(DnsPolicyTest.query("www.google.com", 15)).action
                == DnsPolicy.Action.FORWARD, "MX should not be rewritten");
        check(policy.evaluate(DnsPolicyTest.query("google.co.uk", 1)).action
                == DnsPolicy.Action.FORWARD, "country Google guessed");
        check(policy.evaluate(DnsPolicyTest.query("blocked.example", 1)).action
                == DnsPolicy.Action.BLOCK, "blocked domain rewritten");
        check(new DnsPolicy(hostname -> DomainPolicy.Decision.ALLOW)
                .evaluate(query).action == DnsPolicy.Action.FORWARD, "null rewriter rewrote");

        DnsExchange exchange = new DnsExchange(policy, request -> {
            throw new AssertionError("SafeSearch rewrite reached the upstream resolver");
        });
        DnsExchange.Resolution resolution = exchange.resolveDetailed(query);
        check(resolution.rewrite && !resolution.blocked && resolution.answer != null,
                "exchange did not return the local CNAME");
        check(resolution.answer[resolution.answer.length - 1] == 0, "exchange CNAME truncated");
        System.out.println("DnsPolicyRewrite tests passed: SafeSearch CNAME is local, typed, "
                + "question-preserving and never forwarded");
    }
}
