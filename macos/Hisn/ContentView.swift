import SwiftUI

/// Main window.
///
/// The interface has one job beyond starting a lock: make the state honest.
/// A self-control tool that *looks* armed while the filter is off is worse than
/// no tool, because the person stops being careful. So the banner in
/// `StatusHeader` reports what is actually enforced, not what was requested.
struct ContentView: View {
    @StateObject private var lock = LockManager.shared
    @StateObject private var filter = FilterController.shared

    @State private var duration: LockManager.Duration = .week
    @State private var strict = false
    @State private var error: String?
    @State private var showConfirm = false

    var body: some View {
        VStack(spacing: 0) {
            StatusHeader(lock: lock, filter: filter)
            Divider()
            Group {
                if lock.isLocked { activeLock } else { setup }
            }
            .padding(24)
        }
        .frame(width: 420)
        .task { await filter.reassertIfNeeded() }
        .alert("Something went wrong", isPresented: .constant(error != nil)) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }

    // MARK: - Not locked

    private var setup: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("How long?").font(.headline)
                Picker("", selection: $duration) {
                    ForEach(LockManager.Duration.allCases) { d in
                        Text(d.rawValue + (d.requiresSubscription ? " · Pro" : ""))
                            .tag(d)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            Toggle(isOn: $strict) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Strict mode")
                    Text("Blocks everything except sites you allow. "
                         + "A block list can never cover every site; this can.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            // The honest warning. People who are surprised by a lock fight it;
            // people who chose it with clear eyes keep it.
            GroupBox {
                VStack(alignment: .leading, spacing: 8) {
                    Label("This cannot be undone by you alone",
                          systemImage: "lock.fill")
                        .font(.subheadline.weight(.medium))
                    Text("Once started, you cannot shorten or cancel this. "
                         + "Early release takes 48 hours, or an approval from "
                         + "your accountability partner.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(4)
            }

            Button {
                showConfirm = true
            } label: {
                Text("Start \(duration.rawValue) lock")
                    .frame(maxWidth: .infinity)
            }
            .controlSize(.large)
            .buttonStyle(.borderedProminent)
            .confirmationDialog(
                "Start a \(duration.rawValue) lock?",
                isPresented: $showConfirm, titleVisibility: .visible
            ) {
                Button("Start the lock", role: .destructive) { start() }
                Button("Not yet", role: .cancel) {}
            } message: {
                Text("You will not be able to turn this off until "
                     + "\(Date().addingTimeInterval(duration.seconds).formatted()).")
            }
        }
    }

    // MARK: - Locked

    private var activeLock: some View {
        VStack(spacing: 18) {
            VStack(spacing: 4) {
                Text(lock.remainingDescription)
                    .font(.system(size: 34, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                Text("until \(lock.state.deadline.formatted(date: .abbreviated, time: .shortened))")
                    .font(.caption).foregroundStyle(.secondary)
            }
            .padding(.vertical, 8)

            if let pending = lock.pendingSelfRelease {
                GroupBox {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Early release requested")
                            .font(.subheadline.weight(.medium))
                        Text("Unlocks \(pending.formatted()). "
                             + "You can cancel this, but you cannot speed it up.")
                            .font(.caption).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("Cancel the request") { lock.cancelSelfRelease() }
                            .controlSize(.small)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(4)
                }
            }

            HStack {
                Button("Add a week") {
                    do { try lock.extend(by: 7 * 86400) }
                    catch { self.error = error.localizedDescription }
                }
                if lock.state.mode != "strict" {
                    Button("Switch to strict") {
                        do { try lock.tightenToStrict() }
                        catch { self.error = error.localizedDescription }
                    }
                }
            }
            .controlSize(.regular)

            Text("Making the lock stronger is always available. "
                 + "Making it weaker is not.")
                .font(.caption2).foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
    }

    private func start() {
        Task {
            do { try await lock.start(duration: duration, strict: strict) }
            catch { self.error = error.localizedDescription }
        }
    }
}

/// Reports what is genuinely being enforced right now.
private struct StatusHeader: View {
    @ObservedObject var lock: LockManager
    @ObservedObject var filter: FilterController

    private var isHealthy: Bool { !lock.isLocked || filter.isEnabled }

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(isHealthy ? Color.green : Color.orange)
                .frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 1) {
                Text(lock.isLocked ? "Protected" : "Not locked")
                    .font(.subheadline.weight(.semibold))
                if lock.isLocked && !filter.isEnabled {
                    Text("The system filter is off — reopening it now")
                        .font(.caption2).foregroundStyle(.orange)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }
}
