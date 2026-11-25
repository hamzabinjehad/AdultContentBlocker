package app.hisn.core;

/**
 * The five prayer times (and sunrise) for a place and a date, computed on the phone with the
 * standard astronomical method used by prayer-time tables (the PrayTimes.org formulation). No
 * network and no location permission: the place comes from the user's city or time zone.
 *
 * <p>Times are minutes after local midnight, in the place's UTC offset for that date.
 */
public final class PrayerTimes {

    /** How Fajr and Isha are set. Angles in degrees below the horizon; Isha may be minutes after Maghrib. */
    public enum Method {
        MWL("Muslim World League", 18, 17, 0),
        UMM_AL_QURA("Umm al-Qura, Makkah", 18.5, 0, 90),
        EGYPT("Egyptian General Authority", 19.5, 17.5, 0),
        ISNA("ISNA, North America", 15, 15, 0),
        KARACHI("University of Karachi", 18, 18, 0);

        public final String label;
        final double fajrAngle;
        final double ishaAngle;
        /** Non-zero means Isha is this many minutes after Maghrib (Umm al-Qura). */
        final int ishaMinutes;

        Method(String label, double fajrAngle, double ishaAngle, int ishaMinutes) {
            this.label = label;
            this.fajrAngle = fajrAngle;
            this.ishaAngle = ishaAngle;
            this.ishaMinutes = ishaMinutes;
        }
    }

    /** Asr: shadow equal to the object (Shafi'i, Maliki, Hanbali) or twice it (Hanafi). */
    public enum Asr { STANDARD, HANAFI }

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    /** Minutes after local midnight for Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha. */
    public final int[] minutes;

    private PrayerTimes(int[] minutes) {
        this.minutes = minutes;
    }

    /** The five prayers only (no sunrise), in order. */
    public int[] five() {
        return new int[]{minutes[FAJR], minutes[DHUHR], minutes[ASR], minutes[MAGHRIB], minutes[ISHA]};
    }

    /**
     * @param year/month/day the local date (month 1-12)
     * @param latitude/longitude degrees, north and east positive
     * @param utcOffsetMinutes the place's offset from UTC on that date (e.g. +60 for Amsterdam in winter)
     */
    public static PrayerTimes compute(int year, int month, int day, double latitude, double longitude,
                                      int utcOffsetMinutes, Method method, Asr asr) {
        double jd = julian(year, month, day) - longitude / (15 * 24.0);
        double tz = utcOffsetMinutes / 60.0;

        // First approximation at default hours, then one refinement pass (enough for minutes).
        double[] t = {5, 6, 12, 13, 18, 18};
        for (int pass = 0; pass < 2; pass++) {
            double[] p = new double[6];
            for (int i = 0; i < 6; i++) p[i] = t[i] / 24.0;
            t[FAJR] = sunAngleTime(jd, p[FAJR], method.fajrAngle, latitude, true);
            t[SUNRISE] = sunAngleTime(jd, p[SUNRISE], 0.833, latitude, true);
            t[DHUHR] = midDay(jd, p[DHUHR]);
            t[ASR] = asrTime(jd, p[ASR], asr == Asr.HANAFI ? 2 : 1, latitude);
            t[MAGHRIB] = sunAngleTime(jd, p[MAGHRIB], 0.833, latitude, false);
            t[ISHA] = method.ishaMinutes > 0 ? t[MAGHRIB] : sunAngleTime(jd, p[ISHA], method.ishaAngle, latitude, false);
        }
        // High latitudes: when twilight never ends, use the "angle-based" night portion.
        double night = timeDiff(t[MAGHRIB], t[SUNRISE] + 24);
        double fajrMax = night * method.fajrAngle / 60.0;
        if (Double.isNaN(t[FAJR]) || timeDiff(t[FAJR], t[SUNRISE]) > fajrMax) t[FAJR] = t[SUNRISE] - fajrMax;
        if (method.ishaMinutes == 0) {
            double ishaMax = night * method.ishaAngle / 60.0;
            if (Double.isNaN(t[ISHA]) || timeDiff(t[MAGHRIB], t[ISHA]) > ishaMax) t[ISHA] = t[MAGHRIB] + ishaMax;
        }

        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            double local = t[i] + tz - longitude / 15.0;
            if (i == ISHA && method.ishaMinutes > 0) local += method.ishaMinutes / 60.0;
            // Dhuhr a minute after the zenith, as tables do.
            if (i == DHUHR) local += 1 / 60.0;
            int m = (int) Math.round(fix(local, 24) * 60);
            out[i] = m % (24 * 60);
        }
        return new PrayerTimes(out);
    }

    /** Index of the next prayer of the five (0 Fajr ... 4 Isha) after {@code nowMinutes}; 0 after Isha. */
    public int nextOfFive(int nowMinutes) {
        int[] f = five();
        for (int i = 0; i < f.length; i++) if (f[i] > nowMinutes) return i;
        return 0;
    }

    /** Minutes from now until the next of the five, wrapping to tomorrow's Fajr (approximated by today's). */
    public int minutesUntilNext(int nowMinutes) {
        int[] f = five();
        int i = nextOfFive(nowMinutes);
        int target = f[i];
        return target > nowMinutes ? target - nowMinutes : target + 24 * 60 - nowMinutes;
    }

    public static String hhmm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    // ---- astronomy (degrees) ----

    private static double julian(int year, int month, int day) {
        if (month <= 2) { year -= 1; month += 12; }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** Declination and equation of time for a Julian date. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * d, 360);
        double q = fix(280.459 + 0.98564736 * d, 360);
        double l = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = arctan2(cos(e) * sin(l), cos(l)) / 15.0;
        double eqt = q / 15.0 - fix(ra, 24);
        double decl = arcsin(sin(e) * sin(l));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double portion) {
        double eqt = sunPosition(jd + portion)[1];
        return fix(12 - eqt, 24);
    }

    private static double sunAngleTime(double jd, double portion, double angle, double lat, boolean beforeNoon) {
        double decl = sunPosition(jd + portion)[0];
        double noon = midDay(jd, portion);
        double cosT = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat));
        if (cosT < -1 || cosT > 1) return Double.NaN;
        double t = arccos(cosT) / 15.0;
        return noon + (beforeNoon ? -t : t);
    }

    private static double asrTime(double jd, double portion, int factor, double lat) {
        double decl = sunPosition(jd + portion)[0];
        double angle = -arccot(factor + tan(Math.abs(lat - decl)));
        return sunAngleTime(jd, portion, angle, lat, false);
    }

    private static double timeDiff(double a, double b) { return fix(b - a, 24); }
    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }
    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double tan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double arcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double arccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double arccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    private static double arctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
}
