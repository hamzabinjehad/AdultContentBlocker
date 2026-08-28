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

    /// Root-owned. Removing this needs administrator authority — which the
    /// person running under a standard account does not have.
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

        public init(deadline: Date, mode: String, startedAt: Date) {
            self.deadline = deadline
            self.mode = mode
            self.startedAt = startedAt
        }

        public static let unlocked = LockState(deadline: .distantPast,
                                               mode: "off",
                                               startedAt: .distantPast)
    }

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

        guard let winner = candidates.max(by: { $0.deadline < $1.deadline }) else {
            return .unlocked
        }

        // Self-heal: any store that is behind gets brought back up to the
        // winning value. Clearing one copy is then not merely useless, it is
        // undone on the next read.
        if candidates.count < 3 || candidates.contains(where: { $0.deadline < winner.deadline }) {
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
        let current = readWithoutHealing()
        if let current, state.deadline < current.deadline {
            NSLog("[Hisn] refused to shorten lock: %@ < %@",
                  "\(state.deadline)", "\(current.deadline)")
            return false
        }

        var ok = false
        ok = writeToDefaults(state) || ok
        ok = writeToKeychain(state) || ok
        ok = writeToSystemFile(state) || ok

        if !ok { NSLog("[Hisn] CRITICAL: every lock store failed to write") }
        return ok
    }

    /// Clear the lock. Only legal once the deadline has genuinely passed.
    @discardableResult
    public static func clearIfExpired() -> Bool {
        let state = read()
        guard trustedNow() >= state.deadline else { return false }
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
        NSLog("[Hisn] lock ended early under approval %@", approval.id)
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
        trustedNow() < read().deadline
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
