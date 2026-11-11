# Android 1.5.3: a matched person can go back

Development build in the repository, 8 November 2026, version 1.5.3,
versionCode 41. Signed Play and direct artifacts were built and verified. No
1.5.3 phone adult test is claimed. The adult classifier, thresholds, capture
cadence and after-block pause are unchanged.

## Why

The 1.5.0–1.5.2 people cover was a full-screen, touch-blocking response. It
protected the screen but forced the person to leave the app even when a browser
could simply return to the page they were on before the matching person. The
requested behavior was: when a person who is not allowed is seen, go back to the
last website.

## What changed

- **Rules › Built-in checks › When a person is detected** now offers:
  - **Go back to the last page** — default.
  - **Cover the whole screen** — the previous behavior.
- On a matched face, the guard hides any cover and performs **one Android Back**
  (`GLOBAL_ACTION_BACK`), at most once per **1.2 s**. In a browser that returns
  to the previous page in its history. If the previous page also contains a
  matching face, that page is handled on its own sample, so the person is not
  sent back several pages in one burst.
- If Android refuses Back, the guard falls back to the full cover rather than
  leaving the matching person visible.
- Adult content still takes precedence and closes the detected app.
  Protected/Incognito windows still use the 1.5.1 protected cover because their
  pixels cannot be inspected.
- The 1.5.2 DNS self-start/accessibility re-arm, 1.5.0 people modes, native
  adult TFLite path, 350 ms moving cadence, verdict cache and after-block pause
  are retained. No threshold, permission, stored rule or consent text changed.

## Verification

| Check | Result |
| --- | --- |
| API 36 emulator face fixture, Go back | **PASS** — after a matched face, the previous normal fixture page was foreground; no cover remained |
| People-off visual suite | **PASS** — adult path, input pause, protected cover, intervention, hold and opt-out |
| Core suites | **PASS** |
| Strings / notices | **PASS** — 583 strings, 16 plurals, 577 references |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | **PASS**, 0 errors |
| Phone 1.5.3 update | **PASS** — version 41 installed, data preserved, DNS auto-started and the accessibility service was bound. The camera-free Back smoke test ran on the emulator; the phone was dozing before a phone Back test. |
| `check-release.py`, `apksigner` | **PASS** for both APKs; certificate `2F:BE:D6:…:70:C5` |

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `direct/hisn-1.5.3-direct.apk` | 31,290,641 | `e138f39d94965f9c116e1d2ed9cd893cb978a4a536ba604b242b6d28d00a7d14` |
| `play/hisn-1.5.3-play.apk` | 31,290,633 | `a34d4d4e5b4f3175d2788ad0986a8e0b18dfdb50ddbc462c2cf09dc3509243aa` |
| `play/hisn-1.5.3-play.aab` | 16,893,066 | `a7c50bfc4174021b3c01f39d896570bf161304ae368037e4ddb942cdc1ae1868` |

## Limits

- **Back is history, not a stored “last allowed website”.** If the browser has
  no previous page, Back can close the tab or leave the browser; Hisn does not
  keep a list of URLs or an allowed-site history. Going back far enough can
  return to a page that also contains a matching face, which will be handled
  again.
- **Apps that do not use Back for navigation** may ignore it; the guard then
  covers. Back is a global Android action, not a browser API.
- **Cover still exists** and is the safer choice when the person should not see
  the page at all.
- **Protected/Incognito pixels remain uninspectable.** The protected cover is
  unchanged; actual Incognito removal needs a managed Chrome policy or removing
  Chrome.
- No adult-positive corpus, phone adult detection, battery, thermal or
  long-run reliability measurement; no comparison with HaramBlur.

## Reproduce

The response is `PersonBlur.response`; the UI is `MainActivity.editPersonResponse`;
the Back is in `VisualGuard.decided`. The emulator fixture sequence used the
`visual-face` page and the previous `visual-normal` page in the isolated debug
package.
