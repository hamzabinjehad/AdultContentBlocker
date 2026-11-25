import SwiftUI
import UIKit

/// "Help now": what Islam advises for the hard minute first (wuḍūʾ and prayer, seeking refuge,
/// patience in a test), then the person's own line and promises, then what they chose to do instead.
struct HisnHelpView: View {
    @Environment(\.hisnLook) private var look
    @AppStorage("hisn.onboard.reason") private var reason = ""
    @AppStorage("hisn.onboard.instead") private var instead = "wudu,quran,rakah"
    let onBreathe: () -> Void
    var onSlip: (() -> Void)? = nil
    let onClose: () -> Void

    var body: some View {
        ZStack {
            LinearGradient(colors: [look.helpTop, look.helpBottom], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Text(ia("Help now", "ساعدني الآن")).font(.system(size: 17, weight: .bold)).foregroundColor(look.helpInk)
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark").font(.system(size: 17, weight: .semibold)).foregroundColor(look.helpInk).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(ia("Close", "إغلاق"))
                }
                .padding(.horizontal, 18)
                ScrollView {
                    VStack(spacing: 10) {
                        HisnIcon("phonedown", 74)
                        Text(ia("You made a promise to Allah, and He is near", "عاهدتَ الله، وهو قريب"))
                            .font(.system(size: 26, weight: .semibold)).foregroundColor(look.helpInk).multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                        HisnVerse(text: "فَإِنِّى قَرِيبٌ", ref: ia("“Then surely I am nigh.” (Pickthall) · al-Baqara 186", "سورة البقرة، الآية 186"),
                                  size: 24, ink: look.helpInk)
                        let mine = reason.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !mine.isEmpty {
                            Text(ia("“\(mine)”", "«\(mine)»")).font(.system(size: 17, weight: .semibold)).foregroundColor(look.helpInk)
                                .multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(14).background(panel)
                        }
                        ForEach(Array(HisnContent.advice.enumerated()), id: \.offset) { _, advice in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack(spacing: 10) {
                                    if let icon = advice.icon { HisnIcon(icon, 34) }
                                    Text(ia(advice.en, advice.ar)).font(.system(size: 16, weight: .bold)).foregroundColor(look.helpInk)
                                    Spacer(minLength: 0)
                                }
                                HisnVerse(text: advice.verse,
                                          ref: hisnArabic() ? rtl(advice.refAr) : "“\(advice.meaning)” (Pickthall) · \(advice.refEn)",
                                          size: 19, ink: look.helpInk)
                                Text(ia(advice.bodyEn, advice.bodyAr)).font(.system(size: 13.5)).foregroundColor(look.helpInk.opacity(0.85))
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .padding(14).background(panel)
                        }
                        Text(ia("What you promised:", "ما عاهدتَ عليه:")).font(.system(size: 13)).foregroundColor(look.helpInk.opacity(0.8))
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 6)
                        ForEach(HisnContent.promises, id: \.0) { line in
                            HStack(spacing: 8) {
                                Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundColor(look.kept)
                                Text(ia(line.0, line.1)).font(.system(size: 14.5, weight: .medium)).foregroundColor(look.helpInk)
                                Spacer(minLength: 0)
                            }
                            .padding(12).background(panel)
                        }
                        let mineInstead = HisnContent.instead.filter { Set(instead.split(separator: ",").map(String.init)).contains($0.key) }
                        if !mineInstead.isEmpty {
                            Text(ia("Then get up and do one of yours:", "ثم قم وافعل واحدًا مما اخترت:")).font(.system(size: 13))
                                .foregroundColor(look.helpInk.opacity(0.8)).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 6)
                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                                ForEach(mineInstead) { option in
                                    HStack(spacing: 6) {
                                        HisnIcon(option.icon, 30)
                                        Text(option.label).font(.system(size: 13.5, weight: .medium)).foregroundColor(look.helpInk)
                                        Spacer(minLength: 0)
                                    }
                                    .padding(8).background(panel)
                                }
                            }
                        }
                        HisnPrimaryButton(label: ia("Breathe with dhikr", "تنفّس مع الذكر"), color: look.help, action: onBreathe).padding(.top, 10)
                        if let onSlip {
                            Button(action: onSlip) {
                                Text(ia("I slipped. How do I come back?", "زللتُ، كيف أعود؟")).font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(look.helpInk).frame(maxWidth: .infinity).padding(.vertical, 12)
                            }
                        }
                    }
                    .padding(.horizontal, 18).padding(.bottom, 24).frame(maxWidth: 560).frame(maxWidth: .infinity)
                }
            }
        }
        .preferredColorScheme(look.dark ? .dark : .light)
    }

    private var panel: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(look.dark ? 0.08 : 0.7))
    }
}

/// Breathe with dhikr, as in the approved prototype: three rounds, a slow breath in and a breath out
/// with «أستغفر الله», then "It passed". The phone taps lightly at each change so the eyes can close.
struct HisnBreatheView: View {
    @Environment(\.hisnLook) private var look
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let onClose: () -> Void
    @State private var phase = 0          // 0, 1 = round 1 in/out ... 5 = round 3 out, 6 = done
    @State private var timer: Timer?

    private var done: Bool { phase >= 6 }
    private var inhale: Bool { phase % 2 == 0 }

    var body: some View {
        ZStack {
            LinearGradient(colors: [look.helpTop, look.helpBottom], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 16) {
                HStack {
                    Text(ia("Take control", "تمالك نفسك")).font(.system(size: 17, weight: .bold)).foregroundColor(look.helpInk)
                    Spacer()
                    Button(action: close) {
                        Image(systemName: "xmark").font(.system(size: 17, weight: .semibold)).foregroundColor(look.helpInk).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(ia("Close", "إغلاق"))
                }
                Text(ia("Let's pause together", "لنتوقّف معًا")).font(.system(size: 26, weight: .bold)).foregroundColor(look.helpInk)
                Text(ia("Urges pass; your promise stays. Breathe with the circle.", "الرغبة تمضي، ووعدك يبقى. تنفّس مع الدائرة."))
                    .font(.system(size: 15)).foregroundColor(look.helpInk.opacity(0.8)).multilineTextAlignment(.center)
                Spacer(minLength: 10)
                ZStack {
                    Circle().fill(look.help.opacity(look.dark ? 0.35 : 0.18))
                        .scaleEffect(done ? 0.8 : (inhale ? 1 : 0.55))
                        .animation(reduceMotion ? nil : .easeInOut(duration: 4), value: phase)
                    Text(done ? ia("It passed", "مضت") : (inhale ? ia("Breathe in", "شهيق") : ia("Breathe out", "زفير")))
                        .font(.system(size: 22, weight: .bold)).foregroundColor(look.helpInk)
                }
                .frame(width: 230, height: 230)
                .accessibilityElement(children: .combine)
                Text("\u{200F}أَسْتَغْفِرُ ٱللَّٰهَ\u{200F}").font(.custom("GeezaPro", size: 26)).foregroundColor(look.helpInk)
                    .opacity(done || !inhale ? 1 : 0.35)
                Text(done ? ia("Alḥamdulillāh", "الحمد لله") : ia("Round \(phase / 2 + 1) of 3", "الجولة \(phase / 2 + 1) من 3"))
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(look.helpInk.opacity(0.75))
                Spacer(minLength: 10)
                HisnPrimaryButton(label: done ? ia("Back", "عودة") : ia("Stop", "توقّف"), color: look.help, action: close)
            }
            .padding(.horizontal, 22).padding(.bottom, 12).frame(maxWidth: 560)
        }
        .preferredColorScheme(look.dark ? .dark : .light)
        .onAppear(perform: start)
        .onDisappear { timer?.invalidate() }
    }

    private func start() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 4, repeats: true) { t in
            phase += 1
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
            if phase >= 6 {
                t.invalidate()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }

    private func close() {
        timer?.invalidate()
        onClose()
    }
}
