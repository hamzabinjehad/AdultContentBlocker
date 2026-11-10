# Android: from a working tree to the end project

Date: 8 November 2026. Status: plan, plus artifacts. The candidate is now
committed and numbered **1.6.0 / 43**; the 1.5.4 reconnect lifecycle is ported.
See [release state](RELEASE_STATE_2026-11-10.md) and
[the 1.6.0 note](ANDROID_1.6.0.md).

**Update after the first delivery pass.** The latest complete Android line is
not this checkout. A clean sibling worktree
(`/Users/x/Desktop/GitHub/AdultContentBlocker-android-1.5.3`, branch
`android-1-2-3-responsive`, commit `9270044`) is already at **Android 1.5.3 /
versionCode 41**, with signed Play/direct artifacts and a verified phone
update. That is the **end project** to install; its artifacts are in
`dist/android-1.5.3-end/`. A 1.4.0 build from this checkout would be a
downgrade.

This document therefore covers the **`mdm-finish` package candidate** — the
readiness dashboard, signed mobile list updates, rule import/preview and
person-blur work that is present here but not yet on the 1.5.3 line. It should
merge forward into a **1.6.0** release, not ship as 1.4.0. The P0 below is still
correct for this checkout, and the 1.4.0/36 candidate artifacts in
`dist/android-final-2026-11-08/` are a preview of that merge, not the end
project. On 10 November the candidate was committed, the 1.5.4 reconnect
lifecycle was ported, and the version was set to 1.6.0 / 43. The remaining P0
work is the signed build and the phone acceptance.

The broader feature list already exists in
[the Android feature plan](ANDROID_FEATURE_IMPROVEMENT_PLAN_2026-11-08.md). Its
“starting point” has one stale claim: it says the native TFLite classes are
absent. They are present in this checkout and were committed on 10 November. This plan supersedes that
paragraph and focuses on delivery.

## What “end project” means here

One source revision, one version number, and artifacts that can be reproduced
from it:

- a **signed direct APK** that updates existing sideloads with the same release
  key;
- a **Play APK and AAB** built from the same source (upload is a separate,
  explicit step);
- a **debug APK** for testing, clearly separated from the release identity;
- recorded hashes, certificate fingerprint, release notes and a phone
  acceptance matrix;
- a `check-release.py` pass for both distributions and a `test-core.sh` pass
  under a documented JDK;
- no claim that has not been run on a real phone.

The product features already implemented in the working tree are the starting
point. The work is reconciliation, verification and packaging.

## Evidence gathered for this plan

Toolchain used: Gradle 9.3.1, Android Studio JBR JDK 25, `compileSdk 37`,
`build-tools 36.0.0`, `minSdk 26`, `targetSdk 36`. The project compiles with
Java 17 language settings; the CI runner uses JDK 17.

| Check | Result |
| --- | --- |
| Debug build | `:app:assembleDebug` **PASS**, 1.4.0/36 with a temporary version override (31 MB) |
| Signed direct build | `:app:assembleRelease -PhisnDistribution=direct` **PASS**, 1.4.0-direct/36 (30 MB) |
| Release check | `check-release.py --expect-distribution direct` **PASS**: not debuggable, no test-only content, no DUMP permission, no debug classes/activities; SHA-256 `bc14c88e…` |
| Release signature | `apksigner` certificate SHA-256 `2F:BE:D6:57:…:70:C5`, the release key created 3 November 2026 |
| Core suites | `JAVA_HOME=<JDK 25> sh test-core.sh` **PASS**; strings 651 / 18 plurals / 645 references, notices pass, all Java core tests pass |
| Lint and test APK | `:app:lintDebug :app:assembleDebugAndroidTest` **PASS**; 0 lint errors, 82 warnings, 1 hint (see the report) |
| Text quality | Synthetic corpus: **68/72** expected adult blocks, **0/133** benign controls; 4 Franco-Arabic misses (listed below) |
| Existing 1.4.0 artifact | Signed and valid, but `check-release.py` **rejects it against the current source**: artifact is 36/1.4.0-direct, source declares 32/1.2.3 |
| Source package | Fresh extraction of `hisn-android-1.4.0-source.zip` passes `test-core.sh`, `seed bundle verified` and `assembleDebug`, producing a 1.4.0/36 APK |
| Source state, 8 November | `build.gradle` 1.2.3/32; `versions.json` 1.2.3/32; **45 untracked and 42 modified Android files**; `docs/ANDROID_1.4.0.md` untracked. All committed on 10 November |
| CI | The Android job runs `test-core.sh`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` and `assembleRelease` on Ubuntu with JDK 17 ([ci.yml](../.github/workflows/ci.yml) (lines 127–167)); it cannot see untracked files |

What was not run: any physical phone, emulator instrumentation, live DNS,
accessibility or visual driver, Play upload, or an adult-positive visual
benchmark. No 1.4.0 phone claim is made. The lint warnings were not all
reviewed; the release job fails on errors, not warnings.

## P0 — One source, one version, one artifact (this week)

**Status, 10 November:** steps 1–3 are done. The tree is committed, numbered
1.6.0 / 43, and the metadata is updated. Steps 4 (signed build), 6 (artifact
gate) and 7 (phone) remain.

The checkout was the real problem. It contained a **future release that was not
in git**:

- `TfliteClassifier.java` and `assets/nsfw_mobilenet_v2.tflite` (native visual
  runtime, ~10 MB model);
- `AdultListUpdater`, `HostListStore`, `HostListUpdateJobService`,
  `SignedManifest`, `Ed25519` (signed mobile list updates);
- `ProtectionReadiness`, `PolicyReceipt`, `PolicyIdentity`, `PolicyVersion`,
  `EvidenceAge` (readiness and receipts);
- `RuleDocument`, `RulePreview` (portable rule import/preview);
- `DistractionSites`, `PersonBlur`, `PersonFastPresence`, `VisualOverlay`,
  `VisualRuntime`, `VisualResult`, `VisualVerdictCache`, `DnsCheck`,
  `HostDecision`, `SiteCheckInput`, and their tests;
- `docs/ANDROID_1.4.0.md` and the TFLite model asset.

`MainActivity` already imports and renders `ProtectionReadiness` and
`PolicyReceipt` ([MainActivity.java](../android/app/src/main/java/app/hisn/android/MainActivity.java),
lines 73–79 and 1396–1454); `VisualGuard` already selects `TfliteClassifier` at
runtime ([VisualGuard.java](../android/app/src/main/java/app/hisn/android/VisualGuard.java),
lines 609–615). In other words, the 1.2.3 label is not the code that is running.

Because those files are untracked, another clone — and CI — builds a different
app. That is the delivery blocker.

**Do this, in order:**

1. **Choose the release number.** Recommended: **1.4.0 / versionCode 36**,
   because it matches the existing signed artifacts, the untracked 1.4.0 record
   and the feature set. A new number (1.4.1/37) is also defensible, but only if
   the old 1.4.0 artifacts are explicitly retired.
2. **Commit the candidate.** Add the 45 untracked files and the 42 modified
   files. Include `docs/ANDROID_1.4.0.md`, the TFLite model, and the core tests.
   Do not commit `android/build/`, `android/.gradle/`, personal captures or the
   signing key.
3. **Bump the metadata to match:** `android/app/build.gradle` versionCode/name,
   `versions.json` Android entry and notes, `android/README.md`, `CHANGELOG.md`,
   the 1.4.0 record, and the `dist/` release notes. Today these disagree:
   build.gradle says 32/1.2.3, the artifact says 36/1.4.0, and the feature code
   came from neither.
4. **Rebuild every variant from the committed revision:** debug, direct release,
   Play release APK and AAB. Run `check-release.py` for Play and direct, and
   `apksigner verify --print-certs` for each. Record bytes, SHA-256 and the
   certificate.
5. **Make `test-core.sh` work where it is documented.** It calls `javac` from
   `PATH` ([test-core.sh](../android/test-core.sh) (lines 13–27)); on this machine a
   system Java 8 makes it fail with “Unable to locate a Java Runtime that
   supports javac”. With the Android Studio JBR (JDK 25) it passes. Either
   resolve JDK 17+ inside the script or document the `JAVA_HOME` line in the
   README and CI.
6. **Add a release-artifact gate to CI or the release checklist:** after
   `assembleRelease`, run `check-release.py` and compare the recorded hashes.
   CI already builds and lints; it does not yet verify the distribution
   identity.
7. **Reload the phone once the commit exists.** Install the signed direct APK
   over the existing 1.4.0/1.2.x build only if the version code is equal or
   higher and the certificate matches. The delivered candidate APKs below were
   built from this working tree with a temporary override and the source was
   restored; they are for inspection, not the final release until step 2 is
   done.

**Exit gate:** a fresh clone at the chosen version runs `sh android/test-core.sh`,
`./gradlew assembleDebug assembleRelease lintDebug`, and `check-release.py`
without any local untracked file, and produces byte-identical version metadata
to the artifacts in `dist/`.

## P1 — Close the measured quality gaps (next)

These are the gaps the evidence actually shows, not a wish list.

### 1. Four Franco-Arabic misses

The core corpus reports 68/72 adult blocks and 0/133 benign blocks. The four
misses are:

- `Watch aflam neek masri free`
- `tahmil film neek arabi`
- `tahmil aflam neek 7elwa mojani`
- `sksy download free`

`neek` is a transliteration the shared vocabulary normally carries; these rows
combine it with `aflam`/`tahmil` and, in one case, `sksy` rather than `sks`. Add
these as reviewed fixtures first, then decide whether the fix belongs in
`TextFolding`, `WordMatcher`, `AdultTextPolicy` or the seed vocabulary. Keep the
benign 0/133 gate intact; do not globally fold digits into letters.

**Proof:** the four rows block; the existing 133 benign controls still do not;
Arabic, Arabizi and mixed-script cases report misses and false blocks
separately.

### 2. The visual layer has benign evidence, not detection evidence

The 1.4.0 record reports 0/12 benign views and 38–45 ms native inference on the
API 36 emulator, with a 1.6–1.9 s model load. That is a performance and
false-positive result, not adult recall. A release claim needs an appropriately
obtained adult-positive set and difficult benign controls (medical, swimming,
art), scored through the packaged TFLite runtime, with the thresholds unchanged
and unknowns reported separately.

**Proof:** a private benchmark with positive and benign cases, per-runtime
parity against TFJS, and a phone measurement of capture + inference + action,
not only model time.

### 3. Network and lifecycle behavior on a phone

The DNS path is smoke-tested locally, not across real transitions. Exercise
Wi-Fi/cellular, airplane mode, captive portals, IPv4/IPv6, UDP truncation and
TCP fallback, private DNS and other VPNs. Bind late replies to the network
generation, keep queues bounded, and report the actual routing state rather than
claiming full traffic coverage. Do the same for Doze, force stop, boot,
accessibility rebind and OEM battery restrictions.

**Proof:** a recorded matrix of phone model, OS build, transition and observed
result, with failures left as failures.

### 4. Commitment and removal protection on a disposable device

Test the ordinary, accessibility-friction, device-owner and guardian levels
separately. Verify every weakening path, clock rollback, damaged storage,
replayed guardian command and authorized expiry. An ordinary install remains
removable; a factory reset or another device is outside the guarantee. Do not
run managed-device provisioning on the daily phone.

### 5. Play policy and distribution

The Play build must not carry the direct build's removal guard. The accessibility
declaration still needs review. The Play AAB is built but not uploaded. Decide
whether the end project ships direct only, Play only, or both, and record the
decision with the artifact.

## P2 — Finish the experience, after the release is coherent

- **Readiness dashboard:** `ProtectionReadiness` is integrated in `MainActivity`.
  Verify every layer (DNS, apps, text, visual, lock) against the same model and
  that “not checked” never reads as “working”. Test grant/revoke, service death
  and network change.
- **Rule import/preview:** `RuleDocument` and `RulePreview` exist; verify the UI
  path, add-only merge, locked weakening refusal and file limits.
- **Person blur:** the classes exist; it remains face-based estimation, not a
  full-body or identity guarantee. Measure it; do not widen the claim.
- **Accessibility and Arabic:** TalkBack traversal, large font, contrast, touch
  targets, RTL and long Arabic strings.
- **Diagnostics:** opt-in export with versions, layer states, error codes and
  aggregate timings only; no hosts, page text, captures or keys.
- **Battery and sampling:** measure the whole process tree, not the app process
  alone, with screen state and content change.

## Release gates

| Gate | Required evidence |
| --- | --- |
| Source | One committed revision; no untracked runtime file; version metadata agrees in build.gradle, versions.json, README and docs |
| Build | `sh android/test-core.sh`, `./gradlew assembleDebug assembleDebugAndroidTest lintDebug assembleRelease` pass under JDK 17+; warnings recorded |
| Artifact | `check-release.py` passes for Play and direct; `apksigner` certificate matches the release key; SHA-256 recorded |
| Quality | The four Arabizi fixtures pass without new benign blocks; visual positive/benign benchmark recorded; no accuracy claim beyond it |
| Device | At least one real phone pass for install/update, DNS transition, accessibility, visual, commitment and uninstall limitations |
| Distribution | Direct APK and Play AAB are distinct; Play upload is a recorded, explicit action; the previous artifact is kept |

## Delivered candidate artifacts (this pass)

Built from the current working tree with a temporary `1.4.0 / 36` override; the
source was restored to its declared `1.2.3 / 32` afterwards. They are useful for
inspection and device trials, but they are **not** the final release until P0 is
done.

| File | Bytes | SHA-256 | Identity |
| --- | ---: | --- | --- |
| `dist/android-final-2026-11-08/hisn-1.4.0-direct.apk` | 31,332,357 | `bc14c88e0380182df748403475899b7e000b6fcfe3c7633f526a90d5145c4310` | 1.4.0-direct (36), release key `2F:BE:…:C5` |
| `dist/android-final-2026-11-08/hisn-1.4.0-debug.apk` | 32,069,122 | `bb08376138ff5120931038d74760405c640e481ff70cb43834f39976e1c2f45f` | 1.4.0 (36), debug key `CA:81:…:61` |
| `dist/android-final-2026-11-08/hisn-android-1.4.0-source.zip` | 13,996,677 | `0753bb034d36f4e68678098d849d1033f9f6f4a0f4b99b4de7eb1c6e739676fc` | candidate source, `build.gradle` 1.4.0/36 |

Install only one identity at a time: the debug APK shares `app.hisn.android`
with the release and cannot be installed over it without uninstalling the
release (which removes local rules and a commitment). The direct APK is the
sideload; never upload it to Play.

The source zip in the same folder is the candidate tree with the 1.4.0/36
declaration applied; a fresh extraction passed `test-core.sh`, the Gradle seed
gate and `assembleDebug`. It is a project package, not a git commit: the
repository still needs the P0 commit so that another clone (and CI) sees the
same files.

## Decisions for the owner

1. Release number: 1.4.0/36 (matches the candidate) or 1.4.1/37 (new)?
2. Commit the 45 untracked files and the 1.4.0 record now, or keep the candidate
   in the working tree?
3. Direct only, Play only, or both for the end project?
4. Which physical phone is the acceptance device, and is it disposable for the
   device-owner tests?
5. Where does the adult-positive visual benchmark come from, and who reviews
   its licences?
6. Is the always-on VPN flag in the manifest consistent with the DNS-only
   service? Resolve before claiming lockdown.

## Non-goals

- A full-traffic VPN. It is a separate forwarding engine with IPv6/TCP/UDP/QUIC,
  backpressure and battery work; the DNS-only service is not it.
- Rust migration. Profile first; Java is not yet shown to be the bottleneck.
- Unremovable ordinary installs. Only device-owner/management authority provides
  OS enforcement; accessibility screens are friction.
- Any adult-recall, battery, network-transition or full-coverage claim without a
  recorded phone run.

## Commands used

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
sh test-core.sh
./gradlew :app:assembleDebug
export HISN_ANDROID_KEYSTORE="$PWD/../keys/android/hisn-release.jks"
export HISN_ANDROID_KEYSTORE_PASSWORD="$(cat "$PWD/../keys/android/keystore-password.txt")"
export HISN_ANDROID_KEY_ALIAS=hisn
export HISN_ANDROID_KEY_PASSWORD="$HISN_ANDROID_KEYSTORE_PASSWORD"
./gradlew :app:assembleRelease -PhisnDistribution=direct
python3 check-release.py --build-tools "$ANDROID_HOME/build-tools/36.0.0" \
  --expect-distribution direct app/build/outputs/apk/release/app-release.apk
```
