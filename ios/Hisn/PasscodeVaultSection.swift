import SwiftUI

/// "Deletable only after a date" without a second person: Hisn keeps the
/// Screen Time passcode until the date the person chose.
struct PasscodeVaultSection: View {
    @ObservedObject var vault: PasscodeVaultController
    @State private var days = 7
    @State private var understands = false
    @State private var confirmCreate = false
    @State private var confirmExtend = false
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo: .largeTitle) private var passcodeSize: CGFloat = 44
    @ScaledMetric(relativeTo: .largeTitle) private var passcodeTracking: CGFloat = 12

    var body: some View {
        Section {
            Text("vault.body").font(.callout)
            switch vault.phase {
            case nil:
                if vault.storageHealthy { createControls }
            case .setup(let passcode, let hidesAt)?:
                setupSteps(passcode: passcode, hidesAt: hidesAt)
            case .sealed(let until)?:
                Label("vault.sealed", systemImage: "lock.fill")
                LabeledContent("vault.sealedUntil") {
                    Text(until, format: .dateTime.day().month().year().hour().minute())
                }
                Button("vault.extend") { confirmExtend = true }.disabled(vault.busy)
            case .due(let since)?:
                Label("vault.due", systemImage: "lock.open")
                LabeledContent("vault.sealedUntil") {
                    Text(since, format: .dateTime.day().month().year().hour().minute())
                }
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
            }
        } header: {
            Text("vault.title")
        }
        .onAppear { vault.reload() }
        .onReceive(Timer.publish(every: 30, on: .main, in: .common).autoconnect()) { _ in vault.tick() }
        .onChange(of: days) { _ in understands = false }
        .alert("vault.confirmTitle", isPresented: $confirmCreate) {
            Button("vault.create") { vault.create(days: days) }
            Button("cancel", role: .cancel) { }
        } message: { Text("vault.confirmBody \(days)") }
        .alert("vault.extendTitle", isPresented: $confirmExtend) {
            Button("vault.extend") { vault.extend(days: 7) }
            Button("cancel", role: .cancel) { }
        } message: { Text("vault.extendBody") }
    }

    @ViewBuilder private var createControls: some View {
        Picker("vault.duration", selection: $days) {
            Text("commitment.oneDay").tag(1)
            Text("commitment.week").tag(7)
            Text("commitment.month").tag(30)
            Text("commitment.quarter").tag(90)
        }
        Toggle("vault.acknowledge", isOn: $understands)
        Button("vault.create") { confirmCreate = true }
            .disabled(vault.busy || !understands)
    }

    @ViewBuilder private func setupSteps(passcode: String, hidesAt: Date) -> some View {
        passcodeView(passcode)
        Text("vault.steps").font(.callout)
        Text("vault.hidesSoon").font(.footnote).foregroundStyle(.secondary)
        Button("vault.seal") { vault.seal() }
            .buttonStyle(.borderedProminent)
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
