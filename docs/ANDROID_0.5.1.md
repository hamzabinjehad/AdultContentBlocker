# Hisn Android 0.5.1: visual backend measurement

Development preview, version code 13, measured 2026-10-24 on Samsung SM-G990B2,
Android 16 (SDK 36), WebView 155.0.8059.43. Raw reports:
[ANDROID_VISUAL_BACKEND_2026-10-24.json](ANDROID_VISUAL_BACKEND_2026-10-24.json).

## What changed in 0.5.1

The visual classifier tries WebGL first. If that runtime fails to load, it retries
once in a fresh CPU runtime and keeps CPU for the rest of the process. A failed
frame stays unknown and is never replayed. Replies from a replaced runtime are
ignored. Release builds can be signed with the owner's `HISN_ANDROID_*` variables.

## Test boundary

All runs used the separate `app.hisn.android.visualcheck` debug package
(APK SHA-256 `42a56dba9ea316aa0998a75eb17771514e29e7f10d96d258e35399118240a357`).
The owner's installed `app.hisn.android` stayed at 0.4.6 with its permissions,
rules and services unchanged. No screen was captured and no accessibility or VPN
setting was changed. The phone was charging at 19–20% battery, thermal status 0.

## Results: same 12-view benign corpus, 3 repeats, 36 calls each

| Backend | Actual | Model load | Median | p95 | Max | False blocks | Unknown | Inconsistent |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| WebGL | webgl | 2,026 ms | 121 ms | 164 ms | 206 ms | 0/12 | 0 | 0 |
| CPU | cpu | 2,098 ms | 1,989 ms | 2,143 ms | 2,661 ms | 0/12 | 0 | 0 |

The corpus is the same 12 correlated views of four public-domain/CC0 photographs
used in the [2026-10-22 baseline](ANDROID_VISUAL_BENCHMARK_2026-10-22.md). The CPU
numbers match that baseline (1,984 ms median then). WebGL is about 16 times faster
per call on this phone and gave identical verdicts.

App-process memory excludes the separate WebView renderer, so it is not compared
here. This run cannot show battery drain or GPU power cost.

## Fallback checks on the device

| Check | Result |
| --- | --- |
| Default (`auto`) in a fresh process | PASS: ran on WebGL, fixed benign frame allowed |
| Load failure injected into the WebGL runtime | PASS: recovered on CPU, fixed benign frame allowed |
| Frame failure injected | PASS: that frame stayed unknown once; a fresh session used CPU and allowed a new frame |

Fault injection calls Hisn's own private failure handler in the isolated package.
It does not simulate a real GPU driver crash.

## Decision and remaining gates

Keep `auto` (WebGL first, CPU fallback) as the default. This is based on one phone
and a benign-only corpus. Still pending:

- Adult recall: needs a reviewed positive corpus kept outside the repository.
- Battery and GPU power cost over a longer, unplugged session.
- A second device or GPU vendor, and behavior after a real WebView renderer crash.
- Live capture with the owner's app upgraded to 0.5.1, which needs the owner's approval.
