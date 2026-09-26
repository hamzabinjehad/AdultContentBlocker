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

        /// The name on the picker, in the person's language: "90 days", not
        /// "2 months, 29 days", so each preset counts in its own unit.
        public var title: String {
            let f = DateComponentsFormatter()
            f.unitsStyle = .full
            switch self {
            case .week, .month, .quarter: f.allowedUnits = [.day]
            case .year:                   f.allowedUnits = [.year]
            case .custom:                 return String(localized: "Custom")
            }
            var parts = DateComponents()
            switch self {
            case .week:    parts.day = 7
            case .month:   parts.day = 30
            case .quarter: parts.day = 90
            case .year:    parts.year = 1
            case .custom:  break
            }
            return f.string(from: parts) ?? rawValue
        }

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
                let shortest = LockManager.describe(LockManager.minimumLock)
                let longest = LockManager.describe(LockManager.maximumLock)
                return String(localized: "A lock has to be between \(shortest) and \(longest).")
            case let .alreadyLocked(until):
                return String(localized: "A lock is already running until \(until.formatted()).")
            case .wouldShorten:
                return String(localized: "This would shorten an active lock, which is not allowed.")
            case .notLocked:
                return String(localized: "No lock is currently running.")
            case let .releaseNotDue(at):
                return String(localized: "Your release request completes at \(at.formatted()).")
            case let .filterUnavailable(reason):
                return String(localized: "The content filter could not start: \(reason)")
            }
        }
    }

    private var ticker: AnyCancellable?
    /// A scheduled start is on its way; the next ticks must not start another.
    private var startingScheduled = false

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
        // The daily lock: a weaker schedule whose day has passed takes over,
        // then a window that is open starts — or lengthens — a lock to its end.
        ScheduleStore.applyDue(now: now)
        let schedule = ScheduleStore.current()
        if !startingScheduled,
           let window = schedule.lockNeeded(at: now, lockedUntil: mirrorsHoldLock ? state.deadline : nil) {
            startingScheduled = true
            Task {
                defer { startingScheduled = false }
                try? await start(seconds: window.end.timeIntervalSince(LockStore.trustedNow()),
                                 strict: schedule.strict)
            }
        }
        // The EFFECTIVE deadline, so a matured self-release actually ends the
        // lock. Comparing against `state.deadline` here is what made the whole
        // release mechanism inert: the date was recorded and then never
        // consulted by anything.
        // Never on the mirrors' say-so alone: while the filter's authority
        // still holds a lock, the mirrors ending (a forged clock, deleted
        // files) is healed by the next sync, not acted on.
        if now >= LockStore.effectiveDeadline(state), state.mode != "off",
           !FilterSync.shared.authorityLocked {
            Task { await expire() }
        }
        // The authority holds a lock the mirrors have lost: adopt it now
        // rather than at the next 20-second sync.
        if FilterSync.shared.authorityLocked, now >= state.deadline {
            FilterSync.soon()
        }
    }

    // MARK: - Derived

    public var isLocked: Bool { now < state.deadline || FilterSync.shared.authorityLocked }

    /// Whether the app's own copy holds the running lock — what extend,
    /// tighten and release act on. False for the moment between the mirrors
    /// being lost and the sync writing the authority's lock back.
    private var mirrorsHoldLock: Bool { now < state.deadline }

    public var remaining: TimeInterval { max(0, state.deadline.timeIntervalSince(now)) }

    public var remainingDescription: String {
        guard isLocked else { return String(localized: "Not locked") }
        // The two largest units, abbreviated in the person's language
        // ("3d 4h", «3 ي 4 س»).
        let f = DateComponentsFormatter()
        f.unitsStyle = .abbreviated
        f.allowedUnits = remaining >= 3600 ? [.day, .hour, .minute] : [.minute, .second]
        f.maximumUnitCount = 2
        f.zeroFormattingBehavior = .dropLeading
        return f.string(from: max(0, remaining.rounded(.down))) ?? ""
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

        if mirrorsHoldLock && deadline < state.deadline {
            throw LockError.wouldShorten
        }

        // Starting over a running lock extends it, and keeps whichever mode is
        // stricter: asking for "standard" while a strict lock runs must not be
        // a way out of strict (LockStore.refusal rejects it; this keeps the
        // request from failing for a reason the person did not intend).
        if FilterSync.shared.authorityLocked, !mirrorsHoldLock {
            // Extend the lock the filter holds, not a fresh one beside it.
            await FilterSync.shared.sync()
            state = LockStore.read()
        }
        let running = mirrorsHoldLock
        let keepStrict = running && state.mode == "strict"
        let newState = LockStore.LockState(
            deadline: deadline,
            mode: strict || keepStrict ? "strict" : "blocklist",
            startedAt: running ? state.startedAt : LockStore.trustedNow(),
            releaseNonce: running ? state.releaseNonce : PartnerService.newNonce())

        guard LockStore.write(newState) else { throw LockError.wouldShorten }
        state = newState
        FilterSync.soon()

        // Start the filter *after* the deadline is committed. If the filter
        // fails to start, the lock is still recorded and every later launch
        // will try again — the reverse order would let a failed start leave the
        // user unlocked with a lock they believe is running.
        //
        // A build with no team signature cannot run the filter at all, so it
        // is not asked to: the lock stands on the hosts file, the extension
        // and the browser guard, and the Overview says the filter is missing.
        guard FilterLink.shared.isConfigured else { return }
        do {
            try await FilterController.shared.enable()
        } catch {
            throw LockError.filterUnavailable(error.localizedDescription)
        }
    }

    /// Add time to a running lock. Always permitted.
    public func extend(by seconds: TimeInterval) throws {
        guard mirrorsHoldLock else { FilterSync.soon(); throw LockError.notLocked }
        var next = state
        next.deadline = state.deadline.addingTimeInterval(seconds)
        guard LockStore.write(next) else { throw LockError.wouldShorten }
        state = next
        FilterSync.soon()
    }

    /// Tighten a running blocklist lock into strict mode. Always permitted.
    public func tightenToStrict() throws {
        guard mirrorsHoldLock else { FilterSync.soon(); throw LockError.notLocked }
        var next = state
        next.mode = "strict"
        guard LockStore.write(next) else { throw LockError.wouldShorten }
        state = next
        FilterSync.soon()
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
        guard mirrorsHoldLock else { FilterSync.soon(); throw LockError.notLocked }
        if let existing = state.selfReleaseAt { return existing }

        let at = min(LockStore.trustedNow().addingTimeInterval(Self.selfReleaseDelay),
                     state.deadline)
        var next = state
        next.selfReleaseAt = at
        guard LockStore.write(next) else { throw LockError.wouldShorten }
        state = next
        FilterSync.soon()
        return at
    }

    /// Withdraw a pending request. Always allowed — it makes the lock longer.
    public func cancelSelfRelease() {
        guard state.selfReleaseAt != nil else { return }
        var next = state
        next.selfReleaseAt = nil
        LockStore.write(next)
        state = LockStore.read()
        FilterSync.soon()
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

    /// The code to send an accountability partner, or nil when no lock runs.
    public var partnerChallenge: String? {
        isLocked ? PartnerService.challenge(for: state) : nil
    }

    /// End a lock now, on an accountability partner's authority.
    ///
    /// `approval` is the partner's signature over `partnerChallenge`, checked
    /// against the partner key set up before the lock began. When the filter's
    /// authority is present it checks the same signature itself and must
    /// agree, or the lock stands — the mirrors are cleared only after that.
    public func releaseWithPartnerApproval(_ approval: String) async throws {
        guard isLocked else { throw PartnerService.PartnerError.noLock }
        let granted = try PartnerService.approve(approval, for: state,
                                                 key: PartnerService.currentKey())

        // Whenever the filter is running it must agree, and "no answer" is a
        // refusal. The local check above uses the app's copy of the partner
        // key — a file the person can overwrite with their own key — so on its
        // own it proves nothing once there is a filter to ask.
        if FilterLink.shared.isConfigured,
           FilterController.shared.isEnabled || FilterSync.shared.status != nil {
            guard let reply = await FilterLink.shared.submit(.partnerRelease(approval: approval)) else {
                throw LockError.filterUnavailable("the filter did not answer — try again in a minute")
            }
            guard reply.accepted else {
                throw LockError.filterUnavailable(reply.refusal ?? "the filter refused the approval")
            }
        }
        LockStore.clearWithPartnerApproval(granted)
        state = .unlocked
        FilterSync.soon()
        try? await FilterController.shared.disable()
    }
}
