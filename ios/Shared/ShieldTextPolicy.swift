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

    enum Reason: Equatable {
        case always, dailyBudget, quietHours, unknown
        /// A pause rule with opens left: the shield asks the question instead of
        /// only closing the app.
        case pause(remaining: Int, opens: Int)
        /// A pause rule with nothing left today.
        case pauseClosed
        /// A pause rule whose saved opens could not be read. Closed for the day
        /// rather than handing out opens a damaged record might have created.
        case pauseUnreadable
    }

    struct Copy: Equatable {
        let title: String
        let subtitle: String
        let primaryButton: String
        /// Only a pause rule has a second action. It opens Hisn to the pause
        /// screen; it never opens the shielded app itself.
        var secondaryButton: String?

        init(title: String, subtitle: String, primaryButton: String, secondaryButton: String? = nil) {
            self.title = title
            self.subtitle = subtitle
            self.primaryButton = primaryButton
            self.secondaryButton = secondaryButton
        }
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
                       pauseBudget: PauseRule.Budget = .unreadable,
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
        case .pause:
            switch pauseBudget {
            case .opens(let remaining): return .pause(remaining: remaining, opens: configuration.opens)
            case .exhausted: return .pauseClosed
            case .unreadable: return .pauseUnreadable
            }
        }
    }

    // MARK: - The handoff notification

    /// On iOS versions before the shield can open Hisn itself, the pause action
    /// posts a notification; tapping it opens Hisn, where the pause screen is
    /// waiting. Both strings name the app, so the person knows which paused.
    static func handoffNotification(appName: String?, language: Language) -> (title: String, body: String) {
        let name = appName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        switch language {
        case .english:
            return (title: name.isEmpty ? "Pause before opening" : "Pause before opening \(name)",
                    body: "Tap to take the pause in Hisn. Nothing opens until you finish it.")
        case .arabic:
            return (title: name.isEmpty ? "توقف قبل الفتح" : "توقف قبل فتح \(name)",
                    body: "اضغط لتأخذ التوقف في حصن. لا يُفتح شيء حتى تُكمله.")
        }
    }

    // MARK: - Copy

    static func copy(for reason: Reason, language: Language, appName: String? = nil) -> Copy {
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
                        subtitle: "This app is shielded during your quiet-hours window.",
                        primaryButton: "OK")
        case (.quietHours, .arabic):
            return Copy(title: "ساعات هادئة",
                        subtitle: "هذا التطبيق محجوب خلال نافذة الساعات الهادئة.",
                        primaryButton: "حسنًا")
        case (.unknown, .english):
            return Copy(title: "Hisn is protecting this app",
                        subtitle: "Open Hisn to review the saved rule.",
                        primaryButton: "OK")
        case (.unknown, .arabic):
            return Copy(title: "حصن يحمي هذا التطبيق",
                        subtitle: "افتح حصن لمراجعة القاعدة المحفوظة.",
                        primaryButton: "حسنًا")
        case (.pause(let remaining, let opens), .english):
            let count = "\(remaining) of \(opens) opens left today"
            return Copy(title: "Is this important?",
                        subtitle: appName.map { "\($0) · \(count)" } ?? count,
                        primaryButton: "Close",
                        secondaryButton: "Pause first")
        case (.pause(let remaining, let opens), .arabic):
            let count = "متبقٍ \(remaining) من \(opens) فتحات اليوم"
            return Copy(title: "هل هذا مهم؟",
                        subtitle: appName.map { "\($0) · \(count)" } ?? count,
                        primaryButton: "إغلاق",
                        secondaryButton: "توقف أولًا")
        case (.pauseClosed, .english):
            return Copy(title: "No opens left today",
                        subtitle: "You used today's opens. This app stays closed until tomorrow.",
                        primaryButton: "Close")
        case (.pauseClosed, .arabic):
            return Copy(title: "لا فتحات متبقية اليوم",
                        subtitle: "استخدمت فتحات اليوم. يبقى هذا التطبيق مغلقًا حتى الغد.",
                        primaryButton: "إغلاق")
        case (.pauseUnreadable, .english):
            return Copy(title: "Today’s opens are closed",
                        subtitle: "Hisn could not read today’s opens, so it kept them closed. They return tomorrow.",
                        primaryButton: "Close")
        case (.pauseUnreadable, .arabic):
            return Copy(title: "أُغلقت فتحات اليوم",
                        subtitle: "لم يستطع حصن قراءة فتحات اليوم، فأبقاها مغلقة. تعود غدًا.",
                        primaryButton: "إغلاق")
        }
    }

    /// Everything the shield extension needs in one call.
    static func shieldCopy(defaults: UserDefaults?,
                           appName: String? = nil,
                           now: Date = Date(),
                           preferredLanguages: [String] = Locale.preferredLanguages) -> Copy {
        let config = configuration(in: defaults)
        let pauseBudget: PauseRule.Budget = config?.mode == .pause
            ? PauseRuleStore(defaults: defaults).budget(
                opens: config?.opens ?? PauseRule.defaultOpens, now: now)
            : .unreadable
        let reason = reason(configuration: config,
                            budgetReached: budgetReachedToday(in: defaults),
                            pauseBudget: pauseBudget,
                            now: now,
                            trustedNow: trustedNow(in: defaults))
        return copy(for: reason,
                    language: language(defaults: defaults, preferredLanguages: preferredLanguages),
                    appName: appName)
    }
}
