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
    var onHome: () -> Void = {}

    @State private var editingBefore = false
    @AppStorage(ReminderScheduler.enabledKey) private var remindersOn = false
    @State private var remindersDenied = false
    @State private var showingHelp = false
    @State private var editingReason = false
    @State private var editingIdeas = false
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
        .sheet(isPresented: $showingHelp) { UnrotHelpSheet() }
        .sheet(isPresented: $editingReason) { ReasonEditor(store: store) }
        .sheet(isPresented: $editingIdeas) { InsteadEditor(store: store) }
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
            row("lightbulb", "u.you.ideas", value: "\(store.answers.instead.count)") { editingIdeas = true }
            Divider().overlay(UPalette.cardLine)
            row("quote.opening", "u.you.reason", value: nil) { editingReason = true }
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
            Toggle(isOn: Binding(get: { remindersOn }, set: { setReminders($0) })) {
                HStack(spacing: 12) {
                    Image(systemName: "bell").foregroundStyle(UPalette.gold).frame(width: 28)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("u.rem.title").foregroundStyle(UPalette.ink)
                        Text("u.rem.detail").font(.caption).foregroundStyle(UPalette.dim)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .tint(UPalette.gold)
            .font(.subheadline)
            if remindersDenied {
                Text("u.rem.denied").font(.caption).foregroundStyle(UPalette.warn)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Divider().overlay(UPalette.cardLine)
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
            row("questionmark.circle", "u.help.title", value: nil) { showingHelp = true }
            Divider().overlay(UPalette.cardLine)
            row("sparkles", "u.you.tour", value: nil) { onHome(); store.replayTour() }
            Divider().overlay(UPalette.cardLine)
            row("arrow.counterclockwise", "u.you.replay", value: nil) { confirmReplay = true }
        }
    }

    private func setReminders(_ on: Bool) {
        Task { @MainActor in
            if on {
                guard await ReminderScheduler.requestPermission() else { remindersDenied = true; return }
                remindersDenied = false
            }
            remindersOn = on
            await ReminderScheduler.reschedule(on: on, when: store.answers.when)
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

/// Your reason, in your own words: shown on Home, in Breathe and on the pledge.
struct ReasonEditor: View {
    @ObservedObject var store: UnrotStore
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""

    var body: some View {
        USheet(title: "u.you.reason", subtitle: "u.ob.reason.sub") {
            TextField("u.ob.reason.placeholder", text: $text, axis: .vertical)
                .lineLimit(3...6)
                .font(.uDisplay(20)).foregroundStyle(UPalette.ink)
                .padding(16)
                .background(UPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay { RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(UPalette.gold.opacity(0.7)) }
            UGoldButton(title: "u.save", wide: true) {
                store.update { $0.reason = text.trimmingCharacters(in: .whitespacesAndNewlines) }
                dismiss()
            }
        }
        .onAppear { text = store.answers.reason }
    }
}

/// Which Instead ideas show on the Instead sheet.
struct InsteadEditor: View {
    @ObservedObject var store: UnrotStore
    @Environment(\.dismiss) private var dismiss
    @State private var picked: [String] = []

    var body: some View {
        USheet(title: "u.you.ideas", subtitle: "u.ob.instead.sub", tall: true) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(UnrotInsteadSheet.ideas, id: \.0) { idea in
                    UOption(title: idea.2, systemImage: idea.1, selected: picked.contains(idea.0), multiple: true) {
                        if let i = picked.firstIndex(of: idea.0) { picked.remove(at: i) } else { picked.append(idea.0) }
                    }
                }
            }
            UGoldButton(title: "u.save", wide: true) {
                store.update { $0.instead = picked }
                dismiss()
            }
        }
        .onAppear { picked = store.answers.instead }
    }
}

