import XCTest
import CryptoKit
@testable import Hisn

/// The decision contract — the Mac's half of a two-language agreement.
///
/// `blocklist/terms/policy_cases.json` says what a hostname decision must be for a
/// given mode, allowlist, custom-block list and published-list membership.
/// `extension/test/policy.test.js` asserts every case against the rules the
/// browser hands to Chrome; this file asserts every case against
/// `BlocklistStore`, the way `FilterDataProvider.handleNewFlow` asks it:
/// `isAllowedInStrictMode` in strict mode, `isBlocked` otherwise.
///
/// Before the fixture existed the two clients disagreed silently — a domain on
/// both hand lists was reachable in Chrome and dropped by the Mac, and the
/// allowlist overrode the published list on the Mac but not in Chrome. If they
/// ever diverge again, one of the two suites fails and names the case.
final class PolicyContractTests: XCTestCase {

    private struct Case: Decodable {
        let host: String
        let mode: String
        let allowlist: [String]
        let customBlocks: [String]
        let listed: Bool
        let expect: String
        let why: String
        /// The browser distinguishes resource types and the page that made the
        /// request; the filter sees a socket to a host and nothing else. Cases
        /// carrying these still describe a flow to `host`, judged the same way.
        let resourceType: String?
        let initiator: String?
        /// The browser's plumbing carve-out (rule 6): an allowlisted page may
        /// load scripts, styles and images from anywhere. The filter has no
        /// such carve-out — it is stricter — so these cases are the browser's
        /// alone, and the divergence is deliberate and documented.
        let browserOnly: Bool?
    }
    private struct Fixture: Decodable { let cases: [Case] }

    private func fixture() throws -> [Case] {
        let bundle = Bundle(for: type(of: self))
        let url = try XCTUnwrap(bundle.url(forResource: "policy_cases",
                                           withExtension: "json"),
                                "policy_cases.json is not in the test bundle — "
                                + "check TEST_RESOURCES in generate_xcodeproj.py")
        return try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: url)).cases
    }

    /// A store holding exactly `domains` as its published list, signed with a
    /// throwaway key. The manifest claims a plausible count because the store
    /// refuses a list that looks collapsed; what is actually installed is what
    /// the case says is listed.
    private func store(listing domains: [String]) throws -> BlocklistStore {
        let key = Curve25519.Signing.PrivateKey()
        let hex = { (b: Data) in b.map { String(format: "%02x", $0) }.joined() }
        let store = BlocklistStore(publicKeyHex: hex(key.publicKey.rawRepresentation))
        let data = Data((domains.joined(separator: "\n") + "\n").utf8)
        let sha = hex(Data(SHA256.hash(data: data)))
        let manifest = Data("""
        {"schema":1,"version":1,"built_at":"2026-01-01T00:00:00+00:00",\
        "domain_count":200000,"core_domain_count":200000,\
        "artifacts":{"domains.packed":{"sha256":"\(sha)","bytes":\(data.count)}}}
        """.utf8)
        try store.load(manifestData: manifest,
                       signatureHex: hex(try key.signature(for: manifest)),
                       domainsData: data)
        return store
    }

    /// The host's own name is what the case lists; for a subdomain case the
    /// fixture says the APEX is listed, which is what `listed` models — a
    /// published entry that covers the host through the parent walk.
    private func listedEntry(for host: String) -> String {
        let labels = host.lowercased()
            .trimmingCharacters(in: CharacterSet(charactersIn: "."))
            .split(separator: ".")
        return labels.suffix(2).joined(separator: ".")
    }

    func testEveryCaseMatchesTheBrowser() throws {
        let cases = try fixture()
        XCTAssertGreaterThanOrEqual(cases.count, 20, "fixture lost its cases")

        var browserOnly = 0
        for c in cases {
            if c.browserOnly == true { browserOnly += 1; continue }
            let s = try store(listing: c.listed ? [listedEntry(for: c.host)] : [])
            s.setAllowlist(c.allowlist)
            s.setCustomBlocks(c.customBlocks)

            let blocked: Bool
            switch c.mode {
            case "strict":    blocked = !s.isAllowedInStrictMode(host: c.host)
            case "blocklist": blocked = s.isBlocked(host: c.host)
            default:          XCTFail("unknown mode \(c.mode)"); continue
            }
            XCTAssertEqual(blocked ? "block" : "allow", c.expect,
                           "\(c.host) \(c.resourceType ?? "flow") (\(c.mode)): \(c.why)")
        }
        XCTAssertGreaterThan(browserOnly, 0,
                             "the fixture should carry the browser-only carve-out cases")
    }

    /// The filter is at least as strict as the browser on every case the two
    /// share, and strictly stricter on the carve-out: a flow the browser
    /// allows as an allowlisted page's plumbing is still dropped here, because
    /// a socket carries no initiator.
    func testFilterIsNeverLooserThanTheBrowser() throws {
        for c in try fixture() where c.browserOnly == true {
            let s = try store(listing: [])
            s.setAllowlist(c.allowlist)
            s.setCustomBlocks(c.customBlocks)
            XCTAssertFalse(s.isAllowedInStrictMode(host: c.host),
                           "\(c.host): the filter must drop what the browser only allows as plumbing")
        }
    }
}
