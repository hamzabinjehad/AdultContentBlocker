import AppKit
import SwiftUI

struct FilterSetupControl: View {
    @ObservedObject var filter: FilterController

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !FilterLink.shared.isConfigured {
                Text("This development build cannot activate laptop-wide filtering. Install a properly signed Hisn release, then approve its filter in macOS.")
                    .font(.callout).foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if filter.restartRequired {
                Label("Restart required", systemImage: "arrow.clockwise")
                    .foregroundStyle(.orange)
            } else if filter.needsUserApproval {
                Label("Waiting for your approval in System Settings", systemImage: "hand.raised")
                    .foregroundStyle(.orange)
            } else if filter.isEnabling {
                ProgressView("Setting up the filter...").controlSize(.small)
            }

            HStack {
                Button {
                    Task { @MainActor in
                        do { try await filter.enable() }
                        catch { /* The controller publishes the error below. */ }
                    }
                } label: {
                    Label("Enable system filter", systemImage: "shield.lefthalf.filled")
                }
                .disabled(!FilterLink.shared.isConfigured || filter.isEnabling || filter.isEnabled || filter.restartRequired)

                if filter.needsUserApproval {
                    Button("Open System Settings") {
                        NSWorkspace.shared.open(URL(fileURLWithPath: "/System/Applications/System Settings.app"))
                    }
                }
            }

            if let error = filter.lastError {
                Text(error).font(.callout).foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The same live-evidence explanation on Setup and Overview. Actions navigate
/// to consented setup; rendering this card never enables a protection layer.
struct LaptopSetupStatusView: View {
    let readiness: LaptopSetupReadiness
    let review: (LaptopSetupReadiness.Issue) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(LocalizedStringKey(readiness.headline), systemImage: readiness.isReady ? "checkmark.shield" : "checklist")
                .font(.headline)
                .foregroundStyle(readiness.isReady ? Color.green : Color.primary)
                .accessibilityAddTraits(.isHeader)
            Text(LocalizedStringKey(readiness.summary))
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if readiness.isChecking {
                ProgressView("Checking this Mac...").controlSize(.small)
            }
            if let next = readiness.issues.first {
                Text(LocalizedStringKey(next.detail))
                    .font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
                Button("Review next step") { review(next) }
                    .accessibilityIdentifier("setup.reviewNextStep")
            }
            if readiness.issues.count > 1 {
                DisclosureGroup("Other missing checks") {
                    ForEach(Array(readiness.issues.dropFirst())) { issue in
                        Text(LocalizedStringKey(issue.detail))
                            .font(.callout)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 6)
                    }
                }
            }
            Text("Mixed-content sites can still contain adult material. Strict mode restricts allowed destinations, but cannot certify everything on an allowed site. Custom profiles, tunnels, administrator changes, and recovery access need separate checks.")
                .font(.caption).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("setup.laptopReadiness")
    }
}
