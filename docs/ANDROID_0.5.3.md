# Hisn Android 0.5.3: honest status, notices and housekeeping

Development preview, version code 15, 2026-10-26. Written and tested in a Linux
container that has a JDK but no Android SDK; the Gradle build and lint run in
CI (see Verification). Nothing in this record has been run on a phone.

## What changed

### Status that matches what is running

- **Setup guide.** Each layer is optional, but the guide used to need both an
  app restriction and a passed DNS check, so a DNS-only owner stayed on step 1
  and an app-only owner on step 3 forever. It now reaches one of three ready
  states: both layers, the app layer with "DNS filtering is off" said plainly
  (its button offers DNS), or DNS with no app checks. DNS counts as chosen when
  it is on or was left on. Failing or app-less image checks stop at a new
  review step, and unreadable settings are never ready (`SetupGuide`).
- **"Last screen scan incomplete"** used to clear only after a full text scan,
  so it stayed forever for blocked-app or schedule-only setups. Any later
  complete inspection now clears it: a blocked, scheduled or nothing-to-read
  decision as well as a full text scan (`InspectionHealth`).
- **Image-check failures stay visible.** The status was overwritten with
  "waiting" every two seconds. The latest model or capture failure now stays
  on the dashboard until a later sample succeeds or the layer is turned off
  (`VisualHealth`).
- **DNS notification.** The ongoing notification's channel was named after
  the dashboard label "DNS filtering · Off"; it is now "DNS filtering status"
  (same channel id, so existing installs are renamed). Its text said "DNS
  session running" even while starting. It now shows starting, connected,
  recent failures, or a strict Private DNS bypass (`DnsNotice`), and is
  re-posted only when that changes.
- **Real refusal reasons.** Choosing apps, the adult-text and image-check
  toggles and starting a commitment said "locked by commitment" for every
  failure. They now name the cause: more than 200 apps, invalid input, the
  commitment, no saved restrictions, a failed save, or unreadable settings
  (`PolicyChangeError`). The exceptions keep their old types.
- **Remaining time.** "About %d hours" was a plain string ("1 hours", wrong
  Arabic forms, "2,160 hours" for 90 days). It is now a plural phrase, in
  hours up to 48 (rounded up) and then in days (`RemainingTime`), in the
  commitment status, the VPN-settings reminder and the admin warning.
- **Bundled list version and date** in the DNS section, from
  `seed/manifest.json` via `BuildConfig.DOMAIN_SEED_BUILT`, with "updates
  arrive only with a new Hisn version", since the app downloads no lists.

### Battery and memory

- No image sampling while the screen is off (`PowerManager.isInteractive`); a
  sample that finishes after the screen went off is discarded, never acted on.
- The on-device model is released after three minutes without a sample,
  counting time asleep, and loads again when a selected app returns
  (`VisualDuty`).

### Languages and notices

- **Arabic parity.** The Arabic rules help, accessibility description, visual
  and route-check disclosures each left out a clause the English has; the TCP
  diagnostics line named the wrong counter. The English accessibility
  disclosure now also covers screenshots, as the Arabic already did, and the
  Arabic uses one term for accessibility throughout.
- **App languages.** `res/xml/locales_config.xml` declares English and Arabic,
  so Android 13+ lists Hisn under Settings › App languages.
- **Licenses and sources** (Guide page): a summary of where the bundled lists
  and engine come from, in English and Arabic, and each full license text from
  the APK's assets. The APK bundles the core-tier domain list built from
  StevenBlack hosts and Sinfonietta hostfiles (MIT) and the HaGeZi and
  chadmayfield lists (GPL 3.0), a vocabulary with German terms imported from
  LDNOOBW (CC BY 4.0), and the visual runtime's notices. License texts were
  taken from each upstream repository on 2026-10-26.

### Checks and CI

- `check-strings.py` (run first by `test-core.sh`): same string keys, plurals
  and placeholders in English and Arabic, every Arabic CLDR plural category,
  no values aapt2 would reject, and every `R.string`/`R.plurals`/`@string`
  reference names a key.
- `check-notices.py`: every enabled core-tier source in
  `blocklist/sources.json` has a license text and is named in both summaries.
- New core tests: `DnsNotice`, `PolicyChangeError`, `RemainingTime`,
  `InspectionHealth`, `VisualHealth`, `VisualDuty`; `SetupGuideTest` now
  covers 7,680 states (was 1,280).
- CI gained an `android` job: core suite, then `assembleDebug
  assembleDebugAndroidTest lintDebug assembleRelease`.

## Premises that were partly wrong

- English and Arabic already had the same keys and placeholders; the gaps were
  missing clauses inside matching strings.
- The incomplete-scan flag did clear, but only after a full text scan.
- `blocklist/sources.json` records `chadmayfield-top1m` as MIT; that
  repository's LICENSE file is GPL 3.0. The in-app notice follows upstream.
  `blocklist/` was not edited here.

## Verification

| Check | Where | Result |
| --- | --- | --- |
| `sh test-core.sh` | Linux container, OpenJDK 21.0.12 | Pass: string parity (186 strings, 3 plurals, 195 references), notices (5 core sources), 7,680 setup states, all new and existing core tests, 25/25 corpus blocks and 0/27 benign blocks, 159,762 seed domains |
| App, debug, release and androidTest Java type-checked against Robolectric `android-all` 16 (API 36) with generated `R`/`BuildConfig` stubs | Linux container | Pass. Not a build: no aapt2, manifest merge, dexing or lint |
| `assembleDebug assembleDebugAndroidTest lintDebug assembleRelease` | GitHub Actions `android` job | Pending the first CI run of this branch |

## Still needs a phone

- Licenses and sources: every notice opens; the Arabic summary lays out right
  to left and license texts left to right.
- Settings › App languages lists Hisn on Android 13+, and switching applies.
- The DNS notification moves from starting to connected, shows recent
  failures, and shows the Private DNS warning when strict Private DNS is set.
- A commitment refusal names the commitment; saving with no restrictions names
  that; the Arabic wording of each message.
- "Last screen scan incomplete" clears after a later complete inspection,
  including a blocked app.
- An image-check failure stays on the dashboard until a later sample; no
  captures are requested with the screen off; the model is freed after three
  idle minutes (process memory) and reloads in a selected app.
- The setup guide reaches each ready state: DNS only, image checks only, and both.
- Arabic plural phrases and the list date in Arabic and English locales.
- Everything still open from [0.5.2](ANDROID_0.5.2.md#still-needs-a-phone).
