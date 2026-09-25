import Foundation
@testable import Hisn

/// A throwaway preferences domain for one test.
///
/// Every suite that touches `LockStore` points `LockStore.appGroup` at a fresh
/// domain so a test can never write a real lock onto the machine running it.
/// `removePersistentDomain` empties such a domain but leaves its plist behind —
/// an empty 42-byte file per test, per run — and 1,628 of them had piled up in
/// `~/Library/Preferences` on the development Mac before this existed. So the
/// namespaces come from here, and `dispose` removes the file as well as the
/// contents.
///
/// Mid-test `removePersistentDomain` calls that simulate someone deleting a
/// store are deliberately left as they are: they are the scenario, not cleanup.
enum TestNamespace {

    static let prefix = "app.hisn.tests."

    static func make() -> String {
        _ = sweep
        _ = runDefaults
        return prefix + UUID().uuidString
    }

    /// LockStore's own throwaway locations for this test run, captured before
    /// any suite changes them, so `dispose` can hand them back: the next class
    /// used to inherit a namespace that had already been disposed of.
    private static let runDefaults = (group: LockStore.appGroup,
                                      keychain: LockStore.keychainService,
                                      system: LockStore.systemPath)

    /// `dispose` alone still left ~37 files a run: cfprefsd writes a removed
    /// domain back on its own schedule, and reproduced outside the tests a
    /// second delete half a second later still lost that race. So the first
    /// namespace of each run also clears what earlier runs left — only test
    /// domains, only older than two minutes (another run may be in flight) —
    /// and a run leaves at most its own few behind.
    private static let sweep: Void = {
        let prefs = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Preferences")
        guard let names = try? FileManager.default.contentsOfDirectory(atPath: prefs.path) else { return }
        let cutoff = Date().addingTimeInterval(-120)
        for name in names where name.hasPrefix(prefix) && name.hasSuffix(".plist") {
            let url = prefs.appendingPathComponent(name)
            guard let modified = (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?
                    .contentModificationDate, modified < cutoff
            else { continue }
            try? FileManager.default.removeItem(at: url)
        }
    }()

    static func dispose(_ name: String) {
        precondition(name.hasPrefix(prefix), "refusing to dispose of a non-test domain")
        if LockStore.appGroup == name { LockStore.appGroup = runDefaults.group }
        if LockStore.keychainService == name { LockStore.keychainService = runDefaults.keychain }
        if LockStore.systemPath.contains(name) { LockStore.systemPath = runDefaults.system }
        UserDefaults().removePersistentDomain(forName: name)
        CFPreferencesAppSynchronize(name as CFString)
        let library = FileManager.default.urls(for: .libraryDirectory,
                                               in: .userDomainMask)[0]
        let plist = library.appendingPathComponent("Preferences/\(name).plist")
        try? FileManager.default.removeItem(at: plist)
    }
}
