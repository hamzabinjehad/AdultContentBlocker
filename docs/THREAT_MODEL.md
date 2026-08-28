# Threat model

The adversary is the user. Not a hostile stranger — the same person who
installed this, three weeks later, at 2am, motivated and with a search engine.
That framing decides everything: the attacks that matter are the *easy* ones,
because those are the ones that will actually be tried.

Two independent questions get confused as one, and they have completely
different answers:

1. **Coverage** — can the filter be walked around without disabling it?
2. **Tamper resistance** — can the filter be disabled?

A perfect answer to (2) with a weak answer to (1) is a product that blocks
`pornhub.com` unbreakably while X, Reddit and image search stay wide open.
Coverage is the bigger gap in practice, and it is the one most tools ignore.

---

## Part 1 — Coverage

| Route | Status | Handled by |
|---|---|---|
| Known adult domain | Closed | 982k-domain signed blocklist, all layers |
| New domain, registered today | **Open in blocklist mode** | Strict allowlist mode |
| Mirrors, proxies, "unblock" sites | **Partly open** | Strict mode; upstream lists chase these forever |
| Adult content inside X, Reddit, Telegram | **Open in blocklist mode** | Strict mode only |
| Image/video search results | Partial | SafeSearch enforcement via DNS |
| Content on a shared CDN | Closed | Apex-only safety rail (see below) |

A blocklist is a losing race by construction: someone else adds domains, you
copy them, and the gap between the two is your exposure. The only mode that
approaches full coverage is **strict allowlist** — deny everything, permit the
handful of sites the user names when starting a session. That is why it is a
first-class mode here rather than a future feature.

### The CDN sub-case

The safety rail that stops an upstream list from blocking `apple.com` has two
tiers, and the split matters more than it looks:

* **Suffix tier** — the domain and everything under it is protected. Only OS
  infrastructure belongs here.
* **Apex tier** — only the apex is protected; subdomains stay blockable.

`cloudfront.net` must be in the apex tier. Blocking it entirely breaks half the
web; protecting its whole subtree silently unblocks every adult site hosted on
CloudFront. In this repo that distinction recovered **237 domains** that the
naive one-tier version had quietly let through — an invisible hole that no test
of "is pornhub blocked?" would ever catch.

---

## Part 2 — Tamper resistance

Ordered by how likely someone is to actually try it.

| # | Attack | Defence | Holds? |
|---|---|---|---|
| 1 | Disable the extension | Force-installed via managed policy, `"*": blocked` for others | Yes |
| 2 | Use a different browser | System-level content filter sees every app | Yes |
| 3 | Turn on a VPN | Socket-level filter runs *before* the tunnel | Yes |
| 4 | Browser DNS-over-HTTPS | `DnsOverHttpsMode: off`, locked by policy | Yes |
| 5 | iCloud Private Relay | `allowCloudPrivateRelay: false` (macOS 12+, no supervision needed) | Yes |
| 6 | Set the system clock back | Monotonic high-water mark freezes the countdown | Yes |
| 7 | Delete the app's data | Deadline mirrored across 3 stores, resolved by MAX, self-healing | Yes |
| 8 | Uninstall the app | Extension detects the silence and **fails closed** to strict | Yes |
| 9 | Quit the app | Lock lives in the filter and on disk, not the process | Yes |
| 10 | Remove the profile | Removal password + admin credentials | **Only if not admin** |
| 11 | Disable the filter in System Settings | Re-armed on every launch; needs admin | **Only if not admin** |
| 12 | New user account on the Mac | Profile installed at device scope | Yes |
| 13 | Recovery mode → disable SIP | — | **No** |
| 14 | Erase and reinstall macOS | — | **No** |
| 15 | **Use a phone or another computer** | — | **No** |

### Where it actually breaks

Rows 10–14 all reduce to one root cause: **the user is an administrator of
their own machine.** An admin can remove profiles, disable system extensions,
enter recovery, and reinstall the OS. No userspace software prevents any of
that, and any vendor claiming otherwise is describing a product that does not
exist.

So the strongest configuration available without MDM is not a code change:

> The person runs as a **standard user**. A second person holds the admin
> password and the profile removal password.

That single change converts rows 10, 11 and 13 from "open" to "requires
contacting another human". It is the highest-leverage thing in this entire
document, and it costs nothing to implement.

Row 15 — another device — is closed by no software on this Mac. It is the
reason iOS ships in v1 rather than "later". A locked Mac next to an unlocked
phone is not 90% of a solution; it is closer to 0%, because the constraint is
the person, not the device.

---

## Part 3 — Failure modes that matter more than attacks

Attacks are the interesting part. These are the parts that actually break in
production.

**A silently empty list.** If an upstream source changes format and the parser
returns nothing, the build "succeeds" and ships a list that blocks nothing.
Everything still looks green. Defences: refuse to publish if fewer than half the
sources succeeded, refuse under 200k domains in CI, and refuse a manifest whose
`domain_count` is implausible on the client too.

**A rollback.** An old manifest is still validly signed. Anyone who can serve
one can revert the list to before a domain was added. Defence: monotonic
version, and clients reject any version below the one they hold.

**The filter quietly off.** macOS updates, crashes and a stray toggle in System
Settings can all leave the filter disabled while the app still shows "Locked".
This is worse than no protection, because the user stops being careful.
Defences: re-assert on every launch, and a status header that reports what is
*actually* enforced rather than what was requested.

**Failing open.** The instinct when a component is missing is to allow traffic.
That instinct is backwards here. Every fallback in this codebase is the
restrictive one: no native app → strict mode; unverified list → empty allowlist,
which in strict mode denies everything; failed update → keep the old list.

---

## Part 4 — Why there is an escape hatch

A lock with genuinely no exit is not the safest design. It is the design people
refuse to install, uninstall pre-emptively, or route around by buying a second
device — and it is dangerous when someone needs the web for something real.

What works is making the exit **slow and social** rather than instant and
private:

* **48-hour delayed self-release.** Requestable at any time, cannot be
  accelerated, can be cancelled. The delay outlasts the urge.
* **Partner approval.** Immediate, but requires telling another person, and is
  verified server-side — a locally-checkable code is a code that can be pulled
  out of the binary.

Both preserve the only property that matters at 2am: *the decision cannot be
reversed by the person alone, in the moment, in silence.*

---

## Part 5 — What not to build

**Never log which sites were blocked.** It is the obvious next feature — an
accountability partner would find it useful, and competitors ship it. It is also
a database that maps real identities to attempted adult browsing. One breach
ends the company and seriously harms the exact people who trusted it. Matching
stays on-device; the partner learns that a release was requested and when,
and nothing else.

**Never promise 100%.** Row 15 alone makes it false. A user who finds a gap you
promised did not exist stops trusting the whole product and cancels. A user who
was told the truth up front — and helped to close rows 10–15 themselves — has a
reason to keep paying.
