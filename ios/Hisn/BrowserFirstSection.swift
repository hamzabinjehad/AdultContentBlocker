import SwiftUI

/// Separate from the domain blockers. Permission on one website is not
/// evidence of coverage on other websites, profiles or private browsing.
///
/// The setup steps stay visible; the long explanation of what the scanner can
/// and cannot see is behind one disclosure, so the section is scannable first.
struct BrowserFirstSection: View {
    @ScaledMetric(relativeTo: .subheadline) private var badge: CGFloat = 28

    var body: some View {
        Section("browserfirst.title") {
            Text("browserfirst.summary")
                .font(.callout)
                .fixedSize(horizontal: false, vertical: true)
            Label("browserfirst.unverified", systemImage: "exclamationmark.shield")
                .font(.callout.weight(.medium))
                .foregroundStyle(HisnPalette.warning)

            step(1, title: "browserfirst.enable.title", detail: "browserfirst.enable.short")
            step(2, title: "browserfirst.access.title", detail: "browserfirst.access.short")
            step(3, title: "browserfirst.visual.title", detail: "browserfirst.visual.short")

            DisclosureGroup("browserfirst.details") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("browserfirst.body")
                    Text("browserfirst.steps")
                    Text("browserfirst.enable.body")
                    Text("browserfirst.access.body")
                    Text("browserfirst.visual.body")
                    Text("browserfirst.visual.appearance")
                    Text("browserfirst.visual.unverified")
                    Text("browserfirst.appOnly")
                    Text("browserfirst.limits")
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.top, 6)
            }
            .font(.footnote)
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
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail).font(.footnote).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
