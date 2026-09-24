/**
 * The popup's two lines, pinned state by state.
 *
 * Above all: no reply from the worker must never read as protection. The
 * old popup rendered `state || {}`, and `{}` — every field missing — produced
 * "Protecting". That is the one dishonesty a status display exists to
 * prevent, and the first test here is the one that would have caught it.
 */

import { describeStatus } from "../lib/status.js";
import { HEARTBEAT_GRACE_MS } from "../lib/policy.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

const NOW = 1_800_000_000_000;
const when = (ms) => `T${ms}`;
const describe = (state, extra = {}) =>
  describeStatus(state, { now: NOW, formatWhen: when, ...extra });

const base = {
  mode: "off", lockUntil: 0, appPresent: false, lastHeartbeat: 0,
  failClosed: false, listVersion: 0, rulesApplied: 0,
  inspectText: true, textSensitivity: 50,
};

// ── no reply is not a status ───────────────────────────────────────────────
for (const missing of [undefined, null, "", 0]) {
  const s = describe(missing, { version: "1.0.0" });
  check(s.protection.level === "unknown", `no reply → unknown (${String(missing)})`);
  check(s.protection.headline === "Status unavailable", "no reply headline");
  check(!/active/i.test(s.protection.headline), "no reply never says active");
  check(s.lock.locked === false && /unknown/.test(s.lock.headline),
        "no reply → lock unknown, not 'no lock'");
  check(s.details.length === 1 && s.details[0][1] === "1.0.0",
        "no reply still shows the extension version");
}

// ── browser-only install ───────────────────────────────────────────────────
{
  const s = describe(base);
  check(s.protection.level === "active", "standalone: active");
  check(/this browser only/i.test(s.protection.detail), "standalone: says browser-only");
  check(s.lock.locked === false && s.lock.headline === "No active lock",
        "standalone: no lock");
  check(/need the Hisn app/.test(s.lock.detail), "standalone: locks need the app");
  check(s.notices.length === 0, "standalone: no warnings — not a fault");
  check(s.details.find(([k]) => k === "Hisn app")[1] === "not connected",
        "standalone: missing connection does not prove app is absent");
  check(s.details.find(([k]) => k === "Block list")[1] === "bundled list only",
        "standalone: no downloaded rules → bundled only, not a version number");
}

// ── with the app, connected ───────────────────────────────────────────────
{
  const s = describe({ ...base, appPresent: true, lastHeartbeat: NOW - 30_000,
                       mode: "blocklist", listVersion: 42, rulesApplied: 148_000 });
  check(s.protection.level === "active", "connected: active");
  check(/connected/.test(s.protection.detail), "connected: says so");
  check(s.lock.headline === "No active lock", "connected, unlocked: no lock line");
  check(/Start a lock from the Hisn app/.test(s.lock.detail), "connected: points at the app");
  check(s.details.find(([k]) => k === "Block list")[1] === "v42 · 148000 rules",
        "connected: enforced rules, with version");
  check(s.notices.length === 0, "connected: nothing to warn about");
}

// ── with the app, locked ──────────────────────────────────────────────────
{
  const until = NOW + 6 * 86_400_000;
  const s = describe({ ...base, appPresent: true, lastHeartbeat: NOW - 30_000,
                       mode: "blocklist", lockUntil: until });
  check(s.lock.locked === true, "locked: locked");
  check(s.lock.headline === `Locked until ${when(until)}`, "locked: names the end time");
  check(/^Standard mode/.test(s.lock.detail), "locked: standard mode named");
  check(/weaken protection/.test(s.lock.detail), "locked: explains restrictions accurately");

  const strict = describe({ ...base, appPresent: true, lastHeartbeat: NOW - 30_000,
                            mode: "strict", lockUntil: until });
  check(/^Strict mode/.test(strict.lock.detail), "locked strict: strict named");
}

// ── the mode never leaks into the protection line ─────────────────────────
{
  const s = describe({ ...base, appPresent: true, lastHeartbeat: NOW - 30_000, mode: "off" });
  check(!/off/i.test(s.protection.headline + s.protection.detail),
        "unlocked: protection line never says 'off'");
}

// ── app present but silent ────────────────────────────────────────────────
{
  const silent = { ...base, appPresent: true, lastHeartbeat: NOW - HEARTBEAT_GRACE_MS - 1 };
  const s = describe(silent);
  check(s.protection.level === "partial", "silent app, no lock: partial");
  check(/not responding/.test(s.protection.detail), "silent app: says not responding");
  check(s.notices.some((n) => n.kind === "warn" && /Open the Hisn app/.test(n.text)),
        "silent app: next action is to open the app");
  check(s.details.find(([k]) => k === "Hisn app")[1] === `last heard ${when(silent.lastHeartbeat)}`,
        "silent app: details say when it was last heard");

  // Fail-closed is protection that got STRONGER, not weaker: active, with a
  // warning about how to get the normal settings back.
  const fc = describe({ ...silent, failClosed: true, lockUntil: NOW + 86_400_000 });
  check(fc.protection.level === "active", "fail-closed: active");
  check(/strict mode automatically/.test(fc.protection.detail), "fail-closed: explains the tightening");
  check(/^Strict mode/.test(fc.lock.detail), "fail-closed: lock line reads strict");
  check(fc.notices.some((n) => /restore/.test(n.text)), "fail-closed: says how to restore");
}

// ── details ────────────────────────────────────────────────────────────────
{
  const off = describe({ ...base, inspectText: false });
  check(off.details.find(([k]) => k === "Page-text check")[1] === "off", "text check off");
  const on = describe({ ...base, inspectText: true, textSensitivity: 80 });
  check(on.details.find(([k]) => k === "Page-text check")[1] === "on · sensitivity 80",
        "text check on with sensitivity");
  const v = describe(base, { version: "1.2.3" });
  check(v.details.find(([k]) => k === "Extension version")[1] === "1.2.3", "version row");
}

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} status checks failed`);
