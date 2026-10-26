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
        /// The install-and-device marker the confirmation was given under. The confirmation sits in
        /// UserDefaults, which backups carry; the marker is kept where they do not, so a confirmation
        /// restored onto another iPhone, or onto this one after it was erased, stops counting.
        /// Saved before markers existed: nil, which never counts.
        var deviceMarker: String?

        /// Empty or damaged data reads as no confirmation, so it can only ever lower the level.
        init?(storage data: Data) {
            guard !data.isEmpty, let decoded = try? JSONDecoder().decode(Self.self, from: data),
                  decoded.isWellFormed else { return nil }
            self = decoded
        }

        init(claim: Claim, selfTested: Bool = false, vaultCreatedAt: Date? = nil, confirmedAt: Date,
             deviceMarker: String? = nil) {
            self.claim = claim
            self.selfTested = selfTested
            self.vaultCreatedAt = claim == .passcodeVault ? vaultCreatedAt : nil
            self.confirmedAt = confirmedAt
            self.deviceMarker = deviceMarker
        }

        var isWellFormed: Bool {
            confirmedAt.timeIntervalSinceReferenceDate.isFinite
                && (claim == .passcodeVault) == (vaultCreatedAt != nil)
                && vaultCreatedAt?.timeIntervalSinceReferenceDate.isFinite != false
                && deviceMarker?.isEmpty != true
        }

        /// Given on this install on this device: it carries the marker kept here now.
        func isBound(to marker: String?) -> Bool {
            guard let marker, !marker.isEmpty else { return false }
            return deviceMarker == marker
        }

        var storage: Data { (try? JSONEncoder().encode(self)) ?? Data() }
    }

    /// Where the person's confirmation is kept on this device. Backups carry it, so it counts only
    /// alongside the device marker (`agreedMarker`), which they do not.
    static let lockAttestationKey = "hisn.mobile.lockAttestation"

    /// The device marker every store agrees on: nil when any store has none, or they differ.
    /// A marker restored into one store but not the other is no marker, never a match.
    static func agreedMarker(_ stored: [String?]) -> String? {
        guard let first = stored.first, let marker = first, !marker.isEmpty,
              stored.allSatisfy({ $0 == marker }) else { return nil }
        return marker
    }

    /// What Hisn knows about its passcode vault, without its passcode.
    struct VaultFacts: Equatable {
        let createdAt: Date
        let revealAt: Date
        let revealed: Bool
        /// When the setup window closes on its own; nil once the person sealed it. Until then the
        /// passcode is on screen, so it is not yet kept from anyone.
        let hidesAt: Date?

        init(createdAt: Date, revealAt: Date, revealed: Bool, hidesAt: Date?) {
            self.createdAt = createdAt
            self.revealAt = revealAt
            self.revealed = revealed
            self.hidesAt = hidesAt
        }

        /// The vault's own rule: a clock set back before the vault was made never reopens the window.
        func inSetupWindow(at now: Date) -> Bool {
            guard !revealed, let hidesAt else { return false }
            return now >= createdAt && now < hidesAt
        }

        /// The passcode is still kept from the person: past its setup window, not shown, and its
        /// date not reached.
        func holdsPasscode(at now: Date) -> Bool { !revealed && !inSetupWindow(at: now) && now < revealAt }
    }

    /// Why a saved confirmation no longer counts. Each raw value is its text key.
    enum StaleReason: String {
        /// The vault it was about was shown, replaced or reached its date.
        case vaultChanged = "lock.stale"
        /// It came back from a backup or another iPhone, or this device's marker cannot be read.
        case otherDevice = "lock.staleDevice"
    }

    struct RemovalProtection: Equatable {
        let level: LockLevel
        /// nil only for self only.
        let evidence: LockEvidence?
        /// The highest level an API confirmed in this session.
        let verifiedLevel: LockLevel
        let selfTested: Bool
        /// Why a saved confirmation no longer counts; nil when there is none or it counts.
        let stale: StaleReason?

        var staleAttestation: Bool { stale != nil }
    }

    /// A confirmation counts only on the install and device it was given on. A passcode-vault
    /// confirmation also counts only for the vault it was given for, and only while that vault
    /// keeps the passcode hidden. Other claims stand until the person withdraws them.
    static func attestationApplies(_ attestation: LockAttestation, vault: VaultFacts?, now: Date,
                                   deviceMarker: String?) -> Bool {
        guard attestation.isWellFormed, attestation.isBound(to: deviceMarker) else { return false }
        guard attestation.claim == .passcodeVault else { return true }
        guard let vault, let confirmed = attestation.vaultCreatedAt, vault.holdsPasscode(at: now) else { return false }
        return abs(confirmed.timeIntervalSince(vault.createdAt)) < 1
    }

    static func staleReason(_ attestation: LockAttestation?, vault: VaultFacts?, now: Date,
                            deviceMarker: String?) -> StaleReason? {
        guard let attestation,
              !attestationApplies(attestation, vault: vault, now: now, deviceMarker: deviceMarker) else { return nil }
        return attestation.isBound(to: deviceMarker) ? .vaultChanged : .otherDevice
    }

    /// The level the readiness row shows. Hisn cannot read the Screen Time passcode, the
    /// deletion setting or supervision, so those are attested at most. Child authorization
    /// accepted in this session is the only verified level; `RemovalEvidence` drops it when
    /// approval is lost or the app relaunches. A new install with nothing confirmed is self only.
    static func removalProtection(removal: RemovalState, attestation: LockAttestation?,
                                  vault: VaultFacts?, now: Date, deviceMarker: String?) -> RemovalProtection {
        let verified: LockLevel = removal == .guardianRequestAccepted ? .familyGuardian : .selfOnly
        let applies = attestation.map {
            attestationApplies($0, vault: vault, now: now, deviceMarker: deviceMarker)
        } ?? false
        let stale = staleReason(attestation, vault: vault, now: now, deviceMarker: deviceMarker)
        let attested = applies ? attestation?.claim.level ?? .selfOnly : .selfOnly
        let selfTested = applies && attestation?.selfTested == true
        if attested > verified {
            return .init(level: attested, evidence: .attested, verifiedLevel: verified,
                         selfTested: selfTested, stale: stale)
        }
        if verified > .selfOnly {
            return .init(level: verified, evidence: .verified, verifiedLevel: verified,
                         selfTested: selfTested && attested == verified, stale: stale)
        }
        return .init(level: .selfOnly, evidence: nil, verifiedLevel: .selfOnly,
                     selfTested: false, stale: stale)
    }
}
