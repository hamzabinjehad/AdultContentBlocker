# Hisn Android 0.5.2: blocking hardening

Development preview, version code 14, 2026-10-26. Built and tested on the Mac only.
Nothing in this record has been run on a phone yet.

## DNS filtering

- **Firefox and encrypted DNS.** `use-application-dns.net` gets a local "not found",
  which turns off Firefox's default DNS-over-HTTPS. Well-known public DoH/DoT
  provider names (Google, Cloudflare, Quad9, OpenDNS, NextDNS, AdGuard, Mullvad,
  Control D and others, listed in `EncryptedDnsProviders`) get the same answer. This
  reduces, but does not stop, apps using their own encrypted DNS: some reach
  providers at built-in IP addresses, and a user can force Firefox DoH.
- **Private DNS.** Android's automatic Private DNS probes on port 853 now get an
  immediate reset, so Android falls back to filtered port 53. A strict Private DNS
  hostname is detected and shown as a warning, because system DNS can then bypass
  Hisn. Hisn never changes that setting.
- **Service names.** SRV, `_dmarc` and other underscore names, and one-word names,
  are forwarded instead of failing. The hostname inside an underscore name is
  still checked, so one under a blocked domain stays blocked.
- **Metered Wi-Fi.** The VPN is marked unmetered on Android 10+, so Wi-Fi no longer
  looks metered to every app.
- **Honest status.** A failure still marks the session degraded. It now recovers
  after two quiet minutes with at least ten good answers. Unrelated packets and
  refused port-853 connections are counted separately and are not errors.
- **After a reboot.** If DNS filtering was left on and Android still holds the VPN
  approval, it starts again after the first unlock. Otherwise Hisn posts a "not
  resumed" notification and never asks for approval by itself. When another VPN
  takes over, Hisn posts "DNS filtering stopped".
- A failed commitment save inside the DNS loop no longer stops filtering.

## Screen guard

- **Separated spellings.** Strong terms written as one whole run of single letters
  with one consistent separator ("p o r n", "p/o/r/n", "p|o|r|n", "p,o,r,n", and
  Arabic letters) are recognised. Initials, option lists, mixed separators, paths
  and URLs are left alone.
- **Arabic names and foods.** نيك (Nick), بورنو (Borno), كسكس and كسكسي (couscous)
  and طابون no longer count as explicit on their own. They only add evidence next
  to a separate clear adult signal. The signed term list itself is unchanged.
- **Image checks on feeds and videos.** Before, any scroll or content change
  discarded the in-flight sample, so moving screens were rarely checked. Now only
  a window change discards it. A verdict still needs the same app and window and
  a sample under eight seconds old.
- **60-second hold.** After a word or image block, reopening the same app within
  60 seconds returns Home again. Reopening does not extend the hold.
- **Home failures.** If Android refuses HOME (for example under screen pinning),
  Hisn falls back to BACK and counts it.
- An inspection error keeps a slower recheck running instead of stopping.

## Commitments

- **Reboot bug fixed.** Before, all running time since the last save was lost on
  each reboot, so a commitment could last longer than chosen. Now the time since
  boot counts after a restart, and progress is saved every minute while the guard
  runs. Only time while the phone is switched off is not counted.
- **1-day option** for trying a commitment briefly, with correct English and
  Arabic day plurals.

## Verification

| Check | Result |
| --- | --- |
| Core suite (`sh test-core.sh`) | Pass, including new DNS provider, health, port 853, clock and spelling tests |
| Synthetic adult-text corpus | 25/25 intended blocks (was 23/25), 0/27 harmless blocks |
| `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` | Pass |
| `lintDebug` | 0 errors, 18 warnings |

Debug APK SHA-256 `39d01411ccb88ab66c4849e9bcc7274bcdce532d096d607fe45bae7475bb3011`.
Unsigned release APK SHA-256 `5da89012d85e4fb5328b44359f53b16bfcfacdf5aec4e261502e4e817d5e36ce`.

## Still needs a phone

- Firefox, Chrome, Brave and Samsung Internet with Hisn on: confirm encrypted DNS is
  off or blocked and pages still load.
- Strict Private DNS set in Android settings: the warning appears.
- Reboot with DNS left on: filtering resumes after unlock. Another VPN taking over:
  the notification appears.
- A scrolling feed and a playing video in a selected app: image checks run and the
  60-second hold works.
- A 1-day commitment across a restart: the remaining time keeps going down.
