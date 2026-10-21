# Samsung live accessibility verification — 2026-10-19

Hisn accessibility was enabled by the owner. Tests ran on the connected Samsung
SM-G990B2 / Android 16 using harmless screens in the optional test APK. No
BlockerHero/HaramBlur configuration, other accessibility permission, VPN or real
commitment was changed. These are fixture results, not universal app coverage.

## Findings and fixes

1. Running instrumentation against Hisn restarts its process and interrupted the
   accessibility binding; Android then listed it as crashed without a Java crash
   record. That attempt stopped before changing rules. Reinstalling the updated
   development APK restored the enabled binding. Live tests now use an external
   ADB driver, not instrumentation against the active service.
2. Window events can arrive before the active root changes. Hisn now retries
   root retrieval at most five times, 150 ms apart, and still verifies selected
   package ownership before reading any text. Stale excluded-window events no
   longer cancel the selected window's queued inspection.
3. Initial word tests did match and return the fixture to Home, but the pending
   Home transition covered the explanation screen just after it opened. A
   350 ms post-Home warning delay resolved this in the Samsung fixtures. This is
   measured on this device, not a universal OEM timing guarantee.
4. Enforcement no longer shares the warning debounce. A repeat match during the
   1,500 ms warning cooldown still invokes Home; only repeated warning UI is
   suppressed. The live rapid-reopen test confirms this exact cooldown path.
5. Settings-read failures mark inspection incomplete rather than falsely
   claiming the accessibility connection itself disappeared. Diagnostic outcomes
   and counters are ephemeral and contain no text, rules or app identities.

## Final successful run

| Harmless fixture | Result |
| --- | --- |
| Ordinary content | Stayed available |
| Editable field containing the fixture word | Stayed available |
| Password field containing the fixture word | Stayed available |
| Visible literal word | Restriction screen shown |
| Text label longer than 2,048 characters | Restriction screen shown |
| Matching label at child 75 of a wide container | Restriction screen shown |
| Explicitly selected blocked app | Restriction screen shown |
| Rapid reopening during warning cooldown | Returned out of restricted fixture; cooldown enforcement counter increased |
| Cleanup | Fixture-only additions removed; existing owner rules preserved |

Build and Android lint passed, with zero lint errors and six warnings. The
release merged manifest was checked and contains neither diagnostic activity
nor fixture activity. The installed development APK SHA-256 is
`617a870a29cbabed8884bcd8cfa69ea6a9de1cb79a558ca472139684e9043356`.

## Reproducing safely

Build/install the debug and test APKs. The owner must enable Hisn accessibility
and unlock the screen. Do not use this as an unattended test on a private feed.

```sh
python3 android/test-live-guard.py --adb /absolute/path/to/adb --serial DEVICE_SERIAL
```

The shell-only `GuardTestSetupActivity` exists under `src/debug/` only, requires
Android's DUMP permission and is absent from release. It refuses setup if a real
commitment is active, the fixture already has owner rules, control text overlaps
existing words, or an interrupted backup needs recovery. The backup is private;
the driver restores only its fixture additions in `finally`, retaining concurrent
owner additions. No owner commitment is bypassed. If USB disconnects before
cleanup, reconnect and run the same command with `--restore-only`. Do not start
a real commitment while this short test is running.

The optional test APK's fixture has no network capability added by these tests.
The driver records pass/fail outcomes, not screenshots or screen text. Do not run
the ordinary core instrumentation runner while actively testing accessibility:
it restarts the target app and may interrupt its binding.

## Remaining gates

Real browser/social-app layouts, dynamic feeds, secure/sensitive surfaces,
split-screen, service loss, screen lock and long-running battery behavior still
need separate tests. Android DNS routing is not yet live-tested on this phone;
its VPN consent remains separate. Visual filtering and managed-device removal
restrictions are not implemented by this update.
