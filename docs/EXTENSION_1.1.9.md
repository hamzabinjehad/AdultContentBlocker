# Hisn extension 1.1.9 — appearance confirmation and stable masks

Local development update, verified on 17 October 2026. This document does not
claim a signed app release, a GitHub merge or an update installed in an existing
browser profile. Reload Hisn and refresh open webpages after updating.

## Changes

- Before excluding a face from a women/men preference, confirm a confident
  opposite appearance estimate using a horizontally mirrored 64×64 crop.
  Conflicting or uncertain views are protected without inventing a new gender
  label. Invalid output remains unknown and withheld.
- At most one additional small classifier pass per otherwise-excluded face;
  matching/uncertain first estimates and all-faces mode need no additional pass.
  No additional full-image detector, model weights, remote inference or uploads.
- Keep an existing face mask across a later clean resample only when the broker's
  bounded SHA-256 identity confirms identical decoded pixels and policy, and
  source, route and layout still match. Changed frames receive an independent
  verdict. Unknown results remain withheld; adult-positive results take priority.
- Retain current adult thresholds, ten-face detector limit, commitment rules,
  face/whole-media choices and image/video scope. Native app behavior is unchanged.

## Evidence and verification

The supplied Getty search page uses standard cross-origin images. The first
thumbnails' source/display ratios match, so a proposed visible-crop change was
not shipped. Its actual image nodes become readable after the existing same-node
anonymous reload; no privileged image-fetch proxy was introduced.

The old bundled model could exclude a visible face despite masking a screenshot
crop of it. On one original image, its first-view probabilities were about
0.10/0.90 while the mirrored crop returned about 0.86/0.14. This is evidence of
appearance-estimate disagreement, not evidence of the subject's actual gender.
Disabling experimental graphics options did not resolve it and was not shipped.

The exact new packaged bundle was tested in a separate, logged-out Helium profile
with no native host and women/faces/opaque/images selected. For this isolated
visual-only check, page-text and adult classification were disabled in the test
profile, not in any personal profile. All 14 tested original Getty images received
face masks, including the reproduced misses. Masks remained present after 32
seconds, including periodic checks and cache expiry. A synthetic ordinary no-face
image remained visible. The actual offscreen runtime reported WebGL ready; no
page errors were recorded. The resulting rendered masks were visually inspected.

Additional verification passed:

- Complete extension suite, locale checks and packaging validation.
- 101 visual-decision, 32 canonical-engine, 699 scanner-lifecycle, 124 cache-broker
  and 33 broker-race checks, including invalid confirmation, changed pixels,
  policy/source/route/layout changes, cancellation and adult-positive precedence.
- Complete isolated Helium browser suite, including real SHA-256 caching,
  two-origin image capture, face rendering, normal video playback and live X feeds.
- 12 shared Safari staging/controller tests. This uses the canonical extension
  sources, not a new physical iPhone/iPad installation or runtime verification.

Model bundle SHA-256:
`994b8f38fca73fc8a5b0ed65e70d834e37c8bc75f535af80b907d44909ad20f7`.
Recorded model weight hashes are unchanged; the bundle was rebuilt offline.

## Limits

This is a narrow regression test, not a representative accuracy benchmark.
Faces can still be missed or misclassified; uncertain-face protection can mask
unintended faces. It cannot identify every woman/man, infer actual gender,
detect every body or inspect every video frame. Additional classification can
increase latency; no millisecond or performance-superiority claim is established.
The independent adult model can also make mistakes; this update does not claim
to fix its false positives or establish complete adult-content prevention.

No real webpage media, private screenshots, browser profiles or inference logs
are included in the source or extension package. Diagnostic media stayed local.
