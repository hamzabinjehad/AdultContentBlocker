# Hisn Android 0.5.4: text checks and privacy

Work package B for 0.5.4, 2026-10-27, branch `android/text-privacy` on top of 0.5.3
(version code 15; the version is set when the 0.5.4 packages are integrated). Built,
linted and tested on a Mac with the local Android SDK. No phone or emulator was
available, so nothing here has run on a device.

## What changed

### Franco-Arabic (Arabizi) adult terms

`AdultPolicyAssets` loaded only the seed's `en` and `ar` terms and dropped all 1,447
`ar-latn` entries: 825 single words and 622 phrases. They now load, tagged as
Arabizi, under the same rules as the other languages: strong evidence needs
promotional wording in the same 16-word window, and an ambiguous term needs a clear
signal beside it. Arabizi needs more care than English or Arabic script, because
most of the seed's single spellings are generated variants that are also names or
everyday words: Dara, Nika, Nashwa, Abhi (Hindi "now"), inek (Turkish "cow"),
97ab (Gulf "friends"), 3ara ("shame"), e7a (an exclamation), moms, jins, tabon.

- **78 reviewed single words are clear**: spellings of whore, porn(ographic),
  prostitution, "fucked" and sexy (سكسي) with no everyday meaning (sharmota, kahba,
  3ahra, aba7i, abahi, ebahia, d3ara, mnyok, sksy, sksi, ...). The other 747 single
  words are contextual. That includes neek, niek and neik, because their Arabic form
  نيك is ambiguous (Nick) and Neek is also a nickname, and sks, which is also a
  rifle and the Indonesian abbreviation for university credits.
- **A seed phrase holding nik or neek is contextual** ("aflam neek", "neek masri",
  "neek hd"), as the Arabic نيك phrases (نيك سعودي) are. "Watch Neek Masri live,
  free" is allowed; beside a clear term ("Watch sharmota aflam neek masri free")
  it blocks.
- **Only the spellings of "pornographic"** are trusted alone, as the Arabic اباحي
  and اباحيه are: all 46 seed spellings with 7, h or e (aba7i, aba7e, abahi, abahy,
  alabahia, ebahia, ibahia and variants). "Watch aba7i mojani" and "download abahi"
  block. Other clear words, such as insults and sksy, need a second strong signal:
  "sksy download free" is not blocked, while تحميل سكس مجانا is.
- **Contextual Arabizi evidence joins a window only beside a clear Arabizi signal.**
  A name in English text cannot add up with "pussy cats", OnlyFans budgeting or
  recovery context.
- **A phrase made only of words English also uses** ("video sex", "sex arab",
  "sour sex") is contextual.
- tahmil, mojani, moshahda and their spellings count as promotion, like download,
  free and watch.

Canonical rules: 4,462 before, 5,909 now.

### Card and button labels

The screen scan read only childless nodes, so a content description on a card or
button layout whose children are images or empty views was never seen. The rule now
lives in core (`LabelScan`), within the same budget of 512 visited nodes and 512
scheduled children:

- A childless node gives its text, else its content description, as before.
- A parent's description is read only when its whole subtree was inspected, gave no
  text, and held no editable, password or EditText node, even an invisible one. An
  invisible node with children is not opened, so it rules out every parent around
  it: it may hide an input whose draft a card label repeats. A subtree cut by the
  budget, or with a child Android could not provide, never yields its parent's
  description.
- An inner card that is read covers every card around it, so nothing is read twice.
- Editable and password nodes and their subtrees are still never read.
- Parent descriptions follow the leaf labels, one line each, in screen order. The
  adult-text check still judges each line on its own.

### Private screens

The domain and word editors, the three app choosers (blocked, checked, scheduled),
the schedule editor and the word tester show the person's own lists. Their dialogs
are now `FLAG_SECURE` windows. While one is open the main window is secured as
well, because some Android versions build the recent-apps preview from the activity
window. The word tester is on the main window: it is secured while the Guide page
is shown and the tester holds text. The flag changes only when that state changes,
not on every keystroke. The Rules page, the Privacy section, the tester and the list
editors explain this in English and Arabic.

### Keyboard on Android 15+

With targetSdk 36, Android 15+ lays the window out edge to edge and no longer
resizes it for the keyboard, so the keyboard could cover the word tester. The main
window now uses an explicit `adjustResize` and pads its bottom by the larger of the
system bars and the keyboard inset. The page shrinks and scrolls the focused field
into view. The rule editors are dialogs, which are floating windows. They are
expected to keep Android's own keyboard handling, but this has not been checked on a
phone yet (see below).

### Strings and lint

- `rules_too_many_apps` and `adult_mode_ready` are plurals in both languages, with
  all six Arabic forms. This clears the last two `PluralsCandidate` warnings.
- The Arabic policy line now says "built-in adult-text", as the English does.
- The adult-text disclosure names Franco-Arabic in both languages.

### Build fix

On `origin/main`, the gray-screen merge placed `refreshGray()` between an `if` and its
`else` in `MainActivity.refreshStatus`, so the app did not compile. The call now
comes after the `if`/`else`, inside the same `try`.

## Synthetic corpus

Reproducible fixture counts, not population accuracy. The 112 rows include 60
Franco-Arabic ones: 22 adult and 38 harmless. The harmless rows cover chat with
digits used as letters, names, words shared with English, Hindi, Turkish, German
and Indonesian, and mixed English. Rows in a family ending in `challenge` are known
misses: they stay in the counts and are printed, but do not fail the test.

| Policy | Expected blocks found | Harmless rows blocked | Arabizi blocks | Arabizi harmless blocked |
| --- | --- | --- | --- | --- |
| `origin/main` (en and ar only) | 25/41 | 0/59 | 0/16 | 0/32 |
| ar-latn loaded with no Arabizi rules | 31/41 | 9/59 | 6/16 | 9/32 |
| First review of this branch | 41/41 | 0/59 | 16/16 | 0/32 |
| Review fixes, old policy on the new corpus | 43/47 | 3/65 | 18/22 | 3/38 |
| Review fixes | 43/47 | 0/65 | 18/22 | 0/38 |

The review found that seed phrases holding neek ("neek masri", "neek saudi",
"neek jadid") were clear and trusted, so "Watch Neek Masri live, free", "Neek Saudi
free download" and "Neek jadid free download" were blocked, while the Arabic forms
were not. It also found that the h-spellings of "pornographic" (abahi, abahy) never
blocked. With the old policy, the 12 new rows give 4 misses ("tahmil aflam abahi",
"download abahi free", "aflam abahy mojani tahmil" and "sksy download free") and
those 3 false blocks.
With the fixes, the 4 misses are challenge rows, all by design and matching Arabic
script: three neek rows with no clear term ("Watch aflam neek masri free", "tahmil
film neek arabi", "tahmil aflam neek 7elwa mojani", earlier counted as blocks) and
"sksy download free".

The 52 older rows stay at 25/25 blocks and 0/27 harmless blocked. Loading the terms
without the Arabizi rules blocked "Watch Dara and Nika play, free entry", "Watch Nik
Masri live, free" and "Watch the video, sex ed for teens", among others.

## Tests added

- `LabelScanTest`: card, button and nested-card cases; input fields at any depth,
  including invisible ones and ones inside an invisible container; budget cuts; missing children; long descriptions; node
  release after an error; a description too long to hold still covers its outer
  card. It also runs 2,400 random trees. Without parent
  descriptions the scan equals the old leaf-only scan, budget cuts and partial flags
  included. Small trees match a direct recursive statement of the rule.
- `AdultTextPolicyTest`: Arabizi unit cases, with a sliding-window reference over
  3,000 random samples. Against the real seed it adds fixtures and 3,000 random chat
  samples built from everyday Arabizi, names, promotion words and the contextual
  single spellings (all 747 except those also used in seed phrases). None of these
  blocks, and adding one clear seed phrase does. Treating every Arabizi word as clear
  fails this test.
- The seed fixture written by `test-core.sh` gains a language column.

## Limits

- Arabizi detection is deliberately conservative, so it misses some things.
  "Watch sks free" is not blocked, because sks is also a rifle and an abbreviation.
  "sksy download free" and "Watch aflam neek masri free" are not blocked either, as
  sksy needs a second strong signal and neek is a name; the Arabic نيك phrases are
  treated the same way.
  Spelling variants outside the reviewed list count only beside a clear term. Real
  chat was not used: every corpus row is synthetic.
- Two clear Arabizi insults beside "watch" ("Ya sharmota ya kahba, watch out") are
  blocked, as the same words in Arabic script are beside شاهد.
- A card whose children carry any text is read only through those children. Text
  set on a parent node itself, and views that apps mark as not important for
  accessibility, are still not read.
- `FLAG_SECURE` keeps these windows out of screenshots, screen recording and the
  recent-apps preview. It does not hide them from anyone who can see the screen,
  from a camera, or from accessibility services. The Protection and Rules pages
  show counts and states, not the lists, and are not secured.

## Verification

| Check | Where | Result |
| --- | --- | --- |
| `sh test-core.sh` | macOS 27.2, JetBrains JBR 25.0.3 | Pass. String parity: 194 strings, 5 plurals, 205 references. 2,400 LabelScan trees; 5,911 submitted and 5,909 canonical seed rules; corpus 43/47 (4 challenge rows) and 0/65 |
| `assembleDebug assembleDebugAndroidTest lintDebug assembleRelease` | Same Mac, Gradle 9.3.1, AGP 9.1.1, local SDK | `BUILD SUCCESSFUL`, 119 tasks. `lintDebug`: 0 errors, 14 warnings. One is lint noting a newer Gradle release. Two are `text_guard_service.xml` attributes ignored below API 30/31. Eleven are in debug-only visual test screens. `PluralsCandidate` went from 2 to 0 |

These APKs were built locally and are not for distribution. Debug
`f9a19f258c22f85573d905545655d06f48e6eac6d8e4559a99f2be775133b0ac`, unsigned release
`e6dd0349c980c7b0e639e22c7dfec5355ed6fff8e86d96abd6e906be30549912`, androidTest
`57e33aa113c4f36bba2831f45cc59941f79ebda11890fafb1ce4aae624688be9`. They change
when the version is set.

## Still needs a phone

- On Android 15 or later, the keyboard leaves the word tester's fields and the
  domain and word editor dialogs visible. On Android 8–14 the page still resizes
  once, without double padding. On Android 11–14 this rests on the window, which is
  not edge to edge there, consuming the system-bar and keyboard insets before the
  page sees them, so the page adds no padding of its own. That is the platform's
  documented behaviour but has not been seen on a device.
- A screenshot and a screen recording of each list dialog, and of the tester holding
  text, come out black or are refused. The recent-apps preview of Hisn is blank
  while one is open, and normal again after it closes or the tester is cleared.
- Card descriptions in a real feed app are read, for example a video card whose only
  label is its content description. Nothing typed in a search field appears in the
  inspection.
- Franco-Arabic: a public page with Arabizi promotion is restricted, and ordinary
  Arabizi chat in a messaging app chosen by the owner is not. Use only the owner's
  own or public content.
- The Arabic plural forms of the policy line and of the 200-app message.
