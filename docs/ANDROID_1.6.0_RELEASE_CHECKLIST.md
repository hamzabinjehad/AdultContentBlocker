# Android 1.6.0 (43): signed build and phone checklist

Date: 13 November 2026. Status: to run. The code side of the 1.6.0 candidate is done
(see [the finish plan](ANDROID_FINISH_PLAN_2026-11-12.md)). This file covers what
is left before publishing: the signed build, the phone run, and the publish step.
Tick each box, and write the result or a short note next to it.

## 0. Decide first

- [ ] **Where it ships.** Direct APK only (recommended for 1.6.0), Play only, or
      both. The Play build has no uninstall protection, and Play reviews apps that
      use the accessibility service.
- [ ] **Watchdog restart.** May the background check restart the DNS blocker after
      it stops? Today it only notifies. "No" is fine for 1.6.0, but write it down.
- [ ] **What is on the phone now:** Settings › Apps › Hisn › version. Write it here:
      `________`. If it is not signed with the release key (`1.6.0-direct` or
      `1.2.x-direct` from `dist/`), installing over it fails, and an uninstall deletes
      its rules and lock.

## 1. Build the signed artifacts

From the repository root, on a clean commit:

```bash
bash android/build-signed-release.sh
```

The script refuses to run with uncommitted changes. It runs lint, the unit tests,
`test-core.sh` and the string check. It then builds the Play APK and AAB and the
direct APK with the key in `keys/android/`. Each APK is checked with
`check-release.py` and with `apksigner`, against certificate `2fbed657…`. The output
goes to `dist/android-1.6.0/` with `SHA256SUMS` and `BUILD.txt`.

- [ ] Script finished with "Done". Commit: `________`
- [ ] Direct APK SHA-256 (from `SHA256SUMS`): `________`

## 2. Install over the current app (do not uninstall)

With the phone plugged in and USB debugging on:

```bash
adb install -r dist/android-1.6.0/direct/hisn-1.6.0-direct.apk
```

- [ ] "Success". If it says `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop. The phone has
      a build signed with another key; see step 0.
- [ ] Rules, the lock and the personal name are still there after the update.
- [ ] After the update, the app shows whether App protection (accessibility) is
      off, and re-enabling it in Settings › Accessibility › Installed apps brings
      it to On. Android switches the accessibility service off on an app update.

## 3. Phone checklist

Use a test site you know is on the list. `adb shell` commands are optional
helpers. **Pass** means the home screen says 4 of 4 (or the layers you use)
and the test site does not load.

### Staying on

- [ ] **Reboot.** Restart the phone and don't open Hisn. Within 2 minutes the VPN key
      icon is back and the test site is blocked.
- [ ] **Force stop.** Settings › Apps › Hisn › Force stop. With uninstall protection
      on, the shield should close that page. With it off, note how long the blocker
      stays down until you open Hisn: `________`
- [ ] **Swipe away from recents.** The test site is still blocked.
- [ ] **Doze / overnight.** Leave the phone idle with the screen off for at least an
      hour (or run `adb shell dumpsys deviceidle force-idle`, then
      `adb shell dumpsys deviceidle unforce` after). The site is still blocked.
- [ ] **Battery saver on.** The site is still blocked.

### Networks

- [ ] **Wi-Fi → mobile data → Wi-Fi.** The site is blocked on each, and normal sites
      load.
- [ ] **Airplane mode on and off.** Hisn recovers without being opened.
- [ ] **Captive portal** (hotel or café Wi-Fi sign-in page). The sign-in page opens,
      and blocking works after sign-in.
- [ ] **IPv6 network** (most mobile data). The site is blocked. Run **Check the DNS route**
      in Hisn and note the result: `________`
- [ ] **Private DNS** set to a provider (Settings › Connections › More › Private DNS).
      The site is still blocked. If not, note what Hisn shows: `________`
- [ ] **Another VPN.** Turn on any other VPN app. Hisn shows DNS as off (Android
      allows one VPN), and turning the other one off lets Hisn come back.
- [ ] **Browser Secure DNS.** In Chrome, Settings › Privacy › Use secure DNS on.
      The site is still blocked by the text or app layer, or Hisn says DNS is
      bypassed.

### Layers and screens

- [ ] **Text layer.** A search for an explicit term in a browser shows the block screen.
- [ ] **Image checks** (Android 14+, switched on). An adult image in a chosen app is
      covered or blocked. This confirms TFLite survives R8 shrinking, which the
      emulator run did not observe.
- [ ] **Blocked app / daily limit.** A blocked app opens the restriction screen, and
      a short test limit closes the app when it runs out.
- [ ] **Uninstall protection on.** Trying to uninstall Hisn from the home screen
      and from Settings › Apps is closed by the shield. The Settings row in Hisn
      shows the same state.
- [ ] **Protection lock.** Start a 1-day lock. Removing a rule is refused with a
      clear message, and adding a rule still works. (Only do this if you can live
      with a 1-day lock.)
- [ ] **Long-press the app icon.** The Rules and Settings shortcuts open those tabs.
- [ ] **Arabic.** Switch the phone language to Arabic. The shell is mirrored and
      readable.
- [ ] **Large text.** Font size at maximum. No cut-off buttons on Home, Rules or
      Settings.
- [ ] **No crashes** during all of the above. After the run, the crash log should be
      empty:

```bash
adb logcat -d -b crash
```

## 4. Publish

Only when sections 1–3 pass, or each failure is written down and accepted.

- [ ] Write `dist/android-1.6.0/RELEASE_NOTES.md` in the same shape as
      `dist/android-1.2.3/RELEASE_NOTES.md`: what is new, install, verify (hash and
      certificate), limits. Copy the "Limits" from whatever in section 3 was not run.
- [ ] Update `versions.json`, `docs/VERSIONS.md`, `android/README.md` and the
      CHANGELOG to say the signed 1.6.0 exists and what the phone run showed.
- [ ] Publish the direct APK with its SHA-256 and the certificate fingerprint.
      Never upload the direct APK to Play.
- [ ] If Play: upload `play/hisn-1.6.0-play.aab`, and fill in the accessibility
      declaration and the VPN form.
- [ ] Tag the commit from `BUILD.txt`, for example `android-1.6.0`.

## Results

| Date | Phone / Android | Build hash | Passed | Failed or not run |
| --- | --- | --- | --- | --- |
| | | | | |
