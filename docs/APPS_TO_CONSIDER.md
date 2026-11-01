# Apps to consider blocking

This is a **candidate list for a deliberate self-control choice**, not a claim
that every app below is adult content. Most of these apps are ordinary social,
dating, messaging or browsing tools with a mixed audience. Blocking one blocks
all of it, including the harmless parts, and an app label can be renamed.

Use it only on your own device, or on a minor's device where you hold the
platform's parental authority. Do not install Hisn covertly on another adult's
phone; the project's consent rules are in
[the road to 1.0](ROAD_TO_1.0.md#38-abuse-and-consent-guardrails).

## How to apply the list

**Android:** Rules → **Apps to block** → search the app name → select it. The
picker lists installed, launchable apps only. A sideloaded app appears if it is
installed. The block returns you to the home screen with a restriction notice;
it is visible and can be changed when no commitment is active.

**iPhone/iPad:** Rules → Apps → **Choose apps**, then search the name in
Apple's Screen Time picker. The app never receives app names or bundle IDs,
only opaque tokens, so this list cannot be preselected for you. All six Safari
blockers cover Safari; every other browser or app has to be selected
separately. The shield needs Screen Time authorization.

**Mac:** there is no per-app block list. Hisn's content filter makes host/flow
decisions for every app, and the browser guard handles browsers. Use the list
to decide which browsers or apps to remove from the Mac, not as a filter rule.

## Start narrow

A list this broad is not a good default. Block the smallest set that matches
the actual pattern:

1. **Direct adult apps** that are installed. These are the least ambiguous.
2. **The one or two mixed apps the person actually uses for adult content** —
   usually Reddit, X, Tumblr, Discord, Telegram or a browser, not all of them.
3. **The specific TV/player or live-chat service**, only if it is the route.
   Do not block all television or all live social apps to catch one channel.
4. **Dating/hookup apps**, only if dating or hookups are part of the pattern.
5. **Extra browsers**, only if the main browser is not covered by the
   extension, DNS or Screen Time filter. Keeping one filtered browser is
   usually better than blocking every browser.
6. **Do not block** phone, SMS, emergency, banking, health, maps, school/work,
   password managers or authenticators. A lock that cuts those off is the lock
   people remove in a crisis.

## 1. Direct adult platforms

Block these if they are installed. Many are web-only or distributed outside
the app stores; the list is a set of examples, not a catalogue of every brand.

| App / platform | What it is | Note |
| --- | --- | --- |
| Pornhub, XVideos, XNXX, xHamster, RedTube, YouPorn, SpankBang, Eporner, Beeg | Adult video | Native apps are often sideloaded or region-limited |
| Brazzers, Reality Kings, Bang Bros, Mofos | Studio subscription sites | Mostly web; apps vary |
| OnlyFans, Fansly | Subscription adult content | Native app availability varies; web is covered by DNS/text rules |
| Chaturbate, Stripchat, LiveJasmin, BongaCams, CamSoda, Jerkmate | Live adult cams | Blocking the app does not block the website |
| AdultFriendFinder, Ashley Madison, Seeking | Hookup/affair platforms | Not all content is explicit; still a route |
| Literotica, Nhentai, Fakku, Nutaku, DLsite, F95zone | Adult fiction, manga or games | Often web/desktop; mobile apps are inconsistent |

## 2. TV, catch-up and media-player apps

Mainstream TV apps are **not adult apps**. They carry mature films, erotic
thrillers, documentaries and late-night material alongside ordinary TV, and
most offer a PIN or kids profile. Blocking the app removes all television.
Prefer the broadcaster's parental PIN and a filtered network unless the person
has chosen the stronger block.

| App | What it is | Note |
| --- | --- | --- |
| BBC iPlayer, ITVX, Channel 4, My5, UKTV Play, STV Player, S4C Clic | UK catch-up and live TV | Mature content with guidance/PIN; blocking removes all UK TV |
| Freeview Play, Freesat, Sky Go, NOW, Virgin TV Go, EE TV | Live/TV platform apps | Mature channels and on-demand; account/PIN controls may be weaker on shared devices |
| Netflix, Amazon Prime Video, Disney+, Paramount+, Apple TV, Max, Hulu, Peacock, Crunchyroll, Hayu, Discovery+ | Subscription streaming | Mature/erotic films and documentaries; profiles and PINs exist; block only by choice |
| Pluto TV, Tubi, Plex, Samsung TV Plus, LG Channels, Rakuten TV | Free ad-supported or live-channel apps | Large unregulated libraries and channels; app blocking covers only that device |
| MX Player, VLC, Kodi, Plex, Infuse, nPlayer, IPTV Smarters, TiviMate, "iPlayer Lite" | Generic local/streaming players | Play any downloaded file or IPTV stream; they are not a content source, and blocking them removes all local media |

**Do not confuse BBC iPlayer with “iPlayer Lite.”** BBC iPlayer is the official
UK catch-up service. Store listings using the name “iPlayer Lite” have described a
generic local media player, not a BBC app; copies and package names vary. Check the
icon and developer before blocking. A generic player is evidence of a player,
not of adult use.

## 3. Live chat, cam and chat-for-sex apps

This is the group where “live” matters: the adult content is in a live room,
private chat or a one-to-one conversation, not in a fixed video library.

| App / service | What it is | Note |
| --- | --- | --- |
| Chaturbate, Stripchat, LiveJasmin, BongaCams, CamSoda, Jerkmate, Cam4, Flirt4Free, ImLive, Streamate, xHamsterLive | Live adult cams and private chat | Often web-first or sideloaded; an app block does not cover the website |
| ManChat (manchat.co.uk, manchat.nl), AdultFriendFinder, Ashley Madison, Seeking | Gay chat/phone line, hookup or affair services | ManChat is a website/phone chat line, not an app; block the domain through the DNS/custom-site layer. The others may have apps |
| Chatroulette, Chatrandom, Camsurf, Flingster, Emerald Chat, Chatspin | Random video/chat roulette | Adult exposure is common; mostly web-first, so an app block is partial |
| Bigo Live, Tango, LiveMe, Poppo Live, Mico, Azar, MeetMe, Skout | General live rooms and chat with uneven moderation | Adult-adjacent live rooms and DMs; blocking removes the whole social app |
| Grindr, Scruff, Jack’d, Hornet, Taimi, HER, Lex | Gay/bi and LGBTQ+ chat/dating | Not adult content by itself; choose by the person’s behaviour, not identity. The same rule applies to straight dating apps |

**“Chat for sex” is usually a service, not an app.** Many of these are
web/phone lines (ManChat is one), so the durable block is the domain through
the DNS/custom-site layer, not an Android/iOS app selection. A phone chat
line needs carrier or device call controls; Hisn does not block phone
numbers. A webview inside another app can also reach them; the app block is
per app.

## 4. Mixed feeds and communities

These carry adult content alongside ordinary content. Blocking the app blocks
the whole community, including support, news and friends.

| App | Why it is here | What blocking costs |
| --- | --- | --- |
| Reddit and third-party Reddit clients (Relay, Boost, Sync, Infinity) | Large NSFW communities and profiles; third-party clients bypass the main app block | All of Reddit, including support and hobby groups |
| X (Twitter) | NSFW accounts, media and replies | All of X, including news and contacts |
| Tumblr | Adult and mature posts with labels | All of Tumblr |
| Discord | NSFW servers and DMs | All servers, including work, study and friends |
| Telegram | NSFW channels and groups; also a messaging app | All Telegram messages and channels |
| 9GAG, Imgur | Adult posts and comments in a general feed | The whole feed |
| Pinterest, DeviantArt, Pixiv | Adult art and pins with weak filtering | All creative/reference content |
| Wattpad, Medium, Quora | Mature fiction or adult spaces | All reading/writing |
| Bluesky, Threads, Mastodon | Federated or algorithmic feeds; server-dependent moderation | The whole social account |
| Archive of Our Own, FanFiction.net | Explicit fiction on the web | Web only; app blocking does not apply |

## 5. Short-video and social apps

Adult material appears in feeds, recommendations, live rooms and DMs even
where the platform's rules prohibit it. These are high-cost blocks.

| App | Why it is here | What blocking costs |
| --- | --- | --- |
| TikTok | Adult-adjacent feeds, live rooms and DMs | All short video and social contact |
| Instagram | Reels, stories and DMs | All photos, messages and contacts |
| Snapchat | Disappearing messages and media | All messaging and contacts |
| Facebook | Groups, Reels and Messenger-adjacent content | All social contact and groups |
| YouTube | Adult content violates policy but is uploaded; recommendations can drift | All video, music, learning and subscriptions |
| Twitch, Kick, Rumble | Live streams with uneven moderation | All live content and communities |
| Likee, Triller, Clapper | Short-video feeds with weaker moderation | All content |

## 6. Dating and hookup apps

These are not adult-content apps. They are on the list because dating and
hookups can be the route a person is trying to interrupt. Block only by the
person's own choice, not because of who they are.

| Apps | Note |
| --- | --- |
| Tinder, Bumble, Hinge, OkCupid, Plenty of Fish, Badoo | Mainstream dating |
| Grindr, Scruff, Jack'd, Hornet, HER, Taimi, Lex | LGBTQ+ dating/community; do not blanket-block these as "adult" — the same rule applies to straight dating apps |
| Feeld, Pure, Raya, Seeking, Ashley Madison, AdultFriendFinder, SugarDaddyMeet | Hookup, kink or affair-oriented; higher risk for this use case |

## 7. AI companions and chat

Some services allow or fail to prevent sexual role-play; policies and
jailbreaks change frequently. Many are web-only, so app blocking is partial.

| Apps / services | Note |
| --- | --- |
| Character.AI, Replika, Chai, Poe | General chat companions with uneven NSFW enforcement |
| Janitor AI, Candy AI, DreamGF, Muah.AI, SpicyChat | Mostly web; adult-oriented companions |

## 8. Browsers — the whole web

A browser is the largest possible block: it removes ordinary web use too. If
the browser is already covered by the Hisn extension, DNS or Screen Time web
filter, blocking the app is usually unnecessary.

| Apps | Note |
| --- | --- |
| Chrome, Firefox, Edge, Brave, Opera, Opera GX, Samsung Internet, Vivaldi, Arc, DuckDuckGo, Yandex, UC Browser, Puffin | Every one can reach the open web |
| Safari on iPhone/iPad | Covered by the six Hisn Safari blockers; app-level selection may not be available or needed |
| Tor Browser, Onion Browser, Orbot | Designed to bypass filters; blocking these is a tamper decision, not a content rule |

## 9. Messaging apps

Adult content can arrive in any direct message or group. Blocking the app
also cuts ordinary communication, so this is not a default.

| Apps | Note |
| --- | --- |
| WhatsApp, Messenger, Signal, Telegram, Discord, Snapchat, Kik, Session, Element, Wickr, Viber, LINE, WeChat | Block only with the person's explicit agreement; keep at least one route to family, work and emergency contacts |

## 10. Bypass tools

VPN, proxy, private-DNS and app-cloning tools are not adult content, but they
can remove every other layer. Treat these as a separate tamper-resistance
decision; the network and managed-lockdown docs cover them.

- VPN/proxy clients and "unblock" browsers.
- App cloners, parallel-space apps and second profiles.
- Private DNS or DoH apps that bypass the system resolver.
- Guest/private browser profiles and another browser.

## What not to block

- Phone/dialer, SMS, emergency and accessibility apps.
- Banking, payment, health, pharmacy, maps and transport.
- School, work, password managers and authenticators.
- Sex education, medical, recovery and support apps. A false block here can
  be worse than the content it prevents.
- An app solely because of the person's sexual orientation or gender identity.
  The list is about behaviour and routes, not identity.

## Limits of this list

- **"Maybe" is not evidence.** A mixed app is mostly ordinary; the block is a
  choice, not a classification result.
- **Labels can be renamed.** The Android picker shows the installed label; a
  renamed or cloned app can look innocent. Check the icon and app info if that
  matters.
- **App blocking is per app.** It does not cover a website opened in a browser,
  an in-app web view, another profile, another browser or another device.
- **iOS tokens are opaque.** The app cannot verify which apps were selected or
  read their names; the count is the only evidence.
- **No package IDs here on purpose.** Package names vary by distributor and
  change; a wrong ID is worse than a label search. “iPlayer Lite” is a good
  example: the label has been used for a generic media player, not the BBC
  service, and the package name can differ between stores. If a future preset
  needs exact IDs, it should be generated and tested per release.
- **This list is not exhaustive.** New apps appear constantly; review it when
  the person's phone changes.

Last reviewed: 2026-10-30.
