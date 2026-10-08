import Foundation
import NetworkExtension
import SystemExtensions

/// Installs and controls the content-filter system extension.
///
/// Two things worth knowing before changing anything here:
///
///  * Activating a system extension prompts the user for approval in System
///    Settings, once, at install time. That approval is the moment the whole
///    product hinges on, so it should happen during onboarding while the person
///    is motivated — not the first time they start a lock.
///
///  * `NEFilterManager.shared().isEnabled = false` is the obvious way out, so
///    this type refuses to do it while a lock is active, and the app re-asserts
///    the filter on every launch. That does not stop an administrator disabling
///    it from System Settings — nothing in userspace can. It stops the casual
///    attempt, and it makes the deliberate one visible.
@MainActor
public final class FilterController: ObservableObject {

    public static let shared = FilterController()

    /// What `NEFilterManager` last told us, including "nothing yet".
    ///
    /// This used to be `isEnabled = false` from the first instant, so the
    /// window said "not running" before the question had been asked and the
    /// answer could not be told apart from the filter genuinely being off. A
    /// missing answer is its own state; the status model shows it as
    /// "checking", never as either verdict.
    public enum Availability: Equatable {
        case unknown
        case unavailable(String)
        case off
        case on
    }

    @Published public private(set) var availability: Availability = .unknown
    @Published public private(set) var lastError: String?
    @Published public private(set) var isEnabling = false
    @Published public private(set) var needsUserApproval = false
    @Published public private(set) var restartRequired = false

    public var isEnabled: Bool { availability == .on }

    private let extensionIdentifier = "app.hisn.Hisn.HisnFilter"
    private let recovery = FilterRecoveryCoordinator()
    private var configurationRevision = 0
    private var isDisabling = false

    public enum FilterError: LocalizedError {
        case lockedCannotDisable
        case activationFailed(String)
        case configurationFailed(String)
        case activationInProgress
        case activationRequiresRestart

        public var errorDescription: String? {
            switch self {
            case .lockedCannotDisable:
                return String(localized: "The filter cannot be turned off while a lock is running.")
            case let .activationFailed(d):
                return String(localized: "The system extension could not be activated: \(d)")
            case let .configurationFailed(d):
                return String(localized: "The filter configuration could not be saved: \(d)")
            case .activationInProgress:
                return String(localized: "Filter setup is already in progress. Complete the approval in System Settings.")
            case .activationRequiresRestart:
                return String(localized: "Restart your Mac to finish installing the filter, then open Hisn and enable it again.")
            }
        }
    }

    private init() {
        Task { await refresh() }
    }

    // MARK: - Public API

    public func refresh(clearError: Bool = true) async {
        do {
            try await NEFilterManager.shared().loadFromPreferences()
            availability = NEFilterManager.shared().isEnabled ? .on : .off
            if clearError && !restartRequired { lastError = nil }
        } catch {
            lastError = error.localizedDescription
            availability = .unavailable(error.localizedDescription)
        }
    }

    /// Ensure the filter is installed, configured and running.
    public func enable() async throws {
        // A second request used to replace the first request's weak delegate,
        // leaving its continuation suspended forever while approval was open.
        guard !isEnabling, !isDisabling else { throw FilterError.activationInProgress }
        configurationRevision &+= 1
        isEnabling = true
        needsUserApproval = false
        restartRequired = false
        lastError = nil
        defer {
            isEnabling = false
            needsUserApproval = false
            retainedDelegate = nil
        }
        do {
            try await activateSystemExtension()
            try await configureFilter()
        } catch {
            if case FilterError.activationRequiresRestart = error {
                restartRequired = true
            }
            lastError = error.localizedDescription
            throw error
        }
    }

    @discardableResult
    private func configureFilter(requiresEnabled: Bool = false) async throws -> Bool {

        let manager = NEFilterManager.shared()
        try await manager.loadFromPreferences()
        if requiresEnabled && !manager.isEnabled && !EffectiveLock.isLocked {
            availability = .off
            return false
        }

        if manager.providerConfiguration == nil {
            let config = NEFilterProviderConfiguration()
            config.filterSockets = true      // evaluate application socket flows
            config.filterPackets = false     // flow-level is enough, and cheaper
            manager.providerConfiguration = config
        }
        manager.localizedDescription = "Hisn Content Protection"
        manager.isEnabled = true

        do {
            try await manager.saveToPreferences()
        } catch {
            throw FilterError.configurationFailed(error.localizedDescription)
        }
        availability = .on
        return true
    }

    /// Turn the filter off. Refused outright while a lock is running.
    public func disable() async throws {
        // Not on the app's own word. Its mirrors are the user's files and their
        // clock a key in the user's defaults: with the clock forged to 2099 the
        // app decided the lock was over and switched the filter off itself —
        // and the filter's authority, which knew better, went down with it.
        guard !EffectiveLock.isLocked else { throw FilterError.lockedCannotDisable }
        guard !isEnabling, !isDisabling else { throw FilterError.activationInProgress }
        configurationRevision &+= 1
        isDisabling = true
        defer { isDisabling = false }
        if FilterLink.shared.isConfigured, isEnabled {
            guard let status = await FilterLink.shared.status() else {
                throw FilterError.configurationFailed("the filter did not confirm the lock is over")
            }
            guard !status.isLocked else { throw FilterError.lockedCannotDisable }
        }

        let manager = NEFilterManager.shared()
        try await manager.loadFromPreferences()
        manager.isEnabled = false
        try await manager.saveToPreferences()
        availability = .off
    }

    /// Called at launch and by the reconciliation timer, even with no window.
    /// Disabled configuration is restored only during a lock. An enabled but
    /// unreachable provider gets its configuration reasserted without toggling
    /// it off. This is a recovery attempt, not a guarantee of continuous denial:
    /// protection status still requires the provider's own live response.
    public func reassertIfNeeded() async {
        let revision = configurationRevision
        await recovery.run(eligible: {
            FilterLink.shared.isConfigured && !self.isEnabling
                && !self.isDisabling && !self.needsUserApproval && !self.restartRequired
                && self.configurationRevision == revision
        }, inspect: {
            // Keep the last failure visible while automatic retries back off.
            await self.refresh(clearError: false)
            switch self.availability {
            case .off:
                return EffectiveLock.isLocked ? .disabled : .healthy
            case .on:
                return await FilterLink.shared.status() == nil ? .unresponsive : .healthy
            case .unknown, .unavailable:
                return .unavailable
            }
        }, stillNeeded: { condition in
            condition != .disabled || EffectiveLock.isLocked
        }, recover: { condition in
            NSLog("[Hisn] reasserting filter configuration: %@", "\(condition)")
            switch condition {
            case .disabled:
                try await self.enable()
            case .unresponsive:
                // The system extension is already configured. Don't submit a
                // new activation request or create a disable/enable gap.
                self.isEnabling = true
                defer { self.isEnabling = false }
                do {
                    guard try await self.configureFilter(requiresEnabled: true) else { return }
                    self.lastError = nil
                }
                catch { self.lastError = error.localizedDescription; throw error }
            case .healthy, .unavailable:
                return
            }
            // This records a saved configuration, never provider liveness.
            UserDefaults(suiteName: LockStore.appGroup)?
                .set(Date(), forKey: "filterReassertedAt")
        })
    }

    // MARK: - System extension activation

    private func activateSystemExtension() async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            let request = OSSystemExtensionRequest.activationRequest(
                forExtensionWithIdentifier: extensionIdentifier,
                queue: .main)
            let delegate = ActivationDelegate(continuation: cont) { [weak self] in
                Task { @MainActor in self?.needsUserApproval = true }
            }
            request.delegate = delegate
            retainedDelegate = delegate
            OSSystemExtensionManager.shared.submitRequest(request)
        }
    }

    /// `OSSystemExtensionRequest.delegate` is weak; without this the delegate is
    /// deallocated before the request completes and the continuation never
    /// resumes.
    private var retainedDelegate: ActivationDelegate?
}

/// Serializes automatic recovery across suspension points. Uses monotonic
/// uptime so changing the wall clock cannot accelerate retries. Manual setup
/// remains available independently of this backoff.
@MainActor
final class FilterRecoveryCoordinator {
    enum Condition: Equatable { case healthy, unavailable, disabled, unresponsive }

    private let uptime: () -> TimeInterval
    private var inFlight = false
    private var lastAttempt: TimeInterval?
    private var failures = 0

    init(uptime: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }) {
        self.uptime = uptime
    }

    func run(eligible: () -> Bool, inspect: () async -> Condition,
             stillNeeded: (Condition) -> Bool = { _ in true },
             recover: (Condition) async throws -> Void) async {
        guard !inFlight, eligible() else { return }
        let delay = min(300.0, 60.0 * pow(2.0, Double(max(0, failures - 1))))
        if let lastAttempt, uptime() - lastAttempt < delay { return }
        inFlight = true
        defer { inFlight = false }
        let condition = await inspect()
        // Approval, restart state or a lock may have changed during the read.
        guard eligible() else { return }
        switch condition {
        case .healthy:
            lastAttempt = nil
            failures = 0
        case .unavailable:
            break // no confirmed configuration to safely change
        case .disabled, .unresponsive:
            guard stillNeeded(condition) else { return }
            lastAttempt = uptime() // reserve before another actor turn can run
            do { try await recover(condition); failures = 0 }
            catch { failures = min(failures + 1, 4) }
        }
    }
}

final class ActivationDelegate: NSObject, OSSystemExtensionRequestDelegate {
    private let continuation: CheckedContinuation<Void, Error>
    private let needsApproval: () -> Void
    private var resumed = false

    init(continuation: CheckedContinuation<Void, Error>, needsApproval: @escaping () -> Void) {
        self.continuation = continuation
        self.needsApproval = needsApproval
    }

    private func finish(_ result: Result<Void, Error>) {
        guard !resumed else { return }   // a continuation may resume only once
        resumed = true
        continuation.resume(with: result)
    }

    func request(_ request: OSSystemExtensionRequest,
                 actionForReplacingExtension existing: OSSystemExtensionProperties,
                 withExtension ext: OSSystemExtensionProperties)
    -> OSSystemExtensionRequest.ReplacementAction {
        .replace
    }

    func requestNeedsUserApproval(_ request: OSSystemExtensionRequest) {
        needsApproval()
        NSLog("[Hisn] waiting for user approval in System Settings")
    }

    func request(_ request: OSSystemExtensionRequest,
                 didFinishWithResult result: OSSystemExtensionRequest.Result) {
        switch result {
        case .completed:
            finish(.success(()))
        case .willCompleteAfterReboot:
            finish(.failure(FilterController.FilterError.activationRequiresRestart))
        @unknown default:
            finish(.failure(FilterController.FilterError.activationFailed("Unknown activation result")))
        }
    }

    func request(_ request: OSSystemExtensionRequest, didFailWithError error: Error) {
        finish(.failure(FilterController.FilterError
            .activationFailed(error.localizedDescription)))
    }
}
