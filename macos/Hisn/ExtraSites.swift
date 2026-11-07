import Foundation

/// Optional distraction sites the person can add to the block list in one
/// click.
///
/// These are not adult-content rules: the published list already carries the
/// protection the app exists for. They are ordinary self-control blocks for
/// recognisable sites that pull time away, offered as a first-run choice and
/// again in Blocking Rules. Every site is named on screen, so a category label
/// never hides what is blocked.
enum ExtraSites {

    /// The order is the order the chips appear in. It is deliberately short and
    /// recognisable: a long catalogue would turn a first-run choice into a
    /// configuration project.
    static let suggestions: [String] = [
        "youtube.com",
        "reddit.com",
        "x.com",
        "instagram.com",
        "facebook.com",
        "tiktok.com",
        "twitch.tv",
        "netflix.com",
        "news.ycombinator.com",
        "amazon.com",
        "pinterest.com",
        "linkedin.com",
    ]

    /// Whether a suggestion is already blocked, and how.
    ///
    /// `exact` is the chip's own entry and can be removed by the chip.
    /// `parent` is a wider rule the person wrote themselves — `ycombinator.com`
    /// covers `news.ycombinator.com`. Removing that parent to clear one chip
    /// would unblock every sibling, so the chip reports it and leaves it alone.
    enum Coverage: Equatable {
        case notBlocked
        case exact
        case parent(String)

        var isBlocked: Bool { self != .notBlocked }
    }

    static func coverage(of domain: String, in blocks: [String]) -> Coverage {
        guard suggestions.contains(domain) else { return .notBlocked }
        if blocks.contains(domain) { return .exact }
        if let parent = blocks.first(where: { domain.hasSuffix("." + $0) }) {
            return .parent(parent)
        }
        return .notBlocked
    }

    static func addedCount(in blocks: [String]) -> Int {
        suggestions.filter { coverage(of: $0, in: blocks).isBlocked }.count
    }

    /// The block list after tapping one chip.
    ///
    /// Adding sorts and de-duplicates, matching the list editor. Removing takes
    /// only the exact chip entry; a parent rule is never removed here.
    static func toggling(_ domain: String, in blocks: [String]) -> [String] {
        guard suggestions.contains(domain) else { return blocks }
        switch coverage(of: domain, in: blocks) {
        case .notBlocked:
            return Array(Set(blocks + [domain])).sorted()
        case .exact:
            return blocks.filter { $0 != domain }
        case .parent:
            return blocks
        }
    }
}
