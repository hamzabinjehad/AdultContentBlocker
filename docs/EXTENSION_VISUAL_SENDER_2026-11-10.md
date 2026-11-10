# Extension visual layer: the offscreen sender gate

10 November 2026. A bug fix, not a feature.

## Symptom

The visual layer and the adult-media check could appear enabled while producing
no verdicts in Chrome: no hidden images, no blocked video, no model status.
The engine, models and offscreen document could all be present.

## Cause

The offscreen document accepts only the extension's own service worker. Its
guard was:

```js
sender.id !== chrome.runtime.id || sender.tab
  || sender.url !== chrome.runtime.getURL('background.js')
```

Chromium leaves `sender.url` **undefined** when the sender is a service worker
([w3c/webextensions#851](https://github.com/w3c/webextensions/issues/851));
Firefox sets the worker's script URL. The third condition therefore rejected
every prepare/inspect/status message from Hisn's worker in Chrome, so the
offscreen engine never ran and the content script eventually reported an
unknown or failed layer.

## Fix

[visual/offscreen-sender.js](../extension/visual/offscreen-sender.js) accepts
the worker in both forms:

- `sender.url === undefined` (Chromium service worker), or
- `sender.url === chrome-extension://<id>/background.js` (Firefox).

It still rejects a content script (`sender.tab`), a popup/options page (its
URL), another extension id and a missing sender. The permission set is
unchanged.

## Do not load the model on worker start

Removing the sender rejection made the visual engine actually run, which exposed
a second problem: `syncVisualScanning` prepared the model on every worker start.
That eager load delayed the text policy path on pages with no media and made a
trusted settings update slow enough to miss its window.

The worker now registers the visual scanner without preparing models. The first
capture creates the offscreen document and prepares only the selected model;
the pre-display gate still holds the page's media until its verdict. This keeps
the text and visual layers independent in cost as well as code.

## Verification

| Check | Result |
| --- | --- |
| `offscreen-sender.test.js` | 8/8: Chrome worker, Firefox worker, popup, content script, other extension, missing sender |
| `./test.sh extension` | Pass; all JavaScript suites, package check and evaluation corpus |
| `scan-live.sh` | Pass; the policy recheck and both X/Twitter feeds pass after the eager load is removed |
| `./test.sh extension` | Pass; all JavaScript suites, package check and evaluation corpus |
| `./test.sh browser` | Pass; the existing real-browser harnesses |

No real adult image was used; this proves message delivery, not classifier
accuracy. A person who saved visual checks off before 1.5.3 keeps them off by
design and can turn them on in Settings.
