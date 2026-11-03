// Portable rules: what leaves the browser, what a file is allowed to change,
// and the one direction import must never move.
//
// The important invariants:
//   * a file carries the five hand lists and nothing else — no mode, lock,
//     list version, pause count or browsing data;
//   * import only ADDS, so a more permissive export cannot undo a stricter
//     browser;
//   * while a lock runs, additions to the allowlist and the corrections wait;
//   * a malformed file is rejected whole, while individual entries that could
//     never match are counted and skipped.

import {
  FORMAT, VERSION, MAX_FILE_BYTES, MAX_CUSTOM_TERMS,
  exportDocument, exportJSON, parseDocument, buildImport, importRules,
} from "../lib/transfer.js";

let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

const base = {
  mode: "blocklist", lockUntil: 0, listVersion: 12, appPresent: false,
  customBlocks: ["example.com"], allowlist: ["github.com"],
  customTerms: ["badword"], ignoreTerms: [], textAllow: [],
  inspectText: true, textSensitivity: 50, pauseTally: { day: "2026-11-03", count: 4 },
};

// ---------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------

const doc = exportDocument(base);
check(same(Object.keys(doc).sort(),
  ["allowlist", "customBlocks", "customTerms", "ignoreTerms", "schema", "textAllow", "version"]),
  "a file carries only the format fields and the five hand lists");
check(doc.schema === FORMAT && doc.version === VERSION, "the format and version are named");
check(doc.mode === undefined && doc.lockUntil === undefined && doc.listVersion === undefined,
  "blocking mode, deadline and list generation do not travel");
check(doc.pauseTally === undefined && doc.inspectText === undefined && doc.textSensitivity === undefined,
  "pause counts and device preferences do not travel");

const messy = exportDocument({
  customBlocks: ["https://WWW.Example.com:8443/a", "example.com", "b.example"],
  allowlist: ["GitHub.com", "https://github.com/login"],
  customTerms: ["  Ticket  ", "ticket", ""],
  ignoreTerms: null,
  textAllow: ["cdn.example.com"],
});
check(same(messy.customBlocks, ["b.example", "example.com"]), "hosts are canonical, de-duplicated and sorted");
check(same(messy.allowlist, ["github.com"]), "full addresses and case collapse to one host");
check(same(messy.customTerms, ["ticket"]), "terms are normalised and de-duplicated");
check(same(messy.ignoreTerms, []), "a missing list becomes an empty list");
check(exportJSON(base).endsWith("\n"), "the downloadable text ends with a newline");

// ---------------------------------------------------------------------------
// Parse
// ---------------------------------------------------------------------------

const roundTrip = parseDocument(exportJSON(base));
check(roundTrip.ok && same(roundTrip.document.customBlocks, ["example.com"]),
  "an exported file reads back to the same lists");
check(roundTrip.skipped.hosts === 0 && roundTrip.skipped.terms === 0, "a clean file skips nothing");

for (const [label, text] of [
  ["not JSON", "{"],
  ["an array", "[]"],
  ["null", "null"],
  ["a different schema", JSON.stringify({ schema: "other", version: 1 })],
  ["a future version", JSON.stringify({ schema: FORMAT, version: 2 })],
  ["a non-array list", JSON.stringify({ schema: FORMAT, version: 1, customBlocks: "example.com" })],
  ["an oversized list", JSON.stringify({ schema: FORMAT, version: 1,
    customBlocks: Array.from({ length: 5001 }, (_, i) => `x${i}.example`) })],
]) {
  const result = parseDocument(text);
  check(!result.ok && result.reason === "invalid", `a file is rejected whole: ${label}`);
}
check(parseDocument("x".repeat(MAX_FILE_BYTES + 1)).reason === "too-large", "an oversized file is named as such");

const dirty = parseDocument(JSON.stringify({
  schema: FORMAT, version: 1,
  customBlocks: ["not a host", "localhost", "1.2.3.4", "example.com", "https://www.example.com/x"],
  customTerms: ["valid word", "abc", "", "  spaced   word  ", "x".repeat(101)],
}));
check(dirty.ok, "invalid entries do not reject a valid file");
check(same(dirty.document.customBlocks, ["example.com"]), "only the matchable host survives");
check(dirty.skipped.hosts === 3, "every invalid host is counted; a duplicate is not an error");
check(same(dirty.document.customTerms, ["spaced   word", "valid word"]),
  "valid terms survive in the scorer's normal form");
check(dirty.skipped.terms === 3, "short, empty and over-long terms are counted");

// Unknown top-level fields are ignored, never copied into a patch.
const extra = parseDocument(JSON.stringify({
  schema: FORMAT, version: 1, mode: "off", lockUntil: 1, customBlocks: ["example.com"],
}));
check(extra.ok && extra.document.mode === undefined && extra.document.lockUntil === undefined,
  "a file cannot smuggle a mode or a deadline through an unknown field");

// ---------------------------------------------------------------------------
// Merge
// ---------------------------------------------------------------------------

const merged = buildImport(parseDocument(JSON.stringify({
  schema: FORMAT, version: 1,
  customBlocks: ["new.example", "example.com"],
  allowlist: ["gitlab.com"],
  customTerms: ["anotherword"],
  ignoreTerms: ["medicalterm"],
  textAllow: ["news.example"],
})).document, base);
check(merged.ok && same(merged.patch.customBlocks.sort(), ["example.com", "new.example"]),
  "import merges blocks and keeps every existing one");
check(same(merged.patch.allowlist, ["github.com", "gitlab.com"]), "import merges the allowlist");
check(same(merged.patch.customTerms, ["anotherword", "badword"]), "import merges custom words");
check(same(merged.patch.ignoreTerms, ["medicalterm"]), "import merges corrections");
check(merged.report.addedBlocks === 1 && merged.report.addedAllows === 1
  && merged.report.addedTerms === 1 && merged.report.addedIgnore === 1
  && merged.report.addedTextAllow === 1, "the report counts what actually changed");
check(merged.patch.mode === undefined && merged.patch.lockUntil === undefined,
  "an import patch only names list fields");

const noop = buildImport(parseDocument(exportJSON(base)).document, base);
check(noop.ok && noop.patch === null && noop.report.total === 0, "re-importing the same file changes nothing");

const normalized = buildImport(parseDocument(JSON.stringify({
  schema: FORMAT, version: 1, customTerms: ["  BADWORD "], customBlocks: ["https://www.example.com/"],
})).document, base);
check(normalized.patch === null, "case, spacing and URL spelling do not create duplicates");

// The lock direction.
const locked = buildImport(parseDocument(JSON.stringify({
  schema: FORMAT, version: 1,
  customBlocks: ["new.example"], allowlist: ["gitlab.com"],
  customTerms: ["anotherword"], ignoreTerms: ["medicalterm"], textAllow: ["news.example"],
})).document, base, { locked: true });
check(same(locked.patch.customBlocks, ["example.com", "new.example"]),
  "a lock still accepts new blocks (tightening)");
check(same(locked.patch.customTerms, ["anotherword", "badword"]),
  "a lock still accepts new words (tightening)");
check(locked.patch.allowlist === undefined && locked.patch.ignoreTerms === undefined
  && locked.patch.textAllow === undefined, "a lock withholds additions that would loosen");
check(locked.report.withheld === 3, "every withheld addition is reported");
check(locked.report.addedBlocks === 1 && locked.report.addedTerms === 1, "the rest still applies");

const lockedExisting = buildImport(parseDocument(exportJSON({
  ...base, allowlist: ["github.com", "gitlab.com"],
})).document, { ...base, allowlist: ["github.com"] }, { locked: true });
check(lockedExisting.report.withheld === 1,
  "an allowlist entry the browser already holds is not 'withheld'");
check(lockedExisting.patch === null, "nothing to do is reported as nothing to do");

// The 200-word ceiling.
const room = { ...base, customTerms: Array.from({ length: MAX_CUSTOM_TERMS - 1 }, (_, i) => `term${i}`) };
const capped = buildImport(parseDocument(JSON.stringify({
  schema: FORMAT, version: 1, customTerms: ["newone", "newtwo", "newthree"],
})).document, room);
check(capped.patch.customTerms.length === MAX_CUSTOM_TERMS, "the merged list stops at the 200-word ceiling");
check(capped.report.addedTerms === 1 && capped.report.capped === 2, "words beyond the ceiling are counted, not silently dropped");

// ---------------------------------------------------------------------------
// Authority
// ---------------------------------------------------------------------------

const managed = importRules(exportJSON(base), { ...base, appPresent: true });
check(!managed.ok && managed.reason === "app-managed", "an app-managed browser refuses a file import");
check(!importRules("{", base).ok, "a malformed file never reaches the merge");
const built = importRules(exportJSON({ ...base, customBlocks: ["new.example"] }), base);
check(built.ok && built.report.total === 1 && built.skipped.hosts === 0,
  "importRules carries the parse report through");

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} transfer checks failed`);
