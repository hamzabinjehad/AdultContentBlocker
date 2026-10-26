import SwiftUI
import UIKit
import XCTest
@testable import HisnMobile

/// Honest Screen Time status, readable colours, Dynamic Type and iPad layout.
final class AccessibilityTests: XCTestCase {
    func testStatusTextColoursMeetContrastOnListBackgrounds() {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let traits = UITraitCollection(userInterfaceStyle: style)
            for background in [UIColor.systemGroupedBackground, .secondarySystemGroupedBackground,
                               .tertiarySystemGroupedBackground] {
                for color in [HisnPalette.warningColor, HisnPalette.goodColor] {
                    XCTAssertGreaterThanOrEqual(HisnPalette.contrast(color, background, traits: traits), 4.5,
                                                "style \(style.rawValue): \(color) on \(background)")
                }
            }
        }
        // The system colours these replace fail on a light list row, which is why they were replaced.
        let light = UITraitCollection(userInterfaceStyle: .light)
        XCTAssertLessThan(HisnPalette.contrast(.systemOrange, .secondarySystemGroupedBackground, traits: light), 4.5)
        XCTAssertEqual(HisnPalette.contrast(.white, .black, traits: light), 21, accuracy: 0.01)
    }

    func testPasscodeShowsOnlyWhileTheSceneIsActive() {
        XCTAssertEqual(PasscodeVaultPolicy.displayed("4719", sceneActive: true), "4719")
        XCTAssertEqual(PasscodeVaultPolicy.displayed("4719", sceneActive: false), "••••")
        XCTAssertFalse(PasscodeVaultPolicy.displayed("0386", sceneActive: false).contains(where: \.isNumber))
    }

    func testScreenTimeStateSeparatesAccessTurnedOffFromNeverSetUp() {
        func state(_ authorization: MobileProtectionPolicy.FamilyAuthorization, filter: Bool, before: Bool)
            -> MobileProtectionPolicy.ScreenTimeState {
            MobileProtectionPolicy.screenTimeState(authorization: authorization, filterConfigured: filter,
                                                   previouslyConfigured: before)
        }
        XCTAssertEqual(state(.notDetermined, filter: false, before: false), .notRequested)
        XCTAssertEqual(state(.denied, filter: false, before: false), .declined)
        XCTAssertEqual(state(.denied, filter: false, before: true), .accessRemoved)
        XCTAssertEqual(state(.notDetermined, filter: false, before: true), .accessRemoved)
        XCTAssertEqual(state(.approved, filter: true, before: false), .configured)
        XCTAssertEqual(state(.approved, filter: false, before: true), .filterOff)
        XCTAssertEqual(state(.unavailable, filter: true, before: true), .unavailable)
        // Only approved access with Hisn's filter read back is configured, whatever history says.
        for authorization in [MobileProtectionPolicy.FamilyAuthorization.notDetermined, .denied, .unavailable] {
            for filter in [false, true] {
                for before in [false, true] {
                    XCTAssertNotEqual(state(authorization, filter: filter, before: before), .configured)
                }
            }
        }
    }

    func testAccessTurnedOffIsItsOwnSetupIssue() {
        func issues(screenTime: Bool, removed: Bool) -> [MobileProtectionPolicy.SetupIssue] {
            MobileProtectionPolicy.setupAssessment(checked: true, busy: false, listVerified: true,
                listCount: 4, listVersion: 1, safari: [true, true, true, true], lastReload: [nil, nil, nil, nil],
                screenTime: screenTime, dns: .enabled, storageHealthy: true, screenTimeAccessRemoved: removed).issues
        }
        XCTAssertEqual(issues(screenTime: false, removed: false), [.screenTime])
        XCTAssertEqual(issues(screenTime: false, removed: true), [.screenTimeAccess])
        XCTAssertEqual(issues(screenTime: true, removed: true), [], "a filter read back now outranks history")
    }

    @MainActor func testSimulatorReportsScreenTimeAsUncheckable() {
        #if targetEnvironment(simulator)
        let history = ReadinessHistory(key: "accessibility-history", storage: EphemeralRuleStorage(),
                                       allowed: ["screentime.layer", "dns.title", "safari.title"])
        history.observe(["screentime.layer"])
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(), readinessHistory: history)
        XCTAssertEqual(controller.screenTimeAuthorization, .unavailable)
        XCTAssertEqual(controller.screenTimeState, .unavailable, "the simulator never claims access was removed")
        #endif
    }

    func testNewStatusTextShipsInEnglishAndArabic() throws {
        let keys = [MobileProtectionPolicy.ScreenTimeState.unavailable, .notRequested, .declined,
                    .accessRemoved, .filterOff, .configured].map(\.rawValue)
            + ["setup.next.screentimeAccess", "commitment.screenTimeLost", "screentime.accessRemovedBody",
               "sites.accessRemoved", "plan.safariParts %lld", "vault.hidden"]
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                XCTAssertNotEqual(localized.localizedString(forKey: key, value: nil, table: nil), key, "\(language): \(key)")
            }
        }
    }

    /// Every tab, iPhone and both iPad orientations, the largest accessibility text size, both languages.
    @MainActor func testEveryTabRendersAtLargeTextAndOnIPadInBothLanguages() async throws {
        let cases: [(CGSize, DynamicTypeSize)] = [
            (CGSize(width: 390, height: 844), .accessibility5),
            (CGSize(width: 1024, height: 1366), .large),
            (CGSize(width: 1366, height: 1024), .accessibility3),
        ]
        for language in ["en", "ar"] {
            for (size, typeSize) in cases {
                for tab in 0...2 {
                    let view = MobileRootView(protection: ProtectionController(commitmentStore: MemoryCommitmentStore()),
                                              language: .constant(language), initialTab: tab)
                        .environment(\.locale, Locale(identifier: language))
                        .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                        .environment(\.dynamicTypeSize, typeSize)
                    let image = try await render(view, size: size)
                    XCTAssertEqual(image.size, size)
                    let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
                    let shades = Set(stride(from: 0, to: pixels.count, by: 97).map { pixels[$0] })
                    XCTAssertGreaterThan(shades.count, 8, "A blank image is not evidence of rendered UI")
                    let attachment = XCTAttachment(image: image)
                    attachment.name = "\(language)-\(Int(size.width))x\(Int(size.height))-tab\(tab)-\(typeSize)"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                }
            }
        }
    }

    @MainActor private func render<V: View>(_ view: V, size: CGSize) async throws -> UIImage {
        let host = UIHostingController(rootView: view.frame(width: size.width, height: size.height))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(origin: .zero, size: size)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        host.view.frame = CGRect(origin: .zero, size: size)
        try await Task.sleep(nanoseconds: 300_000_000)
        host.view.layoutIfNeeded()
        return UIGraphicsImageRenderer(size: size).image { _ in
            host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
        }
    }
}
