import Foundation
import Security

/// Tamper-resistant persistence for the lock deadline.
///
/// The threat is not a sophisticated attacker. It is the same person who
/// installed this, at 2am, motivated, with a search engine. The three moves
/// they will actually try are: delete the app's data, set the system clock
/// back, and reinstall the app. This type is built around exactly those.
///
/// Design rules, in order of importance:
///
///  1. **Redundancy, resolved by MAX.** The deadline is written to several
///     independent stores. On read we take the *latest* deadline any store
///     reports, never the earliest. Deleting one copy therefore achieves
///     nothing — you would have to find and clear every one of them, and any
///     store you miss restores the lock.
///
///  2. **A monotonic high-water mark.** We record the latest wall-clock time we
///     have ever observed. If the clock now reads earlier than that, time
///     "went backwards", which on a normal machine does not happen. We treat
///     the high-water mark as the current time instead, so winding the clock
///     back freezes the countdown rather than skipping it.
///
///  3. **Failure is closed.** A store that cannot be read is ignored; a store
///     that cannot be written is a logged error, not a reason to abandon the
///     lock. No error path in this file ever shortens a deadline.
public enum LockStore {

    // MARK: - Locations
    //
    // These three are `var` rather than `let` only so a test run can point them
    // somewhere private. Sharing them with the installed app means a test
    // writes a real multi-week lock onto the machine running it — and because
    // `write` refuses to shorten a deadline, the suite then passes once and
    // fails every run after that. Nothing in the app reassigns them.

    /// Shared with the network extension, which needs the deadline too.
    public static var appGroup = "group.app.hisn"

    /// A third mirror under `/Library`, meant to be out of a standard user's
    /// reach. Honestly: it is written by THIS process, as the user, so it can
    /// only be created while that user is an administrator, and the file then
    /// stays owned by them after `setup_guardian.sh --demote-me`. No privileged
    /// writer exists yet — `docs/TAMPER_MODEL.md` has the audit and the design
    /// (the filter, which runs as root, owning the authoritative copy). Until
    /// then this mirror protects against accidents and casual deletion, not
    /// against a standard user who knows the path.
    public static var systemPath = "/Library/Application Support/Hisn/lock.plist"

    public static var keychainService = "app.hisn.lock"

    private static let keychainAccount = "deadline"

    private static let deadlineKey = "lockDeadline"
    private static let highWaterKey = "timeHighWaterMark"
    private static let modeKey = "lockMode"

    // MARK: - Public API

    public struct LockState: Codable, Equatable {
        public var deadline: Date
        public var mode: String          // "blocklist" | "strict"
        public var startedAt: Date

        /// When a requested early release becomes effective, if one is pending.
        ///
        /// Optional, and decoded leniently, so a state written by a build that
        /// predates this field still loads — a lock recorded before the upgrade
        /// must not be lost, and `nil` is the safe reading of "no request".
        public var selfReleaseAt: Date?

        /// Drawn when the lock starts and kept for its life. It is half of the
        /// code an accountability partner signs (`PartnerService.challenge`),
        /// so an approval cannot be prepared before this lock existed or
        /// carried to the next one. Optional for the same reason as
        /// `selfReleaseAt`: a lock written before it existed must still load.
        public var releaseNonce: String?

        public init(deadline: Date, mode: String, startedAt: Date,
                    selfReleaseAt: Date? = nil, releaseNonce: String? = nil) {
            self.deadline = deadline
            self.mode = mode
            self.startedAt = startedAt
            self.selfReleaseAt = selfReleaseAt
            self.releaseNonce = releaseNonce
        }

        public static let unlocked = LockState(deadline: .distantPast,
                                               mode: "off",
                                               startedAt: .distantPast)
    }

    /// How long a self-requested early release takes to arrive.
    ///
    /// Long enough that the urge has passed, short enough to handle something
    /// genuinely urgent. Lives here rather than in `LockManager` because
    /// `effectiveDeadline` — the function every enforcement path consults —
    /// needs it, and the network extension has no `LockManager`.
    public static let selfReleaseDelay: TimeInterval = 48 * 3600

    /// The effective current time, protected against a rewound system clock.
    ///
    /// Returns the later of "what the clock says" and "the latest time we have
    /// ever seen". Setting the date back one year makes the countdown stand
    /// still; it does not make it run out.
    public static func trustedNow() -> Date {
        let wall = Date()
        let mark = highWaterMark()
        let now = max(wall, mark)
        if now > mark { setHighWaterMark(now) }
        return now
    }

    /// Read the authoritative lock state: the latest deadline any store holds.
    public static func read() -> LockState {
        let candidates = [
            readFromDefaults(),
            readFromKeychain(),
            readFromSystemFile(),
        ].compactMap { $0 }

        guard var winner = candidates.max(by: { $0.deadline < $1.deadline }) else {
            return .unlocked
        }

        // Resolve the release date the same way as the deadline: take the most
        // restrictive answer any store gives. The LATEST pending release wins,
        // so writing an early date into one store achieves nothing — and a
        // store that reports no request at all cannot cancel one that another
        // store still holds. Cancelling has to go through `write`, which
        // updates every store at once.
        winner.selfReleaseAt = candidates.compactMap(\.selfReleaseAt).max()

        // Self-heal: any store that is behind gets brought back up to the
        // winning value. Clearing one copy is then not merely useless, it is
        // undone on the next read.
        if candidates.count < 3
            || candidates.contains(where: { $0.deadline < winner.deadline })
            || candidates.contains(where: { $0.selfReleaseAt != winner.selfReleaseAt }) {
            write(winner)
        }
        return winner
    }

    /// Persist a lock state to every store.
    ///
    /// Refuses to shorten an existing deadline. Extending is always allowed;
    /// shortening is precisely the operation this whole system exists to
    /// prevent, so it is rejected here rather than trusted to callers.
    @discardableResult
    public static func write(_ state: LockState) -> Bool {
        write(state, adoptingAuthority: false)
    }

    /// `FilterSync`'s write: the filter's root-owned lock replaces the mirrors'
    /// even when it names a different lock (another nonce and start time) —
    /// after the mirrors were deleted and a short lock started in their place,
    /// the ordinary rules refused the authority's lock forever, and the two
    /// copies never agreed again. Shortening, bringing a release forward and
    /// leaving strict are refused here exactly as everywhere else.
    @discardableResult
    public static func adopt(_ state: LockState) -> Bool {
        write(state, adoptingAuthority: true)
    }

    private static func write(_ state: LockState, adoptingAuthority: Bool) -> Bool {
        if let why = refusal(current: readWithoutHealing(), proposed: state,
                             now: trustedNow(), adoptingAuthority: adoptingAuthority) {
            NSLog("[Hisn] refused lock write: %@", why)
            return false
        }

        var ok = false
        ok = writeToDefaults(state) || ok
        ok = writeToKeychain(state) || ok
        ok = writeToSystemFile(state) || ok

        if !ok { NSLog("[Hisn] CRITICAL: every lock store failed to write") }
        return ok
    }

    /// Why `proposed` may not replace `current`, or nil if it may.
    ///
    /// The one rule set every writer of a lock applies — these mirrors, and
    /// the filter's root-owned `PolicyAuthority` — so the two cannot drift:
    ///
    ///  * the deadline never moves earlier;
    ///  * a pending release may be cancelled (nil — that TIGHTENS) or pushed
    ///    later, never pulled earlier: "requestable at any time, cannot be
    ///    accelerated, can be cancelled" is the entire mechanism;
    ///  * a running strict lock stays strict. Only the Lock page's controls
    ///    kept this before — `LockManager.start` with a later deadline and
    ///    `strict: false` rewrote a strict lock as a blocklist one, which in
    ///    strict mode's terms is the step from "only my allowlist" to "most of
    ///    the web".
    ///
    /// A lock that has fully run out (`now` past its effective deadline) is
    /// no constraint at all, so anything may replace it.
    public static func refusal(current: LockState?, proposed: LockState,
                               now: Date, adoptingAuthority: Bool = false) -> String? {
        guard let current, now < current.deadline else { return nil }
        if proposed.deadline < current.deadline {
            return "would shorten the lock (\(proposed.deadline) < \(current.deadline))"
        }
        if let existing = current.selfReleaseAt, let asked = proposed.selfReleaseAt,
           asked < existing {
            return "would bring a requested release forward (\(asked) < \(existing))"
        }
        if current.mode == "strict", proposed.mode != "strict" {
            return "would switch a running strict lock out of strict mode"
        }
        // The nonce and the start time name the lock a partner approval is
        // for. Changing either — or giving a lock from before nonces existed
        // one of an earlier, approved lock — would let an old approval end
        // this one.
        if !adoptingAuthority {
            if proposed.releaseNonce != current.releaseNonce {
                return "would change which lock a partner approval applies to"
            }
            if proposed.startedAt != current.startedAt {
                return "would change when the running lock started"
            }
        }
        return nil
    }

    /// Clear the lock. Only legal once the deadline has genuinely passed.
    @discardableResult
    public static func clearIfExpired() -> Bool {
        let state = read()
        guard trustedNow() >= effectiveDeadline(state) else { return false }
        wipeAllStores()
        return true
    }

    /// The one sanctioned way to end a lock early.
    ///
    /// Deliberately separate from `clearIfExpired`, and deliberately awkward to
    /// call: it takes proof that a second party authorised this. There is no
    /// "force unlock" that the person under the lock can reach on their own,
    /// because a bypass the user can invoke alone is not a lock at all.
    ///
    /// - Parameter approval: an opaque server-issued approval that
    ///   `PartnerService` has already verified. Passed in only so that this
    ///   function cannot be called from a code path that skipped verification.
    @discardableResult
    public static func clearWithPartnerApproval(_ approval: PartnerApproval) -> Bool {
        NSLog("[Hisn] lock ended early under a partner approval of %@", approval.challenge)
        wipeAllStores()
        return true
    }

    private static func wipeAllStores() {
        defaults?.removeObject(forKey: deadlineKey)
        defaults?.removeObject(forKey: modeKey)
        deleteKeychain()
        try? FileManager.default.removeItem(atPath: systemPath)
        // The high-water mark is intentionally NOT cleared. It costs nothing to
        // keep and it means a future lock still cannot be skipped by winding
        // the clock back to before this one.
    }

    public static func isLocked() -> Bool {
        trustedNow() < effectiveDeadline(read())
    }

    /// Whether strict enforcement is in force for `state` right now.
    ///
    /// THE function the network filter consults on every tick, kept here so
    /// it cannot drift from what the app and the bridge tell everyone else.
    /// It used to live in the filter as `mode == "strict" && now < deadline`
    /// — the ORIGINAL deadline — while the bridge told the browser the lock had
    /// ended at the effective one. After a matured self-release the Mac kept
    /// denying every socket that was not allowlisted for as long as the
    /// original lock had left to run, and every status light said "unlocked".
    public static func strictModeActive(_ state: LockState,
                                        now: Date = trustedNow()) -> Bool {
        state.mode == "strict" && now < effectiveDeadline(state)
    }

    // MARK: - Early release

    /// When this lock actually ends: the deadline, or a pending self-release if
    /// one has genuinely matured.
    ///
    /// ── WHY THIS IS NOT JUST `selfReleaseAt` ────────────────────────────────
    /// The release date is local state, and local state is editable. Before
    /// this function existed the date sat in plain preferences under
    /// `selfReleaseAt`, where a single `defaults write` set it to any value —
    /// so honouring it directly would have turned the 48-hour delay into an
    /// instant unlock, which is the exact opposite of what it is for.
    ///
    /// The defence is the same shape as the clock-rollback defence: we record
    /// the moment a given release date FIRST became visible to us, and the
    /// release is not honoured until `selfReleaseDelay` after that moment,
    /// whatever the date itself claims. Forging a date in the past therefore
    /// buys nothing — the waiting period starts when we first see it.
    ///
    /// Clearing the first-seen record RESTARTS the wait rather than skipping
    /// it, so tampering with that store fails closed too.
    public static func effectiveDeadline(_ state: LockState) -> Date {
        guard let requested = state.selfReleaseAt else { return state.deadline }
        return effectiveDeadline(state, releaseFirstSeen: firstSeen(requested))
    }

    /// The same answer given the moment the release was first observed, for a
    /// caller that keeps that record itself — the filter's `PolicyAuthority`,
    /// whose record is root-owned rather than in the user's defaults.
    public static func effectiveDeadline(_ state: LockState,
                                         releaseFirstSeen: Date) -> Date {
        guard let requested = state.selfReleaseAt else { return state.deadline }
        let matured = releaseFirstSeen.addingTimeInterval(selfReleaseDelay)
        // Never later than the deadline: a release request may only bring the
        // end forward, and a lock that has run its course is over regardless.
        return min(state.deadline, max(requested, matured))
    }

    /// The first moment we observed this particular release date.
    ///
    /// Keyed by the date itself, so moving the request later starts a fresh
    /// observation rather than inheriting the old one's maturity.
    private static func firstSeen(_ date: Date) -> Date {
        let key = "selfReleaseFirstSeen.\(Int(date.timeIntervalSince1970))"
        if let seen = defaults?.object(forKey: key) as? Date { return seen }
        let now = trustedNow()
        defaults?.set(now, forKey: key)
        return now
    }

    // MARK: - High-water mark

    private static func highWaterMark() -> Date {
        let stored = defaults?.object(forKey: highWaterKey) as? Date ?? .distantPast
        let fileMark = (try? FileManager.default
            .attributesOfItem(atPath: systemPath)[.modificationDate] as? Date) ?? nil
        return max(stored, fileMark ?? .distantPast)
    }

    private static func setHighWaterMark(_ date: Date) {
        defaults?.set(date, forKey: highWaterKey)
    }

    // MARK: - Store: UserDefaults (app group)

    private static var defaults: UserDefaults? {
        UserDefaults(suiteName: appGroup)
    }

    private static func readFromDefaults() -> LockState? {
        guard let data = defaults?.data(forKey: deadlineKey) else { return nil }
        return try? JSONDecoder().decode(LockState.self, from: data)
    }

    private static func writeToDefaults(_ state: LockState) -> Bool {
        guard let data = try? JSONEncoder().encode(state) else { return false }
        defaults?.set(data, forKey: deadlineKey)
        return true
    }

    // MARK: - Store: Keychain

    private static func keychainQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
        ]
    }

    private static func readFromKeychain() -> LockState? {
        var query = keychainQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(LockState.self, from: data)
    }

    private static func writeToKeychain(_ state: LockState) -> Bool {
        guard let data = try? JSONEncoder().encode(state) else { return false }
        let query = keychainQuery()

        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecSuccess { return true }

        var insert = query
        insert[kSecValueData as String] = data
        // Must survive reboot and be readable before first unlock, so the
        // filter can enforce the lock on a machine that just booted.
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
    }

    private static func deleteKeychain() {
        SecItemDelete(keychainQuery() as CFDictionary)
    }

    // MARK: - Store: root-owned file

    private static func readFromSystemFile() -> LockState? {
        guard let data = FileManager.default.contents(atPath: systemPath) else { return nil }
        return try? JSONDecoder().decode(LockState.self, from: data)
    }

    private static func writeToSystemFile(_ state: LockState) -> Bool {
        guard let data = try? JSONEncoder().encode(state) else { return false }
        let dir = (systemPath as NSString).deletingLastPathComponent
        do {
            try FileManager.default.createDirectory(
                atPath: dir, withIntermediateDirectories: true,
                attributes: [.posixPermissions: 0o755])
            try data.write(to: URL(fileURLWithPath: systemPath), options: .atomic)
            try FileManager.default.setAttributes(
                [.posixPermissions: 0o644], ofItemAtPath: systemPath)
            return true
        } catch {
            // Expected when the app runs unprivileged; the privileged helper
            // owns this store. Not fatal — the other two still hold the lock.
            NSLog("[Hisn] system lock file unavailable: %@", error.localizedDescription)
            return false
        }
    }

    /// Plain read with no self-healing, used by `write` to avoid recursion.
    private static func readWithoutHealing() -> LockState? {
        [readFromDefaults(), readFromKeychain(), readFromSystemFile()]
            .compactMap { $0 }
            .max(by: { $0.deadline < $1.deadline })
    }
}
