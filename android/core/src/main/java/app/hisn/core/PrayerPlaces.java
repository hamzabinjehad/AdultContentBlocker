package app.hisn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cities for prayer times, chosen without any location permission: the default is the city of the
 * phone's time zone, and the user can pick another in Settings. Each city carries the calculation
 * method its people usually follow.
 */
public final class PrayerPlaces {

    public static final class Place {
        public final String key;
        public final String en;
        public final String ar;
        public final double latitude;
        public final double longitude;
        public final String zone;
        public final PrayerTimes.Method method;

        Place(String key, String en, String ar, double latitude, double longitude, String zone, PrayerTimes.Method method) {
            this.key = key;
            this.en = en;
            this.ar = ar;
            this.latitude = latitude;
            this.longitude = longitude;
            this.zone = zone;
            this.method = method;
        }
    }

    private static final PrayerTimes.Method MWL = PrayerTimes.Method.MWL;
    private static final PrayerTimes.Method UQ = PrayerTimes.Method.UMM_AL_QURA;
    private static final PrayerTimes.Method EG = PrayerTimes.Method.EGYPT;
    private static final PrayerTimes.Method NA = PrayerTimes.Method.ISNA;
    private static final PrayerTimes.Method KA = PrayerTimes.Method.KARACHI;

    public static final List<Place> ALL;

    static {
        List<Place> p = new ArrayList<>();
        p.add(new Place("makkah", "Makkah", "مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh", UQ));
        p.add(new Place("madinah", "Madinah", "المدينة المنورة", 24.4686, 39.6142, "Asia/Riyadh", UQ));
        p.add(new Place("riyadh", "Riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh", UQ));
        p.add(new Place("jeddah", "Jeddah", "جدة", 21.4858, 39.1925, "Asia/Riyadh", UQ));
        p.add(new Place("dubai", "Dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai", UQ));
        p.add(new Place("doha", "Doha", "الدوحة", 25.2854, 51.531, "Asia/Qatar", UQ));
        p.add(new Place("kuwait", "Kuwait City", "الكويت", 29.3759, 47.9774, "Asia/Kuwait", UQ));
        p.add(new Place("manama", "Manama", "المنامة", 26.2285, 50.586, "Asia/Bahrain", UQ));
        p.add(new Place("muscat", "Muscat", "مسقط", 23.588, 58.3829, "Asia/Muscat", UQ));
        p.add(new Place("sanaa", "Sanaa", "صنعاء", 15.3694, 44.191, "Asia/Aden", UQ));
        p.add(new Place("amman", "Amman", "عمّان", 31.9454, 35.9284, "Asia/Amman", MWL));
        p.add(new Place("jerusalem", "Jerusalem", "القدس", 31.7683, 35.2137, "Asia/Jerusalem", MWL));
        p.add(new Place("gaza", "Gaza", "غزة", 31.5017, 34.4668, "Asia/Gaza", MWL));
        p.add(new Place("damascus", "Damascus", "دمشق", 33.5138, 36.2765, "Asia/Damascus", MWL));
        p.add(new Place("beirut", "Beirut", "بيروت", 33.8938, 35.5018, "Asia/Beirut", MWL));
        p.add(new Place("baghdad", "Baghdad", "بغداد", 33.3152, 44.3661, "Asia/Baghdad", MWL));
        p.add(new Place("cairo", "Cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo", EG));
        p.add(new Place("khartoum", "Khartoum", "الخرطوم", 15.5007, 32.5599, "Africa/Khartoum", EG));
        p.add(new Place("tripoli", "Tripoli", "طرابلس", 32.8872, 13.1913, "Africa/Tripoli", EG));
        p.add(new Place("tunis", "Tunis", "تونس", 36.8065, 10.1815, "Africa/Tunis", MWL));
        p.add(new Place("algiers", "Algiers", "الجزائر", 36.7538, 3.0588, "Africa/Algiers", MWL));
        p.add(new Place("casablanca", "Casablanca", "الدار البيضاء", 33.5731, -7.5898, "Africa/Casablanca", MWL));
        p.add(new Place("nouakchott", "Nouakchott", "نواكشوط", 18.0735, -15.9582, "Africa/Nouakchott", MWL));
        p.add(new Place("mogadishu", "Mogadishu", "مقديشو", 2.0469, 45.3182, "Africa/Mogadishu", MWL));
        p.add(new Place("nairobi", "Nairobi", "نيروبي", -1.2921, 36.8219, "Africa/Nairobi", MWL));
        p.add(new Place("lagos", "Lagos", "لاغوس", 6.5244, 3.3792, "Africa/Lagos", MWL));
        p.add(new Place("dakar", "Dakar", "داكار", 14.7167, -17.4677, "Africa/Dakar", MWL));
        p.add(new Place("johannesburg", "Johannesburg", "جوهانسبرغ", -26.2041, 28.0473, "Africa/Johannesburg", MWL));
        p.add(new Place("istanbul", "Istanbul", "إسطنبول", 41.0082, 28.9784, "Europe/Istanbul", MWL));
        p.add(new Place("tehran", "Tehran", "طهران", 35.6892, 51.389, "Asia/Tehran", MWL));
        p.add(new Place("kabul", "Kabul", "كابل", 34.5553, 69.2075, "Asia/Kabul", KA));
        p.add(new Place("karachi", "Karachi", "كراتشي", 24.8607, 67.0011, "Asia/Karachi", KA));
        p.add(new Place("lahore", "Lahore", "لاهور", 31.5204, 74.3587, "Asia/Karachi", KA));
        p.add(new Place("delhi", "Delhi", "دلهي", 28.6139, 77.209, "Asia/Kolkata", KA));
        p.add(new Place("dhaka", "Dhaka", "دكا", 23.8103, 90.4125, "Asia/Dhaka", KA));
        p.add(new Place("tashkent", "Tashkent", "طشقند", 41.2995, 69.2401, "Asia/Tashkent", MWL));
        p.add(new Place("almaty", "Almaty", "ألماتي", 43.222, 76.8512, "Asia/Almaty", MWL));
        p.add(new Place("baku", "Baku", "باكو", 40.4093, 49.8671, "Asia/Baku", MWL));
        p.add(new Place("kualalumpur", "Kuala Lumpur", "كوالالمبور", 3.139, 101.6869, "Asia/Kuala_Lumpur", MWL));
        p.add(new Place("jakarta", "Jakarta", "جاكرتا", -6.2088, 106.8456, "Asia/Jakarta", MWL));
        p.add(new Place("singapore", "Singapore", "سنغافورة", 1.3521, 103.8198, "Asia/Singapore", MWL));
        p.add(new Place("london", "London", "لندن", 51.5074, -0.1278, "Europe/London", MWL));
        p.add(new Place("dublin", "Dublin", "دبلن", 53.3498, -6.2603, "Europe/Dublin", MWL));
        p.add(new Place("paris", "Paris", "باريس", 48.8566, 2.3522, "Europe/Paris", MWL));
        p.add(new Place("brussels", "Brussels", "بروكسل", 50.8503, 4.3517, "Europe/Brussels", MWL));
        p.add(new Place("amsterdam", "Amsterdam", "أمستردام", 52.3676, 4.9041, "Europe/Amsterdam", MWL));
        p.add(new Place("berlin", "Berlin", "برلين", 52.52, 13.405, "Europe/Berlin", MWL));
        p.add(new Place("vienna", "Vienna", "فيينا", 48.2082, 16.3738, "Europe/Vienna", MWL));
        p.add(new Place("zurich", "Zurich", "زيورخ", 47.3769, 8.5417, "Europe/Zurich", MWL));
        p.add(new Place("madrid", "Madrid", "مدريد", 40.4168, -3.7038, "Europe/Madrid", MWL));
        p.add(new Place("rome", "Rome", "روما", 41.9028, 12.4964, "Europe/Rome", MWL));
        p.add(new Place("stockholm", "Stockholm", "ستوكهولم", 59.3293, 18.0686, "Europe/Stockholm", MWL));
        p.add(new Place("oslo", "Oslo", "أوسلو", 59.9139, 10.7522, "Europe/Oslo", MWL));
        p.add(new Place("copenhagen", "Copenhagen", "كوبنهاغن", 55.6761, 12.5683, "Europe/Copenhagen", MWL));
        p.add(new Place("sarajevo", "Sarajevo", "سراييفو", 43.8563, 18.4131, "Europe/Sarajevo", MWL));
        p.add(new Place("moscow", "Moscow", "موسكو", 55.7558, 37.6173, "Europe/Moscow", MWL));
        p.add(new Place("newyork", "New York", "نيويورك", 40.7128, -74.006, "America/New_York", NA));
        p.add(new Place("chicago", "Chicago", "شيكاغو", 41.8781, -87.6298, "America/Chicago", NA));
        p.add(new Place("denver", "Denver", "دنفر", 39.7392, -104.9903, "America/Denver", NA));
        p.add(new Place("losangeles", "Los Angeles", "لوس أنجلوس", 34.0522, -118.2437, "America/Los_Angeles", NA));
        p.add(new Place("toronto", "Toronto", "تورنتو", 43.6532, -79.3832, "America/Toronto", NA));
        p.add(new Place("saopaulo", "São Paulo", "ساو باولو", -23.5505, -46.6333, "America/Sao_Paulo", MWL));
        p.add(new Place("sydney", "Sydney", "سيدني", -33.8688, 151.2093, "Australia/Sydney", MWL));
        ALL = Collections.unmodifiableList(p);
    }

    public static Place byKey(String key) {
        if (key != null) for (Place p : ALL) if (p.key.equals(key)) return p;
        return null;
    }

    /** The first city in the phone's time zone; Makkah when the zone is not in the list. */
    public static Place forZone(String zoneId) {
        if (zoneId != null) for (Place p : ALL) if (p.zone.equals(zoneId)) return p;
        return ALL.get(0);
    }
}
