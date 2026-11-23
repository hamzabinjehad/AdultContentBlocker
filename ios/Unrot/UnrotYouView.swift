import SwiftUI

/// You: the gardener. Your reason, your before, your ideas, the lock that keeps
/// Unrot on, language, and everything else (Hisn's full settings, named Unrot).
struct UnrotYouView: View {
    @ObservedObject var store: UnrotStore
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice
    @Binding var language: String
    let onTool: (UnrotTool) -> Void

    @State private var editingBefore = false
    @State private var confirmReplay = false
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                UTitleBar(title: "u.tab.you") { EmptyView() }
                profile
                yourPlan
                keepOn
                settings
                Text("u.you.privacy").font(.caption).foregroundStyle(UPalette.dim)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 120)
            .frame(maxWidth: sizeClass == .regular ? 560 : .infinity)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .sheet(isPresented: $editingBefore) { BeforeEditor(store: store) }
        .alert("u.you.replayTitle", isPresented: $confirmReplay) {
            Button("u.you.replay") { store.replayOnboarding() }
            Button("cancel", role: .cancel) {}
        } message: { Text("u.you.replayBody") }
    }

    private var profile: some View {
        UCard {
            HStack(spacing: 14) {
                Text(verbatim: String(store.answers.name.first.map(String.init) ?? "✿"))
                    .font(.uDisplay(28)).foregroundStyle(UPalette.onGold)
                    .frame(width: 60, height: 60)
                    .background(RadialGradient(colors: [Color(red: 1, green: 0.89, blue: 0.6), Color(red: 0.788, green: 0.553, blue: 0.133)],
                                               center: UnitPoint(x: 0.4, y: 0.35), startRadius: 0, endRadius: 40), in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    (store.answers.name.isEmpty ? Text("u.you.gardener") : Text(verbatim: store.answers.name))
                        .font(.uDisplay(22)).foregroundStyle(UPalette.ink)
                    Text("u.you.since \(store.day()) \(UnrotFormat.short(minutes: store.givenBackMinutes))")
                        .font(.footnote).foregroundStyle(UPalette.dim)
                }
            }
            if !store.answers.reason.isEmpty {
                Text(verbatim: "“\(store.answers.reason)”").font(.uDisplay(.body)).foregroundStyle(UPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var yourPlan: some View {
        UCard {
            ULabel("u.you.plan")
            row("hourglass", "u.you.before", value: UnrotFormat.short(minutes: store.answers.beforeMinutes)) { editingBefore = true }
            Divider().overlay(UPalette.cardLine)
            row("book.closed", "u.tool.notes", value: "\(store.notes.count)") { onTool(.notes) }
            Divider().overlay(UPalette.cardLine)
            row("lightbulb", "u.tool.instead", value: "\(store.answers.instead.count)") { onTool(.instead) }
            Divider().overlay(UPalette.cardLine)
            row("slider.horizontal.3", "u.you.rules", value: nil) { onTool(.rules) }
        }
    }

    private var keepOn: some View {
        UCard {
            ULabel("u.you.keepOn")
            NavigationLink { UnrotLockView(protection: protection, vault: vault, lockDevice: lockDevice) } label: {
                rowLabel("lock.shield", "u.lock.row", value: nil)
            }
            Divider().overlay(UPalette.cardLine)
            NavigationLink { CommitmentSetupView(protection: protection, usage: usage, vault: vault) } label: {
                rowLabel("lock", "u.you.commitment", value: protection.commitmentActive ? "u.pane.on" : nil)
            }
            Divider().overlay(UPalette.cardLine)
            NavigationLink { VaultSetupView(protection: protection, vault: vault, lockDevice: lockDevice) } label: {
                rowLabel("key", "u.you.vault", value: nil)
            }
            Divider().overlay(UPalette.cardLine)
            NavigationLink { BlockingLayersSetupView(protection: protection) } label: {
                rowLabel("checkmark.shield", "u.you.layers", value: nil)
            }
        }
    }

    private var settings: some View {
        UCard {
            ULabel("u.you.settings")
            HStack {
                Image(systemName: "globe").foregroundStyle(UPalette.gold).frame(width: 28)
                Text("u.you.language").foregroundStyle(UPalette.ink)
                Spacer()
                Picker("u.you.language", selection: $language) {
                    Text("language.system").tag("system")
                    Text(verbatim: "English").tag("en")
                    Text(verbatim: "العربية").tag("ar")
                }
                .tint(UPalette.goldLight)
            }
            .frame(minHeight: 44)
            Divider().overlay(UPalette.cardLine)
            NavigationLink {
                MoreView(protection: protection, usage: usage, vault: vault, lockDevice: lockDevice, language: $language)
            } label: {
                rowLabel("ellipsis.circle", "u.you.everything", value: nil)
            }
            Divider().overlay(UPalette.cardLine)
            row("arrow.counterclockwise", "u.you.replay", value: nil) { confirmReplay = true }
        }
    }

    private func row(_ icon: String, _ title: LocalizedStringKey, value: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) { rowLabel(icon, title, value: value.map { LocalizedStringKey($0) }, verbatim: value) }
            .buttonStyle(.plain)
    }

    private func rowLabel(_ icon: String, _ title: LocalizedStringKey, value: LocalizedStringKey?,
                          verbatim: String? = nil) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundStyle(UPalette.gold).frame(width: 28)
            Text(title).foregroundStyle(UPalette.ink)
            Spacer()
            if let verbatim { Text(verbatim: verbatim).foregroundStyle(UPalette.dim) }
            else if let value { Text(value).foregroundStyle(UPalette.leaf) }
            Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(UPalette.dim)
        }
        .font(.subheadline)
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }
}

/// The person's own "before": what a usual day in feeds was.
struct BeforeEditor: View {
    @ObservedObject var store: UnrotStore
    @Environment(\.dismiss) private var dismiss
    @State private var minutes = 120

    var body: some View {
        USheet(title: "u.you.before", subtitle: "u.before.body") {
            Text(verbatim: UnrotFormat.short(minutes: minutes)).font(.uDisplay(44)).foregroundStyle(UPalette.ink)
                .frame(maxWidth: .infinity)
            Slider(value: Binding(get: { Double(minutes) }, set: { minutes = Int($0) }), in: 15...600, step: 15)
                .tint(UPalette.gold)
                .accessibilityValue(Text(verbatim: UnrotFormat.short(minutes: minutes)))
            UGoldButton(title: "u.save", wide: true) {
                store.update { $0.beforeMinutes = minutes }
                dismiss()
            }
        }
        .onAppear { minutes = max(15, store.answers.beforeMinutes) }
    }
}
