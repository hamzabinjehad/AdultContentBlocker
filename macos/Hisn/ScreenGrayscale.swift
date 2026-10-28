import AppKit
import Combine

/// The whole screen in shades of gray.
///
/// Colour is much of what makes an image or a video pull at someone; a gray
/// screen leaves every app working while taking most of that pull away. It is
/// the same effect as System Settings › Accessibility › Display › Color
/// Filters › Grayscale, switched from Hisn so it can be one click from the
/// menu bar and so it holds during a lock.
///
/// macOS has no public call for this. The Accessibility pane uses
/// `CGDisplayForceToGray` in CoreGraphics; it is looked up at run time, so a
/// macOS that ever drops it leaves the option unavailable instead of failing
/// to launch.
///
/// Like every other setting here, a lock can only tighten it: gray can be
/// turned on during a lock, and turning it off waits until the lock ends.
@MainActor
final class ScreenGrayscale: ObservableObject {
    static let shared = ScreenGrayscale()

    /// Hisn's own choice, kept per account. Nothing about the screen is
    /// changed while it is off, so a person who set Grayscale in System
    /// Settings keeps it.
    static let defaultsKey = "screenGrayscale"

    @Published private(set) var isOn: Bool
    private let defaults: UserDefaults
    private var timer: Timer?

    private typealias ForceToGray = @convention(c) (Bool) -> Void
    private typealias UsesForceToGray = @convention(c) () -> Bool

    private static let forceToGray: ForceToGray? = symbol("CGDisplayForceToGray")
    private static let usesForceToGray: UsesForceToGray? = symbol("CGDisplayUsesForceToGray")

    private static func symbol<T>(_ name: String) -> T? {
        guard let handle = dlopen(nil, RTLD_NOW), let pointer = dlsym(handle, name) else { return nil }
        return unsafeBitCast(pointer, to: T.self)
    }

    /// False on a macOS without the CoreGraphics call; the toggle is then
    /// shown disabled with the System Settings route instead.
    static var isAvailable: Bool { forceToGray != nil }

    enum Change: Equatable {
        case applied
        /// Turning gray off during a lock: refused, as loosening always is.
        case waitsForLockEnd
        case unavailable
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        isOn = defaults.bool(forKey: Self.defaultsKey)
    }

    /// Whether a change to `on` is allowed now. Pure, so the lock rule can be
    /// tested without touching the screen.
    static func decide(on: Bool, wasOn: Bool, locked: Bool, available: Bool) -> Change {
        if !available { return .unavailable }
        if wasOn && !on && locked { return .waitsForLockEnd }
        return .applied
    }

    /// `locked` defaults to the live lock. Read here, not as a default argument:
    /// Swift evaluates default arguments outside the main actor.
    @discardableResult
    func set(_ on: Bool, locked: Bool? = nil) -> Change {
        let locked = locked ?? LockManager.shared.isLocked
        let change = Self.decide(on: on, wasOn: isOn, locked: locked, available: Self.isAvailable)
        guard change == .applied else { return change }
        isOn = on
        defaults.set(on, forKey: Self.defaultsKey)
        Self.forceToGray?(on)
        return change
    }

    /// Re-apply at launch and keep it applied. A display being attached, the
    /// Mac waking, or the Accessibility pane can all put colour back; checking
    /// every few seconds closes that without anyone noticing the gap.
    func start() {
        guard timer == nil else { return }
        reassert()
        let center = NSWorkspace.shared.notificationCenter
        center.addObserver(forName: NSWorkspace.didWakeNotification, object: nil,
                           queue: .main) { [weak self] _ in
            Task { @MainActor in self?.reassert() }
        }
        NotificationCenter.default.addObserver(
            forName: NSApplication.didChangeScreenParametersNotification, object: nil,
            queue: .main) { [weak self] _ in
            Task { @MainActor in self?.reassert() }
        }
        timer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.reassert() }
        }
    }

    private func reassert() {
        guard isOn, let force = Self.forceToGray else { return }
        if Self.usesForceToGray?() != true { force(true) }
    }
}
