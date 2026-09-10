# Why this tool exists next to Apple Screen Time

Apple already ships adult-content blocking: Screen Time → Content & Privacy →
Limit Adult Websites, lockable with a passcode, and shareable across a family so
a second person holds that passcode. It is free, built in, and works on Mac,
iPhone and iPad. Any honest account of this project has to answer: then why
build anything?

The answer is a split. **Apple gives you the lock. This tool gives you the
coverage.** They are complementary, and the mistake to avoid is rebuilding the
half Apple already does better.

## Use Apple for the lock — do not reinvent it

For "cannot be turned off, across every device," Screen Time with Family
Sharing is simpler, free, and stronger than a custom Mac-only mechanism:

* It enforces uniformly on Mac + iPhone + iPad.
* On **iOS/iPadOS it is genuinely hard to beat**: every browser there is WebKit,
  so the web filter covers all of them, and the organizer holds the passcode
  remotely.
* Its can't-turn-it-off machinery is exactly what this repo's `LockStore`
  re-implements — only on Mac, and more fragile.

So the recommended lock is **Screen Time + a second person holding the
passcode** (or MDM/Supervision for the strong version). See `THREAT_MODEL.md`:
non-beatable always requires that the authority sits with someone who is not the
user — Screen Time just makes "someone else holds it" a passcode instead of an
admin-account dance.

## Use this tool for what Apple's filter misses

Apple's adult filter is a black box that blocks a shallow, English-centric
subset. Four gaps are the reason to exist:

1. **Arabic and non-English coverage.** Apple's classifier badly under-blocks
   Arabic adult content. The 5,547 dialect-aware Arabic terms
   (`blocklist/gen_terms_ar.py`) are the single largest thing Apple does not
   have. For an Arabic-speaking user this alone justifies the tool.

2. **Page-text scanning.** Apple filters by *domain category*; it does not read
   the page. Adult content inside an allowed general site — social media,
   search results, a wiki — and brand-new domains registered today walk past
   Screen Time untouched. The extension's content scoring (`lib/score.js`)
   catches those.

3. **Transparency and tuning.** Apple will not say what it blocks or let you
   change it. This list is auditable (982k domains from five sources, signed)
   and the user adds their own words and domains and tunes sensitivity.

4. **The Mac non-Safari gap.** On **Mac specifically** Apple's web filter is
   Safari-leaning, and Chrome / Helium / Brave can bypass it because they are
   not WebKit. The socket-level filter (`HisnFilter`) sees every app's traffic
   and is VPN-proof; the profile's per-browser DoH lock closes what Screen Time
   leaves open on Mac. On iOS this gap does not exist (all WebKit), so this
   layer is a Mac concern only.

## What this means for the roadmap

Stop investing in re-building the lock. Invest in the content advantage.

* The Mac app's durable value is the **socket filter** (Mac's Screen Time gap)
  plus **delivering the Arabic list and the scorer** — not its lock clock.
* On **iOS/iPadOS**, the value is a Safari content-blocker / network filter
  carrying the **same Arabic list**, running *under* Screen Time's lock rather
  than replacing it.
* `LockStore`'s commitment-device (delayed self-release, partner approval) is
  optional: pleasant for a solo user with no second person, redundant the moment
  Screen Time holds the passcode. It is not the product's edge.

## The one-line pitch

> "Everything just from Apple" is free, lockable and cross-device — but you are
> trusting a shallow English-centric filter that misses Arabic, misses page
> content, and leaks on non-Safari Mac browsers. This tool is the deep,
> transparent, Arabic-aware content layer that fills exactly those holes —
> installed under Apple's lock, not instead of it.
