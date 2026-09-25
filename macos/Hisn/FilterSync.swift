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

        guard let first = await FilterLink.shared.status() else {
            status = nil
            return
        }
        let now = LockStore.trustedNow()
        let local = PolicyView.local(now: now)
        let authority = PolicyView(status: first)
        let merged = PolicyMerge.stricter(editor: local, other: authority, now: now)
        var latest = first

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
        for request in requests {
            guard let reply = await FilterLink.shared.submit(request) else { break }
            latest = reply.status
            if !reply.accepted {
                lastRefusal = reply.refusal
                NSLog("[Hisn] filter refused %@: %@", "\(request)", reply.refusal ?? "")
            }
        }

        // Mirrors ← merged. Each save judges the change against the mirrors'
        // own content with the lock rules on, so this can only tighten them.
        let locked = merged.isLocked(at: now)
        if let lock = merged.lock, lock != local.lock { LockStore.write(lock) }
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

        // The partner key. Unlocked, the app's copy is the one being edited.
        // Locked, the authority's is the one that counts — a key swapped into
        // the app's defaults mid-lock would let the person approve their own
        // release — so it is written back over the mirror.
        let localKey = PartnerService.currentKey()
        let authorityKey = latest.record.partnerKey
        if localKey != authorityKey {
            if !locked, let reply = await FilterLink.shared.submit(.setPartnerKey(localKey)) {
                latest = reply.status
            } else if locked, let authorityKey {
                UserDefaults(suiteName: LockStore.appGroup)?
                    .set(authorityKey, forKey: PartnerService.keyDefaultsKey)
            }
        }
        status = latest
    }

    /// Kick a sync without waiting for it — for call sites that just saved.
    public nonisolated static func soon() {
        Task { @MainActor in await FilterSync.shared.sync() }
    }
}
