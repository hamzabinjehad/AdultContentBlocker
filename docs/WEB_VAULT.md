# Hisn Vault — the lock, off the device

Status: **development preview.** The site is in [`website/`](../website/), runs
from a static directory, and has no hosting, domain, account or analytics. The
gates below say what was run and what was not.

## Why a website at all

The [end goal](END_GOAL_PLAN.md) is not "an app that blocks sites". It is: adult
content does not reach the screen on every device the person uses, and the
person cannot remove or weaken that protection alone before a date they chose.
The apps are one way to carry the filter. They were never the right place to
keep the **key**.

Three problems with keeping the code on the device:

1. **It dies with the thing it protects.** A vault inside Hisn is deleted when
   Hisn is deleted — and before the Screen Time passcode is set, nothing stops
   that deletion. The vault has to live somewhere the person cannot delete in
   the moment.
2. **Every store decides what the lock may be.** A reviewer can veto a flow, a
   platform can change a rule, and the product bends around the review instead
   of the goal. A static page has no reviewer and no entitlement.
3. **The lock is not ours.** On iPhone the enforcement is Apple's Screen Time
   with a passcode. The honest product is: generate that passcode, show it once,
   then keep it away from the person until the date. That is a document, not an
   app.

So this workstream moves the key off the device and leaves enforcement where it
already works. It is the solo-person half of
[workstream D](END_GOAL_PLAN.md#workstream-d--removal-only-after-a-time): no
second human required, no supervision required, no store involved.

Captures of the real flow: [`design/web-vault-2026-11-07`](../design/web-vault-2026-11-07/README.md).

## What it does

One flow, four screens:

1. **Make a vault.** Choose what the code is for (Screen Time, a Mac profile
   password, anything else), the date it opens, and how it may open: a partner's
   approval, the date, or either.
2. **Show the code once.** A 4-digit Screen Time passcode, drawn locally, shown
   with a 15-minute window and the exact Settings path to enter it. This is the
   only time it is displayed.
3. **Seal.** The code is encrypted under a random 32-byte vault key. That key is
   wrapped twice: to the partner's public key, and to a future drand round. The
   page keeps no plaintext; the vault file is the only copy.
4. **Open.** After the date, the drand beacon opens the time path. Before it,
   only a partner's release key does. Both end at the same vault key.

The vault file looks like this (values shortened):

```json
{
  "v": 1, "kind": "hisn-vault", "id": "…", "createdAt": "…", "revealAt": "…",
  "round": 33949546,
  "payload": { "alg": "A256GCM", "iv": "…", "ct": "…" },
  "partner": { "fingerprint": "HISN-PK-…", "envelope": { "pk": "…", "epk": "…", "iv": "…", "ct": "…" } },
  "time": { "round": 33949546, "armor": "-----BEGIN AGE ENCRYPTED FILE-----…" }
}
```

## The cryptography, precisely

| Piece | What it is |
|---|---|
| Code | 4 digits, rejection-sampled, rejects 0000/1234/abab/ascending runs. |
| Payload | AES-256-GCM. The additional data binds the vault id and the reveal date, so payload, id and date cannot be mixed between vaults. |
| Vault key | 32 random bytes from `crypto.getRandomValues`, in memory only. |
| Partner path | P-256 ECDH (ephemeral → partner public key) → HKDF-SHA256 → AES-256-GCM. The partner's private key never leaves their browser; a release returns the 32-byte key, not the code. |
| Time path | [tlock](https://github.com/drand/tlock) to drand **quicknet** (`bls-unchained-g1-rfc9380`, 3-second rounds). Round = ⌈(reveal − genesis)/3⌉ + 1. Before that round the threshold network has not produced the signature, so nobody — Hisn included — can derive the key. |
| Code check | AES-GCM's tag authenticates the payload; a modified file fails closed. |

The tlock bundle is committed at `website/lib/tlock.bundle.js`; its build inputs
and licenses are pinned in [`website/vendor/`](../website/vendor/). Sealing needs
no network: the chain parameters are public constants, so a vault can be made on
a plane. Only opening the time path fetches a beacon.

## What it stops, and what it does not

| Situation | Result |
|---|---|
| Delete the app, clear the browser, open the vault file in a text editor | No code. The file is ciphertext. |
| Move the device clock forward | No. The drand round, not the page clock, decides the time path. |
| Ask Hisn to recover the code | Impossible. There is no server and no key escrow. |
| Lose the vault file | Gone. This is the same trade as a lost key. |
| Partner's key is lost | The date still opens it; the early path is gone. |
| A partner approves early | Intended. That is the second human in the loop. |
| Reset the Apple Account passcode, erase the phone, use another device | **Still open.** The vault is a speed bump on the *code*, not an OS lock; Apple's own reset paths remain. Same limit as the in-app vault, stated in [self-control commitment](SELF_CONTROL_COMMITMENT.md). |
| The operator serves malicious JavaScript when the vault is made | **Possible.** The page can only be trusted as far as its hosting. CSP forbids third-party scripts and network access except `api.drand.sh`, and the site can be self-hosted; a downloadable, versioned build is the next step. |
| A compromised drand threshold decrypts early | **Possible in principle.** The time lock is only as strong as the quicknet threshold assumption. |

The four-digit code is not the security boundary. The 32-byte vault key is; the
code is short because Apple's Screen Time keypad is short.

## What was actually run

- `node --test website/test/*.test.mjs` — **16 passing**: format and policy
  (round math, phases, setup window, guessable codes, import validation), crypto
  round-trips (partner envelope, wrong key, tampered ciphertext), and a live
  drand round-trip. Set `HISN_SKIP_NETWORK=1` to skip the three beacon tests.
- `node website/test/browser/e2e.mjs` — **passing** in headless Chrome: create a
  partner key, seal a vault to it, read the code during setup, seal, produce a
  release from the partner page, open the vault with the returned release key,
  match the code, switch to Arabic. No console errors. Screenshots are written
  to a temporary directory (or `--shots <dir>`).
- The time path was verified against the real beacon: a ciphertext sealed to a
  past round decrypts, and one sealed to a future round is refused by drand
  ("too early to decrypt").

**Not run:** entering the passcode on a real iPhone, a hosted deployment, a real
partner over a real channel, a screen reader pass, and a native review of the
Arabic copy. None of those is claimed.

## Limits of the product, stated plainly

- The site cannot set the passcode for the person; it shows it once and trusts
  them to type it. If they never do, nothing is locked.
- It cannot stop the adult who set the passcode from resetting it with their
  Apple Account, erasing the phone, or picking up another device.
- The partner's private key lives in that browser's storage and its backup file
  is unencrypted. It is a key: copy it to a password manager or a safe, not to a
  shared folder.
- The vault file is ciphertext and safe to send; the release request carries the
  partner envelope, not the code.
- It does not sync between devices. One vault is one file; copy it where needed.
- Screen Time's own filter is still the shallow, English-centric one. Hisn's
  list and page scoring remain the app's job; the vault protects the passcode
  that keeps Screen Time on.

## How to run it

```bash
cd website
python3 -m http.server 8765          # then open http://127.0.0.1:8765/
```

```bash
node --test website/test/*.test.mjs              # logic + crypto (+ live beacon)
HISN_SKIP_NETWORK=1 node --test website/test/*.test.mjs
node website/test/browser/e2e.mjs --shots /tmp/hisn-shots
```

Rebuilding the tlock bundle: see [`website/vendor/README.md`](../website/vendor/README.md).

## Next

1. **Host it and pin it.** A versioned static build with a published hash, plus
   a "download this page" copy so the creation step does not depend on a live
   server. This is the gap that matters most.
2. **More secrets.** A Mac profile removal password and an Android
   device-owner/ADB secret use the same vault; only the instruction text changes.
3. **Partner channel.** A QR code for the public key, and a request that travels
   as a link or a file instead of a pasted blob.
4. **Printable recovery card.** Public key fingerprint, vault digest, date and
   partner contact — so the two people can check they hold matching halves.
5. **Arabic review.** The copy is translated but not reviewed by a native
   speaker; the security sentences are the ones to check first.
6. **Guardian console.** Let the Mac app and the guardian pack import the vault
   format instead of growing a second one.
