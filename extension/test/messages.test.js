import { send, MESSAGE_TIMEOUT_MS, CONNECTION_TIMEOUT_MS, REPAIR_TIMEOUT_MS } from "../lib/messages.js";

const timers = [];
globalThis.setTimeout = (fn, ms) => { const timer = { fn, ms }; timers.push(timer); return timer; };
globalThis.clearTimeout = timer => { timer.cancelled = true; };
let callback;
globalThis.chrome = { runtime: { sendMessage: (_msg, reply) => { callback = reply; } } };
let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }

const pending = send({ type: "forceSync" });
check(timers.at(-1).ms === CONNECTION_TIMEOUT_MS && CONNECTION_TIMEOUT_MS > 10000,
  "connection budget includes native IPC deadline and a preceding heartbeat");
callback({ ok: true, connected: true });
check((await pending).ok && timers.at(-1).cancelled, "valid native reply completes the connection check and cancels its deadline");
const normal = send({ type: "getState" });
check(timers.at(-1).ms === MESSAGE_TIMEOUT_MS && MESSAGE_TIMEOUT_MS === 5000, "ordinary messages retain the short response deadline");
timers.at(-1).fn();
check((await normal).reason === "unavailable", "worker deadline reports lack of verification");
const bounded = send({ type: "forceSync" });
timers.at(-1).fn();
check((await bounded).reason === "unavailable", "unresponsive connection request remains bounded without inventing app absence");
const repair = send({ type: "repairProtection" });
check(timers.at(-1).ms === REPAIR_TIMEOUT_MS && REPAIR_TIMEOUT_MS === 30000,
  "independent browser repair allows bounded queueing and rule readback beyond the ordinary message deadline");
callback({ ok: true, state: { enforcement: { status: "applied" } } });
check((await repair).ok && timers.at(-1).cancelled, "verified repair response completes and cancels its deadline");
const unavailableRepair = send({ type: "repairProtection" });
timers.at(-1).fn();
check((await unavailableRepair).reason === "unavailable", "unresponsive repair never invents a successfully applied policy");
const explicitRepairBudget = send({ type: "repairProtection" }, 1234);
check(timers.at(-1).ms === 1234, "explicit transport deadlines remain supported for repair callers");
timers.at(-1).fn();
await explicitRepairBudget;
chrome.runtime.sendMessage = () => { throw new Error("worker restarting"); };
check((await send({ type: "getState" })).reason === "unavailable", "transport exceptions remain unavailable");
print(`${checks}/${checks} message checks passed`);
