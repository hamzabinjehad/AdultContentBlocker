import SwiftUI

/// Main window.
///
/// Organised around the three questions a person opens it to answer — *am I
/// protected?*, *what is being blocked?*, *what can I change?* — as four pages
/// in a sidebar: Overview, Blocking Rules, Lock, Settings. Each common task
/// has one obvious home, and the window resizes so longer text has room.
///
/// The interface has one job beyond starting a lock: make the state honest.
/// A self-control tool that *looks* armed while the filter is off is worse than
/// no tool, because the person stops being careful. So the Overview reports
/// what is actually enforced (`ProtectionStatus`), separately from whether a
/// lock is recorded (`LockStatus`), and never blends the two into one word.
struct ContentView: View {
    @StateObject private var lock = LockManager.shared
    @StateObject private var filter = FilterController.shared

    @State private var page: Page? = .overview

    enum Page: String, CaseIterable, Identifiable {
        case overview, setup, rules, lock, settings

        var id: String { rawValue }

        var title: LocalizedStringKey {
            switch self {
            case .overview: return "Overview"
            case .setup:    return "Setup"
            case .rules:    return "Blocking Rules"
            case .lock:     return "Lock"
            case .settings: return "Settings"
            }
        }

        var systemImage: String {
            switch self {
            case .overview: return "checkmark.shield"
            case .setup:    return "checklist"
            case .rules:    return "list.bullet.rectangle"
            case .lock:     return "lock"
            case .settings: return "gearshape"
            }
        }
    }

    var body: some View {
        NavigationSplitView {
            List(Page.allCases, selection: $page) { p in
                Label(p.title, systemImage: p.systemImage).tag(p)
            }
            .listStyle(.sidebar)
            .navigationSplitViewColumnWidth(min: 170, ideal: 190, max: 240)
        } detail: {
            ScrollView {
                Group {
                    switch page ?? .overview {
                    case .overview:
                        OverviewPage(lock: lock, filter: filter) { page = $0 }
                    case .setup:
                        SetupPage(filter: filter) { page = $0 }
                    case .rules:
                        RulesPage(isLocked: lock.isLocked)
                    case .lock:
                        LockPage(lock: lock, filter: filter)
                    case .settings:
                        SettingsPage(isLocked: lock.isLocked)
                    }
                }
                // A comfortable reading measure, left-aligned in a wide window
                // rather than stretched across it.
                .frame(maxWidth: 620, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(28)
            }
            .navigationTitle((page ?? .overview).title)
        }
        .frame(minWidth: 720, minHeight: 500)
        .task {
            guard !AppDelegate.isHostingTests else { return }
            await filter.reassertIfNeeded()
        }
    }
}

// MARK: - Shared pieces

/// A titled block with breathing room. The pages are built from these so
/// spacing, titles and subtitles stay identical across them.
private struct PageSection<Content: View>: View {
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey? = nil
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.title3.weight(.semibold))
                if let subtitle {
                    Text(subtitle)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The card the status lines sit on.
private struct Card<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(.quaternary.opacity(0.35), in: RoundedRectangle(cornerRadius: 10))
    }
}

/// Save/Revert row for an editable section. Sections save on their own
/// button rather than a window-wide one so a refused half of one edit cannot
/// take an unrelated edit down with it.
private struct SaveRow: View {
    let message: String?
    let isError: Bool
    let canSave: Bool
    let save: () -> Void
    let revert: () -> Void

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            if let message {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Button("Revert", action: revert).disabled(!canSave)
            Button("Save", action: save).disabled(!canSave)
                .keyboardShortcut(.defaultAction)
        }
    }
}

// MARK: - Overview

/// Am I protected? Am I locked? And, for anything not right, what do I press?
private struct OverviewPage: View {
    @ObservedObject var lock: LockManager
    @ObservedObject var filter: FilterController
    let goTo: (ContentView.Page) -> Void

    @State private var busy: StatusAction?
    @State private var error: String?
    @State private var showBrowserHelp = false
    @State private var notice: String?

    /// Re-read on every render. `lock.now` ticks once a second, so the
    /// extension heartbeat and the filter's domain count are never more than a
    /// second stale here without any extra plumbing.
    private var protection: ProtectionStatus {
        ProtectionStatus(ProtectionEvidence.current(filter: filter.availability,
                                                      authority: FilterSync.shared.status))
    }

    private var lockStatus: LockStatus {
        LockStatus(state: lock.state, now: lock.now,
                   pendingRelease: lock.pendingSelfRelease)
    }

    @ObservedObject private var guardian = BrowserGuard.shared
    @State private var setup: SetupChecklist?

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(guardian.alerts) { alert in
                Label(String(localized: "\(alert.name) is about to close.") + " "
                      + BrowserGuard.message(name: alert.name, reason: alert.reason),
                      systemImage: "exclamationmark.shield.fill")
                    .font(.callout)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
            protectionCard
            lockCard
            if let setup, !setup.isComplete {
                HStack(alignment: .firstTextBaseline) {
                    Label(String(localized: "Setup: \(setup.doneCount) of \(setup.required.count) steps done."),
                          systemImage: "checklist")
                        .font(.callout)
                    Spacer()
                    Button("Continue setup") { goTo(.setup) }
                }
            }

            // The one combination that is genuinely alarming: a lock is
            // recorded, and nothing enforces it. Neither card alone says so.
            if lock.isLocked && !protection.isEnforcingAnything
                && protection.level != .checking {
                Label("""
                      Your lock is running, but nothing is enforcing it yet. \
                      It will block nothing until the steps above are done.
                      """,
                      systemImage: "exclamationmark.triangle.fill")
                    .font(.callout)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let notice {
                Text(notice).font(.callout).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .alert("Something went wrong", isPresented: .constant(error != nil)) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
        .sheet(isPresented: $showBrowserHelp) { BrowserSetupHelp() }
        .task {
            let running = protection.layers.first?.ok ?? false
            setup = await Task.detached {
                SetupChecklist(SetupEvidence.current(systemFilterRunning: running))
            }.value
        }
    }

    private var protectionCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    StatusDot(level: protection.level)
                    Text(protection.headline)
                        .font(.title2.weight(.semibold))
                        .accessibilityAddTraits(.isHeader)
                }
                Text(protection.summary)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                Divider()

                ForEach(protection.layers) { layer in
                    layerRow(layer)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Protection: \(protection.headline). \(protection.summary)")
    }

    private func layerRow(_ layer: ProtectionStatus.Layer) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Image(systemName: layer.ok ? "checkmark.circle.fill"
                  : layer.state == .problem ? "exclamationmark.circle.fill"
                  : layer.state == .checking ? "ellipsis.circle" : "circle")
                .foregroundStyle(layer.ok ? Color.green
                                 : layer.state == .problem ? Color.orange : Color.secondary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(layer.name).font(.body.weight(.medium))
                Text(layer.detail)
                    .font(.callout)
                    .foregroundStyle(layer.state == .problem ? Color.orange : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            if let action = layer.action {
                Button(action.title) { perform(action) }
                    .disabled(busy != nil)
                    .controlSize(.small)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var lockCard: some View {
        Card {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Image(systemName: lockStatus.isLocked ? "lock.fill" : "lock.open")
                    .foregroundStyle(lockStatus.isLocked ? Color.primary : Color.secondary)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(lockStatus.headline)
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    if let detail = lockStatus.detail {
                        Text(detail)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                Spacer()
                Button(lockStatus.isLocked ? "Manage lock" : "Start a lock…") {
                    goTo(.lock)
                }
            }
        }
    }

    // MARK: Actions

    /// Every problem row's button lands here. Each branch does the one thing
    /// the row promised and nothing else; what it cannot do by itself (the
    /// person clicking Allow in System Settings, a browser being opened) it
    /// says in the notice under the cards.
    private func perform(_ action: StatusAction) {
        busy = action
        notice = nil
        Task { @MainActor in
            defer { busy = nil }
            switch action {
            case .retryFilterCheck:
                await filter.refresh()
                await FilterSync.shared.sync()

            case .enableFilter:
                notice = String(localized: """
                    If macOS asks, allow the filter under System Settings › \
                    General › Login Items & Extensions.
                    """)
                do { try await filter.enable() }
                catch { self.error = error.localizedDescription }

            case .updateList:
                await ListUpdater.shared.update()
                await filter.refresh()
                notice = String(localized: """
                    The filter picks up a new list within a few seconds. \
                    If this row does not change, the download failed — check \
                    the internet connection and try again.
                    """)

            case .installExtension:
                showBrowserHelp = true

            case .reconnectExtension:
                // Re-register the native-messaging link, then the only other
                // thing that stops a heartbeat is the browser itself.
                NativeMessagingInstaller.installIfNeeded()
                notice = String(localized: """
                    The link to the browser was re-registered. Open the \
                    browser and make sure the Hisn extension is turned on; \
                    this row updates within a minute.
                    """)
            }
        }
    }
}

private struct StatusDot: View {
    let level: ProtectionStatus.Level

    private var colour: Color {
        switch level {
        case .active:      return .green
        case .partial:     return .orange
        case .setupNeeded: return .secondary
        case .checking:    return .secondary
        }
    }

    var body: some View {
        Circle().fill(colour).frame(width: 10, height: 10)
            .accessibilityHidden(true)
    }
}

/// How to connect a browser, in the app rather than a document. The full
/// guided onboarding replaces this; until then it is the shortest honest
/// version that does not send anyone to a terminal.
private struct BrowserSetupHelp: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Connect a browser").font(.title3.weight(.semibold))
            Text("""
                 The system filter covers every app on this Mac. The browser \
                 extension adds page-text checking inside Chrome, Edge and \
                 other Chromium browsers. This app has already registered \
                 itself with those browsers; what remains is the extension.
                 """)
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 8) {
                step(1, """
                        Install the Hisn Protection extension in the browser, \
                        or ask whoever set up this Mac to install it for you.
                        """)
                step(2, "Open any web page. The extension contacts this app within a minute.")
                step(3, "Come back here — the Browser extension row reads Connected once it has.")
            }
            Text("Safari and Firefox are covered by the system filter only; the extension does not run there.")
                .font(.caption).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack { Spacer(); Button("Done") { dismiss() }.keyboardShortcut(.defaultAction) }
        }
        .padding(22)
        .frame(width: 440)
    }

    private func step(_ n: Int, _ text: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(verbatim: "\(n).").font(.callout.weight(.semibold)).monospacedDigit()
            Text(text).font(.callout).fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Setup

/// The steps of `docs/SETUP.md`, each read from this Mac: what is done, what
/// is left, and for each one left, what to do. Re-read on appear and on
/// "Check again" — most steps happen outside the app (a profile installed, a
/// password handed over), so there is nothing to observe live.
private struct SetupPage: View {
    @ObservedObject var filter: FilterController
    let goTo: (ContentView.Page) -> Void

    @State private var checklist: SetupChecklist?
    @State private var checking = false
    @State private var showBrowserHelp = false
    @State private var copied: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            PageSection(title: "Make it hold",
                    subtitle: """
                        Each step closes a way around the others. Do them in this order, \
                        with your partner for the ones they hold — the account split last, \
                        because it is the one that makes the rest stick.
                        """) {
                if let checklist {
                    Text(String(localized: "\(checklist.doneCount) of \(checklist.required.count) steps done."))
                        .font(.callout.weight(.medium))
                        .foregroundStyle(checklist.isComplete ? Color.green : Color.secondary)
                } else {
                    ProgressView().controlSize(.small)
                }
            }

            if let checklist {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(Array(checklist.steps.enumerated()), id: \.element.id) { index, step in
                        row(index + 1, step)
                    }
                }
            }

            HStack {
                Spacer()
                Button("Check again") { Task { await refresh() } }
                    .disabled(checking)
            }
        }
        .task { await refresh() }
        .sheet(isPresented: $showBrowserHelp) { BrowserSetupHelp() }
    }

    private func row(_ n: Int, _ step: SetupChecklist.Step) -> some View {
        Card {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Image(systemName: step.state == .done ? "checkmark.circle.fill"
                      : step.state == .optional ? "circle.dashed" : "circle")
                    .foregroundStyle(step.state == .done ? Color.green : Color.secondary)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: "\(n). \(step.title)").font(.body.weight(.medium))
                    Text(step.detail)
                        .font(.callout).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    if case let .command(line) = step.action {
                        HStack(spacing: 8) {
                            Text(verbatim: line)
                                .font(.callout.monospaced())
                                .textSelection(.enabled)
                                .environment(\.layoutDirection, .leftToRight)
                            Button(copied == line ? "Copied" : "Copy") {
                                NSPasteboard.general.clearContents()
                                NSPasteboard.general.setString(line, forType: .string)
                                copied = line
                            }
                            .controlSize(.small)
                        }
                        .padding(.top, 2)
                    }
                }
                Spacer()
                switch step.action {
                case .browserHelp:
                    Button("How to connect a browser") { showBrowserHelp = true }.controlSize(.small)
                case .partnerSettings:
                    Button("Open Settings") { goTo(.settings) }.controlSize(.small)
                case .screenTimeSettings:
                    Button("Open Screen Time") {
                        if let url = URL(string: "x-apple.systempreferences:com.apple.Screen-Time-Settings.extension") {
                            NSWorkspace.shared.open(url)
                        }
                    }.controlSize(.small)
                case .command, .none:
                    EmptyView()
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func refresh() async {
        checking = true
        defer { checking = false }
        let running = ProtectionStatus(ProtectionEvidence.current(filter: filter.availability,
                                                                    authority: FilterSync.shared.status))
            .layers.first?.ok ?? false
        checklist = await Task.detached {
            SetupChecklist(SetupEvidence.current(systemFilterRunning: running))
        }.value
    }
}

// MARK: - Lock

/// Starting a lock, or watching one run.
private struct LockPage: View {
    @ObservedObject var lock: LockManager
    @ObservedObject var filter: FilterController

    @State private var duration: LockManager.Duration = .week
    @State private var customAmount = ""
    @State private var customUnit: CustomUnit = .days
    @State private var strict = false
    @State private var error: String?
    @State private var showConfirm = false
    @State private var showReleaseConfirm = false

    enum CustomUnit: String, CaseIterable, Identifiable {
        // Minutes exist so a lock can be TRIED. Without them the shortest
        // startable lock is an hour, and an hour that cannot be shortened,
        // cancelled or escaped is a steep price for finding out what the
        // button does — so nobody tries it, and the first real lock someone
        // starts is also the first one they have ever seen run.
        //
        // This does not weaken anything: `LockManager.validated` still refuses
        // anything under `minimumLock`, so the floor is 60 seconds either way.
        case minutes = "minutes"
        case hours = "hours"
        case days = "days"
        case weeks = "weeks"

        var id: String { rawValue }
        var title: String {
            switch self {
            case .minutes: return String(localized: "minutes")
            case .hours:   return String(localized: "hours")
            case .days:    return String(localized: "days")
            case .weeks:   return String(localized: "weeks")
            }
        }
        var seconds: TimeInterval {
            switch self {
            case .minutes: return 60
            case .hours:   return 3600
            case .days:    return 86400
            case .weeks:   return 7 * 86400
            }
        }
    }

    /// Would a lock started now block anything? Read from the same status the
    /// Overview shows, so the two can never disagree.
    private var enforcingAnything: Bool {
        ProtectionStatus(ProtectionEvidence.current(filter: filter.availability,
                                                      authority: FilterSync.shared.status))
            .isEnforcingAnything
    }

    /// The length the button would start, or nil when there is nothing valid to
    /// start. Nil disables the button rather than substituting a nearby legal
    /// value — a lock cannot be shortened afterwards, so quietly turning a
    /// mistyped 3650 into 365 would commit someone to a year they never chose.
    private var selectedSeconds: TimeInterval? {
        if let preset = duration.seconds { return preset }
        guard let amount = Double(customAmount.trimmingCharacters(in: .whitespaces))
        else { return nil }
        return LockManager.validated(seconds: amount * customUnit.seconds)
    }

    var body: some View {
        Group {
            if lock.isLocked { activeLock } else { setup }
        }
        .alert("Something went wrong", isPresented: .constant(error != nil)) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }

    // MARK: Not locked

    private var setup: some View {
        VStack(alignment: .leading, spacing: 22) {
            PageSection(title: "How long?") {
                Picker("Length", selection: $duration) {
                    ForEach(LockManager.Duration.allCases) { d in
                        Text(d.title).tag(d)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()

                if duration == .custom {
                    HStack(spacing: 8) {
                        TextField("How long", text: $customAmount)
                            .textFieldStyle(.roundedBorder)
                            .frame(width: 80)
                        Picker("Unit", selection: $customUnit) {
                            ForEach(CustomUnit.allCases) { Text($0.title).tag($0) }
                        }
                        .labelsHidden()
                        .frame(width: 100)
                        Spacer()
                    }
                    .padding(.top, 2)
                }

                Text(lengthHint)
                    .font(.callout)
                    .foregroundStyle(isCustomInvalid ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            PageSection(title: "Mode") {
                Toggle(isOn: $strict) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Strict mode")
                        Text("Blocks everything except sites you allow. A block list can never cover every site; this can.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }

            // The honest warning. People who are surprised by a lock fight it;
            // people who chose it with clear eyes keep it.
            Card {
                VStack(alignment: .leading, spacing: 8) {
                    Label("This cannot be undone by you alone",
                          systemImage: "lock.fill")
                        .font(.body.weight(.medium))
                    Text("""
                         Once started, you cannot shorten or cancel this. \
                         Early release takes 48 hours, or an approval from \
                         your accountability partner.
                         """)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            // Just-in-time, at the point of decision rather than as a permanent
            // banner up top. Someone about to commit to a lock that enforces
            // nothing is the one moment this warning earns its alarm — and the
            // confirmation dialog repeats it, so it cannot be clicked past by
            // reflex.
            if !enforcingAnything {
                Label("""
                      Nothing is set up to enforce a lock yet — it would run \
                      but block nothing. Finish setup on the Overview page first.
                      """,
                      systemImage: "exclamationmark.triangle.fill")
                    .font(.callout)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Button {
                showConfirm = true
            } label: {
                Text(startButtonTitle)
                    .frame(maxWidth: .infinity)
            }
            .controlSize(.large)
            .buttonStyle(.borderedProminent)
            .disabled(selectedSeconds == nil)
            .confirmationDialog(
                confirmTitle,
                isPresented: $showConfirm, titleVisibility: .visible
            ) {
                Button("Start the lock", role: .destructive) { start() }
                Button("Not yet", role: .cancel) {}
            } message: {
                // Names the wall-clock moment, not just the length. "90 days"
                // is abstract in a way "18 February" is not, and the point of
                // this dialog is that nobody starts a lock they misjudged.
                Text(confirmMessage)
            }
        }
    }

    // MARK: Locked

    private var activeLock: some View {
        VStack(alignment: .leading, spacing: 22) {
            Card {
                VStack(alignment: .leading, spacing: 4) {
                    Text(lock.remainingDescription)
                        .font(.system(size: 34, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                    Text(String(localized: """
                         \(LockStatus.modeLabel(lock.state.mode)) · until \
                         \(lock.state.deadline.formatted(date: .long, time: .shortened))
                         """))
                        .font(.callout).foregroundStyle(.secondary)
                }
            }

            if let pending = lock.pendingSelfRelease {
                Card {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Early release requested")
                            .font(.body.weight(.medium))
                        Text("Unlocks \(pending.formatted()). You can cancel this, but you cannot speed it up.")
                            .font(.callout).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("Cancel the request") { lock.cancelSelfRelease() }
                            .controlSize(.small)
                    }
                }
            }

            PageSection(title: "Make it stronger",
                    subtitle: "Always available. Making the lock weaker is not.") {
                HStack {
                    Button("Add a week") {
                        do { try lock.extend(by: 7 * 86400) }
                        catch { self.error = error.localizedDescription }
                    }
                    if lock.state.mode != "strict" {
                        Button("Switch to strict") {
                            do { try lock.tightenToStrict() }
                            catch { self.error = error.localizedDescription }
                        }
                    }
                }
            }

            PageSection(title: "Your partner can end it now",
                    subtitle: "Immediate, and never without them.") {
                PartnerReleaseCard(lock: lock)
            }

            if lock.pendingSelfRelease == nil {
                // The way out. Without this button the request could not be
                // created at all, and the panel above — which only ever offered
                // to *cancel* one — was showing an exit nobody could reach.
                //
                // Deliberately quiet, and deliberately present. The threat model
                // is explicit that a lock with no exit is not the safer design:
                // it is the one people uninstall pre-emptively, and it is
                // dangerous when someone genuinely needs the web. What keeps it
                // honest is that the exit is slow and cannot be hurried.
                PageSection(title: "Ending early",
                        subtitle: """
                            A release request takes \(LockManager.describe(LockManager.selfReleaseDelay)) \
                            to arrive and cannot be hurried. Your accountability \
                            partner can approve one immediately.
                            """) {
                    Button("Request early release…") { showReleaseConfirm = true }
                        .confirmationDialog(
                            "Request early release?",
                            isPresented: $showReleaseConfirm, titleVisibility: .visible
                        ) {
                            Button("Request it") { requestRelease() }
                            Button("Never mind", role: .cancel) {}
                        } message: {
                            Text("""
                                 The lock would end \(Date().addingTimeInterval(LockManager.selfReleaseDelay).formatted()). \
                                 You can cancel the request at any time, but you \
                                 cannot make it arrive sooner.
                                 """)
                        }
                }
            }
        }
    }

    private var startButtonTitle: String {
        guard let seconds = selectedSeconds else { return String(localized: "Start lock") }
        return String(localized: "Start a lock of \(LockManager.describe(seconds))")
    }

    private var confirmTitle: String {
        guard let seconds = selectedSeconds else { return String(localized: "Start lock") }
        return String(localized: "Start a lock of \(LockManager.describe(seconds))?")
    }

    /// Only once something has been typed. An empty field is a person who has
    /// not finished, not a person who is wrong, and colouring it red says the
    /// opposite.
    private var isCustomInvalid: Bool {
        duration == .custom && !customAmount.isEmpty && selectedSeconds == nil
    }

    private var lengthHint: String {
        let shortest = LockManager.describe(LockManager.minimumLock)
        let longest = LockManager.describe(LockManager.maximumLock)
        if isCustomInvalid {
            return String(localized: "Enter a length between \(shortest) and \(longest).")
        }
        guard let seconds = selectedSeconds else {
            return String(localized: "Between \(shortest) and \(longest).")
        }
        let end = Date().addingTimeInterval(seconds).formatted(date: .abbreviated, time: .shortened)
        return String(localized: "Ends \(end).")
    }

    /// The confirmation-dialog body: the wall-clock deadline, and — when
    /// nothing is set up to enforce it — the fact that the lock will block
    /// nothing, repeated here so it cannot be clicked past by reflex from the
    /// Start button.
    private var confirmMessage: String {
        guard let seconds = selectedSeconds else { return "" }
        let end = Date().addingTimeInterval(seconds).formatted()
        var msg = String(localized: "You will not be able to turn this off until \(end).")
        if !enforcingAnything {
            msg += "\n\n" + String(localized: """
                Nothing is set up to enforce it yet, so it will run but \
                block nothing until you finish setup.
                """)
        }
        return msg
    }

    private func start() {
        guard let seconds = selectedSeconds else { return }
        Task {
            do { try await lock.start(seconds: seconds, strict: strict) }
            catch { self.error = error.localizedDescription }
        }
    }

    private func requestRelease() {
        do { _ = try lock.requestSelfRelease() }
        catch { self.error = error.localizedDescription }
    }
}

// MARK: - Blocking Rules

/// What is being blocked, beyond the published list: the person's own sites,
/// words and apps.
///
/// Reachable in both states on purpose. Adding a block and dropping an
/// allowance are the two edits that stay legal during a lock, and they are
/// exactly the ones someone wants at the moment they find a site the list
/// missed.
private struct RulesPage: View {
    let isLocked: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 30) {
            if isLocked {
                Label("""
                      A lock is running. You can add blocks and remove \
                      allowances; the reverse waits until it ends.
                      """,
                      systemImage: "lock.fill")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            SiteListsSection(isLocked: isLocked)
            Divider()
            UserBlocksSection(isLocked: isLocked)
            Divider()
            BrowsersSection(isLocked: isLocked)
        }
    }
}

/// Where the person writes their own two lists.
///
/// Both are edited together and saved together, because the guard that matters
/// is about the pair: while a lock runs you may add blocks and withdraw
/// allowances, and neither of the opposites. Saving them separately would let
/// a refused half leave the other half applied.
struct SiteListsSection: View {
    let isLocked: Bool

    @State private var blockText = ""
    @State private var allowText = ""
    @State private var savedBlockText = ""
    @State private var savedAllowText = ""
    @State private var message: String?
    @State private var isError = false

    private var isDirty: Bool {
        blockText != savedBlockText || allowText != savedAllowText
    }

    var body: some View {
        PageSection(title: "Your sites",
                subtitle: """
                    One domain per line — a full URL is fine, it becomes just \
                    the domain. Subdomains are included automatically, so \
                    example.com also covers cdn.example.com.
                    """) {
            editor("Always block these",
                   subtitle: "On top of the published list.",
                   text: $blockText,
                   placeholder: "reddit.com")

            editor("Always allowed",
                   subtitle: "Reachable even if the published list names it. In strict mode nothing else is reachable.",
                   text: $allowText,
                   placeholder: "github.com")

            SaveRow(message: message, isError: isError, canSave: isDirty,
                    save: save, revert: load)
        }
        .onAppear(perform: load)
    }

    private func load() {
        savedBlockText = SiteLists.customBlocks().joined(separator: "\n")
        savedAllowText = SiteLists.allowlist().joined(separator: "\n")
        blockText = savedBlockText
        allowText = savedAllowText
        message = nil
    }

    private func editor(_ title: LocalizedStringKey, subtitle: LocalizedStringKey,
                        text: Binding<String>, placeholder: String) -> some View {
        // Counted as you type, from the same parser Save uses, so the number
        // here and the outcome on Save can never disagree. A dropped line is
        // the failure this exists to prevent: it looks identical to a saved one
        // and is only discovered when the page you meant to block opens.
        let summary = Self.summarize(text.wrappedValue)
        return VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.body.weight(.medium))
            Text(subtitle).font(.caption).foregroundStyle(.secondary)
            ZStack(alignment: .topLeading) {
                if text.wrappedValue.isEmpty {
                    Text(verbatim: placeholder)
                        .font(.system(.body, design: .monospaced))
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 8)
                        .allowsHitTesting(false)
                }
                TextEditor(text: text)
                    .font(.system(.body, design: .monospaced))
                    .scrollContentBackground(.hidden)
                    .accessibilityLabel(title)
            }
            .frame(height: 96)
            .overlay(RoundedRectangle(cornerRadius: 6)
                .stroke(summary.hasProblem ? Color.orange.opacity(0.7)
                                           : Color.secondary.opacity(0.35)))
            Text(summary.text)
                .font(.caption)
                .foregroundStyle(summary.hasProblem ? Color.orange : Color.secondary)
                .frame(minHeight: 12, alignment: .leading)
        }
    }

    /// Live one-line summary of a domain editor's contents.
    static func summarize(_ text: String) -> (text: String, hasProblem: Bool) {
        let r = SiteLists.parse(text)
        let n = r.domains.count
        if r.ignored == 0 {
            return (n == 0 ? "" : String(localized: "\(n) domains"), false)
        }
        return (String(localized: "\(n) valid · \(r.ignored) skipped (not a domain)"), true)
    }

    private func save() {
        let blocks = SiteLists.parse(blockText)
        let allows = SiteLists.parse(allowText)
        do {
            try SiteLists.save(customBlocks: blocks.domains,
                               allowlist: allows.domains,
                               locked: isLocked)
            FilterSync.soon()
        } catch {
            isError = true
            message = error.localizedDescription
            return
        }

        // Saved. Say so — and say what was NOT saved. A line that was silently
        // dropped looks exactly like one that saved, and someone who mistyped a
        // domain would otherwise walk away believing it is blocked.
        let ignored = blocks.ignored + allows.ignored
        savedBlockText = blocks.domains.joined(separator: "\n")
        savedAllowText = allows.domains.joined(separator: "\n")
        isError = false
        if ignored == 0 {
            blockText = savedBlockText
            allowText = savedAllowText
            message = String(localized: "Saved.")
        } else {
            // Leave the rejected lines in the editor so they can be fixed.
            message = String(localized: "Saved. Left out because they are not domains: \(ignored).")
        }
    }
}

/// Words and apps the person blocks by hand.
///
/// Separate from `SiteListsSection` because the two ask different questions.
/// A site list is "which addresses", answered from memory. This is "which
/// words" and "which apps" — the first needs a warning about how easily a bad
/// word blocks the wrong thing, the second needs a file picker.
struct UserBlocksSection: View {
    let isLocked: Bool

    @State private var wordsText = ""
    @State private var apps: [String] = []
    @State private var savedWordsText = ""
    @State private var savedApps: [String] = []
    @State private var message: String?
    @State private var isError = false

    private var isDirty: Bool { wordsText != savedWordsText || apps != savedApps }

    var body: some View {
        PageSection(title: "Your words and apps",
                subtitle: "Blocked on top of the built-in lists.") {
            // --- words ---------------------------------------------------
            VStack(alignment: .leading, spacing: 3) {
                Text("Words").font(.body.weight(.medium))
                Text("""
                     One per line. A page is blocked when these appear often \
                     enough in it — so a common word blocks ordinary pages \
                     too. Minimum \(UserBlocks.minimumTermLength) letters.
                     """)
                    .font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                ZStack(alignment: .topLeading) {
                    if wordsText.isEmpty {
                        Text("gambling\nbetting odds")
                            .font(.system(.body, design: .monospaced))
                            .foregroundStyle(.tertiary)
                            .padding(.horizontal, 5).padding(.vertical, 8)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $wordsText)
                        .font(.system(.body, design: .monospaced))
                        .scrollContentBackground(.hidden)
                        .accessibilityLabel("Words")
                }
                .frame(height: 88)
                .overlay(RoundedRectangle(cornerRadius: 6)
                    .stroke(wordsSummary.hasProblem ? Color.orange.opacity(0.7)
                                                    : Color.secondary.opacity(0.35)))
                // Live, from the same parser Save uses. Catches the too-short
                // words and the 200-word cap here, where they can be fixed,
                // rather than as a refusal after Save.
                Text(wordsSummary.text)
                    .font(.caption)
                    .foregroundStyle(wordsSummary.hasProblem ? Color.orange : Color.secondary)
                    .frame(minHeight: 12, alignment: .leading)
            }

            // --- apps ----------------------------------------------------
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text("Apps").font(.body.weight(.medium))
                    Spacer()
                    Button("Add app…") { pickApp() }
                        .controlSize(.small)
                }
                // Said plainly rather than discovered later. Someone who blocks
                // a note-taking app and finds it still opens will conclude the
                // feature is broken, when it is working exactly as designed.
                Text("""
                     Blocks the app's internet access. It still opens, and \
                     still works offline — stopping a launch needs Screen \
                     Time, which no app can do for you.
                     """)
                    .font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                if apps.isEmpty {
                    Text("No apps blocked.")
                        .font(.callout).foregroundStyle(.tertiary)
                        .padding(.vertical, 6)
                } else {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(apps, id: \.self) { id in
                            HStack(spacing: 6) {
                                Text(verbatim: displayName(for: id)).font(.callout)
                                Text(verbatim: id).font(.caption).foregroundStyle(.tertiary)
                                Spacer()
                                Button {
                                    apps.removeAll { $0 == id }
                                } label: {
                                    Image(systemName: "minus.circle")
                                }
                                .buttonStyle(.borderless)
                                .disabled(isLocked && savedApps.contains(id))
                                .accessibilityLabel("Remove \(displayName(for: id))")
                                .help(isLocked && savedApps.contains(id)
                                      ? "Cannot remove while a lock is running"
                                      : "Remove")
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }
            }

            SaveRow(message: message, isError: isError, canSave: isDirty,
                    save: save, revert: load)
        }
        .onAppear(perform: load)
    }

    private func load() {
        savedWordsText = UserBlocks.terms().joined(separator: "\n")
        savedApps = UserBlocks.apps()
        wordsText = savedWordsText
        apps = savedApps
        message = nil
    }

    private var wordsSummary: (text: String, hasProblem: Bool) {
        Self.summarizeWords(wordsText)
    }

    /// Live one-line summary of the words box: valid count, and any reason a
    /// line will not be used (too short, unusable, or over the cap). Static and
    /// pure so it is testable without standing up the view.
    static func summarizeWords(_ text: String) -> (text: String, hasProblem: Bool) {
        let r = UserBlocks.parseTerms(text)
        var problems: [String] = []
        if !r.tooShort.isEmpty {
            problems.append(String(localized: """
                too short: \(r.tooShort.count) (minimum \(UserBlocks.minimumTermLength) letters)
                """))
        }
        if r.ignored > 0 { problems.append(String(localized: "unusable: \(r.ignored)")) }
        if r.terms.count > UserBlocks.maximumTerms {
            problems.append(String(localized: "over the limit of \(UserBlocks.maximumTerms) words"))
        }
        if problems.isEmpty {
            let n = r.terms.count
            return (n == 0 ? "" : String(localized: "\(n) words"), false)
        }
        return (String(localized: "\(r.terms.count) valid") + " · "
                + problems.formatted(.list(type: .and)), true)
    }

    private func displayName(for bundleID: String) -> String {
        guard let url = NSWorkspace.shared
            .urlForApplication(withBundleIdentifier: bundleID) else { return bundleID }
        return FileManager.default.displayName(atPath: url.path)
    }

    /// Pick the app rather than type its identifier. The filter compares a
    /// signing identifier, which is not something anyone knows by heart, and a
    /// typo produces a rule that silently never matches.
    private func pickApp() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.application]
        panel.allowsMultipleSelection = true
        panel.directoryURL = URL(fileURLWithPath: "/Applications")
        panel.prompt = String(localized: "Block")
        guard panel.runModal() == .OK else { return }

        var added = 0, failed: [String] = []
        for url in panel.urls {
            if let id = UserBlocks.bundleIdentifier(forAppAt: url) {
                if !apps.contains(id) { apps.append(id); added += 1 }
            } else {
                failed.append(FileManager.default.displayName(atPath: url.path))
            }
        }
        apps.sort()
        isError = !failed.isEmpty
        message = failed.isEmpty
            ? (added == 0 ? String(localized: "Already on the list.") : nil)
            : String(localized: "Could not read an identifier for \(failed.formatted(.list(type: .and))).")
    }

    private func save() {
        let parsed = UserBlocks.parseTerms(wordsText)
        do {
            try UserBlocks.save(terms: parsed.terms, apps: apps, locked: isLocked)
            FilterSync.soon()
        } catch {
            isError = true
            message = error.localizedDescription
            return
        }

        // Say what was dropped. A word silently left out looks identical to
        // one that saved, and the person finds out only when the page they
        // expected to be blocked opens normally.
        var notes: [String] = []
        if !parsed.tooShort.isEmpty {
            notes.append(String(localized: "Too short to use: \(parsed.tooShort.formatted(.list(type: .and))).")) 
        }
        if parsed.ignored > 0 {
            notes.append(String(localized: "Lines that were not usable words: \(parsed.ignored)."))
        }
        savedWordsText = parsed.terms.joined(separator: "\n")
        savedApps = apps
        isError = false
        if notes.isEmpty {
            wordsText = savedWordsText
            message = String(localized: "Saved.")
        } else {
            message = ([String(localized: "Saved.")] + notes).joined(separator: " ")
        }
    }
}

// MARK: - Settings

/// The content-inspection settings, and the technical details that used to
/// crowd the status header.
///
/// Only layers that actually run appear here. A switch for a feature that is
/// not built would be the "looks protected, is not" failure the threat model
/// rates as worse than being switched off — so there is no image control until
/// there is image classification.
///
/// The copy under each control says WHERE it applies, because the two layers
/// have genuinely different reach and a user who assumes otherwise is being
/// misled: keyword matching runs in the system filter and therefore covers
/// every browser and app, while page-text checking exists only inside the
/// Chrome extension.
/// Which apps that open web pages may stay open during a lock.
///
/// The guard decides "browser" from what an app declares, so an app that
/// registers for web links without being a browser shows up here too — that
/// is the price of catching tomorrow's Chromium fork without a list update,
/// and the reason this section exists. Allowing waits for the lock to end;
/// withdrawing an allowance is always accepted.
struct BrowsersSection: View {
    let isLocked: Bool

    @ObservedObject private var guardian = BrowserGuard.shared
    @State private var candidates: [BrowserGuard.Candidate] = []
    @State private var error: String?

    var body: some View {
        PageSection(title: "Browsers during a lock",
                subtitle: """
                    While a lock runs, Hisn closes any browser it is not \
                    running inside: a Chromium browser whose Hisn extension has \
                    stopped checking in, and any other browser. Safari is covered \
                    by Screen Time and is left alone.
                    """) {
            if candidates.isEmpty {
                Text("No browsers found.").font(.callout).foregroundStyle(.secondary)
            }
            ForEach(candidates) { c in
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: c.name).font(.body.weight(.medium))
                        Text(describe(c.coverage))
                            .font(.callout).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    if c.coverage == .uncovered || c.coverage == .allowedByUser {
                        Toggle("Allow during a lock", isOn: Binding(
                            get: { c.coverage == .allowedByUser },
                            set: { allow in
                                do { try guardian.setAllowed(c.bundleID, allow) }
                                catch { self.error = error.localizedDescription }
                                reload()
                            }))
                            .toggleStyle(.switch)
                            .controlSize(.small)
                            .disabled(isLocked && c.coverage == .uncovered)
                    }
                }
            }
            if let error {
                Text(error).font(.caption).foregroundStyle(.red)
            }
        }
        .onAppear(perform: reload)
    }

    private func reload() { candidates = guardian.candidates() }

    private func describe(_ coverage: BrowserGuardPolicy.Coverage) -> String {
        switch coverage {
        case .exempt:
            return String(localized: "Left open — covered by Screen Time and the network layers.")
        case .needsExtension:
            return String(localized: """
                Stays open while its Hisn extension checks in; closed if the \
                extension is switched off.
                """)
        case .uncovered:
            return String(localized: "No Hisn protection inside — closed during a lock.")
        case .allowedByUser:
            return String(localized: "You allowed it. It stays open during a lock with no page checking.")
        }
    }
}

/// The accountability partner's key: set up together, before a lock.
///
/// The partner makes a key pair on their own device with the Hisn Partner page
/// (`partner/index.html`) and reads out, or sends, the public half. Hisn keeps
/// only that half — nothing here can approve anything by itself.
struct PartnerSection: View {
    let isLocked: Bool

    @State private var keyText = ""
    @State private var current = PartnerService.currentKey()
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        PageSection(title: "Accountability partner",
                subtitle: """
                    Someone you trust who can end a lock early by approving \
                    it — immediately, but never without them. They open the Hisn \
                    Partner page on their own phone or computer, create a key \
                    there, and send you the code it shows under “Your key”.
                    """) {
            if let current {
                HStack(alignment: .firstTextBaseline) {
                    Label("Partner key set · \(PartnerService.fingerprint(current))",
                          systemImage: "person.badge.shield.checkmark")
                    Spacer()
                    Button("Remove") { save(nil) }
                        .help("""
                              Removing the key removes the immediate exit. It is \
                              always allowed; setting a new one waits for no lock.
                              """)
                }
                Text("Check with your partner that their page shows the same fingerprint.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if !isLocked || current == nil {
                HStack {
                    TextField("HISN-PK-…", text: $keyText)
                        .textFieldStyle(.roundedBorder)
                        .font(.body.monospaced())
                    Button(current == nil ? "Save key" : "Replace key") { save(keyText) }
                        .disabled(keyText.trimmingCharacters(in: .whitespaces).isEmpty || isLocked)
                }
                if isLocked {
                    Text("A lock is running, so a partner key can only be added once it ends.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            if let message {
                Text(message).font(.caption)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func save(_ key: String?) {
        do {
            try PartnerService.saveKey(key, locked: isLocked)
            FilterSync.soon()
            current = PartnerService.currentKey()
            keyText = ""
            isError = false
            message = key == nil ? String(localized: "Partner key removed.")
                                 : String(localized: "Partner key saved.")
        } catch {
            isError = true
            message = error.localizedDescription
        }
    }
}

/// The partner route out of a running lock: send the code, paste the approval.
struct PartnerReleaseCard: View {
    @ObservedObject var lock: LockManager

    @State private var approval = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        if PartnerService.currentKey() == nil {
            Text("""
                 No accountability partner is set up, so the only way out is the \
                 request below. You can add a partner in Settings after this lock.
                 """)
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        } else if let code = lock.partnerChallenge {
            VStack(alignment: .leading, spacing: 8) {
                Text("1. Send this code to your partner:").font(.callout)
                HStack {
                    Text(verbatim: code).font(.callout.monospaced()).textSelection(.enabled)
                    Spacer()
                    Button("Copy") {
                        NSPasteboard.general.clearContents()
                        NSPasteboard.general.setString(code, forType: .string)
                    }.controlSize(.small)
                }
                Text("""
                     2. If they agree, their Hisn Partner page gives them an \
                     approval to send back. Paste it here:
                     """).font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    TextField("HISN-OK-…", text: $approval)
                        .textFieldStyle(.roundedBorder)
                        .font(.callout.monospaced())
                    Button("End the lock") { release() }
                        .disabled(approval.trimmingCharacters(in: .whitespaces).isEmpty || busy)
                }
                if let error {
                    Text(error).font(.caption).foregroundStyle(.red)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func release() {
        busy = true
        Task {
            defer { busy = false }
            do {
                try await lock.releaseWithPartnerApproval(approval)
                error = nil
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

private struct SettingsPage: View {
    let isLocked: Bool

    @State private var settings = Inspection.Settings.default
    @State private var saved = Inspection.Settings.default
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        VStack(alignment: .leading, spacing: 30) {
            PageSection(title: "Content checking",
                    subtitle: isLocked
                        ? "A lock is running. You can turn checks on and raise sensitivity; the reverse waits until it ends."
                        : "These run on top of the site lists, and catch pages the lists have never seen.") {
                Toggle(isOn: $settings.hostKeywords) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Match keywords in addresses")
                        Text("Catches a site registered today, before any list has it. Works in every app on this Mac.")
                            .font(.callout).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                Toggle(isOn: $settings.text) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Check the words on a page")
                        Text("Reads the page itself, so it catches explicit content on an ordinary site. Chrome and Edge only.")
                            .font(.callout).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("How strict").font(.body)
                        Spacer()
                        Text(verbatim: "\(settings.textSensitivity)")
                            .font(.callout).monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                    Slider(value: Binding(
                        get: { Double(settings.textSensitivity) },
                        set: { settings.textSensitivity = Int($0) }
                    ), in: 0...100, step: 5) {
                        Text("How strict")
                    }
                    .labelsHidden()
                    .disabled(!settings.text)
                    Text("""
                         Higher catches more, and blocks more pages that turn out \
                         to be innocent. Medical and reference sites are exempt \
                         from this check at any setting.
                         """)
                        .font(.caption).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                SaveRow(message: message, isError: isError,
                        canSave: settings != saved, save: save, revert: load)
            }

            Divider()

            PartnerSection(isLocked: isLocked)

            Divider()

            LanguageSection()

            Divider()

            DetailsSection()
        }
        .onAppear(perform: load)
    }

    private func load() {
        saved = Inspection.read()
        settings = saved
        message = nil
    }

    private func save() {
        do {
            try Inspection.save(settings, locked: isLocked)
            FilterSync.soon()
            saved = settings
            isError = false
            message = String(localized: "Saved.")
        } catch {
            isError = true
            message = error.localizedDescription
        }
    }
}

/// The app's language. The extension has its own switch in its popup; the
/// two are set separately because each lives in a different program.
private struct LanguageSection: View {
    @ObservedObject private var lock = LockManager.shared
    @State private var choice = AppLanguage.chosen

    var body: some View {
        PageSection(title: "Language") {
            Picker("Language", selection: $choice) {
                ForEach(AppLanguage.allCases) { Text(verbatim: $0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .onChange(of: choice) { AppLanguage.choose($0) }

            if AppLanguage.needsRestart(for: choice) {
                if lock.isLocked {
                    // Quit is refused during a lock, so no restart is offered.
                    Text("Hisn switches language the next time it starts — after this lock, or when you next log in.")
                        .font(.caption).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    HStack {
                        Text("Hisn switches language when it restarts.")
                            .font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Button("Restart now") { AppLanguage.restart() }
                            .controlSize(.small)
                    }
                }
            }
        }
    }
}

/// The numbers. Useful when something is wrong, noise when nothing is — so
/// they live here rather than on the Overview.
private struct DetailsSection: View {
    private var defaults: UserDefaults? { UserDefaults(suiteName: LockStore.appGroup) }

    var body: some View {
        PageSection(title: "Details") {
            Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 6) {
                row("Block list version",
                    value: defaults.map { "\($0.integer(forKey: "listVersion"))" } ?? "—")
                row("Domains in the filter",
                    value: defaults.map { $0.integer(forKey: "filterDomainCount").formatted() } ?? "—")
                row("Last list check",
                    value: (defaults?.object(forKey: "lastListCheck") as? Date)?
                        .formatted(date: .abbreviated, time: .shortened) ?? String(localized: "never"))
                row("Browser last connected",
                    value: (defaults?.object(forKey: "extensionLastSeen") as? Date)?
                        .formatted(date: .abbreviated, time: .shortened) ?? String(localized: "never"))
                row("App version",
                    value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")
            }
            .font(.callout)
        }
    }

    private func row(_ label: LocalizedStringKey, value: String) -> some View {
        GridRow {
            Text(label).foregroundStyle(.secondary)
            Text(verbatim: value).monospacedDigit().textSelection(.enabled)
        }
        .accessibilityElement(children: .combine)
    }
}
