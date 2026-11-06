import XCTest
@testable import Hisn

final class ManagedModeTests: XCTestCase {

    func testOnlyAForcedManagedFlagCounts() {
        let unmanaged = ManagedMode.read { _, _ in nil }
        XCTAssertFalse(unmanaged.isManaged)

        let userWritable = ManagedMode.read { key, _ in
            // A value that is not forced is not management. The injected
            // reader models CFPreferencesAppValueIsForced returning false.
            key == ManagedMode.managedKey ? nil : nil
        }
        XCTAssertFalse(userWritable.isManaged)
    }

    func testAForcedFlagAndOrganizationNameAreRead() {
        let managed = ManagedMode.read { key, domain in
            XCTAssertEqual(domain, "app.hisn.Hisn")
            switch key {
            case ManagedMode.managedKey: return NSNumber(value: true)
            case ManagedMode.organizationKey: return "Example Institute"
            default: return nil
            }
        }
        XCTAssertTrue(managed.isManaged)
        XCTAssertEqual(managed.displayName, "Example Institute")
    }

    func testAManagedFlagWithoutANameUsesTheNeutralPhrase() {
        let managed = ManagedMode.read { key, _ in
            key == ManagedMode.managedKey ? NSNumber(value: true) : nil
        }
        XCTAssertTrue(managed.isManaged)
        XCTAssertFalse(managed.displayName.isEmpty)
    }

    func testAFalseForcedFlagIsNotManaged() {
        let off = ManagedMode.read { key, _ in
            key == ManagedMode.managedKey ? NSNumber(value: false) : nil
        }
        XCTAssertFalse(off.isManaged)
    }

    func testManagedModeAddsAnOrganizationLayerToTheStatus() {
        let managed = ManagedMode(isManaged: true, organizationName: "Example Institute")
        let evidence = ProtectionEvidence(
            filter: .on(domainCount: 150_000), extensionLastSeen: Date(),
            privateBrowsing: PrivateBrowsingStatus(browsers: []),
            managedMode: managed)
        let status = ProtectionStatus(evidence)
        XCTAssertTrue(status.isManaged)
        let layer = status.layers.first { $0.id == "managed" }
        XCTAssertEqual(layer?.state, .ok)
        XCTAssertTrue(layer?.detail.contains("Example Institute") == true, layer?.detail ?? "")
        XCTAssertEqual(status.level, .active)
    }
}
