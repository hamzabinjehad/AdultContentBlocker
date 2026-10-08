import AppKit
import SwiftUI

struct FilterSetupControl: View {
    @ObservedObject var filter: FilterController

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
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
                .disabled(filter.isEnabling || filter.isEnabled || filter.restartRequired)

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
