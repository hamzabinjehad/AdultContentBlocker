# Competitor UI pass: ScreenZen, Opal, BlockerHero, Forest

Date: 13 November 2026. Device: the owner's Samsung SM-G990B2 (Android 16, German
system language). ScreenZen, BlockerHero and Forest were already installed; the owner
installed Opal from Google Play, signed in, and continued past its permission screen.
The owner accepted the suggested block list. Exploration ended at Opal's paywall, without starting a trial. Only screens were viewed. Screenshots
of Android's own permission lists were deleted because they show the installed apps.

**Incident.** While stepping through Opal's setup, a script did not recognise Opal's
permission screen. It opened Android's Usage access list and tapped near the bottom
of the list and the overlay list for about a minute. Afterwards Opal held "Display over
other apps" (it showed off before), and Opal's usage access was not granted. No other
app showed a recent overlay or usage-access change. Android does not record when a
switch was flipped, so this cannot be fully proven. The owner was told how to switch
Opal's overlay off.

## What each app does well

| App | Pattern | Screens |
| --- | --- | --- |
| ScreenZen | **A pause before a chosen app opens**: "Is this important?", how many opens are left today (dots), the streak, "Open in 4s" then "Open for 5 minutes", and Close as an equal choice. | `screenzen-pause-1.png`, `screenzen-pause-2.png` |
| ScreenZen | One card per app group on Home, and inside it daily limit, "Before unlock" (the pause), and time windows. | `screenzen-01.png`, `screenzen-group.png` |
| ScreenZen | "No data saved" under the support list, next to the privacy policy. | `screenzen-more.png` |
| Opal | **One sentence per screen** on a calm full-bleed background, "tap to continue". | `opal-01.png` … `opal-05.png` |
| Opal | Setup as a short chat: a question, chip answers, "Reviewing your responses…", then a personal line ("I can help with that, X Y"). | `opal-30.png` … `opal-35.png` |
| Opal | **Permissions as inline switches, each with one sentence of why**, right after the personal line. | `opal-36.png` |
| Opal | **A stark "cost" moment**: "15 years of your life looking down at your phone", then "Opal can help you get back 5 years". It is a projection from a guessed screen time, labelled in small print. | `opal-61.png`, `opal-62.png` |
| Opal | **Suggested block list from real usage**: the most used apps and their websites, pre-selected, each with Remove, and a count ("4 apps, 9 websites selected"). | `opal-63.png` |
| Opal | **"How it works" in three taps**: you open YouTube without noticing → you start scrolling → a nudge appears ("Time to go outside… you already spent 1h 23m"). It explains the product with the person's own story, not a feature list. | `opal-65.png` … `opal-67.png` |
| Opal | **A named commitment**: "X Y, Opal will get you back 5 years", three outcome rows, then a held "fist bump" button to agree. The paywall follows at once. | `opal-70.png` |
| BlockerHero | Missing permissions as cards at the top of Home ("Accessibility is OFF", "Turn ON"). | `blockerhero-01.png` |

## What to avoid

- **Guilt numbers.** Opal's "15 years of your life" is a projection from a self-reported
  guess. It works as marketing, but it is not a measurement, and for an adult-content
  blocker a shame figure can push the wrong way. Hisn shows facts it has (blocks, pauses)
  and nothing projected.

- **A streak flame.** ScreenZen's "8d 🔥" and BlockerHero's streaks turn a slip into a
  reset. Hisn's personal layer counts days with Hisn and pauses taken, not clean days,
  on purpose ([ANDROID_PERSONAL_2026-11-11.md](../../docs/ANDROID_PERSONAL_2026-11-11.md)).
- **A hard paywall at the end of setup.** After the fist bump Opal shows a full-screen
  trial offer ("Hol dir 2+ Stunden jeden einzelnen Tag", "Jetzt keine Zahlung fällig")
  with no close button; Back does nothing and restarting the app returns to it. The
  main app could not be reached without starting a subscription, so it was not.
- **An account before anything works.** Opal asks for Google, email or a phone number
  before showing a single setting. Hisn needs no account.
- **Upsell on the main screen.** ScreenZen's "Unlock Lifetime Access", BlockerHero's
  Premium crown and locked switches.
- **A wall of default-off switches.** BlockerHero's Home is a long list of switches that
  are all off, so the person has to decide everything at once.

## Where Hisn stands

| Pattern | Hisn today |
| --- | --- |
| Pause before opening | Hisn has a breathing pause **after** a block (`RestrictionActivity`), but no soft "pause, then you may open" mode for chosen apps. |
| One idea per screen | `WelcomeActivity` already has this structure (three intros, personal, permissions, lock), but it uses the old View widgets and doesn't match the Compose shell. |
| Permissions with a reason | Welcome has one card per grant with a sentence; the shell's Home shows "Fix gaps" and a list of what needs attention. |
| Privacy said plainly | It is in Settings › Privacy, but not stated anywhere on the main screens. |

## Recommended changes, in order

1. **Onboarding in the shell's look, told as a story** (finish-plan Phase 3.1). Same pages and
   disclosures, rebuilt in Compose. Each intro is one sentence on a calm full-screen
   page. Permissions become rows with one line of why and a live switch, marked
   *needed* (App protection, DNS) or *optional*. "Step n of 6" at the top, and Skip
   always available. Borrow Opal's "How it works": three short frames of what happens
   when a blocked site or word appears (you open it, Hisn steps in, you get a pause
   rather than a scolding), before any permission is asked.
2. **"Pause before opening" for chosen apps.** A soft mode next to Block and Daily
   limit: a few breaths, "Is this important?", the opens left today, then "Open" after
   a short countdown, with Close as an equal choice. It reuses the guard's app
   detection and the pause screen. It is never offered for adult content, which stays
   a hard block.
3. **Suggest, don't make people search.** Opal pre-fills the block list from usage. Hisn
   can do the same for the browser allowlist and app picker without leaving the phone:
   show the installed browsers and the most-used apps first, each with one tap to add.
4. **Actionable gaps on Home.** Each missing layer in "What needs attention" gets its
   own "Turn on" button that opens the exact Android screen, instead of only the
   combined "Fix gaps".
5. **Say privacy where people look.** One quiet line under Settings and on the last
   onboarding page: "Your rules and activity stay on this phone. No account."
