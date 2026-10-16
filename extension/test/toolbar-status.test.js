import { createToolbarStatus, describeToolbar } from "../lib/toolbar-status.js";
import { language } from "../lib/i18n.js";

let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }
const applied = {
  desiredRevision: 2, appliedRevision: 2,
  enforcement: { version: 1, status: "applied", desiredRevision: 2, appliedRevision: 2,
    layers: { domains: "applied", search: "applied", text: "applied", visual: "off" } },
};
const failed = { ...applied, enforcement: { ...applied.enforcement, status: "failed" } };
const pending = { ...applied, enforcement: { ...applied.enforcement, status: "pending" } };
function fixture(initial = "") {
  let badge = initial, title, fail = false, calls = [];
  const toolbar = createToolbarStatus({
    getBadgeText: async args => { calls.push(["read", args]); return badge; },
    setBadgeBackgroundColor: async args => { calls.push(["color", args]); if (fail) throw new Error("UI unavailable"); },
    setTitle: async args => { calls.push(["title", args]); if (fail) throw new Error("UI unavailable"); title = args.title; },
    setBadgeText: async args => { calls.push(["badge", args]); if (fail) throw new Error("UI unavailable"); badge = args.text; },
  });
  return { toolbar, get badge() { return badge; }, get title() { return title; },
    get calls() { return calls; }, set fail(value) { fail = value; } };
}

check(describeToolbar("pending").text === "?", "unverified configuration has a neutral checking badge");
check(describeToolbar("failed").text === "!", "failed browser enforcement has a warning badge");
check(describeToolbar("applied").text === "", "applied configuration clears the warning rather than displaying a guarantee");
check(describeToolbar("pending", "en", true).text === "!", "pending work cannot erase a previous known failure");
check(describeToolbar("applied").title === "Hisn — Rules and registrations verified",
  "successful tooltip names the evidence instead of promising every item is safe");
check(describeToolbar("failed", "ar").title === "Hisn — الحماية تحتاج إلى إصلاح",
  "warning tooltip is available in Arabic without a second localization catalog");
check(language() === "en", "toolbar translation never changes the global page/scoring language");

const fresh = fixture();
await fresh.toolbar.update(undefined);
check(fresh.badge === "?" && fresh.title === "Hisn — Checking browser protection", "startup honestly displays checking");
const freshCalls = fresh.calls.length;
await fresh.toolbar.update(pending);
check(fresh.calls.length === freshCalls, "repeated successful pending presentation is deduplicated");
await fresh.toolbar.update(failed);
check(fresh.badge === "!" && fresh.title.includes("needs repair"), "real failure changes both badge and accessible title");
await fresh.toolbar.update({ ...pending, appPresent: true, lastHeartbeat: Date.now(), visualEngine: { state: "ready" } });
check(fresh.badge === "!" && fresh.title.includes("needs repair"), "model readiness or native heartbeat cannot clear a failed browser configuration");
await fresh.toolbar.update(applied);
check(fresh.badge === "" && fresh.title.includes("registrations verified"), "verified current receipt clears the earlier warning");
const callsApplied = fresh.calls.length;
await fresh.toolbar.update(applied);
check(fresh.calls.length === callsApplied, "repeated healthy reads do not repeat toolbar writes");

const restarted = fixture("!");
await restarted.toolbar.update(pending);
check(restarted.badge === "!" && restarted.title.includes("needs repair"), "worker restart retains the browser's existing warning while pending");
await restarted.toolbar.update({ ...applied, appliedRevision: 1 });
check(restarted.badge === "!", "a stale applied revision cannot clear the persisted warning");
await restarted.toolbar.update(applied, "ar");
check(restarted.badge === "" && restarted.title === "Hisn — تم التحقق من القواعد وتسجيل الفحص",
  "current configuration readback can clear the restarted warning with Arabic feedback");

const rejected = fixture();
rejected.fail = true;
await rejected.toolbar.update(failed);
const rejectedCalls = rejected.calls.length;
rejected.fail = false;
await rejected.toolbar.update(failed);
check(rejected.calls.length > rejectedCalls && rejected.badge === "!", "rejected API writes are retryable, not cached as success");
check(rejected.calls.every(([, args]) => !JSON.stringify(args).includes("://")), "toolbar payloads contain no page URLs or browsing content");

let unreadableBadge = "!";
const unreadable = createToolbarStatus({
  getBadgeText: async () => { throw new Error("read unavailable"); },
  setBadgeText: async ({ text }) => { unreadableBadge = text; },
});
await unreadable.update(pending);
check(unreadableBadge === "!", "failed historical badge reads never erase an existing warning while pending");
await unreadable.update(applied);
check(unreadableBadge === "", "verified repair can clear a warning even when historical badge reading is unavailable");

const absent = createToolbarStatus(undefined);
await absent.update(pending); await absent.update(failed); await absent.update(applied);
check(true, "absent action APIs do not throw or reject protection work");

// Keep ordering through a slow API: only the final applied receipt may clear
// the failure, and an older presentation cannot run after that confirmation.
let unblock, finalBadge = "", gate = true;
const ordered = createToolbarStatus({
  getBadgeText: async () => "",
  setTitle: async () => { if (gate) await new Promise(resolve => { unblock = resolve; }); },
  setBadgeText: async ({ text }) => { finalBadge = text; },
});
const first = ordered.update(failed);
for (let i = 0; i < 10; i++) await Promise.resolve();
const second = ordered.update(pending), third = ordered.update(applied);
gate = false; unblock();
await first; await second; await third;
check(finalBadge === "", "serialized toolbar writes end at the latest verified state");

print(`${checks}/${checks} toolbar status checks passed`);
