# Hisn Android 0.5.5: single explicit words

Work package, 2026-10-27, branch `android/single-terms` on top of 0.5.4. The version
is not changed here. Built, linted and tested on a Mac with the local Android SDK.
No phone or emulator was available, so nothing here has run on a device.

## The gap

`AdultTextPolicy.blocks()` judged each visible leaf (one label line) and returned
false unless the leaf held a promotion word (watch, download, free, شاهد, tahmil, ...)
or was an exact two-word media label ("Porn videos", "محتوى إباحي"). A page title,
caption, button or search suggestion that was only a strong explicit term ("porn",
"#hentai", "سكس", "aba7i") was therefore allowed.

## The rule

A leaf of at most three words now counts as its own call to action, like a media
label, when more than half of its words are a lone explicit term:

- English: porn, porno, hentai, blowjob, cumshot, creampie, gangbang, handjob,
  including the spellings the policy already recovers (p0rn, p.o.r.n, h e n t a i,
  Cyrillic look-alikes).
- Arabic: the bare words سكس, اباحي and اباحيه (diacritics and hamza folded).
- Franco-Arabic: the reviewed spellings of "pornographic" already trusted alone
  (aba7i, abahi, ebahia and the rest of `ARABIZI_EXPLICIT`).

The term must be configured in the seed with strong weight (8), and the leaf then
goes through the usual scoring, so negative context still cancels it.

What stays allowed, and why:

- **One term beside another word**: "Porn addiction", "Anti porn", "Quit porn",
  "Porn ban", "Porn research". One word of two is not most of the leaf.
- **Longer leaves**: headlines such as "How porn addiction rewires the brain" or
  "Quitting porn saved my marriage" (more than three words) keep the old rule.
- **Subject names**: "pornography", "pornographic", and the prefixed Arabic nouns
  (الإباحية, السكس). These are how encyclopedia headings, laws and news name the
  subject, so they never block alone.
- **Ambiguous words**: نيك (Nick), بورنو (Borno, Bourne), كسكس and كسكسي (couscous),
  طابون, Nik and Neek. They are in `AMBIGUOUS` and never count.
- **Other strong seed words** that are not explicit in themselves: sex, sexy, nude,
  xxx, pussy, onlyfans, the Arabizi insults (sharmota, kahba), sks and sksy.

## Synthetic corpus

Reproducible fixture counts, not population accuracy. 68 rows were added: 22 adult
lone-term rows (4 Franco-Arabic) and 46 harmless ones (8 Franco-Arabic): addiction
and recovery headlines, blocker names, anti-porn campaigns in English and Arabic,
encyclopedic headings, and ambiguous names, places and foods.

| Policy | Expected blocks found | Harmless rows blocked | Arabizi blocks | Arabizi harmless blocked |
| --- | --- | --- | --- | --- |
| 0.5.4 on the 0.5.4 corpus | 43/47 | 0/65 | 18/22 | 0/38 |
| 0.5.4 on the new corpus | 43/69 | 0/111 | 18/26 | 0/46 |
| This branch | 65/69 | 0/111 | 22/26 | 0/46 |

All 22 new adult rows block. The 4 misses are the existing `arabizi-challenge`
rows (neek phrases and "sksy download free"), unchanged and by design.

## Tests added

- `AdultTextPolicyTest.checkLoneTerms`: lone terms, hashtags, punctuation, leet and
  separated spellings, two explicit words; one term beside another word, four-word
  repeats, "pornography", prefixed Arabic nouns and other strong but non-explicit
  words stay allowed; weak, unconfigured and negated terms never block alone;
  Arabic and Arabizi ambiguous words and insults stay allowed.
- Actual-seed fixtures for the same cases.
- The existing sliding-window and Arabizi chat random tests use samples of five or
  more words, so they are unaffected and still pass.

`WordMatcher` (custom literal rules) is unchanged: a custom rule already matches a
single word anywhere, and built-in context never exempts it.

## Limits

- A lone term blocks wherever the owner turned on the adult-text check, including a
  recovery app that lists "Porn" as a trigger. Choose the checked apps accordingly.
- A three-word leaf with one term ("Porn star Daniels") or a two-word leaf such as
  "Hentai manga" is not blocked.
- Unchanged from 0.5.4 and outside this package: a short headline with a promotion
  word can still block, for example "Free yourself from porn", "Porn-free living"
  ("free" is a promotion word) and "Download our free porn recovery guide". Fixing
  these needs new context rules and a look at "Watch porn free", which they would
  also cancel.

## Verification

| Check | Where | Result |
| --- | --- | --- |
| `sh test-core.sh` | macOS 27.2, JetBrains JBR | Pass. 5,911 submitted and 5,909 canonical seed rules; corpus 65/69 (4 challenge rows) and 0/111 |
| `assembleDebug assembleDebugAndroidTest lintDebug assembleRelease`, `check-release.py` | Same Mac, Gradle 9.3.1, local SDK | `BUILD SUCCESSFUL`, 119 tasks. `lintDebug`: 0 errors, 14 warnings, the same count as 0.5.4. Release check passed (0.5.4, version code 16, not debuggable) |

`origin/main` could not be fetched (no network), so this branch starts from the
local `origin/main` at `baef143`. No app code, strings or README changed. The
README's adult-text summary does not yet mention lone terms.
