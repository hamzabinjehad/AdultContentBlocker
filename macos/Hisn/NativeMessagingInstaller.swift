import Foundation

/// Registers `HisnBridge` as a Chrome/Edge native messaging host, at
/// user-scope, only when nobody has already set it up at system-scope.
///
/// Without a manifest *somewhere*, the browser extension has no working
/// channel to the app at all. `background.js` learns the lock state
/// exclusively through `chrome.runtime.sendNativeMessage("app.hisn.bridge", …)`,
/// and Chrome will only start that conversation if a file describing the
/// host already exists at a fixed, browser-specific location before the call
/// is made. Nothing creates that file on its own, so a fresh install of both
/// halves talks to nobody, forever, and the browser extension sits in
/// `mode: "off"` no matter what lock the app thinks is running.
///
/// `macos/install_native_host.sh` already solves this the hardened way: run
/// with `sudo`, it writes to `/Library`, which needs an administrator to
/// install *and to remove* — under the setup this whole product is built
/// around (standard account, second person holding the admin password), that
/// is what stops the person under a lock from deleting the link themselves.
/// This type exists for everyone who has not run that script — which, absent
/// an onboarding flow that forces it, is nearly everyone on first launch — so
/// that the extension works at all rather than silently doing nothing.
///
/// **This must never write where the hardened script writes, or race it**:
/// on macOS, Chrome resolves a native messaging host by checking user-scope
/// *before* system-scope, so a user-writable copy sitting next to an
/// admin-owned one does not add redundancy — it silently wins, and quietly
/// hands the very protection `install_native_host.sh` exists for back to the
/// person the lock is supposed to constrain. `installIfNeeded` checks for a
/// system-scope manifest first and defers to it completely when one is
/// there — see `hasSystemManifest`.
///
/// Two more things below are not optional shortcuts:
///
///  * **The extension ID is pinned**, not discovered. Chrome derives an
///    unpacked extension's ID from `manifest.json`'s `"key"` field when one is
///    present, and otherwise assigns a random one per load location. Native
///    messaging needs the ID *before* the extension is ever loaded — it goes in
///    `allowed_origins` — so leaving it to chance would make this whole
///    mechanism unbuildable. See `extension/keys/README.md`.
///
///  * **The path is resolved at runtime, not hard-coded.** `Bundle.main` knows
///    where this copy of the app actually is; a path baked in at build time
///    would break the moment someone drags the app anywhere else.
public enum NativeMessagingInstaller {

    /// Derived from `extension/keys/hisn-extension-signing.pem` — see
    /// `extension/keys/README.md` for how, and for what changing it breaks.
    static let extensionID = "hfhaffbmoeepcdolgejeidkgaoapcjig"

    static let hostName = "app.hisn.bridge"

    /// One manifest file, one pair of destination folders per browser.
    /// Chromium-family browsers all read the same JSON shape; Chrome and Edge
    /// cover the two this product supports (see `manifest.json`'s
    /// `minimum_chrome_version`). Paths match `install_native_host.sh`
    /// exactly — two independent implementations of where these go is exactly
    /// the kind of drift that made the embed-path bug this file also fixes
    /// invisible for as long as it was.
    struct Browser {
        let name: String
        /// Absolute; admin-writable only.
        let systemDir: String
        /// Relative to `~/Library/Application Support`.
        let userSupportDir: String
    }

    /// Internal rather than private so a test can cross-check these against
    /// `install_native_host.sh`'s own `TARGETS` — see
    /// `NativeMessagingInstallerTests.testSystemPathsMatchTheShellInstaller`.
    static let browsers = [
        Browser(name: "Chrome",
               systemDir: "/Library/Google/Chrome/NativeMessagingHosts",
               userSupportDir: "Google/Chrome"),
        Browser(name: "Edge",
               systemDir: "/Library/Microsoft/Edge/NativeMessagingHosts",
               userSupportDir: "Microsoft Edge"),
    ]

    /// Write (or repair) the user-scope host manifest for every browser that
    /// is actually installed on this machine and does not already have a
    /// system-scope one.
    ///
    /// Deliberately unconditional on whether the user-scope file already
    /// exists: if the app has moved since the last launch — a common macOS
    /// pattern is running once from Downloads before dragging into
    /// /Applications — a stale path left in place is a silent failure
    /// indistinguishable from this never having run. Rewriting every launch
    /// costs nothing and cannot go stale.
    public static func installIfNeeded() {
        guard let bridgePath = bridgeExecutablePath() else {
            NSLog("[Hisn] HisnBridge not found in this bundle — "
                + "native messaging cannot be registered")
            return
        }

        let manifest = hostManifest(bridgePath: bridgePath)
        guard let json = try? JSONSerialization.data(
            withJSONObject: manifest, options: [.prettyPrinted, .sortedKeys])
        else { return }

        for browser in browsers {
            if hasSystemManifest(for: browser) {
                NSLog("[Hisn] %@ already has a system-scope native messaging "
                    + "host (see install_native_host.sh) — leaving it alone",
                    browser.name)
                continue
            }
            guard let dir = userSupportDirectory(for: browser) else { continue }
            let hostsDir = dir.appendingPathComponent("NativeMessagingHosts",
                                                       isDirectory: true)
            let manifestPath = hostsDir.appendingPathComponent("\(hostName).json")
            do {
                try FileManager.default.createDirectory(
                    at: hostsDir, withIntermediateDirectories: true)
                // Skip the write if the content is already exactly this —
                // avoids touching the file's mtime, and Chrome's own docs note
                // some versions re-read these lazily rather than on every
                // launch, so an unnecessary write buys nothing.
                if let existing = try? Data(contentsOf: manifestPath),
                   existing == json {
                    continue
                }
                try json.write(to: manifestPath, options: .atomic)
                NSLog("[Hisn] native messaging host registered for %@ "
                    + "(user scope)", browser.name)
            } catch {
                NSLog("[Hisn] could not register native messaging host for "
                    + "%@: %@", browser.name, error.localizedDescription)
            }
        }
    }

    // MARK: - Manifest content

    static func hostManifest(bridgePath: String) -> [String: Any] {
        [
            "name": hostName,
            "description": "Hisn lock-state bridge",
            "path": bridgePath,
            "type": "stdio",
            "allowed_origins": ["chrome-extension://\(extensionID)/"],
        ]
    }

    // MARK: - Locating things

    /// True if an admin has already run `install_native_host.sh` (default,
    /// system scope) for this browser.
    ///
    /// Existence is enough — this deliberately does not check that the
    /// system copy's `path` still points at a live `HisnBridge`, or that its
    /// `allowed_origins` matches our pinned ID. Trying to "fix" someone else's
    /// file, or fall through to a user-scope write because we judged their
    /// copy stale, is precisely the second-guessing that would let a person
    /// route around the admin-only file by making it look broken. A stale
    /// system manifest is the admin's to repair, with `sudo`, same as they
    /// installed it.
    static func hasSystemManifest(for browser: Browser) -> Bool {
        FileManager.default.fileExists(
            atPath: "\(browser.systemDir)/\(hostName).json")
    }

    /// Only the browsers actually present get a manifest written. Chrome's own
    /// native-messaging docs create the parent folder as a side effect of
    /// installing a host, which is fine for a browser that exists — but
    /// scattering `Microsoft Edge/` into a machine that has never run Edge is
    /// litter for no benefit, since there is nothing there to read it.
    private static func userSupportDirectory(for browser: Browser) -> URL? {
        guard let base = FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask
        ).first else { return nil }

        let dir = base.appendingPathComponent(browser.userSupportDir, isDirectory: true)
        return FileManager.default.fileExists(atPath: dir.path) ? dir : nil
    }

    /// `HisnBridge` is embedded at build time into `Contents/MacOS/`, next to
    /// the app's own binary — see the "Embed Bridge" copy phase in
    /// `generate_xcodeproj.py`, and its comment on why that phase does not use
    /// the usual "Executables" destination. Resolved from `Bundle.main` rather
    /// than assumed, and existence-checked rather than assumed: a manifest
    /// pointing at a binary that is not there is worse than no manifest, since
    /// it fails silently inside Chrome's own process rather than surfacing
    /// anywhere this app can see.
    static func bridgeExecutablePath() -> String? {
        let path = Bundle.main.bundlePath + "/Contents/MacOS/HisnBridge"
        return FileManager.default.isExecutableFile(atPath: path) ? path : nil
    }
}
