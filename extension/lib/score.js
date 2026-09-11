/**
 * Weighted page-text scoring.
 *
 * WHY SCORING AND NOT KEYWORD MATCHING
 * -----------------------------------
 * The URL keyword layer (the DNR rules built by `blocklist/build.py`) matches
 * whole tokens with explicit boundaries, because that is the only way `sex`
 * can be blocked without also taking out `essex.gov.uk` and `sks` without
 * taking out `tasks.office.com`. The price of that boundary rule is real: it
 * misses concatenations like `sexstories`, and it cannot see page text at all.
 *
 * This layer pays a different price. It reads the rendered page, so it sees
 * everything — and because it sees everything, a single-word rule here would
 * block Wikipedia, every medical reference and half of journalism. So it does
 * not match; it SCORES. Signals accumulate, zones are weighted, and a page has
 * to clear both a threshold and a density floor before anything happens.
 *
 * Pure by design: no `chrome.*`, no DOM. It takes strings and returns a
 * verdict, which is what makes it testable outside a browser.
 */

import { normalize, tokenize, variants } from "./normalize.js";

/**
 * Where on the page the text came from, and how much that placement matters.
 *
 * A term in the title or URL is a statement about what the page IS. The same
 * term in the body may be a quotation, a warning, a definition, or a comment
 * arguing against it. Weighting by zone is what separates a tube site's landing
 * page from an article about tube sites.
 */
export const ZONE_WEIGHTS = {
  url: 3,
  title: 3,
  meta: 2,
  heading: 2,
  body: 1,
  alt: 1,
};

/** One term contributes at most this many times. A word repeated four hundred
 *  times in a comment thread is one signal, not four hundred. */
const MAX_OCCURRENCES = 3;

/**
 * How much score a page needs per thousand words before a block is allowed.
 *
 * THE SINGLE MOST IMPORTANT NUMBER IN THIS FILE. A 12,000-word encyclopaedia
 * article on human sexuality legitimately uses clinical vocabulary dozens of
 * times and will out-score a 300-word tube landing page on raw total. Density
 * is what tells them apart: the article sits around 2, the landing page around
 * 60. Without this floor the layer blocks reference material first and adult
 * material second.
 */
const MIN_DENSITY = 2.0;

/** sensitivity 0-100, higher = stricter. Derived so every knob in the product
 *  points the same way and every guard is a `>=`. */
export function threshold(sensitivity) {
  return 12 - 0.09 * sensitivity;      // 50 -> 7.5
}

/**
 * Build a matcher from a compiled `terms.json`.
 *
 * Terms arrive already normalised by the Python compiler, so nothing here
 * re-normalises the list — only the page text. That asymmetry is what makes
 * the cross-language fixture meaningful: if the two normalisers disagree, this
 * lookup silently misses and the test table is what catches it.
 */
export function buildIndex(termsJson) {
  const positive = new Map();
  const negative = new Map();
  let maxWords = 1;

  const add = (map, t, w) => {
    const words = t.split(" ").length;
    if (words > maxWords) maxWords = words;
    map.set(t, (map.get(t) ?? 0) + w);
  };

  for (const r of termsJson.terms ?? []) add(positive, r.t, r.w);
  for (const r of termsJson.negatives ?? []) add(negative, r.t, -Math.abs(r.w));

  return {
    positive,
    negative,
    maxPhrase: maxWords,
    exempt: new Set(termsJson.exempt_domains ?? []),
  };
}

/** True if `hostname` equals, or is a subdomain of, any domain in `list`.
 *
 *  Suffix match on a dot boundary, never a substring: `en.wikipedia.org`
 *  matches `wikipedia.org`, but `notwikipedia.org` and `wikipedia.org.evil.com`
 *  do not. Exported because two lists need exactly this rule — the compiled
 *  `exempt_domains` and a user's own page-text allowlist (the hosts they
 *  reported as wrong blocks) — and one shared definition of "on this host" is
 *  what keeps the false-positive report and the exemption from drifting apart. */
export function hostInList(hostname, list) {
  const host = String(hostname || "").toLowerCase().replace(/\.$/, "");
  if (!host) return false;
  for (const d of list) {
    if (d && (host === d || host.endsWith("." + d))) return true;
  }
  return false;
}

/** True if this hostname is exempt from PAGE-TEXT blocking.
 *
 *  Exemption is narrow on purpose: it suppresses this layer only. The domain
 *  blocklist and the URL keyword rules still apply, and — once image
 *  classification exists — every image on the page is still classified. A
 *  reference article is text we do not want to block and images we very much
 *  still do. */
export function isExempt(hostname, index) {
  return hostInList(hostname, index.exempt);
}

/**
 * Score one zone's text.
 *
 * Counts single tokens and phrases up to the longest phrase in the list, so a
 * multi-word term like `قصص جنسية` is found without the caller having to know
 * it is multi-word.
 */
function scoreZone(text, index, counts) {
  const tokens = tokenize(normalize(text));

  for (let i = 0; i < tokens.length; i++) {
    // Single token, plus its bounded affix-stripped forms. `الإباحية` has to
    // find the listed `اباحيه`.
    for (const v of variants(tokens[i])) {
      if (index.positive.has(v)) counts.set(v, (counts.get(v) ?? 0) + 1);
      if (index.negative.has(v)) counts.set(v, (counts.get(v) ?? 0) + 1);
    }
    // Phrases.
    for (let n = 2; n <= index.maxPhrase && i + n <= tokens.length; n++) {
      const phrase = tokens.slice(i, i + n).join(" ");
      if (index.positive.has(phrase) || index.negative.has(phrase)) {
        counts.set(phrase, (counts.get(phrase) ?? 0) + 1);
      }
    }
  }
  return tokens.length;
}

/**
 * Score a whole page.
 *
 * @param {Object} zones  { url, title, meta, heading, body, alt } — any subset
 * @param {Object} index  from buildIndex()
 * @param {number} sensitivity 0-100
 * @returns {{score, density, words, threshold, block, hits}}
 */
export function scorePage(zones, index, sensitivity = 50) {
  let score = 0;
  let words = 0;
  const hits = new Map();

  for (const [zone, weight] of Object.entries(ZONE_WEIGHTS)) {
    const text = zones[zone];
    if (!text) continue;

    const counts = new Map();
    const n = scoreZone(text, index, counts);
    if (zone === "body") words += n;

    for (const [term, raw] of counts) {
      const occurrences = Math.min(raw, MAX_OCCURRENCES);
      const w = index.positive.get(term) ?? index.negative.get(term) ?? 0;
      score += w * occurrences * weight;
      if (w > 0) hits.set(term, (hits.get(term) ?? 0) + occurrences);
    }
  }

  const t = threshold(sensitivity);
  const density = score / Math.max(1, words / 1000);

  return {
    score,
    density,
    words,
    threshold: t,
    // BOTH conditions, never either. The threshold alone blocks long reference
    // articles; the density alone blocks any short page that mentions anything.
    block: score >= t && density >= MIN_DENSITY,
    hits,
  };
}
