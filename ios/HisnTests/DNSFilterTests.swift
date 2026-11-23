import XCTest
@testable import HisnMobile

// MARK: - Fixtures

private func uint16(_ value: UInt16) -> [UInt8] {
    [UInt8(value >> 8), UInt8(value & 0xFF)]
}

private func makeQuery(_ name: String, id: UInt16 = 0x4A3B, type: UInt16 = 1,
                       flags: UInt16 = 0x0100, additional: Int = 0,
                       extra: (inout [UInt8]) -> Void = { _ in }) -> Data {
    var bytes: [UInt8] = uint16(id) + uint16(flags) + uint16(1) + uint16(0) + uint16(0)
        + uint16(UInt16(additional))
    for label in name.split(separator: ".") {
        bytes.append(UInt8(label.utf8.count))
        bytes.append(contentsOf: label.utf8)
    }
    bytes.append(0)
    bytes += uint16(type) + uint16(1)
    extra(&bytes)
    return Data(bytes)
}

private func ednsQuery(_ name: String, payload: UInt16) -> Data {
    makeQuery(name, additional: 1) { bytes in
        bytes.append(0) // OPT owner: root
        bytes += uint16(41) + uint16(payload) + [0, 0, 0, 0] + uint16(0)
    }
}

private func domainIndex(_ domains: [String]) throws -> DomainIndex {
    try DomainIndex(fileData: Data((domains.joined(separator: "\n") + "\n").utf8))
}

private func dnsName(_ name: String) -> [UInt8] {
    var bytes: [UInt8] = []
    for label in name.split(separator: ".") {
        bytes.append(UInt8(label.utf8.count))
        bytes.append(contentsOf: label.utf8)
    }
    bytes.append(0)
    return bytes
}

private func dnsAnswer(owner: [UInt8], type: UInt16, rdata: [UInt8]) -> [UInt8] {
    owner + uint16(type) + uint16(1) + [0, 0, 0, 60] + uint16(UInt16(rdata.count)) + rdata
}

private func dnsReply(_ question: DNSQuestion, answers: [[UInt8]]) -> Data {
    var bytes = uint16(question.id) + uint16(0x8180) + uint16(1)
        + uint16(UInt16(answers.count)) + uint16(0) + uint16(0)
    bytes += [UInt8](question.questionBytes)
    for answer in answers { bytes += answer }
    return Data(bytes)
}

final class DNSMessageTests: XCTestCase {
    func testParsesAQuestionAndTheEDNSPayloadSize() throws {
        let plain = try DNSMessage.parseQuery(makeQuery("www.Example.COM"))
        XCTAssertEqual(plain.id, 0x4A3B)
        XCTAssertEqual(plain.name, "www.example.com")
        XCTAssertEqual(plain.type, 1)
        XCTAssertEqual(plain.klass, 1)
        XCTAssertTrue(plain.recursionDesired)
        XCTAssertEqual(plain.udpPayloadSize, DNSMessage.minimumUDPSize)
        XCTAssertEqual(plain.questionBytes.count, 21)

        let large = try DNSMessage.parseQuery(ednsQuery("a.example", payload: 1232))
        XCTAssertEqual(large.udpPayloadSize, 1232)
        let huge = try DNSMessage.parseQuery(ednsQuery("a.example", payload: 9_000))
        XCTAssertEqual(huge.udpPayloadSize, DNSMessage.maximumUDPSize)
        let tiny = try DNSMessage.parseQuery(ednsQuery("a.example", payload: 100))
        XCTAssertEqual(tiny.udpPayloadSize, DNSMessage.minimumUDPSize)
    }

    func testRejectsNonQueriesAndMalformedQuestions() {
        func throwsError(_ data: Data) -> DNSMessageError? {
            do { _ = try DNSMessage.parseQuery(data); return nil }
            catch let error as DNSMessageError { return error }
            catch { return nil }
        }
        XCTAssertEqual(throwsError(Data([0, 1])), .tooShort)
        XCTAssertEqual(throwsError(makeQuery("a.example", flags: 0x8100)), .notAQuery)
        XCTAssertEqual(throwsError(makeQuery("a.example", flags: 0x0900)), .unsupportedOpcode)
        // Two questions.
        var twoQuestions = makeQuery("a.example")
        twoQuestions.replaceSubrange(4..<6, with: Data([0, 2]))
        XCTAssertEqual(throwsError(twoQuestions), .questionCount)
        // An answer record in a query.
        var answered = makeQuery("a.example")
        answered.replaceSubrange(6..<8, with: Data([0, 1]))
        XCTAssertEqual(throwsError(answered), .answerCount)
        // A compression pointer where the QNAME must be literal.
        var pointer = makeQuery("a.example")
        pointer.replaceSubrange(12..<13, with: Data([0xC0]))
        XCTAssertEqual(throwsError(pointer), .compressionPointer)
        // A label longer than 63 bytes.
        var longLabel = makeQuery("a.example")
        longLabel.replaceSubrange(12..<13, with: Data([64]))
        XCTAssertEqual(throwsError(longLabel), .invalidName)
        // Non-ASCII byte inside a label.
        var nonASCII = makeQuery("a.example")
        nonASCII.replaceSubrange(13..<14, with: Data([0xC3]))
        XCTAssertEqual(throwsError(nonASCII), .invalidName)
        // Truncated after the name.
        let short = Data(Array(makeQuery("a.example").prefix(14)))
        XCTAssertEqual(throwsError(short), .truncated)
        // Nine additional records is outside the bound.
        XCTAssertEqual(throwsError(makeQuery("a.example", additional: 9)), .additionalSection)
        // A truncated OPT record.
        let badEDNS = makeQuery("a.example", additional: 1) { bytes in
            bytes.append(0)
            bytes += uint16(41) + uint16(1232) + [0, 0, 0, 0] + uint16(4)
        }
        XCTAssertEqual(throwsError(badEDNS), .additionalSection)
    }

    func testNXDomainCopiesTheIDQuestionAndNothingElse() throws {
        let query = makeQuery("blocked.example")
        let question = try DNSMessage.parseQuery(query)
        let reply = DNSMessage.nxdomain(for: question)
        XCTAssertEqual(reply.count, 12 + question.questionBytes.count)
        XCTAssertEqual([UInt8](reply[0..<2]), [0x4A, 0x3B])
        XCTAssertEqual([UInt8](reply[2..<4]), [0x81, 0x83]) // QR, RD, RA, NXDOMAIN
        XCTAssertEqual([UInt8](reply[4..<6]), [0, 1])
        XCTAssertEqual(Data(reply[12...]), question.questionBytes)
        XCTAssertTrue(DNSMessage.isResponse(reply, to: question))
    }

    func testResponseValidationRequiresTheSameIDQuestionAndType() throws {
        let question = try DNSMessage.parseQuery(makeQuery("a.example"))
        let reply = DNSMessage.selfCheckReply(for: question)
        XCTAssertTrue(DNSMessage.isResponse(reply, to: question))

        var wrongID = reply
        wrongID.replaceSubrange(0..<2, with: Data([0, 0]))
        XCTAssertFalse(DNSMessage.isResponse(wrongID, to: question))

        let other = try DNSMessage.parseQuery(makeQuery("b.example"))
        XCTAssertFalse(DNSMessage.isResponse(reply, to: other))

        let otherType = try DNSMessage.parseQuery(makeQuery("a.example", type: 28))
        XCTAssertFalse(DNSMessage.isResponse(reply, to: otherType))

        XCTAssertFalse(DNSMessage.isResponse(makeQuery("a.example"), to: question))
        XCTAssertFalse(DNSMessage.isResponse(Data(), to: question))
    }

    func testLargeUDPRepliesBecomeATruncationRetry() throws {
        let question = try DNSMessage.parseQuery(ednsQuery("a.example", payload: 512))
        let big = Data(repeating: 0x41, count: 700)
        let limited = DNSMessage.limited(big, for: question)
        XCTAssertEqual([UInt8](limited[2..<4]), [0x83, 0x80]) // QR, TC, RD, RA
        XCTAssertEqual(Data(limited[12...]), question.questionBytes)
        XCTAssertEqual(DNSMessage.limited(Data([0, 1]), for: question), Data([0, 1]))
    }

    func testSelfCheckAnswerIsTheReservedDocumentationAddress() throws {
        let question = try DNSMessage.parseQuery(makeQuery(DNSMessage.selfCheckName))
        let reply = DNSMessage.selfCheckReply(for: question)
        XCTAssertEqual([UInt8](reply[2..<4]), [0x85, 0x80]) // QR, AA, RD, RA
        XCTAssertEqual([UInt8](reply[6..<8]), [0, 1])
        XCTAssertEqual(Array(reply.suffix(4)), DNSMessage.selfCheckAddress)
        XCTAssertEqual(DNSMessage.selfCheckAddress, [203, 0, 113, 7])

        let aaaa = try DNSMessage.parseQuery(makeQuery(DNSMessage.selfCheckName, type: 28))
        let empty = DNSMessage.selfCheckReply(for: aaaa)
        XCTAssertEqual([UInt8](empty[6..<8]), [0, 0])
        XCTAssertEqual(Data(empty[12...]), aaaa.questionBytes)
    }
}

final class DomainIndexTests: XCTestCase {
    func testListedApexCoversSubdomainsAndNothingElse() throws {
        let index = try domainIndex(["a.example.net", "b.example.net"])
        XCTAssertTrue(index.blocks(host: "a.example.net"))
        XCTAssertTrue(index.blocks(host: "www.a.example.net"))
        XCTAssertTrue(index.blocks(host: "A.EXAMPLE.NET."))
        XCTAssertFalse(index.blocks(host: ".a.example.net"))
        XCTAssertFalse(index.blocks(host: "nota.example.net"))
        XCTAssertFalse(index.blocks(host: "example.net"))
        XCTAssertFalse(index.blocks(host: "a.example.net.evil.com"))
        XCTAssertFalse(index.blocks(host: ""))
        XCTAssertFalse(index.blocks(host: "a_b.example.net"))
        XCTAssertEqual(index.count, 2)
    }

    func testPrefixLinesAreOrderedCorrectlyInTheSearch() throws {
        let index = try domainIndex(["a.example", "a.example.net", "b.example"])
        XCTAssertTrue(index.contains("a.example"))
        XCTAssertTrue(index.contains("a.example.net"))
        XCTAssertTrue(index.contains("b.example"))
        XCTAssertFalse(index.contains("a.example.ne"))
        XCTAssertFalse(index.contains("a"))
        XCTAssertEqual(index.count, 3)
    }

    func testDamagedOrAmbiguousListsAreRejected() {
        func rejects(_ raw: String) -> Bool {
            do { _ = try DomainIndex(fileData: Data(raw.utf8)); return false } catch { return true }
        }
        XCTAssertTrue(rejects(""))
        XCTAssertTrue(rejects("\n"))
        XCTAssertTrue(rejects("b.example\na.example\n"))
        XCTAssertTrue(rejects("a.example\na.example\n"))
        XCTAssertTrue(rejects("A.example\n"))
        XCTAssertTrue(rejects("a..example\n"))
        XCTAssertTrue(rejects("-a.example\n"))
        XCTAssertTrue(rejects("a-.example\n"))
        XCTAssertTrue(rejects("a.example\r\n"))
        XCTAssertTrue(rejects("single\n"))
        XCTAssertTrue(rejects("a.example\n\nb.example\n"))
        XCTAssertFalse(rejects("a.example"))
        XCTAssertFalse(rejects("a.example\n"))
    }
}

final class HostTermPolicyTests: XCTestCase {
    private func policy() throws -> HostTermPolicy {
        try HostTermPolicy(tokens: ["sex", "porn", "اباحية"],
                           substrings: ["sharmota", "3ahira"],
                           neverKeyword: ["essex", "sussex"],
                           exemptHosts: ["jins.com"])
    }

    func testWholeTokenNeverKeywordAndExemptHostRules() throws {
        let policy = try policy()
        XCTAssertTrue(policy.matches("arab-sex-tube.com"))
        XCTAssertTrue(policy.matches("sharmota123.com"))
        XCTAssertTrue(policy.matches("3ahira-arab.com"))
        XCTAssertTrue(policy.matches("essex-porn.com"))
        XCTAssertTrue(policy.matches("sex.example.com"))
        XCTAssertFalse(policy.matches("essex.gov.uk"))
        XCTAssertFalse(policy.matches("sussex.ac.uk"))
        XCTAssertFalse(policy.matches("jins.com"))
        XCTAssertFalse(policy.matches("www.jins.com"))
        XCTAssertFalse(policy.matches("example.com"))
        XCTAssertFalse(policy.matches(""))
    }

    func testArabicAndPunycodeHostsAreVisibleToTheTerms() throws {
        let policy = try policy()
        XCTAssertFalse(policy.matches("com"), "a TLD-only suffix is not an exempt site")
        XCTAssertFalse(policy.matches("jins.co.in"), "an exempt site is not every plausible name")
        XCTAssertFalse(policy.matches("example.abahiya.example"))
        XCTAssertTrue(policy.matches("xn--mgbacft8o.com"))
        XCTAssertFalse(policy.matches("xn--!!.example"))
    }

    func testPayloadValidationRejectsEmptyUnknownAndOversizedTerms() {
        func rejects(_ payload: HostTermPolicy.Payload) -> Bool {
            do { _ = try HostTermPolicy(payload: payload); return false } catch { return true }
        }
        XCTAssertTrue(rejects(HostTermPolicy.Payload(
            schema: 2, version: 1, host_terms: [], never_keyword: [],
            keyword_exempt_hosts: nil)))
        XCTAssertTrue(rejects(HostTermPolicy.Payload(
            schema: 1, version: 1,
            host_terms: [.init(t: "", kind: "token")],
            never_keyword: [], keyword_exempt_hosts: nil)))
        XCTAssertTrue(rejects(HostTermPolicy.Payload(
            schema: 1, version: 1,
            host_terms: [.init(t: "a", kind: "regex")],
            never_keyword: [], keyword_exempt_hosts: nil)))
    }
}

final class DNSReplyGuardTests: XCTestCase {
    private func question(_ name: String) throws -> DNSQuestion {
        try DNSMessage.parseQuery(makeQuery(name))
    }

    private func blocked(_ host: String) -> Bool {
        host == "listed.example" || host.hasSuffix(".listed.example") || host == "dns.google"
    }

    func testCnameToAListedNameIsBlockedAndASafeTargetIsNot() throws {
        let q = try question("first-party.example")
        let harmful = dnsReply(q, answers: [
            dnsAnswer(owner: [0xC0, 0x0C], type: 5, rdata: dnsName("listed.example")),
        ])
        XCTAssertTrue(try DNSReplyGuard.blocks(harmful, isBlocked: blocked))
        let safe = dnsReply(q, answers: [
            dnsAnswer(owner: [0xC0, 0x0C], type: 5, rdata: dnsName("safe.example")),
        ])
        XCTAssertFalse(try DNSReplyGuard.blocks(safe, isBlocked: blocked))
    }

    func testCnameChainAndDnameSuffixAreFollowed() throws {
        let q = try question("first-party.example")
        let chain = dnsReply(q, answers: [
            dnsAnswer(owner: [0xC0, 0x0C], type: 5, rdata: dnsName("middle.example")),
            dnsAnswer(owner: dnsName("middle.example"), type: 5, rdata: dnsName("listed.example")),
        ])
        XCTAssertTrue(try DNSReplyGuard.blocks(chain, isBlocked: blocked))

        let sub = try question("sub.example.com")
        let dname = dnsReply(sub, answers: [
            dnsAnswer(owner: dnsName("example.com"), type: 39, rdata: dnsName("listed.example")),
        ])
        XCTAssertTrue(try DNSReplyGuard.blocks(dname, isBlocked: blocked))
    }

    func testSvcbServiceModeEndpointIsFollowed() throws {
        let q = try question("first-party.example")
        var rdata: [UInt8] = [0, 1] // priority 1: ServiceMode
        rdata += dnsName("listed.example")
        let response = dnsReply(q, answers: [
            dnsAnswer(owner: [0xC0, 0x0C], type: 65, rdata: rdata),
        ])
        XCTAssertTrue(try DNSReplyGuard.blocks(response, isBlocked: blocked))
    }

    func testAMalformedAliasFailsClosed() throws {
        let q = try question("first-party.example")
        // A CNAME whose rdata claims one byte but contains a full name: the
        // parser must reject it rather than forward an unchecked chain.
        let malformed = dnsReply(q, answers: [
            dnsAnswer(owner: [0xC0, 0x0C], type: 5, rdata: [1, 65]),
        ])
        XCTAssertThrowsError(try DNSReplyGuard.blocks(malformed, isBlocked: blocked))
    }

    func testTheBlockedAliasUsesTheEngineRails() throws {
        let engine = try DNSFilterEngine(
            domains: domainIndex(["listed.example"]),
            hostTerms: HostTermPolicy(tokens: ["porn"], substrings: [],
                                      neverKeyword: [], exemptHosts: []))
        let q = try question("first-party.example")
        let response = dnsReply(q, answers: [
            dnsAnswer(owner: [0xC0, 0x0C], type: 5, rdata: dnsName("dns.google")),
        ])
        XCTAssertTrue(try DNSReplyGuard.blocks(response, isBlocked: {
            engine.blocksHost($0)
        }))
    }
}

final class DNSFilterEngineTests: XCTestCase {
    private func engine(custom: [String] = []) throws -> DNSFilterEngine {
        let index = try domainIndex(["adult.example", "listed.example"])
        let terms = try HostTermPolicy(tokens: ["porn", "sex"], substrings: ["sharmota"],
                                       neverKeyword: [], exemptHosts: [])
        return DNSFilterEngine(domains: index, hostTerms: terms)
    }

    private func question(_ name: String, type: UInt16 = 1) throws -> DNSQuestion {
        try DNSMessage.parseQuery(makeQuery(name, type: type))
    }

    func testEachRailIsNamedAndOrdered() throws {
        let engine = try engine()
        XCTAssertEqual(engine.decide(try question("sub.listed.example")),
                       .blocked(.list))
        XCTAssertEqual(engine.decide(try question("new.porn")), .blocked(.adultTld))
        XCTAssertEqual(engine.decide(try question("sharmota.example")),
                       .blocked(.hostTerm))
        XCTAssertEqual(engine.decide(try question("saved.example"), customSites: ["saved.example"]),
                       .blocked(.customSite))
        XCTAssertEqual(engine.decide(try question("example.com")), .allowed)
        XCTAssertEqual(engine.decide(try question("notxxx.com")), .allowed)
        XCTAssertEqual(engine.decide(try question("new.adult.example.net")), .allowed)
    }

    func testEncryptedDnsProviderNamesAreBlockedBeforeACustomAllowance() throws {
        let engine = try engine()
        // Every listed provider, including a subdomain and the Firefox canary,
        // is answered locally instead of being allowed to resolve.
        for host in ["use-application-dns.net", "dns.google", "dns64.dns.google",
                     "mozilla.cloudflare-dns.com", "family.cloudflare-dns.com",
                     "dns.nextdns.io", "abc123.dns.nextdns.io", "doh.mullvad.net",
                     "_dns.resolver.arpa"] {
            XCTAssertEqual(engine.decide(try question(host)), .blocked(.encryptedDns), host)
        }
        XCTAssertGreaterThanOrEqual(EncryptedDNSProviders.count, 35)
        // A saved allowance is not a content preference for a bypass route.
        XCTAssertEqual(engine.decide(try question("dns.google"), customSites: ["dns.google"]),
                       .blocked(.encryptedDns))
        // Parent sites, look-alike names and ordinary domains keep the normal rails.
        for host in ["google.com", "www.google.com", "cloudflare.com", "notcloudflare-dns.com",
                     "dns.google.example", "application-dns.net", "example.com"] {
            XCTAssertEqual(engine.decide(try question(host)), .allowed, host)
        }
        XCTAssertEqual(engine.decide(try question("self-check.hisn.invalid")), .selfCheck)
        XCTAssertEqual(engine.decide(try question("adult.example")), .blocked(.list))
    }

    func testTheRouteCheckNameIsAnsweredLocally() throws {
        let engine = try engine()
        XCTAssertEqual(engine.decide(try question(DNSMessage.selfCheckName)), .selfCheck)
        XCTAssertEqual(engine.decide(try question("self-check-1234.hisn.invalid")), .selfCheck)
        XCTAssertEqual(engine.decide(try question("hisn.invalid")), .selfCheck)
        XCTAssertEqual(engine.decide(try question("evil-hisn.invalid.example")), .allowed)
    }
}

final class DNSFilterAssetsTests: XCTestCase {
    private func writeFixture(_ directory: URL, count: Int? = nil,
                              adultTlds: [String] = ["adult", "porn", "sex", "xxx"]) throws {
        let domains = Data("a.example.net\nb.example.net\n".utf8)
        let terms = Data(#"{"schema":1,"version":5,"host_terms":[{"t":"2a7a","kind":"token"}],"never_keyword":[],"keyword_exempt_hosts":[]}"#.utf8)
        try domains.write(to: directory.appendingPathComponent(DNSFilterAssets.domainsName))
        try terms.write(to: directory.appendingPathComponent(DNSFilterAssets.termsName))
        let metadata: [String: Any] = [
            "schema": 1, "version": 5, "count": count ?? 2,
            "domains_sha256": DNSFilterAssets.sha256Hex(domains),
            "domains_bytes": domains.count,
            "terms_sha256": DNSFilterAssets.sha256Hex(terms),
            "terms_bytes": terms.count, "terms_version": 5,
            "adult_tlds": adultTlds,
        ]
        try JSONSerialization.data(withJSONObject: metadata)
            .write(to: directory.appendingPathComponent(DNSFilterAssets.metadataName))
    }

    private func temporaryDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-dns-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }

    func testVerifiedAssetsProduceAWorkingEngine() throws {
        let directory = try temporaryDirectory()
        try writeFixture(directory)
        let loaded = try DNSFilterAssets.load(directory: directory)
        XCTAssertEqual(loaded.engine.domains.count, 2)
        XCTAssertTrue(loaded.termsLoaded)
        XCTAssertEqual(loaded.metadata.count, 2)
        let question = try DNSMessage.parseQuery(makeQuery("sub.a.example.net"))
        XCTAssertEqual(loaded.engine.decide(question), .blocked(.list))
    }

    func testDamagedDomainDataNeverLoads() throws {
        for damage in ["digest", "count", "schema", "tlds"] {
            let directory = try temporaryDirectory()
            try writeFixture(directory, count: damage == "count" ? 3 : nil,
                             adultTlds: damage == "tlds" ? ["porn"] : ["adult", "porn", "sex", "xxx"])
            let metadataURL = directory.appendingPathComponent(DNSFilterAssets.metadataName)
            var metadata = try JSONSerialization.jsonObject(with: Data(contentsOf: metadataURL))
                as! [String: Any]
            if damage == "digest" {
                metadata["domains_sha256"] = String(repeating: "0", count: 64)
            } else if damage == "schema" {
                metadata["schema"] = 2
            }
            try JSONSerialization.data(withJSONObject: metadata).write(to: metadataURL)
            XCTAssertThrowsError(try DNSFilterAssets.load(directory: directory), damage)
        }
    }

    func testMissingOrDamagedTermsLeaveTheListEnforcing() throws {
        let directory = try temporaryDirectory()
        try writeFixture(directory)
        try FileManager.default.removeItem(
            at: directory.appendingPathComponent(DNSFilterAssets.termsName))
        let loaded = try DNSFilterAssets.load(directory: directory)
        XCTAssertFalse(loaded.termsLoaded)
        XCTAssertEqual(loaded.engine.decide(try DNSMessage.parseQuery(makeQuery("a.example.net"))),
                       .blocked(.list))

        let damaged = try temporaryDirectory()
        try writeFixture(damaged)
        try Data("{}".utf8).write(to: damaged.appendingPathComponent(DNSFilterAssets.termsName))
        XCTAssertFalse(try DNSFilterAssets.load(directory: damaged).termsLoaded)

        let missing = try temporaryDirectory()
        XCTAssertThrowsError(try DNSFilterAssets.load(directory: missing))
    }
}

// MARK: - Upstream

private typealias StubResult = Result<(status: Int, contentType: String?, body: Data), Error>
private struct StubFailure: Error {}

private final class StubTransport: DNSHTTPTransport {
    private let results: [StubResult]
    private(set) var calls = 0

    init(_ results: [StubResult]) {
        self.results = results
    }

    func post(_ request: URLRequest) async throws
        -> (status: Int, contentType: String?, body: Data) {
        let result = results[min(calls, results.count - 1)]
        calls += 1
        return try result.get()
    }
}

final class DNSUpstreamTests: XCTestCase {
    func testResolverFallsBackAndValidatesEveryReply() async throws {
        let query = makeQuery("a.example")
        let question = try DNSMessage.parseQuery(query)
        let valid = DNSMessage.selfCheckReply(for: question)
        let other = try DNSMessage.parseQuery(makeQuery("b.example"))
        let validForAnotherQuestion = DNSMessage.selfCheckReply(for: other)

        func resolve(_ results: [StubResult]) async -> (Data?, Int) {
            let transport = StubTransport(results)
            let resolver = DoHResolver(transport: transport)
            let data = await resolver.resolve(query, isValid: {
                DNSMessage.isResponse($0, to: question)
            })
            return (data, transport.calls)
        }

        let fallback = await resolve([.failure(StubFailure()),
                                      .success((200, "application/dns-message", valid))])
        XCTAssertEqual(fallback.0, valid)
        XCTAssertEqual(fallback.1, 2)

        let wrongType = await resolve([.success((200, "text/plain", valid)),
                                       .success((200, "application/dns-message; charset=binary", valid))])
        XCTAssertEqual(wrongType.0, valid)
        XCTAssertEqual(wrongType.1, 2)

        let wrongQuestion = await resolve([.success((200, "application/dns-message", validForAnotherQuestion)),
                                           .success((200, "application/dns-message", valid))])
        XCTAssertEqual(wrongQuestion.0, valid)

        let refused = await resolve([.success((500, "application/dns-message", valid)),
                                     .success((200, "application/dns-message", valid))])
        XCTAssertEqual(refused.0, valid)

        let allBad = await resolve([.success((200, "text/html", Data("<html>".utf8))),
                                    .failure(StubFailure())])
        XCTAssertNil(allBad.0)
        XCTAssertEqual(allBad.1, 2)
    }

    func testOversizedQueriesAreNeverSent() async {
        let question = try! DNSMessage.parseQuery(makeQuery("a.example"))
        let transport = StubTransport([.success((200, "application/dns-message",
                                                 DNSMessage.selfCheckReply(for: question)))])
        let resolver = DoHResolver(transport: transport)
        let huge = Data(repeating: 0, count: 4_097)
        let result = await resolver.resolve(huge, isValid: { _ in true })
        XCTAssertNil(result)
        XCTAssertEqual(transport.calls, 0)
    }
}

final class DNSRewriteTests: XCTestCase {
    private func engine() throws -> DNSFilterEngine {
        let index = try domainIndex(["adult.example", "listed.example", "my-site.example.org"])
        let terms = try HostTermPolicy(tokens: ["porn", "sex"], substrings: ["sharmota"],
                                       neverKeyword: [], exemptHosts: [])
        return DNSFilterEngine(domains: index, hostTerms: terms)
    }

    private func question(_ name: String, type: UInt16 = 1) throws -> DNSQuestion {
        try DNSMessage.parseQuery(makeQuery(name, type: type))
    }

    // MARK: Proxied names

    func testProxyLabelsAreReadBackLikeAndroid() {
        XCTAssertEqual(ProxiedHost.original("www-example-com.translate.goog"), "www.example.com")
        XCTAssertEqual(ProxiedHost.original("en-m-wikipedia-org.translate.goog"), "en.m.wikipedia.org")
        XCTAssertEqual(ProxiedHost.original("my--site-example-org.translate.goog"), "my-site.example.org")
        XCTAssertEqual(ProxiedHost.original("a--b--c-example.cdn.ampproject.org"), "a-b-c.example")
        XCTAssertEqual(ProxiedHost.original("0-xn----abc-com-0.translate.goog"), "xn--abc.com")
        XCTAssertEqual(ProxiedHost.original("0--example-com.translate.goog"), "0-example.com")
        XCTAssertEqual(ProxiedHost.original("WWW-Example-COM.Translate.goog"), "www.example.com")
        for other in ["translate.goog", "www.translate.goog.example", "a.b.translate.goog",
                      "example.translate.goog", "-.translate.goog", "cdn.ampproject.org",
                      "www.example.com", "example-com.translate.goog.evil.example",
                      "xn--bcher-de-65a.translate.goog", "a---b.translate.goog"] {
            XCTAssertNil(ProxiedHost.original(other), other)
        }
    }

    func testEveryContentRailDecidesTheProxiedCopy() throws {
        let engine = try engine()
        XCTAssertEqual(engine.decide(try question("www-listed-example.translate.goog")), .blocked(.list))
        XCTAssertEqual(engine.decide(try question("listed-example.cdn.ampproject.org")), .blocked(.list))
        XCTAssertEqual(engine.decide(try question("my--site-example-org.translate.goog")), .blocked(.list))
        XCTAssertEqual(engine.decide(try question("new-site-xxx.translate.goog")), .blocked(.adultTld))
        XCTAssertEqual(engine.decide(try question("sharmota-example.translate.goog")), .blocked(.hostTerm))
        XCTAssertEqual(engine.decide(try question("saved-example.translate.goog"),
                                     customSites: ["saved.example"]), .blocked(.customSite))
        // Ordinary translated pages and the proxies themselves stay open.
        for host in ["en-wikipedia-org.translate.goog", "translate.goog", "cdn.ampproject.org",
                     "translate.google.com"] {
            XCTAssertEqual(engine.decide(try question(host)), .allowed, host)
        }
    }

    // MARK: SafeSearch targets

    func testSearchNamesRewriteOnlyWhenChosen() {
        let standard = DNSRewriteOptions.standard
        XCTAssertEqual(SafeSearchTargets.target(for: "www.google.com", options: standard), SafeSearchTargets.google)
        XCTAssertEqual(SafeSearchTargets.target(for: "google.co.uk", options: standard), SafeSearchTargets.google)
        XCTAssertEqual(SafeSearchTargets.target(for: "www.google.com.sa.", options: standard), SafeSearchTargets.google)
        XCTAssertEqual(SafeSearchTargets.target(for: "images.google.com", options: standard), SafeSearchTargets.google)
        XCTAssertEqual(SafeSearchTargets.target(for: "WWW.BING.COM", options: standard), SafeSearchTargets.bing)
        XCTAssertEqual(SafeSearchTargets.target(for: "duckduckgo.com", options: standard), SafeSearchTargets.duckDuckGo)
        XCTAssertEqual(SafeSearchTargets.target(for: "www.ya.ru", options: standard), SafeSearchTargets.yandex)
        XCTAssertEqual(SafeSearchTargets.target(for: "pixabay.com", options: standard), SafeSearchTargets.pixabay)
        XCTAssertEqual(SafeSearchTargets.googleSuffixes.count, 187)
        // Other Google services, look-alikes and unverified names are never guessed.
        for host in ["mail.google.com", "accounts.google.com", "google.evil", "google.com.evil",
                     "notgoogle.com", "maps.google.com", "yandex.ua", "youtube-nocookie.com",
                     "forcesafesearch.google.com", "strict.bing.com"] {
            XCTAssertNil(SafeSearchTargets.target(for: host, options: standard), host)
        }
        // YouTube is its own choice, off by default.
        XCTAssertNil(SafeSearchTargets.target(for: "www.youtube.com", options: standard))
        let both = DNSRewriteOptions(safeSearch: true, youtubeRestricted: true)
        for host in ["youtube.com", "m.youtube.com", "youtubei.googleapis.com", "www.youtube-nocookie.com"] {
            XCTAssertEqual(SafeSearchTargets.target(for: host, options: both), SafeSearchTargets.youtube, host)
        }
        let youtubeOnly = DNSRewriteOptions(safeSearch: false, youtubeRestricted: true)
        XCTAssertNil(SafeSearchTargets.target(for: "www.google.com", options: youtubeOnly))
        XCTAssertEqual(SafeSearchTargets.target(for: "www.youtube.com", options: youtubeOnly), SafeSearchTargets.youtube)
        XCTAssertNil(SafeSearchTargets.target(for: "www.google.com", options: .none))
    }

    func testTheEngineRewritesAddressTypesAndNeverABlock() throws {
        let engine = try engine()
        let both = DNSRewriteOptions(safeSearch: true, youtubeRestricted: true)
        for type: UInt16 in [1, 28, 64, 65] {
            XCTAssertEqual(engine.decide(try question("www.google.com", type: type), rewrites: both),
                           .rewrite(SafeSearchTargets.google), "type \(type)")
        }
        // MX, TXT and other types carry no address, so they are forwarded.
        XCTAssertEqual(engine.decide(try question("google.com", type: 15), rewrites: both), .allowed)
        XCTAssertEqual(engine.decide(try question("google.com", type: 16), rewrites: both), .allowed)
        // A person's own block wins over a rewrite.
        XCTAssertEqual(engine.decide(try question("www.bing.com"), customSites: ["bing.com"], rewrites: both),
                       .blocked(.customSite))
        XCTAssertEqual(engine.decide(try question("www.google.com")), .allowed)
    }

    // MARK: Wire format

    func testTheRewriteReplyChainsTheQuestionToTheRestrictedAddresses() throws {
        let query = try DNSMessage.parseQuery(ednsQuery("www.google.com", payload: 1232))
        let upstream = DNSMessage.AddressRecord(ttl: 86_400, data: Data([216, 239, 38, 120]))
        let reply = try XCTUnwrap(DNSMessage.cnameReply(for: query, target: SafeSearchTargets.google,
                                                        addresses: [upstream]))
        let bytes = [UInt8](reply)
        XCTAssertEqual(Array(bytes[0..<2]), uint16(query.id))
        XCTAssertEqual(Array(bytes[4..<12]), uint16(1) + uint16(2) + uint16(0) + uint16(0))
        XCTAssertTrue(DNSMessage.isResponse(reply, to: query))
        var at = 12 + query.questionBytes.count
        XCTAssertEqual(Array(bytes[at..<(at + 6)]), [0xC0, 0x0C, 0, 5, 0, 1])
        XCTAssertEqual(Array(bytes[(at + 6)..<(at + 10)]), [0, 0, 1, 0x2C])
        let target = dnsName(SafeSearchTargets.google)
        XCTAssertEqual(Array(bytes[(at + 10)..<(at + 12)]), uint16(UInt16(target.count)))
        let targetOffset = at + 12
        XCTAssertEqual(Array(bytes[targetOffset..<(targetOffset + target.count)]), target)
        at = targetOffset + target.count
        XCTAssertEqual(Array(bytes[at..<(at + 2)]), uint16(0xC000 | UInt16(targetOffset)))
        XCTAssertEqual(Array(bytes[(at + 2)..<(at + 6)]), uint16(1) + uint16(1))
        XCTAssertEqual(Array(bytes[(at + 6)..<(at + 10)]), [0, 0, 1, 0x2C], "TTL capped at five minutes")
        XCTAssertEqual(Array(bytes[(at + 10)..<(at + 16)]), uint16(4) + [216, 239, 38, 120])
        XCTAssertEqual(bytes.count, at + 16)
        // Hisn's own guard reads the chain as ending at the restricted host.
        var seen: [String] = []
        XCTAssertFalse(try DNSReplyGuard.blocks(reply) { seen.append($0); return false })
        XCTAssertEqual(seen, ["www.google.com", SafeSearchTargets.google])
    }

    func testWithoutAddressesTheReplyIsTheCNAMEAlone() throws {
        for type: UInt16 in [1, 65] {
            let query = try question("www.bing.com", type: type)
            let extra = DNSMessage.AddressRecord(ttl: 60, data: Data([1, 2, 3, 4]))
            let reply = try XCTUnwrap(DNSMessage.cnameReply(
                for: query, target: SafeSearchTargets.bing, addresses: type == 1 ? [] : [extra]))
            let bytes = [UInt8](reply)
            XCTAssertEqual(Array(bytes[6..<8]), uint16(1), "type \(type)")
            XCTAssertEqual(bytes.count, 12 + query.questionBytes.count + 12
                + dnsName(SafeSearchTargets.bing).count)
        }
    }

    func testAddressRecordsAreReadFromTheEndOfTheTargetChain() throws {
        let lookup = try XCTUnwrap(DNSMessage.query(name: SafeSearchTargets.bing, type: 1, id: 7))
        let question = try DNSMessage.parseQuery(lookup)
        XCTAssertEqual(question.name, SafeSearchTargets.bing)
        XCTAssertTrue(question.recursionDesired)
        let edge = dnsName("strict.bing.com.edgekey.net")
        let upstream = dnsReply(question, answers: [
            dnsAnswer(owner: [0xC0, 0x0C], type: 5, rdata: edge),
            dnsAnswer(owner: edge, type: 1, rdata: [204, 79, 197, 220]),
            dnsAnswer(owner: edge, type: 28, rdata: Array(repeating: 1, count: 16)),
        ])
        XCTAssertEqual(DNSMessage.addressRecords(in: upstream, type: 1),
                       [DNSMessage.AddressRecord(ttl: 60, data: Data([204, 79, 197, 220]))])
        XCTAssertEqual(DNSMessage.addressRecords(in: upstream, type: 28).count, 1)
        // A wrong-length address or a cut reply gives nothing, never a partial list.
        let bad = dnsReply(question, answers: [dnsAnswer(owner: [0xC0, 0x0C], type: 1, rdata: [1, 2, 3])])
        XCTAssertEqual(DNSMessage.addressRecords(in: bad, type: 1), [])
        XCTAssertEqual(DNSMessage.addressRecords(in: upstream.prefix(upstream.count - 3), type: 1), [])
        XCTAssertEqual(DNSMessage.addressRecords(in: upstream, type: 15), [])
        XCTAssertNil(DNSMessage.query(name: "a..b", type: 1, id: 1))
    }

    // MARK: The saved choice

    func testTheChoiceDefaultsOnAndOnlyTightensDuringACommitment() throws {
        let suite = "hisn-rewrite-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        addTeardownBlock { defaults.removePersistentDomain(forName: suite) }
        XCTAssertEqual(DNSRewriteOptions.read(defaults: defaults), .standard)
        XCTAssertEqual(DNSRewriteOptions.read(defaults: nil), .standard)
        defaults.set(Data("damaged".utf8), forKey: DNSRewriteOptions.key)
        XCTAssertEqual(DNSRewriteOptions.read(defaults: defaults), .standard)
        DNSRewriteOptions.none.write(defaults: defaults)
        XCTAssertEqual(DNSRewriteOptions.read(defaults: defaults), .none)

        let on = DNSRewriteOptions(safeSearch: true, youtubeRestricted: true)
        XCTAssertTrue(DNSRewriteOptions.permits(current: .none, proposed: on, locked: true))
        XCTAssertFalse(DNSRewriteOptions.permits(current: on, proposed: .standard, locked: true))
        XCTAssertFalse(DNSRewriteOptions.permits(current: .standard, proposed: .none, locked: true))
        XCTAssertTrue(DNSRewriteOptions.permits(current: on, proposed: .none, locked: false))
    }

    @MainActor
    func testTheControllerSavesTheChoiceForTheExtension() throws {
        let suite = "hisn-rewrite-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        addTeardownBlock { defaults.removePersistentDomain(forName: suite) }
        let protection = ProtectionController(dnsRewriteShare: DNSRewriteShare(defaults: defaults))
        XCTAssertEqual(protection.dnsRewrites, .standard)
        let both = DNSRewriteOptions(safeSearch: true, youtubeRestricted: true)
        protection.setDNSRewrites(both)
        XCTAssertEqual(protection.dnsRewrites, both)
        XCTAssertEqual(DNSRewriteOptions.read(defaults: defaults), both)
    }
}
