# Hisn Android 0.4.6 — explicit media labels

Development preview, version code 11, 2026-10-21.

Adds conservative detection for complete two-word explicit media labels without
a promotional verb, including `Porn videos` and `محتوى إباحي`. The explicit term
must already be configured at strong weight; negative context remains effective.
The rule does not match these labels as substrings of longer research/recovery
prose or treat generic adult/sex labels as equivalent.

All core tests pass, including weak/missing vocabulary, negative-context overrides,
prose controls and the actual signed seed. On the unchanged 52-example synthetic
corpus, intended blocks detected increased from 21/25 to 23/25; benign false blocks
remain 0/27. This is not a representative accuracy estimate. Spaced letters and
slash-separated spellings remain known challenge misses. The signed seed and
saved owner rules are not modified by this improvement.

Debug and test APK builds, release Java compilation and debug lint pass (zero
errors, eight warnings). Installed version 0.4.6 / code 11 on the Samsung.
Actual-APK policy checks and all controlled adult-text accessibility fixtures
passed: medical/protective/recovery controls stayed available; English/Arabic
promotion, compact spellings and both new exact media labels were restricted.
Fixture cleanup reported owner rules preserved. The attempted post-update DNS
recheck could not start because the device connection was unavailable; the prior
0.4.5 network result is not presented as a fresh 0.4.6 pass.

APK SHA-256:
`4edc05bd735bf0afee106454fc492480aa8506a79b553e3e1ad1b38f671e0529`

Android visual filtering and universal/undeletable protection remain unimplemented.
