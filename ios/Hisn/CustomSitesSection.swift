import SwiftUI

/// The person's own blocked sites, applied through Hisn's Screen Time filter.
struct CustomSitesSection: View {
    @ObservedObject var protection: ProtectionController
    @State private var entry = ""

    var body: some View {
        Section {
            HStack(spacing: 12) {
                TextField("sites.placeholder", text: $entry)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                    .submitLabel(.done)
                    .onSubmit(add)
                    // Hostnames read left to right in both languages.
                    .environment(\.layoutDirection, .leftToRight)
                    .accessibilityIdentifier("customSitesEntry")
                Button("sites.add", action: add)
                    .buttonStyle(.borderedProminent)
                    .disabled(protection.busy || !protection.customSitesStorageHealthy
                              || entry.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            if protection.customSites.isEmpty {
                Text("sites.empty").foregroundStyle(.secondary)
            }
            ForEach(protection.customSites, id: \.self) { host in
                Label {
                    Text(verbatim: host).environment(\.layoutDirection, .leftToRight)
                } icon: {
                    Image(systemName: "nosign").foregroundStyle(.red)
                }
            }
            .onDelete(perform: protection.customSitesLocked ? nil : { offsets in remove(offsets) })
            statusRow
            if let message = protection.customSiteMessage {
                Text(LocalizedStringKey(message)).font(.footnote)
                    .foregroundStyle(message == "sites.added" || message == "sites.removed" ? Color.secondary : Color.orange)
            }
        } header: {
            HStack {
                Text("sites.title")
                Spacer()
                Text("sites.count \(protection.customSites.count) \(CustomSitePolicy.limit)")
                    .monospacedDigit()
            }
        } footer: {
            Text(protection.customSitesLocked ? "sites.lockedScope" : "sites.scope")
        }
    }

    @ViewBuilder private var statusRow: some View {
        if !protection.customSitesStorageHealthy {
            Label("sites.storageFailed", systemImage: "exclamationmark.triangle").foregroundStyle(.orange)
        } else if !protection.customSites.isEmpty {
            if protection.customSitesApplied {
                Label("sites.applied", systemImage: "checkmark.circle").foregroundStyle(.teal)
            } else {
                Label("sites.needsScreenTime", systemImage: "exclamationmark.circle").foregroundStyle(.orange)
            }
        }
    }

    private func add() {
        let text = entry.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        protection.addCustomSite(text)
        if protection.customSiteMessage == "sites.added" { entry = "" }
    }

    private func remove(_ offsets: IndexSet) {
        for host in offsets.map({ protection.customSites[$0] }) { protection.removeCustomSite(host) }
    }
}
