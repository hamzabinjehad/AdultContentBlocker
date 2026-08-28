import Foundation

/// Downloads new blocklists and hands them to `BlocklistStore` for verification.
///
/// Note what this type does NOT do: it never writes a list into the shared
/// container until `BlocklistStore.load` has accepted it. A partially written
/// or unverified list must never be visible to the network extension, which
/// loads whatever it finds there at startup.
public actor ListUpdater {

    public static let shared = ListUpdater()

    private let base = URL(string:
        "https://raw.githubusercontent.com/hisn-app/blocklist/lists")!
    private let minimumInterval: TimeInterval = 6 * 3600

    private init() {}

    public func updateIfStale() async {
        let defaults = UserDefaults(suiteName: LockStore.appGroup)
        let last = defaults?.object(forKey: "lastListCheck") as? Date ?? .distantPast
        guard Date().timeIntervalSince(last) > minimumInterval else { return }
        await update()
    }

    public func update() async {
        do {
            async let manifestBytes = data("manifest.json")
            async let sigBytes = data("manifest.json.sig")
            async let domainBytes = data("domains.packed")

            let (manifest, sig, domains) =
                try await (manifestBytes, sigBytes, domainBytes)
            let signature = String(decoding: sig, as: UTF8.self)

            // Verify BEFORE anything touches the shared container.
            try BlocklistStore.shared.load(manifestData: manifest,
                                           signatureHex: signature,
                                           domainsData: domains)

            try writeToContainer(manifest: manifest,
                                 signature: sig,
                                 domains: domains)

            let defaults = UserDefaults(suiteName: LockStore.appGroup)
            defaults?.set(Date(), forKey: "lastListCheck")
            defaults?.set(BlocklistStore.shared.version, forKey: "listVersion")
        } catch {
            // A failed update is a non-event: the previous verified list stays
            // in place. Never fall back to "no list" on error.
            NSLog("[Hisn] list update failed, keeping current list: %@", "\(error)")
        }
    }

    private func data(_ name: String) async throws -> Data {
        var request = URLRequest(url: base.appendingPathComponent(name))
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return data
    }

    /// Atomic swap: write to a temp directory, then replace. The extension must
    /// never observe a half-written list.
    private func writeToContainer(manifest: Data, signature: Data, domains: Data) throws {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: LockStore.appGroup)
        else { throw CocoaError(.fileNoSuchFile) }

        let fm = FileManager.default
        let staging = container.appendingPathComponent("list.staging", isDirectory: true)
        let live = container.appendingPathComponent("list", isDirectory: true)

        try? fm.removeItem(at: staging)
        try fm.createDirectory(at: staging, withIntermediateDirectories: true)
        try manifest.write(to: staging.appendingPathComponent("manifest.json"))
        try signature.write(to: staging.appendingPathComponent("manifest.json.sig"))
        try domains.write(to: staging.appendingPathComponent("domains.packed"))

        if fm.fileExists(atPath: live.path) {
            _ = try fm.replaceItemAt(live, withItemAt: staging)
        } else {
            try fm.moveItem(at: staging, to: live)
        }
    }
}
