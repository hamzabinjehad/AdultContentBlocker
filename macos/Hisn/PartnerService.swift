import Foundation
import CryptoKit

/// Proof that a second person authorised ending a lock early.
///
/// Only `PartnerService.approve` can produce one, and only after the partner's
/// signature has verified. Passing this value around is how the rest of the
/// codebase proves, at the type level, that an early release was authorised —
/// there is no way to construct one from inside the app otherwise.
public struct PartnerApproval {
    public let challenge: String
    public let approvedAt: Date

    fileprivate init(challenge: String, approvedAt: Date) {
        self.challenge = challenge
        self.approvedAt = approvedAt
    }
}

/// Accountability-partner release, with no server.
///
/// It used to post to `api.hisn.app` — a service that does not exist — and
/// there was no screen to type an approval into anyway, so the only immediate
/// exit the Lock page promised could not be taken. It is now a signature:
///
///  * At setup, with both people present, the partner makes a key pair on
///    their own device (`partner/index.html`) and gives Hisn the PUBLIC half.
///    The private half never leaves the partner's device.
///  * To end a lock early, Hisn shows a release code naming this lock. The
///    person sends it to the partner, who — if they agree — signs it and
///    sends back the approval. Hisn checks the signature and ends the lock.
///
/// Why this is sound where a local code would not be: verification needs only
/// the public key, which is no secret, so there is nothing in the binary to
/// extract. The code names the lock by its start time and a random nonce
/// drawn when it began, so an approval cannot be prepared before the lock
/// starts or reused for the next one. And because the filter's authority
/// holds the same key and checks the same signature, the release is refused
/// there too unless it is genuine.
///
/// Nothing about browsing is ever involved. The partner learns that a release
/// was asked for — because the person asked them — and nothing else.
public enum PartnerService {

    public static let keyDefaultsKey = "partnerPublicKey"
    public static let challengePrefix = "hisn-release-v1"

    public enum PartnerError: LocalizedError {
        case notConfigured
        case badKey
        case badApproval
        case locked
        case noLock

        public var errorDescription: String? {
            switch self {
            case .notConfigured:
                return "No accountability partner is set up. Add your partner’s key "
                    + "in Settings while no lock is running."
            case .badKey:
                return "That is not a partner key. It should be the code your "
                    + "partner’s Hisn Partner page shows under “Your key”."
            case .badApproval:
                return "That approval does not match this lock and your partner’s "
                    + "key. Ask your partner to sign the release code shown here."
            case .locked:
                return "A lock is running, so the partner key cannot be changed "
                    + "until it ends. (Removing it is still allowed.)"
            case .noLock:
                return "No lock is running."
            }
        }
    }

    // MARK: Keys

    /// A partner public key in any of the forms a person might paste — the
    /// page's own `HISN-PK-…` form, bare base64url/base64, or hex — as its
    /// canonical base64url text, or nil if it is not a 32-byte Ed25519 key.
    public static func canonicalKey(_ text: String) -> String? {
        var t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.uppercased().hasPrefix("HISN-PK-") { t = String(t.dropFirst(8)) }
        // 64 hex digits are also valid base64 (of 48 bytes), so try both and
        // keep whichever is a 32-byte key.
        let raw = [Data(base64URL: t), Data(hexString: t)].compactMap { $0 }
            .first { $0.count == 32 }
        guard let raw,
              (try? Curve25519.Signing.PublicKey(rawRepresentation: raw)) != nil
        else { return nil }
        return raw.base64URL
    }

    /// Short, readable fingerprint for the Settings page, so the two people
    /// can check they set up the same key without reading 43 characters.
    public static func fingerprint(_ key: String) -> String {
        guard let raw = Data(base64URL: key) else { return "?" }
        let hex = SHA256.hash(data: raw).prefix(6).map { String(format: "%02X", $0) }.joined()
        return stride(from: 0, to: hex.count, by: 4).map {
            let s = hex.index(hex.startIndex, offsetBy: $0)
            return String(hex[s..<hex.index(s, offsetBy: 4)])
        }.joined(separator: "-")
    }

    private static var defaults: UserDefaults? { UserDefaults(suiteName: LockStore.appGroup) }

    public static func currentKey() -> String? {
        defaults?.string(forKey: keyDefaultsKey).flatMap(canonicalKey)
    }

    /// Set, replace or remove the partner key. Setting or replacing it while a
    /// lock runs is refused — swapping in a key you hold yourself is the
    /// obvious way to approve your own release. Removing it only removes an
    /// exit, so it is always allowed.
    public static func saveKey(_ key: String?, locked: Bool) throws {
        guard let key else {
            defaults?.removeObject(forKey: keyDefaultsKey)
            return
        }
        guard let canonical = canonicalKey(key) else { throw PartnerError.badKey }
        if let refusal = keyRefusal(current: currentKey(), proposed: canonical, locked: locked) {
            throw refusal
        }
        defaults?.set(canonical, forKey: keyDefaultsKey)
    }

    /// Shared with `PolicyAuthority`.
    public static func keyRefusal(current: String?, proposed: String?,
                                  locked: Bool) -> PartnerError? {
        guard locked, let proposed, proposed != current else { return nil }
        return .locked
    }

    // MARK: Release

    /// The code that names this lock. Both the start time and the nonce drawn
    /// when the lock began, so it cannot be known before the lock exists.
    public static func challenge(for lock: LockStore.LockState) -> String {
        "\(challengePrefix).\(Int(lock.startedAt.timeIntervalSince1970)).\(lock.releaseNonce ?? "none")"
    }

    public static func verify(approval: String, challenge: String, key: String) -> Bool {
        let text = approval.trimmingCharacters(in: .whitespacesAndNewlines)
        let sigText = text.uppercased().hasPrefix("HISN-OK-") ? String(text.dropFirst(8)) : text
        guard let raw = Data(base64URL: key),
              let publicKey = try? Curve25519.Signing.PublicKey(rawRepresentation: raw),
              let signature = Data(base64URL: sigText), signature.count == 64
        else { return false }
        return publicKey.isValidSignature(signature, for: Data(challenge.utf8))
    }

    /// Check an approval against a lock and a key.
    public static func approve(_ approval: String, for lock: LockStore.LockState,
                               key: String?) throws -> PartnerApproval {
        guard let key else { throw PartnerError.notConfigured }
        let challenge = challenge(for: lock)
        guard lock.releaseNonce != nil,
              verify(approval: approval, challenge: challenge, key: key) else {
            throw PartnerError.badApproval
        }
        return PartnerApproval(challenge: challenge, approvedAt: Date())
    }

    /// A fresh nonce for a new lock.
    public static func newNonce() -> String {
        var bytes = [UInt8](repeating: 0, count: 9)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64URL
    }
}

// MARK: - Encodings

extension Data {
    /// base64url without padding, as the partner page writes it.
    var base64URL: String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// Accepts base64url or plain base64, padded or not.
    init?(base64URL text: String) {
        var s = text.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        guard !s.isEmpty, s.allSatisfy({ $0.isLetter || $0.isNumber || "+/=".contains($0) })
        else { return nil }
        while s.count % 4 != 0 { s.append("=") }
        self.init(base64Encoded: s)
    }
}
