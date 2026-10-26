import SwiftUI

/// The commitment locks only what is already on, so Setup lists it after the blocking layers
/// and before the passcode vault.
struct CommitmentSection: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var vault: PasscodeVaultController
    @State private var days = 7
    @State private var understandsLimits = false
    private enum Confirmation { case commitment(Int), extend, alerts }
    @State private var confirmation: Confirmation?
    /// The second, separate confirmation when no blocking layer is on.
    @State private var nothingLockedDays: Int?

    var body: some View {
        Section("commitment.title") {
            if protection.clockAssessment != .consistent {
                Label("commitment.clockUncertain", systemImage: "clock.badge.exclamationmark")
                    .foregroundStyle(HisnPalette.warning)
            }
            if protection.commitmentActive, let session = protection.commitment {
                Label("commitment.active", systemImage: "lock.fill")
                LabeledContent("commitment.until") { dateText(session.deadline) }
                endCheck
                vaultDates(commitmentEnd: session.deadline)
                Text("commitment.noEarlyRelease").font(.footnote).foregroundStyle(.secondary)
                Button("commitment.extendWeek") { confirmation = .extend }
                    .disabled(protection.busy || !protection.commitmentStorageHealthy
                              || session.extending(by: 7 * 86400) == nil)
            } else {
                Picker("commitment.duration", selection: $days) {
                    Text("commitment.oneDay").tag(1)
                    Text("commitment.week").tag(7)
                    Text("commitment.month").tag(30)
                    Text("commitment.quarter").tag(90)
                }
                Toggle("commitment.acknowledge", isOn: $understandsLimits)
                Button("commitment.start") { confirmation = .commitment(days) }
                    .disabled(protection.busy || !protection.commitmentStorageHealthy || !understandsLimits)
                if let note = protection.commitmentStartNote { Label(LocalizedStringKey(note), systemImage: "clock.badge.exclamationmark").font(.footnote).foregroundStyle(HisnPalette.warning) }
            }
            if !protection.commitmentStorageHealthy {
                Label("commitment.storageFailed", systemImage: "exclamationmark.triangle")
            }
            Text("commitment.shortScopeGuided").font(.footnote).foregroundStyle(.secondary)
            if !protection.hasBlockingConfiguration {
                Label("commitment.noLayer", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            // iOS drops every Hisn setting when access is turned off; say so rather than "not confirmed".
            if protection.commitmentActive, protection.screenTimeState == .accessRemoved {
                Label("commitment.screenTimeLostSetup", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            layerAlerts
            DisclosureGroup("commitment.details") {
                Text("commitment.scope").font(.footnote).foregroundStyle(.secondary)
                Text("commitment.trustedTime").font(.footnote).foregroundStyle(.secondary)
                Text("screentime.scope").font(.footnote).foregroundStyle(.secondary)
                Text("alerts.limits").font(.footnote).foregroundStyle(.secondary)
            }
        }
        .onChange(of: days) { _ in understandsLimits = false }
        .onChange(of: protection.commitmentActive) { _ in understandsLimits = false }
        .alert(confirmationTitle,
               isPresented: Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } }),
               presenting: confirmation) { action in
            switch action {
            case .commitment(let selected):
                Button("commitment.start") {
                    switch CommitmentStartFlow.afterFirstConfirmation(hasBlockingLayer: protection.hasBlockingConfiguration) {
                    case .start: Task { await protection.startCommitment(days: selected) }
                    case .confirmNothingLocked: nothingLockedDays = selected
                    }
                }
            case .extend:
                Button("commitment.extendWeek") { protection.extendCommitment(days: 7) }
            case .alerts:
                Button("alerts.enable") { Task { await protection.enableLayerAlerts() } }
            }
            Button("cancel", role: .cancel) { }
        } message: { action in
            switch action {
            case .commitment(let selected): commitmentMessage(selected)
            case .alerts: Text("alerts.consentBody")
            case .extend:
                if let next = protection.commitment?.extending(by: 7 * 86400) {
                    Text("commitment.extendBody") + Text(verbatim: " ") + dateText(next.deadline)
                } else {
                    Text("commitment.extendBody")
                }
            }
        }
        .alert("commitment.empty.title",
               isPresented: Binding(get: { nothingLockedDays != nil }, set: { if !$0 { nothingLockedDays = nil } }),
               presenting: nothingLockedDays) { selected in
            Button("commitment.empty.start", role: .destructive) { Task { await protection.startCommitment(days: selected) } }
            Button("commitment.empty.cancel", role: .cancel) { }
        } message: { selected in
            Text("commitment.empty.body \(selected)")
        }
    }

    /// The passcode vault's date beside the commitment's, so the person sees which opens first.
    @ViewBuilder private func vaultDates(commitmentEnd: Date) -> some View {
        if let facts = vault.facts, !facts.revealed {
            LabeledContent("dates.vaultUntil") { dateText(facts.revealAt) }
            if facts.revealAt < commitmentEnd {
                Label("dates.vaultEarlier", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
        }
    }

    private func dateText(_ date: Date) -> Text {
        Text(date, format: .dateTime.day().month().year().hour().minute())
    }

    private var confirmationTitle: LocalizedStringKey {
        if case .extend = confirmation { return "commitment.extendTitle" }
        if case .alerts = confirmation { return "alerts.consentTitle" }
        return "commitment.confirmTitle"
    }

    /// One text, so an alert shows every part. The Screen Time choice recorded with the
    /// commitment is stated before the person confirms it.
    private func commitmentMessage(_ days: Int) -> Text {
        var message = Text("commitment.confirmBody \(days)")
        if !protection.hasBlockingConfiguration {
            message = message + Text(verbatim: "\n\n") + Text("commitment.noLayer")
        }
        if protection.screenTimeConfigured {
            message = message + Text(verbatim: "\n\n") + Text("commitment.restoreConsent")
        }
        return message
    }

    /// The end has passed on this iPhone but not (yet) on Apple's time: still committed, and why.
    @ViewBuilder private var endCheck: some View {
        switch protection.commitmentTimeCheck {
        case .unverified?:
            Label("commitment.endUnverified", systemImage: "wifi.exclamationmark")
                .font(.footnote).foregroundStyle(HisnPalette.warning)
            if protection.checkingServerTime {
                ProgressView("commitment.checkingTime")
            } else {
                Button("commitment.checkTime") { Task { await protection.checkServerTime() } }
            }
        case .clockAhead?:
            Label("commitment.clockAhead", systemImage: "clock.badge.exclamationmark")
                .font(.footnote).foregroundStyle(HisnPalette.warning)
        default:
            EmptyView()
        }
    }

    /// Opt-in notices, asked for from the app only, with the explanation shown first.
    @ViewBuilder private var layerAlerts: some View {
        switch protection.layerAlerts {
        case .off:
            Button("alerts.enable") { confirmation = .alerts }.disabled(protection.busy)
        case .unchecked:
            EmptyView()
        case .on:
            Label("alerts.on", systemImage: "bell.badge")
            Button("alerts.disable") { protection.disableLayerAlerts() }.disabled(protection.busy)
        case .notAllowed, .notScheduled:
            Label(protection.layerAlerts == .notAllowed ? "alerts.notAllowed" : "alerts.notScheduled",
                  systemImage: "bell.slash")
                .font(.footnote).foregroundStyle(HisnPalette.warning)
            Button("alerts.disable") { protection.disableLayerAlerts() }.disabled(protection.busy)
        }
    }
}

/// Starting a commitment takes one confirmation, or two when no blocking layer is on: the
/// second says plainly that the commitment would lock nothing yet.
enum CommitmentStartFlow {
    enum Step: Equatable { case start, confirmNothingLocked }

    static func afterFirstConfirmation(hasBlockingLayer: Bool) -> Step {
        hasBlockingLayer ? .start : .confirmNothingLocked
    }

    static func confirmationsNeeded(hasBlockingLayer: Bool) -> Int {
        afterFirstConfirmation(hasBlockingLayer: hasBlockingLayer) == .start ? 1 : 2
    }
}

/// The Screen Time web filter, a blocking layer. Setup lists it before the commitment.
struct ScreenTimeSection: View {
    @ObservedObject var protection: ProtectionController
    @State private var confirmEnable = false

    var body: some View {
        Section("screentime.layer") {
            Label(protection.screenTimeConfigured ? "screentime.configured" : "screentime.notConfigured",
                  systemImage: "shield.lefthalf.filled")
            // iOS drops every Hisn setting when access is turned off; say so rather than "not confirmed".
            if protection.screenTimeState == .accessRemoved {
                Label(protection.commitmentActive ? "commitment.screenTimeLost" : "screentime.accessRemovedBody",
                      systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            Button("screentime.enable") { confirmEnable = true }.disabled(protection.busy)
            if let error = protection.errorKey, error.hasPrefix("screentime.") {
                Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            if protection.screenTimeConfigured && !protection.commitmentActive {
                Button("screentime.disable") { protection.disablePersonalScreenTime() }
                    .disabled(protection.busy || !protection.commitmentStorageHealthy)
            }
            Text("screentime.scope").font(.footnote).foregroundStyle(.secondary)
        }
        .alert("screentime.consentTitle", isPresented: $confirmEnable) {
            Button("screentime.enable") { Task { await protection.enablePersonalScreenTime() } }
            Button("cancel", role: .cancel) { }
        } message: {
            Text("screentime.consentBody")
        }
    }
}
