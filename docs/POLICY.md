# The decision contract

Two enforcement points judge the same hostname — the macOS socket filter
(`BlocklistStore.swift`, asked by `FilterDataProvider.handleNewFlow`) and the
browser extension's declarativeNetRequest rules (built by
`extension/lib/policy.js`). This document is the one answer both must give.
`blocklist/terms/policy_cases.json` is the same contract as cases, and both
test suites assert every case: `PolicyContractTests.swift` against the store,
`extension/test/policy.test.js` against the exact rules handed to Chrome.
**Change the fixture first.** Then the suites say which client is wrong.

## Inputs

| Input | Owner | Reaches the browser via |
|---|---|---|
| Published list + URL keyword rules | CI, signed | static rulesets and the downloaded list |
| `customBlocks` — "Always block these" | the app (`SiteLists`) | native heartbeat |
| `allowlist` — "Always allowed" | the app (`SiteLists`) | native heartbeat |
| mode, lock deadline | the app (`LockStore`) | native heartbeat |

Browser-only installs author both hand lists in the options page; the rules
below are identical.

## Rules

1. **The published list, the keyword rules and the custom blocks always
   apply.** A custom block is never loosened by anything.
2. **The allowlist is an allowance in every mode.** It overrides the published
   list and the keyword rules — it is the one escape valve a false positive has
   during a lock, when the lists cannot otherwise change — and in strict mode it
   is additionally the only thing reachable.
3. **Most specific wins; ties go to the block.** When an allowlist entry and a
   custom block both cover a host, the entry with more labels decides
   (`mail.google.com` over `google.com`). At equal depth the block wins.
   Every entry covers its subdomains.
4. **A lock ends at its effective deadline** — `LockStore.effectiveDeadline`,
   which includes a matured self-release. The filter consults
   `LockStore.strictModeActive`; the bridge reports the same instant as
   `lockUntil`; the browser's own clock decides nothing but "has that moment
   passed".
5. **Losing the app during a lock is tampering.** After five minutes of silence
   the browser behaves as strict mode with the last allowlist it was given
   (`shouldFailClosed`). Before the app has ever answered, silence is a
   browser-only install and changes nothing.
6. **Nothing user-authored loosens while locked.** Adding a block or removing
   an allowance is always allowed; the reverse waits for the lock to end. Both
   ends enforce this (`SiteLists.write`, `guardedUpdate`), by set membership,
   never by count.
7. **A settings message may touch only the fields in `USER_FIELDS`.** Internal
   state — `appPresent`, `failClosed`, `lastHeartbeat`, `listVersion`,
   `rulesApplied`, `disputed` — is refused whole. Each of those was a way to
   switch the fail-closed defence off from a devtools console.
8. **Strict mode covers embedded traffic.** Every resource type is denied
   unless its destination is allowlisted. One carve-out: a page on an
   allowlisted site may load its *plumbing* — scripts, styles, fonts, images,
   fetches, beacons — from anywhere, or no modern site renders. It may not
   load *content* from an unapproved host: frames, media, objects,
   WebSockets. Custom blocks deny every resource type in every mode. The
   filter has no carve-out (a socket carries no initiator), so it is strictly
   stricter; the fixture marks those cases `browserOnly`.
9. **Pages already open when the rules tighten are closed.** Rules judge new
   requests only, so the worker walks open tabs and sends the ones the policy
   now refuses to the block page (`tabsToBlock`). The Mac cannot do the
   equivalent: a socket open before a lock starts is not torn down.

## Scope, stated plainly

* The **filter** applies every rule to every socket the machine opens, whatever
  application opened it. A flow with no hostname is dropped in strict mode and
  allowed otherwise.
* The **browser** applies the published rulesets to documents and the
  resource types each ruleset names; the strict catch-all covers every
  resource type per rule 8; custom blocks and allowances cover every
  resource type. What the browser cannot see: a request made by another
  browser, or by any process that is not this browser — that is the filter's.
* Hostnames are compared case-insensitively with a trailing dot removed, and
  a hand entry is canonicalised identically on both sides (`SiteLists.normalize`
  ↔ `canonicalHost`): scheme, path, userinfo and port stripped, leading `www.`
  dropped, ASCII labels only, no numeric last label.

## How the browser encodes rule 3

DNR has priorities, not specificity. Each hand list is grouped by label count
and each group is one rule at `1000 + 2 × labels`, blocks one higher than
allows. A request matches every group whose entry covers it; the highest
priority among them is the most specific entry, and a tie between an allow and
a block at the same depth goes to the block. The published rulesets sit at
priority 1–2, so any allowance outranks them, and the strict catch-all sits at
1 so any allowance carves through it.
