import Foundation
import Network
import NetworkExtension

/// Bounds how many DNS exchanges are in flight at once. URLSession already
/// pools connections, but an unbounded burst from one app must not grow the
/// extension's memory without limit.
actor DNSQueryGate {
    private var available: Int
    private var waiters: [CheckedContinuation<Void, Never>] = []

    init(limit: Int) {
        available = max(1, limit)
    }

    func acquire() async {
        if available > 0 {
            available -= 1
            return
        }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    func release() {
        if waiters.isEmpty {
            available += 1
        } else {
            waiters.removeFirst().resume()
        }
    }
}

/// Hisn's device-wide DNS filter as a Network Extension DNS proxy.
///
/// Apple routes every system DNS question through this extension while it is
/// enabled. A question for the signed seed's core tier, an adult TLD, a
/// reviewed hostname term or one of the person's saved sites gets a local
/// NXDOMAIN; everything else is answered by Cloudflare Families over
/// authenticated DNS-over-HTTPS. Nothing is logged and no query is stored.
///
/// The extension is deliberately fail-closed at start: if the build-verified
/// domain list cannot be read, it does not start, so the app shows the layer as
/// off instead of letting it look active while it answers everything.
final class DNSProxyProvider: NEDNSProxyProvider, @unchecked Sendable {
    private var engine: DNSFilterEngine?
    private let resolver = DoHResolver()
    private let gate = DNSQueryGate(limit: 16)
    private let lock = NSLock()
    /// The provider must retain every flow it returns true for.
    private var activeFlows: [ObjectIdentifier: NEAppProxyFlow] = [:]
    /// The person's own blocked sites, re-read while the proxy runs so an edit
    /// in the app takes effect without restarting the tunnel.
    private var customSites: [String] = []
    private var customSitesReadAt = Date.distantPast
    private static let customSitesLifetime: TimeInterval = 30

    override func startProxy(options: [String: Any]?,
                             completionHandler: @escaping (Error?) -> Void) {
        do {
            let loaded = try DNSFilterAssets.load(directory: Bundle.main.bundleURL)
            engine = loaded.engine
            customSites = DNSFilterAssets.customSites(
                defaults: UserDefaults(suiteName: DNSFilterAssets.group))
            customSitesReadAt = Date()
            completionHandler(nil)
        } catch {
            engine = nil
            completionHandler(error)
        }
    }

    override func stopProxy(with reason: NEProviderStopReason,
                            completionHandler: @escaping () -> Void) {
        // `engine` is set once in startProxy and never changed afterwards, so a
        // query task can read it without a lock. Stopping closes the flows.
        lock.lock()
        let flows = Array(activeFlows.values)
        activeFlows.removeAll()
        lock.unlock()
        for flow in flows {
            flow.closeReadWithError(nil)
            flow.closeWriteWithError(nil)
        }
        completionHandler()
    }

    override func handleNewFlow(_ flow: NEAppProxyFlow) -> Bool {
        guard engine != nil else { return false }
        if let udp = flow as? NEAppProxyUDPFlow {
            retain(flow)
            open(udp)
            return true
        }
        if let tcp = flow as? NEAppProxyTCPFlow {
            retain(flow)
            open(tcp)
            return true
        }
        return false
    }

    // MARK: - UDP

    private func open(_ flow: NEAppProxyUDPFlow) {
        flow.open(withLocalEndpoint: nil) { [weak self] error in
            guard let self else { return }
            guard error == nil else { self.close(flow, error: error); return }
            self.read(flow)
        }
    }

    private func read(_ flow: NEAppProxyUDPFlow) {
        flow.readDatagrams { [weak self] datagrams, endpoints, error in
            guard let self else { return }
            guard error == nil, let datagrams, !datagrams.isEmpty else {
                self.close(flow, error: error)
                return
            }
            let endpoints = endpoints ?? []
            for (index, datagram) in datagrams.enumerated() {
                guard index < endpoints.count else { continue }
                let endpoint = endpoints[index]
                Task { [weak self] in
                    guard let self else { return }
                    await self.gate.acquire()
                    let response = await self.answer(datagram, truncate: true)
                    await self.gate.release()
                    guard let response else { return }
                    flow.writeDatagrams([response], sentBy: [endpoint]) { _ in }
                }
            }
            self.read(flow)
        }
    }

    // MARK: - TCP

    private func open(_ flow: NEAppProxyTCPFlow) {
        flow.open(withLocalEndpoint: nil) { [weak self] error in
            guard let self else { return }
            guard error == nil else { self.close(flow, error: error); return }
            self.read(flow, buffer: Data())
        }
    }

    /// DNS over TCP carries a two-byte length before each message. Frames are
    /// answered in order: a client that pipelines questions receives replies in
    /// the order it asked, which every stub resolver accepts.
    private func read(_ flow: NEAppProxyTCPFlow, buffer: Data) {
        if buffer.count >= 2 {
            let start = buffer.startIndex
            let length = Int(buffer[start]) << 8 | Int(buffer[buffer.index(after: start)])
            if length == 0 {
                close(flow, error: nil)
                return
            }
            if buffer.count >= length + 2 {
                let bodyStart = buffer.index(start, offsetBy: 2)
                let bodyEnd = buffer.index(bodyStart, offsetBy: length)
                let query = Data(buffer[bodyStart..<bodyEnd])
                let rest = Data(buffer[bodyEnd...])
                Task { [weak self] in
                    guard let self else { return }
                    await self.gate.acquire()
                    let response = await self.answer(query, truncate: false)
                    await self.gate.release()
                    guard let response, response.count <= 65_535 else {
                        self.close(flow, error: nil)
                        return
                    }
                    var framed = Data()
                    framed.append(UInt8((response.count >> 8) & 0xFF))
                    framed.append(UInt8(response.count & 0xFF))
                    framed.append(response)
                    flow.write(framed) { [weak self] error in
                        guard let self else { return }
                        if error != nil {
                            self.close(flow, error: error)
                        } else {
                            self.read(flow, buffer: rest)
                        }
                    }
                }
                return
            }
        }
        flow.readData { [weak self] data, error in
            guard let self else { return }
            guard error == nil, let data, !data.isEmpty else {
                self.close(flow, error: error)
                return
            }
            var next = buffer
            next.append(data)
            // One message is at most 65,537 bytes plus its prefix. Anything
            // larger is not a DNS frame and must not grow this buffer.
            guard next.count <= 65_539 else {
                self.close(flow, error: nil)
                return
            }
            self.read(flow, buffer: next)
        }
    }

    // MARK: - Decisions

    /// The response for one query. UDP trims to the client's advertised buffer
    /// and sets TC when a reply does not fit; TCP keeps the full body.
    private func answer(_ query: Data, truncate: Bool) async -> Data? {
        guard let engine, let question = try? DNSMessage.parseQuery(query) else {
            return nil
        }
        let response: Data
        switch engine.decide(question, customSites: currentCustomSites()) {
        case .selfCheck:
            response = DNSMessage.selfCheckReply(for: question)
        case .blocked:
            response = DNSMessage.nxdomain(for: question)
        case .allowed:
            guard let upstream = await resolver.resolve(query, isValid: {
                DNSMessage.isResponse($0, to: question)
            }) else {
                return DNSMessage.servfail(for: question)
            }
            response = upstream
        }
        return truncate ? DNSMessage.limited(response, for: question) : response
    }

    /// UserDefaults is cached per process, so read it on a short lifetime
    /// instead of on every datagram, and never let a damaged record fail a query.
    private func currentCustomSites() -> [String] {
        lock.lock()
        let fresh = Date().timeIntervalSince(customSitesReadAt) < Self.customSitesLifetime
        let cached = customSites
        lock.unlock()
        if fresh { return cached }
        let next = DNSFilterAssets.customSites(
            defaults: UserDefaults(suiteName: DNSFilterAssets.group))
        lock.lock()
        customSites = next
        customSitesReadAt = Date()
        lock.unlock()
        return next
    }

    // MARK: - Flow lifetime

    private func retain(_ flow: NEAppProxyFlow) {
        lock.lock()
        activeFlows[ObjectIdentifier(flow)] = flow
        lock.unlock()
    }

    private func close(_ flow: NEAppProxyFlow, error: Error?) {
        flow.closeReadWithError(error)
        flow.closeWriteWithError(error)
        lock.lock()
        activeFlows.removeValue(forKey: ObjectIdentifier(flow))
        lock.unlock()
    }
}
