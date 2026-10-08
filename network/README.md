# Network setup and verified DNS publication

## Plan for any router

The native setup now accepts router family/model details and presents researched
manufacturer instructions, including phone-app routes. See the
[router compatibility guide](../docs/ROUTER_COMPATIBILITY.md) for sources and
the difference between DNS configuration, built-in categories and Hisn-list import.

`setup.py` chooses a route from capabilities rather than a router brand or
internet provider. It is an offline planner: it neither scans networks nor
changes settings, asks for credentials, or verifies installed protection.
The Mac app presents the same routes in **Start with your network**, before
its separate Mac checklist. The app guide does not invoke this Python CLI.

```sh
python3 network/setup.py
python3 network/setup.py --capabilities /absolute/path/to/capabilities.json
```

Omitting the file means every fact is unknown and yields `guided_assessment`.
`--capabilities -` accepts JSON on stdin. Example for an administrator-approved
private network whose router allows IPv4 DNS configuration:

```json
{
  "network_kind": "private",
  "network_consent": true,
  "router_admin": true,
  "dns_ipv4_configurable": true,
  "ipv6_active": null,
  "dns_ipv6_configurable": null,
  "local_resolver_available": false
}
```

Boolean facts accept `true`, `false`, or `null` (unknown). A plain boolean is
reported information. A documented observation may use
`{"value": true, "source": "observed", "evidence": "reference to the settings check"}`.
An observation describes a capability; it still does not prove blocking.

Accepted boolean fields are `network_consent`, `router_admin`,
`dns_ipv4_configurable`, `dns_ipv6_configurable`, `ipv6_active`,
`local_resolver_available`, `dns_ipv4_firewall`, `dns_ipv6_firewall`,
`encrypted_dns_controls`, and `guardian_admin_control`. `network_kind` is
`unknown`, `private`, or `public`. Optional `api_integration` has `identifier`,
`source` (`reported`/`observed`), and a nonempty `evidence` reference. Unknown
fields, duplicate keys, non-boolean capabilities, and claimed `verified` flags
are rejected.

| Route | When it is suggested |
|---|---|
| `guided_assessment` | Permission/access or DNS/firewall capabilities remain unknown |
| `manual_dns` | An authorized private gateway allows DNS configuration |
| `local_resolver` | DNS configuration and a local filtering resolver are available |
| `manual_firewall` | Gateway firewall rules can direct/restrict DNS without editable DNS advertisement |
| `device_only` | Public network, unavailable permission/admin access, or reported unsupported gateway |

No API adapter is implemented. Even a supplied API integration reports
`automatic_configuration_available: false`; users get manual or device
continuation. A resolver on another always-on host can serve routers without
custom-list support. IPv4/IPv6 DNS advertisement and firewall rules are separate
layers. Known encrypted-DNS controls are explicitly partial.

The result contains ordered actions, dependencies, missing information, and
evidence requirements. Every layer stays `not_verified` and
`configuration_performed` stays false. Keep live test records separately,
bound to the actual network, client, firmware, policy version, and time.
Mac and other-device setup always remain available, even when network assessment
is incomplete. A home gateway never proves cellular or roaming protection.

## A DNS sample on the Mac

The Mac app's manual Cloudflare route now offers **Check Cloudflare DNS on this
Mac**. Only pressing the button starts lookups; opening setup does not query
test domains. The app asks the system resolver for A and AAAA records for
Cloudflare's harmless `nudity.testcategory.com` and the ordinary control
`example.com`. It opens no page, downloads no adult content, and changes no DNS
or router settings. The domains are sent to whichever resolver macOS uses,
including a resolver supplied by a VPN. Cloudflare documents the test name and
the IPv4 `0.0.0.0` block response in its
[Families setup reference](https://developers.cloudflare.com/1.1.1.1/setup/).

Each record type has its own result. A null-only adult-test A reply plus a
positive ordinary A reply is a blocking observation. An AAAA `::` reply with a
positive AAAA control is reported as a null-address observation; it is not a
separate contractual Families guarantee. A usable adult-test address, including
a mixture of null and usable addresses, is reported as a non-null reply rather
than a blocking observation. Alternative local/special sinkhole addresses are
inconclusive, rather than evidence that a website can be accessed.
Timeouts, missing records, negative responses, errors, an unavailable control,
or relevant hosts-file aliases are inconclusive. Local hosts evidence is
checked before and after sampling; inaccessible or changed evidence prevents
a blocking conclusion.

The result carries a time and stays separate from **Network protection not
verified**. It may come from the system DNS cache. It does not identify the
resolver provider, prove gateway enforcement, test browser DoH, prevent VPN
bypass, cover another device, or verify cellular/roaming. A and AAAA are address
record types: both can be queried over IPv4, so this does not test IPv6 network
transport or firewall coverage. Results are not persisted and are discarded
when leaving the route. Recheck after DNS/network changes and allow caches to
expire before interpreting a result from a new configuration.

This provider test is deliberately absent from the custom AdGuard/Hisn-list
route. Hisn's signed list may not include Cloudflare's category-test domain.
That route still needs a listed-host/descendant/benign test and resolver query
logs, tied to the actual publication version.

## Verified local DNS publication

`publish.py` is the first network-layer component: it verifies a local signed
Hisn bundle, renders DNS blocking rules, and publishes a complete generation
for an administrator to import into AdGuard Home. It does not configure a
router, change the Mac's DNS, download lists, collect credentials, or reload a
running resolver. Publication success means verified files exist; live DNS
enforcement still needs a resolver import and a blocking test.

## Publish a signed bundle

Use the repository's Python environment with `cryptography`, already required
by `blocklist/keys.py` and the seed tests. POSIX systems with `flock`,
`O_NOFOLLOW`, atomic same-filesystem rename, and directory `fsync` are supported
(Linux/macOS; Windows is not supported by this publisher).

Run from the repository root. The output directory's parent must exist. The
output directory is created if needed, must belong to the account running the
publisher, and must not be writable by the protected user/group or everyone.
For a real gateway, install the script, pinned key, and local safety policy
under administrator-controlled paths and run as that administrator.

```sh
python3 network/publish.py \
  --bundle /absolute/path/to/signed-dist \
  --public-key /absolute/path/to/pinned-public-key.hex \
  --state /absolute/path/to/hisn-dns \
  --min-version 4
```

The default artifact is `domains.txt`, the full signed domain list. The shipped
seed deliberately contains only the core domain tier, so choose it explicitly:

```sh
python3 network/publish.py \
  --bundle seed \
  --artifact domains_core.txt \
  --public-key blocklist/public_key.hex \
  --state /private/tmp/hisn-dns-preview \
  --min-version 4
```

The preview command writes only into its output directory. `/private/tmp` is
the canonical macOS temporary path; use an existing parent on other systems.
Do not treat a temporary directory or the user's source checkout as trusted
persistent gateway deployment. Pin the initial minimum version to the trusted
release being enrolled; later runs also enforce the version in saved state.
Do not delete that state to resolve update errors: doing so discards its
rollback protection.

The publisher verifies the Ed25519 signature over the exact `manifest.json`
bytes before interpreting metadata. It checks schema, selected artifact's
SHA-256 and byte length, the signed full/core count, canonical lowercase ASCII
DNS labels/punycode, and sorted uniqueness. It rejects JSON duplicate keys and
unsafe artifact names even for unused entries. It reads only the two fixed
artifact choices and rejects input symlinks/special files. Other artifacts
named in the signed manifest do not need to be present: this follows the
existing builder's single-artifact verification format.

Default minimum counts are 100,000 for the full tier and 50,000 for core. A
trusted administrator may set `--min-count` for a smaller deliberately signed
deployment; tests use small signed fixtures. `--max-bytes` defaults to 512 MiB;
source processing streams instead of loading millions of domains into RAM.
The canonical grammar matches `blocklist/build.py`, including its currently
permitted dotted numeric labels. The importer does not convert such entries
into IP-address or firewall blocks.

## Published files and atomic updates

```text
hisn-dns/
  .publish.lock
  current -> generations/v4-<manifest-digest-prefix>-<random-id>
  generations/
    v4-<manifest-digest-prefix>-<random-id>/
      adguard.txt
      metadata.json
      manifest.json
      manifest.json.sig
```

`current/adguard.txt` is the stable import path. Metadata records source
version, selected tier, domain count, source/output digests and byte counts,
key and safety-policy fingerprints, generation identity, and publication time.
The CLI prints JSON with `status: published` or `status: unchanged`; failures
print a reason to stderr and exit nonzero.

Writers use one advisory lock and check persisted state while holding it.
Lower versions are rejected. A same-version manifest or safety-policy change
is rejected; replaying identical verified input is a no-op. The selected tier
and pinned key cannot silently change in an existing state directory. Key
rotation/tier migration needs a separate, reviewed enrollment/state migration.
Damaged saved metadata, signatures, or rules cause an error; the publisher does
not reset the accepted version to zero.

Files and generation directories are synced before one atomic `current`
symlink replacement. Readers of a single rules file see an entire old or new
generation. A consumer needing multiple files should resolve `current` once
and read the resulting generation path. The publisher retains previous
generations; it never deletes the last known good one. Failed preparation does
not replace the active pointer. If syncing the pointer fails, it attempts to
restore the previous pointer; if the filesystem also refuses restoration, it
reports that uncertain state explicitly. Storage/hardware that does not honor
`fsync` cannot provide a power-loss durability guarantee.

Staging files are removed on handled failures. A process kill/power loss can
leave an unreferenced staging/generation directory, but `current` still points
to a complete generation when atomic rename/durability work as specified.
There is no automatic pruning yet, so provision disk space for retained
generations. Administrator maintenance must leave the active generation and
its rollback metadata intact and serialize with `.publish.lock`. Never let the
protected user edit the state directory, its parent path, script, key, or
safety-policy file: these are the local trust boundary.

## Import into AdGuard Home

The output uses AdGuard's explicit subtree rule syntax:

```text
||blocked-domain.tld^
||blocked-tenant.shared-cdn.tld^
```

`||domain^` blocks that domain and all its subdomains, matching Hisn's existing
collapsed-list semantics. Plain domain-only rules and hosts entries have exact
host semantics and would miss descendants that the builder removed as
redundant. These rules cover DNS queries, not specific URLs, images, local
files, existing sockets, or every tunnel. See AdGuard's
[DNS rule syntax](https://github.com/AdguardTeam/AdGuardHome/wiki/Hosts-Blocklists).

On a resolver host with access to the publication directory, an administrator
can add the absolute `current/adguard.txt` path as a custom DNS blocklist.
Installed versions may require a narrowly scoped `filtering.safe_fs_patterns`
entry for the local path. AdGuard Home documents these filesystem controls and
the filter configuration in its
[configuration reference](https://github.com/AdguardTeam/AdGuardHome/wiki/Configuration).
Preserve the resolver's existing configuration and refresh/reload the custom
list using that installed version's supported interface. Do not point AdGuard
at the unverified incoming bundle or a raw upstream list URL.

For a local demonstration, a loopback-only server can expose the published
file from the resolver's own host:

```sh
python3 -m http.server 8765 --bind 127.0.0.1 \
  --directory /absolute/path/to/hisn-dns
```

Add `http://127.0.0.1:8765/current/adguard.txt` as the custom blocklist URL and
refresh it. Keep that listener local; for containers `127.0.0.1` refers to the
container, so mount the trusted publication directory or deliberately configure
the resolver's local connectivity. This demonstration server is separate from
the publisher and is not installed automatically.

After importing, use harmless administrator-owned test domains to check the
resolver's query log/verdict for the listed host, one descendant, and a benign
host. Test both IPv4 and IPv6 client DNS use, and a list refresh across a new
generation. A printed generation digest is not proof that clients are using
this resolver; gateway DNS enforcement and encrypted-DNS/VPN controls remain
separate work.

## Hosts export for compatible router importers

RouterOS versions with DNS Adlist support can use a local hosts-format file,
according to the [official MikroTik documentation](https://manual.mikrotik.com/docs/network-management/dns/).
Publish a verified export in its own state directory:

```sh
python3 network/publish.py \
  --bundle seed \
  --public-key blocklist/public_key.hex \
  --state /absolute/path/to/hisn-router-hosts \
  --artifact domains_core.txt \
  --output-format hosts
```

The result is `current/hosts.txt`. Transfer only the verified published file to
the router through its supported administration workflow and select it as an
Adlist local file. Check the model's capacity before importing; a large list
can exhaust DNS cache or storage. The router must provide DNS to the intended
clients, and IPv4/IPv6, descendants, alternate DNS, outage and reboot behavior
must be tested. This export contains exact listed names and does not guarantee
wildcard coverage. The publisher never connects to or changes a router.

The default `--output-format adguard` continues to produce `current/adguard.txt`.
Output format is pinned per publication directory; use a separate directory
for each format. Both formats retain signature, hash, count, safety, and rollback
validation. Neither format validates every domain's content classification.

## Infrastructure and shared CDNs

The default local safety policy reads only `never_block_suffix` and
`never_block_apex` from `blocklist/sources.json`. The administrator may pin a
deployment copy with `--safety-policy`. A candidate containing protected
infrastructure, one of its descendants, a protected apex/`www` apex, or a
blocking parent covering a protected host is rejected. It is not silently
trimmed after signature verification.

Shared CDN apex protection does not create a broad `@@||cdn.tld^` allow rule:
listed adult tenants remain blocked. The importer also never broadens a listed
tenant into a CDN-wide block. This preserves the source pipeline's distinction
between protected infrastructure trees and protected shared apexes.

## Tests

```sh
python3 -m unittest discover -s network -p 'test_*.py' -v
```

The suite uses freshly signed fixtures for tampering, rollback/same-version
equivocation, syntax/rule injection, protected infrastructure/CDN scope,
malicious paths/symlinks/special files, damaged persisted state, failed writes
and pointer durability, lock contention, and concurrent process publication.
It also imports the actual shipped signed core seed using the repository's
pinned public key. It does not need a router or modify resolver settings.
Planner tests cover unknown, locked, public, DNS-only, local resolver, and
firewall capabilities; IPv6 gaps; input validation; unavailable adapters; and
the invariant that capabilities never become verified enforcement.

`./test.sh python` includes the network suite after the blocklist and profile
suites. No Xcode build is needed for this component.
