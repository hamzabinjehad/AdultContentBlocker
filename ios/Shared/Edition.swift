import Foundation

/// Which app this build is. Hisn (the Islamic edition) and Unrot (the general
/// brainrot blocker) share every line of protection code; only names, the app
/// identifier and the App Group differ, so the two can sit on one iPhone without
/// reading each other's rules. `UNROT` is set by `generate_xcodeproj.py --general`
/// on every target of UnrotMobile.xcodeproj.
enum Edition {
    #if UNROT
    static let isUnrot = true
    static let appIdentifier = "app.unrot.mobile"
    static let appGroup = "group.app.unrot.mobile"
    static let displayName = "Unrot"
    #else
    static let isUnrot = false
    static let appIdentifier = "app.hisn.mobile"
    static let appGroup = "group.app.hisn.mobile"
    static let displayName = "Hisn"
    #endif

    /// Hisn's own words with this edition's name: the shield, notifications and
    /// shared screens were written for Hisn. A no-op in Hisn.
    static func brand(_ text: String) -> String {
        #if UNROT
        return text.replacingOccurrences(of: "Hisn", with: "Unrot")
            .replacingOccurrences(of: "حصن", with: "أنروت")
        #else
        return text
        #endif
    }
}
