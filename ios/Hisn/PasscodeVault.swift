import Foundation
import Security

/// A Screen Time passcode Hisn made, kept from the person until a date they chose.
///
/// With it set as the Screen Time passcode, and "Deleting Apps: Don't Allow"
/// under Content & Privacy, no app (Hisn included) can be deleted and Screen
/// Time cannot be changed before that date — on an ordinary iPhone, with no
/// second person. Apple still lets the adult who set the passcode reset it with
/// their Apple Account password, and erasing the phone removes everything.
struct PasscodeVault: Codable, Equatable {
    let passcode: String
    let createdAt: Date
    var revealAt: Date
    /// The person said they entered it. The setup window ends either way.
    var sealed: Bool
    /// Set once the date has passed on both clocks; it then stays shown.
    var revealedAt: Date?
}

enum PasscodeVaultPolicy {
    /// Long enough to type it into Settings; then it hides itself.
    static let setupWindow: TimeInterval = 15 * 60
    static let durations = [1, 7, 30, 90]
    static let maximumSeal: TimeInterval = 366 * 86400

    enum Phase: Equatable {
        case setup(passcode: String, hidesAt: Date)
        case sealed(until: Date)
        case due(since: Date)
        case revealed(passcode: String)
    }

    /// What the person may see now. A clock set back before the vault was made
    /// never reopens the setup window; nothing but a verified reveal shows it again.
    static func phase(_ vault: PasscodeVault, now: Date) -> Phase {
        if vault.revealedAt != nil { return .revealed(passcode: vault.passcode) }
        let hidesAt = vault.createdAt.addingTimeInterval(setupWindow)
        if !vault.sealed, now >= vault.createdAt, now < hidesAt {
            return .setup(passcode: vault.passcode, hidesAt: hidesAt)
        }
        return now >= vault.revealAt ? .due(since: vault.revealAt) : .sealed(until: vault.revealAt)
    }

    enum RevealRefusal: Equatable { case notYet, needsNetwork, clockDisagrees }

    /// The device clock alone can be moved forward, so the date must also have
    /// passed on a server clock read over HTTPS: the same rule as the commitment end.
    static func revealRefusal(_ vault: PasscodeVault, deviceNow: Date, serverNow: Date?) -> RevealRefusal? {
        switch TrustedTime.verdict(for: vault.revealAt, deviceNow: deviceNow, trustedNow: serverNow) {
        case .notYet: return .notYet
        case .unverified: return .needsNetwork
        case .clockAhead: return .clockDisagrees
        case .passed: return nil
        }
    }

    /// The passcode, or one dot per digit while the scene is not active.
    static func displayed(_ passcode: String, sceneActive: Bool) -> String {
        sceneActive ? passcode : String(repeating: "•", count: passcode.count)
    }

    static func isValid(_ vault: PasscodeVault) -> Bool {
        let length = vault.revealAt.timeIntervalSince(vault.createdAt)
        return vault.passcode.count == 4 && vault.passcode.allSatisfy { $0.isASCII && $0.isNumber }
            && length.isFinite && length > 0 && length <= maximumSeal
    }

    /// Four digits nobody would guess first: no repeats like 1111, no runs like 1234 or 9876.
    static func isGuessable(_ code: String) -> Bool {
        let digits = code.compactMap(\.wholeNumberValue)
        guard digits.count == 4 else { return true }
        if Set(digits).count == 1 { return true }
        let steps = zip(digits, digits.dropFirst()).map { $1 - $0 }
        return steps.allSatisfy { $0 == 1 } || steps.allSatisfy { $0 == -1 }
    }

    static func generate<G: RandomNumberGenerator>(using generator: inout G) -> String {
        while true {
            let number = String(Int.random(in: 0...9999, using: &generator))
            let code = String(repeating: "0", count: 4 - number.count) + number
            if !isGuessable(code) { return code }
        }
    }

    static func make<G: RandomNumberGenerator>(days: Int, now: Date, using generator: inout G) -> PasscodeVault? {
        guard durations.contains(days) else { return nil }
        let vault = PasscodeVault(passcode: generate(using: &generator), createdAt: now,
                                  revealAt: now.addingTimeInterval(Double(days) * 86400), sealed: false)
        return isValid(vault) ? vault : nil
    }

    /// Only ever later, and only while still sealed.
    static func extending(_ vault: PasscodeVault, byDays days: Int) -> PasscodeVault? {
        guard [7, 30].contains(days), vault.revealedAt == nil else { return nil }
        var next = vault
        next.revealAt = vault.revealAt.addingTimeInterval(Double(days) * 86400)
        return isValid(next) && next.revealAt > vault.revealAt ? next : nil
    }
}

// MARK: - Storage

protocol PasscodeVaultStorage {
    func load() throws -> Data?
    func save(_ data: Data?) throws
}

/// Device-only Keychain: not in iCloud, and an iPhone backup cannot be read
/// for it elsewhere. Never mirrored to UserDefaults, which backups do include.
final class KeychainPasscodeVaultStorage: PasscodeVaultStorage {
    enum Failure: Error { case inaccessible, writeFailed }
    private let service: String
    init(service: String = "hisn.mobile.passcodeVault") { self.service = service }
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: "vault"]
    }
    func load() throws -> Data? {
        var search = query
        search[kSecReturnData as String] = true
        search[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(search as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data else { throw Failure.inaccessible }
        return data
    }
    func save(_ data: Data?) throws {
        guard let data else {
            let status = SecItemDelete(query as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw Failure.writeFailed }
            return
        }
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw Failure.writeFailed }
        } else if status != errSecSuccess { throw Failure.writeFailed }
    }
}

/// Tests never touch the person's real Keychain.
final class MemoryPasscodeVaultStorage: PasscodeVaultStorage {
    var data: Data?
    var failing = false
    func load() throws -> Data? {
        if failing { throw KeychainPasscodeVaultStorage.Failure.inaccessible }
        return data
    }
    func save(_ data: Data?) throws {
        if failing { throw KeychainPasscodeVaultStorage.Failure.writeFailed }
        self.data = data
    }
}

// MARK: - Server time

// `ServerTimeSource` and `AppleServerTime` live in shared/apple/TrustedTime.swift:
// the commitment and the daily budget use the same check.

// MARK: - Controller

@MainActor
final class PasscodeVaultController: ObservableObject {
    @Published private(set) var vault: PasscodeVault?
    @Published private(set) var storageHealthy = true
    @Published private(set) var messageKey: String?
    @Published private(set) var busy = false
    @Published private(set) var now: Date
    private let storage: PasscodeVaultStorage
    private let serverTime: ServerTimeSource
    private let clock: () -> Date

    init(storage: PasscodeVaultStorage? = nil, serverTime: ServerTimeSource = AppleServerTime(),
         clock: @escaping () -> Date = Date.init) {
        self.storage = storage ?? (NSClassFromString("XCTestCase") == nil
            ? KeychainPasscodeVaultStorage() : MemoryPasscodeVaultStorage())
        self.serverTime = serverTime
        self.clock = clock
        now = clock()
        reload()
    }

    var phase: PasscodeVaultPolicy.Phase? {
        vault.map { PasscodeVaultPolicy.phase($0, now: now) }
    }

    func tick() { now = clock() }

    func reload() {
        do {
            if let data = try storage.load() {
                let decoded = try JSONDecoder().decode(PasscodeVault.self, from: data)
                guard PasscodeVaultPolicy.isValid(decoded) else { throw KeychainPasscodeVaultStorage.Failure.inaccessible }
                vault = decoded
            } else {
                vault = nil
            }
            storageHealthy = true
        } catch {
            // Unreadable is never "no vault": a new one must not replace the sealed passcode.
            storageHealthy = false
            messageKey = "vault.storageFailed"
        }
        now = clock()
    }

    func create(days: Int) {
        reload()
        guard storageHealthy, !busy else { return }
        if let vault, vault.revealedAt == nil {
            messageKey = "vault.exists"; return
        }
        var generator = SystemRandomNumberGenerator()
        guard let next = PasscodeVaultPolicy.make(days: days, now: clock(), using: &generator) else {
            messageKey = "vault.failed"; return
        }
        store(next)
    }

    func seal() {
        reload()
        guard storageHealthy, var current = vault, current.revealedAt == nil, !current.sealed else { return }
        current.sealed = true
        store(current)
    }

    func extend(days: Int) {
        reload()
        guard storageHealthy, let current = vault, let next = PasscodeVaultPolicy.extending(current, byDays: days) else {
            messageKey = "vault.failed"; return
        }
        store(next)
    }

    func unseal() async {
        reload()
        guard storageHealthy, !busy, var current = vault, current.revealedAt == nil else { return }
        busy = true
        defer { busy = false }
        let deviceNow = clock()
        let server = deviceNow >= current.revealAt ? await serverTime.currentTime() : nil
        switch PasscodeVaultPolicy.revealRefusal(current, deviceNow: deviceNow, serverNow: server) {
        case .notYet?: messageKey = "vault.notYet"
        case .needsNetwork?: messageKey = "vault.needsNetwork"
        case .clockDisagrees?: messageKey = "vault.clockDisagrees"
        case nil:
            current.sealed = true
            current.revealedAt = deviceNow
            store(current)
        }
    }

    /// Only after the reveal: the person may then keep the passcode elsewhere or change it.
    func forget() {
        reload()
        guard storageHealthy, vault?.revealedAt != nil else { return }
        do { try storage.save(nil); vault = nil; messageKey = nil }
        catch { storageHealthy = false; messageKey = "vault.storageFailed" }
    }

    private func store(_ next: PasscodeVault) {
        do {
            try storage.save(try JSONEncoder().encode(next))
            vault = next
            messageKey = nil
        } catch {
            storageHealthy = false
            messageKey = "vault.storageFailed"
        }
        now = clock()
    }
}
