import Foundation
import XCTest
@testable import HisnMobile

private struct FixedServerTime: ServerTimeSource {
    let time: Date?
    func currentTime() async -> Date? { time }
}

private struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return state
    }
}

final class PasscodeVaultTests: XCTestCase {
    private let start = Date(timeIntervalSince1970: 1_800_000_000)

    func testGeneratedPasscodesAreFourDigitsAndNeverObvious() {
        var generator = SeededGenerator(state: 42)
        for _ in 0..<2000 {
            let code = PasscodeVaultPolicy.generate(using: &generator)
            XCTAssertEqual(code.count, 4)
            XCTAssertTrue(code.allSatisfy { $0.isASCII && $0.isNumber }, code)
            XCTAssertFalse(PasscodeVaultPolicy.isGuessable(code), code)
        }
        for obvious in ["0000", "1111", "1234", "4321", "6789", "9876", "0123"] {
            XCTAssertTrue(PasscodeVaultPolicy.isGuessable(obvious), obvious)
        }
        XCTAssertFalse(PasscodeVaultPolicy.isGuessable("1243"))
    }

    func testPasscodeShowsOnlyDuringSetupThenStaysHiddenUntilTheDate() throws {
        var generator = SeededGenerator(state: 7)
        let vault = try XCTUnwrap(PasscodeVaultPolicy.make(days: 7, now: start, using: &generator))
        guard case .setup = PasscodeVaultPolicy.phase(vault, now: start.addingTimeInterval(60)) else {
            return XCTFail("visible while it is being entered")
        }
        XCTAssertEqual(PasscodeVaultPolicy.phase(vault, now: start.addingTimeInterval(16 * 60)),
                       .sealed(until: vault.revealAt), "hides itself after the setup window")
        XCTAssertEqual(PasscodeVaultPolicy.phase(vault, now: start.addingTimeInterval(-3600)),
                       .sealed(until: vault.revealAt), "setting the clock back never reopens setup")
        var sealed = vault
        sealed.sealed = true
        XCTAssertEqual(PasscodeVaultPolicy.phase(sealed, now: start.addingTimeInterval(60)),
                       .sealed(until: vault.revealAt))
        XCTAssertEqual(PasscodeVaultPolicy.phase(sealed, now: vault.revealAt), .due(since: vault.revealAt))
    }

    func testRevealNeedsTheDateOnBothTheDeviceAndTheServerClock() throws {
        var generator = SeededGenerator(state: 1)
        let vault = try XCTUnwrap(PasscodeVaultPolicy.make(days: 1, now: start, using: &generator))
        let early = vault.revealAt.addingTimeInterval(-1)
        XCTAssertEqual(PasscodeVaultPolicy.revealRefusal(vault, deviceNow: early, serverNow: vault.revealAt), .notYet)
        XCTAssertEqual(PasscodeVaultPolicy.revealRefusal(vault, deviceNow: vault.revealAt, serverNow: nil), .needsNetwork)
        XCTAssertEqual(PasscodeVaultPolicy.revealRefusal(vault, deviceNow: vault.revealAt, serverNow: early), .clockDisagrees)
        XCTAssertNil(PasscodeVaultPolicy.revealRefusal(vault, deviceNow: vault.revealAt, serverNow: vault.revealAt))
    }

    func testExtensionOnlyMovesTheDateLater() throws {
        var generator = SeededGenerator(state: 3)
        let vault = try XCTUnwrap(PasscodeVaultPolicy.make(days: 30, now: start, using: &generator))
        let later = try XCTUnwrap(PasscodeVaultPolicy.extending(vault, byDays: 7))
        XCTAssertEqual(later.revealAt, vault.revealAt.addingTimeInterval(7 * 86400))
        XCTAssertEqual(later.passcode, vault.passcode)
        XCTAssertNil(PasscodeVaultPolicy.extending(vault, byDays: -7))
        XCTAssertNil(PasscodeVaultPolicy.extending(vault, byDays: 0))
        XCTAssertNil(PasscodeVaultPolicy.make(days: 0, now: start, using: &generator))
        XCTAssertNil(PasscodeVaultPolicy.make(days: 365 * 2, now: start, using: &generator))
    }

    func testServerDateHeaderParses() throws {
        let date = try XCTUnwrap(AppleServerTime.parse("Sat, 24 Oct 2026 21:57:06 GMT"))
        XCTAssertEqual(date.timeIntervalSince1970, 1_792_879_026)
        XCTAssertNil(AppleServerTime.parse("not a date"))
    }

    @MainActor func testControllerSealsAndRevealsOnlyAfterVerifiedDate() async throws {
        var now = start
        let storage = MemoryPasscodeVaultStorage()
        let controller = PasscodeVaultController(storage: storage,
            serverTime: FixedServerTime(time: start), clock: { now })
        controller.create(days: 7)
        guard case .setup(let passcode, _)? = controller.phase else { return XCTFail("setup expected") }
        controller.seal()
        XCTAssertEqual(controller.phase, .sealed(until: start.addingTimeInterval(7 * 86400)))

        controller.create(days: 1)
        XCTAssertEqual(controller.messageKey, "vault.exists", "a new passcode never replaces a sealed one")

        await controller.unseal()
        XCTAssertEqual(controller.messageKey, "vault.notYet")

        now = start.addingTimeInterval(8 * 86400)          // device clock moved past the date
        await controller.unseal()
        XCTAssertEqual(controller.messageKey, "vault.clockDisagrees", "server time still says it is early")
        XCTAssertNil(controller.vault?.revealedAt)

        let honest = PasscodeVaultController(storage: storage,
            serverTime: FixedServerTime(time: now), clock: { now })
        await honest.unseal()
        XCTAssertEqual(honest.phase, .revealed(passcode: passcode))
        honest.forget()
        XCTAssertNil(honest.vault)
        XCTAssertNil(storage.data)
    }

    @MainActor func testUnreadableStorageStaysSealedAndRefusesANewPasscode() throws {
        let storage = MemoryPasscodeVaultStorage()
        storage.data = Data("damaged".utf8)
        let controller = PasscodeVaultController(storage: storage,
            serverTime: FixedServerTime(time: nil), clock: { self.start })
        XCTAssertFalse(controller.storageHealthy)
        XCTAssertNil(controller.phase)
        controller.create(days: 1)
        XCTAssertEqual(storage.data, Data("damaged".utf8), "damaged storage is never overwritten")
        XCTAssertEqual(controller.messageKey, "vault.storageFailed")
    }

    @MainActor func testForgetIsRefusedWhileSealed() {
        let storage = MemoryPasscodeVaultStorage()
        let controller = PasscodeVaultController(storage: storage,
            serverTime: FixedServerTime(time: nil), clock: { self.start })
        controller.create(days: 7)
        controller.seal()
        controller.forget()
        XCTAssertNotNil(controller.vault)
        XCTAssertNotNil(storage.data)
    }

    func testVaultTextShipsInEnglishAndArabic() throws {
        let keys = ["vault.title", "vault.body", "vault.duration", "vault.acknowledge", "vault.create",
                    "vault.confirmTitle", "vault.confirmBody %lld", "vault.steps", "vault.hidesSoon",
                    "vault.seal", "vault.sealed", "vault.sealedUntil", "vault.extend", "vault.extendTitle",
                    "vault.extendBody", "vault.due", "vault.unseal", "vault.revealedBody", "vault.forget",
                    "vault.limitsTitle", "vault.limits", "vault.storageFailed", "vault.exists",
                    "vault.failed", "vault.notYet", "vault.needsNetwork", "vault.clockDisagrees"]
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                let text = localized.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertNotEqual(text, key, "\(language): \(key)")
            }
        }
    }
}
