# The Mac app on macOS 12

The Mac app's deployment target was macOS 13 (Ventura). Everything it actually
protects with — the `NEFilterDataProvider` system extension, the Mach-service
XPC channel, the lock stores, the MDM profile — already ran on macOS 10.15.
What held the app at 13 was its SwiftUI shell — a set of controls SwiftUI added
in macOS 13, plus a couple of system APIs outside the UI — and no deeper
incompatibility. This is what replaced each one, what still differs on macOS
12, and what has and has not been run on a Mac running it.

The deployment target is declared once, in `macos/generate_xcodeproj.py`
(`DEPLOYMENT_TARGET`), and every generated target inherits it — so a target
added later starts at 12.0 too. The app, the filter extension and the bridge
now carry `minos 12.0`:

```
otool -l Hisn.app/Contents/MacOS/Hisn | grep -A3 LC_BUILD_VERSION
  platform 1
    minos 12.0
      sdk 27.0
```

## Replaced

| macOS 13 API | Why it was there | What runs on macOS 12 |
| --- | --- | --- |
| `OSAllocatedUnfairLock` | Mutex around the policy record, the XPC reply box, the filter's code-identifier cache | `HnLock` over `os_unfair_lock` — the same primitive and priority donation, with the same `withLock` / `withLockUnchecked` surface. The filter's per-flow path is why this is not an `NSLock`. |
| `NSXPCConnection.setCodeSigningRequirement` | The filter's listener pins every connection to this team's signature, re-checked per message | On 12 the listener checks the connecting PID's code signature against the same team requirement when the connection is offered (`FilterXPC.process(pid:satisfies:)`). The system still binds every later message to that one connection. |
| `MenuBarExtra` | The menu bar item and the lock countdown | `NSStatusItem` with an `NSMenu` rebuilt as it opens. The rows come from `StatusMenuModel`, a state → rows function, so the menu's content is testable. The icon and countdown logic (`MenuBarIcon`) are unchanged. |
| `openWindow` / `WindowGroup(id:)` | Bring back the main window after closing it | The window is adopted once through an `NSView` probe, and the close button hides it instead of destroying it: `MainWindowCloseProxy` answers `windowShouldClose` with `orderOut` and forwards every other delegate question to SwiftUI's own delegate. "Open Hisn" is then `makeKeyAndOrderFront` on either macOS. |
| `.defaultSize(width:height:)` | The 980×700 first-launch window | `MainWindow.adopt` sets and centers that size once, recorded in `hisn.window.framed`; SwiftUI's frame restoration owns it afterwards. |
| `NavigationSplitView` | Sidebar + detail with a draggable divider | `HSplitView` with the sidebar's `HnPalette.sidebar` surface and its 212/232/268 width bounds. |
| `Grid` / `GridRow` | The Settings → Details table | A stack of aligned rows with a fixed 200-point label column. |
| `Layout` protocol | The two-column page rail, the wrapping chip flow | Measured fallbacks: `HnTwoColumnFallback` reads its width and chooses one column or two; the chip flow measures each chip's natural width and wraps the longest run that fits. |
| `.scrollContentBackground(.hidden)` | Keep the page and the words editor on the design's own surface | The `NSTextView` behind a `TextEditor` is made transparent once it is in the hierarchy; a plain `ScrollView` draws no background there, so it needs nothing. |
| `.tracking(_:)` on a `View` | Sidebar group labels and eyebrows | Applied to `Text` — that method predates macOS 13 — before `.textCase`, which is what had turned the chain into a `View`. |
| `Locale.region` | The region in the Arabic locale override | `AppLanguage.regionCode(from:)` reads the same region out of the locale identifier. |

All of the fallbacks are selected at run time (`if #available(macOS 13.0, *)`),
so macOS 13 and newer keep the newer API and the app's current look. The
compatibility file is `macos/Hisn/Compatibility.swift` (shared by the app, the
filter and the bridge) and `macos/Hisn/CompatibilityUI.swift` (app only).

## What still differs on macOS 12

- **The sidebar cannot be collapsed.** `HSplitView` has no sidebar toggle; the
  sidebar is always there, and its material is the flat `HnPalette.sidebar`
  colour rather than the system sidebar material. The title bar's own buttons
  are still installed — a test pins that both custom toolbar items survive the
  fallback — only the toggle and its separator are missing.
- **The first frame of a page can be one column.** The two-column and chip-flow
  fallbacks learn their width one layout pass after the first frame, so inside
  a wide window they reflow once. Nothing is lost or clipped in either frame.
- **The words editor's transparency is set by walking the view hierarchy.** If
  a future SwiftUI stops wrapping `TextEditor` in an `NSScrollView`, the probe
  finds nothing and the editor keeps its default background — a cosmetic
  difference, not a functional one.
- **The XPC peer is checked once per connection, not per message.** A process
  that exits and whose PID is reused between the kernel creating the connection
  and the listener's check could in principle be checked in the exiting
  process's place. That needs a cooperating process in the same user session,
  and everything after admission is bound to the accepted connection. macOS 13
  and newer keep the per-message check. The client side loses the matching
  pin — `setCodeSigningRequirement` is what it used — but the filter's Mach
  service lives in the system bootstrap namespace, where only a root-owned
  process can register the name, so an unprivileged process cannot answer in
  its place.

## What is not verified

- **No boot on macOS 12.** This Mac runs macOS 27.2, and no macOS 12 machine or
  VM was available. The 12-target build proves the API surface is available;
  the forced-legacy render test below proves the fallback layouts draw; neither
  proves that macOS 12 itself lays out `HSplitView` or a status item the same
  way, and no signed build was installed on 12, so the system extension was not
  loaded there.
- **No signed build, so no live filter.** `macos/verify_enforcement.sh` was not
  run against a 12-target signed install: the filter, the XPC channel and the
  guardian daemon on macOS 12 are compiled and unit-tested, not exercised.
- **The peer check inside the filter's sandbox is inferred, not seen on 12.**
  The filter is a sandboxed system extension. A separately built ad-hoc app
  with `com.apple.security.app-sandbox` (the same enforcement) called the same
  `SecCodeCopyGuestWithAttributes` + `SecCodeCheckValidity` pair on macOS 27.2
  and got a code object and a satisfied requirement, so the sandbox is not
  expected to block it. If a future macOS 12 build did return
  `errSecCSSignatureFailed`, the listener refuses the connection — the
  authority channel closes, it does not open unchecked.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Every target builds for 12 | `xcodebuild -scheme Hisn -configuration Debug build -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO` | Build succeeded |
| Release optimisations build for 12 | the same with `-configuration Release` | Build succeeded |
| Tests build and run | `./test.sh macos` | 624 tests, 0 failures; the Arabic export check, run on its own, reads 851 strings, all with Arabic |
| Binaries target 12 | `otool -l` on the app, the filter and the bridge | `minos 12.0` on all three |
| The fallback layouts draw | `MacUIRefreshTests.testLegacyPagesRenderForVisualReview` renders every page with `HnCompat.forceLegacy = true` into `/tmp/hisn-mac-ui/legacy-*.png` | Every page renders; the words editor's cleared background is `#302C26` on both paths |
| The toolbar survives the split | `MacUIRefreshTests.testTheToolbarButtonsSurviveTheMacOS12Split` | Two custom buttons on both paths; only the toggle and separator differ |
| The window probe finds its window | `CompatibilityTests.testTheWindowProbeFindsItsWindow` | Passes |
| The lock excludes | `CompatibilityTests.testTheLockSerialisesConcurrentMutation` (2,000 concurrent increments) | Passes |
| The close button hides | `MainWindowCloseProxyTests` | Passes |
| The menu's rows | `StatusMenuTests` (lock commands, counts, gray rule, order, and each row's `NSMenuItem`) | Passes |
| The status item exists | `StatusItemControllerTests` | Passes |

`HnCompat.forceLegacy` also honours `HISN_FORCE_LEGACY_UI=1`, so a person can
launch the macOS 13+ app with the macOS 12 paths and look at them without an
older machine.
