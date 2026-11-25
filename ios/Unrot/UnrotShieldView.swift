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
    /// Fixed to one part when Shield and Rules are separate tabs: `.adult` on Shield, `.feeds` on Rules.
    var part: Tab? = nil

    enum Tab: String, CaseIterable { case all, feeds, adult }
    @State private var picked: Tab = .all
    private var tab: Tab { part ?? picked }
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                UTitleBar(title: part == .feeds ? "u.tab.rules" : "u.tab.shield") {
                    // Rules are added on the Rules tab; Shield has nothing to add.
                    if part != .adult { Button(action: onAddRule) {
                        Label("u.shield.add", systemImage: "plus")
                            .font(.footnote.weight(.heavy))
                            .foregroundStyle(UPalette.leafBright)
                            .padding(.horizontal, 14).frame(minHeight: 34)
                            .background(UPalette.leaf.opacity(0.18), in: Capsule())
                    }
                    .buttonStyle(UPressStyle()) }
                }
                if part != .feeds { hero }
                if part == nil { tabs }
                VStack(spacing: 10) {
                    ForEach(UnrotShieldFacts.Pane.allCases.filter { pane in
                        // Shield (adult) keeps every pane but feeds; Rules keeps only feeds.
                        part == .adult ? pane != .feeds : (tab == .all || pane.group == tab)
                    }) { pane in
                        NavigationLink { destination(pane) } label: { row(pane) }
                            .buttonStyle(UPressStyle())
                    }
                }
                // The counts are about the adult filter: they stay on Shield.
                if part != .feeds { numbers }
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

    private var paneBars: some View {
        let heights: [CGFloat] = [70, 104, 126, 104, 70]
        return VStack(spacing: 6) {
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(Array(UnrotShieldFacts.Pane.allCases.enumerated()), id: \.element) { index, pane in
                    let shape = Capsule()
                    if facts.on[pane] == true {
                        shape.fill(UPalette.goldFill)
                            .shadow(color: UPalette.gold.opacity(0.35), radius: 6)
                            .frame(width: 24, height: heights[index])
                    } else if pane == .lock {
                        // Optional, so not a warning: a quiet outline until it is set.
                        shape.fill(Color.white.opacity(0.05))
                            .overlay { shape.strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2) }
                            .frame(width: 24, height: heights[index])
                    } else {
                        shape.fill(UPalette.warn.opacity(0.12))
                            .overlay { shape.strokeBorder(UPalette.warn, style: StrokeStyle(lineWidth: 1.6, dash: [5, 5])) }
                            .frame(width: 24, height: heights[index])
                    }
                }
            }
            Capsule().fill(Color.white.opacity(0.14)).frame(width: 190, height: 6)
        }
        .frame(height: 150, alignment: .bottom)
        .accessibilityHidden(true)
    }

    private var hero: some View {
        VStack(spacing: 6) {
            // The glass as its five panes (prototype: Shield): closed panes are gold,
            // an open one is an amber dashed gap you can see before you read.
            paneBars
            // The lock is optional, so it is drawn but not counted: the number agrees with "n panes open".
            Text(verbatim: "\(UnrotShieldFacts.Pane.allCases.filter { $0 != .lock && facts.on[$0] == true }.count) / \(UnrotShieldFacts.Pane.allCases.count - 1)")
                .font(.uDisplay(38)).foregroundStyle(UPalette.gold)
                .accessibilityHidden(true)
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
                Button { picked = value } label: {
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
                .unrotRoom()
        case .screenTime:
            List { ScreenTimeSection(protection: protection) }
                .listStyle(.insetGrouped)
                .navigationTitle(Text("u.pane.screenTime"))
                .unrotRoom()
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

extension View {
    /// Shared Hisn list screens shown inside Unrot: the warm room behind them
    /// instead of the system's grey grouped background.
    func unrotRoom() -> some View {
        scrollContentBackground(.hidden)
            .background { UBackdrop().ignoresSafeArea() }
    }
}
