# Unrot for Mac — design, 23 Nov 2026

Published privately at https://claude.ai/artifact/XNQMrxSjfsy1q7ihQxn6RJ (the page is `preview.html`, the newest version with images inlined).

| File | What it is |
|---|---|
| `index.html` | v1: the phone's Glasshouse screens put in a Mac window (rejected as "a copy"). |
| `v2.html` | v2: a native macOS 27 app — Liquid Glass sidebar and toolbar, inspector, rules table, wait panel over one window, menu bar, notch Focus, Breathe, Shield, Progress, widgets, Settings, first run. |
| `v3.html` | v2 plus **the blocking system**: how a rule is enforced on a Mac, ideas from Android/CleanPath/Opal, strictness, closed-app / limit / feeds-only moments, Shield lists, permission guide with a picture. |
| `img/` | Painted states copied from `design/glasshouse-2026-11-23/img`. |

The engine behind the blocking chapter is `macos/Hisn/DistractionRules.swift`, tested by `macos/HisnTests/DistractionRulesTests.swift`.

Not built yet: the rule store, app watcher, wait/closed panels, extension wiring for feeds-only and tab time, and all v2/v3 screens. They wait for the owner's approval of the design.
