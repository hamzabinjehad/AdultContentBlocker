# Mac 1.1.11 — after captures

Real window screenshots from the owner's Mac, captured with `screencapture` at
760 pt window width (the app minimum), dark appearance, English. They are not a
user study, a screen-reader audit or a layout snapshot test.

The before state is the 1.1.10 capture set in
[`design/macos-ui-1.1.10`](../macos-ui-1.1.10/README.md).

| File | State |
| --- | --- |
| `after-overview.png` | one status hero; a tile per layer; state-aware **Review setup** / **Start a lock…**; warnings and the setup row directly under the status |
| `after-setup.png` | progress ring (`2 / 12`), compact readiness, next-step line, per-stage `done/total` and a **Next** badge |
| `after-rules.png` | one card per site list, empty states, a parser-derived count, and a **Check a website** row |
| `after-lock.png` | end-date band, trailing switches for the mode, and the bottom-pinned action with its consequence line |
| `after-settings.png` | one card per settings group with an icon in each heading |
| `after-sitecheck-empty.png` | the checker before a query; the sheet sizes to its content |
| `after-sitecheck-blocked.png` | a blocked domain with the deciding list and version, and the bundled-seed note |
| `after-sitecheck-allowed.png` | an address on neither the core list nor the person's lists, with **Add to Always block** |

The captures show this Mac's real state — ad-hoc build (system filter off),
hosts-file blocklist on, browser extension connected — not a mocked healthy
machine. The Rules capture shows one personal allowed domain (`youtube.com`)
because the window is the actual app; nothing else on the desktop is included.
The checker ran against the signed bundled core seed because this ad-hoc build
has no app-group container; the installed-list path is preferred on a signed
build.

Design research observed Screen Zen 2.0.0 (build 63) read-only on this Mac and
read public product pages and reviews of Opal, one sec, Jomo and Clearspace.
The third-party capture stays in temporary storage and is not redistributed.
No competitor code, assets, visual identity or copy was reused.
