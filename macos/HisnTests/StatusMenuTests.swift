import AppKit
import XCTest
@testable import Hisn

/// The menu bar menu as data: which rows a person sees, and in what order.
///
/// The menu used to be a SwiftUI view inside `MenuBarExtra`; it is now built
/// into `NSMenu` so the same menu exists on macOS 12. These assert the decisions
/// that view made — above all that a lock removes the ways to start another.
@MainActor
final class StatusMenuTests: XCTestCase {

    private func makeState(lock: StatusMenuState.Lock = .unlocked,
                           managedName: String? = nil,
                           nextLockStart: Date? = nil,
                           pausesToday: Int? = nil,
                           blockedToday: Int? = nil,
                           grayscale: StatusMenuState.Grayscale? = nil) -> StatusMenuState {
        StatusMenuState(protectionHeadline: "Protected",
                        managedName: managedName,
                        lock: lock,
                        nextLockStart: nextLockStart,
                        pausesToday: pausesToday,
                        blockedToday: blockedToday,
                        lockLengths: QuickLock.lengths,
                        grayscale: grayscale)
    }

    private func actions(_ entries: [StatusMenuEntry]) -> [StatusMenuAction] {
        entries.compactMap {
            if case let .action(action, _, _) = $0 { return action }
            return nil
        }
    }

    private func facts(_ entries: [StatusMenuEntry]) -> [String] {
        entries.compactMap {
            if case let .info(text) = $0 { return text }
            return nil
        }
    }

    private func lockCommand(_ entries: [StatusMenuEntry]) -> [TimeInterval]? {
        for entry in entries {
            if case let .lockLengths(_, _, lengths) = entry { return lengths }
        }
        return nil
    }

    func testTheLockCommandsAreOfferedOnlyWhileUnlocked() {
        let unlocked = StatusMenuModel.entries(for: makeState())
        XCTAssertEqual(actions(unlocked),
                       [.openOverview, .openLockPage, .pause, .blockSite, .checkSite,
                        .openSetup, .hide])
        XCTAssertEqual(lockCommand(unlocked), QuickLock.lengths)

        let locked = StatusMenuModel.entries(for: makeState(lock: .locked(remaining: "2h")))
        XCTAssertEqual(actions(locked),
                       [.openOverview, .pause, .blockSite, .checkSite, .openSetup, .hide])
        XCTAssertNil(lockCommand(locked))
    }

    func testTheMenuAlwaysEndsWithTheWayOut() {
        for lock in [StatusMenuState.Lock.unlocked, .locked(remaining: "1h")] {
            XCTAssertEqual(actions(StatusMenuModel.entries(for: makeState(lock: lock))).last, .hide)
            XCTAssertEqual(StatusMenuModel.entries(for: makeState(lock: lock)).first,
                           .info("Protected"))
        }
    }

    func testTheLockStateIsAFactRow() {
        let locked = facts(StatusMenuModel.entries(
            for: makeState(lock: .locked(remaining: "2h"))))
        XCTAssertTrue(locked.contains { $0.contains("2h") })
        XCTAssertFalse(locked.contains("No active lock"))

        let unlocked = facts(StatusMenuModel.entries(for: makeState()))
        XCTAssertTrue(unlocked.contains("No active lock"))
    }

    func testTheDailyLockIsNamedOnlyWhenOneIsScheduledAndNoLockRuns() {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        let scheduled = facts(StatusMenuModel.entries(
            for: makeState(nextLockStart: start)))
        // Headline, "No active lock", then the schedule.
        XCTAssertEqual(scheduled.count, 3)
        XCTAssertTrue(scheduled[2].contains(start.formatted(date: .omitted, time: .shortened)))

        // A lock running now means a second schedule line would be noise; the
        // menu says one thing about time.
        let locked = facts(StatusMenuModel.entries(
            for: makeState(lock: .locked(remaining: "2h"), nextLockStart: start)))
        XCTAssertEqual(locked.count, 2)
    }

    func testTheDaysCountsAppearOnlyWhenThereAreSome() {
        let quiet = facts(StatusMenuModel.entries(for: makeState()))
        XCTAssertFalse(quiet.contains { $0.contains("Pauses today") })
        XCTAssertFalse(quiet.contains { $0.contains("Blocked connections today") })

        let busy = facts(StatusMenuModel.entries(for: makeState(pausesToday: 2, blockedToday: 41)))
        XCTAssertTrue(busy.contains { $0.hasSuffix("2") && $0.contains("Pauses") })
        XCTAssertTrue(busy.contains { $0.contains("41") })
    }

    func testAManagedInstallNamesTheOrganization() {
        let facts = facts(StatusMenuModel.entries(for: makeState(managedName: "Acme School")))
        XCTAssertTrue(facts.contains { $0.contains("Acme School") })
    }

    func testGrayCanBeTickedDuringALockButNotCleared() {
        let entries = StatusMenuModel.entries(for: makeState(
            lock: .locked(remaining: "2h"),
            grayscale: .init(isOn: true, canChange: false)))
        guard case let .toggle(_, _, on, enabled)? = entries.first(where: {
            if case .toggle = $0 { return true }
            return false
        }) else { return XCTFail("no Gray screen row") }
        XCTAssertTrue(on)
        XCTAssertFalse(enabled)

        let free = StatusMenuModel.entries(for: makeState(
            grayscale: .init(isOn: true, canChange: true)))
        guard case let .toggle(_, _, _, enabledFree)? = free.first(where: {
            if case .toggle = $0 { return true }
            return false
        }) else { return XCTFail("no Gray screen row") }
        XCTAssertTrue(enabledFree)
    }

    func testEveryRowBecomesTheMenuItemItDescribes() {
        let controller = StatusItemController()
        let state = makeState(lock: .locked(remaining: "2h"),
                              pausesToday: 3,
                              grayscale: .init(isOn: true, canChange: false))
        let entries = StatusMenuModel.entries(for: state)
        let menu = NSMenu()
        for entry in entries { menu.addItem(controller.item(for: entry)) }
        XCTAssertEqual(menu.items.count, entries.count)

        // The first row is a fact, not a command: it says what is protected
        // and does nothing when clicked.
        XCTAssertFalse(menu.items[0].isEnabled)
        XCTAssertNil(menu.items[0].action)

        // Separators are separators, in the same places.
        for (entry, item) in zip(entries, menu.items) {
            if case .separator = entry {
                XCTAssertTrue(item.isSeparatorItem)
            } else {
                XCTAssertFalse(item.isSeparatorItem)
            }
        }

        // Every command reaches the controller; nothing is a dead row.
        for (entry, item) in zip(entries, menu.items) {
            switch entry {
            case .action:
                XCTAssertNotNil(item.action)
                XCTAssertTrue(item.target === controller)
            case let .toggle(_, _, on, enabled):
                XCTAssertEqual(item.state, on ? .on : .off)
                XCTAssertEqual(item.isEnabled, enabled)
                XCTAssertNotNil(item.action)
            case let .lockLengths(_, _, lengths):
                XCTAssertEqual(item.submenu?.items.count, lengths.count)
                XCTAssertEqual(item.submenu?.items.map(\.title),
                               lengths.map { LockManager.describe($0) })
                XCTAssertTrue(item.submenu?.items.allSatisfy { $0.target === controller } ?? false)
            case .info, .separator:
                break
            }
        }
    }

    func testGrayIsLeftOutWhereTheSystemCallDoesNotExist() {
        let entries = StatusMenuModel.entries(for: makeState(grayscale: nil))
        XCTAssertFalse(entries.contains {
            if case .toggle = $0 { return true }
            return false
        })
    }
}

/// The menu bar item itself: a real `NSStatusItem` carrying the same mark and
/// name the `MenuBarExtra` label used to.
@MainActor
final class StatusItemControllerTests: XCTestCase {
    func testTheItemShowsTheMarkAndTheAppsName() throws {
        let controller = StatusItemController()
        controller.start()
        defer { controller.stop() }

        let item = try XCTUnwrap(controller.statusItem)
        let button = try XCTUnwrap(item.button)
        XCTAssertNotNil(button.image)
        // The same word the scene label showed, in the app's current language.
        XCTAssertEqual(button.title,
                       MenuBarIcon.shared.remainingText ?? String(localized: "Hisn"))
        XCTAssertNotNil(item.menu)
    }

    func testStoppingRemovesTheItem() {
        let controller = StatusItemController()
        controller.start()
        controller.stop()
        XCTAssertNil(controller.statusItem)
    }
}
