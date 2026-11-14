package app.hisn.core;

import java.util.Locale;

public final class DeviceFamilyTest {
    private static DeviceFamily classify(String manufacturer, String brand, String model) {
        return DeviceFamily.classify(manufacturer, brand, model);
    }

    private static void expect(DeviceFamily expected, String manufacturer, String brand, String model) {
        DeviceFamily actual = classify(manufacturer, brand, model);
        if (actual != expected) {
            throw new AssertionError(manufacturer + " / " + brand + " / " + model
                    + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        // The three phones the owner named, plus the brands each maker ships.
        expect(DeviceFamily.SAMSUNG, "samsung", "samsung", "SM-G990B2");
        expect(DeviceFamily.SAMSUNG, "SAMSUNG", "SAMSUNG", "SM-S921B");
        expect(DeviceFamily.XIAOMI, "Xiaomi", "Redmi", "23021RAAEG");
        expect(DeviceFamily.XIAOMI, "Xiaomi", "POCO", "23122PCD1G");
        expect(DeviceFamily.XIAOMI, "Xiaomi", "Xiaomi", "2211133G");
        expect(DeviceFamily.XIAOMI, "Black Shark", null, null);
        expect(DeviceFamily.NOTHING, "Nothing", "Nothing", "A065");
        expect(DeviceFamily.NOTHING, "NOTHING TECHNOLOGY LIMITED", null, "A063");
        expect(DeviceFamily.STOCK, "Google", "google", "Pixel 9");
        expect(DeviceFamily.STOCK, "motorola", "motorola", "moto g84 5G");
        expect(DeviceFamily.STOCK, "Sony", "Sony", "XQ-DQ72");
        expect(DeviceFamily.STOCK, "HMD Global", "Nokia", "Nokia G42");
        expect(DeviceFamily.OPPO, "OPPO", "OPPO", "CPH2525");
        expect(DeviceFamily.OPPO, "realme", "realme", "RMX3782");
        expect(DeviceFamily.OPPO, "OnePlus", "OnePlus", "CPH2583");
        expect(DeviceFamily.VIVO, "vivo", "vivo", "V2312");
        expect(DeviceFamily.VIVO, "vivo", "iQOO", "I2302");
        expect(DeviceFamily.HUAWEI, "HUAWEI", "HUAWEI", "ANA-NX9");
        expect(DeviceFamily.HUAWEI, "HONOR", "HONOR", "ANY-NX1");
        expect(DeviceFamily.TRANSSION, "TECNO", "TECNO", "CL7");
        expect(DeviceFamily.TRANSSION, "Infinix", "Infinix", "X6835");
        expect(DeviceFamily.TRANSSION, "itel", "itel", "A665L");

        // Case, spaces, hyphens and dots are normalised before matching.
        expect(DeviceFamily.SAMSUNG, "  Samsung  ", "SAMSUNG", "SM-G990B2");
        expect(DeviceFamily.OPPO, "one-plus", "One Plus", null);

        // Unknown, missing and empty builds are never guessed into an aggressive family.
        expect(DeviceFamily.OTHER, "WingTech", "WingTech", "WT-1");
        expect(DeviceFamily.OTHER, null, null, null);
        expect(DeviceFamily.OTHER, "", "  ", "");
        if (DeviceFamily.classify((String[]) null) != DeviceFamily.OTHER) {
            throw new AssertionError("Null build names were classified");
        }
        if (DeviceFamily.classify() != DeviceFamily.OTHER) {
            throw new AssertionError("No build names were classified");
        }

        // The traits drive real rows, so an inconsistency is a visible bug.
        for (DeviceFamily family : DeviceFamily.values()) {
            String where = family.key;
            if (family.autostartScreen && !family.aggressiveBackgroundManager) {
                throw new AssertionError(where + ": an autostart screen implies an OEM manager");
            }
            if (family.parallelSpace && !family.aggressiveBackgroundManager) {
                throw new AssertionError(where + ": a second space implies an OEM profile layer");
            }
            if (family.key == null || family.key.isEmpty()) throw new AssertionError("Missing key");
            if (!family.key.equals(family.key.toLowerCase(Locale.ROOT))) {
                throw new AssertionError(where + ": the key must be lower case");
            }
        }
        // Nothing OS and stock android keep their promise: only Android's own switches.
        for (DeviceFamily family : new DeviceFamily[]{DeviceFamily.STOCK, DeviceFamily.NOTHING,
                DeviceFamily.OTHER}) {
            if (family.autostartScreen || family.recentsLock || family.parallelSpace
                    || family.sideloadGuard || family.aggressiveBackgroundManager) {
                throw new AssertionError(family.key + ": a plain family claims an OEM layer");
            }
        }
        if (!DeviceFamily.SAMSUNG.parallelSpace || !DeviceFamily.SAMSUNG.sideloadGuard) {
            throw new AssertionError("Samsung profile and Auto Blocker notes are missing");
        }
        if (!DeviceFamily.XIAOMI.autostartScreen || !DeviceFamily.XIAOMI.recentsLock) {
            throw new AssertionError("Xiaomi autostart or Recents guidance is missing");
        }
        System.out.println("DeviceFamily tests passed (every named build classified; "
                + DeviceFamily.values().length + " families consistent)");
    }
}
