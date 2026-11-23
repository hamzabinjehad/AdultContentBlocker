import SwiftUI

/// Unrot's look, from the Glasshouse concept: a warm dark room lit from above,
/// gold for the one thing to do, serif numbers, soft glass cards. Always dark,
/// like Android's Unrot; text pairs keep at least 4.5:1 on the backdrop.
enum UPalette {
    static let ink = Color(red: 0.965, green: 0.933, blue: 0.867)        // #f6eedd
    static let dim = Color(red: 0.663, green: 0.616, blue: 0.525)        // #a99d86
    static let gold = Color(red: 0.949, green: 0.757, blue: 0.306)       // #f2c14e
    static let goldLight = Color(red: 1.0, green: 0.875, blue: 0.541)    // #ffdf8a
    static let goldDeep = Color(red: 0.851, green: 0.604, blue: 0.149)   // #d99a26
    static let onGold = Color(red: 0.165, green: 0.110, blue: 0.008)     // #2a1c02
    static let leaf = Color(red: 0.561, green: 0.827, blue: 0.604)       // #8fd39a
    static let leafBright = Color(red: 0.722, green: 0.949, blue: 0.604) // #b8f29a
    static let warn = Color(red: 1.0, green: 0.702, blue: 0.4)           // #ffb366
    static let warnText = Color(red: 1.0, green: 0.851, blue: 0.690)     // #ffd9b0
    static let night = Color(red: 0.804, green: 0.827, blue: 1.0)        // #cdd3ff
    static let card = Color.white.opacity(0.06)
    static let cardLine = Color.white.opacity(0.09)
    static let navBar = Color(red: 0.118, green: 0.102, blue: 0.086).opacity(0.94)
    static let base = Color(red: 0.027, green: 0.024, blue: 0.020)       // #070605
    static let warm = Color(red: 0.227, green: 0.157, blue: 0.063)       // #3a2810
    static let deep = Color(red: 0.071, green: 0.047, blue: 0.024)       // #120c06

    static let goldFill = LinearGradient(colors: [Color(red: 1, green: 0.89, blue: 0.604), gold, goldDeep],
                                         startPoint: .top, endPoint: .bottom)
    static let toolFill = LinearGradient(colors: [Color(red: 0.227, green: 0.208, blue: 0.188),
                                                  Color(red: 0.110, green: 0.098, blue: 0.086)],
                                         startPoint: .top, endPoint: .bottom)
}

extension Font {
    /// The concept's Fraunces role: iOS's own serif (New York).
    static func uDisplay(_ size: CGFloat, weight: Weight = .semibold) -> Font {
        .system(size: size, weight: weight, design: .serif)
    }
    static func uDisplay(_ style: TextStyle, weight: Weight = .semibold) -> Font {
        .system(style, design: .serif).weight(weight)
    }
}

/// The room: warm light over the glass, falling to near black.
struct UBackdrop: View {
    var mood: Mood = .day
    enum Mood { case day, night, wilt, warn }

    var body: some View {
        ZStack {
            UPalette.base
            RadialGradient(colors: [top, UPalette.deep, UPalette.base],
                           center: UnitPoint(x: 0.5, y: 0.14), startRadius: 0, endRadius: 520)
        }
        .ignoresSafeArea()
    }

    private var top: Color {
        switch mood {
        case .day: return UPalette.warm
        case .night: return Color(red: 0.106, green: 0.129, blue: 0.282)
        case .wilt: return Color(red: 0.173, green: 0.149, blue: 0.063)
        case .warn: return Color(red: 0.416, green: 0.180, blue: 0.055)
        }
    }
}

/// A soft glass card.
struct UCard<Content: View>: View {
    var padding: CGFloat = 16
    var highlight: Color? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(UPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(highlight ?? UPalette.cardLine, lineWidth: 1)
            }
    }
}

/// Small caps label over a card.
struct ULabel: View {
    let key: LocalizedStringKey
    init(_ key: LocalizedStringKey) { self.key = key }
    var body: some View {
        Text(key)
            .font(.caption2.weight(.bold))
            .tracking(1.2)
            .textCase(.uppercase)
            .foregroundStyle(UPalette.dim)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The one gold action. Big, round, glowing.
struct UGoldButton: View {
    let title: LocalizedStringKey
    var systemImage: String? = nil
    var pulse = false
    var wide = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage).font(.body.weight(.bold)) }
                Text(title).font(.headline.weight(.heavy))
            }
            .foregroundStyle(UPalette.onGold)
            .padding(.horizontal, 28)
            .padding(.vertical, 14)
            .frame(maxWidth: wide ? .infinity : nil, minHeight: 50)
            .background(UPalette.goldFill, in: Capsule())
            .shadow(color: UPalette.gold.opacity(0.45), radius: 14, y: 6)
            .overlay {
                if pulse {
                    Capsule().stroke(UPalette.gold.opacity(0.22), lineWidth: 8).padding(-6)
                }
            }
        }
        .buttonStyle(UPressStyle())
    }
}

/// A quiet pill for second actions.
struct UPillButton: View {
    let title: LocalizedStringKey
    var systemImage: String? = nil
    var tint: Color = UPalette.ink
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title).font(.subheadline.weight(.bold))
            }
            .foregroundStyle(tint)
            .padding(.horizontal, 18)
            .frame(minHeight: 46)
            .frame(maxWidth: .infinity)
            .background(UPalette.toolFill, in: Capsule())
            .overlay { Capsule().strokeBorder(Color.white.opacity(0.10), lineWidth: 1) }
        }
        .buttonStyle(UPressStyle())
    }
}

/// A round tool with its name under it.
struct UToolButton: View {
    let title: LocalizedStringKey
    let systemImage: String
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 7) {
                Image(systemName: systemImage)
                    .font(.system(size: 21, weight: .semibold))
                    .foregroundStyle(active ? UPalette.onGold : .white)
                    .frame(maxWidth: .infinity, minHeight: 58)
                    .background {
                        if active { Capsule().fill(UPalette.goldFill) } else { Capsule().fill(UPalette.toolFill) }
                    }
                    .overlay { Capsule().strokeBorder(Color.white.opacity(active ? 0 : 0.12), lineWidth: 1) }
                    .shadow(color: .black.opacity(0.4), radius: 6, y: 4)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(UPalette.dim)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .buttonStyle(UPressStyle())
    }
}

/// Status chip in the header ("Shield on", "2 open", "2 steps left").
struct UChip: View {
    let text: Text
    let tint: Color
    let fill: Color

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(tint).frame(width: 7, height: 7)
            text.font(.footnote.weight(.bold)).foregroundStyle(tint)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 32)
        .background(fill, in: Capsule())
    }
}

/// A small press that never fights Reduce Motion.
struct UPressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.96 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// A screen title row: big serif title and an optional trailing view.
struct UTitleBar<Trailing: View>: View {
    let title: LocalizedStringKey
    var subtitle: Text? = nil
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.uDisplay(30))
                    .foregroundStyle(UPalette.ink)
                    .accessibilityAddTraits(.isHeader)
                if let subtitle { subtitle.font(.caption.weight(.medium)).foregroundStyle(UPalette.dim) }
            }
            Spacer(minLength: 8)
            trailing
        }
        .padding(.top, 8)
    }
}

/// A sheet's dark surface with its title.
struct USheet<Content: View>: View {
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey? = nil
    var tall = false
    @ViewBuilder var content: Content
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color(red: 0.090, green: 0.075, blue: 0.051).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(title).font(.uDisplay(24)).foregroundStyle(UPalette.ink)
                                .accessibilityAddTraits(.isHeader)
                            if let subtitle {
                                Text(subtitle).font(.subheadline).foregroundStyle(UPalette.dim)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        Spacer()
                        Button { dismiss() } label: {
                            Image(systemName: "xmark").font(.footnote.weight(.bold))
                                .foregroundStyle(UPalette.ink)
                                .frame(width: 34, height: 34)
                                .background(Color.white.opacity(0.12), in: Circle())
                        }
                        .accessibilityLabel(Text("u.close"))
                    }
                    content
                }
                .padding(20)
            }
        }
        .presentationDetents(tall ? [.large] : [.medium, .large])
        .presentationDragIndicator(.visible)
        .preferredColorScheme(.dark)
    }
}

/// A choice in a list of options: round, gold when picked.
struct UOption: View {
    private static let marks = [["circle", "largecircle.fill.circle"], ["square", "checkmark.square.fill"]]
    let title: LocalizedStringKey
    var detail: LocalizedStringKey? = nil
    var systemImage: String? = nil
    let selected: Bool
    var multiple = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if let systemImage {
                    Image(systemName: systemImage).font(.body.weight(.semibold))
                        .foregroundStyle(selected ? UPalette.goldLight : UPalette.ink)
                        .frame(width: 26)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.body.weight(.bold))
                        .foregroundStyle(selected ? UPalette.goldLight : UPalette.ink)
                    if let detail {
                        Text(detail).font(.footnote).foregroundStyle(UPalette.dim)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                Spacer(minLength: 8)
                Image(systemName: Self.marks[multiple ? 1 : 0][selected ? 1 : 0])
                    .foregroundStyle(selected ? UPalette.gold : UPalette.dim)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(selected ? UPalette.gold.opacity(0.12) : UPalette.card,
                        in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(selected ? UPalette.gold : UPalette.cardLine, lineWidth: 1)
            }
        }
        .buttonStyle(UPressStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// A key built from a prefix and a value ("u.pane." + "safari"). Every key it
/// can build is listed in Localizable.strings; UnrotCoreTests checks them.
func uKey(_ prefix: String, _ value: CustomStringConvertible) -> LocalizedStringKey {
    LocalizedStringKey(prefix + value.description)
}
