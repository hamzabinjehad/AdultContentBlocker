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
        case screenTimeAccess = "setup.next.screentimeAccess"
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
                                storageHealthy: Bool, screenTimeAccessRemoved: Bool = false) -> SetupAssessment {
        if busy { return .init(state: .checking, issues: []) }
        guard checked else { return .init(state: .unchecked, issues: []) }
        var issues: [SetupIssue] = []
        if !listVerified || !(1...(40_000 * blockerIdentifiers.count)).contains(listCount) || listVersion <= 0 {
            issues.append(.list)
        }
        if !safariConfigurationReady(enabled: safari, lastReload: lastReload) { issues.append(.safari) }
        if !screenTime { issues.append(screenTimeAccessRemoved ? .screenTimeAccess : .screenTime) }
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

    /// What the Screen Time layer can honestly report. Access turned off in Settings is not
    /// "never set up": iOS then drops Hisn's filter, sites and app shields, and only the
    /// person can turn access back on.
    enum ScreenTimeState: String {
        case unavailable = "screentime.state.unavailable"
        case notRequested = "screentime.state.notRequested"
        case declined = "screentime.state.declined"
        case accessRemoved = "screentime.state.accessRemoved"
        case filterOff = "screentime.state.filterOff"
        case configured = "screentime.state.configured"
    }

    /// `previouslyConfigured` is Hisn's readiness history: the filter was confirmed before.
    static func screenTimeState(authorization: FamilyAuthorization, filterConfigured: Bool,
                                previouslyConfigured: Bool) -> ScreenTimeState {
        switch authorization {
        case .unavailable: return .unavailable
        case .approved: return filterConfigured ? .configured : .filterOff
        case .denied: return previouslyConfigured ? .accessRemoved : .declined
        case .notDetermined: return previouslyConfigured ? .accessRemoved : .notRequested
        }
    }
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

    // MARK: - Removal protection (lock ladder)

    /// How hard it is for the person to remove Hisn on their own, weakest first.
    /// Each raw value is the level's text key.
    enum LockLevel: String, CaseIterable, Comparable {
        case selfOnly = "lock.level.selfOnly"
        case passcodeVault = "lock.level.passcodeVault"
        case familyGuardian = "lock.level.familyGuardian"
        case supervised = "lock.level.supervised"

        var rank: Int { Self.allCases.firstIndex(of: self) ?? 0 }
        static func < (lhs: Self, rhs: Self) -> Bool { lhs.rank < rhs.rank }
    }

    /// Verified: an Apple API confirmed it during this session. Attested: the person said so,
    /// and Hisn cannot check it. Self only needs neither: it is where every install starts.
    enum LockEvidence: String {
        case verified = "lock.evidence.verified"
        case attested = "lock.evidence.attested"
    }

    /// The person's own statement about a lock Hisn cannot read: the Screen Time passcode,
    /// "Deleting Apps: Don't Allow", a family organizer or supervision. Never upgraded to verified.
    struct LockAttestation: Codable, Equatable {
        enum Claim: String, Codable, CaseIterable {
            case passcodeVault, familyGuardian, supervised
            var level: LockLevel {
                switch self {
                case .passcodeVault: return .passcodeVault
                case .familyGuardian: return .familyGuardian
                case .supervised: return .supervised
                }
            }
        }
        var claim: Claim
        /// The person tried to delete an app and could not. Optional, and also only their word.
        var selfTested: Bool
        /// The vault a passcode-vault claim is about; a new vault needs a new confirmation.
        var vaultCreatedAt: Date?
        var confirmedAt: Date

        /// Empty or damaged data reads as no confirmation, so it can only ever lower the level.
        init?(storage data: Data) {
            guard !data.isEmpty, let decoded = try? JSONDecoder().decode(Self.self, from: data),
                  decoded.isWellFormed else { return nil }
            self = decoded
        }

        init(claim: Claim, selfTested: Bool = false, vaultCreatedAt: Date? = nil, confirmedAt: Date) {
            self.claim = claim
            self.selfTested = selfTested
            self.vaultCreatedAt = claim == .passcodeVault ? vaultCreatedAt : nil
            self.confirmedAt = confirmedAt
        }

        var isWellFormed: Bool {
            confirmedAt.timeIntervalSinceReferenceDate.isFinite
                && (claim == .passcodeVault) == (vaultCreatedAt != nil)
                && vaultCreatedAt?.timeIntervalSinceReferenceDate.isFinite != false
        }

        var storage: Data { (try? JSONEncoder().encode(self)) ?? Data() }
    }

    /// Where the person's confirmation is kept on this device.
    static let lockAttestationKey = "hisn.mobile.lockAttestation"

    /// What Hisn knows about its passcode vault, without its passcode.
    struct VaultFacts: Equatable {
        let createdAt: Date
        let revealAt: Date
        let revealed: Bool

        /// The passcode is still kept from the person: not shown, and its date not reached.
        func holdsPasscode(at now: Date) -> Bool { !revealed && now < revealAt }
    }

    struct RemovalProtection: Equatable {
        let level: LockLevel
        /// nil only for self only.
        let evidence: LockEvidence?
        /// The highest level an API confirmed in this session.
        let verifiedLevel: LockLevel
        let selfTested: Bool
        /// A saved confirmation that no longer counts: its vault was shown, replaced or reached its date.
        let staleAttestation: Bool
    }

    /// A passcode-vault confirmation counts only for the vault it was given for, and only while
    /// that vault still keeps the passcode. Other claims stand until the person withdraws them.
    static func attestationApplies(_ attestation: LockAttestation, vault: VaultFacts?, now: Date) -> Bool {
        guard attestation.isWellFormed else { return false }
        guard attestation.claim == .passcodeVault else { return true }
        guard let vault, let confirmed = attestation.vaultCreatedAt, vault.holdsPasscode(at: now) else { return false }
        return abs(confirmed.timeIntervalSince(vault.createdAt)) < 1
    }

    /// The level the readiness row shows. Hisn cannot read the Screen Time passcode, the
    /// deletion setting or supervision, so those are attested at most. Child authorization
    /// accepted in this session is the only verified level; `RemovalEvidence` drops it when
    /// approval is lost or the app relaunches. A new install with nothing confirmed is self only.
    static func removalProtection(removal: RemovalState, attestation: LockAttestation?,
                                  vault: VaultFacts?, now: Date) -> RemovalProtection {
        let verified: LockLevel = removal == .guardianRequestAccepted ? .familyGuardian : .selfOnly
        let applies = attestation.map { attestationApplies($0, vault: vault, now: now) } ?? false
        let stale = attestation != nil && !applies
        let attested = applies ? attestation?.claim.level ?? .selfOnly : .selfOnly
        let selfTested = applies && attestation?.selfTested == true
        if attested > verified {
            return .init(level: attested, evidence: .attested, verifiedLevel: verified,
                         selfTested: selfTested, staleAttestation: stale)
        }
        if verified > .selfOnly {
            return .init(level: verified, evidence: .verified, verifiedLevel: verified,
                         selfTested: selfTested && attested == verified, staleAttestation: stale)
        }
        return .init(level: .selfOnly, evidence: nil, verifiedLevel: .selfOnly,
                     selfTested: false, staleAttestation: stale)
    }
}
