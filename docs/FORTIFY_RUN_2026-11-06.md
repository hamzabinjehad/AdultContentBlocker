# Fortify run, 6 November 2026

What `script/fortify.py` found and changed on the three attached devices, the
one Android regression it caught, and what remains an owner action. Read
[Fortifying the three devices](FORTIFY.md) for what the tool is allowed to claim.

## Devices

| Device | Identity | Read on |
|---|---|---|
| MacBook (this machine) | macOS 27.2, account `x` (admin) | `verify_enforcement.sh` + `scutil --dns` |
| Android | Samsung SM-G990B2, `RZCTA0XN60K` | adb, Android 16 |
| iPhone | iPhone 11, `00008030-000D642A1E92802E`, paired | devicectl |

## Applied in this run

* **Android** — `private_dns_mode=opportunistic`, `private_dns_specifier=
  dns-family.adguard.com`, `always_on_vpn_app=app.hisn.android`,
  `always_on_vpn_lockdown=0`. Readback confirmed all four. The Hisn VPN was
  already connected with `TextGuardService` and usage access on.
* **iPhone** — `dist/fortify/Hisn-iPhone.mobileconfig` generated (encrypted
  AdGuard Family DNS, Apple's adult web filter, the supervised-only
  restrictions) and copied to iCloud Drive/Hisn/. It is **not installed**: there
  is no remote profile install without MDM, and no removal password is embedded
  because an unsupervised phone ignores it.
* **Mac** — not yet applied. `apply --macos` needs root and the session has no
  passwordless sudo, so the operator command is:

  ```bash
  sudo python3 script/fortify.py apply --macos
  ```

  It writes ~32,000 lines of SafeSearch, Private-Relay/DoH and popular-subdomain
  blocks to `/etc/hosts`, points `Wi-Fi` and `iPhone USB` at AdGuard Family
  (`94.140.14.15`, `94.140.15.16`) and flushes the cache.

## The regression the run caught

Setting `private_dns_mode=hostname` (strict) on the connected Android while the
Hisn VPN was up broke every lookup:

```
$ adb shell ping -c 1 -W 3 example.com
ping: unknown host example.com
$ adb shell ping -c 1 -W 2 1.1.1.1        # raw IP still worked
PING 1.1.1.1 ... 0% packet loss
```

Strict mode makes Android speak DoT to the active network's resolver, which is
the VPN's own `10.111.0.2`; that resolver does not answer DoT. Reverting to
`opportunistic` restored DNS, and the filter still answered as intended:

```
example.com   → 172.66.147.243 (resolves)
pornhub.com   → unknown host   (blocked)
xvideos.com   → unknown host   (blocked)
x.com         → 172.66.0.227   (kept, as chosen)
```

`private_dns_mode_for()` encodes this: `opportunistic` while the VPN resolves,
`hostname` when it is off. The audit reports opportunistic as **partial**, not
enforced.

## State after this run

| Layer | Mac | Android | iPhone |
|---|---|---|---|
| Domain list | 154,908 hosts lines (old list, still blocking) | Hisn VPN list + AdGuard Family DoT | Safari blockers + profile when installed |
| SafeSearch | partial — fixed by `apply --macos` | resolver-level (AdGuard Family) | resolver-level when profile installed |
| Encrypted-DNS bypass | partial — fixed by `apply --macos` | opportunistic (can fall back) | profile DNS is HTTPS |
| Browser extension | enabled, linked | n/a | Hisn Safari installed |
| Screen Time / web filter | Limit Adult Websites on | n/a | unknown until read on the phone |
| System content filter | partial (not reported active) | Hisn VPN | none without entitlement |
| Always-on DNS | no | yes (VPN, no lockdown) | no |
| Tamper resistance | none — `x` is admin | none — ordinary install | none — profile removable |

## What no layer here closes

* **Telegram.** MTProto media does not traverse DNS. Keeping the app keeps the
  hole; only blocking the app closes it. Android's `TextGuardService` can close
  the app on adult *words* if word rules are configured in the app, which is the
  only in-app lever that exists.
* **X.** Adult images are on `pbs.twimg.com`. `--x-media` cuts all images and
  keeps the text; otherwise the in-app **Hide sensitive content** setting is the
  only control.
* **The Mac lock.** `x` is an administrator, so one password removes every layer
  above. `macos/setup_guardian.sh --create-admin` followed by `--demote-me` is
  the only step that turns this from configuration into a lock, and it needs the
  guardian present.
* **The iPhone lock.** Screen Time passcode held by the guardian, typed on the
  phone. A supervised device (one-time erase with Apple Configurator) is the
  only route to a non-removable profile and to `allowAppRemoval=false`.
