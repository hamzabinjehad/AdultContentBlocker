# Hisn Android 0.4.5 — adult-text matching

Development preview, version code 10, tested 2026-10-21.

## Changes

- Recover compact punctuation-obfuscated configured strong explicit terms, including
  dots, dashes, underscores and supported separators. Never join whitespace or URL
  separators, and never recover an arbitrary substring from a larger word.
- Preserve punctuation in bounded, ephemeral accessibility leaf samples so the
  detector receives the original spelling. Custom literal rules still normalize
  independently. Password/editable-node exclusions remain unchanged.
- Share ten reviewed protective/recovery context rules between production and tests.
  The verified seed is unchanged; these additions are app code. The combined policy
  has 4,464 submitted / 4,462 canonical rules.
- Add a synthetic regression corpus and debug-only actual-APK policy/visible-screen
  fixtures. Fixture cleanup restores owner rules; no real commitment is started.

## Evidence and limitations

On the same 52-example synthetic English/Arabic set, baseline detected 7/25 intended
blocks with 4/27 benign false blocks; this version detects 21/25 with 0/27 benign
false blocks. These are regression counts, not population accuracy or a safety
guarantee. Known challenge misses remain: `Watch p/o/r/n`, `Watch p o r n`,
`Porn videos`, and `محتوى إباحي`. Promotion gating and conservative context handling
still limit recall. Custom literal rules deliberately remain independent and can
block protective references when explicitly configured by the owner.

Core tests pass, including sample bounds, literal matching, actual seeds, corpus,
DNS transports and schedule tests. Debug/test APK builds, release Java compilation,
release manifest generation and debug lint pass (zero errors, eight warnings).
Release manifest excludes debug fixture components and the debug DUMP permission.

Installed on Samsung SM-G990B2 / Android 16. Actual-APK policy checks and controlled
accessibility fixtures passed: medical, protective and recovery text allowed;
ordinary and compact-obfuscated English/Arabic adult-promotion text blocked.
An initial device test found punctuation was stripped during sample collection;
that integration defect was fixed, rebuilt, reinstalled and the complete adult
fixture run passed. No private browsing content was inspected.

The existing guard regression suite also passed ordinary/editable/password leaves,
long/wide trees, selected-screen periodic checks, permanent app restriction and
rapid reopening. Live DNS checks passed local blocking and resolving `example.com`
over UDP and reused TCP; this verifies the tested virtual-DNS path, not all traffic.
The full saved-rule/settings fingerprint matched before and after the fixture runs:
`6fbe7d6b50fdce044b2934eedfbb0dc42ddaf79d2516398f7754db7c64dc6685`.

Android image/video/OCR filtering, full-traffic VPN coverage, reliable always-on
enforcement and managed-device uninstall blocking are not implemented. This is
not an undeletable app or universal adult-content prevention system. Apple apps
and browser extension are not updated by this Android-only release.

APK SHA-256:
`197abbf166383cbadfddd39b1b7d6fed32e5ede42ddb545b18560fe7e8a7f523`
