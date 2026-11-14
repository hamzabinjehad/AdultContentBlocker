# Mac browser guard: no browser without the extension

Date: 12 November 2026.

## What changed

The Mac guard no longer leaves a linked Chromium browser open indefinitely
when its extension stops checking in. A missing heartbeat that the guard can
read is now a visible recovery countdown:

| Browser | Missing connection | Countdown |
|---|---|---|
| Linked Chromium browser (`NativeMessagingInstaller.browsers`) | no process-scoped heartbeat, no profile evidence | 1 minute (`warnSilent`), floored at the launch/wake grace |
| Linked Chromium browser | standard profile confirms removal/disablement | 5 minutes (`warnProfileLoss`) |
| Unsupported browser (Safari, Firefox, Tor, an unknown fork) | no Hisn inside it | 1 minute (`warnUncovered`) |
| Linked browser, remembered locked-authority IPC outage | check-ins cannot be read at all | no countdown (`attention`); the browser is shown as waiting |

A heartbeat that returns inside the window cancels the close. A browser
relaunched without repair after a silence closure gets the full one-minute
warning again; a profile-confirmed loss keeps the full five minutes. The delayed
force-close recheck now accepts either still-confirmed profile loss or a
heartbeat that is still absent, and refuses to act when the check-in source is
unreadable.

Build 33 also rebuilds a missing or stale user-scope native-messaging link
while the guard runs, before deciding to close a browser whose extension has
not checked in. A working system link is still deferred to and `/Library` is
never written; without a rebuildable route the one-minute countdown still
applies.

## Why

The extension is the layer that reads page text and carries Hisn's rules inside
the browser. The 2026-11-09/10 pass made silence "attention" so a delayed
Chrome service-worker alarm would not close a working browser. That safety
turned into a hole: a browser whose standard profile could not be read, whose
native-host link was removed, or whose profile path is non-standard could run
without the extension forever. This change makes the absence of a readable
extension connection the enforcement signal and keeps the one-minute warning as
the recovery allowance.

## What was kept from the previous pass

- Process-scoped check-ins: a healthy sibling process or another browser cannot
  lend proof.
- Launch-date rejection: a stamp from a previous process with a reused PID does
  not verify a new instance.
- Profile evidence: two separated reads are still needed to claim confirmed
  removal/disablement, and those reads still extend the countdown to five
  minutes. Unreadable or conflicting preferences are not removal evidence.
- Authority handling: a live empty check-in record remains authoritative-empty;
  editable local defaults are still not trusted during a remembered locked
  outage.
- Wake/launch recovery floors and the visible panel.

## The outage exception

`FilterSync.guardCheckInsUnavailable` is true while a remembered locked
authority cannot be reached (`status == nil` and the last confirmed authority
lock is still in memory). In that state an absent stamp is unknown, not
missing; the guard returns `attention` with no deadline. Without that
distinction an IPC hiccup would close every linked browser on the Mac. A
standard profile that independently confirms loss still counts during an
outage.

## Verification

- `./test.sh macos`: policy, profile-evidence, authority and UI tests pass.
- New policy tests: `testSilenceClosesAfterItsOneMinuteWarning`,
  `testSilentCountdownIsFlooredByLaunchOrWakeGrace`,
  `testUnreadableCheckInsAreUnknownNotMissing`,
  `testForceCloseRequiresConfirmedLossOrASilentHeartbeat`,
  `testUnconfirmedProfileKeepsTheSilentCountdown`.
- `FilterActivationTests` pins the outage case: unreadable check-ins do not
  start a closure.
- Not exercised on hardware in this pass: a real Chrome alarm delay, a real
  browser closure, and a live filter IPC outage. The tests are synthetic policy
  tests, not a deployment record.
