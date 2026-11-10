import Foundation

/// Fetches one published list generation for the DNS filter, verifies it with
/// the pinned Ed25519 key and hands it to `ListGenerationStore`.
///
/// This is deliberately not a background scheduler: iOS suspends the app, and a
/// list is useful only when the DNS proxy is enabled. The app calls it on a
/// foreground refresh at most once a day. The proxy reads the active generation
/// when it starts; a download made while the proxy runs applies on its next
/// start or a DNS toggle.
enum ListUpdater {
    static let base = URL(string: "https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists")!
    static let interval: TimeInterval = 24 * 60 * 60
    private static let lastCheckKey = "hisn.list.lastCheck"
    private static let versionKey = "hisn.list.version"
    private static let lastErrorKey = "hisn.list.error"

    static func checkIfDue() async {
        let defaults = UserDefaults.standard
        let now = Date().timeIntervalSince1970
        if now - defaults.double(forKey: lastCheckKey) < interval { return }
        defaults.set(now, forKey: lastCheckKey)
        guard ListGenerationStore.defaultRoot() != nil else { return }
        do {
            let manifest = try await fetch("manifest.json", limit: ListGeneration.maximumManifestBytes)
            let signature = String(data: try await fetch("manifest.json.sig", limit: 512),
                                   encoding: .utf8) ?? ""
            let domains = try await fetch("domains_core.txt", limit: ListGeneration.maximumArtifactBytes)
            let terms = try await fetch("terms.json", limit: ListGeneration.maximumArtifactBytes)
            let verified = try ListGeneration.verify(manifestData: manifest, signatureHex: signature,
                                                     domains: domains, terms: terms)
            let version = try ListGenerationStore.install(verified)
            defaults.set(version, forKey: versionKey)
            defaults.removeObject(forKey: lastErrorKey)
        } catch {
            // Keep the previous generation and report only a coarse reason; no
            // queried name, response body or browsing material is stored.
            defaults.set(String(describing: type(of: error)), forKey: lastErrorKey)
        }
    }

    private static func fetch(_ name: String, limit: Int) async throws -> Data {
        var request = URLRequest(url: base.appendingPathComponent(name))
        request.timeoutInterval = 30
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("no-store", forHTTPHeaderField: "Cache-Control")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              data.count > 0, data.count <= limit else {
            throw ListGeneration.Failure.artifact
        }
        return data
    }
}
