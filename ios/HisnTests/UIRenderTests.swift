import SwiftUI
import UIKit
import XCTest
@testable import HisnMobile

/// Captures the three tabs at the standard iPhone size in both languages. The
/// existing accessibility test covers the largest text sizes and iPad; this
/// one is the ordinary first screen a person sees, kept as layout evidence for
/// UI passes rather than as a pixel test.
final class UIRenderTests: XCTestCase {
    @MainActor func testEveryTabRendersAtStandardSize() async throws {
        let cases: [(size: CGSize, name: String, style: UIUserInterfaceStyle)] = [
            (CGSize(width: 390, height: 844), "phone-light", .light),
            (CGSize(width: 390, height: 844), "phone-dark", .dark),
            (CGSize(width: 1024, height: 1366), "pad-light", .light),
        ]
        for language in ["en", "ar"] {
            for (size, name, style) in cases {
                for tab in 0...3 {
                    let view = MobileRootView(
                        protection: ProtectionController(commitmentStore: MemoryCommitmentStore()),
                        language: .constant(language),
                        initialTab: tab
                    )
                    .environment(\.locale, Locale(identifier: language))
                    .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                    let image = try await render(view, size: size, style: style)
                    XCTAssertEqual(image.size, size)
                    let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
                    let shades = Set(stride(from: 0, to: pixels.count, by: 97).map { pixels[$0] })
                    XCTAssertGreaterThan(shades.count, 8, "A blank image is not evidence of rendered UI")
                    let attachment = XCTAttachment(image: image)
                    attachment.name = "standard-\(name)-\(language)-tab\(tab)"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                }
            }
        }
    }

    /// The three drill-ins keep the controls that used to make the old Setup
    /// page a wall. Rendering them separately catches a crash or an empty page
    /// without needing a signed build.
    @MainActor func testSetupDetailPagesRender() async throws {
        let suite = "hisn.tests.ui." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let protection = ProtectionController(commitmentStore: MemoryCommitmentStore())
        let usage = AppUsageController(defaults: defaults)
        let vault = PasscodeVaultController(storage: MemoryPasscodeVaultStorage(), clock: { Date() })
        let device = LockConfirmationDevice(stores: [MemoryLockMarkerStore()])
        let pages: [(String, AnyView)] = [
            ("layers", AnyView(NavigationStack {
                BlockingLayersSetupView(protection: protection)
            })),
            ("rules", AnyView(NavigationStack {
                CommitmentSetupView(protection: protection, usage: usage, vault: vault)
            })),
            ("vault", AnyView(NavigationStack {
                VaultSetupView(protection: protection, vault: vault, lockDevice: device)
            })),
        ]
        for (name, page) in pages {
            let image = try await render(page, size: CGSize(width: 390, height: 844), style: .light)
            let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
            let shades = Set(stride(from: 0, to: pixels.count, by: 97).map { pixels[$0] })
            XCTAssertGreaterThan(shades.count, 8, "A blank image is not evidence of rendered UI")
            let attachment = XCTAttachment(image: image)
            attachment.name = "detail-\(name)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    /// The pause handoff and the editor are the two screens a person meets only
    /// at a decision. They are rendered separately so a layout break cannot
    /// hide behind the tabs.
    @MainActor func testDecisionScreensRender() async throws {
        let suite = "hisn.tests.ui.decisions." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let protection = ProtectionController(commitmentStore: MemoryCommitmentStore())
        let usage = AppUsageController(defaults: defaults)
        let screens: [(String, AnyView)] = [
            ("pause-flow", AnyView(PauseRuleGrantView(usage: usage, appName: "Threads",
                                                      startedAt: Date().addingTimeInterval(-11),
                                                      onClose: {}))),
            ("pause-flow-ar", AnyView(PauseRuleGrantView(usage: usage, appName: "Threads",
                                                         startedAt: Date().addingTimeInterval(-11),
                                                         onClose: {})
                .environment(\.layoutDirection, .rightToLeft)
                .environment(\.locale, Locale(identifier: "ar")))),
            ("rule-editor", AnyView(RuleEditorView(protection: protection, usage: usage,
                                                   guardMode: false, initialMode: .pause))),
            ("rule-editor-days", AnyView(RuleEditorView(protection: protection, usage: usage,
                                                        guardMode: false,
                                                        initialMode: .quietHours))),
            ("more", AnyView(NavigationStack {
                MoreView(protection: protection, usage: usage,
                         vault: PasscodeVaultController(storage: MemoryPasscodeVaultStorage(),
                                                        clock: { Date() }),
                         lockDevice: LockConfirmationDevice(stores: [MemoryLockMarkerStore()]),
                         language: .constant("en"))
            })),
        ]
        // The weekday chips sit below the fold on a phone; the iPad render shows
        // the whole editor.
        let padDays = AnyView(RuleEditorView(protection: protection, usage: usage,
                                             guardMode: false, initialMode: .quietHours))
        let padImage = try await render(padDays, size: CGSize(width: 1024, height: 1366), style: .light)
        let padAttachment = XCTAttachment(image: padImage)
        padAttachment.name = "decision-rule-editor-days-pad"
        padAttachment.lifetime = .keepAlways
        add(padAttachment)

        for (name, screen) in screens {
            for style in [UIUserInterfaceStyle.light, .dark] {
                let image = try await render(screen, size: CGSize(width: 390, height: 844), style: style)
                let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
                let shades = Set(stride(from: 0, to: pixels.count, by: 97).map { pixels[$0] })
                XCTAssertGreaterThan(shades.count, 8, "A blank image is not evidence of rendered UI")
                let attachment = XCTAttachment(image: image)
                attachment.name = "decision-\(name)-\(style == .dark ? "dark" : "light")"
                attachment.lifetime = .keepAlways
                add(attachment)
            }
        }
    }

    @MainActor private func render<V: View>(_ view: V, size: CGSize,
                                           style: UIUserInterfaceStyle) async throws -> UIImage {
        let host = UIHostingController(rootView: view.frame(width: size.width, height: size.height))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(origin: .zero, size: size)
        window.overrideUserInterfaceStyle = style
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        host.view.frame = CGRect(origin: .zero, size: size)
        try await Task.sleep(nanoseconds: 300_000_000)
        host.view.layoutIfNeeded()
        // One point per pixel. Eighteen full-resolution simulator captures at
        // 3x retain too much bitmap memory and the test process is killed;
        // this is layout evidence, not a pixel snapshot.
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
        }
    }
}
