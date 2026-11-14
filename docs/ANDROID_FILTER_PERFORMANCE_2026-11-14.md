# Android matcher performance, 2026-11-14

Two pieces of `android/core` decide what the phone blocks while it is in use:
the DNS decision (`DnsPolicy` → `HostDecision` → `HostTermPolicy`,
`DomainPolicy`) answers every hostname an app looks up, and the screen-text
scan (`AdultTextPolicy`) judges the labels the accessibility service reads.
This pass measured both, found the text scan allocating memory in proportion to
the vocabulary instead of the text, and removed that and the avoidable work in
the hostname path. No verdict changed.

This is local evidence from a desktop JVM on one Mac. It is not a phone
benchmark, not a battery or thermal measurement, and not a signed build. The
committed benchmark is below so the numbers can be re-taken.

## The measurement

- Mac with Apple M3, macOS 27.2, Android Studio's bundled OpenJDK 25.
- `script/bench_android_core.sh` compiles the **shipping** `android/core`
  sources against `android/bench/CoreBench.java`. It is the real matcher on the
  committed seed (229,034 enforceable domains) and the signed terms (68 token
  and 85 substring host terms; the English, Arabic and Franco-Arabic page
  vocabulary the app loads).
- Each row is thread CPU time and allocated bytes per call, the best of five
  rounds after a full warm-up pass. **Allocation is exact and repeatable. CPU
  time is not:** the Mac was running Android emulators, so absolute times moved
  by 2× between sessions. The table is two back-to-back runs of each build in
  one quiet window; the two runs of each build agreed within 10%.
- "Uncached" rows rebuild the policy each round, so the per-policy decision
  caches start empty: that is the cost of a hostname the phone has not asked
  about since the cache was last cleared. "Before" is the same driver compiled
  against the pre-change sources.

## Results

| Path | Before | After | CPU | Allocation |
|---|---:|---:|---:|---:|
| Screen scan, 120 lines (`blocksAnyLine`) | 238 µs, 4,418 KB | 154 µs, 133 KB | 1.5× | **33×** |
| One UI label (`blocksPromotion`) | 1.5 µs, 36.5 KB | 0.92 µs, 0.8 KB | 1.6× | **46×** |
| DNS, new allowed hostname | 8.8 µs, 4.7 KB | 4.2 µs, 1.6 KB | 2.1× | 3.0× |
| DNS, hostname on the list | 4.2 µs, 2.3 KB | 1.4 µs, 0.6 KB | 3.0× | 4.0× |
| DNS, subdomain of a listed host | 6.7 µs, 4.7 KB | 2.0 µs, 0.9 KB | 3.3× | 5.3× |
| DNS, punycode hostname | 7.2 µs, 11.0 KB | 6.8 µs, 10.4 KB | — | — |
| DNS, repeated hostname (cached) | 0.36 µs, 0.3 KB | 0.35 µs, 0.3 KB | — | — |

The first two rows are the ones that matter. The text scan runs on the
accessibility path while the person scrolls, and every label containing a
call-to-action word ("watch", "free", "download", "تحميل") allocated about
72 KB before any matching happened. On a phone that is garbage-collector work
and jank, not just CPU.

The DNS rows are reported for completeness, not as a claim that DNS feels
faster. An uncached decision was already under 10 µs and the resolver round
trip that follows an allowed answer is milliseconds; the repeated-hostname path,
which is most questions, did not change.

Two rows did not improve, and are reported as such:

- **Punycode hostnames** still take the reference path: IDN decode, the
  normaliser passes, regexes and a per-host set. They are rare per page.
- **Seed load** was not touched. It measured ~340 ms on this JVM under load;
  no before/after pair was taken.

## What changed

1. **The text scan's window counters follow the hits, not the vocabulary**
   (`AdultTextPolicy.judge`, `classifyWindows`). The sliding-window classifier
   kept three `int[entries.size()]` counter arrays — about 6,000 entries, 24 KB
   each — and allocated them for every judged label. Each distinct entry hit in
   a label now gets a small slot number as it is recorded, and the counters are
   sized by that count, which is usually one or two.

2. **A label with no term hit returns before the classifier.** Without a hit no
   window can hold a strong signal, so the verdict is already false.

3. **An ASCII hostname path without regexes or sets**
   (`HostTermPolicy.decideAscii`). The reference path built a `HashSet` of every
   token variant using three compiled regexes per token, then ran 85
   `String.contains` calls. Over ASCII the variants reduce to three forms — the
   token, the token without trailing digits, and the token with each run of
   three or more equal characters cut to one or two — because the Arabic
   affixes cannot occur. The path tests those directly, then scans the host
   once against substring terms grouped by first character.

4. **The `never_keyword` rewrite stays exact by declining.** The reference path
   removes innocent words from the haystack before the substring test, in a
   `HashSet`'s iteration order. The ASCII path does not reproduce that: a host
   holding any never-keyword token (`essex.gov.uk`, `analytics.google.com`) is
   handed to the reference path unchanged. So is every non-ASCII host.

5. **The parent-domain walk does not create a substring per label**
   (`CompactHostSet.containsFrom`, `DomainPolicy.containsParent`). The packed
   seed set hashes and compares each suffix in place. An empty rule set — the
   personal allowlist on every install — costs no walk.

## What this deliberately did NOT do

- **No semantic change to the verdict.** The order (personal block, adult-TLD
  rail, hostname term, published list), the exemptions, the `never_keyword`
  rewrite and every text-scoring rule are unchanged. This pass made nothing
  block that did not block before, and nothing stop blocking.
- **No native code.** A Rust or C matcher behind JNI was considered and
  rejected: the decision is microseconds beside a millisecond resolver round
  trip, the remaining cost was allocation rather than arithmetic, and
  `android/core` is plain Java so that `test-core.sh` can run it without an
  Android toolchain. An NDK build would give that up for no measurable gain.
- **No change to the decision caches.** Android already keeps bounded
  per-policy hostname → verdict maps in memory (8,192 entries, cleared whole
  when full, never persisted or logged). They are neither grown nor removed
  here. The Mac filter deliberately has no such cache; that difference predates
  this pass.
- **No new logging, no persisted key, no network request.**

## How it is checked

- `android/test-core.sh` passes in full, including the shared hostname fixture
  (`host_cases.json`), `AdultTextPolicyTest`, `AdultTextCorpusTest`,
  `LabelScanTest` and the real-seed `DomainSeedTest`.
- New differential check in `HostTermPolicyTest`: 60,000 hostnames generated
  from the signed tokens, substrings and never-keyword words — with trailing
  digits, held keys, glued words, mixed case, trailing dots and several
  innocent words in one host — must get the same verdict from `matches` and
  from the reference path alone (`matchesReference`). The test also requires
  more than 5,000 hosts on each side of the verdict, so it cannot pass on a
  corpus that never blocks.
- A one-off comparison, **not** in the suite: the pre-change and post-change
  `AdultTextPolicy` gave identical `blocks`, `blocksPromotion` and
  `blocksAnyLine` verdicts on 120,000 generated samples built from the signed
  phrases and promotion, protective and evasion filler (55,117 blocked). The
  text change has no reference path left in the tree to compare against, so
  the existing text suites are its standing check.
- `script/bench_android_core.sh` — the measurement above.

Not done: no run on a phone or emulator, no instrumented test, no measurement
under ART (whose allocator and collector differ from HotSpot's), and no check
of the R8-minified release build.

## What a next pass could take

In descending order of value:

1. **Measure on a phone.** The allocation numbers carry over; the CPU times do
   not. A trace of `TextGuardService` during a scroll on a low-end device would
   say whether the text scan is now below the noise or still worth work.
2. **The punycode hostname path.** `HostTermPolicy.variants` builds a set and
   runs regexes per token. Testing the bounded affixes directly would help
   Arabic IDN hosts; the three-language fixture table pins the semantics.
3. **Per-label token allocation in the text scan.** A 120-line screen still
   allocates ~133 KB, mostly the canonical string and its `split` per line.
   Tokenising in place would remove most of it.
4. **Seed load.** It was not profiled here; SHA-256 over 4 MB and one `String`
   per line are the likely costs.
