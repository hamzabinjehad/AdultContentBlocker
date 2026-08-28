import NetworkExtension
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

    // MARK: - Lifecycle

    override func startFilter(completionHandler: @escaping (Error?) -> Void) {
        NSLog("[Hisn] filter starting")

        loadListFromDisk()
        refreshLockState()

        // The lock deadline can change while the filter runs (a partner grants
        // early release, the user extends). Re-read periodically rather than
        // caching for the life of the process.
        let timer = DispatchSource.makeTimerSource(queue: .global(qos: .utility))
        timer.schedule(deadline: .now() + 30, repeating: 30)
        timer.setEventHandler { [weak self] in self?.refreshLockState() }
        timer.resume()
        refreshTimer = timer

        completionHandler(nil)
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
        let strict = state.mode == "strict" && LockStore.trustedNow() < state.deadline
        strictModeActive.withLock { $0 = strict }

        if let allow = UserDefaults(suiteName: LockStore.appGroup)?
            .stringArray(forKey: "allowlist") {
            store.setAllowlist(allow)
        }
    }

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
    private func loadListFromDisk() {
        let count: Int
        if loadDownloadedList() || loadBundledSeed() {
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
