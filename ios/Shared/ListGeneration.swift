import CryptoKit
import Foundation

/// One signed published list generation, verified before any byte is used.
///
/// The same manifest and artifacts the Mac, browser extension and Android
/// client accept. iOS had no download path; this type is the trust boundary for
/// adding one. The public key is pinned here, not fetched, and the signature
/// covers the exact manifest bytes, whose artifact hashes then cover the
/// domain and terms files.
enum ListGeneration {
    /// The blocklist signing key. Keep in step with `blocklist/public_key.hex`;
    /// the Python suite compares the two.
    static let pinnedPublicKeyHex =
        "eb6751a0429413d0cfb24a778f7d6ecdd8436e573af94c325de5fcbd38e59ede"

    static let maximumManifestBytes = 64 << 10
    static let maximumArtifactBytes = 8 << 20
    static let minimumDomains = 1_000
    static let maximumDomains = 250_000

    enum Failure: Error, Equatable {
        case manifestSize
        case signatureFormat
        case publicKey
        case signature
        case schema
        case version
        case coverage
        case artifact
        case domains
        case terms
    }

    struct Artifact: Decodable {
        let sha256: String
        let bytes: Int
    }

    struct Manifest: Decodable {
        let schema: Int
        let version: Int
        let coreDomainCount: Int
        let domainCount: Int
        let artifacts: [String: Artifact]

        enum CodingKeys: String, CodingKey {
            case schema, version, artifacts
            case coreDomainCount = "core_domain_count"
            case domainCount = "domain_count"
        }
    }

    struct Verified {
        let version: Int
        let coreDomainCount: Int
        let termsVersion: Int
        let manifest: Data
        let signatureHex: String
        let domains: Data
        let terms: Data
    }

    /// Verify exact manifest bytes, the Ed25519 signature, both artifact hashes
    /// and the parsers the DNS extension will use. Any failure rejects the whole
    /// generation; a partial list is never installed.
    static func verify(manifestData: Data, signatureHex: String, domains: Data, terms: Data,
                       publicKeyHex: String = pinnedPublicKeyHex) throws -> Verified {
        guard manifestData.count > 0, manifestData.count <= maximumManifestBytes else {
            throw Failure.manifestSize
        }
        let signatureText = signatureHex.trimmingCharacters(in: .whitespacesAndNewlines)
        guard signatureText.count == 128,
              let signature = hex(signatureText), signature.count == 64 else {
            throw Failure.signatureFormat
        }
        guard let keyData = hex(publicKeyHex), keyData.count == 32,
              let publicKey = try? Curve25519.Signing.PublicKey(rawRepresentation: keyData) else {
            throw Failure.publicKey
        }
        guard publicKey.isValidSignature(signature, for: manifestData) else {
            throw Failure.signature
        }
        let manifest: Manifest
        do {
            manifest = try JSONDecoder().decode(Manifest.self, from: manifestData)
        } catch {
            throw Failure.schema
        }
        guard manifest.schema == 1 else { throw Failure.schema }
        guard manifest.version >= 1, manifest.version <= 1_000_000 else { throw Failure.version }
        guard manifest.coreDomainCount >= minimumDomains,
              manifest.coreDomainCount <= maximumDomains,
              manifest.domainCount >= manifest.coreDomainCount else { throw Failure.coverage }
        guard let domainsArtifact = manifest.artifacts["domains_core.txt"],
              let termsArtifact = manifest.artifacts["terms.json"],
              domainsArtifact.bytes == domains.count,
              termsArtifact.bytes == terms.count,
              domainsArtifact.bytes > 0, domainsArtifact.bytes <= maximumArtifactBytes,
              termsArtifact.bytes > 0, termsArtifact.bytes <= maximumArtifactBytes,
              sha256Hex(domains) == domainsArtifact.sha256.lowercased(),
              sha256Hex(terms) == termsArtifact.sha256.lowercased() else {
            throw Failure.artifact
        }
        // The published core file carries a generated two-line comment header.
        // It is covered by the signed hash, but it is not a domain and must not
        // be counted or handed to the DNS index as one.
        let domainFile = canonicalDomains(domains)
        let index: DomainIndex
        do {
            index = try DomainIndex(fileData: domainFile)
        } catch {
            throw Failure.domains
        }
        guard index.count == manifest.coreDomainCount else { throw Failure.domains }
        let payload: HostTermPolicy.Payload
        do {
            payload = try JSONDecoder().decode(HostTermPolicy.Payload.self, from: terms)
        } catch {
            throw Failure.terms
        }
        guard payload.schema == 1, payload.version == manifest.version,
              (try? HostTermPolicy(payload: payload)) != nil else { throw Failure.terms }
        return Verified(version: manifest.version, coreDomainCount: manifest.coreDomainCount,
                        termsVersion: payload.version, manifest: manifestData,
                        signatureHex: signatureText, domains: domainFile, terms: terms)
    }

    /// Strip blank lines and `#` comments, normalize CRLF, and keep one
    /// trailing newline. The signed bytes are still what the manifest hash
    /// covers; only the parsed/installed domain file is canonicalized.
    static func canonicalDomains(_ data: Data) -> Data {
        var out = Data()
        out.reserveCapacity(data.count)
        for raw in data.split(separator: 0x0A, omittingEmptySubsequences: false) {
            var line = raw
            if line.last == 0x0D { line.removeLast() }
            if line.isEmpty || line.first == 0x23 { continue }
            out.append(contentsOf: line)
            out.append(0x0A)
        }
        return out
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func hex(_ text: String) -> Data? {
        guard text.count.isMultiple(of: 2) else { return nil }
        var data = Data(capacity: text.count / 2)
        var index = text.startIndex
        while index < text.endIndex {
            let next = text.index(index, offsetBy: 2)
            guard let byte = UInt8(String(text[index..<next]), radix: 16) else { return nil }
            data.append(byte)
            index = next
        }
        return data
    }
}
