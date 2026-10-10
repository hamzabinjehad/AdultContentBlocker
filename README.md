# Hisn — content protection for the Apple ecosystem

A deep, transparent, **Arabic-aware** adult-content filter — designed to run
*under* Apple's own lock (Screen Time + Family Sharing, or MDM), not to reinvent
it. Apple gives you the lock; this gives you the coverage Apple's black-box
filter misses. **Start with [`docs/POSITIONING.md`](docs/POSITIONING.md)** — why
this exists next to Screen Time, and what it should and should not try to be.

**Read [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) before changing anything.**
Every design decision in this repo is downstream of that document, and several
of them look wrong until you know which bypass they close.

For the network-first protection roadmap and current limits, see
[`docs/NETWORK_FIRST_PROTECTION.md`](docs/NETWORK_FIRST_PROTECTION.md).
The app starts setup with a guide for any router's capabilities. The
[`network/` tools](network/README.md) plan manual/device fallback paths and
atomically publish verified signed DNS rules for administrator import. Live
router configuration is not automated yet.
The Cloudflare guide can sample DNS blocking on this Mac; it keeps that result
separate from verified gateway enforcement.

**To actually deploy it on a Mac, follow [`docs/SETUP.md`](docs/SETUP.md)** —
the whole thing in dependency order, with what each step needs and who does it.
`macos/install.sh` does the software half in one command. Then
[`docs/DOGFOOD.md`](docs/DOGFOOD.md) is how to live with it and try to break it,
and [`docs/LAUNCH.md`](docs/LAUNCH.md) is what stands between that and publishing.

---

## What's here

```
blocklist/       list pipeline: fetch → merge → sign, keyword layer, seed bundle  (Python)
network/         capability planning + verified DNS-rule publication             (Python)
profile/         .mobileconfig generator: DNS, DoH, Private Relay                (Python)
extension/       Chrome/Edge MV3 extension                                       (JS)
macos/           the app + content filter                                        (Swift)
ios/             universal iPhone/iPad app, Safari blockers + optional family DNS (Swift)
ipados/          iPad-specific documentation; shares the ios/ target
shared/apple/   portable Apple-platform configuration and protection-state code
seed/            signed starter list + keyword layer, bundled into the filter
partner/         the accountability partner's page: key, and signed approvals    (HTML)
.github/         CI on every PR; daily signed rebuild and publish                 (Actions)
```

The new iPhone/iPad development version is documented in [`ios/README.md`](ios/README.md).
See [`docs/APPLE_PLATFORMS.md`](docs/APPLE_PLATFORMS.md) for platform boundaries.
It shares the signed core list, not macOS privileges: Safari domain protection,
an independently enabled Safari text-scanning extension and optional family DNS
are implemented; all-app content scanning and Mac/phone sync are not. A signed
real-device build is required before relying on phone enforcement.
See [browser-first mixed-content protection](docs/BROWSER_FIRST_PROTECTION.md)
for individual X/Twitter post checking and optional native-app restrictions.

## Testing

For the planned signed beta, see [Using Hisn](docs/USER_GUIDE.md).
Maintainers: [Release runbook](docs/RELEASE_RUNBOOK.md) and
[release validation record](docs/RELEASE_VALIDATION.md).

```bash
./test.sh              # python, seed, extension, release, macos, ios
./test.sh python seed  # the suites that run on any OS
./test.sh browser      # the page scanner in headless Chrome (CI always runs it)
./test.sh ios          # mobile unit tests + a locally available iOS simulator
extension/eval/run.sh  # score the evaluation corpus; a blocked benign page fails
```

One entry point, six default suites, and the same script CI runs. `python` is the
list pipeline, keyword layer, seed tool, profile generator, and network tools; `seed` is the
gate that every shipped artifact matches the signed seed manifest; `extension`
is the JavaScript suites plus a check of the packaged zip; `release` validates
distribution settings and installer behavior; `macos` is
`xcodebuild test`; `ios` runs native mobile tests on an available iPhone Simulator.
The default run requires Xcode and an installed iOS Simulator runtime; explicit
suite names allow a narrower run. A suite whose toolchain is missing fails rather than skips —
"the tests passed" has to mean they ran.

## Protection layers

| Layer | Closes | Defeated by |
|---|---|---|
| Network DNS (administrator setup) | Listed hosts on devices using the resolver | Outside DNS, tunnels, another network, gateway reset |
| Browser extension | Known domains and scanned page content in supported browsers | Another browser, disabling the extension, classification misses |
| Configuration profile | Supported DNS, Private Relay, and browser settings | A tunnel; an administrator removing the profile |
| Content filter (system extension) | Host/application decisions on new Mac flows | Provider loss, established flows, unidentified destinations outside strict mode, recovery/admin changes |
| Accountability partner | Ordinary user loosening an active policy | Guardian authority or recovery; a separate unprotected device |
| Browser guard (in the app) | Warns/closes disconnected browsers and repeatedly confirmed off standard profiles during locks or outside-lock opt-in | Force-quitting Hisn, explicitly trusted unknown apps, unreadable/custom profiles, guest/private windows, admin changes |

The load-bearing layers are the bottom two. The top two are convenience and
defence in depth — they are not what makes this work.

Mac **Blocking Rules → Browser protection** has an optional **Keep browser
protection on** switch, off by default with explicit confirmation. Active locks
always require guarding. Loss of a verified extension connection or confirmed
standard-profile removal starts a visible **60-second recovery countdown**.
Known unsupported browsers, including Safari, also close and cannot use app
exceptions. An unrepaired relaunch cannot renew the warning. Check-ins are
scheduled every thirty seconds; detection is periodic, not instantaneous.
This is best-effort process closure while Hisn runs, not an immediate traffic
block or uninstall prohibition.
See [browser-removal behavior and limits](docs/SELF_CONTROL_COMMITMENT.md#browser-extension-removal-and-disconnection).

The installed Mac app starts at login for the protected account and keeps
running in the background when its window closes or Quit is selected. Its
LaunchAgent restarts it after a crash or force-quit. Logout, restart, shutdown
and sleep retain their normal system behavior. See [setup](docs/SETUP.md#step-1--the-app--macosinstallsh)
for the account scope and administrator removal command.

Each new Mac app process opens on Setup, not on a remembered successful visit.
Setup and Overview distinguish partial blocking from current laptop protection
checks: a live nonempty filter response, healthy policy, protected installation
files, detected standard browser profiles, a recent extension connection, and
the explicitly enabled outside-lock browser requirement. Browser warnings and
trusted app exceptions prevent a passing verdict. These are configuration and
liveness checks, not an end-to-end blocking test or a guarantee of all-content
coverage. Administrator/partner hardening is shown separately. A development
build without the filter's signing prerequisites cannot pass these checks.
Hosts-file coverage remains available but is reported as partial protection.

Why the content filter adds coverage beyond DNS: VPNs and encrypted resolvers
can bypass router DNS. Hisn's `NEFilterDataProvider` evaluates new Mac socket
flows by available hostname and application identity. This is not a guarantee
of identifying destinations inside every tunnel. Unknown destinations pass in
normal blocklist mode and are denied in strict mode; already-open flows are not
re-evaluated by the current implementation. Verify the supported VPN/proxy and
essential-service cases on a signed installed Mac before relying on them.

---

## Building

### Blocklist

```bash
cd blocklist
python3 keys.py generate --out ../keys          # once, offline. Guard the .pem.
python3 build.py --out ../dist --sign-key ../keys/blocklist_ed25519.pem
python3 keys.py verify --dist ../dist --pub ../keys/blocklist_ed25519.pub
```

Produces ~982k domains from the upstream sources in under a minute.
`domains_core.txt` (~148k, high-confidence sources only) is what the browser
extension ships; the full list goes to the network filter, which has no rule
budget. `terms.json`, the keyword layer, is compiled from `blocklist/terms/`
in the same build and covered by the same signature.

For CI, put the PEM in the `BLOCKLIST_SIGNING_KEY` secret of the `list-signing`
environment, which admits `main` only (docs/SETUP.md). The workflow runs the
whole test suite first, then `blocklist/publish_guard.py` compares the build
with the list it would replace and refuses one that lost a core source, fell
below 80% of the previous counts, or has a file near GitHub's size limit — a
list that quietly shrinks is the worst failure this system has, because it
looks like it is working.

### Seed bundle

The filter and the extension each ship a starting list so a machine that has
never completed an update still enforces something. Every one of those files
comes from ONE signed build and is checked against its manifest:

```bash
python3 blocklist/seed.py verify                 # what CI and the tests run
python3 blocklist/seed.py sync --build --sign-key keys/blocklist_ed25519.pem
```

`verify` fails if a shipped file is missing from the signed manifest, differs
from it, or — for `terms.json` — differs from what `blocklist/terms/` compiles
to today. So a term edit is not done until the seed is re-cut and re-signed.
Without the private key on your machine, dispatch the *Build and publish
blocklist* workflow on your branch with **refresh_seed** ticked; CI, which
holds the key, commits the re-signed bundle to the branch. Details in
`blocklist/seed.py`.

### Hardening profile

```bash
cd profile
python3 make_profile.py --doh https://dns.example.com/dns-query \
                        --extension-id <chrome-web-store-id> \
                        --out ../dist/hisn-hardening.mobileconfig
```

Add `--lock-settings` to also hide the Network, Users and Screen Time panes.
That trade is real: the user can no longer fix their own Wi-Fi. Add
`--supervised` only if the Mac is genuinely supervised — `ProhibitDisablement`
is ignored otherwise, and shipping it unsupervised creates a false sense of
protection.

The generated removal password is random and is not printed by default. Escrow
it with the accountability partner; the user must never see it.

### Extension

Load `extension/` unpacked in `chrome://extensions`. Needs Chrome 137+ for
Ed25519 in WebCrypto. `rules/*.json` and `seed/terms.json` are placed by
`blocklist/seed.py sync`, never by hand — see *Seed bundle* above.

Its ID is pinned by the `"key"` in `manifest.json` (see
`extension/keys/README.md`), so it loads as
`hfhaffbmoeepcdolgejeidkgaoapcjig` from any machine, any folder, every time.
That matters more than it looks: native messaging needs the ID in
`allowed_origins` *before* the extension is ever loaded, so an ID that changed
per load location — which is what an unpacked extension gets without a pinned
key — would make the next section unbuildable.

### macOS app

```bash
./test.sh macos                                      # or, by hand:
cd macos
xcodebuild -scheme Hisn -configuration Debug test -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO
xcodebuild -scheme Hisn -configuration Release build
```

Four targets: `Hisn` (the app), `HisnFilter` (the content-filter system
extension), `HisnBridge` (the native-messaging host the browser extension talks
to) and `HisnTests`. `BlocklistStore`, `LockStore` and `PartnerService` are
compiled into three of them; `Hisn.xcodeproj` is generated by
`generate_xcodeproj.py` so that shared membership is one list in one place
rather than something you can forget to tick a box for. The project is
committed — run the generator only after adding or removing a file.

To *run* it you need a paid Apple Developer account, because two of the
entitlements are granted per-account and cannot be self-signed:
`com.apple.developer.networking.networkextension` (content-filter-provider) and
`com.apple.developer.system-extension.install`. Set your team, then build.
Without a team it still compiles and the tests still pass — pass
`CODE_SIGNING_ALLOWED=NO` — but the filter will not activate.

The app must be in `/Applications` before the system extension will install;
macOS refuses to activate one from a DerivedData path.

### Native messaging host

The browser extension gets the lock state from the app, not from its own
storage, over Chrome native messaging — and that link needs a manifest that
nothing creates on its own. Skip it and `background.js`'s `pollNative()` fails
on every single attempt, forever; the extension has no other way to learn a
lock exists, so it just sits in `mode: "off"` no matter what the app records.
Nothing crashes and nothing logs an error anywhere visible — the popup simply
always says "Unlocked". (If a lock was already recorded before the link broke,
the failure looks different: after five minutes of silence the extension
correctly reads the silence as tampering and fails closed to strict mode
instead — so "blocking everything" and "blocking nothing" are the same missing
manifest, depending only on whether it ever worked.)

The manifest is written for **every Chromium-family browser** the profile
force-installs the extension on — Chrome, Edge, Brave, Vivaldi, Opera, Arc,
Chromium and Helium — not just Chrome and Edge. Those two lists must stay
aligned: a browser the profile force-installs the extension on but the app
never links is one where the extension is present and mute. Helium
(`net.imput.helium`) is the case that made this concrete — it is the one
non-Safari browser installed on the target machine, so leaving it out meant the
only real browser in use had a force-installed extension that could never reach
the lock clock. `NativeMessagingInstaller.browsers` and
`install_native_host.sh`'s `TARGETS` carry the same set, cross-checked by a test.

There are two ways this gets installed, and they are not redundant:

* **The app writes a user-scope copy on every launch** —
  `macos/Hisn/NativeMessagingInstaller.swift` — so the extension works at all
  for anyone who just builds and runs the app, which given no onboarding step
  forces the alternative below, is nearly everyone. This is what makes the
  default path actually function; before it existed, nothing ever wrote this
  file and the browser extension never blocked anything, ever, regardless of
  what lock the app itself enforced.

* **`install_native_host.sh`, run once with `sudo`, writes a system-scope copy**
  to `/Library` instead:

  ```bash
  sudo macos/install_native_host.sh --extension-id hfhaffbmoeepcdolgejeidkgaoapcjig
  ```

  This is the hardened form. `/Library` needs an administrator to write *and
  to remove* — under the setup this product is built around (standard
  account, second person holding the admin password), that is what stops the
  person under a lock from deleting the link themselves. A user-scope file
  cannot offer that; it is a plain file in their own home directory.

The app checks for a system-scope manifest before writing anywhere, and
defers completely when one exists — never both. That check is required, not
just tidy: Chrome resolves a native messaging host by checking user-scope
*before* system-scope, so a user-scope copy sitting next to an admin-owned one
would not add redundancy, it would silently win and hand the exact protection
`install_native_host.sh` exists for back to the person the lock is supposed to
constrain.

---

## Choosing a length, and your own two lists

The four presets are the common cases; **Custom** takes a typed length in hours,
days or weeks. Anything outside one minute to one year is refused rather than
clamped — a lock cannot be shortened afterwards, so silently turning a mistyped
`3650` into the one-year cap would commit someone to a year they never chose.

**Blocking Rules** in the app's sidebar holds the two hand-maintained lists:
sites to block on top of the published list, and the sites that stay reachable
in strict mode. Write them in the app, not in the browser extension — the extension takes both from
the app on every heartbeat, so anything typed on that side is overwritten within
a minute.

While a lock is running the lists may only move one way: you can add a block and
withdraw an allowance, never the reverse. That is checked by membership rather
than by count, because swapping one allowed domain for another leaves the count
identical, and in strict mode — where the allowlist is the only thing reachable
at all — that swap is a complete bypass rather than a small loosening.

## Setup that actually holds

Software alone tops out well short of "unbreakable", because the person owns the
machine. The configuration that closes the remaining gaps is procedural:

1. The person uses a **standard (non-admin) account**.
2. A **second person holds the admin password** and the profile removal
   password. Generate both; the user never sees either.
3. **iOS is set up at the same time.** A locked Mac beside an unlocked phone is
   theatre.

Steps 1 and 2 are what upgrade this from "annoying to bypass" to "cannot be
bypassed alone". Everything in `macos/` is built to make them the default rather
than an advanced option.

---

## Licensing note

This deliberately does **not** fork SelfControl. SelfControl is GPL-3.0, which
would require publishing any derivative under the same terms — incompatible
with a closed paid tier. The Swift here is original work built on Apple's
NetworkExtension framework, so you are free to license it as you choose.

Upstream blocklists carry their own licences (MIT, GPL-3.0, Unlicense) and are
recorded per-source in `blocklist/sources.json`. You redistribute the merged
list, not their code — but check the terms before shipping commercially.

## Privacy

Domain matching happens entirely on-device. Do not build a feature that reports
which sites a user tried to visit. A server-side log of blocked adult domains,
tied to identities, is the most damaging thing this company could hold, and one
breach away from ruining the people it was built to help. The accountability
partner learns that a release was *requested*, and when — nothing else.
