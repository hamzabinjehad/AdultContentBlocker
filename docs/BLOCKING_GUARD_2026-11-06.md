# The unlisted-host guard, 2026-11-06

A blocking pass across all four clients. It closes one admitted coverage gap —
a name that no blocklist has seen yet — without changing the signed seed, and
it brings the Android DNS layer up to the hostname-keyword parity the Mac and
the browser extension already had.

This is a code and local-test record. No signed APK, notarized Mac build,
TestFlight/App Store build or store listing is claimed, and no physical device
was used for this pass.

## The gap

`docs/THREAT_MODEL.md` lists **"new domain, registered today — open in
blocklist mode"** as the first coverage hole. Three things made it wider than it
needed to be:

1. `blocklist/sources.json` has carried `adult_tlds` (`adult`, `cam`, `porn`,
   `sex`, `xxx`) since the core-fill pass, but only as a **selection signal**
   for which domains fit the bundled tier (`blocklist/build.py`). No client
   enforced the TLD itself. A domain registered under `.xxx` this morning was
   reachable until the next list build reached that client.
2. The browser and the Mac had a **hostname-keyword layer** (116 reviewed
   terms in `seed/terms.json`, `host_cases.json` as the cross-language
   contract). Android's DNS policy did not: it loaded `terms.json` only for
   selected-app screen text. A new host named `arab-sex-tube.com` was blocked
   on the Mac and in Chrome and reachable on Android whenever DNS was the
   covering layer.
3. The Mac's TLD coverage was incidental: the terms `porn`, `sex`, `xxx` and
   `adult` happen to be token terms, so the keyword layer caught many of those
   hosts. If `terms.json` failed to load, the whole TLD opened silently, and no
   test asserted the behaviour without terms.

## What every client now does

### Adult-TLD rail (all four)

Four TLDs are adult-sponsored at the registry: `.adult`, `.porn`, `.sex` and
`.xxx`. A name whose **final label** is one of them is blocked, whatever the
list generation says. `.cam` is deliberately excluded — unlike the four, it is a
generic TLD with ordinary camera and webcam uses, and blocking it whole would
take out non-adult sites. The keyword layer still catches a `.cam` host whose
own labels name adult material.

The rail is a policy constant in code, not a downloaded artifact, so it does
not depend on a list entry or a terms file. On the extension, Mac and Safari
paths it still answers when the list is missing; Android's DNS service still
requires the seed list to start, but the rail adds no lookup that can fail. It
sits **after** the hand allowance — the escape hatch a false positive keeps in
every layer — and **before** the published list.

| Client | Where | What it covers |
|---|---|---|
| Browser extension | `rules/adult_tlds.json`, a static ruleset at `PRIORITY_LIST`; `lib/policy.js` mirrors it for `decide` and the open-tab sweep | documents (block page) and every embedded resource type (dropped) |
| Mac | `macos/Hisn/AdultTldGuard.swift`, called by `BlocklistStore.blocked`; `SiteChecker` names the answer `adultTld` | every socket the filter sees |
| iPhone & iPad | `ios/prepare_rules.py` appends four rules to the last Safari blocker (3,414 spare slots before the change) | Safari and WebKit apps that use Safari content blockers |
| Android | `android/core/.../AdultTldPolicy.java`, consulted in the DNS policy before the seed | every DNS query the Hisn tunnel resolves |

### Hostname-keyword parity on Android

`HostTermPolicy.java` implements the same contract as
`BlocklistStore.hostMatchesTerm` and Chrome's generated keyword rules:

- 59 `token` terms match a whole hostname label token, so `essex.gov.uk` never
  equals `sex`;
- 57 `substring` terms match inside a label, so a concatenated mirror is
  caught;
- `never_keyword` tokens are removed before the substring test, so
  `milford.ct.us` and `pornic.fr` stay reachable while
  `milford-milfs.example` does not;
- `keyword_exempt_hosts` keeps a whole site and its subdomains reachable;
- Arabic/Persian orthographic folds, punycode decoding, trailing digits and
  keyboard-hold reduction are the same on all four implementations.

The layer loads **synchronously** from the APK-pinned signed `terms.json`
(digest and version checked before parsing) before the DNS tunnel is
established. A failure leaves the seed list enforcing and the keyword layer
off; unverified bytes are never used. Decisions are cached (bounded at 8,192)
so a repeated DNS question does not repeat the folding work.

`blocklist/terms/host_cases.json` remains the shared contract. The Android
test now asserts all 42 cases (17 blocked, 25 allowed), plus the named safety
cases, against the real signed terms.

### Contract and generated-policy updates

- `blocklist/terms/policy_cases.json` gains five cases: a new `.porn`, a
  subdomain under `.xxx`, `.adult`, the deliberate `.cam` non-guard and a hand
  allowance that outranks the guard. Both `PolicyContractTests.swift` and
  `extension/test/policy.test.js` assert them.
- `extension/test/adult-tlds.test.js` asserts the static ruleset's two rules,
  the same regular expression in both, the four TLDs and nothing else, and
  positive/negative URLs: absolute FQDN trailing dot, userinfo, port,
  subdomains, `notxxx.com`, `porn.example.com`, `xxx.evil.com`, a path and
  `.cam`.
- `ios/test_rules.py` asserts the guard is only in the final Safari part, that
  the per-part 40,000-rule cap still holds (a full final part plus the guard is
  rejected, never truncated), and the URL boundaries.
- `macos/HisnTests` adds the guard's store and site-checker cases, including
  the unreadable-list path and the allowance override.
- `extension/check_package.py` pins `rules/adult_tlds.json` to the repo bytes,
  like the other built-in policy files.

## Verification on this machine

| Suite | Result |
|---|---|
| `extension/test/run.sh` | **PASS** — all JavaScript suites, including 63/63 adult-TLD checks |
| `extension/test` Python (unpacked-load checks) | **PASS** — 45 tests; the browser-runner exit test is timing-sensitive under load and passed when rerun alone |
| `python3 -m unittest discover -s ios -p 'test_*.py'` | **PASS** — 44 tests |
| Android `test-core.sh` (JDK 17+) | **PASS** — every core suite, including `HostTermPolicyTest` (42/42 shared cases) and `AdultTldPolicyTest`; `check-strings.py` and `check-notices.py` pass |
| Android `:app:compileDebugJavaWithJavac` | **PASS** — the app compiles against the new core classes and the renumbered build |
| Mac `xcodebuild test` and localization | **PASS** — 578 XCTest cases, 0 failures; 721 strings, all with Arabic |
| iOS simulator suite | **PASS** — 252 tests, 0 failures; WebKit compiled all six Safari parts, including the four guard rules (311.6 s for the part-compile test) |

The generated Safari metadata now reports **236,590 rules** (236,586 core
domains plus four TLD rules), still inside the 240,000-rule cap. The extension's
new ruleset is appended last in `manifest.json`; a new ruleset is never inserted
before an existing one because Chromium keys its compiled cache by position.

## What this does not close

- **No device or signed-build evidence.** No signed 1.2.2 APK was built or
  installed for this pass (the owner's phone is on the 1.2.1 build recorded by
  its own release note); the Mac build is unsigned; the iOS suite runs on the
  simulator. Real shielding and browser behaviour still need the signed,
  entitled test device.
- **Other generic TLDs and random `.com` names remain list-dependent.** A brand
  new `example.some-new-gTLD` with no adult token is open until a list carries
  it. The guard covers four registries, not the open web.
- **Android DNS still cannot see paths, queries or IP literals.** The hostname
  layer judges a name; a page on a permitted host is the text/visual layer's
  job, and a direct-IP connection bypasses DNS entirely.
- **DoH and another VPN remain the Android layer's largest holes**, unchanged
  here; `docs/ANDROID_ENCRYPTED_DNS_RESOLVERS_2026-11-05.md` keeps the scope.
- **The adult-TLD rail is a deliberate over-block for the rare adult-sponsored
  domain used for non-adult content.** The hand allowance can override it. It
  is not extended to `.cam` or to adult-token generic TLDs.
- **No runtime list update was added to iOS or Android.** The guard reduces,
  but does not remove, the cost of a stale seed.

## Reproduce

```sh
extension/test/run.sh
python3 -m unittest discover -s ios -p 'test_*.py'
./test.sh extension
./test.sh macos
./test.sh ios
cd android && sh test-core.sh
```
