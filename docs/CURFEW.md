# Night curfew

`script/curfew.py` installs a per-user LaunchAgent that, inside a chosen window,
turns the display off whenever the frontmost app is not on an allow list. It is
the laptop half of "do not let me use this from 22:00 to 04:00 except what I
allow".

```bash
python3 script/curfew.py install --start 22:00 --end 04:00
python3 script/curfew.py allow com.apple.Music com.apple.iCal
python3 script/curfew.py deny com.apple.Music
python3 script/curfew.py status
python3 script/curfew.py check --now 23:30     # one evaluation, touches nothing
python3 script/curfew.py uninstall
```

Every ten seconds the agent reads `lsappinfo front`, compares the bundle id with
the allow list, and runs `pmset displaysleepnow` when the app is blocked. Finder,
the Dock, the login window and the system UI are always allowed, so the Mac
stays recoverable. The config is
`~/Library/Application Support/app.hisn.curfew/curfew.json`; the job is
`~/Library/LaunchAgents/app.hisn.curfew.plist`; the log is
`~/Library/Logs/Hisn/curfew.log`.

**Recovery without a terminal:** in Finder, Go to Folder ▸
`~/Library/LaunchAgents`, delete `app.hisn.curfew.plist`, then delete
`~/Library/Application Support/app.hisn.curfew`. The running tick exits when its
script disappears.

## What it is not

It runs as you, so it is a speed bump: it cannot stop an administrator, and it
does not lock the login session — the display goes to sleep, and the desktop is
still behind it. The real curfew is **Screen Time ▸ Downtime** with a passcode
held by someone else; use that when the guardian is available. This tool exists
for the case where the passcode is not available yet and something has to start
tonight.

It also does nothing to the phones. Those have their own feature:

* **iPhone** — Settings ▸ Screen Time ▸ Downtime ▸ Custom Schedule 22:00–04:00
  ▸ every day, then **Always Allowed**: Phone, Messages, FaceTime, Clock, Maps.
  The Screen Time passcode is typed by the guardian (Settings ▸ Screen Time ▸
  Lock Screen Time Settings), not by the person being protected.
* **Android** — Hisn ▸ Rules ▸ **Edit schedule** ▸ enable, 22:00–04:00, every
  day, mode **"Block every app except the ones I keep"**, and keep Phone,
  Messages, Clock, Maps. That mode exists for exactly this request: the app
  blocks everything else in the window and leaves the kept apps reachable.
