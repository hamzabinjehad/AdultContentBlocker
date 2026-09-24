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

    public var isEnabled: Bool { availability == .on }

    private let extensionIdentifier = "app.hisn.Hisn.HisnFilter"

    public enum FilterError: LocalizedError {
        case lockedCannotDisable
        case activationFailed(String)
        case configurationFailed(String)

        public var errorDescription: String? {
            switch self {
            case .lockedCannotDisable:
                return "The filter cannot be turned off while a lock is running."
            case let .activationFailed(d):
                return "The system extension could not be activated: \(d)"
            case let .configurationFailed(d):
                return "The filter configuration could not be saved: \(d)"
            }
        }
    }

    private init() {
        Task { await refresh() }
    }

    // MARK: - Public API

    public func refresh() async {
        do {
            try await NEFilterManager.shared().loadFromPreferences()
            availability = NEFilterManager.shared().isEnabled ? .on : .off
        } catch {
            lastError = error.localizedDescription
            availability = .unavailable(error.localizedDescription)
        }
    }

    /// Ensure the filter is installed, configured and running.
    public func enable() async throws {
        try await activateSystemExtension()

        let manager = NEFilterManager.shared()
        try await manager.loadFromPreferences()

        if manager.providerConfiguration == nil {
            let config = NEFilterProviderConfiguration()
            config.filterSockets = true      // the VPN-proof layer
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
    }

    /// Turn the filter off. Refused outright while a lock is running.
    public func disable() async throws {
        guard !LockStore.isLocked() else { throw FilterError.lockedCannotDisable }

        let manager = NEFilterManager.shared()
        try await manager.loadFromPreferences()
        manager.isEnabled = false
        try await manager.saveToPreferences()
        availability = .off
    }

    /// Called at every app launch.
    ///
    /// If a lock is running but the filter is off, someone turned it off — by
    /// disabling it in System Settings, or by the extension being torn down.
    /// Re-arm silently and record it, so the state the user sees always matches
    /// the state that is actually enforced.
    public func reassertIfNeeded() async {
        guard LockStore.isLocked() else { return }
        await refresh()
        guard !isEnabled else { return }

        NSLog("[Hisn] filter was off during an active lock — re-arming")
        UserDefaults(suiteName: LockStore.appGroup)?
            .set(Date(), forKey: "filterReassertedAt")
        try? await enable()
    }

    // MARK: - System extension activation

    private func activateSystemExtension() async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            let request = OSSystemExtensionRequest.activationRequest(
                forExtensionWithIdentifier: extensionIdentifier,
                queue: .main)
            let delegate = ActivationDelegate(continuation: cont)
            request.delegate = delegate
            Self.retainedDelegate = delegate
            OSSystemExtensionManager.shared.submitRequest(request)
        }
    }

    /// `OSSystemExtensionRequest.delegate` is weak; without this the delegate is
    /// deallocated before the request completes and the continuation never
    /// resumes.
    private static var retainedDelegate: ActivationDelegate?
}

private final class ActivationDelegate: NSObject, OSSystemExtensionRequestDelegate {
    private let continuation: CheckedContinuation<Void, Error>
    private var resumed = false

    init(continuation: CheckedContinuation<Void, Error>) {
        self.continuation = continuation
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
        NSLog("[Hisn] waiting for user approval in System Settings")
    }

    func request(_ request: OSSystemExtensionRequest,
                 didFinishWithResult result: OSSystemExtensionRequest.Result) {
        switch result {
        case .completed:
            finish(.success(()))
        case .willCompleteAfterReboot:
            // Treat as success: the lock is already recorded, and the filter
            // comes up on next boot.
            finish(.success(()))
        @unknown default:
            finish(.success(()))
        }
    }

    func request(_ request: OSSystemExtensionRequest, didFailWithError error: Error) {
        finish(.failure(FilterController.FilterError
            .activationFailed(error.localizedDescription)))
    }
}
