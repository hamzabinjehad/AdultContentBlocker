import AppKit
import SwiftUI

struct FilterSetupControl: View {
    @ObservedObject var filter: FilterController
    /// The step around this control already prints the build block in its
    /// detail. Repeating it here made the same orange paragraph appear twice
    /// in one card.
    var showsBuildBlock = true

    /// A profile-owned filter is enabled by the organization profile. Showing
    /// an "Enable" button here would either fail or overwrite the management
    /// layer's configuration, so the control says who owns it instead.
    private var managedByProfile: Bool { ManagedMode.current().filterManaged }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if showsBuildBlock, let block = FilterLink.shared.buildBlock {
                Text(block.explanation)
                    .font(.hnCaption()).foregroundStyle(HnPalette.ember)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if filter.restartRequired {
                Label("Restart required", systemImage: "arrow.clockwise")
                    .font(.hnCaption()).foregroundStyle(HnPalette.ember)
            } else if filter.needsUserApproval {
                Label("Waiting for your approval in System Settings", systemImage: "hand.raised")
                    .font(.hnCaption()).foregroundStyle(HnPalette.ember)
            } else if filter.isEnabling {
                ProgressView("Setting up the filter...").controlSize(.small)
            }

            if managedByProfile {
                Label("The system filter is managed by your organization. Ask your administrator to change it.",
                      systemImage: "building.2")
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                if filter.needsUserApproval {
                    HnButton(title: "Open System Settings", kind: .outline) {
                        NSWorkspace.shared.open(URL(fileURLWithPath: "/System/Applications/System Settings.app"))
                    }
                }
            } else {
                HStack(spacing: 8) {
                    HnButton(title: "Enable system filter", systemImage: "shield.lefthalf.filled",
                             kind: .quiet) {
                        Task { @MainActor in
                            do { try await filter.enable() }
                            catch { /* The controller publishes the error below. */ }
                        }
                    }
                    .disabled(!FilterLink.shared.isConfigured || filter.isEnabling || filter.isEnabled || filter.restartRequired)

                    if filter.needsUserApproval {
                        HnButton(title: "Open System Settings", kind: .outline) {
                            NSWorkspace.shared.open(URL(fileURLWithPath: "/System/Applications/System Settings.app"))
                        }
                    }
                }
            }

            if let error = filter.lastError {
                Text(error).font(.hnCaption()).foregroundStyle(HnPalette.danger)
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

/// The same live-evidence status on Setup and Overview. The one-line state is
/// visible; what it means, and the detail of the next issue, live in `Explain`.
/// Actions navigate to consented setup; rendering this card never enables a
/// protection layer.
struct LaptopSetupStatusView: View {
    let readiness: LaptopSetupReadiness
    let review: (LaptopSetupReadiness.Issue) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 7) {
                Image(systemName: readiness.isReady ? "checkmark.shield.fill" : "checklist")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(readiness.isReady ? HnPalette.calm : HnPalette.ember)
                    .accessibilityHidden(true)
                Text(LocalizedStringKey(readiness.headline))
                    .font(.system(.title3).weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                    .accessibilityAddTraits(.isHeader)
                HnExplain {
                    Text(LocalizedStringKey(readiness.summary))
                    if let next = readiness.issues.first {
                        Text(verbatim: readiness.detail(next))
                    }
                }
                Spacer(minLength: 0)
            }
            if readiness.isChecking {
                HStack(spacing: 6) {
                    ProgressView().controlSize(.small)
                    Text("Checking this Mac...").font(.hnCaption()).foregroundStyle(HnPalette.muted)
                }
            }
            if let next = readiness.issues.first {
                HStack(spacing: 8) {
                    HnButton(title: "Review next step", systemImage: "arrow.forward",
                             kind: .quiet) { review(next) }
                        .accessibilityIdentifier("setup.reviewNextStep")
                    if readiness.issues.count > 1 {
                        Text(String(localized: "\(readiness.issues.count - 1) more checks below."))
                            .font(.hnCaption())
                            .foregroundStyle(HnPalette.faint)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.top, 1)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("setup.laptopReadiness")
    }
}
