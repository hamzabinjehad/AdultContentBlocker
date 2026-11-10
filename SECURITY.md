# Security policy

Hisn protects people from adult content and from removing their own guard; a
security defect can therefore expose browsing, weaken a commitment, or make a
protection layer report success when it is not running. Please report those
privately, not in a public issue.

## Reporting a vulnerability

Use **GitHub Security Advisories** for this repository:

1. Open the repository's **Security** tab.
2. Choose **Advisories** and **Report a vulnerability**.
3. Include the affected platform and version, the exact steps, the observed and
   expected result, and any proof without including real browsing history,
   private keys, pairing secrets or another person's data.

GitHub keeps the report private to the maintainers until an advisory is
published. If the form is unavailable, open a minimal public issue that asks
for a private channel and contains no vulnerability detail.

Do not send secrets, signing keys, passcodes or captured adult material. A
redacted log or a synthetic fixture is enough.

## Supported versions

Security fixes target the current development line of each surface. The
`versions.json` file is the pinned set; a version there is not a claim that a
signed artifact has shipped.

| Surface | Current development line |
| --- | --- |
| Browser extension | 1.5.3 |
| Mac | 1.1.18 |
| iPhone & iPad | 1.8.0 |
| Android | 1.2.3 declared; 1.4.0-and-later candidate in the tree |

Older versions may have known gaps recorded in the changelog. Update the app
and the extension before reporting a defect that an update already fixes.

## What is in scope

- A bypass of a layer that the product claims to enforce, including a quietly
  weakened commitment or an applied receipt that was never enforced.
- A path that sends browsing content, page text, screenshots, hostnames or
  keys off the device beyond the documented resolver and update traffic.
- Removal or disablement of the Mac filter, the Android DNS service or the
  iOS extensions that the documented authority model says should resist it.
- A signature, manifest, list-generation or update defect that could install
  an unverified or rolled-back list.
- A crash, lockup or data loss that removes protection without the person
  being told.

## What is a documented limit, not a vulnerability

The [threat model](docs/THREAT_MODEL.md) and the per-platform records name the
limits deliberately kept: another browser or device, an administrator or
account owner, a network that bypasses the configured resolver, unsupported
browsers, inaccessible media, classifier misses, a factory reset, and
established connections the Mac filter did not see start. Those are product
boundaries; a report that treats one as a new defect will be answered with the
record that already names it.

## Disclosure

Please give the maintainers time to ship a fix and a signed artifact before
publishing details. Advisories credit the reporter unless asked not to.
