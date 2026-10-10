import Foundation

/// Shared, platform-neutral configuration. No macOS enforcement assumptions.
enum MobileProtectionPolicy {
    static let appIdentifier = "app.hisn.mobile"
    static let blockerIdentifiers = (1...4).map { "\(appIdentifier).blocker\($0)" }
    static let dnsURL = URL(string: "https://family.cloudflare-dns.com/dns-query")!
    static let dnsServers = ["1.1.1.3", "1.0.0.3", "2606:4700:4700::1113", "2606:4700:4700::1003"]

    enum DNSState: Equatable {
        case unknown, absent, saved, enabled, differentConfiguration, unavailable
    }

    /// A current configuration assessment, never a stored “setup complete” flag
    /// or evidence that every app, page or network route was filtered.
    enum SetupState: String {
        case unchecked = "setup.status.unchecked"
        case checking = "setup.status.checking"
        case needsSetup = "setup.status.incomplete"
        case configured = "setup.status.configured"
    }
    enum SetupIssue: String, Equatable {
        case list = "setup.next.list"
        case safari = "setup.next.safari"
        case screenTime = "setup.next.screentime"
        case dns = "setup.next.dns"
        case dnsSaved = "setup.next.dnssaved"
        case dnsDifferent = "setup.next.dnsdifferent"
        case storage = "setup.next.storage"
    }
    struct SetupAssessment: Equatable {
        let state: SetupState
        let issues: [SetupIssue]
    }
    struct SafariListPart: Equatable {
        let count: Int
        let version: Int
        let integrityMatches: Bool
    }

    static func safariListReady(count: Int, version: Int, parts: [SafariListPart]) -> Bool {
        guard (1...(40_000 * blockerIdentifiers.count)).contains(count), version > 0,
              parts.count == blockerIdentifiers.count else { return false }
        guard parts.allSatisfy({ (1...40_000).contains($0.count) && $0.version == version && $0.integrityMatches })
            else { return false }
        return parts.reduce(0) { $0 + $1.count } == count
    }

    static func setupAssessment(checked: Bool, busy: Bool, listVerified: Bool,
                                listCount: Int, listVersion: Int,
                                safari: [Bool?], lastReload: [Bool?],
                                screenTime: Bool, dns: DNSState,
                                storageHealthy: Bool) -> SetupAssessment {
        if busy { return .init(state: .checking, issues: []) }
        guard checked else { return .init(state: .unchecked, issues: []) }
        var issues: [SetupIssue] = []
        if !listVerified || !(1...(40_000 * blockerIdentifiers.count)).contains(listCount) || listVersion <= 0 {
            issues.append(.list)
        }
        if !safariConfigurationReady(enabled: safari, lastReload: lastReload) { issues.append(.safari) }
        if !screenTime { issues.append(.screenTime) }
        switch dns {
        case .enabled: break
        case .saved: issues.append(.dnsSaved)
        case .differentConfiguration: issues.append(.dnsDifferent)
        default: issues.append(.dns)
        }
        if !storageHealthy { issues.append(.storage) }
        return .init(state: issues.isEmpty ? .configured : .needsSetup, issues: issues)
    }

    enum FamilyAuthorization { case notDetermined, denied, approved, unavailable }
    enum RemovalState: String {
        case notRequested = "removal.notRequested"
        case denied = "removal.denied"
        case approvedScopeUnknown = "removal.scopeUnknown"
        case guardianRequestAccepted = "removal.accepted"
        case unavailable = "removal.unavailable"
    }

    /// Approval alone does not reveal child vs. individual authorization.
    /// Evidence is session-local and is discarded as soon as approval is lost.
    struct RemovalEvidence {
        private(set) var childRequestAccepted = false
        mutating func observe(_ status: FamilyAuthorization, childRequestSucceeded: Bool = false) -> RemovalState {
            guard case .approved = status else {
                childRequestAccepted = false
                switch status {
                case .denied: return .denied
                case .unavailable: return .unavailable
                default: return .notRequested
                }
            }
            if childRequestSucceeded { childRequestAccepted = true }
            return childRequestAccepted ? .guardianRequestAccepted : .approvedScopeUnknown
        }
    }

    enum SafariPartState: Equatable {
        case unknown, disabled, enabled, reloadFailed
    }

    static func safariPartState(enabled: Bool?, lastReload: Bool?) -> SafariPartState {
        if lastReload == false { return .reloadFailed }
        guard let enabled else { return .unknown }
        return enabled ? .enabled : .disabled
    }

    static func safariConfigurationReady(enabled: [Bool?], lastReload: [Bool?]) -> Bool {
        allSafariLayersEnabled(enabled) && lastReload.count == blockerIdentifiers.count &&
            !lastReload.contains(false)
    }

    static func dnsState(hasConfiguration: Bool, isEnabled: Bool,
                         serverURL: URL?, servers: [String], matchDomains: [String]? = nil) -> DNSState {
        guard hasConfiguration else { return .absent }
        guard serverURL == dnsURL, Set(servers) == Set(dnsServers) else { return .differentConfiguration }
        // A split-DNS configuration must not be reported as device-wide protection.
        // nil/empty retains the system's unscoped configuration; an explicit
        // empty-string domain selects the default resolver for all domains.
        if let matchDomains, !matchDomains.isEmpty, !matchDomains.contains("") {
            return .differentConfiguration
        }
        return isEnabled ? .enabled : .saved
    }

    static func allSafariLayersEnabled(_ enabled: [Bool?]) -> Bool {
        enabled.count == blockerIdentifiers.count && enabled.allSatisfy { $0 == true }
    }
}
