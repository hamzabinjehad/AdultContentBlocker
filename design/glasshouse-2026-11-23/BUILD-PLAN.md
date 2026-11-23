# The Glasshouse: from mockup to app

Concept: https://claude.ai/artifact/E6X2RsCZNPAgSxYe8jUjaG (source `index.html` here).
Look: painted cartoon (Higgsfield), `img/paint-1..9.jpg`, in the app as `res/drawable-nodpi/unrot_glass_*.webp`.

Status: **done** = in the app and checked on a device · **next** = the order to build · **later** = needs art, a partner or a decision.

## Today (home)
| Piece | Status | Where |
|---|---|---|
| Glass hero: painted sprout by stage, laurel stage name, "time given back since", ⓘ explainer | done | `UnrotGlassHero.kt` |
| Big `0d 0h 59m` number and next stage with a segmented bar | done | `UnrotGlassHero.kt` |
| Glass cracks when Unrot closes an adult page; Breathe within 10 min seals it with gold | done | `GlassState.java` (tested), `GlassMoments.kt` |
| "Had a hard moment": adult content got through breaks the glass (own report) | done | `UnrotGlassHero.kt` |
| Gold seal only after the breaths finish; Breathe ends by showing the glass | done | `BreatheActivity.kt`, `RestrictionActivity.java` |
| Shield pill in the header (On / 2 open / 2 steps) | done (existing chip) | `ShieldChip` |
| Four round tools (Focus, My notes, Instead, More) | done | `UnrotHome.kt` |
| Home layout as the mockup: Home tab, Day pill, beam over the glass, Breathe under it (floats once scrolled), amber banner with Show/Fix, check-in button + "Had a hard moment" | done | `ProtectionScreen.kt`, `UnrotHome.kt` |
| Cracked: live `6m 49s` countdown, "Nothing is lost", glowing "Breathe to seal it" | done | `UnrotGlassHero.kt`, `UnrotPauseFab` |
| Instead sheet: your own ideas, one tap closes feeds for 10 minutes | done | `UnrotInsteadSheet` |
| Today's three (check in, one pause, under your before) | done | `UnrotTodaysThree.kt` |
| Daily check-in sheet (mood + what pulls) | done | `CheckIns` in `UnrotTodaysThree.kt` |
| More sheet with every tool | done | `UnrotMoreSheet.kt` |
| Hold Breathe for quick choices | done (Breathe, My notes, Lock) | `UnrotPauseFab` |
| Night look (quiet hours: blue, moon, sleeping sprout) | done (not yet seen on a device during quiet hours) | `UnrotGlassHero.kt` |
| Live plant for Sprout → Flower (code-drawn on the seed painting, grows with time, sways, sparkles on a new stage) | built, not yet seen on a device | `GlassPlant.kt` |
| Air under the lamp: breathing glow, rising gold motes, glint over the glass | built, not yet seen on a device | `GlassPlant.kt` (`GlassAir`) |
| First-open tour on the hero | later | existing tour targets |

## Shield
| Piece | Status | Where |
|---|---|---|
| Glass as the status, amber dashed ring when something is off, one big fix button | done | `UnrotShieldStatus` |
| Tabs (All · Feeds and apps · Adult) | done | `UnrotShieldTabs` |
| Design review pass: soft header fade, pill dock with Home icon, breathe-ring icon, calmer banner, labelled check-in, sheets with scrim, gold ticks, quiet More tiles, cream tabs, serif Progress headings | done | several |
| Number tiles (adult sites, adult words, apps with a rule) | partly (`UnrotShieldNumbers`) | restyle |
| "Add a rule" button | next | |

## Breathe (CleanPath Meditation style)
| Piece | Status |
|---|---|
| Start screen with one sentence, rounds (3/5/10), Start | done (skipped from block screens) |
| 3-2-1 get ready | done |
| Flower that opens on in-breath, closes on out-breath | done (`BreathRing`, Unrot only) |
| "It passed" with the glass result | done |

## Progress (CleanPath Analytics style)
| Piece | Status |
|---|---|
| Gauge: less feed time than your before | done (`UnrotAnalyticsTop.kt`) |
| Two tiles (days with Unrot, today's check-in) | done |
| Meters: sleep (quiet hours), focus (running session), calm, kept out | done |
| This week strip coloured by daily check-ins | done |
| Wood: one swaying cartoon tree per day | in progress (other session, `UnrotWood.kt`) |
| "Understanding your progress" with "If you slip" | done (tap the gauge) |
| Wood: a tree a day on the hill, sized by time back, six kinds earned by day (oak 1, pine 7, cherry 14, lemon 21, olive 30, willow 45), snowy pines in winter, dry tree the day the glass broke, tap a tree | built (`UnrotWood.kt`), waits for one line in `UnrotProgress.kt` |
| Seed box, compost → willow, real trees, together | later |

## First run (24 screens, CleanPath order)
Partly done: chapter bar (You · Why it’s hard · What helps · Your plan), the four tricks page, "What helps" with the glass, and a hold-to-plant pledge are in `UnrotOnboarding.kt` (not yet walked on a device). Still to do from the concept's four chapters (You → Why it's hard → What helps → Your plan),
the four tricks, "your picture", the pledge and "your reason" are **next** as a rewrite of the
onboarding screens, reusing today's questions and setup steps.

## Art still to paint (Higgsfield, from `img/hf-cartoon.jpg` as reference)
Each tree kind (pine, cherry, oak, lemon, moon fern, willow, olive, cedar, winter pine), in-between stages
(young plant, bud, flower), open pane for Shield, dawn (light theme). The free account is out of credits.
