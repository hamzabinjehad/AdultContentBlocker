import Foundation

/// The adult-TLD rail: a name under a sponsored adult TLD is blocked even
/// before any list has heard of it.
///
/// `blocklist/build.py` already treats these TLDs as a *selection signal* for
/// the bundled core tier. That is not enforcement: a domain registered after
/// the last build is not on any list, so the socket filter would allow it. The
/// four TLDs below are adult-sponsored at the registry (ICM Registry operates
/// `.xxx`, `.porn`, `.sex` and `.adult`), which makes the final label itself a
/// reliable signal rather than a guess about a name.
///
/// `.cam` is deliberately absent. It is a generic TLD with ordinary camera and
/// webcam uses, and blocking it whole would take out sites that are not adult
/// content. The keyword layer still catches a `.cam` host whose own labels name
/// adult material.
///
/// This is a policy constant, not a downloaded artifact, so it works when the
/// list is missing and cannot be rolled back by serving an older generation.
/// `SiteCheckRules` names it separately from the published list, and the
/// browser extension carries the same set in `rules/adult_tlds.json`.
enum AdultTldGuard {
    /// Keep in step with `extension/lib/policy.js` (`ADULT_TLDS`) and
    /// `android/core/.../AdultTldPolicy.java`.
    static let tlds: Set<String> = ["adult", "porn", "sex", "xxx"]

    /// True when the host's final label is one of `tlds`.
    ///
    /// The host is already canonicalised by `BlocklistStore.blocked` (lowercase,
    /// trailing dot removed); trimming here keeps the call safe from the site
    /// checker and from tests that pass raw input. A bare label is accepted,
    /// because `xxx/` is a valid URL host even if it is unusual.
    static func matches(host: String) -> Bool {
        let cleaned = host.lowercased()
            .trimmingCharacters(in: CharacterSet(charactersIn: ". \t\n\r"))
        guard !cleaned.isEmpty else { return false }
        guard let dot = cleaned.lastIndex(of: ".") else { return tlds.contains(cleaned) }
        return tlds.contains(String(cleaned[cleaned.index(after: dot)...]))
    }
}
