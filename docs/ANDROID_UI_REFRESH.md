# Hisn Android UI and UX refresh — 2026-10-30

> Historical. The 2026-11-05 pass replaces the Protection, Rules and Guide
> presentation and adds the first-run permission checklist; see
> [ANDROID_UX_2026-11-05.md](ANDROID_UX_2026-11-05.md).

Development preview. This pass changes presentation only: no enforcement,
permission, consent, persistence, notification-delivery or commitment rule was
added, removed or weakened. The current tree carries version 0.8.2
(versionCode 22) from the separate list update; this document is not a release
record and makes no device-acceptance claim.

## Why

The previous screens worked but were hard to read at a glance. Protection was one
long list of sentences and buttons, and the three top "tabs" were plain buttons
whose selected state was only a colour. Every layer reported in a full sentence,
so a person had to read several paragraphs to answer "what is on?". Switches were
buttons whose label changed, app lists were an unsearchable multi-choice dialog,
and all colours were hard-coded in the activity, including the 0.5.5 version
string. There was no light appearance, adaptive icon or Android 12 splash.

## What changed

### Navigation and structure

- Three real bottom-navigation destinations — **Protection**, **Rules**,
  **Guide** — each with an icon, label, selected pill and a 58 dp target. The
  page keeps its scroll position when switching or rotating.
- Back from Rules or Guide returns to Protection instead of leaving the app.
  Android 13+ uses the predictive-back dispatcher.
- Pages are capped at a 640 dp reading width and centred on tablets, foldables
  and landscape, with the same measure for the bottom bar.
- Rules and Guide have page titles. Protection leads with the live assessment
  rather than another title.

### Protection

- A hero card with one status headline, a shield, and an expandable **What this
  means** disclosure (the existing `status_body`, unchanged).
- Three layer tiles — Network, Apps, Images — each showing one state word:
  On, Off, Needs attention, Starting, Not set up or Not supported. Text and
  colour both carry the state.
- **What needs attention** lists the concrete reasons behind the warning
  headline: a stopped or failing DNS layer, strict Private DNS, a missing or
  disconnected accessibility service, an incomplete scan, a pending or failed
  adult-text policy, a failed image check, a failed route check, an out-of-sync
  uninstall or managed-lockdown state, a lost watchdog layer, notifications off,
  an unreadable commitment, a restored settings copy, or unreadable settings.
  It is derived from the same conditions the old status headline used; it adds
  no new claim.
- The setup guide keeps its deterministic next step and one primary action.
- The network card keeps the bundled-list version and age warning, the consent
  and Android VPN buttons, the route check and the background check. The DNS
  button now says what state it is in and disables when a session is running.
  A notification-settings action appears when notifications are off, and a
  Private DNS settings action appears when strict Private DNS can bypass Hisn.
- App and screen checks, commitment and uninstall protection are separate
  cards. The diagnostics counters moved to the Guide, with a **Copy
  diagnostics** action.
- The stale version label is gone: the header formats
  `BuildConfig.VERSION_NAME`, so a direct build shows its `-direct` suffix.

### Rules

- Switches replace state-changing buttons. Each switch explains what the layer
  does while it is off and reports the real policy/sample state while it is on.
  The consent disclosure still appears before anything is enabled, and the
  switch reverts if it is cancelled.
- Separate cards for built-in checks, accessibility, custom rules, app lists,
  the daily schedule, the commitment and private screens. Each list row shows a
  live count and opens the same editor.
- Editors count lines against the same 1,000-line bound the store enforces, and
  keep their refusal reason inline.
- App choosers are searchable, show app icons, expose a checkable row to
  TalkBack, keep the existing selection when filtering (including packages that
  are not currently installed), and refuse a 201st selection before saving.
- The schedule editor groups the enable switch, the two times and the app count
  in one form, and keeps the same validation and commitment rules.

### Guide

- Numbered setup steps parsed from the existing guide text, followed by the
  same coverage-limits and privacy text.
- Uninstall/admin guidance, diagnostics, the local word tester and the licenses
  screen, each in its own card.
- The word tester still marks the page secure while it holds text, and the
  diagnostics copy contains counters only.

### Restriction and pause screen

- The reason, a full-width **Return to home screen** action and the pause card
  are visually separated. Returning home is still never delayed by the pause.
- The breathing circle has a labelled step counter and three cycle dots. The
  existing reminders, need choices, tips and daily tally are unchanged, and
  animations still stop when Android animations are off.
- Back on this screen still goes Home.

### Dialogs and actions

- Dialog actions are sentence case and medium weight through one alert-dialog
  theme, instead of the framework's all-caps bar.
- The commitment period chooser is four explicit full-width options with the
  existing "choose how long" hint. The confirmation still shows the complete
  commitment disclosure before anything is locked.
- The uninstall dialog no longer relies on a three-button framework bar, which
  stacked long labels vertically. Apply/Copy, admin or release, and Cancel are
  explicit full-width actions with 48 dp targets.
- The app-picker row is checked through TalkBack, and its checked state is kept
  after a rebind; a UI-tree pass caught and fixed an earlier version that lost
  the checkable class after a selection.

### Rest of the app

- Day/night semantic palette, typography scale, rounded surfaces, ripple
  buttons and vector icons replace hard-coded colours. Status/accent/success/
  warning/danger pairs stay above 4.5:1 for body text in both appearances.
- Adaptive launcher icon (with a monochrome layer on Android 13+), Android 12
  splash theme, notification colour and the white shield small icon, and two
  dynamic launcher shortcuts for Rules and Guide.
- Boot-shield overlay uses the same palette, an icon and a progress line.

## Honesty and privacy boundaries kept

- The hero never says the device is protected. It keeps the existing
  "Selected protection layers active · Experimental coverage" and "Protection
  needs attention" strings. The attention list names layers, not guarantees.
- Every consent, coverage, limitation, private-screen and commitment disclosure
  remains, word for word, in the same dialogs. Only their container changed.
- No new permission, intent filter, exporter, network call or stored field.
  Dynamic shortcuts target this activity only; their intents carry a page number.
- Private screens keep `FLAG_SECURE`; the app chooser, rule editor and schedule
  editor are still secure dialogs. The app header and the new cards do not show
  lists, words, domains or app names on the recent-apps preview.
- Arabic receives the same labels and states; the layout mirrors through
  `START`/`END`, auto-mirrored chevrons and system RTL direction.

## Accessibility

- Section titles and page titles are accessibility headings.
- Status headlines, status lines and tester results are polite live regions;
  unchanged text is not re-announced every two seconds.
- Switches expose their summary as part of the label, so TalkBack reads the
  layer and its state in one focus stop.
- App-list rows expose a checkable/checked state and a single label; the
  decorative check box and icons are removed from the accessibility tree.
- The disclosure header is a 48 dp target and announces "Show details" or
  "Hide details"; decorative icons are excluded.
- All primary actions and rows are at least 48 dp; body text is 15 sp, captions
  13 sp, and every size uses sp so the system font scale applies.
- Colour is never the only state signal: layer tiles and status lines carry a
  state word, and status lines add a coloured dot. The semantic text pairs were
  spot-checked at 5.0:1 or higher in both appearances.

## Verification

Local, 2026-10-30:

- `./gradlew :app:assembleDebug :app:assembleRelease` with Android Studio's JBR
  and the local SDK: **PASS**.
- `./gradlew :app:lintDebug`: 0 errors; only the 11 debug-only warnings already
  present before this pass.
- `python3 check-strings.py`: **PASS**, 351 strings and 13 plurals in English and
  Arabic, with matching placeholders.
- `python3 check-notices.py` and `python3 test_check_release.py`: **PASS**.
- `JAVA_HOME=<JBR> sh test-core.sh`: **PASS**. Adult corpus result is unchanged
  at 68/72 expected blocks and 0/133 benign controls.
- Play and direct release APKs built; `check-release.py --expect-distribution`
  passes for both (0.8.2 and 0.8.2-direct, no debug-only component or class).
- The current seed is v5: `test-core.sh` loaded 239,880 domains (5 IP entries
  excluded from 239,885), with the same 68/72 adult-text fixture result and
  0/133 benign controls blocked.
- API 36 emulator captures: 20 screenshots in
  [design/android-ui-2026-10-30](../design/android-ui-2026-10-30/), covering
  light, dark, Arabic RTL, the attention state, the restriction/pause screen,
  both uninstall dialogs, the commitment flow, 130% text and landscape. Secure
  app chooser/editor windows were checked through their accessibility tree
  instead (system screenshots are black by design). These are emulator checks,
  not physical-device or TalkBack acceptance.

## Screenshots

| Capture | What it shows |
| --- | --- |
| [01 Protection, light](../design/android-ui-2026-10-30/01-protection-light.png) | Clean install; hero, layer tiles, setup, DNS card |
| [02 Rules, light](../design/android-ui-2026-10-30/02-rules-light.png) | Switches, accessibility card, list rows |
| [03 Guide, light](../design/android-ui-2026-10-30/03-guide-light.png) | Numbered setup steps |
| [04 Protection, Arabic](../design/android-ui-2026-10-30/04-protection-arabic.png) | RTL hero, tiles, app bar and bottom navigation |
| [05 Rules, Arabic](../design/android-ui-2026-10-30/05-rules-arabic.png) | RTL switch rows |
| [06 Protection, dark](../design/android-ui-2026-10-30/06-protection-dark.png) | Night palette |
| [07 Rules, dark](../design/android-ui-2026-10-30/07-rules-dark.png) | Night palette with switches |
| [08 Rules, short videos on](../design/android-ui-2026-10-30/08-rules-short-video-on.png) | Enabled switch and accessibility warning |
| [09 Protection, attention](../design/android-ui-2026-10-30/09-protection-attention.png) | Warning headline and concrete attention list |
| [10 Restriction pause](../design/android-ui-2026-10-30/10-restriction-pause.png) | Reason, immediate Home action, breathing card |
| [11 Restriction need](../design/android-ui-2026-10-30/11-restriction-need.png) | Selected need and its tip |
| [12 Protection details](../design/android-ui-2026-10-30/12-protection-details.png) | Expanded status explanation |
| [13 Uninstall dialog](../design/android-ui-2026-10-30/13-uninstall-dialog.png) | Explicit actions, command block |
| [14 Commitment chooser](../design/android-ui-2026-10-30/14-commitment-dialog.png) | Four period options |
| [15 Commitment confirmation](../design/android-ui-2026-10-30/15-commitment-confirm.png) | Full disclosure before locking |
| [16 Large text](../design/android-ui-2026-10-30/16-protection-large-text.png) | 130% system font scale |
| [17 Landscape](../design/android-ui-2026-10-30/17-protection-landscape.png) | Capped reading width and bottom navigation |
| [18 Direct build](../design/android-ui-2026-10-30/18-protection-direct.png) | `0.8.2-direct` version suffix |
| [19 Direct uninstall](../design/android-ui-2026-10-30/19-uninstall-direct.png) | Long direct-build uninstall disclosure with all actions visible |
| [20 Tester with keyboard](../design/android-ui-2026-10-30/20-guide-tester-keyboard.png) | Keyboard inset: bottom bar hidden, focused field above the keyboard |

The folder's [README](../design/android-ui-2026-10-30/README.md) records the
emulator, build, locale, font-scale and reproduction commands.

## Still open

- Physical-device inspection in English and Arabic, light and dark, with the
  largest font scale, landscape, split screen and a hardware keyboard.
- TalkBack and Switch Access pass over every dialog and the live status lines.
- Real Samsung/Xiaomi/Pixel rendering of the adaptive icon and splash.
- The DNS, accessibility and device-owner flows still need their separate
  controlled device tests; this pass did not run them.
