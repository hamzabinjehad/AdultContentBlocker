package app.hisn.core;

import java.util.EnumSet;
import java.util.Set;

/** Pure decisions for the background layer-health watchdog. */
public final class LayerWatchTest {
    public static void main(String[] args) {
        // Only a direct build with the keep switch expects the accessibility guard;
        // DNS is expected only when the owner left it on.
        check(LayerWatch.expected(false, true, false).isEmpty(), "ordinary build expected a guard");
        check(LayerWatch.expected(true, false, false).isEmpty(), "idle direct build expected a guard");
        check(LayerWatch.expected(true, true, false)
                        .equals(EnumSet.of(LayerWatch.Layer.ACCESSIBILITY)),
                "keep-installed did not expect accessibility");
        check(LayerWatch.expected(false, false, true)
                        .equals(EnumSet.of(LayerWatch.Layer.DNS)),
                "DNS request did not expect DNS");
        check(LayerWatch.expected(true, true, true)
                        .equals(EnumSet.of(LayerWatch.Layer.ACCESSIBILITY, LayerWatch.Layer.DNS)),
                "both armed layers not expected");

        // Running state is independent, so a lost layer is visible rather than assumed.
        Set<LayerWatch.Layer> expected = LayerWatch.expected(true, true, true);
        check(LayerWatch.lost(expected, Set.of()).size() == 2, "both lost layers hidden");
        check(LayerWatch.lost(expected, EnumSet.of(LayerWatch.Layer.ACCESSIBILITY))
                        .equals(EnumSet.of(LayerWatch.Layer.DNS)),
                "missing DNS not reported");
        check(LayerWatch.lost(expected, EnumSet.of(LayerWatch.Layer.DNS))
                        .equals(EnumSet.of(LayerWatch.Layer.ACCESSIBILITY)),
                "missing accessibility not reported");
        check(LayerWatch.lost(expected, expected).isEmpty(), "healthy layers reported lost");
        check(LayerWatch.lost(Set.of(), expected).isEmpty(), "none expected reported lost");

        // A stable signature is order-independent and never contains a healthy layer.
        check(LayerWatch.signature(Set.of()).isEmpty(), "empty loss has a signature");
        check(LayerWatch.signature(EnumSet.of(LayerWatch.Layer.DNS, LayerWatch.Layer.ACCESSIBILITY))
                        .equals("accessibility,dns"),
                "two-layer signature is not stable");
        check(LayerWatch.signature(EnumSet.of(LayerWatch.Layer.DNS)).equals("dns"),
                "DNS signature wrong");

        // A new loss notifies; the same loss repeats only after the interval; a
        // healthy check is silent; a clock rollback does not silence it.
        long hour = 3600000L;
        check(!LayerWatch.shouldNotify("", 0, "", 0, 12 * hour), "healthy check notified");
        check(LayerWatch.shouldNotify("", 0, "dns", 0, 12 * hour), "new loss was silent");
        check(LayerWatch.shouldNotify("accessibility", 0, "dns", 0, 12 * hour),
                "changed loss was silent");
        check(!LayerWatch.shouldNotify("dns", 0, "dns", hour, 12 * hour), "repeat was too early");
        check(LayerWatch.shouldNotify("dns", 0, "dns", 12 * hour, 12 * hour), "repeat was too late");
        check(LayerWatch.shouldNotify("dns", 10 * hour, "dns", hour, 12 * hour),
                "clock rollback silenced the notice");

        System.out.println("LayerWatch tests passed: expected/running sets, lost layers, "
                + "stable signature and notification cadence");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
