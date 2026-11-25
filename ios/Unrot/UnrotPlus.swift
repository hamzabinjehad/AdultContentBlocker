import StoreKit
import SwiftUI

/// Unrot Plus on this iPhone: whether it is on, and the App Store behind it.
/// What is free and what is paid is decided in `PlusGate` (UnrotCore), never here.
///
/// The app never decides that someone has paid: it asks StoreKit which
/// purchases this Apple ID holds and writes the answer down, so Plus keeps
/// working offline.
///
/// Products to create in App Store Connect (ids are fixed here): an
/// auto-renewing group with `yearly` and `monthly`, and the non-consumable
/// `lifetime`. A free-trial introductory offer on the yearly plan is picked up
/// by itself and shown as "N days free".
@MainActor
final class UnrotPlusStore: ObservableObject {
    static let shared = UnrotPlusStore()

    static let yearly = "app.unrot.plus.yearly"
    static let monthly = "app.unrot.plus.monthly"
    static let lifetime = "app.unrot.plus.lifetime"
    private static let order = [yearly, monthly, lifetime]

    /// Purchases stay closed until the app has something paid to give. Today
    /// iPhone has no paid limit yet (Android's is the fourth app rule), so the
    /// page explains Plus and sells nothing. Turn this on with the first gate.
    static let selling = false

    /// A subscription seen by StoreKit is trusted this long without it.
    private static let grace: TimeInterval = 3 * 24 * 3600
    private enum Key { static let paidUntil = "unrot.plus.paidUntil"; static let lifetime = "unrot.plus.lifetime" }

    @Published private(set) var products: [Product] = []
    @Published private(set) var active: Bool
    @Published private(set) var reachable = true

    private let defaults: UserDefaults
    private var updates: Task<Void, Never>?

    init(defaults: UserDefaults = UserDefaults(suiteName: Edition.appGroup) ?? .standard) {
        self.defaults = defaults
        active = PlusGate.active(now: Date().timeIntervalSince1970,
                                 paidUntil: defaults.double(forKey: Key.paidUntil),
                                 lifetime: defaults.bool(forKey: Key.lifetime))
        guard Self.selling else { return }
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let transaction) = result { await transaction.finish() }
                await self?.refresh()
            }
        }
    }

    deinit { updates?.cancel() }

    func open(_ feature: PlusGate.Feature, have: Int = 0) -> Bool {
        PlusGate.open(feature, plus: active, have: have)
    }

    /// Prices from the App Store, then what this Apple ID owns.
    func load() async {
        guard Self.selling else { return }
        do {
            let found = try await Product.products(for: Self.order)
            products = found.sorted { (Self.order.firstIndex(of: $0.id) ?? 9) < (Self.order.firstIndex(of: $1.id) ?? 9) }
            reachable = true
        } catch {
            reachable = false
        }
        await refresh()
    }

    /// Ask StoreKit what is owned and write it down.
    func refresh() async {
        var subscribed = false, forGood = false
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result, transaction.revocationDate == nil else { continue }
            if transaction.productID == Self.lifetime { forGood = true }
            else if Self.order.contains(transaction.productID) { subscribed = true }
        }
        let now = Date().timeIntervalSince1970
        defaults.set(subscribed ? now + Self.grace : 0, forKey: Key.paidUntil)
        defaults.set(forGood, forKey: Key.lifetime)
        active = PlusGate.active(now: now, paidUntil: defaults.double(forKey: Key.paidUntil), lifetime: forGood)
    }

    func buy(_ product: Product) async {
        guard let result = try? await product.purchase() else { return }
        if case .success(let verification) = result, case .verified(let transaction) = verification {
            await transaction.finish()
            await refresh()
        }
    }

    /// "Restore purchases": sign-in may be asked for by the system.
    func restore() async {
        try? await AppStore.sync()
        await refresh()
    }

    /// Days in a free-trial introductory offer, or 0.
    static func trialDays(_ product: Product) -> Int {
        guard let offer = product.subscription?.introductoryOffer, offer.paymentMode == .freeTrial else { return 0 }
        let unit: Int
        switch offer.period.unit {
        case .day: unit = 1
        case .week: unit = 7
        case .month: unit = 30
        case .year: unit = 365
        @unknown default: unit = 0
        }
        return offer.period.value * unit
    }
}

/// The Plus page. It says what stays free before it asks for anything, because
/// a person fighting adult content must never feel the help is behind a wall.
struct UnrotPlusSheet: View {
    @ObservedObject private var plus = UnrotPlusStore.shared
    @State private var picked = UnrotPlusStore.yearly

    private var offer: Product? { plus.products.first { $0.id == picked } ?? plus.products.first }

    var body: some View {
        USheet(title: "u.plus.title", subtitle: plus.active ? "u.plus.owned" : "u.plus.lede", tall: true) {
            HStack(alignment: .bottom, spacing: -14) {
                Image("tree_cherry").resizable().scaledToFit().frame(width: 92)
                Image("tree_maple").resizable().scaledToFit().frame(width: 128).zIndex(1)
                Image("tree_pine").resizable().scaledToFit().frame(width: 92)
            }
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)

            UCard {
                ULabel("u.plus.with")
                row("slider.horizontal.3", "u.plus.rules", UnrotPlusStore.selling ? "u.plus.rulesSub" : nil)
                row("heart", "u.plus.support", "u.plus.supportSub")
                row("leaf", "u.plus.soon", "u.plus.soonSub")
            }
            UCard {
                ULabel("u.plus.free")
                row("shield.lefthalf.filled", "u.plus.freeBlock", nil)
                row("wind", "u.plus.freeBreathe", nil)
                row("lock", "u.plus.freeLock", nil)
                // Until iPhone has a paid limit, every app rule is free here.
                row("moon", UnrotPlusStore.selling ? "u.plus.freeRules" : "u.plus.freeRulesAll", nil)
            }
            if !plus.active { shop }
        }
        .task { await plus.load() }
    }

    @ViewBuilder private var shop: some View {
        if !UnrotPlusStore.selling {
            note("u.plus.notYet")
        } else if plus.products.isEmpty {
            note(plus.reachable ? "u.plus.loading" : "u.plus.noStore")
        } else {
            HStack(spacing: 10) {
                ForEach(plus.products, id: \.id) { product in plan(product) }
            }
            if let offer {
                let days = UnrotPlusStore.trialDays(offer)
                UGoldButton(title: days > 0 ? "u.plus.startTrial \(days)" : "u.plus.start", wide: true) {
                    Task { await plus.buy(offer) }
                }
            }
            Button("u.plus.restore") { Task { await plus.restore() } }
                .font(.subheadline.weight(.semibold)).foregroundStyle(UPalette.goldLight)
                .frame(maxWidth: .infinity, minHeight: 44)
            note("u.plus.note")
        }
    }

    private func note(_ key: LocalizedStringKey) -> some View {
        Text(key).font(.footnote).foregroundStyle(UPalette.dim)
            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity)
    }

    private func row(_ symbol: String, _ title: LocalizedStringKey, _ detail: LocalizedStringKey?) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(UPalette.gold).frame(width: 28)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).foregroundStyle(UPalette.ink)
                if let detail {
                    Text(detail).font(.caption).foregroundStyle(UPalette.dim)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .font(.subheadline)
    }

    private func plan(_ product: Product) -> some View {
        let selected = product.id == offer?.id
        let days = UnrotPlusStore.trialDays(product)
        return Button { picked = product.id } label: {
            VStack(alignment: .leading, spacing: 3) {
                Text(planName(product.id)).font(.subheadline.weight(.bold)).foregroundStyle(UPalette.ink)
                Text(verbatim: product.displayPrice).font(.footnote).foregroundStyle(UPalette.dim)
                if days > 0 {
                    Text("u.plus.trialDays \(days)").font(.caption2.weight(.bold)).foregroundStyle(UPalette.onGold)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(UPalette.gold, in: Capsule())
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 76, alignment: .topLeading)
            .background(UPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(selected ? UPalette.gold : UPalette.cardLine, lineWidth: selected ? 2 : 1)
            }
        }
        .buttonStyle(UPressStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func planName(_ id: String) -> LocalizedStringKey {
        switch id {
        case UnrotPlusStore.yearly: return "u.plus.yearly"
        case UnrotPlusStore.monthly: return "u.plus.monthly"
        default: return "u.plus.lifetime"
        }
    }
}

/// The row on You that opens the Plus page.
struct UnrotPlusRow: View {
    @ObservedObject private var plus = UnrotPlusStore.shared
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image("tree_maple").resizable().scaledToFit().frame(width: 48, height: 48)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("u.plus.title").font(.uDisplay(18)).foregroundStyle(UPalette.ink)
                    Text(plus.active ? "u.plus.rowOwned" : "u.plus.rowSub").font(.caption).foregroundStyle(UPalette.dim)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(UPalette.dim)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(UPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(UPalette.gold.opacity(0.45), lineWidth: 1)
            }
        }
        .buttonStyle(UPressStyle())
    }
}
