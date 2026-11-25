package app.hisn.core;

public final class PrayerTimesTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Prayer times: " + label);
    }

    private static boolean between(int minutes, String from, String to) {
        return minutes >= parse(from) && minutes <= parse(to);
    }

    private static int parse(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3));
    }

    private static void ordered(PrayerTimes t, String label) {
        for (int i = 1; i < 6; i++) check(t.minutes[i] > t.minutes[i - 1], label + ": times are in order");
    }

    public static void main(String[] args) {
        // Greenwich, 3 November: the equation of time is about +16.4 min, so solar noon is ~11:43:36
        // UTC and Dhuhr (a minute after) is 11:44 or 11:45.
        PrayerTimes g = PrayerTimes.compute(2026, 11, 3, 51.48, 0.0, 0, PrayerTimes.Method.MWL, PrayerTimes.Asr.STANDARD);
        check(between(g.minutes[PrayerTimes.DHUHR], "11:44", "11:45"), "Dhuhr follows the equation of time: " + PrayerTimes.hhmm(g.minutes[PrayerTimes.DHUHR]));
        ordered(g, "Greenwich");

        // Makkah, 1 January (UTC+3), Umm al-Qura: published tables give about 05:38 Fajr, 06:59 sunrise,
        // 12:23 Dhuhr, 15:26 Asr, 17:48 Maghrib, 19:18 Isha.
        PrayerTimes m = PrayerTimes.compute(2026, 1, 1, 21.4225, 39.8262, 180, PrayerTimes.Method.UMM_AL_QURA, PrayerTimes.Asr.STANDARD);
        check(between(m.minutes[PrayerTimes.FAJR], "05:34", "05:42"), "Makkah Fajr " + PrayerTimes.hhmm(m.minutes[0]));
        check(between(m.minutes[PrayerTimes.SUNRISE], "06:55", "07:03"), "Makkah sunrise " + PrayerTimes.hhmm(m.minutes[1]));
        check(between(m.minutes[PrayerTimes.DHUHR], "12:20", "12:26"), "Makkah Dhuhr " + PrayerTimes.hhmm(m.minutes[2]));
        check(between(m.minutes[PrayerTimes.ASR], "15:22", "15:30"), "Makkah Asr " + PrayerTimes.hhmm(m.minutes[3]));
        check(between(m.minutes[PrayerTimes.MAGHRIB], "17:44", "17:52"), "Makkah Maghrib " + PrayerTimes.hhmm(m.minutes[4]));
        check(m.minutes[PrayerTimes.ISHA] - m.minutes[PrayerTimes.MAGHRIB] == 90, "Umm al-Qura Isha is 90 minutes after Maghrib");
        ordered(m, "Makkah");

        PrayerTimes hanafi = PrayerTimes.compute(2026, 1, 1, 21.4225, 39.8262, 180, PrayerTimes.Method.UMM_AL_QURA, PrayerTimes.Asr.HANAFI);
        check(hanafi.minutes[PrayerTimes.ASR] > m.minutes[PrayerTimes.ASR] + 30, "Hanafi Asr is later");

        // Oslo, 21 June: twilight never ends, yet Fajr and Isha still exist; Isha falls after midnight.
        PrayerTimes o = PrayerTimes.compute(2026, 6, 21, 59.91, 10.75, 120, PrayerTimes.Method.MWL, PrayerTimes.Asr.STANDARD);
        for (int i = 1; i < 5; i++) check(o.minutes[i] > o.minutes[i - 1], "Oslo: Fajr to Maghrib in order");
        int ishaAfter = ((o.minutes[PrayerTimes.ISHA] - o.minutes[PrayerTimes.MAGHRIB]) % 1440 + 1440) % 1440;
        check(ishaAfter >= 45 && ishaAfter <= 240, "Oslo: Isha a sensible time after Maghrib");
        check(between(o.minutes[PrayerTimes.FAJR], "01:00", "03:30"), "Oslo: Fajr before sunrise " + PrayerTimes.hhmm(o.minutes[0]));
        System.out.println("Oslo 21 June: " + PrayerTimes.hhmm(o.minutes[0]) + " " + PrayerTimes.hhmm(o.minutes[1]) + " " + PrayerTimes.hhmm(o.minutes[2]) + " " + PrayerTimes.hhmm(o.minutes[3]) + " " + PrayerTimes.hhmm(o.minutes[4]) + " " + PrayerTimes.hhmm(o.minutes[5]));
        System.out.println("Makkah 1 Jan: " + PrayerTimes.hhmm(m.minutes[0]) + " " + PrayerTimes.hhmm(m.minutes[1]) + " " + PrayerTimes.hhmm(m.minutes[2]) + " " + PrayerTimes.hhmm(m.minutes[3]) + " " + PrayerTimes.hhmm(m.minutes[4]) + " " + PrayerTimes.hhmm(m.minutes[5]));

        check(m.nextOfFive(parse("13:00")) == 2, "after Dhuhr the next is Asr");
        check(m.nextOfFive(parse("23:00")) == 0, "after Isha the next is Fajr");
        check(m.minutesUntilNext(parse("23:00")) == m.minutes[PrayerTimes.FAJR] + 60, "wraps to tomorrow's Fajr");
        check(PrayerTimes.hhmm(65).equals("01:05") && PrayerTimes.hhmm(-5).equals("23:55"), "hh:mm formatting");
        check(PrayerPlaces.forZone("Europe/Amsterdam").key.equals("amsterdam"), "city from the time zone");
        check(PrayerPlaces.forZone("Asia/Riyadh").method == PrayerTimes.Method.UMM_AL_QURA, "Saudi cities use Umm al-Qura");
        check(PrayerPlaces.forZone("Pacific/Nowhere").key.equals("makkah"), "unknown zone falls back to Makkah");
        check(PrayerPlaces.byKey("cairo").method == PrayerTimes.Method.EGYPT, "Cairo uses the Egyptian method");
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (PrayerPlaces.Place pl : PrayerPlaces.ALL) {
            check(keys.add(pl.key), "unique city key " + pl.key);
            check(java.util.TimeZone.getTimeZone(pl.zone).getID().equals(pl.zone), "known zone " + pl.zone);
            check(Math.abs(pl.latitude) < 70 && Math.abs(pl.longitude) <= 180, "sane coordinates " + pl.key);
        }
        System.out.println("Prayer times: ok");
    }
}
