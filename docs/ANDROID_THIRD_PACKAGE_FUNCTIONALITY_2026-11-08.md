# Android package 3: fresher blocking and useful rule tools

Status: implementation brief for DeepSeek, 8 November 2026.

## Goal and scope

Add useful functionality after package 2: update protection without upgrading
the APK, explain a hostname decision, and make personal rules easier to manage
and move between installations. Preserve commitment enforcement and privacy.

First inspect the current checkout and the active Android release line. Reuse
any completed functionality rather than implementing it twice. Coordinate with
the 15-language work and package-2 revision receipts. Do not touch another
worktree, commit, publish, install on the owner's phone or interrupt benchmarks.

Deliver 3A before 3B. Keep native-model integration, new detection languages,
full-traffic VPN and schedule-schema expansion outside this package.

## 3A.1 — One explainable hostname decision

Current reference points: `DnsVpnService`, `DnsPolicy`, `DomainPolicy`,
`DomainSeed`, `AdultTldPolicy` and `HostTermPolicy`.

- Extract/reuse one pure composed hostname evaluator for enforcement and UI.
  Preserve actual Android precedence; do not import a different platform's
  allowlist rules or introduce an exception mechanism in this refactor.
- Return decision, reason and generation: personal block, published domain,
  adult TLD, hostname term, no local match, invalid input or unavailable data.
- Keep domain evaluation separate from packet parsing and SafeSearch rewrites.
  Use the same evaluator for DNS-answer target checks where applicable.
- Build immutable policy/list snapshots off-thread. A request sees a complete
  old or new snapshot and its matching explanation, never mixed generations.

Proof: existing DNS/hostname fixtures remain unchanged; checker and actual
enforcement agree across parents/subdomains, Unicode/punycode, trailing dots,
exemptions and personal rules. Invalid/unavailable is never “allowed”.

## 3A.2 — Automatic verified mobile list updates

- Add `AdultListUpdater` and a private active-generation store. Reuse the
  repository's published manifest/signature/artifact formats and pinned public
  trust key. Never read or distribute the signing private key.
- Verify exact signed manifest bytes, artifact hashes, schema, generation,
  byte/count limits, entry validity and plausible coverage before adoption.
  Use an Ed25519 implementation compatible with the existing minimum Android
  API; do not assume a newer platform crypto provider or raise minSdk silently.
- Choose a published mobile-compatible artifact after checking memory and
  available tiers. Do not load the full multi-million-domain list by default.
- Download with bounded time/bytes into staging, validate and build the index
  off-thread, then atomically activate the coherent generation. Preserve the
  last working generation and reject older downloaded offers across restarts.
- Keep the bundled seed available for offline first use. If installed data is
  damaged, disclose fallback/degraded state; never silently reset the rollback
  floor to accept an old downloaded generation.
- Update domain data and related terms coherently. Do not expand the current
  language-scoring contract while installing a newer multilingual terms file.
- Invalidate revision-bound decision caches and evidence on adoption. Bound
  simultaneous old/new index memory and close superseded resources safely.
- Schedule bounded background checks through the project's supported Android
  job infrastructure; add manual “Check for updates”. An active commitment does
  not need to prevent authenticated publisher maintenance. Personal rules and
  the commitment deadline remain unchanged.

Proof: bad signature/hash, malformed/oversized file, interrupted download/write,
rollback, offline reboot and concurrent queries retain coherent enforcement.
Measure first load, peak memory and lookup latency on a phone or explicitly
targeted test emulator. Test that a newly listed fixture blocks after adoption.

## 3A.3 — Check a website

- Add a read-only tool reachable from Rules. Accept a hostname or HTTP(S) URL;
  parse explicitly and reject malformed/unsupported input. Explain that a URL's
  path/query is not evaluated by this hostname tool.
- Call the same current evaluator locally. Show the normalized hostname,
  reason and installed generation. Do not visit the destination or contact a
  classifier. Keep input/results ephemeral, with no recent-check history.
- Say “No local hostname rule matched” rather than “Safe” or “Not blocked
  everywhere”. Upstream filtering, app restrictions, page text and images can
  produce different outcomes.
- Offer “Add to blocked sites” after a confirmation showing the normalized
  rule. Route it through ordinary `PolicyStore` validation/revision handling.
  Checking is always read-only; adding must follow the existing lock contract.

Proof: evaluator parity, URL parsing boundaries, unknown/unavailable state,
current-generation changes while open, RTL and no persistent query history.

## 3B.1 — Better personal-rule editing

- Add search, counts, normalized previews and duplicate detection for sites and
  custom words. Reuse existing editors and named site chips.
- Add bounded paste-many input with a preview: valid additions, duplicates and
  rejected lines with reasons. Apply the accepted set in one validated write;
  no partial mutation when persistence fails.
- Provide clear individual removal actions and existing commitment explanations.
  Keep hostname normalization and literal-word semantics consistent with their
  enforcing components; do not treat arbitrary pasted URL text as a valid rule.

Proof: Unicode input, duplicate/oversized lists, invalid lines, failed save,
active commitment, large fonts, TalkBack and every currently complete locale.

## 3B.2 — Portable personal rules

- Inspect the existing browser rule-document schema first. Define a versioned
  Android-supported subset with explicit capability differences.
- Export personal site/word rules with Android's document picker; use its
  supported file access without requesting broad storage access. No deadlines,
  guardian secrets, browsing data, receipts or signed-list generations.
- Import with strict byte/count/schema limits and a preview. Explain unsupported
  fields rather than silently applying or dropping them. Package identifiers
  and platform-specific rules need a separate reviewed contract before transfer.
- Make this first import add-only. Route every change through normal commitment
  checks; no file can add exceptions, shorten deadlines or turn off protection.

Proof: round trip, duplicates, malformed/future schemas, oversized files,
unsupported browser fields, failed save and locked import. Importing never
removes existing rules or resets usage/commitment state.

## Completion gates

1. Run focused new pure-policy tests and `sh android/test-core.sh` using Android
   Studio's JDK, plus current locale/source-notice checks.
2. Build/lint the affected variants. Exercise the update → enforcement → site
   checker flow on an explicitly selected disposable emulator; do not disturb
   the phone or benchmark emulator. Report unavailable device checks honestly.
3. Test offline startup and process death during activation. Check list changes
   propagate to the service and package-2 readiness receipts.
4. Check all new strings, plurals, RTL and accessibility against the localization
   catalog present at implementation time. Do not advertise unfinished locales.
5. Report exact features completed, measured limits, tests passed and remaining
   blockers. 3A is independently deliverable; do not call 3B complete early.

The user-visible result: fresher domain coverage, a useful explanation tool and
quicker personal-rule management, without introducing a new commitment bypass.
