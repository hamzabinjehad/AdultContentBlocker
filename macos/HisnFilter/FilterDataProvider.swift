import NetworkExtension
import Foundation

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
    private var lockState = LockStore.LockState.unlocked
    private var refreshTimer: DispatchSourceTimer?

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

    private var isStrict: Bool {
        lockState.mode == "strict" && LockStore.isLocked()
    }

    private func refreshLockState() {
        lockState = LockStore.read()

        if let allow = UserDefaults(suiteName: LockStore.appGroup)?
            .stringArray(forKey: "allowlist") {
            store.setAllowlist(allow)
        }
    }

    /// Pull a hostname out of a flow.
    ///
    /// `remoteHostname` is populated for flows the system resolved by name,
    /// which covers browser traffic. When only an endpoint is available we fall
    /// back to it; a bare IP will simply not match any domain rule, which is
    /// the correct outcome for the blocklist and a deliberate deny in strict
    /// mode.
    private static func hostname(for flow: NEFilterFlow) -> String? {
        if let browserFlow = flow as? NEFilterBrowserFlow,
           let url = browserFlow.url, let host = url.host {
            return host
        }
        if let socketFlow = flow as? NEFilterSocketFlow {
            if let name = socketFlow.remoteHostname, !name.isEmpty {
                return name
            }
            if let endpoint = socketFlow.remoteEndpoint as? NWHostEndpoint {
                return endpoint.hostname
            }
        }
        return nil
    }

    // MARK: - List loading

    /// Load the last verified list from the shared container.
    ///
    /// If verification fails we run with an EMPTY blocklist in normal mode —
    /// but note what happens in strict mode: an empty allowlist denies
    /// everything. That asymmetry is deliberate. A corrupt list should never
    /// silently turn strict mode into an open door.
    private func loadListFromDisk() {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: LockStore.appGroup)
        else {
            NSLog("[Hisn] no app group container")
            return
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
        } catch {
            NSLog("[Hisn] REFUSING unverified list: %@", "\(error)")
        }
    }
}
