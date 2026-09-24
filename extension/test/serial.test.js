/**
 * The state lock. Pins that queued transactions run one at a time, in order,
 * and that one failing does not stall the ones behind it.
 */
import { createLock } from "../lib/serial.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}
const tick = () => new Promise((r) => setTimeout(r, 0));

const run = createLock();
const log = [];

// A slow transaction and a fast one, started together. Without the lock the
// fast one's write would land in the middle of the slow one's read-modify-write.
const slow = run(async () => {
  log.push("slow:read");
  await tick(); await tick(); await tick();
  log.push("slow:write");
  return "slow";
});
const fast = run(async () => {
  log.push("fast:read");
  await tick();
  log.push("fast:write");
  return "fast";
});
const results = await Promise.all([slow, fast]);
check(JSON.stringify(log) === JSON.stringify(["slow:read", "slow:write", "fast:read", "fast:write"]),
      `transactions do not interleave: ${log.join(" ")}`);
check(results[0] === "slow" && results[1] === "fast", "return values reach their own callers");

// A throwing transaction fails its caller and nothing else.
let caught = null;
await run(async () => { throw new Error("boom"); }).catch((e) => { caught = e; });
check(caught?.message === "boom", "a failure is reported to the caller that queued it");
const after = await run(async () => "still running");
check(after === "still running", "the chain is not poisoned by an earlier failure");

// Synchronous functions and non-async return values work too.
check((await run(() => 42)) === 42, "a synchronous function is fine");

// Ordering holds across many queued tasks.
const order = [];
await Promise.all(Array.from({ length: 20 }, (_, i) => run(async () => {
  await tick(); order.push(i);
})));
check(order.every((v, i) => v === i), `twenty queued tasks ran in order: ${order.join(",")}`);

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} serial checks failed`);
