/**
 * Guarded settings updates — the only path a UI patch may take into state.
 *
 * A patch is checked in three directions before it is applied: its SHAPE (a
 * closed field list with a type/shape per field), its AUTHORITY (the native app
 * owns policy once it has answered), and its DIRECTION (nothing that weakens a
 * running lock). Any message handler that skipped one of these would be a way
 * out of a commitment from a devtools console, so the checks live together
 * where a future caller cannot pick two of the three.
 */

import { validatePatch, visualPolicyWeakens } from "../lib/policy.js";
import { normalize } from "../lib/normalize.js";
import { restrictionsActive, settingsAccess } from "../lib/settings.js";
import { getState, setState } from "./state.js";
import { applyRules } from "./rules.js";

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
export async function guardedUpdate(rawPatch) {
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
      visualPolicyWeakens(state, patch) ||
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
  return { ok: true, state: await getState() };
}
