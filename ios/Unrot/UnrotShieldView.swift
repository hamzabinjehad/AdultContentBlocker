import SwiftUI

/// The dome's state as Home and Shield read it. Built from what iOS reports back
/// (configuration readback, never a claim that every route is filtered).
struct UnrotShieldFacts {
    enum Pane: String, CaseIterable, Identifiable {
        case feeds, safari, everyApp, screenTime, lock
        var id: String { rawValue }
        var group: UnrotShieldView.Tab { self == .feeds ? .feeds : self == .lock ? .all : .adult }
    }

    struct Banner { let title: LocalizedStringKey; let detail: LocalizedStringKey }

    var on: [Pane: Bool] = [:]
    var setupLeft = 0
    var quietNow = false
    var quietEnds: String?
    var feedWatched = false
    var listCount = 0
    var customSites = 0
    var ruleApps = 0
    var checked = false

    var openPanes: [Pane] { Pane.allCases.filter { $0 != .lock && on[$0] != true } }
    /// What to close first: adult layers before feeds, like the Home banner.
    var firstFix: Pane? { [Pane.safari, .screenTime, .everyApp, .feeds].first { on[$0] != true } }

    var chip: ShieldSummary.Chip {
        ShieldSummary.chip(openPanes: checked ? openPanes.count : 0, setupLeft: 0, quietNow: quietNow)
    }

    var chipView: some View {
        guard checked else {
            return UChip(text: Text("u.chip.checking"), tint: UPalette.dim, fill: Color.white.opacity(0.08))
        }
        switch chip {
        case .on:
            return UChip(text: Text("u.chip.on"), tint: UPalette.leafBright, fill: UPalette.leaf.opacity(0.16))
        case .night:
            return UChip(text: Text("u.chip.night"), tint: UPalette.night, fill: UPalette.night.opacity(0.18))
        case .open(let n):
            return UChip(text: Text("u.chip.open \(n)"), tint: Color(red: 1, green: 0.77, blue: 0.54),
                         fill: UPalette.warn.opacity(0.2))
        case .steps(let n):
            return UChip(text: Text("u.chip.steps \(n)"), tint: Color(red: 1, green: 0.77, blue: 0.54),
                         fill: UPalette.warn.opacity(0.2))
        }
    }

    /// The one thing to fix first, adult layers before feeds.
    var banner: Banner? {
        guard checked else { return nil }
        if on[.safari] != true { return Banner(title: "u.banner.safari", detail: "u.banner.safariDetail") }
        if on[.screenTime] != true { return Banner(title: "u.banner.screenTime", detail: "u.banner.screenTimeDetail") }
        if on[.feeds] != true { return Banner(title: "u.banner.feeds", detail: "u.banner.feedsDetail") }
        return nil
    }
}

/// Shield, laid out like CleanPath's blocker: the glass as the status with an
/// amber ring when a pane is open, one big fix button, tabs, the panes, the
/// numbers, and one way to add a rule. Each pane opens the full, existing setup.
struct UnrotShieldView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice
    let facts: UnrotShieldFacts
    let onAddRule: () -> Void

    enum Tab: String, CaseIterable { case all, feeds, adult }
    @State private var tab: Tab = .all
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                UTitleBar(title: "u.tab.shield") {
                    Button(action: onAddRule) {
                        Label("u.shield.add", systemImage: "plus")
                            .font(.footnote.weight(.heavy))
                            .foregroundStyle(UPalette.leafBright)
                            .padding(.horizontal, 14).frame(minHeight: 34)
                            .background(UPalette.leaf.opacity(0.18), in: Capsule())
                    }
                    .buttonStyle(UPressStyle())
                }
                hero
                tabs
                VStack(spacing: 10) {
                    ForEach(UnrotShieldFacts.Pane.allCases.filter { tab == .all || $0.group == tab }) { pane in
                        NavigationLink { destination(pane) } label: { row(pane) }
                            .buttonStyle(UPressStyle())
                    }
                }
                numbers
                Text("u.shield.scope").font(.caption).foregroundStyle(UPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
                NavigationLink {
                    ProtectionDetailView(protection: protection, usage: usage, vault: vault, lockDevice: lockDevice)
                } label: {
                    Text("u.shield.everything").font(.footnote.weight(.bold)).foregroundStyle(UPalette.goldLight)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 120)
            .frame(maxWidth: sizeClass == .regular ? 560 : .infinity)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .refreshable { await protection.refresh() }
    }

    // MARK: - Hero

    private var open: Int { facts.checked ? facts.openPanes.count : 0 }

    private var hero: some View {
        VStack(spacing: 6) {
            ZStack {
                if open > 0 {
                    Circle()
                        .strokeBorder(UPalette.warn.opacity(0.7), style: StrokeStyle(lineWidth: 3, dash: [8, 6]))
                        .frame(width: 190, height: 190)
                }
                Image(open > 0 ? "glass_cracked" : "glass_sprout")
                    .resizable().scaledToFit().frame(width: 230)
                    .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 115))
                    .accessibilityHidden(true)
            }
            .frame(height: 200)
            Text(!facts.checked ? "u.shield.checking" : open == 0 ? "u.shield.allClosed" : "u.shield.open \(open)")
                .font(.headline.weight(.heavy)).foregroundStyle(UPalette.ink)
                .accessibilityAddTraits(.isHeader)
            Text(!facts.checked ? "u.shield.checkingSub" : open == 0 ? "u.shield.allClosedSub" : "u.shield.openSub")
                .font(.footnote).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
            if open > 0, let first = facts.firstFix {
                let paneName = Text(uKey("u.pane.", first.rawValue))
                NavigationLink { destination(first) } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "wrench.and.screwdriver.fill")
                        Text("u.shield.fix \(paneName)")
                    }
                    .font(.subheadline.weight(.heavy))
                    .foregroundStyle(Color(red: 0.165, green: 0.071, blue: 0.024))
                    .padding(.horizontal, 26).frame(minHeight: 50)
                    .background(LinearGradient(colors: [UPalette.warn, Color(red: 0.878, green: 0.478, blue: 0.18)],
                                               startPoint: .top, endPoint: .bottom), in: Capsule())
                    .shadow(color: Color(red: 0.878, green: 0.478, blue: 0.18).opacity(0.45), radius: 12, y: 6)
                }
                .buttonStyle(UPressStyle())
                .padding(.top, 6)
            }
        }
    }

    private var tabs: some View {
        HStack(spacing: 4) {
            ForEach(Tab.allCases, id: \.self) { value in
                Button { tab = value } label: {
                    Text(uKey("u.shield.tab.", value.rawValue))
                        .font(.footnote.weight(tab == value ? .bold : .regular))
                        .foregroundStyle(tab == value ? Color(red: 0.14, green: 0.09, blue: 0.03) : UPalette.dim)
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .background(tab == value ? Color(red: 1, green: 0.97, blue: 0.90) : .clear, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(tab == value ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Color.white.opacity(0.08), in: Capsule())
    }

    // MARK: - Panes

    private func icon(_ pane: UnrotShieldFacts.Pane) -> String { Self.icons[pane] ?? "shield" }
    private static let icons: [UnrotShieldFacts.Pane: String] = [.feeds: "apps.iphone", .safari: "safari",
        .everyApp: "network", .screenTime: "hourglass", .lock: "lock"]

    private func row(_ pane: UnrotShieldFacts.Pane) -> some View {
        let isOn = facts.on[pane] == true
        return HStack(spacing: 12) {
            Image(systemName: icon(pane)).font(.body.weight(.semibold)).foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(UPalette.toolFill, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(uKey("u.pane.", pane.rawValue)).font(.subheadline.weight(.bold))
                    .foregroundStyle(UPalette.ink)
                Text(uKey("u.pane.", pane.rawValue + ".sub")).font(.caption).foregroundStyle(UPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Text(isOn ? "u.pane.on" : (pane == .lock ? "u.pane.optional" : "u.pane.off"))
                .font(.caption.weight(.bold))
                .foregroundStyle(isOn ? UPalette.leaf : pane == .lock ? UPalette.dim : Color(red: 1, green: 0.83, blue: 0.63))
                .padding(.horizontal, 10).frame(minHeight: 26)
                .background((isOn ? UPalette.leaf : pane == .lock ? Color.white : UPalette.warn).opacity(isOn ? 0.16 : 0.12),
                            in: Capsule())
            Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(UPalette.dim)
        }
        .padding(12)
        .background(LinearGradient(colors: [Color(red: 0.165, green: 0.149, blue: 0.133),
                                            Color(red: 0.102, green: 0.090, blue: 0.078)],
                                   startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private func destination(_ pane: UnrotShieldFacts.Pane) -> some View {
        switch pane {
        case .feeds:
            AppsView(protection: protection, usage: usage, onProtection: {}, onPause: {})
        case .safari, .everyApp:
            BlockingLayersSetupView(protection: protection)
        case .screenTime:
            List { ScreenTimeSection(protection: protection) }
                .listStyle(.insetGrouped)
                .navigationTitle(Text("u.pane.screenTime"))
        case .lock:
            UnrotLockView(protection: protection, vault: vault, lockDevice: lockDevice)
        }
    }

    // MARK: - Numbers

    private var numbers: some View {
        HStack(spacing: 8) {
            tile(facts.listCount > 0 ? facts.listCount.formatted(.number.notation(.compactName)) : "—", "u.num.sites")
            tile("\(facts.customSites)", "u.num.yours")
            tile("\(facts.ruleApps)", "u.num.apps")
        }
    }

    private func tile(_ value: String, _ label: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: value).font(.uDisplay(24)).foregroundStyle(UPalette.ink)
            Text(label).font(.caption2).foregroundStyle(UPalette.dim)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(LinearGradient(colors: [Color(red: 0.165, green: 0.149, blue: 0.133),
                                            Color(red: 0.102, green: 0.090, blue: 0.078)],
                                   startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}
