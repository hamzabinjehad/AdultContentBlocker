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

// This is a reviewed, small evasion vocabulary, NOT a new blocklist or a
// replacement normaliser. Only an already-present strong positive can match;
// ignored/removed/reweighted terms cannot be resurrected here. In particular,
// do not globally fold Franco-Arabic digits or all Greek/Cyrillic prose.
const EVASION_TERMS = new Set(["porn", "porno", "pornography", "hentai",
  "اباحي", "اباحيه", "بورنو", "سكس"]);
const EVASION_FOLDS = new Map([
  ["0", "o"], ["1", "i"], ["3", "e"], ["4", "a"], ["7", "t"],
  ["о", "o"], ["ο", "o"], ["р", "p"], ["ρ", "p"],
  ["а", "a"], ["е", "e"], ["і", "i"], ["у", "y"], ["һ", "h"],
]);
const EVASION_MARKER = /[01347\u043e\u03bf\u0440\u03c1\u0430\u0435\u0456\u0443\u04bb]/u;
const LOCAL_TERMS = [...EVASION_TERMS];
const LOCAL_BITS = new Map(LOCAL_TERMS.map((term, i) => [term, 1 << i]));
const PROMOTION_WORDS = new Set(["watch", "download", "stream", "شاهد", "تحميل", "مشاهده"]);

/** A narrow additional verdict, not a replacement for page scoring. Require
 * two distinct reviewed strong terms AND promotional wording in 16 tokens.
 * Any signed negative context within eight tokens on either side prevents
 * this lane from acting. Distant negatives cannot cancel the local evidence.
 * Counts and numeric marks are ephemeral and bounded by the submitted text. */
function localPromotion(tokens, strong, negative, index, sensitivity) {
  const counts = new Uint8Array(LOCAL_TERMS.length);
  const weights = LOCAL_TERMS.map((term) => index.positive.get(term) ?? 0);
  let score = 0, distinct = 0, promotion = 0, context = 0;
  for (let i = 0; i < Math.min(8, tokens.length); i++) context += negative[i];
  for (let end = 0; end < tokens.length; end++) {
    context += negative[end + 8] ?? 0;
    if (end >= 24) context -= negative[end - 24];
    if (PROMOTION_WORDS.has(tokens[end])) promotion++;
    if (end >= 16 && PROMOTION_WORDS.has(tokens[end - 16])) promotion--;
    for (let bit = 0; bit < LOCAL_TERMS.length; bit++) {
      if (strong[end] & (1 << bit)) {
        if (counts[bit]++ === 0) { distinct++; score += weights[bit]; }
      }
      if (end >= 16 && strong[end - 16] & (1 << bit)) {
        if (--counts[bit] === 0) { distinct--; score -= weights[bit]; }
      }
    }
    if (!context && promotion && distinct >= 2 && score >= Math.max(16, threshold(sensitivity))) {
      return { score, words: Math.min(16, end + 1),
        terms: LOCAL_TERMS.filter((_, bit) => counts[bit] > 0) };
    }
  }
  return null;
}

function foldEvasion(token) {
  let candidate = "";
  for (const ch of token) candidate += EVASION_FOLDS.get(ch) ?? ch;
  return candidate;
}
function evasionTerm(token, index) {
  if (token.length < 3 || token.length > 32) return null;
  let candidate = foldEvasion(token);
  // Keep the existing trailing-digit-decoration meaning if its base is a
  // reviewed term. Otherwise "porn0" could turn into a different word after
  // the user ignored "porn". Embedded digits and "hent41" still fold normally.
  const undecorated = token.replace(/[0-9]+$/, "");
  if (undecorated !== token) {
    const base = foldEvasion(undecorated);
    if (EVASION_TERMS.has(base)) candidate = base;
  }
  return EVASION_TERMS.has(candidate) && (index.positive.get(candidate) ?? 0) >= 8
    && !index.negative.has(candidate) ? candidate : null;
}

/** A reviewed local French form-label ambiguity, not a page-wide exemption.
 * Only this occurrence of the seed's weight-6 term is ignored; deliberate
 * custom rules have a larger weight and still apply. Other content in the
 * same item/zone continues through the complete scorer. */
function formChoice(term, tokens, i, index) {
  if (term !== "sexe" || index.positive.get(term) !== 6) return false;
  const a = tokens[i + 1], b = tokens[i + 2];
  return (a === "femme" && b === "homme") || (a === "homme" && b === "femme")
    || (a === "masculin" && b === "feminin") || (a === "feminin" && b === "masculin");
}

// Custom phrases may be pasted from wrapped text. Normalisation deliberately
// preserves whitespace (a shared native/compiler contract); only the matcher
// key collapses it, exactly as tokenising page text already does.
function phraseKey(term) {
  return term.replace(/\s+/gu, " ").trim();
}

/** Compile only multi-word terms. A missing prefix ends a phrase lookup
 * immediately, instead of slicing/joining every possible length at every
 * token. Terminal nodes retain the complete key for scoring and explanations.
 * This is exact token matching, not stemming or substring matching. */
function phraseTree(positive, negative) {
  const root = new Map();
  for (const map of [positive, negative]) {
    for (const term of map.keys()) {
      const words = term.split(" ");
      if (words.length < 2) continue;
      let branch = root, node;
      for (const word of words) {
        node = branch.get(word);
        if (!node) {
          node = { next: new Map(), term: null };
          branch.set(word, node);
        }
        branch = node.next;
      }
      node.term = term;
    }
  }
  return root;
}

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
    t = phraseKey(t);
    if (!t) return;
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
    phrases: phraseTree(positive, negative),
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

/** A copy of `index` with `terms` removed from its positive map — the words a
 *  user marked as wrong on a block page, so they stop counting as adult text
 *  everywhere. Negatives are left untouched: a term the user disowns should
 *  simply not score, not start pulling scores DOWN. `terms` must already be
 *  normalised to the list's spelling (the keys of `positive`), which the matched
 *  words the user taps already are. Returns `index` unchanged when there is
 *  nothing to drop, so the common no-ignores path allocates nothing. */
export function withoutTerms(index, terms) {
  if (!terms || !terms.length) return index;
  const drop = new Set(terms.map(phraseKey));
  const positive = new Map();
  for (const [t, w] of index.positive) if (!drop.has(t)) positive.set(t, w);
  return { ...index, positive, phrases: phraseTree(positive, index.negative) };
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
function scoreZone(text, index, counts, inspectLocal = false, sensitivity = 50) {
  const normalised = normalize(text);
  const tokens = tokenize(normalised);
  const strong = inspectLocal ? new Uint8Array(tokens.length) : null;
  const negative = inspectLocal ? new Uint8Array(tokens.length) : null;
  let union = 0, starts = null;
  function record(term, start, end = start) {
    if (!inspectLocal) return;
    const bit = LOCAL_BITS.get(term) ?? 0;
    if (bit && (index.positive.get(term) ?? 0) >= 8 && !index.negative.has(term)) {
      strong[end] |= bit; union |= bit;
    }
    if (index.negative.has(term) && !index.positive.has(term)) {
      for (let i = start; i <= end; i++) negative[i] = 1;
    }
  }
  // Build source offsets only if an accepted dotted word/hashtag needs them.
  // No repeated tokenisation of every prefix and no raw text retained outside
  // this scoring call. Binary search binds the alias to its actual token span.
  function tokenAt(offset) {
    if (!starts) {
      starts = new Uint32Array(tokens.length);
      let cursor = 0;
      for (let i = 0; i < tokens.length; i++) {
        starts[i] = normalised.indexOf(tokens[i], cursor);
        cursor = starts[i] + tokens[i].length;
      }
    }
    let lo = 0, hi = starts.length;
    while (lo < hi) {
      const mid = (lo + hi) >>> 1;
      if (starts[mid] <= offset) lo = mid + 1; else hi = mid;
    }
    return Math.max(0, lo - 1);
  }

  for (let i = 0; i < tokens.length; i++) {
    // Single token, plus its bounded affix-stripped forms. `الإباحية` has to
    // find the listed `اباحيه`.
    let positiveMatch = false;
    for (const v of variants(tokens[i])) {
      if (index.positive.has(v)) {
        positiveMatch = true;
        if (!formChoice(v, tokens, i, index)) {
          counts.set(v, (counts.get(v) ?? 0) + 1); record(v, i);
        }
      }
      if (index.negative.has(v)) { counts.set(v, (counts.get(v) ?? 0) + 1); record(v, i); }
    }
    // Do not invent a second signal when an existing exact/affix spelling
    // already matched (e.g. the trailing-digit variant of "porn0").
    if (!positiveMatch && EVASION_MARKER.test(tokens[i])) {
      const term = evasionTerm(tokens[i], index);
      if (term) { counts.set(term, (counts.get(term) ?? 0) + 1); record(term, i); }
    }
    // Follow only prefixes present in the vocabulary. Keep walking after a
    // terminal so overlapping/longer phrases retain their original scores.
    let node = index.phrases.get(tokens[i]);
    for (let j = i + 1; node && j < tokens.length; j++) {
      node = node.next.get(tokens[j]);
      if (node?.term) { counts.set(node.term, (counts.get(node.term) ?? 0) + 1); record(node.term, i, j); }
    }
  }

  // Whole punctuation-joined runs of single letters only. No arbitrary
  // whitespace, path separators, long fragments or substring search: "pop-
  // corn", hostnames and version numbers must not be fuzzy adult matches.
  // The run regex is linear; candidates have a fixed length/fragment bound.
  for (const match of normalised.matchAll(/(?:^|[^\p{L}\p{N}])([\p{L}\p{N}]+(?:[._*·-][\p{L}\p{N}]+)+)/gu)) {
    if (match[1].length > 32) continue;
    const letters = match[1].split(/[._*·-]/u);
    if (letters.length < 3 || letters.some((part) => [...part].length !== 1)) continue;
    const term = evasionTerm(letters.join(""), index);
    if (term) {
      counts.set(term, (counts.get(term) ?? 0) + 1);
      if (inspectLocal) {
        const start = tokenAt(match.index + match[0].length - match[1].length);
        record(term, start, start + letters.length - 1);
      }
    }
  }
  // Anchored promotional hashtags only; not arbitrary concatenated words or
  // substrings such as #Freeport or #FreePornResearch. Use canonical weights.
  for (const match of normalised.matchAll(/(?:^|[^\p{L}\p{N}_])#((?:free|watch)([\p{L}\p{N}]{3,32}))(?![\p{L}\p{N}_])/gu)) {
    if ([...variants(match[1])].some((v) => index.positive.has(v))) continue;
    const term = evasionTerm(match[2], index);
    if (!term) continue;
    counts.set(term, (counts.get(term) ?? 0) + 1);
    if (inspectLocal) record(term, tokenAt(match.index + match[0].length - match[1].length));
  }
  return { words: tokens.length,
    local: inspectLocal && (union & (union - 1)) !== 0
      ? localPromotion(tokens, strong, negative, index, sensitivity) : null };
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
  let localEvidence = null;

  for (const [zone, weight] of Object.entries(ZONE_WEIGHTS)) {
    const text = zones[zone];
    if (!text) continue;

    const counts = new Map();
    const result = scoreZone(text, index, counts, zone === "body" || zone === "alt", sensitivity);
    if (zone === "body") words += result.words;
    if (!localEvidence && result.local) localEvidence = { zone, ...result.local };

    for (const [term, raw] of counts) {
      const occurrences = Math.min(raw, MAX_OCCURRENCES);
      const w = index.positive.get(term) ?? index.negative.get(term) ?? 0;
      score += w * occurrences * weight;
      if (w > 0) hits.set(term, (hits.get(term) ?? 0) + occurrences);
    }
  }

  const t = threshold(sensitivity);
  const density = score / Math.max(1, words / 1000);
  const wholePageBlock = score >= t && density >= MIN_DENSITY;

  return {
    score,
    density,
    words,
    threshold: t,
    // The whole-page lane still requires BOTH conditions. A separately gated
    // local promotion cannot be diluted by unrelated words/negatives elsewhere.
    block: wholePageBlock || localEvidence !== null,
    hits,
    ...(!wholePageBlock && localEvidence ? { localEvidence } : {}),
  };
}
