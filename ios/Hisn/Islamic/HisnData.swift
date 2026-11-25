import Foundation

// The Islamic edition's day-to-day data on iPhone, the same as Android's: prayer times computed on the
// phone (no location permission; the city comes from the time zone or the person's choice), the
// scholars' sayings bundled from Android's sayings.tsv, and the nights of the journey.

// MARK: - Prayer times (port of app.hisn.core.PrayerTimes, the PrayTimes.org formulation)

enum HisnPrayerMethod: String {
    case mwl, ummAlQura, egypt, isna, karachi
    var fajrAngle: Double { [.mwl: 18, .ummAlQura: 18.5, .egypt: 19.5, .isna: 15, .karachi: 18][self]! }
    var ishaAngle: Double { [.mwl: 17, .ummAlQura: 0, .egypt: 17.5, .isna: 15, .karachi: 18][self]! }
    /// Non-zero: Isha is this many minutes after Maghrib (Umm al-Qura).
    var ishaMinutes: Int { self == .ummAlQura ? 90 : 0 }
}

struct HisnPrayerTimes {
    static let fajr = 0, sunrise = 1, dhuhr = 2, asr = 3, maghrib = 4, isha = 5
    /// Minutes after local midnight for Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha.
    let minutes: [Int]
    var five: [Int] { [minutes[0], minutes[2], minutes[3], minutes[4], minutes[5]] }

    static func compute(year: Int, month: Int, day: Int, latitude: Double, longitude: Double,
                        utcOffsetMinutes: Int, method: HisnPrayerMethod, hanafi: Bool) -> HisnPrayerTimes {
        let jd = julian(year, month, day) - longitude / (15 * 24.0)
        let tz = Double(utcOffsetMinutes) / 60
        var t: [Double] = [5, 6, 12, 13, 18, 18]
        for _ in 0..<2 {
            let p = t.map { $0 / 24 }
            t[fajr] = sunAngleTime(jd, p[fajr], method.fajrAngle, latitude, true)
            t[sunrise] = sunAngleTime(jd, p[sunrise], 0.833, latitude, true)
            t[dhuhr] = midDay(jd, p[dhuhr])
            t[asr] = asrTime(jd, p[asr], hanafi ? 2 : 1, latitude)
            t[maghrib] = sunAngleTime(jd, p[maghrib], 0.833, latitude, false)
            t[isha] = method.ishaMinutes > 0 ? t[maghrib] : sunAngleTime(jd, p[isha], method.ishaAngle, latitude, false)
        }
        let night = timeDiff(t[maghrib], t[sunrise] + 24)
        let fajrMax = night * method.fajrAngle / 60
        if t[fajr].isNaN || timeDiff(t[fajr], t[sunrise]) > fajrMax { t[fajr] = t[sunrise] - fajrMax }
        if method.ishaMinutes == 0 {
            let ishaMax = night * method.ishaAngle / 60
            if t[isha].isNaN || timeDiff(t[maghrib], t[isha]) > ishaMax { t[isha] = t[maghrib] + ishaMax }
        }
        var out = [Int](repeating: 0, count: 6)
        for i in 0..<6 {
            var local = t[i] + tz - longitude / 15
            if i == isha && method.ishaMinutes > 0 { local += Double(method.ishaMinutes) / 60 }
            if i == dhuhr { local += 1.0 / 60 }
            out[i] = Int((fix(local, 24) * 60).rounded()) % (24 * 60)
        }
        return HisnPrayerTimes(minutes: out)
    }

    /// Index of the next of the five after `now` (minutes); 0 (tomorrow's Fajr) after Isha.
    func nextOfFive(_ now: Int) -> Int { five.firstIndex { $0 > now } ?? 0 }
    func minutesUntilNext(_ now: Int) -> Int {
        let target = five[nextOfFive(now)]
        return target > now ? target - now : target + 1440 - now
    }
    static func hhmm(_ m: Int) -> String { let v = ((m % 1440) + 1440) % 1440; return String(format: "%02d:%02d", v / 60, v % 60) }

    private static func julian(_ y0: Int, _ m0: Int, _ d: Int) -> Double {
        var y = Double(y0), m = Double(m0)
        if m <= 2 { y -= 1; m += 12 }
        let a = (y / 100).rounded(.down), b = 2 - a + (a / 4).rounded(.down)
        return (365.25 * (y + 4716)).rounded(.down) + (30.6001 * (m + 1)).rounded(.down) + Double(d) + b - 1524.5
    }
    private static func sunPosition(_ jd: Double) -> (decl: Double, eqt: Double) {
        let d = jd - 2451545.0
        let g = fix(357.529 + 0.98560028 * d, 360), q = fix(280.459 + 0.98564736 * d, 360)
        let l = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360)
        let e = 23.439 - 0.00000036 * d
        let ra = atan2d(cos(e) * sin(l), cos(l)) / 15
        return (asind(sin(e) * sin(l)), q / 15 - fix(ra, 24))
    }
    private static func midDay(_ jd: Double, _ portion: Double) -> Double { fix(12 - sunPosition(jd + portion).eqt, 24) }
    private static func sunAngleTime(_ jd: Double, _ portion: Double, _ angle: Double, _ lat: Double, _ beforeNoon: Bool) -> Double {
        let decl = sunPosition(jd + portion).decl, noon = midDay(jd, portion)
        let cosT = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat))
        if cosT < -1 || cosT > 1 { return .nan }
        let t = acosd(cosT) / 15
        return noon + (beforeNoon ? -t : t)
    }
    private static func asrTime(_ jd: Double, _ portion: Double, _ factor: Double, _ lat: Double) -> Double {
        let decl = sunPosition(jd + portion).decl
        let angle = -atand(1 / (factor + tan(abs(lat - decl))))
        return sunAngleTime(jd, portion, angle, lat, false)
    }
    private static func timeDiff(_ a: Double, _ b: Double) -> Double { fix(b - a, 24) }
    private static func fix(_ a: Double, _ b: Double) -> Double { let r = a - b * (a / b).rounded(.down); return r < 0 ? r + b : r }
    private static func sin(_ d: Double) -> Double { Foundation.sin(d * .pi / 180) }
    private static func cos(_ d: Double) -> Double { Foundation.cos(d * .pi / 180) }
    private static func tan(_ d: Double) -> Double { Foundation.tan(d * .pi / 180) }
    private static func asind(_ x: Double) -> Double { Foundation.asin(x) * 180 / .pi }
    private static func acosd(_ x: Double) -> Double { Foundation.acos(x) * 180 / .pi }
    private static func atand(_ x: Double) -> Double { Foundation.atan(x) * 180 / .pi }
    private static func atan2d(_ y: Double, _ x: Double) -> Double { Foundation.atan2(y, x) * 180 / .pi }
}

struct HisnPlace: Identifiable {
    let key, en, ar: String
    let latitude, longitude: Double
    let zone: String
    let method: HisnPrayerMethod
    var id: String { key }
    var name: String { ia(en, ar) }

    static let all: [HisnPlace] = {
        let uq = HisnPrayerMethod.ummAlQura, mwl = HisnPrayerMethod.mwl, eg = HisnPrayerMethod.egypt
        let na = HisnPrayerMethod.isna, ka = HisnPrayerMethod.karachi
        let rows: [(String, String, String, Double, Double, String, HisnPrayerMethod)] = [
            ("makkah", "Makkah", "مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh", uq), ("madinah", "Madinah", "المدينة المنورة", 24.4686, 39.6142, "Asia/Riyadh", uq),
            ("riyadh", "Riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh", uq), ("jeddah", "Jeddah", "جدة", 21.4858, 39.1925, "Asia/Riyadh", uq),
            ("dubai", "Dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai", uq), ("doha", "Doha", "الدوحة", 25.2854, 51.531, "Asia/Qatar", uq),
            ("kuwait", "Kuwait City", "الكويت", 29.3759, 47.9774, "Asia/Kuwait", uq), ("manama", "Manama", "المنامة", 26.2285, 50.586, "Asia/Bahrain", uq),
            ("muscat", "Muscat", "مسقط", 23.588, 58.3829, "Asia/Muscat", uq), ("sanaa", "Sanaa", "صنعاء", 15.3694, 44.191, "Asia/Aden", uq),
            ("amman", "Amman", "عمّان", 31.9454, 35.9284, "Asia/Amman", mwl), ("jerusalem", "Jerusalem", "القدس", 31.7683, 35.2137, "Asia/Jerusalem", mwl),
            ("gaza", "Gaza", "غزة", 31.5017, 34.4668, "Asia/Gaza", mwl), ("damascus", "Damascus", "دمشق", 33.5138, 36.2765, "Asia/Damascus", mwl),
            ("beirut", "Beirut", "بيروت", 33.8938, 35.5018, "Asia/Beirut", mwl), ("baghdad", "Baghdad", "بغداد", 33.3152, 44.3661, "Asia/Baghdad", mwl),
            ("cairo", "Cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo", eg), ("khartoum", "Khartoum", "الخرطوم", 15.5007, 32.5599, "Africa/Khartoum", eg),
            ("tripoli", "Tripoli", "طرابلس", 32.8872, 13.1913, "Africa/Tripoli", eg), ("tunis", "Tunis", "تونس", 36.8065, 10.1815, "Africa/Tunis", mwl),
            ("algiers", "Algiers", "الجزائر", 36.7538, 3.0588, "Africa/Algiers", mwl), ("casablanca", "Casablanca", "الدار البيضاء", 33.5731, -7.5898, "Africa/Casablanca", mwl),
            ("nouakchott", "Nouakchott", "نواكشوط", 18.0735, -15.9582, "Africa/Nouakchott", mwl), ("mogadishu", "Mogadishu", "مقديشو", 2.0469, 45.3182, "Africa/Mogadishu", mwl),
            ("nairobi", "Nairobi", "نيروبي", -1.2921, 36.8219, "Africa/Nairobi", mwl), ("lagos", "Lagos", "لاغوس", 6.5244, 3.3792, "Africa/Lagos", mwl),
            ("dakar", "Dakar", "داكار", 14.7167, -17.4677, "Africa/Dakar", mwl), ("johannesburg", "Johannesburg", "جوهانسبرغ", -26.2041, 28.0473, "Africa/Johannesburg", mwl),
            ("istanbul", "Istanbul", "إسطنبول", 41.0082, 28.9784, "Europe/Istanbul", mwl), ("tehran", "Tehran", "طهران", 35.6892, 51.389, "Asia/Tehran", mwl),
            ("kabul", "Kabul", "كابل", 34.5553, 69.2075, "Asia/Kabul", ka), ("karachi", "Karachi", "كراتشي", 24.8607, 67.0011, "Asia/Karachi", ka),
            ("lahore", "Lahore", "لاهور", 31.5204, 74.3587, "Asia/Karachi", ka), ("delhi", "Delhi", "دلهي", 28.6139, 77.209, "Asia/Kolkata", ka),
            ("dhaka", "Dhaka", "دكا", 23.8103, 90.4125, "Asia/Dhaka", ka), ("tashkent", "Tashkent", "طشقند", 41.2995, 69.2401, "Asia/Tashkent", mwl),
            ("almaty", "Almaty", "ألماتي", 43.222, 76.8512, "Asia/Almaty", mwl), ("baku", "Baku", "باكو", 40.4093, 49.8671, "Asia/Baku", mwl),
            ("kualalumpur", "Kuala Lumpur", "كوالالمبور", 3.139, 101.6869, "Asia/Kuala_Lumpur", mwl), ("jakarta", "Jakarta", "جاكرتا", -6.2088, 106.8456, "Asia/Jakarta", mwl),
            ("singapore", "Singapore", "سنغافورة", 1.3521, 103.8198, "Asia/Singapore", mwl), ("london", "London", "لندن", 51.5074, -0.1278, "Europe/London", mwl),
            ("dublin", "Dublin", "دبلن", 53.3498, -6.2603, "Europe/Dublin", mwl), ("paris", "Paris", "باريس", 48.8566, 2.3522, "Europe/Paris", mwl),
            ("brussels", "Brussels", "بروكسل", 50.8503, 4.3517, "Europe/Brussels", mwl), ("amsterdam", "Amsterdam", "أمستردام", 52.3676, 4.9041, "Europe/Amsterdam", mwl),
            ("berlin", "Berlin", "برلين", 52.52, 13.405, "Europe/Berlin", mwl), ("vienna", "Vienna", "فيينا", 48.2082, 16.3738, "Europe/Vienna", mwl),
            ("zurich", "Zurich", "زيورخ", 47.3769, 8.5417, "Europe/Zurich", mwl), ("madrid", "Madrid", "مدريد", 40.4168, -3.7038, "Europe/Madrid", mwl),
            ("rome", "Rome", "روما", 41.9028, 12.4964, "Europe/Rome", mwl), ("stockholm", "Stockholm", "ستوكهولم", 59.3293, 18.0686, "Europe/Stockholm", mwl),
            ("oslo", "Oslo", "أوسلو", 59.9139, 10.7522, "Europe/Oslo", mwl), ("copenhagen", "Copenhagen", "كوبنهاغن", 55.6761, 12.5683, "Europe/Copenhagen", mwl),
            ("sarajevo", "Sarajevo", "سراييفو", 43.8563, 18.4131, "Europe/Sarajevo", mwl), ("moscow", "Moscow", "موسكو", 55.7558, 37.6173, "Europe/Moscow", mwl),
            ("newyork", "New York", "نيويورك", 40.7128, -74.006, "America/New_York", na), ("chicago", "Chicago", "شيكاغو", 41.8781, -87.6298, "America/Chicago", na),
            ("denver", "Denver", "دنفر", 39.7392, -104.9903, "America/Denver", na), ("losangeles", "Los Angeles", "لوس أنجلوس", 34.0522, -118.2437, "America/Los_Angeles", na),
            ("toronto", "Toronto", "تورنتو", 43.6532, -79.3832, "America/Toronto", na), ("saopaulo", "São Paulo", "ساو باولو", -23.5505, -46.6333, "America/Sao_Paulo", mwl),
            ("sydney", "Sydney", "سيدني", -33.8688, 151.2093, "Australia/Sydney", mwl)
        ]
        return rows.map { HisnPlace(key: $0.0, en: $0.1, ar: $0.2, latitude: $0.3, longitude: $0.4, zone: $0.5, method: $0.6) }
    }()

    static func forZone(_ zone: String) -> HisnPlace { all.first { $0.zone == zone } ?? all[0] }
}

/// The five prayers: names, today's times for the person's city, and which they marked prayed.
enum HisnPrayers {
    static let names: [(String, String)] = [("Fajr", "الفجر"), ("Dhuhr", "الظهر"), ("Asr", "العصر"), ("Maghrib", "المغرب"), ("Isha", "العشاء")]
    private static var defaults: UserDefaults { .standard }

    static var place: HisnPlace {
        if let key = defaults.string(forKey: "hisn.prayer.city"), let p = HisnPlace.all.first(where: { $0.key == key }) { return p }
        return HisnPlace.forZone(TimeZone.current.identifier)
    }
    static func setPlace(_ key: String) { defaults.set(key, forKey: "hisn.prayer.city") }

    static func times(on date: Date = Date()) -> HisnPrayerTimes {
        let p = place
        let zone = TimeZone(identifier: p.zone) ?? .current
        var cal = Calendar(identifier: .gregorian); cal.timeZone = zone
        let c = cal.dateComponents([.year, .month, .day], from: date)
        let noon = cal.date(bySettingHour: 12, minute: 0, second: 0, of: date) ?? date
        return HisnPrayerTimes.compute(year: c.year!, month: c.month!, day: c.day!, latitude: p.latitude, longitude: p.longitude,
                                       utcOffsetMinutes: zone.secondsFromGMT(for: noon) / 60, method: p.method,
                                       hanafi: defaults.bool(forKey: "hisn.prayer.hanafi"))
    }

    /// Minutes after midnight now, in the city's own time zone.
    static func nowMinutes(_ date: Date = Date()) -> Int {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: place.zone) ?? .current
        let c = cal.dateComponents([.hour, .minute], from: date)
        return c.hour! * 60 + c.minute!
    }

    static func prayed(_ day: Int = HisnJourney.today) -> Int { defaults.integer(forKey: "hisn.prayed.\(day)") }
    static func toggle(_ index: Int, day: Int = HisnJourney.today) { defaults.set(prayed(day) ^ (1 << index), forKey: "hisn.prayed.\(day)") }
}

// MARK: - Nights of the journey

struct HisnStation { let nights: Int; let icon, en, ar: String; var name: String { ia(en, ar) } }

enum HisnJourney {
    static let stations = [HisnStation(nights: 7, icon: "medal", en: "First week", ar: "الأسبوع الأول"),
                           HisnStation(nights: 14, icon: "lantern", en: "Bright lamp", ar: "المصباح المضيء"),
                           HisnStation(nights: 40, icon: "crescent", en: "Forty nights", ar: "أربعون ليلة"),
                           HisnStation(nights: 90, icon: "tower", en: "The fortress", ar: "الحصن")]
    private static var defaults: UserDefaults { .standard }

    /// Days since 1970 in the phone's calendar: the key every day's record is kept under.
    static func dayNumber(_ date: Date) -> Int {
        let start = Calendar.current.startOfDay(for: date)
        return Int((start.timeIntervalSince1970 + Double(TimeZone.current.secondsFromGMT(for: start))) / 86_400)
    }
    static var today: Int { dayNumber(Date()) }
    static func date(of day: Int) -> Date {
        Calendar.current.date(byAdding: .day, value: day - today, to: Calendar.current.startOfDay(for: Date())) ?? Date()
    }

    /// The day the journey began: set once when the first run ends.
    static var startDay: Int {
        if defaults.object(forKey: "hisn.start") == nil { defaults.set(today, forKey: "hisn.start") }
        return defaults.integer(forKey: "hisn.start")
    }
    static func begin() { if defaults.object(forKey: "hisn.start") == nil { defaults.set(today, forKey: "hisn.start") } }

    /// Whole nights since the start; a slip does not reset them, it is marked on its day.
    static var nights: Int { max(0, today - startDay) }
    static func stations(for nights: Int) -> (previous: HisnStation?, next: HisnStation) {
        (stations.last { $0.nights <= nights }, stations.first { $0.nights > nights } ?? stations.last!)
    }

    static func slipped(_ day: Int) -> Bool { defaults.bool(forKey: "hisn.slip.\(day)") }
    static func setSlipped(_ day: Int, _ on: Bool) { defaults.set(on, forKey: "hisn.slip.\(day)") }
    static func turnedBack(_ day: Int) -> Bool { defaults.bool(forKey: "hisn.back.\(day)") }
    static func setTurnedBack(_ day: Int) { defaults.set(true, forKey: "hisn.back.\(day)") }
    static func note(_ day: Int) -> String { defaults.string(forKey: "hisn.note.\(day)") ?? "" }
    static func setNote(_ day: Int, _ text: String) { defaults.set(text, forKey: "hisn.note.\(day)") }

    /// Today's three: morning adhkār, today's reading, the evening reckoning.
    static func done(_ task: String, day: Int = today) -> Bool { defaults.bool(forKey: "hisn.task.\(day).\(task)") }
    static func markDone(_ task: String, day: Int = today) { defaults.set(true, forKey: "hisn.task.\(day).\(task)") }
}

// MARK: - The scholars' sayings (Android's sayings.tsv, word for word)

struct HisnSaying: Identifiable {
    let index: Int
    let text: String
    let title, authorAr, authorEn: String
    let page: Int
    let volume: String
    let topics: Int
    let score: Int
    let folded: String
    var id: Int { index }
    var pageLabel: String { volume.isEmpty ? "\(page)" : "\(volume)/ \(page)" }
    var author: String { hisnArabic() ? (authorAr == "ابن رجب الحنبلي" ? "ابن رجب" : authorAr) : authorEn }
    var source: String { ia("\(author) · \(title), p. \(pageLabel)", "\(author) · \(title)، ص \(pageLabel)") }
}

struct HisnTopic: Identifiable { let key, en, ar: String; let bit: Int; var id: String { key }; var name: String { ia(en, ar) } }

final class HisnSayings {
    static let shared = HisnSayings()
    private(set) var topics: [HisnTopic] = []
    private(set) var all: [HisnSaying] = []

    enum Moment: String {
        case tempted, afterSlip, alone, weak, time, heavy
        var pattern: String {
            switch self {
            case .tempted: return "النظر|البصر|الطرف|الهوي|الشهو|العشق|اللذه|غض"
            case .afterSlip: return "توب|التائب|الاستغفار|استغفر|الندم|رحمه|يغفر|المغفره"
            case .alone: return "(^|\\s)(الخلوه|الخلوات|خلا|السر|السريره|السرائر|البواطن)(\\s|$|[،.])|يراك|نظر الله|نظر الحق|مراقب"
            case .weak: return "الصبر|صبر|الهمه|العزم|مجاهده|جاهد|الفتور|الكسل"
            case .time: return "الوقت|العمر|الاوقات|الايام|الساعه|طول الامل|الفراغ"
            case .heavy: return "الهم|الغم|الحزن|ضيق|انشراح|الفرح|السرور|الانس"
            }
        }
    }

    private init() {
        guard let url = Bundle.main.url(forResource: "sayings", withExtension: "tsv"),
              let tsv = try? String(contentsOf: url, encoding: .utf8) else { return }
        var books: [(String, String, String, String)] = []
        for line in tsv.split(separator: "\n", omittingEmptySubsequences: true).dropFirst() {
            let f = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
            switch f.first {
            case "T": topics.append(HisnTopic(key: f[1], en: f[2], ar: f[3], bit: 1 << topics.count))
            case "B": books.append((f[1], f[2], f[3], f[4]))
            case "Q" where f.count == 10:
                guard let b = Int(f[1]), b < books.count else { continue }
                let text = f[9].replacingOccurrences(of: "\\n", with: "\n")
                all.append(HisnSaying(index: all.count, text: text, title: books[b].0, authorAr: books[b].2, authorEn: books[b].3,
                                      page: Int(f[2]) ?? 0, volume: f[3], topics: Int(f[4]) ?? 0, score: Int(f[5]) ?? 0,
                                      folded: Self.fold(text)))
            default: continue
            }
        }
    }

    /// Drops harakat and tatweel and folds the alif forms, ta marbuta and alif maqsura, so «الهوى» finds «الهَوَى».
    static func fold(_ s: String) -> String {
        var out = ""
        for u in s.unicodeScalars {
            if (0x064B...0x065F).contains(u.value) || u.value == 0x0670 || u.value == 0x0640 { continue }
            switch u {
            case "أ", "إ", "آ", "ٱ": out.append("ا")
            case "ة": out.append("ه")
            case "ى": out.append("ي")
            default: out.unicodeScalars.append(u)
            }
        }
        return out.lowercased()
    }

    /// Echoes of the weak hadith "the glance is a poisoned arrow" stay in the library but are never pushed.
    private func pushable(_ s: HisnSaying) -> Bool { !s.folded.contains("سهم مسموم") }

    func moment(_ m: Moment) -> [HisnSaying] {
        guard let re = try? NSRegularExpression(pattern: m.pattern) else { return [] }
        return all.filter { pushable($0) && re.firstMatch(in: $0.folded, range: NSRange($0.folded.startIndex..., in: $0.folded)) != nil }
            .sorted { ($0.score + ($0.text.count < 260 ? 6 : 0)) > ($1.score + ($1.text.count < 260 ? 6 : 0)) }
    }

    /// One saying for the day, stable all day; `skip` moves to the next one.
    func daily(day: Int = HisnJourney.today, skip: Int = 0) -> HisnSaying? {
        let pool = all.filter { $0.text.count <= 240 && $0.score >= 18 && pushable($0) }.sorted { $0.score > $1.score }
        guard !pool.isEmpty else { return nil }
        return pool[((day + skip) % pool.count + pool.count) % pool.count]
    }

    func filter(topic: String?, query: String) -> [HisnSaying] {
        let bit = topics.first { $0.key == topic }?.bit
        let q = Self.fold(query.trimmingCharacters(in: .whitespaces))
        return all.filter { s in
            (bit == nil || s.topics & bit! != 0) &&
            (q.isEmpty || s.folded.contains(q) || Self.fold(s.title).contains(q) || Self.fold(s.authorAr).contains(q) || s.authorEn.lowercased().contains(q))
        }
    }

    var saved: Set<Int> {
        get { Set(UserDefaults.standard.array(forKey: "hisn.sayings.saved") as? [Int] ?? []) }
        set { UserDefaults.standard.set(Array(newValue), forKey: "hisn.sayings.saved") }
    }
}

// MARK: - The verse of the day (a short list, each with Pickthall's English)

struct HisnDailyVerse { let text, ref, pickthall: String }

enum HisnVerses {
    static let list = [
        HisnDailyVerse(text: "أَلَمْ يَعْلَم بِأَنَّ ٱللَّهَ يَرَىٰ", ref: "96:14", pickthall: "Is he then unaware that Allah seeth?"),
        HisnDailyVerse(text: "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ", ref: "24:30", pickthall: "Tell the believing men to lower their gaze."),
        HisnDailyVerse(text: "وَمَن يَتَّقِ ٱللَّهَ يَجْعَل لَّهُۥ مَخْرَجًا", ref: "65:2", pickthall: "Whosoever keepeth his duty to Allah, Allah will appoint a way out for him."),
        HisnDailyVerse(text: "لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ", ref: "39:53", pickthall: "Despair not of the mercy of Allah."),
        HisnDailyVerse(text: "فَإِنِّى قَرِيبٌ", ref: "2:186", pickthall: "Then surely I am nigh."),
        HisnDailyVerse(text: "إِنَّ ٱلصَّلَوٰةَ تَنْهَىٰ عَنِ ٱلْفَحْشَآءِ وَٱلْمُنكَرِ", ref: "29:45", pickthall: "Lo! worship preserveth from lewdness and iniquity.")
    ]
    static var today: HisnDailyVerse { list[((HisnJourney.today % list.count) + list.count) % list.count] }
}
