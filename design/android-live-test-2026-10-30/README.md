# Hisn Android live test captures — 2026-10-30

Emulator-only evidence for the live record in
[docs/ANDROID_LIVE_TEST_2026-10-30.md](../../docs/ANDROID_LIVE_TEST_2026-10-30.md).
Do not read these images as physical-device or all-app coverage.

Device: Android 16 (API 36) emulator `emulator-5554`, 1080 × 2400, 420 dpi,
Hisn 0.8.2 (Play debug build), seed v5. The physical Samsung was inspected
read-only and was not locked or modified.

| File | What it shows |
| --- | --- |
| `01-word-block-chrome.png` | Hisn restriction screen after a normal Chrome page containing the harmless rule `hisnfixtureword` was opened. |
| `02-dns-route-pass.png` | Protection page after the route check: the DNS tunnel is still marked degraded, while the route-check line records a pass. This is the honest distinction between a point-in-time check and recent failures. |
| `03-settings-uninstall-blocked.png` | Android Settings → App info → Hisn. Archive, Uninstall and Force stop are disabled under the device-owner uninstall block. |
| `04-uninstall-on-commitment.png` | Hisn Protection shows the active 24-hour commitment and “Uninstall protection · ON … Android is refusing ordinary uninstall.” |
| `05-commitment-active.png` | Rules page with the one-day commitment remaining. |
| `06-dns-route-check.png` | DNS route-check result: “UDP and TCP DNS checks passed · Local blocks and resolver responses observed.” |

The 1-day commitment was started on the disposable emulator after provisioning
Hisn as device owner. It blocks ordinary uninstall for the commitment period
only. A factory reset, device-owner removal, ADB/root or Safe Mode at boot can
still remove the app.

The owner chose to leave the physical Samsung unchanged, so it has no lock; the
one-day device-owner lock exists only on the disposable emulator.

The test-only instrumentation fixture did not expose readable text to the
emulator accessibility service; the word block was therefore verified with a
normal Chrome page. See the live record for the exact counters and the one
limitation.
