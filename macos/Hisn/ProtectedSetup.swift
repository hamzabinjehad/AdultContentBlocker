import Foundation

/// Current functional checks, separate from administrator/partner hardening.
/// Never persisted: opening Setup, a hosts fallback, or a previous successful
/// check cannot certify this session. This is configuration/liveness evidence,
/// not an end-to-end test or a guarantee that every adult page is identified.
struct LaptopSetupReadiness: Equatable {
    enum Issue: String, Identifiable {
        case signedBuild, filter, policy, installation, browserProfiles
        case browserConnection, browserGuard, browserWarning, appExceptions
        var id: String { rawValue }

        var detail: String {
            switch self {
            case .signedBuild:
                return String(localized: "This development build cannot activate laptop-wide filtering. Install a properly signed Hisn release, then approve its filter in macOS.")
            case .filter:
                return String(localized: "The system filter must answer with a nonempty verified blocklist. An enabled setting or a hosts file is not enough.")
            case .policy:
                return String(localized: "The filter reports a saved-policy problem. Repair it before relying on these checks.")
            case .installation:
                return String(localized: "The installed app, browser bridge, and login agent need administrator-protected files in their expected locations.")
            case .browserProfiles:
                return String(localized: "Connect Hisn in a supported browser and verify every detected standard profile. Missing or unreadable profiles are not protected evidence.")
            case .browserConnection:
                return String(localized: "No recent browser connection is confirmed. Open your protected browser and reconnect Hisn.")
            case .browserGuard:
                return String(localized: "Enable Keep browser protection on in Blocking Rules to require protection after a commitment ends. This needs your confirmation.")
            case .browserWarning:
                return String(localized: "A browser currently has a protection warning. Restore its extension connection before continuing.")
            case .appExceptions:
                return String(localized: "Trusted app exceptions bypass extension checks. Review them in Blocking Rules; these checks cannot certify their browsing coverage.")
            }
        }
    }

    let issues: [Issue]
    let isChecking: Bool
    var isReady: Bool { !isChecking && issues.isEmpty }

    var headline: String {
        if isChecking { return String(localized: "Checking laptop protection") }
        return isReady ? String(localized: "Laptop protection checks passed")
            : String(localized: "Set up laptop protection")
    }

    var summary: String {
        isReady
            ? String(localized: "The filter, browser connection, detected standard profiles, installation files, and outside-lock browser requirement are confirmed. This is not proof that every route is blocked.")
            : String(localized: "Installing or opening Hisn does not finish protection setup. Complete the missing checks below; your current commitment is unchanged.")
    }

    static func filterIsReady(_ evidence: ProtectionEvidence) -> Bool {
        guard evidence.filterCanRun, !evidence.policyRecoveryRequired,
              evidence.policyPersistenceError == nil,
              case let .on(count) = evidence.filter else { return false }
        return count > 0
    }

    init(protection: ProtectionEvidence, checklist: SetupChecklist?,
         requireOutsideLock: Bool, browserWarning: Bool = false,
         hasAppExceptions: Bool = false) {
        var missing: [Issue] = []
        isChecking = checklist == nil || (protection.filterCanRun && protection.filter == .unknown)
        if !protection.filterCanRun { missing.append(.signedBuild) }
        else if !Self.filterIsReady(protection) { missing.append(.filter) }
        if protection.policyRecoveryRequired || protection.policyPersistenceError != nil {
            missing.append(.policy)
        }
        if let checklist {
            if checklist.steps.first(where: { $0.id == .appFiles })?.state != .done {
                missing.append(.installation)
            }
            if checklist.steps.first(where: { $0.id == .browsers })?.state != .done {
                missing.append(.browserProfiles)
            }
        }
        if !ProtectionStatus(protection).layers.contains(where: { $0.id == "extension" && $0.ok }) {
            missing.append(.browserConnection)
        }
        if !requireOutsideLock { missing.append(.browserGuard) }
        if browserWarning { missing.append(.browserWarning) }
        if hasAppExceptions { missing.append(.appExceptions) }
        issues = missing
    }
}

struct ProtectedSetup: Equatable {
    struct Stage: Identifiable, Equatable {
        enum ID: String, CaseIterable { case installation, browser, recovery, accounts }
        let id: ID
        let steps: [SetupChecklist.Step]
        let confirmed: Bool

        var isComplete: Bool { steps.allSatisfy { $0.state == .done } && confirmed }
        var title: String {
            switch id {
            case .installation: return String(localized: "Install and connect")
            case .browser: return String(localized: "Protect browsing settings")
            case .recovery: return String(localized: "Keep a recovery route")
            case .accounts: return String(localized: "Separate administrator access")
            }
        }
    }

    let stages: [Stage]
    let administratorConfirmed: Bool
    let recoveryConfirmed: Bool

    init(checklist: SetupChecklist, administratorConfirmed: Bool, recoveryConfirmed: Bool) {
        self.administratorConfirmed = administratorConfirmed
        self.recoveryConfirmed = recoveryConfirmed
        let groups: [(Stage.ID, [SetupChecklist.Step.ID], Bool)] = [
            (.installation, [.systemFilter, .appFiles, .browsers], true),
            (.browser, [.extensionManagement, .domains, .profile, .safeSearch, .screenTime], true),
            (.recovery, [.partner], recoveryConfirmed),
            (.accounts, [.accounts], administratorConfirmed),
        ]
        stages = groups.map { id, identifiers, confirmed in
            Stage(id: id, steps: identifiers.compactMap { wanted in
                checklist.steps.first { $0.id == wanted }
            }, confirmed: confirmed)
        }
    }

    var totalCount: Int { stages.flatMap(\.steps).count + 2 }
    var doneCount: Int {
        stages.flatMap(\.steps).filter { $0.state == .done }.count
            + (administratorConfirmed ? 1 : 0) + (recoveryConfirmed ? 1 : 0)
    }
    var deviceChecksPassed: Bool { stages.flatMap(\.steps).allSatisfy { $0.state == .done } }
    var isComplete: Bool { deviceChecksPassed && administratorConfirmed && recoveryConfirmed }
    var nextStage: Stage? { stages.first { !$0.isComplete } }
    var nextStep: SetupChecklist.Step? { stages.flatMap(\.steps).first { $0.state != .done } }
}
