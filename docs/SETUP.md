# Setting it up, in order

This is the whole thing end to end. The order is not a suggestion — each step
is only real if the one before it is done, and doing them out of order gives you
a machine that looks protected and is not. Run `macos/verify_enforcement.sh`
after any step to see the truth rather than the hope.

Two facts decide everything below, so read them first:

* **The lock is a person, not the software.** Every "the user cannot undo it"
  reduces to "the user is not an administrator, and someone else is." Skip the
  account split and everything else is a setting the user removes in a minute.
* **A blocklist is coverage; the account split is tamper-resistance.** They are
  independent. You need both, and $99 buys the first kind, not the second.

## Step 0 — the second person (no software)

Decide who holds the secrets: the admin password, the profile removal password,
the Screen Time passcode. It must be someone who is **not** the daily user — a
secret you hold yourself is a stop button, not a lock. Without this person,
stop here: nothing below becomes a lock, and building more is wasted effort.

## Step 1 — the account split · `macos/setup_guardian.sh`

```bash
macos/setup_guardian.sh --check                 # read-only readiness
sudo macos/setup_guardian.sh --create-admin      # the SECOND PERSON types the password
# log in as the guardian once to prove it works, then:
sudo macos/setup_guardian.sh --demote-me         # you become a standard user
```

Needs: the second person, present. Gets you: the user can no longer delete the
app, disable the filter, or remove the profile. This is the load-bearing step.
FileVault-safe — the guardian is given a secure token and you keep yours.

## Step 2 — domain blocking · `macos/block_dns.sh`

```bash
sudo macos/block_dns.sh --merge      # adds only what your hosts file lacks
```

Needs: admin (do it before step 1's demotion, or have the guardian run it).
Free. Gets you: ~148k adult domains blocked for every app and browser, VPN or
not — the OS resolver is below the browser. Bypassed only by browser DoH, which
step 4 closes.

## Step 3 — the app and filter · needs Apple's $99

Build and put the app in **`/Applications`** (not `~/Applications` — a
home-folder app is user-owned and deletes without admin). Activating the
socket-level content filter needs the paid Apple Developer entitlements; without
them the app runs but the filter stays off. With them: every browser, every app,
VPN-proof, and — behind step 1 — not disableable by the user.

## Step 4 — the hardening profile · `profile/make_profile.py`

```bash
python3 profile/make_profile.py --resolver cloudflare \
    --out dist/hisn-hardening.mobileconfig --print-password
```

The **second person** installs it: System Settings → General → Device
Management. Save the printed removal password with them, never the user. Gets
you: DoH locked off across eleven Chromium browsers, iCloud Private Relay off,
per-browser proxy pinned across twelve Chromium browsers (Helium included), and
**private/incognito browsing disabled** — the enforceable way to close the
incognito bypass (`--allow-incognito` opts out; see `CHROME_ENFORCEMENT.md`).
Do not add `--extension-id` until step 5 is actually published.

## Step 5 — force the browser extension · Chrome only

The extension is the only layer that reads page **text** — it is what catches
adult content on a new domain or inside a general site, and where the 5,547
Arabic terms live. Making it un-removable is Chrome/Edge only; Safari cannot do
it without supervised MDM. The full path is its own runbook:
**`docs/CHROME_ENFORCEMENT.md`** — package, publish to the Web Store, wire the
store-assigned id into the app and the profile, install the profile.

## Check it took

```bash
macos/verify_enforcement.sh
```

Read-only; reports each layer as enforced or open and exits non-zero if a
critical one is open. Have the second person run it after setup — the whole
point of this product is that "looks protected" and "is protected" differ, and
this is where you catch the difference.

## What is still open, honestly

Even with all of the above: an admin removing the profile (closed only by
step 1), Recovery mode (`THREAT_MODEL.md` row 13, open as long as the daily user
holds a FileVault token they need to boot), and **another device** — a phone or
second computer, closed by nothing on this Mac. The Mac is not the whole
problem; the person is.
