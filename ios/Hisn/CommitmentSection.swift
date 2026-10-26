import SwiftUI

struct CommitmentSection: View {
    @ObservedObject var protection: ProtectionController
    @State private var days = 7
    @State private var understandsLimits = false
    private enum Confirmation { case commitment(Int), screenTime, extend, alerts }
    @State private var confirmation: Confirmation?

    var body: some View {
        Section("commitment.title") {
            if protection.clockAssessment != .consistent {
                Label("commitment.clockUncertain", systemImage: "clock.badge.exclamationmark")
                    .foregroundStyle(HisnPalette.warning)
            }
            if protection.commitmentActive, let session = protection.commitment {
                Label("commitment.active", systemImage: "lock.fill")
                LabeledContent("commitment.until") {
                    Text(session.deadline, format: .dateTime.day().month().year().hour().minute())
                }
                endCheck
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
            }
            if !protection.commitmentStorageHealthy {
                Label("commitment.storageFailed", systemImage: "exclamationmark.triangle")
            }
            Text("commitment.shortScope").font(.footnote).foregroundStyle(.secondary)
            if !protection.hasBlockingConfiguration {
                Label("commitment.noLayer", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            Label(protection.screenTimeConfigured ? "screentime.configured" : "screentime.notConfigured",
                  systemImage: "shield.lefthalf.filled")
            // iOS drops every Hisn setting when access is turned off; say so rather than "not confirmed".
            if protection.screenTimeState == .accessRemoved {
                Label(protection.commitmentActive ? "commitment.screenTimeLost" : "screentime.accessRemovedBody",
                      systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            Button("screentime.enable") { confirmation = .screenTime }.disabled(protection.busy)
            if let error = protection.errorKey, error.hasPrefix("screentime.") {
                Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            if protection.screenTimeConfigured && !protection.commitmentActive {
                Button("screentime.disable") { protection.disablePersonalScreenTime() }
                    .disabled(protection.busy || !protection.commitmentStorageHealthy)
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
                Button("commitment.start") { protection.startCommitment(days: selected) }
            case .screenTime:
                Button("screentime.enable") { Task { await protection.enablePersonalScreenTime() } }
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
            case .screenTime: Text("screentime.consentBody")
            case .extend:
                Text("commitment.extendBody")
                if let next = protection.commitment?.extending(by: 7 * 86400) {
                    Text(next.deadline, format: .dateTime.day().month().year().hour().minute())
                }
            }
        }
    }

    private var confirmationTitle: LocalizedStringKey {
        if case .screenTime = confirmation { return "screentime.consentTitle" }
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
