import XCTest
@testable import Hisn

/// The authority model is a value; every state is tested without creating an
/// account or touching launchd. The detector itself is read-only and is not
/// exercised against real system accounts here.
final class MacAuthorityTests: XCTestCase {

    private func authority(dailyAdmin: Bool? = false, guardian: MacAuthority.Guardian = .ready,
                           daemon: MacAuthority.Daemon = .loaded, locked: Bool = false) -> MacAuthority {
        MacAuthority(dailyUser: "daily", dailyUserIsAdmin: dailyAdmin,
                     guardian: guardian, daemon: daemon, lockActive: locked)
    }

    func testGuardianStatesAreHonest() {
        XCTAssertEqual(MacAuthority.guardian(exists: false, isAdmin: false, hasSecureToken: false), .missing)
        XCTAssertEqual(MacAuthority.guardian(exists: true, isAdmin: false, hasSecureToken: true), .notAdmin)
        XCTAssertEqual(MacAuthority.guardian(exists: true, isAdmin: true, hasSecureToken: false), .noSecureToken)
        XCTAssertEqual(MacAuthority.guardian(exists: true, isAdmin: true, hasSecureToken: nil), .noSecureToken)
        XCTAssertEqual(MacAuthority.guardian(exists: true, isAdmin: true, hasSecureToken: true), .ready)
        XCTAssertEqual(MacAuthority.guardian(exists: true, isAdmin: true, hasSecureToken: true,
                                             unreadable: true), .unreadable)
    }

    func testDaemonStatesAreHonest() {
        XCTAssertEqual(MacAuthority.daemon(plistExists: false, loaded: true), .missing)
        XCTAssertEqual(MacAuthority.daemon(plistExists: true, loaded: false), .notLoaded)
        XCTAssertEqual(MacAuthority.daemon(plistExists: true, loaded: true), .loaded)
    }

    func testVerifiedNeedsAllThreeFacts() {
        XCTAssertEqual(authority().level, .verified)
        XCTAssertTrue(authority().isVerified)
        XCTAssertEqual(authority(dailyAdmin: true).level, .partial)
        XCTAssertEqual(authority(guardian: .missing).level, .partial)
        XCTAssertEqual(authority(guardian: .noSecureToken).level, .partial)
        XCTAssertEqual(authority(daemon: .missing).level, .partial)
        XCTAssertEqual(authority(dailyAdmin: nil).level, .partial)
    }

    func testAMissingAnswerIsNotPromotedToVerified() {
        let unknown = MacAuthority(dailyUser: "daily", dailyUserIsAdmin: nil,
                                   guardian: .unreadable, daemon: .missing, lockActive: false)
        XCTAssertEqual(unknown.level, .unavailable)
        XCTAssertFalse(unknown.isVerified)
    }

    func testALockDisablesAuthorityChangesAndTheRestoreCommand() {
        let locked = authority(locked: true)
        XCTAssertFalse(locked.mayChangeAuthority)
        XCTAssertFalse(locked.mayOfferRestoreCommand)
        let unlocked = authority(locked: false)
        XCTAssertTrue(unlocked.mayChangeAuthority)
        XCTAssertTrue(unlocked.mayOfferRestoreCommand)
        XCTAssertFalse(authority(guardian: .missing).mayOfferRestoreCommand)
        XCTAssertTrue(authority(dailyAdmin: true).mayOfferRestoreCommand,
                      "a ready guardian can still restore a daily admin")
    }

    func testTheRestoreCommandNamesTheDailyUserAndIsGuardianOnly() {
        let line = MacAuthority.restoreAdminCommand(dailyUser: "x")
        XCTAssertEqual(line, "sudo dseditgroup -o edit -a x -t user admin")
        XCTAssertEqual(MacAuthority.guardianSetupCommand,
                       "sudo macos/setup_guardian.sh --create-admin")
    }

    func testTheDailyUserIsNeverTheGuardianByDefault() {
        XCTAssertEqual(MacAuthority.guardianUser, "hisn_guardian")
    }
}
