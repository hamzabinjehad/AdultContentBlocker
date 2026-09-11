/**
 * Page-text scoring.
 *
 * The tests that matter here are the NEGATIVE ones. A missed adult page costs
 * one gap in a layer that was never going to be complete; a blocked Wikipedia
 * article costs the user's trust in the whole product, and they uninstall it.
 *
 * The fixtures below are shaped like the real thing rather than minimal: a long
 * clinical article really does use explicit vocabulary many times, and if the
 * test used a two-sentence stub the density floor would never be exercised and
 * the bug it prevents would ship.
 */

import { buildIndex, scorePage, isExempt, threshold, hostInList }
  from "../lib/score.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

const terms = JSON.parse(readFile("../seed/terms.json"));
const index = buildIndex(terms);

// ── the page this layer exists to block ────────────────────────────────────
// Short, and about nothing else. High score AND high density.
const tubeSite = {
  url: "https://example-tube.com/arab-videos",
  title: "أفلام إباحية عربية مجانا - سكس عربي",
  meta: "شاهد أفضل مقاطع سكس عربي ونيك وقصص جنسية مجانا",
  heading: "سكس عربي جديد",
  body: "أفلام إباحية ومقاطع سكس ونيك عربي. قصص جنسية ساخنة. شاهد الآن مجانا.",
};
const tube = scorePage(tubeSite, index, 50);
check(tube.block, `an Arabic tube landing page must block ` +
      `(score ${tube.score.toFixed(1)}, density ${tube.density.toFixed(1)})`);

// ── the page this layer must NOT block ─────────────────────────────────────
// A long clinical article. It uses the vocabulary, repeatedly, and legitimately.
const clinical = {
  url: "https://example.org/health/sexual-health-guide",
  title: "الصحة الجنسية - دليل شامل",
  meta: "معلومات طبية عن الصحة الجنسية والأمراض المنقولة جنسيا",
  heading: "الصحة الجنسية",
  body: ("تشمل الصحة الجنسية الوقاية من الأمراض المنقولة جنسيا والتثقيف. " +
         "يوصي الأطباء بالفحص الدوري. " +
         "التربية الجنسية جزء أساسي من المناهج المدرسية في كثير من الدول. " +
         "الجنس البشري يتكاثر بالتكاثر الجنسي، وتحدد الخلايا الجنسية جنس الجنين. " +
         "الهرمونات الجنسية تؤثر على النمو. ").repeat(40),
};
const med = scorePage(clinical, index, 50);
check(!med.block, `an Arabic clinical article must NOT block ` +
      `(score ${med.score.toFixed(1)}, density ${med.density.toFixed(1)}, ` +
      `${med.words} words)`);

// ── the density floor, tested directly ─────────────────────────────────────
// An earlier version of this file asserted `score >= threshold || density < 2`
// against the clinical fixture and called that a density test. It was vacuous:
// that page scores NEGATIVE, so the second clause is trivially true and the
// assertion passed without exercising anything. The floor deserves a test that
// fails if the floor is removed, so here it is, on identical vocabulary at two
// lengths.
//
// Worth knowing while reading this: for pages under ~1000 body words density
// EQUALS score, because the divisor is `max(1, words/1000)`. The floor is
// deliberately inert on short pages — a short page has nothing to dilute — and
// only starts doing work on long ones. That is why the long fixture below has
// to be genuinely long.
// Identical scoring vocabulary, identical score, different amounts of ordinary
// prose around it. Only the divisor can tell these apart, so this fails the
// moment the floor is removed or the divisor stops applying.
//
// The filler is deliberately neutral: it must add WORDS without adding SCORE,
// or the test measures two things at once.
const vocab = "nude erotic lingerie topless cleavage seductive sensual arousal";
const filler = "the committee met on tuesday to review the quarterly report. ";

const dense = scorePage({ title: "Note", body: vocab }, index, 50);
const diluted = scorePage({ title: "Note", body: vocab + " " + filler.repeat(400) },
                          index, 50);

check(dense.score === diluted.score,
      `filler adds words but no score (${dense.score} vs ${diluted.score})`);
check(diluted.words > dense.words * 50,
      `the diluted fixture really is much longer (${diluted.words} vs ${dense.words})`);
check(diluted.density < dense.density,
      `length dilutes density (${diluted.density.toFixed(2)} < ` +
      `${dense.density.toFixed(2)}) — equal values mean the divisor has ` +
      `stopped applying and the floor is dead code`);

// ── an honest note about how much the floor actually does ──────────────────
// Both fixtures above still BLOCK. That is not a bug in the test, it is the
// current tuning being reported accurately: at MIN_DENSITY = 2.0, a page
// scoring 37 has to reach roughly 18,000 words before density saves it, which
// is longer than almost any real article.
//
// So the floor is a backstop for extreme cases, not the main defence. What
// actually keeps reference and medical pages reachable, as the fixtures
// earlier in this file demonstrate, is the NEGATIVES list driving their scores
// below zero, plus the exempt-domain rail. Anyone tempted to raise
// MIN_DENSITY should tune it against real pages rather than these synthetic
// ones — five hand-written fixtures are far too few to fit a threshold to, and
// a floor set too high would let genuinely explicit long-form content through.
check(diluted.block === dense.block,
      `at the current tuning dilution alone does not change the verdict — ` +
      `if this ever fails, MIN_DENSITY was raised and the comment above needs ` +
      `rewriting rather than the test deleting`);

// A second reason the floor does less than it looks: the occurrence cap and
// the density divisor partly cancel. A page repeating the same terms hits the
// cap at 3, and a page three times as long divides by three — so uniform
// repetition scores identically either way. The floor only earns its place on
// long documents with DIVERSE vocabulary, where distinct terms are not capped.

// ── the nationality trap, end to end ───────────────────────────────────────
// Prevents blocking every Arabic passport and visa page. `جنس` is a substring
// of `الجنسية`, and this is the whole-pipeline version of the unit test in
// test_terms.py.
const passport = {
  url: "https://example.gov/nationality",
  title: "طلب الحصول على الجنسية",
  heading: "قانون الجنسية",
  body: ("يشترط لمنح الجنسية الإقامة المستمرة. " +
         "يقدم طلب الجنسية إلى الوزارة مع المستندات. " +
         "ازدواج الجنسية مسموح في بعض الحالات. ").repeat(30),
};
const pass = scorePage(passport, index, 50);
check(!pass.block, `an Arabic nationality page must NOT block ` +
      `(score ${pass.score.toFixed(1)}, density ${pass.density.toFixed(1)})`);

// ── an ordinary page scores nothing ────────────────────────────────────────
const ordinary = {
  url: "https://example.com/weather",
  title: "Weather forecast for tomorrow",
  body: "Sunny with a chance of rain in the afternoon. ".repeat(50),
};
const plain = scorePage(ordinary, index, 50);
check(plain.score === 0 && !plain.block, "an unrelated page scores zero");

// ── zone weighting actually applies ────────────────────────────────────────
// The same term in the title must count for more than in the body, or the
// weights are decoration.
const inTitle = scorePage({ title: "سكس", body: "" }, index, 50).score;
const inBody = scorePage({ title: "", body: "سكس" }, index, 50).score;
check(inTitle > inBody, `title outweighs body (${inTitle} vs ${inBody})`);

// ── sensitivity points the same way as every other knob ────────────────────
check(threshold(80) < threshold(20),
      "higher sensitivity means a LOWER threshold, i.e. stricter");

// ── exemption is by suffix, not substring ──────────────────────────────────
check(isExempt("en.wikipedia.org", index), "subdomain of an exempt domain");
check(isExempt("wikipedia.org", index), "the exempt domain itself");
check(!isExempt("notwikipedia.org", index),
      "a lookalike host must NOT inherit the exemption");
check(!isExempt("wikipedia.org.evil.com", index),
      "an exempt domain as a PREFIX must not exempt the whole host");

// ── host matching underlies both exemption and the report-wrong allowlist ────
// The false-positive report adds a host to `textAllow`, and the text layer then
// skips it by the SAME rule isExempt uses, so a subdomain is covered and a
// lookalike is not. If these two ever diverge, a reported host would keep being
// blocked, or a lookalike would ride a report it never earned.
check(hostInList("en.example.com", ["example.com"]), "subdomain matches the list");
check(hostInList("example.com", ["example.com"]), "exact host matches");
check(!hostInList("notexample.com", ["example.com"]), "a lookalike does not match");
check(!hostInList("example.com.evil.com", ["example.com"]),
      "the listed domain as a prefix does not match the whole host");
check(!hostInList("example.com", []), "an empty allowlist matches nothing");
check(hostInList("EXAMPLE.com.", ["example.com"]),
      "the host is lowercased and its trailing dot dropped before matching");

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
