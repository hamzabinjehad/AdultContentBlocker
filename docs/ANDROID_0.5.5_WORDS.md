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
label, when every one of its words is a lone explicit term ("Porn", "Porn & hentai",
"سكس اباحي"), and no label on the same screen holds protective context (see
"Review follow-up" below):

- English: porn, hentai, blowjob, cumshot, creampie, gangbang, handjob,
  including the spellings the policy already recovers (p0rn, p.o.r.n, h e n t a i,
  Cyrillic look-alikes).
- Arabic: the bare words سكس, اباحي and اباحيه (diacritics and hamza folded).
- Franco-Arabic: the reviewed spellings of "pornographic" already trusted alone
  (aba7i, abahi, ebahia and the rest of `ARABIZI_EXPLICIT`).

The term must be configured in the seed with strong weight (8), and the leaf then
goes through the usual scoring, so negative context still cancels it.

What stays allowed, and why:

- **A term beside any other word**: "Porn addiction", "Anti porn", "Quit porn",
  "Porn ban", "Porn research", "Porn, hentai blocker", "ضد سكس اباحي".
- **A bare term on a protective screen**: a "Porn" tag chip under the headline
  "Study links porn addiction to anxiety", or a recovery app's trigger list.
- **"Porno"** alone: the Spanish, Italian and German noun in news, and the title of
  Irvine Welsh's novel. "Porno videos" and "Watch porno" still block.
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

Reproducible fixture counts, not population accuracy. The first version added 68 rows: 22 adult
lone-term rows (4 Franco-Arabic) and 46 harmless ones (8 Franco-Arabic): addiction
and recovery headlines, blocker names, anti-porn campaigns in English and Arabic,
encyclopedic headings, and ambiguous names, places and foods.

| Policy | Expected blocks found | Harmless rows blocked | Arabizi blocks | Arabizi harmless blocked |
| --- | --- | --- | --- | --- |
| 0.5.4 on the 0.5.4 corpus | 43/47 | 0/65 | 18/22 | 0/38 |
| 0.5.4 on the new corpus | 43/69 | 0/111 | 18/26 | 0/46 |
| First version | 65/69 | 0/111 | 22/26 | 0/46 |

The review follow-up moved "Porno" to the harmless rows and added 14 harmless
three-word rows, 4 adult screens and 7 harmless screens (labels separated by `\n`;
the earlier code is scored as `TextGuardService` ran it, blocking when any label
blocks):

| Policy, review-follow-up corpus | Expected blocks found | Harmless rows blocked | Arabizi blocks | Arabizi harmless blocked |
| --- | --- | --- | --- | --- |
| 0.5.4 | 45/72 | 0/133 | 18/26 | 0/49 |
| First version | 68/72 | 21/133 | 22/26 | 2/49 |
| After the review follow-up | 68/72 | 0/133 | 22/26 | 0/49 |

The 4 misses are the existing `arabizi-challenge` rows (neek phrases and "sksy
download free"), unchanged and by design.

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

## Review follow-up

A review of the first version found four issues. All four were confirmed and fixed.

1. **High: two terms and a protective word blocked.** The first rule needed more
   than half of a three-word leaf to be lone terms. Negative context for these words
   is only reviewed two-word phrases ("porn addiction", "porn blocker"), so "Porn,
   hentai blocker", "Stop porn & hentai", "Anti porn hentai", "porn porn recovery",
   "NoFap porn hentai", "ضد سكس اباحي" and "سكس اباحي ادمان" blocked. Now every word
   of the leaf must be a lone term. Any other word may be protective, so it never
   counts as a label.
2. **Medium: the corpus did not cover that shape.** 14 harmless rows with two terms
   and a third word were added (English, Arabic and Franco-Arabic, for example
   "aba7i sks 3ilaj" and "edman aba7i abahi"), and the same strings were added to
   `checkLoneTerms` and the actual-seed fixtures. The corpus test already fails on
   any harmless row that blocks.
3. **Medium: context in a sibling label never protected a bare term.**
   `TextGuardService` judged each label alone, so a "Porn" tag chip next to a
   recovery headline blocked the screen. The new `AdultTextPolicy.blocksAnyLine`
   still judges each label alone, so unrelated medical or news wording never exempts
   an adult promotion elsewhere. It turns off only the bare-term rule when any label
   on the screen has a protective word (anti, quit, blocker, addiction, recovery,
   detox, nofap, therapy, research, awareness, accountability, ضد, ادمان, علاج, حجب,
   حظر, مكافحة, تعافي, إقلاع, أضرار, توعية, edman, 3ilaj, ...) or a configured
   negative phrase (for example "sex education"). Ordinary button words such as
   "stop", "ban" and "block" are left out. `TextGuardService` now calls it. The
   corpus can hold screens: `\n` in a row separates the labels.
4. **Low: a bare "Porno" blocked.** It is no longer a lone term (see above).

## Limits

- A bare term still blocks on a screen with no protective word, for example a
  recovery app whose trigger list screen shows only "Porn". Choose the checked apps
  accordingly.
- A protective word anywhere on a screen also lets a bare term through on an adult
  page that shows it, for example a "Research" tab. A promotion, a media label or a
  custom rule on that screen still blocks.
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
| `sh test-core.sh` | macOS 27.2, JetBrains JBR | Pass. 5,911 submitted and 5,909 canonical seed rules; corpus 68/72 (4 challenge rows) and 0/133 after the review follow-up (65/69 and 0/111 before it) |
| `assembleDebug assembleDebugAndroidTest lintDebug assembleRelease`, `check-release.py` | Same Mac, Gradle 9.3.1, local SDK | `BUILD SUCCESSFUL`, 119 tasks. `lintDebug`: 0 errors, 14 warnings, the same count as 0.5.4. Release check passed (0.5.4, version code 16, not debuggable) |

`origin/main` could not be fetched (no network), so this branch starts from the
local `origin/main` at `baef143`. The review follow-up changed one call in
`TextGuardService` (to `blocksAnyLine`) and the README's adult-text summary. No
strings changed. The checks above were rerun after the follow-up with the same
results (lint 0 errors, 14 warnings; release check passed).
