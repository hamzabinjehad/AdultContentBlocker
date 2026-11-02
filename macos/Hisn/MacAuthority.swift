import Foundation

/// Who can change Hisn's protection at the operating-system level.
///
/// The app cannot make itself an administrator, and a hidden self-granted
/// admin account would be a backdoor: one bug or one extracted password would
/// own every install. The supported design is a visible guardian account held
/// by a real second person, with the daily account standard. The app only
/// **reads** that state, shows it honestly, and refuses to offer authority
/// changes while a lock runs.
///
/// Everything here is a plain value, so tests can pin every state without
/// creating accounts or touching launchd. `current(lockActive:)` is the only
/// part that reads this Mac.
public struct MacAuthority: Equatable {
    public static let guardianUser = "hisn_guardian"

    public enum Guardian: String, Equatable {
        case missing
        case notAdmin
        case noSecureToken
        case unreadable
        case ready
    }

    public enum Daemon: String, Equatable {
        case missing
        case notLoaded
        case loaded
    }

    public enum Level: String, Equatable {
        /// Daily account standard, guardian admin with a secure token, daemon loaded.
        case verified
        /// Some of the evidence is present, or the answer is incomplete.
        case partial
        /// No usable evidence.
        case unavailable
    }

    public let dailyUser: String
    /// nil when membership could not be read. Never guessed.
    public let dailyUserIsAdmin: Bool?
    public let guardian: Guardian
    public let daemon: Daemon
    /// A protection lock is running. Authority and removal changes are disabled.
    public let lockActive: Bool

    public init(dailyUser: String, dailyUserIsAdmin: Bool?, guardian: Guardian,
                daemon: Daemon, lockActive: Bool) {
        self.dailyUser = dailyUser
        self.dailyUserIsAdmin = dailyUserIsAdmin
        self.guardian = guardian
        self.daemon = daemon
        self.lockActive = lockActive
    }

    /// The strongest claim this evidence supports. A missing answer is never
    /// promoted to a positive one.
    public var level: Level {
        if dailyUserIsAdmin == nil && guardian == .unreadable && daemon == .missing {
            return .unavailable
        }
        if dailyUserIsAdmin == false && guardian == .ready && daemon == .loaded {
            return .verified
        }
        return .partial
    }

    public var isVerified: Bool { level == .verified }

    /// Whether the app may offer an authority or removal change. A lock makes
    /// the answer no even when the operating system would still allow it; the
    /// account split is what enforces the rest.
    public var mayChangeAuthority: Bool { !lockActive }

    /// Restoring administrator rights is a guardian action, never a daily-user
    /// action. The app only reveals the command after the lock ends.
    public var mayOfferRestoreCommand: Bool { !lockActive && guardian == .ready }

    /// Pure mapping for the guardian account's three facts.
    public static func guardian(exists: Bool, isAdmin: Bool, hasSecureToken: Bool?,
                                unreadable: Bool = false) -> Guardian {
        if unreadable { return .unreadable }
        if !exists { return .missing }
        if !isAdmin { return .notAdmin }
        guard hasSecureToken == true else { return .noSecureToken }
        return .ready
    }

    public static func daemon(plistExists: Bool, loaded: Bool) -> Daemon {
        if !plistExists { return .missing }
        return loaded ? .loaded : .notLoaded
    }

    /// The command the guardian runs to create the visible administrator.
    /// Shown and copied by the app; never executed by it.
    public static let guardianSetupCommand =
        "sudo macos/setup_guardian.sh --create-admin"

    /// The guardian runs this to restore the daily account's administrator
    /// rights after the lock and the account split. The app never stores the
    /// guardian password.
    public static func restoreAdminCommand(dailyUser: String) -> String {
        "sudo dseditgroup -o edit -a \(dailyUser) -t user admin"
    }

    // MARK: - Reading this Mac

    /// Read-only evidence. No sudo, no prompts, no account changes.
    public static func current(lockActive: Bool, user: String = NSUserName()) -> MacAuthority {
        let dailyAdmin = adminMembership(user)
        let guardianExists = process("/usr/bin/id", ["-u", guardianUser]).status == 0
        let guardianAdmin = guardianExists ? (adminMembership(guardianUser) ?? false) : false
        let token = guardianExists ? secureToken(guardianUser) : false
        let guardianState = guardian(exists: guardianExists, isAdmin: guardianAdmin,
                                     hasSecureToken: token,
                                     unreadable: guardianExists && token == nil)
        let plist = "/Library/LaunchDaemons/app.hisn.guardian.plist"
        let plistExists = FileManager.default.fileExists(atPath: plist)
        let loaded = process("/bin/launchctl", ["print", "system/app.hisn.guardian"]).status == 0
        return MacAuthority(dailyUser: user, dailyUserIsAdmin: dailyAdmin,
                            guardian: guardianState,
                            daemon: daemon(plistExists: plistExists, loaded: loaded),
                            lockActive: lockActive)
    }

    /// Membership of the admin group, read from the directory because this
    /// process's groups only change at the next login.
    public static func adminMembership(_ user: String) -> Bool? {
        let result = process("/usr/bin/dseditgroup", ["-o", "checkmember", "-m", user, "admin"])
        if result.status == 0 { return true }
        // dseditgroup exits nonzero for "not a member" and for an unreadable
        // account. Tell them apart so a missing answer stays nil.
        if result.output.localizedCaseInsensitiveContains("not a member") { return false }
        if result.output.isEmpty { return false }
        return nil
    }

    /// `sysadminctl -secureTokenStatus` prints ENABLED/DISABLED. Anything else
    /// is treated as absent rather than as a positive secure-token claim.
    public static func secureToken(_ user: String) -> Bool? {
        let result = process("/usr/sbin/sysadminctl", ["-secureTokenStatus", user])
        let text = result.output.uppercased()
        if text.contains("SECURETOKENSTATUS") || text.contains("SECURE TOKEN") {
            if text.contains("ENABLED") { return true }
            if text.contains("DISABLED") { return false }
        }
        return nil
    }

    private static func process(_ path: String, _ arguments: [String]) -> (status: Int32, output: String) {
        let task = Process()
        task.executableURL = URL(fileURLWithPath: path)
        task.arguments = arguments
        let pipe = Pipe()
        task.standardOutput = pipe
        task.standardError = pipe
        do { try task.run() } catch { return (-1, "") }
        task.waitUntilExit()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        return (task.terminationStatus, String(decoding: data, as: UTF8.self))
    }
}
