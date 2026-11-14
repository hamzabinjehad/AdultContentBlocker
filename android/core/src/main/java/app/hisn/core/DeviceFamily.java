package app.hisn.core;

import java.util.Locale;

/**
 * The phone makers whose background management changes whether Hisn's services keep running.
 *
 * <p>Android itself can pause any background app, but several makers add their own manager on
 * top: Xiaomi, Oppo/Realme/OnePlus, Vivo/iQOO, Huawei/Honor and Transsion require an
 * "autostart" switch, Samsung and the Chinese ROMs favour apps that are locked in Recents, and
 * Samsung/Oppo/Vivo/Xiaomi/Transsion ship a second space that a single installation does not
 * cover. Nothing OS and the stock builds only use Android's own battery setting.</p>
 *
 * <p>This class is data, not a device check: the app classifies the phone from
 * {@code Build.MANUFACTURER}, {@code Build.BRAND} and {@code Build.MODEL}, then every step it
 * shows is backed by a live Android reading or by the owner confirming an OEM switch that
 * Android does not expose. A family never turns a missing permission into "protected".</p>
 */
public enum DeviceFamily {
    /** Google, Pixel, Motorola, Sony, Nokia/HMD, Fairphone and other builds without an OEM manager. */
    STOCK("stock", false, false, false, false, false),
    /** Nothing OS 2 and later: close to stock Android, no OEM autostart screen. */
    NOTHING("nothing", false, false, false, false, false),
    SAMSUNG("samsung", false, true, true, true, true),
    XIAOMI("xiaomi", true, true, true, true, true),
    /** ColorOS: Oppo, Realme and OnePlus. */
    OPPO("oppo", true, true, true, false, true),
    /** OriginOS / Funtouch: Vivo and iQOO. */
    VIVO("vivo", true, true, true, false, true),
    /** EMUI / MagicOS: Huawei and Honor. */
    HUAWEI("huawei", true, true, true, false, true),
    /** HiOS / XOS: Tecno, Infinix and itel. */
    TRANSSION("transsion", true, true, true, true, true),
    /** A maker this build does not know; only Android's own readings are shown. */
    OTHER("other", false, false, false, false, false);

    /** Stable key for stored confirmations and tests; never translated. */
    public final String key;

    /** The maker has a launch-in-background / autostart screen Hisn can try to open. */
    public final boolean autostartScreen;

    /** Apps that are not locked in Recents are commonly killed by this ROM. */
    public final boolean recentsLock;

    /** The phone can run a second space or dual-app profile that a single install does not cover. */
    public final boolean parallelSpace;

    /** Installing an update by hand crosses an extra maker switch (Samsung Auto Blocker and others). */
    public final boolean sideloadGuard;

    /** The ROM's own background manager commonly stops apps that were left alone. */
    public final boolean aggressiveBackgroundManager;

    DeviceFamily(String key, boolean autostartScreen, boolean recentsLock, boolean parallelSpace,
            boolean sideloadGuard, boolean aggressiveBackgroundManager) {
        this.key = key;
        this.autostartScreen = autostartScreen;
        this.recentsLock = recentsLock;
        this.parallelSpace = parallelSpace;
        this.sideloadGuard = sideloadGuard;
        this.aggressiveBackgroundManager = aggressiveBackgroundManager;
    }

    /**
     * Classifies the phone from the build strings. Any of them may be null; the first recognised
     * maker wins, so the manufacturer is read before a model name that happens to contain a brand.
     * An unrecognised build is {@link #OTHER}, never guessed into an aggressive family.
     */
    public static DeviceFamily classify(String... names) {
        if (names == null) return OTHER;
        for (String name : names) {
            String normalized = normalize(name);
            if (normalized.isEmpty()) continue;
            if (normalized.contains("samsung")) return SAMSUNG;
            if (normalized.contains("xiaomi") || normalized.contains("redmi")
                    || normalized.contains("poco") || normalized.contains("blackshark")) return XIAOMI;
            if (normalized.contains("nothing")) return NOTHING;
            if (normalized.contains("oneplus") || normalized.contains("oppo")
                    || normalized.contains("realme")) return OPPO;
            if (normalized.contains("vivo") || normalized.contains("iqoo")) return VIVO;
            if (normalized.contains("huawei") || normalized.contains("honor")) return HUAWEI;
            if (normalized.contains("tecno") || normalized.contains("infinix")
                    || normalized.contains("itel")) return TRANSSION;
            if (normalized.contains("google") || normalized.contains("pixel")
                    || normalized.contains("motorola") || normalized.contains("moto")
                    || normalized.contains("sony") || normalized.contains("nokia")
                    || normalized.contains("hmd") || normalized.contains("fairphone")) return STOCK;
        }
        return OTHER;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (character == ' ' || character == '-' || character == '_' || character == '.') continue;
            normalized.append(character);
        }
        return normalized.toString();
    }
}
