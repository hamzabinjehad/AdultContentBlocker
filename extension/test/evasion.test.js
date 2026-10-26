/**
 * URL-obfuscation rules (rules/evasion.json).
 *
 * The keyword layer matches `porn` but not `p0rn`, and `sex` as a hostname
 * label but not `s3x`. These generated rules close the cheapest substitutions
 * for terms that are already reviewed, without inventing vocabulary:
 *
 *   * `substring` aliases match anywhere in a URL (a search query, a path, a
 *     hostname) and are limited to canonicals the keyword layer already treats
 *     as unambiguous;
 *   * `host_token` aliases match a whole hostname label only, so `s3x.com`
 *     matches and `s3xeducation.com` does not.
 *
 * The manifest and the worker's baseline list must both know the ruleset, or
 * the file ships inert — the exact "looks healthy, enforces nothing" failure
 * this repository keeps guarding against. `blocklist/evasion.py --check`
 * proves the file is the byte-for-byte output of the reviewed table.
 */

import { PRIORITY_EVASION, PRIORITY_KEYWORDS, PRIORITY_HAND } from "../lib/policy.js";

const rules = JSON.parse(readFile("../rules/evasion.json"));
const manifest = JSON.parse(readFile("../manifest.json"));
const table = JSON.parse(readFile("../../blocklist/evasion_aliases.json"));
const seed = JSON.parse(readFile("../seed/terms.json"));

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

// ── it ships, enabled, on the right rung ──────────────────────────────────
check(manifest.declarative_net_request.rule_resources
        .some((r) => r.id === "evasion" && r.enabled && r.path === "rules/evasion.json"),
      "the evasion ruleset is declared and enabled from install");
check(PRIORITY_KEYWORDS < PRIORITY_EVASION && PRIORITY_EVASION < PRIORITY_HAND,
      "evasion sits above exact keyword rules and below every hand list");
// Every reviewed alias gets a document rule; a host-token alias also gets an
// embedded rule, because a leet-spelled adult host must not be able to serve a
// video or image into an otherwise ordinary page.
const documentRules = rules.filter((r) => r.action.type === "redirect");
const embeddedRules = rules.filter((r) => r.action.type === "block");
check(documentRules.length === table.substring.length + table.host_token.length,
      "one document rule per reviewed alias");
check(embeddedRules.length === table.host_token.length,
      "one embedded rule per host-token alias");
check(rules.length === table.substring.length + 2 * table.host_token.length,
      "the generated rule count is exactly those two sets");
check(rules.every((r) => r.priority === PRIORITY_EVASION),
      "every alias uses the documented priority");
check(new Set(rules.map((r) => r.id)).size === rules.length, "rule ids are unique");
check(documentRules.every((r) => r.action.redirect.extensionPath === "/blocked.html?reason=terms"),
      "a block explains itself as a term match, with no matched text in the URL");
check(documentRules.every((r) => r.condition.resourceTypes.length === 2
                  && r.condition.resourceTypes.includes("main_frame")
                  && r.condition.resourceTypes.includes("sub_frame")),
      "document rules see the top document and frames");
check(embeddedRules.every((r) => !r.condition.resourceTypes.includes("main_frame")
                  && !r.condition.resourceTypes.includes("sub_frame")
                  && r.condition.resourceTypes.includes("image")
                  && r.condition.resourceTypes.includes("media")),
      "embedded rules cover subresources but never replace the document redirect");
check(rules.every((r) => !r.condition.excludedRequestDomains?.includes("google.com")),
      "search engines stay in scope: the point is to catch the query");
check(rules.every((r) => r.condition.excludedRequestDomains?.includes("wikipedia.org")),
      "the reference-site rail the keyword layer applies is preserved");

// ── every alias is grounded in the signed vocabulary ───────────────────────
const hostTerms = new Set((seed.host_terms ?? []).map((t) => t.t));
const pageTerms = new Set((seed.terms ?? []).map((t) => t.t));
for (const category of ["substring", "host_token"]) {
  for (const entry of table[category]) {
    check(hostTerms.has(entry.canonical),
          `${entry.alias}: canonical ${entry.canonical} exists in the signed host vocabulary`);
    check(!hostTerms.has(entry.alias) && !pageTerms.has(entry.alias),
          `${entry.alias}: is not already a reviewed term`);
  }
}

// ── substring aliases match where the keyword layer would ─────────────────
const lower = (url) => url.toLowerCase();
const substr = documentRules.filter((r) => typeof r.condition.urlFilter === "string");
const substrAlias = new Set(substr.map((r) => r.condition.urlFilter));
const substrMatches = (url) => substr.some((r) => lower(url).includes(r.condition.urlFilter));
for (const alias of ["p0rn", "pr0n", "h3ntai", "henta1", "h4ntai",
                     "0nlyfans", "onlyf4ns", "0nlyf4ns"]) {
  check(substrAlias.has(alias), `substring alias present: ${alias}`);
}
for (const url of [
  "https://p0rnhub.example/",
  "https://www.google.com/search?q=p0rn",
  "https://example.com/blog/pr0n-addiction",
  "https://h3ntai.example/gallery",
  "https://onlyf4ns.example/user",
  "https://example.org/?q=0nlyf4ns",
]) {
  check(substrMatches(url), `substring alias catches ${url}`);
}
for (const url of ["https://example.com/", "https://sp0rts.example/",
                   "https://example.com/news/history"]) {
  check(!substrMatches(url), `substring aliases leave ${url}`);
}

// ── host-token aliases are whole-label only ───────────────────────────────
const token = documentRules.filter((r) => typeof r.condition.regexFilter === "string");
const tokenMatch = (url) => token.some((r) => new RegExp(r.condition.regexFilter, "i").test(url));
for (const url of [
  "https://s3x.example/",
  "https://www.s3x123.example/watch",
  "https://n0de.example/",
  "https://m1lf.example/",
  "https://c4mgirl.example/",
  "https://s3xchat.example/",
  "https://s3x.example:8443/path",
]) {
  check(tokenMatch(url), `host-token alias catches ${url}`);
}
for (const url of [
  "https://s3xeducation.example/",
  "https://nots3x.example/",
  "https://n0density.example/",
  "https://example.com/s3x",
  "https://example.com/?q=s3x",
]) {
  check(!tokenMatch(url), `host-token alias leaves ${url}`);
}

// The embedded rule list is a real coverage gain: a listed alias host cannot
// serve media into an allowed page even though the document request itself
// would never have reached the extension.
for (const entry of table.host_token) {
  const rule = embeddedRules.find((r) => new RegExp(r.condition.regexFilter, "i")
    .test(`https://${entry.alias}.example/`));
  check(rule, `embedded rule exists for ${entry.alias}`);
}
for (const entry of table.substring) {
  check(!embeddedRules.some((r) => new RegExp(r.condition.regexFilter, "i")
        .test(`https://${entry.alias}.example/`)),
        `substring alias ${entry.alias} has no embedded rule`);
}

// Every regex must compile in the engine the tests use as an RE2 stand-in.
for (const r of token) {
  try { new RegExp(r.condition.regexFilter, "i"); }
  catch (e) { check(false, `regex does not compile: ${r.condition.regexFilter} (${e})`); }
}

// ── a rule table entry and its rule agree on the alias ────────────────────
for (const entry of table.host_token) {
  check(token.some((r) => new RegExp(r.condition.regexFilter, "i")
        .test(`https://${entry.alias}.example/`)),
        `host-token rule exists for ${entry.alias}`);
}
for (const entry of table.substring) {
  check(substrAlias.has(entry.alias), `substring rule exists for ${entry.alias}`);
}

print(`  ${checks - failures}/${checks} evasion-rule checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
