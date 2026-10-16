# Visual hide-first research — 2026-10-12

## Primary sources checked

- [HaramBlur README](https://github.com/alganzory/HaramBlur): Human face/gender
  estimates plus NSFWJS, the same library families already bundled in Hisn.
- [HaramBlur queue](https://github.com/alganzory/HaramBlur/blob/main/src/modules/queues.js):
  concurrent image loading, but one detection job at a time; the next starts
  when the previous job completes rather than after a per-image second.
- [HaramBlur styles](https://github.com/alganzory/HaramBlur/blob/main/src/modules/style.js):
  its optional temporary start blur has a 7000 ms animation that ends unblurred.
  This is not a suitable fail-closed completion rule for Hisn's requested mode.
- [HaramBlur license](https://github.com/alganzory/HaramBlur/blob/main/LICENSE):
  AGPL-3.0. No source code or model assets were copied in this change.
- [NSFW Filter README](https://github.com/nsfw-filter/nsfw-filter/blob/master/README.md):
  documents hiding images first and revealing those classified safe.

These sources describe approaches, not proof of universal detection accuracy.

## Local change implemented

Hisn now coalesces immediate, browser-yielding scan passes on discovering new
visible media and after a ready result. One inference remains in flight per
scanner; the existing shared model queue remains bounded. The one-second timer
is still a fallback for retries and periodic source/video sampling, not an
artificial delay between every successful image classification. Cancellation,
generation checks, opt-out and cleanup remain in place.

The deterministic scanner test drains 50 records without interval ticks and
verifies no unchanged-source reclassification and no pump after stop. This is
**not** a 50-image real-model timing benchmark. Extension checks pass with 141
scanner lifecycle assertions. No millisecond throughput claim is established.

## Pre-display gate implemented locally

Opted-in Chromium registration now includes a document-start stylesheet. Pending,
unreadable, malformed and failed results do not grant reveal permission. Matching
persons are fully hidden and retained for the same source/preference, rather
than merely blurred. Validated non-matches receive an ephemeral DOM marker.
Source mutations, seeks and mode changes revoke prior reveal permission. New injections
discard stale reveal markers. Failed jobs retry with a bounded delay while the
queue advances to other media. HTTP pages can generate request correlation IDs
without secure-context-only randomUUID.

The 2026-10-15 X playback repair distinguishes initial admission from periodic
monitoring: an already checked, unchanged source stays visible during later
sampling, rather than blanking the player every two seconds. Buffering recovery
(`playing`) no longer cancels checks or resets retry backoff. Positive and unknown
results still withhold media; adult-source latches and new-source/seek/policy
invalidation remain enforced. This is a deliberate playback tradeoff, not
certification of every intervening video frame or animation frame.

The canonical CSS/JS is also staged into Safari resources. Safari's static
registration temporarily withholds media while preferences are pending, even
when the eventual setting is Off; confirmed Off releases it without inference.
A missing settings reply is retried and does not silently mean Off. Physical
Safari execution is still unverified.

The visual-gate browser fixture covers 50 pending images with deliberately
delayed model replies, malformed results, source replacement, opt-out, startup
failure and preference recovery. This is rendering/lifecycle evidence, not
real-model accuracy. The isolated Helium UI review additionally checks actual
registered CSS and a first-animation-frame synthetic gallery. Measure warm/cold
actual-model latency separately; no millisecond or universal first-paint promise
is made. Small or unreadable media can remain hidden while checks are enabled.

Reload the source extension and refresh existing webpages to apply these local
changes. No native app, device permissions or commitment settings were reset.

Local verification: extension unit/package checks passed (83 worker and 141
scanner assertions); the full browser suite passed, including the gate fixture
and X/Twitter/DNR regressions. The isolated Helium UI review passed 69 checks,
including actual stylesheet registration and the first-frame gallery. Safari
resource staging passed 12 checks; no physical-device execution is implied.

Coverage remains face-based and uncertain, not identity recognition or full-body
detection. CSS backgrounds, canvas, shadow roots, protected media and unsampled
video moments require separate coverage work. Do not promise every image or
zero exposure on every webpage.
