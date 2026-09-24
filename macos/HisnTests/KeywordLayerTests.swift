import XCTest
@testable import Hisn

/// The Swift third of a three-language contract.
///
/// `blocklist/terms.py` (Python) and `extension/lib/normalize.js` (JavaScript)
/// implement the same normaliser and the same hostname-matching rule, and all
/// three suites assert the SAME fixture tables — `normalize_cases.json` and
/// `host_cases.json`, read here from the test bundle rather than copied, so a
/// copy cannot drift.
///
/// This generalises `test_collapse_lookup_invariant` from the domain list. The
/// failure shape is identical: builder and client live in different languages,
/// nothing at build time forces them to agree, and a disagreement is silent —
/// a term that is visibly in the signed list and simply never matches, or a
/// domain blocked on the Mac and reachable in Chrome.

private struct NormalizeCase: Decodable {
    let `in`: String
    let out: String
    let why: String
}

private struct HostCase: Decodable {
    let host: String
    let block: Bool
    let why: String
}

/// Declared at file scope because Swift forbids nesting a type inside a generic
/// function — the same constraint `PartnerService.swift`'s `Ack` works around.
private struct FixtureFile<E: Decodable>: Decodable { let cases: [E] }

private func fixture<T: Decodable>(_ name: String, _ type: [T].Type,
                                   file: StaticString = #filePath,
                                   line: UInt = #line) throws -> [T] {
    let bundle = Bundle(for: KeywordNormalizeTests.self)
    let url = try XCTUnwrap(bundle.url(forResource: name, withExtension: "json"),
                            "\(name).json is not in the test bundle — check "
                            + "TEST_RESOURCES in generate_xcodeproj.py",
                            file: file, line: line)
    return try JSONDecoder()
        .decode(FixtureFile<T>.self, from: Data(contentsOf: url)).cases
}

// MARK: - Normalisation

final class KeywordNormalizeTests: XCTestCase {

    /// Prevents a normaliser drift that makes every Arabic term unmatchable.
    ///
    /// The term list is normalised once, at build time, by the Python
    /// implementation. This one normalises the hostname at match time. If they
    /// disagree by a single fold, a signed list full of Arabic terms matches
    /// nothing at all and reports no error anywhere.
    func testNormalizeGolden() throws {
        for c in try fixture("normalize_cases", [NormalizeCase].self) {
            XCTAssertEqual(TextNormalizer.normalize(c.in), c.out,
                           "\(c.why) — input \(c.in.debugDescription)")
        }
    }

    /// Prevents a fold that changes its own output.
    ///
    /// Terms are normalised at build time and page text at match time; a
    /// non-idempotent function produces two different strings for one word.
    func testNormalizeIsIdempotent() throws {
        for c in try fixture("normalize_cases", [NormalizeCase].self) {
            let once = TextNormalizer.normalize(c.in)
            XCTAssertEqual(TextNormalizer.normalize(once), once,
                           "not idempotent: \(c.in.debugDescription)")
        }
    }

    /// Prevents digit-folding from deleting the entire romanised Arabic tier.
    ///
    /// 2/3/5/7/9 are LETTERS in Franco-Arabic. Arabic-Indic digits fold to
    /// ASCII; ASCII digits must not fold to anything, or `3ahira` becomes
    /// `ahira` and matches nothing — and that tier is the one that makes
    /// hostname matching work for Arabic-audience domains at all.
    func testFrancoArabicDigitsSurvive() {
        XCTAssertEqual(TextNormalizer.normalize("3ahira"), "3ahira")
        XCTAssertEqual(TextNormalizer.normalize("2a7a"), "2a7a")
    }

    /// Prevents the Arabic definite article from disabling every term.
    func testBoundedPrefixStripping() {
        let variants = TextNormalizer.variants(
            of: TextNormalizer.normalize("الإباحية"))
        XCTAssertTrue(variants.contains("اباحيه"),
                      "the definite article was not stripped: \(variants)")
    }

    /// Prevents unbounded stemming — which is how `جنس` starts matching
    /// `الجنسية` and blocks every Arabic passport and visa page.
    func testStrippingStaysBounded() {
        let variants = TextNormalizer.variants(
            of: TextNormalizer.normalize("الجنسية"))
        XCTAssertFalse(variants.contains("جنس"),
                       "over-stemmed into the nationality false positive")
    }

    /// Prevents both halves of the digit problem at once.
    func testTrailingDigitsAreDecorationButLeadingOnesAreNot() {
        XCTAssertTrue(TextNormalizer.variants(of: "sharmota123").contains("sharmota"))
        XCTAssertFalse(TextNormalizer.variants(of: "3ahira").contains("ahira"))
    }

    /// Prevents shouted spellings from slipping the list: "pooorn" is "porn"
    /// held down on the keyboard. Both reductions are emitted so a term's real
    /// gemination survives ("ass" from "asssss"), while a mere double is left
    /// alone. Kept identical to normalize.js and terms.py.
    func testLetterElongationCollapsesToTheBaseWord() {
        XCTAssertTrue(TextNormalizer.variants(of: "pooorn").contains("porn"))
        XCTAssertTrue(TextNormalizer.variants(
            of: TextNormalizer.normalize("نيييك")).contains("نيك"))
        XCTAssertTrue(TextNormalizer.variants(of: "asssss").contains("ass"))
        XCTAssertFalse(TextNormalizer.variants(of: "pass").contains("pas"))
    }
}

// MARK: - Punycode

final class PunycodeTests: XCTestCase {

    /// Prevents Arabic-script domains being invisible to the whole keyword
    /// layer. On the wire the label is ASCII and shares no character with any
    /// Arabic term, so without decoding the match can never happen.
    func testDecodesAnArabicLabel() {
        XCTAssertEqual(Punycode.decodeHost("xn--mgbh0fb.example"), "مثال.example")
    }

    /// Prevents one malformed label from discarding the rest of a hostname,
    /// which would turn a corrupt name into a free pass.
    func testUndecodableLabelIsKeptNotDropped() {
        XCTAssertEqual(Punycode.decodeHost("xn--!!!.example.com"),
                       "xn--!!!.example.com")
    }

    /// Prevents paying for a decoder on every flow. The overwhelming majority
    /// of hostnames contain no `xn--` at all and must return untouched.
    func testPlainHostnamesPassThroughUnchanged() {
        for host in ["apple.com", "www.example.co.uk", "a.b.c.d.example"] {
            XCTAssertEqual(Punycode.decodeHost(host), host)
        }
    }

    /// Prevents a crash on hostile input.
    ///
    /// This runs inside the network extension on the connection path, where
    /// the hostname is attacker-controlled. An arithmetic trap here does not
    /// throw — it kills the filter, which on this product means protection
    /// silently stops.
    func testMalformedInputDoesNotTrap() {
        for host in ["xn--", "xn---", "xn--zzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
                     "xn--99999999999999999999", "xn--\u{0}", "xn--a-",
                     String(repeating: "xn--a", count: 200)] {
            _ = Punycode.decodeHost(host)   // must simply return
        }
    }
}

// MARK: - Hostname matching

final class HostKeywordTests: XCTestCase {

    /// A store loaded with the committed term sources, compiled by the same
    /// Python that produces the signed artifact.
    private func loadedStore() throws -> BlocklistStore {
        let store = BlocklistStore()
        store.setHostTerms(
            tokens: ["sex", "sks", "anal", "ass", "tit", "cum", "kos", "jins",
                     "nude", "escort", "adult", "boobs", "slut"],
            substrings: ["porn", "pornhub", "xnxx", "xvideos", "hentai",
                         "sharmota", "3ahira", "ebahia", "sksarab"],
            neverKeyword: ["essex", "sussex", "middlesex", "scunthorpe",
                           "analytics", "analysis", "assets", "class",
                           "classroom", "institute", "constitution", "tasks",
                           "risks", "kosher", "therapist", "document"])
        return store
    }

    /// THE load-bearing negative test.
    ///
    /// Prevents the Scunthorpe problem. Naive substring matching on hostnames
    /// blocks a county council, two universities, Google Analytics and the
    /// asset host of half the web — and `tasks.office.com`, because the
    /// Franco-Arabic term `sks` is a substring of `tasks`. A wrong verdict here
    /// takes out an ENTIRE SITE, not one page, which makes this the highest
    /// blast radius in the feature.
    func testNeverBlocksInnocentHosts() throws {
        let store = try loadedStore()
        for host in ["essex.gov.uk", "sussex.ac.uk", "middlesex.edu",
                     "scunthorpe.gov.uk", "analytics.google.com",
                     "analysis.example.org", "assets.example.com",
                     "classroom.google.com", "institute.edu",
                     "constitution.org", "tasks.office.com",
                     "risks.example.com", "kosher-food.com", "therapist.com",
                     "apple.com", "github.com", "who.int", "icloud.com"] {
            XCTAssertFalse(store.isBlocked(host: host),
                           "\(host) would be blocked — whole-site lockout")
        }
    }

    /// Prevents the obvious over-correction to the test above.
    ///
    /// If a `never_keyword` hit exempted the whole hostname, registering
    /// `essex-porn.com` would be a complete bypass of the keyword layer.
    func testInnocentTokenDoesNotImmuniseTheRestOfTheHost() throws {
        let store = try loadedStore()
        XCTAssertFalse(store.isBlocked(host: "essex.gov.uk"))
        XCTAssertTrue(store.isBlocked(host: "essex-porn.com"))
    }

    /// The point of the whole layer: a domain nobody has listed yet.
    func testCatchesAnUnlistedDomain() throws {
        let store = try loadedStore()
        for host in ["arab-sex-tube.com", "sks-arab.net", "bestpornsite.io",
                     "xnxx-videos.co", "sharmota123.com", "3ahira-arab.com"] {
            XCTAssertTrue(store.isBlocked(host: host),
                          "\(host) slipped past the keyword layer")
        }
    }

    /// Prevents the FQDN bypass reaching the keyword layer, the same hole the
    /// domain list already closes.
    func testTrailingDotDoesNotBypass() throws {
        let store = try loadedStore()
        XCTAssertTrue(store.isBlocked(host: "pornhub.com."))
        XCTAssertTrue(store.isBlocked(host: "PORNHUB.COM"))
    }

    /// Prevents a keyword rule overriding a domain the person explicitly named
    /// as reachable.
    ///
    /// A named allowance is a more specific statement than a keyword, and it is
    /// the ONLY escape valve a keyword false positive has while a lock is
    /// running — without it, one bad term takes out a site the user needs for
    /// the whole lock period with no way to correct it.
    func testAllowlistBeatsAKeywordMatch() throws {
        let store = try loadedStore()
        XCTAssertTrue(store.isBlocked(host: "essex-porn.com"))
        store.setAllowlist(["essex-porn.com"])
        XCTAssertFalse(store.isBlocked(host: "essex-porn.com"))
    }

    /// Prevents an empty keyword list from failing closed.
    ///
    /// Unlike the strict-mode allowlist, an absent keyword list means "no
    /// keyword opinion", not "block everything" — the domain list is still
    /// doing its job. Failing closed here would take the machine offline the
    /// first time a term file failed to parse.
    func testAnEmptyKeywordListBlocksNothing() {
        let store = BlocklistStore()
        XCTAssertFalse(store.isBlocked(host: "arab-sex-tube.com"))
        XCTAssertFalse(store.isBlocked(host: "apple.com"))
    }

    /// The cross-language hostname contract, both halves, against the real
    /// committed term sources.
    ///
    /// Prevents a domain being blocked on the Mac and reachable in Chrome, or
    /// the reverse, with nothing anywhere to signal the disagreement.
    func testHostCasesGolden() throws {
        let store = try seedLoadedStore()
        for c in try fixture("host_cases", [HostCase].self) {
            XCTAssertEqual(store.isBlocked(host: c.host), c.block,
                           "\(c.host): \(c.why)")
        }
    }

    /// A store loaded from the committed `seed/terms.json` on the path the
    /// filter really takes: the bundled manifest's signature is checked with
    /// the production key, and the expected hash comes from that manifest.
    ///
    /// This used to compute the hash from the file itself, because the seed
    /// manifest predated `terms.json` and had no entry for it — which is the
    /// same reason the filter left the keyword layer OFF on every shipped
    /// build. Reading the hash from the signed manifest makes this suite fail
    /// in that state instead of documenting it. `blocklist/seed.py sync`
    /// re-cuts the bundle; `BundledSeedTests.testSeedManifestCoversTheKeywordLayer`
    /// names the failure.
    private func seedLoadedStore() throws -> BlocklistStore {
        let store = BlocklistStore()
        let (expected, data) = try BundledSeedTests.bundledTerms(store: store)
        try store.loadHostTerms(termsData: data, expectedSHA256: expected)
        return store
    }

    /// Prevents an unverified term list ever being installed.
    ///
    /// The keyword layer decides whether ENTIRE SITES are reachable, so a
    /// tampered `terms.json` is as damaging as a tampered blocklist — a single
    /// added token takes a site off the machine, and a removed one opens it.
    /// The hash argument is mandatory precisely so no caller can skip this.
    func testRefusesTermsWithAWrongHash() throws {
        let bundle = Bundle(for: HostKeywordTests.self)
        let url = try XCTUnwrap(bundle.url(forResource: "terms",
                                           withExtension: "json"))
        let store = BlocklistStore()
        XCTAssertThrowsError(
            try store.loadHostTerms(termsData: try Data(contentsOf: url),
                                    expectedSHA256: String(repeating: "00", count: 32))
        ) { error in
            guard case BlocklistStore.LoadError.hashMismatch = error else {
                return XCTFail("expected hashMismatch, got \(error)")
            }
        }
        XCTAssertFalse(store.isBlocked(host: "bestpornsite.io"),
                       "a rejected term list must not be installed")
    }
}
