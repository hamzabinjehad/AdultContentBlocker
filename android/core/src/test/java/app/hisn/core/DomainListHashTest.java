package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;

/** Same canonical hash as script/guardian_protocol.domain_list_sha256. */
public final class DomainListHashTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        String first = DomainListHash.sha256(Arrays.asList("b.example", "a.example"));
        String second = DomainListHash.sha256(Arrays.asList("a.example", "b.example"));
        check(first.equals(second), "order changed the hash");
        check(first.equals("2a191a2b08b137ec8a456ced0dd264342eef15e085432e31a7c5c072c734654d"),
                "known vector mismatch: " + first);
        check(!first.equals(DomainListHash.sha256(Arrays.asList("a.example", "c.example"))),
                "different lists collided");
        check(DomainListHash.sha256(Collections.emptyList()).equals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                "empty-list hash");
        System.out.println("DomainListHash tests passed: canonical order, empty list and different lists");
    }
}
