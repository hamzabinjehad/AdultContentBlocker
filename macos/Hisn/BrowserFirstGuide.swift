import SwiftUI

/// Guidance only: displaying this view never blocks an app or changes policy.
struct BrowserFirstGuide: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Use a protected browser for mixed-content services", systemImage: "text.magnifyingglass")
                .font(.title3.weight(.semibold))
            Text("Keep a service's website available while checking its text. In supported Chromium browsers, Hisn checks changing page text and individually hides detected X/Twitter posts; one detected post does not add the whole service to your blocked sites.")
                .font(.callout).fixedSize(horizontal: false, vertical: true)
            Text("If you choose, use Add app below to block the native app's new internet connections, then use its website with the Hisn extension connected. Do not add the service's domain to blocked sites if you want its website to remain available. Existing connections and cached or offline content are not removed.")
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Button("Review text checking") { AppNavigation.shared.open(.settings) }
                .controlSize(.small)
            DisclosureGroup("What text checking cannot detect") {
                Text("Text checking uses words, captions and image descriptions, not image or video classification. Media without meaningful text can be missed. Checking takes time, requires the extension and website access, and can make mistakes. Unsupported X/Twitter layouts remain unverified; other sites use page-level checking. Deliberate domain blocks and strict-mode rules still apply.")
                    .font(.callout).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("rules.browserFirstGuide")
    }
}
