# Apps that carry adult content on Android and iOS

A research record of the applications through which adult content reaches a
phone, and how each intersects with what Hisn blocks. The domain layer already
covers a bounded set of hostnames; apps matter because several of the routes
below never touch a hostname that appers in a blocklist — a local-file player
needs no network, and a player built around a link host needs only that host,
which is deliberately not obvious from the app's appearance.

Not every app in the list is "adult content". Many are dual-use — a video
player, a cloud client, a client for a social network. They are listed because
they are the vehicles the adult ecosystem actually uses, and the line between
"innocent player" and "pipeline" is where the work is. Nothing here is a
blocking recommendation for the app itself; it is a map of the routes to
weight when the question is coverage.

## The X/Twitter video-link pipeline

The single most important finding. Native video on X is heavily compressed,
so the adult ecosystem there does not host video on X at all. A poster drops a
link to a file host, and the audience opens that link in a player or downloader
that fetches the bytes itself. The two hosts that dominate this traffic are
**MDisk** and **TeraBox**.

- **MDisk** (`mdisk.me`) is a video-oriented link host and shortener
  (`mdiskshortener.link`) that exists almost entirely to carry the videos X
  will not. Its community — across X, forums and app-store listings — is
  overwhelmingly adult-oriented.
- **TeraBox** (`terabox.com`, `1024tera.com`, `teraboxapp.com`,
  `terasharelink.com`) is a general cloud service whose link-sharing feature is
  the de-facto delivery network for adult video in the Middle East and South
  Asia. The share-link surface is where the adult traffic lives, not the app's
  home page.

This is why a DNS list alone missed it: `mdisk.me` and `terabox.com` look
innocuous to a curator, so upstream blocklists skip a host that in practice
routes a very large fraction of the region's adult video. These hostnames now
live in `extra_block` in `blocklist/sources.json`, which places them in both
the core tier and the full list regardless of upstream coverage.

### Video players built around the pipeline

The clearest signal is a package name that names the host. The developer
"snap game team" ships a family of near-identical "offline video player" apps
whose package ids leak the actual purpose:

| App (store name) | Package id |
|---|---|
| iPlayer Lite - Video Plalyer | `com.lite.tera.iplayerbox` |
| iPlayer - Offline Video Player | `iplayer.link.mdisk.terabox.com` |
| iPlayer - Video & Media Player | `iplayer.video.mdisk.terabox.com` |
| lPlayer - Offline Video Player | `iplayer.and.new.com` |

(`iPlaySR Lite` as it circulates is this iPlayer family — store searches
normalise it back to "iPlayer Lite".) These apps are listed as ordinary players
but are the downloader/player front-end for MDisk/TeraBox links shared from X.
They are not fixed to a specific source: they fetch from whatever URL is
handed to them, which is exactly what makes them hard for a DNS filter to see
individually but predictable once the link hosts themselves are blocked.

### Player apps with private folders

A second family does not fetch anything on its own; it plays local files and
offers a PIN-guarded "private" folder for hiding them. These matter to a
parental filter because the content is already on the device and no network
lookup happens at all:

- XPlayer / "Video Player All Format" (InShot) — `video.player.videoplayer`,
  ~500M installs; the private-folder feature is its main adult-relevant draw.
  Clone packages share the name: `com.xbs.xplayer`, `com.wxx.xplayer`,
  `com.hdvideo.player.offline`.
- MX Player (`com.mxtech.videoplayer.ad`), VLC (`org.videolan.vlc`), KMPlayer
  (`com.kmplayer`), PLAYit (`com.playit.videoplayer`), Video Player All Format
  (`uplayer.video.player`) — general-purpose, ubiquitous, and simply there for
  whatever the user points them at.

### Downloader / save tools

Video-downloader apps are the tool of choice for saving adult video from
social platforms, and routinely surface "private player" or vault features:

- Video Downloader / Video Saver — `com.video.safe.downloader.private.player`
- Terabox Downloader & Player — `com.futureitlab.tbvideodownloader`
- Terabox Downloader — `com.teraboxdownloader.teraboxdownloads`
- Twitter Video Downloader — `com.savetwittervideos.twittervideodownloader`,
  Downloader for Twitter, Twitter Downloader X, TwiTok (TikTok-style client)

## Dedicated adult applications (Android)

Native adult apps are excluded from Google Play and the Amazon Appstore, so the
entire category ships as sideloaded APKs from the sites themselves or from
Aptoide/APKPure-class stores. The list below is the standing roster compiled by
TROYPOINT (updated 2026-11-06) and holds up against direct app-store checks.

Tube and aggregator apps: Incognito Play, XVideos, XNXX, PornHub, xHamster,
YouPorn, RedTube, AIO Streamer (120+ sources), Swipis (vertical-scroll clips),
SexTo, PornTotal, SpankBang, Eporner.

Premium and VR: Adult Time, Wankz, SexLikeReal, BaDoink, VRBangers, Dezyred.

Niche: Nutaku (adult games), HentaiBox (hentai aggregator).

All-in-one media apps with adult sections enabled by third-party add-ons, a
settings toggle, or a hidden category:

| App | Mechanism |
|---|---|
| Stremio | community add-ons scrape adult sources |
| Kodi | adult add-ons (Cumination, FapZone, XXX-O-DUS, Ultimate WhiteCream) |
| ClipBox | "Adult Content" toggle in Settings |
| CloudStream | NSFW extensions from a community repo |
| Flix Vision | "AdultZone" category |
| UK Turks | PIN-protected XXX section |
| Nuvio | Stremio-compatible scraping plugins |

## iOS

The App Store bans adult apps outright, which changes the shape of the problem
without removing it. The routes, in practice:

- **Hot Tub** — the first Apple-notarised native adult app (iOS 15+). Not on
  the App Store: installed via AltStore PAL in the EU, or SideStore / a paid
  signing service elsewhere.
- **Documents by Readdle** — a file manager whose built-in browser and video
  player make it the de-facto iOS route for fetching and playing adult video
  from a link. It is free, heavily installed, and looks like a productivity app.
- **VLC, nPlayer, Infuse** — stream from any URL, no browser involved.
- **Vault apps** — Secret Photo Vault, "Calculator" photo-hiders, and similar
  mask hidden media behind a benign icon.
- **X and Reddit native apps** — adult content with the sensitive/NSFW toggle
  on; Reddit's own app is the main social vector on iOS.
- **Safari** — the universal fallback, which is why the DNS layer remains the
  anchor for iOS rather than app-level detection.

Age-verification state (as of late 2026): iOS 26.4 introduced an Apple-ID-based
age check, which brought PornHub back to UK iOS users (May 2026) after Aylo's
earlier pullout over the Online Safety Act; US App Store still bans adult apps;
EU users can install Hot Tub through AltStore PAL under the DMA.

## What Hisn can and cannot do about this

Framed against `docs/THREAT_MODEL.md`, the app routes split the same way the
domain routes do:

- **Closed** — the link pipeline. Blocking `mdisk.me`, `mdiskshortener.link`
  and the TeraBox share hosts (`terabox.com`, `1024tera.com`, `teraboxapp.com`,
  `terasharelink.com`) breaks the fetch the iPlayer-family and downloader apps
  perform, because those apps do resolve the host even when the UI never shows
  it. This is the addition this research produced.
- **Open in blocklist mode** — local-file playback through a private-folder
  player, and anything the user already downloaded. No DNS treatment reaches
  this; strict allowlist mode does not either. It is the honest limit of a
  network filter.
- **App detection** — Android can see an installed package name (the table
  above is the seed for such a list); iOS cannot introspect other apps, so the
  Safari/DNS layer is the only lever there.

## Sources

- TROYPOINT, "Best Porn Apps for Firestick & Android TV" (updated 2026-11-06).
- APKPure app-store lookups for the iPlayer/lPlayer family, XPlayer variants,
  TeraBox downloaders and Twitter downloaders (2026-11-06).
- `blocklist/sources.json` and `docs/THREAT_MODEL.md` for the blocking model the
  additions slot into.

General-purpose messaging clients are deliberately out of scope for this list:
their adult use rides on private groups rather than a discoverable
distribution surface, and blocking them is not something a network filter
should do.