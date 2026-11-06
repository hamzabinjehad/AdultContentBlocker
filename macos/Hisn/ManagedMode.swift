import Foundation

/// Whether this Mac is managed by an organization.
///
/// The evidence is a **forced** preference delivered by a configuration
/// profile — `HisnManaged` in the app's own preference domain. A value the
/// person can write with `defaults write` is not management; it must come from
/// `CFPreferences`'s forced layer, which is what a profile or MDM supplies.
///
/// Managed mode changes what the app offers, not what the OS enforces. It hides
/// local removal and weakening controls; it cannot stop a local administrator
/// from removing the profile or erasing the Mac. The deployment note
/// `docs/ORGANIZATION_MODE.md` states that limit.
public struct ManagedMode: Equatable, Sendable {
    public let isManaged: Bool
    public let organizationName: String?

    public init(isManaged: Bool, organizationName: String? = nil) {
        self.isManaged = isManaged
        self.organizationName = organizationName
    }

    public static let none = ManagedMode(isManaged: false)

    /// The organization name when the profile supplied one, otherwise a
    /// neutral phrase that reads correctly in both languages.
    public var displayName: String {
        let trimmed = organizationName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? String(localized: "your organization") : trimmed
    }

    public static let domain = "app.hisn.Hisn"
    public static let managedKey = "HisnManaged"
    public static let organizationKey = "OrganizationName"

    public static func current() -> ManagedMode {
        read()
    }

    /// Injected reader for tests. Only a forced value counts.
    static func read(forced: (String, String) -> Any? = { key, domain in
        guard CFPreferencesAppValueIsForced(key as CFString, domain as CFString) else {
            return nil
        }
        return CFPreferencesCopyAppValue(key as CFString, domain as CFString)
    }) -> ManagedMode {
        guard let raw = forced(managedKey, domain) as? NSNumber,
              raw.boolValue else { return .none }
        let name = forced(organizationKey, domain) as? String
        return ManagedMode(isManaged: true, organizationName: name)
    }
}
