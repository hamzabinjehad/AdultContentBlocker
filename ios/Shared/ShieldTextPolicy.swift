import Foundation

/// The words on Hisn's own Screen Time shield. The shield configuration and
/// action extensions run outside the app, so the copy is deliberately a small,
/// fixed English/Arabic table rather than a Localizable.strings lookup: the
/// language choice is written by the app into the App Group and read here.
///
/// The shield explains the saved rule and nothing else. It offers no unlock
/// action; only the person can remove or weaken a rule in Hisn, and a running
/// commitment keeps it frozen. A missing or unreadable configuration still gets
/// an honest generic shield rather than a false claim about which rule applied.
enum ShieldTextPolicy {
    static let languageKey = "hisn.shield.language"

    enum Language: String, Equatable {
        case english, arabic
    }

    enum Reason: String, Equatable {
        case always, dailyBudget, quietHours, unknown
    }

    struct Copy: Equatable {
        let title: String
        let subtitle: String
        let primaryButton: String
    }

    // MARK: - Language

    /// The app writes "system", "en" or "ar" here. "system" and anything unknown
    /// follow the extension's preferred language.
    static func store(languageChoice: String, defaults: UserDefaults?) {
        defaults?.set(languageChoice, forKey: languageKey)
    }

    static func storedLanguageChoice(in defaults: UserDefaults?) -> String? {
        defaults?.string(forKey: languageKey)
    }

    static func resolveLanguage(storedChoice: String?,
                                preferredLanguages: [String]) -> Language {
        switch storedChoice {
        case "ar": return .arabic
        case "en": return .english
        default:
            let first = preferredLanguages.first?.lowercased() ?? ""
            return first.hasPrefix("ar") ? .arabic : .english
        }
    }

    static func language(defaults: UserDefaults?,
                         preferredLanguages: [String] = Locale.preferredLanguages) -> Language {
        resolveLanguage(storedChoice: storedLanguageChoice(in: defaults),
                        preferredLanguages: preferredLanguages)
    }

    // MARK: - Why the shield is showing

    static func configuration(in defaults: UserDefaults?) -> AppUsageConfiguration? {
        guard let defaults else { return nil }
        return try? AppUsageConfiguration.load(defaults)
    }

    static func budgetReachedToday(in defaults: UserDefaults?) -> Bool {
        guard let defaults else { return false }
        return (try? AppUsageConfiguration.budgetReached(mode: .dailyBudget, defaults: defaults)) ?? false
    }

    /// The App Group's saved reading of Apple's time, carried forward with the
    /// monotonic clock. nil until the app has read it in this boot.
    static func trustedNow(in defaults: UserDefaults?) -> Date? {
        guard let defaults else { return nil }
        return TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: defaults)).load()?
            .lowerBound(uptime: TrustedTime.uptime, boot: TrustedTime.bootSession)
    }

    static func reason(configuration: AppUsageConfiguration?,
                       budgetReached: Bool,
                       now: Date,
                       trustedNow: Date?) -> Reason {
        guard let configuration else { return .unknown }
        switch configuration.mode {
        case .always:
            return .always
        case .dailyBudget:
            // A budget store can outlive its reached-day marker after a rule
            // change. Do not claim the budget was reached without the marker.
            return budgetReached ? .dailyBudget : .unknown
        case .quietHours:
            return configuration.windowActive(deviceNow: now, trustedNow: trustedNow)
                ? .quietHours : .unknown
        }
    }

    // MARK: - Copy

    static func copy(for reason: Reason, language: Language) -> Copy {
        switch (reason, language) {
        case (.always, .english):
            return Copy(title: "Hisn is protecting this app",
                        subtitle: "This app is blocked by your saved rule. Open Hisn to review it when you are ready.",
                        primaryButton: "OK")
        case (.always, .arabic):
            return Copy(title: "حصن يحمي هذا التطبيق",
                        subtitle: "هذا التطبيق محجوب بقاعدتك المحفوظة. افتح حصن لمراجعتها عندما تكون مستعدًا.",
                        primaryButton: "حسنًا")
        case (.dailyBudget, .english):
            return Copy(title: "Today’s time is used",
                        subtitle: "This app stays shielded until the next daily interval begins.",
                        primaryButton: "OK")
        case (.dailyBudget, .arabic):
            return Copy(title: "انتهى وقت اليوم",
                        subtitle: "يبقى هذا التطبيق محجوبًا حتى تبدأ الفترة اليومية التالية.",
                        primaryButton: "حسنًا")
        case (.quietHours, .english):
            return Copy(title: "Quiet hours",
                        subtitle: "This app is shielded during your daily quiet-hours window.",
                        primaryButton: "OK")
        case (.quietHours, .arabic):
            return Copy(title: "ساعات هادئة",
                        subtitle: "هذا التطبيق محجوب خلال فترة الساعات الهادئة اليومية.",
                        primaryButton: "حسنًا")
        case (.unknown, .english):
            return Copy(title: "Hisn is protecting this app",
                        subtitle: "Open Hisn to review the saved rule.",
                        primaryButton: "OK")
        case (.unknown, .arabic):
            return Copy(title: "حصن يحمي هذا التطبيق",
                        subtitle: "افتح حصن لمراجعة القاعدة المحفوظة.",
                        primaryButton: "حسنًا")
        }
    }

    /// Everything the shield extension needs in one call.
    static func shieldCopy(defaults: UserDefaults?,
                           now: Date = Date(),
                           preferredLanguages: [String] = Locale.preferredLanguages) -> Copy {
        let config = configuration(in: defaults)
        let reason = reason(configuration: config,
                            budgetReached: budgetReachedToday(in: defaults),
                            now: now,
                            trustedNow: trustedNow(in: defaults))
        return copy(for: reason,
                    language: language(defaults: defaults, preferredLanguages: preferredLanguages))
    }
}
