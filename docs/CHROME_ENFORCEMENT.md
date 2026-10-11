# Browser protection and independent managed layers

Hisn's extension can filter page text on mixed-content sites without blocking
the entire site. Optional local visual inspection is another extension layer,
not proof that every image or video can be read or classified. DNS and socket
filters see domains/flows, not the contents of every encrypted post.

An ordinary unpacked extension remains removable. Stronger removal resistance
requires consented, effective browser management and a recovery arrangement.
Exporting a configuration file is not enforcement, and a device owner with
administrator/recovery access can still undo protections.

## What the app guard adds

When the Mac browser guard is required, Hisn warns and closes supported,
non-exempt browser processes that lose their verified extension check-in,
after the configured recovery grace period. Process-scoped check-ins prevent
a healthy, separate browser process from covering a disconnected process.
This requires the matching app, bridge and filter versions and Hisn to run.

A process check-in is **not** proof of protection in every profile, guest or
private window inside that process. Runtime checks of standard-profile
preferences add evidence of removal/disablement, but custom data directories
and inaccessible/corrupt preferences are not a universal coverage check.
See [exact guard behavior and limits](SELF_CONTROL_COMMITMENT.md#browser-extension-removal-and-disconnection).

The extension's browser-only protection does not require the app connection.
Conversely, independently installed DNS/browser profiles do not disappear just
because the app or extension is removed. Each layer needs its own verification.

## Browser support: distinguish three kinds

| Layer | Configured scope | What is not yet proved by configuration |
| --- | --- | --- |
| Chromium extension | Manifest V3-compatible desktop browsers | Every fork/version/API, profile and private-window combination |
| Mac native link and guard catalog | Chrome Stable/Beta/Dev/Canary, Edge, Brave, Vivaldi, Opera, Arc, Chromium, Helium | A working live bridge or enforcement on every listed browser |
| Profile policy candidates | The above plus Opera GX, Thorium, Yandex, Comet, Dia, Atlas; Firefox has separate DNS/private-browsing policy | That every fork reads its policy domain or accepts the update service; these extra candidates have no configured native link |

The generator retains its existing broad candidate catalog by default.
Use repeatable `--browser` flags to request only the browsers actually intended
for a deployment. Do not label a candidate as supported merely because its
policy names appear in its binary.

Chrome's release channels have different user-data folders but share the
configured system native-host folder. [Chromium's user-data directory documentation](https://chromium.googlesource.com/chromium/src/+/HEAD/docs/user_data_dir.md)
and [Chrome's native-messaging documentation](https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging)
describe the underlying path rules. The installer offers a read-only inventory:

```bash
macos/install_native_host.sh --user --print-targets
macos/install_native_host.sh --print-targets
```

Edge's app identity is `com.microsoft.edgemac`; its managed policy domain is
`com.microsoft.Edge`. Browser identities and policy domains are not interchangeable.

## Consented setup sequence

1. Package and publish the extension through an accepted update service.
   `extension/package.sh` produces a clean upload ZIP. An unpacked folder is
   not a managed update feed. Use the real published extension ID, not an
   assumed development ID.
2. Add that published ID to `HisnExtensionIDs` in the Mac app's `Info.plist`
   before rebuilding. Native-host `allowed_origins` must match that exact ID.
3. Test the update feed and policy on a disposable/test deployment first.
   Google's guidance states that force-installed items cannot be removed by
   ordinary users when management applies. This is not a claim that the owner
   can never remove management. [Google's force-installation guidance](https://support.google.com/chrome/a/answer/6306504?hl=en)
   and [Mac configuration instructions](https://support.google.com/chrome/a/answer/7517624?hl=en).
4. Generate the optional profiles and explain the scope before the user
   installs them. The browser profile requests disabling incognito, guest/new
   profiles, developer tools, browser DoH overrides and rival extensions.
   These are significant restrictions, not invisible defaults. `--lock-settings`
   is separately opt-in because hiding Network settings also affects repairs.
5. Agree on recovery and password custody. A standard daily account with
   separately held administration can strengthen resistance; do not change
   accounts or install profiles silently. Self-managed administrator access
   remains an override. Keep a usable, disclosed recovery path.

### Separate profiles survive losing one layer

The new `--separate-layers` mode writes **two files**, without installing either:

```bash
python3 profile/make_profile.py --resolver cloudflare \
    --browser com.google.Chrome --browser net.imput.helium \
    --extension-id YOUR_PUBLISHED_32_LETTER_ID \
    --separate-layers --out dist/hisn-hardening.mobileconfig --print-password
```

Replace the placeholder with the real 32-character ID (letters a–p). This writes:

- `hisn-hardening-network.mobileconfig`: system filtering DNS and the Private
  Relay restriction, without a Hisn app/extension dependency.
- `hisn-hardening-browsers.mobileconfig`: selected browser policy and any
  explicitly requested settings restrictions, independently removable from
  the network profile.

The files have different profile identifiers, UUIDs and generated removal
passwords. Supplying `--removal-password` deliberately reuses the operator's
chosen password; no independence of credentials is claimed in that case.
Without `--separate-layers`, the original single-profile output is preserved.

The ID and HTTPS update URL are validated before generating force-installation
policy, including for imported Python builders. Syntax validation is **not**
proof of publication. With no extension ID, no blanket extension block or
force-installation request is emitted, so a development copy can still load.
Never apply the blanket-block profile against an unpublished extension: it can
block the unpacked copy while the managed copy fails to install.

Profiles contain their removal password in plain text, even when it is not
printed. Outputs are owner-readable (0600), but the owner can still read them.
Keep them off the protected user's accessible storage, out of source control,
and escrow the original secret before installation. Re-running the generator
creates a new secret; it does not recover an installed profile's password.

## Verify real behavior before calling it protected

On each intended browser/version:

1. Open the effective policy page (`chrome://policy`, `edge://policy`, or the
   browser's supported equivalent). Reload policy and check each requested
   key's value, scope, source and status. Unknown/ignored/error keys are failures.
2. Verify the **published ID** is installed by policy, runs in the intended
   profiles, cannot be disabled/removed by the ordinary daily account, and
   reconnects to the correct app. Test update delivery and restart as well.
3. Verify private/guest/new-profile restrictions actually prevent the intended
   bypass, or explicitly validate extension permission in the retained modes.
4. Test known-safe fixtures for domain/text filtering and any enabled visual
   inspection. Include unreadable cross-origin media and failure states;
   an uninspectable frame must never be described as verified safe.
5. Confirm the independent DNS profile still operates after removing the app
   in that **test deployment**, and that removing one test profile leaves the
   other in place. Do not perform destructive tests on a user's real setup.

Google recommends a staged deployment and checking actual policy application,
not merely the profile file. [Deployment verification](https://support.google.com/chrome/a/answer/9020078?hl=en).
For Edge, consult its own [force-installation policy requirements](https://learn.microsoft.com/en-us/deployedge/microsoft-edge-policies/extensioninstallforcelist),
including its accepted update source and management prerequisites.

Hisn's Protected Setup and `macos/verify_enforcement.sh` provide read-only
evidence about the controls they implement. They are supplementary diagnostics,
not certification of every fork/window or visual accuracy. The shell verifier
now inventories the eleven configured native-linked browsers, including the
Chrome channels, but not every additional policy candidate. An observed
managed preference is not proof that the browser accepted it.

## Remaining limits

Helium/Brave/other fork policy delivery and update-source acceptance require a
live managed test; this change does not install or certify those policies.
Safari does not use Chromium native-host or extension-management controls.
iOS/iPadOS Safari needs its separately authorized extension and Apple controls.

DNS cannot identify a sexual clip inside an otherwise allowed encrypted site.
Visual models have false positives/negatives and cannot read every protected
or cross-origin image/video. A running-app guard does not survive losing all
app execution on its own. Administrator actions, Recovery, alternate tunnels,
unsupported apps/browsers and other devices remain separate risks. No layer or
combination here makes Hisn universally undeletable or guarantees zero adult
content.
