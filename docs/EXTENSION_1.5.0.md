# Hisn extension 1.5.0 — portable rules

Released 3 November 2026 as a development candidate. Unpacked and store-ready;
no Chrome Web Store listing, assigned store ID or managed force-install is
claimed.

## Why

[The end-goal plan](END_GOAL_PLAN.md) Workstream A1 asks for one custom list on
every device the person uses, carried by a file they export and import — no
account and no server. The extension held the five hand lists but had no way to
move them out of one browser profile. 1.5.0 adds that, without adding a
permission, a network path or a storage field.

The file is the person's own data, not a policy authority. It is plain JSON,
readable and editable; the worker still validates every entry and still owns
the direction guard.

## What travels

| Field | Contents |
| --- | --- |
| `schema` | `"hisn.rules"` |
| `version` | `1` |
| `customBlocks` | Sites the person blocked |
| `allowlist` | Sites that stay reachable |
| `customTerms` | Words added to page-text scoring |
| `ignoreTerms` | Words the person reported as wrongly caught |
| `textAllow` | Sites the page-text check skips |

`exportDocument` writes exactly those fields. A blocking mode, lock deadline,
installed list generation, pause count, UI language or visual preference does
**not** travel: those are one browser's state, not a preference another browser
should inherit. The export reads the saved state, never an unsaved textarea
draft, so the file is what is actually enforced.

## What import does

- **Adds, never removes.** Every existing entry is kept. A file exported from a
  more permissive browser cannot undo this one.
- **Canonicalises.** Hosts are lowercased, a `www.` is stripped, a full web
  address is reduced to its host, and an entry that could never match is
  counted and skipped. Terms are normalised to the scorer's spelling.
- **Stops at 200 custom words.** The same ceiling `guardedUpdate` enforces.
  Words beyond it are reported, not silently dropped.
- **Rejects the wrong file whole.** A non-JSON file, a different schema or
  version, a non-array list or a list over 5,000 entries is not a Hisn rules
  file. Individual bad entries inside a valid file are skipped and counted.
- **Respects a lock.** New blocks and words are tightening and apply. New
  allowlist entries, ignored words and skipped sites are loosening: they are
  withheld and reported, and the person can import again after the lock ends.
- **Respects the app.** When the Mac app owns the lists, the page can export
  them but refuses an import — the app would overwrite the file on its next
  heartbeat. Edit the lists in the app instead.

The worker's `guardedUpdate` rechecks the shape, authority and direction of the
final patch. The page deciding what to withhold is UX; it is not the guard.

## Interface

**Settings → Blocking Rules → Move your rules** carries **Export file** and
**Import file…**, a hidden file input, and one status line. The section is in
English and Arabic, with export and import disabled when protection state is
unavailable and import disabled when the app owns the lists. Nothing is
uploaded and no file is stored by the extension.

## Files

| File | Change |
| --- | --- |
| `extension/lib/transfer.js` | Format, export, validation, add-only merge and lock direction |
| `extension/test/transfer.test.js` | 45 checks over the file shape, refusals, merge, lock and ceiling |
| `extension/options.html`, `options.js` | The section and its guarded `update` path |
| `extension/lib/icons.js` | Download and upload icons |
| `extension/lib/locale/en.js`, `ar.js` | English and Arabic copy |
| `extension/test/browser/settings.html` | Real-browser import, invalid-file and lock cases |
| `extension/manifest.json` | 1.5.0 |

## Verification

- `./test.sh extension` — **PASS**: JavaScript suites, package and evaluation
  corpus. `transfer.test.js` passes 45/45.
- `extension/test/browser/run.sh settings.html` — **PASS**, including: a real
  file reaches the mocked guarded worker; an invalid file writes nothing; a
  locked browser applies a new block and withholds a new allowance; an
  app-managed browser disables import and keeps export.
- `python3 extension/check_package.py dist/hisn-extension-1.5.0.zip` — **PASS**.

Package:

| Field | Value |
| --- | --- |
| Version | 1.5.0 |
| Path | `dist/hisn-extension-1.5.0.zip` |
| Files | 92 |
| Size | 6.2 MB |
| SHA-256 | `96ca854f52b3a4cb406bcc735013263a6adea56aedda93c843bf95733b1d85a7` |

## Limits

- Only the five hand lists travel. The blocking mode, lock deadline, installed
  list generation, pause count and device preferences are deliberately absent.
- The Mac app, iPhone/iPad and Android do not read this format yet. This
  release is the extension side of Workstream A1; the end-goal gate ("an entry
  added on one device is enforced on the others after import") is **not** met
  until those importers exist.
- Import is add-only. Removing an entry still has to be done on each device,
  subject to that device's own lock. A file cannot remove a block.
- The file is unencrypted and includes custom words and corrections. It is the
  person's to store; treating it like a private document is their choice.
- A valid file's entries still pass through the worker's validation and the app
  authority. No signature is added or claimed: the file carries the person's
  own entries, not a policy an administrator issued.
- No user study, store review or clean-profile acceptance is claimed.

## Reproduction

```bash
./test.sh extension
extension/test/browser/run.sh settings.html
extension/package.sh
python3 extension/check_package.py dist/hisn-extension-1.5.0.zip
```
