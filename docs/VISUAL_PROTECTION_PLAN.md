# Visual protection and independent layers

Status: experimental opt-in implementation and remaining acceptance plan,
2026-10-11. The repository now includes a bundled local visual engine, bounded
image/video sampling, optional face-based media blurring, and an adaptive
protection-layer interface. Visual checks and person blurring are off by default.
The browser's baseline rules still work without the native app. Updating this
source does not install the extension, upgrade an installed app, or publish a
signed release.

## Product contract

Adult-content filtering is the default purpose. Optional personal modesty
preferences are separate: a predicted woman/man is not a pornography verdict.
Keep normal videos available, as the user requested. Never equate a generic
platform "sensitive content" notice with adult content; it can describe other
material. Explain that classification can miss content and make mistakes.

The extension works independently and gains reinforcement when Hisn is
connected. Losing one layer must not turn off the others. Chromium visual
preferences are browser-owned; the app heartbeat does not overwrite them.
During an app-managed commitment, the extension permits strengthening selected
visual preferences but rejects weakening them. This does not make browser
storage or an ordinary extension unremovable. Safari visual preferences are
separate from the native app's commitment and are not locked by it. No hidden
browser-management policy, account demotion, router changes, surveillance, TLS
interception or OS-security disablement is part of this implementation.

## Reuse decision

[HaramBlur](https://github.com/alganzory/HaramBlur) uses Human and NSFWJS for
face-based preferences and adult-media detection. Its repository is
[AGPL-3.0](https://github.com/alganzory/HaramBlur/blob/main/LICENSE), not a
license-free source of snippets. It also offers immediate disable/unblur
controls, which are unsuitable as Hisn commitment recovery controls.

Hisn uses an original integration of
[NSFWJS](https://github.com/infinitered/nsfwjs), whose library code has an
[MIT license](https://github.com/infinitered/nsfwjs/blob/master/LICENSE), and
[Human](https://github.com/vladmandic/human) for the separately chosen face-based
preference. No HaramBlur code was copied. Third-party libraries and model
weights are now bundled, with pinned package versions, model-source revision,
recorded URLs, byte counts, SHA-256 hashes and license notices in
[`extension/visual/`](../extension/visual/).

NSFWJS MobileNetV2 weights are embedded from the pinned package. The face and
gender models are packaged locally; the gender label is an uncertain model
estimate, not a person's identity. The build downloads required assets, while
runtime loads only packaged code/models. The artifact inventory and packaging
checks detect drift; they are not a separately signed model-update manifest
or proof of classification accuracy. Each model's own notices must stay with
redistributed packages.

## Implemented experimental path

- The privileged broker accepts bounded pixel captures from permitted extension
  contexts, not arbitrary page-supplied fetch URLs. Chromium runs the local
  engine in an extension offscreen document; Safari uses its nonpersistent
  background page. No camera, screen-recording or identity-recognition
  permission is requested. The extension does not upload or persist captures,
  face descriptors or browsing URLs for this feature.
- Visible images and accessible sampled video frames are downscaled and queued
  with bounded work. Route/source changes, removed elements and cancelled
  requests discard stale results. This is periodic inspection, not analysis of
  every frame before display; content can appear before a verdict.
- An adult verdict hides the matching media and pauses a flagged video. The
  optional face preference instead blurs the whole matching image/video. It
  currently recognizes visible faces, not full bodies or every person. A
  predicted female/male face is not an adult-content verdict.
- Settings and the layer overview distinguish waiting/loading, ready, disabled,
  unsupported and failure states. Browser
  [cross-origin canvas restrictions](https://developer.mozilla.org/en-US/docs/Web/HTML/How_to/CORS_enabled_image)
  and protected media can prevent inspection. Missing results remain unknown,
  not clean. Normal videos remain available; there is no silent hide-all-video
  fallback and no immediate reveal control for a confirmed adult verdict.
- The signed domain/text seed remains a separate resource set. Visual asset
  hashes and library licenses do not replace signed-seed verification.

The packaged engine currently targets Chrome 137+ and Safari 18. The native
iPhone/iPad app's lower deployment target is not evidence that every earlier
Safari version can run the visual engine. Unsupported visual execution must
remain visible while independent text/domain layers continue.

## Verification so far and release gates

An isolated Helium test exercised the real packaged visual engine on a benign
image and ordinary synthetic video; ordinary video remained usable. Unit and
lifecycle fixtures cover decisions, queue limits, cancellation and stale
responses. The real all-faces preference also blurred a benign public-domain
astronaut image. On that sample, the female/male model's highest confidence
remained below the selected threshold, so those specific preferences did not
blur the detected face. This is a known missed match, not a passing targeted
test. These checks are not an adult-detection or face-estimation accuracy
benchmark; enabling a preference does not prove its intended coverage.

The Safari adapter has package/message-contract checks and a successful
unsigned simulator-target build, not a physical Safari execution record.
Signed real iPhone/iPad activation, permissions, actual model operation,
performance and battery use still need verification.

Before enabling visual checks by default or making coverage claims, validate
benign medical, educational, family, art and ordinary video cases; assess adult
detection using an appropriately obtained and handled benchmark separately.
Measure false positives, misses, latency, CPU, memory and battery. Add positive
face-blurring tests for each offered preference and representative skin tones,
poses and lighting. Mock scores, a loaded model or a successful build are not
substitutes for these tests.

## Independent enforcement

- Browser: bundled domain/URL rules remain enabled when the app is absent;
  local text scanning follows its own chosen policy. Tests must call the actual
  worker, not merely repeat its intended rule choices in a test helper.
- Mac: exact-process native check-ins and the recovery guard reinforce browser
  operation. Properly signed, active network filtering is a separate layer,
  not something a healthy bridge or unsigned test build proves.
- iPhone/iPad: Hisn Text packages the canonical scanner and local engine where
  supported. Safari extension activation, website access and visual preferences
  are separate from the app's Screen Time shields and four domain blockers.
  Test permissions/model support on physical devices. The Mac process
  termination guard is not available on iOS, and settings do not synchronize
  across devices.
- Network: optional administrator-approved router DNS protects devices using
  that resolver. It cannot distinguish individual encrypted X posts, and it
  does not cover a phone using cellular or another network.
- Commitment: lock the selected policy against in-app weakening while retaining
  documented recovery. Browser-only commitments, if added, need honest warnings
  that deleting the extension can remove their local enforcement.

## Removal resistance

An ordinary extension cannot prevent its own removal. A consented managed
installation may prevent ordinary removal/disabling while its effective policy
is applied. Use a real published extension ID/update source and verify policies
and behavior in the actual browser; writing a profile is not proof of Helium
support. See [managed deployment](CHROME_ENFORCEMENT.md).

Never silently force-install policies or block essential recovery. Device
administrators and owners retain recovery powers. Individual iOS Screen Time
authorization does not prevent app deletion and can be revoked;
[Apple documents the distinction](https://developer.apple.com/videos/play/wwdc2022/110336/).
Supervised/family modes are separate opt-in setups, not automatic adult-account
conversion. Removing Hisn must not instruct independent DNS/managed layers to
disable themselves; neither can those layers be called permanently unremovable.

## Protection-layer interface

The popup and options now use the selected protection-layer layout with
Arabic/English, RTL, reused bundled assets and accessible controls. Dark/light
colors follow the browser's reported color preference; this is not a claim to
read every custom browser theme. The primary popup explains browser-only
operation and displays separate layer states, not one universal green
"protected everywhere" label. Missing app connection is an optional
reinforcement prompt, not a failure of independent baseline filtering.
Private-window and site-permission gaps remain visible.

Settings distinguish adult-content detection from optional person blurring
(both off by default), explain whole-media blurring and face-only limits, and
save choices automatically. Model readiness is reported for the selected
features rather than inferred from an unrelated loaded model. No fake
percentages, invented block counts or one-click policy weakening during an
app-managed commitment. Safari's options explicitly disclose that its visual
settings are not covered by that commitment. UI screenshots and design mockups
are not coverage evidence.

## Acceptance gates

Require actual normal-profile Helium connection and known-safe playback; then
test separately prepared disconnect/reconnect scenarios, restart/sleep,
concurrent browser processes, multiple profiles and private-window permissions.
Add feed/search/detail/quoted-post/media-viewer fixtures before broad X coverage
claims. Keep signed-device, live-feed, visual-model and tamper-resistance
acceptance records separate. Do not use real adult searches or personal history
for routine GUI smoke tests.
