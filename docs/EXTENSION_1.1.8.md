# Hisn extension 1.1.8

This update publishes the current Chromium extension and the canonical scanner
and bundled engine used by Safari staging. It does not publish a new signed
macOS/iOS application, install a phone build, or make extensions undeletable.

## Changes

- Women/men viewing preferences withhold matching appearance estimates and
  uncertain faces. Only a confident opposite estimate remains outside the
  selected preference. Unintended faces can also be masked; these estimates
  do not establish gender, identity, or whether content is adult.
- Tied, malformed and failed appearance results remain unknown and withheld.
  Adult-positive blocking remains independent of the person preference.
- Compatible cross-origin images reload anonymously under the gate. Only
  fresh pixels from the actual displayed image can authorize its reveal.
  Servers that refuse scanning access still remain unknown, not presumed safe.
- Ordinary X posts/videos retain verified presentation during bounded
  monitoring. Source, route and preference changes still require new checks.
- Face-only/whole-media masking, opaque/blur effects and image/video scope are
  shared with Safari staging. Video and unsupported face layouts fall back to
  whole-media protection. The popup/settings include English and Arabic copy.
- A bounded, RAM-only exact-pixel verdict cache avoids repeated identical
  inference. Toolbar warnings and a guarded repair action reflect verified
  enforcement rather than model availability alone.

The additional viewer-domain source and generated Chromium/macOS policy files
are published together to preserve generator consistency. Other uncommitted
native application changes are outside this update.

## Local verification

The working extension passed the complete extension and isolated Helium browser
suites, including actual bundled-model startup, CORS pixel binding, X playback,
popup/settings and network-rule checks. Shared Safari staging/bridge passed
12 Python tests. Decisions include 74 checks, the canonical engine 19 checks,
and scanner lifecycle 676 checks.

In one user-provided screenshot, all-face detection found five unmasked faces
but the former 0.80 appearance cutoff rejected all five. The final rebuilt
model, actual scanner and mask renderer covered those five faces in three
repeated local tests; the blank tile received no invented face mask. This is
not a general accuracy or latency benchmark. The screenshot and experimental
variants are not included in this repository or its packages.

## Loading the update

For an existing unpacked installation pointing at this checkout, pull/update
the source, click **Reload** on the existing Hisn entry in the browser's
extensions page, and refresh affected webpages. Confirm version **1.1.8**.
Reloading source files on disk alone does not prove browser activation.

For a fresh installation, prepare a new folder instead of loading browser
caches or metadata from a reused checkout:

```sh
python3 extension/prepare_unpacked.py --out dist/hisn-unpacked-1.1.8
```

Load that folder using **Load unpacked**. The pinned local extension identity
is preserved. `extension/package.sh` creates a separate store-clean ZIP and
deliberately removes that pinned development identity.

For algorithm-only engine rebuilds using the recorded local assets:

```sh
node extension/visual/build.mjs --offline
```

This verifies existing recorded asset hashes and does not silently download
replacement weights or notices. Build dependencies must already be installed.

Coverage remains limited to detected visible faces and sampled readable media.
Some faces can be missed or misclassified; this does not identify all women or
men, inspect every video frame, cover other apps, or prevent browser-owned
extension removal. Safari staging checks are not physical iPhone/iPad runtime
or installation verification.
