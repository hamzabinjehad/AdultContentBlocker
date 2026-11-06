import SwiftUI

/// The app side of a pause rule.
///
/// The shield's **Pause first** button cannot open the shielded app; it hands a
/// request to Hisn (directly on iOS 26.5 and later, through a notification
/// before that). This view is where the request becomes a decision: a short
/// breath, then one open bought with a session. Closing the view spends
/// nothing and the shield stays.
struct PauseRuleGrantView: View {
    @ObservedObject var usage: AppUsageController
    let appName: String?
    let onClose: () -> Void

    @State private var started = Date()
    @State private var granted: AppUsageController.PauseGrant?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var sessionMinutes: Int {
        usage.configuration?.minutes ?? PauseRule.defaultSessionMinutes
    }

    private var progress: Double {
        min(1, max(0, Date().timeIntervalSince(started) / PauseRule.pauseSeconds))
    }

    private var finished: Bool { progress >= 1 }

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Spacer(minLength: 8)
                Text("pauseRule.title")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                if let appName, !appName.isEmpty {
                    Text(appName)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                if let granted {
                    result(granted)
                } else {
                    breath
                    Text("pauseRule.body")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                    Button {
                        granted = usage.grantPauseSession()
                    } label: {
                        Text("pauseRule.open \(sessionMinutes)")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(!finished)
                    Button("pauseRule.keepClosed") { onClose() }
                        .buttonStyle(.plain)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    if granted == nil {
                        Button("pauseRule.keepClosed") { onClose() }
                    }
                }
            }
        }
    }

    /// A ten-second ring, drawn from a clock rather than an animation so a
    /// backgrounded view resumes at the true elapsed time. The number is the
    /// accessible value; the ring is decoration.
    private var breath: some View {
        TimelineView(.animation(minimumInterval: reduceMotion ? 1 : 1.0 / 30.0)) { _ in
            let current = progress
            ZStack {
                Circle()
                    .stroke(Color.secondary.opacity(0.18), lineWidth: 10)
                Circle()
                    .trim(from: 0, to: current)
                    .stroke(Color.accentColor,
                            style: StrokeStyle(lineWidth: 10, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Text(verbatim: "\(Int((PauseRule.pauseSeconds * (1 - current)).rounded(.up)))")
                    .font(.system(size: 42, weight: .semibold, design: .rounded))
                    .monospacedDigit()
            }
            .frame(width: 168, height: 168)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("pauseRule.countdown"))
            .accessibilityValue(Text(verbatim: "\(Int((PauseRule.pauseSeconds * (1 - current)).rounded(.up)))"))
        }
    }

    @ViewBuilder private func result(_ grant: AppUsageController.PauseGrant) -> some View {
        switch grant {
        case .granted(let minutes):
            Label {
                Text("pauseRule.granted \(minutes)")
                    .font(.headline)
                    .fixedSize(horizontal: false, vertical: true)
            } icon: {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            }
            Text("pauseRule.grantedBody")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button("pauseRule.done") { onClose() }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
        case .noOpens:
            Text("pauseRule.noOpens")
                .multilineTextAlignment(.center)
            Text("pauseRule.noOpensBody")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button("pauseRule.done") { onClose() }
                .buttonStyle(.bordered)
        case .unreadable:
            Text("pauseRule.unreadable")
                .multilineTextAlignment(.center)
            Button("pauseRule.done") { onClose() }
                .buttonStyle(.bordered)
        case .failed(let key):
            Label(LocalizedStringKey(key), systemImage: "exclamationmark.triangle")
                .multilineTextAlignment(.center)
            Button("pauseRule.done") { onClose() }
                .buttonStyle(.bordered)
        }
    }
}
