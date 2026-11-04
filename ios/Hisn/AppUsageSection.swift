import SwiftUI
import FamilyControls

struct AppUsageSection: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    @State private var selection = FamilyActivitySelection()
    @State private var mode = AppUsageConfiguration.Mode.always
    @State private var minutes = 30
    @State private var windowStart = AppUsageConfiguration.defaultWindowStart
    @State private var windowEnd = AppUsageConfiguration.defaultWindowEnd
    @State private var selecting = false
    @State private var confirm = false

    /// `initialMode` lets the render tests show the quiet-hours editor without
    /// changing a saved rule.
    init(protection: ProtectionController, usage: AppUsageController,
         initialMode: AppUsageConfiguration.Mode = .always) {
        _protection = ObservedObject(wrappedValue: protection)
        _usage = ObservedObject(wrappedValue: usage)
        _mode = State(initialValue: initialMode)
    }

    private var locked: Bool { protection.rulesLocked }
    private var windowValid: Bool {
        mode != .quietHours
            || AppUsageConfiguration.isValidWindow(start: windowStart, end: windowEnd)
    }

    var body: some View {
        Section {
            Label(LocalizedStringKey(usage.health.rawValue), systemImage: "shield.lefthalf.filled")
                .foregroundStyle(usage.health.needsAttention ? HisnPalette.warning : Color.secondary)
            if !usage.authorized { Text("apps.authorization").font(.footnote).foregroundStyle(.secondary) }

            // Choose the objects first; the rule is a second decision.
            Button("apps.choose") { selecting = true }
                .disabled(locked || !protection.commitmentStorageHealthy || !usage.authorized)
            Text("apps.count \(selection.applicationTokens.count + selection.categoryTokens.count + selection.webDomainTokens.count)")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .monospacedDigit()

            Text("apps.mode")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.top, 8)
            modeSelector

            if mode == .dailyBudget {
                Stepper("apps.minutes \(minutes)", value: $minutes, in: 15...240, step: 15)
                    .disabled(locked || !protection.commitmentStorageHealthy)
                Text("apps.budgetScope").font(.footnote).foregroundStyle(.secondary)
            }
            if mode == .quietHours {
                windowEditor
            }
            Button("apps.apply") { confirm = true }
                .disabled(locked || !protection.commitmentStorageHealthy || !usage.authorized || !windowValid)
            if locked { Text("apps.locked").font(.footnote).foregroundStyle(.secondary) }
            if !locked, usage.configuration != nil {
                Button("apps.clear") {
                    usage.clear(rulesLocked: locked,
                                storageHealthy: protection.commitmentStorageHealthy)
                }.disabled(locked || !protection.commitmentStorageHealthy)
            }
            if usage.registered && usage.authorized { Label("apps.registered", systemImage: "clock") }
            if let error = usage.errorKey {
                Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
            }
        } header: {
            Text("apps.title")
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                Text("apps.scope")
                DisclosureGroup("apps.details") {
                    Text("apps.browserGuardGuidance")
                    Text("apps.browserGuardLimits")
                }
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
        .familyActivityPicker(isPresented: $selecting, selection: $selection)
        .alert("apps.consent", isPresented: $confirm) {
            Button("apps.apply") {
                usage.apply(AppUsageConfiguration(selection: selection, mode: mode, minutes: minutes,
                                                  windowStart: windowStart, windowEnd: windowEnd),
                            rulesLocked: locked, storageHealthy: protection.commitmentStorageHealthy,
                            trustedNow: protection.trustedNow)
            }
            Button("cancel", role: .cancel) { }
        } message: {
            if mode == .quietHours {
                Text("apps.consentBody") + Text(verbatim: "\n\n") + Text("apps.windowConsent")
            } else {
                Text("apps.consentBody")
            }
        }
        .onAppear {
            usage.refresh(trustedNow: protection.trustedNow)
            if let c = usage.configuration {
                selection = c.selection
                mode = c.mode
                minutes = c.minutes
                windowStart = c.windowStart
                windowEnd = c.windowEnd
            }
        }
    }

    /// Three rules, shown together so the choice is visible before it is made.
    /// The selected row carries a filled check; the others stay quiet.
    private var modeSelector: some View {
        VStack(spacing: 0) {
            ForEach(modes, id: \.self) { option in
                Button { mode = option } label: {
                    HStack(spacing: 12) {
                        modeIcon(option)
                            .frame(width: 24)
                            .foregroundStyle(mode == option ? Color.accentColor : Color.secondary)
                        Text(modeTitle(option))
                            .font(.subheadline)
                            .multilineTextAlignment(.leading)
                        Spacer(minLength: 8)
                        if mode == option {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Color.accentColor)
                                .accessibilityHidden(true)
                        }
                    }
                    .padding(.vertical, 7)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(locked || !protection.commitmentStorageHealthy)
            }
        }
    }

    private var modes: [AppUsageConfiguration.Mode] {
        [.always, .dailyBudget, .quietHours]
    }

    private func modeTitle(_ mode: AppUsageConfiguration.Mode) -> LocalizedStringKey {
        switch mode {
        case .always: return "apps.always"
        case .dailyBudget: return "apps.budget"
        case .quietHours: return "apps.quietHours"
        }
    }

    @ViewBuilder private func modeIcon(_ mode: AppUsageConfiguration.Mode) -> some View {
        switch mode {
        case .always:
            Image(systemName: "shield.lefthalf.filled")
        case .dailyBudget:
            Image(systemName: "hourglass")
        case .quietHours:
            Image(systemName: "moon.stars")
        }
    }

    @ViewBuilder private var windowEditor: some View {
        Menu("apps.windowPreset") {
            Button("apps.windowLateNight") {
                windowStart = 22 * 60
                windowEnd = 7 * 60
            }
            Button("apps.windowEvening") {
                windowStart = 20 * 60
                windowEnd = 23 * 60
            }
            Button("apps.windowWork") {
                windowStart = 9 * 60
                windowEnd = 17 * 60
            }
        }
        .disabled(locked || !protection.commitmentStorageHealthy)
        DatePicker("apps.windowStart", selection: timeBinding($windowStart),
                   displayedComponents: .hourAndMinute)
            .disabled(locked || !protection.commitmentStorageHealthy)
        DatePicker("apps.windowEnd", selection: timeBinding($windowEnd),
                   displayedComponents: .hourAndMinute)
            .disabled(locked || !protection.commitmentStorageHealthy)
        if windowValid {
            Text("apps.windowScope").font(.footnote).foregroundStyle(.secondary)
            if windowEnd < windowStart {
                Label("apps.windowOvernight", systemImage: "moon.stars")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        } else {
            Label("apps.windowInvalid", systemImage: "exclamationmark.triangle")
                .font(.footnote)
                .foregroundStyle(HisnPalette.warning)
        }
    }

    /// The picker works on a Date; the rule stores minutes after midnight, so a
    /// time-zone or calendar change cannot silently move the saved window.
    private func timeBinding(_ minutes: Binding<Int>) -> Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(bySettingHour: minutes.wrappedValue / 60,
                                      minute: minutes.wrappedValue % 60,
                                      second: 0, of: Date()) ?? Date()
            },
            set: { date in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                minutes.wrappedValue = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
            }
        )
    }
}
