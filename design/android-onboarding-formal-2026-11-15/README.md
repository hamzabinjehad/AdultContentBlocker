# Android onboarding: formal, minimal, and what Hisn is for

Date: 15 November 2026. Debug build on the API 36 emulator, light theme.

## What changed

- **Formal marks.** The glowing gradient "gem" behind each opening page is now one outlined
  icon in a thin ring: no glow, no gradient, no motion (`01-welcome.png`). The wait-before-
  opening screen's filled circle is a thin ring that breathes.
- **Three pages that say what Hisn solves**, in place of the two feature pages ("Break the
  autopilot", "Keep your promise"). Each states a problem and, under a short rule, Hisn's
  answer (`02-problem-finds-you.png`):
  1. *It finds you first* → site filtering for the whole phone, words and images in chosen apps.
  2. *The moment is short* → a short wait before chosen apps.
  3. *A blocker is easy to switch off* → a lock set while calm.
  Unchaind and Unrot make this case with shame slides and invented numbers ("37% higher
  dependency", "106 days a year"); these pages say only what is plainly true.
- Story pages are now nine (was eight). The "How it works" label now starts at the first
  how-it-works frame; `03-problem-switch-off-before-fix.png` shows the bug this fixed (the
  label appeared one page early).

## Tried and removed

A cartoon companion (ten poses cut from the owner's illustrations, with a confetti burst) was
built and then removed the same day at the owner's request: Hisn stays formal. No mascot code
or images remain in the tree.

## Checked

`OnboardingTest` 17 of 17 and `OpenWaitTest` 4 of 4 on the emulator; string parity passes
(English and Arabic). Not checked: dark theme and Arabic by eye for the new pages; the
owner's phone (not connected).

## Second pass (same day), from Unchaind's 89 screens

Hisn already had Unchaind's flow (one question per page, held promises, problem pages, a setup
summary). Three things it still lacked, taken in the formal style:

- **Serif headings** (`04-welcome-serif-benefits.png`, `05-problem-serif.png`,
  `06-question-serif.png`). Unchaind sets its titles in a heavy serif over plain body text.
  Onboarding headings and questions now use the system serif, semi-bold; body text keeps the
  app's sans. No font is bundled; Arabic falls to the system Naskh face.
- **What you get, on the first page** (Unchaind's three welcome chips, as plain rows): blocks
  adult sites across the whole phone; a short wait before the apps that pull at you; and the
  existing privacy line.
- **The first promise is addressed by name** ("Sam, make it a promise"), as Unchaind's
  "X, today is a new beginning". Only the first of the three, and only when a name was given.

Left out again: the paywall and countdown discount, the "calculating" screen, the "37% higher
dependency" chart, the quit-by date, the rating request, and the shame slides.

Checked: `OnboardingTest` 17 of 17 on the emulator, including the fit checks at double text
size and right-to-left; string parity passes. Not checked: Arabic and dark theme by eye for
this pass; the owner's phone.

## Third pass (same day): "Why it is hard"

The owner asked for what Unchaind's five dark slides do (0232–0236: "pornography is a drug",
"it creates a vicious circle", "it rewires your body", "it separates you from God", "but you
can break free"): tell the person what scrolling and adult content do to the brain, then the
way out. Four pages now sit between the welcome and the three problem-and-answer pages, under
the header "Why it is hard" (`07`–`10`):

1. **Built to keep you looking**: endless novelty, and the reward system's pull toward it.
2. **It becomes a loop**: cue (boredom, stress, a late hour), tap, brief relief; with
   repetition it runs before a decision.
3. **What it costs**: heavy scrolling is *linked with* shorter attention and worse sleep; for
   *some people* adult content becomes compulsive. A small line says these are research links
   across many people, not a diagnosis.
4. **A habit can be unlearned**: remove the cue, add a pause, keep the rule in place. The next
   three pages show Hisn doing exactly those three things.

Each claim is only as strong as the research: novelty/reward and habit loops are established;
attention and sleep are stated as links; compulsive use as "for some people" (it is a
recognised condition, while "porn addiction" as a drug-like addiction is disputed). Nothing
says "drug", "rewires your body" or anything about faith or shame, and there are no numbers.

Story pages are now 13. `OnboardingTest` 17 of 17 on the emulator (fit at double text size and
right-to-left included); string parity passes. Not checked: Arabic and dark theme by eye; the
owner's phone.
