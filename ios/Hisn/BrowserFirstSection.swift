import SwiftUI

/// Separate from the four domain blockers. Permission on one website is not
/// evidence of coverage on other websites, profiles or private browsing.
struct BrowserFirstSection: View {
    @ScaledMetric(relativeTo: .subheadline) private var badge: CGFloat = 28

    var body: some View {
        Section("browserfirst.title") {
            Text("browserfirst.body")
                .font(.callout)
            Label("browserfirst.unverified", systemImage: "exclamationmark.shield")
                .font(.callout.weight(.medium))
                .foregroundStyle(HisnPalette.warning)
            step(1, title: "browserfirst.enable.title", detail: "browserfirst.enable.body")
            step(2, title: "browserfirst.access.title", detail: "browserfirst.access.body")
            step(3, title: "browserfirst.visual.title", detail: "browserfirst.visual.body")
            Text("browserfirst.visual.appearance")
                .font(.footnote).foregroundStyle(.secondary)
            Text("browserfirst.visual.unverified")
                .font(.footnote).foregroundStyle(.secondary)
            DisclosureGroup("browserfirst.details") {
                Text("browserfirst.appOnly").font(.footnote).foregroundStyle(.secondary)
                Text("browserfirst.limits").font(.footnote).foregroundStyle(.secondary)
            }
        }
        .accessibilityIdentifier("setup.browserFirstGuide")
    }

    private func step(_ number: Int, title: LocalizedStringKey,
                      detail: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(verbatim: "\(number)")
                .font(.subheadline.weight(.semibold)).monospacedDigit()
                .frame(width: badge, height: badge)
                .background(.quaternary, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail).font(.footnote).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }
}
