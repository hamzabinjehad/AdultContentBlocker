# REA design audit: Screen Zen and Hisn, 3 November 2026

This is a **design-research record**, not a security assessment, a bug count or a
claim about another product's behaviour. It records what REA could observe
statically, what was then implemented in Hisn 1.1.8, and what remains unknown.

## Tool and method

- [REA](https://github.com/morluto/rea) 6.4.0 was installed into a temporary
  directory from npm with installation scripts disabled
  (`npm install --ignore-scripts`). No MCP server was registered, no editor or
  agent configuration was changed, no native disassembler was installed, and no
  inspected application code was executed.
- REA 6.8.0 was tried first. Its `analyze` command requires an external native
  analysis provider (Hopper, Ghidra or IDA), none of which is installed. Version
  6.4.0 — the version this repository used for its 2026-10-13 audit — was pinned
  and its **provider-free** commands were used instead:
  `inspect-artifact`, `analyze-javascript-application` and
  `capture-native-ui-scenario`.
- Full evidence JSON and the Screen Zen window screenshot remain in
  `/tmp/hisn-work/` on the audit machine. They are temporary engineering
  artifacts, not repository or release artifacts. The third-party UI capture is
  deliberately not redistributed.
- Screen Zen 2.0.0 (build 63) was already running. REA observed the existing
  window by PID and window ID; no setting, account or rule was changed. The
  menu bar item was clicked once through System Events to make the window
  visible. The observation is a 48-node accessibility snapshot plus one
  screenshot, not a semantic model of the app.

## Evidence

| Subject | REA operation | Evidence | Observed digest |
| --- | --- | --- | --- |
| `/Applications/ScreenZen.app` | `inspect-artifact` | `ev_914842e780b424b338ac5ad746902664b5cd4e488bd8d696f4aae8e63e053cab` | SHA-256 `3d71a3666b43ba400dc05e62d772c7a5f85eea06234e750b42c7d698fe8c7547` |
| `/Applications/Hisn.app` | `inspect-artifact` | `ev_4385889591f4dd6fdc5b2ead9ed849aa1c6bb2fd2381d08deb606e8f4577d985` | SHA-256 `c13d7902d26878c76bb1ef8073b79d896b34b76390e2aecd1c1cac4c8bb5b225` |
| Screen Zen Firefox extension | `analyze-javascript-application` | `ev_ac7da350505820ff98ea6c11c30620f8e56b893c52fb4b060184dbb5bea240ff` | 5 source files, 14 graph nodes, 21 relationships; graph coverage complete, not truncated |
| Screen Zen window | `capture-native-ui-scenario` | `ev_7e5597cf9f1d78b8b5ee4b52d5af7f40b6c771d42a40abb5ebac3ffd7372a29d` | 48 accessibility nodes before a 300 ms wait; screenshot and digest bound in the Evidence |

The installed Hisn app is the 1.1.7 build from this machine. Its inventory
identifies those bytes; it does not prove that the current source tree is the
same as the installed build, that the filter is active, or that any particular
browser is protected. None of those claims is made here.

### What REA could not establish

- Native Swift/SwiftUI semantics, data flow or enforcement behaviour. REA had no
  native analysis provider, so this was an inventory and UI observation, not
  decompilation.
- Whether Screen Zen's blocking, pause or unlock paths work as designed, on
  which OS versions, or under which account. Static strings and one rendered
  window do not prove runtime behaviour.
- A complete view of Screen Zen: the capture is one window and 48 accessible
  nodes. Most SwiftUI controls presented no title or value to the accessibility
  tree, so the screenshot and the binary's user-facing strings supplied the
  design vocabulary.

## What Screen Zen's interface and strings show

The observations are specific to Screen Zen 2.0.0 build 63 on this Mac:

- **Pause is the product, not a settings sub-page.** The block path offers a
  breathing exercise, an intentional reason, a password intervention, and a
  daily-opens limit. After the daily goal, the app becomes a strict block.
- **A calm, card-based dashboard.** Websites, Halo and Daily goal lead; rules
  are compact rows; the selected group uses a block/allow segmented control;
  advanced rule, adult-websites and test actions sit under the list. Apps,
  daily limit and Zen screen are single rows.
- **Time windows and schedules** are first-class (“Every day · All day”, “Add
  Time Window”).
- **Streaks and local progress** are shown on the pause page; the app records
  website/group daily opens and resets them on a configurable boundary.
- **Halo** uses Bluetooth proximity for physical boundaries, and the settings
  lock can depend on being inside or outside the zone.
- **Browser blocking is a companion extension** connected to the app, with a
  local block page and a native app requirement.
- **The macOS app is a menu-bar app** (`LSUIElement`) with a separate dashboard
  window; the block page is served from the app bundle and speaks to the app
  over a local socket.

## What Hisn already had

| Capability | Hisn state before 1.1.8 |
| --- | --- |
| Adult-domain blocking | Signed 239,885-domain core seed; full list for the Mac filter when a generation is installed |
| Local page-text checking | Extension, Arabic/English/German and more; language-aware in 1.4.0 |
| Pause at a blocked page | Extension 1.4.0 block page: breath, reminder, need, local tally |
| On-demand pause in the Mac app | **Missing** — the gap this audit addressed |
| Commitments and locks | Mac, iOS and Android; fixed horizon, delayed release, partner and guardian paths |
| Recurring schedule | Mac daily lock, Android schedules |
| Daily opens limit / streak | Not a Hisn concept; Hisn's lock is the stronger control |
| App blocking | Android selected apps; iOS Screen Time; not a macOS feature |
| Physical-boundary blocking | None |
| Account/cloud requirement | None — on-device by design |

## What was implemented

Mac 1.1.8 adds an **on-demand pause** with the same privacy shape as the
extension's block page: a 4-second inhale and 6-second exhale over three cycles,
five rotating reminders, four needs with offscreen tips, and a local
day/count/time tally (30-second merge, 9,999 cap, no host or history). It is
reachable from Overview, the menu bar and **Protection › Take a pause… (⇧⌘P)**,
and it is available during a lock. Closing is always immediate; no rule,
permission or protection layer changes.

The design borrows the *idea* of a calm, closeable pause and a local count. It
does not copy Screen Zen's code, assets, visual identity, copy or data model.
The reminder and need copy comes from Hisn's own extension locale tables.

## What was deliberately not copied

- **Halo proximity blocking.** It needs a physical device, a signed-in account
  and cloud state. That conflicts with Hisn's on-device, no-account promise and
  with the 1.0 scope.
- **Quick disable / time-boxed unblocking.** Hisn's rules make weakening wait;
  a one-tap “disable for 15 minutes” would undermine the commitment model.
- **Daily opens as a protection claim.** A count of opens is not a measure of
  protection or recovery. Hisn shows a pause count only; it does not advertise a
  streak, a score or an accuracy claim.
- **A cloud dashboard, account or cross-device state.** The tally stays in
  `UserDefaults` on this Mac and is never sent anywhere.
- **Screen Zen's visual identity or copy.** The Hisn window uses Hisn's own
  SwiftUI components, colors and English/Arabic strings.

## Limits and follow-up

- This is a single-window observation on one machine, not a competitive
  benchmark. Do not cite it as evidence that Screen Zen is better or worse at
  blocking.
- The implementation is covered by unit and render tests, not by a user study,
  a screen-reader audit or a physical-Mac review. Those remain owner actions.
- Native analysis (with a configured provider) was not run. If a future audit
  needs native call graphs, install a provider deliberately and record its
  side effects; do not treat this inventory as a substitute.
