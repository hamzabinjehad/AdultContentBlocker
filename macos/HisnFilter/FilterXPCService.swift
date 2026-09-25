import Foundation

/// The filter's end of `HisnFilterXPC`: accepts connections from this team's
/// app and bridge only, and hands each call to the `PolicyService` or, for a
/// list generation, to the provider.
///
/// A connection from anything not signed by this team is refused before a
/// single message is read — `setCodeSigningRequirement` makes the system
/// check the peer's signature on every message, not just at connect. An
/// unsigned filter (which cannot run as a system extension anyway) accepts
/// nothing: without a team to compare against, there is no one to trust.
final class FilterXPCService: NSObject, NSXPCListenerDelegate, HisnFilterXPC {

    private let policy: PolicyService
    private let install: (Data, Data, Data, Data) -> GenerationReply
    private let requirement = FilterXPC.peerRequirement()
    private var listener: NSXPCListener?

    init(policy: PolicyService,
         install: @escaping (Data, Data, Data, Data) -> GenerationReply) {
        self.policy = policy
        self.install = install
    }

    /// Listen on the Mach service named in this extension's Info.plist.
    func start() {
        guard let info = Bundle.main.infoDictionary,
              let name = FilterXPC.machServiceName(filterInfo: info) else {
            NSLog("[Hisn] no NEMachServiceName — the app cannot reach the policy service")
            return
        }
        let l = NSXPCListener(machServiceName: name)
        l.delegate = self
        l.resume()
        listener = l
        NSLog("[Hisn] policy service listening on %@", name)
    }

    func stop() {
        listener?.invalidate()
        listener = nil
    }

    func listener(_ listener: NSXPCListener,
                  shouldAcceptNewConnection connection: NSXPCConnection) -> Bool {
        guard let requirement else {
            NSLog("[Hisn] refusing XPC connection: this filter is not team-signed")
            return false
        }
        if #available(macOS 13.0, *) {
            connection.setCodeSigningRequirement(requirement)
        } else {
            return false
        }
        connection.exportedInterface = FilterXPC.interface()
        connection.exportedObject = self
        connection.resume()
        return true
    }

    // MARK: HisnFilterXPC

    func status(reply: @escaping (Data) -> Void) {
        reply((try? JSONEncoder().encode(policy.status())) ?? Data())
    }

    func submit(_ request: Data, reply: @escaping (Data) -> Void) {
        guard let decoded = try? JSONDecoder().decode(PolicyRequest.self, from: request) else {
            let refused = PolicyResponse(accepted: false, refusal: "Malformed request.",
                                         status: policy.status())
            reply((try? JSONEncoder().encode(refused)) ?? Data())
            return
        }
        reply((try? JSONEncoder().encode(policy.handle(decoded))) ?? Data())
    }

    func installGeneration(manifest: Data, signature: Data, domains: Data,
                           terms: Data, reply: @escaping (Data) -> Void) {
        let result = install(manifest, signature, domains, terms)
        reply((try? JSONEncoder().encode(result)) ?? Data())
    }
}
