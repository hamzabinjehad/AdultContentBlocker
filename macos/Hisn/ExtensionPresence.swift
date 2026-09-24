import Foundation
#if canImport(AppKit)
import AppKit
#endif

/// When the browser extension last checked in — overall, and per browser.
///
/// The bridge is the only process that can observe this: the extension polls
/// *into* it once a minute, so a timestamp written here is the whole of the
/// evidence that the extension is alive. The overall stamp has always existed
/// (the Overview's "Browser extension" row reads it). The per-browser stamps
/// are what `BrowserGuard` needs: "the extension is running somewhere" says
/// nothing about the Helium window someone just disabled it in, while Chrome
/// keeps checking in beside it.
///
/// One key per browser rather than one dictionary, because two browsers'
/// bridges run as separate processes and would otherwise race a
/// read-modify-write of the same value.
public enum ExtensionPresence {

    public static let anyBrowserKey = "extensionLastSeen"

    public static func key(forBrowser bundleID: String) -> String {
        "extensionSeen.\(bundleID)"
    }

    /// Called by the bridge on every heartbeat.
    public static func record(browser bundleID: String?, at now: Date = Date(),
                              in defaults: UserDefaults?) {
        defaults?.set(now, forKey: anyBrowserKey)
        if let bundleID, !bundleID.isEmpty {
            defaults?.set(now, forKey: key(forBrowser: bundleID))
        }
    }

    public static func lastSeen(browser bundleID: String,
                                in defaults: UserDefaults?) -> Date? {
        defaults?.object(forKey: key(forBrowser: bundleID)) as? Date
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
