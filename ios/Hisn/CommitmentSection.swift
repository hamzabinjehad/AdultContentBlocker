import SwiftUI

struct CommitmentSection: View {
    @ObservedObject var protection: ProtectionController
    @State private var days = 7
    @State private var understandsLimits = false
    private enum Confirmation { case commitment(Int), screenTime, extend }
    @State private var confirmation: Confirmation?

    var body: some View {
        Section("commitment.title") {
            if protection.clockAssessment != .consistent {
                Label("commitment.clockUncertain", systemImage: "clock.badge.exclamationmark")
                    .foregroundStyle(.orange)
            }
            if protection.commitmentActive, let session = protection.commitment {
                Label("commitment.active", systemImage: "lock.fill")
                LabeledContent("commitment.until") {
                    Text(session.deadline, format: .dateTime.day().month().year().hour().minute())
                }
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
                    .font(.footnote).foregroundStyle(.orange)
            }
            Label(protection.screenTimeConfigured ? "screentime.configured" : "screentime.notConfigured",
                  systemImage: "shield.lefthalf.filled")
            Button("screentime.enable") { confirmation = .screenTime }.disabled(protection.busy)
            if let error = protection.errorKey, error.hasPrefix("screentime.") {
                Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(.orange)
            }
            if protection.screenTimeConfigured && !protection.commitmentActive {
                Button("screentime.disable") { protection.disablePersonalScreenTime() }
                    .disabled(protection.busy || !protection.commitmentStorageHealthy)
            }
            DisclosureGroup("commitment.details") {
                Text("commitment.scope").font(.footnote).foregroundStyle(.secondary)
                Text("screentime.scope").font(.footnote).foregroundStyle(.secondary)
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
            }
            Button("cancel", role: .cancel) { }
        } message: { action in
            switch action {
            case .commitment(let selected):
                Text("commitment.confirmBody \(selected)")
                if !protection.hasBlockingConfiguration { Text("commitment.noLayer") }
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
        return "commitment.confirmTitle"
    }
}
