import AppKit
import Combine
import SwiftUI
import UserNotifications

/// Enforces Unrot's distraction rules on Mac apps.
///
/// It watches which app is in front (NSWorkspace, no special permission),
/// counts its minutes into the usage ledger, and asks `DistractionEngine` what
/// to do. A wait or a closed app is shown as a glass panel laid over that
/// app's own window — the rest of the screen keeps working, and nothing is
/// quit, so a half-written message survives. "Close" hides the app.
///
/// Browsers are handled by the extensions (pages, feeds); here a browser is an
/// ordinary app, so a rule on "Safari" itself still works.
@MainActor
public final class DistractionEnforcer {
    public static let shared = DistractionEnforcer()

    private let store: DistractionStore
    private var front: (bundleID: String, pid: pid_t, since: Date)?
    private var timer: Timer?
    private var observers: [NSObjectProtocol] = []
    private let panel = DistractionPanelController()
    /// Apps the person let through the panel for this activation only (a
    /// closed app they chose to look at for "5 more minutes" is a snooze, not this).
    private var passed: Set<pid_t> = []

    public static let tickInterval: TimeInterval = 5

    init(store: DistractionStore = .shared) {
        self.store = store
    }

    public func start() {
        guard observers.isEmpty else { return }
        let center = NSWorkspace.shared.notificationCenter
        observers.append(center.addObserver(forName: NSWorkspace.didActivateApplicationNotification,
                                            object: nil, queue: .main) { [weak self] note in
            let app = note.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication
            Task { @MainActor in self?.activated(app) }
        })
        observers.append(center.addObserver(forName: NSWorkspace.willSleepNotification,
                                            object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.flush(endingFront: true) }
        })
        observers.append(center.addObserver(forName: NSWorkspace.screensDidSleepNotification,
                                            object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.flush(endingFront: true) }
        })
        activated(NSWorkspace.shared.frontmostApplication)
        timer = Timer.scheduledTimer(withTimeInterval: Self.tickInterval, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }

    private func activated(_ app: NSRunningApplication?) {
        flush(endingFront: true)
        guard let app, let id = app.bundleIdentifier else { return }
        if id == Bundle.main.bundleIdentifier { return }  // our own panel or window
        passed.remove(app.processIdentifier)
        front = (id, app.processIdentifier, LockStore.trustedNow())
        evaluate(app)
    }

    /// Writes the time the front app has been in front so far.
    private func flush(endingFront: Bool) {
        guard let current = front else { return }
        let now = LockStore.trustedNow()
        store.record(subject: "app:" + current.bundleID, from: current.since, to: now)
        front = endingFront ? nil : (current.bundleID, current.pid, now)
        if endingFront { panel.hide() }
    }

    /// The end each rule's "closes again" notice was last sent for.
    private var announced: [UUID: Date] = [:]

    private func announceRelocks() {
        let book = store.book
        for item in RelockNotice.due(day: book.day, rules: book.rules, now: LockStore.trustedNow(), announced: announced) {
            announced[item.rule] = item.at
            guard !AppDelegate.isHostingTests, let rule = book.rules.first(where: { $0.id == item.rule }) else { continue }
            let center = UNUserNotificationCenter.current()
            center.requestAuthorization(options: [.alert]) { granted, _ in
                guard granted else { return }
                let content = UNMutableNotificationContent()
                content.title = String(localized: "\(rule.name) closes again in a minute")
                content.body = String(localized: "Time to wrap up.")
                center.add(UNNotificationRequest(identifier: "unrot.relock.\(rule.id.uuidString)",
                                                 content: content, trigger: nil))
            }
        }
    }

    private func tick() {
        flush(endingFront: false)
        store.tick()
        announceRelocks()
        if let current = front, let app = NSRunningApplication(processIdentifier: current.pid) {
            evaluate(app)
        }
    }

    private func evaluate(_ app: NSRunningApplication) {
        guard let id = app.bundleIdentifier else { return }
        if passed.contains(app.processIdentifier) { return }
        let decision = store.decide(.app(id))
        switch decision {
        case .allow:
            panel.hide()
        case .wait, .block:
            guard !panel.isShowing(for: app.processIdentifier) else { return }
            if case .block = decision { store.recordClosed() } else { store.recordPause() }
            panel.show(decision, over: app, book: store.book) { [weak self] choice in
                self?.handle(choice, decision: decision, app: app)
            }
        }
    }

    private func handle(_ choice: DistractionPanelChoice, decision: RuleDecision, app: NSRunningApplication) {
        switch choice {
        case .close:
            panel.hide()
            app.hide()
        case .open:
            if case let .wait(rule, _, _, _) = decision { store.recordOpen(rule: rule) }
            passed.insert(app.processIdentifier)
            panel.hide()
        case .snooze:
            if case let .block(rule, _, _) = decision { store.snooze(rule: rule) }
            panel.hide()
        case .breathe:
            panel.hide()
            app.hide()
            PauseWindowController.shared.show()
        case .emergencyPass:
            let alert = NSAlert()
            alert.messageText = String(localized: "Use this week’s emergency pass?")
            alert.informativeText = String(localized: "Locked rules and Focus stop until their current window ends. Adult protection stays on. The next pass is in 7 days.")
            alert.addButton(withTitle: String(localized: "Not now"))
            alert.addButton(withTitle: String(localized: "Use the pass"))
            alert.window.level = .modalPanel
            guard alert.runModal() == .alertSecondButtonReturn else { return }
            _ = store.useEmergencyPass()
            panel.hide()
        }
    }
}

// MARK: - Where to put the panel

public enum FrontWindowFrame {
    /// The frame of an app's main window, in AppKit screen coordinates, read
    /// from the window server. Only bounds are read — no titles, no pixels —
    /// so it needs no Screen Recording permission.
    public static func mainWindow(of pid: pid_t) -> CGRect? {
        guard let list = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements],
                                                    kCGNullWindowID) as? [[String: Any]] else { return nil }
        let candidates: [CGRect] = list.compactMap { info in
            guard (info[kCGWindowOwnerPID as String] as? pid_t) == pid,
                  (info[kCGWindowLayer as String] as? Int) == 0,
                  let bounds = info[kCGWindowBounds as String] as? [String: CGFloat],
                  let rect = CGRect(dictionaryRepresentation: bounds as CFDictionary)
            else { return nil }
            return rect
        }
        guard let topLeft = pick(candidates) else { return nil }
        return flip(topLeft)
    }

    /// The window to cover: the front-most (first listed) window that is big
    /// enough to be a real window, not a tooltip or a palette.
    public static func pick(_ rects: [CGRect]) -> CGRect? {
        rects.first { $0.width >= 320 && $0.height >= 240 }
    }

    /// Window-server rects have their origin at the top-left of the main
    /// display; AppKit's at the bottom-left.
    static func flip(_ rect: CGRect) -> CGRect {
        let mainHeight = NSScreen.screens.first?.frame.height ?? rect.maxY
        return CGRect(x: rect.minX, y: mainHeight - rect.maxY, width: rect.width, height: rect.height)
    }
}

// MARK: - The panel

public enum DistractionPanelChoice {
    case close, open, snooze, breathe, emergencyPass
}

@MainActor
final class DistractionPanelController {
    private var window: NSPanel?
    private var shownFor: pid_t?

    func isShowing(for pid: pid_t) -> Bool { window?.isVisible == true && shownFor == pid }

    func show(_ decision: RuleDecision, over app: NSRunningApplication, book: RuleBook,
              onChoice: @escaping (DistractionPanelChoice) -> Void) {
        hide()
        let frame = FrontWindowFrame.mainWindow(of: app.processIdentifier)
            ?? NSScreen.main?.visibleFrame ?? CGRect(x: 0, y: 0, width: 900, height: 600)
        let model = DistractionPanelModel(decision: decision, appName: app.localizedName ?? String(localized: "This app"),
                                          appIcon: app.icon, book: book, now: LockStore.trustedNow())
        let view = DistractionPanelView(model: model, onChoice: onChoice)
        let panel = NSPanel(contentRect: frame, styleMask: [.borderless, .nonactivatingPanel],
                            backing: .buffered, defer: false)
        panel.level = .floating
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .transient]
        panel.isMovable = false
        panel.becomesKeyOnlyIfNeeded = false

        let blur = NSVisualEffectView(frame: CGRect(origin: .zero, size: frame.size))
        blur.material = .hudWindow
        blur.blendingMode = .behindWindow
        blur.state = .active
        blur.wantsLayer = true
        blur.layer?.cornerRadius = 12
        blur.layer?.masksToBounds = true
        let host = NSHostingView(rootView: view)
        host.frame = blur.bounds
        host.autoresizingMask = [.width, .height]
        blur.addSubview(host)
        panel.contentView = blur
        panel.orderFrontRegardless()
        panel.makeKey()
        window = panel
        shownFor = app.processIdentifier
    }

    func hide() {
        window?.orderOut(nil)
        window = nil
        shownFor = nil
    }
}

/// What the panel says, worked out from the decision. Kept apart from the view
/// so the words are tested.
struct DistractionPanelModel {
    enum Kind: Equatable { case wait(seconds: Int), closed }

    var kind: Kind
    var eyebrow: String
    var title: String
    /// The person's own reason as a line for the panel, or nil when they gave none.
    static func own(_ book: RuleBook) -> String? {
        book.ownReason.map { String(localized: "You said: “\($0)”") }
    }

    var detail: String
    var openTitle: String?
    var opensLeft: Int?
    var opensPerDay: Int?
    var snoozeDelay: Int?
    var passAvailable: Bool
    var appName: String
    var appIcon: NSImage?

    init(decision: RuleDecision, appName: String, appIcon: NSImage?, book: RuleBook, now: Date) {
        self.appName = appName
        self.appIcon = appIcon
        let ruleID: UUID?
        switch decision {
        case let .wait(rule, _, _, _), let .block(rule, _, _): ruleID = rule
        case .allow: ruleID = nil
        }
        let rule = book.rules.first { $0.id == ruleID }
        let ruleName = rule?.name ?? String(localized: "Focus")
        passAvailable = EmergencyPass.available(lastUsed: book.emergencyPassUsed, at: now)

        switch decision {
        case let .wait(_, seconds, opensLeft, openMinutes):
            kind = .wait(seconds: seconds)
            eyebrow = ruleName
            title = String(localized: "What did you open \(appName) for?")
            detail = Self.own(book) ?? String(localized: "Breathe out slowly while the ring fills.")
            openTitle = String(localized: "Open for \(openMinutes) minutes")
            self.opensLeft = opensLeft
            if case let .wait(_, _, perDay?, _) = rule?.action { opensPerDay = perDay } else { opensPerDay = nil }
            snoozeDelay = nil
        case let .block(_, reason, until):
            kind = .closed
            let time = until.map { Self.time($0, now: now) }
            switch reason {
            case .focus:
                eyebrow = String(localized: "Focus")
                title = String(localized: "\(appName) is closed during Focus")
                detail = book.focus.map { $0.intent.isEmpty ? "" : String(localized: "You started this to: \($0.intent)") } ?? ""
            case .limitReached:
                eyebrow = ruleName
                title = String(localized: "Today’s time for \(appName) is used")
                detail = String(localized: "It opens again tomorrow.")
            case .noOpensLeft:
                eyebrow = ruleName
                title = String(localized: "No opens left today")
                detail = String(localized: "\(appName) opens again tomorrow.")
            case .scheduled:
                eyebrow = time.map { String(localized: "\(ruleName) · until \($0)") } ?? ruleName
                title = String(localized: "\(appName) is closed for now")
                detail = Self.own(book) ?? String(localized: "You chose this earlier, for a calmer day.")
            case .blocked:
                eyebrow = ruleName
                title = String(localized: "\(appName) is closed")
                detail = String(localized: "You chose to keep it closed.")
            }
            openTitle = nil
            opensLeft = nil
            opensPerDay = nil
            let strictness = reason == .focus ? (book.focus?.strictness ?? .gentle) : (rule?.strictness ?? .gentle)
            let snoozes = ruleID.flatMap { book.day.snoozesToday[$0] } ?? 0
            snoozeDelay = reason == .focus ? nil : SnoozePolicy.delayBeforeSnooze(strictness: strictness, snoozesToday: snoozes)
            if strictness != .locked { passAvailable = false }  // only offered when nothing else is
        case .allow:
            kind = .closed
            eyebrow = ""; title = ""; detail = ""
            openTitle = nil; opensLeft = nil; opensPerDay = nil; snoozeDelay = nil
        }
    }

    static func time(_ date: Date, now: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.autoupdatingCurrent
        formatter.setLocalizedDateFormatFromTemplate(Calendar.current.isDate(date, inSameDayAs: now) ? "jj:mm" : "EEE jj:mm")
        return formatter.string(from: date)
    }
}

struct DistractionPanelView: View {
    let model: DistractionPanelModel
    let onChoice: (DistractionPanelChoice) -> Void

    @State private var remaining: Int
    @State private var reason: String?

    init(model: DistractionPanelModel, onChoice: @escaping (DistractionPanelChoice) -> Void) {
        self.model = model
        self.onChoice = onChoice
        switch model.kind {
        case .wait(let seconds): _remaining = State(initialValue: seconds)
        case .closed: _remaining = State(initialValue: model.snoozeDelay ?? 0)
        }
    }
    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    private let gold = Color(red: 0.94, green: 0.73, blue: 0.24)

    var body: some View {
        ZStack {
            Color.black.opacity(0.28)
            card
                .frame(maxWidth: 440)
                .padding(24)
        }
        .onReceive(ticker) { _ in if remaining > 0 { remaining -= 1 } }
        .environment(\.colorScheme, .dark)
    }

    private var card: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Text(model.eyebrow).font(.system(size: 12, weight: .semibold)).foregroundColor(.secondary)
            }
            if case .closed = model.kind, let icon = model.appIcon {
                Image(nsImage: icon).resizable().frame(width: 56, height: 56)
                    .saturation(0.2).opacity(0.85)
                    .accessibilityHidden(true)
            }
            if case .wait(let seconds) = model.kind {
                ring(progress: seconds == 0 ? 1 : 1 - Double(remaining) / Double(seconds))
            }
            Text(model.title)
                .font(.system(size: 22, weight: .bold))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if !model.detail.isEmpty {
                Text(model.detail).font(.system(size: 13)).foregroundColor(.secondary).multilineTextAlignment(.center)
            }
            if case .wait = model.kind { reasons }
            if let left = model.opensLeft {
                Text(String(localized: "\(left) opens left today")).font(.system(size: 12)).foregroundColor(.secondary)
            }
            buttons.padding(.top, 6)
        }
        .padding(.horizontal, 28)
        .padding(.vertical, 26)
        .background(
            // Unrot's own dark gray, whatever the system appearance.
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(Color(white: 0.13).opacity(0.94))
                .shadow(color: .black.opacity(0.45), radius: 40, y: 20)
        )
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).stroke(Color.white.opacity(0.18), lineWidth: 0.5))
    }

    private func ring(progress: Double) -> some View {
        ZStack {
            Circle().stroke(Color.primary.opacity(0.1), lineWidth: 6)
            Circle().trim(from: 0, to: progress).stroke(gold, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 1), value: progress)
            Text(verbatim: remaining > 0 ? "\(remaining)" : "✓").font(.system(size: 26, weight: .bold, design: .rounded))
        }
        .frame(width: 84, height: 84)
        .accessibilityLabel(remaining > 0 ? String(localized: "\(remaining) seconds") : String(localized: "Ready"))
    }

    private var reasons: some View {
        let options = [String(localized: "Something I need"), String(localized: "Bored"),
                       String(localized: "A break"), String(localized: "Just habit")]
        return HStack(spacing: 6) {
            ForEach(options, id: \.self) { option in
                Button(option) { reason = option }
                    .buttonStyle(.plain)
                    .font(.system(size: 12, weight: reason == option ? .semibold : .regular))
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Capsule().fill(reason == option ? gold.opacity(0.25) : Color.primary.opacity(0.06)))
            }
        }
    }

    @ViewBuilder
    private var buttons: some View {
        VStack(spacing: 8) {
            switch model.kind {
            case .wait:
                primary(String(localized: "Close \(model.appName)")) { onChoice(.close) }
                if reason == String(localized: "Bored") || reason == String(localized: "Just habit") {
                    secondary(String(localized: "Breathe instead")) { onChoice(.breathe) }
                }
                let ready = remaining == 0
                Button(ready ? (model.openTitle ?? "") : String(localized: "\(model.openTitle ?? "") · in \(remaining) s")) {
                    onChoice(.open)
                }
                .buttonStyle(.plain)
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .disabled(!ready)
            case .closed:
                primary(String(localized: "Breathe instead")) { onChoice(.breathe) }
                secondary(String(localized: "Hide \(model.appName)")) { onChoice(.close) }
                if let delay = model.snoozeDelay {
                    let ready = remaining == 0
                    Button(ready ? String(localized: "5 more minutes")
                                 : String(localized: "5 more minutes · in \(remaining) s")) { onChoice(.snooze) }
                        .buttonStyle(.plain).font(.system(size: 12.5)).foregroundColor(.secondary)
                        .disabled(!ready)
                        .help(delay > 0 ? String(localized: "Firm rule: the wait grows each time today.") : "")
                } else if model.passAvailable {
                    Button(String(localized: "Use this week’s emergency pass…")) { onChoice(.emergencyPass) }
                        .buttonStyle(.plain).font(.system(size: 12)).foregroundColor(.secondary)
                }
            }
        }
    }

    private func primary(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 14, weight: .semibold)).frame(maxWidth: .infinity).frame(height: 38)
                .foregroundColor(Color(red: 0.17, green: 0.11, blue: 0.01))
                .background(Capsule().fill(LinearGradient(colors: [Color(red: 0.95, green: 0.76, blue: 0.35),
                                                                   Color(red: 0.85, green: 0.60, blue: 0.12)],
                                                          startPoint: .top, endPoint: .bottom)))
        }
        .buttonStyle(.plain)
        .keyboardShortcut(.defaultAction)
    }

    private func secondary(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 13.5, weight: .semibold)).frame(maxWidth: .infinity).frame(height: 36)
                .background(Capsule().fill(Color.primary.opacity(0.08)))
        }
        .buttonStyle(.plain)
    }
}
