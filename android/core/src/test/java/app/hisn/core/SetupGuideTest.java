package app.hisn.core;

public final class SetupGuideTest {
    private static void expect(SetupGuide.Step expected, boolean rules, GuardHealth health,
            SetupGuide.Assets assets, SetupGuide.Network network, SetupGuide.Probe probe) {
        SetupGuide.Step actual = SetupGuide.next(rules, health, assets, network, probe);
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void missing(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Missing state accepted");
    }

    public static void main(String[] args) {
        int combinations = 0;
        int ready = 0;
        for (boolean rules : new boolean[]{false, true})
            for (GuardHealth health : GuardHealth.values())
                for (SetupGuide.Assets assets : SetupGuide.Assets.values())
                    for (SetupGuide.Network network : SetupGuide.Network.values())
                        for (SetupGuide.Probe probe : SetupGuide.Probe.values()) {
                            SetupGuide.Step step = SetupGuide.next(rules, health, assets, network, probe);
                            combinations++;
                            if (!rules && step != SetupGuide.Step.CHOOSE_RULES)
                                throw new AssertionError("Missing rules did not take priority");
                            boolean healthy = rules && health == GuardHealth.ACTIVE
                                    && (assets == SetupGuide.Assets.DISABLED || assets == SetupGuide.Assets.READY)
                                    && network == SetupGuide.Network.RUNNING && probe == SetupGuide.Probe.PASSED;
                            if ((step == SetupGuide.Step.READY) != healthy)
                                throw new AssertionError("False ready/not-ready result: " + health + "/" + assets
                                        + "/" + network + "/" + probe);
                            if (step == SetupGuide.Step.READY) ready++;
                        }
        if (combinations != 1280 || ready != 2) throw new AssertionError("State enumeration incomplete");

        expect(SetupGuide.Step.REVIEW_APP_LAYER, true, GuardHealth.SETTINGS_UNAVAILABLE,
                SetupGuide.Assets.ERROR, SetupGuide.Network.ERROR, SetupGuide.Probe.FAILED);
        expect(SetupGuide.Step.REVIEW_APP_LAYER, true, GuardHealth.PERMISSION_UNKNOWN,
                SetupGuide.Assets.LOADING, SetupGuide.Network.STARTING, SetupGuide.Probe.CHECKING);
        expect(SetupGuide.Step.REVIEW_APP_LAYER, true, GuardHealth.READY_NO_RULES,
                SetupGuide.Assets.READY, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);
        for (GuardHealth health : new GuardHealth[]{GuardHealth.DISABLED,
                GuardHealth.PERMISSION_REQUIRED, GuardHealth.RECONNECT_REQUIRED}) {
            expect(SetupGuide.Step.CONNECT_SERVICE, true, health,
                    SetupGuide.Assets.ERROR, SetupGuide.Network.ERROR, SetupGuide.Probe.FAILED);
        }
        expect(SetupGuide.Step.LOAD_POLICY, true, GuardHealth.LIMITED,
                SetupGuide.Assets.LOADING, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);
        expect(SetupGuide.Step.REPAIR_POLICY, true, GuardHealth.LIMITED,
                SetupGuide.Assets.ERROR, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);
        expect(SetupGuide.Step.REVIEW_APP_LAYER, true, GuardHealth.LIMITED,
                SetupGuide.Assets.READY, SetupGuide.Network.DEGRADED, SetupGuide.Probe.FAILED);
        expect(SetupGuide.Step.STARTING_DNS, true, GuardHealth.ACTIVE,
                SetupGuide.Assets.READY, SetupGuide.Network.STARTING, SetupGuide.Probe.CHECKING);
        expect(SetupGuide.Step.REVIEW_DNS, true, GuardHealth.ACTIVE,
                SetupGuide.Assets.DISABLED, SetupGuide.Network.DEGRADED, SetupGuide.Probe.PASSED);
        for (SetupGuide.Network network : new SetupGuide.Network[]{SetupGuide.Network.OFF, SetupGuide.Network.ERROR}) {
            expect(SetupGuide.Step.ENABLE_DNS, true, GuardHealth.ACTIVE,
                    SetupGuide.Assets.READY, network, SetupGuide.Probe.PASSED);
        }
        expect(SetupGuide.Step.CHECKING_DNS, true, GuardHealth.ACTIVE,
                SetupGuide.Assets.DISABLED, SetupGuide.Network.RUNNING, SetupGuide.Probe.CHECKING);
        for (SetupGuide.Probe probe : new SetupGuide.Probe[]{SetupGuide.Probe.IDLE, SetupGuide.Probe.FAILED}) {
            expect(SetupGuide.Step.TEST_DNS, true, GuardHealth.ACTIVE,
                    SetupGuide.Assets.READY, SetupGuide.Network.RUNNING, probe);
        }
        missing(() -> SetupGuide.next(true, null, SetupGuide.Assets.READY,
                SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED));
        missing(() -> SetupGuide.next(true, GuardHealth.ACTIVE, null,
                SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED));
        missing(() -> SetupGuide.next(true, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                null, SetupGuide.Probe.PASSED));
        missing(() -> SetupGuide.next(true, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                SetupGuide.Network.RUNNING, null));
        System.out.println("SetupGuide tests passed (" + combinations + " setup combinations and priority checks)");
    }
}
