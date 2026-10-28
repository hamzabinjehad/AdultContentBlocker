# Hisn Android 0.5.5: protection development preview

The Android app contains an opt-in DNS-only VPN, saved custom rules, selected-app
accessibility restrictions, opt-in daily app schedules, built-in adult-text checks and
sampled image/video checks, an in-app
commitment and an optional legacy-admin confirmation step. The interface supports
Arabic and English. This remains a development build, not the completed end goal.
Core tests pass; device evidence is version-specific in the release records below.
The visual model and policy tests ran in a separate Samsung debug installation;
the owner's existing app and permissions were not replaced or enabled.
The current live VPN check passed local-block and external-resolver steps over
both UDP and reused TCP. On 0.4.2, Brave displayed an ordinary page, search results and
an IETF documentation page while the VPN was active. These are limited smoke
tests, not proof that every browser request uses Hisn. Broader real-app coverage
and VPN/lifecycle testing remain pending. Visual accuracy and real-app capture acceptance
remain pending; the isolated live fixture sequence passes. Usage limits, full traffic coverage and managed-device uninstall
blocking are not implemented.

## In progress: pause screen and short-video blocking (not yet released)

- The restriction screen opens with a pause: three guided breaths, a reminder,
  a "What do you need right now?" choice with tips, and today's pause count
  (a local date and number only). Returning home is never delayed. Checked on
  the Samsung in Arabic and English in an isolated debug install.
- **Block short videos** in Rules leaves YouTube Shorts and Instagram Reels with
  BACK, so the rest of each app stays usable, and closes TikTok. Players are
  recognised by view ID (`core/.../ShortVideoSurfaces.java`); no screen text is
  read. Commitment-locked like other layers. On the Samsung with YouTube 21.15
  (isolated debug install, 2026-10-28): all three Shorts player IDs were present
  in the Shorts tab and absent from the home feed; opening Shorts showed the
  restriction screen and left YouTube on its home feed; reopening YouTube and
  playing an ordinary video caused no intervention. Instagram Reels IDs and
  TikTok remain unconfirmed on a device (neither app was installed).

## 0.5.3 honest status, notices and housekeeping

The setup guide now reaches ready for DNS alone, the selected-app layer alone, or
both, and says which layer is off. "Last screen scan incomplete" clears after any
later complete inspection, and an image-check failure stays visible until a later
sample. The DNS notification has a proper channel name and shows the real session
state. Refused rule changes name their real cause instead of always "locked".
Image sampling stops while the screen is off and the model is freed after three
idle minutes. Remaining commitment time uses plurals and switches to days after 48
hours. Arabic disclosures now say everything the English says, Android 13+ lists
Hisn under App languages, the DNS section shows the bundled list's version and
date, and the Guide page has a Licenses and sources screen. `test-core.sh` now
checks English/Arabic resource parity and list notices; CI builds and lints the
app. Phone acceptance is pending; see [the 0.5.3 record](../docs/ANDROID_0.5.3.md).

## 0.5.2 blocking hardening and commitment clock fix

DNS filtering now turns off Firefox's default DNS-over-HTTPS, answers well-known
encrypted-DNS provider names with "not found", warns when strict Private DNS can
bypass Hisn, recovers from a degraded state, and resumes after a reboot when left
on. The screen guard catches separated spellings such as "p o r n", no longer
treats Arabic names and foods (Nick, Borno, couscous) as explicit on their own,
lets image checks run on scrolling feeds and videos, and keeps a blocked app
blocked for 60 seconds. Commitments now count running time after a restart, so
they cannot outlive the chosen duration, and a 1-day option exists. Core tests
and builds pass; phone acceptance is still pending. See
[the 0.5.2 record](../docs/ANDROID_0.5.2.md).

## 0.5.1 visual runtime fallback, release signing and 1.0 plan

The visual model now tries WebGL first. If that runtime fails to load, it retries
once in a fresh CPU runtime and keeps CPU for the rest of the process. A failed
frame stays unknown and is never replayed, and replies from a replaced runtime are
ignored. The debug benchmark accepts `--backend auto|cpu|webgl` so both paths can
be compared on the same corpus. On the Samsung (isolated test package, owner app
unchanged) WebGL took 121 ms median per call versus 1,989 ms on CPU, with identical
verdicts on the 12-view benign corpus. Injected load and frame failures recovered on
CPU as designed. Adult recall and battery cost remain unmeasured; see
[the 0.5.1 record](../docs/ANDROID_0.5.1.md).

`assembleRelease` signs the APK when all four `HISN_ANDROID_*` variables are set.
The finish line, the non-goals and the remaining device and owner steps are in
[the Android 1.0 plan](../docs/ANDROID_1.0_PLAN.md).

## 0.5.0 sampled image/video checks and app goal

The app now separates Protection, Rules and Guide. Its goal is to reduce exposure
to adult websites, explicit text and flagged visual content while supporting the
boundaries the owner chooses. English and Arabic explanations cover setup, privacy
and each layer's actual scope.

In Rules, opt into experimental image/video checks and select apps to inspect.
Android 14+ and the accessibility service are required. A bundled local model
checks downscaled selected-window samples about every two seconds. A flagged
sample returns to Home and shows a restriction notice; it does not blur a specific
image or pause a specific video. Samples are not saved or uploaded. Identified
focused inputs pause capture, and protected windows remain unreadable.

The actual model and isolated policy/commitment tests passed on Samsung. After
explicit test-service activation, nine live fixture checks passed for screenshots,
moving shapes, input/password pause, protected windows, a synthetic intervention
and capture stopping. This is not an adult-accuracy or real-video benchmark.
See [0.5.0 evidence, repeatable driver and remaining gates](../docs/ANDROID_0.5.0.md).

The [benign-image/performance baseline](../docs/ANDROID_VISUAL_BENCHMARK_2026-10-22.md)
adds twelve correlated views from four public-domain/CC0 photographs, each run
three times through the production classifier on Samsung. No view was falsely
blocked, no call was unknown, and repeats agreed. Median roundtrip was 1,984 ms
(p95 2,290 ms), close to the nominal inspection interval. Live sampling can be
slower while a request is busy. Adult recall and battery drain remain unmeasured;
the powered-phone run does not establish battery life. See
[reproduction tools](visual-eval/README.md).

## 0.4.6 explicit media labels

Complete two-word explicit media labels can now be detected without a promotional
verb. Longer research/recovery prose, weak or missing vocabulary and negative
context remain protected by regression tests. The same synthetic set now detects
23/25 intended blocks with 0/27 benign false blocks; two challenges remain.
See [the release record](../docs/ANDROID_0.4.6.md) for scope and limitations.

## 0.4.5 adult-text matching

Compact punctuation-obfuscated strong terms, such as `p.o.r.n`, are recovered
conservatively without joining whitespace, URL separators or arbitrary substrings.
Ten reviewed local context rules reduce false blocks of recovery and filtering
references; custom literal rules remain independent. The 52-example synthetic
regression set improved from 7/25 to 21/25 intended blocks and from 4/27 to 0/27
benign false blocks. Four challenge misses remain; this is not a general accuracy
estimate. See [the release record](../docs/ANDROID_0.4.5.md).

## 0.4.4 daily selected-app schedule

An optional daily local-time window can restrict a separate set of selected
apps, including overnight windows such as 22:00–07:00. It defaults off on new
and upgraded installations. Changes remain a draft until Save; equal endpoints
are rejected rather than interpreted as a full day. Use permanent app blocks
for all-day restrictions. Existing permanent blocks and word checks remain
independent, including outside the scheduled window.

The existing accessibility service checks an allowed scheduled foreground app
about once per second so a static screen can be restricted when its window
starts. It uses root ownership and explicitly selected package metadata before
action; stale events from another selected app cannot silently cancel that
foreground schedule. No alarm, screen capture, device clock change or new
permission is used. Initial content can appear before intervention. Disabling
accessibility, changing the device clock/timezone or removing Hisn remain gaps.

During a commitment, the backend rejects removing protected schedule apps,
disabling their schedule or reducing any previously blocked minute. Expanding
coverage is allowed; moving every old scheduled app to permanent blocking also
preserves coverage. Other rule editors preserve schedule choices. Schedule-only
protection is recognized by setup; an enabled schedule with no apps cannot
justify a new commitment.

Existing in-app VPN-settings links now give a dismissible commitment reminder
while time remains. It does not intercept system settings opened elsewhere or
prevent deactivation/uninstall. Stronger managed-device controls are a separate
consented product scope; see [tamper-resistance limits](../docs/ANDROID_TAMPER_RESISTANCE.md).

`test-live-schedule.py --serial SERIAL` runs isolated persistence/commitment
checks, then uses a harmless static fixture across a real upcoming local-time
boundary. It refuses to replace any saved owner schedule or run during a real
commitment. It does not change the device clock or grant permissions, and
restores its fixture additions. See [0.4.4 verification](../docs/ANDROID_0.4.4.md).

## 0.4.3 private-browser diagnostic

Debug builds can arm a fixed, additive deny of exactly `www.example.org` for
120 seconds to check a real browser path without adult pages. The lease is
memory-only, uses elapsed time, expires during policy evaluation/status checks,
and is cleared with its DNS session. It never edits saved rules or commitments.
Clearing or expiring it always returns to the owner's existing policy. No
browser settings, system permissions, DNS fallback or rule-removal controls are
introduced. Release builds use a separate no-op hook and cannot arm this lease.

The shell-only debug controller is `test-live-browser-canary.py`:

```sh
python3 test-live-browser-canary.py --serial SERIAL --action start --confirm-harmless-example-domain
python3 test-live-browser-canary.py --serial SERIAL --action status
python3 test-live-browser-canary.py --serial SERIAL --action clear
```

It requires an already-approved running Hisn DNS session. Open a fresh private
tab manually, then navigate to `https://www.example.org/` during the lease.
Do not change secure-DNS or private-tab capture settings for this test. A live
browser pass requires both an observed DNS error and an increase in the
fixed-host evaluation counter during that navigation. The counter represents
policy evaluations, not unique requests, replies delivered or browser identity.
No hits is inconclusive: private mode is not proof of cache isolation, and
independent DNS can bypass the virtual endpoint. An error alone is not proof.
Brave's protected private-tab screenshots remain protected; ask the owner to
confirm visible behavior rather than disable privacy controls. DNS/browser
caches may retain an error after the two-minute rule expires.

On 2026-10-21 the Samsung test recorded matching policy evaluations and the
owner confirmed a DNS/site-unreachable error in the new Brave private tab.
This verifies the tested path, not every browser request or bypass.
See [0.4.3 verification](../docs/ANDROID_0.4.3.md) for details and limits.

## 0.4.2 bounded TCP DNS update

The virtual IPv4 DNS address now accepts TCP port 53, alongside UDP. A small,
bounded endpoint handles connection setup, DNS length framing, split and
pipelined requests, connection reuse, duplicate/overlapping packets, partial
acknowledgements, peer MSS/window limits, retransmission and half-close. It is
not a general TCP stack. Limits are 16 flows, 4,096-byte DNS frames, an 8,192-byte
receive buffer and four queued requests plus one active per flow. Fixed partial
frame/request deadlines, idle expiry and bounded retransmission prevent
unbounded retained work. There is no out-of-order buffering or negotiation of
Fast Open, SACK, timestamps or window scaling.

Both protocols use the existing local domain policy, validated DNS replies and
authenticated TLS upstream. The four-worker/64-request queue is shared; overload
is explicit. Validated replies too large for the UDP client's advertised budget
produce a TC retry signal, not a resource record cut in half. The limit is 512
bytes without EDNS and at most 1,232 with EDNS. The TCP path remains capped at
4,096 bytes. The dashboard separates accepted TCP queries, prepared replies
(not confirmed acknowledgements) and UDP retry signals.

The explicit route check now sends four fixed queries when successful: local
synthetic block plus `example.com` over UDP, then the same pair over one reused
TCP connection. Samsung checks passed, including simulated network invalidation
and recovery, and a native local test of two pipelined blocks. A full 4,096-byte
UDP-TC-to-TCP fallback passes in core tests; it has not been reproduced as a
large native/browser reply. See [0.4.2 verification](../docs/ANDROID_0.4.2.md).

## 0.4.1 network recovery update

The active DNS session observes `INTERNET + NOT_VPN` network callbacks using
Android's normal `ACCESS_NETWORK_STATE` permission. It unregisters when the
session closes. New/lost networks, changed link properties and validation/captive
state changes invalidate resolver connections and the previous route-check
result. Unchanged callbacks and signal/bandwidth churn do not cause resets.
This is conservative monitoring of matching underlying networks, not proof of
which physical network carried every query. No Wi-Fi/mobile setting is changed.

Invalidation cancels old in-flight TLS exchanges, rejects buffered old-generation
TLS replies and reconnects later requests to the same authenticated resolver. It does
not restart a revoked VPN, introduce a fallback resolver or automatically send
public test queries. Old-network failures cannot overwrite new-network health;
a current request failure during a check prevents its result being published as
a current pass. The dashboard distinguishes unsupported TCP/other packets,
malformed packets and cancelled old-network requests without keeping payloads.
Unsupported traffic still means incomplete coverage. In 0.4.1 these diagnostics
did not implement TCP DNS; 0.4.2 adds only the bounded port-53 endpoint described
above, not full traffic filtering.
Network invalidation is not an atomic withdrawal of answers already accepted
by the endpoint: a callback can race final publication, and pending TCP segments
can still be delivered. This is not a physical-network provenance guarantee.

Core tests and the live simulated-invalidation/recheck on Samsung pass. The
simulation does not toggle Wi-Fi or prove real Wi-Fi/mobile handover. See
[0.4.1 verification](../docs/ANDROID_0.4.1.md). Earlier instrumentation evidence
is recorded separately for 0.4.0; it was not rerun after this update.

## Implemented

- A build step verifies the existing signed seed bundle with the repository's
  pinned public key before packaging `domains_core.txt` and `terms.json`. Python 3 and the
  repository's existing blocklist dependencies are required for this step.
- The APK pins the verified manifest's domain digest and entry count. Runtime
  loading checks SHA-256, UTF-8, count, duplicates and domain validity. This is
  build-time signature verification plus runtime digest verification, not a
  runtime signed-update client. No downloaded list updates are implemented.
- The current seed loads 159,762 domain rules. Six IPv4 entries in the shared
  seed are explicitly excluded because a domain-only policy cannot enforce them.
- The opt-in adult-text loader pins the terms seed's SHA-256, checks its version,
  UTF-8, size and rule bounds, and compiles English/Arabic terms and negative
  context on this device. Together with ten reviewed local protective/recovery-context
  additions, the current policy contains 4,462 canonical rules. The additions
  are app code, not extra signed-seed entries. Loader failure is visible and
  does not disable independent custom literal rules.
- A consent screen describes resolver privacy, other-VPN conflicts and limits.
  Android's VPN approval is requested after this screen. Nothing starts on app
  launch. After a reboot, `VpnBootReceiver` restarts DNS filtering only if the
  owner left it on and `VpnService.prepare` still returns no consent screen;
  otherwise it posts a "not resumed" notification and never asks for consent.
  Android sends the boot broadcast only after the first unlock, and not to a
  force-stopped app; device battery or sleeping-app settings may also withhold it.
  An Android revocation (another VPN took over, or a disconnect in Settings)
  clears that choice and posts a "DNS filtering stopped" notification. A service
  error posts a similar notification but keeps the choice, so the next boot
  tries again. Notifications need Android's notification permission. Starting
  the service can replace another active VPN.
- A foreground service routes only `10.111.0.2/32` into the local TUN interface
  and advertises that address as the DNS server. Normal internet traffic and
  IPv6 traffic use the underlying network. This is not a full traffic tunnel.
- IPv4 UDP and TCP DNS packets are checked for sizes, checksums, destination,
  fragmentation and supported header/options structure. TCP terminates only on
  the virtual DNS address's port 53. Four workers and a shared bounded 64-request
  queue limit resolver concurrency.
  No screen content or browsing log is stored.
- Domain matches return local NXDOMAIN without contacting the upstream server.
  Allowed queries go to Cloudflare Families at `1.1.1.3:853` with authenticated
  DNS-over-TLS, certificate and hostname validation, and TLS 1.2/1.3 only.
  When `1.1.1.3` refuses or times out, the same exchange moves once to the
  second Families address, `1.0.0.3:853`, with the same identity
  (`family.cloudflare-dns.com`); `1.1.1.3` is tried again by one connection
  after 30 seconds, doubling to at most five minutes, and at once when the
  network in use changes. TLS identity failures and bad replies never move.
  Each worker reuses its connection. There is no plaintext fallback and no
  other resolver. The resolver can see allowed domain requests. Saved domain
  rules are applied immediately alongside the bundled seed.
- Only the network that protected resolver sockets use resets connections: on
  Android 12+ the best-matching non-VPN network Android reports, on Android 8
  to 11 the one Hisn ranks first the way Android does. A change on a standby
  network, such as mobile data kept up beside Wi-Fi, keeps them.
- Upstream sockets must pass `VpnService.protect`. Transaction IDs are randomized
  upstream and restored for clients; mismatched questions, IDs, oversized and
  truncated replies are rejected. A bounded reply parser checks record lengths,
  compressed names and reachable CNAME/DNAME/HTTPS/SVCB aliases against the
  policy. Unrelated aliases do not block a query. This is not DNSSEC validation,
  nor full semantic validation of every resource-record type.
- The app shows connection state, loaded-domain count and session-only blocked,
  forwarded and error counters. A connected tunnel does not prove full protection.
  A failure marks the session degraded. It returns to connected only after two
  minutes without a new failure and at least ten validated resolver answers, and
  never while network monitoring is unavailable. Error counters and a recovery
  count stay visible for the whole session. Unrelated packets (other protocols
  or ports, IPv6) and refused port-853 connections are counted separately and do
  not degrade the session.
- Android's automatic Private DNS probes port 853 on the advertised DNS server.
  Those SYNs get an immediate TCP reset, so Android falls back to filtered port
  53 instead of waiting for a timeout. Strict Private DNS (a provider hostname on
  the underlying network) is detected read-only and shown as a warning: system
  DNS may then go to that provider and bypass Hisn. Hisn never changes it, and
  answers that one hostname normally so Android's own lookup of it still works.
- Well-known public DoH/DoT provider hostnames (Google, Cloudflare, Quad9,
  OpenDNS, CleanBrowsing, NextDNS, AdGuard, Mullvad, Control D, Comcast, CIRA,
  CZ.NIC, DNS.SB, AliDNS, DNSPod, and `resolver.arpa` discovery; the list is in
  `EncryptedDnsProviders`) and Firefox's `use-application-dns.net` canary get
  local NXDOMAIN, which turns off Firefox's default DoH. This reduces but does
  not eliminate browser DoH bypass: clients with hard-coded bootstrap IPs,
  IP-literal endpoints, unlisted providers or user-forced Firefox DoH never ask
  Hisn. Hisn's own upstream is pinned to `1.1.1.3` and `1.0.0.3` and uses its
  hostname only for TLS identity, so it does not depend on resolving these names.
- Underscore names (SRV, `_dmarc`, DDR) and single-label names can never equal
  a domain rule, so they are forwarded instead of failing and do not degrade
  the session. The hostname part of an underscore name is still checked: one
  under a blocked domain, such as `_8443._https.blocked.example`, stays blocked.
- The VPN is marked unmetered on Android 10+, so it inherits the underlying
  network's metered state instead of making Wi-Fi look metered to apps.
- After a separate disclosure and owner confirmation, the DNS route check sends
  a synthetic `blocked.hisn-self-check.invalid` query through the virtual DNS
  route, then `example.com` only if the local NXDOMAIN check passes, first over
  UDP and then over one reused TCP connection (four queries on full success).
  Public queries use the existing authenticated resolver path; the probe itself is not
  protected from the VPN and has no fallback endpoint. A passed check describes
  this route only, not all apps, encrypted content or all device traffic. A
  stopped/restarted DNS session clears the previous result. The latest Samsung
  0.4.0 runs passed the local step but initially failed resolver socket protection.
  The transport now binds its socket locally before protecting it and only then
  connects; a regression test checks this ordering. The phone subsequently
  reported no active underlying internet network. Once connectivity returned,
  checks exposed reused TLS connections closed by the server. A pooled EOF/reset
  now gets one reconnect to the same authenticated endpoint; fresh errors,
  timeouts, identity failures and invalid frames do not. Tests cover stale
  recovery and no fresh-error retry. Three consecutive live checks then passed
  on the 0.4.0 APK. Broader network changes remain a release gate. The
  historical DEGRADED session state is not cleared by a passing check. No
  plaintext fallback was added.
- Supported queries receive SERVFAIL if the resolver or reply validation fails;
  unsupported packets are counted and dropped. This avoids silently switching
  to an unfiltered resolver, but can interrupt page loading.
- Rules persist in private app preferences. During an active 7-, 30- or 90-day
  in-app commitment, the settings backend rejects removal of saved restrictions
  and reductions in the remaining period. Disabling opted-in built-in adult-text
  checks is also rejected. Stronger settings are allowed. A new commitment is
  rejected until meaningful restrictions are configured; old empty commitments
  still load without erasing their remaining period and can receive added rules. The
  clock uses elapsed time and Android's boot counter, not the editable date.
  Progress is checkpointed on app pause and periodically during a DNS session;
  reboot or power-off can extend the period. Android settings, data clearing
  and uninstall remain outside this in-app lock.
- Settings are saved twice, in `hisn_policy` and `hisn_policy_copy`, each
  sealed with a SHA-256 over its values. A damaged or unreadable main file is
  replaced by its copy and Protection asks the owner to check the rules. If
  neither can be read although settings were saved, the commitment is kept
  active, shown as unknown, for at most 90 days of running time, the longest
  one can last, instead of reading as a new installation. Clearing app data
  removes both files and resets everything.
- The accessibility service is off until the owner enables it. It acts only in
  explicitly selected apps. Chosen blocked apps, matching literal word rules or
  built-in adult-promotion evidence in the optional mode
  return to the launcher and show a generic restriction screen. Settings,
  launchers and dialers are excluded. Traversal is bounded and skips editable
  and password nodes and their descendants. Childless labels are read, and a
  card or button's own content description only when nothing inside it has text
  and no input or password field sits inside it, so a parent never repeats an
  input value (`LabelScan`). Sensitive content may still be exposed by other
  labels; the consent screen discloses this. Screen text is not saved.
- Built-in adult-text checks use bounded local context within individual visible
  labels, rather than exempting a whole screen because of unrelated medical
  or news wording. A label of up to three words that are all explicit terms
  ("Porn", "سكس") blocks on its own, unless a label on the same screen names
  recovery, blocking, research or similar context; then only promotions and media
  labels block there. Custom rules are checked independently first; built-in benign
  context never overrides a chosen literal rule. Opt-in is off by default for
  existing installations and enabling it does not grant any Android permission
  or choose apps automatically.
- The built-in check uses the seed's English, Arabic and Franco-Arabic (Arabizi)
  terms. Most single Arabizi spellings are also names or everyday chat, so they
  count only beside a reviewed clear Arabizi term; see
  [the text and privacy record](../docs/ANDROID_0.5.4_TEXT_PRIVACY.md).
- The rule editors, app lists, schedule editor and a word tester holding text
  are FLAG_SECURE windows: Android leaves them out of screenshots, screen
  recordings and the recent-apps preview. Anyone looking at the screen and
  accessibility services can still read them.
- Long visible labels are sampled rather than skipped above 2,048 characters.
  A global 512-node/child-attempt budget and 32,768-character sample bound work;
  truncated tokens cannot manufacture whole-word matches. The dashboard reports
  incomplete inspections and configured accessibility layers independently of DNS.
- Normalized Unicode words and IDN domains must fit persistence limits before
  saving. Rejected updates preserve existing settings and remain reloadable.
- While a selected word-scanned app remains foreground, bounded periodic
  inspections run about once per second. They stop after leaving the selected
  app and on service interruption. Scroll events also trigger checks. On
  binding, the service probes current-window metadata for selected-app recovery.
- Layer health distinguishes permission enabled from service connected, as well
  as no saved restrictions, partial inspection and unreadable settings. A live
  service alone is never presented as configured enforcement.
- A shared setup guide supplies both the next-step message and its action.
  Its 7,680 tested state combinations distinguish rules, whether DNS filtering
  was left on, permission, binding, loading/error states, incomplete app
  inspections, image-check failures, DNS startup/degradation and the explicit
  route check. Each layer is optional. Ready for the app layer requires
  configured app protection, healthy service state, a ready or disabled optional
  policy and no failing image check; ready for DNS requires a running DNS layer
  and a passed check. When only one layer is chosen, the guide says the other is
  off. Unreadable settings are never ready. Ready does not mean complete device
  protection.
- "Last screen scan incomplete" is set by a partial or failed inspection and
  cleared by a later complete one, including blocked-app and schedule
  decisions. An image-check failure stays on the dashboard until a later sample
  succeeds or the layer is turned off.
- Image sampling never runs while the screen is off, and the on-device model is
  released after three minutes without a sample.
- The ongoing DNS notification ("DNS filtering status" channel) shows starting,
  connected, recent failures or a strict Private DNS bypass.
- A refused rule change names its cause: too many apps (200 per list), invalid
  input, an active commitment, no saved restrictions, a failed save or
  unreadable settings.
- The Guide page's Licenses and sources screen summarises the bundled lists'
  and engine's origins in English and Arabic and shows each full license text
  from the APK; nothing is downloaded. The DNS section shows the bundled list's
  version and build date; lists change only with a new app version.
- Selected-window root changes receive bounded retries. Warning cooldown never
  suppresses a fresh enforcement action. The explanation screen is delayed until
  after Home to avoid the observed Samsung transition race.
- The optional legacy device-admin receiver declares an empty policy set:
  no wipe, password or device-lock capabilities. Android may require admin
  deactivation before uninstalling. The receiver can show a reminder, but it
  cannot stop deactivation, make the app undeletable, or establish device-owner
  management. Hisn never uses accessibility to trap the Android uninstall UI.

## Known coverage gaps

Apps' independent encrypted DNS, direct-IP connections, cached DNS, alternative resolvers, secure
browser DNS, other profiles and mixed-content websites can bypass this layer.
Blocking known provider hostnames narrows, but does not close, the encrypted-DNS
gap. Strict Private DNS can route system DNS around Hisn; it is warned about, not changed.
The virtual DNS server handles IPv4 UDP and bounded TCP port 53 only; client TLS
port 853 is refused with a reset, and TCP to other destinations is not served. Fragmented IP, compressed
questions and malformed multi-label names are unsupported; replies above the
4,096-byte cap, unsupported queries and resolver outages may cause page loads to fail. The
service reports errors rather than claiming those requests were filtered.

Always-on support is explicitly disabled. Do not enable lockdown for this
DNS-only route: it would prevent normal internet traffic from using the
underlying network. After boot it resumes only if left on and VPN approval
remains; it does not restart after process death or permission loss. Android
VPN settings can disconnect it.

The local tester accepts one literal rule per line with bounded rule and text
sizes. Tester inputs/results are not saved; rules entered in the separate saved
rule editor are saved. This tester checks custom literal rules, not the optional
built-in classifier. Whole-word matching avoids `sex` inside `Essex`; ambiguous
chosen rules can still match benign content. The built-in policy is a conservative
adult-promotion heuristic, not an unrestricted semantic classifier. It can miss
adult text without the required evidence or misclassify harmless text. Cross-node
phrases, hidden/secure text, images,
video and all-app coverage are not guaranteed. Text beyond traversal limits
may not be inspected, and intervention happens after an accessibility event.

## Build and test

Use JDK 17+, Android SDK platform 37 and build tools 36.0.0. The wrapper pins
Gradle 9.3.1. Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
sh test-core.sh
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug assembleRelease
```

CI runs the same commands in its `android` job. `test-core.sh` starts with two
resource checks: `check-strings.py` (English and Arabic have the same string
keys, plurals and placeholders, every Arabic plural category, nothing aapt2
would reject, and every referenced key exists) and `check-notices.py` (every
bundled list source has its license text and an entry in both summaries). Add
any new `*Test` class to the loop in `test-core.sh`.

The core suite covers word boundaries, Unicode, bounded lists, seed tampering,
the actual full seed, domain exceptions, DNS/EDNS packets, packet checksums,
fragment rejection, malformed inputs, resolver timeouts and local UDP exchanges.
TCP tests cover framing, pipelining/reuse, sequence wrap, partial/future ACKs,
overlap/duplicates, peer MSS/window limits, retransmission, FIN/RST, queue/flow
caps, deadlines and late completions. Packet mutation/fuzz tests and a validated
4,096-byte UDP-TC-to-segmented-TCP fixture run without public network access.
The suite also checks all 7,680 setup-guide combinations, actual-seed adult-text
fixtures, local context, Unicode/evasion boundaries and bounded classifier work.
DNS-probe tests use loopback responders for route-check response validation,
timeouts, cancellation and no public query after a failed local check.
TLS tests generate a disposable localhost certificate, verify hostname and
trust failures, and test encrypted exchanges and connection reuse. Commitment
tests cover expiry, clock rollback, boot changes and checkpoints. The standard
suite uses loopback only. `ResolverProbe --public-example-only` is a separate,
explicit optional diagnostic which contacts Cloudflare for `example.com` only.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The app supports
Android 8.0+. A debug build is signed with a development key, not a production
release certificate.

The optional test APK uses a custom instrumentation runner. After installing
the app and test APK, run:

```sh
adb shell am instrument --user 0 -w app.hisn.android.test/app.hisn.android.CoreDeviceTestRunner
```

This test checks the seeds, Arabic rules, word boundaries, a local DNS block,
saved settings, built-in opt-in and commitment checks in isolated test preferences without
enabling services. Actual VPN routing, accessibility interventions, admin
deactivation behavior and lifecycle need separate controlled device tests after
approving the corresponding consent screens. On 2026-10-20 the device runner
passed on the Samsung with 159,762 bundled domains, Arabic/boundary matching,
local DNS blocks, Unicode persistence/rejection, long-text sampling and
commitment checks, verified adult-term loading, custom-rule independence,
empty-commitment rejection and opt-in persistence/commitment gating. Generated
test preferences are separate from `hisn_policy`
and cleared after testing; real user rules and commitments are untouched.

See [the REA review](../docs/REA_BLOCKERHERO_2026-10-19.md) for competitor
observations, evidence identifiers, independent improvements and remaining gates.
See [live Samsung guard tests](../docs/ANDROID_LIVE_GUARD_2026-10-19.md) for
verified fixture results and the opt-in external test driver. Instrumentation
restarts Hisn and can interrupt accessibility; use the external driver for live
service tests instead. Its shell-only setup activity is excluded from release.
The historical [0.3.1 verification](../docs/ANDROID_0.3.1.md) includes the successful periodic
foreground-check and stop-outside-selected-app tests, installed APK identity and
the visually checked Arabic dashboard. Follow-up controlled normal-content,
English/Arabic adult-promotion and benign medical-context fixtures passed on
2026-10-20. These are fixture checks, not proof of general app coverage.

A separate small, read-only text corpus produced 9/16 adult-text hits and 1/18
benign false positives. Its misses and false positive remain release-quality
work: these denominators describe only those fixtures, not general detection
accuracy. The current UDP/TCP VPN-path check and short Brave browsing smoke test
pass, but physical network-transition reliability, longer browsing sessions and
broader browser coverage still need acceptance testing. Passing core,
instrumentation, accessibility or route checks is not complete protection.

See the [0.4.0 verification record](../docs/ANDROID_0.4.0.md) for the installed
APK identity, socket-ordering correction and connected-network result.
See [0.4.2](../docs/ANDROID_0.4.2.md) for the current APK, TCP evidence and remaining
coverage limits. `test-live-tcp-local.py --serial SERIAL` performs only two
synthetic local blocks on the already-approved running VPN; it does not test
public resolver access or grant permissions.

References: [Android VPN](https://developer.android.com/develop/connectivity/vpn),
[VPN routes and address families](https://developer.android.com/reference/android/net/VpnService.Builder),
[foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required),
[Cloudflare Families](https://developers.cloudflare.com/1.1.1.1/setup/#1111-for-families).
