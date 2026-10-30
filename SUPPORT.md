# Hisn support

Hisn is built and supported through this repository. Before asking for help,
check the platform guide and the honest limits below; most reports are about a
layer that is not actually on, a browser profile that has not reloaded, or a
device that was never provisioned as managed.

## Start with the right guide

| Platform | Guide |
| --- | --- |
| Everything, in order | [SETUP.md](docs/SETUP.md) |
| Browser extension | [EXTENSION_USAGE.md](docs/EXTENSION_USAGE.md) |
| Mac | [Mac user guide](docs/USER_GUIDE.md) · [Mac setup](docs/SETUP.md) |
| iPhone and iPad | [iOS README](ios/README.md) · [supervised setup](docs/IPHONE_SUPERVISED.md) |
| Android | [Android README](android/README.md) · [0.8.0 release record](docs/ANDROID_0.8.0.md) |
| Windows browser policy | [WINDOWS_SETUP.md](docs/WINDOWS_SETUP.md) |
| Routers and DNS | [ROUTER_COMPATIBILITY.md](docs/ROUTER_COMPATIBILITY.md) |
| What is verified vs. still manual | [TEST_MATRIX.md](docs/TEST_MATRIX.md) |

## Common problems

### The extension says "App offline"

1. Run `python3 macos/check_extension_bridge.py`. It is read-only and names the
   app, bridge, browser manifest or extension ID that is wrong.
2. Reload the extension in `chrome://extensions`, then restart the browser. A
   Chromium process caches native-host manifests.
3. If the extension was installed from a store, the store assigned a different
   ID. Re-run `macos/install_native_host.sh` with that ID; see
   [EXTENSION_BRIDGE.md](docs/EXTENSION_BRIDGE.md).
4. A silent bridge during a lock is treated as tampering and the extension fails
   closed to Strict. That is deliberate; fix the bridge rather than disabling it.

### The extension is on but a page is not blocked

- Check the status: the installed list generation, its age and whether only the
  built-in list is loaded. Use **Update block list** in Settings.
- Domain rules cover listed hosts, not every page on every site. Page-text
  checks are separate and can miss unreadable, image-only or encrypted content.
- Strict mode is stronger than the default. Do not turn it off to make a test
  pass.
- A deleted or disabled extension enforces nothing. The Mac filter and DNS are
  the independent layers; see [COVERAGE.md](docs/COVERAGE.md).

### The Mac app says a layer is missing

Run `macos/verify_enforcement.sh` (read-only). It reports each layer separately:
profile, filter, browser host, admin/account status. A single green light is not
the goal; every layer has its own gap. If the filter is off, open the app's
setup and approve the system extension. If a layer was removed by an
administrator, only an administrator can restore it.

### iPhone/iPad: Screen Time access was turned off

iOS removes Hisn's filter, site list and app shields when Screen Time access is
revoked. Open the app's setup and grant access again. A vault passcode stays
sealed until its date; a supervised profile can only be removed with its removal
password. If the device is not supervised, the owner can reset the Screen Time
passcode with their Apple Account. Hisn cannot prevent that and says so.

### iPhone/iPad: the vault passcode will not reveal

The date must have passed on **both** the device clock and Apple's server time.
Connect to the internet, then try again. If the phone's clock is ahead of
Apple's, the app refuses and keeps it sealed. A damaged device-only record stays
sealed; it is not replaced with a new passcode. Use the documented recovery
(Apple Account reset or erase) if you are locked out.

### Android: "Not managed" or a restriction shows "out of sync"

"Not managed" means Hisn is not this user's device owner, which is the normal
state of an ordinary install. Only a factory-reset, account-free device can be
provisioned (`adb shell dpm set-device-owner …`). Never provision a daily phone
in place. If a managed device shows out of sync, open **Manage uninstall
protection** and apply; the app reads Android's own state back. If Android
rejects it, the app says so and does not claim protection.

### Android: the DNS or accessibility layer stopped

Android can stop a VPN service or an accessibility service after a reboot, a
battery optimisation or a permission change. Reopen Hisn and check the status.
An accessibility service can be turned off by the person; Android has no
supported API that makes it undeletable. The app must say "nothing is guarding"
rather than stay green.

### The block list will not update

The update needs `raw.githubusercontent.com`. A failed or refused update keeps
the previous signed generation; it does not fall back to an unsigned resolver.
Check the network, then retry **Update block list**. The list branch has to be
published for a new generation to exist; the bundled seed is the fallback.

### Something is blocked that should not be

Use **Always allowed** for the site in the extension, or the app's allowlist.
That decision stays on the device. Then report the false positive with the
domain only (not the page content) so the list review can look at it. Medical,
sex-education, recovery, religious and LGBTQ+ pages are explicit false-positive
priorities.

### I need to remove Hisn or stop a lock

- The extension, the Mac app and an ordinary Android install can be removed by
  the device's user. A commitment can delay changes inside Hisn; it does not
  remove the OS controls.
- A passcode vault, a family guardian, a supervised iPhone/iPad profile and a
  device-owner Android setup have disclosed exits: the date, the guardian, the
  removal password or **Remove managed mode**. The app shows which one applies.
- No setup survives a factory reset from recovery, a device owner removing the
  appointment, ADB/root, or a different device. Hisn never claims otherwise.

## Reporting a bug

Open an issue at
<https://github.com/hamzabinjehad/AdultContentBlocker/issues> and include:

- app, extension and OS versions; the platform and the layer that failed;
- the exact steps and what you expected instead;
- whether the layer was verified, attested or unavailable in the app;
- a log or screenshot **only if it contains no browsing history, adult URL,
  personal name, message or face**. Redact or reproduce with public/example pages.

**Do not post** browsing history, private messages, screenshots of adult content,
your passcode, signing keys, or the contents of `~/Library`/app data. A domain
(or a single host that should not be blocked) is enough for most reports. If a
reproduction needs a sensitive page, describe the structure instead of sending
it, or use a synthetic fixture.

## Security reports

Report vulnerabilities privately through GitHub Security Advisories:
<https://github.com/hamzabinjehad/AdultContentBlocker/security/advisories/new>.
Do not open a public issue for a bypass that requires a new technique, a signing
key, a privilege escalation or a way to read another person's data. We will
confirm the report, agree on a disclosure date, and credit you unless you ask
otherwise.

## Consent, abuse and emergencies

- Install Hisn only on a device you own and consent to, or on a minor's device
  as a parent/guardian using the platform's parental controls. Do not install it
  covertly on another adult's phone; that is not a use this project supports.
- Hisn must never block emergency calls, the lock screen, or a factory reset
  from recovery. If a setup does, treat it as a bug and report it.
- If you are in danger, contact your local emergency service. Hisn is a
  self-control and family tool, not a safety or monitoring service.
- If you are struggling with compulsive use, the app's pause screen is a small
  tool, not treatment. A qualified professional or a local support service is
  the right next step.

## Privacy

Read [PRIVACY.md](PRIVACY.md). In short: matching stays on the device, there is
no account or telemetry, the partner learns only that a release was requested,
and the list updater reveals only which repository file was fetched.
