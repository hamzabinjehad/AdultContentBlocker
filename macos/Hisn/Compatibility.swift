import Foundation
import os

/// A scoped mutex with the same shape as `OSAllocatedUnfairLock`, which is
/// macOS 13 and newer only.
///
/// The app, the filter and the bridge all build for macOS 12, and all three
/// share `PolicyAuthority` and `FilterXPC` — the two hot paths where this
/// matters. `os_unfair_lock` is the primitive underneath the newer type, so
/// the backport keeps its priority donation and its cost. `NSLock` would have
/// been shorter and slower: the filter takes this lock on every socket flow it
/// inspects (`FilterDataProvider.identifierCache`), and that is not a place to
/// add a lower-priority fallback.
///
/// Deliberately not recursive, exactly like the type it stands in for: every
/// current caller holds the lock for a leaf critical section, and a recursive
/// call is a bug that should trap rather than quietly corrupt the state.
final class HnLock<State>: @unchecked Sendable {
    private var state: State
    private let lock: UnsafeMutablePointer<os_unfair_lock>

    init(initialState: State) {
        self.state = initialState
        self.lock = UnsafeMutablePointer<os_unfair_lock>.allocate(capacity: 1)
        self.lock.initialize(to: os_unfair_lock())
    }

    deinit {
        lock.deinitialize(count: 1)
        lock.deallocate()
    }

    /// Run `body` with the protected state. `@discardableResult` so a
    /// mutation-only call reads like a statement; every caller that wants the
    /// result still gets it.
    @discardableResult
    func withLock<R>(_ body: (inout State) throws -> R) rethrows -> R {
        os_unfair_lock_lock(lock)
        defer { os_unfair_lock_unlock(lock) }
        return try body(&state)
    }

    /// The unchecked spelling the callers use: `State` need not be `Sendable`.
    /// The lock itself is `@unchecked Sendable` for the same reason the OS
    /// type is — mutual exclusion, not the compiler, is what makes sharing
    /// safe here.
    @discardableResult
    func withLockUnchecked<R>(_ body: (inout State) throws -> R) rethrows -> R {
        os_unfair_lock_lock(lock)
        defer { os_unfair_lock_unlock(lock) }
        return try body(&state)
    }
}

extension HnLock where State == Void {
    /// The `OSAllocatedUnfairLock()` spelling: no stored value, a plain
    /// critical section around the instance's own fields.
    convenience init() {
        self.init(initialState: ())
    }

    @discardableResult
    func withLock<R>(_ body: () throws -> R) rethrows -> R {
        os_unfair_lock_lock(lock)
        defer { os_unfair_lock_unlock(lock) }
        return try body()
    }

    @discardableResult
    func withLockUnchecked<R>(_ body: () throws -> R) rethrows -> R {
        os_unfair_lock_lock(lock)
        defer { os_unfair_lock_unlock(lock) }
        return try body()
    }
}
