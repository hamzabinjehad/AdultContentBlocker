# Person area, effect and media choices

Local development follow-up, not a new signed release. In the popup's visual
settings choose:

- **Area:** whole media (default), or faces only for supported still images.
- **Effect:** opaque black mask (default), or strong blur.
- **Media:** images and videos (default), images only, or videos only.

These choices apply to the selected person preference. Adult classification is
independent: an adult-positive verdict always withholds the entire medium,
regardless of person area, effect or media scope. Pending/failed checks keep the
existing pre-display gate for media whose enabled protection applies.

Face-only is experimental. It uses the detector's bounded normalized regions,
with a margin, and a local native SVG filter. It does not copy webpage pixels
into a new stored image, upload data or add another model. Source, mode and
effect changes dispose owned filters and revoke reveal markers. Existing site
filters are preserved. Model errors, missing or invalid regions use whole-media
hiding rather than exposing an incomplete mask.

Supported geometry currently means a borderless/padding-free image with
object-fit fill, or contain/cover with explicit percentage object positions.
Masks map original face coordinates through cropping and letterboxing. Size,
intrinsic dimensions, fit and position changes invalidate the mask; unsupported
geometry or an entirely cropped-away detected region falls back to whole hiding. Video
and recognized GIF sources use whole-media protection, because verified moving
face tracking is not implemented. Unrecognized animation can remain a coverage
gap; this is not frame-by-frame or full-body detection. Faces can be missed or
misclassified. Prefer the default whole opaque protection for the strongest
available person setting.

## Commitment and compatibility

The worker rejects full-to-face narrowing, opaque-to-blur changes, and media
scope reductions during an active commitment. Full coverage, opaque masks and
both media types can strengthen weaker existing choices. UI reflects those
rules, but does not replace backend enforcement. Native app behavior and user
commitment settings were not reset. No standalone native lock is added.

Chrome receives the shared renderer before the scanner. Safari's staged
resources use the same renderer and expose the same options, but Safari visual
preferences retain their existing browser-owned/unlocked boundary. Physical
iPhone/iPad execution is still unverified. Older content scripts without a media
kind do not receive a clean verdict for narrowed media scope; reload the source
extension and refresh pages to use the new contract.

## Verification and remaining gates

- Extension tests: 86 worker assertions and 51 visual assertions, including
  commitment weakening/strengthening cases; packaging and locale checks passed.
- Safari resource/controller checks: 12 Python tests, with new option saving
  and malformed-option cases in the staged JS contract.
- Real browser rendering fixture: eight checks, plus visual inspection of the
  generated black face mask and blurred face. Coordinates are simulated, not
  evidence of real-model detection accuracy.
- Scanner integration fixture: face rendering before reveal, media exclusions,
  video fallback, adult-positive precedence and cleanup passed.
- Isolated Helium UI review: 70 checks, including actual worker option saves,
  wide toolbar sizing and the first-frame synthetic gallery. Safari UI layout
  is explicitly mocked, not physical Safari inference.

Moving-video face tracking, broader object-fit support, actual-model timing and
comparative accuracy remain open. No claim that Hisn outperforms HaramBlur is
established. No HaramBlur source code was copied for these features.

## Conservative appearance decisions — extension 1.1.8

The opt-in women/men preference now withholds both matching appearance
estimates and uncertain faces. Previously it discarded every selected estimate
below 0.80, leaving the face unmasked. A leading selected estimate now matches
at 0.50 or above; an opposite estimate below 0.80 is also withheld as uncertain,
not relabeled as the selected gender. A confident opposite estimate remains
outside that preference. Consequently this wider-coverage setting can also mask
unintended faces, as disclosed beside the English and Arabic controls. Choose
all detected faces to avoid relying on appearance estimates for selection.

Malformed, non-finite or tied appearance outputs remain unknown and withheld,
not a no-person/clean verdict. Adult-positive blocking stays independent.
Existing full-media/face-only, black-mask/blur and image/video choices are
unchanged, as are commitments. Face presence still requires valid detector
confidence; blank images are not assumed to contain a person.

The detector retains its 0.60 confidence threshold and ten-face bound. Its
overlap suppression threshold is explicitly 0.30 to avoid the previous more
aggressive suppression of nearby boxes. The meaning of this setting is
documented in [Human's detector configuration](https://vladmandic.github.io/human/typedoc/interfaces/FaceDetectorConfig.html).
No extra detector passes, new model weights, skin-colour/text heuristics,
face identity features or external image uploads were added. The engine can be
rebuilt with `node extension/visual/build.mjs --offline`, which checks recorded
local asset hashes rather than downloading replacement weights or notices.

This is still visible-face filtering, not reliable identification of all women
or men, whole-body recognition, or every-video-frame inspection. The shared
Safari staging uses the same engine; it does not establish a new physical
iPhone/iPad installation or runtime test.

## Mirrored exclusion confirmation — extension 1.1.9

A confident opposite estimate is no longer sufficient by itself to exclude a
face from a selected women/men preference. Only that exclusion path runs one
additional classifier pass on a horizontally mirrored 64×64 greyscale face
crop. Both views must agree confidently. A disagreement or weak second view
is recorded as uncertainty and the face is protected, without relabeling its
gender or inventing a confidence score. Invalid/tied model output remains
unknown and withheld. Matching or already-uncertain first views, all-faces
mode and adult-positive results do not run this extra pass.

The detector, ten-face bound, adult classifier and model weights are unchanged.
This adds at most one small classifier pass per otherwise-excluded face, not
another full-image detector or an every-frame video guarantee. It may increase
latency and unintended masking; it is not evidence of perfect gender detection.

A verified face mask also survives a later clean resample when the broker's
SHA-256 identity confirms identical decoded pixels and policy, and source,
route and mask geometry remain unchanged. Changed pixels receive an independent
verdict; missing hashes never enable this retention. Unknown results remain
withheld and adult-positive results always take precedence. Only a bounded
identity/verdict is retained in memory, not raw frames or new persistent data.
