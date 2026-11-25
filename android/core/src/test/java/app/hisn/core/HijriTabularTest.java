package app.hisn.core;

public final class HijriTabularTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Hijri: " + label);
    }

    public static void main(String[] args) {
        // 23 November 2026 is 13 Jumada II 1448 (Umm al-Qura); the tabular calendar may be a day off.
        HijriTabular h = HijriTabular.fromGregorian(2026, 11, 23);
        check(h.year == 1448 && h.month == 6 && Math.abs(h.day - 13) <= 1, "23 Nov 2026: " + h.day + "/" + h.month + "/" + h.year);
        // 1 Muharram 1446 was 7 July 2024 (Umm al-Qura).
        HijriTabular m = HijriTabular.fromGregorian(2024, 7, 7);
        check((m.year == 1446 && m.month == 1 && m.day <= 2) || (m.year == 1445 && m.month == 12 && m.day >= 29), "new year 1446");
        // The epoch: 16 July 622 (Julian) is 1 Muharram 1; 19 July 622 Gregorian proleptic.
        HijriTabular e = HijriTabular.fromGregorian(622, 7, 19);
        check(e.year == 1 && e.month == 1 && e.day == 1, "the Hijri epoch");
        check(HijriTabular.MONTHS_AR.length == 12 && HijriTabular.MONTHS_EN.length == 12, "twelve month names");
        System.out.println("Hijri: ok");
    }
}
