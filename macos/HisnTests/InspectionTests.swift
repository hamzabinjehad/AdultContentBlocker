import XCTest
@testable import Hisn

/// The lock guard on inspection settings.
///
/// Same asymmetry the rest of the product enforces, in a new place: a check may
/// be turned ON or made stricter at any time; turning one off or lowering
/// sensitivity waits for the lock to end. The guard exists twice on purpose —
/// here for authoring, and in `guardedUpdate` in background.js for anything a
/// devtools console can post — so both need testing, and they need to agree.
final class InspectionTests: XCTestCase {

    private var namespace: String!

    override func setUp() {
        super.setUp()
        namespace = "app.hisn.tests.\(UUID().uuidString)"
        LockStore.appGroup = namespace
    }

    override func tearDown() {
        UserDefaults().removePersistentDomain(forName: namespace)
        super.tearDown()
    }

    private func store(_ s: Inspection.Settings) {
        try? Inspection.save(s, locked: false)
    }

    // MARK: - Defaults

    /// Prevents a fresh install waving everything through.
    ///
    /// `UserDefaults.bool(forKey:)` returns `false` for a missing key, so the
    /// naive read turns every check OFF on a machine that has never saved
    /// these — the exact inversion of what an absent setting should mean.
    func testAbsentSettingsDefaultToChecking() {
        let s = Inspection.read()
        XCTAssertTrue(s.text, "page-text checking defaulted to off")
        XCTAssertTrue(s.hostKeywords, "keyword checking defaulted to off")
        XCTAssertEqual(s.textSensitivity, 50)
    }

    /// Prevents a percentage outside 0–100 reaching the scorer, where the
    /// derived threshold would go negative and block every page ever loaded.
    func testSensitivityIsClamped() {
        store(Inspection.Settings(textSensitivity: 900))
        XCTAssertEqual(Inspection.read().textSensitivity, 100)
        store(Inspection.Settings(textSensitivity: -50))
        XCTAssertEqual(Inspection.read().textSensitivity, 0)
    }

    // MARK: - The lock guard

    func testUnlockedAnythingGoes() throws {
        try Inspection.save(Inspection.Settings(text: false,
                                                textSensitivity: 10,
                                                hostKeywords: false),
                            locked: false)
        let s = Inspection.read()
        XCTAssertFalse(s.text)
        XCTAssertFalse(s.hostKeywords)
        XCTAssertEqual(s.textSensitivity, 10)
    }

    /// Prevents the obvious escape: switch the check off, browse, switch it on.
    func testCannotTurnOffTextCheckingWhileLocked() {
        store(Inspection.Settings(text: true))
        XCTAssertThrowsError(
            try Inspection.save(Inspection.Settings(text: false), locked: true))
        XCTAssertTrue(Inspection.read().text,
                      "a refused save must not partially apply")
    }

    func testCannotTurnOffKeywordCheckingWhileLocked() {
        store(Inspection.Settings(hostKeywords: true))
        XCTAssertThrowsError(
            try Inspection.save(Inspection.Settings(hostKeywords: false),
                                locked: true))
        XCTAssertTrue(Inspection.read().hostKeywords)
    }

    /// The quieter escape, and the one a length- or flag-based guard misses:
    /// leave everything switched on and simply make it not catch anything.
    func testCannotLowerSensitivityWhileLocked() {
        store(Inspection.Settings(textSensitivity: 60))
        XCTAssertThrowsError(
            try Inspection.save(Inspection.Settings(textSensitivity: 10),
                                locked: true))
        XCTAssertEqual(Inspection.read().textSensitivity, 60)
    }

    /// The permitted direction. Making a lock stricter is always available —
    /// refusing this would be a bug, and a user who cannot tighten mid-lock
    /// has no way to respond to something the filter missed.
    func testCanTightenWhileLocked() throws {
        store(Inspection.Settings(text: false, textSensitivity: 30,
                                  hostKeywords: false))
        try Inspection.save(Inspection.Settings(text: true,
                                                textSensitivity: 80,
                                                hostKeywords: true),
                            locked: true)
        let s = Inspection.read()
        XCTAssertTrue(s.text)
        XCTAssertTrue(s.hostKeywords)
        XCTAssertEqual(s.textSensitivity, 80)
    }

    /// A refusal must name every reason, not just the first one it hits.
    /// Fixing one weakening and being refused again for a second is the kind of
    /// small cruelty that makes people fight the tool.
    func testRefusalNamesEveryReason() {
        store(Inspection.Settings(text: true, textSensitivity: 60,
                                  hostKeywords: true))
        do {
            try Inspection.save(Inspection.Settings(text: false,
                                                    textSensitivity: 10,
                                                    hostKeywords: false),
                                locked: true)
            XCTFail("expected a refusal")
        } catch {
            let msg = error.localizedDescription
            XCTAssertTrue(msg.contains("page-text"), msg)
            XCTAssertTrue(msg.contains("keyword"), msg)
            XCTAssertTrue(msg.contains("sensitivity"), msg)
        }
    }

    // MARK: - The bridge contract

    /// Prevents a field added to `Inspection` but forgotten in the bridge
    /// payload, which `pollNative` would then leave at the extension's own
    /// default forever — with nothing anywhere to signal the gap.
    ///
    /// The key NAMES are the contract: they must match `DEFAULT_STATE` in
    /// background.js exactly, and a rename on one side is invisible on the
    /// other until someone notices the setting no longer does anything.
    func testBridgeReplyCarriesEveryField() {
        store(Inspection.Settings(text: false, textSensitivity: 35,
                                  hostKeywords: true))
        let payload = Inspection.bridgePayload()

        XCTAssertEqual(payload["inspectText"] as? Bool, false)
        XCTAssertEqual(payload["textSensitivity"] as? Int, 35)
        XCTAssertEqual(payload["hostKeywords"] as? Bool, true)
        XCTAssertEqual(payload.count, 3,
                       "a field was added to Inspection.Settings without being "
                       + "added to bridgePayload — the extension will never "
                       + "see it. Update background.js's DEFAULT_STATE and "
                       + "pollNative's key list at the same time.")
    }
}

// MARK: - Hand-typed words and blocked apps

/// `UserBlocks` is the only place a person's own judgement enters the filter,
/// which makes it the only place a person can break their own machine. The
/// guards it carries — a length floor, a count ceiling, tighten-only while
/// locked — are the subject of these tests.
final class UserBlocksTests: XCTestCase {

    private var namespace: String!

    override func setUp() {
        super.setUp()
        namespace = "app.hisn.tests.\(UUID().uuidString)"
        LockStore.appGroup = namespace
    }

    override func tearDown() {
        UserDefaults().removePersistentDomain(forName: namespace)
        super.tearDown()
    }

    func testUnlockedAnythingGoes() throws {
        try UserBlocks.save(terms: ["gambling"], apps: ["com.example.a"], locked: false)
        try UserBlocks.save(terms: [], apps: [], locked: false)
        XCTAssertEqual(UserBlocks.terms(), [])
        XCTAssertEqual(UserBlocks.apps(), [])
    }

    func testLockedAllowsAdding() throws {
        try UserBlocks.save(terms: ["gambling"], apps: ["com.example.a"], locked: false)
        try UserBlocks.save(terms: ["gambling", "betting"],
                            apps: ["com.example.a", "com.example.b"], locked: true)
        XCTAssertEqual(UserBlocks.terms().count, 2)
        XCTAssertEqual(UserBlocks.apps().count, 2)
    }

    func testLockedRefusesRemovingAWord() throws {
        try UserBlocks.save(terms: ["gambling", "betting"], apps: [], locked: false)
        XCTAssertThrowsError(
            try UserBlocks.save(terms: ["gambling"], apps: [], locked: true))
        XCTAssertEqual(Set(UserBlocks.terms()), ["betting", "gambling"],
                       "a refused save must not partially apply")
    }

    func testLockedRefusesUnblockingAnApp() throws {
        try UserBlocks.save(terms: [], apps: ["com.example.a"], locked: false)
        XCTAssertThrowsError(
            try UserBlocks.save(terms: [], apps: [], locked: true))
        XCTAssertEqual(UserBlocks.apps(), ["com.example.a"])
    }

    /// The failure mode this exists to prevent: a three-letter word matches
    /// initials, acronyms and half of another language, and the person has no
    /// way to see why an ordinary page stopped loading.
    func testShortWordsAreRefused() {
        XCTAssertThrowsError(
            try UserBlocks.save(terms: ["sex"], apps: [], locked: false))
        let parsed = UserBlocks.parseTerms("sex\ngambling\nxx")
        XCTAssertEqual(parsed.terms, ["gambling"])
        XCTAssertEqual(parsed.tooShort.sorted(), ["sex", "xx"])
    }

    /// A phrase is judged on its longest word: `hot sex` is safe to block even
    /// though neither half would be, because it only matches together.
    func testPhrasesAreJudgedOnTheirLongestWord() {
        let parsed = UserBlocks.parseTerms("hot sex")
        XCTAssertEqual(parsed.terms, ["hot sex"])
    }

    func testTooManyWordsRefused() {
        let many = (0..<(UserBlocks.maximumTerms + 1)).map { "word\($0)" }
        XCTAssertThrowsError(
            try UserBlocks.save(terms: many, apps: [], locked: false))
    }

    /// Typed words are normalised the same way the compiled list is, so an
    /// Arabic word matches whichever way it is spelled — otherwise a person
    /// would have to type every hamza and taa-marbuta variant themselves.
    func testWordsAreNormalisedLikeTheCompiledList() {
        XCTAssertEqual(UserBlocks.normalizeTerm("إباحية"),
                       UserBlocks.normalizeTerm("اباحيه"))
        XCTAssertEqual(UserBlocks.parseTerms("  GAMBLING  ").terms, ["gambling"])
    }

    /// Apps are enforced by the filter only. Sending the list of someone's
    /// installed apps to the browser would be exposure bought for nothing.
    func testAppsAreNotSentToTheBrowser() throws {
        try UserBlocks.save(terms: ["gambling"], apps: ["com.example.a"],
                            locked: false)
        let payload = UserBlocks.bridgePayload()
        XCTAssertEqual(payload["customTerms"] as? [String], ["gambling"])
        XCTAssertNil(payload["blockedApps"],
                     "the browser cannot enforce apps and must not receive them")
    }
}

// MARK: - Live editor summaries

/// The count under each editor is computed from the SAME parser Save uses, so
/// what the person is told as they type and what happens on Save can never
/// disagree. These pin that: a dropped line is the failure the whole feature
/// exists to surface, and it must show as a problem before Save, not after.
@MainActor
final class EditorSummaryTests: XCTestCase {

    // ── domains (SiteListsSection) ────────────────────────────────────────────

    func testEmptyDomainsSummaryIsBlankAndClean() {
        let s = SiteListsSection.summarize("")
        XCTAssertEqual(s.text, "")
        XCTAssertFalse(s.hasProblem)
    }

    func testValidDomainsCountAndPluralise() {
        XCTAssertEqual(SiteListsSection.summarize("reddit.com").text, "1 domain")
        XCTAssertEqual(SiteListsSection.summarize("reddit.com\nx.com").text, "2 domains")
    }

    func testDomainsNormaliseBeforeCounting() {
        // A URL and its bare domain are one entry, not two.
        let s = SiteListsSection.summarize("https://www.reddit.com/r/x\nreddit.com")
        XCTAssertEqual(s.text, "1 domain")
        XCTAssertFalse(s.hasProblem)
    }

    func testBadDomainLineIsFlaggedBeforeSave() {
        let s = SiteListsSection.summarize("reddit.com\nnot a domain")
        XCTAssertTrue(s.hasProblem)
        XCTAssertTrue(s.text.contains("1 valid"))
        XCTAssertTrue(s.text.contains("not a domain"))
    }

    // ── words (UserBlocksSection) ─────────────────────────────────────────────

    func testValidWordsCountAndPluralise() {
        XCTAssertEqual(UserBlocksSection.summarizeWords("gambling").text, "1 word")
        XCTAssertEqual(UserBlocksSection.summarizeWords("gambling\nbetting").text,
                       "2 words")
    }

    func testShortWordFlaggedWithTheMinimum() {
        let s = UserBlocksSection.summarizeWords("gambling\nxx")
        XCTAssertTrue(s.hasProblem)
        XCTAssertTrue(s.text.contains("1 valid"))
        XCTAssertTrue(s.text.contains("too short"))
        XCTAssertTrue(s.text.contains("\(UserBlocks.minimumTermLength)"))
    }

    /// The 200-word cap is caught here, where it can be fixed, rather than as a
    /// refusal after Save.
    func testOverTheCapFlaggedBeforeSave() {
        let many = (0..<(UserBlocks.maximumTerms + 5))
            .map { "word\($0)" }.joined(separator: "\n")
        let s = UserBlocksSection.summarizeWords(many)
        XCTAssertTrue(s.hasProblem)
        XCTAssertTrue(s.text.contains("over the \(UserBlocks.maximumTerms)-word limit"))
    }
}
