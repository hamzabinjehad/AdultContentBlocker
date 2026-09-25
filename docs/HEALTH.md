# Enforcement health — what the filter reports, and what "enabled" means

"Filter enabled" was a saved preference: `NEFilterManager.isEnabled`, plus a
domain count the provider wrote once at load and never refreshed. A provider
that crashed, was stopped by the system, or never received the shared
container's list looked identical to one enforcing 5 million domains. This
is the contract that replaces that.

## Keys the filter writes (app-group defaults, every 30 s while running)

| Key | Type | Meaning |
|---|---|---|
| `filterHeartbeatAt` | Date | Written on every tick. **The only evidence the provider is alive.** Older than 2 min → not running, whatever the preference says |
| `filterDomainCount` | Int | Domains actually held in memory |
| `filterListVersion` | Int | Version of the generation in force; also the persisted rollback floor read at the next start |
| `filterHostTermCount` | Int | Host terms in the keyword layer; 0 = layer off |
| `filterKeywordSource` | String | `downloaded`, `bundled`, or `none` |
| `filterStrictActive` | Bool | Whether strict enforcement is in force right now (effective deadline) |
| `filterStoppedDuringLock` | Date | Written by `stopFilter` if a lock was running — the system tore the provider down mid-lock |

## Keys the updater writes

| Key | Type | Meaning |
|---|---|---|
| `lastListCheck` | Date | Last attempt, success or not (gates the 6 h minimum) |
| `lastListUpdate` | Date | Last generation installed |
| `lastListError` | String | Why the last attempt failed; removed on success |
| `listVersion` | Int | Version the app installed and wrote to the container |

## Keys the bridge writes

| Key | Type | Meaning |
|---|---|---|
| `extensionLastSeen` | Date | The browser polled the native host; older than 5 min → extension not running |

## What the app should say (status model contract)

* **Filter**: ok only if `isEnabled` **and** heartbeat fresh **and**
  `filterDomainCount > 0`. Detail names which is missing. "Enabled, never
  reported in" and "enabled, last seen 40 m ago" are different failures with
  different fixes (activate vs. relaunch/reassert).
* **Keyword layer**: its own line; ok if `filterHostTermCount > 0`, with the
  source. A lock in blocklist mode without it does not catch new domains, and
  the person should know.
* **Updates**: freshness, not enforcement. A generation older than 7 days or
  a persistent `lastListError` is shown, never counted as a missing layer.
* **During a lock**, a stale heartbeat or `filterStoppedDuringLock` newer than
  the heartbeat is a *problem line*, and the app's `reassertIfNeeded` is the
  recovery path — re-arming the filter configuration, which relaunches the
  provider.

## Recovery behaviour by component

| Failure | Behaviour | Recovery |
|---|---|---|
| Provider crashes or is stopped | Traffic passes (macOS does not fail closed for a stopped content filter). `filterStoppedDuringLock` is written; heartbeat stops | App re-asserts on launch and hourly; the status header names it within 2 min |
| Downloaded generation corrupt or rolled back | Refused; previous generation kept; on restart the seed is loaded if the container fails | Next update replaces it |
| `terms.json` missing from a generation | Whole generation refused (native and browser) | Next generation |
| Native host silent | Browser fails closed to strict after 5 min (rule 5) | Heartbeat resumes → normal |
| App quit / not running | Filter enforces from its own memory and the mirrors; updates and re-assertion pause | App relaunch |
| Sleep/wake | Provider keeps running; the 30 s tick resumes; no state is lost | none needed |

## Where the app gets these now

The keys above are written by a root process, into root's defaults — the app
cannot read them on a real install. The app therefore asks the filter itself:
`FilterLink.status()` returns a `PolicyStatus` whose `health` carries the
domain count, host-term count, keyword source, list version and the filter's
start time, straight from the process that holds them (`FilterSync.status`,
refreshed every 20 s). An answer at all is the heartbeat; no answer within a
second is "not reachable". The defaults keys remain as a fallback for a
development build and are never trusted over an XPC answer.
