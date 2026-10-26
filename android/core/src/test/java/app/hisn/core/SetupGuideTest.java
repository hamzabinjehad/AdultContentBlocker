package app.hisn.core;

public final class SetupGuideTest {
    private static void expect(SetupGuide.Step expected, boolean rules, boolean dnsWanted, GuardHealth health,
            SetupGuide.Assets assets, SetupGuide.Visual visual, SetupGuide.Network network, SetupGuide.Probe probe) {
        SetupGuide.Step actual = SetupGuide.next(rules, dnsWanted, health, assets, visual, network, probe);
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void expect(SetupGuide.Step expected, boolean rules, GuardHealth health,
            SetupGuide.Assets assets, SetupGuide.Network network, SetupGuide.Probe probe) {
        expect(expected, rules, true, health, assets, SetupGuide.Visual.OFF, network, probe);
    }

    private static void missing(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Missing state accepted");
    }

    public static void main(String[] args) {
        int combinations = 0;
        int[] ready = new int[3];
        for (boolean rules : new boolean[]{false, true})
            for (boolean wanted : new boolean[]{false, true})
                for (GuardHealth health : GuardHealth.values())
                    for (SetupGuide.Assets assets : SetupGuide.Assets.values())
                        for (SetupGuide.Visual visual : SetupGuide.Visual.values())
                            for (SetupGuide.Network network : SetupGuide.Network.values())
                                for (SetupGuide.Probe probe : SetupGuide.Probe.values()) {
                                    SetupGuide.Step step = SetupGuide.next(rules, wanted, health, assets, visual, network, probe);
                                    combinations++;
                                    boolean dns = wanted || network != SetupGuide.Network.OFF;
                                    boolean readable = health != GuardHealth.SETTINGS_UNAVAILABLE;
                                    boolean appsHealthy = rules && health == GuardHealth.ACTIVE
                                            && (assets == SetupGuide.Assets.DISABLED || assets == SetupGuide.Assets.READY)
                                            && visual != SetupGuide.Visual.FAILED;
                                    boolean dnsHealthy = network == SetupGuide.Network.RUNNING && probe == SetupGuide.Probe.PASSED;
                                    String state = rules + "/" + wanted + "/" + health + "/" + assets + "/" + visual
                                            + "/" + network + "/" + probe + " -> " + step;
                                    if ((step == SetupGuide.Step.READY) != (appsHealthy && dns && dnsHealthy))
                                        throw new AssertionError("False ready/not-ready result: " + state);
                                    if ((step == SetupGuide.Step.READY_APPS) != (appsHealthy && !dns))
                                        throw new AssertionError("False app-layer ready result: " + state);
                                    if ((step == SetupGuide.Step.READY_DNS) != (!rules && readable && dnsHealthy))
                                        throw new AssertionError("False DNS-only ready result: " + state);
                                    if (readable && !rules && !dns && step != SetupGuide.Step.CHOOSE_RULES)
                                        throw new AssertionError("No chosen layer did not ask for one: " + state);
                                    if (step == SetupGuide.Step.CHOOSE_RULES && (rules || dns))
                                        throw new AssertionError("Asked to choose a layer although one is chosen: " + state);
                                    if (step == SetupGuide.Step.READY) ready[0]++;
                                    if (step == SetupGuide.Step.READY_APPS) ready[1]++;
                                    if (step == SetupGuide.Step.READY_DNS) ready[2]++;
                                }
        // READY: 2 asset states x 2 visual states x 2 DNS wishes; READY_APPS: 2 x 2 x 4 probe results
        // (DNS off and not wanted); READY_DNS: 7 readable health states x 4 assets x 3 visual x 2 wishes.
        if (combinations != 7680 || ready[0] != 8 || ready[1] != 16 || ready[2] != 168)
            throw new AssertionError("State enumeration incomplete: " + combinations + " " + ready[0] + "/" + ready[1] + "/" + ready[2]);

        // Every layer combination the app offers can reach ready.
        SetupGuide.Step dnsOnly = SetupGuide.next(false, true, GuardHealth.DISABLED, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OFF, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);
        if (dnsOnly != SetupGuide.Step.READY_DNS) throw new AssertionError("DNS only: " + dnsOnly);
        expect(SetupGuide.Step.READY_APPS, true, false, GuardHealth.ACTIVE, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OK, SetupGuide.Network.OFF, SetupGuide.Probe.IDLE);   // image checks only
        expect(SetupGuide.Step.READY_APPS, true, false, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                SetupGuide.Visual.OFF, SetupGuide.Network.OFF, SetupGuide.Probe.IDLE);  // adult-text checks only
        expect(SetupGuide.Step.READY_APPS, true, false, GuardHealth.ACTIVE, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OFF, SetupGuide.Network.OFF, SetupGuide.Probe.IDLE);  // blocked apps, schedule or words
        expect(SetupGuide.Step.READY, true, false, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                SetupGuide.Visual.OK, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);  // everything
        expect(SetupGuide.Step.READY_DNS, false, false, GuardHealth.READY_NO_RULES, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OFF, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);

        // Failing layers block ready.
        expect(SetupGuide.Step.REVIEW_VISUAL, true, false, GuardHealth.ACTIVE, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.FAILED, SetupGuide.Network.OFF, SetupGuide.Probe.IDLE);
        expect(SetupGuide.Step.REVIEW_VISUAL, true, true, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                SetupGuide.Visual.FAILED, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);
        // DNS left on but off now (for example not resumed after a restart) still asks for DNS.
        expect(SetupGuide.Step.ENABLE_DNS, true, true, GuardHealth.ACTIVE, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OK, SetupGuide.Network.OFF, SetupGuide.Probe.IDLE);
        expect(SetupGuide.Step.ENABLE_DNS, false, true, GuardHealth.DISABLED, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OFF, SetupGuide.Network.OFF, SetupGuide.Probe.IDLE);
        expect(SetupGuide.Step.TEST_DNS, false, false, GuardHealth.DISABLED, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OFF, SetupGuide.Network.RUNNING, SetupGuide.Probe.IDLE);
        expect(SetupGuide.Step.CHOOSE_RULES, false, false, GuardHealth.DISABLED, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.FAILED, SetupGuide.Network.OFF, SetupGuide.Probe.PASSED);
        // Unreadable settings never read as ready, whichever layers seem chosen.
        expect(SetupGuide.Step.REVIEW_APP_LAYER, false, false, GuardHealth.SETTINGS_UNAVAILABLE, SetupGuide.Assets.DISABLED,
                SetupGuide.Visual.OFF, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED);

        // Priority order within the app layer, as before.
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
        missing(() -> SetupGuide.next(true, true, null, SetupGuide.Assets.READY,
                SetupGuide.Visual.OFF, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED));
        missing(() -> SetupGuide.next(true, true, GuardHealth.ACTIVE, null,
                SetupGuide.Visual.OFF, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED));
        missing(() -> SetupGuide.next(true, true, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                null, SetupGuide.Network.RUNNING, SetupGuide.Probe.PASSED));
        missing(() -> SetupGuide.next(true, true, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                SetupGuide.Visual.OFF, null, SetupGuide.Probe.PASSED));
        missing(() -> SetupGuide.next(true, true, GuardHealth.ACTIVE, SetupGuide.Assets.READY,
                SetupGuide.Visual.OFF, SetupGuide.Network.RUNNING, null));
        System.out.println("SetupGuide tests passed (" + combinations + " setup combinations; DNS-only, "
                + "app-only and combined layers each reach ready)");
    }
}
