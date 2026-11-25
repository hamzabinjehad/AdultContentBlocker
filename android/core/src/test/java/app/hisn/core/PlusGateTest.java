package app.hisn.core;

public final class PlusGateTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Plus gate: " + label); }

    public static void main(String[] args) {
        check(PlusGate.open(PlusGate.Feature.MORE_RULES, false, 0), "the first rule is free");
        check(PlusGate.open(PlusGate.Feature.MORE_RULES, false, 2), "the third rule is free");
        check(!PlusGate.open(PlusGate.Feature.MORE_RULES, false, 3), "the fourth rule needs Plus");
        check(PlusGate.open(PlusGate.Feature.MORE_RULES, true, 40), "Plus has no rule limit");
        check(PlusGate.open(PlusGate.Feature.MORE_WOODS, false, 1), "a second wood is free");
        check(!PlusGate.open(PlusGate.Feature.MORE_WOODS, false, 2), "a third wood needs Plus");
        check(!PlusGate.open(PlusGate.Feature.TREE_KINDS, false, 0), "extra tree kinds need Plus");
        check(PlusGate.open(PlusGate.Feature.SEASONS, true, 0), "Plus opens the seasons");
        check(!PlusGate.active(1_000, 0, false), "nothing bought is not Plus");
        check(PlusGate.active(1_000, 2_000, false), "a running subscription is Plus");
        check(!PlusGate.active(3_000, 2_000, false), "a lapsed subscription is not Plus");
        check(PlusGate.active(3_000, 0, true), "a one-time purchase is Plus for good");
        System.out.println("PlusGate tests passed (three free rules, two free woods, paid kinds, subscription window, lifetime)");
    }
}
