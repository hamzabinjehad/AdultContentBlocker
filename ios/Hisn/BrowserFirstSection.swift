import SwiftUI

/// Separate from the four domain blockers. Permission on one website is not
/// evidence of coverage on other websites, profiles or private browsing.
struct BrowserFirstSection: View {
    var body: some View {
        Section("browserfirst.title") {
            Text("browserfirst.body")
            Label("browserfirst.unverified", systemImage: "exclamationmark.shield")
                .foregroundStyle(.orange)
            Text("browserfirst.steps").font(.footnote).foregroundStyle(.secondary)
            Text("browserfirst.appOnly").font(.footnote).foregroundStyle(.secondary)
            Text("browserfirst.limits").font(.footnote).foregroundStyle(.secondary)
        }
        .accessibilityIdentifier("setup.browserFirstGuide")
    }
}
