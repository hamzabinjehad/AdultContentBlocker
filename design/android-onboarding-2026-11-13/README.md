# Android onboarding redesign, 13 November 2026

Captured on the owner's Samsung SM-G990B2 (Android 16, dark mode) from a separate test copy,
`app.hisn.android.visualcheck`, so the phone's real Hisn was not touched. Direct build.

The first run is now Compose in the shell's own theme (`nativeshell/Onboarding.kt`,
`WelcomeActivity.kt`), replacing the View-based `WelcomeActivity.java`. Patterns come from the
[competitor pass](../competitors-2026-11-13/README.md), mainly Opal:

| Capture | What it shows |
| --- | --- |
| `en-01-shield.png` | One sentence per page, tap anywhere, a slowly breathing shield, and the privacy line: "Your rules and activity stay on this phone. No account." |
| `en-04-how-open.png` … `en-06-how-pause.png` | "How it works" on a drawn phone: you open something you would regret → Hisn steps in before it loads ("Not this time") → you get a pause, not a lecture. |
| `en-07-you.png` | Step 1 of 3: "What should I call you?" and the reason as chips. Optional. |
| `en-08-permissions.png` | Step 2 of 3: "I can help with that, Sam." Each grant is a native switch with one line of why, split into needed for blocking and optional. A switch shows what Android reports; flipping it opens Android's screen. |
| `en-09-guard.png` | Step 3 of 3 (direct build only): the uninstall guard, with "Requires App protection above" while accessibility is off. |

Not copied from the competitors: projected "years of your life", an account wall, a paywall,
streak flames. The DNS disclosure, the guard behaviour and every string's Arabic copy are kept.

Not checked: Arabic on a device (the app sets its own language in Settings, so a per-app
locale did not apply; the tests check right-to-left layout only), TalkBack speech, and light mode.
