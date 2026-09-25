import Foundation
import Security
import os

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

    /// Whether this build could reach a filter at all.
    public var isConfigured: Bool { serviceName != nil && requirement != nil }

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
        let box = OSAllocatedUnfairLock<Data?>(initialState: nil)
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
