# Android people cover captures — 8 November 2026

API 36 `hisn_ui` emulator, isolated `app.hisn.android.visualcheck` debug package and
its `app.hisn.android.visualcheck.test` fixture. The fixture face is the pinned
public-domain NASA portrait of astronaut Eileen Collins from the visual-eval
benign corpus. No network, real app, private image or adult content was used.

| Capture | State |
| --- | --- |
| `settings-people.png` | Rules › Built-in checks › Blur people, with Off / All faces / Women (estimated) / Men (estimated) |
| `people-cover-all.png` | All faces: a matching face covers the whole screen; the snapshot is 10 px abstract color and the scrim is opaque |
| `people-cover-women.png` | Women (estimated): the same face matches the preference and is covered |
| `people-off.png` | Off: the same fixture remains visible, confirming the feature is opt-in |

The cover is an accessibility overlay above the app, below the status bar. It is
touch-blocking, so the person leaves with Home or by switching apps. The top
status-bar strip can still show the app's edge-to-edge pixels; the app content
below it is covered. These are emulator captures, not a phone or accuracy claim.
