import SwiftUI

/// The Islamic edition's face on iPhone: the first run from the approved prototype, then the prototype's
/// five tabs (Home, Progress, Help, Library, Tools). Tools holds the protection screens unchanged.
struct HisnIslamicRoot: View {
    @ObservedObject var protection: ProtectionController
    @Binding var language: String
    @AppStorage("hisn.onboard.done") private var onboarded = false
    @AppStorage("hisn.look") private var lookKey = "fajr"

    var body: some View {
        let look = HisnLook.named(lookKey)
        Group {
            if onboarded {
                HisnShell(protection: protection, language: $language)
            } else {
                HisnOnboardingView(onDone: { onboarded = true }, protection: protection)
            }
        }
        .hisnEnvironment(look)
    }
}
