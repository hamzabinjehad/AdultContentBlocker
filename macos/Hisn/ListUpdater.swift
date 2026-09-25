import Foundation

/// Downloads new blocklists and hands them to `BlocklistStore` for verification.
///
/// Note what this type does NOT do: it never writes a list into the shared
/// container until every artifact of the generation has been verified. A
/// partially written or unverified list must never be visible to the network
/// extension, which loads whatever it finds there at startup.
///
/// A GENERATION, NOT A FILE
/// ------------------------
/// The published list is several artifacts under one signed manifest: the
/// domain list, and the keyword layer (`terms.json`) that covers domains
/// registered since the list was built. This used to download the domains
/// alone. The keyword layer therefore never updated on the Mac at all — a
/// term added upstream reached the browser's static rules on the next
/// extension release and reached the filter never, and a false-positive fix
/// to the term list could not be shipped to an installed Mac by any path.
///
/// Now the four files — manifest, signature, domains, terms — are fetched,
/// every hash is checked against the signed manifest, and only then is the
/// whole set swapped into the container atomically. If any one fails, none
/// of them is applied and the previous generation stays in force: a stale
/// keyword layer next to a fresh domain list is a state nobody can reason
/// about, and the manifest's version would then describe neither.
public actor ListUpdater {

    public static let shared = ListUpdater()

    private let base = URL(string:
        "https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists")!
    private let minimumInterval: TimeInterval = 6 * 3600

    /// What the shared container holds after a successful update, in the
    /// order they are written. `FilterDataProvider` reads exactly these.
    public static let generationFiles = ["manifest.json", "manifest.json.sig",
                                         "domains.packed.deflate", "terms.json"]

    public enum GenerationError: LocalizedError {
        case incomplete(String)
        case hashMismatch(String)
        public var errorDescription: String? {
            switch self {
            case let .incomplete(name):   return "published generation lacks \(name)"
            case let .hashMismatch(name): return "\(name) does not match the signed manifest"
            }
        }
    }

    private init() {}

    /// The app stays running for the whole of a lock, so a check at launch
    /// alone meant "check once a month". The first `updateIfStale` starts an
    /// hourly tick; each tick is gated by `minimumInterval`, so a fresh list
    /// costs one small manifest fetch every six hours and a failed attempt
    /// simply gets the next tick — the bounded retry, with no tighter loop
    /// for a file that changes daily.
    private var refreshTask: Task<Void, Never>?
    private let refreshEvery: TimeInterval = 3600

    public func updateIfStale() async {
        startPeriodicRefresh()
        let defaults = UserDefaults(suiteName: LockStore.appGroup)
        let last = defaults?.object(forKey: "lastListCheck") as? Date ?? .distantPast
        guard Date().timeIntervalSince(last) > minimumInterval else { return }
        await update()
    }

    private func startPeriodicRefresh() {
        guard refreshTask == nil else { return }
        refreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64((self?.refreshEvery ?? 3600) * 1e9))
                await self?.updateIfStale()
            }
        }
    }

    public func update() async {
        let defaults = UserDefaults(suiteName: LockStore.appGroup)
        do {
            async let manifestBytes = data("manifest.json")
            async let sigBytes = data("manifest.json.sig")
            async let domainBytes = data("domains.packed.deflate")
            async let termBytes = data("terms.json")

            let (manifest, sig, deflated, terms) =
                try await (manifestBytes, sigBytes, domainBytes, termBytes)
            let signature = String(decoding: sig, as: UTF8.self)

            // Verify the WHOLE generation before anything is applied anywhere:
            // signature, then every artifact's hash, then the shapes. Only the
            // domain load below can still refuse (rollback, size floor), and
            // it refuses before the terms are installed.
            let store = BlocklistStore.shared
            let verified = try store.verifiedManifest(manifestData: manifest,
                                                      signatureHex: signature)
            // The download is checked against the signed hash BEFORE it is
            // inflated — nothing unverified is decompressed — and the result
            // is checked again as `domains.packed` by `store.load` below.
            guard let deflateSHA = verified.artifacts["domains.packed.deflate"]?.sha256 else {
                throw GenerationError.incomplete("domains.packed.deflate")
            }
            guard BlocklistStore.sha256Hex(deflated) == deflateSHA else {
                throw GenerationError.hashMismatch("domains.packed.deflate")
            }
            let domains = try BlocklistStore.inflate(deflated)
            guard let termsSHA = verified.artifacts["terms.json"]?.sha256 else {
                throw GenerationError.incomplete("terms.json")
            }
            guard BlocklistStore.sha256Hex(terms) == termsSHA else {
                throw GenerationError.hashMismatch("terms.json")
            }
            // Parse the terms into a scratch store first so a malformed file
            // is refused before the shared store has changed at all.
            try BlocklistStore().loadHostTerms(termsData: terms, expectedSHA256: termsSHA)

            try store.load(manifestData: manifest, signatureHex: signature,
                           domainsData: domains)
            try store.loadHostTerms(termsData: terms, expectedSHA256: termsSHA)

            try writeToContainer(manifest: manifest, signature: sig,
                                 domains: domains, terms: terms)

            // And to the filter, which cannot read this user's container (it
            // runs as root) and re-verifies everything before using it. No
            // filter is the normal state without the paid entitlements.
            if FilterLink.shared.isConfigured,
               let reply = await FilterLink.shared.installGeneration(
                   manifest: manifest, signature: sig, domains: deflated, terms: terms),
               !reply.installed {
                NSLog("[Hisn] the filter refused generation v%d: %@", store.version,
                      reply.error ?? "no reason given")
            }

            defaults?.set(Date(), forKey: "lastListCheck")
            defaults?.set(Date(), forKey: "lastListUpdate")
            defaults?.set(store.version, forKey: "listVersion")
            defaults?.removeObject(forKey: "lastListError")
        } catch {
            // A failed update is a non-event for enforcement: the previous
            // verified generation stays in place. Never fall back to "no list"
            // on error. It is NOT a non-event for the status header, which
            // reports the reason so a permanently failing update is visible.
            NSLog("[Hisn] list update failed, keeping current list: %@", "\(error)")
            defaults?.set(Date(), forKey: "lastListCheck")
            defaults?.set("\(error)", forKey: "lastListError")
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
    /// never observe a half-written generation.
    private func writeToContainer(manifest: Data, signature: Data,
                                  domains: Data, terms: Data) throws {
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
        try terms.write(to: staging.appendingPathComponent("terms.json"))

        if fm.fileExists(atPath: live.path) {
            _ = try fm.replaceItemAt(live, withItemAt: staging)
        } else {
            try fm.moveItem(at: staging, to: live)
        }
    }
}
