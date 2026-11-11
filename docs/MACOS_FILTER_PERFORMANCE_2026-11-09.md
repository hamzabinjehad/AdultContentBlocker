# Mac filter performance, 2026-11-09

The socket filter answers `handleNewFlow` for **every new connection the Mac
opens** — browsers, Electron apps, `curl`, software updates. That verdict is on
the connection path, so its cost is paid by every page load, not by a background
task. This pass measured it, found the keyword layer and the canonicalisation
step dominated, and removed most of that work.

This is local, unsigned evidence on one Mac. It is not a signed build, not a
device acceptance record, and not a claim about power draw. The methodology and
the committed benchmark are below so the numbers can be re-taken.

## The measurement

- Mac with Apple M3, macOS 27.2, Swift 6.4, `swiftc -O`.
- `script/bench_macos_blocklist.sh` compiles the **shipping** sources
  (`BlocklistStore`, the generated `WebProtectionPolicy`, `AdultTldGuard`,
  `TextNormalizer`, `Punycode`) against a small driver. It is the real matcher.
- The workload is 8,031 flows: repeated ordinary hosts (what a browser actually
  opens), 1,500 real blocked hosts sampled from the list plus two subdomains of
  each, the keyword-layer shapes, and deep parent walks. Five runs each, median.
- `seed` is the committed 236,586-domain core tier. `full` is the local
  `dist/` generation: 5,280,200 domains, 129 MB, the same files the updater
  installs.
- Per-flow numbers are `shouldBlockFlow(host:sourceAppIdentifier:)`, the
  function the extension calls. They exclude the private-app-identity callback,
  which is only consulted when the person has blocked an app.

The "before" binary was built from the same benchmark driver against the
committed (pre-change) matcher, so the comparison is the same workload on the
same machine, not a different harness.

## Results — per flow

| Path | Before | After | Speed-up |
|---|---:|---:|---:|
| Mixed workload (seed list) | 48,480 ns | 1,052 ns | **46×** |
| Allowed host, list + keyword miss | 75,795 ns | 1,076 ns | **70×** |
| Blocked by the published list | 18,684 ns | 739 ns | **25×** |
| Subdomain of a blocked host | 24,152 ns | 927 ns | **26×** |
| Keyword layer, ASCII host | 70,500 ns | 1,208 ns | **58×** |
| Keyword layer, Unicode path | 79,354 ns | 32,354 ns | 2.5× |
| Strict mode, allowed | 12,271 ns | 625 ns | **20×** |
| Strict mode, denied | 13,604 ns | 729 ns | **19×** |
| Mixed workload (full list) | 49,509 ns | 1,123 ns | **44×** |
| Allowed host (full list) | 74,769 ns | 1,078 ns | **69×** |
| Blocked by list (full list) | 20,355 ns | 759 ns | **27×** |

The ordinary allowed flow is the one that matters: it is the overwhelming
majority of traffic, and it went from ~76 µs to ~1.1 µs. A page that opens 100
connections cost ~7.6 ms of filter CPU before and costs ~0.11 ms now.

Two rows did not improve as much, and are reported honestly:

- **IDN and multi-innocent hosts** still take the Unicode path: punycode
  decode, ICU normalisation and Arabic clitic variants. It improved ~2.5×, to
  ~32 µs, but it is still the most expensive keyword path. It is also rare per
  page compared with ordinary hosts.
- **Full-list start-up** improved only 8% (614 → 568 ms). That cost is
  dominated by SHA-256 over 129 MB and 5.28 M hash insertions, not by the
  scanner the parser change touched.

## Results — memory and start-up

| | Before | After |
|---|---:|---:|
| Seed list load (236,586 domains) | 22.4 ms | 18.0 ms |
| Resident after seed load | 77.6 MB | **17.1 MB** |
| Resident after seed workload | 80.6 MB | 20.0 MB |
| Full list load (5,280,200 domains) | 614.5 ms | 568.0 ms |
| Resident after full load | 202.7 MB | 203.0 MB |

The seed win is the largest memory change. `load` reserved the hash set with the
**manifest's** `domain_count` — the full list's 5.28 M — even when installing the
bundled 236 K core tier, so a first launch or a no-network Mac allocated a
60 MB table for a tenth of the entries. The capacity hint now follows the
artifact being installed (`core_domain_count` for the seed), bounded by the
artifact's byte count. The full list is unchanged: its 129 MB artifact and
~67 MB hash set dominate, and shrinking the set further would trade memory for
lookup latency.

## What changed

All of it is in `macos/Hisn/BlocklistStore.swift` unless noted.

1. **Canonicalise once, and usually not at all.** `blocked()` canonicalised the
   host, then `walk()` canonicalised it again, then `hostMatchesTerm` trimmed
   and lowercased it a third time. Canonicalisation is now done once at the flow
   entry point, and the common case — lowercase ASCII, no stray dot — returns
   the same `String` with **no allocation**. The non-ASCII path still uses ICU
   lowercase and the three IDNA dot separators, exactly as before.

2. **Parent-domain walks hash bytes in place.** `walk` used to allocate a
   `Substring` per label and recompute the host's canonical form inside the loop.
   It now hashes each suffix from the host's UTF-8 buffer. The hand lists
   (`allowlist`, `customBlocks`) collapsed from two walks (`allowedByHand` then
   `listSaysBlocked`) into one, and when both lists are empty — the state almost
   every install is in — they cost no walk at all.

3. **An allocation-free ASCII keyword path.** The keyword layer was the largest
   cost: NFKC decomposition, re-composition, punycode, `tokenize`, a `Set` of
   every token variant, two set intersections, and 57 Foundation `contains`
   calls, for every allowed flow. On a canonical ASCII host, `normalize` is the
   identity, tokenisation is the ASCII alphanumeric split, and an ASCII token
   can only take the trailing-digit and letter-elongation variants (the Arabic
   clitic affixes cannot prefix an ASCII token). The fast path tests those forms
   by 64-bit hash against precomputed term hashes, then scans the host once
   against substring terms grouped by first byte.

4. **The `never_keyword` rewrite stays exact.** The reference path removes
   innocent words from the haystack before the substring test. The fast path
   reproduces that removal byte for byte when there is **one** innocent word,
   and declines to the reference path when there is more than one — because two
   distinct removals can interact and the reference resolves them in a `Set`'s
   iteration order, which is not reproducible. This is why
   `analytics.google.com` and `tasks.office.com` stay on the fast path (~1 µs)
   while an artificial `essex.analytics.tasks.com` does not.

5. **The Unicode path also stopped paying Foundation's fixed cost.** It tests
   each token variant directly instead of unioning everything into a set and
   intersecting twice, and scans the rewritten haystack by UTF-8 bytes instead
   of one `String.contains` per term.

6. **The adult-TLD rail and the generated search/viewer policy read bytes.**
   `AdultTldGuard.matches` no longer lowercases and trims a `String` to compare
   the last label; it compares UTF-8 bytes case-insensitively.
   `WebProtectionPolicy` gained `isBlockedCanonical`, which skips the re-trim and
   re-lowercase and uses precomputed `.domain` suffixes instead of building
   `"." + domain` per flow. The generator in `blocklist/web_protection.py` emits
   both; the raw-input `isBlocked(_:)` is kept for the site checker and tests.

7. **The blocked-flow tally no longer derives a calendar day per block.** It
   cached the local midnights that bound the day and compares two dates in the
   common case. Deriving `yyyy-MM-dd` through `Calendar` under a lock added
   ~3.5 µs to every denied flow; the comparison is now a few nanoseconds.

8. **The list parser uses `memchr` and the right capacity hint.** The parser no
   longer walks every byte with a bounds-checked Swift loop, and the seed no
   longer sizes its set from the full-list count (see above).

## What this deliberately did NOT do

- **No verdict cache.** A small map of hostname → verdict would have removed
  most of the remaining repeat work. It is also an in-memory record of the sites
  this Mac visited, and this product's promise is that no such record exists —
  the tally counts denied flows and cannot store a host. The optimisation budget
  went into making the matcher itself cheap instead. A cache keyed by 64-bit
  hash would not fix that: a candidate list plus the hash is a browsing history
  lookup away.
- **No semantic change to the verdict.** The hand-list precedence, the
  adult-TLD rail, the strict-mode inversion, the keyword `never_keyword` rewrite
  and the published-list walk are unchanged. The differential test below holds
  the two keyword paths to the same answer.
- **No new logging, no new persisted key, no new network request.** The health
  contract and the shared defaults keys are untouched.

## How it is checked

- `./test.sh macos` — 600 tests, including the existing keyword goldens
  (`host_cases.json`, `normalize_cases.json`), the load/rollback/hash suites and
  the filter's flow-policy tests. The seed-loaded golden runs the real term
  sources through the real parser.
- New `KeywordFastPathTests` in `macos/HisnTests/KeywordLayerTests.swift`:
  - differential: for ~2,400 plain ASCII hosts, wherever the fast path answers,
    it must agree with the reference path; at least 90% must be answered by the
    fast path, so disagreement cannot pass by declining everything;
  - coverage: ordinary hosts, an innocent-word host, an exempt site and an
    elongated spelling must be answered fast; a non-ASCII host, an `xn--` host
    and a two-innocent host must decline;
  - Arabic hosts, their IDN spelling and a Persian-keyboard spelling still match
    through the Unicode path;
  - the haystack rewrite still lets a substring term through
    (`essex-porn.com`, `assets-pornhub.com`, `analytics.xnxx.net`) while the
    innocent hosts stay reachable.
- `python3 blocklist/web_protection.py --check` — the generated Swift/DNS/JS
  copies are current.
- `script/bench_macos_blocklist.sh [seed|full]` — the measurement above.

## What a next pass could take

These are deliberately left out of this change, in descending order of value:

1. **The updater re-downloads and re-installs the whole generation every six
   hours even when the signed manifest's version is unchanged.** `ListUpdater`
   fetches all four artifacts in parallel before it can compare versions, so an
   unchanged list still costs a 21 MB download, a 129 MB inflate and a 129 MB
   container write. Fetching and verifying `manifest.json` first, and returning
   when its version equals what is installed, would cut the recurring check to
   a few KB. It needs a testable seam before it changes; there is no
   `ListUpdater` network test today.
2. **The full list's ~67 MB hash set.** A sorted `UInt64` array (42 MB) plus a
   small Bloom filter would trade a few nanoseconds per miss for ~25 MB, and a
   succinct filter would do better still. That changes the load/rollback
   contract and the hash agreement test, so it is not a flow-path change.
3. **The Unicode keyword path.** `TextNormalizer.variants` builds a `Set` and
   runs a dozen `String` operations per token. A matcher that tests the bounded
   affixes and elongation without materialising the variants would help Arabic
   IDN hosts, which are currently ~32 µs. The three-language fixture table pins
   the semantics, so the optimisation has a safe oracle.

## Limits

- One machine, one run of the toolchain, unsigned. Absolute nanoseconds will
  differ on Intel Macs and under memory pressure; the ratios are what the change
  claims.
- The benchmark calls `shouldBlockFlow` in-process. It does not measure the
  Network Extension framework's own per-flow overhead, which this change does
  not affect.
- The full-list set is still ~67 MB and is still built at filter start. Making
  that meaningfully cheaper needs a different on-disk format (a sorted array or
  a succinct filter), which changes the load/rollback contract and was out of
  scope for a flow-path pass.
- No signed build or physical-Mac acceptance is claimed. The unsigned build was
  exercised by the unit suite only; the system-extension path needs the paid
  entitlements and a signed install.
