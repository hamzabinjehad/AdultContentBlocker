import AppKit
import Foundation

/// Private browsing is the one bypass a browser extension cannot close on its
/// own: Chrome keeps an extension out of Incognito until the person turns on
/// "Allow in Incognito", and a private window otherwise has no Hisn inside it.
///
/// This file gathers the honest evidence:
///
///   * an installed browser whose policy forces private windows off is
///     `.disabled` — the strong state;
///   * a Chromium browser whose extension reports that it is allowed in
///     private windows is `.extensionChecked` — covered, but only while the
///     extension is healthy;
///   * anything else that can be controlled is `.open` and shown as a gap;
///   * Safari, WebKit browsers and hardened forks that do not accept the
///     policy are `.unsupported`, never silently counted as protected.
///
/// The app reads policy with `CFPreferencesAppValueIsForced`, so a preference
/// the person set by hand (and can unset) is not evidence of a lock.

public enum PrivateBrowsingFamily: String, Equatable, Sendable {
    case chromium
    case firefox
    case webKit
    case other
}

public struct PrivateBrowsingBrowser: Identifiable, Equatable, Sendable {
    public enum State: Equatable, Sendable {
        /// A configuration profile forces private windows off.
        case disabled
        /// The extension itself is allowed in private windows and checked in.
        case extensionChecked
        /// Private windows are available and nothing in Hisn covers them.
        case open
        /// Hisn cannot read or change this browser's private mode.
        case unsupported
    }

    public let name: String
    public let bundleID: String
    public let family: PrivateBrowsingFamily
    public let state: State
    /// Whether a profile can disable private windows in this browser.
    public let canDisable: Bool

    public var id: String { bundleID }
    public var isCovered: Bool { state == .disabled || state == .extensionChecked }
    public var isOpen: Bool { state == .open }
}

public struct PrivateBrowsingStatus: Equatable, Sendable {
    public let browsers: [PrivateBrowsingBrowser]

    public init(browsers: [PrivateBrowsingBrowser]) {
        self.browsers = browsers
    }

    /// Installed browsers a Hisn profile can control and whose private windows
    /// are currently open. This is the list the consent sheet offers.
    public var open: [PrivateBrowsingBrowser] { browsers.filter(\.isOpen) }
    public var disabled: [PrivateBrowsingBrowser] { browsers.filter { $0.state == .disabled } }
    public var extensionChecked: [PrivateBrowsingBrowser] { browsers.filter { $0.state == .extensionChecked } }
    public var unsupported: [PrivateBrowsingBrowser] { browsers.filter { $0.state == .unsupported } }
    public var controllable: [PrivateBrowsingBrowser] { browsers.filter(\.canDisable) }

    /// True when every installed browser Hisn can control is covered.
    public var isCovered: Bool { open.isEmpty }
    public var hasControllableBrowser: Bool { !controllable.isEmpty }

    public var openNames: String {
        open.map(\.name).formatted(.list(type: .and))
    }

    /// Read this Mac now. Injected readers keep the model testable without
    /// depending on which browsers happen to be installed on the test host.
    public static func read(
        now: Date = Date(),
        installed: (String) -> Bool = { bundleID in
            NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID) != nil
        },
        forced: (String, String) -> Any? = { key, domain in
            guard CFPreferencesAppValueIsForced(key as CFString, domain as CFString) else {
                return nil
            }
            return CFPreferencesCopyAppValue(key as CFString, domain as CFString)
        },
        extensionAllowed: ((String) -> Bool?)? = nil
    ) -> PrivateBrowsingStatus {
        let readExtension = extensionAllowed ?? { bundleID in
            ExtensionPresence.incognitoAllowed(browser: bundleID,
                in: UserDefaults(suiteName: LockStore.appGroup), now: now)
        }
        let browsers = PrivateBrowsingCatalog.all.compactMap { entry -> PrivateBrowsingBrowser? in
            guard installed(entry.bundleID) else { return nil }
            let state: PrivateBrowsingBrowser.State
            if entry.canDisable {
                if policyDisablesPrivateBrowsing(entry, forced: forced) {
                    state = .disabled
                } else if entry.family == .chromium, readExtension(entry.bundleID) == true {
                    state = .extensionChecked
                } else {
                    state = .open
                }
            } else {
                state = .unsupported
            }
            return PrivateBrowsingBrowser(
                name: entry.name, bundleID: entry.bundleID,
                family: entry.family, state: state, canDisable: entry.canDisable)
        }.sorted { lhs, rhs in
            lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
        return PrivateBrowsingStatus(browsers: browsers)
    }

    /// A short cache: the Overview asks for status repeatedly, and enumerating
    /// installed applications on every SwiftUI render is needless work. Policy
    /// changes made in System Settings are picked up within this window.
    private static let cacheLock = NSLock()
    nonisolated(unsafe) private static var cached: (status: PrivateBrowsingStatus, at: Date)?
    public static let cacheLifetime: TimeInterval = 5

    public static func current(now: Date = Date(), force: Bool = false) -> PrivateBrowsingStatus {
        cacheLock.lock()
        if !force, let cached, now.timeIntervalSince(cached.at) >= 0,
           now.timeIntervalSince(cached.at) < cacheLifetime {
            let status = cached.status
            cacheLock.unlock()
            return status
        }
        cacheLock.unlock()
        let status = read(now: now)
        cacheLock.lock()
        cached = (status, now)
        cacheLock.unlock()
        return status
    }

    public static func invalidateCache() {
        cacheLock.lock()
        cached = nil
        cacheLock.unlock()
    }

    private static func policyDisablesPrivateBrowsing(
        _ entry: PrivateBrowsingCatalog.Entry,
        forced: (String, String) -> Any?
    ) -> Bool {
        guard let domain = entry.policyDomain else { return false }
        switch entry.family {
        case .chromium:
            return (forced("IncognitoModeAvailability", domain) as? NSNumber)?.intValue == 1
        case .firefox:
            // Firefox ignores every policy unless enterprise policies are on.
            let enabled = (forced("EnterprisePoliciesEnabled", domain) as? NSNumber)?.boolValue == true
            let disabled = (forced("DisablePrivateBrowsing", domain) as? NSNumber)?.boolValue == true
            return enabled && disabled
        case .webKit, .other:
            return false
        }
    }

}

// MARK: - Catalog

public enum PrivateBrowsingCatalog {
    public struct Entry: Equatable, Sendable {
        public let name: String
        public let bundleID: String
        public let family: PrivateBrowsingFamily
        public let policyDomain: String?
        public let canDisable: Bool

        init(_ name: String, _ bundleID: String, _ family: PrivateBrowsingFamily,
             policyDomain: String? = nil, canDisable: Bool) {
            self.name = name
            self.bundleID = bundleID
            self.family = family
            self.policyDomain = policyDomain
            self.canDisable = canDisable
        }
    }

    /// Installed browsers only. The list is deliberately broader than the
    /// native-messaging catalogue: the question here is "can private windows
    /// in this browser be closed?", not "can the extension run inside it?".
    ///
    /// Firefox-based browsers other than the official channels are marked
    /// unsupported: several hardened forks deliberately ignore enterprise
    /// private-browsing policy, and claiming otherwise would be worse than
    /// naming the limit.
    public static let all: [Entry] = [
        // Chromium — policy is read by the browser, not by Hisn.
        Entry("Google Chrome", "com.google.Chrome", .chromium, policyDomain: "com.google.Chrome", canDisable: true),
        Entry("Google Chrome Beta", "com.google.Chrome.beta", .chromium, policyDomain: "com.google.Chrome.beta", canDisable: true),
        Entry("Google Chrome Dev", "com.google.Chrome.dev", .chromium, policyDomain: "com.google.Chrome.dev", canDisable: true),
        Entry("Google Chrome Canary", "com.google.Chrome.canary", .chromium, policyDomain: "com.google.Chrome.canary", canDisable: true),
        Entry("Microsoft Edge", "com.microsoft.edgemac", .chromium, policyDomain: "com.microsoft.Edge", canDisable: true),
        Entry("Brave", "com.brave.Browser", .chromium, policyDomain: "com.brave.Browser", canDisable: true),
        Entry("Vivaldi", "com.vivaldi.Vivaldi", .chromium, policyDomain: "com.vivaldi.Vivaldi", canDisable: true),
        Entry("Opera", "com.operasoftware.Opera", .chromium, policyDomain: "com.operasoftware.Opera", canDisable: true),
        Entry("Opera GX", "com.operasoftware.OperaGX", .chromium, policyDomain: "com.operasoftware.OperaGX", canDisable: true),
        Entry("Arc", "company.thebrowser.Browser", .chromium, policyDomain: "company.thebrowser.Browser", canDisable: true),
        Entry("Chromium", "org.chromium.Chromium", .chromium, policyDomain: "org.chromium.Chromium", canDisable: true),
        Entry("Helium", "net.imput.helium", .chromium, policyDomain: "net.imput.helium", canDisable: true),
        Entry("Thorium", "org.chromium.Thorium", .chromium, policyDomain: "org.chromium.Thorium", canDisable: true),
        Entry("Yandex Browser", "ru.yandex.desktop.yandex-browser", .chromium, policyDomain: "ru.yandex.desktop.yandex-browser", canDisable: true),
        Entry("Perplexity Comet", "ai.perplexity.comet", .chromium, policyDomain: "ai.perplexity.comet", canDisable: true),
        Entry("Dia", "company.thebrowser.dia", .chromium, policyDomain: "company.thebrowser.dia", canDisable: true),
        Entry("ChatGPT Atlas", "com.openai.atlas", .chromium, policyDomain: "com.openai.atlas", canDisable: true),

        // Firefox — DisablePrivateBrowsing only applies when enterprise
        // policies are enabled, which the generated payload also sets.
        Entry("Firefox", "org.mozilla.firefox", .firefox, policyDomain: "org.mozilla.firefox", canDisable: true),
        Entry("Firefox Developer Edition", "org.mozilla.firefoxdeveloperedition", .firefox, policyDomain: "org.mozilla.firefoxdeveloperedition", canDisable: true),
        Entry("Firefox Nightly", "org.mozilla.nightly", .firefox, policyDomain: "org.mozilla.nightly", canDisable: true),
        Entry("Zen Browser", "app.zen-browser.zen", .firefox, policyDomain: "app.zen-browser.zen", canDisable: true),
        Entry("Floorp", "one.ablaze.floorp", .firefox, policyDomain: "one.ablaze.floorp", canDisable: true),
        Entry("LibreWolf", "io.gitlab.librewolf-community", .firefox, policyDomain: nil, canDisable: false),
        Entry("Waterfox", "net.waterfox.waterfox", .firefox, policyDomain: nil, canDisable: false),
        Entry("Mullvad Browser", "net.mullvad.mullvadbrowser", .firefox, policyDomain: nil, canDisable: false),
        Entry("Tor Browser", "org.torproject.torbrowser", .firefox, policyDomain: nil, canDisable: false),

        // WebKit and other engines — no supported private-mode policy.
        Entry("Safari", "com.apple.Safari", .webKit, policyDomain: nil, canDisable: false),
        Entry("Safari Technology Preview", "com.apple.SafariTechnologyPreview", .webKit, policyDomain: nil, canDisable: false),
        Entry("DuckDuckGo", "com.duckduckgo.macos.browser", .webKit, policyDomain: nil, canDisable: false),
        Entry("Ladybird", "org.ladybird.Ladybird", .other, policyDomain: nil, canDisable: false),
    ]

    public static func entry(for bundleID: String) -> Entry? {
        all.first { $0.bundleID == bundleID }
    }
}

// MARK: - The narrow configuration profile

/// The small configuration profile that disables private windows in the
/// browsers Hisn can control.
///
/// The full hardening profile (`profile/make_profile.py`) also changes DNS,
/// Private Relay and guest windows. When private windows are the only open
/// item, this profile is the narrow fix: it sets only the private-browsing
/// keys for each selected browser and nothing else. It is handed to System
/// Settings as a file; macOS 26 no longer installs profiles from the command
/// line, so the person approves it there.
public enum PrivateWindowsProfile {
    public static let identifier = "app.hisn.profile.incognito"

    /// Installed browsers a profile can disable private windows in.
    public static func installedBrowsers() -> [PrivateBrowsingBrowser] {
        PrivateBrowsingStatus.current(force: true).controllable
    }

    /// The profile as a property list. Pure, so tests can read it without
    /// touching the system.
    public static func makeProfile(browsers: [PrivateBrowsingBrowser],
                                   removalDisallowed: Bool = false) -> [String: Any] {
        // One payload per preference domain, with the policy keys at the top
        // level — the same shape `profile/make_profile.py` uses for Chrome,
        // Edge and Firefox. Firefox does not read the generic
        // `com.apple.ManagedClient.preferences` wrapper, so putting its keys
        // there would have produced a profile that looked installed and did
        // nothing. Chromium accepts the direct payload too.
        var payloads: [[String: Any]] = []
        for browser in browsers where browser.canDisable {
            guard let entry = PrivateBrowsingCatalog.entry(for: browser.bundleID),
                  let domain = entry.policyDomain else { continue }
            let settings: [String: Any]
            switch entry.family {
            case .chromium:
                settings = ["IncognitoModeAvailability": 1]
            case .firefox:
                settings = ["EnterprisePoliciesEnabled": true,
                            "DisablePrivateBrowsing": true]
            case .webKit, .other:
                continue
            }
            var payload: [String: Any] = [
                "PayloadType": domain,
                "PayloadVersion": 1,
                "PayloadIdentifier": identifier + "." + domain,
                "PayloadUUID": UUID().uuidString,
                "PayloadDisplayName": "Hisn private windows disabled — \(browser.name)",
                "PayloadDescription": "Disables private windows in \(browser.name). Nothing else is changed.",
                "PayloadOrganization": "Hisn",
                "PayloadEnabled": true,
            ]
            for (key, value) in settings { payload[key] = value }
            payloads.append(payload)
        }
        return configuration(payloads: payloads, removalDisallowed: removalDisallowed,
            description: "Disables private and incognito windows in the browsers Hisn protects. Nothing else is changed.")
    }

    /// The original Chromium-only entry point, kept because it is the shape
    /// the existing narrow-profile tests pin. New callers should pass browsers
    /// so Firefox is included when it is installed.
    public static func makeProfile(domains: [String]) -> [String: Any] {
        var forced: [String: Any] = [:]
        for domain in domains {
            forced[domain] = ["Forced": [["mcx_preference_settings": ["IncognitoModeAvailability": 1]]]]
        }
        return profile(forced: forced,
                       description: "Disables private and incognito windows in the Chromium browsers Hisn protects. Nothing else is changed.")
    }

    public static func data(browsers: [PrivateBrowsingBrowser],
                            removalDisallowed: Bool = false) throws -> Data {
        try PropertyListSerialization.data(fromPropertyList: makeProfile(browsers: browsers, removalDisallowed: removalDisallowed),
                                            format: .xml, options: 0)
    }

    public static func data(domains: [String]) throws -> Data {
        try PropertyListSerialization.data(fromPropertyList: makeProfile(domains: domains),
                                            format: .xml, options: 0)
    }

    /// Write the profile somewhere stable and open it, so System Settings takes
    /// over the approval. Returns the file it wrote.
    @discardableResult
    public static func openForInstallation() throws -> URL {
        let browsers = installedBrowsers().filter { !$0.isCovered }
        guard !browsers.isEmpty else { throw PrivateWindowsProfileError.noBrowser }
        return try openForInstallation(browsers: browsers)
    }

    @discardableResult
    public static func openForInstallation(browsers: [PrivateBrowsingBrowser]) throws -> URL {
        let selected = browsers.filter { $0.canDisable && !$0.isCovered }
        guard !selected.isEmpty else { throw PrivateWindowsProfileError.noBrowser }
        let directory = FileManager.default.urls(for: .applicationSupportDirectory,
                                                 in: .userDomainMask)[0]
            .appendingPathComponent("Hisn", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("Hisn-Private-Windows-Disabled.mobileconfig")
        try data(browsers: selected, removalDisallowed: ManagedMode.current().isManaged).write(to: file, options: .atomic)
        NSWorkspace.shared.open(file)
        return file
    }

    public enum PrivateWindowsProfileError: LocalizedError {
        case noBrowser
        public var errorDescription: String? {
            String(localized: "No supported Chromium or Firefox browser is installed, so there is nothing to disable.")
        }
    }

    private static func profile(forced: [String: Any], description: String) -> [String: Any] {
        let payload: [String: Any] = [
            "PayloadType": "com.apple.ManagedClient.preferences",
            "PayloadVersion": 1,
            "PayloadIdentifier": identifier + ".payload",
            "PayloadUUID": UUID().uuidString,
            "PayloadDisplayName": "Hisn private windows disabled",
            "PayloadDescription": description,
            "PayloadOrganization": "Hisn",
            "PayloadContent": forced,
        ]
        return configuration(payloads: [payload], description: description)
    }

    private static func configuration(payloads: [[String: Any]],
                                      removalDisallowed: Bool = false,
                                      description: String) -> [String: Any] {
        [
            "PayloadType": "Configuration",
            "PayloadVersion": 1,
            "PayloadIdentifier": identifier,
            "PayloadUUID": UUID().uuidString,
            "PayloadDisplayName": "Hisn private windows disabled",
            "PayloadDescription": description
                + " Remove it with: sudo profiles remove -identifier " + identifier,
            "PayloadOrganization": "Hisn",
            "PayloadRemovalDisallowed": removalDisallowed,
            "PayloadContent": payloads,
        ]
    }
}
