import SwiftUI
import SafariServices
import CryptoKit

/// Separate limited edition. No Family Controls, DNS or app-deletion claims.
@main
struct SafariOnlyApp: App {
    @StateObject private var protection = SafariOnlyProtection()
    @AppStorage("hisn.safari.language") private var language = "system"
    @Environment(\.scenePhase) private var phase
    private var arabic: Bool {
        language == "ar" || (language == "system" && Locale.autoupdatingCurrent.language.languageCode?.identifier == "ar")
    }
    var body: some Scene {
        WindowGroup {
            SafariEditionRoot(protection: protection, language: $language)
            .environment(\.locale, language == "system" ? .autoupdatingCurrent : Locale(identifier: language))
            .environment(\.layoutDirection, arabic ? .rightToLeft : .leftToRight)
            .task { await protection.refresh() }
            .onChange(of: phase) { next in if next == .active { Task { await protection.refresh() } } }
        }
    }
}

struct SafariEditionRoot: View {
    @ObservedObject var protection: SafariOnlyProtection
    @Binding var language: String
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var page = 0
    private let titles: [LocalizedStringKey] = ["tab.protection", "tab.setup", "tab.settings"]
    private let symbols = ["shield.lefthalf.filled", "checklist", "gearshape"]
    private var enabledCount: Int { protection.enabled.filter { $0 == true }.count }
    private var statusTitle: LocalizedStringKey {
        if protection.busy { return "working" }
        return enabledCount == 4 && protection.verified && !protection.failed
            ? "design.safari.configured" : "design.safari.attention"
    }

    var body: some View {
        Group {
            if sizeClass == .regular {
                NavigationSplitView {
                    List(selection: Binding<Int?>(get: { page }, set: { if let value = $0 { page = value } })) {
                        ForEach(0..<3) { index in Label(titles[index], systemImage: symbols[index]).tag(index) }
                    }.navigationTitle("Hisn Safari")
                } detail: { NavigationStack { destination(page) } }
            } else {
                TabView(selection: $page) {
                    ForEach(0..<3) { index in
                        NavigationStack { destination(index) }
                            .tabItem { Label(titles[index], systemImage: symbols[index]) }.tag(index)
                    }
                }
            }
        }.tint(.blue)
    }

    @ViewBuilder private func destination(_ index: Int) -> some View {
        List {
            if index == 0 {
                Section {
                    VStack(alignment: .leading, spacing: 14) {
                        Label("limited.title", systemImage: "shield.lefthalf.filled")
                            .font(.headline).foregroundStyle(.blue)
                        Text(statusTitle)
                            .font(.title.bold()).accessibilityAddTraits(.isHeader)
                        Text(LocalizedStringKey(protection.domainOnly ? "limited.domainOnlyScope" : "limited.configOnly"))
                            .font(.callout).foregroundStyle(.secondary)
                        HStack(alignment: .firstTextBaseline) {
                            Text(verbatim: "\(enabledCount) / 4").font(.largeTitle.weight(.bold)).monospacedDigit()
                            Text("design.safari.parts").font(.callout).foregroundStyle(.secondary)
                        }
                        ProgressView(value: Double(enabledCount), total: 4)
                            .accessibilityLabel("design.safari.parts")
                        Button("setup.continue") { page = 1 }.buttonStyle(.borderedProminent)
                    }.padding(.vertical, 8)
                }
                Section("design.layers.heading") {
                    Label(LocalizedStringKey(protection.verified ? "limited.listReady" : "limited.listUnavailable"),
                          systemImage: protection.verified ? "checkmark.seal" : "exclamationmark.triangle")
                    ForEach(0..<4) { index in
                        HStack(spacing: 12) {
                            Image(systemName: protection.enabled[index] == true ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(protection.enabled[index] == true ? Color.teal : Color.secondary)
                                .accessibilityHidden(true)
                            Text(verbatim: "Hisn \(index + 1)")
                            Spacer()
                            Text(LocalizedStringKey(protection.enabled[index] == true ? "part.enabled" : protection.enabled[index] == false ? "part.disabled" : "status.unknown"))
                                .foregroundStyle(.secondary).font(.callout)
                        }.accessibilityElement(children: .combine)
                    }
                }
                checkSection
            } else if index == 1 {
                Section {
                    Label("design.setup.heading", systemImage: "checklist").font(.title2.bold())
                    Text("limited.scope").foregroundStyle(.secondary)
                }
                Section("design.setup.enable") { Text("limited.steps") }
                Section("design.setup.reload") {
                    Text(LocalizedStringKey(protection.domainOnly ? "limited.domainOnlySteps" : "limited.textSteps")).foregroundStyle(.secondary)
                    Button("safari.reload") { Task { await protection.reload() } }
                        .disabled(protection.busy || !protection.verified)
                    Link("limited.appleGuide", destination: URL(string: "https://support.apple.com/guide/iphone/get-extensions-iphb01fc3c85/ios")!)
                }
                checkSection
            } else {
                Section("language.title") {
                    Picker("language.title", selection: $language) {
                        Text("language.system").tag("system")
                        Text("English").tag("en")
                        Text("العربية").tag("ar")
                    }
                }
                Section("limited.limitsTitle") {
                    Text("limited.limits")
                    Text("limited.expiry").foregroundStyle(.secondary)
                }
                Section("design.privacy.heading") { Text("limited.privacy").foregroundStyle(.secondary) }
            }
        }
        .navigationTitle(titles[index])
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .frame(maxWidth: sizeClass == .regular ? 800 : .infinity)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .refreshable { await protection.refresh() }
    }

    private var checkSection: some View {
        Section("status.title") {
            if let date = protection.checkedAt {
                LabeledContent("status.checked") { Text(date, style: .time) }.font(.footnote)
            }
            if protection.failed { Label("safari.failed", systemImage: "exclamationmark.triangle").foregroundStyle(.orange) }
            if protection.busy { ProgressView("working") }
            Button("status.refresh") { Task { await protection.refresh() } }.disabled(protection.busy)
        }
    }
}

@MainActor
final class SafariOnlyProtection: ObservableObject {
    @Published private(set) var enabled: [Bool?] = Array(repeating: nil, count: 4)
    @Published private(set) var busy = false
    @Published private(set) var failed = false
    @Published private(set) var checkedAt: Date?
    let verified: Bool
    let domainOnly: Bool
    private let identifiers: [String]
    init() {
        let prefix=Bundle.main.bundleIdentifier ?? "app.hisn.safari"
        domainOnly=Bundle.main.object(forInfoDictionaryKey: "HisnLimitedDomainOnly") as? String == "YES"
        let reuse=Bundle.main.object(forInfoDictionaryKey: "HisnLimitedReuseTextID") as? String == "YES"
        identifiers = (1...4).map { reuse && $0 == 1 ? "\(prefix).HisnText" : "\(prefix).blocker\($0)" }
        verified = Self.listVerified(at: Bundle.main.bundleURL)
    }
    func refresh() async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        await readState()
    }
    private func readState() async {
        for (index, id) in identifiers.enumerated() {
            enabled[index] = await withCheckedContinuation { continuation in
                SFContentBlockerManager.getStateOfContentBlocker(withIdentifier: id) { state, error in
                    continuation.resume(returning: error == nil ? state?.isEnabled : nil)
                }
            }
        }
        checkedAt = Date()
    }
    func reload() async {
        guard !busy, verified else { return }
        busy = true; defer { busy = false }
        var anyFailure = false
        for id in identifiers {
            let success: Bool = await withCheckedContinuation { continuation in
                SFContentBlockerManager.reloadContentBlocker(withIdentifier: id) { error in
                    continuation.resume(returning: error == nil)
                }
            }
            anyFailure = anyFailure || !success
        }
        failed = anyFailure
        await readState()
    }
    struct Summary: Decodable { let count: Int; let version: Int; let parts: Int }
    struct Part: Decodable { let count: Int; let version: Int; let sha256: String }
    nonisolated static func listVerified(at url: URL) -> Bool {
        guard let raw = try? Data(contentsOf: url.appendingPathComponent("rules-metadata.json")),
              let summary = try? JSONDecoder().decode(Summary.self, from: raw), summary.parts == 4 else { return false }
        var parts: [MobileProtectionPolicy.SafariListPart] = []
        for index in 1...4 {
            let folder = url.appendingPathComponent("PlugIns/Hisn\(index).appex")
            guard let data = try? Data(contentsOf: folder.appendingPathComponent("rules.json")),
                  let metadata = try? Data(contentsOf: folder.appendingPathComponent("rules-metadata.json")),
                  let part = try? JSONDecoder().decode(Part.self, from: metadata),
                  let rules = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return false }
            let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
            let blocks = rules.allSatisfy { ($0["action"] as? [String: Any])?["type"] as? String == "block"
                && (($0["trigger"] as? [String: Any])?["url-filter"] as? String)?.isEmpty == false }
            parts.append(.init(count: part.count, version: part.version,
                integrityMatches: hash == part.sha256 && rules.count == part.count && blocks))
        }
        return MobileProtectionPolicy.safariListReady(count: summary.count, version: summary.version, parts: parts)
    }
}
