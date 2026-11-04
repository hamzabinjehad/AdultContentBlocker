import AppKit
import SwiftUI

struct FilterSetupControl: View {
    @ObservedObject var filter: FilterController
    /// The step around this control already prints the build block in its
    /// detail. Repeating it here made the same orange paragraph appear twice
    /// in one card.
    var showsBuildBlock = true

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if showsBuildBlock, let block = FilterLink.shared.buildBlock {
                Text(block.explanation)
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

/// A command to copy verbatim. The signed-build command keeps its team ID as a
/// visible placeholder: a guessed team ID would fail like a bug, not like a
/// missing step.
struct CopyableCommand: View {
    let command: String
    @State private var copied = false

    var body: some View {
        HStack(spacing: 8) {
            Text(verbatim: command)
                .font(.callout.monospaced())
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
                .environment(\.layoutDirection, .leftToRight)
            Button(copied ? "Copied" : "Copy") {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(command, forType: .string)
                copied = true
            }
            .controlSize(.small)
        }
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
                Text(LocalizedStringKey(readiness.detail(next)))
                    .font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
                Button("Review next step") { review(next) }
                    .accessibilityIdentifier("setup.reviewNextStep")
            }
            if readiness.issues.count > 1 {
                DisclosureGroup("Other missing checks") {
                    ForEach(Array(readiness.issues.dropFirst())) { issue in
                        Text(LocalizedStringKey(readiness.detail(issue)))
                            .font(.callout)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 6)
                    }
                }
            }
            DisclosureGroup("Limits of these checks") {
                Text("Mixed-content sites can still contain adult material. Strict mode restricts allowed destinations, but cannot certify everything on an allowed site. Custom profiles, tunnels, administrator changes, and recovery access need separate checks.")
                    .font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
            }
            .font(.caption)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("setup.laptopReadiness")
    }
}
