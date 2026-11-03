# Google Play readiness — Hisn Android 1.1.0

Repository-side record for a Play submission. It is **not** a Play listing and
does not claim Play approval. The direct build and the Play build are different
products of the same source. 1.1.0 is built as a signed direct APK; the GitHub
release is pending. The Play AAB is built and checked in the repository but not
uploaded.
1.1.0 adds only a first-run welcome, a voluntary pause and today's usage in the
daily-limit editor; the permission and data-safety answers below are unchanged.

## Which build goes to Play

| Build | Command | What it contains | Play? |
| --- | --- | --- | --- |
| Play-style | `./gradlew assembleRelease` (or `bundleRelease`) | Filter, DNS, app/text checks, schedules, commitments, device-owner uninstall block and lockdown, watchdog, no uninstall-screen guard | Candidate, subject to policy review |
| Direct | `./gradlew assembleRelease -PhisnDistribution=direct` | Play-style plus the accessibility uninstall-screen guard and boot shield | **No** — do not upload |

`check-release.py --expect-distribution play` refuses a `-direct` artifact;
`direct` refuses the Play artifact. Use it before every upload.

The direct guard reads Settings, the package installer and Play Store windows to
recognise Hisn's own uninstall/disable screens. That use is outside what Play's
Accessibility policy allows for a self-control app, so it is absent from the
Play build. The Play build still uses accessibility for selected-app text
restrictions; that declaration and approval are separate and may be refused.

## Listing copy

**Title:** Hisn — Adult Content Blocker

**Short description (80 chars):**
> On-device adult-content blocking, app limits and a commitment that holds.

**Full description:**

> Hisn blocks adult domains over an on-device DNS filter, restricts the apps you
> choose, checks visible text in those apps, and can add a commitment that
> refuses to weaken your rules until it ends. Optional local image/video checks
> never upload a frame. Arabic, Persian, Urdu and Franco-Arabic spellings are
> handled on the device.
>
> A commitment is a voluntary app-level lock, not a device-owner lock. On a phone
> you provision as device owner, Hisn can also refuse ordinary uninstall and,
> with a second opt-in, block Safe Mode, app controls, Settings erase and
> VPN/Private DNS changes. That is an optional managed-device setup; the ordinary
> install stays removable and the app says so.
>
> **Privacy:** no account, no analytics, no server, no browsing history. Matching
> stays on the phone. The only routine request is a signed block-list build
> update; the DNS resolver sees the names it resolves.
>
> **Limits:** no filter catches everything. Encryption, direct IPs, another
> browser, another network, clearing app data or a factory reset can bypass a
> layer. Visual checks are optional, sampled and can miss content. The app shows
> what each layer covers and does not claim otherwise.

**Category:** Lifestyle (alternative: Productivity). Content rating: Everyone /
3+; the app blocks adult content but does not contain or display it.

## Data safety answers

| Question | Answer |
| --- | --- |
| Does the app collect or share any required user data? | **No** |
| Browsing history / web activity | Not collected; processed on-device |
| App activity, files, messages, photos/videos | Not collected; selected-app text and frames are processed transiently on-device, and opt-in daily limits read Android's local foreground-time totals |
| App usage / installed-app time | Not collected or shared; the Usage access grant is used only to compare a chosen app's local daily foreground time with its saved limit |
| Device or other IDs | Not collected |
| Location, contacts, health, financial info | Not collected |
| Data encrypted in transit | The list update uses HTTPS; DNS uses DoT to the configured resolver |
| Data deletion request | No account or server data; clearing app data or uninstalling removes local state |
| Ads / tracking | None |

The accessibility service can read visible text and, if separately enabled,
sample screenshots of **selected apps**. That is disclosed in-app and covered by
the Data safety answers as on-device processing, not collection.

## Permissions and policy declarations

| Permission / API | Declaration |
| --- | --- |
| Accessibility (`BIND_ACCESSIBILITY_SERVICE`) | Selected-app text restrictions and optional visual sampling; not for uninstall prevention in the Play build. Prominent in-app disclosure before enabling. |
| `VpnService` (DNS-only) | Local DNS filtering; not full-tunnel, not always-on, no traffic forwarding. Play VPN declaration required. |
| `POST_NOTIFICATIONS` | DNS status and layer-loss notice. |
| `PACKAGE_USAGE_STATS` (Usage access, special access) | Opt-in daily app time limits read Android's on-device foreground-time record. No usage event leaves the phone; the owner grants it in Android Settings and can revoke it at any time. Not used for advertising, analytics or a parental dashboard. |
| `SYSTEM_ALERT_WINDOW` | Direct build's boot shield only; not used by the Play-style build for protection. |
| Device admin / device owner | Optional uninstall block and managed lockdown; no wipe/password/screen-lock powers. DPC/EMM declarations may be required for the managed edition. |
| `QUERY_ALL_PACKAGES`? | Not declared; `queries` entries cover launcher/home/dial only. |
| `WRITE_SECURE_SETTINGS` | Optional grayscale only; not granted on install. |

Play policy review is required before claiming Play distribution. The direct
build remains the recommended first distribution for the uninstall-screen guard,
because that feature is not Play-compliant.

## Reviewer test steps

1. Install, open Protection. No account or login.
2. In Rules, add `example.com` as a blocked site. Visit it in a browser: the
   restriction screen/notice should appear. Remove it afterwards.
3. Enable the DNS layer and approve the VPN request. The notification must show
   filtering, and the route check must run. Disable it afterwards.
4. Select a browser or a simple app for text checks, enable accessibility, and
   open a page containing ordinary text: no false block. No adult content is
   needed for review.
5. Start a short commitment and verify that weakening a rule is refused; let it
   expire or use a disposable install.
6. Optional daily time limits: in Rules choose an app and a 5-minute limit, grant
   Android Usage access, then use the app. The app is closed at the limit and the
   pause screen names the daily limit. Revoke Usage access afterwards if wanted.
7. Managed features require a factory-reset, account-free device and are not
   needed for the base review. The app reports "Not managed" on an ordinary
   install and makes no device-owner claim.

## Release checklist

- [x] Create the production keystore offline, two backups, and record the
      certificate SHA-256. (Created 3 November 2026; the keystore and password
      are in the gitignored `keys/android/` folder. **The owner must back both
      files up offline in two places.**)
- [x] Build the Play-style AAB/APK; run `check-release.py --expect-distribution play`.
      Hash and certificate are in [the 1.0 record](ANDROID_1.0.md).
- [ ] Upload the Play AAB. Not done: this release is direct-sideload only and
      Play policy review has not been requested.
- [ ] Fill Data safety, content rating, VPN, usage-access and accessibility
      declarations from this record.
- [ ] Host [PRIVACY.md](../PRIVACY.md) and [SUPPORT.md](../SUPPORT.md) at stable
      URLs and enter them in Play Console.
- [ ] Internal testing on a clean phone; then closed testing.
- [ ] Record the signed artifact hash, version code and certificate in
      [the coverage pass record](COVERAGE_PASS_2026-10-30.md) or the 1.0 record.
- [ ] If Play rejects the accessibility declaration, ship the direct build and
      say so; do not remove the in-app disclosure to pass review.
