// The block page's pause tally: one number per local day, nothing else.
//
// What matters most: a redirect chain or a reload is one moment, not several,
// and a stored value that is broken or from yesterday starts over instead of
// carrying a wrong count into today.

import { MERGE_MS, REMINDER_COUNT, dayKey, recordPause, reminderFor } from "../lib/pause.js";

let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }

const morning = new Date(2026, 9, 27, 9, 0, 0);
const later = (ms) => new Date(morning.getTime() + ms);

check(dayKey(morning) === "2026-10-27", "the day is the local calendar date");
check(dayKey(new Date(2026, 0, 5, 23, 59)) === "2026-01-05", "months and days are zero-padded");

const first = recordPause(undefined, morning);
check(first.count === 1 && first.day === "2026-10-27" && first.at === morning.getTime(), "the first pause counts 1");
check(JSON.stringify(Object.keys(first).sort()) === '["at","count","day"]', "the tally holds a day, a count and a time — no host");

check(recordPause(first, later(5000)).count === 1, "a block page within the merge window is the same moment");
check(recordPause(first, later(5000)).at === first.at, "merging keeps the counted time");
check(recordPause(recordPause(first, later(20000)), later(MERGE_MS)).count === 2,
      "reloading every few seconds cannot hold one pause open forever");
check(recordPause(first, later(MERGE_MS)).count === 2, "after the window, a new block counts again");

check(recordPause({ ...first, count: 7 }, new Date(2026, 9, 28, 0, 0, 1)).count === 1, "a new day starts over");
check(recordPause(first, later(-60000)).count === 2, "a clock moved backwards counts rather than merging forever");
for (const bad of [null, "3", { day: "2026-10-27", count: "3", at: 0 }, { day: "2026-10-27", count: -1, at: 0 },
                   { day: "2026-10-27", count: 2.5, at: 0 }, { day: "2026-10-27", count: 3 }]) {
  check(recordPause(bad, morning).count === 1, `a malformed tally starts over (${JSON.stringify(bad)})`);
}
check(recordPause({ day: "2026-10-27", count: 9999, at: 0 }, morning).count === 9999, "the count is capped");

check(reminderFor(1) === 1 && reminderFor(REMINDER_COUNT) === REMINDER_COUNT
      && reminderFor(REMINDER_COUNT + 1) === 1, "reminders take turns and wrap");
check(reminderFor(undefined) === 1 && reminderFor(0) === 1, "without a count, the first reminder shows");

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} pause checks failed`);
