# Android 6.0 support

**2026-11-11.** The Android app now builds and runs on **Android 6.0 (API 23)** and
later. The previous floor was Android 8.0 (API 26). This record states what
changed, what still differs on an old release, and what was actually exercised.

## Why API 23 is the floor

The app already depends on APIs introduced in Android 6.0:

- `KeyGenParameterSpec` for the guardian identity's Android Keystore key;
- `Context.getSystemService(Class)`, `Context.getColor`, `Settings.canDrawOverlays`
  and `PowerManager.isIgnoringBatteryOptimizations`;
- the runtime-permission model used by the permission screens and service checks.

Supporting Android 5.x would need a different guardian key store and a
replacement for every one of those calls. That is a separate change and is not
claimed here. Android 6.0 is the oldest release on which the existing security
and permission design is intact.

## What changed

- **Build floor and Java APIs.** `minSdk` is 23 and core-library desugaring
  (`desugar_jdk_libs 2.1.5`) supplies `java.time`, `String.join`, `java.util.Base64`,
  `List.sort`, `Map.computeIfAbsent` and the Java 8 function/stream types below
  API 26. The adaptive launcher icon moved to `mipmap-anydpi-v26/` with PNG
  fallbacks for 6.0/6.1; the round icon has a circular legacy PNG.
- **One compatibility surface.** `Compat` centralizes notification channels and
  legacy priorities, `startForegroundService` versus `startService`,
  `stopForeground(int)`, `getPendingJob`, `getDataDir`, `deleteSharedPreferences`,
  `getContentLengthLong`, `Configuration.getLocales`, `ValueAnimator.areAnimatorsEnabled`,
  `setImportantForAutofill`, `AccessibilityWindowInfo.isInPictureInPictureMode`,
  the pre-Android 8 overlay type, device-protected storage, the per-app
  notification screen and the VPN settings screen.
- **TLS identity is checked on every release.** `SSLSocket` did not compare the
  certificate name with the requested hostname before Android 7.0.
  `TlsHostname` now performs RFC 6125 matching after the handshake on every
  release: exact names, one-label wildcards, trailing dot, and subject common
  name only when the certificate has no subjectAltName at all.
- **One additional trust anchor, scoped to the resolver.** Cloudflare's
  DNS-over-TLS chain is rooted at `SSL.com Root Certification Authority ECC`
  (2016), which Android 6's 2015 system store does not contain (the root's
  subject hash `0bf05006` is absent from the API 23 image). `TlsTrust` accepts a
  chain when either the platform trust store or the bundled root validates it;
  the extra `SSLContext` is used only for the resolver socket. The root is
  pinned by DER SHA-256
  `3417bb06cc6007da1b961c920b8ab4ce3fad820e4aa30b9acbc4a74ebdcebc65`
  in `ResolverTls`; a missing or altered asset falls back to the platform store
  and an old device fails closed rather than trusting an unverified chain.
- **The seed needs the larger heap on old devices.** The signed seed is
  more than 236,000 domains. This API 23 image caps the normal app heap at
  48 MB and the DNS reader died with `OutOfMemoryError` while loading the list;
  the manifest now sets `android:largeHeap="true"`, which uses the platform's
  larger per-app cap. The app does not keep anything else in memory.
- **Android 7-only device policy is gated.** Always-on VPN
  (`get/setAlwaysOnVpnPackage`) and package suspension (`setPackagesSuspended`)
  arrived in API 24. Android 6 still gets the user restrictions and the
  uninstall block, but the suspension switch is hidden with a short explanation
  and the always-on readback is not requested.
- **Boot shield on Android 6.** Direct boot and `LOCKED_BOOT_COMPLETED` arrived
  in Android 7. On Android 6 the receiver now also accepts `BOOT_COMPLETED`
  (and ignores it on newer releases), uses ordinary storage for the armed flag,
  and draws the shield with `TYPE_SYSTEM_ALERT` because
  `TYPE_APPLICATION_OVERLAY` arrived in Android 8.
- **Latent policy-identity crash fixed.** `PolicyIdentity` returned the raw FNV
  hash, which could have the high bit set, while `PolicyReceipt` and the
  readiness UI use `-1` as unknown and `>= 0` as readable. A negative identity
  crashed the DNS route check on this image and made a passed route display as
  "not checked". `PolicyIdentity.of` now clears the high bit, so every identity
  is non-negative; the receipt keeps rejecting negative values. Regression cases
  sweep 128 revisions × 192 components.
- **Legacy navigation bar.** The light navigation-bar flag arrived in Android 8.
  On Android 6/7 the bar keeps a dark background so the white system icons stay
  visible.

## Behavior by release

| Feature | Android 6.0/6.1 | Android 7.x | Android 8+ |
| --- | --- | --- | --- |
| DNS filtering (UDP and TCP) | Yes; bundled resolver anchor | Yes | Yes |
| Selected-app and text restrictions | Yes | Yes | Yes |
| Daily time limits | Yes | Yes | Yes |
| Image/video checks | No (Android 14+) | No | No until 14 |
| Notification channels | Legacy priority | Legacy priority | Channels |
| Long-press shortcuts | No | Yes on 7.1 | Yes |
| Managed always-on DNS / suspension | No | Yes | Yes |
| Boot shield | After boot completes | Before first unlock | Before first unlock |
| Navigation-bar contrast | Dark bar | Dark bar | Light/dark appearance |

## Verification

The API 23 image was `system-images;android-23;google_apis;arm64-v8a`, booted
headless at 1080 × 1920 with 2 GB of RAM. No physical Android 6 phone was
available.

- `android/test-core.sh` passes under JDK 25, including the new
  `TlsHostnameTest`, `TlsTrustTest` and the corrected `PolicyReceiptTest`.
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
  :app:assembleRelease` passes on the same tree; lint reports 0 errors.
  `check-release.py` passes on the unsigned Play release APK
  (1.6.0 / 43, minSdk 23, no debug-only component).
- On API 23 the debug APK launched, rendered the welcome and all three tabs,
  read and wrote settings, opened the notification row through the app-info
  fallback, connected the accessibility guard, blocked the harmless word fixture
  (`OUTCOME word-intervention`, `SCANS 1`, `RestrictionActivity` resumed) and
  restored the fixture.
- The DNS route check passed on API 23:
  `VPN RUNNING DNS_CHECK PASSED DNS_LOCAL_BLOCK true DNS_TCP_CHECK true
  TCP_QUERIES 2 TCP_REPLIES 2 DNS_BLOCKED 2 DNS_FORWARDED 2 DNS_ERRORS 0`.
  That exercises UDP and reused TCP through the tunnel, the local synthetic
  block, the authenticated Cloudflare resolver and the manual hostname check.
- The `CoreDeviceTestRunner` instrumentation passes on API 23 and API 36.
- The direct (`-PhisnDistribution=direct`) debug build was installed on API 23,
  the overlay permission and keep-installed flag were set, and the emulator was
  rebooted. The Android 6 boot shield appeared with a live countdown
  (`Hisn is starting protection`, 58 s) and cleared after the guard connected. After the reboot the accessibility guard was connected and
  the DNS VPN was running again.
- API 36 stayed green: instrumentation passed, the DNS route check passed and
  the UI rendered.

## Limits

- Emulator evidence only. No physical Android 6 or 7 phone, OEM skin, Doze
  soak, battery test or low-RAM (1 GB) measurement is claimed.
- The image layer is still Android 14+ by design; Android 6/7 get the DNS, text
  and app layers only. No adult-image accuracy claim is made for any release.
- The bundled SSL.com root is a public CA anchor for the resolver socket, not a
  leaf pin. It is valid until 2041, but Cloudflare or SSL.com can change chains
  earlier; the pin and the root then need a reviewed update. The name check
  remains mandatory, so a trusted certificate for another host is still
  rejected.
- `largeHeap` raises the ceiling; it does not make the seed small. A future
  streaming or packed representation could remove the need, but is not part of
  this change.
- The 7.1 shortcut path and the 7.x always-on/suspension paths were compiled and
  guarded but not run on an API 24/25 image.
- A synthetic route check started immediately after a cold DNS start once raced
  the slow emulator's reader and failed with no DNS errors; the next check
  passed. The product button is gated by the next-step state, which waits for a
  running tunnel, but the test hook can be called earlier.
