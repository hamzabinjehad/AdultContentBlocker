# Android image check: first accuracy baseline, 2026-11-19

Until now every visual benchmark used four benign photos, so it showed speed and
that cats and cups are not blocked, but not how the check behaves on hard
pictures, and nothing about the women/men people cover. This is the first run on
a larger labelled set. **It has no adult pictures, so it measures false blocks
and the people cover only, not how much adult content is caught.**

The [complete record](ANDROID_VISUAL_ACCURACY_2026-11-19.json) holds every
verdict with its picture id, view and labels; no pixels.

## What was run

- **Code:** the shipping preparation and runtimes through the debug-only
  `AccuracyRunActivity` (see `android/visual-eval/README.md`): adult checks on
  native TFLite at 224 px with the plain-background crop; people modes through
  `HybridRuntime` (native adult check plus the faces-only WebView engine at
  384 px), the people path as of this change.
- **Device:** arm64 Android 16 emulator (API 36) on an Apple M3 Mac. Not a phone;
  times are emulator times.
- **Pictures:** 258 freely licensed Wikimedia Commons photos picked by
  `pick-commons.py` from `commons-plan.json`. Claude pre-reviewed 640 px copies
  and kept 219 (117 benign, 102 people); the 39 rejections and their reasons are
  in `commons-sources.json` (`suggested`, `suggested_note`). **The owner has not
  spot-checked these labels yet.**
- **Screens:** each picture rendered as four 1080 × 2400 phone screens (full,
  tall, feed, tile). Views of one picture are correlated, so the picture count is
  the honest sample size. Intervals are 95% Wilson.

## Results

### Benign hard pictures (adult check, expected: allow)

| Group | Benign screens blocked |
|---|---:|
| **All** (468 screens, 117 pictures) | **19 = 4.1%** (2.6–6.2) |
| full view | 9/117 = 7.7% |
| feed view | 10/117 = 8.6% |
| tall view | 0/117 |
| tile view | 0/117 |
| breastfeeding | 7/72 = 9.7% |
| medical (mammography, dermatology) | 8/100 = 8.0% |
| swimwear | 3/136 = 2.2% |
| portrait | 1/160 = 0.6% |

12 of 117 pictures were blocked in at least one view: breastfeeding close-ups,
bare-skin dermatology photos, one rotated portrait and three swimwear pictures.
**Every one of the 19 blocks went through the plain-background crop**, which
hands the model the photo itself at full 224 px instead of a strip of a tall
screen. The crop exists to catch adult photos shown on a plain background; this
run shows its cost on skin-heavy benign photos. Whether the trade is right needs
the adult-positive set: no threshold was changed.

Median 22 ms per check.

### People cover

| Setting | Wanted faces covered | Other people covered | No person, covered |
|---|---:|---:|---:|
| All faces | 348/380 = **91.6%** | — | 4/28 = 14.3% |
| Women (estimated) | 148/196 = **75.5%** | men: 101/184 = **54.9%** | 1/28 = 3.6% |
| Men (estimated) | 154/184 = **83.7%** | women: 94/196 = **48.0%** | 3/28 = 10.7% |

By group:

| Group | All faces | Women setting, women covered | Men setting, women covered |
|---|---:|---:|---:|
| Women, no head covering | 96.4% (uncovered and men combined) | 85/96 = 88.5% | 50/96 = 52.1% |
| Women in hijab | 46/52 = 88.5% | 41/52 = 78.8% | 25/52 = 48.1% |
| Women in niqab | 32/48 = **66.7%** | 22/48 = **45.8%** | 19/48 = 39.6% |
| Bearded men | 80/88 = 90.9% | (men) 48/88 = 54.5% | 65/88 = 73.9% covered |

What this says:

1. **The women/men choice barely separates the two.** About half of the other
   group is covered too. Part of that is deliberate: a face is shown only when
   the 64 × 64 greyscale appearance model is confident it is the opposite group
   (`OPPOSITE_CONFIDENCE`), and a face under 32 px is always covered. Part is the
   model itself (from `oarriaga/face_classification`, trained on IMDB-WIKI
   celebrity photos).
2. **Veiled faces are the weakest case.** The face detector finds only two
   thirds of women in niqab, and the women setting covers fewer than half of
   them. For Hisn's users this is the case that matters most.
3. **Grid tiles hurt the men setting** (56.5% covered against 89–96% in the other
   views): faces in a 356 px tile reach the model at about 60 px.

Median 61–63 ms per check (all three settings).

## Women/men model replaced: FaceRes (same day)

The baseline above pointed at the 64 × 64 greyscale appearance model. It was
compared with FaceRes, the face description model Human already ships (from
HSE-asavchenko/HSE_FaceRec_tf, Apache 2.0, pinned at the same human-models
revision), on the same 102 people pictures. An evaluation mode in the engine
(`evaluateAppearance`, accuracy runs only) recorded both models' raw estimates
for every detected face; decisions were then replayed offline, and the replay
reproduced the app's real covers on 408 of 408 screens.

How well each model separates women from men (368 labelled faces, AUC):

| Faces | 64 × 64 model (with mirror check) | FaceRes |
|---|---:|---:|
| all | 0.80 | **0.97** |
| under 32 px | 0.57 | 0.85 |
| 32–64 px | 0.83 | 0.97 |
| 64 px and up | 0.87 | 0.99 |

FaceRes never rated a man more likely a woman (0 of 181 faces) but reads many
veiled women as men (65% of niqab faces lean male, 6% above 0.70). So the
thresholds are asymmetric: the women setting shows a face only at P(man) ≥ 0.80,
the men setting at P(woman) ≥ 0.60, with no small-face exception
(`decisions.js FACERES_OPPOSITE`). Chosen on one half of the pictures, both
settings still beat the old model by a wide margin on the other half.

In the app FaceRes runs **natively**: the WebView only finds faces and returns
their boxes; `FaceresClassifier` crops each box as Human does and runs
`faceres_gender.tflite` (converted by `android/visual-eval/convert-faceres.py`,
float16 weights as pinned, 6.9 MB; TFLite equals TensorFlow to 3e-4). Native and
WebView FaceRes agree face by face: median |ΔP(man)| 0.002, maximum 0.035 over
322 faces. The WebView copy of FaceRes is not shipped in the app.

| Setting (emulator, 408 screens) | Old: wanted / others covered | FaceRes: wanted / others covered |
|---|---:|---:|
| Women | 75.5% / 54.9% (men) | **86.7% / 25.0%** |
| Women in niqab, women setting | 45.8% | **66.7%** (now the detector's limit) |
| Men | 83.7% / 48.0% (women) | **95.1% / 35.7%** |
| Women in niqab, men setting | 39.6% covered | 56.2% covered (worse: veils read as male) |

No-face screens (33 pictures, 132 screens) covered in the women setting: 8.3%,
all false face detections (mostly breast X-rays), the same with either model.

Lowering the face detector's 0.60 threshold was measured and **not** changed: at
0.50 the women setting would cover 92% of women and 81% of niqab faces, but
no-face screens covered rise from 10% to 23%. That trade is the owner's call.

Cost: the women/men settings take a median 109 ms per check on this 2-core
emulator (FaceRes is 0.57 GMAC at 224 px), against 63 ms with the old model and
146 ms before today; all-faces mode is unchanged at 60 ms. The release APK grows
by 6.9 MB. A phone with more cores should run FaceRes faster than this emulator;
not measured.

## Speed of the people cover (same change)

People mode used to send everything, including the adult check, through the
WebView. With `HybridRuntime` the adult check stays native and runs in parallel
with a faces-only WebView that never loads the adult model. On 20 smoke-test
screens, same files before and after, verdicts identical on all 40 checks:

| | Before | After |
|---|---:|---:|
| Women setting, median per check | 146 ms | 65–70 ms (two repeat runs) |
| All faces, median per check | 126 ms | 68 ms |

## Limits

- No adult pictures: recall is unmeasured. The adult-positive set waits on the
  research-dataset agreement and stays outside the repository.
- Labels are Claude's pre-review of small copies; spot-check before relying on
  them. Commons portraits are mostly well-lit, frontal and Western; real feeds
  have smaller, angled, partly covered and moving faces.
- Still screens rendered from photos, on an emulator: no live capture, video,
  scrolling, second capture or phone timing.
- Four views per picture are correlated; per-picture counts are the honest sample.

## Next steps this points to

- Build the adult-positive set, then decide the plain-background crop and the
  0.70/0.85 thresholds from both sides of the trade.
- ~~Replace the women/men appearance model~~: done, FaceRes (section above).
- Face detection now limits the women setting for veiled women: one in three
  niqab faces is not found. Options: the detector threshold trade measured above,
  a detector trained with covered faces, or a body/clothing cue beside the face.
- Measure FaceRes speed on a phone (the emulator has two cores).
