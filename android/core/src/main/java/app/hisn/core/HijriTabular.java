package app.hisn.core;

/**
 * The tabular (arithmetic) Hijri calendar, used only where the system has no Umm al-Qura calendar
 * (Android 6). It can differ from the sighted or Umm al-Qura date by a day, so screens that show it
 * never present it as more exact than that.
 */
public final class HijriTabular {
    public final int year;
    public final int month;
    public final int day;

    private HijriTabular(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    /** Converts a Gregorian date (month 1-12) through the Julian day number. */
    public static HijriTabular fromGregorian(int y, int m, int d) {
        int a = (14 - m) / 12;
        int yy = y + 4800 - a;
        int mm = m + 12 * a - 3;
        long jdn = d + (153L * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
        long l = jdn - 1948440 + 10632;
        long n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        long j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int month = (int) ((24 * l) / 709);
        int day = (int) (l - (709L * month) / 24);
        int year = (int) (30 * n + j - 30);
        return new HijriTabular(year, month, day);
    }

    public static final String[] MONTHS_EN = {"Muharram", "Safar", "Rabiʿ I", "Rabiʿ II", "Jumada I", "Jumada II",
            "Rajab", "Shaʿban", "Ramadan", "Shawwal", "Dhu al-Qaʿda", "Dhu al-Hijja"};
    public static final String[] MONTHS_AR = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};
}
