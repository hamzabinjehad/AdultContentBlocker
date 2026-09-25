/**
 * The decision contract — the browser half.
 *
 * Two enforcement points judge the same hostname: the macOS socket filter
 * (`BlocklistStore.swift`) and this extension's declarativeNetRequest rules.
 * Nothing at build time forces them to agree, and a disagreement is silent: a
 * site reachable in Chrome and dropped by the Mac, or the reverse, with no
 * error anywhere. Before this file they disagreed twice — a domain on both hand
 * lists was ALLOWED here and BLOCKED there, and the allowlist overrode the
 * published list there but not here.
 *
 * `docs/POLICY.md` states the contract in prose. `blocklist/terms/policy_cases.json`
 * states it as cases, and both suites assert every case: Swift against the
 * store, JavaScript against `decide` below — a reference evaluator over the
 * exact rules `policyRules` hands to Chrome. Change the contract in the
 * fixture first; then both suites tell you which client is wrong.
 *
 * THE RULES, IN ONE PLACE
 * -----------------------
 *  1. The published list, the URL keyword rules and the hand-added blocks
 *     always apply; a custom block is never loosened by anything.
 *  2. The allowlist is an allowance in EVERY mode. It overrides the published
 *     list and the keyword rules — it is the one escape valve a false
 *     positive has during a lock, when the lists cannot otherwise change —
 *     and in strict mode it is additionally the only thing reachable. (The
 *     browser used to honour it in strict mode only; the Mac always did both.)
 *  3. When an allowlist entry and a custom block both cover a host, the more
 *     specific entry wins (`mail.google.com` over `google.com`); at equal
 *     specificity the block wins. Contradicting yourself resolves to the safer
 *     answer.
 *  4. A lock ends at its EFFECTIVE deadline — the app's, including a matured
 *     self-release — and the browser only ever learns that number from the
 *     app. The browser's own clock decides nothing but "has that moment
 *     passed".
 *  5. Losing the app during a lock is treated as tampering: after
 *     `HEARTBEAT_GRACE_MS` of silence the browser behaves as strict mode with
 *     the last allowlist it was given. Before the app has ever answered,
 *     silence means browser-only and changes nothing.
 *  6. Strict mode covers EMBEDDED traffic, not only navigations. Every
 *     resource type is denied unless its destination is allowlisted, with one
 *     carve-out: a page on an allowlisted site may load its PLUMBING —
 *     scripts, styles, fonts, images, fetches — from anywhere, because
 *     without that no modern site renders. It may not load CONTENT from an
 *     unapproved host: frames, media, objects, WebSockets. So an allowed
 *     forum still cannot embed an unapproved video. Custom blocks deny every
 *     resource type in every mode, as the Mac's filter does for every socket.
 *     (The catch-all used to cover top-level navigations only; an allowed
 *     page could embed anything.)
 *
 * HOW SPECIFICITY BECOMES A PRIORITY
 * ----------------------------------
 * DNR has no "most specific wins"; it has integer priorities. So the hand
 * lists are grouped by label count and each group gets its own rule at
 * `PRIORITY_HAND + 2 * labels`, blocks one higher than allows. A request
 * matches every group whose entry is the host or a parent; the highest
 * priority among them is the most specific entry, and a tie between an allow
 * and a block at the same depth goes to the block. One rule per depth rather
 * than one per domain keeps the dynamic-rule budget untouched.
 *
 * This module is pure. It must stay importable from `jsc` with no `chrome`.
 */

/** How long the native app may stay silent during a lock before the browser
 *  assumes it was removed and clamps down. */
export const HEARTBEAT_GRACE_MS = 5 * 60 * 1000;

// Dynamic rule ids owned by applyRules. Everything below RULE_DOWNLOADED_BASE
// is cleared and rebuilt on each lock-state change; the downloaded list lives
// above it and is never touched by that path.
export const RULE_STRICT_BLOCK_ALL = 1;        // documents → block page
export const RULE_STRICT_BLOCK_EMBEDDED = 2;   // everything else → dropped
export const RULE_STRICT_PLUMBING = 3;         // an allowed page's own assets
export const RULE_ALLOW_BASE = 200;            // + label count
export const RULE_CUSTOM_BLOCK_BASE = 300;     // + label count, documents
export const RULE_CUSTOM_BLOCK_EMBEDDED_BASE = 400;   // + label count, the rest
export const RULE_DOWNLOADED_BASE = 10000;

/**
 * The priority ladder, lowest first. Chrome applies the highest-priority
 * matching rule, and an allow beats a block only at EQUAL priority.
 *
 *   1  strict catch-all        — the floor: everything unlisted is refused
 *   2  strict plumbing allow   — an allowlisted page may load its scripts,
 *                                images and fetches from anywhere…
 *   3  published domain list   — …but never from a listed domain. The list sat
 *                                at 1 with the catch-all, so the carve-out
 *                                (then at 1000) outranked it: in strict mode an
 *                                allowed page could pull images and fetched
 *                                video from listed adult domains that
 *                                blocklist mode refused. Tightening loosened.
 *   4  published URL keywords
 *   1000+ the hand lists (see the header) — above everything published.
 */
export const PRIORITY_CATCHALL = 1;
export const PRIORITY_PLUMBING = 2;
export const PRIORITY_LIST = 3;
export const PRIORITY_KEYWORDS = 4;
/** Base priority of the hand lists; see the header. */
export const PRIORITY_HAND = 1000;
const MAX_LABELS = 32;

/** What a page IS: the top document and any frame in it. */
const DOCUMENT_TYPES = ["main_frame", "sub_frame"];
/** What an allowed page may fetch from anywhere to render itself. */
const PLUMBING_TYPES = ["script", "stylesheet", "font", "image", "xmlhttprequest", "ping", "other"];
/** What an allowed page may NOT pull in from an unapproved host: content. */
const CONTENT_TYPES = ["sub_frame", "media", "object", "websocket", "webtransport", "webbundle"];
const EMBEDDED_TYPES = [...new Set([...PLUMBING_TYPES, ...CONTENT_TYPES, "csp_report"])];
const ALL_RESOURCE_TYPES = [...new Set([...DOCUMENT_TYPES, ...EMBEDDED_TYPES])];
/** What the published domain list covers (build.py writes exactly these). */
const LIST_RESOURCE_TYPES = [...DOCUMENT_TYPES, "script", "image", "media", "xmlhttprequest",
                             "stylesheet", "font", "object", "websocket", "other"];
export const RESOURCE_TYPES = Object.freeze({
  documents: DOCUMENT_TYPES, plumbing: PLUMBING_TYPES, content: CONTENT_TYPES, all: ALL_RESOURCE_TYPES,
  list: LIST_RESOURCE_TYPES,
});

// --------------------------------------------------------------------------
// Lock state
// --------------------------------------------------------------------------

export function isLocked(state, now = Date.now()) {
  return (state.lockUntil || 0) > now;
}

/** Strict enforcement in the browser: asked for, or imposed by fail-closed. */
export function effectiveStrict(state) {
  return state.mode === "strict" || state.failClosed === true;
}

/**
 * Whether silence from the native app should clamp the browser down NOW.
 *
 * Pure so the four gates can be pinned: never heard from the app (browser-only
 * install), no lock running, still inside the grace period, already clamped.
 */
export function shouldFailClosed(state, now = Date.now()) {
  if (!state.appPresent) return false;
  if (!isLocked(state, now)) return false;
  if (now - (state.lastHeartbeat || 0) < HEARTBEAT_GRACE_MS) return false;
  return !state.failClosed;
}

// --------------------------------------------------------------------------
// Hostnames
// --------------------------------------------------------------------------

/**
 * Reduce whatever was typed to a bare host, or null.
 *
 * Mirrors `SiteLists.normalize` in the app, label for label, so an entry the
 * app accepts is one this side accepts and vice versa: scheme, path, userinfo
 * and port stripped; trailing dots and a leading `www.` dropped; ASCII labels
 * only (DNR's `requestDomains` rejects anything else); a numeric last label
 * refused because it is an address, which the browser cannot express.
 */
export function canonicalHost(raw) {
  let s = String(raw ?? "").trim().toLowerCase();
  if (!s) return null;
  const scheme = s.indexOf("://");
  if (scheme >= 0) s = s.slice(scheme + 3);
  const slash = s.indexOf("/");
  if (slash >= 0) s = s.slice(0, slash);
  const at = s.lastIndexOf("@");
  if (at >= 0) s = s.slice(at + 1);
  const colon = s.indexOf(":");
  if (colon >= 0) s = s.slice(0, colon);
  while (s.endsWith(".")) s = s.slice(0, -1);
  if (s.startsWith("www.")) s = s.slice(4);

  if (!s || s.length > 253) return null;
  const labels = s.split(".");
  if (labels.length < 2) return null;
  for (const label of labels) {
    if (label.length < 1 || label.length > 63) return null;
    if (label.startsWith("-") || label.endsWith("-")) return null;
    if (!/^[a-z0-9-]+$/.test(label)) return null;
  }
  if (/^[0-9]+$/.test(labels[labels.length - 1])) return null;
  return s;
}

/** Depth of a host, capped so a pathological entry cannot climb out of the
 *  priority band the hand lists own. */
function depth(host) {
  return Math.min(MAX_LABELS, host.split(".").length);
}

/** Canonical, de-duplicated hosts grouped by depth. Entries that do not
 *  canonicalise are dropped: they could never match a request anyway. */
function groupByDepth(list) {
  const groups = new Map();
  const seen = new Set();
  for (const raw of list || []) {
    const host = canonicalHost(raw);
    if (!host || seen.has(host)) continue;
    seen.add(host);
    const d = depth(host);
    if (!groups.has(d)) groups.set(d, []);
    groups.get(d).push(host);
  }
  return groups;
}

// --------------------------------------------------------------------------
// Rules
// --------------------------------------------------------------------------

/**
 * The dynamic rules the browser enforces for a given state — everything
 * `applyRules` installs below RULE_DOWNLOADED_BASE. This is the contract in
 * executable form; `decide` evaluates the same array.
 */
export function policyRules(state) {
  const rules = [];
  const strict = effectiveStrict(state);

  if (strict) {
    // Rule 6. Documents go to the block page so the person sees why; every
    // other kind of request is simply dropped — a redirect would hand an
    // extension URL to a <video> or a fetch, which helps nobody.
    rules.push({
      id: RULE_STRICT_BLOCK_ALL,
      priority: PRIORITY_CATCHALL,
      action: {
        type: "redirect",
        redirect: { extensionPath: "/blocked.html?reason=strict" },
      },
      condition: { urlFilter: "*", resourceTypes: DOCUMENT_TYPES },
    });
    rules.push({
      id: RULE_STRICT_BLOCK_EMBEDDED,
      priority: PRIORITY_CATCHALL,
      action: { type: "block" },
      condition: { urlFilter: "*", resourceTypes: EMBEDDED_TYPES },
    });
    // The plumbing carve-out: a request INITIATED by an allowlisted page, of
    // a type a page needs to render, may go anywhere. Content types are not
    // in the list, so a frame, a video or a socket still needs its own
    // destination allowlisted. Below the published list (see the ladder), so
    // a listed destination stays blocked, and below every custom block.
    const initiators = [...groupByDepth(state.allowlist).values()].flat();
    if (initiators.length) {
      rules.push({
        id: RULE_STRICT_PLUMBING,
        priority: PRIORITY_PLUMBING,
        action: { type: "allow" },
        condition: { initiatorDomains: initiators, resourceTypes: PLUMBING_TYPES },
      });
    }
  }

  // Rule 2: the allowlist is an allowance in every mode. Above the published
  // rulesets (PRIORITY_LIST) and the strict catch-all, so it overrides both;
  // below the custom block of the same depth, so it never overrides that.
  for (const [d, domains] of groupByDepth(state.allowlist)) {
    rules.push({
      id: RULE_ALLOW_BASE + d,
      priority: PRIORITY_HAND + 2 * d,
      action: { type: "allow" },
      condition: { requestDomains: domains, resourceTypes: ALL_RESOURCE_TYPES },
    });
  }

  // Rules 1 and 3: custom blocks apply in every mode and to every resource
  // type — the Mac drops every socket to a custom-blocked host, and so does
  // this — one priority above the allow rule of the same depth. Documents go
  // to the block page; anything embedded is dropped.
  for (const [d, domains] of groupByDepth(state.customBlocks)) {
    rules.push({
      id: RULE_CUSTOM_BLOCK_BASE + d,
      priority: PRIORITY_HAND + 2 * d + 1,
      action: {
        type: "redirect",
        redirect: { extensionPath: "/blocked.html?reason=custom" },
      },
      condition: { requestDomains: domains, resourceTypes: DOCUMENT_TYPES },
    });
    rules.push({
      id: RULE_CUSTOM_BLOCK_EMBEDDED_BASE + d,
      priority: PRIORITY_HAND + 2 * d + 1,
      action: { type: "block" },
      condition: { requestDomains: domains, resourceTypes: EMBEDDED_TYPES },
    });
  }

  return rules;
}

function coveredBy(list, host) {
  return list.some((d) => host === d || host.endsWith("." + d));
}

function ruleMatches(rule, host, resourceType, initiator) {
  const c = rule.condition;
  if (c.resourceTypes && !c.resourceTypes.includes(resourceType)) return false;
  if (c.initiatorDomains && !(initiator && coveredBy(c.initiatorDomains, initiator))) return false;
  if (c.requestDomains) return coveredBy(c.requestDomains, host);
  // No URL condition at all means any URL, as it does for Chrome.
  return c.urlFilter === undefined || c.urlFilter === "*";
}

/**
 * What the browser decides for a document request to `host`, given `state`.
 *
 * A reference evaluator with Chrome's precedence: the highest-priority
 * matching rule wins, and among equals an `allow` beats a block or redirect.
 * `listed` stands in for the published rulesets — the domain list blocks a
 * listed host for documents and every embedded type alike, at PRIORITY_LIST.
 * Returns "allow" or "block".
 *
 * Not on the request path — Chrome evaluates the real rules — but it is what
 * the shared fixture is asserted against, so it must model the rules honestly.
 */
export function decide(state, hostname,
                       { listed = false, resourceType = "main_frame", initiator = null } = {}) {
  const host = String(hostname || "").toLowerCase().replace(/\.$/, "");
  const from = initiator ? String(initiator).toLowerCase().replace(/\.$/, "") : null;
  const rules = policyRules(state);
  if (listed) {
    rules.push({
      id: RULE_DOWNLOADED_BASE,
      priority: PRIORITY_LIST,
      action: { type: "block" },
      condition: { urlFilter: "*", resourceTypes: LIST_RESOURCE_TYPES },
    });
  }
  let best = null;
  for (const r of rules) {
    if (!ruleMatches(r, host, resourceType, from)) continue;
    if (!best || r.priority > best.priority
        || (r.priority === best.priority && r.action.type === "allow")) {
      best = r;
    }
  }
  if (!best) return "allow";
  return best.action.type === "allow" ? "allow" : "block";
}

// --------------------------------------------------------------------------
// Pages already open
// --------------------------------------------------------------------------

/**
 * Which open tabs must be sent to the block page NOW.
 *
 * DNR judges requests as they are made. A page that was open before a lock
 * began — a video already streaming, a feed already loaded — makes no new
 * document request, so the strict catch-all never sees it, and it can stay
 * up for as long as the person keeps that tab. So when the rules tighten,
 * the worker walks the open tabs and navigates the ones the policy would
 * now refuse. Only the hand lists and strict mode can be judged here: the
 * published list lives in Chrome's static rulesets, which the worker cannot
 * query, so `listed` is false and a listed-but-not-custom-blocked tab in
 * blocklist mode is left alone (its next navigation is caught as before).
 *
 * Returns `[{ id, reason }]`. Pure; `enforceOpenTabs` in background.js does
 * the navigating.
 */
export function tabsToBlock(tabs, state) {
  const out = [];
  for (const tab of tabs || []) {
    // A regex rather than `new URL`, so this stays pure and runs under jsc.
    // Only http(s) tabs are the policy's business — extension pages, the
    // new-tab page, file: and chrome: URLs are left alone.
    const m = /^https?:\/\/(?:[^@/?#]*@)?([^/:?#]+)/i.exec(String(tab?.url || ""));
    if (!m) continue;
    const host = m[1];
    if (decide(state, host, { listed: false }) === "block") {
      const custom = coveredBy((state.customBlocks || []).map(canonicalHost).filter(Boolean),
                               host.toLowerCase().replace(/\.$/, ""));
      out.push({ id: tab.id, reason: custom ? "custom" : "strict" });
    }
  }
  return out;
}

// --------------------------------------------------------------------------
// Settings schema
// --------------------------------------------------------------------------

/**
 * The fields a settings message may touch, and nothing else.
 *
 * `guardedUpdate` used to merge whatever keys a patch carried. That let a
 * message posted from a devtools console overwrite INTERNAL state — set
 * `appPresent` to false and the fail-closed defence never fires again; bump
 * `lastHeartbeat` and the app is never "silent"; clear `failClosed` and a
 * clamp lifts. Every one of those is a way out of a lock. The schema is an
 * allowlist of fields, each with a shape; a patch naming any other field is
 * refused whole.
 */
export const USER_FIELDS = Object.freeze({
  mode:            { kind: "enum", values: ["off", "blocklist", "strict"] },
  lockUntil:       { kind: "epochMs" },
  allowlist:       { kind: "hosts" },
  customBlocks:    { kind: "hosts" },
  textAllow:       { kind: "hosts" },
  customTerms:     { kind: "terms" },
  ignoreTerms:     { kind: "terms" },
  inspectText:     { kind: "bool" },
  textSensitivity: { kind: "int", min: 0, max: 100 },
});

const MAX_LIST = 5000;
const MAX_TERM = 100;
const MAX_EPOCH_MS = 8.64e15;   // the largest instant Date can represent

/** One field's value in canonical form, or undefined if it is not acceptable. */
export function coerceField(name, value) {
  const spec = USER_FIELDS[name];
  if (!spec) return undefined;
  switch (spec.kind) {
    case "enum":
      return spec.values.includes(value) ? value : undefined;
    case "bool":
      return typeof value === "boolean" ? value : undefined;
    case "int":
      return Number.isInteger(value) && value >= spec.min && value <= spec.max
        ? value : undefined;
    case "epochMs":
      return Number.isInteger(value) && value >= 0 && value <= MAX_EPOCH_MS
        ? value : undefined;
    case "hosts": {
      if (!Array.isArray(value) || value.length > MAX_LIST) return undefined;
      const out = [];
      const seen = new Set();
      for (const v of value) {
        if (typeof v !== "string") return undefined;
        const host = canonicalHost(v);
        if (!host) return undefined;
        if (!seen.has(host)) { seen.add(host); out.push(host); }
      }
      return out;
    }
    case "terms": {
      if (!Array.isArray(value) || value.length > MAX_LIST) return undefined;
      const out = [];
      const seen = new Set();
      for (const v of value) {
        if (typeof v !== "string") return undefined;
        const term = v.trim();
        if (!term || term.length > MAX_TERM) return undefined;
        if (!seen.has(term)) { seen.add(term); out.push(term); }
      }
      return out;
    }
    default:
      return undefined;
  }
}

/**
 * Validate a settings patch against `USER_FIELDS`.
 *
 * Returns `{ ok: true, patch }` with every value canonicalised, or
 * `{ ok: false, reason, field }`. Refuses the whole patch on the first bad
 * field rather than applying the good ones: a partial write is exactly the
 * kind of half-state that is hard to reason about afterwards, and the caller
 * is a UI that can simply resend.
 */
export function validatePatch(patch) {
  if (!patch || typeof patch !== "object" || Array.isArray(patch)) {
    return { ok: false, reason: "invalid-patch" };
  }
  const clean = {};
  for (const [name, value] of Object.entries(patch)) {
    if (!Object.prototype.hasOwnProperty.call(USER_FIELDS, name)) {
      return { ok: false, reason: "unknown-field", field: name };
    }
    const v = coerceField(name, value);
    if (v === undefined) return { ok: false, reason: "invalid-field", field: name };
    clean[name] = v;
  }
  return { ok: true, patch: clean };
}

/**
 * The fields the native bridge is allowed to set, shaped the same way.
 *
 * The app is the authority, but its reply still crosses a pipe as JSON, and an
 * older or broken bridge can send a field of the wrong shape. Presence rule
 * unchanged from before: a field the reply does not mention is left alone. A
 * field it mentions with an unacceptable value is ALSO left alone — a
 * malformed list is not an empty list.
 */
export const BRIDGE_FIELDS = Object.freeze([
  "mode", "allowlist", "customBlocks", "customTerms", "inspectText", "textSensitivity",
]);

export function bridgePatch(reply) {
  const patch = {};
  for (const name of BRIDGE_FIELDS) {
    if (!(name in reply)) continue;
    const v = coerceField(name, reply[name]);
    if (v !== undefined) patch[name] = v;
  }
  return patch;
}
