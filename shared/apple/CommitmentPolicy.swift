import Foundation
import Darwin

/// Session-local diagnostics only. Neither wall time nor uptime is a trusted
/// time service across reboot; assessment never changes a saved deadline.
struct CommitmentClock {
    /// Monotonic boot-relative seconds, including time spent asleep.
    static var continuousTime: TimeInterval {
        guard let scale = timebase else { return .nan }
        return Double(mach_continuous_time()) * scale
    }
    private static let timebase: Double? = {
        var info = mach_timebase_info_data_t()
        guard mach_timebase_info(&info) == KERN_SUCCESS, info.denom > 0 else { return nil }
        return Double(info.numer) / Double(info.denom) / 1_000_000_000
    }()
    enum Assessment: Equatable { case consistent, changed, unavailable }
    let wallAnchor: Date
    let uptimeAnchor: TimeInterval
    func elapsedDate(uptime: TimeInterval) -> Date? {
        guard wallAnchor.timeIntervalSinceReferenceDate.isFinite,
              uptimeAnchor.isFinite, uptimeAnchor >= 0,
              uptime.isFinite, uptime >= uptimeAnchor else { return nil }
        let date = wallAnchor.addingTimeInterval(uptime - uptimeAnchor)
        return date.timeIntervalSinceReferenceDate.isFinite ? date : nil
    }
    func assessment(wall: Date, uptime: TimeInterval) -> Assessment {
        guard wall.timeIntervalSinceReferenceDate.isFinite,
              let expected = elapsedDate(uptime: uptime) else { return .unavailable }
        // Allow small synchronization corrections without a warning.
        return abs(wall.timeIntervalSince(expected)) > 120 ? .changed : .consistent
    }
}

/// A voluntary, time-limited commitment, not OS-level deletion prevention.
enum CommitmentPolicy {
    enum RestorationFailure: Error { case corrupt }

    /// A surviving valid mirror can repair accidental damage. Malformed objects
    /// with no valid mirror are not evidence that a commitment was never saved.
    static func restoredSession(from copies: [Any?]) throws -> Session? {
        let present = copies.compactMap { $0 }
        guard !present.isEmpty else { return nil }
        let sessions = present.compactMap { object -> Session? in
            guard let data = object as? Data,
                  let session = try? JSONDecoder().decode(Session.self, from: data),
                  session.isValid() else { return nil }
            return session
        }
        // The latest end wins; at the same end, a copy that records the person's
        // Screen Time choice wins over one written before it was recorded.
        guard let latest = sessions.max(by: { ($0.deadline, $0.screenTimeFilter == true ? 1 : 0)
                                              < ($1.deadline, $1.screenTimeFilter == true ? 1 : 0) }) else {
            throw RestorationFailure.corrupt
        }
        // A confirmed end counts only when every readable copy carries one: a single copy
        // (a damaged or edited mirror) cannot end a commitment. Without it the end is
        // simply checked again on both clocks.
        guard latest.endConfirmedAt == nil || sessions.allSatisfy({ $0.endConfirmed }) else {
            return latest.withoutEndConfirmation()
        }
        return latest
    }

    /// Restore only a previously chosen layer, with readable consent and live OS access.
    static func shouldRestoreProtection(active: Bool, commitmentReadable: Bool,
                                        historyReadable: Bool, previouslyEnabled: Bool,
                                        authorized: Bool) -> Bool {
        active && commitmentReadable && historyReadable && previouslyEnabled && authorized
    }

    /// After a relaunch, put Hisn's Screen Time filter back only when the person had
    /// chosen it for this commitment (recorded with it), the commitment is readable and
    /// still running, iOS still grants access and the filter is missing. A filter the
    /// person turned off before committing was never recorded, so it is never re-enabled,
    /// and access is never requested here.
    static func shouldRestoreScreenTimeFilter(session: Session?, active: Bool, commitmentReadable: Bool,
                                              authorized: Bool, filterPresent: Bool) -> Bool {
        session?.screenTimeFilter == true && active && commitmentReadable && authorized && !filterPresent
    }

    static func validDuration(_ seconds: TimeInterval) -> Bool {
        seconds.isFinite && seconds >= 60 && seconds <= 365 * 86400
    }

    static func extendedDeadline(_ deadline: Date, by seconds: TimeInterval) -> Date? {
        guard validDuration(seconds), deadline.timeIntervalSinceReferenceDate.isFinite else { return nil }
        let extended = deadline.addingTimeInterval(seconds)
        return extended.timeIntervalSinceReferenceDate.isFinite && extended > deadline ? extended : nil
    }

    /// An early self-release cannot bypass the fixed commitment horizon.
    static func releaseDeadline(deadline: Date, requested: Date, matured: Date,
                                fixedUntil: Date?) -> Date {
        min(deadline, max(requested, matured, fixedUntil ?? .distantPast))
    }

    struct Session: Codable, Equatable {
        let startedAt: Date
        let deadline: Date
        /// Whether the person had Hisn's Screen Time filter on when they committed (or
        /// turned it on during the commitment). Recorded with the commitment as their
        /// consent to have it restored; nil for sessions saved before it was recorded.
        let screenTimeFilter: Bool?
        /// Apple's time (a lower bound) when the end had passed on both clocks, saved with the
        /// commitment so it stays ended: a reboot that loses the same-boot reading cannot
        /// make it run again, nor bring back a filter the person turned off after the end.
        /// Never before the deadline; nil until the end is confirmed.
        let endConfirmedAt: Date?
        init(startedAt: Date, deadline: Date, screenTimeFilter: Bool? = nil, endConfirmedAt: Date? = nil) {
            self.startedAt = startedAt; self.deadline = deadline; self.screenTimeFilter = screenTimeFilter
            self.endConfirmedAt = endConfirmedAt
        }
        func isActive(at now: Date) -> Bool { now < deadline }
        func isValid() -> Bool {
            CommitmentPolicy.validDuration(deadline.timeIntervalSince(startedAt))
                && (endConfirmedAt == nil || endConfirmed)
        }
        /// The end was confirmed on both clocks at or after the deadline.
        var endConfirmed: Bool {
            guard let endConfirmedAt, endConfirmedAt.timeIntervalSinceReferenceDate.isFinite else { return false }
            return endConfirmedAt >= deadline
        }
        func extending(by seconds: TimeInterval) -> Session? {
            guard isValid(), let next = CommitmentPolicy.extendedDeadline(deadline, by: seconds) else { return nil }
            let session = Session(startedAt: startedAt, deadline: next, screenTimeFilter: screenTimeFilter)
            return session.isValid() ? session : nil
        }
        /// Recording the choice only ever adds it; the commitment's dates do not change.
        func choosingScreenTimeFilter() -> Session {
            Session(startedAt: startedAt, deadline: deadline, screenTimeFilter: true, endConfirmedAt: endConfirmedAt)
        }
        /// Records that the end has passed on both clocks; nil if `trustedNow` is before it.
        func confirmingEnd(at trustedNow: Date) -> Session? {
            let session = Session(startedAt: startedAt, deadline: deadline, screenTimeFilter: screenTimeFilter,
                                  endConfirmedAt: trustedNow)
            return session.endConfirmed && session.isValid() ? session : nil
        }
        func withoutEndConfirmation() -> Session {
            Session(startedAt: startedAt, deadline: deadline, screenTimeFilter: screenTimeFilter)
        }
    }
}
