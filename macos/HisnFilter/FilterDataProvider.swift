import NetworkExtension
import Security
import Foundation
import Network
import os

/// Socket-level content filter.
///
/// WHY THIS EXISTS AND DNS FILTERING DOES NOT REPLACE IT
/// ----------------------------------------------------
/// DNS filtering is the cheap, cross-platform layer, and it is defeated by one
/// click: turn on any VPN and DNS resolution moves inside the tunnel where our
/// resolver never sees it. `NEFilterDataProvider` runs at the *socket flow*
/// level, so it evaluates a connection when the app opens it — before the
/// packets reach a VPN interface. A VPN therefore does not bypass this filter,
/// which is why it, not DNS, is the load-bearing control on macOS.
///
/// It also means we see flows from every browser, every Electron app and every
/// command-line tool, not just the browsers we ship policy for.
///
/// PERFORMANCE
/// -----------
/// `handleNewFlow` is on the connection path for every socket the machine
/// opens. It must not block, allocate heavily, or touch the network. Verdicts
/// come from an in-memory hash set; anything slower belongs elsewhere.
final class FilterDataProvider: NEFilterDataProvider {

    private let store = BlocklistStore.shared
    private var refreshTimer: DispatchSourceTimer?

    /// Version of the downloaded list currently installed, or 0 while running
    /// on the bundled seed. The seed shares its manifest version with the full
    /// published list, so this is tracked separately rather than read back from
    /// `store.version` — otherwise a fresh install would never swap its
    /// 148k-domain seed for the ~982k-domain download.
    private var installedDownloadedVersion = 0

    /// Whether strict mode is in force right now.
    ///
    /// Cached rather than derived per flow. `LockStore.read()` queries the
    /// keychain, stats a file and decodes three JSON blobs, and may write all
    /// three stores back when it self-heals — costs that are fine every thirty
    /// seconds and ruinous once per socket. The refresh timer below is what
    /// keeps it current, which is the job that timer already had.
    ///
    /// Behind a lock because the timer fires on a utility queue while
    /// `handleNewFlow` runs on the provider's own queue. Uncontended, this is
    /// tens of nanoseconds — the keychain query it replaces is milliseconds.
    private let strictModeActive = OSAllocatedUnfairLock(initialState: false)

    /// Signing identifiers of apps whose traffic is refused outright.
    /// Same locking rationale as `strictModeActive` above.
    private let blockedApps = OSAllocatedUnfairLock(initialState: Set<String>())

    // MARK: - Lifecycle

    override func startFilter(completionHandler: @escaping (Error?) -> Void) {
        NSLog("[Hisn] filter starting")

        // Rollback protection across restarts: the highest version this
        // filter ever installed is the floor for what it will accept now. A
        // new process would otherwise start at 0 and take any signed old list.
        store.versionFloor = Self.defaults?.integer(forKey: Self.listVersionKey) ?? 0

        loadListFromDisk()
        loadKeywordLayer()
        refreshLockState()
        publishHealth()

        // The lock deadline can change while the filter runs (a partner grants
        // early release, the user extends). Re-read periodically rather than
        // caching for the life of the process. The same tick publishes a
        // heartbeat: the app's "filter enabled" used to mean only that the
        // preference was saved, and this is what turns it into evidence.
        let timer = DispatchSource.makeTimerSource(queue: .global(qos: .utility))
        timer.schedule(deadline: .now() + 30, repeating: 30)
        timer.setEventHandler { [weak self] in
            self?.refreshLockState()
            self?.reloadListIfUpdated()
            self?.publishHealth()
        }
        timer.resume()
        refreshTimer = timer

        completionHandler(nil)
    }

    // MARK: - Health

    static var defaults: UserDefaults? { UserDefaults(suiteName: LockStore.appGroup) }
    static let listVersionKey = "filterListVersion"

    /// Whether the keyword layer currently installed came from the downloaded
    /// generation, the bundled seed, or nowhere. Reported, never assumed.
    private var keywordSource = "none"

    /// Everything the app needs to say what this filter is ACTUALLY doing,
    /// written to the shared container every tick. Read by
    /// `Enforcement.layers` in the app. Keys are the contract; see there.
    private func publishHealth() {
        guard let d = Self.defaults else { return }
        d.set(Date(), forKey: "filterHeartbeatAt")
        d.set(store.domainCount, forKey: "filterDomainCount")
        d.set(store.hostTermCount, forKey: "filterHostTermCount")
        d.set(keywordSource, forKey: "filterKeywordSource")
        d.set(strictModeActive.withLock { $0 }, forKey: "filterStrictActive")
        d.set(store.version, forKey: Self.listVersionKey)
    }

    override func stopFilter(with reason: NEProviderStopReason,
                             completionHandler: @escaping () -> Void) {
        NSLog("[Hisn] filter stopping, reason: %ld", reason.rawValue)
        refreshTimer?.cancel()
        refreshTimer = nil

        // We cannot refuse to stop — the system decides that. What we can do is
        // leave a record, so the app notices on next launch that the filter was
        // torn down during an active lock and can re-arm and report it.
        if LockStore.isLocked() {
            UserDefaults(suiteName: LockStore.appGroup)?
                .set(Date(), forKey: "filterStoppedDuringLock")
        }
        completionHandler()
    }

    // MARK: - Flow handling

    override func handleNewFlow(_ flow: NEFilterFlow) -> NEFilterNewFlowVerdict {
        let isStrict = strictModeActive.withLock { $0 }

        // App blocking, before any hostname work: a blocked app reaches
        // nothing, whatever host it is asking for. Note what this is NOT — the
        // app still launches and still works offline. macOS gives a normal
        // application no way to stop a launch; refusing the traffic is the
        // whole of what a content filter can do here.
        //
        // The empty check comes first and is doing real work. Resolving the app
        // behind a flow costs several Security framework calls (see
        // `signingIdentifier`), and this runs for every socket the machine
        // opens. Nobody who has not used this feature should pay for it.
        let blocked = blockedApps.withLock { $0 }
        if !blocked.isEmpty,
           let appID = Self.signingIdentifier(for: flow),
           blocked.contains(appID) {
            return .drop()
        }

        guard let host = Self.hostname(for: flow) else {
            // No hostname to judge. In strict mode an unidentifiable flow is
            // exactly what a bypass tool looks like, so deny; otherwise allow.
            return isStrict ? .drop() : .allow()
        }

        if isStrict {
            return store.isAllowedInStrictMode(host: host) ? .allow() : .drop()
        }

        return store.isBlocked(host: host) ? .drop() : .allow()
    }

    // MARK: - State

    private func refreshLockState() {
        let state = LockStore.read()
        // The EFFECTIVE deadline, via the one function the app and the bridge
        // also use — see `LockStore.strictModeActive`.
        let strict = LockStore.strictModeActive(state)
        strictModeActive.withLock { $0 = strict }

        // Both hand-maintained lists, re-read on the same tick. The custom
        // blocks used to be read by the browser extension and ignored here,
        // which meant a domain the person added by hand was blocked in Chrome
        // and reachable from every other app on the machine.
        store.setAllowlist(SiteLists.allowlist())
        store.setCustomBlocks(SiteLists.customBlocks())

        // Apps the person blocked by hand. Cached in the same way and for the
        // same reason as `strictModeActive`: `handleNewFlow` is on the
        // connection path for every socket the machine opens, and reading
        // defaults there would put a preference lookup in front of every one.
        blockedApps.withLock { $0 = Set(UserBlocks.apps()) }
    }

    /// The signing identifier of the app that opened this flow.
    ///
    /// `NEFilterFlow.sourceAppIdentifier` is the obvious API and it is iOS
    /// only — on macOS it does not compile. What macOS gives instead is
    /// `sourceAppAuditToken`, and turning that into something comparable to a
    /// bundle id means asking the Security framework which code the token
    /// belongs to. That is the whole of the extra work here; the identifier it
    /// returns is the same string `UserBlocks.bundleIdentifier(forAppAt:)`
    /// reads out of the app the person picked, which is what lets the two ends
    /// of this feature agree.
    ///
    /// Results are cached by audit token because a browser opens hundreds of
    /// flows and the answer cannot change for a given token — the token
    /// identifies one running process, and a process cannot re-sign itself.
    private static func signingIdentifier(for flow: NEFilterFlow) -> String? {
        guard let token = flow.sourceAppAuditToken else { return nil }

        if let hit = identifierCache.withLock({ $0[token] }) { return hit }

        var code: SecCode?
        let attributes = [kSecGuestAttributeAudit: token] as CFDictionary
        guard SecCodeCopyGuestWithAttributes(nil, attributes, [], &code) == errSecSuccess,
              let code else { return nil }

        var staticCode: SecStaticCode?
        guard SecCodeCopyStaticCode(code, [], &staticCode) == errSecSuccess,
              let staticCode else { return nil }

        var infoRef: CFDictionary?
        guard SecCodeCopySigningInformation(staticCode, [], &infoRef) == errSecSuccess,
              let info = infoRef as? [String: Any],
              let identifier = info[kSecCodeInfoIdentifier as String] as? String
        else { return nil }

        identifierCache.withLock { cache in
            // A bound, not a policy: the cache exists to spare repeated lookups
            // for the handful of apps actually opening sockets, and an unbounded
            // one in a long-lived filter process is a slow leak.
            if cache.count > 512 { cache.removeAll() }
            cache[token] = identifier
        }
        return identifier
    }

    private static let identifierCache =
        OSAllocatedUnfairLock(initialState: [Data: String]())

    /// Pull a hostname out of a flow.
    ///
    /// Every flow the filter sees on macOS is an `NEFilterSocketFlow`.
    /// `NEFilterBrowserFlow` is an iOS-only type and does not exist on this
    /// platform, so there is no browser-flow branch to write.
    ///
    /// `remoteHostname` is populated for flows the system resolved by name,
    /// which covers browser traffic. When only an endpoint is available we fall
    /// back to it; a bare IP will simply not match any domain rule, which is
    /// the correct outcome for the blocklist and a deliberate deny in strict
    /// mode — the same verdict a nil hostname already produces.
    private static func hostname(for flow: NEFilterFlow) -> String? {
        if let host = flow.url?.host, !host.isEmpty {
            return host
        }
        guard let socketFlow = flow as? NEFilterSocketFlow else { return nil }

        if let name = socketFlow.remoteHostname, !name.isEmpty {
            return name
        }
        if #available(macOS 15.0, *),
           case let .hostPort(host, _)? = socketFlow.remoteFlowEndpoint {
            switch host {
            case let .name(name, _): return name
            case let .ipv4(address): return "\(address)"
            case let .ipv6(address): return "\(address)"
            @unknown default: return nil
            }
        }
        return nil
    }

    // MARK: - List loading

    /// Install a verified list, preferring the downloaded one.
    ///
    /// If verification fails we run with an EMPTY blocklist in normal mode —
    /// but note what happens in strict mode: an empty allowlist denies
    /// everything. That asymmetry is deliberate. A corrupt list should never
    /// silently turn strict mode into an open door.
    ///
    /// The bundled seed is the fallback rather than nothing, because "no list"
    /// is the failure this product can least afford: in blocklist mode it
    /// blocks nothing while every layer above still reports that the filter is
    /// running. That is the state the threat model calls worse than being
    /// switched off, since the person stops being careful. On a machine that
    /// has never completed an update — a first launch, or one with no network —
    /// the seed is the difference between enforcing 148k domains and enforcing
    /// none.
    /// Install the keyword layer from the bundled, signed `terms.json`.
    ///
    /// This is what makes the network layer catch a domain registered today —
    /// the row `docs/THREAT_MODEL.md` marks "Open in blocklist mode" — and it
    /// runs in the SOCKET filter, so unlike the browser rules it covers every
    /// application on the Mac.
    ///
    /// Verified against the SHA-256 in the SIGNED manifest on exactly the same
    /// path a downloaded list takes: signature first, then the hash. Being
    /// bundled buys it no trust. If the manifest does not cover `terms.json`
    /// the layer stays off rather than loading unverified terms — failing
    /// closed here means "no keyword matching", never "unchecked keyword
    /// matching". `blocklist/seed.py verify` and `BundledSeedTests` exist so
    /// that state is caught in CI rather than discovered on a user's machine.
    private func loadKeywordLayer() {
        // The downloaded generation first — it is newer by construction — and
        // the bundle only when the container has no verifiable terms. A
        // container written by an older app carries no terms.json; that is
        // the bundled case, not a failure.
        if let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: LockStore.appGroup) {
            let dir = container.appendingPathComponent("list", isDirectory: true)
            if loadKeywordLayer(manifest: dir.appendingPathComponent("manifest.json"),
                                signature: dir.appendingPathComponent("manifest.json.sig"),
                                terms: dir.appendingPathComponent("terms.json"),
                                source: "downloaded") {
                return
            }
        }
        guard let manifestURL = Bundle.main.url(forResource: "manifest",
                                                withExtension: "json"),
              let sigURL = Bundle.main.url(forResource: "manifest.json",
                                           withExtension: "sig"),
              let termsURL = Bundle.main.url(forResource: "terms",
                                             withExtension: "json")
        else {
            NSLog("[Hisn] no bundled terms.json or manifest — keyword layer stays off")
            keywordSource = "none"
            return
        }
        if !loadKeywordLayer(manifest: manifestURL, signature: sigURL,
                             terms: termsURL, source: "bundled") {
            keywordSource = "none"
        }
    }

    /// One keyword layer from one signed manifest. Signature first, then the
    /// hash, then the parse; any failure leaves the current layer untouched.
    @discardableResult
    private func loadKeywordLayer(manifest manifestURL: URL, signature sigURL: URL,
                                  terms termsURL: URL, source: String) -> Bool {
        guard let manifestData = try? Data(contentsOf: manifestURL),
              let sig = try? String(contentsOf: sigURL, encoding: .utf8),
              let termsData = try? Data(contentsOf: termsURL)
        else { return false }
        do {
            let manifest = try store.verifiedManifest(manifestData: manifestData,
                                                      signatureHex: sig)
            guard let expected = manifest.artifacts["terms.json"]?.sha256 else {
                NSLog("[Hisn] %@ manifest does not cover terms.json — not using its keyword layer",
                      source)
                return false
            }
            try store.loadHostTerms(termsData: termsData, expectedSHA256: expected)
            keywordSource = source
            NSLog("[Hisn] keyword layer from %@ generation v%d", source, manifest.version)
            return true
        } catch {
            NSLog("[Hisn] %@ keyword layer rejected: %@", source, "\(error)")
            return false
        }
    }

    /// Re-read the downloaded list when a newer one has landed since startup.
    ///
    /// `loadListFromDisk()` runs exactly once, in `startFilter`. The app's
    /// `ListUpdater` writes a freshly verified list into the shared container
    /// at any point after that, and this process is long-lived — the system
    /// does not restart it when a new list arrives. Without this the machine
    /// keeps enforcing whatever was current at the last filter launch, which on
    /// a first install is the 148k-domain seed, while every status signal still
    /// reads healthy.
    ///
    /// Only the small manifest is decoded to compare versions; the expensive
    /// signature check and parse runs solely when the container genuinely holds
    /// something newer. A failed load leaves the in-memory list untouched.
    private func reloadListIfUpdated() {
        guard let version = downloadedManifestVersion(),
              version > installedDownloadedVersion else { return }

        NSLog("[Hisn] blocklist v%d in container, running v%d — reloading",
              version, installedDownloadedVersion)
        guard loadDownloadedList() else { return }
        installedDownloadedVersion = version
        // The generation is the pair. A new domain list with the old keyword
        // layer is the half-state the updater exists to prevent.
        loadKeywordLayer()
        publishHealth()
        NSLog("[Hisn] reloaded generation v%d: %d domains, %d host terms (%@)",
              version, store.domainCount, store.hostTermCount, keywordSource)
    }

    /// The `version` field of the downloaded manifest, without decoding the
    /// rest or touching the domain artifact.
    private func downloadedManifestVersion() -> Int? {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: LockStore.appGroup)
        else { return nil }
        let url = container.appendingPathComponent("list/manifest.json")
        guard let data = try? Data(contentsOf: url) else { return nil }
        struct VersionOnly: Decodable { let version: Int }
        return (try? JSONDecoder().decode(VersionOnly.self, from: data))?.version
    }

    private func loadListFromDisk() {
        let count: Int
        if loadDownloadedList() {
            installedDownloadedVersion = downloadedManifestVersion() ?? 0
            count = store.domainCount
        } else if loadBundledSeed() {
            count = store.domainCount
        } else {
            count = 0
            NSLog("[Hisn] running with NO blocklist — nothing is blocked in "
                  + "blocklist mode")
        }
        // Publish what is genuinely loaded so the app can report it. The app
        // and this extension are separate processes with separate stores, so
        // the app cannot see this any other way, and a status header that
        // guesses is exactly the dishonesty this is here to prevent.
        UserDefaults(suiteName: LockStore.appGroup)?
            .set(count, forKey: "filterDomainCount")
    }

    private func loadDownloadedList() -> Bool {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: LockStore.appGroup)
        else {
            NSLog("[Hisn] no app group container")
            return false
        }

        let dir = container.appendingPathComponent("list", isDirectory: true)
        do {
            let manifest = try Data(contentsOf: dir.appendingPathComponent("manifest.json"))
            let sig = try String(contentsOf: dir.appendingPathComponent("manifest.json.sig"),
                                 encoding: .utf8)
            let domains = try Data(contentsOf: dir.appendingPathComponent("domains.packed"))
            try store.load(manifestData: manifest,
                           signatureHex: sig,
                           domainsData: domains)
            return true
        } catch {
            NSLog("[Hisn] REFUSING unverified list: %@", "\(error)")
            return false
        }
    }

    /// The core tier shipped inside this bundle, covered by the same signed
    /// manifest as the full list and verified on exactly the same path — being
    /// bundled buys it no trust.
    private func loadBundledSeed() -> Bool {
        guard let manifestURL = Bundle.main.url(forResource: "manifest",
                                                withExtension: "json"),
              let sigURL = Bundle.main.url(forResource: "manifest.json",
                                           withExtension: "sig"),
              let domainsURL = Bundle.main.url(forResource: "domains_core",
                                               withExtension: "txt")
        else {
            NSLog("[Hisn] no bundled seed list")
            return false
        }
        do {
            try store.load(manifestData: try Data(contentsOf: manifestURL),
                           signatureHex: try String(contentsOf: sigURL, encoding: .utf8),
                           domainsData: try Data(contentsOf: domainsURL),
                           artifact: "domains_core.txt")
            NSLog("[Hisn] no downloaded list yet — enforcing the bundled seed")
            return true
        } catch {
            NSLog("[Hisn] REFUSING unverified seed: %@", "\(error)")
            return false
        }
    }
}
