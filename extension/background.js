/**
 * Hisn Protection — MV3 service worker.
 *
 * Three jobs:
 *   1. Keep the declarativeNetRequest rules in sync with the lock state.
 *   2. Stay in contact with the native macOS app, which owns the authoritative
 *      lock clock.
 *   3. FAIL CLOSED. If the native app stops answering while a lock is active,
 *      tighten to lockdown instead of relaxing. Every "safe" default here is
 *      the restrictive one, because the failure mode of a self-control tool is
 *      not "the user is annoyed", it is "the tool quietly stopped working and
 *      nobody noticed".
 */

import { acceptList } from "./lib/verify.js";
import { buildIndex, scorePage, isExempt, hostInList, withoutTerms }
  from "./lib/score.js";
import { normalize } from "./lib/normalize.js";
import { isLocked, effectiveStrict, shouldFailClosed, policyRules, tabsToBlock,
         validatePatch, bridgePatch, RULE_DOWNLOADED_BASE, HEARTBEAT_GRACE_MS }
  from "./lib/policy.js";
import { createLock } from "./lib/serial.js";
import { settingsAccess, restrictionsActive } from "./lib/settings.js";
import { planGeneration, downloadedRuleIds, GENERATION_ARTIFACTS } from "./lib/generation.js";

const NATIVE_HOST = "app.hisn.bridge";
const LIST_BASE = "https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists";

const RULESET_BLOCKLIST = "blocklist";
const RULESET_KEYWORDS = "keywords";
const RULESET_LOCKDOWN = "lockdown";

// Rule ids, priorities and the grace period live in lib/policy.js with the
// rules themselves, so the tests evaluate exactly what Chrome is given.

const DEFAULT_STATE = {
  mode: "off",              // off | blocklist | strict
  lockUntil: 0,             // epoch ms; 0 = not locked
  allowlist: [],            // always reachable: overrides the published list
                            // and keyword rules; in strict mode, the ONLY
                            // domains reachable. See lib/policy.js rule 2.
  customBlocks: [],         // user-added domains
  customTerms: [],          // user-added words, scored like the compiled list
  listVersion: 0,
  rulesApplied: 0,          // downloaded rules actually installed, not claimed
  keywordRulesApplied: 0,   // downloaded keyword rules, same rule
  lastListUpdate: 0,        // epoch ms of the last generation installed
  lastHeartbeat: 0,
  failClosed: false,        // true once we lost the native app mid-lock
  inspectText: true,        // page-text scoring
  textSensitivity: 50,      // 0-100, higher = stricter (see lib/score.js)
  textAllow: [],            // hosts the user reported as WRONG page-text blocks;
                            // page-text scoring skips them. Extension-local (the
                            // app does not own this list), and add-only while
                            // locked — see guardedUpdate. Never the domain
                            // blocklist's business: this suppresses the text
                            // layer only, exactly like the compiled exempt set.
  ignoreTerms: [],          // individual words the user marked wrong on a block
                            // page — a medical term, an over-broad stem. They
                            // stop counting as adult text everywhere. Normalised
                            // to the list's spelling, extension-local, add-only
                            // while locked. The opposite number of customTerms.
  disputed: [],             // {host, at} for reports filed DURING a lock, when
                            // the exemption cannot take effect yet. Holds the
                            // host the user calls safe and the time — never the
                            // words that matched, which stay out of durable
                            // storage entirely (docs/THREAT_MODEL.md Part 5).

  // Has the native app ever answered? Distinguishes the two ways this
  // extension is legitimately run, which behave differently and must be
  // described differently:
  //
  //   standalone  — browser-only. Baseline blocking is on, the user authors
  //                 their own lists here, and nothing is unremovable.
  //   with the app — the app is the authority: it owns the lists, it can
  //                 impose a lock, and this side stops being editable.
  //
  // Never used to decide whether to FILTER. Baseline protection does not
  // depend on the app being there, and a field that could switch it off would
  // be one uninstall away from being the bypass.
  appPresent: false,
};

// --------------------------------------------------------------------------
// State
// --------------------------------------------------------------------------

async function getState() {
  const stored = await chrome.storage.local.get("state");
  return { ...DEFAULT_STATE, ...(stored.state || {}) };
}

/**
 * Every transaction that WRITES state or rules runs under this lock — see
 * lib/serial.js for the lost-update it closes. Taken at the entry points
 * (messages, alarms, lifecycle events), never inside the functions they call,
 * so a transaction that calls another (pollNative → handleNativeLoss) does
 * not wait for itself. Reads (`getState`, `scoreText`) do not take it.
 */
const transaction = createLock();

async function setState(patch) {
  const next = { ...(await getState()), ...patch };
  await chrome.storage.local.set({ state: next });
  return next;
}

// --------------------------------------------------------------------------
// Rule application
// --------------------------------------------------------------------------

/*
 * The dynamic rules — strict catch-all, allowlist carve-outs, custom blocks —
 * are built by `policyRules` in lib/policy.js. That file is the decision
 * contract shared with the macOS filter, and `blocklist/terms/policy_cases.json` is
 * asserted against the very array it returns. Do not build rules here.
 */

async function applyRules(state) {
  const dynamic = policyRules(state);

  // Clear only the ids this function owns. Removing every dynamic rule here
  // would also delete the downloaded blocklist, so every lock state change
  // would silently unblock 148k domains until the next successful update.
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existing
      .filter((r) => r.id < RULE_DOWNLOADED_BASE)
      .map((r) => r.id),
    addRules: dynamic,
  });

  // ── BASELINE PROTECTION IS UNCONDITIONAL ─────────────────────────────────
  // These two rulesets are never disabled. Not when unlocked, not when the
  // native app is absent, not on a fresh install.
  //
  // This used to be gated on `state.mode !== "off" || isLocked(state)`, and
  // that gate was inverted in a way that made the extension useless on its own:
  // `DEFAULT_STATE.mode` is "off", so a newly installed extension with no lock
  // running — which is every install, before any lock is ever started —
  // DISABLED the 148k-domain blocklist and blocked precisely nothing. The
  // product's whole premise is a blocklist that is always there; a lock makes
  // it stricter and unremovable, it is not the switch that turns it on.
  //
  // Anyone tempted to re-add a condition here should note what the two states
  // actually mean: "no lock" means the person has not committed to a period
  // yet, and "no app" means they installed only the browser half. Neither is a
  // request to stop filtering.
  await chrome.declarativeNetRequest.updateEnabledRulesets({
    enableRulesetIds: [RULESET_BLOCKLIST, RULESET_KEYWORDS],
    disableRulesetIds: [],
  });

  await syncTextScanning(state);
  await enforceOpenTabs(state);

  console.info("[hisn] rules applied", {
    mode: state.mode,
    strict: effectiveStrict(state),
    failClosed: state.failClosed,
    locked: isLocked(state),
    dynamicRules: dynamic.length,
  });
}

/**
 * Send already-open tabs that the policy now refuses to the block page.
 *
 * Rules only judge NEW requests; a tab opened before a lock began would
 * otherwise stay up for as long as it is left alone. See `tabsToBlock`.
 * Cheap when nothing is strict and no custom block exists, which is the
 * common case: one `tabs.query` and no navigations. Errors are logged, not
 * thrown — a tab that vanished mid-walk must not abort the rest.
 */
async function enforceOpenTabs(state) {
  if (!effectiveStrict(state) && !(state.customBlocks || []).length) return;
  let tabs;
  try {
    tabs = await chrome.tabs.query({});
  } catch (err) {
    console.warn("[hisn] could not enumerate tabs:", err?.message ?? err);
    return;
  }
  for (const { id, reason } of tabsToBlock(tabs, state)) {
    try {
      await chrome.tabs.update(id, {
        url: chrome.runtime.getURL(`blocked.html?reason=${reason}`),
      });
    } catch (err) {
      console.warn("[hisn] could not close tab %d:", id, err?.message ?? err);
    }
  }
}

// --------------------------------------------------------------------------
// Page-text scoring
// --------------------------------------------------------------------------

/** The compiled term list, loaded once and kept for the worker's lifetime.
 *  A service worker is torn down aggressively, so this is a cache, not state:
 *  everything it holds can be rebuilt from the bundled file. */
let termIndex = null;
let termJson = null;
let effectiveIndex = null;
let effectiveKey = " uninitialised";

async function getTermIndex(customTerms = [], ignoreTerms = []) {
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
 * Register or remove the content script that reads page text.
 *
 * Registered dynamically rather than declared in the manifest, because a
 * manifest `content_scripts` block is unconditional: it would inject into every
 * page on every site even with text scanning switched off. `persistAcrossSessions`
 * means Chrome re-registers it before the worker wakes on a cold start, so
 * there is no window where pages load unscanned while the worker spins up.
 */
async function syncTextScanning(state) {
  const wanted = state.inspectText || state.failClosed || isLocked(state);
  const existing = await chrome.scripting.getRegisteredContentScripts({
    ids: ["hisn-scan"],
  }).catch(() => []);

  if (!wanted) {
    if (existing.length) {
      await chrome.scripting.unregisterContentScripts({ ids: ["hisn-scan"] });
    }
    return;
  }
  if (existing.length) return;

  await chrome.scripting.registerContentScripts([{
    id: "hisn-scan",
    matches: ["<all_urls>"],
    js: ["content/scan.js"],
    runAt: "document_start",
    allFrames: true,
    // `allFrames` alone still misses the frames that carry no real URL of their
    // own — `about:srcdoc`, `blob:` and `data:` iframes — which is exactly how
    // embedded players and popunder ad frames render their content. These
    // inherit their embedder's origin, so `matchOriginAsFallback` injects the
    // scanner into them by that inherited origin; without it a tube page can put
    // the whole video in a srcdoc frame the text layer never reads.
    matchOriginAsFallback: true,
    persistAcrossSessions: true,
  }]);
  console.info("[hisn] page-text scanning registered");
}

/**
 * Score one page's text.
 *
 * The exempt-domain rail is applied HERE rather than in the content script, so
 * a page cannot talk its way out of it. It suppresses this layer only: the
 * domain blocklist and the URL keyword rules still apply to an exempt host, and
 * that narrowness is what makes granting an exemption safe.
 */
async function scoreText(zones, sender) {
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
  }
  return { block: result.block };
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
async function reportWrongBlock(host) {
  const clean = String(host || "").toLowerCase().replace(/\.$/, "");
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
async function reportWrongWord(term) {
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
async function resolveDisputed(entry) {
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

// --------------------------------------------------------------------------
// Native app link
// --------------------------------------------------------------------------

/**
 * Ask the native app for the authoritative lock state.
 *
 * The extension's own storage is not trustworthy as the source of truth: it
 * lives in a profile directory the user can edit. The native app holds the
 * clock in a location the filter itself protects.
 */
async function pollNative() {
  try {
    const reply = await chrome.runtime.sendNativeMessage(NATIVE_HOST, {
      type: "getLockState",
    });
    if (!reply || !Number.isFinite(reply.lockUntil)) {
      throw new Error("malformed reply");
    }
    // Build the patch by PRESENCE, not by defaulting.
    //
    // The obvious version — `allowlist: reply.allowlist ?? []` — has a trap in
    // it that only shows up during an upgrade. If the bridge is an older build
    // that does not yet send a field, `??` substitutes the empty value and
    // silently deletes whatever the user had. For `customBlocks` that quietly
    // unblocks every domain they added by hand; for `inspectText` it would turn
    // a layer off. A field the bridge does not mention is a field we know
    // nothing about, and the safe reading of that is "leave it alone".
    //
    // `bridgePatch` applies that rule and one more: a field the reply mentions
    // with a value of the wrong shape is ALSO left alone. It crossed a pipe as
    // JSON; a malformed list is not an empty list.
    const patch = {
      lockUntil: Math.max(0, Math.floor(reply.lockUntil)),
      mode: "blocklist",
      ...bridgePatch(reply),
      lastHeartbeat: Date.now(),
      failClosed: false,
      appPresent: true,
    };
    const state = await setState(patch);
    await applyRules(state);
    return true;
  } catch (err) {
    console.warn("[hisn] native host unreachable:", err?.message ?? err);
    await handleNativeLoss();
    return false;
  }
}

/**
 * The native app went away.
 *
 * If a lock was running, treat this as tampering — the user uninstalling the
 * app is the most obvious way to try to escape — and clamp down rather than
 * open up. If no lock was running, losing the app is unremarkable.
 */
async function handleNativeLoss() {
  const state = await getState();

  // Never having heard from the app is not the app going away. A browser-only
  // install is a supported configuration, not a fault, and treating it as
  // tampering would clamp every standalone user into strict mode forever —
  // which is both wrong and the fastest way to get the extension removed.
  // That gate, the lock check, the grace period and the already-clamped check
  // are all in `shouldFailClosed`, where the tests can see them.
  if (!shouldFailClosed(state)) return;

  console.error("[hisn] native app silent for %d minutes during an active lock "
                + "— failing closed", HEARTBEAT_GRACE_MS / 60000);
  const next = await setState({ failClosed: true });
  await applyRules(next);
}

// --------------------------------------------------------------------------
// List updates
// --------------------------------------------------------------------------

async function fetchBytes(url) {
  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return new Uint8Array(await res.arrayBuffer());
}

/**
 * Install a downloaded, verified generation: domain rules, keyword rules and
 * the scanner's vocabulary, together.
 *
 * Downloaded rules live in their OWN id space, above `RULE_DOWNLOADED_BASE`,
 * and `applyRules` only ever clears ids below it. Both functions call
 * `updateDynamicRules`, and without that split whichever ran last would wipe
 * the other's work — a lock change would silently delete the blocklist, or a
 * list update would silently delete strict mode.
 *
 * One `updateDynamicRules` call, so the swap is atomic: there is no window in
 * which the old rules are gone and the new ones are not yet in, and no window
 * with new domain rules beside old keyword rules. The vocabulary is written
 * after the rules land; a crash between the two leaves the previous
 * vocabulary, which is stale, not wrong, and the next check repairs it.
 */
async function applyGeneration(plan) {
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: downloadedRuleIds(existing),
    addRules: [...plan.blockRules, ...plan.keywordRules],
  });
  await chrome.storage.local.set({
    terms: { version: plan.terms.version ?? 0, payload: plan.terms },
  });
  // Drop the cached index so the next scored page uses the new vocabulary.
  termIndex = null;
  termJson = null;
  return { rules: plan.blockRules.length, keywordRules: plan.keywordRules.length };
}

/**
 * Check for a newer blocklist, verify it, and actually apply it.
 *
 * ── WHAT WAS BROKEN HERE ──────────────────────────────────────────────────
 * This function used to pass `artifacts: new Map()`. It fetched the manifest,
 * checked its signature, recorded the version — and downloaded no rules at
 * all. The extension's blocking was therefore whatever JSON happened to be
 * hand-copied into the package at build time, and it never changed, however
 * many times this ran or how new the published list was. The version number in
 * the popup climbed while the rules behind it stood still, which is the exact
 * "looks healthy, enforces nothing" failure the threat model rates as worse
 * than being switched off.
 *
 * It also meant the hash-checking loop in `acceptList` had never once executed
 * in production. It does now: the artifact bytes go through it, so a CDN that
 * serves a valid manifest alongside a swapped rules file is caught.
 *
 * The two-step fetch is deliberate. The manifest is small and the rules file is
 * megabytes, so the version is checked first and the large download only
 * happens when there is genuinely something newer to install.
 *
 * A failed update remains a non-event: the rules already installed stay
 * installed. A stale blocklist still blocks; an empty one does not.
 */
async function updateList() {
  const state = await getState();
  try {
    const [manifestBytes, sigBytes] = await Promise.all([
      fetchBytes(`${LIST_BASE}/manifest.json`),
      fetchBytes(`${LIST_BASE}/manifest.json.sig`),
    ]);
    const signatureHex = new TextDecoder().decode(sigBytes).trim();

    const probe = await acceptList({
      manifestBytes,
      signatureHex,
      artifacts: new Map(),
      heldVersion: state.listVersion,
    });
    if (!probe.ok) {
      console.error("[hisn] list rejected:", probe.reason);
      return;
    }
    if (probe.manifest.version === state.listVersion && state.rulesApplied
        && state.keywordRulesApplied) {
      return;
    }

    // The whole generation, fetched together. Every artifact is verified
    // against the signed manifest before any is applied — verifying the
    // manifest alone proves only that someone signed a description of files.
    const artifacts = new Map();
    await Promise.all(GENERATION_ARTIFACTS.map(async (name) => {
      artifacts.set(name, await fetchBytes(`${LIST_BASE}/${name}`));
    }));
    const verified = await acceptList({
      manifestBytes, signatureHex, artifacts, heldVersion: state.listVersion,
    });
    if (!verified.ok) {
      console.error("[hisn] generation rejected:", verified.reason);
      return;
    }

    // Shape and plausibility, after the hashes. A validly signed list that
    // collapsed to a handful of rules or lost its vocabulary is a broken
    // build, and keeping yesterday's is strictly safer than applying it.
    const plan = planGeneration(verified.manifest, artifacts);
    if (!plan.ok) {
      console.error("[hisn] generation not installable:", plan.reason, "— keeping current");
      return;
    }

    const applied = await applyGeneration(plan);
    await setState({
      listVersion: verified.manifest.version,
      rulesApplied: applied.rules,
      keywordRulesApplied: applied.keywordRules,
      lastListUpdate: Date.now(),
    });
    console.info("[hisn] generation v%d applied — %d domain rules, %d keyword rules, %d terms",
      verified.manifest.version, applied.rules, applied.keywordRules, plan.terms.terms.length);
  } catch (err) {
    console.warn("[hisn] list update failed, keeping current list:",
      err?.message ?? err);
  }
}

// --------------------------------------------------------------------------
// Guards
// --------------------------------------------------------------------------

/** Entries in `next` that are not in `prev`. */
function added(next, prev) {
  const before = new Set(prev || []);
  return (next || []).filter((d) => !before.has(d));
}

/**
 * Refuse to relax settings while locked. Every path that could weaken
 * protection funnels through here.
 *
 * The list checks compare membership, not length. Counting was the obvious
 * version and it is wrong in the case that matters: swapping one allowed domain
 * for another leaves the count identical, and in strict mode — where the
 * allowlist is the only thing reachable at all — that swap is not a small
 * loosening, it is a complete bypass.
 */
async function guardedUpdate(rawPatch) {
  // Shape first, then direction. A patch naming a field outside USER_FIELDS —
  // `appPresent`, `failClosed`, `lastHeartbeat`, anything internal — is refused
  // whole, because each of those is a way to switch the fail-closed defence
  // off from a devtools console. See `validatePatch`.
  const checked = validatePatch(rawPatch);
  if (!checked.ok) return checked;
  const patch = checked.patch;

  const state = await getState();
  const access = settingsAccess(state, patch);
  if (!access.ok) return access;
  if (patch.customTerms) {
    patch.customTerms = [...new Set(patch.customTerms.map(normalize))];
    if (patch.customTerms.length > 200 || patch.customTerms.some((term) => term.replace(/\s/g, "").length < 4)) {
      return { ok: false, reason: "invalid-field", field: "customTerms" };
    }
  }
  if (restrictionsActive(state)) {
    const relaxing =
      (patch.mode && patch.mode === "off") ||
      (patch.mode === "blocklist" && state.mode === "strict") ||
      (patch.lockUntil !== undefined && patch.lockUntil < state.lockUntil) ||
      (patch.allowlist && added(patch.allowlist, state.allowlist).length > 0) ||
      // Dropping a custom block is the same move in the other direction.
      // options.js merges add-only before sending, but a UI-only guard is no
      // guard at all — anything can post this message from a devtools console.
      (patch.customBlocks &&
        added(state.customBlocks, patch.customBlocks).length > 0) ||
      (patch.customTerms && added(state.customTerms, patch.customTerms).length > 0) ||
      // Switching text scanning off, or making it less sensitive, are both
      // weakenings and both wait for the lock to end. Turning it on or raising
      // sensitivity is tightening and is always allowed — the same asymmetry
      // every other guard in this file enforces.
      (patch.inspectText === false && state.inspectText) ||
      (patch.textSensitivity !== undefined &&
        patch.textSensitivity < state.textSensitivity) ||
      // Exempting a host from the text layer is a loosening like any other, so
      // it waits for the lock to end — reportWrongBlock enforces the same rule
      // by queueing to `disputed` instead of writing `textAllow` while locked,
      // but this closes the raw `update` path a devtools console could post.
      (patch.textAllow && added(patch.textAllow, state.textAllow).length > 0) ||
      // Disowning a word (dropping it from scoring) is the same loosening at the
      // term level — reportWrongWord queues it while locked; this guards the
      // raw path, so a lock cannot be neutralised by ignoring "porn", "sex"…
      (patch.ignoreTerms && added(patch.ignoreTerms, state.ignoreTerms).length > 0);
    if (relaxing) {
      return { ok: false, reason: "locked", until: state.lockUntil };
    }
  }
  const next = await setState(patch);
  await applyRules(next);
  return { ok: true, state: next };
}

// --------------------------------------------------------------------------
// Wiring
// --------------------------------------------------------------------------

/** The two recurring jobs, and how often each runs. */
const ALARMS = { heartbeat: 1, listUpdate: 360 };

/**
 * Make sure both alarms exist. Runs on EVERY worker start, not only on install.
 *
 * They used to be created in `onInstalled` alone. Chrome documents that alarms
 * "may be cleared upon browser restart", and they do not come back when an
 * extension is disabled and re-enabled — neither of which fires
 * `onInstalled`. After either, the heartbeat simply stopped: no more polls of
 * the app, so no lock updates, no fail-closed detection, and — now that the
 * app closes a browser whose extension stops checking in during a lock — a
 * browser shut for an extension that was on the whole time.
 */
async function ensureAlarms() {
  for (const [name, periodInMinutes] of Object.entries(ALARMS)) {
    const existing = await chrome.alarms.get(name).catch(() => null);
    if (!existing || existing.periodInMinutes !== periodInMinutes) {
      await chrome.alarms.create(name, { periodInMinutes });
    }
  }
}

/**
 * Once per worker instance: restore the alarms and, if the app has not heard
 * from us for a while, check in now rather than at the next alarm. This is
 * the path a re-enabled extension takes — it fires no lifecycle event at all
 * — and checking in immediately is what tells the app's browser guard, within
 * seconds, that the extension is back.
 *
 * Throttled on `lastHeartbeat` because a worker is restarted for nearly every
 * event after thirty idle seconds, and each poll launches the bridge process.
 */
const BOOT_POLL_AFTER_MS = 30 * 1000;
const booted = (async () => {
  try {
    await ensureAlarms();
    const state = await getState();
    if (Date.now() - (state.lastHeartbeat || 0) > BOOT_POLL_AFTER_MS) {
      await transaction(pollNative);
    }
  } catch (err) {
    console.warn("[hisn] worker start-up check failed:", err?.message ?? err);
  }
})();

chrome.runtime.onInstalled.addListener(async () => {
  await transaction(async () => applyRules(await getState()));
  await ensureAlarms();
  await transaction(pollNative);
  await transaction(updateList);
});

chrome.runtime.onStartup.addListener(async () => {
  // Re-assert on every browser start. A service worker that never woke up is
  // a browser session with no rules applied.
  await transaction(async () => applyRules(await getState()));
  await transaction(pollNative);
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === "heartbeat") await transaction(pollNative);
  if (alarm.name === "listUpdate") await transaction(updateList);
});

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  (async () => {
    switch (msg?.type) {
      case "getState":
        sendResponse(await getState());
        break;
      case "update":
        sendResponse(await transaction(() => guardedUpdate(msg.patch || {})));
        break;
      case "forceSync":
        sendResponse({ ok: await transaction(pollNative) });
        break;
      case "scoreText":
        sendResponse(await scoreText(msg.zones || {}, _sender));
        break;
      case "reportWrongBlock":
        sendResponse(await transaction(() => reportWrongBlock(msg.host)));
        break;
      case "reportWrongWord":
        sendResponse(await transaction(() => reportWrongWord(msg.term)));
        break;
      case "resolveDisputed":
        sendResponse(await transaction(() => resolveDisputed(msg.entry)));
        break;
      default:
        sendResponse({ ok: false, reason: "unknown-message" });
    }
  })().catch((err) => {
    console.error("[hisn] message failed:", msg?.type, err?.message ?? err);
    sendResponse({ ok: false, reason: "internal-error" });
  });
  return true; // async response
});

export { applyRules, guardedUpdate, DEFAULT_STATE, resolveDisputed, ensureAlarms, booted, ALARMS };
