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
    /// Apps the browser guard leaves open during a lock. Kept here too, so a
    /// `defaults write` of the app's copy mid-lock cannot exempt Tor Browser.
    public var guardAllowed: [String] = []
    /// Incremented on every accepted change; picks the newer of the two
    /// on-disk copies.
    public var revision: Int = 0
    /// A damaged authority is never a fresh, unlocked installation. This flag
    /// is sticky until an administrator restores the policy and its evidence.
    public var recoveryRequired: Bool = false

    public init() {}

    private enum CodingKeys: String, CodingKey {
        case lock, releaseFirstSeen, allowlist, customBlocks, customTerms, blockedApps,
             inspection, highWaterMark, listVersionFloor, stoppedDuringLock, partnerKey,
             guardAllowed, revision, recoveryRequired
    }

    /// Every field optional on the way in. Synthesised decoding throws
    /// `keyNotFound` for a field an older build never wrote — and then NEITHER
    /// on-disk copy decodes, and the running lock, the rollback floor and the
    /// partner key are gone the first time a filter update adds a field.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        guard !c.allKeys.isEmpty else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath,
                                                   debugDescription: "Empty policy record."))
        }
        lock = try c.decodeIfPresent(LockStore.LockState.self, forKey: .lock)
        releaseFirstSeen = try c.decodeIfPresent(Date.self, forKey: .releaseFirstSeen)
        allowlist = try c.decodeIfPresent([String].self, forKey: .allowlist) ?? []
        customBlocks = try c.decodeIfPresent([String].self, forKey: .customBlocks) ?? []
        customTerms = try c.decodeIfPresent([String].self, forKey: .customTerms) ?? []
        blockedApps = try c.decodeIfPresent([String].self, forKey: .blockedApps) ?? []
        inspection = try c.decodeIfPresent(Inspection.Settings.self, forKey: .inspection) ?? .default
        highWaterMark = try c.decodeIfPresent(Date.self, forKey: .highWaterMark) ?? .distantPast
        listVersionFloor = try c.decodeIfPresent(Int.self, forKey: .listVersionFloor) ?? 0
        stoppedDuringLock = try c.decodeIfPresent(Date.self, forKey: .stoppedDuringLock)
        partnerKey = try c.decodeIfPresent(String.self, forKey: .partnerKey)
        guardAllowed = try c.decodeIfPresent([String].self, forKey: .guardAllowed) ?? []
        revision = try c.decodeIfPresent(Int.self, forKey: .revision) ?? 0
        recoveryRequired = try c.decodeIfPresent(Bool.self, forKey: .recoveryRequired) ?? false
        guard revision >= 0, revision < Int.max else {
            throw DecodingError.dataCorruptedError(forKey: .revision, in: c,
                                                   debugDescription: "Invalid policy revision.")
        }
    }
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
    /// The apps the browser guard leaves open. Add only while unlocked.
    case setGuardAllowed([String])
    /// The bridge: this browser's extension just checked in. Held in memory
    /// only, never written down — it is evidence of liveness, not policy.
    case checkIn(browser: String)
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
    /// When each browser's extension last checked in through the bridge —
    /// over the code-signed channel, so unlike the app's defaults it cannot
    /// be forged to keep the browser guard quiet. Optional so an older
    /// filter's reply still decodes.
    public var checkIns: [String: Date]?
    /// A failed durable write or a damaged store, reported by the authority.
    /// Optional so replies from older filters still decode.
    public var persistenceError: String? = nil

    public var isLocked: Bool {
        record.recoveryRequired || (effectiveDeadline.map { now < $0 } ?? false)
    }
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

    public static let recoveryMessage = "Hisn's saved protection policy is damaged or unreadable. "
        + "Protection stays restricted. Ask the administrator to restore the policy before changing it."

    public static func trustedNow(_ record: PolicyRecord, wall: Date) -> Date {
        max(wall, record.highWaterMark)
    }

    public static func effectiveDeadline(_ record: PolicyRecord) -> Date? {
        if record.recoveryRequired { return .distantFuture }
        guard let lock = record.lock else { return nil }
        guard lock.selfReleaseAt != nil else { return lock.deadline }
        return LockStore.effectiveDeadline(
            lock, releaseFirstSeen: record.releaseFirstSeen ?? record.highWaterMark)
    }

    public static func isLocked(_ record: PolicyRecord, now: Date) -> Bool {
        if record.recoveryRequired { return true }
        return effectiveDeadline(record).map { now < $0 } ?? false
    }

    public static func strictActive(_ record: PolicyRecord, now: Date) -> Bool {
        record.recoveryRequired || (isLocked(record, now: now) && record.lock?.mode == "strict")
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
        if case .checkIn = request { return .success(record) }   // not policy; see PolicyService
        guard !record.recoveryRequired else { return .failure(.init(recoveryMessage)) }
        var r = settle(record, wall: wall)
        let now = r.highWaterMark
        let locked = isLocked(r, now: now)

        switch request {
        case .checkIn:
            return .success(record)

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

        case let .setGuardAllowed(apps):
            if locked, !Set(apps).subtracting(r.guardAllowed).isEmpty {
                return .failure(.init("A lock is running, so apps can only be allowed again once it ends."))
            }
            r.guardAllowed = apps
        }
        r.revision += 1
        return .success(r)
    }

    public static func status(_ record: PolicyRecord, health: FilterHealth,
                              checkIns: [String: Date]? = nil,
                              persistenceError: String? = nil) -> PolicyStatus {
        let now = record.highWaterMark
        return PolicyStatus(record: record,
                            effectiveDeadline: isLocked(record, now: now)
                                ? effectiveDeadline(record) : nil,
                            strictActive: strictActive(record, now: now),
                            now: now, health: health, checkIns: checkIns,
                            persistenceError: persistenceError)
    }
}

public struct PolicyRefusal: LocalizedError, Equatable {
    public let message: String
    public init(_ message: String) { self.message = message }
    public var errorDescription: String? { message }
}

// MARK: - Persistence

/// The record on disk: two copies, written alternately, each atomically.
///
/// A surviving copy preserves the last readable policy. A damaged copy could
/// have held a newer, stricter lock, so any damage also requires recovery;
/// silently reverting to an older permissive copy would be a way out.
public final class PolicyStore {

    private let slots: [URL]
    private let directory: URL
    private let recoveryMarker: URL
    private var initializationFailed = false
    private var recoveryRequired = false
    public static let recoveryMarkerName = "policy.recovery-required"

    public init(directory: URL) {
        self.directory = directory
        recoveryMarker = directory.appendingPathComponent(Self.recoveryMarkerName)
        slots = [directory.appendingPathComponent("policy.a.json"),
                 directory.appendingPathComponent("policy.b.json")]
        do {
            try FileManager.default.createDirectory(
                at: directory, withIntermediateDirectories: true,
                attributes: [.posixPermissions: 0o700])
        } catch {
            initializationFailed = true
            NSLog("[Hisn] CRITICAL: cannot open the policy directory: %@", "\(error)")
        }
    }

    /// Freshness is proven by an accessible empty directory, not by failed
    /// reads. Damaged originals stay in place, with forensic copies alongside
    /// them; a marker and legacy evidence keep recovery sticky across restarts.
    public func load() -> PolicyRecord {
        guard !initializationFailed,
              let names = try? FileManager.default.contentsOfDirectory(atPath: directory.path) else {
            return recoveryRecord()
        }
        var decoded: [PolicyRecord] = []
        var unreadable: [URL] = []
        for url in slots where names.contains(url.lastPathComponent) {
            do {
                let data = try Data(contentsOf: url)
                decoded.append(try JSONDecoder().decode(PolicyRecord.self, from: data))
            } catch {
                unreadable.append(url)
            }
        }
        var record = decoded.max(by: { $0.revision < $1.revision }) ?? PolicyRecord()
        let hasEvidence = names.contains(Self.recoveryMarkerName) || names.contains { name in
            slots.contains { name.hasPrefix($0.lastPathComponent + ".unreadable-") }
        }
        if !unreadable.isEmpty || hasEvidence || record.recoveryRequired {
            record.recoveryRequired = true
            recoveryRequired = true
            if !names.contains(Self.recoveryMarkerName) {
                // Even if this write fails, the damaged originals and any
                // existing evidence still prevent an unlocked fresh start.
                try? Data(PolicyAuthority.recoveryMessage.utf8).write(to: recoveryMarker, options: .atomic)
            }
            for url in unreadable {
                let prefix = url.lastPathComponent + ".unreadable-"
                if !names.contains(where: { $0.hasPrefix(prefix) }) {
                    let aside = url.appendingPathExtension("unreadable-\(UUID().uuidString)")
                    try? FileManager.default.copyItem(at: url, to: aside)
                }
                NSLog("[Hisn] CRITICAL: policy record %@ unreadable; administrator recovery required",
                      url.lastPathComponent)
            }
        }
        return record
    }

    private func recoveryRecord() -> PolicyRecord {
        recoveryRequired = true
        var record = PolicyRecord()
        record.recoveryRequired = true
        return record
    }

    public func save(_ record: PolicyRecord) throws {
        guard !recoveryRequired, !record.recoveryRequired else {
            throw PolicyRefusal(PolicyAuthority.recoveryMessage)
        }
        guard record.revision >= 0, record.revision < Int.max else {
            throw PolicyRefusal("Invalid policy revision.")
        }
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
    private var checkIns: [String: Date] = [:]
    private var persistenceError: String?
    /// An observed clock must not go backwards even when checkpointing it
    /// fails. Only a successful save advances the persisted checkpoint.
    private var observedHighWaterMark: Date
    /// Called after every accepted change, with the new record, so the filter
    /// can re-read its enforcement state at once rather than on its next tick.
    public var onChange: ((PolicyRecord) -> Void)?

    public init(store: PolicyStore, clock: @escaping () -> Date = Date.init,
                health: @escaping () -> FilterHealth = { FilterHealth() }) {
        self.store = store
        self.clock = clock
        self.health = health
        let initial = store.load()
        self.record = initial
        self.observedHighWaterMark = initial.highWaterMark
        self.persistedMark = initial.highWaterMark
        if initial.recoveryRequired { persistenceError = PolicyAuthority.recoveryMessage }
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
            guard !record.recoveryRequired else { return record }
            var settled = PolicyAuthority.settle(record, wall: observeNow())
            let expired = settled.lock != record.lock
            if expired || settled.highWaterMark.timeIntervalSince(persistedMark)
                > Self.markCheckpoint {
                if expired { settled.revision += 1 }
                // Ending a lock is a persistent mutation too. If it cannot
                // be saved, retain the last policy and try again next read.
                _ = commit(settled)
            } else {
                record = settled
            }
            return record
        }
    }

    public func status() -> PolicyStatus {
        _ = current()
        return lock.withLockUnchecked { statusLocked() }
    }

    public func handle(_ request: PolicyRequest) -> PolicyResponse {
        if case let .checkIn(browser) = request {
            lock.withLockUnchecked {
                if checkIns.count > 64 { checkIns.removeAll() }
                checkIns[browser] = clock()
            }
            return PolicyResponse(accepted: true, refusal: nil, status: status())
        }
        let result: (PolicyResponse, PolicyRecord?) = lock.withLockUnchecked {
            switch PolicyAuthority.apply(request, to: record, wall: observeNow()) {
            case let .success(next):
                guard commit(next) else {
                    return (PolicyResponse(accepted: false, refusal: persistenceError,
                                           status: statusLocked()), nil)
                }
                return (PolicyResponse(accepted: true, refusal: nil, status: statusLocked()), next)
            case let .failure(refusal):
                return (PolicyResponse(accepted: false, refusal: refusal.message,
                                       status: statusLocked()), nil)
            }
        }
        if let next = result.1 { onChange?(next) }
        return result.0
    }

    /// Raise the rollback floor after a list generation is installed.
    @discardableResult
    public func raiseListFloor(to version: Int) -> Bool {
        lock.withLockUnchecked {
            guard !record.recoveryRequired else { return false }
            guard version > record.listVersionFloor else { return true }
            var next = record
            next.listVersionFloor = version
            next.revision += 1
            next.highWaterMark = observeNow()
            return commit(next)
        }
    }

    /// The system is stopping the filter. If a lock was running, say so in the
    /// record — the app reports it and re-arms.
    @discardableResult
    public func noteStoppedDuringLock() -> Bool {
        lock.withLockUnchecked {
            guard !record.recoveryRequired else { return false }
            let now = observeNow()
            guard PolicyAuthority.isLocked(record, now: now) else { return true }
            var next = record
            next.stoppedDuringLock = now
            next.revision += 1
            next.highWaterMark = now
            return commit(next)
        }
    }

    /// Carry forward a lock found only in the old mirrors — an install that
    /// predates the authority. Judged like any other proposal, so it can only
    /// ever extend what the authority already holds.
    @discardableResult
    public func adopt(legacy lock: LockStore.LockState?) -> Bool {
        guard let lock else { return true }
        return handle(.proposeLock(lock)).accepted
    }

    /// Call only while holding `lock`. Memory, checkpoint and success replies
    /// all follow the durable write, never precede it.
    private func commit(_ r: PolicyRecord) -> Bool {
        do {
            try store.save(r)
            record = r
            persistedMark = r.highWaterMark
            persistenceError = nil
            return true
        } catch {
            persistenceError = "Hisn could not save the protection policy. The previous policy stays in force. "
                + "Ask the administrator to check free disk space and policy-folder permissions, then retry. "
                + error.localizedDescription
            NSLog("[Hisn] CRITICAL: policy record not saved: %@", "\(error)")
            return false
        }
    }

    private func observeNow() -> Date {
        observedHighWaterMark = max(observedHighWaterMark, max(record.highWaterMark, clock()))
        return observedHighWaterMark
    }

    private func statusLocked() -> PolicyStatus {
        PolicyAuthority.status(record, health: health(), checkIns: checkIns,
                               persistenceError: persistenceError)
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
    /// Whether this copy says a lock is running — judged by THIS copy's own
    /// clock when the view was made. The merge used to re-judge both copies
    /// with the app's clock, whose high-water mark is a key in the user's
    /// defaults: forging it to 2099 made the authority's running lock look
    /// over, and the merge let the browser unlock.
    public var locked: Bool
    public var allowlist: [String]
    public var customBlocks: [String]
    public var customTerms: [String]
    public var blockedApps: [String]
    public var inspection: Inspection.Settings
    /// Apps the browser guard lets stay open during a lock.
    public var guardAllowed: [String]
    /// Kept separate from a real lock, so recovery never writes an invented
    /// permanent deadline into the user's mirrors.
    public var recoveryRequired: Bool

    public init(lock: LockStore.LockState?, effectiveDeadline: Date?, locked: Bool? = nil,
                allowlist: [String], customBlocks: [String], customTerms: [String],
                blockedApps: [String], inspection: Inspection.Settings,
                guardAllowed: [String] = [], recoveryRequired: Bool = false) {
        self.lock = lock
        self.effectiveDeadline = effectiveDeadline
        self.locked = locked ?? (lock != nil && effectiveDeadline != nil)
        self.allowlist = allowlist
        self.customBlocks = customBlocks
        self.customTerms = customTerms
        self.blockedApps = blockedApps
        self.inspection = inspection
        self.guardAllowed = guardAllowed
        self.recoveryRequired = recoveryRequired
    }

    /// Whether the lock this view describes is still running at `now` — for
    /// the browser, which compares `lockUntil` with its own clock anyway.
    public func isLocked(at now: Date) -> Bool {
        recoveryRequired || (locked && (effectiveDeadline.map { now < $0 } ?? false))
    }

    /// What this user's own stores say.
    public static func local(now: Date = LockStore.trustedNow(),
                             guardAllowed: [String] = []) -> PolicyView {
        let state = LockStore.read()
        let end = LockStore.effectiveDeadline(state)
        let running = now < end
        return PolicyView(lock: running ? state : nil,
                          effectiveDeadline: running ? end : nil,
                          locked: running,
                          allowlist: SiteLists.allowlist(),
                          customBlocks: SiteLists.customBlocks(),
                          customTerms: UserBlocks.terms(),
                          blockedApps: UserBlocks.apps(),
                          inspection: Inspection.read(),
                          guardAllowed: guardAllowed)
    }

    /// What the filter's authority says, judged by the authority's own clock.
    public init(status: PolicyStatus) {
        let r = status.record
        self.init(lock: status.isLocked && !r.recoveryRequired ? r.lock : nil,
                  effectiveDeadline: status.isLocked ? status.effectiveDeadline : nil,
                  locked: status.isLocked,
                  allowlist: r.recoveryRequired ? [] : r.allowlist, customBlocks: r.customBlocks,
                  customTerms: r.customTerms, blockedApps: r.blockedApps,
                  inspection: r.inspection, guardAllowed: r.recoveryRequired ? [] : r.guardAllowed,
                  recoveryRequired: r.recoveryRequired)
    }
}

public enum PolicyMerge {

    /// The stricter reading of two views of the same policy.
    ///
    /// While either says a lock is running, the fields come from the copies
    /// that say so, and where both do, each takes its stricter value: the
    /// later deadline, strict over standard, the union of blocks, words and
    /// apps, the intersection of allowances, every check either has on. So
    /// neither copy can loosen what the other holds — forging the user's
    /// defaults buys nothing while the authority says otherwise, and a filter
    /// that lost its record cannot unlock the browser while the mirrors hold
    /// the lock.
    ///
    /// Only LOCKED copies are combined. An authority that has not heard of
    /// the lock yet (a first lock, one from before the filter existed) holds
    /// whatever it held unlocked — often nothing — and intersecting with that
    /// wiped the allowlist, which a strict lock then could not get back.
    ///
    /// The early-release request travels with the app's copy (`editor`),
    /// requests and cancellations alike: the authority starts its own 48-hour
    /// wait when it first sees one, so passing it on shortens nothing. Picking
    /// the lock with the later EFFECTIVE end instead — as this once did —
    /// chose the authority's release-less copy over every request, and the
    /// sync then cancelled it.
    ///
    /// With no lock anywhere, `editor` is the answer, and the authority is
    /// brought into line with it.
    public static func stricter(editor: PolicyView, other: PolicyView) -> PolicyView {
        if editor.recoveryRequired || other.recoveryRequired {
            return PolicyView(lock: nil, effectiveDeadline: .distantFuture, locked: true,
                              allowlist: [], customBlocks: union(editor.customBlocks, other.customBlocks),
                              customTerms: union(editor.customTerms, other.customTerms),
                              blockedApps: union(editor.blockedApps, other.blockedApps),
                              inspection: Inspection.Settings(
                                text: true,
                                textSensitivity: max(editor.inspection.textSensitivity,
                                                     other.inspection.textSensitivity),
                                hostKeywords: true),
                              guardAllowed: [], recoveryRequired: true)
        }
        let locked = [editor, other].filter(\.locked)
        guard !locked.isEmpty else { return editor }

        // The later deadline; the app's copy on a tie.
        var base = locked[0]
        for v in locked.dropFirst()
        where (v.lock?.deadline ?? .distantPast) > (base.lock?.deadline ?? .distantPast) {
            base = v
        }
        var lock = base.lock
        if locked.contains(where: { $0.lock?.mode == "strict" }) { lock?.mode = "strict" }
        // The authority names the lock (start time and nonce) whenever it holds
        // one, so the copies converge on ONE lock — the one a partner approval
        // is checked against — even after the mirrors were replaced by another.
        if other.locked, let theirs = other.lock {
            lock?.startedAt = theirs.startedAt
            lock?.releaseNonce = theirs.releaseNonce
            lock?.selfReleaseAt = theirs.selfReleaseAt
        }
        if editor.locked, let mine = editor.lock, mine.releaseNonce == lock?.releaseNonce,
           mine.startedAt == lock?.startedAt {
            lock?.selfReleaseAt = mine.selfReleaseAt
        }

        lock?.commitmentUntil = locked.compactMap { $0.lock?.commitmentUntil }.max()
        let first = locked[0]
        let both = locked.count == 2
        let o = both ? locked[1] : first
        let allows = Set(first.allowlist).intersection(o.allowlist)
        let guardOK = Set(first.guardAllowed).intersection(o.guardAllowed)
        let e = first.inspection, i = o.inspection
        return PolicyView(
            lock: lock,
            // The later of the running copies' effective ends: a release the
            // authority has not seen yet cannot end the lock early anywhere.
            effectiveDeadline: locked.compactMap(\.effectiveDeadline).max(),
            locked: true,
            allowlist: first.allowlist.filter(allows.contains),
            customBlocks: union(first.customBlocks, o.customBlocks),
            customTerms: union(first.customTerms, o.customTerms),
            blockedApps: union(first.blockedApps, o.blockedApps),
            inspection: Inspection.Settings(
                text: e.text || i.text,
                textSensitivity: max(e.textSensitivity, i.textSensitivity),
                hostKeywords: e.hostKeywords || i.hostKeywords),
            guardAllowed: first.guardAllowed.filter(guardOK.contains))
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
    /// the lock in the browser at the same moment it ends everywhere else. It
    /// is sent whenever a copy says a lock runs; the browser compares it with
    /// its own clock, which a forged key in the app's defaults cannot move.
    public func bridgeReply(listVersion: Int) -> [String: Any] {
        [
            "lockUntil": locked ? (effectiveDeadline ?? .distantPast).timeIntervalSince1970 * 1000
                                : Double(0),
            "mode": recoveryRequired ? "strict" : (locked ? (lock?.mode ?? "blocklist") : "off"),
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
