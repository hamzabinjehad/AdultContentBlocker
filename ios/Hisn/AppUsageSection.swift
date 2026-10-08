import SwiftUI
import FamilyControls

struct AppUsageSection: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    @State private var selection = FamilyActivitySelection()
    @State private var mode = AppUsageConfiguration.Mode.always
    @State private var minutes = 30
    @State private var selecting = false
    @State private var confirm = false
    private var locked: Bool { protection.commitmentActive }
    var body: some View {
        Section("apps.title") {
            Label(LocalizedStringKey(usage.health.rawValue), systemImage: "shield.lefthalf.filled")
                .foregroundStyle(usage.health.needsAttention ? Color.orange : Color.secondary)
            Text("apps.scope").font(.footnote).foregroundStyle(.secondary)
            Text("apps.browserGuardGuidance").font(.footnote).foregroundStyle(.secondary)
            Text("apps.browserGuardLimits").font(.footnote).foregroundStyle(.secondary)
            if !usage.authorized { Text("apps.authorization").font(.footnote).foregroundStyle(.secondary) }
            Button("apps.choose") { selecting = true }.disabled(!protection.commitmentStorageHealthy || !usage.authorized)
            Text("apps.count \(selection.applicationTokens.count + selection.categoryTokens.count + selection.webDomainTokens.count)")
            Picker("apps.mode", selection: $mode) {
                Text("apps.always").tag(AppUsageConfiguration.Mode.always)
                Text("apps.budget").tag(AppUsageConfiguration.Mode.dailyBudget)
            }.disabled(!protection.commitmentStorageHealthy)
            if mode == .dailyBudget {
                Stepper("apps.minutes \(minutes)", value: $minutes, in: 15...240, step: 15)
                    .disabled(!protection.commitmentStorageHealthy)
                Text("apps.budgetScope").font(.footnote).foregroundStyle(.secondary)
            }
            Button("apps.apply") { confirm = true }.disabled(!protection.commitmentStorageHealthy || !usage.authorized)
            if locked { Text("apps.locked").font(.footnote).foregroundStyle(.secondary) }
            if !locked, usage.configuration != nil {
                Button("apps.clear") {
                    usage.clear(commitmentActive: protection.commitmentActive,
                                storageHealthy: protection.commitmentStorageHealthy)
                }.disabled(!protection.commitmentStorageHealthy)
            }
            if usage.registered && usage.authorized { Label("apps.registered", systemImage: "clock") }
            if let error = usage.errorKey { Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle") }
        }
        .familyActivityPicker(isPresented: $selecting, selection: $selection)
        .alert("apps.consent", isPresented: $confirm) {
            Button("apps.apply") {
                usage.apply(AppUsageConfiguration(selection: selection, mode: mode, minutes: minutes),
                    commitmentActive: locked, storageHealthy: protection.commitmentStorageHealthy)
            }
            Button("cancel", role: .cancel) { }
        } message: { Text("apps.consentBody") }
        .onAppear { usage.refresh(); if let c = usage.configuration { selection = c.selection; mode = c.mode; minutes = c.minutes } }
    }
}
