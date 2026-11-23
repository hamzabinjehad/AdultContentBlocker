import Foundation
import UserNotifications

/// One line from the shared reminder pack (android/app/src/general/assets/reminders/reminders.json):
/// the same 86 reviewed lines Android's Unrot shows, with their sources.
struct UnrotReminder: Decodable, Equatable {
    let id: String
    let kind: String
    let topics: [String]
    let en: String
    let ar: String
    let sourceEn: String
    let sourceAr: String

    enum CodingKeys: String, CodingKey {
        case id, kind, topics, en, ar
        case sourceEn = "source_en", sourceAr = "source_ar"
    }

    func text(arabic: Bool) -> String { arabic && !ar.isEmpty ? ar : en }
    func source(arabic: Bool) -> String? {
        let value = arabic && !sourceAr.isEmpty ? sourceAr : sourceEn
        return value.isEmpty ? nil : value
    }
}

enum UnrotReminders {
    private struct Pack: Decodable { let version: Int; let items: [UnrotReminder] }

    static func parse(_ data: Data) -> [UnrotReminder] {
        ((try? JSONDecoder().decode(Pack.self, from: data))?.items ?? [])
            .filter { !$0.en.isEmpty && !$0.id.isEmpty }
    }

    static let all: [UnrotReminder] = {
        guard let url = Bundle.main.url(forResource: "reminders", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        return parse(data)
    }()

    /// The same line all day, a new one each day (Android's `forDay`).
    static func forDay(_ date: Date, in items: [UnrotReminder] = all, calendar: Calendar = .current) -> UnrotReminder? {
        guard !items.isEmpty else { return nil }
        let day = calendar.ordinality(of: .day, in: .era, for: date) ?? 0
        return items[((day % items.count) + items.count) % items.count]
    }

    static var arabic: Bool {
        let choice = UserDefaults.standard.string(forKey: "hisn.mobile.language") ?? "system"
        return choice == "ar" || (choice == "system" && Locale.preferredLanguages.first?.hasPrefix("ar") == true)
    }
}

/// When the gentle reminders come: a few minutes before each time the person
/// said the pull gets them, for the next week. Rescheduled on every open, so the
/// lines change and a changed answer takes effect. Never more than 4 a day.
enum ReminderPlan {
    struct Slot: Equatable { let key: String; let hour: Int; let minute: Int }
    struct Request: Equatable { let id: String; let date: DateComponents; let index: Int }

    static let prefix = "unrot.reminder."
    static let slots: [String: Slot] = [
        "morning": Slot(key: "morning", hour: 7, minute: 45),
        "work": Slot(key: "work", hour: 12, minute: 15),
        "evening": Slot(key: "evening", hour: 19, minute: 45),
        "bed": Slot(key: "bed", hour: 22, minute: 15),
    ]

    /// The person's times in day order; the evening alone when they chose none.
    static func slots(for when: [String]) -> [Slot] {
        let chosen = when.compactMap { slots[$0] }
        return (chosen.isEmpty ? [slots["evening"]!] : chosen).sorted { ($0.hour, $0.minute) < ($1.hour, $1.minute) }
    }

    /// Requests for the next `days` days that are still ahead of `now`; each
    /// picks a different line so a week never repeats one.
    static func requests(now: Date, days: Int = 7, when: [String], count: Int,
                         calendar: Calendar = .current) -> [Request] {
        guard count > 0 else { return [] }
        let today = calendar.startOfDay(for: now)
        let base = calendar.ordinality(of: .day, in: .era, for: now) ?? 0
        var out: [Request] = []
        for offset in 0..<days {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            for (n, slot) in slots(for: when).enumerated() {
                guard let fire = calendar.date(bySettingHour: slot.hour, minute: slot.minute, second: 0, of: day),
                      fire > now else { continue }
                let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
                let index = ((base + offset) * 4 + n) % count
                out.append(Request(id: "\(prefix)\(UnrotFormat.dayKey(day)).\(slot.key)", date: comps, index: index))
            }
        }
        return out
    }
}

/// Puts the plan into Notification Center. Only Unrot's own reminders are touched.
enum ReminderScheduler {
    static let enabledKey = "unrot.reminders.on"

    static func reschedule(on: Bool, when: [String], center: UNUserNotificationCenter = .current()) async {
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(ReminderPlan.prefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)
        guard on else { return }
        let items = UnrotReminders.all
        let arabic = UnrotReminders.arabic
        for plan in ReminderPlan.requests(now: Date(), when: when, count: items.count) {
            let content = UNMutableNotificationContent()
            content.title = Edition.displayName
            content.body = items[plan.index].text(arabic: arabic)
            content.sound = nil
            content.interruptionLevel = .passive
            let trigger = UNCalendarNotificationTrigger(dateMatching: plan.date, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: plan.id, content: content, trigger: trigger))
        }
    }

    /// Asks once, from the person's own tap.
    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge])) ?? false
    }
}
