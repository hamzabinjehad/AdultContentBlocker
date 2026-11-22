# Unrot: mockup vs the real app (20 Nov 2026)

What `unrot-full.html` shows, and what the Android app really has today.

**Built** = in the app and close to the mockup · **Different** = built, but not as the mockup shows ·
**Re-themed only** = the old screen with the new colours and fonts, not redesigned · **Not built**

Research done so far: Breathe (Stanford 2023 cyclic-sighing study, one sec PNAS 2023 study),
setup and block screen (one sec, CleanPath), Today and Progress (CleanPath recording, one sec).
The other pages were not researched one by one.

## A · Onboarding (v2.2, built 20 Nov; pictures: onboarding-v22-row*.png, page: onboarding-v2.html)
22 steps, researched against the owner's captures of Unchaind (89 screens) and Unrot (64) and CleanPath's recording.
| Step | Status |
|---|---|
| Hello | Built (existing page) |
| See it work ×3 (feed closes, breath first, apps sleep) | Built: phone demos with numbered markers and legend |
| Your name | Built: used on year, time back, plan, finish, home tour |
| What you want back (6 goals) | Built: pre-fill quiet hours, wait / limit / close, adult filter |
| When it gets you | Built: one tap moves on; sets quiet hours |
| How it feels | Built: worded on the block screen ("You said scrolling leaves you tired") |
| Your real starting point | Built: 7-day Android usage events, gauge, opens a day, longest scroll (capped 4 h, ends on screen off), hardest hour; permission card + guess slider without access |
| What it adds up to | Built: year of dots, half as the goal |
| Your time back | Built: "Instead: …" on block and after-breathing screens |
| Apps, How firm, Try it (live), Quiet (dial, hint from answers), Note (title and ideas from answers) | Built |
| Your plan (diagram of own choices) | Built; plan saved here, before the switches |
| Two switches, Three notifications (lock-screen preview), First real test, Hold to begin (3 s, haptics, TalkBack action), Finish (7-day arch, dated milestone, aim) | Built |
| Home tour with name | Built |
| "Why we ask" (?) on every step, % progress, pill buttons, step colour wash | Built |

## B · Core screens
| Screen | Status | Gap |
|---|---|---|
| Navigation | Built | 4 labelled tabs Today · Shield · Progress · You (Hisn keeps its 3) |
| Today | Built | Date + Shield chip, "needs you" card only when something is wrong, day card, tools, today numbers, thought, note; floating button shrinks while scrolling |
| Breathe + After | Built | Better than mockup (research-based) |
| Shield | Built | One-sentence status card (quiet route check when healthy), numbers, rows sorted by outcome with coloured tiles (switch or arrow), rows say "needs app access" instead of claiming a result, "+ Add" sheet with six rule kinds, "All rules" page |
| Progress | Built | Tab with week bars, today numbers, milestones, hard-hours card with hour chart and opt-in nudge 15 min before (once a day, carries the person’s note, re-armed on boot/update); “?” explainer |

## C · Key moments
| Screen | Status |
|---|---|
| When a feed is blocked | Different (full page, not bottom sheet; no daily pass) |
| When something breaks | Built | Status card lists each part turned on as Running / Stopped; VPN-conflict card with both choices |
| Milestone celebration | Built | Once per milestone on Today, real numbers only; share card is a picture with the day count only |
| Explain mode | Built on Shield | Long-press a Shield row: plain words, Change it, Read aloud (Android TTS). Other pages not yet |

## D · Paywall — Not built (needs your prices and decision)

## E · Every feature has a home
| Screen | Status |
|---|---|
| Wait before opening | Built (today’s opened / closed-instead per app) |
| All rules | Re-themed only (reached from Shield) |
| You | Built (You card, Commitment, Keep it running, one row to Tools, appearance and help) |
| Dark mode | Built |

## F · States
| Screen | Status |
|---|---|
| Day one | Different ("Start your day count" card) |
| Time is up · Bedtime | Built (block page) |
| Protection lock on | Built on Shield (banner with time left, on-switches “Locked on”, limits “can be made stricter”, Breathe now) |

## G · Outside the app
| Screen | Status |
|---|---|
| Quick Settings + notification | Different (tile existed; Breathe action added to notification) |
| Home-screen widget | Built (day count + Breathe) |
| Add a rule (in-app sheet) | Built (Shield "+ Add") |
| Add a rule (Android share sheet) | Not built |

## K · What makes us different — all Re-themed only
Images and video · Blur people · Telegram · Website filter repair · Protection lock · Guardian pairing

## L · Detail screens
Sites list: search when long, “Often added” one-tap sites (built) · Schedule: the 24-hour dial above the exact pickers (built) · Daily limits: − / + on each row, + off during a lock (built; the mockup had the lock on −, which would block tightening) · Wait before opening: per-app counts (built) · Check a site: verdict + numbered steps (built) · VPN conflict: card on Shield (built) · Health check: re-themed only

## J · Lessons from CleanPath
Built: after-breathing actions, week strip, first-visit tour (3 stops, once), explainer sheet on Progress. Not built: goal cards, starting point, asking in context, "show me where" pictures.

## Order of work from here
Each item: study the code → research other apps → improve the mockup page → build → check.
1. ~~4-tab navigation and Today as drawn~~ done 20 Nov
2. ~~Shield tab as drawn~~ done 20 Nov
3. ~~Progress tab + hard-hours nudge~~ done 20 Nov
4. ~~You tab~~ done 20 Nov
5. ~~Milestone moment, "when something breaks"~~ done 20 Nov
6. ~~Onboarding gaps: live Try-it, clock dial, first real test, green finish~~ done 20 Nov
7. Detail screens L (done except health check), F lock state (done); K screens still re-themed only
8. Explain mode (Shield), first-visit tour, Progress explainer done 20 Nov; explain mode on other pages and "show me where" pictures still to do
9. Paywall (after your price decision)
