# Android: clearer uninstall protection, and "Show all apps"

Date: 15 November 2026.

## Uninstall protection (direct build)

The dialog was titled "Managed uninstall protection", opened with a 190-word paragraph, and
its switch read "Close Hisn's removal and disable screens". Now
(`01-uninstall-protection-samsung.png`, captured on the owner's Galaxy before the last tweak,
which removed the repeated "Uninstall protection ·" from the first line):

- Title: **Uninstall protection**. "Managed" stays only for device-owner mode.
- **The state comes first** ("On. Hisn closes the screens that would remove it or switch it
  off."), then the switches, then the explanation.
- Switches: **Keep Hisn installed** and **Also block installing new apps**.
- The explanation is short lists: what Hisn closes, what still works, what it cannot stop
  (Safe Mode from the hardware keys, ADB, a factory reset), that it needs App protection, and
  how to turn it off.
- The eight state lines are shorter and say "App protection" instead of "accessibility
  service". English and Arabic.

No behaviour changed; this is wording and order only.

## Show all apps

Every app list (Apps to block, the daily-limit picker, Wait before opening, and the
onboarding "Apps to check") can now include apps that have no home-screen icon.
`installedApps(context, all = true)` adds `getInstalledApplications`; the shared
`ShowAllAppsRow` switch says what "all" means on this build.

- **Direct build** declares `QUERY_ALL_PACKAGES`, so the switch lists every app.
- **Play build** does not (Google Play restricts it), so the switch lists what Android lets
  Hisn see and says so. Checked with `aapt2 dump permissions` on both release builds, and
  `check-release.py` passes for both.
- Wait before opening still hides the apps the guard never watches (Settings, launchers,
  Hisn itself), because a wait there could not work.

## Checked

- Both release builds assemble and pass `check-release.py`; only the direct build declares
  `QUERY_ALL_PACKAGES`. String parity passes.
- `bash android/test-ui.sh emulator-5560 direct`: **40 of 40**.
- On the owner's Galaxy (signed direct build, data kept, App protection still running): Rules ›
  Apps to block listed **103** apps; with "Show all apps" on it listed **548**, including
  system apps such as "3 Button Navigation Bar". The switch was turned back off. The count
  line now says "apps" rather than "launchable apps" while the switch is on.
