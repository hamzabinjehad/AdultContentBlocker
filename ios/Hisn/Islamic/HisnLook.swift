import SwiftUI

/// The Islamic edition's three looks, the same tokens as Android's `HisnLook`:
/// Fajr (dawn rose and indigo), Madinah (green and sand) and Layl (night indigo, lamp gold).
struct HisnLook: Equatable {
    let key: String
    let dark: Bool
    let sky, ink, sub, card, line, pri, priInk, acc, soft, deep, deepInk, helpTop, helpBottom, helpInk, chip: Color
    /// The flat red of "Help now", the same in every look.
    let help = Color(hex: 0xC8443C)
    let kept = Color(hex: 0x2FB36B)

    static let fajr = HisnLook(
        key: "fajr", dark: false, sky: Color(hex: 0xFBF1EC), ink: Color(hex: 0x241640), sub: Color(hex: 0x6D6280),
        card: .white, line: Color(hex: 0xEFE3EA), pri: Color(hex: 0x2B1A5E), priInk: .white, acc: Color(hex: 0xC8862F),
        soft: Color(hex: 0xF5EBF1), deep: Color(hex: 0x2B1A5E), deepInk: .white, helpTop: Color(hex: 0xFBE2DD),
        helpBottom: Color(hex: 0xF6D3D6), helpInk: Color(hex: 0x3A1830), chip: Color(hex: 0xEFE6F6))
    static let madinah = HisnLook(
        key: "madinah", dark: false, sky: Color(hex: 0xF7F4EA), ink: Color(hex: 0x13261E), sub: Color(hex: 0x5C6B62),
        card: .white, line: Color(hex: 0xE5E7DC), pri: Color(hex: 0x0F4D3A), priInk: .white, acc: Color(hex: 0xB8862B),
        soft: Color(hex: 0xEEF2E8), deep: Color(hex: 0x0F3D2F), deepInk: .white, helpTop: Color(hex: 0xF7EAD6),
        helpBottom: Color(hex: 0xF2DCC5), helpInk: Color(hex: 0x3A2614), chip: Color(hex: 0xE5EFE6))
    static let layl = HisnLook(
        key: "layl", dark: true, sky: Color(hex: 0x14132C), ink: Color(hex: 0xEFEBFA), sub: Color(hex: 0xA9A5C6),
        card: Color(hex: 0x1F1E3E), line: Color(hex: 0x302E57), pri: Color(hex: 0xE9C46A), priInk: Color(hex: 0x1B1530),
        acc: Color(hex: 0xE9C46A), soft: Color(hex: 0x24234A), deep: Color(hex: 0x0D0C24), deepInk: Color(hex: 0xEFEBFA),
        helpTop: Color(hex: 0x2C1A36), helpBottom: Color(hex: 0x3A1D33), helpInk: Color(hex: 0xF6E3EA), chip: Color(hex: 0x2A2952))

    static let all = [fajr, madinah, layl]
    static func named(_ key: String) -> HisnLook { all.first { $0.key == key } ?? fajr }

    var name: String {
        switch key {
        case "madinah": return ia("Madinah", "المدينة")
        case "layl": return ia("Layl", "الليل")
        default: return ia("Fajr", "الفجر")
        }
    }
    var background: String { "hisn_bg_\(key)" }
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

private struct HisnLookKey: EnvironmentKey { static let defaultValue = HisnLook.fajr }
extension EnvironmentValues {
    var hisnLook: HisnLook {
        get { self[HisnLookKey.self] }
        set { self[HisnLookKey.self] = newValue }
    }
}

/// Whether the screens are in Arabic: the person's choice in the app, else the iPhone's language.
func hisnArabic() -> Bool {
    let choice = UserDefaults.standard.string(forKey: "hisn.mobile.language") ?? "system"
    if choice == "ar" { return true }
    if choice == "en" { return false }
    return Locale.autoupdatingCurrent.language.languageCode?.identifier == "ar"
}

/// Marks a line as right-to-left. iOS lays a line out by the iPhone's own language, so in an Arabic screen on
/// an English or German iPhone a line that starts or ends with «, —, a number or a Latin word would otherwise put
/// those marks on the wrong end. The right-to-left marks are invisible.
func rtl(_ text: String) -> String { "\u{200F}" + text + "\u{200F}" }

/// One line in the screen's language. Arabic is written with 1 2 3 digits and always runs right to left;
/// English always runs left to right, even when it starts with an Arabic name.
func ia(_ en: String, _ ar: String) -> String { hisnArabic() ? rtl(ar) : "\u{200E}" + en + "\u{200E}" }

/// "سورة النور، الآية 30" in Arabic, "24:30" in English.
func hisnVerseRef(_ ref: String) -> String {
    guard hisnArabic() else { return ref }
    let parts = ref.split(separator: ":").map(String.init)
    guard parts.count == 2 else { return ref }
    return rtl("سورة \(HisnContent.surahNames[parts[0]] ?? parts[0])، الآية \(parts[1])")
}

// MARK: - Pieces every Hisn screen is built from

/// One of the painted objects (lantern, shield, ibrik...), from `HisnArt.xcassets`.
struct HisnIcon: View {
    let name: String
    let size: CGFloat
    init(_ name: String, _ size: CGFloat) { self.name = name; self.size = size }
    var body: some View {
        Image("hisn_ic_\(name)").resizable().scaledToFit().frame(width: size, height: size).accessibilityHidden(true)
    }
}

struct HisnTitle: View {
    @Environment(\.hisnLook) private var look
    let text: String
    var center = false
    var size: CGFloat = 28
    var light = false
    var body: some View {
        Text(text).font(.system(size: size, weight: .bold, design: hisnArabic() ? .default : .serif))
            .foregroundColor(light ? look.deepInk : look.ink)
            .multilineTextAlignment(center ? .center : .leading)
            .frame(maxWidth: .infinity, alignment: center ? .center : .leading)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }
}

struct HisnSub: View {
    @Environment(\.hisnLook) private var look
    let text: String
    var center = false
    var light = false
    var body: some View {
        Text(text).font(.system(size: 15)).lineSpacing(4)
            .foregroundColor(light ? look.deepInk.opacity(0.85) : look.sub)
            .multilineTextAlignment(center ? .center : .leading)
            .frame(maxWidth: .infinity, alignment: center ? .center : .leading)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// A verse in the Naskh hand between its marks, then where it is from. The text is never altered.
struct HisnVerse: View {
    @Environment(\.hisnLook) private var look
    let text: String
    let ref: String
    var light = false
    var size: CGFloat = 22
    var ink: Color?
    var body: some View {
        let mark = light ? Color(hex: 0xE9C46A) : look.acc
        let color = ink ?? (light ? look.deepInk : look.ink)
        VStack(spacing: 6) {
            (Text("\u{200F}﴿ ").foregroundColor(mark) + Text(text).foregroundColor(color) + Text(" ﴾\u{200F}").foregroundColor(mark))
                .font(.custom("GeezaPro", size: size)).lineSpacing(size * 0.55)
                .multilineTextAlignment(.center)
                .environment(\.layoutDirection, .rightToLeft)
                .frame(maxWidth: .infinity)
                .fixedSize(horizontal: false, vertical: true)
            Text(ref).font(.system(size: 12.5, weight: .semibold)).foregroundColor(color.opacity(0.75))
                .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// A scholar's words set like a page of the book: a rule above and below, the source after a dash.
struct HisnQuote: View {
    @Environment(\.hisnLook) private var look
    let text: String
    let source: String
    var english: String?
    var light = false
    var ink: Color?
    var body: some View {
        let color = ink ?? (light ? look.deepInk : look.ink)
        VStack(spacing: 8) {
            Rectangle().fill(color.opacity(0.25)).frame(height: 1)
            // The quotation is Arabic in every language of the app, so it always runs right to left.
            Text(rtl("«\(text)»")).font(.custom("GeezaPro", size: 17)).lineSpacing(9).foregroundColor(color)
                .multilineTextAlignment(.center).environment(\.layoutDirection, .rightToLeft)
                .frame(maxWidth: .infinity).fixedSize(horizontal: false, vertical: true)
            if let english, !hisnArabic() {
                Text(english).font(.system(size: 13)).foregroundColor(color.opacity(0.8)).multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text(hisnArabic() ? rtl("— \(source)") : "— \(source)").font(.system(size: 12, weight: .medium)).foregroundColor(color.opacity(0.7))
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            Rectangle().fill(color.opacity(0.25)).frame(height: 1)
        }
    }
}

/// The one filled action of a page.
struct HisnPrimaryButton: View {
    @Environment(\.hisnLook) private var look
    let label: String
    var white = false
    var color: Color?
    var enabled = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: 16, weight: .bold))
                .foregroundColor(color != nil ? .white : (white ? look.deep : look.priInk))
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(Capsule().fill(color ?? (white ? Color.white : look.pri)))
        }
        .buttonStyle(.plain)
        .opacity(enabled ? 1 : 0.45)
        .disabled(!enabled)
    }
}

extension View {
    /// A flat card: one fill and a hairline, no shadow, no gradient.
    func hisnCard(_ look: HisnLook, radius: CGFloat = 16, fill: Color? = nil, stroke: Color? = nil) -> some View {
        background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(fill ?? look.card))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(stroke ?? look.line, lineWidth: stroke == nil ? 1 : 1.5))
    }
}
