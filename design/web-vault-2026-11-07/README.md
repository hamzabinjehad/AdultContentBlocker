# Hisn Vault — captures, 2026-11-07

Headless Chrome 156 on macOS at 900 × 1300, dark appearance, served from
`python3 -m http.server` in `website/`. The states were driven through the
DevTools protocol by [`website/test/browser/e2e.mjs`](../../website/test/browser/e2e.mjs),
so they are the real flow rather than mocked screens: a partner key was created,
the vault was sealed to it, the partner page produced a release key, and the
vault opened with that key.

| File | State |
| --- | --- |
| `start-en.png` | the form: what the code is for, when it opens, the three open modes, the partner key and the acknowledgement |
| `start-ar.png` | the same form in Arabic, RTL |
| `setup-en.png` | the one showing of the code, the 15-minute window and the Settings path |
| `sealed-en.png` | sealed state: date, countdown, the drand round, the partner fingerprint, both open paths |
| `partner-en.png` | the partner's page: fingerprint, public key, backup, the release request and the returned `HISN-VK-` line |
| `revealed-en.png` | the code after the partner released it |
| `revealed-ar.png` | the revealed screen in Arabic, RTL |

What these do not show: a real iPhone with Screen Time, a hosted origin, a
screen-reader pass, or a native review of the Arabic copy. The Arabic security
sentences are draft. See [the web vault note](../../docs/WEB_VAULT.md).
