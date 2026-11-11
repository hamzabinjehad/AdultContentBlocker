import Foundation
import Security
import os

/// A signed installation must not downgrade the browser to editable local
/// mirrors when the configured authority stops answering. A diagnostic reply
/// has no lockUntil, so the browser counts it as a failed heartbeat and keeps
/// its last restrictive rules. Unsigned/browser-only setups still use mirrors.
public enum BridgePolicy {
    public static func reply(local: PolicyView, authority: PolicyStatus?,
                             requiresAuthority: Bool, listVersion: Int) -> [String: Any] {
        guard !requiresAuthority || authority != nil else {
            return ["ok": false, "reason": "authority-unreachable"]
        }
        let view = authority.map {
            PolicyMerge.stricter(editor: local, other: PolicyView(status: $0))
        } ?? local
        var reply = view.bridgeReply(listVersion: listVersion)
        if let authority, authority.isLocked, authority.persistenceError != nil {
            // A failed expiry save keeps the previous policy in force, whose
            // real deadline may already be past on the browser's clock. Keep
            // correction/settings guards locked until a fresh authority reply
            // confirms a durable unlock. This is transport only: neither the
            // real lock nor the user's mirrors acquire an invented deadline.
            reply["lockUntil"] = Date.distantFuture.timeIntervalSince1970 * 1000
        }
        return reply
    }
}

/// The channel between the app (and the bridge) and the filter's
/// `PolicyService`.
///
/// Every value crosses as JSON `Data` — `PolicyRequest`, `PolicyResponse`,
/// `PolicyStatus` — so the Objective-C surface is three methods and there is
/// no NSSecureCoding class list to keep in step with the Swift types.
///
/// Apple's pattern for talking to a Network Extension system extension: the
/// extension's Info.plist names a Mach service (`NEMachServiceName`, prefixed
/// with one of its app groups), the provider listens on it, and the app
/// connects with `.privileged` because the listener lives in the system
/// (root) bootstrap namespace. Both ends pin the other's code signature to
/// this team, so a `defaults write`, a script, or a look-alike binary cannot
/// ask for anything.
@objc public protocol HisnFilterXPC {
    func status(reply: @escaping (Data) -> Void)
    func submit(_ request: Data, reply: @escaping (Data) -> Void)
    /// A verified list generation, re-verified by the filter before use.
    func installGeneration(manifest: Data, signature: Data, domains: Data,
                           terms: Data, reply: @escaping (Data) -> Void)
}

/// The filter's reply to `installGeneration`.
public struct GenerationReply: Codable, Equatable {
    public var installed: Bool
    public var version: Int
    public var error: String?
}

public enum FilterXPC {

    public static let infoKey = "NEMachServiceName"

    /// The service name, read from the filter bundle embedded in the app — the
    /// same Info.plist the system reads, so the two cannot disagree. Its team
    /// prefix is substituted at build time, which is why it is not a constant.
    public static func machServiceName(appBundle: URL) -> String? {
        let plist = appBundle
            .appendingPathComponent("Contents/Library/SystemExtensions/HisnFilter.systemextension")
            .appendingPathComponent("Contents/Info.plist")
        guard let info = NSDictionary(contentsOf: plist) as? [String: Any]
        else { return nil }
        return machServiceName(filterInfo: info)
    }

    public static func machServiceName(filterInfo: [String: Any]) -> String? {
        guard let ne = filterInfo["NetworkExtension"] as? [String: Any],
              let name = ne[infoKey] as? String, !name.isEmpty,
              !name.contains("$(") else { return nil }
        return name
    }

    /// The app bundle this process belongs to: the app itself, or — for the
    /// bridge in `Contents/MacOS` — the bundle around it.
    public static func enclosingAppBundle() -> URL? {
        if Bundle.main.bundleURL.pathExtension == "app" { return Bundle.main.bundleURL }
        var url = URL(fileURLWithPath: CommandLine.arguments[0]).resolvingSymlinksInPath()
        while url.path != "/" {
            url.deleteLastPathComponent()
            if url.pathExtension == "app" { return url }
        }
        return nil
    }

    /// The team this process is signed by, or nil for an unsigned build.
    public static func ownTeamIdentifier() -> String? {
        var code: SecCode?
        guard SecCodeCopySelf([], &code) == errSecSuccess, let code else { return nil }
        var staticCode: SecStaticCode?
        guard SecCodeCopyStaticCode(code, [], &staticCode) == errSecSuccess,
              let staticCode else { return nil }
        var info: CFDictionary?
        guard SecCodeCopySigningInformation(
                staticCode, SecCSFlags(rawValue: kSecCSSigningInformation), &info)
                == errSecSuccess,
              let dict = info as? [String: Any],
              let team = dict[kSecCodeInfoTeamIdentifier as String] as? String,
              !team.isEmpty else { return nil }
        return team
    }

    /// The requirement each end pins the other to: Apple-issued signature,
    /// same team. nil when unsigned — then nothing is trusted.
    public static func peerRequirement() -> String? {
        guard let team = ownTeamIdentifier() else { return nil }
        return "anchor apple generic and certificate leaf[subject.OU] = \"\(team)\""
    }

    public static func interface() -> NSXPCInterface {
        NSXPCInterface(with: HisnFilterXPC.self)
    }

    /// Whether the process with `pid` is signed by this team — the admission
    /// check the listener uses on macOS 12, where `setCodeSigningRequirement`
    /// does not exist.
    ///
    /// macOS 13 pins the whole connection to the requirement and the system
    /// re-checks every message. macOS 12's `NSXPCConnection` exposes no audit
    /// token and no code signature, only the peer's PID, so the peer is checked
    /// once when the connection is offered. Everything after that travels over
    /// the connection the system bound to that peer. The residual gap is a PID
    /// reused between the kernel creating the connection and this call, which
    /// needs a cooperating process in this same user's session — worth naming,
    /// and not the same thing as an unchecked connection.
    ///
    /// Every team-signed process is trusted by the existing requirement: this
    /// check is exactly as strict as the one the newer API is given.
    static func process(pid: pid_t, satisfies requirement: String) -> Bool {
        guard pid > 0 else { return false }
        let attributes = [kSecGuestAttributePid as String: NSNumber(value: pid)] as CFDictionary
        var code: SecCode?
        guard SecCodeCopyGuestWithAttributes(nil, attributes, [], &code) == errSecSuccess,
              let code else { return false }
        var parsed: SecRequirement?
        guard SecRequirementCreateWithString(requirement as CFString, [], &parsed) == errSecSuccess,
              let parsed else { return false }
        return SecCodeCheckValidity(code, [], parsed) == errSecSuccess
    }
}

/// Why a build cannot reach a system filter, when the build itself is the
/// reason. Separate from a filter that is merely off: these are properties of
/// the installed binary, so no button in the app can change them.
public enum FilterBuildBlock: String, Equatable, Sendable {
    /// No Apple-issued team on this binary — an ad-hoc or unsigned build.
    /// macOS never loads a Network Extension from one, and the restricted
    /// Network Extension entitlement cannot be issued without the paid
    /// Developer Program.
    case unsignedBuild
    /// The app bundle carries no filter extension, or its Mach service name
    /// still contains the unsubstituted build variable.
    case filterNotEmbedded

    /// One sentence naming the cause and the fix, so a status row is never a
    /// dead end.
    public var explanation: String {
        switch self {
        case .unsignedBuild:
            return String(localized: "This build is ad-hoc signed, so macOS will not load its system filter. Rebuild it with your Apple Developer Program team (Setup step 9).")
        case .filterNotEmbedded:
            return String(localized: "This app bundle has no filter extension, so there is nothing to turn on. Reinstall the full Hisn build.")
        }
    }
}

/// The app's and the bridge's side of the channel.
///
/// Every call has a timeout and every failure is `nil`: an unreachable filter
/// is the normal state on a machine without the paid entitlements, and it
/// must read as "no authority here", never hang a heartbeat or the UI.
public final class FilterLink: @unchecked Sendable {

    public static let shared = FilterLink()

    private let serviceName: String?
    private let requirement: String?
    private let queue = DispatchQueue(label: "app.hisn.filterlink")
    private var connection: NSXPCConnection?

    public init(serviceName: String? = FilterXPC.enclosingAppBundle()
                    .flatMap(FilterXPC.machServiceName(appBundle:)),
                requirement: String? = FilterXPC.peerRequirement()) {
        self.serviceName = serviceName
        self.requirement = requirement
    }

    /// Why this build cannot reach a filter, or nil when it can. The two
    /// causes have different fixes — re-sign the build, versus reinstall a
    /// complete one — so the UI must not collapse them into one message.
    public var buildBlock: FilterBuildBlock? {
        if requirement == nil { return .unsignedBuild }
        if serviceName == nil { return .filterNotEmbedded }
        return nil
    }

    /// Whether this build could reach a filter at all.
    public var isConfigured: Bool { buildBlock == nil }

    private func proxy(onError: @escaping (Error) -> Void) -> HisnFilterXPC? {
        queue.sync {
            guard let serviceName, let requirement else { return nil }
            if connection == nil {
                let c = NSXPCConnection(machServiceName: serviceName, options: .privileged)
                c.remoteObjectInterface = FilterXPC.interface()
                if #available(macOS 13.0, *) { c.setCodeSigningRequirement(requirement) }
                c.invalidationHandler = { [weak self] in
                    self?.queue.async { if self?.connection === c { self?.connection = nil } }
                }
                // The filter restarted. Invalidate this connection before
                // dropping it — dropping alone leaked one per restart — and the
                // next call makes a fresh one.
                c.interruptionHandler = { [weak self] in
                    self?.queue.async {
                        if self?.connection === c { self?.connection = nil }
                        c.invalidate()
                    }
                }
                c.resume()
                connection = c
            }
            return connection?.remoteObjectProxyWithErrorHandler(onError) as? HisnFilterXPC
        }
    }

    /// Call `body` and wait at most `timeout` for its reply.
    private func call(timeout: TimeInterval,
                      _ body: (HisnFilterXPC, @escaping (Data?) -> Void) -> Void) -> Data? {
        let done = DispatchSemaphore(value: 0)
        let box = HnLock<Data?>(initialState: nil)
        let finish: (Data?) -> Void = { data in
            box.withLock { if $0 == nil { $0 = data } }
            done.signal()
        }
        guard let remote = proxy(onError: { _ in finish(nil) }) else { return nil }
        body(remote, finish)
        guard done.wait(timeout: .now() + timeout) == .success else { return nil }
        return box.withLock { $0 }
    }

    public func status(timeout: TimeInterval = 1) -> PolicyStatus? {
        call(timeout: timeout) { remote, finish in remote.status(reply: finish) }
            .flatMap { try? JSONDecoder().decode(PolicyStatus.self, from: $0) }
    }

    public func submit(_ request: PolicyRequest, timeout: TimeInterval = 2) -> PolicyResponse? {
        guard let body = try? JSONEncoder().encode(request) else { return nil }
        return call(timeout: timeout) { remote, finish in remote.submit(body, reply: finish) }
            .flatMap { try? JSONDecoder().decode(PolicyResponse.self, from: $0) }
    }

    public func installGeneration(manifest: Data, signature: Data, domains: Data,
                                  terms: Data, timeout: TimeInterval = 60) -> GenerationReply? {
        call(timeout: timeout) { remote, finish in
            remote.installGeneration(manifest: manifest, signature: signature,
                                     domains: domains, terms: terms, reply: finish)
        }.flatMap { try? JSONDecoder().decode(GenerationReply.self, from: $0) }
    }

    /// Async wrappers for the app's main actor, which must never block on XPC.
    public func status() async -> PolicyStatus? {
        await withCheckedContinuation { c in
            DispatchQueue.global(qos: .utility).async { c.resume(returning: self.status()) }
        }
    }

    /// The install waits up to a minute on XPC; off the cooperative pool.
    public func installGeneration(manifest: Data, signature: Data, domains: Data,
                                  terms: Data) async -> GenerationReply? {
        await withCheckedContinuation { c in
            DispatchQueue.global(qos: .utility).async {
                c.resume(returning: self.installGeneration(manifest: manifest, signature: signature,
                                                           domains: domains, terms: terms))
            }
        }
    }

    public func submit(_ request: PolicyRequest) async -> PolicyResponse? {
        await withCheckedContinuation { c in
            DispatchQueue.global(qos: .userInitiated).async {
                c.resume(returning: self.submit(request))
            }
        }
    }
}
