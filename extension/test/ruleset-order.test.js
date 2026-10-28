/**
 * The manifest's static rulesets may only grow at the end.
 *
 * Chromium identifies a static ruleset by its POSITION in
 * `declarative_net_request.rule_resources`, not by its id: the enabled set is
 * stored as positions, and an unpacked install keeps the compiled rules in
 * `_metadata/generated_indexed_rulesets/_ruleset<position>` inside the
 * extension folder. A browser that trusts that cache after the manifest
 * changes serves the OLD ruleset from each position under the NEW name.
 *
 * 1.1.12 inserted `evasion` and `encoded_queries` before `paths`, moving the
 * disabled `lockdown` catch-all from position 6 to 8. A source folder loaded
 * directly kept the 1.1.11 cache, so `encoded_queries` — a baseline ruleset
 * the worker always enables — ran `lockdown` and sent every page to
 * blocked.html?reason=strict while the extension reported strict mode off.
 *
 * Appending keeps every position meaning the same ruleset, so a stale cache
 * can only ever be an older copy of the right rules. Add a new ruleset to the
 * end of the manifest AND of SHIPPED below; never reorder or remove one (an
 * unused ruleset stays, disabled).
 */

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

// Every position as released, from 1.1.12 on. Append only.
const SHIPPED = ["blocklist", "keywords", "safesearch", "web_protection", "evasion",
                 "encoded_queries", "paths", "lockdown", "short_video"];

const manifest = JSON.parse(readFile("../manifest.json"));
const resources = manifest.declarative_net_request.rule_resources;
const ids = resources.map((r) => r.id);

check(ids.length >= SHIPPED.length,
      `no shipped ruleset was removed (${ids.length} in the manifest, ${SHIPPED.length} shipped)`);
SHIPPED.forEach((id, i) => {
  check(ids[i] === id, `position ${i + 1} is still "${id}" (found "${ids[i]}")`);
});
check(ids.length === SHIPPED.length,
      `every manifest ruleset is recorded in SHIPPED (${ids.slice(SHIPPED.length).join(", ") || "none extra"})`);

// The catch-all is only ever enabled by the worker, never by the manifest.
const lockdown = resources.find((r) => r.id === "lockdown");
check(lockdown && lockdown.enabled === false, "lockdown ships disabled");

if (failures) print(`  ${failures} of ${checks} ruleset-order checks FAILED`);
else print(`  ${checks}/${checks} ruleset-order checks passed`);
if (failures) throw new Error("ruleset-order failed");
