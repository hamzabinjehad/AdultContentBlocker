# Hisn extension 1.1.11 — focused text hardening

Local development candidate, 17 October 2026. Not a GitHub publication,
native-app installation or confirmation of browser/phone activation.

## Changes

- Compile exact multi-word phrases into a reusable prefix tree. Preserve
  overlapping matches and zone weights while avoiding impossible phrase scans.
- Canonicalise whitespace in pasted custom phrases and ignored-phrase keys.
  Leave the compiler/native Unicode normalisation contract unchanged.
- Recognise a reviewed set of already-listed strong terms disguised with
  selected digits/lookalike letters or punctuation-separated single letters.
  Examples: `p0rn`, Cyrillic/Greek-o variants, `p.o.r.n` and Arabic dotted words.
  This does not apply fuzzy matching to the whole vocabulary or concatenate
  arbitrary words, URLs or whitespace-separated letters.
- Check the effective vocabulary before accepting an alias. Removed/ignored
  terms cannot reappear through aliases, weak terms are not promoted, and
  aliases use the same canonical three-occurrence cap. Existing trailing-digit
  decoration takes precedence, including when an internal letter is disguised.
- Correct one local French form-label ambiguity: the seed `sexe` term followed
  immediately by the reviewed choice pairs does not score that occurrence.
  Other explicit text on the same page/item still scores. A stronger deliberate
  custom rule is not cancelled by this heuristic.
- Run the labelled evaluation corpus at sensitivity 50, 80 and 100 in the
  standard extension suite. No sensitivity threshold was lowered or raised.

The implementation is shared by Chromium and the staged Safari scanner used
on macOS/iPhone/iPad. No native Mac enforcement, network rules, commitment,
browser-management policy or instant-disable/reveal control was changed.
The new logic adds no storage, browsing history, external service or permission.

## Validation scope

Focused checks: 112 evasion/context checks; 36 phrase-scoring checks; 4,699
legacy comparisons/correction assertions. The five old-corpus sensitivity
comparisons for the French form explicitly assert its intended score correction;
they are not claimed as unchanged legacy results.

The expanded synthetic corpus contains 18 adult and 23 benign cases. At
sensitivity 50, 80 and 100 it reports 0 misses and 0 benign blocks. The new
labelled fixtures are also verdict-gated in the unit suite; this is not a
measurement of real-world recall or false-positive rates.

The isolated real-worker Helium scanner test covers disguised text, a French
form after a trusted sensitivity-80 update, explicit content beside a form
label, closed shadow roots, soft hyphens, blocked navigation and custom-policy
changes. X/Twitter fixtures check new body/caption evasions and edits without
redirecting the whole feed or hiding ordinary neighboring posts.

Shared Safari staging tests verify that build resources use the canonical
scorer. They do not prove physical iPhone/iPad activation or performance.

Final verification passed:

- `./test.sh extension`: complete unit suite, clean-package validation and
  corpus gates at sensitivity 50, 80 and 100.
- `CHROME='/Applications/Helium.app/Contents/MacOS/Helium' ./test.sh browser`:
  complete isolated-browser suite, including normal X/video playback, visual
  regression tests, SafeSearch/network rules and the expanded real-worker test.
- `python3 -m unittest discover -s ios -p 'test_web_extension.py'`: 12 tests.
- `python3 -m unittest discover -s script -p 'test_*.py'`: 5 runner tests.
- `git diff --check`: no whitespace errors.

Prepared a fresh `dist/hisn-extension-1.1.11` unpacked folder with the existing
development key preserved. Packaged `lib/score.js` and `manifest.json` match
the canonical sources; the download ZIP passes its compressed-data check.
ZIP SHA-256:
`3819f4d0c084424b8f986ba20b75bf57f482a0470054a1229f427221dcd5dbc4`.

An informational 122,430-character scoring sample varied across runs and
concurrent browser load. It does not establish a faster end-to-end browsing
latency or a universal performance gain from the added evasion checks.

## Remaining work

This is a bounded first implementation of the roadmap, not completion of it.
Whitespace-spelled words, broader context calibration, unrelated negative-text
padding, more site-specific feed adapters and physical Apple-device acceptance
remain. Text checking cannot identify uncaptioned media, inspect every native
app, or make an administrator-owned device impossible to bypass.

For an existing unpacked Chromium installation, reload Hisn in the browser's
extensions page and refresh webpages after updating the files. Safari requires
a rebuilt/signed containing app and user-enabled website access; staging alone
does not install it. Do not remove a working extension or relax an active
commitment to apply an update.
