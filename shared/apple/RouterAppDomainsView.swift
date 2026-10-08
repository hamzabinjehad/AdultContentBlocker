import SwiftUI

struct RouterAppDomainsView: View {
    @State private var text = ""
    @State private var reviewed = false
    private var hosts: [String]? { try? RouterAppDomains.parse(text) }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Optional router app-domain layer").font(.headline)
            Text("Enter reviewed app-service hostnames, one per line. Hisn does not infer servers from an app name. Shared hosting can affect unrelated services.")
                .font(.footnote).foregroundStyle(.secondary)
            TextEditor(text: $text).frame(minHeight: 90).accessibilityLabel("App-service domains")
                .onChange(of: text) { _ in reviewed = false }
            if hosts == nil { Text("Use hostnames only: no URLs, IP addresses, wildcards or shared infrastructure roots.").foregroundStyle(.orange) }
            Toggle("I understand importing these rules affects everyone using this DNS server, not just me.", isOn: $reviewed)
            if reviewed, let hosts, !hosts.isEmpty {
                ShareLink("Export AdGuard Home rules", item: RouterAppDomains.adGuardRules(hosts))
            }
            Text("Export only: no router was changed. Rules persist independently only after you import and verify them on an external resolver. They do not impose a time limit, block offline apps or guarantee all app servers are covered. Use your router's per-device schedule for internet hours where supported; a network-wide schedule affects everyone.")
                .font(.footnote).foregroundStyle(.secondary)
        }
    }
}
