import AppKit
import Foundation

/// The setup steps of `docs/SETUP.md`, each read from this Mac, in the app.
///
/// `verify_enforcement.sh` answered "what is actually enforced?" — in a
/// terminal, which is the last place someone mid-setup looks. This is the same
/// evidence where the person already is, in the order the steps must be done,
/// each with what to do next. Like `ProtectionStatus`, the checklist itself is
/// a plain value computed from evidence, so every state is testable without
/// this Mac's real settings; `SetupEvidence.current()` is the only part that
/// reads the machine.

// MARK: - Evidence

public struct BrowserSetup: Equatable {
    public var name: String
    /// Profiles where the Hisn extension is missing or switched off. Empty
    /// means it runs in every profile.
    public var extensionOffIn: [String]
    /// What the hardening profile locks in this browser, read as policy
    /// forced on it — a setting the person could flip back does not count.
    public var incognitoLocked: Bool
    public var guestLocked: Bool
    public var dnsLocked: Bool
    public var profilesChecked: Bool
    public var extensionManaged: Bool
    public var nativeLinkProtected: Bool

    public init(name: String, extensionOffIn: [String] = [], incognitoLocked: Bool = false,
                guestLocked: Bool = false, dnsLocked: Bool = false,
                profilesChecked: Bool = true, extensionManaged: Bool = false,
                nativeLinkProtected: Bool = false) {
        self.name = name
        self.extensionOffIn = extensionOffIn
        self.incognitoLocked = incognitoLocked
        self.guestLocked = guestLocked
        self.dnsLocked = dnsLocked
        self.profilesChecked = profilesChecked
        self.extensionManaged = extensionManaged
        self.nativeLinkProtected = nativeLinkProtected
    }
}

/// Whether /etc/hosts points each search engine at its own SafeSearch
/// address (`block_dns.sh`, `safesearch_hosts.txt`).
public struct SafeSearchDNS: Equatable {
    /// Engines with no such line: SafeSearch holds only where the extension
    /// or a browser policy runs.
    public var missing: [String]
    /// Engines whose line points at an address their SafeSearch host no
    /// longer resolves to: that engine now fails to load.
    public var stale: [String]

    public init(missing: [String] = [], stale: [String] = []) {
        self.missing = missing
        self.stale = stale
    }
}

public struct SetupEvidence: Equatable {
    /// nil when the group could not be read — then the step says so rather
    /// than guessing either way.
    public var isAdmin: Bool?
    public var hostsEntries: Int?
    /// Private Relay and the public DoH servers blocked in /etc/hosts
    /// (`bypass_hosts.txt`): without that, encrypted DNS walks around it.
    public var dnsBypassesBlocked: Bool
    public var safeSearch: SafeSearchDNS
    public var partnerKeySet: Bool
    /// Installed Chromium browsers only: an absent browser is not a hole.
    public var browsers: [BrowserSetup]
    public var privateRelayOff: Bool
    public var screenTimeAdultFilter: Bool
    public var systemFilterRunning: Bool
    /// /Applications/Hisn.app and its bridge owned by root, the bridge not
    /// writable by this user. nil when Hisn is not in /Applications.
    public var appFilesProtected: Bool?

    public init(isAdmin: Bool?, hostsEntries: Int?, dnsBypassesBlocked: Bool = true,
                safeSearch: SafeSearchDNS = SafeSearchDNS(),
                partnerKeySet: Bool, browsers: [BrowserSetup], privateRelayOff: Bool,
                screenTimeAdultFilter: Bool, systemFilterRunning: Bool,
                appFilesProtected: Bool? = true) {
        self.isAdmin = isAdmin
        self.hostsEntries = hostsEntries
        self.dnsBypassesBlocked = dnsBypassesBlocked
        self.safeSearch = safeSearch
        self.partnerKeySet = partnerKeySet
        self.browsers = browsers
        self.privateRelayOff = privateRelayOff
        self.screenTimeAdultFilter = screenTimeAdultFilter
        self.systemFilterRunning = systemFilterRunning
        self.appFilesProtected = appFilesProtected
    }
}

// MARK: - Checklist

public struct SetupChecklist: Equatable {

    public enum State: Equatable {
        case done
        case todo
        /// Worth doing, not required: the paid system filter.
        case optional
    }

    /// What the step's button does.
    public enum Action: Equatable {
        case browserHelp
        case partnerSettings
        case screenTimeSettings
        case enableFilter
        case deviceManagementSettings
        case accountsSettings
        case applicationsFolder
        /// A command to run from the Hisn folder in Terminal.
        case command(String)
    }

    public struct Step: Identifiable, Equatable {
        public enum ID: String {
            case browsers, extensionManagement, domains, safeSearch, partner, profile, screenTime, appFiles, accounts, systemFilter
        }
        public let id: ID
        public let title: String
        public let detail: String
        public let state: State
        public let action: Action?
    }

    public let steps: [Step]

    public var required: [Step] { steps.filter { $0.state != .optional } }
    public var doneCount: Int { required.filter { $0.state == .done }.count }
    public var isComplete: Bool { doneCount == required.count }

    public init(_ e: SetupEvidence) {
        var steps: [Step] = []

        // 1. The extension — the only layer that reads page text.
        let missing = e.browsers.filter { !$0.extensionOffIn.isEmpty }
        if e.browsers.isEmpty {
            steps.append(Step(id: .browsers, title: String(localized: "Browser extension"),
                              detail: String(localized: "No supported browser is installed. Connect Hisn in a supported browser; Safari has no verified Hisn page scanner on this Mac."),
                              state: .todo, action: .browserHelp))
        } else if e.browsers.contains(where: { !$0.profilesChecked }) {
            let names = e.browsers.filter { !$0.profilesChecked }.map(\.name).formatted(.list(type: .and))
            steps.append(Step(id: .browsers, title: String(localized: "Browser extension"),
                              detail: String(localized: "Open and connect these browsers, then check again: \(names). Their profiles have not been verified."),
                              state: .todo, action: .browserHelp))
        } else if missing.isEmpty {
            steps.append(Step(id: .browsers, title: String(localized: "Browser extension"),
                              detail: String(localized: "On in every profile of every browser."),
                              state: .done, action: nil))
        } else {
            let places = missing.map { b in
                String(localized: "\(b.name) (\(b.extensionOffIn.formatted(.list(type: .and))))")
            }.formatted(.list(type: .and))
            steps.append(Step(id: .browsers, title: String(localized: "Browser extension"),
                              detail: String(localized: "Missing or switched off in: \(places). A profile without it is a way around it."),
                              state: .todo, action: .browserHelp))
        }

        let removable = e.browsers.filter { !$0.extensionManaged || !$0.nativeLinkProtected }
        steps.append(Step(id: .extensionManagement, title: String(localized: "Required browser protection"),
                          detail: removable.isEmpty
                            ? String(localized: "Installed browsers have required Hisn protection and an administrator-protected connection to the app.")
                            : String(localized: "Protection can still be removed or disconnected in \(removable.map(\.name).formatted(.list(type: .and))). Ask the administrator to install the published extension and Hisn protection profile."),
                          state: removable.isEmpty ? .done : .todo,
                          action: removable.isEmpty ? nil : .deviceManagementSettings))

        // 2. Domains for every app: the hosts file, or the system filter.
        let hostsOK = (e.hostsEntries ?? 0) >= ProtectionEvidence.hostsMinimum
        if hostsOK && !e.dnsBypassesBlocked && !e.systemFilterRunning {
            steps.append(Step(id: .domains, title: String(localized: "Domain blocking for every app"),
                              detail: String(localized: "The hosts file blocks domains, but encrypted DNS and iCloud Private Relay can still go around it. Enable the system filter to cover this gap."),
                              state: .todo, action: .enableFilter))
        } else if hostsOK || e.systemFilterRunning {
            steps.append(Step(id: .domains, title: String(localized: "Domain blocking for every app"),
                              detail: hostsOK
                                ? String(localized: "\((e.hostsEntries ?? 0).formatted()) domains in the hosts file.")
                                : String(localized: "The system filter is blocking domains."),
                              state: .done, action: nil))
        } else {
            steps.append(Step(id: .domains, title: String(localized: "Domain blocking for every app"),
                              detail: String(localized: "Enable the system filter to block domains outside your browser."),
                              state: .todo, action: .enableFilter))
        }

        // 2b. SafeSearch outside the browser: Safari, Firefox and every app
        //     that opens a search, through the hosts file.
        if !e.safeSearch.stale.isEmpty {
            steps.append(Step(id: .safeSearch, title: String(localized: "SafeSearch everywhere"),
                              detail: String(localized: "The address changed for \(e.safeSearch.stale.formatted(.list(type: .and))), so it stops loading until you run this again in the Hisn folder."),
                              state: .todo, action: .command("macos/install.sh --hosts")))
        } else if !e.safeSearch.missing.isEmpty {
            steps.append(Step(id: .safeSearch, title: String(localized: "SafeSearch everywhere"),
                              detail: String(localized: "Not forced outside the extension for \(e.safeSearch.missing.formatted(.list(type: .and))). Run this in the Hisn folder."),
                              state: .todo, action: .command("macos/install.sh --hosts")))
        } else {
            steps.append(Step(id: .safeSearch, title: String(localized: "SafeSearch everywhere"),
                              detail: String(localized: "Forced for Google, YouTube, Bing, DuckDuckGo and Brave Search in every browser and app."),
                              state: .done, action: nil))
        }

        // 3. The partner's key, before the first lock.
        steps.append(Step(id: .partner, title: String(localized: "Accountability partner"),
                          detail: e.partnerKeySet
                            ? String(localized: "Your partner’s key is set.")
                            : String(localized: "Without it, the only way out of a lock is the 48-hour request."),
                          state: e.partnerKeySet ? .done : .todo,
                          action: e.partnerKeySet ? nil : .partnerSettings))

        // 4. The hardening profile: private windows, guest windows, browser
        //    DNS, Private Relay.
        var open: [String] = []
        if !e.privateRelayOff { open.append(String(localized: "iCloud Private Relay")) }
        let privateOpen = e.browsers.filter { !$0.incognitoLocked }.map(\.name)
        if !privateOpen.isEmpty {
            open.append(String(localized: "private windows in \(privateOpen.formatted(.list(type: .and)))"))
        }
        let guestOpen = e.browsers.filter { !$0.guestLocked }.map(\.name)
        if !guestOpen.isEmpty {
            open.append(String(localized: "guest windows in \(guestOpen.formatted(.list(type: .and)))"))
        }
        let dnsOpen = e.browsers.filter { !$0.dnsLocked }.map(\.name)
        if !dnsOpen.isEmpty {
            open.append(String(localized: "private DNS in \(dnsOpen.formatted(.list(type: .and)))"))
        }
        steps.append(Step(id: .profile, title: String(localized: "Hardening profile"),
                          detail: open.isEmpty
                            ? String(localized: "Installed: private and guest windows, browser DNS and Private Relay are locked.")
                            : String(localized: "Still open: \(open.formatted(.list(type: .and))). Ask the person who manages this Mac to install the Hisn protection profile."),
                          state: open.isEmpty ? .done : .todo,
                          action: open.isEmpty ? nil : .deviceManagementSettings))

        // 5. Screen Time's adult filter, its passcode with the partner.
        steps.append(Step(id: .screenTime, title: String(localized: "Screen Time"),
                          detail: e.screenTimeAdultFilter
                            ? String(localized: "Limit Adult Websites is on. Its passcode belongs with your partner.")
                            : String(localized: "Turn on Limit Adult Websites, and let your partner choose the passcode."),
                          state: e.screenTimeAdultFilter ? .done : .todo,
                          action: e.screenTimeAdultFilter ? nil : .screenTimeSettings))

        // 6. Hisn's own files, owned by root — before the split, which is
        //    when owning them starts to matter: the admin-owned browser link
        //    runs the bridge inside the bundle.
        switch e.appFilesProtected {
        case .some(true):
            steps.append(Step(id: .appFiles, title: String(localized: "Hisn’s own files"),
                              detail: String(localized: "Owned by the system: no one without the administrator password can change them."),
                              state: .done, action: nil))
        case .some(false):
            steps.append(Step(id: .appFiles, title: String(localized: "Hisn’s own files"),
                              detail: String(localized: "Reinstall Hisn using its installer package so its files are protected by the administrator account."),
                              state: .todo, action: .applicationsFolder))
        case .none:
            steps.append(Step(id: .appFiles, title: String(localized: "Hisn’s own files"),
                              detail: String(localized: "Install Hisn using its installer package, then open it from Applications."),
                              state: .todo, action: .applicationsFolder))
        }

        // 7. Last: the account split. Everything above can be undone by an
        //    administrator, so this is the step that makes the rest hold.
        switch e.isAdmin {
        case .some(false):
            steps.append(Step(id: .accounts, title: String(localized: "Account split"),
                              detail: String(localized: "This is a standard account. Confirm below who keeps the separate administrator account."),
                              state: .done, action: nil))
        case .some(true):
            steps.append(Step(id: .accounts, title: String(localized: "Account split"),
                              detail: String(localized: "You are an administrator, so you can undo every step above. Do this last, with your partner."),
                              state: .todo, action: .accountsSettings))
        case .none:
            steps.append(Step(id: .accounts, title: String(localized: "Account split"),
                              detail: String(localized: "Could not read whether you are an administrator."),
                              state: .todo, action: .accountsSettings))
        }

        // 7. The system filter: VPN-proof, every app, needs the paid program.
        steps.append(Step(id: .systemFilter, title: String(localized: "System filter"),
                          detail: e.systemFilterRunning
                            ? String(localized: "Running: domain filtering is active for app connections. Search settings and page text need their other protection layers.")
                            : String(localized: "Enable the system filter, then approve it in System Settings if macOS asks."),
                          state: e.systemFilterRunning ? .done : .optional,
                          action: nil))

        self.steps = steps
    }
}

// MARK: - Reading this Mac

extension SetupEvidence {

    /// Everything the checklist needs, read now. Off the main thread: it reads
    /// every browser profile's preferences.
    static func current(systemFilterRunning: Bool) -> SetupEvidence {
        let ids = Set(NativeMessagingInstaller.extensionIDs)
        let support = FileManager.default.urls(for: .applicationSupportDirectory,
                                               in: .userDomainMask)[0]
        let browsers = NativeMessagingInstaller.browsers.compactMap { b -> BrowserSetup? in
            guard NSWorkspace.shared.urlForApplication(withBundleIdentifier: b.bundleID) != nil
            else { return nil }
            let domain = policyDomain(b.bundleID)
            let root = support.appendingPathComponent(b.userSupportDir)
            let profiles = inspectBrowserProfiles(in: root, ids: ids)
            let managedIDs = ids.filter { extensionIsManaged(ids: [$0],
                settings: forced("ExtensionSettings", domain),
                forceList: forced("ExtensionInstallForcelist", domain)) }
            let managedProfiles = inspectBrowserProfiles(in: root, ids: Set(managedIDs), requireManaged: true)
            return BrowserSetup(
                name: b.name,
                extensionOffIn: profiles.missing,
                incognitoLocked: forced("IncognitoModeAvailability", domain) as? Int == 1,
                guestLocked: forced("BrowserGuestModeEnabled", domain) as? Bool == false
                    && forced("BrowserAddPersonEnabled", domain) as? Bool == false,
                dnsLocked: forced("DnsOverHttpsMode", domain) as? String == "off",
                profilesChecked: profiles.checked,
                extensionManaged: !managedIDs.isEmpty && managedProfiles.checked
                    && managedProfiles.missing.isEmpty,
                nativeLinkProtected: forced("NativeMessagingUserLevelHosts", domain) as? Bool == false
                    && protectedBrowserLink(directory: b.systemDir, ids: ids))
        }
        let hosts = try? String(contentsOfFile: "/etc/hosts", encoding: .utf8)
        return SetupEvidence(
            isAdmin: isAdministrator(NSUserName()),
            hostsEntries: HostsFile.blockingEntries(),
            dnsBypassesBlocked: dnsBypassesBlocked(hosts: hosts),
            safeSearch: safeSearchDNS(hosts: hosts),
            partnerKeySet: PartnerService.currentKey() != nil,
            browsers: browsers,
            privateRelayOff: forced("allowCloudPrivateRelay", "com.apple.applicationaccess") as? Bool == false,
            screenTimeAdultFilter: forced("restrictWeb", "com.apple.familycontrols.contentfilter") as? Bool == true,
            systemFilterRunning: systemFilterRunning,
            appFilesProtected: appFilesProtected())
    }

    /// One name per engine, and the host whose address it should carry —
    /// the first entry of each group in `macos/safesearch_hosts.txt`.
    static let safeSearchEngines: [(engine: String, name: String, target: String)] = [
        ("Google", "www.google.com", "forcesafesearch.google.com"),
        ("YouTube", "www.youtube.com", "restrict.youtube.com"),
        ("Bing", "www.bing.com", "strict.bing.com"),
        ("DuckDuckGo", "duckduckgo.com", "safe.duckduckgo.com"),
        ("Brave Search", "search.brave.com", "forcesafe.search.brave.com"),
    ]

    /// Compares /etc/hosts with DNS. The SafeSearch hosts are not in the
    /// hosts file, so the system resolver answers them from DNS. Offline, an
    /// address cannot be judged stale, so a present line counts as forced.
    static func safeSearchDNS(hosts: String?,
                              resolve: (String) -> Set<String>? = resolveIPv4) -> SafeSearchDNS {
        let mapped = hostsMapping(hosts)
        var result = SafeSearchDNS()
        for e in safeSearchEngines {
            guard let have = mapped[e.name], have != "0.0.0.0" else {
                result.missing.append(e.engine)
                continue
            }
            if let want = resolve(e.target), !want.isEmpty, !want.contains(have) {
                result.stale.append(e.engine)
            }
        }
        return result
    }

    /// Name → address, first line wins — as the resolver reads the file.
    static func hostsMapping(_ hosts: String?) -> [String: String] {
        var mapped: [String: String] = [:]
        for line in (hosts ?? "").split(separator: "\n") {
            let f = line.split(whereSeparator: { $0 == " " || $0 == "\t" })
            guard f.count >= 2, !f[0].hasPrefix("#") else { continue }
            for name in f.dropFirst() {
                if name.hasPrefix("#") { break }
                if mapped[String(name)] == nil { mapped[String(name)] = String(f[0]) }
            }
        }
        return mapped
    }

    /// A sample of `bypass_hosts.txt`, as verify_enforcement.sh reads it:
    /// Private Relay's name, Firefox's default DoH server, Google's.
    static func dnsBypassesBlocked(hosts: String?) -> Bool {
        let mapped = hostsMapping(hosts)
        return ["mask.icloud.com", "mozilla.cloudflare-dns.com", "dns.google"]
            .allSatisfy { mapped[$0] == "0.0.0.0" }
    }

    static func resolveIPv4(_ host: String) -> Set<String>? {
        var hints = addrinfo(ai_flags: 0, ai_family: AF_INET, ai_socktype: SOCK_STREAM,
                             ai_protocol: 0, ai_addrlen: 0, ai_canonname: nil,
                             ai_addr: nil, ai_next: nil)
        var list: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &list) == 0 else { return nil }
        defer { freeaddrinfo(list) }
        var out = Set<String>()
        var p = list
        while let ai = p {
            if let sa = ai.pointee.ai_addr, ai.pointee.ai_family == AF_INET {
                var addr = sa.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr }
                var buf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                if inet_ntop(AF_INET, &addr, &buf, socklen_t(INET_ADDRSTRLEN)) != nil {
                    out.insert(String(cString: buf))
                }
            }
            p = ai.pointee.ai_next
        }
        return out
    }

    /// Whether the installed bundle and its bridge belong to root and the
    /// bridge cannot be written by this user.
    /// And the login agent root's too, in /Library/LaunchAgents: one in the
    /// user's own Library is theirs to delete.
    static func appFilesProtected(bundle: String = "/Applications/Hisn.app",
                                  agent: String = "/Library/LaunchAgents/app.hisn.agent.plist") -> Bool? {
        let fm = FileManager.default
        let bridge = bundle + "/Contents/MacOS/HisnBridge"
        guard let b = try? fm.attributesOfItem(atPath: bundle),
              let x = try? fm.attributesOfItem(atPath: bridge) else { return nil }
        let agentOwner = (try? fm.attributesOfItem(atPath: agent))?[.ownerAccountName] as? String
        return b[.ownerAccountName] as? String == "root"
            && x[.ownerAccountName] as? String == "root"
            && !fm.isWritableFile(atPath: bridge)
            && agentOwner == "root"
    }

    /// Where a configuration profile puts a browser's policy. The bundle id,
    /// except for Edge, whose app and policy domain differ.
    static func policyDomain(_ bundleID: String) -> String {
        bundleID == "com.microsoft.edgemac" ? "com.microsoft.Edge" : bundleID
    }

    /// A value only when a profile forces it: a preference the person set
    /// themselves, and could unset, is no lock.
    private static func forced(_ key: String, _ domain: String) -> Any? {
        guard CFPreferencesAppValueIsForced(key as CFString, domain as CFString) else { return nil }
        return CFPreferencesCopyAppValue(key as CFString, domain as CFString)
    }

    /// Membership of the admin group, from the directory rather than from
    /// this process's groups, which only change at the next login.
    static func isAdministrator(_ user: String) -> Bool? {
        guard let group = getgrnam("admin") else { return nil }
        var member = group.pointee.gr_mem
        while let name = member?.pointee {
            if String(cString: name) == user { return true }
            member = member?.advanced(by: 1)
        }
        return false
    }

    /// Profiles of one browser where none of `ids` is installed and switched
    /// on, by the name the browser shows. A profile is a folder (`Default`,
    /// `Profile 2`, …) with a `Preferences` file; the extension's state may
    /// sit in `Preferences` or `Secure Preferences`. Mirrors the check in
    /// `verify_enforcement.sh`.
    static func extensionMissing(in root: URL, ids: Set<String>) -> [String] {
        inspectBrowserProfiles(in: root, ids: ids).missing
    }

    static func inspectBrowserProfiles(in root: URL, ids: Set<String>,
                                       requireManaged: Bool = false) -> (checked: Bool, missing: [String]) {
        let fm = FileManager.default
        guard let names = try? fm.contentsOfDirectory(atPath: root.path) else { return (false, []) }
        var off: [String] = []
        var checked = 0
        var allReadable = true
        for folder in names.sorted() where folder == "Default" || folder.hasPrefix("Profile ") {
            let dir = root.appendingPathComponent(folder)
            checked += 1
            var settings: [String: Any] = [:]
            var shown = folder
            var readable = false
            for file in ["Preferences", "Secure Preferences"] {
                guard let data = fm.contents(atPath: dir.appendingPathComponent(file).path),
                      let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
                else { continue }
                readable = true
                if let s = (json["extensions"] as? [String: Any])?["settings"] as? [String: Any] {
                    settings.merge(s) { _, new in new }
                }
                if let name = (json["profile"] as? [String: Any])?["name"] as? String, !name.isEmpty {
                    shown = name
                }
            }
            allReadable = allReadable && readable
            let on = ids.contains { id in
                guard let e = settings[id] as? [String: Any] else { return false }
                if requireManaged {
                    // Chromium persists ManifestLocation: 7/9 are policy installs;
                    // an unpacked/user-installed copy is not proof the policy took.
                    guard let location = e["location"] as? Int, [7, 9].contains(location) else { return false }
                }
                let reasons = e["disable_reasons"]
                if reasons != nil && !(reasons is [Any]) && !(reasons is Int) { return false }
                let disabled = (reasons as? [Any]).map { !$0.isEmpty } ?? ((reasons as? Int).map { $0 != 0 } ?? false)
                return !disabled && (e["state"] as? Int) == 1
            }
            if !on { off.append(shown) }
        }
        return (checked > 0 && allReadable, off)
    }

    static func extensionIsManaged(ids: Set<String>, settings: Any?, forceList: Any?) -> Bool {
        guard !ids.isEmpty else { return false }
        let policies = settings as? [String: Any] ?? [:]
        let list = forceList as? [String] ?? []
        return ids.contains { id in
            if let policy = policies[id] as? [String: Any],
               let mode = policy["installation_mode"] as? String {
                return mode == "force_installed"
                    && secureUpdateURL(policy["update_url"] as? String)
            }
            return list.contains { entry in
                let fields = entry.split(separator: ";", maxSplits: 1, omittingEmptySubsequences: false)
                return fields.first.map(String.init) == id && fields.count == 2
                    && secureUpdateURL(String(fields[1]))
            }
        }
    }

    private static func secureUpdateURL(_ value: String?) -> Bool {
        guard let value, let url = URL(string: value), url.scheme == "https",
              let host = url.host, !host.isEmpty else { return false }
        return true
    }

    static func bridgeManifestMatches(_ data: Data, ids: Set<String>) -> Bool {
        guard let manifest = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              manifest["name"] as? String == NativeMessagingInstaller.hostName,
              manifest["type"] as? String == "stdio",
              manifest["path"] as? String == "/Applications/Hisn.app/Contents/MacOS/HisnBridge",
              let origins = manifest["allowed_origins"] as? [String], !origins.isEmpty,
              origins.allSatisfy({ origin in ids.contains { origin == "chrome-extension://\($0)/" } })
        else { return false }
        return true
    }

    static func protectedBrowserLink(directory: String, ids: Set<String>) -> Bool {
        let fm = FileManager.default
        let file = directory + "/\(NativeMessagingInstaller.hostName).json"
        for path in [directory, file] {
            guard let attributes = try? fm.attributesOfItem(atPath: path),
                  attributes[.ownerAccountName] as? String == "root",
                  let permissions = attributes[.posixPermissions] as? NSNumber,
                  permissions.intValue & 0o022 == 0,
                  !fm.isWritableFile(atPath: path) else { return false }
        }
        guard let data = fm.contents(atPath: file) else { return false }
        return bridgeManifestMatches(data, ids: ids)
    }
}
