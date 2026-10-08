import Foundation
import XCTest
@testable import Hisn

final class NetworkDNSProbeTests: XCTestCase {
    private let positiveA = NetworkDNSProbe.RecordReply.addresses(["93.184.215.14"])
    private let positiveAAAA = NetworkDNSProbe.RecordReply.addresses(["2606:4700:4700::1111"])

    func testNetworkFingerprintPreservesPropertyListTypesAndIgnoresDictionaryOrder() {
        let first: [String: Any] = ["dns": ["servers": ["1.1.1.3"], "data": Data([0, 1])],
                                   "updated": Date(timeIntervalSince1970: 100)]
        let reordered: [String: Any] = ["updated": Date(timeIntervalSince1970: 100),
                                       "dns": ["data": Data([0, 1]), "servers": ["1.1.1.3"]]]
        XCTAssertNotNil(NetworkDNSProbe.fingerprint(first))
        XCTAssertEqual(NetworkDNSProbe.fingerprint(first), NetworkDNSProbe.fingerprint(reordered))
        XCTAssertNotEqual(NetworkDNSProbe.fingerprint(["value": Data([0, 1])]),
                          NetworkDNSProbe.fingerprint(["value": "AAE="]))
        XCTAssertNil(NetworkDNSProbe.fingerprint([:]))
        XCTAssertNil(NetworkDNSProbe.fingerprint(["unknown": NSObject()]))
    }

    func testNetworkChangesOrUnavailableMetadataInvalidateEveryVerdict() {
        for evidence in [NetworkDNSProbe.NetworkEvidence.changed, .unavailable] {
            for type in NetworkDNSProbe.RecordType.allCases {
                for reply in [null(type), positive(type), .timedOut] {
                    let sample = NetworkDNSProbe.classify(recordType: type, adult: reply,
                        control: positive(type), networkEvidence: evidence)
                    XCTAssertEqual(sample.verdict, .inconclusive)
                    XCTAssertEqual(sample.reason, evidence == .changed ? .networkChanged : .networkUnverified)
                }
            }
        }
    }

    func testChangingNetworkDiscardsAnOtherwiseSuccessfulSample() async throws {
        let script = HostsScript(["wifi-before", "vpn-after"])
        let probe = NetworkDNSProbe(resolver: { name, type in
            if name == NetworkDNSProbe.adultTestName {
                return .addresses([type == .a ? "0.0.0.0" : "::"])
            }
            return .addresses([type == .a ? "93.184.215.14" : "2606:4700:4700::1111"])
        }, readHosts: { "" }, readNetwork: { script.read() })
        let result = try await probe.check()
        XCTAssertEqual(result.networkEvidence, .changed)
        XCTAssertEqual(result.a.reason, .networkChanged)
        XCTAssertEqual(result.aaaa.verdict, .inconclusive)
    }

    func testMissingNetworkMetadataCannotValidateFiltering() async throws {
        let probe = NetworkDNSProbe(resolver: { _, type in
            .addresses([type == .a ? "0.0.0.0" : "::"])
        }, readHosts: { "" }, readNetwork: { nil })
        let result = try await probe.check()
        XCTAssertEqual(result.networkEvidence, .unavailable)
        XCTAssertEqual(result.a.reason, .networkUnverified)
        XCTAssertEqual(result.aaaa.reason, .networkUnverified)
    }

    func testOnlyNullAWithPositiveAControlCountsAsObservedFiltering() {
        let result = NetworkDNSProbe.classify(recordType: .a,
                                             adult: .addresses(["0.0.0.0"]), control: positiveA)
        XCTAssertEqual(result.verdict, .filteringObserved)
        XCTAssertEqual(result.reason, .nullAnswerWithPositiveControl)
        XCTAssertEqual(result.recordType, .a)
    }

    func testNullAAAAIsOnlyAnObservedSampleNotIPv6EnforcementProof() {
        let result = NetworkDNSProbe.classify(recordType: .aaaa,
                                             adult: .addresses(["0:0:0:0:0:0:0:0"]),
                                             control: positiveAAAA)
        XCTAssertEqual(result.verdict, .nullReplyObserved)
        XCTAssertNotEqual(result.verdict, .filteringObserved)
    }

    func testNoRecordAndNXDomainStyleOutcomesNeverCountAsFiltering() {
        for type in NetworkDNSProbe.RecordType.allCases {
            XCTAssertEqual(NetworkDNSProbe.classify(recordType: type, adult: .noRecord,
                                                    control: positive(type)).verdict,
                           .inconclusive)
            XCTAssertEqual(NetworkDNSProbe.classify(recordType: type, adult: null(type),
                                                    control: .noRecord).verdict,
                           .inconclusive)
        }
    }

    func testUnavailableAnswersNeverCountAsFiltering() {
        let unavailable: [NetworkDNSProbe.RecordReply] = [
            .timedOut, .failed(-65563), .malformedReply, .addresses([])
        ]
        for reply in unavailable {
            XCTAssertEqual(NetworkDNSProbe.classify(recordType: .a, adult: reply,
                                                    control: positiveA).verdict, .inconclusive)
            XCTAssertEqual(NetworkDNSProbe.classify(recordType: .a, adult: .addresses(["0.0.0.0"]),
                                                    control: reply).verdict, .inconclusive)
        }
    }

    func testMixedNullAndPositiveAnswersNeverCountAsBlocked() {
        let a = NetworkDNSProbe.classify(recordType: .a,
                                        adult: .addresses(["0.0.0.0", "93.184.215.14"]),
                                        control: positiveA)
        let aaaa = NetworkDNSProbe.classify(recordType: .aaaa,
                                           adult: .addresses(["::", "2606:4700:4700::1111"]),
                                           control: positiveAAAA)
        XCTAssertEqual(a.verdict, .notFiltered)
        XCTAssertEqual(aaaa.verdict, .notFiltered)
    }

    func testPositiveAdultAnswerStillReportsNotFilteredWhenControlFailed() {
        let result = NetworkDNSProbe.classify(recordType: .a, adult: positiveA, control: .timedOut)
        XCTAssertEqual(result.verdict, .notFiltered)
        XCTAssertEqual(result.reason, .positiveTestAnswer)
    }

    func testLocalAndSpecialAdultAddressesRemainInconclusiveIncludingMixedNullAnswers() {
        for value in ["127.0.0.1", "10.0.0.1", "169.254.1.1", "192.168.1.1",
                      "100.64.0.1", "192.0.2.1", "198.18.1.1", "203.0.113.1"] {
            for values in [[value], ["0.0.0.0", value]] {
                let result = NetworkDNSProbe.classify(recordType: .a, adult: .addresses(values),
                                                      control: positiveA)
                XCTAssertEqual(result.verdict, .inconclusive, values.joined(separator: ","))
                XCTAssertEqual(result.reason, .unexpectedAddress)
            }
        }
        for value in ["::1", "::2", "fc00::1", "fe80::1", "fec0::1", "ff02::1", "2001:db8::1"] {
            let result = NetworkDNSProbe.classify(recordType: .aaaa,
                                                  adult: .addresses(["::", value]),
                                                  control: positiveAAAA)
            XCTAssertEqual(result.verdict, .inconclusive, value)
        }
    }

    func testNullAndLocalPositiveControlsCannotValidateAFilteringSample() {
        for value in ["0.0.0.0", "127.0.0.1", "10.0.0.1", "169.254.1.1", "192.168.1.1",
                      "100.64.0.1", "198.18.0.1"] {
            XCTAssertEqual(NetworkDNSProbe.classify(recordType: .a, adult: null(.a),
                                                    control: .addresses([value])).verdict,
                           .inconclusive, value)
        }
        for value in ["::", "::1", "::2", "fc00::1", "fe80::1", "fec0::1", "ff02::1",
                      "::ffff:127.0.0.1", "2001:db8::1"] {
            XCTAssertEqual(NetworkDNSProbe.classify(recordType: .aaaa, adult: null(.aaaa),
                                                    control: .addresses([value])).verdict,
                           .inconclusive, value)
        }
        XCTAssertEqual(NetworkDNSProbe.classify(recordType: .a, adult: null(.a),
                                                control: .addresses(["0.0.0.0", "93.184.215.14"])).verdict,
                       .inconclusive)
    }

    func testMalformedAndWrongFamilyAddressesCannotValidateFiltering() {
        for value in ["", "not-an-address", "0.0.0.0.example.com", "::", "0.0.0", "0.0.0.0\0suffix"] {
            XCTAssertEqual(NetworkDNSProbe.classify(recordType: .a, adult: .addresses([value]),
                                                    control: positiveA).verdict, .inconclusive)
        }
        XCTAssertEqual(NetworkDNSProbe.classify(recordType: .aaaa, adult: .addresses(["0.0.0.0"]),
                                                control: positiveAAAA).verdict, .inconclusive)
        XCTAssertEqual(NetworkDNSProbe.classify(recordType: .a, adult: null(.a),
                                                control: positiveAAAA).verdict, .inconclusive)
    }

    func testHostsParserRecognizesAliasesCaseAndTrailingRootDots() {
        for text in [
            "0.0.0.0 nudity.testcategory.com\n",
            "::1 other-alias NUDITY.TESTCATEGORY.COM. # comment\n",
            "127.0.0.1 localhost\texample.COM.\n",
            "::\texample.com\tanother.example\r\n"
        ] {
            XCTAssertEqual(NetworkDNSProbe.hostsEvidence(in: text), .overridden, text)
        }
    }

    func testCommentedHostsNamesAndSimilarNamesAreNotAliases() {
        let text = """
        # 0.0.0.0 nudity.testcategory.com
        127.0.0.1 localhost # example.com
        0.0.0.0 other.nudity.testcategory.com nudity.testcategory.com.example
        ::1 subdomain.example.com
        """
        XCTAssertEqual(NetworkDNSProbe.hostsEvidence(in: text), .clear)
    }

    func testLargeGeneratedHostsFileStaysClearAndLateTargetAliasIsDetected() async throws {
        let generated = "127.0.0.1 localhost\n" + (0..<12_000).map {
            "0.0.0.0 blocked-\($0).example.invalid\n"
        }.joined()
        XCTAssertGreaterThan(generated.utf8.count, 128 * 1024)
        XCTAssertEqual(NetworkDNSProbe.hostsEvidence(in: generated), .clear)
        let clearResult = try await makeProbe(readHosts: { generated }).check()
        XCTAssertEqual(clearResult.hostsEvidence, .clear)
        XCTAssertEqual(clearResult.a.verdict, .filteringObserved)
        let overridden = generated + "::1 late-alias NUDITY.TESTCATEGORY.COM. # last row\n"
        XCTAssertEqual(NetworkDNSProbe.hostsEvidence(in: overridden), .overridden)
        let overriddenResult = try await makeProbe(readHosts: { overridden }).check()
        XCTAssertEqual(overriddenResult.hostsEvidence, .overridden)
        XCTAssertEqual(overriddenResult.a.verdict, .inconclusive)
    }

    func testEitherHostsAliasInvalidatesBothRecordTypeSamples() async throws {
        for name in [NetworkDNSProbe.adultTestName, NetworkDNSProbe.controlName] {
            let probe = makeProbe(readHosts: { "0.0.0.0 unrelated \(name.uppercased())." })
            let result = try await probe.check()
            XCTAssertEqual(result.hostsEvidence, .overridden)
            XCTAssertEqual(result.a.verdict, .inconclusive)
            XCTAssertEqual(result.aaaa.verdict, .inconclusive)
            XCTAssertEqual(result.a.reason, .localOverride)
        }
    }

    func testUnreadableHostsNeverProducesAnObservedFilteringVerdict() async throws {
        let probe = makeProbe(readHosts: { throw TestError.unreadable })
        let result = try await probe.check()
        XCTAssertEqual(result.hostsEvidence, .unreadable)
        XCTAssertEqual(result.a.verdict, .inconclusive)
        XCTAssertEqual(result.aaaa.verdict, .inconclusive)
        XCTAssertEqual(result.a.reason, .hostsUnverified)
    }

    func testHostsChangeDuringLookupDiscardsEvenPositiveNullSamples() async throws {
        let script = HostsScript(["127.0.0.1 localhost", "127.0.0.1 localhost\n# edited"])
        let result = try await makeProbe(readHosts: { script.read() }).check()
        XCTAssertEqual(result.hostsEvidence, .unreadable)
        XCTAssertEqual(result.a.verdict, .inconclusive)
        XCTAssertEqual(result.aaaa.verdict, .inconclusive)
    }

    func testAliasAddedDuringLookupIsReportedAsLocalOverride() async throws {
        let script = HostsScript(["127.0.0.1 localhost", "127.0.0.1 EXAMPLE.COM."])
        let result = try await makeProbe(readHosts: { script.read() }).check()
        XCTAssertEqual(result.hostsEvidence, .overridden)
        XCTAssertEqual(result.a.reason, .localOverride)
    }

    func testRequestsOnlyBothFixedNamesForBothRecordTypesAndReturnsTimestamp() async throws {
        let log = QueryLog()
        let timestamp = Date(timeIntervalSince1970: 123456)
        let probe = NetworkDNSProbe(resolver: { name, type in
            await log.append(name, type)
            return name == NetworkDNSProbe.adultTestName ? self.null(type) : self.positive(type)
        }, readHosts: { "127.0.0.1 localhost" }, now: { timestamp }, readNetwork: { "stable" })
        let result = try await probe.check()
        let queries = await log.queries
        XCTAssertEqual(queries.sorted(), [
            "example.com:A", "example.com:AAAA",
            "nudity.testcategory.com:A", "nudity.testcategory.com:AAAA"
        ].sorted())
        XCTAssertEqual(result.checkedAt, timestamp)
        XCTAssertEqual(result.hostsEvidence, .clear)
        XCTAssertEqual(result.a.adult, .addresses(["0.0.0.0"]))
        XCTAssertEqual(result.a.verdict, .filteringObserved)
        XCTAssertEqual(result.aaaa.verdict, .nullReplyObserved)
    }

    func testEachRecordTypeRetainsItsOwnFailureAndVerdict() async throws {
        let probe = NetworkDNSProbe(resolver: { name, type in
            if type == .aaaa { return .timedOut }
            return name == NetworkDNSProbe.adultTestName ? .addresses(["0.0.0.0"]) :
                .addresses(["93.184.215.14"])
        }, readHosts: { "" }, readNetwork: { "stable" })
        let result = try await probe.check()
        XCTAssertEqual(result.a.verdict, .filteringObserved)
        XCTAssertEqual(result.aaaa.verdict, .inconclusive)
        XCTAssertEqual(result.aaaa.adult, .timedOut)
        XCTAssertEqual(result.aaaa.control, .timedOut)
    }

    func testCancellingCheckCancelsAllOutstandingQueriesAndReturnsNoResult() async {
        let started = expectation(description: "all four queries started")
        started.expectedFulfillmentCount = 4
        let cancelled = QueryLog()
        let probe = NetworkDNSProbe(resolver: { name, type in
            started.fulfill()
            do {
                try await Task.sleep(nanoseconds: 60_000_000_000)
                return .addresses(["0.0.0.0"])
            } catch {
                await cancelled.append(name, type)
                throw error
            }
        }, readHosts: { "" })
        let task = Task { try await probe.check() }
        await fulfillment(of: [started], timeout: 2)
        task.cancel()
        do {
            _ = try await task.value
            XCTFail("A cancelled sample must not return a protection result")
        } catch is CancellationError {
            let cancelledQueries = await cancelled.queries
            XCTAssertEqual(cancelledQueries.count, 4)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    private func null(_ type: NetworkDNSProbe.RecordType) -> NetworkDNSProbe.RecordReply {
        .addresses([type == .a ? "0.0.0.0" : "::"])
    }

    private func positive(_ type: NetworkDNSProbe.RecordType) -> NetworkDNSProbe.RecordReply {
        type == .a ? positiveA : positiveAAAA
    }

    private func makeProbe(readHosts: @escaping @Sendable () throws -> String) -> NetworkDNSProbe {
        NetworkDNSProbe(resolver: { name, type in
            if name == NetworkDNSProbe.adultTestName {
                return .addresses([type == .a ? "0.0.0.0" : "::"])
            }
            return .addresses([type == .a ? "93.184.215.14" : "2606:4700:4700::1111"])
        }, readHosts: readHosts, readNetwork: { "stable-test-network" })
    }

    private enum TestError: Error { case unreadable }

    private final class HostsScript: @unchecked Sendable {
        private let lock = NSLock()
        private var replies: [String]

        init(_ replies: [String]) { self.replies = replies }

        func read() -> String {
            lock.lock()
            defer { lock.unlock() }
            return replies.removeFirst()
        }
    }

    private actor QueryLog {
        var queries: [String] = []

        func append(_ name: String, _ type: NetworkDNSProbe.RecordType) {
            queries.append("\(name):\(type == .a ? "A" : "AAAA")")
        }
    }
}
