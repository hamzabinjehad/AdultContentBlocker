# Hisn Android 0.5.4, part A: reliability

Branch `android/reliability`, 2026-10-27. This part of 0.5.4 covers resolver
failover, network-change handling, damaged settings and a release APK check.
The branch still says 0.5.3 (version code 15); the integrator sets 0.5.4. Built
and tested on a Mac with Android Studio's JDK and SDK build-tools 36.0.0.
Nothing here has run on a phone or an emulator.

## What changed

### Second Cloudflare Families address

The upstream was fixed to Cloudflare for Families at `1.1.1.3:853`. It now also
knows the second Families address, `1.0.0.3:853`, with the same TLS identity
(`family.cloudflare-dns.com`), the same certificate and hostname checks and the
same TLS 1.2/1.3 limit (`TlsDnsTransport.cloudflareFamily`).

- **What moves an exchange.** A failure to connect (refused, unreachable,
  connect timeout), a failed TLS handshake (including a certificate or name
  that does not verify, for example a middlebox answering on `1.1.1.x:853`),
  or a read timeout. The same exchange then tries the next address once. Both
  addresses must prove the same identity, so moving never accepts anything the
  first would not. A bad frame or reply, a failure to protect the socket, or
  an end of stream on a fresh connection fails as before. The existing single
  retry of a reused connection closed by the server stays at the same address.
- **One slow reply is not a failing resolver.** A read timeout on a connection
  that has already answered is retried once at the next address, but does not
  put the first address into backoff; the next exchange opens a fresh
  connection to it. A fresh connection that never answers does count, so a
  resolver that accepts connections but stops answering still moves within two
  timeouts.
- **Bounds.** At most one attempt per address per exchange, plus that one
  reused-connection retry. When both addresses are failing, an exchange makes
  one attempt (on the worker's open connection, else at the address due
  soonest) and then answers SERVFAIL as before.
  Every attempt keeps the 2-second socket timeouts.
- **Return to 1.1.1.3** (`ResolverFailover`). After a failure, `1.1.1.3` is
  tried again by one connection once 30 seconds have passed, then 60, 120, up
  to 5 minutes between tries while it keeps failing. Other workers keep using
  `1.0.0.3` meanwhile. The first success clears its record and every worker
  moves back. A change of the network in use starts again from `1.1.1.3`.
  While `1.1.1.3` stays down, each try can delay one query by up to the timeout.
- **Fencing.** A failure from a connection that a network change already
  invalidated is never counted against an address and never moved to the other.
- **Never** plaintext DNS and never a resolver other than these two addresses.
  The DNS consent text in English and Arabic now names both addresses.
- **Not shown.** The dashboard does not say which address is in use. Failures
  and recoveries are counted as before.

### Only the network in use resets connections

The callback covered every `INTERNET + NOT_VPN` network, so a change on any of
them dropped every upstream connection. With mobile data kept up beside Wi-Fi
(Android's "Mobile data always active"), each mobile-data event did so.

- **Android 12 and later** register a best-matching network callback for the
  same request. Android then reports only the best non-VPN network, which is
  the system default that protected resolver sockets use, and reports a new one
  when it switches.
- **Android 8 to 11** still hear every matching network. `UnderlyingNetwork`
  infers the one in use the way Android ranks them: foreground before
  background (Android 9+), validated first, then Ethernet, Wi-Fi, other
  transports and mobile data, then the earliest.
- Connections are dropped only when the network in use switches or is lost, or
  its validation, captive-portal state or link properties change. Events on any
  other network are ignored. The network epoch, transport generations, check
  invalidation and old-network failure fencing are unchanged.
- Callbacks run on their own handler thread, which ends with the session.
  Strict Private DNS is read only from networks still followed (the setting is
  global, so the default network carries it).
- **Limits.** On Android 8 to 11 the inference can be wrong, for example with
  a vendor feature that moves to mobile data on slow Wi-Fi. Connections on a
  network that went away then fail by timeout and reconnect, which can mark the
  session degraded for a while, as any failure does. That the best-matching
  network equals the default network is Android's documented ranking, not
  something observed on a phone here.

### Damaged settings no longer end a commitment

Android's preferences loader catches a parse error, logs it and returns an
empty map, so a corrupt or unreadable `hisn_policy.xml` read as a new
installation. `PolicyStore` then saved "no commitment" at once.

Every save now writes all values plus a seal, a SHA-256 over the other values
in the same commit, to `hisn_policy`, then the same to a second copy,
`hisn_policy_copy` (`SettingsIntegrity`, `PolicyStore`). On start:

| What is found | What Hisn does |
| --- | --- |
| Intact main file | Uses it, and mirrors it to the copy if they differ |
| Main file damaged, missing or unreadable; intact copy | Uses the copy, rewrites the main file, and shows "the main settings file could not be read... check your rules" in Protection with a "My rules are as I set them" button that clears the notice |
| 0.5.3 settings: no seal, no copy | Reads them as before, then seals both files. Invalid values still refuse to load, as before |
| No copy and an empty main file | A new installation (also after clearing app data, or a first save cut short) |
| A file whose seal matches but whose values are not valid settings (for example a later version lowered a limit) | Refuses to load and changes nothing, as for invalid 0.5.3 values: Protection shows that settings are unavailable |
| Anything else: both damaged, or a damaged sealed main file | Commitment unknown, below |

**Unknown commitment.** No commitment can have more than 90 days left (the
longest choice), so Hisn keeps it active for 90 days of running time from then,
counted like any commitment, keeps whatever rules still read, and saves that
state sealed so a restart does not start the 90 days again. Protection says the
commitment stays active because its saved end could not be read, for at most
the remaining time, that some rules may be missing, and that clearing Hisn's
app data in Android settings removes every rule and this commitment. All
weakening is refused meanwhile, as in any commitment. Starting a new commitment
at least as long as the time left gives it a known end again.

- **Clearing app data** in Android settings deletes both files: a new
  installation with no rules and no commitment, as before.
- **Not harder to remove.** Both files are ordinary private app data. No new
  permission, backup, external file or system setting is involved; uninstall,
  force stop and clearing data behave as before.
- **Writes.** Each save writes both files in full. Checkpoints with no
  commitment running no longer write at all. The DNS service saves its
  once-a-minute checkpoint on its own thread, never on the packet loop. A
  failed copy write is logged (`HisnSettings`) and retried each minute even
  with no commitment running; a missing copy file after a save is logged.
- **Where the decision lives.** Reading each value with its type, the load
  decision and the values written are in core (`SavedSettings`) and tested
  with maps shaped as Android returns them; `PolicyStore` adds only the rule
  limits and the file access.
- **Limits.** If the app dies between the two writes, the copy is one save
  behind until the next start mirrors it; it is used only if the main file is
  damaged before then. A 0.5.3 file damaged before the first start of this
  version still reads as new, because an empty main file without a copy is
  also what an interrupted first save leaves. The copy's file is found in
  Android's `shared_prefs` folder; if that path ever differed, two damaged
  files would read as new, as before (logged, not shown). While a copy write
  keeps failing, a damaged main file can be detected but not restored. An
  unknown commitment can last longer than the real one did; the owner can
  clear app data. For an unknown commitment, the VPN-settings reminder and
  the admin warning say it was kept for at most the time left, as Protection
  does. Edits by someone with root access are out of scope.

### Release APK check

`check-release.py` inspects a built APK with `aapt2` and `dexdump` from the SDK
build-tools and fails if:

- the application is debuggable (badging or `android:debuggable`) or test-only;
- the DUMP permission appears anywhere in the manifest (`uses-permission` or a
  component's `android:permission`);
- a component declared in `app/src/debug/AndroidManifest.xml` is declared, or
  a class defined only in `app/src/debug/java` (not also in `app/src/release`)
  is in the dex, including its inner and synthetic classes;
- the package, `versionCode` or `versionName` differs from `app/build.gradle`.

The debug-only lists are read from the debug source set, so new fixtures are
covered without editing the script. It prints the APK's SHA-256 on success.

```sh
cd android
./gradlew --no-daemon assembleRelease
python3 check-release.py app/build/outputs/apk/release/app-release-unsigned.apk
# Signed builds: check app-release.apk (the default when it exists).
```

Tools come from `--build-tools DIR`, else the newest build-tools under
`ANDROID_HOME` or `ANDROID_SDK_ROOT`, else `PATH`. `test-core.sh` does not call
it, because it runs before any APK is built and without the SDK. It is not yet
part of CI; the integrator can add it after the release build step.

## Outside this package

- `MainActivity`: `origin/main` did not compile; the gray-screen change put
  `refreshGray()` between an `if` and its `else`. It now runs after the visual
  status (separate commit). The commitment status line, the restored notice
  and its confirm button were also added here.
- `CoreDeviceTestRunner`: a damaged-settings device test (corrupts isolated
  files, never `hisn_policy`) and removal of both files after every test.
- `SchedulePolicyChecks` (debug): also deletes the new copy files it creates.

## Review follow-up

A review of this branch found four low-severity issues; all were real and are
fixed on `android/reliability-fix`: intact but invalid settings are refused
instead of becoming an unknown commitment with emptied rules; the settings
decision moved to core with tests; copy-write failures are logged and retried;
the DNS checkpoint left the packet loop. Of its spec gaps, the handshake
failover, the slow-reply backoff and the "about N" wording are fixed; Android
8 to 11 network inference and on-device runs remain as listed below.

## Verification

| Check | Result |
| --- | --- |
| `sh test-core.sh` (JBR 25.0.3) | Pass, including the new `ResolverFailover`, `UnderlyingNetwork` and `SettingsIntegrity` tests, the extended `CommitmentClock` test and `TlsDnsTransport` failover over loopback fakes (timeout, refusal, doubling waits, both down, network change, no move on TLS failure or a reply-less close, no move after invalidation); string parity 196 strings |
| `./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug assembleRelease` | `BUILD SUCCESSFUL`; lint 0 errors, 16 warnings, none in changed files |
| `check-release.py` on the unsigned release APK | Pass: 2 activities and 1,288 classes, none of the 4 debug-only components or 6 debug-only classes |
| `check-release.py` on the debug APK (negative control) | Fails: debuggable, 4 DUMP-gated fixture activities, 6 debug-only classes and their synthetic classes |

APKs from this branch (version name still 0.5.3; unsigned or debug-signed, not
for distribution): debug
`5565f159933b0a89c409b067b990e0c5aa27ea42a8e320ba0d15d2c61d50c2e8`, unsigned
release `baafd79baab3e8c6c6cdb4e22607ba4d4259e677456aac1b2a8771c51a36e260`,
androidTest `aca7633aaf5ab13dc84e2bfb31f1ff5e3da6edcebfa06bef6faf53f48a294855`.

## Still needs a phone

- `CoreDeviceTestRunner` on a test device, including the damaged-settings test.
- Upgrade over 0.5.3 with a short commitment running: the commitment and rules
  stay, and `hisn_policy_copy.xml` appears.
- Protection in English and Arabic: the unknown-commitment line and the
  restored notice, and the confirm button clearing it.
- Resolver loss: with `1.1.1.3:853` blocked upstream (for example a router
  rule; never by changing phone settings), queries keep resolving through
  `1.0.0.3`; after unblocking, the session returns to `1.1.1.3` within about
  five minutes. With both blocked, the session becomes degraded.
- Network changes on Android 12+ and on an Android 8 to 11 device: with mobile
  data always active beside Wi-Fi, toggling mobile data leaves "Updates"
  unchanged; turning Wi-Fi off increases it and DNS continues over mobile data.
