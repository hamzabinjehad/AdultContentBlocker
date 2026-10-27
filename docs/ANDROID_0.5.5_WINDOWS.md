# Hisn Android 0.5.5: other windows, feed and video cadence

Branch `android/windows-feeds` on `origin/main` (0.5.4, version code 16), 2026-10-27.
The version is not changed here. Built, linted and tested on a Mac with Android
Studio's JDK and the local SDK. No phone or emulator was available, so nothing here
has run on a device.

## What changed

### Split-screen, pop-up and picture-in-picture windows

Text checks, app and schedule restrictions and image sampling read only
`getRootInActiveWindow()`. A selected app in the other half of a split screen, in a
pop-up (freeform) window or in picture-in-picture was never inspected while another
app had the active window.

- The accessibility service now asks for `flagRetrieveInteractiveWindows` and
  `typeWindowsChanged` events (`res/xml/text_guard_service.xml`), and lists
  `getWindows()` on each inspection.
- **Which windows** (`GuardWindows`, core): only application windows (full screen,
  split-screen halves, pop-up and picture-in-picture windows) whose app is selected
  and not excluded. System, keyboard, accessibility-overlay and split-screen divider
  windows are never read, nor any window of Settings, launchers, dialers, installers,
  permission screens, System UI or Hisn, even if one was somehow selected. The
  active window comes first, then the focused one, then the rest top to bottom; at
  most 4 windows. Listing a window reads its root's package name and nothing else.
  If Android gives no window list, only the active window is used, as before.
- **Text**: blocked and scheduled apps are checked in every listed window first.
  Then each selected window with text checks is read with the same `LabelScan`
  rules (editable, password and EditText nodes and their subtrees are never read).
  All windows share one 512-node budget: each window gets an even share of what is
  left. Picture-in-picture windows are read first, then other inactive windows, and
  the active window last (`GuardWindows.scanOrder`), so a small window hands what it
  leaves to the window in use, and a split screen costs no more nodes than one app
  did. A window cut short by its share
  marks the inspection incomplete, as a cut always has. Time per inspection is
  bounded by the same budget; the heartbeat stays at 1 s.
- **Images**: one window is sampled per capture. With several selected windows on
  screen they take turns. A window that holds input focus and whose focused input is
  editable or a password is skipped (`GuardWindows.typing`); a field left focused in
  a window the user moved away from does not pause that window. It is skipped as is one that closed or changed app since it was listed. Protection
  shows "paused for input" only when one was skipped for input and none is left to
  sample. The frame gate now fences a verdict to the sampled window: the same window
  id and app, still listed, still selected, no focused input, the screen still on,
  no window-state change since, and at most 8 s old. Otherwise the sample is
  discarded, as before.
- **New windows**: a `typeWindowsChanged` event (a window opening, closing or
  moving) looks for selected windows 500 ms later, once, unless an inspection is
  already queued. It inspects only when the set of selected windows (ids and apps)
  differs from the last inspection's; otherwise the heartbeat and the apps' own
  events cover them, so window animations do not trigger inspections twice a second. Events from inactive windows of selected apps also schedule
  inspections now, because Android delivers them only with this flag.
- **Interventions act on the offending app.** The 60-second hold names the app whose
  window was blocked, not whichever app was active. When that window is
  picture-in-picture, Hisn performs Android's dismiss action on it
  (`ACTION_DISMISS`, which System UI handles for picture-in-picture), so the app in
  use is left alone. Home would not close a picture-in-picture window. If the
  window is not picture-in-picture, or Android refuses the dismiss, Home applies as
  before (Back if Home fails). The restriction notice follows in every case. The
  outcome ends in `-pip-dismissed` when the dismiss succeeded.
- **No Home loop** (`InterventionRepeat`, core). A window that Home does not remove
  (a Samsung pop-up, or a picture-in-picture window whose dismiss was refused) is
  not acted on again for 10 s while it stays out of use; other selected windows are
  still checked meanwhile. When the user goes back to it (it becomes the active
  window), or reopens a held app, Hisn acts at once.
- Disclosures: the accessibility and image-check disclosures (English and Arabic)
  now say that split-screen, pop-up and picture-in-picture windows of selected apps
  are inspected.

### Feed and video cadence

Image sampling ran about every 2 s whatever the screen was doing. The interval now
follows the screen (`VisualCadence`, core):

| Screen | Next poll | Notes |
| --- | --- | --- |
| A selected window scrolled, changed content or changed state in the last 2 s | 1 s | Never sooner than 1 s after the previous capture started, nor 1 s after the previous poll, whether or not that poll sampled. An event or a finished sample brings a waiting poll forward to that point, so an event storm while nothing can be sampled (typing, a retry wait) still polls at most once a second |
| Default, or a frame that differs from the last sample of that window | 2 s | As before |
| Unchanged samples of every window in the rotation, no events | 3, 4, then 5 s | Kept per window (at most 4), so windows taking turns back off too; the interval follows the least still window. Any event, a changed frame or a window joining returns to the default; a window that left is forgotten |
| Screen off | 10 s | Polls never sample with the screen off, as before. Turning the screen on brings the next poll forward (`ACTION_SCREEN_ON`, a runtime receiver on Android 14+, not exported) |

- **Changed frames**: a playing video often sends no accessibility events. Each
  sample keeps a 64-bit hash of a 16 x 16 grid of its downscaled frame, with the
  low four bits of each colour ignored, and compares it with the previous sample of
  the same window. Only the hash and the window id are kept, never pixels; the hash
  cannot be turned back into an image. A video or animation keeps the 2 s default.
- **Unchanged**: one capture and one inference at a time (`busy`), the 10 s capture
  timeout, the 30 s retry after a failure, the frame gate and its invalidation on
  window-state changes, the 3-minute idle release of the model and its pause with
  the screen off.
- After the model loads, the first sample is requested within a second instead of
  at the next 2 s tick.
- The image-check disclosure now gives these intervals instead of "about every two
  seconds", in both languages.

## Tests added

- `GuardWindowsTest`: active, focused and top-to-bottom order; inactive split-screen
  halves; picture-in-picture over the launcher and over Settings; Hisn, Settings
  and System UI skipped even when selected; non-application windows skipped
  whatever their package; unknown apps and ids; duplicates; the 4-window bound;
  turns between windows; a decision applying only to the same window and app; node
  shares. 5,000 random window lists check that every chosen window is eligible,
  active windows come first, shares stay within one budget, and an eligible window
  is never dropped from a short list.
- `VisualCadenceTest`: each row of the table above, the bounded back-off and its
  reset by events, changed frames and a window joining; two unchanged windows taking
  turns backing off; a closed window forgotten; an event every 160 ms with no sample
  starting giving at most one poll a second; the signature ignoring
  low-bit noise and changing when content moves. 2,000 random event sequences check
  that intervals stay between 1 and 5 s with the screen on (10 s off) and that no
  poll may run sooner than 1 s after the last capture started or the last poll.
- `GuardWindowsTest`, added: text read order (PiP, inactive, active), typing only
  in the focused window, window-set keys.
- `InterventionRepeatTest`: a window acted on is held off for 10 s only while it is
  the same app's same window and not active; 5,000 random sequences.
- `LabelScanTest`: a share of the budget obtains at most that many nodes, marks a
  cut partial and releases every node; a tree within its share is complete; a zero
  share still visits only the root; a large active feed beside a tiny PiP window is
  read in full when the PiP window goes first, and cut at half the budget otherwise.

## Limits

- **Not seen on a phone.** That `getWindows()` lists split-screen, pop-up and
  picture-in-picture windows with their app's root, that `takeScreenshotOfWindow`
  captures an inactive or picture-in-picture window, and that System UI dismisses a
  picture-in-picture window on `ACTION_DISMISS` from a node of that window, all
  follow Android's documented behaviour and source but were not observed here.
- **Home leaves both halves.** In split screen, Home also leaves the other app. A
  Samsung pop-up window of the offending app may stay on screen after Home. Hisn
  then acts on it again every 10 s while it stays (Home and the notice), at once when
  the user goes back to it, and the hold returns Home whenever the app opens again
  within 60 s. Between those, the pop-up stays visible and usable.
- **Picture-in-picture text** may be little or nothing: many players show only a
  video surface there, which only image checks can see.
- **Budget.** With two or more selected windows, each gets part of the 512 nodes, so
  a long feed beside another selected app is more often cut short (and shown as
  incomplete) than it was full screen.
- **Still screens.** A still screen is sampled up to every 5 s instead of 2 s. A
  change too small to alter the coarse grid (a few pixels) keeps the back-off until
  an accessibility event or a larger change arrives.
- **Typing in another window.** A field left focused in a selected window the user
  moved away from no longer pauses that window's image checks, so that window can be
  captured with whatever the field shows.
- Several selected windows take turns, so each is sampled less often than one
  alone: with two, each about every 2 to 4 s by default.
- Windows of apps that are not selected, and protected (secure) windows, are still
  not inspected.

## Review follow-up

A review of this branch found six problems; all were confirmed in the code and fixed
on `android/windows-feeds-fix`:

1. Events brought polls forward to 200 ms when no sample could start (typing, a
   retry wait), about three polls a second, each listing every window. Polls are now
   at most one a second (`VisualCadence.polled`).
2. The still back-off compared only consecutive samples, so windows taking turns
   never backed off. It is now kept per window.
3. A window Home leaves on screen caused Home and the notice in a loop. Now held off
   for 10 s while out of use (`InterventionRepeat`).
4. The active window's text was read first and capped at half the budget beside a
   tiny PiP window. Small windows are now read first.
5. A focused field in a window the user had left paused its image checks for as long
   as it stayed on screen. Only the focused window counts as typing; the image-check
   disclosure says so in both languages.
6. Window animations re-ran the full text inspection every 500 ms. Discovery now
   runs only when the set of selected windows changed.

## Verification

| Check | Where | Result |
| --- | --- | --- |
| `sh test-core.sh` | macOS 27.2, Android Studio JBR | Pass. String parity: 199 strings, 5 plurals, 210 references. GuardWindows 5,000 random lists; VisualCadence 2,000 random sequences; InterventionRepeat 5,000 random sequences; LabelScan 2,400 random trees; corpus 43/47 (4 challenge rows) and 0/65 |
| `assembleDebug assembleDebugAndroidTest lintDebug assembleRelease` | Same Mac, Gradle 9.3.1, local SDK | `BUILD SUCCESSFUL`, 119 tasks. `lintDebug`: 0 errors, 14 warnings, the same kinds as 0.5.4 (none in the changed files) |
| `check-release.py` | Unsigned release APK | Pass: app.hisn.android 0.5.4 (16), not debuggable or test-only, no DUMP permission, no debug-only component or class |

Rerun after the review follow-up with the same results. These APKs were built locally and are not for distribution. Debug
`054f399ddd5d77d5a6fd0a7e6c3593daaea815d1864c0b665c29b7d0973dace6`, unsigned release
`4a44fb38faf95de4834b2d4e8ea10cc8d735f0c91b863c857921b901745102a7`, androidTest
`a2cedcee36ac0bb3ca2dc567eaa885297540a40a0774853181c4ddfb0250a021`.

## Still needs a phone

Use only the owner's own or public content, and a selected test app.

- Split screen with a selected app in the inactive half: its text is read, a
  matching label returns Home with the notice, and typing in the other half's
  search field is never read. Settings in the other half is never read.
- Picture-in-picture of a selected video app over the launcher or another app:
  image samples are captured (`ELIGIBILITY SELECTED`, captures rising in the debug
  capture probe); a synthetic block dismisses the picture-in-picture window and
  leaves the other app open; if the dismiss is refused, Home applies.
- A Samsung pop-up window of a selected app: inspected; what Home does to it.
- Cadence: in the debug capture probe, capture requests about every second while
  scrolling a selected feed, about every 2 s with a video playing and the screen
  untouched, slowing toward 5 s on a still page, and none with the screen off; the
  first sample soon after the screen comes back on.
- Battery over an hour of feed use compared with 0.5.4.
