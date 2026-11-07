import Foundation

/// The answer to “would Hisn block this address?”, with the rule that decided
/// it. This is a read-only explanation, not a new enforcement path: the filter
/// and the browser extension remain the only things that block a request.
///
/// The check is deliberately address-only. Page-text checks, visual checks and
/// each browser profile's extension state are separate layers, and saying
/// “blocked” here must never be read as “this page is safe”.
struct SiteCheckResult: Equatable, Sendable {

    enum Outcome: Equatable, Sendable {
        case blocked
        case allowed
        /// The installed block list could not be read. Hand lists and strict
        /// mode were still checked; the published list was not.
        case listUnavailable
        case invalid
    }

    enum Source: Equatable, Sendable {
        case builtIn
        case customBlock
        case allowlist
        case publishedList
        case keyword
        /// A sponsored adult TLD, independent of any list version.
        case adultTld
        case strictMode
        case notListed
        case none
    }

    /// Which copy of the list answered. The full generation installed in the
    /// shared container is preferred; the signed core seed is the offline
    /// fallback, and the UI says so because it covers fewer domains.
    enum ListSource: Equatable, Sendable {
        case installed
        case bundledCore
        case unavailable
        case none
    }

    let host: String?
    let outcome: Outcome
    let source: Source
    let listVersion: Int?
    let listSource: ListSource

    static let invalid = SiteCheckResult(host: nil, outcome: .invalid,
                                         source: .none, listVersion: nil,
                                         listSource: .none)
}

/// Pure rule order, extracted so every branch is testable without a window or
/// a real block list. The order matches `BlocklistStore`:
///
/// 1. built-in search/viewer rules cannot be allowed;
/// 2. a hand-typed block outranks a hand-typed allowance;
/// 3. strict mode blocks everything except an allowance;
/// 4. an allowance outranks the published list, the adult-TLD rail and the
///    keyword layer;
/// 5. the adult-TLD rail blocks every name under `.xxx`, `.porn`, `.sex` and
///    `.adult` without needing the list to be readable;
/// 6. the published list, then the keyword layer.
enum SiteCheckRules {

    /// - Parameter list: called only when the answer needs the installed list.
    ///   Returns nil when the list could not be read. `listed` is the published
    ///   domain list; `keyword` is the hostname keyword layer.
    static func evaluate(raw: String,
                         blocks: [String],
                         allows: [String],
                         strict: Bool,
                         keywordsEnabled: Bool,
                         list: (String) -> (version: Int, listed: Bool, keyword: Bool,
                                            source: SiteCheckResult.ListSource)?)
        -> SiteCheckResult {

        guard let host = SiteLists.normalize(raw) else { return .invalid }

        func covers(_ domain: String) -> Bool {
            host == domain || host.hasSuffix("." + domain)
        }

        // Built-in rules are not a list the person can override.
        if WebProtectionPolicy.isBlocked(host) {
            return SiteCheckResult(host: host, outcome: .blocked,
                                   source: .builtIn, listVersion: nil,
                                   listSource: .none)
        }

        if blocks.contains(where: covers) {
            return SiteCheckResult(host: host, outcome: .blocked,
                                   source: .customBlock, listVersion: nil,
                                   listSource: .none)
        }

        // Strict mode does not need the published list: anything not allowed is
        // blocked, and an allowance remains reachable.
        if strict {
            if allows.contains(where: covers) {
                return SiteCheckResult(host: host, outcome: .allowed,
                                       source: .allowlist, listVersion: nil,
                                       listSource: .none)
            }
            return SiteCheckResult(host: host, outcome: .blocked,
                                   source: .strictMode, listVersion: nil,
                                   listSource: .none)
        }

        if allows.contains(where: covers) {
            return SiteCheckResult(host: host, outcome: .allowed,
                                   source: .allowlist, listVersion: nil,
                                   listSource: .none)
        }

        // A sponsored adult TLD needs no list entry and no list read. Naming it
        // separately matters: the installed generation may be older than the
        // domain, and an unreadable list must not turn a guarded TLD into
        // "not blocked".
        if AdultTldGuard.matches(host: host) {
            return SiteCheckResult(host: host, outcome: .blocked,
                                   source: .adultTld, listVersion: nil,
                                   listSource: .none)
        }

        guard let list = list(host) else {
            return SiteCheckResult(host: host, outcome: .listUnavailable,
                                   source: .none, listVersion: nil,
                                   listSource: .unavailable)
        }
        if list.listed {
            return SiteCheckResult(host: host, outcome: .blocked,
                                   source: .publishedList, listVersion: list.version,
                                   listSource: list.source)
        }
        if keywordsEnabled && list.keyword {
            return SiteCheckResult(host: host, outcome: .blocked,
                                   source: .keyword, listVersion: list.version,
                                   listSource: list.source)
        }
        return SiteCheckResult(host: host, outcome: .allowed,
                               source: .notListed, listVersion: list.version,
                               listSource: list.source)
    }
}

/// Reads the list already installed on this Mac and answers the check off the
/// main thread. It reuses `BlocklistStore.shared` — the same instance the list
/// updater fills — rather than holding a second copy of several million
/// hashes. The filter remains the authority; a failed read is reported as such
/// and never turns into an “allowed” answer.
enum SiteCheckService {

    private static let queue = DispatchQueue(label: "app.hisn.site-check")
    private static var lastAttempt: Date?
    private static var loadFailed = false

    /// Load the installed generation while the sheet is opening. Failure is
    /// quiet: the check itself reports what it could not read.
    static func prewarm() {
        queue.async { _ = installedList() }
    }

    private struct LoadedList {
        let store: BlocklistStore
        let source: SiteCheckResult.ListSource
    }

    private static var cached: LoadedList?
    private static var loadedSource: SiteCheckResult.ListSource = .none

    static func check(raw: String, strict: Bool, keywordsEnabled: Bool) async -> SiteCheckResult {
        await withCheckedContinuation { continuation in
            queue.async {
                let result = SiteCheckRules.evaluate(
                    raw: raw,
                    blocks: SiteLists.customBlocks(),
                    allows: SiteLists.allowlist(),
                    strict: strict,
                    keywordsEnabled: keywordsEnabled,
                    list: { host in
                        guard let list = installedList() else { return nil }
                        return (list.store.version,
                                list.store.isOnPublishedList(host: host),
                                list.store.matchesKeyword(host: host),
                                list.source)
                    })
                continuation.resume(returning: result)
            }
        }
    }

    /// The newest list this process can read: the installed generation from the
    /// shared container, or the signed core seed bundled with the app when
    /// there is no container (a fresh install, or an ad-hoc build). A failed
    /// attempt is retried after a short pause rather than remembered forever,
    /// so fixing a missing list does not require a relaunch.
    private static func installedList() -> LoadedList? {
        let store = BlocklistStore.shared

        // ListUpdater loads the installed generation into this same store.
        if loadedSource == .none, store.domainCount > 0 {
            loadedSource = .installed
            let list = LoadedList(store: store, source: .installed)
            cached = list
            return list
        }
        if let cached, cached.store === store, cached.source == .installed,
           store.domainCount > 0 {
            return cached
        }
        // The bundled seed stays usable while no container exists; if one
        // appears later, try it again rather than freezing the weaker answer
        // for the rest of the run.
        if let cached, cached.store === store, cached.source == .bundledCore,
           store.domainCount > 0, !containerExists() {
            return cached
        }
        if loadFailed, let lastAttempt,
           Date().timeIntervalSince(lastAttempt) < 10 { return nil }
        lastAttempt = Date()

        if loadContainer(into: store) {
            loadedSource = .installed
            loadFailed = false
            let list = LoadedList(store: store, source: .installed)
            cached = list
            return list
        }
        if loadBundledSeed(into: store) {
            loadedSource = .bundledCore
            loadFailed = false
            let list = LoadedList(store: store, source: .bundledCore)
            cached = list
            return list
        }
        loadFailed = true
        return nil
    }

    private static func containerExists() -> Bool {
        guard let container = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: LockStore.appGroup) else { return false }
        return FileManager.default.fileExists(
            atPath: container.appendingPathComponent("list/manifest.json").path)
    }

    private static func loadContainer(into store: BlocklistStore) -> Bool {
        guard let container = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: LockStore.appGroup) else { return false }
        let directory = container.appendingPathComponent("list", isDirectory: true)
        guard let manifest = try? Data(contentsOf: directory.appendingPathComponent("manifest.json")),
              let signature = try? String(contentsOf: directory.appendingPathComponent("manifest.json.sig"),
                                          encoding: .utf8),
              let domains = try? Data(contentsOf: directory.appendingPathComponent("domains.packed"),
                                      options: .mappedIfSafe) else { return false }

        do {
            try store.load(manifestData: manifest, signatureHex: signature, domainsData: domains)
            if let terms = try? Data(contentsOf: directory.appendingPathComponent("terms.json")),
               let sha = try? store.verifiedManifest(manifestData: manifest,
                                                     signatureHex: signature)
                    .artifacts["terms.json"]?.sha256 {
                try store.loadHostTerms(termsData: terms, expectedSHA256: sha)
            }
            return true
        } catch {
            NSLog("[Hisn] site check could not read the installed list: %@", "\(error)")
            return false
        }
    }

    private static func loadBundledSeed(into store: BlocklistStore) -> Bool {
        guard let manifestURL = Bundle.main.url(forResource: "manifest", withExtension: "json"),
              let signatureURL = Bundle.main.url(forResource: "manifest.json", withExtension: "sig"),
              let domainsURL = Bundle.main.url(forResource: "domains_core", withExtension: "txt"),
              let manifest = try? Data(contentsOf: manifestURL),
              let signature = try? String(contentsOf: signatureURL, encoding: .utf8),
              let domains = try? Data(contentsOf: domainsURL, options: .mappedIfSafe)
        else { return false }

        do {
            try store.load(manifestData: manifest, signatureHex: signature,
                           domainsData: domains, artifact: "domains_core.txt")
            if let termsURL = Bundle.main.url(forResource: "terms", withExtension: "json"),
               let terms = try? Data(contentsOf: termsURL),
               let sha = try? store.verifiedManifest(manifestData: manifest,
                                                     signatureHex: signature)
                    .artifacts["terms.json"]?.sha256 {
                try store.loadHostTerms(termsData: terms, expectedSHA256: sha)
            }
            return true
        } catch {
            NSLog("[Hisn] site check could not read the bundled seed: %@", "\(error)")
            return false
        }
    }
}
