/**
 * The pause on the block page: a short guided breath, a reminder, a question
 * about what the person actually needs, and how many pauses today.
 *
 * A block arrives at the worst moment, and a plain "no" leaves the urge with
 * nowhere to go. The pause gives it somewhere: breathe, name the need, meet it
 * another way. Nothing here delays leaving the page — walking away must stay
 * the easiest thing on it.
 *
 * The tally is a day and a number in `chrome.storage.local`, nothing more: no
 * host, no reason, no time list. It is never sent anywhere, and a breach of
 * this browser profile learns only "N pauses on this date".
 */

/** Block pages that open within this long of the last counted one are the
 *  same moment — a redirect chain, a reload, a second blocked frame — and do
 *  not count again. Measured from the counted pause, so reloading every few
 *  seconds cannot hold one pause open forever. */
export const MERGE_MS = 30 * 1000;

/** The number of reminders in each locale table, `pause.reminder.1…N`. */
export const REMINDER_COUNT = 5;

/** The needs offered, each with `pause.need.<id>` and `pause.need.<id>.tip`. */
export const NEEDS = Object.freeze(["tired", "lonely", "stressed", "bored"]);

/** One breath: in for 4 s, out for 6 s. A longer exhale is the part that
 *  calms; three of them is about half a minute. */
export const BREATH = Object.freeze({ inMs: 4000, outMs: 6000, cycles: 3 });

const MAX_COUNT = 9999;

/** The local calendar day, as the tally stores it. */
export function dayKey(date) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * The tally after one more block page opened at `now`. A stored value from
 * another day, or one that is malformed, starts over at 1; one within
 * `MERGE_MS` of the last counted pause is returned unchanged.
 */
export function recordPause(stored, now = new Date()) {
  const day = dayKey(now);
  const at = now.getTime();
  const valid = stored && typeof stored === "object" && stored.day === day
    && Number.isInteger(stored.count) && stored.count > 0 && Number.isFinite(stored.at);
  if (!valid) return { day, count: 1, at };
  const since = at - stored.at;
  if (since >= 0 && since < MERGE_MS) return { day, count: stored.count, at: stored.at };
  return { day, count: Math.min(stored.count + 1, MAX_COUNT), at };
}

/** Which reminder to show: they take turns through the day, so the same words
 *  do not meet the person every time. 1-based, as the locale keys are. */
export function reminderFor(count) {
  const n = Number.isInteger(count) && count > 0 ? count : 1;
  return ((n - 1) % REMINDER_COUNT) + 1;
}
