import Foundation
import Combine
import NetworkExtension

/// The lock clock, and the only place a lock may be started, extended or ended.
///
/// The rule this type enforces, everywhere, without exception: **a lock can be
/// made longer or stricter at any time, and shorter or looser only when it has
/// genuinely expired or an accountability partner has approved it.**
///
/// On escape hatches
/// -----------------
/// There *is* one, and it is deliberate. A tool with literally no exit is not
/// safer — it is a tool people avoid installing, uninstall pre-emptively, or
/// route around by buying a second device. What works is making the exit slow
/// and social rather than instant and private:
///
///   * a delayed release the user can request but not accelerate, and
///   * a partner approval that is immediate but requires telling someone.
///
/// Both preserve the property that matters at 2am: the decision cannot be
/// reversed by the person alone, in the moment, in silence.
@MainActor
public final class LockManager: ObservableObject {

    public static let shared = LockManager()

    @Published public private(set) var state: LockStore.LockState = .unlocked
    @Published public private(set) var now: Date = Date()

    /// How long a self-requested early release takes to arrive. Long enough
    /// that the urge has passed; short enough to handle a genuine emergency.
    ///
    /// Defined in `LockStore` because `effectiveDeadline` — which every
    /// enforcement path consults, including the network extension, where no
    /// `LockManager` exists — has to apply it. Re-exported here so callers on
    /// this side read it from the type they already hold.
    public static var selfReleaseDelay: TimeInterval { LockStore.selfReleaseDelay }

    public enum Duration: String, CaseIterable, Identifiable {
        case week = "7 days"
        case month = "30 days"
        case quarter = "90 days"
        case year = "1 year"
        case custom = "Custom"

        public var id: String { rawValue }

        /// The preset length. `nil` for `.custom`, which takes its length from
        /// what the person types instead.
        public var seconds: TimeInterval? {
            switch self {
            case .week:    return 7 * 86400
            case .month:   return 30 * 86400
            case .quarter: return 90 * 86400
            case .year:    return 365 * 86400
            case .custom:  return nil
            }
        }
    }

    /// Free tier gets a week; longer commitments are paid.
    ///
    /// Applies to a typed length exactly as it does to a preset — otherwise
    /// "Custom: 8 days" is simply a way around the paywall.
    public nonisolated static func requiresSubscription(seconds: TimeInterval) -> Bool {
        seconds > 7 * 86400
    }

    /// The shortest and longest lock that may be started.
    ///
    /// The floor stops a blank or zero field starting a lock that is already
    /// over. The ceiling is the one that matters: a lock cannot be shortened,
    /// so without it a slipped keystroke — 3650 where 365 was meant — is a
    /// decade-long commitment entered by accident. A year matches the longest
    /// preset, and past it the answer is to extend, which is always allowed.
    public nonisolated static let minimumLock: TimeInterval = 60
    public nonisolated static let maximumLock: TimeInterval = 365 * 86400

    /// A length we are willing to act on, or `nil`.
    ///
    /// Returns nil rather than clamping. Rounding 3650 days down to the cap
    /// would start a year-long lock nobody asked for, and the one thing this
    /// type must never do is commit someone to a deadline they did not choose.
    /// The `isFinite` check is not decoration: `Double("inf")` parses.
    public nonisolated static func validated(seconds: TimeInterval) -> TimeInterval? {
        guard seconds.isFinite,
              seconds >= minimumLock,
              seconds <= maximumLock else { return nil }
        return seconds
    }

    /// "7 days", "36 hours" — for telling someone what they are about to start.
    public nonisolated static func describe(_ seconds: TimeInterval) -> String {
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .full
        formatter.allowedUnits = [.year, .month, .day, .hour, .minute]
        formatter.maximumUnitCount = 2
        return formatter.string(from: seconds) ?? "\(Int(seconds)) seconds"
    }

    public enum LockError: LocalizedError {
        case alreadyLocked(until: Date)
        case wouldShorten
        case notLocked
        case releaseNotDue(at: Date)
        case filterUnavailable(String)
        case durationOutOfRange

        public var errorDescription: String? {
            switch self {
            case .durationOutOfRange:
                return "A lock has to be between "
                    + "\(LockManager.describe(LockManager.minimumLock)) and "
                    + "\(LockManager.describe(LockManager.maximumLock))."
            case let .alreadyLocked(until):
                return "A lock is already running until \(until.formatted())."
            case .wouldShorten:
                return "This would shorten an active lock, which is not allowed."
            case .notLocked:
                return "No lock is currently running."
            case let .releaseNotDue(at):
                return "Your release request completes at \(at.formatted())."
            case let .filterUnavailable(reason):
                return "The content filter could not start: \(reason)"
            }
        }
    }

    private var ticker: AnyCancellable?

    private init() {
        state = LockStore.read()
        ticker = Timer.publish(every: 1, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in self?.tick() }
    }

    private func tick() {
        now = LockStore.trustedNow()
        let fresh = LockStore.read()
        if fresh != state { state = fresh }
        // The EFFECTIVE deadline, so a matured self-release actually ends the
        // lock. Comparing against `state.deadline` here is what made the whole
        // release mechanism inert: the date was recorded and then never
        // consulted by anything.
        if now >= LockStore.effectiveDeadline(state), state.mode != "off" {
            Task { await expire() }
        }
    }

    // MARK: - Derived

    public var isLocked: Bool { now < state.deadline }

    public var remaining: TimeInterval { max(0, state.deadline.timeIntervalSince(now)) }

    public var remainingDescription: String {
        guard isLocked else { return "Not locked" }
        let t = Int(remaining)
        let d = t / 86400, h = (t % 86400) / 3600, m = (t % 3600) / 60
        if d > 0 { return "\(d)d \(h)h" }
        if h > 0 { return "\(h)h \(m)m" }
        return "\(m)m \(t % 60)s"
    }

    // MARK: - Starting

    /// Begin a lock. Extending an existing lock is fine; shortening is not.
    ///
    /// Takes a length rather than a `Duration` because the length may have been
    /// typed. The range is re-checked here and not only in the view: this is
    /// the last point before a deadline becomes unshortenable, so it is the
    /// wrong place to trust a caller.
    public func start(seconds: TimeInterval, strict: Bool) async throws {
        guard let seconds = Self.validated(seconds: seconds) else {
            throw LockError.durationOutOfRange
        }
        let deadline = LockStore.trustedNow().addingTimeInterval(seconds)

        if isLocked && deadline < state.deadline {
            throw LockError.wouldShorten
        }

        let newState = LockStore.LockState(
            deadline: deadline,
            mode: strict ? "strict" : "blocklist",
            startedAt: LockStore.trustedNow())

        guard LockStore.write(newState) else { throw LockError.wouldShorten }
        state = newState

        // Start the filter *after* the deadline is committed. If the filter
        // fails to start, the lock is still recorded and every later launch
        // will try again — the reverse order would let a failed start leave the
        // user unlocked with a lock they believe is running.
        do {
            try await FilterController.shared.enable()
        } catch {
            throw LockError.filterUnavailable(error.localizedDescription)
        }
    }

    /// Add time to a running lock. Always permitted.
    public func extend(by seconds: TimeInterval) throws {
        guard isLocked else { throw LockError.notLocked }
        var next = state
        next.deadline = state.deadline.addingTimeInterval(seconds)
        guard LockStore.write(next) else { throw LockError.wouldShorten }
        state = next
    }

    /// Tighten a running blocklist lock into strict mode. Always permitted.
    public func tightenToStrict() throws {
        guard isLocked else { throw LockError.notLocked }
        var next = state
        next.mode = "strict"
        guard LockStore.write(next) else { throw LockError.wouldShorten }
        state = next
    }

    // MARK: - Ending

    private func expire() async {
        guard LockStore.clearIfExpired() else { return }
        state = .unlocked
        try? await FilterController.shared.disable()
    }

    /// Request early release. Does not end the lock — it schedules the end.
    ///
    /// The delay is the entire mechanism: requestable at any time, cannot be
    /// accelerated, can be cancelled.
    ///
    /// The date goes into `LockState`, so it inherits every protection the
    /// deadline has — written to all three stores, resolved by taking the
    /// LATEST value any of them reports, and refused by `LockStore.write` if it
    /// would move earlier. It previously lived in a plain preferences key that
    /// one `defaults write` could set to any value, and nothing read it back
    /// anyway; either half alone was a bug, and together they made the only
    /// documented way out of a lock inoperable.
    @discardableResult
    public func requestSelfRelease() throws -> Date {
        guard isLocked else { throw LockError.notLocked }
        if let existing = state.selfReleaseAt { return existing }

        let at = min(LockStore.trustedNow().addingTimeInterval(Self.selfReleaseDelay),
                     state.deadline)
        var next = state
        next.selfReleaseAt = at
        guard LockStore.write(next) else { throw LockError.wouldShorten }
        state = next
        return at
    }

    /// Withdraw a pending request. Always allowed — it makes the lock longer.
    public func cancelSelfRelease() {
        guard state.selfReleaseAt != nil else { return }
        var next = state
        next.selfReleaseAt = nil
        LockStore.write(next)
        state = LockStore.read()
    }

    /// The pending release date as the user should see it: the moment the lock
    /// will actually end, not merely what was asked for.
    ///
    /// These differ when a release date was tampered with — the store honours
    /// `selfReleaseDelay` from when it first saw the date, so a forged past
    /// date still waits. Showing the requested value there would promise an
    /// unlock that is not coming.
    public var pendingSelfRelease: Date? {
        guard state.selfReleaseAt != nil else { return nil }
        return LockStore.effectiveDeadline(state)
    }

    /// End a lock now, on an accountability partner's authority.
    ///
    /// `token` must be a server-issued, single-use approval. It is verified
    /// server-side on purpose: a locally-checkable code is a code that can be
    /// extracted from the binary.
    public func releaseWithPartnerApproval(token: String) async throws {
        guard isLocked else { throw LockError.notLocked }

        // Verification happens server-side and must succeed before anything is
        // touched locally. `LockStore.write` deliberately refuses to shorten a
        // deadline, so the only way past it is the approval-carrying path.
        let approval = try await PartnerService.shared.verifyRelease(
            token: token, lockStartedAt: state.startedAt)

        LockStore.clearWithPartnerApproval(approval)
        state = .unlocked
        try? await FilterController.shared.disable()
    }
}
