# Hisn: an Islamic recovery companion (Android)

Hisn is a strong **blocker** with a long, personal onboarding story. Unchain ("Unchain: Defeat Lust", unchainmen.com) is a **recovery app**: it tracks you and coaches you, but blocks nothing. The plan is to add Unchain's recovery loop to Hisn and give it an Islamic frame. The result is the one app that both blocks and helps you recover, built around the deen.

## What Unchain has (Nov 2026)

| Unchain | What it does | Hisn today |
|---|---|---|
| Intake assessment | Questions about triggers, history and goals | **Ahead**: 39-page story (name, chat, hours maths, feelings, triggers, reason, goals, pledge, Day One) |
| Streak + "Shield" | Clean-day counter. The shield's look evolves from cracked iron to gold | Missing |
| 180-day benefits timeline | Day 3, 7, 30 … milestones | Missing |
| SOS panic button | Physical challenge first, then 7 tools (breathing, journal, AI, urge surfing, reads, walk timer) | Partial: "First response" is rehearsed in onboarding, but there is no in-app button |
| Daily and weekly missions | Real-world challenges for XP and levels (Observer → Legend) | Missing |
| Habit replacement | 8 daily habits (cold shower, workout, reading …) | Missing |
| Daily check-ins and trigger detection | Learns risky hours and warns ahead of them | Partial: triggers are asked once, plus a schedule screen |
| AI coach | 24/7 "tough-love" chat | Missing (needs a server and has privacy cost) |
| Brotherhood and leaderboard | Anonymous community and ranks | Missing (needs a server and moderation) |
| Blocking | **None** | **Ahead**: DNS VPN, browsers, Shorts and Reels, image check, uninstall guard, guardian |
| Price | $9.99/mo or $79.99/yr, 3-day trial | Free |

## The Islamic version: what changes

The frame is "Guard your gaze, guard your time" (غض البصر وحفظ الوقت). This keeps the brainrot positioning: adult content and Shorts are both things the eye takes in. All religious text must be **authentic and cited** (surah:ayah, or hadith with collection and number), stored offline, and checked by a qualified reviewer before release. No fabricated or weak narrations are presented as authentic.

### 1. Streak → "Days of steadfastness" (أيام الثبات)
- Clean-day counter on Home, with Hijri and Gregorian dates.
- A fortress (حصن) that is built up stage by stage, instead of Unchain's shield. It fits the name Hisn.
- Milestones: 1, 3, 7, 14, **40** (the 40-day habit), 90, 180 and 365 days. Each milestone has a short benefit plus one ayah or hadith.
- Long-press to see your longest streak and history. Everything stays on the device.

### 2. SOS → "I'm being tested" (أنا في ابتلاء)
One tap from Home, the notification and a launcher shortcut. Steps:
1. **Leave the place and change your state**: stand up, make wudu, leave the room (the sunnah of changing state).
2. Say **A'udhu billah** and short adhkar on a 60-second counter.
3. **Lower the gaze** (24:30) as a full-screen reminder, with your personal reason from onboarding.
4. Toolkit: pray 2 rak'ah, a short Quran recitation page, breathing, a walk timer, call your guardian.
5. Hisn turns on the strictest blocking for 30 minutes (this reuses the existing pause and guard).

### 3. Relapse → Tawbah flow, not shame
- "I slipped" opens: the conditions of tawbah, the dua, and an optional 2 rak'ah of tawbah.
- The streak restarts, but **total clean days** and the longest streak are kept. Mercy, not reset-to-zero despair.
- An optional, private "what happened" note feeds trigger insights.

### 4. Habits → Daily deeds (أعمال اليوم)
These replace Unchain's 8 habits:
- 5 prayers on time
- Morning and evening adhkar
- Quran (pages a day)
- Fasting Monday and Thursday
- Night prayer (qiyam)
- Exercise
- Sleep before midnight
- Phone-free hour after Fajr

Each is a simple check-off with a weekly ring.

### 5. Missions → Weekly challenges
Examples: pray Fajr in the masjid 3 times; a day with no Shorts; visit or call family; give sadaqah; read one surah's tafsir. Levels use Islamic names (Murid → Mujahid → Muhsin), but **there is no public leaderboard**.

### 6. Prayer-time awareness (offline)
- Prayer times are computed on the device from a manually chosen city or coordinates. No location permission is required. Library: `adhan-java` (MIT).
- Optional: block Shorts and feeds from the adhan until the iqama window ends.
- Late-night risk: if the person's triggers include "late night", Hisn gets stricter after Isha and suggests the adhkar before sleep.

### 7. Ramadan and seasons
Ramadan mode: suhoor and iftar times, stricter feeds during fasting hours, and a last-10-nights checklist. Also Dhul-Hijjah first 10 days.

### 8. Onboarding: Islamic touches (keep the existing story)
- Add an optional "Faith" answer to "What do you want less of?", plus a question: "Do you want Islamic reminders?" (yes / light / no).
- Change Pledge to **Niyyah**, using the person's own words. Hold-to-promise stays.
- Truth page: add 24:30 and the hadith on the gaze, both cited.
- Day One: show the first adhkar and the SOS button.

### 9. Accountability (instead of a community)
Hisn already has a guardian. Add an optional **accountability partner** who gets a weekly streak summary and an SOS "call me" button. There is no public feed and no leaderboard: privacy and the Islamic value of covering sins (ستر) take priority.

### Deliberately not copied
- **AI coach**: it needs a server, conflicts with Hisn's offline privacy model, and carries a risk of wrong religious rulings. Revisit later, if ever.
- **Public community or leaderboard**: moderation cost, privacy risk, and it conflicts with ستر.
- **Paywall and trial**: free remains Hisn's edge.

## Suggested milestones (each becomes a GitHub issue)

| # | Issue | Size |
|---|---|---|
| M1 | Streak store + Home "Days of steadfastness" card + milestones (local, Hijri dates) | M |
| M2 | SOS flow + notification action + launcher shortcut + strict-mode 30 min | M |
| M3 | Relapse / tawbah flow, keep totals | S |
| M4 | Verified ayah/hadith content pack (en + ar), with citations and review | M (content) |
| M5 | Daily deeds tracker | M |
| M6 | Offline prayer times + "quiet at adhan" option | M |
| M7 | Onboarding: faith question, niyyah, Islamic Truth page | S |
| M8 | Weekly challenges + levels | M |
| M9 | Accountability partner summary | M |
| M10 | Ramadan mode | S |

The order is M4 → M1 → M2 → M3 → M7, which gives a shippable "recovery core". After that come M5, M6, M8, M9 and M10.
