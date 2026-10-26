/**
 * Page-text inspection: the compiled vocabulary, the scorer's runtime index,
 * and the small local corrections a person can make to it.
 *
 * Everything here is on-device and ephemeral. The index is rebuilt from the
 * bundled or downloaded `terms.json`; the matched terms are shown only on the
 * extension's own block page from session storage and are never written to a
 * URL, a title, durable state or a network request. See docs/THREAT_MODEL.md
 * Part 5 — a record of what someone tried to reach is the one artifact this
 * product must never create.
 */

import { buildIndex, scorePage, isExempt, hostInList, withoutTerms } from "../lib/score.js";
import "../content/feed.js";
import { normalize } from "../lib/normalize.js";
import { canonicalHost, isLocked } from "../lib/policy.js";
import { restrictionsActive } from "../lib/settings.js";
import { getState, setState } from "./state.js";
import { applyRules } from "./rules.js";
import { ensureBlocked } from "./tabs.js";

/** The compiled term list, loaded once and kept for the worker's lifetime.
 *  A service worker is torn down aggressively, so this is a cache, not state:
 *  everything it holds can be rebuilt from the bundled file. */
let termIndex = null;
let termJson = null;
let effectiveIndex = null;
let effectiveKey = " uninitialised";

/** Forget the cached vocabulary after a new generation is installed. */
export function resetTermIndex() {
  termIndex = null;
  termJson = null;
  effectiveIndex = null;
  effectiveKey = " uninitialised";
}

export async function getTermIndex(customTerms = [], ignoreTerms = []) {
  if (!termIndex) {
    // The downloaded generation's vocabulary first — it was verified against
    // the signed manifest by updateList and is newer by construction — and
    // the bundled seed only when no generation has been installed.
    const stored = await chrome.storage.local.get("terms").catch(() => ({}));
    if (stored.terms?.payload?.terms) {
      termJson = stored.terms.payload;
    } else {
      const res = await fetch(chrome.runtime.getURL("seed/terms.json"));
      termJson = await res.json();
    }
    termIndex = buildIndex(termJson);
    effectiveKey = " uninitialised";
  }
  // Hand-typed words, folded in on top of the compiled list.
  //
  // Rebuilt only when the set actually changes, because buildIndex walks every
  // one of ~5,800 terms and this is called on every scored page. The words
  // arrive from the app over the heartbeat and are stored, like everything else
  // the native side owns, as state the browser may read and not write.
  const key = customTerms.join("\u0000");
  // Fold in the words the user disowned too; either set changing rebuilds.
  const combinedKey = key + " ignore:" + ignoreTerms.join(" ");
  if (combinedKey !== effectiveKey) {
    effectiveKey = combinedKey;
    // A word someone typed deliberately is a stronger signal than one mined
    // from a corpus: they know their own triggers. It still goes through the
    // same scorer, so a single mention of it does not block a long article —
    // the density floor applies to these exactly as it does to the rest.
    let base = termIndex;
    if (customTerms.length) {
      base = buildIndex({ ...termJson,
                          terms: [...termJson.terms,
                                  ...customTerms.map((t) => ({ t: normalize(t), w: 8, l: "user" }))] });
    }
    // Then drop the disowned words, normalised to the list's spelling. Done
    // last so it can also cancel a custom word the user added earlier.
    effectiveIndex = withoutTerms(base, ignoreTerms.map(normalize));
  }
  return effectiveIndex;
}

/**
 * Score one page's text.
 *
 * The exempt-domain rail is applied HERE rather than in the content script, so
 * a page cannot talk its way out of it. It suppresses this layer only: the
 * domain blocklist and the URL keyword rules still apply to an exempt host, and
 * that narrowness is what makes granting an exemption safe.
 */
export async function scoreText(zones, sender) {
  const state = await getState();
  if (!(state.inspectText || state.failClosed || isLocked(state))) {
    return { block: false };
  }

  const index = await getTermIndex(state.customTerms ?? [], state.ignoreTerms ?? []);
  let host = "";
  try {
    host = new URL(sender?.url ?? "").hostname;
  } catch { /* opaque origin — no exemption, judge it on its text */ }
  // Two allowlists suppress this layer: the compiled `exempt_domains`, and the
  // user's own `textAllow` — the hosts they reported as wrong blocks. Both are
  // narrow (text layer only) and both are matched by the same host rule.
  if (host && (isExempt(host, index)
               || hostInList(host, state.textAllow ?? []))) {
    return { block: false };
  }

  const sensitivity = state.failClosed ? 80 : (state.textSensitivity ?? 50);
  const result = scorePage(zones, index, sensitivity);

  // The content script still receives the verdict and NOTHING else — sending
  // the matched terms into the page, the least trusted context there is, is the
  // record docs/THREAT_MODEL.md Part 5 forbids. But a person deserves to see
  // WHY their own page was blocked and to say it was wrong, so the terms are
  // stashed for the block page in `chrome.storage.session`: memory-only, cleared
  // when the browser closes, readable only by trusted extension pages, a single
  // slot overwritten each block, and never persisted, sent, or put in a URL.
  if (result.block) {
    await rememberBlock(host, result.hits);
    if (sender?.tab?.id !== undefined && sender.frameId === 0) ensureBlocked(sender.tab.id, sender.url);
  }
  return { block: result.block };
}


/**
 * Judge ONE rendered feed item. No block-page state, tab navigation, matched
 * terms or host history is created by this path. Only known top-level feed
 * origins can invoke it; client-supplied page title/URL/meta are ignored.
 */
export async function scoreFeedItem(zones, sender) {
  let location;
  try { location = new URL(sender?.url ?? ""); } catch { return { ok: false, reason: "unsupported-feed" }; }
  if (sender?.frameId !== 0 || !globalThis.HisnFeed.isSupportedLocation(location)) {
    return { ok: false, reason: "unsupported-feed" };
  }
  const state = await getState();
  if (!(state.inspectText || state.failClosed || isLocked(state))) {
    return { block: false, enabled: false };
  }
  const index = await getTermIndex(state.customTerms ?? [], state.ignoreTerms ?? []);
  if (isExempt(location.hostname, index) || hostInList(location.hostname, state.textAllow ?? [])) {
    return { block: false, enabled: false };
  }
  const itemZones = {
    body: globalThis.HisnFeed.boundedText(zones?.body),
    alt: globalThis.HisnFeed.boundedText(zones?.alt),
  };
  const sensitivity = state.failClosed ? 80 : (state.textSensitivity ?? 50);
  return { block: scorePage(itemZones, index, sensitivity).block, enabled: true };
}

/** The top matched terms and their host, for the block page to show — held in
 *  ephemeral session storage only. See the Part 5 note in `scoreText`. */
async function rememberBlock(host, hits) {
  const terms = [...(hits ?? new Map()).entries()]
    .sort((a, b) => b[1] - a[1])   // most-hit first
    .slice(0, 8)
    .map(([term]) => term);
  try {
    await chrome.storage.session.set({
      lastTextBlock: { host: host || "", terms, at: Date.now() },
    });
  } catch { /* session storage unavailable — the block still stands */ }
}

/**
 * The user says a page-text block was wrong.
 *
 * This is a LOCAL correction, never a report to anyone: there is no server, and
 * Part 5 forbids one. Unlocked, it adds the host to `textAllow` so the text
 * layer skips it from now on — the domain blocklist and URL rules still apply,
 * so this cannot turn a genuine porn domain reachable, only stop the text layer
 * second-guessing a host the user vouches for. Locked, it cannot loosen
 * anything (that is the whole point of a lock), so the host is queued in
 * `disputed` — host and time only — to apply once the lock ends, and the user
 * is told plainly that it will not lift before then.
 */
export async function reportWrongBlock(host) {
  // Canonical, exactly as the options page's lists are: stored raw, a `www.`
  // or an IP here later made every removal from the list fail (or, unlocked,
  // silently widened `www.x.com` to `x.com`).
  const clean = canonicalHost(host);
  if (!clean) return { ok: false, reason: "no-host" };
  const state = await getState();

  if (restrictionsActive(state)) {
    if (!hostInList(clean, state.disputed.map((d) => d.host))) {
      await setState({ disputed: [...state.disputed, { host: clean, at: Date.now() }] });
    }
    return { ok: false, reason: "locked", until: state.lockUntil, host: clean };
  }

  if (!hostInList(clean, state.textAllow)) {
    const next = await setState({ textAllow: [...state.textAllow, clean] });
    await applyRules(next);
  }
  return { ok: true, host: clean };
}

/**
 * The user says one specific matched WORD was wrong — a medical term, or an
 * over-broad stem catching an innocent word. Narrower than exempting the whole
 * host: the word stops counting as adult text everywhere, and every other word
 * on the list keeps working.
 *
 * Same two rails as the host report. Locked, it cannot loosen, so the word is
 * queued to `disputed` and nothing changes yet. Unlocked, it joins
 * `ignoreTerms`, and getTermIndex drops it from the positive map on the next
 * scored page. Stored normalised — the same spelling the list and the matched
 * chip already use — so `withoutTerms` finds it. A word the user disowns is not
 * a record of anything they sought; it is the opposite, so keeping it is within
 * Part 5 exactly as `customTerms` (their block words) already is.
 */
export async function reportWrongWord(term) {
  const norm = normalize(String(term || ""));
  if (!norm) return { ok: false, reason: "no-term" };
  const state = await getState();

  if (restrictionsActive(state)) {
    if (!state.disputed.some((d) => d.term === norm)) {
      await setState({ disputed: [...state.disputed, { term: norm, at: Date.now() }] });
    }
    return { ok: false, reason: "locked", until: state.lockUntil, term };
  }

  const ignore = state.ignoreTerms ?? [];
  if (!ignore.includes(norm)) {
    await setState({ ignoreTerms: [...ignore, norm] });
    // No applyRules: this changes page-text scoring, not the DNR rule set.
    // getTermIndex rebuilds on the next scored page because the key changed.
  }
  return { ok: true, term };
}

/**
 * Resolve one report that was parked in `disputed` because a lock was running
 * when it was filed. Without this the queue was a dead-end write — a person's
 * mid-lock report was saved and never surfaced again.
 *
 *  * apply — move it into the real list (`ignoreTerms` for a word, `textAllow`
 *    for a host). That is a loosening, so it is refused while a lock is still
 *    running, exactly like the original report was; the entry stays queued.
 *  * dismiss — just drop it from the queue. That loosens nothing, so it is
 *    allowed at any time.
 *
 * `entry` names the item by its value: `{ term }` or `{ host }`, plus `apply`.
 */
export async function resolveDisputed(entry) {
  const term = typeof entry?.term === "string" ? entry.term : "";
  const host = typeof entry?.host === "string" ? entry.host : "";
  if (!term && !host) return { ok: false, reason: "no-entry" };

  const state = await getState();
  const matches = (d) => term ? d.term === term : d.host === host;
  if (!state.disputed.some(matches)) return { ok: false, reason: "not-found" };
  const remaining = state.disputed.filter((d) => !matches(d));

  if (!entry.apply) {
    const next = await setState({ disputed: remaining });
    return { ok: true, applied: false, state: next };
  }

  if (restrictionsActive(state)) {
    return { ok: false, reason: "locked", until: state.lockUntil };
  }

  const patch = { disputed: remaining };
  if (term) {
    const ignore = state.ignoreTerms ?? [];
    if (!ignore.includes(term)) patch.ignoreTerms = [...ignore, term];
  } else {
    const allow = state.textAllow ?? [];
    if (!hostInList(host, allow)) patch.textAllow = [...allow, host];
  }
  const next = await setState(patch);
  await applyRules(next);
  return { ok: true, applied: true, state: next };
}
