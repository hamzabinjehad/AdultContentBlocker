import SwiftUI

/// The sprout under glass, as numbers: which stage, how far to the next.
struct SproutProgress: Equatable {
    var stage: Int          // 1...7
    var givenBack: Double   // seconds
    var fraction: Double    // progress inside the stage, 0...1
    var toNext: Double?     // seconds to the next stage; nil at the last

    init(givenBack: Double) {
        self.givenBack = max(0, givenBack)
        stage = TimeGivenBack.stage(forSeconds: self.givenBack)
        let starts = TimeGivenBack.stageStarts
        if stage < starts.count {
            let from = starts[stage - 1], to = starts[stage]
            fraction = min(1, max(0, (self.givenBack - from) / (to - from)))
            toNext = to - self.givenBack
        } else {
            fraction = 1
            toNext = nil
        }
    }

    static func name(_ stage: Int) -> String {
        switch stage {
        case 1: return String(localized: "Seed")
        case 2: return String(localized: "Sprout")
        case 3: return String(localized: "Two leaves")
        case 4: return String(localized: "Young plant")
        case 5: return String(localized: "Bud")
        case 6: return String(localized: "Flower")
        default: return String(localized: "Tree")
        }
    }

    /// The painted glass for a stage. Three paintings cover seven stages until
    /// the in-between ones are painted.
    static func image(_ stage: Int) -> String {
        switch stage {
        case 1: return "UnrotGlassSeed"
        case 2...4: return "UnrotGlassSprout"
        default: return "UnrotGlassTree"
        }
    }

    /// "1d 4h 12m" split for the big number: value and unit pairs.
    static func parts(_ seconds: Double) -> [(String, String)] {
        let minutes = Int(seconds / 60)
        let d = minutes / 1440, h = (minutes % 1440) / 60, m = minutes % 60
        var out: [(String, String)] = []
        if d > 0 { out.append(("\(d)", String(localized: "d"))) }
        if d > 0 || h > 0 { out.append(("\(h)", String(localized: "h"))) }
        out.append(("\(m)", String(localized: "m")))
        return out
    }
}
