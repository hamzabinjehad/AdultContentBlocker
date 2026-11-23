import SwiftUI
import WidgetKit

/// The sprout on the Home Screen: the painting for its stage and glass, the
/// stage, and time given back. Reads only the snapshot the app writes.
struct UnrotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

struct UnrotProvider: TimelineProvider {
    static let sample = WidgetSnapshot(givenBack: 710, day: 9, crackedAt: 0, sealedAt: 0, brokenAt: 0,
                                       sealedDay: nil, night: false, updated: Date())

    func placeholder(in context: Context) -> UnrotEntry { UnrotEntry(date: Date(), snapshot: Self.sample) }

    func getSnapshot(in context: Context, completion: @escaping (UnrotEntry) -> Void) {
        completion(UnrotEntry(date: Date(), snapshot: context.isPreview ? Self.sample : load() ?? Self.sample))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<UnrotEntry>) -> Void) {
        let now = Date()
        let snapshot = load()
        var entries = [UnrotEntry(date: now, snapshot: snapshot)]
        if let change = snapshot?.nextChange(after: now) { entries.append(UnrotEntry(date: change, snapshot: snapshot)) }
        // A new day can change the gold seam; the app also reloads on every open.
        let midnight = Calendar.current.startOfDay(for: now.addingTimeInterval(86400))
        entries.append(UnrotEntry(date: midnight, snapshot: snapshot))
        completion(Timeline(entries: entries, policy: .after(midnight.addingTimeInterval(3600))))
    }

    private func load() -> WidgetSnapshot? {
        guard let data = UserDefaults(suiteName: Edition.appGroup)?.data(forKey: WidgetSnapshot.key) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }
}

struct UnrotWidgetView: View {
    let entry: UnrotEntry
    @Environment(\.widgetFamily) private var family

    private var givenBack: Int { entry.snapshot?.givenBack ?? 0 }
    private var stage: Int { GlassStage.stage(givenBackMinutes: givenBack) }
    private var glass: GlassState { entry.snapshot?.glass(at: entry.date) ?? GlassState(look: .clear, sealLeft: 0) }
    private var art: String { GlassStage.art(stage: stage, look: glass.look, night: entry.snapshot?.night ?? false) }

    private var stageName: Text {
        switch glass.look {
        case .cracked: return Text("u.glass.cracked")
        case .gold: return Text("u.glass.gold")
        case .broken: return Text("u.glass.broken")
        case .clear: return Text(LocalizedStringKey(GlassStage.nameKeys[stage]))
        }
    }

    var body: some View {
        switch family {
        case .accessoryCircular, .accessoryRectangular, .accessoryInline:
            lockScreen.widgetURL(UnrotLink.breathe.url).modifier(ClearBackground())
        default:
            homeScreen
        }
    }

    /// Lock Screen: a ring to the next stage, or the number in a line. Tapping opens Breathe.
    @ViewBuilder private var lockScreen: some View {
        let part = GlassStage.part(givenBackMinutes: givenBack)
        switch family {
        case .accessoryCircular:
            Gauge(value: part) {
                Image(systemName: "leaf.fill")
            } currentValueLabel: {
                Text(verbatim: UnrotFormat.short(minutes: givenBack).replacingOccurrences(of: " ", with: ""))
                    .minimumScaleFactor(0.5)
            }
            .gaugeStyle(.accessoryCircular)
            .accessibilityLabel(Text("u.glass.timerA11y \(UnrotFormat.short(minutes: givenBack))"))
        case .accessoryInline:
            Label {
                Text("u.glass.timerA11y \(UnrotFormat.short(minutes: givenBack))")
            } icon: { Image(systemName: "leaf.fill") }
        default:
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Image(systemName: "leaf.fill")
                    stageName.font(.caption2.weight(.heavy)).textCase(.uppercase)
                }
                Text("u.glass.timerA11y \(UnrotFormat.short(minutes: givenBack))")
                    .font(.headline).minimumScaleFactor(0.7).lineLimit(1)
                Text("u.widget.tapBreathe").font(.caption2).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var homeScreen: some View {
        Group {
            if entry.snapshot == nil {
                VStack(spacing: 6) {
                    Image("glass_seed").resizable().scaledToFit()
                    Text("u.widget.open").font(.caption2).foregroundStyle(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }
            } else if family == .systemMedium {
                HStack(spacing: 12) {
                    picture
                    VStack(alignment: .leading, spacing: 4) { words; next }
                    Spacer(minLength: 0)
                }
            } else {
                VStack(spacing: 2) {
                    picture.frame(maxHeight: 90)
                    words.multilineTextAlignment(.center)
                }
            }
        }
        .modifier(WidgetBackground())
    }

    private var picture: some View {
        Image(art).resizable().scaledToFit()
            // A soft round fade that scales with the widget, so the painting's square never shows.
            .mask(Circle().padding(6).blur(radius: 7))
            .accessibilityHidden(true)
    }

    private var words: some View {
        VStack(alignment: family == .systemMedium ? .leading : .center, spacing: 1) {
            stageName.font(.caption2.weight(.heavy)).textCase(.uppercase)
                .foregroundStyle(Color(red: 1, green: 0.875, blue: 0.541))
            Text(verbatim: UnrotFormat.short(minutes: givenBack))
                .font(.system(size: family == .systemMedium ? 30 : 20, weight: .semibold, design: .serif))
                .foregroundStyle(Color(red: 0.965, green: 0.933, blue: 0.867))
                .minimumScaleFactor(0.6).lineLimit(1)
            Text("u.glass.givenBack").font(.caption2).foregroundStyle(Color(red: 0.663, green: 0.616, blue: 0.525))
        }
    }

    @ViewBuilder private var next: some View {
        if glass.look == .clear, let left = GlassStage.minutesToNext(givenBackMinutes: givenBack) {
            let nextName = Text(LocalizedStringKey(GlassStage.nameKeys[stage + 1]))
            let leftText = UnrotFormat.short(minutes: left)
            Text("u.stageUp.next \(nextName) \(leftText)")
                .font(.caption2).foregroundStyle(Color(red: 0.663, green: 0.616, blue: 0.525))
        } else if glass.look == .cracked {
            Text("u.glass.sealButton").font(.caption2.weight(.semibold))
                .foregroundStyle(Color(red: 1, green: 0.875, blue: 0.541))
        }
    }
}

/// The warm room behind the glass, on iOS 17's widget background when available.
private struct WidgetBackground: ViewModifier {
    private var room: some View {
        RadialGradient(colors: [Color(red: 0.227, green: 0.157, blue: 0.063), Color(red: 0.071, green: 0.047, blue: 0.024)],
                       center: UnitPoint(x: 0.5, y: 0.2), startRadius: 0, endRadius: 200)
    }
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.containerBackground(for: .widget) { room }
        } else {
            content.padding().background(room)
        }
    }
}

/// Lock Screen widgets take the system's own backdrop.
private struct ClearBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.containerBackground(for: .widget) { Color.clear }
        } else {
            content
        }
    }
}

@main
struct UnrotWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "UnrotSprout", provider: UnrotProvider()) { entry in
            UnrotWidgetView(entry: entry)
        }
        .configurationDisplayName(Text("u.widget.name"))
        .description(Text("u.widget.description"))
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}
