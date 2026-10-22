# Android benign-image and classifier-performance baseline

Review date: 2026-10-22. This is a limited benchmark of the existing 0.5.0 model,
not a new model, a representative accuracy result, or a battery-life estimate.
The owner explicitly chose benign images and performance for this step.

The [complete recorded result](ANDROID_VISUAL_BENCHMARK_2026-10-22.json) includes
all 36 model calls, the labelled manifest, image/engine hashes, runtime versions,
resource counters and limitations. The [repeatable tools](../android/visual-eval/README.md)
prepare the corpus and run the actual production classifier in an isolated APK.

## Results

Device: Samsung SM-G990B2, Android API 36, WebView 155.0.8059.43. The pinned
NSFWJS MobileNetV2 engine used its existing TensorFlow.js CPU backend. Four
attributed public-domain/CC0 photographs each had standalone, synthetic-feed and
small-tile views. All twelve views were run three times.

| Observation | Measured result |
| --- | --- |
| Unique source photographs / views | 4 / 12 (layouts are correlated) |
| Actual model calls | 36 |
| Benign views falsely blocked | 0 / 12 |
| Unknown calls / inconsistent repeated views | 0 / 0 |
| Median classifier roundtrip | 1,984 ms |
| p95 / maximum roundtrip | 2,290 ms / 2,629 ms |
| Classifier initialization | 2,244 ms |
| Full run elapsed time | 76,315 ms |
| Main app-process PSS before / latest during run | 39,267 KiB / 141,449 KiB |
| Main app-process CPU-time increase | 3,505 ms; renderer excluded |
| OS thermal status before / after | 0 / 0 |
| Battery temperature before / after | 24.9°C / 24.1°C |
| External power / battery level | Power flag 1 throughout; level 84 throughout |

The timing covers production pixel conversion, bridge and model response after
the prepared bitmap exists. It excludes screenshot acquisition, tree checks,
input decoding and intervention. Main app-process memory and CPU omit the
WebView renderer and cannot represent total classifier resource use. Resource
counters are short-run observations with other phone activity, not app-attributed
energy measurements. No battery-saving or no-throttling guarantee follows.

The 1.98-second median is close to the nominal two-second inspection interval.
This is a performance concern: the live guard allows only one request at a time
and can skip polling opportunities while busy, so actual screen sampling can be
less frequent. These results do not establish a two-second blocking deadline or
protection before display. No production backend or detection threshold changed
as part of this measurement.

## Scope and verification

The model allowed every prepared benign sample; no synthetic verdict hook was
used. This is a small false-block regression baseline, not a general false-positive
rate. No adult-positive corpus was used, so adult recall and precision remain
unestablished. Real video playback, varied medical/art contexts, broad app layouts,
long runs and battery drain remain separate acceptance work.

The benchmark verifies the actual APK engine digest against the corpus manifest,
validates each PNG, and counts repeated calls separately from distinct views.
Three reporting tests passed, including unknown handling and absent-positive
denominators. Debug/release builds and lint passed; the release manifest excludes
the benchmark activity and debug DUMP permission.

No screenshot, camera, microphone, VPN, real commitment or private content was
used. No accessibility permission was requested. The staged image files and the
isolated test installation were removed after measurement. The existing owner's
installation and settings were not changed.
