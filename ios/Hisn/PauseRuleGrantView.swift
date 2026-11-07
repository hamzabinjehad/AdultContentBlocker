import SwiftUI

/// The app side of a pause rule.
///
/// The shield's **Pause first** button cannot open the shielded app; it hands a
/// request to Hisn (directly on iOS 26.5 and later, through a notification
/// before that). This is where the request becomes a decision: one breath, then
/// an open bought with a session. Closing spends nothing and the shield stays.
struct PauseRuleGrantView: View {
    @ObservedObject var usage: AppUsageController
    let appName: String?
    let onClose: () -> Void

    @State private var started: Date
    @State private var granted: AppUsageController.PauseGrant?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss

    /// `startedAt` exists so the render tests can capture the finished breath
    /// without waiting ten seconds; production always starts now.
    init(usage: AppUsageController, appName: String?, startedAt: Date = Date(),
         onClose: @escaping () -> Void) {
        self.usage = usage
        self.appName = appName
        self.onClose = onClose
        _started = State(initialValue: startedAt)
    }

    private var sessionMinutes: Int {
        usage.configuration?.minutes ?? PauseRule.defaultSessionMinutes
    }

    private var opens: Int { usage.configuration?.opens ?? PauseRule.defaultOpens }
    private var left: Int { usage.opensLeft ?? 0 }

    var body: some View {
        ZStack {
            HnPalette.inkCard.ignoresSafeArea()
            RadialGradient(colors: [HnPalette.accent.opacity(0.38), .clear],
                           center: .top, startRadius: 10, endRadius: 520)
                .ignoresSafeArea()
            ScrollView {
                VStack(spacing: 22) {
                    closeRow
                    mark
                    heading
                    if let granted {
                        result(granted)
                    } else {
                        TimelineView(.animation(minimumInterval: reduceMotion ? 1 : 1.0 / 30.0)) { _ in
                            let current = progress
                            VStack(spacing: 20) {
                                breath(current: current)
                                openDots
                                Text(current >= 1 ? "pauseRule.bodyReady" : "pauseRule.body")
                                    .font(.footnote)
                                    .foregroundStyle(HnPalette.onInkMuted)
                                    .multilineTextAlignment(.center)
                                    .fixedSize(horizontal: false, vertical: true)
                                actions(finished: current >= 1)
                            }
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
        }
        .accessibilityIdentifier("pauseRule.screenView")
    }

    private var closeRow: some View {
        HStack {
            Spacer()
            Button(action: close) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(HnPalette.onInk)
                    .frame(width: 42, height: 42)
                    .background(Color.white.opacity(0.10), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("pauseRule.keepClosed"))
        }
    }

    private var mark: some View {
        ZStack {
            Circle()
                .fill(HnPalette.accent.opacity(0.16))
                .frame(width: 74, height: 74)
            HnMark(size: 34, tint: .white)
        }
        .accessibilityHidden(true)
    }

    private var heading: some View {
        VStack(spacing: 8) {
            Text("pauseRule.title")
                .font(.system(.largeTitle, design: .rounded).weight(.bold))
                .foregroundStyle(HnPalette.onInk)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            if let appName, !appName.isEmpty {
                HnBadge(text: LocalizedStringKey(appName), tint: HnPalette.onInkAccent, onInk: true)
            }
            Text("pauseRule.opensLeft \(left) \(opens)")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(HnPalette.onInkMuted)
                .multilineTextAlignment(.center)
        }
    }

    /// A ten-second ring, drawn from a clock rather than an animation so a
    /// backgrounded view resumes at the true elapsed time. The number is the
    /// accessible value; the ring is decoration.
    private func breath(current: Double) -> some View {
        let seconds = Int((PauseRule.pauseSeconds * (1 - current)).rounded(.up))
        return ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.14), lineWidth: 10)
                Circle()
                    .trim(from: 0, to: current)
                    .stroke(HnPalette.accent, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Circle()
                    .fill(RadialGradient(colors: [HnPalette.accent.opacity(0.45), .clear],
                                         center: .center, startRadius: 4, endRadius: 90))
                    .padding(24)
                    .scaleEffect(0.72 + 0.28 * current)
                    .allowsHitTesting(false)
                VStack(spacing: 2) {
                    Text(verbatim: "\(seconds)")
                        .font(.system(size: 52, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(HnPalette.onInk)
                    Text(current >= 1 ? "pauseRule.ready" : "pauseRule.breathe")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(HnPalette.onInkMuted)
                }
            }
        .frame(width: 208, height: 208)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("pauseRule.countdown"))
        .accessibilityValue(Text(verbatim: "\(seconds)"))
    }

    private var openDots: some View {
        HStack(spacing: 8) {
            ForEach(0..<max(opens, 1), id: \.self) { index in
                Circle()
                    .fill(index < left ? HnPalette.accent : Color.white.opacity(0.18))
                    .frame(width: 9, height: 9)
            }
        }
        .accessibilityHidden(true)
    }

    private func actions(finished: Bool) -> some View {
        // A pause rule with nothing left is closed for the day; the shield hides
        // its pause button then, and this is the same answer if it is reached.
        let closedToday = usage.configuration?.mode == .pause && left == 0
        return VStack(spacing: 12) {
            HnButton(title: LocalizedStringKey("pauseRule.open \(sessionMinutes)"),
                     systemImage: "arrow.up.right", kind: .onInk) {
                grant()
            }
            .disabled(!finished || closedToday)
            .opacity(finished && !closedToday ? 1 : 0.4)
            HnButton(title: "pauseRule.keepClosed", kind: .textOnInk) { close() }
        }
        .padding(.top, 4)
    }

    private var progress: Double {
        min(1, max(0, Date().timeIntervalSince(started) / PauseRule.pauseSeconds))
    }

    private func grant() {
        PauseRuleStore.appGroup.recordPractice()
        granted = usage.grantPauseSession()
    }

    private func close() {
        onClose()
        dismiss()
    }

    @ViewBuilder private func result(_ grant: AppUsageController.PauseGrant) -> some View {
        switch grant {
        case .granted(let minutes):
            VStack(spacing: 12) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 44, weight: .semibold))
                    .foregroundStyle(HnPalette.calm)
                    .accessibilityHidden(true)
                Text("pauseRule.granted \(minutes)")
                    .font(.system(.title2, design: .rounded).weight(.bold))
                    .foregroundStyle(HnPalette.onInk)
                    .multilineTextAlignment(.center)
                Text("pauseRule.grantedBody")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.onInkMuted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                HnButton(title: "pauseRule.done", kind: .onInk) { close() }
                    .padding(.top, 6)
            }
        case .noOpens:
            message("pauseRule.noOpens", body: "pauseRule.noOpensBody")
        case .unreadable:
            message("pauseRule.unreadable", body: "pauseRule.unreadableBody")
        case .failed(let key):
            message(LocalizedStringKey(key), body: "pauseRule.failedBody")
        }
    }

    private func message(_ title: LocalizedStringKey, body: LocalizedStringKey) -> some View {
        VStack(spacing: 12) {
            Text(title)
                .font(.hnTitle())
                .foregroundStyle(HnPalette.onInk)
                .multilineTextAlignment(.center)
            Text(body)
                .font(.footnote)
                .foregroundStyle(HnPalette.onInkMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            HnButton(title: "pauseRule.done", kind: .onInk) { close() }
                .padding(.top, 6)
        }
    }
}
