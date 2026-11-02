# Hisn extension 1.3.0 — language-aware page-text checking

Released 2 November 2026 as a development candidate. Unpacked and store-ready;
no Chrome Web Store listing, assigned store ID or managed force-install is
claimed.

## Why

The signed vocabulary has carried a language tag on every reviewed term since
seed v5, but the scorer ignored it: all ~5,800 terms were applied to every
page. That is safe, and it is also noisy. A weak German or Spanish word could
add evidence to an English page that never meant it, and there was no way to
show a person which language the product thought they were reading.

A language filter is only worth having if it cannot be used as a bypass. The
obvious design — "match only the declared language" — fails that test: a page
owns its own `<html lang>` attribute, so an Arabic page could set `lang="en"`
and remove the entire Arabic vocabulary with one attribute. 1.3.0 therefore
uses three rules together.

## What changed

### 1. The vocabulary knows its language

`buildIndex()` reads the seed's `l` tag for every term and keeps a
`termLanguage` map alongside the positive/negative maps. `ar-latn` (Arabizi)
folds into `ar`. A spelling that occurs in more than one language is marked
`shared` rather than being owned by whichever line compiled first. User-typed
words keep the `user` tag and are always present.

`indexForLanguage()` builds a cached view containing:

| Included | Why |
| --- | --- |
| The detected language's terms | The page's own reviewed vocabulary |
| English | The common base; most pages mix in English labels and UI |
| `shared` terms | No reliable single-language owner |
| User-typed words | A deliberate choice by the person using the product |
| Terms with weight ≥ 7 | Explicit labels travel across languages |
| Every negative/context phrase | Protective context must never disappear |

Weak terms from unrelated languages are omitted. A null or unsupported
language returns the complete index, so detection can only narrow the
vocabulary when it is confident, never make a page unscorable.

### 2. The page's text outranks its tag

`extension/lib/language.js` counts Arabic and Latin letters in a bounded,
rendered sample. When Arabic is clearly dominant (at least 12 Arabic letters
and more than 1.2× the Latin count), the page is treated as Arabic even if it
declares another language. This closes the `lang="en"` bypass without
introducing a second classifier.

A declared Arabic page with mostly Latin text stays Arabic, so Arabizi terms
are still considered. Unsupported declarations (`nl`, `ru`, …) and pages with
no usable evidence produce `null` and the full list.

### 3. Every page and feed item carries its own signals

- `content/scan.js` collects `htmlLang`, `meta content-language`,
  `og:locale`, the browser language and a bounded head/tail sample of the
  rendered text. Language fields are part of the content signature, so a
  language change re-scores the page.
- `content/feed.js` prefers a post's own `lang` attribute over the page's
  interface language, adds a bounded sample of the post's body and captions,
  and includes the tag in the per-item equality signature.
- `worker/inspection.js` bounds every field again at the message boundary and
  scores the feed item through the same detector as a whole page.

### 4. The person can see the decision

- The toolbar popup gains a compact **Page language** row, shown only while
  the text layer is active. It displays the detected language in the UI's
  language, or "Not identified" when the full list is being used.
- Content settings explain that detection reads the text as well as the tag,
  name the seven dedicated lists, and state the full-list fallback.
- All new copy is translated into Arabic. The language switch re-renders the
  row immediately.

## Files

| File | Change |
| --- | --- |
| `extension/lib/language.js` | New: tag normalisation, names, script counts, declared-language order, detector |
| `extension/lib/score.js` | Per-term language, `indexForLanguage()` views, language-aware `scorePage()` |
| `extension/content/scan.js` | Language tags/sample in zones and signature; popup-only signal read |
| `extension/content/feed.js` | Per-post language tags/sample and signature inclusion |
| `extension/worker/inspection.js` | Bounded language fields for feed scoring |
| `extension/popup.html`, `popup.js`, `ui.css` | Detected-language row and explanation |
| `extension/options.html`, `lib/locale/en.js`, `lib/locale/ar.js` | Language settings copy, English and Arabic |
| `extension/eval/run.js` | Scores each corpus case in its declared language |
| `extension/test/language.test.js` | New 68-check suite |
| `extension/test/feed.test.js` | Per-post language-signal checks |
| `extension/test/browser/visual-real-engine.html` | Four synthetic fixtures instead of one |
| `ios/prepare_web_extension.py`, `ios/HisnWebExtension/options.*` | Stages `lib/language.js` for the shared Safari web-extension scorer; text-check copy says language-aware |

## Verification

- `./test.sh extension` — **PASS**: unpacked-load checks, every JavaScript
  suite, the packaged-zip check, and the evaluation corpus at sensitivity
  50, 80 and 100.
- Evaluation at 50: **0/22** adult cases missed, **0/28** benign pages
  blocked. Dilution holds through 8,000 filler words; every adult tube body
  stays blocked under a benign title and meta.
- `test/language.test.js` — **68/68**. Covers regional tags, `ar-latn`, script
  counting, declaration order, the declared-`en` bypass, view membership,
  strong cross-language labels, negatives, caching, the unknown-language
  fallback, ignored words and the shipped seed's language values.
- `feed.test.js` — **94/94**. Adds a post whose own `lang` outranks the page,
  a bounded sample, the page fallback, and signature invalidation on a
  language change.
- `./test.sh browser` — **PASS**: scanner harness, DNR 42/42, live scanner,
  six block pages, settings and popup in English and Arabic, feed
  compatibility, and the real packaged visual engine on CPU.
- The real-engine visual smoke now runs flat green, flat blue, a soft gradient
  and a checkerboard through adult classification, plus the three person
  preferences on a flat capture. All four synthetic images return a ready,
  inspected, non-adult verdict; no face is invented; every model request stays
  on loopback.

Package produced by `extension/package.sh`:

| Field | Value |
| --- | --- |
| Version | 1.3.0 |
| Path | `dist/hisn-extension-1.3.0.zip` |
| Files | 91 |
| Size | 6.2 MB |
| SHA-256 | `a8471ab3a3e4db4f61656507c933ec51981264162f2c8d593bd63d33694abcac` |

`python3 extension/check_package.py` passes: no test, key or build file ships,
every manifest asset is present, and the seed/rules bytes match the repository.

## Limits

- Detection is a heuristic over a bounded sample, not a language classifier.
  Mixed-language pages may select either language's view; strong labels,
  English and shared terms still apply.
- Terms below weight 7 in a language the detector did not select are not used.
  The fallback exists for uncertainty; it cannot cover a page that confidently
  looks like one language while hiding weak terms from another.
- Text hidden from rendering, canvas text, images and videos without readable
  text are outside this layer, as before. The visual layer remains optional
  and explicitly limited.
- No new network request, permission, durable storage field or browsing record
  was added. The language sample is the same rendered text the worker already
  scores; it is never written to disk or sent off the device.
- Store publication, a real store ID, managed force-install and a clean-profile
  acceptance run remain owner actions recorded in
  [EXTENSION_STORE.md](EXTENSION_STORE.md).

## Reproduction

```bash
./test.sh extension
./test.sh browser
extension/package.sh
python3 extension/check_package.py dist/hisn-extension-1.3.0.zip
```
