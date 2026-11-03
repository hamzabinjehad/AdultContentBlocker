/**
 * The person's own rules, in a file they own.
 *
 * `docs/END_GOAL_PLAN.md` Workstream A1 asks for one custom list on every
 * device the person uses, without an account and without a server. This module
 * is the browser half of that: a small, versioned JSON document holding only
 * the hand-maintained lists, plus a merge that can never remove an entry.
 *
 * WHAT THE FILE IS
 * ----------------
 *   schema   "hisn.rules" — a name, so another JSON file is not mistaken for
 *                           one of these
 *   version  1
 *   customBlocks, allowlist, customTerms, ignoreTerms, textAllow
 *
 * Nothing else. Not the blocking mode, the lock deadline, the installed list
 * version, the pause tally, the browser's language, or any page, host or time
 * the person visited. A settings file is portable between browsers; the fields
 * that make one browser's protection state are not a preference and would be a
 * surprise if imported.
 *
 * WHY IMPORT ONLY MERGES
 * ----------------------
 * Import is an ADD operation. It never removes a block the receiving browser
 * already has, and it never drops a word or a correction. That makes it safe
 * during a lock without a special case for the tightening lists, and it means
 * a file exported from a more permissive device cannot quietly undo a stricter
 * one. Removal stays a deliberate, local act — and, during a lock, one that
 * waits.
 *
 * The allowlist and the two correction lists are still loosening when they
 * GROW, so a locked browser withholds their additions and applies the rest.
 * `guardedUpdate` in `worker/updates.js` enforces the same direction again;
 * this module deciding it first is UX, not the guard.
 *
 * Pure: no `chrome.*`, no DOM, no network. That is what lets the jsc suite
 * drive every branch without a browser.
 */

import { canonicalHost } from "./policy.js";
import { normalize } from "./normalize.js";

export const FORMAT = "hisn.rules";
export const VERSION = 1;

/** The same character ceiling the options page applies to a picked file. A
 *  rules file is a few kilobytes; anything near this is not one. */
export const MAX_FILE_BYTES = 512 * 1024;

/** `customTerms` is capped at 200 by `guardedUpdate`. Import respects the cap
 *  rather than building a patch the worker must refuse whole. */
export const MAX_CUSTOM_TERMS = 200;

/** `USER_FIELDS` in policy.js refuses any list longer than this. */
const MAX_LIST = 5000;
const MAX_TERM_LENGTH = 100;
/** A term shorter than four non-space characters is not a phrase the scorer
 *  can use, and `guardedUpdate` refuses the customTerms patch that contains it. */
const MIN_TERM_CHARS = 4;

/** The five lists that travel. `exportDocument` writes exactly these. */
export const LIST_FIELDS = Object.freeze([
  "customBlocks", "allowlist", "customTerms", "ignoreTerms", "textAllow",
]);

const HOST_FIELDS = new Set(["customBlocks", "allowlist", "textAllow"]);
const LOOSENING_FIELDS = new Set(["allowlist", "ignoreTerms", "textAllow"]);

function invalid() {
  return { ok: false, reason: "invalid" };
}

/** Canonical hosts, de-duplicated, sorted. A file that is stable byte-for-byte
 *  is easier to compare, and the order carries no meaning. */
function hostList(values) {
  const seen = new Set();
  for (const value of Array.isArray(values) ? values : []) {
    const host = typeof value === "string" ? canonicalHost(value) : null;
    if (host) seen.add(host);
  }
  return [...seen].sort();
}

/** Terms in the scorer's spelling, de-duplicated and sorted. Normalising here
 *  and not only on import keeps `Ticket` and `ticket` from travelling as two
 *  entries that match the same page. */
function termList(values) {
  const seen = new Set();
  for (const value of Array.isArray(values) ? values : []) {
    if (typeof value !== "string") continue;
    const term = normalize(value);
    if (term && term.length <= MAX_TERM_LENGTH) seen.add(term);
  }
  return [...seen].sort();
}

/**
 * The document for `state`. Missing lists become empty arrays so the shape of
 * the file does not depend on which features the person has used.
 */
export function exportDocument(state) {
  return {
    schema: FORMAT,
    version: VERSION,
    customBlocks: hostList(state?.customBlocks),
    allowlist: hostList(state?.allowlist),
    customTerms: termList(state?.customTerms),
    ignoreTerms: termList(state?.ignoreTerms),
    textAllow: hostList(state?.textAllow),
  };
}

/** Pretty-printed JSON with a trailing newline, ready for a downloaded file. */
export function exportJSON(state) {
  return JSON.stringify(exportDocument(state), null, 2) + "\n";
}

/**
 * Read and validate a rules file.
 *
 * `{ ok: true, document, skipped }` — `skipped.hosts` and `skipped.terms` count
 * entries that could never match, so the UI can say what was ignored instead
 * of silently importing a shorter list. A wrong schema, version, field type or
 * an over-long list rejects the whole file: those are signs it is not a Hisn
 * file, not small mistakes to paper over.
 */
export function parseDocument(text) {
  if (typeof text !== "string" || text.length > MAX_FILE_BYTES) {
    return { ok: false, reason: text?.length > MAX_FILE_BYTES ? "too-large" : "invalid" };
  }
  let doc;
  try {
    doc = JSON.parse(text);
  } catch {
    return invalid();
  }
  if (!doc || typeof doc !== "object" || Array.isArray(doc)) return invalid();
  if (doc.schema !== FORMAT || doc.version !== VERSION) return invalid();

  const document = {};
  const skipped = { hosts: 0, terms: 0 };
  for (const field of LIST_FIELDS) {
    const raw = doc[field] === undefined ? [] : doc[field];
    if (!Array.isArray(raw) || raw.length > MAX_LIST) return invalid();
    const seen = new Set();
    const values = [];
    for (const value of raw) {
      if (HOST_FIELDS.has(field)) {
        const host = typeof value === "string" ? canonicalHost(value) : null;
        if (!host) { skipped.hosts++; continue; }
        if (!seen.has(host)) { seen.add(host); values.push(host); }
      } else {
        const term = typeof value === "string" ? normalize(value) : "";
        const compact = term.replace(/\s/g, "");
        if (!term || term.length > MAX_TERM_LENGTH || compact.length < MIN_TERM_CHARS) {
          skipped.terms++; continue;
        }
        if (!seen.has(term)) { seen.add(term); values.push(term); }
      }
    }
    document[field] = values.sort();
  }
  return { ok: true, document, skipped };
}

/** The entries in `incoming` that `current` does not already hold, in order. */
function additions(current, incoming) {
  const before = new Set(current || []);
  return (incoming || []).filter((value) => !before.has(value));
}

/**
 * The patch an import would send, and what it would do.
 *
 * `locked` is the caller's `restrictionsActive(state)` reading. It withholds
 * additions to the allowlist and the two correction lists, because growing
 * either while a lock runs is a loosening. Blocks and words are additions in
 * the tightening direction and apply normally.
 *
 * Returns `{ ok: true, patch, report }`. `patch` is null when every entry was
 * already present (or every addition was withheld): sending an empty patch
 * would bump the worker's revision for nothing.
 */
export function buildImport(document, state, { locked = false } = {}) {
  const current = state || {};
  const patch = {};
  const report = {
    addedBlocks: 0, addedAllows: 0, addedTerms: 0, addedIgnore: 0, addedTextAllow: 0,
    withheld: 0, capped: 0, skippedHosts: 0, skippedTerms: 0,
  };

  const mergeHosts = (field) => {
    const before = hostList(current[field]);
    const add = additions(before, document[field]);
    if (!add.length) return;
    if (locked && LOOSENING_FIELDS.has(field)) { report.withheld += add.length; return; }
    patch[field] = [...before, ...add].sort();
    if (field === "customBlocks") report.addedBlocks = add.length;
    else if (field === "allowlist") report.addedAllows = add.length;
    else report.addedTextAllow = add.length;
  };

  for (const field of ["customBlocks", "allowlist", "textAllow"]) mergeHosts(field);

  const currentTerms = new Set(current.customTerms || []);
  const termAdds = additions([...currentTerms], document.customTerms);
  const room = Math.max(0, MAX_CUSTOM_TERMS - currentTerms.size);
  const terms = termAdds.slice(0, room);
  report.capped = termAdds.length - terms.length;
  if (terms.length) {
    patch.customTerms = [...currentTerms, ...terms].sort();
    report.addedTerms = terms.length;
  }

  const ignored = additions(current.ignoreTerms, document.ignoreTerms);
  if (ignored.length) {
    if (locked) report.withheld += ignored.length;
    else {
      patch.ignoreTerms = [...(current.ignoreTerms || []), ...ignored].sort();
      report.addedIgnore = ignored.length;
    }
  }

  report.total = report.addedBlocks + report.addedAllows + report.addedTerms
    + report.addedIgnore + report.addedTextAllow;
  return { ok: true, patch: Object.keys(patch).length ? patch : null, report };
}

/**
 * Validate a file and build its merge in one step.
 *
 * An app-managed browser refuses the import: the Mac app owns the lists and
 * would overwrite them on its next heartbeat, so claiming success would be a
 * lie. The corrections stay editable by hand in that mode; a file import is
 * not the place to mix the two authorities.
 */
export function importRules(text, state, options = {}) {
  const parsed = parseDocument(text);
  if (!parsed.ok) return parsed;
  if (state?.appPresent) return { ok: false, reason: "app-managed" };
  const built = buildImport(parsed.document, state, options);
  return { ...built, skipped: parsed.skipped };
}
