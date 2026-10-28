import SwiftUI

/// "Deletable only after a date" without a second person: Hisn keeps the
/// Screen Time passcode until the date the person chose.
struct PasscodeVaultSection: View {
    @ObservedObject var vault: PasscodeVaultController
    @ObservedObject var protection: ProtectionController
    let lockDevice: LockConfirmationDevice
    /// `VaultCommitmentDates.pickerTag` stands for "until my commitment ends".
    @State private var days = 7
    @State private var understands = false
    /// What a confirmation shows, saved as it opens; its button applies exactly this or nothing.
    @State private var pendingCreate: VaultConfirmation.Choice?
    @State private var pendingMatch: VaultCommitmentDates.Plan?
    @State private var confirmExtend = false
    /// Why the last confirmed choice changed nothing.
    @State private var refusal: VaultConfirmation.Refusal?
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
                LockConfirmationRows(claims: [.passcodeVault], vault: vault.facts, now: protection.now,
                                     device: lockDevice)
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
            if let refusal {
                Label(LocalizedStringKey(refusal.rawValue), systemImage: "exclamationmark.triangle")
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
        .onChange(of: days) { _ in understands = false; refusal = nil }
        // "Until my commitment ends" disappears with the commitment; never keep a choice that is not
        // shown. An open confirmation keeps its own saved choice, so this never changes what it applies.
        .onChange(of: createPlan == nil) { gone in
            if gone, days == VaultCommitmentDates.pickerTag { days = 7 }
        }
        .alert("vault.confirmTitle",
               isPresented: Binding(get: { pendingCreate != nil }, set: { if !$0 { pendingCreate = nil } }),
               presenting: pendingCreate) { choice in
            Button("vault.create") { confirm(choice) }
            Button("cancel", role: .cancel) { }
        } message: { choice in createMessage(choice) }
        .alert("vault.extendTitle", isPresented: $confirmExtend) {
            Button("vault.extend") { vault.extend(days: 7) }
            Button("cancel", role: .cancel) { }
        } message: { Text("vault.extendBody") }
        .alert("vault.matchTitle",
               isPresented: Binding(get: { pendingMatch != nil }, set: { if !$0 { pendingMatch = nil } }),
               presenting: pendingMatch) { plan in
            Button("vault.matchButton") { confirm(.plan(plan)) }
            Button("cancel", role: .cancel) { }
        } message: { plan in
            Text("vault.matchBody") + Text(verbatim: " ") + dateText(plan.until)
        }
    }

    private var commitmentEnd: Date? {
        protection.commitmentActive ? protection.commitment?.deadline : nil
    }

    private var createPlan: VaultCommitmentDates.Plan? {
        guard vault.vault == nil,
              let plan = VaultCommitmentDates.plan(vault: nil, commitmentEnd: commitmentEnd, now: vault.now),
              plan.createDays != nil else { return nil }
        return plan
    }
    /// The vault opens before the commitment ends, and what Hisn can do about it.
    private var earlier: VaultCommitmentDates.Earlier? {
        VaultCommitmentDates.earlier(vault: vault.vault, commitmentEnd: commitmentEnd, now: vault.now)
    }

    /// Saves the choice, with its date worked out from the vault's clock now, as the alert opens.
    private func offerCreate() {
        vault.tick()
        refusal = nil
        pendingCreate = VaultConfirmation.choice(days: days, plan: createPlan)
    }

    private func offerMatch() {
        vault.tick()
        refusal = nil
        if case .matchable(let plan)? = earlier { pendingMatch = plan }
    }

    /// Applies exactly what the alert showed, or nothing and says why.
    private func confirm(_ choice: VaultConfirmation.Choice) {
        refusal = VaultConfirmation.carryOut(choice, on: vault, commitmentEnd: commitmentEnd)
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
        Button("vault.create") { offerCreate() }
            .disabled(vault.busy || !understands
                      || (days == VaultCommitmentDates.pickerTag && createPlan == nil))
    }

    private func createMessage(_ choice: VaultConfirmation.Choice) -> Text {
        switch choice {
        case .plan(let plan): return Text("vault.matchCreateBody") + Text(verbatim: " ") + dateText(plan.until)
        case .days(let days): return Text("vault.confirmBody \(days)")
        }
    }

    /// The commitment's end beside the vault's date. Whenever the vault opens first this says so,
    /// then offers to keep the passcode until the commitment ends, or says why Hisn cannot.
    @ViewBuilder private var commitmentDates: some View {
        if let end = commitmentEnd {
            LabeledContent("dates.commitmentUntil") { dateText(end) }
            if let earlier {
                Label("dates.vaultEarlierHere", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
                switch earlier {
                case .matchable(let plan):
                    LabeledContent("vault.matchUntil") { dateText(plan.until) }
                        .font(.footnote).foregroundStyle(.secondary)
                    Button("vault.matchButton") { offerMatch() }.disabled(vault.busy)
                case .beyondLongestSeal:
                    Text("vault.cannotReach").font(.footnote).foregroundStyle(.secondary)
                case .endReached:
                    EmptyView()
                }
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
    var facts: MobileProtectionPolicy.VaultFacts? { vault.map(MobileProtectionPolicy.VaultFacts.init) }
}

extension MobileProtectionPolicy.VaultFacts {
    /// A vault not yet sealed hides itself when its setup window closes; until then it is on screen.
    init(_ vault: PasscodeVault) {
        self.init(createdAt: vault.createdAt, revealAt: vault.revealAt, revealed: vault.revealedAt != nil,
                  hidesAt: vault.sealed ? nil : vault.createdAt.addingTimeInterval(PasscodeVaultPolicy.setupWindow))
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

    static func seconds(_ steps: [Int]) -> TimeInterval { Double(steps.reduce(0, +)) * 86400 }

    /// The vault opens before a running commitment ends. Said whenever it is true, not only
    /// when Hisn has a fix to offer.
    enum Earlier: Equatable {
        /// Hisn can keep the passcode until the commitment ends with this plan.
        case matchable(Plan)
        /// The longest seal (366 days in all, 7 or 30 days at a time) stops short of the end.
        case beyondLongestSeal
        /// The end has passed on this iPhone's clock and waits on Apple's time: nothing to match.
        case endReached

        /// The commitment section's warning, which names the fix in Setup only when there is one.
        var commitmentKey: String {
            switch self {
            case .matchable: return "dates.vaultEarlier"
            case .beyondLongestSeal: return "dates.vaultEarlierBeyond"
            case .endReached: return "dates.vaultEarlierHere"
            }
        }
    }

    static func earlier(vault: PasscodeVault?, commitmentEnd: Date?, now: Date) -> Earlier? {
        guard let vault, vault.revealedAt == nil, let end = commitmentEnd, vault.revealAt < end else { return nil }
        if let plan = plan(vault: vault, commitmentEnd: end, now: now) { return .matchable(plan) }
        return end > now ? .beyondLongestSeal : .endReached
    }

    /// The label for the vault's date: hidden until it, or shown now while the setup window is open.
    static func vaultDateKey(_ facts: MobileProtectionPolicy.VaultFacts, now: Date) -> String {
        facts.inSetupWindow(at: now) ? "dates.vaultShownUntil" : "dates.vaultUntil"
    }

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

/// A vault's date can never be shortened, so its confirmation applies what it showed or nothing.
/// The choice is saved as the alert opens; while it is open the picker can reset (the commitment
/// ended) and a fresh plan can differ (the clock moved on, the commitment was extended), and
/// neither may change what the button does.
enum VaultConfirmation {
    enum Choice: Equatable {
        /// A fixed duration from the picker.
        case days(Int)
        /// "Until my commitment ends": a new passcode, or extensions to the one Hisn keeps.
        case plan(VaultCommitmentDates.Plan)
    }

    /// Why a confirmed choice changed nothing. Each raw value is its text key.
    enum Refusal: String, Equatable {
        case commitmentEnded = "vault.confirm.ended"
        case datesMoved = "vault.confirm.moved"
    }

    /// How much later than the date it showed a new passcode may land: time to read the alert.
    static let slack: TimeInterval = 5 * 60

    /// What the Create button's alert shows and applies. nil when "until my commitment ends" has no plan.
    static func choice(days: Int, plan: VaultCommitmentDates.Plan?) -> Choice? {
        guard days == VaultCommitmentDates.pickerTag else { return .days(days) }
        return plan.map(Choice.plan)
    }

    /// nil when the saved choice still lands where it said; otherwise why it must not be applied.
    static func refusal(_ choice: Choice, vault: PasscodeVault?, commitmentEnd: Date?, now: Date) -> Refusal? {
        guard case .plan(let plan) = choice else { return nil }
        guard let end = commitmentEnd, end > now else { return .commitmentEnded }
        let added = VaultCommitmentDates.seconds(plan.extensions)
        let lands: Date
        if let days = plan.createDays {
            guard vault == nil else { return .datesMoved }
            lands = now.addingTimeInterval(Double(days) * 86400 + added)
            guard lands >= plan.until, lands.timeIntervalSince(plan.until) <= slack else { return .datesMoved }
        } else {
            guard let vault, vault.revealedAt == nil else { return .datesMoved }
            lands = vault.revealAt.addingTimeInterval(added)
            guard abs(lands.timeIntervalSince(plan.until)) < 1,
                  lands.timeIntervalSince(vault.createdAt) <= PasscodeVaultPolicy.maximumSeal else { return .datesMoved }
        }
        return lands >= end ? nil : .datesMoved
    }

    /// What the alert's button does: re-reads the vault, then applies the saved choice exactly, or
    /// nothing and returns why.
    @MainActor static func carryOut(_ choice: Choice, on vault: PasscodeVaultController,
                                    commitmentEnd: Date?) -> Refusal? {
        vault.reload()
        if let refusal = refusal(choice, vault: vault.vault, commitmentEnd: commitmentEnd, now: vault.now) {
            return refusal
        }
        switch choice {
        case .days(let days): vault.create(days: days)
        case .plan(let plan): VaultCommitmentDates.apply(plan, to: vault)
        }
        return nil
    }
}
