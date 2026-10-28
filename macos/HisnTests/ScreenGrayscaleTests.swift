import XCTest
@testable import Hisn

/// A lock only tightens: gray can be turned on during one, never off.
@MainActor
final class ScreenGrayscaleTests: XCTestCase {
    func testTurningGrayOffWaitsForTheLockToEnd() {
        XCTAssertEqual(ScreenGrayscale.decide(on: false, wasOn: true, locked: true, available: true),
                       .waitsForLockEnd)
        XCTAssertEqual(ScreenGrayscale.decide(on: false, wasOn: true, locked: false, available: true),
                       .applied)
    }

    func testTurningGrayOnIsAlwaysAllowed() {
        XCTAssertEqual(ScreenGrayscale.decide(on: true, wasOn: false, locked: true, available: true),
                       .applied)
        XCTAssertEqual(ScreenGrayscale.decide(on: true, wasOn: false, locked: false, available: true),
                       .applied)
    }

    func testNothingChangesWithoutTheSystemCall() {
        XCTAssertEqual(ScreenGrayscale.decide(on: true, wasOn: false, locked: false, available: false),
                       .unavailable)
    }

    func testTheChoiceIsRememberedPerAccount() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "ScreenGrayscaleTests"))
        defaults.removePersistentDomain(forName: "ScreenGrayscaleTests")
        defaults.set(true, forKey: ScreenGrayscale.defaultsKey)
        XCTAssertTrue(ScreenGrayscale(defaults: defaults).isOn)
        defaults.removePersistentDomain(forName: "ScreenGrayscaleTests")
    }

    func testGrayScreenShipsArabic() throws {
        let ar = try XCTUnwrap(Bundle(for: LockManager.self).path(forResource: "ar", ofType: "lproj")
                                .flatMap(Bundle.init(path:)))
        XCTAssertEqual(ar.localizedString(forKey: "Gray screen", value: nil, table: nil), "شاشة رمادية")
    }
}
