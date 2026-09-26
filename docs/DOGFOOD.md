# Living with it — the dogfooding protocol

Months of daily use by the person who built it is the test the unit suites
cannot be. This is how to run those months so they produce evidence rather than
impressions: what to try, what to write down, and what would stop a release.

Nothing here reports anywhere. Every note is kept by you, on your own machine
(`docs/THREAT_MODEL.md` Part 5 — no browsing history leaves the device, and
that includes into this file's log).

## The log (one line per event)

Keep a plain text file outside the repository — `~/Documents/hisn-log.txt` —
and add a line whenever one of these happens. The date, the kind, one sentence.
Never a URL of something you were trying to reach.

| Kind | Write down |
|---|---|
| `FP` | A page that should have opened was blocked (the host, and whether it was the list, a word, or strict mode). Use the block page's *report* button too. |
| `FN` | Something got through that should not have — **the kind of site and the route** (a search result page, an app, a new domain), not the address. |
| `BYPASS` | A way around a running lock that worked, however silly. These are the most valuable lines in the file. |
| `BREAK` | Something stopped working: the guard closed a browser whose extension was on, an update failed, the app did not come back after a restart. |
| `ANNOY` | Friction that would make an ordinary person uninstall it. |
| `URGE` | You wanted out, and what the tool did about it. Did the delay outlast the urge? |

## Month 1 — shake-down, short locks

* Start with a **one-hour** lock in standard mode, then a day, then a week.
  Tighten only when a week passes with no `BREAK`.
* Once a week, with a lock running, spend ten honest minutes trying to get out
  (the list below). Log every attempt, successful or not.
* Run `macos/verify_enforcement.sh` every Monday; screenshot the output into
  the log if any row changed.
* Watch `FP` volume. More than a few a week at the default sensitivity means
  a term list problem — that is a fix in `blocklist/terms/`, not a reason to
  turn sensitivity down.

## Month 2 — real locks, strict days

* A 30-day standard lock. One strict-mode day a week with a written allowlist.
* Use the partner route once, deliberately, with the partner's agreement, to
  prove it works end to end. Use the 48-hour self-release once, and cancel it.
* Travel or a different network at least once (hotel Wi-Fi, a phone hotspot,
  a VPN): the hosts file and the extension must not care.
* The daily lock (*Lock › Every day*), for the hours that are hardest — e.g.
  22:00–07:00. Check that it starts by itself with the app in the background,
  that a longer lock is left alone, and that switching it off waits 24 hours
  (and says until when) rather than taking effect that evening.

## Month 3 — the paid layer and the phone

* Join the Apple Developer Program, `macos/install.sh --team <TEAMID>`, turn on
  the system filter, and repeat the bypass list with the filter running —
  especially the VPN, Tor, and "delete the app's files" rows, which only the
  filter's root-owned authority closes.
* Set up the iPhone side (SETUP step 7) and live with both for the month.

## The bypass list (try each, during a lock)

Each should fail. A success is a `BYPASS` line and a bug.

1. Switch the extension off in `helium://extensions` — the guard should warn
   and close Helium within about a minute; relaunching should close it again.
2. Install another browser (Firefox, a Chromium fork from the web) and open it.
3. Open a guest window, a new browser profile, a private window.
4. Turn on the browser's own secure DNS; turn on a VPN (ProtonVPN is installed).
5. Force-quit Hisn in Activity Monitor — it should be back within ~10 seconds.
6. `defaults delete group.app.hisn` and the keychain item; with the filter,
   the lock must survive; without it, note what happened.
7. Set the clock back a day, or a year.
8. Image search, video search, YouTube search for something explicit.
9. A general site with adult sections (social media, forums) — the page-text
   layer's job.
10. An Arabic-language adult site, and a romanised-Arabic one.
11. A brand-new domain (days old) — only page text and the keyword layer
    can catch it.
12. Ask the partner for an approval code for "the next lock" before it starts —
    the partner page should warn it has not started.

## What stops a release

Any one of these, unresolved:

* A `BYPASS` that needs no administrator rights and no second device.
* A `BREAK` where protection silently stopped while every status said it was on.
* The guard closing a browser whose extension was genuinely on, more than once.
* An `FP` rate that makes ordinary browsing (news, medicine, education, Arabic
  sites) frustrating at default settings.
* Any list update that failed for more than three days without the app saying so.
