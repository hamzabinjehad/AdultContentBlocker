package app.hisn.core;

public final class PolicyVersionTest {
    public static void main(String[] args) {
        if (PolicyVersion.next(0, false) != 0) throw new AssertionError("identical save moved the version");
        if (PolicyVersion.next(0, true) != 1) throw new AssertionError("legacy change did not start at 1");
        if (PolicyVersion.next(7, false) != 7) throw new AssertionError("checkpoint moved the version");
        if (PolicyVersion.next(7, true) != 8) throw new AssertionError("change did not advance");
        try { PolicyVersion.next(-1, true); throw new AssertionError("negative version accepted"); }
        catch (IllegalArgumentException expected) { }
        try { PolicyVersion.next(Long.MAX_VALUE, true); throw new AssertionError("overflow accepted"); }
        catch (IllegalStateException expected) { }
        if (PolicyVersion.LEGACY != 0) throw new AssertionError("legacy version changed");
        System.out.println("PolicyVersion tests passed (identical/checkpoint stay, change advances, overflow refused)");
    }
}
