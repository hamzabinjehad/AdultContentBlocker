import SwiftUI

/// "Deletable only after a date" without a second person: Hisn keeps the
/// Screen Time passcode until the date the person chose.
struct PasscodeVaultSection: View {
    @ObservedObject var vault: PasscodeVaultController
    @ObservedObject var protection: ProtectionController
    /// `VaultCommitmentDates.pickerTag` stands for "until my commitment ends".
    @State private var days = 7
    @State private var understands = false
    @State private var confirmCreate = false
    @State private var confirmExtend = false
    @State private var confirmMatch = false
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo: .largeTitle) private var passcodeSize: CGFloat = 44
    @ScaledMetric(relativeTo: .largeTitle) private var passcodeTracking: CGFloat = 12
    /// One shared timer. A publisher made inside `body` is replaced, and restarted, every time the
    /// parent redraws (each second, with the commitment clock), so it never reached 30 seconds and
    /// the setup window and the dates below did not move on their own.
    private static let ticker = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        Section {
            Text("vault.body").font(.callout)
            switch vault.phase {
            case nil:
                if vault.storageHealthy { createControls }
            case .setup(let passcode, let hidesAt)?:
                setupSteps(passcode: passcode, hidesAt: hidesAt)
                commitmentDates
            case .sealed(let until)?:
                Label("vault.sealed", systemImage: "lock.fill")
                LabeledContent("vault.sealedUntil") { dateText(until) }
                commitmentDates
                Button("vault.extend") { confirmExtend = true }.disabled(vault.busy)
                LockConfirmationRows(claims: [.passcodeVault], vault: vault.facts, now: protection.now)
            case .due(let since)?:
                Label("vault.due", systemImage: "lock.open")
                LabeledContent("vault.sealedUntil") { dateText(since) }
                commitmentDates
                Button("vault.unseal") { Task { await vault.unseal() } }.disabled(vault.busy)
                if vault.busy { ProgressView() }
            case .revealed(let passcode)?:
                passcodeView(passcode)
                Text("vault.revealedBody").font(.footnote).foregroundStyle(.secondary)
                Button("vault.forget", role: .destructive) { vault.forget() }
            }
            if let message = vault.messageKey {
                Label(LocalizedStringKey(message), systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            DisclosureGroup("vault.limitsTitle") {
                Text("vault.limits").font(.footnote).foregroundStyle(.secondary)
                Text("vault.writeDown").font(.footnote).foregroundStyle(.secondary)
            }
        } header: {
            Text("vault.title")
        }
        .onAppear { vault.reload() }
        .onReceive(Self.ticker) { _ in vault.tick() }
        .onChange(of: days) { _ in understands = false }
        // "Until my commitment ends" disappears with the commitment; never keep a choice that is not shown.
        .onChange(of: createPlan == nil) { gone in
            if gone, days == VaultCommitmentDates.pickerTag { days = 7 }
        }
        .alert("vault.confirmTitle", isPresented: $confirmCreate) {
            Button("vault.create") { create() }
            Button("cancel", role: .cancel) { }
        } message: { createMessage }
        .alert("vault.extendTitle", isPresented: $confirmExtend) {
            Button("vault.extend") { vault.extend(days: 7) }
            Button("cancel", role: .cancel) { }
        } message: { Text("vault.extendBody") }
        .alert("vault.matchTitle", isPresented: $confirmMatch) {
            Button("vault.matchButton") {
                vault.tick()
                if let plan = extendPlan { VaultCommitmentDates.apply(plan, to: vault) }
            }
            Button("cancel", role: .cancel) { }
        } message: {
            if let plan = extendPlan {
                Text("vault.matchBody") + Text(verbatim: " ") + dateText(plan.until)
            } else {
                Text("vault.matchBody")
            }
        }
    }

    private var commitmentEnd: Date? {
        protection.commitmentActive ? protection.commitment?.deadline : nil
    }

    private var plan: VaultCommitmentDates.Plan? {
        VaultCommitmentDates.plan(vault: vault.vault, commitmentEnd: commitmentEnd, now: vault.now)
    }
    private var createPlan: VaultCommitmentDates.Plan? {
        guard vault.vault == nil, let plan, plan.createDays != nil else { return nil }
        return plan
    }
    private var extendPlan: VaultCommitmentDates.Plan? {
        guard vault.vault != nil, let plan, plan.createDays == nil else { return nil }
        return plan
    }

    @ViewBuilder private var createControls: some View {
        if let end = commitmentEnd {
            LabeledContent("dates.commitmentUntil") { dateText(end) }
        }
        Picker("vault.duration", selection: $days) {
            if createPlan != nil {
                Text("vault.match").tag(VaultCommitmentDates.pickerTag)
            }
            Text("commitment.oneDay").tag(1)
            Text("commitment.week").tag(7)
            Text("commitment.month").tag(30)
            Text("commitment.quarter").tag(90)
        }
        if days == VaultCommitmentDates.pickerTag, let plan = createPlan {
            LabeledContent("vault.sealedUntil") { dateText(plan.until) }
                .font(.footnote).foregroundStyle(.secondary)
        }
        Toggle("vault.acknowledge", isOn: $understands)
        Button("vault.create") { confirmCreate = true }
            .disabled(vault.busy || !understands
                      || (days == VaultCommitmentDates.pickerTag && createPlan == nil))
    }

    private var createMessage: Text {
        if days == VaultCommitmentDates.pickerTag, let plan = createPlan {
            return Text("vault.matchCreateBody") + Text(verbatim: " ") + dateText(plan.until)
        }
        return Text("vault.confirmBody \(days)")
    }

    private func create() {
        if days == VaultCommitmentDates.pickerTag {
            // The plan is worked out again from the vault's own clock, the one `create` uses.
            vault.tick()
            if let plan = createPlan { VaultCommitmentDates.apply(plan, to: vault) }
        } else {
            vault.create(days: days)
        }
    }

    /// The commitment's end beside the vault's date, and the offer to keep the passcode
    /// until the commitment ends when it would open first.
    @ViewBuilder private var commitmentDates: some View {
        if let end = commitmentEnd {
            LabeledContent("dates.commitmentUntil") { dateText(end) }
            if let plan = extendPlan {
                Label("dates.vaultEarlierHere", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
                LabeledContent("vault.matchUntil") { dateText(plan.until) }
                    .font(.footnote).foregroundStyle(.secondary)
                Button("vault.matchButton") { confirmMatch = true }.disabled(vault.busy)
            }
            Text("dates.neverEarlier").font(.footnote).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder private func setupSteps(passcode: String, hidesAt: Date) -> some View {
        passcodeView(passcode)
        Text("vault.steps").font(.callout)
        Text("vault.moreLocks").font(.callout)
        Label("vault.writeDown", systemImage: "square.and.pencil")
            .font(.footnote).foregroundStyle(.secondary)
        Link("vault.appleGuide", destination: URL(string: "https://support.apple.com/en-gb/105121")!)
        Text("vault.hidesSoon").font(.footnote).foregroundStyle(.secondary)
        Button("vault.seal") { vault.seal() }
            .buttonStyle(.borderedProminent)
    }

    private func dateText(_ date: Date) -> Text {
        Text(date, format: .dateTime.day().month().year().hour().minute())
    }

    /// Scales with Dynamic Type. Leaving the active state (the app switcher takes its snapshot
    /// then) replaces the digits with dots, so the passcode is not kept in the snapshot.
    private func passcodeView(_ passcode: String) -> some View {
        let active = scenePhase == .active
        return Text(verbatim: PasscodeVaultPolicy.displayed(passcode, sceneActive: active))
            .font(.system(size: passcodeSize, weight: .semibold, design: .monospaced))
            .tracking(passcodeTracking)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .frame(maxWidth: .infinity)
            .environment(\.layoutDirection, .leftToRight)
            .accessibilityLabel(active ? Text(verbatim: passcode.map(String.init).joined(separator: " "))
                                       : Text("vault.hidden"))
            .textSelection(.disabled)
    }
}

extension PasscodeVaultController {
    /// What readiness may know about the vault: its dates, never its passcode.
    var facts: MobileProtectionPolicy.VaultFacts? {
        vault.map { .init(createdAt: $0.createdAt, revealAt: $0.revealAt, revealed: $0.revealedAt != nil) }
    }
}

/// Lines the passcode vault's date up with a running commitment using only the vault's own
/// steps: a new passcode lasts 1, 7, 30 or 90 days, and an extension adds 7 or 30. Neither
/// date ever moves earlier, so the plan is the first date Hisn can set that is not before the
/// commitment's end. A vault that already lasts as long gets no plan.
enum VaultCommitmentDates {
    /// The duration picker's tag for "until my commitment ends"; never a real duration.
    static let pickerTag = 0

    struct Plan: Equatable {
        /// Days for a new passcode; nil when Hisn already keeps one.
        let createDays: Int?
        /// 30- and 7-day extensions, applied in order.
        let extensions: [Int]
        /// The vault's date afterwards: never before the commitment's end.
        let until: Date
    }

    static func plan(vault: PasscodeVault?, commitmentEnd: Date?, now: Date) -> Plan? {
        guard let end = commitmentEnd, end.timeIntervalSinceReferenceDate.isFinite,
              now.timeIntervalSinceReferenceDate.isFinite, end > now else { return nil }
        if let vault {
            // A shown passcode is the person's again; a later date is never shortened.
            guard vault.revealedAt == nil, vault.revealAt < end else { return nil }
            let used = vault.revealAt.timeIntervalSince(vault.createdAt)
            guard let steps = extensions(covering: end.timeIntervalSince(vault.revealAt),
                                         room: PasscodeVaultPolicy.maximumSeal - used) else { return nil }
            return Plan(createDays: nil, extensions: steps,
                        until: vault.revealAt.addingTimeInterval(seconds(steps)))
        }
        var best: Plan?
        for createDays in PasscodeVaultPolicy.durations {
            let length = Double(createDays) * 86400
            let base = now.addingTimeInterval(length)
            guard let steps = extensions(covering: end.timeIntervalSince(base),
                                         room: PasscodeVaultPolicy.maximumSeal - length) else { continue }
            let candidate = Plan(createDays: createDays, extensions: steps, until: base.addingTimeInterval(seconds(steps)))
            if let current = best,
               (current.until, current.extensions.count) <= (candidate.until, candidate.extensions.count) { continue }
            best = candidate
        }
        return best
    }

    /// The fewest whole days of 30- and 7-day steps that cover `gap` seconds without the vault
    /// passing its longest allowed seal (`room` seconds left), then the fewest steps.
    static func extensions(covering gap: TimeInterval, room: TimeInterval) -> [Int]? {
        guard gap.isFinite, room.isFinite else { return nil }
        if gap <= 0 { return [] }
        guard gap <= room else { return nil }
        let needed = Int((gap / 86400).rounded(.up))
        var best: [Int]?
        for thirties in 0...(needed / 30 + 1) {
            let sevens = (max(0, needed - thirties * 30) + 6) / 7
            let steps = Array(repeating: 30, count: thirties) + Array(repeating: 7, count: sevens)
            guard seconds(steps) <= room, seconds(steps) >= gap else { continue }
            if let current = best,
               (seconds(current), current.count) <= (seconds(steps), steps.count) { continue }
            best = steps
        }
        return best
    }

    private static func seconds(_ steps: [Int]) -> TimeInterval { Double(steps.reduce(0, +)) * 86400 }

    /// Applies a plan through the vault's own create and extend, stopping at the first refusal.
    /// A partial result only ever leaves the date later than before, never earlier.
    @MainActor static func apply(_ plan: Plan, to vault: PasscodeVaultController) {
        if let days = plan.createDays {
            guard vault.vault == nil else { return }
            vault.create(days: days)
            guard vault.vault != nil, vault.messageKey == nil else { return }
        }
        for days in plan.extensions {
            vault.extend(days: days)
            guard vault.messageKey == nil else { return }
        }
    }
}
