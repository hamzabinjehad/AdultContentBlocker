# Hisn extension 1.1.10 — faster preparation and repeat checks

Local development update, 17 October 2026. Reload the unpacked extension and
refresh existing pages to activate it. This is not a signed native-app release,
a GitHub merge, or evidence of installation in a personal browser or phone.

## Changes

- Start preparing the user's enabled local models during background protection
  reconciliation, before the first webpage capture. Rule application does not
  wait for preparation. Disabled visual preferences do not load models. Pending
  preparation is shared, cancelable and fenced against opt-out/late callbacks.
- Increase the bounded memory-only verdict cache from 32 to 128 entries. Keep
  the existing 30-second maximum age. Every reuse still hashes decoded pixels,
  dimensions, effective preferences, media kind and full visual policy; changed
  pixels, policy revisions, model contexts and opt-out invalidate reuse.
- Prepare at most four eligible image-readiness probes per pass, including while
  another image is being inspected. A one-pixel same-node probe may start an
  existing anonymous image reload; it is never a clean-content verdict. No extra
  full-resolution frames are queued or stored, and authored CORS modes are kept.
- Refuse capture/probing of an incomplete image reload, which can temporarily
  retain an older decoded bitmap. Actual inference still uses the fully decoded
  bounded image from that same node, not the readiness pixel or a URL proxy.
- Favor unchecked visible media over routine image rechecks. Reserve a due video
  monitoring slot after at most four first checks so continuous new images do
  not starve an already-playing source. Model inference remains serialized.
- Coalesce scroll notifications into a 50 ms viewport refresh and promptly queue
  pending work when a tab becomes visible, instead of relying on the 1 s poll.

The second-view appearance confirmation, full capture resolution, detector and
adult thresholds, commitment rules and hide-before-verdict gate are unchanged.
Native macOS/iOS app behavior is not changed by this extension-only update.

## Measured repeat-work regression

Separate logged-out Helium profiles loaded the actual packaged 1.1.9 and 1.1.10
models. The isolated test selected women/faces/opaque/images and disabled text
and adult classification only in those test profiles. Synthetic 224×224 images
contained distinct ordinary flat colors; all were visible in the viewport.
Broker instrumentation counted real inspections/cache hits and elapsed requests
in RAM, with no pixels or browsing information written to extension storage.

| Gallery size | First inspections, both versions | 1.1.9 repeat inspections | 1.1.10 repeat inspections |
| --- | ---: | ---: | ---: |
| 10 | 10 | 0 | 0 |
| 50 | 50 | 50 | 0 |
| 100 | 100 | 100 | 0 |

The larger galleries previously exceeded the 32-entry cache and evicted their
own earlier results before the next scan. All unchanged pixels still passed
fresh hashing; only redundant model inference was avoided. All ordinary images
stayed visible. This is not a representative model-accuracy benchmark or a claim
that first-time photos, difficult faces or videos take a fixed number of
milliseconds. The main measured gain is repeat checking, not a faster detector.
Startup/navigation retries, other workloads and hardware make single-run page
timings unsuitable for a universal speedup claim. Battery use was not measured.

## Verification

The complete extension and isolated Helium browser suites passed. Focused checks
passed: 711 scanner lifecycle, 201 cache, 41 broker race, 34 canonical-engine and
9 capture-readiness checks. All 12 shared Safari staging/controller tests passed.

- Extension tests cover the 128-entry cap, unchanged 30-second expiry, changed
  pixels/policy and unknown results; 10/50/100-image repeated-gallery cases also
  use real browser SHA-256.
- Preparation tests cover concurrent requests, latest policy, opt-out during
  creation and transport, late responses, and disable during model loading.
- Scanner checks cover four-probe bounds, independent hidden-first verdicts,
  authored credential preservation, fresh-item priority, bounded video fairness,
  viewport scheduling and timer cleanup.
- The two-origin browser fixture delays a genuine anonymous response while its
  previous bitmap exists. Its same-URL red-to-green image must still be inspected
  as the actual reloaded green image; denied pixels remain hidden/unknown.
- The actual current bundle was retested on the supplied Getty page in a
  separate visual-only profile: all 14 tested images received face masks,
  including the previous misses; protection remained after repeat scans/cache
  expiry, while an ordinary no-face control stayed visible. No personal profile
  settings, accounts, native host or actual phone installation were used.
- Shared Safari staging/controller checks use the same canonical sources. This
  is not physical iPhone/iPad performance verification.

Packaged engine SHA-256:
`b282533fa8fa974c0d6224c12542b06845a663c7921f0a3612d952f4c236db38`.
Model weight hashes remain unchanged; rebuilding used the pinned offline assets.

## Remaining limits

Models still miss or misclassify some faces; appearance estimates do not identify
actual gender. No complete adult-content, whole-body, every-frame video or
unremovable-extension guarantee is made. First-time detection and inaccessible
image servers can still delay or prevent a verified display. Unknown results
remain withheld. No raw-frame cache, external model service, smaller input
resolution or removed accuracy safeguard was introduced.
