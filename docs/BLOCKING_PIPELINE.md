# The browser blocking pipeline

This is the code map for the Chromium extension: which layer sees which request,
which file builds it, and which invariant a change has to preserve. The decision
contract shared with the Mac filter is [POLICY.md](POLICY.md); this document is
about the browser side's structure.

## Layers, in the order a request meets them

| # | Layer | Scope | Built by | Lives in |
|---|---|---|---|---|
| 1 | Published domain list | documents and most embedded types, signed core tier | CI list build | `rules/dnr_block_rules.json` (static), downloaded generation (dynamic) |
| 2 | Published URL keywords | host/path/query of documents, exact spellings | `blocklist/build.py` | `rules/dnr_keyword_rules.json` (static), downloaded generation (dynamic) |
| 3 | Site path rules | a word that is only unambiguous in one site's path, e.g. `/r/nsfw…` | maintained by hand | `rules/paths.json` |
| 4 | Obfuscation aliases | `p0rn`, `s3x`, `m1lf`, … — reviewed spellings of terms already in the list | `blocklist/evasion.py` | `rules/evasion.json` |
| 5 | Encoded Arabic queries | strong Arabic terms percent-encoded in a recognised search parameter, with reviewed exclusions for ordinary words | `blocklist/encoded_queries.py` | `rules/encoded_queries.json` |
| 6 | SafeSearch / restricted YouTube | search engines and YouTube, scoped to allowances | `blocklist/web_protection.py` | `rules/safesearch.json`, `lib/web-protection.js` |
| 7 | Built-in search/viewer blocks | Yandex search hosts and anonymous Twitter viewers | `blocklist/web_protection.py` | `rules/web_protection.json` |
| 8 | Dynamic policy | strict catch-all, allowlist, custom blocks, scoped SafeSearch | `lib/policy.js` | `worker/rules.js` installs and reads it back |
| 9 | Page text | rendered text, alt text and metadata of every frame | `lib/score.js` + `lib/normalize.js` | `content/scan.js` collects, `worker/inspection.js` scores |
| 10 | Feed items | one rendered post at a time on supported mixed sites | `content/feed.js` | `content/scan.js` delegates to it |
| 11 | Visual checks (opt-in) | readable images and video frames | `visual/` + `content/visual.js` | separate registration, off by default |

Priorities are global across rulesets: strict catch-all 1, plumbing 2, published
domain list 3, keyword/path rules 4, obfuscation and encoded queries 5, the
hand lists at 1000+, built-in search/viewer blocks at 2000. The full ladder and
its tie rules are in `lib/policy.js` and [POLICY.md](POLICY.md).

The last four layers exist because a domain list cannot see the inside of a
page, and the text layer cannot see a page before it renders. They are not
alternatives: the URL layers refuse a bad destination before a request is
made, and the text layer catches content on a destination that must stay
reachable.

## Code map

```
extension/
  background.js            entry point: alarms, lifecycle, message table, transaction lock
  worker/
    state.js               DEFAULT_STATE, storage read/write, enforcement receipt
    updates.js             validated settings patches and the lock-direction rules
    rules.js               applyRules(), baseline rulesets, readback
    scanners.js            dynamic registration/repair of text and visual scanners
    tabs.js                already-open tabs → block page (fresh tab, no history)
    native.js              heartbeat, authoritative lock state, fail-closed
    lists.js               signed generation download, verify, install
    inspection.js          term index, page/feed scoring, local corrections
  lib/
    policy.js              the decision contract in executable form (pure)
    score.js               weighted page scoring and reviewed evasion matching (pure)
    normalize.js           Unicode normalisation, one of three language twins (pure)
    generation.js          shape/plausibility checks for a downloaded build (pure)
    verify.js              Ed25519 manifest and artifact verification (pure)
    ...                    popup/settings/status helpers
  content/
    scan.js                page-text collection, scheduling, block navigation
    feed.js                per-post adapter for supported mixed-content sites
  rules/                   static rulesets referenced by the manifest
```

The blocking logic in `lib/` — `policy.js`, `score.js`, `normalize.js`,
`generation.js` and `verify.js` — is pure: no `chrome.*`, no DOM. That is what
lets the same scorer run in the content script, the service worker, the
JavaScriptCore tests and the staged Safari build. Extension APIs are touched by
`background.js` and by the modules under `worker/`. The entry point is wiring,
and `worker-layout.test.js` fails if a concern moves back into it.

## Generated files

Three generators produce the bundled policy, and each has a `--check` mode
that `./test.sh python` runs:

| Generator | Output | Source of truth |
|---|---|---|
| `blocklist/evasion.py` | `extension/rules/evasion.json` | `blocklist/evasion_aliases.json` + signed host vocabulary |
| `blocklist/encoded_queries.py` | `extension/rules/encoded_queries.json` | signed Arabic page vocabulary, weight ≥ 8, single token |
| `blocklist/web_protection.py` | `rules/web_protection.json`, `rules/safesearch.json`, `lib/web-protection.js`, Swift/DNS copies | `blocklist/web_protection.json` |

Do not edit a generated file by hand. The "generated" comment is in the file
where the format allows one; the `--check` gate is the actual enforcement.

## Invariants to keep

1. **The signed vocabulary is the only vocabulary.** Evasion aliases must name
   a term that exists in `host_terms.tsv`; encoded-query rules are derived from
   signed page terms. A merge that drops a term must regenerate both (the
   Python suites fail if the output is stale).
2. **Aliases add spellings, never leniency.** Scores, occurrence caps, ignored
   terms and the density floor stay attached to the canonical term.
3. **A rule must compile in Chrome.** A single unsupported regex silently
   disables a whole static ruleset. `test/browser/dnr.sh` validates every static
   regex with `chrome.declarativeNetRequest.isRegexSupported` and then exercises
   the real matches in a headless browser.
4. **The allowlist still wins.** Every new layer sits below the hand lists so an
   explicit allowance (the false-positive escape valve during a lock) is not
   bypassed by this module.
5. **No record of what was visited.** Nothing in these layers writes the
   matched term, host or query to storage, a URL or a network request. The only
   exception is the in-memory session record the block page uses to explain
   itself, documented in `worker/inspection.js`.
6. **Fail closed, except where a heuristic legitimately cannot.** URL and domain
   rules tighten on uncertainty; a failed text score retries with backoff and
   never treats silence as a clean page.

## What this does not cover

A second browser, a native app's own traffic, encrypted media with no readable
text, and an administrator who removes the extension are outside the browser
pipeline. Those gaps are the reason the Mac filter, DNS and the platform
controls exist; see [THREAT_MODEL.md](THREAT_MODEL.md).
