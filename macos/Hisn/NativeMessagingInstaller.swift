import Foundation

/// Registers `HisnBridge` as a native messaging host for every Chromium-family
/// browser with a configured native link — Chrome (including Beta/Dev/Canary), Edge, Brave,
/// Vivaldi, Opera, Arc, Chromium and Helium — at user-scope, only when nobody
/// has already set it up at system-scope. Covering only Chrome and Edge left
/// the extension mute on Helium, the one non-Safari browser the user runs.
///
/// Without a manifest *somewhere*, the browser extension has no working
/// channel to the app at all. `background.js` learns the lock state
/// exclusively through `chrome.runtime.sendNativeMessage("app.hisn.bridge", …)`,
/// and Chrome will only start that conversation if a file describing the
/// host already exists at a fixed, browser-specific location before the call
/// is made. Nothing creates that file on its own, so a fresh install of both
/// halves talks to nobody, forever, and the browser extension sits in
/// browser-only mode, without the additional app commitment layer.
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

    /// Every extension id allowed to reach the bridge.
    ///
    /// A list, not one id, because the same extension has TWO ids depending on
    /// how it was loaded, and both are legitimate:
    ///
    ///   * unpacked, from `extension/` — the id is derived from the pinned
    ///     `key` in the manifest, `hfhaffbmoeepcdolgejeidkgaoapcjig`. This is
    ///     what a developer runs, and what `docs/EXTENSION_KEY.md` documents.
    ///
    ///   * published to the Chrome Web Store — the store assigns its OWN id and
    ///     ignores the manifest key, so the shipping extension has a different
    ///     one. `package.sh` says this out loud when it strips the key.
    ///
    /// Listing both means one app build serves the developer and the published
    /// extension without a rebuild. When the extension is published, add its
    /// store id in Info.plist (and to the force-install profile). `allowed_origins` is
    /// an allowlist of exact ids, so listing an id that does not exist yet
    /// costs nothing — it simply never matches until that extension is loaded.
    static var extensionIDs: [String] {
        Bundle.main.object(forInfoDictionaryKey: "HisnExtensionIDs") as? [String] ?? []
    }

    static var storeURLs: [URL] {
        ["HisnChromeStoreURL", "HisnEdgeStoreURL"].compactMap { key in
            guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
                  let url = URL(string: value), url.scheme == "https",
                  ["chromewebstore.google.com", "microsoftedge.microsoft.com"].contains(url.host)
            else { return nil }
            return url
        }
    }

    static let hostName = "app.hisn.bridge"

    /// One manifest file, one pair of destination folders per browser.
    /// Every Chromium-family browser reads the same JSON shape; it just scans a
    /// different folder, named after its own product directory. Paths match
    /// `install_native_host.sh` exactly — two independent implementations of
    /// where these go is exactly the kind of drift that made the embed-path bug
    /// this file also fixes invisible for as long as it was.
    ///
    /// This list mirrors the profile's `NATIVE_LINKED_BROWSER_IDS` in `make_profile.py`.
    /// It configures the link an installed extension can use to reach the app;
    /// it does not prove that a fork accepts managed policy or that every
    /// profile/private window checks in. Unregistered policy candidates remain
    /// distinct from browsers with a configured native link.
    struct Browser {
        let name: String
        /// Absolute; admin-writable only. For Chromium forks this is derived
        /// from `userSupportDir` by the standard convention (see `fork`); only
        /// Chrome and Edge are the branded special cases Chromium hard-codes.
        let systemDir: String
        /// The product directory under `~/Library/Application Support` — the
        /// same folder that holds the browser's `Default/` profile and
        /// `Local State`. The host manifest goes in its `NativeMessagingHosts/`
        /// subfolder, exactly as Chrome's own docs place it.
        let userSupportDir: String
        /// The app's bundle identifier — what `BrowserGuard` sees running and
        /// what the bridge records when this browser's extension checks in.
        let bundleID: String

        /// Edge's macOS policy domain differs from its application bundle ID.
        /// Never guess policy delivery from a display name or an app ID.
        var policyDomain: String {
            bundleID == "com.microsoft.edgemac" ? "com.microsoft.Edge" : bundleID
        }

        /// A Chromium fork: the system-wide host directory is
        /// `/Library/Application Support/<productDir>/NativeMessagingHosts`,
        /// the location `chrome::DIR_NATIVE_MESSAGING` resolves to for any
        /// non-Google, non-Microsoft build. Deriving it here, rather than
        /// retyping it, means the system and user paths for a fork cannot
        /// disagree about which product directory a browser uses.
        static func fork(_ name: String, _ productDir: String,
                         bundleID: String) -> Browser {
            Browser(
                name: name,
                systemDir: "/Library/Application Support/\(productDir)/NativeMessagingHosts",
                userSupportDir: productDir,
                bundleID: bundleID)
        }
    }

    /// Internal rather than private so a test can cross-check these against
    /// `install_native_host.sh`'s own `TARGETS` — see
    /// `NativeMessagingInstallerTests.testSystemPathsMatchTheShellInstaller`.
    ///
    /// Chrome and Edge keep the two branded directories Chromium special-cases
    /// (`/Library/Google/Chrome`, `/Library/Microsoft/Edge`); every other fork
    /// follows the `Browser.fork` convention. `installIfNeeded` writes only
    /// where the product directory already exists, so listing a browser that is
    /// not installed is free — it is simply skipped. Arc is the one entry that
    /// could not be verified against a live install on the dev machine; it
    /// follows Arc's documented `Arc/User Data` data directory and is skipped
    /// harmlessly if that turns out wrong, like any absent browser.
    static let browsers = [
        Browser(name: "Chrome",
               systemDir: "/Library/Google/Chrome/NativeMessagingHosts",
               userSupportDir: "Google/Chrome",
               bundleID: "com.google.Chrome"),
        // Google-branded channels share a system host folder, but not their
        // user-data folders. Chromium's documented channel paths and
        // chrome_paths.cc distinguish these two scopes explicitly.
        Browser(name: "Chrome Beta",
               systemDir: "/Library/Google/Chrome/NativeMessagingHosts",
               userSupportDir: "Google/Chrome Beta",
               bundleID: "com.google.Chrome.beta"),
        Browser(name: "Chrome Dev",
               systemDir: "/Library/Google/Chrome/NativeMessagingHosts",
               userSupportDir: "Google/Chrome Dev",
               bundleID: "com.google.Chrome.dev"),
        Browser(name: "Chrome Canary",
               systemDir: "/Library/Google/Chrome/NativeMessagingHosts",
               userSupportDir: "Google/Chrome Canary",
               bundleID: "com.google.Chrome.canary"),
        Browser(name: "Edge",
               systemDir: "/Library/Microsoft/Edge/NativeMessagingHosts",
               userSupportDir: "Microsoft Edge",
               bundleID: "com.microsoft.edgemac"),
        Browser.fork("Brave", "BraveSoftware/Brave-Browser", bundleID: "com.brave.Browser"),
        Browser.fork("Vivaldi", "Vivaldi", bundleID: "com.vivaldi.Vivaldi"),
        Browser.fork("Opera", "com.operasoftware.Opera", bundleID: "com.operasoftware.Opera"),
        Browser.fork("Arc", "Arc/User Data", bundleID: "company.thebrowser.Browser"),
        Browser.fork("Chromium", "Chromium", bundleID: "org.chromium.Chromium"),
        Browser.fork("Helium", "net.imput.helium", bundleID: "net.imput.helium"),
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
                if let support = userSupportDirectory(for: browser) {
                    let userManifest = support.appendingPathComponent("NativeMessagingHosts/\(hostName).json")
                    do {
                        try removeRedundantUserManifest(at: userManifest)
                    } catch {
                        NSLog("[Hisn] could not remove redundant browser link for %@: %@",
                              browser.name, error.localizedDescription)
                    }
                }
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

    /// Chrome checks user scope first. Remove the earlier app-created copy
    /// after the system installer takes over, so it cannot shadow that copy.
    static func removeRedundantUserManifest(at url: URL) throws {
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        try FileManager.default.removeItem(at: url)
    }

    static func hostManifest(bridgePath: String) -> [String: Any] {
        [
            "name": hostName,
            "description": "Hisn lock-state bridge",
            "path": bridgePath,
            "type": "stdio",
            "allowed_origins": extensionIDs.map { "chrome-extension://\($0)/" },
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
