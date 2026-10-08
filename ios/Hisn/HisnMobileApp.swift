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
                .tint(.teal)
                .task { await protection.refresh() }
                .onChange(of: phase) { next in
                    if next == .active { Task { await protection.refresh() } }
                }
        }
    }

    private var isArabic: Bool {
        language == "ar" || (language == "system" && Locale.autoupdatingCurrent.language.languageCode?.identifier == "ar")
    }
}
