# Tamper resistance: current improvement and remaining work

## Implemented locally

The Mac setup checklist now includes an expandable removal-protection guide,
official Chrome documentation, an explicit Device Management shortcut and a
fresh evidence check. It does not install policies. No installed supported
browser is a pending state, not a successful empty check. Existing completion
requires both forced extension policy and an administrator-protected native
connection; it does not certify every profile/window's runtime enforcement.
The iPhone/iPad removal section explains the separate Screen Time route and
individual-authorization limits, with a refresh action and no fake Chrome setup.

Every Chromium worker start now reasserts the current browser rules and page
scanner registrations before waiting for native communication. A recent native
heartbeat is no longer used as a reason to skip enforcement recovery. Existing
standalone mode, unlocked choices and commitment rules remain unchanged.

Regression tests remove browser rules and scanner registrations while preserving
a recent heartbeat and an active strict commitment. Recovery must restore both
without an unnecessary native call. This does not execute after uninstall: a
deleted extension cannot enforce anything itself.

## Additional recovery layers implemented locally — 2026-10-13

- Chromium checks the complete scanner registration contract: permitted-site
  scope, frame coverage, document-start timing, origin fallback, persistence,
  isolated execution world and absence of narrowing exclusions. Matching script
  file names alone no longer suppress repair. Visual opt-out is still respected.
- Mac guard retains the last locked authority's allowed-app constraint across
  IPC outages and rejects editable local heartbeats during that remembered lock.
  Both normal observation and delayed force-close use the same evidence. Missing
  IPC remains visibly unavailable, not cached green filtering status.
- iPhone/iPad share validated mirrored daily-threshold markers. Damage to one
  copy can use the other; unreadable evidence does not authorize clearing a
  previously applied DeviceActivity shield. No counters/permissions are restarted.
- Mac setup now validates the on-disk login agent's ownership, write protection,
  executable/account arguments and crash-restart settings rather than accepting
  any root-owned file. This is configuration evidence, not a loaded-job test or
  proof of actual restart. Missing/invalid configurations do not count as ready.

No new instant stop control, uninstall hook, administrator account, remote
watcher or network policy is added. Existing commitment/recovery routes remain.
These changes recover existing chosen layers; they do not make uninstalling an
extension, OS permission revocation or administrator recovery impossible.

Extension acceptance: full extension suite (100 worker checks) and 70 isolated
Helium UI checks passed. Mac outage acceptance: 97 focused native tests passed.
Mobile acceptance: 43 hosted tests passed, followed by all five new marker
regressions against final source; 21 Python mobile tests passed. Combined Mac
acceptance passed all 60 selected tests, including 28 setup tests with the eight
new startup-configuration fixture methods. Arabic localization validation passed.
Physical
deletion/profile/permission acceptance remains pending.

Known remaining hardening work includes monotonic Mac guard warning timing,
verified managed extension distribution and physical independent-layer failure
tests. This update does not claim those gaps are closed.

## Priority order for further releases

1. Verify native browser-guard detection with disabled and removed extensions,
   multiple profiles, private windows, app restarts and sleep/wake on physical
   devices. Never borrow a healthy sibling profile's evidence.
2. Offer explicit managed-browser setup only for supported browsers and a valid
   distributable extension. Confirm effective policy, not just profile creation.
   Keep administrator changes and recovery instructions visible and consensual.
3. Verify independent network enforcement with extension and app unavailable.
   DNS/domain filtering is not classification inside mixed-content platforms.
4. Validate iPhone/iPad guardian authorization and Screen Time restrictions on
   physical devices. Individual self-control authorization is not proof that
   app deletion is prevented. Safari coverage is not every native app.
5. Publish a signed, tested release with a protection-health screen that
   distinguishes verified, unavailable and unsupported layers.

No hidden administrator accounts, irreversible uninstall traps, browsing logs
or secret changes to real-device policies are part of this work. An owner with
administrator/recovery access remains able to bypass protections.

## Official platform references

- [Chrome force-installed extension lifecycle](https://developer.chrome.com/docs/extensions/develop/concepts/extensions-update-lifecycle): managed force-installed extensions cannot be disabled or uninstalled through the normal extension controls while the policy applies. This is not a guarantee for every Chromium fork or an unpacked extension.
- [Apple parental controls](https://support.apple.com/en-euro/105121): Screen Time can restrict app deletion in parental-control setups; recovery and authorization conditions matter.

These deployment steps are not enabled or verified merely by the local code
change above. See APPLE_PLATFORMS.md and SELF_CONTROL_COMMITMENT.md for limits.
