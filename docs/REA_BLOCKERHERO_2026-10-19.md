# BlockerHero inspection and independently authored Hisn changes

## Scope and identity

On 2026-10-19 the owner-authorized Samsung was connected by USB. BlockerHero
1.5.0 (version code 190) was opened without changing its rules, permissions,
account, administrator state or uninstall settings. No sign-in was performed.
REA's Android workflow was used through its official `rea-agents@6.5.0` CLI
with JADX headless MCP 0.7.1. REA configuration was not registered globally.
The base APK SHA-256 was
`691e1001a12a34933afd097e36c969a581876e8e9574c4092a4827787a5ce578`.
Pulled APKs and full decompiler output remain outside the repository.
No competitor implementation, model or asset was copied into Hisn.

## Evidence ledger

| REA observation | Evidence ID |
| --- | --- |
| Installed package's six APKs pulled and digested | `ev_471b4630a8cbafbf32fb5a562cdfea474b983eaa1c407f571b3df3ced039fe22` |
| Package declarations and manifest | `ev_59e64ebd11128dcec2d18221971c3289c25337190a7e4670b73a837b5861b625` |
| Application class search | `ev_51422d73430b344dfef9c925f19afe218d92de2c8b5f4ff7d97dfc21b005b495` |
| Accessibility service member inventory | `ev_2ce83c035a0c10cbf2fd6b46ecbcda2e02495e39103abad151814b87c28f569f` |
| Exact `onAccessibilityEvent` overload | `ev_bb7d8990664376c01ab83fb47d4a4b0572c407fd61bd8306e1a3a621c9eb6f6e` |
| Admin receiver member inventory | `ev_892a2e4da64166acc970b75c208ae6fb67d16fd83665bb3101ed0dada29e16e1` |
| Exact `onDisableRequested` overload | `ev_11d0b679ba108aefeed11d105f5335ba5a54683545211bf556b842d60d4bc310` |

The event decompilation was available, but includes decompiler type-inference
warnings and obfuscated helpers. These observations describe selected static
paths, not exhaustive behavior or measured protection effectiveness.

## Observations, not assumptions

- Live dashboard: accessibility OFF, overlay permission requested and
  notifications requested. Live protection was therefore not ready for a
  meaningful enforcement test. Existing settings were preserved.
- Live dashboard: self-accountability mode describes timed random-text typing
  before disabling features. This is friction, not proof of OS-level removal
  prevention. The challenge was not started.
- Live blocklist: Apps (0), Keywords/websites (0); adding a rule requested Google
  sign-in. No account was accessed and no competitor rule was added.
- Static accessibility handler: distinct branches for selected apps, browser
  address-bar handling, keywords, focus periods and selected social-app features.
  A path counts normalized event text words and returns when the count exceeds
  25. This does not establish that every other helper skips long text.
- Static anti-removal path: settings/admin-related detection can navigate away
  or invoke an intervention. Its full helper conditions and bypass resistance
  were not established. Do not treat this as device-owner enforcement.
- Static admin callback: `onDisableRequested` returns a warning string. That
  callback itself does not veto Android administrator deactivation.

## Hisn changes from this review

1. Long visible leaf labels are sampled instead of being dropped at 2,048
   characters. Samples are bounded to 32,768 UTF-16 units; a truncated final
   token is discarded to avoid manufacturing a whole-word match.
2. Traversal no longer stops at the 64th child of each container. A global
   512-node/child-attempt budget remains to bound CPU and memory. Incomplete
   inspections are explicitly reported; this is not an exhaustive screen scan.
3. Canonical Unicode rule expansion is validated before persistence. Rejected
   word/domain updates leave existing settings intact, avoiding restart failures.
4. Dashboard protection state considers configured accessibility restrictions as
   well as DNS, rather than falsely saying all protection is inactive when only
   DNS is off. Connection is not represented as complete protection.
5. Core and Android device regressions cover long samples, truncation, Unicode
   persistence, local domain blocking, commitments and the actual signed seed.

Hisn retains explicit app selection, password/editable subtree exclusions,
on-device ephemeral screen processing, separate DNS enforcement and optional
legacy-admin friction. No accessibility uninstall trap, settings takeover or
silent service activation was added.

Verification: all core suites passed; debug and instrumentation APK builds and
lint passed (zero errors, six warnings at the recorded run). Hisn 0.3.0 was
installed and opened on the Samsung. The final device runner passed all listed
checks, including the actual 159,762-domain seed. Initial instrumentation
failures revealed context/storage handling issues and were fixed before the
successful run. No VPN, accessibility service or administrator was activated.
Tests use a generated preference file, not the owner's `hisn_policy` file.

Installed debug APK SHA-256:
`2db46d55fac94d9c75827c22ec5aee7d9494b93248c087205cfc82cd9e40b6c2`.

Follow-up: the owner subsequently enabled accessibility and live fixtures
exposed and led to fixes for warning/Home ordering and cooldown enforcement.
See `ANDROID_LIVE_GUARD_2026-10-19.md` for the final successful test matrix and
the newer installed APK digest. The paragraph above records the earlier core
verification, before that consent and follow-up build.

## Next implementation gates

Use harmless fixture screens after owner accessibility consent to measure
actual interventions, false positives, wide trees, long paragraphs, dynamic
feeds and sensitive-field skips. Then test DNS routing after separate VPN
consent. Add browser-specific address adapters and social-feature restrictions
only with versioned fixtures; app UI identifiers change. Visual filtering needs
a separately consented capture/model pipeline and measured evaluation. Managed
device removal restrictions require a distinct, explicitly enrolled deployment
mode; no phone reset or enrollment is part of this work.

Tool source: [REA](https://github.com/morluto/rea).
