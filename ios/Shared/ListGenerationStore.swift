import Foundation

/// Atomic storage for one verified published generation in the App Group.
///
/// The app writes; the DNS proxy reads. The pointer file is the commit point:
/// until it moves, every reader resolves the previous generation. The floor
/// never moves backward, so a replayed or rolled-back offer is refused after a
/// restart as well.
enum ListGenerationStore {
    enum Failure: Error, Equatable {
        case unavailable
        case rollback
        case storage
        case invalidGeneration
    }

    static let folder = "list-generations"
    static let pointerName = "active"
    static let floorName = "floor"

    /// The App Group root, or nil when the entitlement is unavailable (for
    /// example a simulator build without the group). Callers then use the
    /// bundled assets.
    static func defaultRoot() -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: DNSFilterAssets.group)?
            .appendingPathComponent(folder, isDirectory: true)
    }

    /// The directory a reader should try, or nil when no generation is active.
    static func activeDirectory(root: URL? = defaultRoot()) -> URL? {
        guard let root,
              let text = try? String(contentsOf: root.appendingPathComponent(pointerName),
                                     encoding: .utf8),
              let first = text.split(whereSeparator: \.isNewline).first,
              let version = Int(first), version >= 1 else { return nil }
        let directory = generationDirectory(root, version)
        var isDirectory: ObjCBool = false
        guard FileManager.default.fileExists(atPath: directory.path, isDirectory: &isDirectory),
              isDirectory.boolValue else { return nil }
        return directory
    }

    static func floor(root: URL? = defaultRoot()) -> Int {
        guard let root else { return 0 }
        let file = (try? String(contentsOf: root.appendingPathComponent(floorName), encoding: .utf8))
            .flatMap { Int($0.trimmingCharacters(in: .whitespacesAndNewlines)) } ?? 0
        // The pointer is the commit; reading it as a floor closes the window
        // between the pointer write and the separate floor write.
        return max(file, pointerVersion(root))
    }

    /// Verify once more through the same loader the proxy uses, then move the
    /// pointer. A failed load leaves the previous generation active.
    @discardableResult
    static func install(_ verified: ListGeneration.Verified, root: URL? = defaultRoot()) throws -> Int {
        guard let root else { throw Failure.unavailable }
        guard verified.version >= floor(root: root) else { throw Failure.rollback }
        let fileManager = FileManager.default
        let staging = root.appendingPathComponent(".staging-\(UUID().uuidString)", isDirectory: true)
        do {
            try fileManager.createDirectory(at: root, withIntermediateDirectories: true)
            try fileManager.createDirectory(at: staging, withIntermediateDirectories: true)
            try verified.domains.write(to: staging.appendingPathComponent(DNSFilterAssets.domainsName),
                                       options: .atomic)
            try verified.terms.write(to: staging.appendingPathComponent(DNSFilterAssets.termsName),
                                     options: .atomic)
            try verified.manifest.write(to: staging.appendingPathComponent("manifest.json"),
                                        options: .atomic)
            try Data(verified.signatureHex.utf8)
                .write(to: staging.appendingPathComponent("manifest.json.sig"), options: .atomic)
            let metadata: [String: Any] = [
                "schema": DNSFilterAssets.schema,
                "version": verified.version,
                "count": verified.coreDomainCount,
                "domains_sha256": ListGeneration.sha256Hex(verified.domains),
                "domains_bytes": verified.domains.count,
                "terms_sha256": ListGeneration.sha256Hex(verified.terms),
                "terms_bytes": verified.terms.count,
                "terms_version": verified.termsVersion,
                "adult_tlds": AdultTldGuard.tlds.sorted(),
            ]
            let metadataData = try JSONSerialization.data(withJSONObject: metadata, options: [.sortedKeys])
            try metadataData.write(to: staging.appendingPathComponent(DNSFilterAssets.metadataName),
                                   options: .atomic)

            // The proxy will use DNSFilterAssets.load on exactly this directory.
            // Rejecting here means a broken generation never becomes the pointer.
            _ = try DNSFilterAssets.load(directory: staging)

            let target = generationDirectory(root, verified.version)
            try? fileManager.removeItem(at: target)
            try fileManager.moveItem(at: staging, to: target)
            try Data("\(verified.version)\n".utf8)
                .write(to: root.appendingPathComponent(pointerName), options: .atomic)
            try Data("\(verified.version)\n".utf8)
                .write(to: root.appendingPathComponent(floorName), options: .atomic)
            cleanup(root, active: verified.version)
            return verified.version
        } catch let failure as Failure {
            try? fileManager.removeItem(at: staging)
            throw failure
        } catch {
            try? fileManager.removeItem(at: staging)
            throw Failure.invalidGeneration
        }
    }

    private static func pointerVersion(_ root: URL) -> Int {
        guard let text = try? String(contentsOf: root.appendingPathComponent(pointerName),
                                     encoding: .utf8),
              let first = text.split(whereSeparator: \.isNewline).first,
              let version = Int(first) else { return 0 }
        return version
    }

    private static func generationDirectory(_ root: URL, _ version: Int) -> URL {
        root.appendingPathComponent("gen-\(version)", isDirectory: true)
    }

    /// Keep the active generation and one predecessor.
    private static func cleanup(_ root: URL, active: Int) {
        guard let children = try? FileManager.default.contentsOfDirectory(atPath: root.path) else { return }
        for name in children where name.hasPrefix("gen-") {
            guard let version = Int(name.dropFirst(4)), version != active, version != active - 1 else {
                continue
            }
            try? FileManager.default.removeItem(at: root.appendingPathComponent(name, isDirectory: true))
        }
    }
}
