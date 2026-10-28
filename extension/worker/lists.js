/**
 * Signed list updates.
 *
 * The downloaded generation is the only artifact this extension accepts over
 * the network, and it is accepted only when every byte matches a manifest
 * signed by the pinned key. Downloads happen outside the state transaction;
 * only the install takes the lock. A failed update is a non-event: yesterday's
 * rules stay installed. A stale blocklist still blocks; an empty one does not.
 */

import { acceptList } from "../lib/verify.js";
import {
  GENERATION_ARTIFACTS, downloadedRuleIds, planGeneration,
} from "../lib/generation.js";
import { getState, setState } from "./state.js";
import { applyRules } from "./rules.js";
import { resetTermIndex } from "./inspection.js";

const LIST_BASE = "https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists";

/** A download that stalls (sleep, a captive portal, bad Wi-Fi) gives up
 *  rather than hanging the worker. */
const FETCH_TIMEOUT_MS = 60 * 1000;

async function fetchBytes(url) {
  const res = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
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
  resetTermIndex();
  return { rules: plan.blockRules.length, keywordRules: plan.keywordRules.length };
}

/** The version of the vocabulary this package shipped with. A fresh install
 *  or a wiped storage holds listVersion 0, and without this floor any older
 *  signed generation would be accepted over the newer bundled one. */
let bundledVersion = null;
async function bundledFloor() {
  if (bundledVersion === null) {
    try {
      const res = await fetch(chrome.runtime.getURL("seed/terms.json"));
      bundledVersion = Number((await res.json()).version) || 0;
    } catch { bundledVersion = 0; }
  }
  return bundledVersion;
}

/** Whether the downloaded rules the state claims are actually installed —
 *  counted from Chrome, not from our own record of having installed them. */
async function downloadedRulesIntact(state) {
  const installed = downloadedRuleIds(await chrome.declarativeNetRequest.getDynamicRules()).length;
  return installed > 0 && installed === (state.rulesApplied || 0) + (state.keywordRulesApplied || 0);
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
async function fetchGeneration() {
  const state = await getState();
  const heldVersion = Math.max(state.listVersion || 0, await bundledFloor());
  const [manifestBytes, sigBytes] = await Promise.all([
    fetchBytes(`${LIST_BASE}/manifest.json`),
    fetchBytes(`${LIST_BASE}/manifest.json.sig`),
  ]);
  const signatureHex = new TextDecoder().decode(sigBytes).trim();

  const probe = await acceptList({ manifestBytes, signatureHex, artifacts: new Map(), heldVersion });
  if (!probe.ok) {
    console.error("[hisn] list rejected:", probe.reason);
    return { ok: false, reason: "signature" };
  }
  if (probe.manifest.version === state.listVersion && await downloadedRulesIntact(state)) {
    return { ok: true, current: true };
  }

  // The whole generation, fetched together. Every artifact is verified
  // against the signed manifest before any is applied — verifying the
  // manifest alone proves only that someone signed a description of files.
  const artifacts = new Map();
  await Promise.all(GENERATION_ARTIFACTS.map(async (name) => {
    artifacts.set(name, await fetchBytes(`${LIST_BASE}/${name}`));
  }));
  const verified = await acceptList({ manifestBytes, signatureHex, artifacts, heldVersion });
  if (!verified.ok) {
    console.error("[hisn] generation rejected:", verified.reason);
    return { ok: false, reason: "generation" };
  }

  // Shape and plausibility, after the hashes. A validly signed list that
  // collapsed to a handful of rules or lost its vocabulary is a broken
  // build, and keeping yesterday's is strictly safer than applying it.
  const plan = planGeneration(verified.manifest, artifacts);
  if (!plan.ok) {
    console.error("[hisn] generation not installable:", plan.reason, "— keeping current");
    return { ok: false, reason: "not-installable" };
  }
  return { ok: true, current: false, plan, version: verified.manifest.version };
}

/** Install a verified generation — the only part that takes the lock. */
async function installGeneration({ plan, version }) {
  const state = await getState();
  if (version < (state.listVersion || 0)) return;   // a newer one landed meanwhile
  const applied = await applyGeneration(plan);
  await setState({
    listVersion: version,
    rulesApplied: applied.rules,
    keywordRulesApplied: applied.keywordRules,
    lastListUpdate: Date.now(),
  });
  await applyRules(await getState());
  console.info("[hisn] generation v%d applied — %d domain rules, %d keyword rules, %d terms",
    version, applied.rules, applied.keywordRules, plan.terms.terms.length);
}

/**
 * Download and verify the next generation, with NO lock held.
 *
 * This used to run inside `transaction` from start to finish, multi-megabyte
 * downloads included, with no timeout — and every heartbeat queued behind it.
 * One stalled download stopped the extension checking in, and the browser
 * guard then closed a browser whose extension was working. Only the install
 * needs the lock now.
 */
let updateInFlight = null;

/**
 * Check for a newer signed generation now. One update at a time: the automatic
 * six-hour alarm and a person pressing "Update now" must not download and
 * install twice, and the second caller should see the first one's answer.
 *
 * Returns `{ ok, updated, version?, reason? }`. A failed or refused update is
 * not an error state for protection: the previous signed generation stays in
 * force, which is why the reply says `updated: false` rather than pretending
 * the list changed.
 */
export function updateListNow(transaction) {
  if (updateInFlight) return updateInFlight;
  updateInFlight = (async () => {
    try {
      const next = await fetchGeneration();
      if (!next.ok) return { ok: false, updated: false, reason: next.reason };
      if (next.current) {
        return { ok: true, updated: false, reason: "current", version: (await getState()).listVersion };
      }
      await transaction(() => installGeneration(next));
      return { ok: true, updated: true, version: (await getState()).listVersion };
    } catch (err) {
      console.warn("[hisn] list update failed, keeping current list:",
        err?.message ?? err);
      return { ok: false, updated: false, reason: "update-failed" };
    } finally {
      updateInFlight = null;
    }
  })();
  return updateInFlight;
}

/** The alarm path: fetch and install if there is a newer generation. */
export async function updateList(transaction) {
  await updateListNow(transaction);
}
