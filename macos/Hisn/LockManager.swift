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
    public static let selfReleaseDelay: TimeInterval = 48 * 3600

    public enum Duration: String, CaseIterable, Identifiable {
        case week = "7 days"
        case month = "30 days"
        case quarter = "90 days"
        case year = "1 year"

        public var id: String { rawValue }
        public var seconds: TimeInterval {
            switch self {
            case .week:    return 7 * 86400
            case .month:   return 30 * 86400
            case .quarter: return 90 * 86400
            case .year:    return 365 * 86400
            }
        }
        /// Free tier gets the 7-day plan; longer commitments are paid.
        public var requiresSubscription: Bool { self != .week }
    }

    public enum LockError: LocalizedError {
        case alreadyLocked(until: Date)
        case wouldShorten
        case notLocked
        case releaseNotDue(at: Date)
        case filterUnavailable(String)

        public var errorDescription: String? {
            switch self {
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
        if now >= state.deadline, state.mode != "off" {
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
    public func start(duration: Duration, strict: Bool) async throws {
        let deadline = LockStore.trustedNow().addingTimeInterval(duration.seconds)

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
    /// The delay is the entire mechanism. The request itself is recorded and
    /// cannot be accelerated, only cancelled.
    public func requestSelfRelease() throws -> Date {
        guard isLocked else { throw LockError.notLocked }
        let at = min(LockStore.trustedNow().addingTimeInterval(Self.selfReleaseDelay),
                     state.deadline)
        UserDefaults(suiteName: LockStore.appGroup)?
            .set(at, forKey: "selfReleaseAt")
        return at
    }

    public func cancelSelfRelease() {
        UserDefaults(suiteName: LockStore.appGroup)?
            .removeObject(forKey: "selfReleaseAt")
    }

    public var pendingSelfRelease: Date? {
        UserDefaults(suiteName: LockStore.appGroup)?
            .object(forKey: "selfReleaseAt") as? Date
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
