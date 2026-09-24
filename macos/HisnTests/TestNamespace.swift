import Foundation

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
        prefix + UUID().uuidString
    }

    static func dispose(_ name: String) {
        precondition(name.hasPrefix(prefix), "refusing to dispose of a non-test domain")
        UserDefaults().removePersistentDomain(forName: name)
        CFPreferencesAppSynchronize(name as CFString)
        let library = FileManager.default.urls(for: .libraryDirectory,
                                               in: .userDomainMask)[0]
        let plist = library.appendingPathComponent("Preferences/\(name).plist")
        try? FileManager.default.removeItem(at: plist)
    }
}
