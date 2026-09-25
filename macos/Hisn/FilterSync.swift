import Foundation

/// Keeps the app's own mirrors and the filter's root-owned authority telling
/// one story.
///
/// Every sync computes the stricter merge of the two (`PolicyMerge`) and
/// writes it to both sides. So:
///
///  * a lock or a list edit made in the app reaches the filter — and if the
///    filter was unreachable at the time (being updated, not yet approved),
///    it reaches it on the next sync rather than never;
///  * a lock the user tried to end by deleting the app's mirrors is written
///    straight back from the authority;
///  * an install that predates the authority hands its running lock over.
///
/// Each side still judges what it is given by its own rules — the authority
/// refuses anything that loosens a running lock, and so do the mirrors — so a
/// sync can only ever tighten.
///
/// Without the paid entitlements there is no filter to reach and this does
/// nothing: `status` stays nil and the mirrors carry on as the only copy.
@MainActor
public final class FilterSync: ObservableObject {

    public static let shared = FilterSync()

    /// The authority's last answer, or nil when no filter answered.
    @Published public private(set) var status: PolicyStatus?
    /// The last change the authority refused, in its own words.
    @Published public private(set) var lastRefusal: String?

    private var timer: Timer?
    private var syncing = false

    private init() {}

    public func start() {
        guard timer == nil, FilterLink.shared.isConfigured else { return }
        timer = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { _ in
            Task { @MainActor in await FilterSync.shared.sync() }
        }
        Task { await sync() }
    }

    /// Run one reconciliation now. Called on a timer, and straight after any
    /// change the app makes, so the filter learns of it within a second.
    public func sync() async {
        guard FilterLink.shared.isConfigured else { status = nil; return }
        guard !syncing else { return }
        syncing = true
        defer { syncing = false }

        guard var latest = await FilterLink.shared.status() else {
            status = nil
            return
        }

        // The partner key FIRST, while the authority may still be unlocked. A
        // first lock is what turns the filter on, and a key sent after the lock
        // was refused ("no key while locked") for the whole of that lock — so
        // a genuine approval was then refused too. Unlocked, the app's copy is
        // the one being edited. Locked, removing the key only removes an exit
        // and is passed on; any other difference is a key swapped into the
        // app's defaults, and the authority's is written back over it.
        let localKey = PartnerService.currentKey()
        if localKey != latest.record.partnerKey {
            if !latest.isLocked || localKey == nil {
                if let reply = await FilterLink.shared.submit(.setPartnerKey(localKey)) {
                    latest = reply.status
                }
            } else if let authorityKey = latest.record.partnerKey {
                UserDefaults(suiteName: LockStore.appGroup)?
                    .set(authorityKey, forKey: PartnerService.keyDefaultsKey)
            }
        }

        let local = PolicyView.local(guardAllowed: BrowserGuard.shared.allowed.sorted())
        let authority = PolicyView(status: latest)
        let merged = PolicyMerge.stricter(editor: local, other: authority)

        // Authority ← merged.
        var requests: [PolicyRequest] = []
        if let lock = merged.lock, lock != authority.lock {
            requests.append(.proposeLock(lock))
        }
        if merged.customBlocks != authority.customBlocks || merged.allowlist != authority.allowlist {
            requests.append(.setLists(customBlocks: merged.customBlocks,
                                      allowlist: merged.allowlist))
        }
        if merged.customTerms != authority.customTerms || merged.blockedApps != authority.blockedApps {
            requests.append(.setUserBlocks(terms: merged.customTerms, apps: merged.blockedApps))
        }
        if merged.inspection != authority.inspection {
            requests.append(.setInspection(merged.inspection))
        }
        if Set(merged.guardAllowed) != Set(authority.guardAllowed) {
            requests.append(.setGuardAllowed(merged.guardAllowed))
        }
        for request in requests {
            guard let reply = await FilterLink.shared.submit(request) else { break }
            latest = reply.status
            if !reply.accepted {
                lastRefusal = reply.refusal
                NSLog("[Hisn] filter refused %@: %@", "\(request)", reply.refusal ?? "")
            }
        }

        // Mirrors ← merged. The lock is ADOPTED — the authority's identity
        // replaces whatever the mirrors held (see LockStore.adopt) — and the
        // rest is judged against the mirrors' own content with the lock rules
        // on, so this can only tighten them.
        let locked = merged.locked
        if let lock = merged.lock, lock != local.lock { LockStore.adopt(lock) }
        if merged.customBlocks != local.customBlocks || merged.allowlist != local.allowlist {
            try? SiteLists.save(customBlocks: merged.customBlocks,
                                allowlist: merged.allowlist, locked: locked)
        }
        if merged.customTerms != local.customTerms || merged.blockedApps != local.blockedApps {
            try? UserBlocks.save(terms: merged.customTerms, apps: merged.blockedApps,
                                 locked: locked)
        }
        if merged.inspection != local.inspection {
            try? Inspection.save(merged.inspection, locked: locked)
        }
        if Set(merged.guardAllowed) != Set(local.guardAllowed) {
            BrowserGuard.shared.replaceAllowed(Set(merged.guardAllowed))
        }
        status = latest
    }

    /// Whether the filter's authority says a lock is running — by its own
    /// clock, which a key in the user's defaults cannot move.
    public var authorityLocked: Bool { status?.isLocked ?? false }

    /// Kick a sync without waiting for it — for call sites that just saved.
    public nonisolated static func soon() {
        Task { @MainActor in await FilterSync.shared.sync() }
    }
}

/// Whether a lock is running, as the app should act on it.
///
/// The app's own mirrors are the user's files, and their clock is a key in
/// the user's defaults: forged to 2099, `LockStore.isLocked()` says "over".
/// So anything that would LOOSEN on the app's say-so — turning the filter
/// off, letting the app quit, standing the browser guard down — asks this
/// instead, which also counts the filter's root-owned authority.
@MainActor
public enum EffectiveLock {
    public static var isLocked: Bool {
        LockStore.isLocked() || FilterSync.shared.authorityLocked
    }
}
