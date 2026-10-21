# Hisn design guide — Apple HIG-informed

Reviewed 2026-10-12. Applies to macOS, iOS, iPadOS and the browser extension.
This is a project design specification, not a claim that the current product
passes every HIG or accessibility requirement. It does not change enforcement.

### Implementation pass — 2026-10-13

Mobile overview now puts current layer status before commitment and moves the
introductory explanation into a disclosure. No enforcement evidence is removed.
The overview has a bounded reading width in regular size class and returns to
full width in compact multitasking. Native grouped background and larger minimum
row height remain adaptive. Mac layer actions sit below their descriptions to
avoid squeezing text, and shared section headings have accessibility traits.
The extension status heading is more prominent, with clearer keyboard focus,
hover affordances and reduced-motion treatment; intrinsic popup sizing is kept.

Validation: 70 isolated Helium UI checks passed after correcting an initial
overflow regression; Arabic dark popup screenshot inspected. Twenty Mac setup
tests and the Arabic catalog gate passed. The universal mobile simulator build
passed. Native screenshot, VoiceOver, large-text and physical iPad acceptance
remain pending. No installed app or real browser profile was changed in this pass.

## Purpose

### Navigation and surface redesign — 2026-10-13

Mac now has a branded sidebar and consistent page introductions with semantic
native colors and an expanded reading measure. Full mobile uses a sidebar/detail
navigation on regular-width iPads and retains native tabs in compact widths.
The free Safari edition now separates Protection, Setup and Settings instead of
presenting one long list. Its summary names the actual enabled part count and
keeps domain-only coverage limits visible; the setup page offers explicit steps.

Chromium now opens on a status-first summary followed by grouped layer rows.
Settings navigation and forms use the same new neutral/blue token palette,
rounded surfaces and light/dark hierarchy. Safari web settings shares those
tokens with contrast/reduced-motion fallbacks. Existing message handlers, save
guards, draft handling, permissions and enforcement policy are unchanged.

Evidence: 70 isolated browser UI checks passed before the final Safari palette
pass; the full extension suite and 27 Python mobile tests passed. Both mobile
targets built for Simulator; 38 Mac setup tests and the Arabic gate passed, and
the final Mac build confirmed success. Actual iPhone Simulator English/light
and Arabic/dark app screenshots were inspected. A later full-browser rerun
timed out at browser launch under host load, not at a UI assertion; final Safari
palette visual acceptance remains pending. iPad startup completed but a usable
app screenshot was not collected. Mac visual inspection, iPad screenshots,
VoiceOver, maximum text size and physical-device acceptance remain pending.

The redesigned builds have not replaced the installed Mac/phone apps. Test
simulators were shut down; real browser profiles and device policies were not
modified. Do not present this design pass as stronger content classification.

### Grouped-surface redesign — 2026-10-13

The popup now separates summary, grouped layer rows and persistent actions with
consistent rounded surfaces. Full settings uses the same summary/connection
hierarchy. High-contrast preference strengthens boundaries without replacing
textual evidence. Mac cards use semantic native backgrounds and separator
colors rather than a low-opacity decoration. Mobile overview is ordered as
assessment, current status, commitment, readiness details and further guidance;
the redundant status refresh is removed while assessment refresh remains.
Regular-width iPad setup now shares the overview's bounded reading measure.

Evidence: 70 isolated browser UI checks passed; English light and Arabic dark
popup screenshots reviewed. Universal mobile simulator build, 20 Mac setup
tests and Arabic localization validation passed. Native visual/device and
assistive-technology acceptance are still not established by these checks.
This pass is local, not installed, and changes no protection policy.

Help someone answer three questions quickly: What is protected? What needs my
attention? What commitment did I choose? Keep protection evidence separate from
commitment duration. A recorded lock does not prove a running filter.

Apple's [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)
are the platform reference. The decisions below adapt them to Hisn; project
spacing, popup sizes and acceptance tests are our choices, not Apple mandates.

## Shared information hierarchy

1. Protection summary: scope, verified layers, last successful check.
2. One most important next action, such as “Connect your browser”.
3. Commitment: end date, remaining time and permitted recovery route.
4. Layer details and advanced configuration, disclosed when needed.

Every layer must distinguish checking, verified/configured, needs action,
unavailable, unsupported and deliberately off. Use text and a symbol with color.
“Configured” is not a synonym for verified runtime coverage. Never show a green
all-device shield based on DNS configuration, app installation or one heartbeat.

## Navigation by platform

| Surface | Design direction |
| --- | --- |
| Mac | Keep the existing sidebar: Overview, Protected Setup, Blocking Rules, Lock, Settings. Preserve selection and drafts; support resizing and keyboard navigation. |
| iPhone | Keep familiar tab navigation. Give commitment and blocking rules clear destinations within the current structure before adding more tabs. Use stacked content, not squeezed desktop columns. |
| iPad | Adapt to available width: sidebar/detail where useful, compact navigation in narrow multitasking. Do not depend on a fullscreen device size. |
| Extension | Status-first popup with in-popup editing and an optional full settings page. Preserve drafts while navigating; clearly distinguish browser-only and app-managed settings. |

Apple references: [sidebars](https://developer.apple.com/design/human-interface-guidelines/sidebars),
[tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars),
[layout](https://developer.apple.com/design/human-interface-guidelines/layout).

## Visual language

- Use system typography and semantic text/background colors in native apps.
  Extension tokens should match the same hierarchy, not imitate native chrome.
- Follow system light/dark appearance by default; offer explicit appearance
  choices only if both are fully tested. Black/white surfaces need depth and
  visible separators, not one undifferentiated slab.
- Use a consistent accent for primary actions. Reserve warning/error emphasis
  for actual problems; avoid making the whole interface look dangerous.
- Start with an 8-point spacing rhythm for custom layouts, then adjust to native
  component spacing. Prefer whitespace and headings over nested cards.
- Use SF Symbols in native UI and the existing licensed icon set on the web.
  Give icon-only controls accessible names; hide purely decorative symbols.
- Prefer standard navigation and controls. Do not add custom glass to every
  content card. Preserve opaque readable fallbacks on supported older systems.

Apple's [materials guidance](https://developer.apple.com/design/human-interface-guidelines/materials)
places Liquid Glass primarily in the controls/navigation layer and recommends
restraint. Respect reduced transparency and contrast preferences.
See also [Dark Mode](https://developer.apple.com/design/human-interface-guidelines/dark-mode).

## Interaction, commitment and consent

Use action labels that say what happens: “Check protection”, “Save rules”,
“Request delayed release”. Explain why a change is unavailable during a lock,
and when it becomes possible. Keep strengthening actions available where the
actual policy permits them; the worker/native authority still enforces policy.

Setup should show one next task with expandable advanced details. Explain scope
before requesting permissions or administrator confirmation. Opening Settings
is not completing setup. Recovery must remain visible and consistent with the
existing commitment rules; design must not add a silent escape or a hidden trap.

Use inline status for routine checks, progress and recoverable failures. Reserve
modal alerts for consequential decisions or interruptions requiring a response.
Avoid repeatedly presenting the same modal without a meaningful state change.
References: [alerts](https://developer.apple.com/design/human-interface-guidelines/alerts),
[privacy](https://developer.apple.com/design/human-interface-guidelines/privacy).

## Accessibility and language

Support larger text without truncating actions or collapsing controls. Test
VoiceOver reading order, keyboard focus, reduced motion, increased contrast and
light/dark appearances. Do not rely on color alone or tiny unlabeled gear icons.

Arabic must receive the same functionality and content as English. Mirror
directional navigation, not logos or nondirectional icons. Keep URLs, IDs and
technical commands readable left-to-right within the Arabic interface. Test
long translated button labels and mixed-direction sentences.

Reference: [accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility).

## Current-code review and priorities

The native Mac already uses a sidebar and separates protection from lock state.
Mobile already uses native lists, tabs and confirmation alerts. Preserve these.

Next implementation passes, in order:

1. Simplify MobileRootView overview: it currently stacks setup, readiness,
   commitment, introduction, status and browser guidance. Keep one summary and
   one next action before detailed sections; do not discard evidence.
2. Consolidate duplicate refresh/connect actions across setup and status views.
   Make pending, failure and last-check states understandable without scrolling.
3. Improve iPad width adaptation without changing protection policy.
4. Audit native and extension settings for text scaling, focus and draft/error
   handling. Keep the popup's current wide intrinsic sizing and narrow fallback.
5. Validate removal-protection guidance: distinguish policy readback from
   physically tested browser behavior and from administrator-removal resistance.

## Release acceptance checklist

- Review English/Arabic, light/dark, long labels and maximum supported text sizes.
- Exercise Mac resizing, iPad portrait/landscape/multitasking and smallest phones.
- Verify VoiceOver and keyboard operation, including sheets and embedded editor.
- Test checking, offline, missing permission, stale evidence, active commitment,
  app-disconnected and browser-only states; no incorrect “protected” success.
- Test a real toolbar popup, not only a webpage approximation; no horizontal
  overflow or single-letter wrapping. Scroll must leave every action reachable.
- Compare screenshots before/after and rerun policy/commitment tests. Visual
  changes cannot loosen a lock, log browsing history or change OS settings.
- Record physical-device gaps explicitly. Simulator builds are not iPhone/iPad
  acceptance, and unit tests are not a visual-accessibility audit.

Use this guide with THREAT_MODEL.md, APPLE_PLATFORMS.md and
SELF_CONTROL_COMMITMENT.md whenever changing user-facing protection flows.
