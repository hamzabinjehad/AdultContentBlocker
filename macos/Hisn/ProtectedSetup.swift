import Foundation

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
