# Android craft pass — capture record

Layout evidence for the 7 November 2026 Android pass. These are emulator
captures from the debug build, not pixel assertions, a user study or a
screen-reader audit. The API 36 `hisn_ui2` emulator was 1080 × 2400 at 420 dpi.

The **before** set is the 1.2.3 development tree before this pass. The **after**
set is the same tree with the pass applied. The version was not bumped; the
signed 1.2.3 Play/direct artifacts predate this work. See the
[design record](../../docs/ANDROID_CRAFT_2026-11-07.md) for the research, the
decision and the limits.

## Before

| File | State |
| --- | --- |
| `before-welcome.png` | First run, first intro page: a 164 dp filled blue icon tile, a centred sentence and a pill **Next** button. |
| `before-protection.png` | Protection, light: one large rounded hero containing the ring mark, the verdict, three tinted layer tiles and the next-step block; DNS is a second card; the rules begin below the fold. |
| `before-rules.png` | Rules: a built-in switch card, a lone **App protection** row, then custom rules and apps. All icons are accent blue. |
| `before-settings.png` | Settings: two grouped cards, every icon accent blue. |
| `before-pause.png` | Voluntary pause, mid-breath: an icon tile, a **Back to Hisn** button above the card, then the breath, reminder, four need buttons and the day count in one card. |

## After

| File | State |
| --- | --- |
| `after-welcome.png` | First run, first intro page: hairline seal, 22 sp serif wordmark, 32 sp serif statement, 15.5 sp body, ink primary button. |
| `after-protection-off.png` | Protection, light, no layer running: **Protection is off**, the state sentence, the three-column coverage ledger, **NEXT STEP**, and the DNS card. |
| `after-protection-partly.png` | Protection, light, one app blocked and the guard connected: **Partly protected**, **Apps On** and **Network / Images Off**, with the DNS card as the only primary action. |
| `after-rules.png` | Rules, light: **Common distractions** with YouTube and Instagram selected, the shortened note, the built-in checks, the app-protection row and the saved-domain count. |
| `after-rules-connected.png` | Rules, light, guard connected: one selected chip, **App protection Connected** and one saved domain. |
| `after-settings.png` | Settings: the same rows in secondary ink, warm paper sheets and serif page title. |
| `after-pause.png` | Voluntary pause, mid-breath: **A PAUSE**, serif **Take a pause**, one reason sentence, the breath ring, **Breathe in…**, the cycle dots and the quiet **Back to Hisn** exit. No card. |
| `after-pause-reflection.png` | Voluntary pause, after three breaths: **Breathe**, the reminder quote, **What do you need?**, the four choices, and **First pause today.** The exit is still above the reflection. |
| `after-block.png` | Enforced block, mid-breath: **A BLOCK**, serif **One breath first**, the word-reason sentence, the breath ring and the primary **Back to home** exit. |
| `after-block-reflection.png` | Enforced block, after three breaths: the reminder quote, the need choices and the day's count. |
| `after-shield.png` | Direct-build protection-held page, after the 8-second hold: **HISN**, serif **Protection held**, **You can continue now.**, the filled **Back to Hisn** and the quiet home exit. |
| `after-protection-dark.png` | Protection, dark: warm charcoal canvas, light ink-blue accent and the same coverage ledger. |
| `after-rules-dark.png` | Rules, dark: selected chips in light ink blue, dark hairline sheets. |
| `after-protection-ar.png` | Protection, Arabic RTL: the tracked Latin-only rule dropped, the app bar on the right, the coverage order mirrored, the nav reversed. |
| `after-rules-ar.png` | Rules, Arabic RTL: the chip row starts at the right and the note and rows are mirrored. |

## What the captures show

- The command hierarchy is visible in one glance: verdict → coverage → next
  action. In the partly protected state the next-step section is hidden because
  the DNS card owns the same action; there is one filled button.
- The named chips are the only new interaction. They are selected in place and
  the saved-domain count below confirms the write.
- The pause is no longer a form. The breath and the exit are on the first
  screen; the reflection is revealed only after the three breaths.
- Arabic keeps its joins because tracking is disabled; the layout mirrors rather
  than leaving the English column order.
- The shield page is a rarely seen direct-build surface, but it uses the same
  rule, eyebrow, serif and soft panel as the rest of the app.

## Limits

- Emulator only: no signed APK, physical phone, Play upload or device
  acceptance.
- No user study, TalkBack audit, large-font pass, tablet, landscape or foldable
  capture.
- The block and shield captures use the debug fixture, not a live block or a
  direct-build guard.
- The 1.2.3 signed artifacts predate the pass and must be rebuilt under a new
  version before release.
