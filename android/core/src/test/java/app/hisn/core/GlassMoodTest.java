package app.hisn.core;

public final class GlassMoodTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Glass mood: " + label); }

    public static void main(String[] args) {
        check(GlassMood.of(GlassState.Look.BROKEN, true, true) == GlassMood.SLIP, "a broken glass comes before everything");
        check(GlassMood.of(GlassState.Look.CRACKED, true, true) == GlassMood.HARD, "a crack is never hidden by Focus or night");
        check(GlassMood.of(GlassState.Look.CRACKED, false, false) == GlassMood.HARD, "a crack alone is a hard moment");
        check(GlassMood.of(GlassState.Look.CLEAR, true, true) == GlassMood.NIGHT, "night comes before Focus");
        check(GlassMood.of(GlassState.Look.GOLD, false, true) == GlassMood.NIGHT, "night comes before the gold seal");
        check(GlassMood.of(GlassState.Look.CLEAR, true, false) == GlassMood.WORKING, "Focus is working");
        check(GlassMood.of(GlassState.Look.GOLD, true, false) == GlassMood.WORKING, "Focus after a seal is still working");
        check(GlassMood.of(GlassState.Look.GOLD, false, false) == GlassMood.REST, "sealed today rests");
        check(GlassMood.of(GlassState.Look.CLEAR, false, false) == GlassMood.GROWING, "a clear glass grows");
        System.out.println("GlassMood tests passed (slip first, crack over Focus and night, night over Focus, rest after a seal, growing by default)");
    }
}
