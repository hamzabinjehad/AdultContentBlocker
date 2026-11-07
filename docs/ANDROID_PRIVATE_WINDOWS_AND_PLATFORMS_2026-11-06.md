# Android: private windows, X/Reddit and Telegram — 2026-11-06

Three questions about what Hisn can and cannot see on the phone. This is a
coverage note, not a claim of complete protection. The layers themselves are
unchanged; the only code change is one paragraph in the browser coverage dialog
so the private-window answer sits where the browser list is.

## What this phone actually has

Checked over ADB on the owner's Samsung SM-G990B2 on 2026-11-06:

| Installed | Not installed |
| --- | --- |
| Chrome (`com.android.chrome`) | Samsung Internet, Firefox, Brave, Opera, Edge, DuckDuckGo, Tor Browser, Telegram, Reddit, X |

So "all browsers" is one browser here, and X, Reddit and Telegram are used in
Chrome, not as apps.

## 1. Can incognito/private mode be deleted on Android?

**Not on an ordinary phone, and not for every browser even on a managed one.**
Android gives an app no permission to switch another app's private mode off.
The supported route is a device owner publishing managed configuration:

- Chrome for Android, and the Chromium browsers that read the same policy,
  honour `IncognitoModeAvailability = 1` when the app is managed. That is the
  same policy the Mac app forces through its configuration profile.
- On Android that requires a device-owner install. Hisn's direct build can hold
  device owner only on a freshly provisioned phone (the managed-lockdown path);
  this phone is not one.

What that means in practice:

- **Private mode is not a hole in what is already running.** Chrome's incognito
  tabs resolve through the same DNS tunnel as normal tabs, because the VPN
  covers the app and not the tab; and the image/text inspection samples the
  browser *window*, so an incognito window is inspected like any other. Both
  layers work on the package, not on the browsing profile.
- **The ways around it are elsewhere:** a browser with its own VPN or proxy
  (Opera VPN), a private DNS resolver the tunnel cannot see, Tor Browser, or a
  browser installed later. Those are named in `docs/COVERAGE.md` and in the
  browser dialog's own limit line.
- **The only way to remove private mode on this phone is to remove the
  browser**: block Chrome in Hisn's browser list. The trade-off is every normal
  page too, which is why the app offers "Keep only this" instead of blocking
  everything by default.

The browser coverage dialog now says both halves in English and Arabic: private
windows still get the DNS layer and the inspection, and Android cannot switch
another browser's private mode off unless the phone is managed. The release
build carrying that copy compiles and passes the string check; it is not
installed on the phone yet, and the dialog itself cannot be opened while the
protection lock is active (the screen is read-only), so the copy is verified by
compile and resource check, not by a screenshot.

## 2. X, "twitter viewer" sites and Reddit

All three are web content in Chrome on this phone, so the covering layers are
the DNS tunnel, the word rules and the image sampling — the same three that
cover any browser page.

| Target | What blocks it completely | Middle ground |
| --- | --- | --- |
| X / Twitter | Block `x.com` and `twitter.com` in Blocked domains, or block the X app | Inspect Chrome: word rules catch visible text, the image layer samples the screen |
| "Twitter viewer" / nitter-style mirrors | Block that viewer's exact hostname in Blocked domains; there is no fixed list of them | Nothing else names them; they are ordinary unlisted hostnames |
| Reddit | Block `reddit.com` (and third-party clients as apps) | Block only `i.redd.it` and `v.redd.it`: NSFW images and video stop loading while text threads stay reachable |
| Reddit app or a third-party client (Relay, Boost, Sync, Infinity) | Block each package: a DNS block does not stop text already cached | Add the app to inspection so words and screen images are checked |

The seed list blocks adult sites; it deliberately does not block mixed platforms
like X or Reddit, because blocking them removes news, work threads and support
groups as well. That is a choice each household makes, and the app exposes it
through Blocked domains rather than pretending the platforms are filtered.

Two honest limits in this area:

- Hisn's word rules match visible text. A post whose text says nothing explicit
  but whose image is explicit is caught only by the image layer, and that layer
  samples about one frame every two seconds while the page is on screen.
- A "twitter viewer" or mirror you have not named is not blocked. If it serves
  adult posts, add its hostname.

## 3. Telegram and similar apps

Telegram is not in the same class as a browser page. Its traffic is MTProto, not
HTTP: the DNS tunnel sees `telegram.org` and `t.me` and the media hosts, but not
channels, messages or the content of a photo. A domain block therefore cannot
tell an adult channel from a family group.

What actually works, in order of completeness:

1. **Block the app entirely** (Apps to block entirely). If Telegram is not
   needed for family, work or emergency contact, this is the only complete
   answer: no channel, no secret chat, no media.
2. **Inspect it** (Apps to inspect). The word rules then read visible message
   and channel text, including Arabic and Franco-Arabic terms, and the image
   layer samples photos and videos as they are displayed. What it is not: a
   review of unopened chats, of media that never appears on screen, or of secret
   chats' end-to-end content.
3. **DNS for links**: `t.me` links opened from other apps are ordinary
   hostnames and can be blocked by name.

The same shape applies to Discord, Snapchat, TikTok and Signal: app-level
content is only reachable through the app block, the visible-text rules and the
screen sampling. Messengers that carry family or work conversations should be
blocked only with the person's agreement — `docs/APPS_TO_CONSIDER.md` has the
per-app trade-offs.

## What remains a hole

- A browser installed after the block, or a browser with its own VPN, Tor or an
  unlisted encrypted-DNS resolver.
- Secure windows (`FLAG_SECURE`) cannot be screenshotted, by design.
- Image sampling is sampling: fast scrolling, video and short-lived overlays can
  pass between frames.
- Telegram secret chats, and any media that is not on screen.
- The image layer's adult recall is still not measured (see
  [the protection pass](ANDROID_PROTECTION_PASS_2026-11-06.md)).

## What could be built next

1. **Device-owner incognito policy.** Under managed lockdown, set
   `IncognitoModeAvailability = 1` through
   `DevicePolicyManager.setApplicationRestrictions` for the installed Chromium
   browsers, and clear it when lockdown ends. This is the only way to "delete"
   private mode, it needs a provisioned phone to test, and it does nothing on a
   phone without device owner.
2. **A mixed-platform preset.** One consented action that blocks `x.com`,
   `twitter.com`, `reddit.com`, `i.redd.it` and `v.redd.it`, for households that
   would rather lose the platforms than the edges of them.

Neither is implemented. The app states what it covers; it does not claim to
cover what it cannot.
