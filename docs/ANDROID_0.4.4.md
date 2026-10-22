# Hisn Android 0.4.4 — daily app schedule

2026-10-21 · version code 9 · development preview on Samsung SM-G990B2 / Android
16. Android-only update: no new Apple installer or browser-extension release.

## Protection added

- One optional every-day local-time window, with a separate chosen app set.
  It defaults off, with an editable 22:00–07:00 draft and no selected apps.
  Start is inclusive, end exclusive; overnight windows wrap midnight. Equal
  endpoints are rejected; permanent blocking remains the all-day option.
- Draft-only English/Arabic editor, system-style time picker, app count/status,
  persistent validation errors and disclosure of timing/accessibility limits.
  Time labels preserve locale/12–24-hour preferences without DST normalization
  changing a stored time-of-day label. No device timezone is changed.
- Saved preferences and existing rule editors retain schedule choices. Setup
  recognizes schedule-only protection; empty enabled schedules cannot start a
  commitment. Corrupt schedule fields fail visibly without erasing other rules.
- During a commitment, schedule changes must preserve every prior app/minute
  of coverage. Wider windows/additional apps are allowed. An old scheduled app
  may instead become permanently blocked. Existing permanent/word/domain/adult
  rules retain their separate weakening protections.
- A static selected foreground app is rechecked about once per second. The
  service verifies active-root ownership before intervention. It can retarget
  stale selected-app events using only another selected root's package metadata,
  preventing an unrelated event from cancelling its periodic schedule check.
  Unselected/excluded app text is not read by this path.

The app still excludes Settings, permission controllers, dialers and launchers.
No new permission, screen capture, alarm, clock change or real owner commitment
is used by the new feature's tests.

## Settings/removal friction

Both existing in-app VPN-settings links now show a single dismissible reminder
when a commitment remains, with Cancel/Open Android Settings. The message
explicitly says it does not prevent changes made in system settings. No external
settings interception, repeated overlay, device-owner enrollment or uninstall
ban was introduced. The warning's active-commitment UI path was compiled/reviewed
but not exercised by starting a real owner commitment.

The owner's described BlockerHero popup is not treated as proof of an OS-level
uninstall ban. The distribution-policy and official managed-device distinctions
are documented in [Android tamper resistance](ANDROID_TAMPER_RESISTANCE.md), with
links to current Google Play and Android Enterprise documentation.

## Verification

Core tests pass, including all 1,440 minute boundaries, day/overnight windows,
invalid input, immutable app sets and 3,000 randomized coverage comparisons
against an independent oracle. Existing DNS/TCP/TLS/seed/text/clock/setup and
release no-op tests also pass. Debug/test builds, release Java compilation and
release-manifest generation pass. Lint: zero errors, eight warnings.

The main and test APKs were updated on the Samsung. The debug-only isolated
persistence checks passed: defaults, reload, old-editor preservation, rejected
weakening without preference mutation, expansions, permanent replacement,
corrupt-setting rejection and isolated commitment persistence. UUID-generated
test preferences were cleaned afterward; the owner's shared PolicyStore and
commitment were not used for those commitment checks.

The Arabic schedule editor was visually inspected on the Samsung: disabled
checkbox, start/end time labels, empty app count, readable limitations and
Save/Cancel controls. It was dismissed without saving a real owner schedule.

The live static-fixture schedule test passed: the fixture was initially allowed,
then the periodic check restricted it at the actual configured local-time
boundary without a new fixture screen event. The test read (but did not change)
the public device clock, required an in-window local minute, and checked for
clock/timezone discontinuity before accepting the result. It allowed the normal
brief HOME-to-restriction transition. The temporary schedule was restored and
the same harmless fixture was usable afterward. A fixture-only keep-awake flag
kept that test window visible; global screen timeout settings were not changed.

The full existing controlled app/text suite also passed, including ordinary,
editable/password, long/wide labels, permanent app blocking, rapid reopening and
stopping periodic work outside the selected app. English/Arabic adult-promotion
fixtures were restricted and the benign medical fixture remained available.
Fixture additions were restored. The owner domain/word/app/adult-opt-in
fingerprint matched before and after; the new schedule remained off with an
empty selection and its original 22:00–07:00 draft.

### Initial live DNS result: failed while offline

The subsequent current-device route check passed its initial synthetic local
block but could not connect to the authenticated upstream resolver, so the
combined check failed at RESOLVER_CHECK with CONNECT diagnostics; the TCP phase
was not completed. Android reported `Active default network: none`, with mobile
data disabled. Read-only checks showed no always-on VPN package and lockdown
disabled. No Wi-Fi/data/VPN setting was changed to manufacture a pass. The owner
was asked to reconnect working internet. Earlier 0.4.3 route passes are
historical evidence, not a fresh 0.4.4 live-network pass. The app retains the
failed/degraded status; do not assume current network filtering is healthy.

### Follow-up after owner reconnection: UDP and TCP passed

On 2026-10-21, the owner confirmed working internet was connected. Without
installing another APK, granting a permission or changing network settings, the
same external runner passed synthetic local blocking and authenticated
`example.com` resolution over UDP and one reused TCP connection. The session
reported `DNS_CHECK PASSED`, `DNS_LOCAL_BLOCK true`, `DNS_TCP_CHECK true` and no
current check failure. The network observer remained registered and recorded
network changes. This is fresh 0.4.4 route evidence after reconnection, not a
physical Wi-Fi/mobile handover or complete-traffic test.

The session remains DEGRADED because earlier failures/unsupported packets are
retained. Its historical CONNECT diagnostic is not erased by the successful
check. No new code, owner-rule changes or test commitment was needed.

## Limits

This restricts selected apps, not the entire phone, network or router. It does
not force sleep, implement usage budgets or guarantee intervention before a
first frame appears. Device clock/timezone changes and accessibility loss can
bypass schedule timing. Repeated/skipped DST hours follow actual local minutes,
not a fixed elapsed duration. Broader lifecycle, timing, battery and OEM testing
remain necessary. Android visual/OCR filtering, full-traffic/always-on VPN and
managed uninstall prevention remain unimplemented. No universal/undeletable
protection claim is made.

APK SHA-256:
`e717ff75d288aaf86238d116b961867bdf3072615ec8794ebc149ae433a3f027`.
