# Android 1.6.0: the candidate, with the reconnect lifecycle

Development candidate in the repository, 10 November 2026, version **1.6.0**,
versionCode **43**. No signed artifact, Play upload or phone run is claimed.

## What is in it

1.6.0 is the first numbered build of the work that was committed on
10 November: the 1.5.3 feature set (including the person cover and the
go-back response), the package-2 readiness and receipt work, the package-3
signed list updates, hostname explanation and portable rule tools, and the
1.5.4 reconnect lifecycle.

A downloaded list generation now carries its terms with its domains:
`AdultListUpdater` verifies `terms.json` against the same signed manifest,
compiles the hostname and text policies before staging, and publishes both
through one `HostListStore.adopt` call. The text and DNS layers therefore read
the same vocabulary version, and a damaged or mismatched terms file rejects
the update instead of leaving one layer on the old list. A damaged installed
terms file still falls back to the APK terms while the signed domain list
enforces. The rollback floor also reads the committed pointer, so a crash
between the pointer and floor writes cannot let an older generation in.

The 1.5.4 lifecycle is ported rather than merged as a whole branch. The
service now proves the bound instance in `onCreate`, re-arms in `onRebind`,
returns `true` from `onUnbind`, restores `connected` on a live accessibility
event, and unregisters its rearm receiver in `onDestroy`. `MainActivity` asks a
live but not-yet-connected service to re-arm when it returns to the foreground.
The two reconnect strings name the Accessibility → Installed apps path instead
of a dead end.

No detection threshold, permission, captured field, seed or stored setting
changed.

## Why the lifecycle work

The 1.5.3 dashboard could show **Apps: Needs attention** and “Hisn
accessibility is enabled but not connected” while `dumpsys accessibility`
listed the service as bound. The service waited for `onServiceConnected`,
which Android does not always repeat after an update or rebind, and the
one-shot rearm broadcast could be missed while the service was being created.
The full 1.5.4 record, the phone evidence and the artifact hashes are in
[the 1.5.4 record](ANDROID_1.5.4.md).

## Verification

| Check | Result |
| --- | --- |
| `android/test-core.sh` (JDK 25) | Pass; every core class, including the orphan guard, the seed, strings, notices and the synthetic corpus |
| `:app:assembleDebug` | Pass |
| Version-aware terms loader | Exercised by `CoreDeviceTestRunner` against the bundled seed |
| Signed fixture generation | Pass on the API 36 emulator: adoption of domains + host terms + text terms, bad-signature rejection, damaged-terms rejection with the old generation retained, cleanup |
| `:app:assembleDebugAndroidTest` | Pass |
| `:app:lintDebug` | Pass; 0 errors |
| `:app:assembleRelease` | Pass; unsigned because no release key was supplied in this run |
| Python, extension, browser, macOS and iOS suites | Unchanged by this pass; their current results are in [release state](RELEASE_STATE_2026-11-10.md) |

The core corpus now blocks **71/72** adult rows and **0/133** benign controls.
Three of the four Franco-Arabic misses are closed by the reviewed
`aflam neek` / `film neek` context; the fourth, `sksy download free`, stays a
reviewed benign control because `sksy` is also Indonesian "SKS". Blocking it
would trade a real false positive for one challenge row.

## Limits

- **The post-reboot dashboard is still unverified.** The 1.5.4 record says the
  dashboard could read disconnected after a reboot even with the service
  bound. This candidate adds the lifecycle evidence and the foreground rearm,
  but no phone run has confirmed that the fresh instance clears the state. A
  clean re-enable may still be needed.
- **No signed 1.6.0 artifact.** The release key was not used in this pass;
  `assembleRelease` produced an unsigned APK. `check-release.py` has not run
  against a 1.6.0 artifact.
- **No phone, visual-accuracy or battery claim.** The image layer keeps its
  1.4.0 native runtime and the same thresholds; no adult-positive benchmark was
  added.
- **The terms update has no phone run.** The signed fixture generation proves
  the install path on an emulator, but no real phone or network download has
  exercised it.
- **The Play/direct split still needs review before upload.** No Play AAB was
  uploaded.

## Artifacts

None. The candidate is a source revision; build it, sign it with the owner's
key, run `check-release.py` for the intended distribution, and record the
artifact hashes before installing it over a phone.
