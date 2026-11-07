# macOS adult-content blockers — competitive research

> Research record, 7 November 2026. This pass changed no code, policy, list or
> release state. It records what the products a Hisn Mac user might choose
> actually document for macOS, where Hisn is already ahead, and which gaps are
> worth closing. Vendor pages are marketing claims, not independent tests; see
> [Evidence limits](#evidence-limits).

Scope: adult-content filters, accountability tools and self-control blockers
with a macOS story. Mobile-only products are named only where they explain a
pattern. No competitor was installed, decompiled or benchmarked for this pass.

## The short answer

The macOS market is not one category. It is five different mechanisms:

1. **Device-wide content filters** — Canopy, Covenant Eyes, Qustodio, Mobicip,
   Kaspersky Safe Kids. They install an agent and claim coverage across apps and
   browsers.
2. **Network / DNS filters** — NextDNS, Mobicip's DNS path, CleanBrowsing. Cheap,
   device-wide, and blind to page content.
3. **Browser extensions** — Canopy's Chrome path, HaramBlur, Safari content
   blockers. They can read the page; they only cover the browsers they are
   installed in.
4. **Accountability / screen monitoring** — Covenant Eyes is the clearest
   example: random screenshots go to a human ally.
5. **Focus and list blockers** — Opal, Cold Turkey, SelfControl, Freedom. They
   win on friction, schedules and app blocking, not on classifying adult
   content.

**Canopy's real moat is not the blocklist.** It is the promise that the page
keeps working: AI removes explicit images, videos and text, including in AI
chatbots; apps can be blocked; a human or AI Ally handles the moment the person
wants to weaken protection. **Covenant Eyes' moat is a person**, not a filter:
an ally receives screenshots and check-ins. **Opal, Cold Turkey and Freedom win
on friction design** — sessions, difficulty levels, app blocks, allowances and
typed-text unlocks. **NextDNS wins on price and reach**: one DNS profile covers
the device and enforces SafeSearch and YouTube Restricted Mode.

Hisn already has the hardest parts those products do not have together:

- a **socket-level content filter** that sees every app's new flows and is not
  defeated by simply switching DNS or turning on a VPN for those new flows;
- a **5.28M-domain signed list** plus an Arabic-aware **5,831-term** page
  scanner, adult-TLD rail and hostname-term rail;
- **app network blocking** (the app opens but reaches nothing), **strict
  allowlist mode**, a partner-signed immediate release with a 48-hour
  self-release fallback, a root guardian path and MDM organization mode;
- **no account, no browsing log and no telemetry**.

The gaps that matter on macOS are narrower and more specific:

1. **Safari has no Hisn page-text or visual coverage.** The socket filter sees
   Safari's domains, but not the Arabic page scanner. Hisn's own browser guard
   closes Safari because there is no supported extension. This is the largest
   coverage gap.
2. **AI chatbots are not a named surface.** Canopy advertises filtering in
   ChatGPT, Grok, Gemini and Claude. Hisn's generic page scanner may catch
   visible text, but there is no verified chat adapter or acceptance test.
3. **Adding a service is hand work.** Canopy, Qustodio and NextDNS let a person
   block "social" or "TikTok" in one action; Hisn asks for domains or a
   `.app` picker one at a time.
4. **The human moment around an urge is thin.** Hisn's partner can approve an
   early release, but there is no ongoing, private accountability presence
   comparable to Canopy's Ally — and Hisn should not copy the cloud version.
5. **The Mac build is still unsigned and unnotarized.** Canopy, Covenant Eyes
   and Qustodio install like normal Mac apps. No feature closes that gap except
   release engineering.
6. **One dashboard for the family is missing.** Canopy, Qustodio and Mobicip
   sell it. Hisn's Guardian Mode plan owns this; it is not a quick Mac feature.

The recommendation is not to become Canopy. It is to close the coverage gaps
Safari and chatbots expose, borrow the *friction* patterns that fit Hisn's
commitment model, and keep the two things no competitor has: Arabic depth and
the promise that nothing about browsing leaves the Mac.

### Choosing between them

| If the person wants… | Best current Mac answer | What Hisn should take from it |
|---|---|---|
| "Don't block the site, just hide the explicit parts" | Canopy | Safari and AI-chat coverage; keep withholding adult media rather than copying the white rectangle. |
| "Someone else should be involved" | Covenant Eyes | A private companion and a partner note; not screenshots. |
| "Lock me out for weeks or months" | Hisn, SelfControl, Cold Turkey | Hisn already has the strongest honest lock; ship it signed. |
| "Twenty-five minutes of focus" | Opal, Cold Turkey, Freedom | A short, tightening-only session; no daily budget. |
| "One dashboard for the family" | Qustodio, Mobicip, Canopy | Guardian Mode; status and lock state, not browsing. |
| "Free and built in" | Screen Time, SelfControl, NextDNS | Position Hisn as the deeper layer under Apple's lock, not a replacement. |

## What the leaders actually document for macOS

### Canopy

Canopy lists macOS support and says it runs on iOS, macOS, Android, Windows and
Chrome ([Canopy home](https://canopy.us/), [FAQ](https://canopy.us/#faq)). Its
pitch is **filtering without blocking the whole internet**:

- The SafeSmart filter scans images, videos and text in real time in browsers
  and "some apps", replacing explicit media with harmless white rectangles
  rather than blocking the site ([technology](https://canopy.us/our-technology/)).
- It filters **AI chatbot conversations** — ChatGPT, Grok, Gemini and Claude —
  "at the browser and device level", and can block the AI category outright
  ([Canopy home](https://canopy.us/)).
- App management can cut off an app's internet access or block the app; Focus
  Time and screen-time controls sit alongside filtering.
- **Ally** is the retention feature: a human partner or an AI Ally reviews
  requests to change protection, talks the decision through against the goals
  set at setup, and can approve or refuse. Canopy says it keeps no reports,
  logs or judgement ([Ally](https://canopy.us/ally/)).
- Removal prevention, location alerts and sexting prevention are part of the
  family product. Filters include an optional lingerie/swimwear setting.
- Pricing is $8.33, $9.99 or $12.50 per month billed annually for 1, 3 or up to
  10 devices, with a 7-day trial ([pricing](https://canopy.us/pricing/)).

**What to learn:** sell the outcome ("the page still works"), treat chatbots as
a first-class surface, and make the moment of weakening a conversation rather
than a toggle. **What not to copy:** the cloud account, the AI Ally reading
settings and conversations, and any accuracy claim without a benchmark. An
independent 2026 comparison reports inconsistent AI filtering and some
overblocking in Canopy's tests ([Sandstudio comparison](https://www.sandstudioapp.com/parental-insights/canopy-vs-covenant-eyes/));
that is one review, not a benchmark, but it is a warning against precision
claims.

### Covenant Eyes / Victory

Covenant Eyes is the opposite trade: it watches the screen and sends it to a
person. On **Mac 11+** it takes random screenshots, sends them to an ally, blocks
explicit domains, and supports a custom block/allow list across browsers and
apps ([Victory](https://www.covenanteyes.com/victory/)). Screenshots are
blurred, distorted and AES-256 encrypted; the ally relationship is human, and
Covenant Eyes explicitly says "real humans, never bots". Pricing is $18/month,
$198/year or $950 lifetime for up to 10 users ([Victory pricing](https://www.covenanteyes.com/victory/#pricing-section)).

**What to learn:** the accountability relationship is the product, and a
release decision made with another person is stickier than a timer. **What not
to copy:** screenshots and screen monitoring. It is the exact opposite of
Hisn's privacy statement, and it creates the most sensitive data a company can
hold.

### Qustodio

Qustodio's Mac tab lists a fuller desktop feature set than its mobile-first
reputation suggests: web filtering and activity alerts, games/apps blocking by
category and time limits, social-media limits, daily time limits, restricted
times, routines, pause-the-internet, YouTube monitoring and AI-powered alerts
([Qustodio features](https://www.qustodio.com/en/features/), [downloads](https://www.qustodio.com/en/downloads/)).

**What to learn:** routines and "pause the internet" are simple controls a
family understands immediately. **What not to copy:** the cloud dashboard and
the parental-control framing; Hisn's primary user is the person protecting
themselves.

### Mobicip

Mobicip's Mac page promises website filtering and adult-content blocking,
screen-time schedules, an activity summary, and removal protection: "Only the
registered admin user can uninstall Mobicip from the Mac"
([Mobicip for Mac](https://www.mobicip.com/parental-controls/mac)). It is a
managed Mac agent with a parent dashboard.

**What to learn:** one clear statement of who can uninstall it is more honest
and more useful than a vague "tamper-proof" claim. **What not to copy:** the
account-bound family dashboard as the only model.

### Opal

Opal's Mac app blocks and limits websites and apps, with sessions up to 24
hours, open limits and time limits, three difficulty levels, and a "No way, I'm
hardcore" mode that cannot be snoozed or ended early. A weekly Emergency Pass
is the only exit. It supports Chrome, Safari, Opera, Edge, Brave and Arc via
macOS Automation permission, and says all data stays on the device
([Opal Mac help](https://help.opalapp.com/legacyopal/what-is-and-how-do-i-use-opals-mac-app)).

**What to learn:** difficulty levels and a visible "you cannot end this" state
are powerful, and local data is a selling point. **What not to copy:** the
Emergency Pass and open limits as a general weakening surface. Hisn's partner
release is the stronger version of the same idea.

### Cold Turkey, SelfControl and Freedom

- **Cold Turkey** blocks websites, specific URLs, YouTube channels, wildcards
  and the whole internet; blocks `.app` bundles and device actions; schedules
  blocks; and offers a deep friction ladder: timer locks, delays up to 40 days,
  typed text up to 5,000 characters, passwords, restart locks, allowances,
  Pomodoro breaks, rewards and open counts. It also stores statistics locally
  and can block changes to macOS Screen Time settings
  ([Cold Turkey features](https://getcoldturkey.com/features/)).
- **SelfControl** is the free, open-source reference: a blocklist and a timer,
  and the block survives restarting the computer or deleting the app
  ([SelfControl](https://selfcontrolapp.com/)).
- **Freedom** sells sessions across macOS, Windows, iOS, Android, Chromebook
  and Linux: block apps, websites or the entire internet, set session length
  and frequency, recurring sessions ([Freedom](https://freedom.to/)).

**What to learn:** the friction ladder works. **What not to copy:** statistics,
allowances and open counts as a new history surface. Hisn's week-to-year lock is
already a stronger commitment; the missing piece is a short, tightening-only
session, not a daily budget.

### NextDNS

NextDNS is the DNS benchmark: parental-control categories for porn, violence
and piracy; SafeSearch enforcement across search engines including images and
video; YouTube Restricted Mode; per-app/game blocks (TikTok, Tinder, Fortnite,
etc.); Recreation Time; and configurable logs, including a no-logs mode
([NextDNS](https://nextdns.io/)). It is natively supported on major platforms
and is cheap compared with device agents.

**What to learn:** one-click service blocks and SafeSearch enforcement are
worth more than another dashboard. **What not to copy:** DNS-only coverage and
cloud logs as the product's centre.

### Apple Screen Time

Apple's own Mac guide describes the web-content setting under **Safari**:
Unrestricted, Limit Adult Websites, or Allowed Websites Only, with custom
allow/restrict lists ([Apple Screen Time on Mac](https://support.apple.com/guide/mac-help/mchlbcf0dfe2/mac)).
That is the free baseline Hisn is built under, and it is why the non-Safari Mac
gap exists: a DOM-reading filter in Safari is not the same as a system-wide
filter for Chrome, Electron apps and command-line tools. Hisn's
[positioning](POSITIONING.md) already states this split correctly.

### HaramBlur (adjacent, not a Mac system filter)

HaramBlur is a browser extension that auto-detects and blurs "Haram" content
for Islamic values, and is AGPL-3.0 ([HaramBlur](https://github.com/alganzory/HaramBlur)).
It is not a macOS system product and Hisn has not copied its code. It matters
as evidence of demand for *modesty-aware* visual filtering, not as a Mac
competitor.

### Products excluded from the matrix

Net Nanny's current Mac client and Bark's Mac story could not be verified from
an official page in this pass. Bark's desktop app is a monitoring dashboard for
linked mobile devices, and Bark Home is a network device, not a Mac content
filter. Scoring them as Mac filters would overstate what was checked.

## Feature matrix

`●` documented, `◐` partial or limited, `—` not offered/documented, `?` not
verified. These are documented capabilities, not measured accuracy. `—*` in the
Arabic column means no Arabic-specific layer is documented on that vendor's Mac
page; domain lists and AI may still block Arabic content.

### Coverage and mechanism

| Product | Mac delivery | Domain filter | Page text | Visual / AI | Native apps |
|---|---|---|---|---|---|
| **Hisn 1.1.17** | Content-filter system extension + DNS/profile + Chromium extension + browser guard | ● full 5.28M once installed; 236k seed before that; adult TLDs, host terms | ◐ Chromium only; Arabic + 6 other languages | ◐ Chromium, opt-in; adult media withheld | ◐ host/flow for every app; app network block; no content |
| **Canopy** | macOS app; vendor describes browser/device-level filtering | ● | ● AI text | ● images/videos, white rectangles | ◐ "some apps" + app management |
| **Covenant Eyes** | Mac agent (macOS 11+) | ● | — | ◐ screenshots to an ally | ◐ screenshots of apps/browsers |
| **Qustodio** | Mac agent | ● | — | — | ◐ app blocking |
| **Mobicip** | Mac agent / DNS | ● | — | — | ◐ screen-time controls |
| **Opal** | Mac app + browser Automation | ◐ list-based | — | — | ● app blocks |
| **Cold Turkey** | Mac app | ◐ lists/URLs | — | — | ● app/device blocks |
| **SelfControl** | Mac app/daemon | ◐ blocklist | — | — | — |
| **Freedom** | Mac app | ◐ lists | — | — | ● app blocks |
| **NextDNS** | DNS / native profile | ● categories | — | — | ◐ DNS only |
| **Screen Time** | Built-in | ◐ Safari web content | — | — | ◐ app limits |

### Control, accountability and trust

| Product | App control | Schedules | Accountability | Removal resistance | Local / no account | Arabic layer |
|---|---|---|---|---|---|---|
| **Hisn** | ◐ network cut-off only; no launch/time block | ● lock windows, curfew | ◐ partner release + pause count | ● locks, guardian, MDM; **unsigned build** | ● | ● 5,831 terms + normalization |
| **Canopy** | ● block apps | ● focus/screen time | ● human or AI Ally | ● claimed removal prevention | — cloud account | —* |
| **Covenant Eyes** | ◐ custom lists, no launch/time | — | ● human ally, screenshots | ◐ uninstall email | — | —* |
| **Qustodio** | ● app categories/time | ● routines, restricted times | ◐ parent reports | ◐ | — | —* |
| **Mobicip** | ◐ | ● | ◐ activity summary | ● admin-only uninstall | — | —* |
| **Opal** | ● | ● sessions/limits | — | ◐ hardcore mode | ● device-local | —* |
| **Cold Turkey** | ● | ● | — | ● lock methods | ● | —* |
| **SelfControl** | — | ◐ timer | — | ● timer/daemon | ● | —* |
| **Freedom** | ● | ● | — | ◐ | — account sync | —* |
| **NextDNS** | ◐ service domains | ◐ recreation time | — logs | ◐ | — (no-log option) | —* |
| **Screen Time** | ● app limits | ● downtime | — | ● passcode | ● | —* |

The matrix makes the strategic point: no reviewed product combines Hisn's
Arabic layer, no-account privacy and socket-level reach. Canopy is broader in
the browser and more social; Covenant Eyes is deeper in accountability and
worse for privacy; the focus apps are better at short-term friction; NextDNS is
cheaper and narrower.

## Where Hisn is already ahead

- **Arabic is not a translation layer.** The 5,831-term layer, normalization,
  evasion handling and RTL UI are code and data, not a localized settings page.
  No reviewed competitor documents an Arabic-specific filter layer.
- **The socket filter is stronger than DNS.** It sees new flows from every app
  before a VPN interface, and the hand-list/app-block policy is enforced there,
  not only in a browser ([`FilterDataProvider.swift`](../macos/HisnFilter/FilterDataProvider.swift)).
- **App network blocking is an honest app-control story.** The app still opens
  and still works offline; the filter refuses its traffic
  ([`UserBlocks.swift`](../macos/Hisn/UserBlocks.swift)). That is exactly what
  Canopy's "cut off internet access for an app" does, without pretending macOS
  can stop a launch.
- **Strict allowlist mode is the only mode that approaches complete coverage**
  of new domains, mirrors and mixed-content sites; the
  [threat model](THREAT_MODEL.md) says so plainly.
- **The partner release is cryptographically real.** A partner signs a
  challenge tied to this lock; the filter's authority checks the same signature
  ([`PartnerService.swift`](../macos/Hisn/PartnerService.swift)). It is stronger
  than a local passcode and needs no server.
- **Privacy is architectural.** No account, no browsing log, no screenshots,
  no telemetry, matching on-device. That is a product position competitors
  cannot copy without changing their business model.
- **The visual layer is already conservative.** Adult-positive verdicts always
  withhold the entire medium; person blur is a separate, opt-in preference
  ([person presentation](PERSON_PRESENTATION.md)). This is stronger than
  Canopy's white rectangle.

## The gap, ranked

| Rank | Gap | Why it matters on macOS | Proof from the market | Effort |
|---|---|---|---|---|
| 1 | **Safari has no Hisn page-text/visual layer** | Safari is the Mac's default browser; the socket filter is domain-only; the browser guard currently closes Safari | Canopy lists macOS; Covenant Eyes and Qustodio claim Mac-wide filtering | L |
| 2 | **AI chatbots are not a tested surface** | Canopy sells chatbot filtering as a headline feature; Hisn's generic scanner is unverified there | Canopy home and technology pages | M |
| 3 | **No named service bundles** | Canopy/Qustodio/NextDNS block a service in one action; Hisn asks for domains/apps one by one | Canopy app management, NextDNS service blocks | M |
| 4 | **The urge/accountability moment is thin** | Canopy Ally and Covenant Eyes' ally are retention engines; Hisn's partner appears only at release | Canopy Ally, Covenant Eyes Victory | M |
| 5 | **Unsigned, unnotarized Mac build** | Commercial products install cleanly; Hisn needs a build and terminal | Every commercial Mac product | L |
| 6 | **No weekly view of progress** | Canopy/Qustodio reports motivate; Hisn shows blocked-today only | Canopy, Qustodio | S–M |
| 7 | **No short focus session** | Opal/Cold Turkey/Freedom own the 25-minute use case; Hisn's presets run from a week to a year | Opal, Cold Turkey, Freedom | M |
| 8 | **No cross-device console** | Canopy/Qustodio/Mobicip sell one dashboard; Hisn has separate apps | Canopy, Qustodio, Mobicip | XL |

## Recommended backlog

Two items are P0 and can run in parallel: the Safari extension (product) and
the signed/notarized build (release). The release item is listed after the
product items because it has a separate owner and runbook.

### P0 — Safari Web Extension for Mac

**Problem.** A Hisn Mac user who opens Safari gets the socket filter's domain
decisions and nothing else: no Arabic page-text scoring, no visual inspection,
no block page. [`BrowserGuard.swift`](../macos/Hisn/BrowserGuard.swift) closes
Safari because there is no supported extension, which is honest but is a
coverage hole, not a feature. Apple's own content setting is Safari-only, so the
person may believe they are protected when Hisn's strongest layer is not
running.

**Design.**

- Add a macOS Safari Web Extension target to `macos/Hisn.xcodeproj`, bundled
  with the app. Reuse the shared scorer and seed assets from `extension/lib/`
  and `seed/`; reuse the `SafariWebExtensionHandler` pattern already present in
  [`ios/HisnWebExtension/`](../ios/HisnWebExtension/SafariWebExtensionHandler.swift).
- Two layers: a content blocker for the core domain tier and the four adult
  TLDs, and a content script for page-text scoring and media inspection. The
  latter can use the same local scorer; the visual engine is optional and
  opt-in, as in Chromium.
- Native messaging to the Mac app for the lock state, hand block/allow lists,
  custom words, service bundles and filter health. Do not add a new store or a
  new history.
- Update `BrowserGuard` so Safari with a *connected* Hisn Safari extension is
  treated as supported; an extension that is installed but disabled or
  unreachable must still produce the visible countdown. Evidence must come from
  the extension heartbeat, not from a Safari setting.
- Setup should deep-link to Safari's Extensions settings and explain that Apple
  requires the person to enable it. Do not claim it is on because the app is
  installed.

**Acceptance.**

- A known adult domain is blocked in Safari; an Arabic page-text fixture is
  blocked without a domain entry; an allowlisted site is reachable.
- A running lock freezes weakening in Safari exactly as it does in Chromium.
- Extension disabled or native host unreachable → the browser guard shows the
  same one-minute countdown, then closes Safari; no silent pass.
- Private-window limits are disclosed, not hand-waved.
- No host, page text or capture is written to disk or sent anywhere.
- JS scorer fixtures, content-blocker rule tests, native-handler tests and the
  Mac UI suite pass; an unsigned local Safari run is recorded separately from a
  signed-device acceptance.

**Risks.** Safari Web Extension packaging and notarization; user enablement;
private-window permissions; Safari DOM differences; the visual engine's
cross-origin limits are the same as Chromium's. This is the largest item in the
backlog and the one with the clearest coverage payoff.

### P1 — AI chat output guard

**Problem.** Canopy treats ChatGPT, Grok, Gemini and Claude as a surface. Hisn's
generic scanner may read visible chat text, but there is no adapter or fixture
that proves it, and chat UIs reuse nodes, stream tokens and virtualize long
conversations. A generic scanner can miss a response that arrives after the
first scan or hide an entire chat for one flagged paragraph.

**Design.**

- Add conservative adapters for `chatgpt.com`, `chat.openai.com`, `claude.ai`,
  `gemini.google.com`, `grok.com`, `copilot.microsoft.com` and
  `perplexity.ai`. Observe assistant-message nodes; scan their text with the
  shared scorer and their images/video with the existing visual engine.
- Hide or cover the flagged response, not the whole conversation; keep the
  prompt and benign answers usable. Explain privately why.
- No prompt text, response text or image is persisted or sent. Verdicts are
  in-memory and stale results are discarded on navigation.
- A conservative "AI chat" service bundle can block the sites entirely for
  people who want that; the content guard is for people who still need the
  tool.
- Locked commitments cannot turn the guard off; it can only be strengthened.

**Acceptance.**

- Synthetic chat fixtures: explicit text, explicit image, benign coding answer,
  medical question, Arabic answer, streaming answer, edited message, reused
  node, long virtualized thread. Explicit output is withheld; benign output is
  not.
- No raw prompt/response text in logs, storage or diagnostics.
- The guard does not break the chat input or scroll; CPU and mutation work are
  bounded.
- Disabling it while locked is refused.

**Caveat.** Native chat apps (ChatGPT desktop, Claude desktop) are outside a
browser extension. For those, the honest coverage is a service bundle that
blocks their domains or an app network block, not content inspection.

### P1 — Named service bundles, shown in full

**Problem.** Canopy and Qustodio offer "block social" or "block TikTok";
NextDNS offers service blocks. Hisn's Rules page asks the person to type
domains or pick apps one at a time. That is precise but slow, and it makes the
common "just block the apps that pull me in" request harder than it needs to
be.

Hisn's [extra-sites pass](MACOS_EXTRA_SITES_2026-11-07.md) deliberately chose
**named sites rather than opaque categories**. A service bundle must keep that
choice: the person sees every domain and app before applying it.

**Design.**

- Add a signed `seed/services.json` (or a signed section of the existing seed
  manifest) with reviewed bundles: social, short video, dating, gambling, AI
  chat, games. Each bundle has display names in English and Arabic, exact
  domains, app bundle IDs, a source note and a "what this does not cover" line.
- UI: "Add a service…" opens a picker. Each row expands to show every domain
  and app; the apply button says exactly what will be blocked. No opaque
  "category" toggle.
- Store bundle application with provenance, either as a new policy field or as
  an expansion into `customBlocks`/`blockedApps`. The filter, DNS build and
  Chromium extension must all read the same expansion. Prefer a policy field so
  removing a bundle cannot remove a hand-written domain that happens to
  overlap.
- Apply the same lock asymmetry: adding a bundle is tightening and works during
  a lock; removing it waits. An allowlist entry still wins.
- Never include shared CDNs, authentication domains or essential services.
  Exact domains only; no wildcard that could catch unrelated sites.

**Acceptance.**

- The picker shows every domain; no domain is blocked that was not shown.
- Applying a bundle blocks the expected domains in the filter and the
  extension; removing it restores them without touching hand lists.
- An allowlisted domain inside a bundle stays reachable.
- A running lock refuses removal and permits addition.
- Arabic names and RTL layout render; tests cover expansion, provenance,
  overlap and essential-service exclusions.

**Caveat.** "Social" bundles are a blunt instrument. A person may need
Messenger or WhatsApp for family; the picker must make exceptions obvious, and
the product must not pretend a bundle is a content classifier.

### P1 — A private companion at the urge moment

**Problem.** Canopy's Ally is a conversation; Covenant Eyes' ally is a person
who sees screenshots. Hisn's partner appears when a release is requested, which
is the highest-stakes moment, but there is nothing between locks. The
[Pause](../macos/Hisn/Pause.swift) is already a good breathing exercise with a
need question and a daily count. It can carry more of the human weight without
becoming surveillance.

**Design.**

- At setup (or in Settings), let the person write a short **"why"** and record
  a short **note they received from their partner** (or write their own). Both
  are local, editable by the person, and labeled as reminders, not evidence or
  authorization.
- Show the "why" and the note in the Pause window and before either exit is
  offered. Add a **60-second reflection** before the release request: the why,
  the note, the lock start and a single question ("What do you need right
  now?"). The person can still request the release after the minute; the
  partner still decides, and a self-release still waits 48 hours.
- Add a **10-minute wait** option that starts a calm timer and offers one
  optional offline action (water, wudu, a short walk, a call, or a prewritten
  Arabic/English reflection). No unlock, no timer that disables protection, no
  streak.
- Keep the existing exits: a partner's signature ends a lock immediately;
  without one, a self-release request completes after 48 hours. The companion
  cannot approve, shorten or bypass either.
- No AI, no network, no new browsing history; the only new data is the text the
  person chose to write and the pause count that already exists.

**Acceptance.**

- Neither exit is bypassed by the reflection: a partner signature still ends
  the lock immediately, and a self-release still takes 48 hours.
- The companion text cannot be used to weaken a lock; it is not authority.
- Arabic and English layouts, VoiceOver labels and long text are tested.
- The feature works with no partner configured (it simply shows the why).
- Nothing is sent anywhere; a test can inspect the store and find only the
  person's own text and the existing count.

**Why not an AI Ally.** A cloud model that reads the person's settings,
messages or browsing would violate the no-account/no-log position and would be
hard to make trustworthy in Arabic. A local LLM is too heavy for the value. A
deterministic reflection plus a real human partner is the honest version of the
same behavior.

### P0 (release blocker) — make the Mac build installable like a commercial app

**Problem.** The Mac target is still unsigned and unnotarized
([versions](VERSIONS.md)). Canopy, Covenant Eyes and Qustodio install from a
download; Hisn currently needs a build and a terminal. This is a bigger adoption
barrier than any missing feature, and it undermines the claim that the lock is
for ordinary people.

**Design.**

- Developer ID Application signing, hardened runtime, notarization and a
  stapled ticket; the system extension and native messaging host signed with
  the same team; the login item through `SMAppService`.
- A signed update path, a clean first-run approval flow for the content filter,
  and an uninstall path that does not leave a broken filter behind.
- Keep the honest limits: no claim of undeletability, no hidden admin, no
  vendor-held recovery key. The guardian split stays a human-held secret.
- Treat this as release engineering owned by [`RELEASE_RUNBOOK.md`](RELEASE_RUNBOOK.md),
  not as a UI polish item.

**Acceptance.**

- Gatekeeper opens the app on a clean Mac; the filter approval appears; the
  filter re-arms after reboot; the extension connects; an update installs and
  preserves the lock; a signed build's signature and notarization verify.
- The app can be removed without leaving the filter enabled or the network
  broken.
- The release record says exactly what was signed, on which macOS version, and
  what was not tested.

### P2 — Private counts, not a report

**Problem.** Canopy and Qustodio motivate with reports. Hisn deliberately keeps
no browsing history and no streaks
([extra-sites pass](MACOS_EXTRA_SITES_2026-11-07.md)). A small, local,
aggregate view could show progress without becoming a log.

**Design.** A 7-day ring buffer of counts only: blocked flows, pauses, days with
a lock, minutes a layer was unconfirmed. No hosts, no per-app usage, no goals,
no streaks, no score, no percentage. Show it in Overview as "This week on this
Mac"; make it optional and deletable. Do not add a dashboard, notifications or
a share link. This needs a product/privacy review before implementation because
it is close to the line the earlier passes drew.

**Acceptance.** Nothing stored can identify a site or an app; the buffer is
bounded; deletion works; no goal or streak language; the UI says "counts, not
history".

### P2 — A short, tightening-only focus session

**Problem.** Opal, Cold Turkey and Freedom own the "25 minutes of focus" use
case. Hisn's locks are 7, 30, 90 days or a year, which is the right tool for a
commitment and the wrong tool for a Tuesday afternoon.

**Design.** A separate session that blocks a chosen set of service bundles,
sites or apps for 25/50/90 minutes and cannot be ended early. It is an overlay
that only adds blocks; it never disables adult protection. No allowances, no
emergency pass, no open counts, no usage history. The session ends
automatically and leaves the baseline policy unchanged. It should use the
existing filter policy and lock invariants, not a second enforcement path.

**Acceptance.** Session blocks are active in the filter and extension; the
session cannot be shortened; sleep/wake and restart preserve the end time; an
active long lock is unaffected; when the session reaches its scheduled end it
restores exactly the prior policy. If the product stays adult-content-first,
this is optional and should not delay P0/P1 items.

### P2 — Cross-device guardian console

Canopy, Qustodio and Mobicip sell one dashboard for the family. Hisn's
[Guardian Mode plan](GUARDIAN_MODE_PLAN.md) already owns the design: device
owner/supervision levels, a guardian console and phone–laptop pairing. Do not
duplicate it in a Mac-only feature. The competitive lesson is that the console
must show **layer status and lock state, not browsing**, and that a signed
pairing protocol is the hard part. The Mac app can be the first console surface
once the pairing protocol is ready.

### P2 — Firefox extension

Firefox is a smaller Mac audience than Safari, but the browser guard currently
closes it for the same reason as Safari. A Firefox WebExtension could reuse most
of the Chromium content script and scorer. Do Safari first; Firefox is a
follow-on only if the browser guard's "unsupported browser" friction proves to
be a real adoption problem.

## Canopy, feature by feature

| Canopy feature | Hisn today | Recommendation |
|---|---|---|
| AI removes explicit images, videos and text without blocking the site | Chromium: page text + opt-in visual; adult media withheld; Safari: none | **Adapt, do not copy the white rectangle.** Keep withholding adult media. Add Safari and chat coverage. |
| AI chatbot filtering (ChatGPT, Grok, Gemini, Claude) | Generic scanner; no verified adapter | **Copy the surface, not the cloud.** Add chat adapters and fixtures. |
| App management / cut off an app's internet | App network block already implemented | **Improve discoverability** with service bundles; keep the honest "it still opens offline" copy. |
| Human or AI Ally | Partner-signed release only | **Adapt.** Add the private companion and a partner note; keep the human signature as the only authority. |
| Removal prevention | Locks, guardian, MDM, but unsigned | **Close the release gap.** Do not claim undeletability. |
| Focus Time / screen time | Lock windows and curfew | **Optional short session**, tightening-only; no time budgets. |
| No browsing history | Already stronger | **Keep and say it.** This is the differentiator. |
| Lingerie/swimwear filter | Person blur modes, adult hide | **Do not copy** without a benchmark; clothing classification is a new accuracy claim. |
| Cross-device dashboard | Separate apps; Guardian Mode planned | **Roadmap**, not a Mac-only feature. |
| Easy setup | Three-step welcome + detailed Setup | **Keep the three decisions; add a Safari step** and a one-line "what is covered now" summary. |

## What not to copy

1. **Screenshots or screen monitoring.** Covenant Eyes' model is the opposite of
   Hisn's privacy statement and creates the most sensitive data possible.
2. **A cloud AI Ally that reads settings or conversations.** It needs an
   account, a model and a data policy Hisn does not have.
3. **Undeletability claims.** No reviewed product can defeat an administrator,
   Recovery Mode or a second device; Hisn's [threat model](THREAT_MODEL.md)
   says so.
4. **System-wide visual blur through screen capture.** It needs Screen
   Recording permission, can be defeated by secure/DRM surfaces, and turns the
   app into a surveillance tool. Browser-level visual inspection is the
   proportionate version.
5. **Daily time budgets, open counts, streaks and scores.** The
   [extra-sites pass](MACOS_EXTRA_SITES_2026-11-07.md) rejected these for good
   reasons: they need a new history and a new weakening surface.
6. **Opaque categories.** "Social media" hides what will break. Show the
   domains and apps.
7. **A lingerie/swimwear classifier without a measured benchmark.** It creates
   false positives on ordinary clothing, swimwear, art and medical content.
8. **Blocking an entire site because one image was explicit.** Overblocking is
   a common criticism in the independent review; Hisn's hide-first, per-media
   approach is better.
9. **Location, sexting and family surveillance features.** Out of scope for a
   Mac self-control product and corrosive to the privacy position.
10. **An emergency pass that ends a commitment.** Opal's weekly pass is a
    usability valve, but Hisn's partner-signed release is the accountable
    version; do not add a second, weaker exit.

## Positioning

The one-line contrast:

> **Canopy filters what you see. Covenant Eyes shows someone what you saw.
> Hisn blocks the connection and reads the page in Arabic — and tells no one.**

Supporting statements, all defensible today:

- "The Mac filter sees every app's new connections, including browsers Hisn
  has no extension for." True; see `FilterDataProvider`.
- "Arabic is a first-class layer, not a translated settings page." True; cite
  the 5,831 terms and the normalization/evasion work.
- "There is no account and no browsing log." True; keep it true as features are
  added.
- "The app can cut off an app's internet without pretending macOS can stop it
  from opening." True; this is a more honest app-control story than most.
- "A partner is the only immediate exit; without one, a release request still
  waits 48 hours." True; do not add an emergency pass.

Do not say:

- "AI-powered" without a measured benchmark and a clear statement of misses.
- "Blocks all adult content", "complete", "unbreakable" or "cannot be removed".
- "Works in Safari" until the Safari extension ships and is tested.
- "Filters every app's content" — the socket filter sees hosts and flows, not
  encrypted page text.

## Evidence limits

- Vendor pages are marketing claims. No independent accuracy benchmark was run
  for Canopy, Covenant Eyes, Qustodio, Mobicip, Opal, Cold Turkey, SelfControl,
  Freedom or NextDNS in this pass. No competitor was installed on this Mac.
- Canopy's macOS mechanism is not documented publicly beyond "runs on macOS" and
  "browser and device level". The matrix records the documented promise, not an
  implementation detail.
- The Sandstudio comparison is one independent review with its own test
  devices; its overblocking and inconsistency observations are anecdotes, not a
  benchmark.
- Prices and device limits change. Canopy's and Covenant Eyes' prices were read
  on 7 November 2026 from their public pages.
- "No Arabic-specific layer documented" is not a claim that these products
  cannot block Arabic content. Domain lists are language-agnostic and AI may
  catch some Arabic; the table records documented capability, not a measured
  result.
- Hisn's own Mac build is unsigned and has no physical-device acceptance for
  this pass. Nothing here changes that.
- This document is a research record. The implementation notes are a proposed
  backlog, not a promise of a release.

## Sources

- Canopy: [home and FAQ](https://canopy.us/), [technology](https://canopy.us/our-technology/), [adults](https://canopy.us/adults/), [Ally](https://canopy.us/ally/), [pricing](https://canopy.us/pricing/), [parental control](https://canopy.us/parental-control-app/).
- Covenant Eyes: [Victory](https://www.covenanteyes.com/victory/), [pricing section](https://www.covenanteyes.com/victory/#pricing-section).
- Qustodio: [features](https://www.qustodio.com/en/features/), [downloads](https://www.qustodio.com/en/downloads/).
- Mobicip: [Mac](https://www.mobicip.com/parental-controls/mac), [home](https://www.mobicip.com/).
- Opal: [Mac app help](https://help.opalapp.com/legacyopal/what-is-and-how-do-i-use-opals-mac-app).
- Cold Turkey: [features](https://getcoldturkey.com/features/).
- SelfControl: [home](https://selfcontrolapp.com/).
- Freedom: [home](https://freedom.to/).
- NextDNS: [home](https://nextdns.io/).
- Apple: [Screen Time content restrictions on Mac](https://support.apple.com/guide/mac-help/mchlbcf0dfe2/mac).
- HaramBlur: [repository](https://github.com/alganzory/HaramBlur).
- Independent comparison: [Sandstudio, Canopy vs Covenant Eyes](https://www.sandstudioapp.com/parental-insights/canopy-vs-covenant-eyes/).
- Hisn context: [README](../README.md), [positioning](POSITIONING.md), [threat model](THREAT_MODEL.md), [coverage](COVERAGE.md), [visual protection](VISUAL_PROTECTION_PLAN.md), [person presentation](PERSON_PRESENTATION.md), [versions](VERSIONS.md), [release runbook](RELEASE_RUNBOOK.md), [guardian mode](GUARDIAN_MODE_PLAN.md).
