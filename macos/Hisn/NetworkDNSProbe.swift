import Darwin
import Foundation
import SystemConfiguration
import dnssd

/// An on-demand sample of this Mac's system DNS answers, not a router or
/// transport-family check. mDNSResponder may answer from its existing cache.
/// No settings, DNS caches, credentials, or saved protection status are changed.
struct NetworkDNSProbe {
    static let adultTestName = "nudity.testcategory.com"
    static let controlName = "example.com"

    enum RecordType: UInt16, CaseIterable, Sendable {
        case a = 1
        case aaaa = 28

        fileprivate var addressFamily: Int32 { self == .a ? AF_INET : AF_INET6 }
        fileprivate var byteCount: Int { self == .a ? 4 : 16 }
    }

    enum RecordReply: Equatable, Sendable {
        case addresses([String])
        case noRecord
        case timedOut
        case failed(Int32)
        case malformedReply
    }

    enum HostsEvidence: Equatable, Sendable {
        case clear
        case overridden
        /// The file could not be checked, or changed during the DNS sample.
        case unreadable
    }

    enum Verdict: Equatable, Sendable {
        /// Only the documented A=0.0.0.0 response with a positive A control.
        case filteringObserved
        /// An AAAA=:: sample with a positive AAAA control; no contractual claim.
        case nullReplyObserved
        case notFiltered
        case inconclusive
    }

    enum NetworkEvidence: Equatable, Sendable {
        case stable, changed, unavailable
    }

    enum Reason: Equatable, Sendable {
        case nullAnswerWithPositiveControl
        case positiveTestAnswer
        case localOverride
        case hostsUnverified
        case testAnswerUnavailable
        case controlAnswerUnavailable
        case unexpectedAddress
        case networkChanged
        case networkUnverified
    }

    struct Sample: Equatable, Sendable {
        let recordType: RecordType
        let adult: RecordReply
        let control: RecordReply
        let verdict: Verdict
        let reason: Reason
    }

    struct Result: Equatable, Sendable {
        let checkedAt: Date
        let a: Sample
        let aaaa: Sample
        let hostsEvidence: HostsEvidence
        var networkEvidence: NetworkEvidence = .stable
    }

    typealias Resolver = @Sendable (String, RecordType) async throws -> RecordReply
    private let resolver: Resolver
    private let readHosts: @Sendable () throws -> String
    private let now: @Sendable () -> Date
    private let readNetwork: @Sendable () -> String?

    init(timeout: TimeInterval = 4,
         resolver: Resolver? = nil,
         readHosts: @escaping @Sendable () throws -> String = { try NetworkDNSProbe.readSystemHosts() },
         now: @escaping @Sendable () -> Date = { Date() },
         readNetwork: @escaping @Sendable () -> String? = { NetworkDNSProbe.networkFingerprint() }) {
        // Even an accidentally unbounded caller cannot leave a native operation
        // running indefinitely. The four queries run concurrently.
        let boundedTimeout = timeout.isFinite ? min(max(timeout, 0.1), 10) : 4
        self.resolver = resolver ?? { name, type in
            try await SystemDNSQuery(name: name, type: type, timeout: boundedTimeout).run()
        }
        self.readHosts = readHosts
        self.now = now
        self.readNetwork = readNetwork
    }

    func check() async throws -> Result {
        try Task.checkCancellation()
        let before = try? readHosts()
        let networkBefore = readNetwork()
        async let adultA = resolver(Self.adultTestName, .a)
        async let controlA = resolver(Self.controlName, .a)
        async let adultAAAA = resolver(Self.adultTestName, .aaaa)
        async let controlAAAA = resolver(Self.controlName, .aaaa)
        let replies = try await (adultA, controlA, adultAAAA, controlAAAA)
        try Task.checkCancellation()
        let after = try? readHosts()
        let networkAfter = readNetwork()
        let networkEvidence: NetworkEvidence
        if let networkBefore, let networkAfter {
            networkEvidence = networkBefore == networkAfter ? .stable : .changed
        } else {
            networkEvidence = .unavailable
        }
        let evidence: HostsEvidence
        if before.map(Self.hostsEvidence) == .overridden ||
            after.map(Self.hostsEvidence) == .overridden {
            evidence = .overridden
        } else if let before, let after, before == after {
            evidence = .clear
        } else {
            evidence = .unreadable
        }
        try Task.checkCancellation()
        return Result(checkedAt: now(),
                      a: Self.classify(recordType: .a, adult: replies.0,
                                       control: replies.1, hostsEvidence: evidence,
                                       networkEvidence: networkEvidence),
                      aaaa: Self.classify(recordType: .aaaa, adult: replies.2,
                                          control: replies.3, hostsEvidence: evidence,
                                          networkEvidence: networkEvidence),
                      hostsEvidence: evidence, networkEvidence: networkEvidence)
    }

    static func classify(recordType: RecordType, adult: RecordReply,
                         control: RecordReply, hostsEvidence: HostsEvidence = .clear,
                         networkEvidence: NetworkEvidence = .stable) -> Sample {
        func sample(_ verdict: Verdict, _ reason: Reason) -> Sample {
            Sample(recordType: recordType, adult: adult, control: control,
                   verdict: verdict, reason: reason)
        }
        switch networkEvidence {
        case .changed: return sample(.inconclusive, .networkChanged)
        case .unavailable: return sample(.inconclusive, .networkUnverified)
        case .stable: break
        }
        switch hostsEvidence {
        case .overridden: return sample(.inconclusive, .localOverride)
        case .unreadable: return sample(.inconclusive, .hostsUnverified)
        case .clear: break
        }
        guard case let .addresses(values) = adult, !values.isEmpty else {
            return sample(.inconclusive, .testAnswerUnavailable)
        }
        let parsed = values.compactMap { DNSProbeAddress($0, type: recordType) }
        guard parsed.count == values.count else {
            return sample(.inconclusive, .unexpectedAddress)
        }
        // A mixed null and positive reply must never become a blocked verdict.
        if parsed.contains(where: \.isUsablePublicAddress) {
            return sample(.notFiltered, .positiveTestAnswer)
        }
        guard parsed.allSatisfy(\.isNull) else {
            return sample(.inconclusive, .unexpectedAddress)
        }
        guard case let .addresses(controlValues) = control, !controlValues.isEmpty else {
            return sample(.inconclusive, .controlAnswerUnavailable)
        }
        let controlAddresses = controlValues.compactMap { DNSProbeAddress($0, type: recordType) }
        guard controlAddresses.count == controlValues.count,
              controlAddresses.allSatisfy(\.isUsablePublicAddress) else {
            return sample(.inconclusive, .controlAnswerUnavailable)
        }
        return sample(recordType == .a ? .filteringObserved : .nullReplyObserved,
                      .nullAnswerWithPositiveControl)
    }

    /// Check every alias, including non-primary aliases and null/loopback entries.
    /// DNS names are case-insensitive and may be written with a final root dot.
    static func hostsEvidence(in text: String) -> HostsEvidence {
        let names = Set([adultTestName, controlName])
        for line in text.split(whereSeparator: \.isNewline) {
            let content = line.split(separator: "#", maxSplits: 1,
                                     omittingEmptySubsequences: false)[0]
            let fields = content.split(whereSeparator: \.isWhitespace)
            guard fields.count >= 2 else { continue }
            for alias in fields.dropFirst() {
                var name = alias.lowercased()
                if name.hasSuffix(".") { name.removeLast() }
                if names.contains(name) { return .overridden }
            }
        }
        return .clear
    }

    private enum HostsReadError: Error { case unavailable }

    /// Local metadata only, including service DNS and tunnel interfaces. This
    /// detects changes across a sample, not browser DoH or router enforcement.
    private static func networkFingerprint() -> String? {
        guard let store = SCDynamicStoreCreate(nil, "Hisn DNS sample" as CFString, nil, nil),
              let values = SCDynamicStoreCopyMultiple(store, nil, [
                "State:/Network/Global/.*", "State:/Network/Service/.*/(DNS|IPv4|IPv6)",
                "State:/Network/Interface/.*/(IPv4|IPv6)"
              ] as CFArray) as? [String: Any] else { return nil }
        return fingerprint(values)
    }

    static func fingerprint(_ values: [String: Any]) -> String? {
        // Dynamic-store property lists can contain CFData and dates. Preserve
        // their type as well as value; do not silently drop unknown fields.
        func canonical(_ value: Any) -> Any? {
            if let dictionary = value as? [String: Any] {
                var result = [String: Any]()
                for (key, item) in dictionary {
                    guard let converted = canonical(item) else { return nil }
                    result[key] = converted
                }
                return ["dictionary": result]
            }
            if let array = value as? [Any] {
                let converted = array.compactMap(canonical)
                return converted.count == array.count ? ["array": converted] : nil
            }
            if let data = value as? Data { return ["data": data.base64EncodedString()] }
            if let date = value as? Date { return ["date": date.timeIntervalSinceReferenceDate] }
            if let string = value as? String { return ["string": string] }
            if let number = value as? NSNumber { return ["number": number] }
            return nil
        }
        guard !values.isEmpty, let normalized = canonical(values),
              JSONSerialization.isValidJSONObject(normalized),
              let data = try? JSONSerialization.data(withJSONObject: normalized, options: [.sortedKeys])
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// A malformed hosts path must not cause an unbounded read or a FIFO wait.
    private static func readSystemHosts() throws -> String {
        let descriptor = open("/etc/hosts", O_RDONLY | O_NONBLOCK | O_CLOEXEC)
        guard descriptor >= 0 else { throw HostsReadError.unavailable }
        defer { close(descriptor) }
        var attributes = stat()
        guard fstat(descriptor, &attributes) == 0,
              attributes.st_mode & S_IFMT == S_IFREG else {
            throw HostsReadError.unavailable
        }
        // Hisn's normal hosts fallback contains hundreds of thousands of rows.
        // Keep a realistic bound rather than rejecting those installations.
        let maximum = 64 * 1024 * 1024
        guard attributes.st_size <= maximum else { throw HostsReadError.unavailable }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while data.count <= maximum {
            let count = buffer.withUnsafeMutableBytes { raw in
                Darwin.read(descriptor, raw.baseAddress, min(raw.count, maximum + 1 - data.count))
            }
            if count < 0 {
                if errno == EINTR { continue }
                throw HostsReadError.unavailable
            }
            if count == 0 { break }
            data.append(contentsOf: buffer.prefix(count))
        }
        guard data.count <= maximum, let text = String(data: data, encoding: .utf8) else {
            throw HostsReadError.unavailable
        }
        return text
    }
}

private struct DNSProbeAddress {
    let bytes: [UInt8]

    init?(_ text: String, type: NetworkDNSProbe.RecordType) {
        guard !text.utf8.contains(0) else { return nil }
        var storage = [UInt8](repeating: 0, count: type.byteCount)
        let valid = storage.withUnsafeMutableBytes { raw in
            text.withCString { inet_pton(type.addressFamily, $0, raw.baseAddress) }
        }
        guard valid == 1 else { return nil }
        bytes = storage
    }

    var isNull: Bool { bytes.allSatisfy { $0 == 0 } }

    /// Conservative public-address evidence. Local, sinkhole, documentation,
    /// and common special-use addresses remain inconclusive. Resolving a name
    /// still does not prove HTTP works or reveal the DNS transport family.
    var isUsablePublicAddress: Bool {
        guard !isNull else { return false }
        if bytes.count == 4 {
            guard bytes[0] != 0 && bytes[0] != 10 && bytes[0] != 127 && bytes[0] < 224 else {
                return false
            }
            if bytes[0] == 100 && (64...127).contains(bytes[1]) { return false }
            if bytes[0] == 169 && bytes[1] == 254 { return false }
            if bytes[0] == 172 && (16...31).contains(bytes[1]) { return false }
            if bytes[0] == 192 {
                if bytes[1] == 168 { return false }
                if bytes[1] == 0 && (bytes[2] == 0 || bytes[2] == 2) { return false }
                if bytes[1] == 88 && bytes[2] == 99 { return false }
            }
            if bytes[0] == 198 {
                if bytes[1] == 18 || bytes[1] == 19 { return false }
                if bytes[1] == 51 && bytes[2] == 100 { return false }
            }
            if bytes[0] == 203 && bytes[1] == 0 && bytes[2] == 113 { return false }
            return true
        }
        // Other prefixes (including translation/mapped addresses) may work in
        // particular networks; this diagnostic deliberately leaves them open.
        guard bytes[0] & 0xe0 == 0x20 else { return false }
        if bytes[0] == 0x20 && bytes[1] == 0x01 {
            if bytes[2] == 0x0d && bytes[3] == 0xb8 { return false }
            if bytes[2] == 0x00 && bytes[3] == 0x02 { return false }
        }
        return true
    }
}

/// The continuation and native reference are owned by one serial queue.
/// Cancellation, callback completion, setup failure and timeout all use the
/// same cleanup path, including DNSServiceRefDeallocate on its scheduling queue.
private final class SystemDNSQuery: @unchecked Sendable {
    private let name: String
    private let type: NetworkDNSProbe.RecordType
    private let timeout: TimeInterval
    private let queue = DispatchQueue(label: "app.hisn.network-dns-sample")
    private var continuation: CheckedContinuation<NetworkDNSProbe.RecordReply, Error>?
    private var service: DNSServiceRef?
    private var context: UnsafeMutableRawPointer?
    private var timeoutTimer: DispatchSourceTimer?
    private var settleTimer: DispatchSourceTimer?
    private var addresses = Set<String>()
    private var cancellationRequested = false
    private var finished = false

    init(name: String, type: NetworkDNSProbe.RecordType, timeout: TimeInterval) {
        self.name = name
        self.type = type
        self.timeout = timeout
    }

    func run() async throws -> NetworkDNSProbe.RecordReply {
        try Task.checkCancellation()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                queue.async { self.start(continuation) }
            }
        }, onCancel: {
            self.queue.async {
                self.cancellationRequested = true
                if self.continuation != nil { self.finish(.failure(CancellationError())) }
            }
        })
    }

    private func start(_ continuation: CheckedContinuation<NetworkDNSProbe.RecordReply, Error>) {
        self.continuation = continuation
        if cancellationRequested {
            finish(.failure(CancellationError()))
            return
        }
        context = Unmanaged.passRetained(self).toOpaque()
        let error = DNSServiceQueryRecord(&service, DNSServiceFlags(kDNSServiceFlagsTimeout),
                                         0, name + ".", type.rawValue,
                                         UInt16(kDNSServiceClass_IN), Self.callback, context)
        guard error == kDNSServiceErr_NoError, let service else {
            finish(.success(.failed(error)))
            return
        }
        let scheduleError = DNSServiceSetDispatchQueue(service, queue)
        guard scheduleError == kDNSServiceErr_NoError else {
            finish(.success(.failed(scheduleError)))
            return
        }
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + timeout)
        timer.setEventHandler { [weak self] in self?.finish(.success(.timedOut)) }
        timeoutTimer = timer
        timer.resume()
    }

    private static let callback: DNSServiceQueryRecordReply = {
        _, flags, _, error, _, rrtype, rrclass, count, data, _, context in
        guard let context else { return }
        let operation = Unmanaged<SystemDNSQuery>.fromOpaque(context).takeUnretainedValue()
        operation.receive(flags: flags, error: error, type: rrtype, rrclass: rrclass,
                          count: count, data: data)
    }

    private func receive(flags: DNSServiceFlags, error: DNSServiceErrorType,
                         type: UInt16, rrclass: UInt16, count: UInt16,
                         data: UnsafeRawPointer?) {
        guard !finished else { return }
        if error != kDNSServiceErr_NoError {
            let reply: NetworkDNSProbe.RecordReply
            switch error {
            case Int32(kDNSServiceErr_NoSuchRecord), Int32(kDNSServiceErr_NoSuchName): reply = .noRecord
            case Int32(kDNSServiceErr_Timeout): reply = .timedOut
            default: reply = .failed(error)
            }
            finish(.success(reply))
            return
        }
        guard type == self.type.rawValue, rrclass == UInt16(kDNSServiceClass_IN),
              Int(count) == self.type.byteCount, let data else {
            finish(.success(.malformedReply))
            return
        }
        var text = [CChar](repeating: 0, count: Int(INET6_ADDRSTRLEN))
        guard inet_ntop(self.type.addressFamily, data, &text, socklen_t(text.count)) != nil else {
            finish(.success(.malformedReply))
            return
        }
        let address = String(cString: text)
        if flags & DNSServiceFlags(kDNSServiceFlagsAdd) != 0 {
            addresses.insert(address)
        } else {
            addresses.remove(address)
        }
        settleTimer?.cancel()
        settleTimer = nil
        if flags & DNSServiceFlags(kDNSServiceFlagsMoreComing) == 0 {
            // Briefly collect other already-arriving scoped answers, especially
            // a mixed null/positive set, while retaining the hard query deadline.
            let timer = DispatchSource.makeTimerSource(queue: queue)
            timer.schedule(deadline: .now() + 0.15)
            timer.setEventHandler { [weak self] in
                guard let self else { return }
                self.finish(.success(self.addresses.isEmpty ? .noRecord :
                    .addresses(self.addresses.sorted())))
            }
            settleTimer = timer
            timer.resume()
        }
    }

    private func finish(_ result: Swift.Result<NetworkDNSProbe.RecordReply, Error>) {
        guard !finished, let continuation else { return }
        finished = true
        self.continuation = nil
        timeoutTimer?.cancel()
        settleTimer?.cancel()
        timeoutTimer = nil
        settleTimer = nil
        if let service { DNSServiceRefDeallocate(service) }
        service = nil
        if let context { Unmanaged<SystemDNSQuery>.fromOpaque(context).release() }
        context = nil
        continuation.resume(with: result)
    }
}
