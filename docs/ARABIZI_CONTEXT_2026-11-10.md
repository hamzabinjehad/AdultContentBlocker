# Reviewed Arabizi context in the shared scorer

Date: 10 November 2026.

## Problem

The shared extension/Safari scorer treated every signed `ar-latn` term as
positive evidence. On a short page that made the generated name phrases
`neek masri`, `aflam nik` and the Indonesian homograph `sksy` block on their
own. The Android policy already kept those contextual; the extension and the
iOS Safari web extension, which use the same `extension/lib/score.js`, did not.
Measured before this change:

| Text | Extension verdict |
|---|---|
| `Watch aflam neek masri free` | block (correct) |
| `Watch Neek Masri live, free` | **block (false positive)** |
| `tahmil aflam Nik Cage mojani` | **block (false positive)** |
| `sksy download free` | **block (false positive)** |

## Decision

The scored term is skipped unless its **zone** also contains a clear reviewed
Arabizi term. The reviewed exceptions are:

- **clear:** `aflam neek`, `film neek`, and the existing unambiguous Arabizi
  spellings (`sharmota`, `kahba`, `3ahra`, ...);
- **contextual:** every term containing `neek`, `nik`, `niek` or `neik`, plus
  `sksy`/`sksi` and their spellings.

A phrase such as `aflam neek masri` contains the clear `aflam neek`, so it
blocks. `Watch Neek Masri live, free` has no clear term and stays readable.
`sksy sharmota download` blocks through `sharmota`; `sksy download free` does
not. This mirrors the Android reviewed-phrase decision and does not change the
signed seed or any custom rule.

## Verification

| Check | Result |
|---|---|
| `arabizi.test.js` | 7/7: three miss rows block, `Neek Masri`/`Nik Cage`/`sksy download` stay readable, `sksy sharmota` blocks |
| Extension suite | Pass, including `score-equivalence` with the same contextual rule in its independent reference |
| `ios/test_web_extension.py` and `test_safari_only.py` | Pass; the iOS Safari extension stages the same shared scorer |

## Limits

The decision is term-based, not a semantic name detector. It deliberately
does not close every `sksy` variation: `sksy` alone remains allowed because it
is Indonesian "SKS". No adult-positive corpus was run; this record covers the
named false positives and the three reviewed misses.
