import XCTest
import CryptoKit
@testable import Hisn

/// Accountability-partner release: a signature from a key only the partner
/// holds, over a code that names one lock.
final class PartnerTests: XCTestCase {

    private let partner = Curve25519.Signing.PrivateKey()
    private var publicKey: String { partner.publicKey.rawRepresentation.base64URL }
    private let t0 = Date(timeIntervalSince1970: 1_800_000_000)

    private func lock(nonce: String? = "abcDEF123", days: Double = 30) -> LockStore.LockState {
        LockStore.LockState(deadline: t0.addingTimeInterval(days * 86_400), mode: "blocklist",
                            startedAt: t0, releaseNonce: nonce)
    }

    private func sign(_ text: String, with key: Curve25519.Signing.PrivateKey? = nil) -> String {
        "HISN-OK-" + (try! (key ?? partner).signature(for: Data(text.utf8))).base64URL
    }

    // MARK: Keys

    func testKeyIsAcceptedInEveryFormAPersonMightPaste() {
        let raw = partner.publicKey.rawRepresentation
        let canonical = raw.base64URL
        XCTAssertEqual(PartnerService.canonicalKey("HISN-PK-" + canonical), canonical)
        XCTAssertEqual(PartnerService.canonicalKey("  " + canonical + "\n"), canonical)
        XCTAssertEqual(PartnerService.canonicalKey(raw.base64EncodedString()), canonical)
        XCTAssertEqual(PartnerService.canonicalKey(raw.map { String(format: "%02x", $0) }.joined()),
                       canonical)
    }

    func testNonKeysAreRefused() {
        XCTAssertNil(PartnerService.canonicalKey(""))
        XCTAssertNil(PartnerService.canonicalKey("HISN-PK-short"))
        XCTAssertNil(PartnerService.canonicalKey(Data(repeating: 1, count: 31).base64URL))
        XCTAssertNil(PartnerService.canonicalKey("not a key at all!"))
    }

    func testFingerprintIsShortAndStable() {
        let f = PartnerService.fingerprint(publicKey)
        XCTAssertEqual(f, PartnerService.fingerprint(publicKey))
        XCTAssertEqual(f.count, 14)       // XXXX-XXXX-XXXX
    }

    // MARK: Approvals

    func testGenuineApprovalEndsThisLock() throws {
        let l = lock()
        let code = PartnerService.challenge(for: l)
        XCTAssertTrue(code.hasPrefix("hisn-release-v1.1800000000."))
        XCTAssertNoThrow(try PartnerService.approve(sign(code), for: l, key: publicKey))
    }

    func testApprovalForAnotherLockIsRefused() {
        let other = PartnerService.challenge(for: lock(nonce: "someOtherNonce"))
        XCTAssertThrowsError(try PartnerService.approve(sign(other), for: lock(), key: publicKey))
    }

    func testApprovalSignedByAnyoneElseIsRefused() {
        let code = PartnerService.challenge(for: lock())
        let impostor = Curve25519.Signing.PrivateKey()
        XCTAssertThrowsError(try PartnerService.approve(sign(code, with: impostor),
                                                        for: lock(), key: publicKey))
    }

    func testTamperedApprovalIsRefused() {
        // A character in the middle: the last one of 86 carries only two
        // significant bits, so changing it can decode to the very same bytes.
        var chars = Array(sign(PartnerService.challenge(for: lock())))
        let i = "HISN-OK-".count + 20
        chars[i] = chars[i] == "A" ? "B" : "A"
        let approval = String(chars)
        XCTAssertThrowsError(try PartnerService.approve(approval, for: lock(), key: publicKey))
    }

    func testNoKeyOrNoNonceMeansNoPartnerRoute() {
        let code = PartnerService.challenge(for: lock())
        XCTAssertThrowsError(try PartnerService.approve(sign(code), for: lock(), key: nil))
        // A lock from before nonces existed cannot be released this way: its
        // code would be predictable from the start time alone.
        let old = lock(nonce: nil)
        XCTAssertThrowsError(try PartnerService.approve(sign(PartnerService.challenge(for: old)),
                                                        for: old, key: publicKey))
    }

    func testNoncesAreUnpredictable() {
        XCTAssertNotEqual(PartnerService.newNonce(), PartnerService.newNonce())
        XCTAssertGreaterThanOrEqual(PartnerService.newNonce().count, 12)
    }

    // MARK: The lock rules

    func testNonceCannotChangeMidLock() {
        var swapped = lock()
        swapped.releaseNonce = "attackerNonce"
        XCTAssertNotNil(LockStore.refusal(current: lock(), proposed: swapped, now: t0))
        XCTAssertNil(LockStore.refusal(current: lock(), proposed: lock(days: 40), now: t0))
    }

    func testKeyCannotBeSwappedMidLockButCanBeRemoved() {
        let other = Curve25519.Signing.PrivateKey().publicKey.rawRepresentation.base64URL
        XCTAssertNotNil(PartnerService.keyRefusal(current: publicKey, proposed: other, locked: true))
        XCTAssertNotNil(PartnerService.keyRefusal(current: nil, proposed: other, locked: true))
        XCTAssertNil(PartnerService.keyRefusal(current: publicKey, proposed: nil, locked: true))
        XCTAssertNil(PartnerService.keyRefusal(current: publicKey, proposed: other, locked: false))
    }

    // MARK: The authority

    func testAuthorityReleasesOnlyOnAGenuineApproval() {
        var r = PolicyRecord()
        guard case let .success(keyed) = PolicyAuthority.apply(.setPartnerKey(publicKey),
                                                               to: r, wall: t0),
              case let .success(locked) = PolicyAuthority.apply(.proposeLock(lock()),
                                                                to: keyed, wall: t0)
        else { return XCTFail("setup refused") }
        r = locked

        let impostor = Curve25519.Signing.PrivateKey()
        let code = PartnerService.challenge(for: lock())
        guard case .failure = PolicyAuthority.apply(.partnerRelease(approval: sign(code, with: impostor)),
                                                    to: r, wall: t0) else {
            return XCTFail("a forged approval ended the lock")
        }
        guard case .failure = PolicyAuthority.apply(
            .setPartnerKey(impostor.publicKey.rawRepresentation.base64URL), to: r, wall: t0) else {
            return XCTFail("the partner key was swapped mid-lock")
        }
        guard case let .success(released) = PolicyAuthority.apply(
            .partnerRelease(approval: sign(code)), to: r, wall: t0) else {
            return XCTFail("a genuine approval was refused")
        }
        XCTAssertNil(released.lock)
        XCTAssertFalse(PolicyAuthority.isLocked(released, now: t0))
    }
}
