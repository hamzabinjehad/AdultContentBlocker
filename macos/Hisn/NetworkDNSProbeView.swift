import SwiftUI

/// User-triggered DNS samples are separate from the network's unverified
/// enforcement state. Results are ephemeral and disappear with this route.
struct NetworkDNSProbeView: View {
    @State private var result: NetworkDNSProbe.Result?
    @State private var request: UUID?
    @State private var checking = false
    @State private var failed = false

    init(initialResult: NetworkDNSProbe.Result? = nil) {
        _result = State(initialValue: initialResult)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Button("Check Cloudflare DNS on this Mac") {
                    result = nil
                    failed = false
                    checking = true
                    request = UUID()
                }
                .disabled(checking)
                if checking { ProgressView().controlSize(.small) }
            }
            Text("Checks a harmless adult-category test name and an ordinary name through this Mac's system resolver. No website is opened and no settings are changed.")
                .foregroundStyle(.secondary)
            if let result {
                HStack {
                    Text("DNS sample taken")
                    Text(result.checkedAt, style: .time)
                }
                .foregroundStyle(.secondary)
                sample("IPv4 address lookup (A)", verdict: result.a.verdict)
                sample("IPv6 address lookup (AAAA)", verdict: result.aaaa.verdict)
                if result.networkEvidence != .stable {
                    Text("The network or DNS settings changed or could not be verified during this check. Retry on a stable connection.")
                        .foregroundStyle(.secondary)
                } else if result.hostsEvidence != .clear {
                    Text("Local name overrides could not be ruled out. Ask your administrator to check the hosts file, then retry.")
                        .foregroundStyle(.secondary)
                } else if result.a.verdict == .inconclusive || result.aaaa.verdict == .inconclusive {
                    Text("A missing reply or an unavailable ordinary name does not prove blocking. Check connectivity and DNS settings, then retry.")
                        .foregroundStyle(.secondary)
                }
            }
            if failed {
                Text("The DNS check could not finish. Check connectivity and try again.")
                    .foregroundStyle(.secondary)
            }
            Text("This is a sample on this Mac and may come from a DNS cache. It does not verify router enforcement, IPv6 network routing, browser DNS, VPN restrictions, or other devices. Recheck after changing networks or DNS settings.")
                .foregroundStyle(.secondary)
        }
        .font(.callout)
        .fixedSize(horizontal: false, vertical: true)
        .task(id: request) {
            guard let id = request else { return }
            do {
                let sample = try await NetworkDNSProbe().check()
                guard !Task.isCancelled, request == id else { return }
                result = sample
            } catch is CancellationError {
                // Leaving the guide cancels native queries and discards results.
            } catch {
                guard !Task.isCancelled, request == id else { return }
                failed = true
            }
            if request == id {
                checking = false
                request = nil
            }
        }
        .onDisappear {
            request = nil
            checking = false
            result = nil
            failed = false
        }
    }

    private func sample(_ title: LocalizedStringKey, verdict: NetworkDNSProbe.Verdict) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).fontWeight(.medium)
            switch verdict {
            case .filteringObserved:
                Label("Blocking response observed on this Mac", systemImage: "checkmark.circle")
                    .foregroundStyle(.green)
            case .nullReplyObserved:
                Label("Null address observed for this sample", systemImage: "info.circle")
                    .foregroundStyle(.secondary)
            case .notFiltered:
                Label("Test name returned a non-null address", systemImage: "exclamationmark.circle")
                    .foregroundStyle(.orange)
            case .inconclusive:
                Label("DNS sample inconclusive", systemImage: "questionmark.circle")
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
