import AppIntents
import Foundation

/// Unrot inside macOS itself: actions for Shortcuts and Spotlight, and a
/// Focus filter so one of the Mac's own Focus modes (Work, Sleep, a custom
/// one) closes distractions when it turns on and opens them when it ends.
///
/// Everything here calls the same store the window uses. Nothing is added to
/// what leaves the Mac: there is still no account and no network call.

@available(macOS 13.0, *)
struct StartFocusIntent: AppIntent {
    static var title: LocalizedStringResource = "Start Focus"
    static var description = IntentDescription("Closes your distracting apps and sites for a set time.")

    @Parameter(title: "Minutes", default: 25, inclusiveRange: (5, 240))
    var minutes: Int

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let store = DistractionStore.shared
        let targets = Set(store.book.rules.flatMap(\.targets)).union(RuleTemplate.commonSites)
        let started = store.startFocus(minutes: minutes, intent: "", targets: targets, allowOnly: false, strictness: .gentle)
        return .result(dialog: started ? "Focus is on for \(minutes) minutes." : "A longer Focus is already running.")
    }
}

@available(macOS 13.0, *)
struct BreatheIntent: AppIntent {
    static var title: LocalizedStringResource = "Breathe"
    static var description = IntentDescription("Opens a slow breath for the moment you reach for something.")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        PauseWindowController.shared.show()
        return .result()
    }
}

@available(macOS 13.0, *)
struct TimeGivenBackIntent: AppIntent {
    static var title: LocalizedStringResource = "Time given back"
    static var description = IntentDescription("Says how much time Unrot has given back so far.")

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        let store = DistractionStore.shared
        let seconds = store.book.givenBackSeconds(at: store.clock(), calendar: store.calendar)
        let text = SproutProgress.parts(seconds).map { $0.0 + $0.1 }.joined(separator: " ")
        return .result(value: text, dialog: "\(text) given back.")
    }
}

/// Shown in System Settings › Focus › (a Focus) › Focus Filters.
@available(macOS 13.0, *)
struct UnrotFocusFilter: SetFocusFilterIntent {
    static var title: LocalizedStringResource = "Close distractions"
    static var description: IntentDescription? = IntentDescription("While this Focus is on, Unrot closes the apps and sites in your rules and the big feeds.")

    @Parameter(title: "Close distracting apps and sites", default: true)
    var closeDistractions: Bool

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "Close distractions")
    }

    /// The system also calls this when the Focus ends; it is then no longer
    /// the current filter, and the session it started is ended.
    @MainActor
    func perform() async throws -> some IntentResult {
        let active = (try? await UnrotFocusFilter.current)?.closeDistractions ?? false
        let store = DistractionStore.shared
        store.systemFocus(on: active, targets: Set(store.book.rules.flatMap(\.targets)).union(RuleTemplate.commonSites))
        return .result()
    }
}

@available(macOS 13.0, *)
struct UnrotShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: StartFocusIntent(), phrases: ["Start Focus in \(.applicationName)"],
                    shortTitle: "Start Focus", systemImageName: "play.fill")
        AppShortcut(intent: BreatheIntent(), phrases: ["Breathe with \(.applicationName)"],
                    shortTitle: "Breathe", systemImageName: "wind")
        AppShortcut(intent: TimeGivenBackIntent(), phrases: ["Time given back in \(.applicationName)"],
                    shortTitle: "Time given back", systemImageName: "leaf")
    }
}
