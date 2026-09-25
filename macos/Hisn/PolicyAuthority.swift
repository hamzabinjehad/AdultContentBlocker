import Foundation
import os

/// The authoritative copy of policy, owned by the content filter.
///
/// WHY THIS EXISTS
/// ---------------
/// The filter is a system extension, and system extensions run as root. Its
/// `group.app.hisn` defaults and container therefore resolve under
/// `/private/var/root`, not the user's home — so everything the app wrote for
/// it there (the lock, the hand lists, the downloaded list) was invisible to
/// it, and everything it wrote back (its heartbeat, its domain count) was
/// invisible to the app. `docs/TAMPER_MODEL.md` named this as the assumption
/// to verify first; it is how system extensions work, so the design stops
/// depending on it.
///
/// It also closes the bigger hole that document describes: every copy of the
/// lock the app keeps is the user's own file, so a standard user who deletes
/// all three mirrors ends the lock. This record lives in the filter's sandbox
/// container under root's home — a standard user can neither read nor write
/// it — and it changes only through `PolicyService`, which applies the same
/// one-way rules the app does: longer, stricter, add a block, drop an
/// allowance. Never the reverse until the lock has run out.
///
/// The app and the bridge talk to it over XPC (`FilterXPC.swift`). Without the
/// paid entitlements there is no filter and no authority, and the app's own
/// mirrors carry on exactly as before.
public struct PolicyRecord: Codable, Equatable {
    public var lock: LockStore.LockState?
    /// When the authority first saw the pending release date. The release is
    /// honoured `selfReleaseDelay` after THIS, whatever the date claims —
    /// the same defence `LockStore.effectiveDeadline` applies, kept here in
    /// the root-owned record instead of the user's defaults.
    public var releaseFirstSeen: Date?
    public var allowlist: [String] = []
    public var customBlocks: [String] = []
    public var customTerms: [String] = []
    public var blockedApps: [String] = []
    public var inspection: Inspection.Settings = .default
    /// The latest time the authority has seen. A clock wound back makes the
    /// countdown stand still rather than run out.
    public var highWaterMark: Date = .distantPast
    /// The highest list version installed; older signed lists are refused.
    public var listVersionFloor: Int = 0
    /// When the system tore the filter down while a lock ran, if it did.
    public var stoppedDuringLock: Date?
    /// The accountability partner's public key (`PartnerService`), or nil.
    public var partnerKey: String?
    /// Incremented on every accepted change; picks the newer of the two
    /// on-disk copies.
    public var revision: Int = 0

    public init() {}
}

/// One change the app asks the authority to make.
public enum PolicyRequest: Codable, Equatable {
    /// Start, extend, tighten to strict, or request/cancel an early release —
    /// every lock change is a whole proposed state, judged by
    /// `LockStore.refusal` exactly as the mirrors judge it.
    case proposeLock(LockStore.LockState)
    case setLists(customBlocks: [String], allowlist: [String])
    case setUserBlocks(terms: [String], apps: [String])
    case setInspection(Inspection.Settings)
    /// Set, replace or remove the partner's public key.
    case setPartnerKey(String?)
    /// End the running lock on the partner's signed approval.
    case partnerRelease(approval: String)
}

/// What the filter is actually doing, reported by the filter itself.
public struct FilterHealth: Codable, Equatable {
    public var domainCount: Int
    public var hostTermCount: Int
    public var keywordSource: String
    public var listVersion: Int
    public var startedAt: Date

    public init(domainCount: Int = 0, hostTermCount: Int = 0,
                keywordSource: String = "none", listVersion: Int = 0,
                startedAt: Date = Date()) {
        self.domainCount = domainCount
        self.hostTermCount = hostTermCount
        self.keywordSource = keywordSource
        self.listVersion = listVersion
        self.startedAt = startedAt
    }
}

/// The authority's answer to "what is in force right now?".
public struct PolicyStatus: Codable, Equatable {
    public var record: PolicyRecord
    /// When the lock actually ends — a matured release included — or nil
    /// when no lock is running.
    public var effectiveDeadline: Date?
    public var strictActive: Bool
    /// The authority's trusted time when it answered.
    public var now: Date
    public var health: FilterHealth

    public var isLocked: Bool { effectiveDeadline.map { now < $0 } ?? false }
}

public struct PolicyResponse: Codable, Equatable {
    public var accepted: Bool
    /// Why a request was refused, in words fit to show the person.
    public var refusal: String?
    public var status: PolicyStatus
}

// MARK: - Rules

/// The rules, as pure functions of a record and a time. No I/O, so every case
/// is a unit test.
public enum PolicyAuthority {

    public static func trustedNow(_ record: PolicyRecord, wall: Date) -> Date {
        max(wall, record.highWaterMark)
    }

    public static func effectiveDeadline(_ record: PolicyRecord) -> Date? {
        guard let lock = record.lock else { return nil }
        guard lock.selfReleaseAt != nil else { return lock.deadline }
        return LockStore.effectiveDeadline(
            lock, releaseFirstSeen: record.releaseFirstSeen ?? record.highWaterMark)
    }

    public static func isLocked(_ record: PolicyRecord, now: Date) -> Bool {
        effectiveDeadline(record).map { now < $0 } ?? false
    }

    public static func strictActive(_ record: PolicyRecord, now: Date) -> Bool {
        isLocked(record, now: now) && record.lock?.mode == "strict"
    }

    /// Advance the clock and drop a lock that has run out. Run on every read
    /// and every request, so an expired lock never outlives its deadline.
    public static func settle(_ record: PolicyRecord, wall: Date) -> PolicyRecord {
        var r = record
        let now = trustedNow(r, wall: wall)
        r.highWaterMark = now
        if r.lock != nil, !isLocked(r, now: now) {
            r.lock = nil
            r.releaseFirstSeen = nil
        }
        return r
    }

    /// Apply one request. Returns the new record, or the reason it was refused
    /// (the record is then unchanged).
    public static func apply(_ request: PolicyRequest, to record: PolicyRecord,
                             wall: Date) -> Result<PolicyRecord, PolicyRefusal> {
        var r = settle(record, wall: wall)
        let now = r.highWaterMark
        let locked = isLocked(r, now: now)

        switch request {
        case let .proposeLock(proposed):
            guard proposed.deadline > now else {
                return .failure(.init("That lock would already be over."))
            }
            if let why = LockStore.refusal(current: r.lock, proposed: proposed, now: now) {
                return .failure(.init("A lock is running and this " + why + "."))
            }
            // A new release date starts its own wait from now; the same date
            // resubmitted keeps the wait it already had.
            if proposed.selfReleaseAt != r.lock?.selfReleaseAt {
                r.releaseFirstSeen = proposed.selfReleaseAt == nil ? nil : now
            }
            r.lock = proposed

        case let .setLists(blocks, allows):
            if locked, let refusal = SiteLists.loosening(
                fromBlocks: r.customBlocks, fromAllows: r.allowlist,
                toBlocks: blocks, toAllows: allows) {
                return .failure(.init(refusal.localizedDescription))
            }
            r.customBlocks = blocks
            r.allowlist = allows

        case let .setUserBlocks(terms, apps):
            if locked, let refusal = UserBlocks.loosening(
                fromTerms: r.customTerms, fromApps: r.blockedApps,
                toTerms: terms, toApps: apps) {
                return .failure(.init(refusal.localizedDescription))
            }
            r.customTerms = terms
            r.blockedApps = apps

        case let .setInspection(settings):
            if locked, let refusal = Inspection.loosening(from: r.inspection, to: settings) {
                return .failure(.init(refusal.localizedDescription))
            }
            r.inspection = settings

        case let .setPartnerKey(key):
            let canonical = key.flatMap(PartnerService.canonicalKey)
            if key != nil, canonical == nil {
                return .failure(.init(PartnerService.PartnerError.badKey.localizedDescription))
            }
            if let refusal = PartnerService.keyRefusal(current: r.partnerKey, proposed: canonical,
                                                       locked: locked) {
                return .failure(.init(refusal.localizedDescription))
            }
            r.partnerKey = canonical

        case let .partnerRelease(approval):
            guard locked, let lock = r.lock else {
                return .failure(.init(PartnerService.PartnerError.noLock.localizedDescription))
            }
            do {
                _ = try PartnerService.approve(approval, for: lock, key: r.partnerKey)
            } catch {
                return .failure(.init(error.localizedDescription))
            }
            r.lock = nil
            r.releaseFirstSeen = nil
        }
        r.revision += 1
        return .success(r)
    }

    public static func status(_ record: PolicyRecord, health: FilterHealth) -> PolicyStatus {
        let now = record.highWaterMark
        return PolicyStatus(record: record,
                            effectiveDeadline: isLocked(record, now: now)
                                ? effectiveDeadline(record) : nil,
                            strictActive: strictActive(record, now: now),
                            now: now, health: health)
    }
}

public struct PolicyRefusal: Error, Equatable {
    public let message: String
    public init(_ message: String) { self.message = message }
}

// MARK: - Persistence

/// The record on disk: two copies, written alternately, each atomically.
///
/// A torn or corrupt write can cost at most the latest change — the other
/// copy still decodes — rather than the lock itself. Starting over from an
/// empty record because one file would not parse is the failure this avoids:
/// it would end a running lock.
public final class PolicyStore {

    private let slots: [URL]

    public init(directory: URL) {
        try? FileManager.default.createDirectory(
            at: directory, withIntermediateDirectories: true,
            attributes: [.posixPermissions: 0o700])
        slots = [directory.appendingPathComponent("policy.a.json"),
                 directory.appendingPathComponent("policy.b.json")]
    }

    /// The newest copy that decodes, or an empty record if neither does.
    public func load() -> PolicyRecord {
        slots.compactMap { url -> PolicyRecord? in
            guard let data = try? Data(contentsOf: url) else { return nil }
            return try? JSONDecoder().decode(PolicyRecord.self, from: data)
        }
        .max(by: { $0.revision < $1.revision }) ?? PolicyRecord()
    }

    public func save(_ record: PolicyRecord) throws {
        let data = try JSONEncoder().encode(record)
        try data.write(to: slots[record.revision % 2], options: .atomic)
    }
}

// MARK: - Service

/// The authority as the filter runs it: the record in memory, persisted on
/// every accepted change, and the entry points the XPC listener calls.
///
/// Kept out of `FilterDataProvider` so the tests drive exactly this — with a
/// temporary directory for a store — rather than a copy of its logic.
public final class PolicyService: @unchecked Sendable {

    private let store: PolicyStore
    private let clock: () -> Date
    private let health: () -> FilterHealth
    private let lock = OSAllocatedUnfairLock()
    private var record: PolicyRecord
    /// Called after every accepted change, with the new record, so the filter
    /// can re-read its enforcement state at once rather than on its next tick.
    public var onChange: ((PolicyRecord) -> Void)?

    public init(store: PolicyStore, clock: @escaping () -> Date = Date.init,
                health: @escaping () -> FilterHealth = { FilterHealth() }) {
        self.store = store
        self.clock = clock
        self.health = health
        self.record = store.load()
    }

    /// The high-water mark last written to disk. The mark advances on every
    /// read; writing it every time would be a disk write per flow check, so it
    /// is written when it has moved on by `markCheckpoint` — a clock wound back
    /// across a filter restart then loses at most that much.
    private var persistedMark: Date = .distantPast
    private static let markCheckpoint: TimeInterval = 300

    /// The current record, settled to now.
    public func current() -> PolicyRecord {
        lock.withLockUnchecked {
            var settled = PolicyAuthority.settle(record, wall: clock())
            let expired = settled.lock != record.lock
            if expired || settled.highWaterMark.timeIntervalSince(persistedMark)
                > Self.markCheckpoint {
                if expired { settled.revision += 1 }
                persist(settled)
            }
            record = settled
            return record
        }
    }

    public func status() -> PolicyStatus {
        PolicyAuthority.status(current(), health: health())
    }

    public func handle(_ request: PolicyRequest) -> PolicyResponse {
        let result: Result<PolicyRecord, PolicyRefusal> = lock.withLockUnchecked {
            let outcome = PolicyAuthority.apply(request, to: record, wall: clock())
            if case let .success(next) = outcome {
                persist(next)
                record = next
            }
            return outcome
        }
        let status = PolicyAuthority.status(current(), health: health())
        switch result {
        case let .success(next):
            onChange?(next)
            return PolicyResponse(accepted: true, refusal: nil, status: status)
        case let .failure(refusal):
            return PolicyResponse(accepted: false, refusal: refusal.message, status: status)
        }
    }

    /// Raise the rollback floor after a list generation is installed.
    public func raiseListFloor(to version: Int) {
        lock.withLockUnchecked {
            guard version > record.listVersionFloor else { return }
            var next = record
            next.listVersionFloor = version
            next.revision += 1
            persist(next)
            record = next
        }
    }

    /// The system is stopping the filter. If a lock was running, say so in the
    /// record — the app reports it and re-arms.
    public func noteStoppedDuringLock() {
        lock.withLockUnchecked {
            let now = PolicyAuthority.trustedNow(record, wall: clock())
            guard PolicyAuthority.isLocked(record, now: now) else { return }
            var next = record
            next.stoppedDuringLock = now
            next.revision += 1
            persist(next)
            record = next
        }
    }

    /// Carry forward a lock found only in the old mirrors — an install that
    /// predates the authority. Judged like any other proposal, so it can only
    /// ever extend what the authority already holds.
    public func adopt(legacy lock: LockStore.LockState?) {
        guard let lock, lock.deadline > clock() else { return }
        _ = handle(.proposeLock(lock))
    }

    private func persist(_ r: PolicyRecord) {
        persistedMark = r.highWaterMark
        do { try store.save(r) }
        catch { NSLog("[Hisn] CRITICAL: policy record not saved: %@", "\(error)") }
    }
}

// MARK: - Two copies, one answer

/// Policy as one reader sees it: the app's own mirrors, or the authority's
/// record. The app, the bridge and the browser all act on the merge of the
/// two, never on either alone.
public struct PolicyView: Equatable {
    /// The running lock, or nil when none is running.
    public var lock: LockStore.LockState?
    public var effectiveDeadline: Date?
    public var allowlist: [String]
    public var customBlocks: [String]
    public var customTerms: [String]
    public var blockedApps: [String]
    public var inspection: Inspection.Settings

    public init(lock: LockStore.LockState?, effectiveDeadline: Date?,
                allowlist: [String], customBlocks: [String], customTerms: [String],
                blockedApps: [String], inspection: Inspection.Settings) {
        self.lock = lock
        self.effectiveDeadline = effectiveDeadline
        self.allowlist = allowlist
        self.customBlocks = customBlocks
        self.customTerms = customTerms
        self.blockedApps = blockedApps
        self.inspection = inspection
    }

    public func isLocked(at now: Date) -> Bool {
        effectiveDeadline.map { now < $0 } ?? false
    }

    /// What this user's own stores say.
    public static func local(now: Date = LockStore.trustedNow()) -> PolicyView {
        let state = LockStore.read()
        let end = LockStore.effectiveDeadline(state)
        let running = now < end
        return PolicyView(lock: running ? state : nil,
                          effectiveDeadline: running ? end : nil,
                          allowlist: SiteLists.allowlist(),
                          customBlocks: SiteLists.customBlocks(),
                          customTerms: UserBlocks.terms(),
                          blockedApps: UserBlocks.apps(),
                          inspection: Inspection.read())
    }

    /// What the filter's authority says.
    public init(status: PolicyStatus) {
        let r = status.record
        self.init(lock: status.isLocked ? r.lock : nil,
                  effectiveDeadline: status.isLocked ? status.effectiveDeadline : nil,
                  allowlist: r.allowlist, customBlocks: r.customBlocks,
                  customTerms: r.customTerms, blockedApps: r.blockedApps,
                  inspection: r.inspection)
    }
}

public enum PolicyMerge {

    /// The stricter reading of two views of the same policy.
    ///
    /// While either says a lock is running, every field takes its stricter
    /// value: the later end, strict over standard, the union of blocks and
    /// words and apps, the intersection of allowances, every check that
    /// either has on. So neither copy can loosen what the other holds —
    /// forging the user's defaults buys nothing while the authority says
    /// otherwise, and a filter that lost its record cannot unlock the browser
    /// while the app's mirrors still hold the lock.
    ///
    /// With no lock anywhere, `editor` — the app's own copy, which the person
    /// edits — is the answer, and the authority is brought into line with it.
    public static func stricter(editor: PolicyView, other: PolicyView,
                                now: Date) -> PolicyView {
        guard editor.isLocked(at: now) || other.isLocked(at: now) else { return editor }

        let locks = [editor, other].filter { $0.isLocked(at: now) }
        let longest = locks.max { ($0.effectiveDeadline ?? .distantPast)
                                   < ($1.effectiveDeadline ?? .distantPast) }!
        var lock = longest.lock
        if locks.contains(where: { $0.lock?.mode == "strict" }) { lock?.mode = "strict" }

        let allows = Set(editor.allowlist).intersection(other.allowlist)
        let e = editor.inspection, o = other.inspection
        return PolicyView(
            lock: lock,
            effectiveDeadline: longest.effectiveDeadline,
            allowlist: editor.allowlist.filter(allows.contains),
            customBlocks: union(editor.customBlocks, other.customBlocks),
            customTerms: union(editor.customTerms, other.customTerms),
            blockedApps: union(editor.blockedApps, other.blockedApps),
            inspection: Inspection.Settings(
                text: e.text || o.text,
                textSensitivity: max(e.textSensitivity, o.textSensitivity),
                hostKeywords: e.hostKeywords || o.hostKeywords))
    }

    /// Order-preserving union: `a`'s entries first, then `b`'s new ones.
    static func union(_ a: [String], _ b: [String]) -> [String] {
        var seen = Set(a)
        return a + b.filter { seen.insert($0).inserted }
    }
}

extension PolicyView {

    /// What the bridge tells the browser extension on each heartbeat.
    ///
    /// Read-only in both directions of trust: the browser can change none of
    /// it, and blocked apps are left out — the browser cannot enforce them,
    /// and a list of someone's apps is exposure bought for nothing.
    ///
    /// `lockUntil` is the EFFECTIVE deadline, so a matured self-release ends
    /// the lock in the browser at the same moment it ends everywhere else.
    public func bridgeReply(now: Date, listVersion: Int) -> [String: Any] {
        let locked = isLocked(at: now)
        return [
            "lockUntil": locked ? (effectiveDeadline ?? now).timeIntervalSince1970 * 1000
                                : Double(0),
            "mode": locked ? (lock?.mode ?? "blocklist") : "off",
            "allowlist": allowlist,
            "customBlocks": customBlocks,
            "customTerms": customTerms,
            "inspectText": inspection.text,
            "textSensitivity": inspection.textSensitivity,
            "hostKeywords": inspection.hostKeywords,
            "listVersion": listVersion,
        ]
    }
}
