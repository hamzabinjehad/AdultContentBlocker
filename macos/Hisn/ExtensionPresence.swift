import Foundation
#if canImport(AppKit)
import AppKit
#endif

/// When the browser extension last checked in — overall, per bundle, and per
/// native-host parent process. A check-in is not proof of every browser profile.
///
/// The bridge is the only process that can observe this: the extension polls
/// *into* it every thirty seconds, so a timestamp written here is the whole of the
/// evidence that the extension is alive. The overall stamp has always existed
/// (the Overview's "Browser extension" row reads it). The per-browser stamps
/// remain for legacy status readers. `BrowserGuard` requires exact-process
/// stamps so another connected Helium or Chrome process cannot lend its proof.
///
/// One key per identity rather than one dictionary, because two browsers'
/// bridges run as separate processes and would otherwise race a
/// read-modify-write of the same value.
public enum ExtensionPresence {

    public static let anyBrowserKey = "extensionLastSeen"

    public static func key(forBrowser bundleID: String) -> String {
        "extensionSeen.\(bundleID)"
    }

    public static func key(forBrowser bundleID: String, processID: Int32) -> String? {
        BrowserCheckInIdentity.key(browser: bundleID, processID: processID).map { "extensionSeen.\($0)" }
    }

    /// Called by the bridge on every heartbeat.
    public static func record(browser bundleID: String?, processID: Int32? = nil, at now: Date = Date(),
                              in defaults: UserDefaults?) {
        defaults?.set(now, forKey: anyBrowserKey)
        if let bundleID, !bundleID.isEmpty {
            defaults?.set(now, forKey: key(forBrowser: bundleID))
            if let processID, let processKey = key(forBrowser: bundleID, processID: processID) {
                defaults?.set(now, forKey: processKey)
            }
        }
    }

    public static func lastSeen(browser bundleID: String,
                                in defaults: UserDefaults?) -> Date? {
        defaults?.object(forKey: key(forBrowser: bundleID)) as? Date
    }

    /// No bundle fallback: another Helium process is not this process's proof.
    public static func lastSeen(browser bundleID: String, processID: Int32,
                                in defaults: UserDefaults?) -> Date? {
        guard let processKey = key(forBrowser: bundleID, processID: processID) else { return nil }
        return defaults?.object(forKey: processKey) as? Date
    }

    public static func launchingBrowserPID() -> Int32? {
        let parent = getppid()
        return parent > 1 ? parent : nil
    }

    /// The bundle identifier of the app containing `executablePath` — the
    /// OUTERMOST `.app`, because a browser's helper processes live in `.app`
    /// bundles nested inside the browser's own.
    public static func bundleIdentifier(containing executablePath: String) -> String? {
        guard let range = executablePath.range(of: ".app/") else { return nil }
        let appPath = String(executablePath[..<range.lowerBound]) + ".app"
        return Bundle(path: appPath)?.bundleIdentifier
    }

    /// The browser that launched this process: native-messaging hosts are
    /// started by the browser's main process, so it is our parent.
    public static func launchingBrowser() -> String? {
        let parent = getppid()
        #if canImport(AppKit)
        if let id = NSRunningApplication(processIdentifier: parent)?.bundleIdentifier {
            return id
        }
        #endif
        var buffer = [CChar](repeating: 0, count: 4 * Int(MAXPATHLEN))
        guard proc_pidpath(parent, &buffer, UInt32(buffer.count)) > 0 else { return nil }
        return bundleIdentifier(containing: String(cString: buffer))
    }
}
