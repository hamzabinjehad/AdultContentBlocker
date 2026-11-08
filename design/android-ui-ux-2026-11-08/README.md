# Android UI/UX pass — 8 November 2026

Device: Samsung SM-G990B2, Android 16, Hisn 1.5.3-direct → 1.5.4-direct.
Tools: `adb`, `uiautomator dump`, `screencap`, `logcat`, `dumpsys accessibility`,
`dumpsys connectivity`, Android Studio JBR and the Gradle build.

This is a narrow UI/UX and reliability pass, not a visual redesign. It records
what the phone actually showed and what was changed.

## What was wrong

### 1. One screen in two languages

The dashboard rendered Arabic labels beside English values: Arabic toolbar,
tile names, bottom navigation and section labels; English hero, tile values,
next-step sentence, button and DNS body. See
[before-mixed-language.png](before-mixed-language.png).

Cause: the system locale list is `de-DE, ar-AE, en-DE`. The app declares
`en` and `ar` in `locales_config.xml`. Layout resources resolved to Arabic,
while several strings read from Java resolved to the English default on the same
screen. The app has no explicit in-app language choice.

Immediate fix on this phone: set the per-app locale explicitly.

```sh
adb shell cmd locale set-app-locales app.hisn.android --locales en
```

The result is one language throughout: [after-consistent-english.png](after-consistent-english.png).
The code-level fix is listed below; it should not rely on a device setting.

### 2. “Reconnect needed” while the service was bound

`dumpsys accessibility` showed
`Service[label=حصن, id=app.hisn.android/.TextGuardService]` in **Bound services**,
but the app showed **Apps: Needs attention**, “Hisn accessibility is enabled but
not connected”, and a generic “Open accessibility settings” button.

`logcat` also showed a leaked receiver:
`Service app.hisn.android.TextGuardService has leaked IntentReceiver … originally
registered at TextGuardService.onCreate(TextGuardService.java:202)`.

The app relied on `onServiceConnected` and a one-shot rearm broadcast. After an
update or rebind, Android can keep the service alive without repeating that
callback, so the static `connected` flag stayed false. The broadcast is sent once
on resume and can be missed while the service is being created.

### 3. The guard blocks the recovery the app asks for

Trying to turn Hisn accessibility off and on opened the direct build's
self-protection screen: **Protection held** — “Hisn closed a screen that would
force-stop it, clear its data, deactivate its device admin or turn off its
accessibility service.” See [protection-held.png](protection-held.png).

That is the removal guard doing its job, but it means the app’s own advice
(“turn Hisn off and on”) is a dead end while the guard is armed. A repair must be
a reboot, a deliberate guard-off flow, or an in-app rearm that does not require
the system toggle.

### 4. Smaller friction

- “Apps On” beside “Adult text Off” reads as a contradiction; “Apps” means the
  accessibility guard, not the text check.
- “Experimental coverage” appears in the hero and the DNS row; it weakens a
  status that is otherwise green.
- The DNS attention card said “Recent requests failed or were unsupported”
  after an airplane-mode period, even after DNS resolution worked again, until a
  route check was run.
- “Open accessibility settings” opens the generic list of 12 services with no
  highlight; the person must find Hisn among ScreenZen, HaramBlur and others.
- System dialogs (“Abbrechen”, “Zulassen”) stayed German while the app was
  English; the app does not apply its locale to those system buttons.

## What changed in 1.5.4

`android/app/src/main/java/app/hisn/android/TextGuardService.java`

- `onCreate` now marks the live service `connected` and runs the idempotent
  `startGuard()`, instead of waiting for a callback Android may not repeat.
- `onUnbind` returns `true`; a new `onRebind` sets `connected` and starts the
  guard again.
- The rearm `BroadcastReceiver` is unregistered in `onDestroy`, fixing the leak.

`values/strings.xml` and `values-ar/strings.xml`

- `text_guard_reconnect`: “Turn accessibility off and on” / “أوقف إمكانية الوصول
  ثم شغّلها”.
- `attention_guard_disconnected`: now names the exact path, Accessibility →
  Installed apps, and tells the person to turn Hisn off and on and return.

Build: 1.5.4, versionCode 42, signed with the release key. Installed on the
phone over 1.5.3 with data preserved. Core suite, lint and release check pass;
hashes are in [the 1.5.4 record](../../docs/ANDROID_1.5.4.md).

## What is still wrong

The installed 1.5.4 still shows the disconnected state because the accessibility
service was already bound before the new process started; the new `onCreate`
has not run for a fresh instance. The self-protection guard blocks the toggle.
The safe next step is a **reboot** (accessibility services rebind at boot;
1.5.4 then proves the live instance connected), followed by unlocking the phone.

Still open for the next pass:

1. Apply one supported locale in every activity (a `LocaleSupport.wrap` in
   `attachBaseContext`) so layout and Java strings cannot diverge; keep the
   per-app locale setting as the user’s choice.
2. Replace “Turn accessibility off and on” with a **Repair guard** action when
   the service is bound but the flag is stale: force a rearm, or state that a
   reboot is required, and never send the person into a toggle the guard will
   close.
3. Add a direct link or highlighted path to Hisn inside the accessibility list;
   show the system-dialog language mismatch as a known limitation.
4. Rename the “Apps” tile to “App access” or “Screen access”, and show the text
   check as its own state.
5. Re-run the route check automatically after a network change and clear the
   stale attention state; keep the explicit consent for the deeper UDP/TCP check.
6. Consider “Coverage: experimental” as secondary detail rather than hero copy.
