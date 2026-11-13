package app.hisn.core;

public final class PersonalProfileTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Personal profile: " + label);
    }

    public static void main(String[] args) {
        check(PersonalProfile.cleanName(null).isEmpty(), "null name");
        check(PersonalProfile.cleanName("   ").isEmpty(), "blank name");
        check(PersonalProfile.cleanName("  Ahmed\n\tMohamed  ").equals("Ahmed Mohamed"),
                "name whitespace is one line");
        check(PersonalProfile.cleanName("A\u0007h\u0000med").equals("Ahmed"), "control characters dropped");
        check(PersonalProfile.cleanName("Ah\u202Emed\u2066.example").equals("Ahmed.example"),
                "directional overrides dropped");
        check(PersonalProfile.cleanName("\uFEFFAhmed\u200E").equals("Ahmed"), "marks and BOM dropped");
        check(PersonalProfile.cleanName("أحمد").equals("أحمد"), "Arabic name kept");
        check(PersonalProfile.cleanName("علی\u200Cرضا").equals("علی\u200Cرضا"),
                "zero-width non-joiner kept inside a name");
        check(PersonalProfile.cleanName("A\u00A0\u00A0B").equals("A B"), "non-breaking spaces collapse");
        check(PersonalProfile.cleanName(repeat('a', 30)).length() == PersonalProfile.MAX_NAME_CODE_POINTS,
                "name is bounded");
        String emoji = "\uD83D\uDE00";
        check(PersonalProfile.cleanName(repeat(emoji, 29)).codePointCount(0,
                PersonalProfile.cleanName(repeat(emoji, 29)).length()) == 24,
                "bounds count code points, not UTF-16 units");
        check(PersonalProfile.cleanName("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67")
                .equals("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"),
                "emoji joiners kept");

        check(PersonalProfile.cleanReason(repeat('b', 120)).length()
                == PersonalProfile.MAX_REASON_CODE_POINTS, "reason is bounded");
        check(PersonalProfile.cleanReason("For my family.\n").equals("For my family."),
                "reason trims the line break");

        check(PersonalProfile.cleanPreset("family").equals("family"), "known preset");
        check(PersonalProfile.cleanPreset(" FAMILY ").equals("family"), "preset is case-insensitive");
        check(PersonalProfile.cleanPreset("custom").equals(PersonalProfile.CUSTOM), "custom key");
        check(PersonalProfile.cleanPreset("unknown").isEmpty(), "unknown preset");
        check(PersonalProfile.cleanPreset(null).isEmpty(), "null preset");
        check(PersonalProfile.isPreset("rest") && !PersonalProfile.isPreset("custom")
                && !PersonalProfile.isPreset(null), "preset predicate");

        PersonalProfile preset = new PersonalProfile(" Ahmed ", "family", "stale words", 100, 3);
        check(preset.name.equals("Ahmed") && preset.hasName() && preset.hasReason(), "saved profile");
        check(preset.preset.equals("family") && preset.reason.isEmpty(), "preset drops stale words");
        check(preset.startDay == 100 && preset.pauses == 3 && preset.hasDetails(), "counters kept");

        PersonalProfile custom = new PersonalProfile("", "", "  For my family.  ", 0, 0);
        check(custom.preset.equals(PersonalProfile.CUSTOM) && custom.reason.equals("For my family."),
                "custom text implies a custom reason");
        check(!custom.hasName() && custom.hasReason(), "reason without a name");

        PersonalProfile emptyCustom = new PersonalProfile("Ahmed", PersonalProfile.CUSTOM, "  ", 0, 0);
        check(emptyCustom.preset.isEmpty() && !emptyCustom.hasReason(), "empty custom is no reason");
        check(emptyCustom.hasName(), "name survives an empty custom reason");

        PersonalProfile clamped = new PersonalProfile("A", "family", "", -4, -9);
        check(clamped.startDay == 0 && clamped.pauses == 0, "negative counters clamp");
        PersonalProfile capped = new PersonalProfile("A", "family", "", 1,
                PersonalProfile.MAX_PAUSES + 500);
        check(capped.pauses == PersonalProfile.MAX_PAUSES, "pause count is capped");
        check(!PersonalProfile.empty().hasDetails(), "empty profile");

        check(PersonalProfile.greeting(5) == PersonalProfile.MORNING, "morning starts at five");
        check(PersonalProfile.greeting(11) == PersonalProfile.MORNING, "morning ends at noon");
        check(PersonalProfile.greeting(12) == PersonalProfile.AFTERNOON, "afternoon starts at noon");
        check(PersonalProfile.greeting(17) == PersonalProfile.AFTERNOON, "afternoon ends at six");
        check(PersonalProfile.greeting(18) == PersonalProfile.EVENING, "evening starts at six");
        check(PersonalProfile.greeting(23) == PersonalProfile.EVENING, "late evening");
        check(PersonalProfile.greeting(0) == PersonalProfile.EVENING, "midnight");
        check(PersonalProfile.greeting(-1) == PersonalProfile.EVENING, "negative hour wraps");
        check(PersonalProfile.greeting(29) == PersonalProfile.MORNING, "hour wraps past 24");

        check(PersonalProfile.dayNumber(0, 100) == 0, "no start day");
        check(PersonalProfile.dayNumber(100, 100) == 1, "first day");
        check(PersonalProfile.dayNumber(100, 101) == 2, "second day");
        check(PersonalProfile.dayNumber(100, 99) == 1, "clock moved before the start");
        check(PersonalProfile.dayNumber(100, -1) == 0, "invalid today");
        check(PersonalProfile.dayNumber(1, Long.MAX_VALUE) == PersonalProfile.MAX_DAY_NUMBER,
                "day number is capped");

        check(PersonalProfile.nextPauseCount(0) == 1, "first pause");
        check(PersonalProfile.nextPauseCount(5) == 6, "next pause");
        check(PersonalProfile.nextPauseCount(-3) == 1, "damaged pause count");
        check(PersonalProfile.nextPauseCount(PersonalProfile.MAX_PAUSES)
                == PersonalProfile.MAX_PAUSES, "pause count stays capped");

        System.out.println("PersonalProfile tests passed (cleaning, bounds, presets, greetings, "
                + "day numbers and pause counts)");
    }

    private static String repeat(char value, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int index = 0; index < count; index++) text.append(value);
        return text.toString();
    }

    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) text.append(value);
        return text.toString();
    }
}
