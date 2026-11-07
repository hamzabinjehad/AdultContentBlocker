import Foundation

/// Whether this Mac is managed by an organization.
///
/// Two pieces of evidence are separate and both matter:
///
/// 1. **The profile flag.** `HisnManaged` in the app's own preference domain,
///    read through `CFPreferences`'s forced layer. A value the person can write
///    with `defaults write` is not management.
/// 2. **The enrollment.** `/usr/bin/profiles status -type enrollment` is the
///    system command that reports whether the Mac is actually enrolled in MDM.
///    It is a CLI, not a framework API, so the parser is conservative: an
///    output shape it does not recognize becomes `.unknown`. A profile flag
///    alone cannot tell an organization deployment from a profile the person
///    installed on their own; the enrollment can.
///
/// Managed mode changes what the app offers, not what the OS enforces. It hides
/// local removal and weakening controls; it cannot stop a local administrator
/// from removing the profile or erasing the Mac. The deployment note
/// `docs/ORGANIZATION_MODE.md` states that limit, and
/// `docs/MDM_DEPLOYMENT.md` covers the fleet route.
public struct ManagedMode: Equatable, Sendable {
    public let isManaged: Bool
    public let organizationName: String?
    /// The `HisnProfileVersion` the organization profile declared. `nil` means
    /// an older profile (or a local one), not a claim that anything is missing.
    public let profileVersion: Int?
    /// Whether the profile says it delivers the managed system filter. This is
    /// a declaration used to keep the app from overwriting a profile-owned
    /// filter; the filter's own status is still the evidence that it runs.
    public let filterManaged: Bool
    /// What the Mac reports about MDM enrollment. `.unknown` when the probe
    /// could not answer — never silently treated as "not enrolled".
    public let enrollment: MDMEnrollment

    public init(isManaged: Bool, organizationName: String? = nil,
                profileVersion: Int? = nil, filterManaged: Bool = false,
                enrollment: MDMEnrollment = .unknown) {
        self.isManaged = isManaged
        self.organizationName = organizationName
        self.profileVersion = profileVersion
        self.filterManaged = filterManaged
        self.enrollment = enrollment
    }

    public static let none = ManagedMode(isManaged: false)

    /// The organization name when the profile supplied one, otherwise a
    /// neutral phrase that reads correctly in both languages.
    public var displayName: String {
        let trimmed = organizationName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? String(localized: "your organization") : trimmed
    }

    public var isMDMEnrolled: Bool { enrollment.isEnrolled }

    public static let domain = "app.hisn.Hisn"
    public static let managedKey = "HisnManaged"
    public static let organizationKey = "OrganizationName"
    public static let profileVersionKey = "HisnProfileVersion"
    public static let filterManagedKey = "HisnFilterManaged"

    public static func current() -> ManagedMode {
        current(probingEnrollment: false)
    }

    /// The Settings card asks for enrollment even when the Hisn profile is not
    /// installed yet: "enrolled in MDM, but Hisn is not managed" is a state the
    /// handoff must be able to see. Every other caller keeps the cheap path and
    /// does not run `profiles` for a personal Mac.
    static func current(probingEnrollment: Bool) -> ManagedMode {
        let unprobed = read()
        guard unprobed.isManaged || probingEnrollment else { return unprobed }
        return read(enrollment: MDMEnrollmentProbe.current())
    }

    /// Injected reader for tests. Only a forced value counts.
    static func read(enrollment: MDMEnrollment = .unknown,
                     forced: (String, String) -> Any? = { key, domain in
        guard CFPreferencesAppValueIsForced(key as CFString, domain as CFString) else {
            return nil
        }
        return CFPreferencesCopyAppValue(key as CFString, domain as CFString)
    }) -> ManagedMode {
        guard let raw = forced(managedKey, domain) as? NSNumber,
              raw.boolValue else {
            return ManagedMode(isManaged: false, enrollment: enrollment)
        }
        let name = forced(organizationKey, domain) as? String
        let version = (forced(profileVersionKey, domain) as? NSNumber)?.intValue
        let filterManaged = (forced(filterManagedKey, domain) as? NSNumber)?.boolValue ?? false
        return ManagedMode(isManaged: true, organizationName: name,
                           profileVersion: version, filterManaged: filterManaged,
                           enrollment: enrollment)
    }

    /// A sentence for the Device management card. It separates "the profile is
    /// current" from "the profile owns the filter", because an organization can
    /// deploy DNS and browser policy without the managed filter payload. The
    /// missing-version case is not an error in the app: an older profile
    /// simply had no marker, and the remedy is a redeploy.
    public var profileSummary: String? {
        guard isManaged else { return nil }
        guard let version = profileVersion else {
            return String(localized: "This organization profile does not report a version. Ask your administrator to redeploy the current profile.")
        }
        if filterManaged {
            return String(localized: "Protection profile v\(version). The system filter is managed by the profile.")
        }
        return String(localized: "Protection profile v\(version). The profile does not include the managed system filter.")
    }

    /// The sentence the Organization policy layer shows. Callers use it only
    /// when `isManaged` is true; the MDM clause keeps the layer from implying
    /// that a locally installed profile is an organization deployment.
    public var policyDetail: String {
        let name = displayName
        switch enrollment {
        case .enrolled(dep: true, userApproved: true):
            return String(localized: "Managed by \(name). Enrolled via DEP and MDM. Local removal and weakening controls are disabled.")
        case .enrolled(dep: false, userApproved: true):
            return String(localized: "Managed by \(name). Enrolled in MDM. Local removal and weakening controls are disabled.")
        case .enrolled(dep: _, userApproved: false):
            return String(localized: "Managed by \(name). MDM enrollment is present but not user-approved. Local removal and weakening controls are disabled.")
        case .none:
            return String(localized: "Managed by \(name). This Mac is not enrolled in MDM. Local removal and weakening controls are disabled.")
        case .unknown:
            return String(localized: "Managed by \(name). Local removal and weakening controls are disabled.")
        }
    }
}

/// The enrollment states `profiles status -type enrollment` documents.
///
/// The command's own wording — "Enrolled via DEP", "MDM enrollment: Yes",
/// "Yes (User Approved)" — is the contract. Anything else is `.unknown`: an
/// unreadable answer must not turn into a reassuring "not managed".
public enum MDMEnrollment: Equatable, Sendable {
    case unknown
    case none
    /// `dep` is the "Enrolled via DEP" line. `userApproved` is the
    /// "(User Approved)" qualifier on the MDM line.
    case enrolled(dep: Bool, userApproved: Bool)

    public var isEnrolled: Bool {
        if case .enrolled = self { return true }
        return false
    }

    public var isUserApproved: Bool {
        if case .enrolled(_, let approved) = self { return approved }
        return false
    }

    public var isDEP: Bool {
        if case .enrolled(let dep, _) = self { return dep }
        return false
    }

    /// Parse the CLI output. Keys are matched exactly and values
    /// case-insensitively; unknown keys (such as `MDM server`) are ignored.
    static func parse(_ output: String) -> MDMEnrollment {
        var dep: Bool?
        var mdm: String?

        for rawLine in output.split(whereSeparator: \.isNewline) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = String(line[..<colon]).trimmingCharacters(in: .whitespaces)
            let value = String(line[line.index(after: colon)...])
                .trimmingCharacters(in: .whitespaces)
            switch key {
            case "Enrolled via DEP":
                switch value.lowercased() {
                case "yes": dep = true
                case "no":  dep = false
                default:    return .unknown
                }
            case "MDM enrollment":
                mdm = value
            default:
                continue
            }
        }

        guard let mdm else { return .unknown }
        let answer = mdm.lowercased()
        if answer == "no" {
            // DEP without MDM enrollment is an incomplete enrollment, not a
            // Mac that is simply unmanaged.
            return dep == true ? .unknown : .none
        }
        if answer == "yes (user approved)" {
            return .enrolled(dep: dep ?? false, userApproved: true)
        }
        if answer == "yes" {
            // DEP implies an approved MDM enrollment; "DEP yes, approved no"
            // is a contradictory reading, so do not present it as usable.
            return dep == true ? .unknown : .enrolled(dep: false, userApproved: false)
        }
        return .unknown
    }
}

/// Reads and caches the enrollment answer.
///
/// `profiles status` takes about a fifth of a second and does not need root.
/// The result is cached for ten minutes because enrollment changes only when
/// an administrator enrolls or unenrolls the Mac, and `ManagedMode.current()`
/// is read from several view bodies.
enum MDMEnrollmentProbe {
    static let ttl: TimeInterval = 600

    private static let lock = NSLock()
    private static var cached: MDMEnrollment?
    private static var checkedAt: Date?

    static func current(now: Date = Date(),
                        probe: () -> MDMEnrollment = { read() }) -> MDMEnrollment {
        lock.lock()
        if let cached, let checkedAt, now.timeIntervalSince(checkedAt) < ttl {
            lock.unlock()
            return cached
        }
        lock.unlock()

        let fresh = probe()

        lock.lock()
        cached = fresh
        checkedAt = now
        lock.unlock()
        return fresh
    }

    /// The next `current()` asks the Mac again. Used by tests and by a caller
    /// that has just returned from System Settings.
    static func reset() {
        lock.lock()
        cached = nil
        checkedAt = nil
        lock.unlock()
    }

    static func read() -> MDMEnrollment {
        guard let output = run() else { return .unknown }
        return MDMEnrollment.parse(output)
    }

    /// Run the supported command, bounded so a wedged `profiles` cannot hang
    /// the window. A timeout or a non-zero exit is an unknown answer.
    static func run() -> String? {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/profiles")
        process.arguments = ["status", "-type", "enrollment"]
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = FileHandle.nullDevice
        do {
            try process.run()
        } catch {
            return nil
        }

        let deadline = Date().addingTimeInterval(2)
        while process.isRunning, Date() < deadline {
            usleep(20_000)
        }
        if process.isRunning {
            process.terminate()
            process.waitUntilExit()
            return nil
        }
        guard process.terminationStatus == 0 else { return nil }

        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        return String(data: data, encoding: .utf8)
    }
}
