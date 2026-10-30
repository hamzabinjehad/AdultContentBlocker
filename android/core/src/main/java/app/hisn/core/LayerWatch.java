package app.hisn.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure decisions for the background layer-health check.
 *
 * The app window is only a control panel: enforcement lives in the DNS VPN and
 * the accessibility service. Android can stop either while Hisn is not open.
 * A periodic check compares what the owner left armed with what is actually
 * running and raises a local notice when a layer has gone:
 *
 *   ACCESSIBILITY  selected-app text guard (direct build, keep installed)
 *   DNS            the DNS-only VPN the owner left on
 *
 * It never restarts a service by itself and never claims a layer is running
 * because it was once enabled. A missing layer is a local notification, not a
 * server report; no hostname or screen text is involved.
 */
public final class LayerWatch {
    private LayerWatch() { }

    public enum Layer {
        ACCESSIBILITY("accessibility"),
        DNS("dns");

        public final String key;

        Layer(String key) { this.key = key; }
    }

    /** What the owner's saved choices expect to be running. */
    public static Set<Layer> expected(boolean selfProtectionBuild, boolean keepInstalled,
                                      boolean dnsRequested) {
        Set<Layer> layers = EnumSet.noneOf(Layer.class);
        if (selfProtectionBuild && keepInstalled) layers.add(Layer.ACCESSIBILITY);
        if (dnsRequested) layers.add(Layer.DNS);
        return Collections.unmodifiableSet(layers);
    }

    /** What is actually running now, from the process's own live state. */
    public static Set<Layer> running(boolean accessibilityReady, boolean dnsRunning) {
        Set<Layer> layers = EnumSet.noneOf(Layer.class);
        if (accessibilityReady) layers.add(Layer.ACCESSIBILITY);
        if (dnsRunning) layers.add(Layer.DNS);
        return Collections.unmodifiableSet(layers);
    }

    /** Expected layers that are not running. */
    public static Set<Layer> lost(Set<Layer> expected, Set<Layer> running) {
        Set<Layer> layers = EnumSet.noneOf(Layer.class);
        for (Layer layer : expected) if (!running.contains(layer)) layers.add(layer);
        return layers;
    }

    /** Stable, order-independent key for "which layers are lost"; empty when all are up. */
    public static String signature(Set<Layer> lost) {
        StringBuilder text = new StringBuilder();
        for (Layer layer : Layer.values()) {
            if (!lost.contains(layer)) continue;
            if (text.length() > 0) text.append(',');
            text.append(layer.key);
        }
        return text.toString();
    }

    /**
     * Whether this loss needs a notice. A new set always does; the same set
     * repeats after {@code repeatMillis} so an ignored notice is not forgotten,
     * but a healthy check does not ping. A clock rollback is treated as due
     * rather than silencing the notice.
     */
    public static boolean shouldNotify(String previousSignature, long previousAt,
                                       String currentSignature, long now, long repeatMillis) {
        if (currentSignature == null || currentSignature.isEmpty()) return false;
        if (previousSignature == null || !currentSignature.equals(previousSignature)) return true;
        if (now < previousAt) return true;
        return now - previousAt >= repeatMillis;
    }
}
