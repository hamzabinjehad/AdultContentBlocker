# Setting it up, in order

This is the whole thing end to end. The order is not a suggestion — each step
is only real if the one before it is done, and doing them out of order gives you
a machine that looks protected and is not. After any step, the app's **Setup**
page (or `macos/verify_enforcement.sh` in a terminal) shows the truth rather
than the hope: each step read from this Mac, and what is left.

Two facts decide everything below, so read them first:

* **The lock is a person, not the software.** Every "the user cannot undo it"
  reduces to "the user is not an administrator, and someone else is." Skip the
  account split and everything else is a setting the user removes in a minute.
* **A blocklist is coverage; the account split is tamper-resistance.** They are
  independent. You need both, and $99 buys the first kind, not the second.

Everything from step 1 to step 7 needs an administrator, so it is done
**before** step 8 takes administrator rights away from the daily account.
(The old order split the accounts first and then asked for admin rights the
daily user no longer had.)

## Step 0 — the second person (no software)

Decide who holds the secrets: the admin password, the profile removal password,
the Screen Time passcode, and the partner key that can end a lock early. It
must be someone who is **not** the daily user — a secret you hold yourself is a
stop button, not a lock. Without this person, stop here: nothing below becomes
a lock, and building more is wasted effort.

## Step 1 — the app · `macos/install.sh`

```bash
macos/install.sh                      # no paid Apple account yet
macos/install.sh --team ABCDE12345    # once you have one (step 9)
```

Builds Hisn, puts it in **`/Applications`** (a home-folder app deletes without
a password, and the system extension only activates from `/Applications`),
installs a LaunchAgent that starts it at login and brings it back if it is
force-quit, registers the browser link for every Chromium browser, and runs
the verifier. During a lock the app refuses an ordinary Quit: it is also the
**browser guard**, which closes any browser Hisn is not running inside
(`BrowserGuard.swift`) — review *Blocking Rules › Browsers during a lock*
before your first lock, and allow any app there that opens web links without
being a browser.

**In Arabic:** *Settings › Language › العربية*, then **Restart now** — the
window mirrors right-to-left and numbers stay in Latin digits, as in the
extension. During a lock the app cannot quit, so the switch waits for the next
launch. The extension has its own switch, in its popup.

## Step 2 — the browser extension

The extension is the only layer that reads page **text** — it is what catches
adult content on a new domain or inside a general site, and where the Arabic
vocabulary lives. Until it is on the Chrome Web Store, load it unpacked:

1. Open `helium://extensions` (or `chrome://extensions`), switch on
   **Developer mode**, **Load unpacked**, and pick this repository's
   `extension/` folder. It loads with the pinned id
   `hfhaffbmoeepcdolgejeidkgaoapcjig`.
2. The Hisn popup should say **App connected** within a minute.

Unpacked, it can be switched off on that page — which is exactly what the
browser guard answers during a lock. Making it un-removable needs the store
and the profile: **`docs/CHROME_ENFORCEMENT.md`** is that runbook.

## Step 3 — domain blocking · `macos/block_dns.sh`

```bash
macos/install.sh --hosts          # or: sudo macos/block_dns.sh --merge
```

Free. Blocks the core list for every app and browser, VPN or not — the OS
resolver is below the browser. Bypassed only by browser DoH, which step 5
closes.

It also forces **SafeSearch everywhere**: Google (all 187 of its domains),
YouTube (Restricted Mode, strict), Bing and DuckDuckGo are pointed at their own
SafeSearch addresses (`macos/safesearch_hosts.txt`) — so Safari, Firefox and
any app that opens a search get it too, not just the browsers with the
extension. The addresses are looked up when the script runs; if one of those
engines ever stops loading, its address moved: run the command again. The
Setup page and `verify_enforcement.sh` both say when that has happened.

## Step 4 — the accountability partner's key · `partner/index.html`

With both of you present: the partner opens `partner/index.html` on **their
own** phone or computer (send them the file, or host it), taps *Make a key*,
and reads you the code under *Your key*. Paste it into Hisn › Settings ›
Accountability partner and check the fingerprints match. From then on the
partner can end a lock early by signing the release code Hisn shows —
immediately, and never without them. The private key never leaves their
device; tell them to keep the backup the page offers somewhere private.

## Step 5 — the hardening profile · `profile/make_profile.py`

```bash
macos/install.sh --profile                      # builds it and opens it
macos/install.sh --profile -- --allow-devtools  # if you develop extensions yourself
```

`--profile` first installs the **admin-owned browser link**
(`install_native_host.sh`, asks for the password): the profile switches off
user-level links — a user-level manifest is the user's own file and can point
at a script that answers "no lock" — so without the admin-owned one the
extension would lose the app. The **second person** installs the profile:
System Settings → General → Device Management. Save the printed removal password with them, never the user. Gets
you: DoH locked off across the Chromium family (Helium included), iCloud
Private Relay off, per-browser proxy pinned, guest windows and new browser
profiles disabled (both run without the extension), Google SafeSearch and
YouTube strict mode forced by policy, and **private/incognito browsing
disabled** — the enforceable way to close the incognito bypass
(`--allow-incognito` opts out; see `CHROME_ENFORCEMENT.md`). Do not add
`--extension-id` until the extension is actually published.

## Step 6 — Screen Time, held by the partner

System Settings › Screen Time › Content & Privacy › **Limit Adult Websites**,
and a Screen Time passcode the partner sets and keeps. This is Apple's lock,
and Hisn sits under it (`docs/POSITIONING.md`): Safari is covered by it, and
the guard leaves Safari open for that reason.

## Step 7 — the phone, at the same time

A locked Mac beside an unlocked phone is theatre. On the iPhone: Screen Time ›
Content & Privacy Restrictions › Web Content › **Limit Adult Websites**, with
the partner's passcode — or, better, Family Sharing with the partner as the
organiser, so the passcode is theirs by construction.

## Step 8 — the account split · `macos/setup_guardian.sh` — last

```bash
macos/setup_guardian.sh --check                 # read-only readiness
sudo macos/setup_guardian.sh --create-admin      # the SECOND PERSON types the password
# log in as the guardian once to prove it works, then:
sudo macos/setup_guardian.sh --demote-me         # you become a standard user
```

Needs: the second person, present. Gets you: the daily user can no longer
delete the app, disable the filter, or remove the profile. This is the
load-bearing step, and it is last only because everything above needed the
admin rights it removes. FileVault-safe — the guardian is given a secure token
and you keep yours.

## Step 9 — the system filter · Apple's $99

The socket-level filter sees every app's traffic and a VPN does not bypass it.
It needs the paid Apple Developer Program: set your team, run
`macos/install.sh --team <TEAMID>`, open Hisn and press *Turn on the system
filter*, and approve it in System Settings › General › Login Items &
Extensions (an administrator — after step 8, the partner). From then on the
lock, the lists and the partner key live in the filter's root-owned store as
well (`docs/TAMPER_MODEL.md`), and deleting the app's own files no longer ends
a lock.

## Publishing the list the clients download

Both clients download signed updates from this repository's `lists` branch —
`LIST_BASE` in `extension/background.js` and `base` in
`macos/Hisn/ListUpdater.swift`, pinned to each other and to this repository by
`test_build.py`. Two things must be true before any install receives an update:

1. **The repository is public.** `raw.githubusercontent.com` answers 404 for a
   private repository, and the clients then keep their bundled seed forever.
2. **The signing key lives in an environment, not a repository secret.**
   Settings › Environments › *New environment* `list-signing`; under
   *Deployment branches and tags* choose *Selected branches* and add `main`;
   then *Add environment secret* `BLOCKLIST_SIGNING_KEY` with the PEM whose
   public half is pinned in `blocklist/public_key.hex`. If an older
   repository-level secret of that name exists, delete it — it would still be
   readable from any branch. Then run *Build and publish blocklist* once from
   the Actions tab, on `main`; after that it runs daily.

Check it took: `curl -sI https://raw.githubusercontent.com/<owner>/<repo>/lists/manifest.json`
answers `200`.

## Check it took

```bash
macos/verify_enforcement.sh
```

Read-only; reports each layer as enforced or open — the account split, the
hosts file, and for every installed browser whether the Hisn extension is
**on**, incognito, DoH, guest windows, SafeSearch and the browser link, plus
Screen Time, Private Relay and the system filter — and exits non-zero if a
critical one is open. Have the second person run it after setup — the whole
point of this product is that "looks protected" and "is protected" differ, and
this is where you catch the difference. Then start a **one-hour lock** and try
to get around it yourself; `docs/DOGFOOD.md` has the list.

## What is still open, honestly

Even with all of the above: an admin removing the profile (closed only by
step 8), Recovery mode (`THREAT_MODEL.md` row 13, open as long as the daily user
holds a FileVault token they need to boot), and **another device** — a phone or
second computer, closed by nothing on this Mac. The Mac is not the whole
problem; the person is.
