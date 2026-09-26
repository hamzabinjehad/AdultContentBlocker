import AppKit
import Foundation

/// The app's own language, independent of the Mac's.
///
/// Written where macOS itself keeps a per-app language — `AppleLanguages` in
/// this app's own defaults domain, the key System Settings › Language & Region
/// › Applications writes — so the choice made here and the one made there are
/// the same setting. It applies from the next launch: AppKit reads it once.
///
/// Arabic keeps Latin digits (`@numbers=latn`), as the browser extension does,
/// so a deadline reads the same in the app and on the block page. It also
/// mirrors the window: an app switched to Arabic this way is not mirrored by
/// macOS on its own (only a Mac whose own language is Arabic is), so the two
/// right-to-left keys AppKit reads are written with it.
enum AppLanguage: String, CaseIterable, Identifiable {
    case system, english, arabic

    var id: String { rawValue }

    /// Each language names itself, so someone who cannot read the current one
    /// can still find theirs.
    var label: String {
        switch self {
        case .system:  return String(localized: "Same as the Mac")
        case .english: return "English"
        case .arabic:  return "العربية"
        }
    }

    private static let languagesKey = "AppleLanguages"
    private static let localeKey = "AppleLocale"
    private static let directionKeys = ["AppleTextDirection", "NSForceRightToLeftWritingDirection"]

    /// The choice stored for this app, read from its own domain only: the
    /// global `AppleLanguages` is the Mac's list, not a choice made here.
    static var chosen: AppLanguage {
        guard let id = Bundle.main.bundleIdentifier,
              let first = (UserDefaults.standard.persistentDomain(forName: id)?[languagesKey]
                            as? [String])?.first
        else { return .system }
        if first.hasPrefix("ar") { return .arabic }
        if first.hasPrefix("en") { return .english }
        return .system
    }

    /// The language this process is actually showing.
    static var running: String {
        Bundle.main.preferredLocalizations.first ?? "en"
    }

    /// Whether `choice` differs from what is on screen now, i.e. whether a
    /// restart would change anything.
    static func needsRestart(for choice: AppLanguage) -> Bool {
        switch choice {
        case .english: return !running.hasPrefix("en")
        case .arabic:  return !running.hasPrefix("ar")
        case .system:
            let mac = Bundle.preferredLocalizations(
                from: Bundle.main.localizations,
                forPreferences: UserDefaults.standard.persistentDomain(
                    forName: UserDefaults.globalDomain)?[languagesKey] as? [String]).first ?? "en"
            return mac != running
        }
    }

    /// Tell the browser extension which language the app shows, through the
    /// shared defaults the bridge reads: its pages set to Automatic follow
    /// it, so an app switched to Arabic brings the extension along.
    static func publish() {
        UserDefaults(suiteName: LockStore.appGroup)?
            .set(running.hasPrefix("ar") ? "ar" : "en", forKey: "appLanguage")
    }

    static func choose(_ language: AppLanguage) {
        let d = UserDefaults.standard
        switch language {
        case .system:
            d.removeObject(forKey: languagesKey)
            d.removeObject(forKey: localeKey)
        case .english:
            d.set(["en"], forKey: languagesKey)
            d.removeObject(forKey: localeKey)
        case .arabic:
            d.set(["ar"], forKey: languagesKey)
            let region = Locale.current.region?.identifier ?? "US"
            d.set("ar_\(region)@numbers=latn", forKey: localeKey)
        }
        for key in directionKeys {
            if language == .arabic { d.set(true, forKey: key) } else { d.removeObject(forKey: key) }
        }
    }

    /// Quit and open again. Only offered while no lock runs: during one, quit
    /// is refused (this process is the browser guard), so the choice waits for
    /// the next launch instead.
    ///
    /// The helper waits for this process to be gone before opening the app,
    /// so the new copy's one-instance check does not find the old one and
    /// quit itself.
    @MainActor static func restart() {
        let helper = Process()
        helper.executableURL = URL(fileURLWithPath: "/bin/sh")
        helper.arguments = [
            "-c",
            "while /bin/kill -0 \"$1\" 2>/dev/null; do /bin/sleep 0.2; done; /usr/bin/open \"$2\"",
            "sh", String(ProcessInfo.processInfo.processIdentifier), Bundle.main.bundleURL.path,
        ]
        do { try helper.run() } catch { return }
        NSApp.terminate(nil)
    }
}
