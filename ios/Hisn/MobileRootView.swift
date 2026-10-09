import SwiftUI

struct MobileRootView: View {
    @ObservedObject var protection: ProtectionController
    @Binding var language: String
    private enum Confirmation { case dns, guardian }
    @State private var confirmation: Confirmation?
    @State private var selectedTab = 0
    @StateObject private var usage = AppUsageController()

    init(protection: ProtectionController, language: Binding<String>, initialTab: Int = 0) {
        self.protection = protection
        self._language = language
        self._selectedTab = State(initialValue: (0...2).contains(initialTab) ? initialTab : 0)
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack { overview.navigationTitle("app.name") }
                .tabItem { Label("tab.protection", systemImage: "shield.lefthalf.filled") }
                .tag(0)
            NavigationStack { setup.navigationTitle("tab.setup") }
                .tabItem { Label("tab.setup", systemImage: "checklist") }
                .tag(1)
            NavigationStack { settings.navigationTitle("tab.settings") }
                .tabItem { Label("tab.settings", systemImage: "gearshape") }
                .tag(2)
        }
        .alert(confirmation == .guardian ? "removal.consent.title" : "dns.consent.title",
               isPresented: Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } }),
               presenting: confirmation) { action in
            Button(action == .guardian ? "removal.request" : "dns.prepare") {
                Task {
                    if action == .guardian { await protection.requestGuardianProtection() }
                    else { await protection.installDNS() }
                }
            }
            Button("cancel", role: .cancel) { }
        } message: { action in
            Text(action == .guardian ? "removal.consent.body" : "dns.consent.body")
        }
        .onAppear { usage.refresh() }
        .onChange(of: protection.checkedAt) { _ in usage.refresh() }
    }

    private var overview: some View {
        List {
            verifiedSetup
            readiness
            CommitmentSection(protection: protection)
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Image(systemName: "shield.lefthalf.filled").font(.largeTitle).foregroundStyle(.teal)
                    Text("overview.title").font(.title2.bold())
                    Text("overview.body").foregroundStyle(.secondary)
                }.padding(.vertical, 12)
            }
            Section("status.title") {
                LabeledContent("dns.title") { Text(dnsLabel).foregroundStyle(.secondary) }
                LabeledContent("safari.title") {
                    Text("\(protection.safari.filter { $0 == true }.count) / 4").monospacedDigit()
                }
                Text(MobileProtectionPolicy.safariConfigurationReady(enabled: protection.safari,
                    lastReload: protection.safariReloads) ? "safari.enabled" : "safari.partial")
                    .font(.footnote).foregroundStyle(.secondary)
                Button("setup.continue") { selectedTab = 1 }
                if let date = protection.checkedAt {
                    LabeledContent("status.checked") { Text(date, style: .time) }
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Button("status.refresh") { Task { await protection.refresh() } }
                    .disabled(protection.busy)
                if protection.busy { ProgressView("working") }
            }
            safariParts
            removalSection
            Section("list.title") {
                LabeledContent("list.domains", value: protection.listCount.formatted())
                LabeledContent("list.version", value: String(protection.listVersion))
                Text("list.body").font(.footnote).foregroundStyle(.secondary)
            }
            errorRow
        }
        .refreshable { await protection.refresh() }
    }

    private var setup: some View {
        List {
            verifiedSetup
            readiness
            CommitmentSection(protection: protection)
            AppUsageSection(protection: protection, usage: usage)
            Section("safari.title") {
                Text("safari.steps")
                Button("safari.reload") { Task { await protection.reloadSafari() } }
                    .disabled(protection.busy || protection.listCount == 0)
                Text("safari.scope").font(.footnote).foregroundStyle(.secondary)
            }
            safariParts
            Section("dns.title") {
                Text("dns.steps")
                Button("dns.prepare") { confirmation = .dns }.disabled(protection.busy)
                Text("dns.scope").font(.footnote).foregroundStyle(.secondary)
                Link("dns.provider", destination: URL(string: "https://developers.cloudflare.com/1.1.1.1/setup/")!)
            }
            Section("family.title") {
                Text("family.steps")
                Text("family.scope").font(.footnote).foregroundStyle(.secondary)
            }
            Section("network.title") {
                RouterAppDomainsView()
                Text("network.steps")
                Text("network.scope").font(.footnote).foregroundStyle(.secondary)
                Link("network.guide", destination: URL(string: "https://developers.cloudflare.com/1.1.1.1/setup/router/")!)
            }
            removalSection
            errorRow
        }
    }

    private var verifiedSetup: some View {
        let assessment = protection.setupAssessment
        return Section("setup.status.title") {
            setupStatusLabel(assessment.state)
                .font(.headline)
                .foregroundStyle(assessment.state == .configured ? Color.teal : Color.orange)
                .accessibilityIdentifier("setup.currentAssessment")
            Text("setup.status.scope").font(.footnote).foregroundStyle(.secondary)
            if assessment.state == .unchecked {
                Text("setup.next.check").font(.callout)
            }
            ForEach(assessment.issues, id: \.rawValue) { issue in
                Label(LocalizedStringKey(issue.rawValue), systemImage: "arrow.right.circle")
                    .font(.callout)
            }
            if assessment.state == .checking { ProgressView("working") }
            Button("status.refresh") { Task { await protection.refresh() } }
                .disabled(protection.busy)
            if assessment.state == .needsSetup, selectedTab != 1 {
                Button("setup.continue") { selectedTab = 1 }
            }
            Text("setup.status.mixedcontent").font(.footnote).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private func setupStatusLabel(_ state: MobileProtectionPolicy.SetupState) -> some View {
        switch state {
        case .configured: Label(LocalizedStringKey(state.rawValue), systemImage: "checkmark.shield")
        case .checking, .unchecked: Label(LocalizedStringKey(state.rawValue), systemImage: "arrow.clockwise")
        case .needsSetup: Label(LocalizedStringKey(state.rawValue), systemImage: "exclamationmark.shield")
        }
    }

    private var readiness: some View {
        Section("plan.title") {
            if !protection.historyStorageHealthy {
                Label("plan.historyUnavailable", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
            }
            if !protection.unconfirmedContentLayers.isEmpty {
                Label("plan.lost", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
                ForEach(protection.unconfirmedContentLayers.sorted(), id: \.self) { key in
                    Text(LocalizedStringKey(key)).font(.callout)
                }
                Button("status.refresh") { Task { await protection.refresh() } }
                    .disabled(protection.busy)
            }
            if usage.health.needsAttention {
                Label(LocalizedStringKey(usage.health.rawValue), systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
                Text("apps.health.recovery").font(.footnote)
                Button("setup.continue") { selectedTab = 1 }
            }
            Text("plan.layers \(protection.configuredContentLayers)")
                .font(.headline).accessibilityIdentifier("plan.configuredLayers")
            readinessRow("safari.title", ready: protection.safariConfigured)
            readinessRow("dns.title", ready: protection.dns == .enabled)
            readinessRow("screentime.layer", ready: protection.screenTimeConfigured)
            Text("plan.scope").font(.footnote).foregroundStyle(.secondary)
            if protection.checkedAt == nil {
                Label("plan.checkFirst", systemImage: "arrow.clockwise")
                    .font(.footnote)
            }
        }
    }

    private func readinessRow(_ title: LocalizedStringKey, ready: Bool) -> some View {
        HStack {
            Text(title)
            Spacer()
            if ready {
                Label("plan.configured", systemImage: "checkmark.circle").foregroundStyle(.teal)
            } else {
                Label("plan.needsSetup", systemImage: "circle").foregroundStyle(.secondary)
            }
        }
        .font(.subheadline)
        .accessibilityElement(children: .combine)
    }

    private var removalSection: some View {
        Section("removal.title") {
            Label(LocalizedStringKey(protection.removal.rawValue), systemImage: "lock.shield")
            Text("removal.body").font(.footnote).foregroundStyle(.secondary)
            Button("removal.request") { confirmation = .guardian }.disabled(protection.busy)
            Text("removal.manual").font(.footnote).foregroundStyle(.secondary)
            Text("removal.recovery").font(.footnote).foregroundStyle(.secondary)
            Link("removal.guide", destination: URL(string: "https://support.apple.com/en-gb/105121")!)
        }
    }

    private var safariParts: some View {
        Section("safari.parts") {
            ForEach(0..<MobileProtectionPolicy.blockerIdentifiers.count, id: \.self) { index in
                HStack {
                    Text(verbatim: "Hisn \(index + 1)")
                    Spacer()
                    let state = MobileProtectionPolicy.safariPartState(enabled: protection.safari[index],
                        lastReload: protection.safariReloads[index])
                    switch state {
                    case .enabled: Label("part.enabled", systemImage: "checkmark.circle")
                    case .disabled: Label("part.disabled", systemImage: "circle")
                    case .unknown: Label("status.unknown", systemImage: "questionmark.circle")
                    case .reloadFailed: Label("part.failed", systemImage: "exclamationmark.triangle")
                    }
                }
                .font(.subheadline)
                .accessibilityElement(children: .combine)
            }
        }
    }

    private var settings: some View {
        Form {
            Section("language.title") {
                Picker("language.title", selection: $language) {
                    Text("language.system").tag("system")
                    Text(verbatim: "العربية").tag("ar")
                    Text(verbatim: "English").tag("en")
                }
            }
            Section("privacy.title") { Text("privacy.body") }
            Section("coverage.title") { Text("coverage.body") }
            Section("apple.title") { Text("apple.body") }
        }
    }

    @ViewBuilder private var errorRow: some View {
        if let error = protection.errorKey {
            Section { Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle") }
        }
    }

    private var dnsLabel: LocalizedStringKey {
        switch protection.dns {
        case .unknown: return "status.unknown"
        case .absent: return "dns.absent"
        case .saved: return "dns.saved"
        case .enabled: return "dns.enabled"
        case .differentConfiguration: return "dns.different"
        case .unavailable: return "dns.unavailable"
        }
    }
}
