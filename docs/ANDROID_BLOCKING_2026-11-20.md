# Android blocking and people-cover verification

## Changes

- Text events are checked against the current visible leaf in the same app and window. Scroll-only events avoid the synchronous source read. Truncated labels and incomplete screen scans cannot turn a bare adult tag into a block without its surrounding context.
- The adult image check retains a full-screen view beside an isolated picture crop. A positive crop requires supporting full-screen evidence and live confirmation. Its preprocessing reuses interpolation coordinates and clears the input buffer once per frame.
- People mode loads FaceRes in parallel, shares pixel extraction with the face detector, and skips unnecessary face inference. The guard retains only a 32-pixel, box-blurred colour snapshot for its cover, shown as a frosted blur behind a readable card. A cover belongs to its originating window. Back requires a second positive capture from the currently active, focused application window.
- The WebView fallback checks faces when an initial adult hit is cleared by the shape-preserving view.
- Faces are found natively by MediaPipe's BlazeFace "back" model (`blazeface_back.tflite`, google/mediapipe v0.7.12, Apache 2.0, SHA-256 pinned), the model Human's `blazeface.json` was converted from. It runs with FaceRes on one worker thread; the WebView engine starts only when a native face model cannot load. Boxes are enlarged 1.4 times and squared as Human does. Decoding is `app.hisn.core.BlazeFace`, unit-tested.
- A third people response, "Blur only the face, keep using the app", draws soft see-through patches over matched people (`FaceBlurLayer`) and lets taps through. When the area under a face scores as revealing or explicit, the whole person is blurred instead; that check never blocks on its own. Patches follow reported scroll distances between screenshots. Geometry and thresholds are `app.hisn.core.PeopleRegions`, unit-tested.
- With the face-only blur, what no screenshot has checked yet is veiled (`app.hisn.core.UncheckedArea`, unit-tested): a new screen as soon as Android reports it, each strip a scroll brings into view, and a lead band at the incoming edge while the page moves. A veil holds no pixels of what it hides; it is a wash in the last checked frame's colours, and it clears when a check lands. Android shows an app's pixels before any accessibility service can see them, so this narrows the gap to the event delay (about 0.1 s on a phone, more on a busy emulator); it cannot close it.
- Screenshots are prepared on a worker thread; the focus query runs only while a keyboard is shown; each window's app is looked up once. Together these removed main-thread stalls of up to 1.6 s that delayed accessibility events on the emulator.
- A screenshot timeout discards that capture and retries with backoff while retaining the loaded model. Cover ownership is checked during model loading as well as ordinary sampling, so leaving the selected window can release its cover promptly.
- DNS address replies avoid allocating alias maps and a traversal set when they contain no aliases. Wire validation and the current policy recheck still run.

## Image comparison

The isolated debug APK was tested on an Android 16 arm64 emulator. Identical
case IDs and label digests were checked against the saved baseline. The benign
set has 468 screens from 117 photos. The people set has 408 screens from 102
photos. Labels are the earlier Commons pre-review described in the accuracy
baseline, and still require owner spot-checking.

| Measure | Baseline | Updated |
|---|---:|---:|
| Benign adult-image false blocks | 19/468 | 16/468 |
| Photos blocked in any view | 12/117 | 11/117 |
| Benign hits allowed to act on the first capture | 8 | 0 |
| Women setting, wanted people covered | 170/196 | 171/196 |
| Women setting, other people covered | 46/184 | 47/184 |
| Women setting, no-person screens covered | 4/28 | 4/28 |
| Adult-image blocks within the people set | 4/408 | 2/408 |
| Unknown results, both sets | 0 | 0 |

Three benign feed screens changed from block to allow, comprising two medical
images and one swimwear image. Two people feed screens also changed from adult
block to allow. Their face settings then applied. All other people decisions
matched the baseline.

The measured adult median was 59.0 ms before and 33.0 ms after. The women-setting
median was 123.4 ms before and 124.3 ms after. These runs occurred under varying
host load and emulator restarts. They establish observed timings, with no
controlled end-to-end speed claim. The activity timer excludes screenshot,
decode and bitmap preparation.

A separate local JVM microbenchmark measured FaceRes preparation at 12.39 ms
before and 5.37 ms after, with identical model inputs in 800 crop cases. An
alias-free DNS reply microbenchmark measured 408 to 224 allocated bytes per
reply and 256 to 164 ns CPU per reply. Both describe the measured operation on
the host JVM. Phone latency, battery use and thermal behaviour remain unmeasured.

## Verification

- The full Android core suite passed. The text corpus caught 72/73 blocking controls and blocked 0/138 benign controls. The existing Franco-Arabic challenge `sksy download free` remains a miss.
- Both debug editions compiled. The isolated APK and its instrumentation APK built successfully. Hisn debug lint completed with zero errors and 406 warnings.
- All seven offline accuracy-tool tests passed. The Android JavaScript bridge regressions passed, including a cleared adult hit followed by a matching or unreadable face result.
- Live text checks passed for a stale event, a real label update, medical/recovery/blocker context, English and Arabic adult labels, and screen context split across many labels.
- All 12 live visual checks passed, including moving frames, typing/password pauses, protected windows, a synthetic one-shot block, capture recovery after the hold, opt-out, a shape-only people scene without a speculative cover, and a portrait covered before exactly one Back action.

The live run exposed a status-reporting regression during input pauses, which
was corrected and verified again. It also exposed screenshot timeouts that
unnecessarily reloaded the model. Capture timeout handling now retains the
runtime. The recovery check allows up to three ten-second attempts for Android's
transient screenshot failures and the guard's bounded retry backoff. Tests used
the dedicated emulator and isolated fixture package.

## Remaining limits

This set contains no adult-positive images. Adult recall, including the impact
of requiring full-screen evidence for a picture crop, is unmeasured. Small
explicit pictures may lose evidence in the full-screen view. Medical and
breastfeeding images still produce false blocks. The people detector and
appearance estimator still miss faces and cover other people. Four views of
one photo are correlated. These results cannot establish universal accuracy.

## People blur speed on repeated frames

The face runtime now reuses one known face result for up to five seconds when
SHA-256 over every RGB pixel and both frame dimensions matches. The cache is
owned by one runtime and people setting. It retains a digest and verdict, with
no source pixels. Changed inputs receive a fresh face check. The native adult
check and the guard's foreground-window validation still run on every submitted
frame. Reuse expires from the original capture time, so repeated hits cannot
extend it indefinitely.

On the same two-core Android 16 arm64 emulator, 24 distinct screens from 24
photos were each submitted four consecutive times. Each mode therefore had 96
frames. This sequence measures repeated still-image performance. It cannot
establish a speedup for new or moving images.

| Setting | Before median | After median | Before p95 | After p95 |
|---|---:|---:|---:|---:|
| All people | 95.5 ms | 19.5 ms | 507.6 ms | 80.4 ms |
| Women | 86.5 ms | 19.1 ms | 114.0 ms | 107.3 ms |

All 192 verdicts matched their before-run counterpart, including the adult
verdict, people-cover decision and strong flag. Both updated runs had zero
unknown results. Timing begins at runtime submission and excludes capture,
decode and bitmap preparation. Host load can affect emulator timings.

The core suite and the new cache regressions passed. The regressions exercise
one-bit RGB changes, dimensions, hashing across buffer boundaries, expiry,
clock reversal, unknown results, clean-frame replacement and mode isolation.
The updated APK also passed all 12 live visual checks, including capture
recovery, opt-out, a shape-only screen with no speculative cover, and a matching
portrait covered before exactly one Back action.
