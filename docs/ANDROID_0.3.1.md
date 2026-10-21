# Hisn Android 0.3.1 — foreground checks and honest layer health

Version code 4. Development build, not a complete adult-content filtering release.

## Functionality

- After a successful non-matching word scan, a bounded heartbeat schedules
  another inspection about one second later. Content/scroll events can trigger
  earlier checks; continuous content events do not continually postpone a queued
  inspection.
- Each scan still verifies the selected package owns the active root before
  reading text. Moving to a different app stops the heartbeat after bounded
  metadata-only root retries. Unbind, interruption and destruction cancel its
  callbacks. A matched restriction cancels pending periodic work.
- On accessibility binding, Hisn checks the current root's package metadata and
  schedules inspection if that app is selected, rather than relying solely on
  a future window event. A missing root at bind time still needs a later event.
- Root acquisition exceptions are caught alongside traversal errors, preventing
  that path from crashing the service. Setup/inspection failures remain visible
  as incomplete protection, not fabricated connection loss.
- The dashboard combines actual binding state with Android's enabled-service
  declarations and readable saved settings. It distinguishes permission needed,
  enabled-but-disconnected, connected-with-no-rules, active selected rules,
  partial inspection and unavailable settings. DNS failures also raise the
  attention heading. A commitment-clock read failure is shown instead of
  crashing the refresh loop.
- English and Arabic copy explains periodic rechecking and its on-device scope.
  No permission is granted automatically and no data is sent to a server.

## Verification

Core suites pass, including all 48 combinations of permission knowledge,
binding, configuration, settings availability and partial inspection. Debug and
test APK builds, Android lint and release-manifest generation pass. Lint reports
zero errors and six warnings. The release manifest contains no debug fixture
setup or test activity.

The external live runner passed on the connected Samsung SM-G990B2 / Android 16
on 2026-10-19:

- Ordinary, editable and password-field fixtures stayed available.
- Periodic checks ran while the selected fixture was open and stopped after
  leaving it for Home.
- Literal word, long text, child-75 and selected-app restrictions showed the
  restriction screen.
- Rapid reopening exercised enforcement during the warning cooldown and was
  blocked.
- Fixture-only additions were removed; existing owner rules were preserved.

Android reports installed version 0.3.1 / code 4 with Hisn bound and no crashed
service entry. The Arabic dashboard was visually checked: it correctly says
the service is connected but no app/word restrictions are selected and DNS is
off, rather than claiming active protection. Activation remains an owner choice.
The runner never starts a commitment or grants a service permission.

Installed debug APK SHA-256:
`7e1354a97353069bdff8c02303133c75183af877927bc0c46b820c881191931f`.

## Limits

This is visible-text checking in explicitly selected apps, not visual analysis,
OCR, all-app enforcement or a guarantee that content cannot appear first. The
existing 512-node/child-attempt and 32,768-character budgets remain. Periodic
work can cost battery in complex selected apps; sustained battery and latency
benchmarks are still needed. Permission revocation and stopped/crashed services
can require owner action in Android settings; this is not an OS bypass or an
unstoppable watchdog. Real-app layouts and live DNS routing still need further
testing and separate VPN consent.
