# Mac 1.1.16 — the filter's own count, and a batch that survives a missed reply

Released 6 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build or physical-Mac acceptance is claimed.
This note records what changed and what the change does not cover.

## Why

Three small gaps, all about what the app can truthfully say:

1. The app could show that the filter was running, but not that it had denied
   anything. A count is the smallest useful answer to "is this doing
   something?" — and the one answer that must never become a browsing log.
2. `FilterSync` sent its authority requests in order and stopped at the first
   reply that never arrived. Each request is a whole state, not a patch, so one
   dropped XPC answer could leave a hand-list change unpushed until the next
   twenty-second tick.
3. The menu bar said **Hisn** while a lock ran. The fact a glance wants is how
   long is left.

## What changed

- **An aggregate denied-flow tally.** `BlockedFlowTally` holds a day key and a
  count, capped at 9,999. It is advanced from `shouldBlockFlow` on the filter's
  concurrent queue under its own lock, and `count(on:)` answers zero for any
  other local day, so a filter that stays up across midnight starts the new day
  at zero without a timer. Nothing else is recorded: no host, app, path or
  address, and the type has no field that could carry one.
- **The count travels over the existing XPC health reply.** `FilterHealth`
  gains `blockedToday` and `blockedDay`, decoded with `decodeIfPresent` so a
  reply from an older filter still reads. The filter's own process is the
  source; the app does not infer a count from its own state.
- **Two places show it, both only when it is non-zero and for today.** The menu
  bar adds **Blocked connections today: N** below the pause tally, and the
  Overview's Layer details adds the same count as a labelled row. Zero is not a
  fact worth a row.
- **The request batch continues past a missing reply.**
  `FilterSync.apply(_:from:submit:)` is a static, seam-injected function: a nil
  reply is skipped rather than ending the batch, and refusals are collected in
  order (the last one becomes `lastRefusal`). Whatever was missed is reconciled
  by the same comparison on the next tick, and only ever in the stricter
  direction.
- **The menu bar shows the lock's time left.** `MenuBarIcon.remainingText`
  rounds up to a whole minute, uses day/hour/minute units, and republishes at
  most once a minute from the existing two-second poll. The label falls back to
  **Hisn** when no lock runs.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/BlocklistStore.swift` | `BlockedFlowTally`; count advanced on a deny verdict; `blockedFlowsToday(now:)` |
| `macos/Hisn/PolicyAuthority.swift` | `FilterHealth.blockedToday` / `blockedDay`, tolerant decoding |
| `macos/HisnFilter/FilterDataProvider.swift` | reports the tally in the XPC health reply |
| `macos/Hisn/FilterSync.swift` | `apply` seam: continue past a nil reply, keep refusals |
| `macos/Hisn/StatusMenu.swift` | blocked-today row; coarse countdown in the menu bar label |
| `macos/Hisn/HisnApp.swift` | label uses the countdown while locked |
| `macos/Hisn/ContentView.swift` | blocked-today row in Layer details |
| `macos/Hisn/Localizable.xcstrings` | two new strings, both with Arabic |
| `macos/HisnTests/BlocklistStoreTests.swift` | tally day rollover, read-only other day, cap, time zone |
| `macos/HisnTests/PolicyAuthorityTests.swift` | batch continues past a dropped reply; refusal order |
| `macos/HisnTests/LockScheduleTests.swift` | countdown granularity and rounding |

## Verification

- `./test.sh macos` — **568 tests, 0 failures**; 720 strings, all with Arabic.
- The tally tests pin the two facts that matter: another day reads zero without
  erasing the stored count, and the count stops at the cap.
- The batch tests pin that the request behind a dropped answer still runs and
  that refusals are reported in submission order.
- No live XPC run with real filtered traffic is claimed. The count crosses the
  wire on the health reply the existing authority tests already cover; its
  arithmetic is unit-tested.

## Limits

- **Per process, per local day.** Restarting the filter or the Mac starts at
  zero. Nothing is persisted, by design.
- **Denied flows, not sites or people.** Retries and subresource loads inflate
  the number; traffic denied by a resolver or another layer is not counted.
- **Not evidence of coverage.** Liveness remains the heartbeat and the XPC
  answer; a count is not proof that any particular route is blocked.
- **XPC only.** With no authority answer the app shows no count.
- **The menu text is coarse.** It updates on the two-second poll, in whole
  minutes rounded up; the final minute can read **1 min** until the lock ends.
