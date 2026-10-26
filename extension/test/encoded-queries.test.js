/**
 * Percent-encoded Arabic search queries (rules/encoded_queries.json).
 *
 * Chrome matches a URI, so an Arabic term can never appear literally in a DNR
 * keyword rule. This generated layer encodes strong single-token Arabic terms
 * from the signed vocabulary and matches them **only inside a recognised search
 * parameter**. That last clause is the whole design:
 *
 *   * `?q=سكس` on any engine is a search for the term and is blocked;
 *   * `سكس` in a path is left to the page-text layer and its negatives;
 *   * `سكسونيا` (Saxony) is a longer word and must not match;
 *   * `?x=سكس` is not a recognised search and must not match.
 *
 * RE2 has no lookahead, so the word ending is an alternation of what may
 * follow. That makes these tests the specification.
 */

const manifest = JSON.parse(readFile("../manifest.json"));
const rules = JSON.parse(readFile("../rules/encoded_queries.json"));
const seed = JSON.parse(readFile("../seed/terms.json"));
import { PRIORITY_EVASION, PRIORITY_KEYWORDS, PRIORITY_HAND } from "../lib/policy.js";

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

const ARABIC = /^[\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff]+$/u;
const strong = new Set((seed.terms ?? [])
  .filter((t) => (t.w ?? 0) >= 8 && !t.t.includes(" ") && t.t.length >= 3 && ARABIC.test(t.t))
  .map((t) => t.t));

// Reviewed exclusions: the page-text lane sees context, a query rule sees one
// word. A food or topical word that also appears in adult vocabulary must not
// block an ordinary search. The generator fails if an entry is not a strong
// signed term, so a typo cannot silently do nothing.
const excluded = new Set(readFile("../../blocklist/encoded_query_exclusions.txt")
  .split("\n").map((line) => line.split("#")[0].trim()).filter(Boolean));

check(strong.size > 0, "the signed vocabulary has explicit single-token Arabic terms");
check(excluded.size > 0, "the reviewed exclusion file has entries");
for (const term of excluded) {
  check(ARABIC.test(term) && strong.has(term),
        `exclusion ${term} is a strong single-token Arabic term`);
}

// ── it ships, enabled, on the documented rung ─────────────────────────────
check(manifest.declarative_net_request.rule_resources
        .some((r) => r.id === "encoded_queries" && r.enabled && r.path === "rules/encoded_queries.json"),
      "the encoded-query ruleset is declared and enabled from install");
check(PRIORITY_KEYWORDS < PRIORITY_EVASION && PRIORITY_EVASION < PRIORITY_HAND,
      "the rules sit above exact keyword matches and below every hand list");
check(rules.length === strong.size - excluded.size,
      `one rule per strong Arabic term after exclusions (${rules.length} vs ${strong.size - excluded.size})`);
check(rules.every((r) => r.priority === PRIORITY_EVASION),
      "every encoded-query rule uses the documented priority");
check(new Set(rules.map((r) => r.id)).size === rules.length, "rule ids are unique");
check(rules.every((r) => r.action.type === "redirect"
                  && r.action.redirect.extensionPath === "/blocked.html?reason=terms"),
      "a block explains itself as a term match, with no matched text in the URL");
check(rules.every((r) => r.condition.resourceTypes.length === 2
                  && r.condition.resourceTypes.includes("main_frame")
                  && r.condition.resourceTypes.includes("sub_frame")),
      "documents only");
check(rules.every((r) => r.condition.isUrlFilterCaseSensitive === false),
      "percent escapes may be upper- or lower-case; the rule is case-insensitive");
check(rules.every((r) => r.condition.excludedRequestDomains?.includes("wikipedia.org")),
      "the reference-site rail the keyword layer applies is preserved");

// ── the three outcomes in prose above, executed ───────────────────────────
const match = (url) => rules.some((r) => new RegExp(r.condition.regexFilter, "i").test(url));
const enc = (text) => encodeURIComponent(text);

for (const url of [
  `https://www.google.com/search?q=${enc("سكس")}`,
  `https://www.bing.com/search?q=${enc("سكس")}&form=QBLH`,
  `https://duckduckgo.com/?q=${enc("سكس")}`,
  `https://www.youtube.com/results?search_query=${enc("سكس")}`,
  `https://example.com/search?query=${enc("سكس")}`,
  `https://example.com/?q=${enc("سكس")}%20${enc("عربي")}`,
]) {
  check(match(url), `an explicit Arabic query is blocked: ${url}`);
}
check(match(`https://example.com/?q=${enc("سكس").toLowerCase()}`),
      "lower-case percent escapes are still a match");
check(!match(`https://www.google.com/search?q=${enc("سكسونيا")}`),
      "Saxony (سكسونيا) is not the explicit term");
check(!match(`https://news.example/${enc("سكس")}`),
      "a path is not a search query");
check(!match(`https://news.example/${enc("سكس")}/story`),
      "a longer path is not a search query");
check(!match(`https://example.com/?x=${enc("سكس")}`),
      "an unrecognised parameter is not a search query");
check(!match(`https://example.com/?q=hello&x=${enc("سكس")}`),
      "a term in a different parameter is not a search query");
check(!match(`https://example.com/?q=${enc("سكس")}ed`),
      "a following ASCII letter fails the word-ending boundary");
check(!match(`https://example.com/?q=${enc("سكس")}%D8%A7`),
      "a following Arabic letter fails the word-ending boundary");
for (const term of excluded) {
  check(!match(`https://www.google.com/search?q=${enc(term)}`),
        `an excluded ordinary search is not blocked: ${term}`);
}

// Every regex must compile in the engine the tests use as an RE2 stand-in.
for (const r of rules) {
  try { new RegExp(r.condition.regexFilter, "i"); }
  catch (e) { check(false, `regex does not compile: ${e}`); }
}

print(`  ${checks - failures}/${checks} encoded-query checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
