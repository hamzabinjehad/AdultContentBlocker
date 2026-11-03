import SwiftUI

@main
struct HisnMobileApp: App {
    @StateObject private var protection = ProtectionController()
    @AppStorage("hisn.mobile.language") private var language = "system"
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            MobileRootView(protection: protection, language: $language)
                .environment(\.locale, language == "system" ? .autoupdatingCurrent : Locale(identifier: language))
                .environment(\.layoutDirection, isArabic ? .rightToLeft : .leftToRight)
                .tint(.blue)
                .task { await protection.refresh() }
                .onChange(of: phase) { next in
                    if next == .active { Task { await protection.refresh() } }
                }
                .onAppear { storeShieldLanguage() }
                .onChange(of: language) { _ in storeShieldLanguage() }
        }
    }

    /// The shield extensions run in their own processes, so the language choice
    /// is written where they can read it. "system" lets them follow the device.
    private func storeShieldLanguage() {
        ShieldTextPolicy.store(languageChoice: language,
                               defaults: UserDefaults(suiteName: AppUsageConfiguration.group))
    }

    private var isArabic: Bool {
        language == "ar" || (language == "system" && Locale.autoupdatingCurrent.language.languageCode?.identifier == "ar")
    }
}
