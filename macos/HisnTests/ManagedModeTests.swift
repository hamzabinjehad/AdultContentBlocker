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

    func testTheManagedProfileMarkersAreRead() {
        let managed = ManagedMode.read { key, _ in
            switch key {
            case ManagedMode.managedKey: return NSNumber(value: true)
            case ManagedMode.organizationKey: return "Example Institute"
            case ManagedMode.profileVersionKey: return NSNumber(value: 2)
            case ManagedMode.filterManagedKey: return NSNumber(value: true)
            default: return nil
            }
        }
        XCTAssertTrue(managed.isManaged)
        XCTAssertEqual(managed.profileVersion, 2)
        XCTAssertTrue(managed.filterManaged)
        XCTAssertEqual(managed.profileSummary,
                       "Protection profile v2. The system filter is managed by the profile.")
    }

    func testAnOlderProfileIsNotTreatedAsHavingAManagedFilter() {
        let managed = ManagedMode.read { key, _ in
            switch key {
            case ManagedMode.managedKey: return NSNumber(value: true)
            case ManagedMode.filterManagedKey: return NSNumber(value: false)
            default: return nil
            }
        }
        XCTAssertTrue(managed.isManaged)
        XCTAssertNil(managed.profileVersion)
        XCTAssertFalse(managed.filterManaged)
        XCTAssertTrue(managed.profileSummary?.contains("does not report a version") == true,
                      managed.profileSummary ?? "")
    }

    func testAProfileWithoutTheFilterSaysSo() {
        let managed = ManagedMode(isManaged: true, organizationName: "Example Institute",
                                  profileVersion: 2, filterManaged: false)
        XCTAssertEqual(managed.profileSummary,
                       "Protection profile v2. The profile does not include the managed system filter.")
    }

    func testAnUnmanagedMacHasNoProfileSummary() {
        XCTAssertNil(ManagedMode.none.profileSummary)
    }

    func testEnrollmentIsCarriedEvenWhenTheProfileFlagIsAbsent() {
        let enrollment = MDMEnrollment.enrolled(dep: false, userApproved: true)
        let off = ManagedMode.read(enrollment: enrollment) { _, _ in nil }
        XCTAssertFalse(off.isManaged)
        XCTAssertEqual(off.enrollment, enrollment)
        XCTAssertTrue(off.isMDMEnrolled)
    }

    func testTheEnrollmentParserReadsTheDocumentedShapes() {
        XCTAssertEqual(MDMEnrollment.parse("Enrolled via DEP: No\nMDM enrollment: No"),
                       .none)
        XCTAssertEqual(MDMEnrollment.parse("Enrolled via DEP: Yes\nMDM enrollment: Yes (User Approved)\nMDM server: https://mdm.example.com/path?x=1"),
                       .enrolled(dep: true, userApproved: true))
        XCTAssertEqual(MDMEnrollment.parse("Enrolled via DEP: No\nMDM enrollment: Yes (User Approved)"),
                       .enrolled(dep: false, userApproved: true))
        XCTAssertEqual(MDMEnrollment.parse("MDM enrollment: Yes"),
                       .enrolled(dep: false, userApproved: false))
    }

    func testAnUnrecognizedEnrollmentAnswerIsUnknownNotUnmanaged() {
        let outputs = [
            "",
            "MDM enrollment: perhaps",
            "MDM enrollment: No\nEnrolled via DEP: Yes",
            "Enrolled via DEP: Maybe\nMDM enrollment: No",
            "MDM enrollment: Yes\nEnrolled via DEP: Yes",
            "profiles: could not read enrollment",
        ]
        for output in outputs {
            XCTAssertEqual(MDMEnrollment.parse(output), .unknown, output)
        }
    }

    func testTheEnrollmentProbeCachesForItsTTL() {
        MDMEnrollmentProbe.reset()
        defer { MDMEnrollmentProbe.reset() }

        var reads = 0
        let start = Date()
        let first = MDMEnrollmentProbe.current(now: start) {
            reads += 1
            return .none
        }
        let second = MDMEnrollmentProbe.current(now: start.addingTimeInterval(30)) {
            reads += 1
            return .enrolled(dep: true, userApproved: true)
        }
        XCTAssertEqual(first, .none)
        XCTAssertEqual(second, .none, "a fresh answer must not be replaced by a second subprocess")
        XCTAssertEqual(reads, 1)

        let third = MDMEnrollmentProbe.current(
            now: start.addingTimeInterval(MDMEnrollmentProbe.ttl + 1)) {
            reads += 1
            return .unknown
        }
        XCTAssertEqual(third, .unknown)
        XCTAssertEqual(reads, 2)
    }

    func testTheManagedLayerSaysWhetherTheProfileIsMDMBacked() {
        let cases: [(MDMEnrollment, String)] = [
            (.enrolled(dep: true, userApproved: true), "DEP"),
            (.enrolled(dep: false, userApproved: true), "Enrolled in MDM"),
            (.enrolled(dep: false, userApproved: false), "not user-approved"),
            (.none, "not enrolled"),
            (.unknown, "Local removal and weakening controls are disabled."),
        ]
        for (enrollment, expected) in cases {
            let mode = ManagedMode(isManaged: true,
                                   organizationName: "Example Institute",
                                   enrollment: enrollment)
            XCTAssertTrue(mode.policyDetail.contains("Example Institute"),
                          mode.policyDetail)
            XCTAssertTrue(mode.policyDetail.contains(expected),
                          "\(enrollment): \(mode.policyDetail)")
        }
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

    func testTheHandoffNamesEveryDeploymentState() {
        let cases: [(ManagedMode, MDMHandoff.State)] = [
            (ManagedMode(isManaged: true, organizationName: "Example",
                         enrollment: .enrolled(dep: false, userApproved: true)), .enrolled),
            (ManagedMode(isManaged: true, organizationName: "Example", enrollment: .none), .managedLocally),
            (ManagedMode(isManaged: true, organizationName: "Example", enrollment: .unknown), .unknown),
            (ManagedMode(isManaged: false, enrollment: .enrolled(dep: true, userApproved: true)), .enrolledNotManaged),
            (ManagedMode(isManaged: false, enrollment: .none), .notEnrolled),
            (ManagedMode(isManaged: false, enrollment: .unknown), .unknown),
        ]
        for (mode, expected) in cases {
            XCTAssertEqual(MDMHandoff.state(for: mode), expected)
            XCTAssertFalse(MDMHandoff.status(for: mode).isEmpty)
            XCTAssertFalse(MDMHandoff.detail(for: mode).isEmpty)
        }
    }

    func testTheHandoffDetailKeepsTheLimitVisible() {
        let enrolled = ManagedMode(isManaged: true, organizationName: "Example Institute",
                                   enrollment: .enrolled(dep: false, userApproved: true))
        XCTAssertTrue(MDMHandoff.detail(for: enrolled).contains("Example Institute"))

        let local = ManagedMode(isManaged: true, organizationName: "Example Institute",
                                enrollment: .none)
        XCTAssertTrue(MDMHandoff.detail(for: local).contains("not enrolled in MDM"))

        let enrolledNotManaged = ManagedMode(isManaged: false,
                                             enrollment: .enrolled(dep: true, userApproved: true))
        XCTAssertTrue(MDMHandoff.detail(for: enrolledNotManaged).contains("not applied"))

        let notEnrolled = ManagedMode(isManaged: false, enrollment: .none)
        XCTAssertTrue(MDMHandoff.detail(for: notEnrolled).contains("cannot enroll"))
    }

    func testAProfileOwnedFilterThatIsOffHasNoEnableAction() {
        let managed = ManagedMode(isManaged: true, organizationName: "Example Institute",
                                  profileVersion: 2, filterManaged: true)
        let evidence = ProtectionEvidence(filter: .off, extensionLastSeen: Date(),
                                          managedMode: managed)
        let layer = ProtectionStatus(evidence).layers.first { $0.id == "filter" }
        XCTAssertNil(layer?.action, "the app cannot start a profile-owned filter")
        XCTAssertTrue(layer?.detail.contains("organization") == true,
                      layer?.detail ?? "")

        let unowned = ManagedMode(isManaged: true, organizationName: "Example Institute",
                                  profileVersion: 2, filterManaged: false)
        let unownedLayer = ProtectionStatus(ProtectionEvidence(
            filter: .off, extensionLastSeen: Date(), managedMode: unowned))
            .layers.first { $0.id == "filter" }
        XCTAssertNotNil(unownedLayer?.action, "without the filter layer the app can still enable it")
    }

    func testARealMDMEnrollmentShowsInTheOrganizationLayer() {
        let managed = ManagedMode(
            isManaged: true, organizationName: "Example Institute",
            enrollment: .enrolled(dep: true, userApproved: true))
        let evidence = ProtectionEvidence(
            filter: .on(domainCount: 150_000), extensionLastSeen: Date(),
            privateBrowsing: PrivateBrowsingStatus(browsers: []),
            managedMode: managed)
        let layer = ProtectionStatus(evidence).layers.first { $0.id == "managed" }
        XCTAssertTrue(layer?.detail.contains("DEP") == true, layer?.detail ?? "")
    }
}
