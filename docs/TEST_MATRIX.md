# Test matrix — what is demonstrated, what is inferred

Passing unit tests do not prove bypass resistance. This is the ledger of what
each claim rests on. **Demonstrated** means a repeatable check exists and ran;
**inferred** means read from the code; **manual** means a procedure exists but
no automation, and it must be run on a real Mac by a person.

## Automated (run by `./test.sh` and CI)

| Claim | Evidence | Suite |
|---|---|---|
| Both clients decide every fixture case identically (35 cases) | `policy_cases.json` asserted against the store (Swift) and against the exact DNR rules (JS) | `PolicyContractTests`, `policy.test.js` |
| Filter is never looser than the browser | browser-only cases asserted blocked by the store | `PolicyContractTests` |
| A settings message cannot touch internal state | schema refusals | `policy.test.js` |
| Concurrent writers cannot lose a restriction | lock ordering | `serial.test.js` |
| Lock ends at the effective deadline in the filter | matured release → `strictModeActive` false | `SelfReleaseTests` |
| Rollback protection survives a filter restart | `versionFloor` | `BlocklistLoadTests` |
| Scanner: equal-length replacement, non-settling feed, malformed URL, worker restart, stale verdict, subframe, SPA route | fake DOM + fake clock | `scan.test.js` |
| Same scanner scenarios in a real Chromium DOM | harness page, headless Chrome in CI | `test/browser/run.sh` |
| A generation is installed whole or not at all (browser) | planner refusals | `generation.test.js` |
| Every shipped artifact matches the signed seed manifest and the sources | `seed.py verify` | `test_seed.py`, CI |
| Clients download from the same place; requested artifacts are built | config pins | `test_build.py` |
| Known adult domains blocked, critical infrastructure not blocked, in a real build | built `dist/` | `test_build.py` (CI builds first) |
| Every test class is discovered | `unittest discover` in `test.sh` | `test.sh python` |

## Manual (procedures exist; must be run on hardware)

| Scenario | Procedure | Status |
|---|---|---|
| **App-group sharing between filter (root) and app** | `docs/TAMPER_MODEL.md` → "Assumption to verify first" | **not yet run — blocks every claim below** |
| Profile, incognito, DoH, Private Relay, native host presence, admin status | `macos/verify_enforcement.sh` (read-only) | exists |
| Filter enforces a lock after restart | start lock → reboot → `defaults read group.app.hisn filterStrictActive`; browse a listed site | manual |
| Sleep/wake | lock → sleep 10 min → wake → heartbeat within 30 s | manual |
| Private browsing | with the profile: incognito unavailable; without: `verify_enforcement.sh` reports the gap | manual |
| Alternative browsers | the filter blocks in Safari/Firefox (socket level); `verify_enforcement.sh` lists native-host coverage per Chromium browser | manual |
| VPN / proxy | socket filter sees flows before the tunnel: test with a system VPN on, listed site blocked | manual |
| Direct IP | **known open in blocklist mode** (`FilterDataProvider.handleNewFlow`: no hostname → allow); strict mode drops it | inferred, documented |
| Clock changes | `trustedNow` high-water mark: set clock back 1 y → countdown frozen | `LockStore` unit tests for the mark; end-to-end manual |
| Interrupted update | kill the app mid-download → previous generation intact (staging dir swap) | inferred from `writeToContainer`; manual |
| Missing components | delete user native-host manifest → extension strict after 5 min; quit app → filter keeps enforcing | inferred; manual |
| Connections open before a lock starts | browser: open tabs are navigated to the block page on rule change (`tabsToBlock`); **Mac: existing sockets are not closed** — a stream already playing continues until it reconnects | browser demonstrated in unit tests; Mac limitation documented |

## Isolated environment for disruptive checks

None of the manual rows may be run on a developer's own account: they change
accounts, install profiles and start real locks. Use a separate macOS user or
a VM with a throwaway Apple developer team, and `setup_guardian.sh --dry-run`
before any real run.
