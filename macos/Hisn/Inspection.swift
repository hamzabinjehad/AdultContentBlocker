import Foundation

/// Settings for the content-inspection layers, authored in the app.
///
/// The app is the authority for these the same way it is for `SiteLists`: the
/// browser extension overwrites its own copy from here on every heartbeat, so
/// anything typed on that side survives at most a minute. Authoring has to
/// happen on this side, and the lock guard has to live here too — `guardedUpdate`
/// in background.js enforces the same asymmetry a second time, because a value
/// posted from a devtools console never passes through this file.
///
/// ── WHAT IS DELIBERATELY ABSENT ────────────────────────────────────────────
/// There is no image-blurring setting here, and there should not be one until
/// image classification exists. A settings pane offering a switch for a feature
/// that is not built is worse than an empty pane: it is the "looks protected,
/// is not" failure `docs/THREAT_MODEL.md` rates as more damaging than being
/// switched off, because the person stops being careful. Every field below
/// corresponds to a layer that is actually running.
public enum Inspection {

    public static let textKey = "inspectText"
    public static let textSensitivityKey = "textSensitivity"
    public static let hostKeywordsKey = "hostKeywords"

    public struct Settings: Equatable, Codable {
        /// Score the rendered text of a page in the browser extension.
        public var text: Bool
        /// 0–100, higher is stricter. Named a *sensitivity* rather than a
        /// threshold on purpose: every knob in this product points the same
        /// way, so every guard below is a `<` comparison in the same
        /// direction. The threshold is derived from it in `lib/score.js`.
        public var textSensitivity: Int
        /// Match keywords in hostnames at the socket filter, and in full URLs
        /// through the browser's rule engine.
        public var hostKeywords: Bool

        public init(text: Bool = true,
                    textSensitivity: Int = 50,
                    hostKeywords: Bool = true) {
            self.text = text
            self.textSensitivity = textSensitivity
            self.hostKeywords = hostKeywords
        }

        public static let `default` = Settings()
    }

    public enum SettingsError: LocalizedError {
        case wouldLoosenWhileLocked([String])

        public var errorDescription: String? {
            switch self {
            case let .wouldLoosenWhileLocked(reasons):
                return "A lock is running, so you cannot "
                    + reasons.joined(separator: " or ")
                    + " until it ends. Making checking stricter still works."
            }
        }
    }

    private static var defaults: UserDefaults? {
        UserDefaults(suiteName: LockStore.appGroup)
    }

    // MARK: - Reading

    /// Read the stored settings.
    ///
    /// Every default is the RESTRICTIVE value, so a machine that has never
    /// saved these — or one whose defaults were cleared — inspects rather than
    /// waves through. `object(forKey:)` rather than `bool(forKey:)` because the
    /// latter returns `false` for a missing key, which would silently turn
    /// inspection off on a fresh install.
    public static func read() -> Settings {
        guard let d = defaults else { return .default }
        var s = Settings.default
        if let v = d.object(forKey: textKey) as? Bool { s.text = v }
        if let v = d.object(forKey: hostKeywordsKey) as? Bool { s.hostKeywords = v }
        if let v = d.object(forKey: textSensitivityKey) as? Int {
            s.textSensitivity = clamp(v)
        }
        return s
    }

    /// Sensitivity is a percentage, and a value outside 0–100 is a bug
    /// somewhere upstream rather than an instruction to be honoured.
    static func clamp(_ value: Int) -> Int { min(100, max(0, value)) }

    // MARK: - Writing

    /// What a change would loosen, or nil — shared with `PolicyAuthority`.
    public static func loosening(from current: Settings, to next: Settings) -> SettingsError? {
        var reasons: [String] = []
        if current.text, !next.text {
            reasons.append("turn off page-text checking")
        }
        if current.hostKeywords, !next.hostKeywords {
            reasons.append("turn off keyword checking")
        }
        if next.textSensitivity < current.textSensitivity {
            reasons.append("lower the sensitivity")
        }
        return reasons.isEmpty ? nil : .wouldLoosenWhileLocked(reasons)
    }

    /// Persist settings, refusing any change that weakens checking while a lock
    /// is running.
    ///
    /// The direction is the same asymmetry the rest of the product enforces:
    /// you may turn a check ON and you may raise sensitivity at any time; the
    /// reverse waits for the lock to end.
    public static func save(_ next: Settings, locked: Bool) throws {
        if locked, let refusal = loosening(from: read(), to: next) {
            throw refusal
        }
        defaults?.set(next.text, forKey: textKey)
        defaults?.set(next.hostKeywords, forKey: hostKeywordsKey)
        defaults?.set(clamp(next.textSensitivity), forKey: textSensitivityKey)
    }
}
