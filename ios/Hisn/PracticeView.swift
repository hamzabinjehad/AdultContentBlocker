import SwiftUI

/// The practice tab: the pause as a habit rather than an emergency.
///
/// Nothing here changes a rule. A finished pause adds one mark to the week and
/// one day to the streak; Hisn still writes down no reason and no history.
struct PracticeView: View {
    let streak: Int
    let practiceDays: Set<String>
    let pauseCount: Int?
    let now: Date
    let onPause: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HnIntro(title: "practice.title", subtitle: "practice.subtitle")
                startCard
                StreakCard(streak: streak, practiceDays: practiceDays, now: now,
                           pauseCount: pauseCount)
                whatCard
                promiseCard
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .padding(.bottom, 104)
            .hnColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HnPalette.canvas)
    }

    private var startCard: some View {
        HnInkCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .center, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        HnBadge(text: "practice.badge", tint: HnPalette.onInkAccent, onInk: true)
                        Text("practice.start.title")
                            .font(.system(.title, design: .rounded).weight(.bold))
                            .foregroundStyle(HnPalette.onInk)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("practice.start.detail")
                            .font(.subheadline)
                            .foregroundStyle(HnPalette.onInkMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    if !dynamicTypeSize.isAccessibilitySize {
                        breathPreview
                    }
                }
                HnButton(title: "practice.start.action", systemImage: "wind",
                         kind: .onInk, action: onPause)
                Text("practice.start.footnote")
                    .font(.caption)
                    .foregroundStyle(HnPalette.onInkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var breathPreview: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.14), lineWidth: 1.5)
                .frame(width: 104, height: 104)
            Circle()
                .fill(RadialGradient(colors: [HnPalette.accent.opacity(0.55),
                                              HnPalette.accent.opacity(0.12)],
                                     center: .center, startRadius: 4, endRadius: 52))
                .frame(width: 78, height: 78)
            HnMark(size: 30, tint: .white)
        }
        .accessibilityHidden(true)
    }

    private var whatCard: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("practice.what.title")
                    .font(.hnHeadline())
                    .foregroundStyle(HnPalette.ink)
                bullet(systemImage: "hourglass", "practice.what.one")
                bullet(systemImage: "questionmark.circle", "practice.what.two")
                bullet(systemImage: "lock", "practice.what.three")
            }
        }
    }

    private func bullet(systemImage: String, _ text: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(HnPalette.accent)
                .frame(width: 30, height: 30)
                .background(HnPalette.accentSoft, in: Circle())
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote)
                .foregroundStyle(HnPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 5)
            Spacer(minLength: 0)
        }
    }

    private var promiseCard: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "hand.raised")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(HnPalette.muted)
                .accessibilityHidden(true)
            Text("practice.privacy")
                .font(.caption)
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 4)
    }
}

/// The week's practice: a number, seven dots, and today's count. Shared by
/// Today and Practice so the streak can never disagree with itself.
struct StreakCard: View {
    let streak: Int
    let practiceDays: Set<String>
    let now: Date
    let pauseCount: Int?

    @Environment(\.locale) private var locale

    var body: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(verbatim: "\(streak)")
                        .font(.hnNumber())
                        .foregroundStyle(HnPalette.ink)
                        .accessibilityHidden(true)
                    Text(streak == 1 ? "today.streak.one" : "today.streak.many")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(HnPalette.ink)
                    Spacer(minLength: 8)
                    if let pauseCount, pauseCount > 0 {
                        HnBadge(text: "today.pauses \(pauseCount)", tint: HnPalette.calm)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("today.streak.a11y \(streak)"))
                HnWeekDots(days: weekDots)
                Text(streak == 0 ? "today.streak.hint" : "today.streak.body")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityIdentifier("today.streakCard")
    }

    /// Oldest first, seven days, ending today. The letters follow the locale.
    private var weekDots: [HnWeekDots.Day] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        let symbols = calendar.veryShortWeekdaySymbols
        var dots: [HnWeekDots.Day] = []
        for offset in stride(from: 6, through: 0, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: now) else { continue }
            let weekday = calendar.component(.weekday, from: day)
            dots.append(.init(letter: symbols[(weekday - 1) % 7],
                              done: practiceDays.contains(PauseRule.dayKey(day)),
                              today: offset == 0))
        }
        return dots
    }
}
