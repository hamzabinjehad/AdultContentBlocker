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

## Accuracy runs (adult check and people cover)

The benchmarks above time the classifier on a dozen small files. An accuracy run
instead asks **how often the shipped image check gets it right**, on hundreds or
thousands of labelled pictures shown the way phone apps show them.

`AccuracyRunActivity` in the isolated debug package
`app.hisn.android.visualcheck` uses the same bitmap preparation as
`VisualGuard`. The native adult check receives the 224 px full screen and an
optional picture crop. A crop that tests positive needs supporting evidence
from the full screen, then a second capture in the live guard. People modes
also pass a 384 px full screen to the face engine.

```sh
# 1. Labelled pictures -> phone screens (full, tall, feed, tile; partial is opt-in, adult only).
python3 android/visual-eval/make-views.py --labels LABELS.json --source PICTURES --out NEW_VIEWS
# 2. Run and score. Modes: adult, people-all, people-female, people-male.
python3 android/visual-eval/run-accuracy.py --serial emulator-5554 --views NEW_VIEWS \
    --mode people-female --report report.json --markdown report.md
# Offline checks of the scoring and views:
python3 android/visual-eval/test-accuracy.py
```

`labels.json` is `{"schema": 1, "kind": "adult" | "people", "cases": [...]}`.
An adult case has `id`, `file`, `sha256`, `expected` (`block`/`allow`) and a
`category`. A people case has `people` (any of `woman`, `man`; empty for no
person) and optional string `attributes` such as `headwear`, `skin`,
`age_group` or `pose`, which the report breaks results down by.

What the report counts:

- **Adult mode:** recall (adult screens blocked) and the benign block rate, with
  95% Wilson intervals, by view and category, plus how many catches were
  `strong` (blocked on the first frame). A non-strong block on a still screen is
  confirmed by the next capture, so it counts as blocked; `unknown` does not.
- **People modes:** covered when the setting wants it (a woman in women mode),
  other people covered (a man in women mode) and no-person screens covered, by
  view and attribute. The cover is recall-biased on purpose, so the second and
  third numbers are expected to be above zero; the run says by how much.
- Views of one picture are correlated. Quote the picture count next to any rate.

Keeping material where it belongs:

- Only ids, digests and PNGs go to the device; labels stay on the computer. The
  device deletes each file once judged, and the runner removes the staging and
  app folders whether or not the run finishes. Reports hold ids, labels and
  counts, never pixels or paths.
- A label set with adult positives is refused inside this repository, and runs
  only on an emulator unless `--allow-physical-device` is passed. Use a
  disposable AVD for it and delete the AVD afterwards: freed emulator storage
  can keep file contents in the AVD disk image on the Mac.
- Never use images that could show a minor, private screenshots or messages,
  web-crawl collections (LAION and similar) or the Reddit/Imgur scrapes NSFWJS
  was trained on (`nsfw_data_scraper`): the last inflate the score, and none of
  them record age or consent.

### Benign hard set and people set from Wikimedia Commons

`commons-plan.json` names freely licensed Commons categories: swimwear,
breastfeeding, medical images and portraits for the benign set; women in hijab
or niqab, women and men without head covering, bearded men and no-person photos
for the people set. No adult material.

```sh
# Metadata only: picks files with a free licence and writes them as "pending".
python3 android/visual-eval/pick-commons.py --plan android/visual-eval/commons-plan.json \
    --out android/visual-eval/commons-sources.json
# A person reviews every entry on its Commons page: "review": "ok", or "rejected" with a
# "review_note" (a child, no visible face, mixed sexes, label does not fit).
python3 android/visual-eval/fetch-commons.py --sources android/visual-eval/commons-sources.json \
    --out android/build/visual-commons
```

The fetch checks every file against the SHA-1 Commons publishes and writes
`benign-labels.json`, `people-labels.json` and `ATTRIBUTION.json` next to the
pictures. Category names are hints: the review is what makes them labels.

### Women/men estimate: FaceRes, run natively

The women and men settings estimate appearance with FaceRes (Human's face
description model, from HSE-asavchenko/HSE_FaceRec_tf, Apache 2.0), not the
64 × 64 greyscale classifier the browser extension keeps. In the app the WebView
engine only finds faces; `HybridRuntime` crops each face box the way Human does
and runs FaceRes on TFLite (`FaceresClassifier`), then applies the same
thresholds as `decisions.js FACERES_OPPOSITE`. If native FaceRes cannot load,
the app uses the packaged WebView's 64 × 64 appearance estimator.

`faceres_gender.tflite` is a conversion of the pinned TensorFlow.js files, not a
new model, and keeps only the gender output:

```sh
# Python environment with tensorflow==2.16.2
python android/visual-eval/convert-faceres.py           # writes the asset, checks TFLite = TF
python android/visual-eval/convert-faceres.py --check   # re-checks an existing asset
```

`run-accuracy.py --appearance native|faceres|oarriaga` picks the path for people
modes (default `native`, as the app). `--evaluate-appearance` records each face's
raw estimates from both WebView models and lets faces down to 0.30 detector
confidence through to the record, so thresholds can be replayed offline; the
decision itself is unchanged.
