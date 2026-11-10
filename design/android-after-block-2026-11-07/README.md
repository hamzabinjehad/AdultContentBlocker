# Android after-block pause, 2026-11-07

Captures of Android 1.3.0 on the API 36 `hisn_ui` emulator, English, light
theme, 1080 × 2400. The Settings screen was reached with the normal bottom
navigation. The pause screen was opened through the debug-only restriction
fixture with a five-minute hold, not by matching adult content.

| Capture | What it shows |
| --- | --- |
| `01-settings-en.png` | **Settings › Protection › After a block** reading *Keep this app closed for 5 min*. The picker behind it offers 1, 5, 15, 30 and 60 min. |
| `02-pause-en.png` | The pause screen after a word detection: the closed-for-5-minutes card, **Rest 5 more minutes**, **Rest 15 more minutes**, **Rest 1 hour**, **Lock the screen**, then **Back to home** and the breathing pause. |

The same screens are string-parity checked in Arabic; no Arabic capture was
taken. A device check of the actual close path with a real detection is still
open, and the emulator produced a System UI ANR during capture that was
dismissed before these images.
