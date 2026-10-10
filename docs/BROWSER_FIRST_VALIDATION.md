# Browser-first validation — 2026-10-10

Scope: per-post X/Twitter text checking, canonical Safari Web Extension packaging,
and English/Arabic browser-first setup guidance. Existing domain rules, strict
mode, commitment rules and macOS background guarding remain separate layers.

## Local evidence

| Check | Result |
| --- | --- |
| macOS hosted tests | 440 passed, zero failures |
| macOS Release build | Passed; unsigned structural release validation is separate from distribution signing |
| Extension JavaScript suites | Passed, including 62 granular feed checks |
| Chromium integration suites | Passed: DNR, text scanning, localized UI, synthetic X/Twitter feed |
| Feed interaction QA | 14 synthetic checks passed, zero runtime exceptions |
| Python, signed seed and release checks | Passed; mobile packaging/rules suite contains 15 tests |
| iPhone/iPad generic Simulator build-for-testing | Passed, including embedded Hisn Text and all four existing Safari domain blockers |
| macOS compiler-exported Arabic localization | All 475 exported strings have translations |
| Generated Xcode projects and whitespace | Regenerated; diff check passed |

Tests use isolated profiles, fake time where needed and synthetic text. They do
not require visiting adult sites or reading the user's browsing history.

## Not established by these checks

- Physical iPhone/iPad Safari extension activation, website grants, private
  browsing, real layout compatibility and actual Screen Time enforcement.
- Successful inspection of every X/Twitter layout, another browser or a native
  app. The adapter is limited to supported tweet articles.
- Image/video pixel classification, zero exposure before detection, or
  infallible text classification. Queue, caption and cache limits intentionally
  bound work; they are not a guarantee that every description is scored.
- Non-removability or protection against an administrator, permission
  revocation, device reset or an unsupported browser.
- Developer ID distribution readiness or an active macOS system network filter.
  The local development build remains ad-hoc signed; structure checks do not
  satisfy those prerequisites.

GitHub CI must be checked against the pushed commit, not inferred from an older
green run. See [the setup and acceptance guide](BROWSER_FIRST_PROTECTION.md) for
manual activation and physical-device checks.
