# Android local visual evaluation

This runs the production `VisualClassifier` on supplied PNG files in the separate
`app.hisn.android.visualcheck` debug installation. It does not enable accessibility,
capture the screen, change rules, start a VPN, or use synthetic verdict overrides.
The benchmark activity and its shell DUMP entry point are absent from release.

## Prepare the benign baseline

Requires Python 3 with Pillow, JDK/Android SDK and the normal Android build setup.
From the repository root:

```sh
python3 android/visual-eval/prepare-benign.py --out android/build/visual-benchmark-benign
```

The output folder must be new. The script downloads four pinned scikit-image
0.25.2 sample PNGs, verifies their published SHA-256 hashes, and creates three
views per source: standalone, a synthetic feed, and a small synthetic tile.
The originals are the NASA portrait of Eileen Collins (public domain), Chelsea
the cat (Stefan van der Walt, CC0), a coffee cup (Rachel Michetti, CC0), and the
camera photograph (Lav Varshney, CC0). See the [official image descriptions and
rights notes](https://scikit-image.org/docs/0.25.x/api/skimage.data.html) and the
[pinned registry](https://github.com/scikit-image/scikit-image/blob/v0.25.2/skimage/data/_registry.py).
Source URLs, hashes and attribution are recorded in `benign-sources.json` and
copied into the generated manifest. These four images were also visually reviewed.

These twelve views are correlated derivatives of four images, not twelve
independent photographs or a representative benign-content benchmark. There are
no adult-positive examples, so adult recall cannot be estimated from this corpus.

## Build and run

```sh
cd android
./gradlew -PhisnIsolatedVisualCheck=true assembleDebug
cd ..
# Check output-metadata.json has applicationId app.hisn.android.visualcheck before installing.
adb -s SERIAL install -r android/app/build/outputs/apk/debug/app-debug.apk
python3 android/visual-eval/run-device.py --serial SERIAL --corpus android/build/visual-benchmark-benign --report /tmp/hisn-visual-benchmark.json
adb -s SERIAL uninstall app.hisn.android.visualcheck
```

The runner copies only the manifest's files into the isolated app's private
benchmark directory. The device validates every PNG's hash, dimensions (at most
384 per edge), labels and bounds, and verifies the actual bundled engine digest
against the manifest. Results must contain every expected `(case, repeat)` once.
The runner removes staged inputs afterwards; on failure it also force-stops only
the test package. It never changes the installed owner's app or permissions.

Three repetitions at a nominal two-second start interval produce 36 model calls.
Only one call runs at a time. Slow inference extends the interval; it never
creates a backlog. The report counts each unique view's first verdict once for
the confusion counts, flags inconsistent repeats, keeps unknowns separate and
reports median, nearest-rank p95 and maximum successful roundtrip latency.

Latency begins before the production Java pixel-to-Base64 conversion and ends at
the classifier callback, including WebView/model/bridge work. It excludes PNG
decoding, accessibility capture, traversal and intervention. This is not a
measurement of the complete live screen-filtering interval. Initialization is
measured separately for a fresh classifier instance, not a device-wide cold start.

App-process PSS and CPU time explicitly exclude the separate WebView renderer.
They must not be presented as total classifier memory or compute use. The report
also records external-power flags, battery level/temperature and OS thermal
status. A short run on a powered phone with other apps running cannot determine
incremental battery drain, daily battery life, or thermal stability.

`test-summary.py` verifies that repeats do not inflate accuracy denominators,
unknown results are not counted as successful fast calls, and missing positive
examples do not produce a recall claim.

## Future reviewed corpora

To evaluate a separately reviewed local corpus, create a manifest using the same
schema with `kind: "reviewed-labelled"`, an explicit `engine_sha256`, `repeats`,
`interval_ms`, and a `cases` array. Each case has a unique safe token `id`,
`expected: "allow"` or `"block"`, and its PNG `sha256`; the file is `<id>.png`.
Keep images at most 384 pixels per edge and record provenance, rights and label
review. Use `--confirm-reviewed-corpus` with the runner. Do not use private
messages/screenshots or unreviewed image collections. The tool does not fetch
an adult corpus, select labels automatically, or upload any inputs.
