# Phone–laptop pairing and signed policy

Status: **reference implementation and tests, not wired into the phone apps.**
The Python reference is [script/guardian_protocol.py](../script/guardian_protocol.py)
with [script/test_guardian_protocol.py](../script/test_guardian_protocol.py); the
phone-side Android reference is
[GuardianPairing.java](../android/core/src/main/java/app/hisn/core/GuardianPairing.java)
with [GuardianPairingTest.java](../android/core/src/test/java/app/hisn/core/GuardianPairingTest.java).
This document is the wire format and the threat model the clients must match.

## What it is for

A guardian holds a key on a laptop. A phone holds a device key. They pair once,
compare a six-digit code on both screens, and from then on the laptop can sign
a policy pack and read a coarse health receipt. Nothing about which sites the
person visited is sent, stored or needed.

The protocol is deliberately smaller than an account system:

- no server, no password, no email;
- no browsing data;
- offline verification on the phone;
- a hostile relay cannot silently substitute keys because of the six-digit
  code;
- a stolen old policy cannot be replayed because the phone keeps a counter.

## Roles and keys

| Role | Key | Where it lives |
| --- | --- | --- |
| Guardian | P-256 ECDSA/ECDH keypair | Laptop Secure Enclave or Keychain |
| Device | P-256 ECDSA/ECDH keypair | Android Keystore or iOS Keychain |
| Relay (optional) | none; opaque envelopes | Untrusted server |

P-256 is used because both Apple's Secure Enclave and Android's
Keystore/StrongBox can hold it. Ed25519/X25519 are fine in software but are not
hardware-backed on both platforms; do not mix the two without a migration rule.

The **device ID** is the unpadded base64url SHA-256 of the device public key's
DER SubjectPublicKeyInfo. It is not a secret.

## Pairing

### Offer

The laptop shows a QR code containing:

```text
hisn://pair/1?gk=<base64url P-256 public key>&nonce=<base64url 16 bytes>&label=<text>
```

- `gk` is the guardian public key, DER SubjectPublicKeyInfo, base64url without
  padding.
- `nonce` is 16 random bytes, base64url without padding.
- `label` is optional, at most 64 characters, and must not contain control
  characters.
- Unknown or repeated query parameters are rejected.

### Response

The phone scans the offer, generates a device keypair, and returns:

```json
{
  "v": 1,
  "kind": "pair-response",
  "nonce": "<the offer nonce>",
  "guardian": "<the offer gk>",
  "device": "<device public key, base64url DER>",
  "proof": "<base64url P-256 ECDSA signature>"
}
```

The signature covers the canonical JSON of every field except `proof`:

```json
{"device":"...","guardian":"...","kind":"pair-response","nonce":"...","v":1}
```

Only the device private key can produce it, so the laptop knows the response
came from the key it will later trust.

### Six-digit code

Both sides compute:

```text
shared = ECDH(own private key, peer public key)
info   = canonical({"v":1,"guardian":<gk>,"device":<device>,"nonce":<nonce>})
key    = HKDF-SHA256(shared, salt="hisn-pair-v1", info=info, length=32)
code   = decimal(HKDF output as a big integer) mod 1_000_000, zero-padded to 6
```

The guardian reads the six digits on the laptop and compares them with the
phone. A relay that substitutes either key produces a different shared secret
and therefore different digits. The digits are never sent over the link.

### What pairing does not prove

- That the person holding the phone is the protected person. It proves key
  possession, not identity.
- That the QR code was not replaced before the first scan. The six-digit
  comparison is the defense; if the two screens differ, stop.
- That the laptop is uncompromised. A compromised guardian laptop can sign any
  policy; the device key and the recovery path limit the damage, not the
  signature.

## Policy envelope (version 2)

```json
{
  "counter": 42,
  "device": "<device id>",
  "issued_at": 1793000000,
  "kind": "policy",
  "not_after": 1793086400,
  "rules": "<base64url of canonical rules JSON>",
  "sig": "<base64url P-256 ECDSA signature>",
  "v": 2
}
```

Version 2 keeps the signed envelope **flat**. The rules are canonical JSON
inside a base64url string, so a phone can verify the signature without
implementing a nested JSON canonicalizer. A version-1 envelope with `rules` as
an object is still accepted by the Python reference for old fixtures, but new
policies are signed as version 2.

The example rules, once decoded, are:

```json
{
  "strict": true,
  "blocked_hosts_sha256": "...",
  "lock_until": "2026-12-01T00:00:00Z",
  "schedule": {"start": 1320, "end": 420}
}
```

The signature covers the canonical JSON of every field except `sig`. The
phone verifies, in order:

1. exactly the expected fields, `v == 2`, `kind == "policy"`;
2. `device` equals its own device ID;
3. `counter` is a positive integer and **greater than the highest counter it
   has accepted** (a lower or equal counter is a replay);
4. `not_after > issued_at`, `issued_at <= now + 300 s`, `not_after > now`;
5. `rules` is valid base64url and decodes to a JSON object;
6. the P-256 signature verifies under the pinned guardian public key.

A policy that fails any check is not applied. An expired policy keeps the last
known rules but raises a visible "policy stale" state; it never silently
unlocks. The Android verifier in
[GuardianPolicy.java](../android/core/src/main/java/app/hisn/core/GuardianPolicy.java)
checks all six steps and exposes the authenticated rules bytes without
interpreting them.

### Rollback and clock

The counter is persisted with the policy. Clearing app data also clears the
counter, which is why the phone stores the highest accepted policy in its
protected storage and why a factory reset is documented as a full reset. Time
checks use the phone clock plus a trusted-time source where available; the
`issued_at <= now + 300` window tolerates small clock skew, not a large
rollback.

## Health receipt

The phone signs a coarse receipt:

```json
{
  "v": 1,
  "kind": "health",
  "device": "<device id>",
  "at": 1793000123,
  "layers": {
    "owner": true,
    "uninstall_blocked": true,
    "dns": "running",
    "vpn": "always_on",
    "filter": "on",
    "accessibility": "on"
  },
  "tamper": ["uninstall_attempt"],
  "sig": "..."
}
```

There is no hostname, URL, page title, image or search term. A receipt is a
status report, not a browsing log. The laptop keeps only the latest receipt and
the device's counter.

## Transport

- **QR** carries the offer. The response can travel over the same QR scan in
  reverse, a local network connection, or a USB path on Android.
- **LAN** uses mDNS/Bonjour discovery and a TLS 1.3 channel with a self-signed
  certificate pinned to the pairing key, or a Noise handshake over TCP. No
  router configuration.
- **Relay** is optional: store-and-forward encrypted envelopes, push to wake,
  delete on delivery. The server sees a random device ID and a timestamp, not a
  hostname. A compromise must not reveal browsing.
- **USB** on Android uses ADB for provisioning and a wired pairing fallback;
  iOS supervision uses Apple Configurator. Do not use private iOS USB APIs.

## Threat matrix

| Attack | Defense | Residual |
| --- | --- | --- |
| Relay substitutes keys during pairing | Six-digit SAS comparison | A person who ignores mismatched digits |
| Replay an old, looser policy | Persisted counter | App-data clear; factory reset |
| Replay a policy to another device | Device ID binding | A stolen guardian key plus a copied device key |
| Tamper with rules in transit | ECDSA signature over canonical body | Guardian key compromise |
| Forge a health receipt | Device signature | Device key compromise |
| Read policy on a relay | Envelope encryption (when relay is built) | Metadata: device ID and time |
| Steal the laptop | Secure Enclave/Keychain, device confirmation | A compromised unlocked laptop |
| Shoulder-surf the QR | The QR only contains public keys and a nonce | The six-digit code is visible on both screens |
| Downgrade the protocol | `v` is fixed and signed; unknown fields rejected | A client bug that ignores `v` |

## Test coverage

`script/test_guardian_protocol.py` has 33 tests: key round trips, offer
parsing and rejection, SAS equality across both sides, a man-in-the-middle key
substitution producing a different SAS, response proof checks, policy
signature and tamper checks, device binding, replay, expiry, future issue,
bool-as-int rejection, canonical JSON and base64url validation.
`script/test_guardian_console.py` adds 11 file-level tests for key permissions,
the encrypted-key path, offers, response verification, signing, wrong-device
and replay refusal, and rules validation.
`GuardianPairingTest` loads a Python-generated P-256 fixture, verifies the
Python response in Java, builds a Java response and verifies it, and checks the
SAS on both sides. `GuardianPolicyTest` verifies a Python-signed version-2
policy and rejects replay, expiry, future issue, tampered rules/signature,
unknown fields and non-canonical integers. The Java response was also verified
by the Python reference during development, so the canonical JSON and
ECDH/HKDF agreement is not Java-only.

`python3 script/guardian_protocol.py --check` runs the end-to-end round trip.

**Runtime check.** `android/test-live-pairing.py` starts the debug-only
`GuardianPairDebugActivity` on a connected build, exchanges a Python guardian
offer, verifies the Java response and SAS through `run-as`, signs a version-2
policy bound to the same device key, and has the activity verify it. On the API
36 emulator on 2026-11-01 it passed both steps and the same device ID persisted
across runs. On API 31+ the device identity is an **EC P-256 key inside
AndroidKeyStore** with sign and key-agreement purposes; older runtimes fall back
to a software P-256 key wrapped by an AES-256-GCM Keystore key. The QR/manual
entry UI is still not built.

With `--apply-policy`, the signed rules in the debug harness can also be
**tighten-only applied** after verification:

- `keep_installed`, `lockdown`, `strict`, `suspend_blocked` can turn choices on,
  never off;
- `blocked_apps` adds package names to the block list, never removes one;
- `blocked_domains` adds hostnames to the domain rules, never removes one;
- `blocked_domains_sha256` is optional; when present the applier computes the
  canonical hash (sorted, newline-joined UTF-8) and refuses a mismatch. This is
  implemented in both Python and Java (`DomainListHash`).
- unknown or unapplied rules are authenticated but not interpreted;
- `UninstallProtectionController.sync` then applies the managed restrictions
  and suspension and the activity writes the readback.

On the emulator this produced `reason=protected` with `com.android.chrome`
suspended after a Python-signed policy. A later run added `example.com` through
`blocked_domains` and read it back in the applied policy. A later run included
`blocked_domains_sha256` and applied successfully. A separately transported
signed domain **list** remains open.

## Manual pairing screen

The product build now has **Guide → Guardian pairing** →
**Pair with a laptop** (`GuardianPairActivity`):

- paste a `hisn://pair/1` offer;
- the phone builds the response and shows the six-digit SAS and its device ID;
- **Copy response** puts the response JSON on the clipboard for the laptop;
- no camera, QR scanner, network connection or browsing data is used;
- the phone identity is the same EC-in-AndroidKeyStore identity (with the
  wrapped fallback) used by the runtime harness.

This is a manual fallback, not the intended QR flow. The laptop still has to
carry the response back by clipboard/file. After pairing, the same screen also
shows a signed-policy field and an **Apply policy** action; it verifies the
signature/device binding/counter with `GuardianPolicy` and applies tighten-only
rules through `GuardianPolicyApplier`. On the API 36 emulator a Python-signed
policy for the persisted device ID was pasted into this screen, applied, and
reported **“Policy applied (already-protected)”**. The debug harness remains the
path that also writes machine-readable readback.

On the API 36 emulator a Python guardian offer was pasted into this screen; the
phone displayed the SAS, **Copy response** wrote the 439-byte response JSON to
the clipboard, and Python verified the proof and matched the SAS. The test used
a debug trampoline to open the non-exported screen and a debug clipboard reader;
neither is in a release build.

## Reference console

[script/guardian_console.py](../script/guardian_console.py) is a laptop-side
prototype over the same library. It writes files and prints the protocol
values; it does not display a QR image or open a network port.

```sh
python3 script/guardian_console.py keygen --out ~/.hisn-guardian
python3 script/guardian_console.py offer --key ~/.hisn-guardian/guardian.key
python3 script/guardian_console.py verify-response --key ~/.hisn-guardian/guardian.key \
    --offer 'hisn://pair/1?...' --response response.json
python3 script/guardian_console.py sign-policy --key ~/.hisn-guardian/guardian.key \
    --device DEVICE_ID --counter 1 --days 30 --rules rules.json --out policy.json
python3 script/guardian_console.py verify-policy --public-key ~/.hisn-guardian/guardian.pub \
    --policy policy.json --device DEVICE_ID
```

`keygen` writes `guardian.key` with mode 0600 and `guardian.pub`; it encrypts
the private key by default and accepts `--password-env` for a non-interactive
password. `--no-password` is for a throwaway prototype and warns. The console
is a reference, not the final Mac app: it has no QR display, no LAN transport
and no health-receipt storage.

## Integration plan

1. **Android**: the offer parser, response builder/verifier and SAS are in
   `GuardianPairing`, and the version-2 policy verifier is in `GuardianPolicy`;
   both match Python fixtures. Still to build: generate and store a P-256 key in
   the Keystore, pin the guardian public key, scan the QR with the camera (no
   Google Play Services dependency), show the six digits and require a confirm,
   and apply the authenticated rules through `PolicyStore`.
2. **iOS**: generate a P-256 key in the Secure Enclave; store the guardian
   public key and counter in the Keychain; scan the QR with
   `AVCaptureMetadataOutput`; show the six digits; verify before applying.
3. **Laptop**: a Swift command-line or Mac app console using CryptoKit; print
   the offer as a QR in the terminal or a window; sign policy with the guardian
   key; show the latest receipt.
4. **Do not** add a cloud account for this. A relay is a later, optional
   transport for the same envelopes, not a new identity.

## What this document does not claim

- That the pairing is integrated into the apps today.
- That a six-digit code prevents a person who controls both screens from
  approving a substituted key.
- That a relay is built, audited or hosted.
- That the policy language covers every setting the apps support; the `rules`
  object is intentionally open and each client must ignore unknown fields it
  cannot enforce rather than silently weakening.
