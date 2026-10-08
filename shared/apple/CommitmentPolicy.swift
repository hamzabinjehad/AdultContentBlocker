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
        guard let latest = sessions.max(by: { $0.deadline < $1.deadline }) else {
            throw RestorationFailure.corrupt
        }
        return latest
    }

    /// Restore only a previously chosen layer, with readable consent and live OS access.
    static func shouldRestoreProtection(active: Bool, commitmentReadable: Bool,
                                        historyReadable: Bool, previouslyEnabled: Bool,
                                        authorized: Bool) -> Bool {
        active && commitmentReadable && historyReadable && previouslyEnabled && authorized
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
        func isActive(at now: Date) -> Bool { now < deadline }
        func isValid() -> Bool { CommitmentPolicy.validDuration(deadline.timeIntervalSince(startedAt)) }
        func extending(by seconds: TimeInterval) -> Session? {
            guard isValid(), let next = CommitmentPolicy.extendedDeadline(deadline, by: seconds) else { return nil }
            let session = Session(startedAt: startedAt, deadline: next)
            return session.isValid() ? session : nil
        }
    }
}
