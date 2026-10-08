# Self-control first, across Apple platforms

Hisn's primary scenario is an adult voluntarily choosing protection in advance,
not turning an adult Apple account into a child account. External helpers are
optional reinforcement, not prerequisites for recording a commitment.

## Implemented in this change

- iPhone and iPad share one universal target and one commitment implementation.
  Choose 1, 7, 30 or 90 days with explicit confirmation. The app has no shortening
  or cancellation action during the plan. Defaults and a device-local Keychain
  item mirror the deadline; storage failure is displayed as unknown, never as
  proof that a lock ended. Wrong-type stored objects count as damaged data,
  not a new unlocked installation; a valid surviving mirror can restore the
  latest deadline. Defaults writes require immediate readback before reporting
  success (not a guarantee of durable disk storage). Tests use isolated storage.
- Personal Screen Time is a separate, explicitly authorized adult flow. It sets
  Apple's automatic adult-content web filter in a named Managed Settings store.
  Existing approved access is reused, not replaced by individual authorization.
  The app offers no filter-off action while a commitment is active, including
  direct action-method calls. After expiry the person may turn off this named
  filter; Hisn does not revoke system authorization or clear other apps' stores.
  During an active plan, refreshing Hisn reasserts its previously enabled
  automatic filter when commitment/history storage are readable and Screen Time
  authorization remains approved. It never enables an unchosen layer, requests
  child authorization automatically, or repairs revoked OS permission. This is
  foreground reconciliation, not an always-running anti-removal service. Consent
  evidence is session-local: a layer already missing at launch is not enabled
  from historical readiness alone. Layers turned off before a plan stay off.
- macOS adds an opt-in fixed horizon to its existing lock. New UI plans default
  to this mode, with consent. The horizon is persisted in lock mirrors and the
  filter authority, cannot be reduced through normal proposals, and is used by
  the same effective-deadline calculation in app, bridge and filter. A forged
  or queued 48-hour self-release cannot end the fixed horizon early. Existing
  locks retain their prior behavior. Partner-authorized recovery remains.
- Network DNS, device DNS, Safari rules and Screen Time remain independent
  layers. Ending a commitment does not automatically disable them. The mobile
  setup includes official router-DNS guidance; Mac retains its existing router
  discovery/assistance and DNS checks. No new router mutation occurs here.

## What this does not promise

An in-app deadline is not an OS deletion lock. Apple explicitly permits revoking
individual Screen Time authorization; it does not implicitly prevent deletion.
Child authorization requires a real child account and guardian consent. Hisn
does not silently request that mode for an adult.

The mobile timer uses monotonic elapsed time including sleep while its process survives, so changing the
wall clock during that process does not immediately finish a plan. After process
termination/reboot the saved deadline depends on the wall clock. No trusted-time
server or root authority is implemented on mobile; clock changes across relaunch,
device erasure, deleting persistence, removal or permission revocation remain
bypasses. Keychain retention after removal is not a guarantee of enforcement.

Mac, iPhone and iPad show a session-local warning when wall time differs from
the sleep-inclusive monotonic clock by more than two minutes, or the clock
sample is invalid. The warning does not rewrite deadlines or alter Mac recovery.
Restoring clock agreement clears it; it does not establish trusted time across
relaunch. Mobile keeps its last valid elapsed-time sample if the counter is invalid.

Screen Time's configured policy is not proof of blocked traffic or all-app
coverage. Apple classification may miss content. Safari needs its four parts
enabled; DNS is not VPN-proof. Router DNS only helps on that network. Router
instructions are manual, not a verified phone-side router-installation feature.

macOS administrators and physical device owners retain OS recovery powers. No
hidden uninstall hooks, administrator demotion, router-password destruction,
all-app removal prohibition or silent OS setting changes are introduced.

## Browser extension removal and disconnection

On Mac, browser guarding already runs during an active commitment. The
**Blocking Rules → Browser protection → Require the extension outside a lock**
switch extends that behavior outside a commitment with explicit confirmation.
It is off by default; disabling it does not disable the guard inside a lock.
The UI does not offer turning an enabled requirement off during a lock.

Supported Chromium browsers need their own extension heartbeat. A missing or
stale heartbeat produces a visible warning followed by a termination request.
Unknown non-exempt browsers receive a shorter warning. A known active filter
authority's empty check-in record no longer falls back to editable app defaults;
that fallback remains only when no authority answers.

The Mac runtime also reads supported Chromium browsers' standard `Default` and
`Profile *` preferences off the main thread. Two consistent readings at least
ten seconds apart are needed before explicit extension disablement/removal
overrides a fresh heartbeat from another profile. Routine reads are fifteen
seconds apart. A relaunch prompts a fresh read before cached profile evidence
may close the browser again. Profile-only closure also needs a read started at
or after the original warning deadline, even if the browser never restarted;
awaiting that read does not reset the countdown. The force-close fallback
independently rereads the same still-confirmed folders and rechecks consent and
process identity. A recovered or unconfirmed reread prevents profile-only force
closure; an independently stale heartbeat can still require closure.
Reenablement clears profile-loss evidence. Confirmed loss cannot earn healthy
startup grace from another profile's heartbeat.

The reader accepts the modern disable-reason representation without assuming
an absent legacy `state` means disabled. Malformed, conflicting or unreadable
sources remain unconfirmed, not removal evidence. Reads are bounded to twenty
megabytes per browser per scan, including malformed inputs. The UI distinguishes
confirmed off profiles from unconfirmed checks. Neither category proves page
content classification or authenticates user-editable preferences. Existing
setup/managed-deployment checks retain their separate conservative semantics.

Startup/wake grace is 60 seconds. Heartbeats become stale after 150 seconds;
the ordinary disconnected-browser warning is 45 seconds, uncovered-browser
warning 15 seconds, and repeat-closure warning 5 seconds. Checks run every five
seconds. A browser refusing termination may be force-closed after ten more
seconds, only if enforcement still applies and connection has not recovered.
Pending force-closure is cancelled when guarding stops or coverage recovers.
These windows are recovery allowances, not immediate denial of web access.

Unverified browser restarts do not reset either initial grace or a warning
countdown. A previously verified healthy browser may receive one fresh startup
grace after a normal restart; startup grace alone cannot earn another one.
This evidence is session-local: restarting Hisn or changing the system clock
is not solved by this ledger.

Safari, link-routing apps and previously allowed exceptions retain their
existing compatibility exemptions. Safari exemption does not prove Screen Time
or network protection is configured. A heartbeat is browser-bundle evidence,
not proof that every profile or private window is protected. Standard-profile
checks improve known removal detection; custom profile paths, guest/private
windows and unreadable profiles remain unverified and can remain bypasses.
Force-quitting
Hisn, administrative changes and external device recovery remain bypasses.
The separately installed LaunchAgent can restart Hisn; a development app alone
does not establish that installation or persistent enforcement.

Stronger resistance to ordinary extension deletion needs separately consented
managed browser installation, a working published extension ID/update source,
and verified profile/private-window policies. Do not install a force-install
profile targeting an unpublished unpacked extension. Follow
[managed deployment and verification](CHROME_ENFORCEMENT.md).

iPhone/iPad cannot reproduce this Mac process-closing guard. The shared mobile
app now explains how to explicitly select browser or mixed-content apps, choose
**Always block**, and apply Screen Time shields. Hisn refuses in-app weakening
of that saved selection during commitment, but individual adult authorization
remains revocable at the OS level. Mobile app shields, Safari filtering and
family DNS remain independent layers. No new permission or shield is enabled
merely by displaying this guidance.

Mobile guidance explicitly says to select every browser app the person wants
shielded: Safari rules alone do not cover every other browser. No Mac-style
profile reader or arbitrary-browser termination is implemented on iPhone/iPad.

`x.com` is a mixed-content service, not an adult-only domain. If a person wants
to avoid the entire service, they can deliberately add the whole domain/app to
their blocking rules. Content scanning cannot guarantee classification of all
posts, images or videos inside it.

## Acceptance before distribution

Use signed entitled builds on real iPhone and iPad. Verify personal consent,
actual Safari/web filtering, authorization revocation and reapproval, app
relaunch with an active plan, persistence failure and expiry. Verify filter-off
is rejected during the plan and allowed afterward. Test Mac authority/mirror
reconciliation and partner recovery. Keep essential services accessible.

## Strengthening a running commitment

Both Mac and mobile now ask for confirmation before adding seven days. The new
date is the old end date plus seven days, never today plus seven days. Mobile
keeps its original start and rejects a total lifetime over 365 days; a failed
save retains the original session and disables changes until storage is healthy.

Mac fixed-lock extension advances both the lock deadline and its fixed
commitment horizon. The authority rejects a proposal that extends a fully fixed
lock but leaves its commitment horizon behind. Negative, non-finite, sub-minute
or over-365-day increments are rejected. Legacy non-fixed locks and
partner-authorized recovery retain their existing behavior.

This closes an in-app consistency gap, not Apple's external deletion or
permission-revocation paths. Physical-device enforcement is still an acceptance
requirement; a successful unit test is not a real-world blocking test.

## Detecting unconfirmed protection

Mac, iPhone and iPad retain a bounded device-local set of layer identifiers that
were previously ready, and name any that are no longer confirmed. Two versioned
copies preserve this baseline across supported relaunches. Mac identifiers are
independent of translated display names. The baseline is configuration history,
not an audit log or evidence of activity while the app was closed. A fresh
installation has an empty baseline and is not accused of disabling layers.

Unreadable history produces a separate warning while current layer checks
continue. Corrupt history is not automatically overwritten. Failed history
writes keep observations in the current session and retry later; a missing copy
can use its valid surviving mirror. Losing both copies, removal or device erasure
can still erase this baseline. Remembering a layer never marks it currently ready.

Mobile separately reads saved app-restriction validity, Family Controls
authorization and Device Activity registration. Permission revocation is
observed while the app is running, and the status is refreshed when returning
to the app. Missing registration is not silently repaired by restarting usage
counters. Always-block restoration uses the already authorized saved selection;
no new permission is silently requested. Corrupt or wrong-type saved data cannot
be replaced through Apply as though no previous restriction existed.

Mac future/non-finite browser heartbeat ages and non-positive filter list counts
are unconfirmed/problem evidence, not active protection. Existing filter,
browser guard, authority and recovery behavior otherwise remains in place.
These checks cannot stop system-level deletion or revocation and do not prove
actual blocking. Test the recovery steps on signed physical devices.

Sources:

- [Apple: individual authorization and revocation](https://developer.apple.com/videos/play/wwdc2022/110336/)
- [Apple: Family Controls entitlement](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)
- [Apple: Web Content Settings](https://developer.apple.com/documentation/managedsettings/webcontentsettings)
- [Cloudflare: router DNS](https://developers.cloudflare.com/1.1.1.1/setup/router/)

## Mobile app/time extension (Mac left unchanged)

Selected apps/categories/websites can be shielded continuously or after a
combined daily threshold. A separately embedded Device Activity monitor uses
an App Group to read the selection and its named budget store. Empty selections
are rejected so they cannot accidentally monitor all device activity. During
commitment additions and tighter limits are allowed; removals, more time and
downgrading always-block are refused. Remove-rules is only available outside
commitment. This is still subject to Apple's authorization and OS callbacks.

An authorized app refresh and same-day monitor registration reapply a shield
when the saved daily threshold marker matches today. This restores configuration
without restarting monitoring or resetting usage counters. A prior-day marker
does not reapply a shield, and the app does not infer that a limit was reached
when the OS callback was never received. Day markers depend on the local calendar;
this is not trusted-clock or time-zone tamper protection.

Router exports accept manually reviewed hostnames, not opaque Apple tokens or
guessed app-server mappings. AdGuard Home rules have no usage timer. Importing
them affects all clients of that resolver and requires a separate acknowledgement.
They survive app deletion only because the external server owns its imported
rules; deleting the app cannot guarantee that every way to access a service is
blocked. Router scheduling remains model-specific manual setup. No Mac app,
filter, lock, schedule or app-blocking behavior is changed in this extension.

- [Apple Device Activity events](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent)
- [Apple shield settings](https://developer.apple.com/documentation/managedsettings/shieldsettings)
