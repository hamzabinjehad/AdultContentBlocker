# Android craft pass — 7 November 2026

A visual and interaction pass over the Android app, on top of the 1.2.3
development tree. The pass changes what the three tabs look like and how a
person reaches the first rule and the pause; it changes no enforcement layer,
permission, consent text, stored rule, list or release claim. The captures are
in [design/android-craft-2026-11-07](../design/android-craft-2026-11-07/README.md).

> **Version and state.** This pass does not bump the version. The tree is still
> 1.2.3 / versionCode 32 because the signed 1.2.3 Play and direct artifacts were
> already built and recorded in [ANDROID_1.2.3.md](ANDROID_1.2.3.md); those
> artifacts predate this pass. The next release should take a new version and
> rebuild both distributions. No signed APK is produced by this pass.

## Why

The 2026-11-06 pass fixed the row system: one line per rule, a trailing answer,
quiet section labels, a page-aware bar. It left the page *composition* and the
visual voice untouched. On the emulator the first screen was still a large
rounded card containing a ring mark, a centred verdict, three tinted layer
tiles, a whole next-step block, and a second card for DNS; the rules began
below the fold, the three tabs used several unrelated card shapes, and every
icon was the same bright blue. It looked like a competent Material dashboard, not a
product with a point of view.

The same problem appeared at the two moments that matter most. The block page
led with a filled icon tile and a generic reason sentence, then a card called
**Breathe** with the breath, a reminder, four need buttons and the day's count
all visible at once. The pause was thoughtful; its first screen was a form. The
welcome page was a 164 dp blue icon tile over a centred sentence.

This pass keeps the policy and the copy that carries obligations, and changes
the composition, type, surface and sequence around them.

## What was studied

The public store listings and screenshots for the products below were read on
7 November 2026. Nothing was installed, decompiled or benchmarked for this
pass. Screenshots in the stores are marketing captures, not independent tests.
**No competitor code, asset, font, illustration, palette or string was copied.**
The borrow is at the level of interaction and information order.

| Product | Mechanism and useful pattern | Taken into this pass | Not copied |
| --- | --- | --- | --- |
| [Opal](https://play.google.com/store/apps/details?id=com.withopal.opal) | Device-wide focus app; a photographic full-screen mood, one score/verdict, a large timer, rules and routines. | The verdict is the page; one thing to do next. | The score, charts, streak, history, atmospheric photography and subscription surface. |
| [Jomo](https://apps.apple.com/us/app/jomo-screen-time-blocker/id1609960918) | App blocker with a playful cobalt skin, **Instagram is blocked**, a reflection countdown, named unlocking challenges and pre-made rules. | Named rules instead of categories; a block screen that is a place to stop, not an error dialog. | The brush lettering, 3D objects, streak, challenges, coins and the subscription funnel. |
| [one sec](https://play.google.com/store/apps/details?id=wtf.riedel.onesec) | A single-purpose breathing intervention before an app opens, a strict blocker and a short "I don't want to open" path. | The breath is the first screen, not a secondary card; the way out is always visible. | The forced delay and the intervention counter as a public score. |
| [ScreenZen](https://play.google.com/store/apps/details?id=com.screenzen) | Calm rows and plans, **Is this important?**, pause before opening, open limits and schedules. | One question after the breath; progressive disclosure of the reflection. | Open counts, timed unlocks, streaks and the "most customizable" breadth. |
| [Brick](https://play.google.com/store/apps/details?id=com.brickllc.brick) | Monochrome restraint, very large time and selection figures, **You've selected 3 distractions**. | The typographic hierarchy: a serif statement, few boxes, named counts. | The physical NFC brick, the "sessions" model and the weekly chart. |
| [AppBlock](https://play.google.com/store/apps/details?id=cz.mobilesoft.appblock) | Strict mode, schedules and a blocked screen with the app's icon, name and open count. | A blocked state that says what happened in one sentence and offers one exit. | The statistics, open counts, strict-mode ladder and ad-supported surface. |
| [Clearspace](https://play.google.com/store/apps/details?id=com.clearspace.app) | A blocked app leads to one required action (breaths, push-ups) before a short unlock. | The block screen as a deliberate pause with a single task. | Exercise challenges, budgeted opens and the trial/paywall flow. |
| [Canopy Shield](https://play.google.com/store/apps/details?id=com.canopy.vpn.filter) | Family filter with clear device status, app management and named allow/block lists. | Grouped, plain-language management; a state word rather than a technical summary. | The account, family dashboard, cloud Ally and location features. |
| [BlockerX](https://play.google.com/store/apps/details?id=io.funswitch.blocker) | Adult-content blocker with streaks, a journal, courses, coins, a community feed and an AI/VPN pitch. | Nothing visual; it is the clearest example of what Hisn is not. | Gamification, community, courses, coins, AI chat, claims and social proof. |

The [Opal vs Jomo comparison](https://screentimeindex.com/posts/opal-vs-jomo/)
was read for the trade-off between harder locks and shorter reflection
prompts. It describes Jomo's shorter prompts as less punitive and Opal's stats
as its main differentiator. Hisn keeps its own lock rules and adds neither a
score nor a budget.

## The direction

**Ink and paper on Android.** Hisn already has this language on the Mac: warm
neutral paper, ink text, one deep ink-blue accent, a serif statement per page,
hairline grouping and compact controls. Android had a cool blue-grey Material
palette and pill-shaped controls from the previous pass. Extending the Mac
language to the phone makes the product one thing rather than two skins, and
it is a stronger identity than copying a competitor's cobalt or charcoal.

The pass is not "make Android look like the Mac". It keeps Android's material
and density: a bottom bar, 48 dp actions, the system switch, the framework app
picker and dialogs. It changes the page composition and the type, not the
platform grammar.

### Design tokens

| Token | Day | Night |
| --- | --- | --- |
| Canvas | `#F4F1EA` warm paper | `#141311` warm charcoal |
| Surface | `#FFFDF8` | `#1C1A17` |
| Ink | `#1C1A17` | `#F2EDE3` |
| Secondary ink | `#5E574B` | `#BDB5A7` |
| Accent | `#1F3A5F` deep ink blue | `#A7C2E2` light ink blue |
| Success | `#336B4B` | `#8FCCA1` |
| Warning | `#855B10` ochre | `#E7C17F` |
| Danger | `#A13B2B` brick | `#ECA99E` |

Card radius is 16 dp, controls 10–12 dp and section labels 11 sp with 0.12
tracking. Tracking is disabled in Arabic, where it breaks joins, as on the Mac.

| Pair | Ratio (day) | Ratio (night) |
| --- | --- | --- |
| Primary ink on surface | 17.08:1 | 14.88:1 |
| Secondary ink on canvas | 6.33:1 | 9.13:1 |
| Tertiary ink on canvas | 5.00:1 | 5.97:1 |
| Accent on surface | 11.30:1 | 9.47:1 |
| Success on surface | 6.17:1 | 9.36:1 |
| Warning on surface | 5.90:1 | 10.20:1 |
| Danger on surface | 6.50:1 | 8.88:1 |
| On-accent on accent | 11.30:1 | 9.94:1 |

## What changed

### 1. One verdict, one next action

The Protection page no longer opens with a card. It opens with a 2 dp accent
rule, a tracked **PROTECTION** line, a 33 sp serif verdict and one state-specific
sentence: **Protection is off**, **Partly protected** or **Protection needs
attention**. The colour of the rule and the verdict follows the state. The
generic "Each layer covers a different path" sentence is gone; the sentence now
says what the state means.

The three layers are a **coverage ledger**: one white sheet, three columns,
hairline dividers, a state dot and a state word. It is the fastest read of what
is running and it fits in one row. The three tinted tiles are gone.

The **next step** is a labelled section with one sentence and one filled
button. On a page where DNS is the next step, the section hides and the DNS
card owns the primary action, so there is never a second filled button. The
DNS card is the only card with a status pill; its start button becomes primary
only when it is the next step.

This is the Opal/Brick hierarchy — one answer, one move — without their score,
timer or history.

### 2. Named rules, not categories

Rules now opens with **Common distractions**: twelve named sites (YouTube,
Reddit, X, Instagram, Facebook, TikTok, Twitch, Netflix, Hacker News, Amazon,
Pinterest, LinkedIn). One tap writes that site's domain into the existing
blocked-domain list. A selected chip is filled ink; an unselected chip is a
hairline outline. The row scrolls horizontally and shows the start of the next
chip, so it is obvious there is more.

The chips are deliberately not a category system. A category ("social media")
would hide what is blocked and would let one tap remove a rule the person cannot
see. The names are the same twelve the Mac craft pass uses. The copy says the
list is examples, not categories, and that each chip saves its domain in
**Blocked domains**.

The chip writes through the ordinary `PolicyStore.update` path, so:

- a protection lock freezes the chips, like every other saved rule;
- the app's existing refusal and readback rules still apply;
- a chip covered by a wider rule stays selected and explains the wider rule
  instead of silently deleting it;
- no new permission, service, network call or stored field exists. The only new
  core code is a pure `DistractionSites` helper (state, add, remove) and its
  unit test.

This is the Jomo/Brick "pre-made, named rules" pattern at the scale Hisn can
honestly support: named sites, not a silent category bundle.

### 3. The block screen is a place, not an error

The block page now opens with **A BLOCK** (or **A PAUSE** for a voluntary
pause), a 32 sp serif statement, and the reason in one quiet sentence. The
filled icon tile is gone. The breath is the page: a fine ink circle with a
radial accent wash, the inhale/exhale word, the cycle count and three quiet
dots. **Back to home** is visible immediately and is never gated by the
exercise. A voluntary pause uses the quiet **Back to Hisn** exit.

After the third breath, the page reveals the reflection: a vertical accent
rule beside one reminder, the serif question **What do you need?**, the four
need choices and the day's count. The reflection fades in; it is not a second
screen and it does not hide the exit. **Breathe again** is a quiet action above
the way out.

This is one sec's breath-first composition and ScreenZen's one question after
the pause, with Hisn's rule that the person can always leave. There is no
unlock, no open limit, no countdown to a bypass and no exercise challenge.

The completed state was checked at the full three breaths: on a 1080 × 2400
screen the exit, the reminder, the question and all four choices are visible,
and the day's count closes the page below them.

### 4. The first run is a page, not a poster

The welcome pages replace the 124–164 dp filled icon tile with a 64 dp hairline
seal, a serif statement at 32 sp and a 15.5 sp body. The three intro pages and
the permission checklist are otherwise the same flow; the permission cards
keep their one action and their granted state. The final lock page uses the same
seal and statement. No page was added, skipped or made mandatory.

### 5. The guard page reads like the rest of the app

The direct-build protection-held page is left-aligned, with the same rule,
**HISN** eyebrow, serif title, body and a soft panel for the countdown. The
8-second hold, the 60-second self-close and the Home escape are unchanged. This
page is rarely seen; it should not look like a different product when it is.

### 6. Settings and the small things

Row icons are now secondary ink instead of accent blue, so the icon column is
quiet and the state word is the only colour. Cards are white sheets with a
hairline and no elevation. Section labels are tracked small caps; the app bar
is a vertical accent rule plus the serif wordmark, not an icon tile. The bottom
bar keeps its selected pill and gains the quieter icon colour. The page titles
are serif; the tagline is tracked upper case in Latin.

## What did not change

- No enforcement path, permission, consent sentence, lock length, partner
  path, seed, list or background policy changed.
- No account, telemetry, network request or browsing history was added.
- No streak, score, badge, open count, timed unlock, budget or "life back"
  claim was added.
- The app picker, domain/word editors, schedule, time-limit, commitment,
  uninstall, guardian and DNS consent flows are the same code paths.
- The 1.2.3 version and its signed artifacts are untouched. The captures are a
  debug build on the API 36 emulator, not a signed release.

## Captures

The images are in
[design/android-craft-2026-11-07](../design/android-craft-2026-11-07/README.md).
They are layout captures from the API 36 `hisn_ui2` emulator at 1080 × 2400
and 420 dpi, not pixel assertions or a user study. The before set is the 1.2.3
tree before this pass; the after set is the debug build of this tree.

| Before | After |
| --- | --- |
| `before-welcome.png` | `after-welcome.png` |
| `before-protection.png` | `after-protection-off.png`, `after-protection-partly.png` |
| `before-rules.png` | `after-rules.png`, `after-rules-connected.png` |
| `before-settings.png` | `after-settings.png` |
| `before-pause.png` | `after-pause.png`, `after-pause-reflection.png` |
| — | `after-block.png`, `after-block-reflection.png`, `after-shield.png` |
| — | `after-protection-dark.png`, `after-rules-dark.png` |
| — | `after-protection-ar.png`, `after-rules-ar.png` |

The **partly protected** capture was made by selecting one app, enabling the
accessibility service and reading back **Apps On**; the DNS layer was off, so
the ledger honestly shows a gap. The **rules** capture shows two selected
chips and two saved domains; the connected capture shows the guard connected
with one saved domain. The block captures use the debug fixture that opens the
restriction screen, with the word reason, because the emulator has no live
blocked app. The shield capture uses the debug fixture too. No capture claims a
real block, a real VPN session or a signed build.

## Verification

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** — every core suite, including the new `DistractionSites` cases, 564 strings, 16 plurals and 553 source references in English and Arabic |
| `:app:assembleDebug` | **PASS** — the debug APK used for the captures |
| `:app:lintDebug` | **PASS** — no lint errors |
| Debug install and launch on the API 36 emulator | **PASS** — English light/dark, Arabic RTL, the three tabs, the chip selection, a voluntary pause and the debug block/shield fixtures rendered and were inspected |
| Screenshots | 20 images, listed above; reviewed at 1080 × 2400 |
| Signed 1.2.3 artifacts | **Not rebuilt for this pass** — they predate it; a release needs a new version and a rebuild |

### Limits

- This is an emulator pass. No physical phone, signed APK, Play upload or
  device acceptance is claimed.
- No user study, TalkBack audit, large-font pass, tablet, landscape or
  foldable pass is claimed. The 1080 × 2400 phone at 420 dpi is the only
  geometry captured.
- The chips are twelve examples; they do not classify a site, and a domain that
  is not one of the twelve or in the signed list is still list-dependent.
- The block page does not name the blocked app. The guard's launch intent does
  not carry the package in this tree, and changing `TextGuardService` here would
  collide with the in-flight 1.2.3 work. Naming the app is the obvious next
  parity step with Jomo and AppBlock; it needs its own intent and copy review.
- The chip labels are brand names and stay Latin in Arabic, like the platform's
  own app labels. They are not translated.
- The reflection remains a suggestion, not a measurement. The app does not
  claim that a pause changes behaviour.
- The 1.2.3 signed artifacts do not include this pass. Rebuild and re-record
  them under the next version before publishing.

### Anti-slop checklist

- No filled rounded-square icon tile sits beside a heading. The app bar has a
  2 dp rule; the welcome seal is a hairline circle.
- No page has a card inside a card. The pause and block pages have no card at
  all around the breath.
- No row says "Explain"; help remains a control or a disclosure.
- One filled primary action per content screen. When DNS is the next step, the
  next-step section is hidden and the DNS button is the only fill.
- Page headings are states or sentences (**Partly protected**, **One breath
  first**), not generic nouns.
- No page-level gradient, glass, neon, purple or decorative shadow. The only
  gradient is the breath wash, which is the signal.
- Shared tokens are used on every page; the same surface, radius, type scale
  and spacing recur. Arabic drops tracking.
- The layout was rendered in light, dark and RTL before this record was
  written.
