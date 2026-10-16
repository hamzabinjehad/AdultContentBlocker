# Extension source comparison — 2026-10-15

Scope: study similar extensions and independently implement useful patterns in
Hisn. This is not a merger of another extension, an accuracy comparison, or a
claim that another project's model/assets can be redistributed unchanged.

## Primary source review

| Project and pinned revision | Source pattern | Hisn decision |
| --- | --- | --- |
| NSFW Filter `af7a8f7d46ea1c122300f2eedf5db17783298e38` | Successful predictions are cached; failed predictions are not. The queue uses a bounded cache. | Implement short-lived, bounded, exact-capture memoization instead of trusting a URL to keep serving the same pixels. |
| HaramBlur `f47000ef21d2c067e0c0290e96c14968b56d459c` | Separate image loading from single-model detection; advance the queue when a job finishes. | Retain Hisn's existing browser-yielding, bounded sequential pipeline. Do not add a one-second wait per image or unbounded simultaneous model work. |
| LeechBlock NG Chrome `e88ef65427ade4f7b14703325500c0ede975ca1b` | Toolbar badge and accessible action title communicate status without opening settings. | Show generic enforcement warnings, not browsing history, blocked URLs, or page text. Clear a repair warning only after current configuration readback succeeds. |

Source files:

- [NSFW Filter prediction success/failure handling](https://github.com/nsfw-filter/nsfw-filter/blob/af7a8f7d46ea1c122300f2eedf5db17783298e38/src/background/Queue/PredictionQueue.ts#L51-L77),
  [queue cache](https://github.com/nsfw-filter/nsfw-filter/blob/af7a8f7d46ea1c122300f2eedf5db17783298e38/src/background/Queue/QueueBase.ts#L25-L39),
  [LRU implementation](https://github.com/nsfw-filter/nsfw-filter/blob/af7a8f7d46ea1c122300f2eedf5db17783298e38/src/background/LRUCache.ts#L12-L30).
- [HaramBlur queues](https://github.com/alganzory/HaramBlur/blob/f47000ef21d2c067e0c0290e96c14968b56d459c/src/modules/queues.js).
- [LeechBlock toolbar status](https://github.com/proginosko/LeechBlockNG-chrome/blob/e88ef65427ade4f7b14703325500c0ede975ca1b/background.js#L1046),
  [local diagnostics](https://github.com/proginosko/LeechBlockNG-chrome/blob/e88ef65427ade4f7b14703325500c0ede975ca1b/diagnostics.js#L68).

## Adaptation constraints

Cache reuse must depend on the actual capture and current protection policy,
not a media URL, caption, elapsed time, or presumed person identity. No raw
frames or browsing URLs are retained in the cache or written to storage.
Unknown, failed and incomplete clean verdicts must not become cached safe
results. Cancellation, policy changes, expiry and engine retirement must not
allow a late result to repopulate a cleared cache.

New-source, seek and policy-change gates remain independent of caching. A cache
hit is an optimization for identical model inputs, not permission to skip
capture or to reveal unreadable pixels. Adult-source latches remain enforced.

The toolbar indicates verified configuration state, not whether every page,
image, video frame, browser profile or other application has been inspected.
Browser repair reasserts saved rules and scanner registrations independently
of the native app. It does not reset commitments, transfer app-owned settings,
save unsaved drafts, or claim to fix DRM/CORS access limitations.

## License and privacy boundary

The reviewed projects identify [NSFW Filter as GPL-3.0](https://github.com/nsfw-filter/nsfw-filter/blob/af7a8f7d46ea1c122300f2eedf5db17783298e38/LICENSE),
[HaramBlur as AGPL-3.0](https://github.com/alganzory/HaramBlur/blob/f47000ef21d2c067e0c0290e96c14968b56d459c/LICENSE),
and [LeechBlock as MPL-2.0](https://github.com/proginosko/LeechBlockNG-chrome/blob/e88ef65427ade4f7b14703325500c0ede975ca1b/LICENSE).
No source files, model assets, packages or executable code from these projects
were copied into Hisn or run during this comparison. Hisn's changes are its own
implementation of the described general patterns. Direct incorporation of
third-party code/assets would require a separate license/provenance review.

LeechBlock's diagnostic URL/referrer reporting is not adopted. No history log,
remote classification API, new browser permission or media upload is added.

## Verification boundary

Deterministic tests and isolated browser fixtures verify cache identity,
invalidation, boundedness, cancellation, repair and UI behavior. Simulated model
replies can establish fewer repeated inference calls for duplicate captures;
they cannot establish millisecond real-model latency or better classification
accuracy. Record completed validation in `RELEASE_VALIDATION.md`.
