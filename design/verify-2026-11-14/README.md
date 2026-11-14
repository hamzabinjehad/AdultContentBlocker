# Mac browser link and recovery countdown check

Date: 14 November 2026.

## What was wrong

The installed Mac guard gave a browser relaunched after a silence closure only
five seconds (`warnRepeat`) before closing it again. That was shorter than the
extension's thirty-second heartbeat, so the browser could never reconnect, and
the person had no time to find the extension. The system native-messaging
manifests on this Mac also still named
`/Applications/Hisn 1.1.17 UNSIGNED.app/Contents/MacOS/HisnBridge`, a bundle
that had been deleted, while the app removed the working user-scope copy merely
because a system file existed.

## What changed

- Every closure countdown is now a full minute, including a relaunch after an
  earlier closure. Confirmed profile loss keeps its five minutes.
- A system link whose bridge is missing falls through to the user-scope link
  Chrome checks first; a readable system link to a live bridge is still
  deferred to and a malformed one is left alone. Mac build 32.

## Verified

- `xcodebuild ... -only-testing:HisnTests/BrowserGuardPolicyTests
  -only-testing:HisnTests/NativeMessagingInstallerTests`: 61 tests, 0 failures.
- Chrome for Testing 157 with `dist/hisn-unpacked-cft` and a test-scope native
  host manifest: its service worker loaded and the Hisn app row read
  **App connected** (`popup-connected.png`).
- Build 32 installed as `/Applications/Hisn.app`; the repaired system manifest
  points at `/Applications/Hisn.app/Contents/MacOS/HisnBridge`. A fresh Helium
  profile loaded the unpacked extension, checked in at
  `extensionSeen.process:<pid>:net.imput.helium`, and stayed open past the
  guard's former deadline.
- The person's own phone was not touched.
