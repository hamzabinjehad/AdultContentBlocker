# Chrome Web Store readiness — Hisn Protection 1.5.2

This is the owner-facing record for publishing the extension. It is not a
listing that exists yet. The packaged zip is built by `extension/package.sh`
(`dist/hisn-extension-1.5.2.zip`, SHA-256
`ef3f7d8a9c8e2a290891ef92b56f2b44fdc3d0ad071cf83c44dfe6f41fc25d7f`);
the store assigns its own extension ID, which then has to be wired into the
native-messaging installer and policy generators.

**Before the next upload:** the permission table below describes the next build,
not the 1.5.2 zip named above. The `offscreen` permission was restored after
1.5.2 (the opt-in visual layer had been unavailable in Chromium without it), so
the 1.5.2 artifact predates the fix and must not be uploaded. The existing
`dist/hisn-extension-1.5.3.zip` is also a pre-fix build and fails the current
package check (“missing bundled visual resource:
`visual/wasm/tfjs-backend-wasm.wasm`”). Rebuild, record the new version and
SHA-256, and run the store-package smoke test described in the extension
improvement plan.

## Listing copy

**Name:** Hisn Protection

**Short name:** Hisn

**Summary (under 132 characters):**
> On-device adult-content and SafeSearch filtering for Chromium. No browsing history leaves your device.

**Category:** Productivity. Secondary language: Arabic.

**Description:**

> Hisn blocks listed adult domains, forces SafeSearch on supported search
> engines, and checks accessible page text for adult content. Optional local
> image and video checks blur media on this device; nothing is uploaded.
>
> Page-text checks choose the vocabulary for the language the page is actually
> written in — English, Arabic, German, Spanish, French, Italian or Portuguese.
> They read the page's text as well as its language tag, so a misleading tag
> cannot hide Arabic content; explicit adult labels are checked in every
> language, and an unidentified language falls back to the full list.
>
> The popup and settings dashboard lead with one clear protection status and
> short layer states, with technical details available only when needed. Your
> own blocked sites, allowed sites, words and corrections can be exported to a
> local file and imported in another browser; nothing is uploaded.
>
> It runs on its own or with the Hisn Mac app. With the app, a timed lock,
> the app's site lists and a browser guard become authoritative. Without the
> app, the browser-only rules work and stay editable.
>
> **Privacy:** matching stays on this device. Hisn has no account, no analytics
> and no server. The only routine request is a signed block-list update from
> GitHub; it reveals only which repository file was fetched. A list update
> never includes a site you visited.
>
> **Limits:** no filter catches every adult page. Encrypted media, direct IP
> access, another browser or another device can bypass it. An unpacked or
> store-installed extension can be disabled or removed; only managed browser
> policy makes it non-removable for a standard user. Hisn says so in its pages.

Links: [Privacy policy](https://github.com/hamzabinjehad/AdultContentBlocker/blob/main/PRIVACY.md),
[Support](https://github.com/hamzabinjehad/AdultContentBlocker/blob/main/SUPPORT.md),
[Source](https://github.com/hamzabinjehad/AdultContentBlocker).

## Permission justifications

| Permission | Why it is needed | What it does not do |
| --- | --- | --- |
| `declarativeNetRequest` | Block listed domains, force SafeSearch, and apply the opt-in short-video rules | No request bodies, no browsing log |
| `storage` | Keep settings, rules, the installed list version and local verdict caches | No sync to a server; no account |
| `alarms` | Check for a signed list generation every six hours and retry a failed install later | No background browsing collection |
| `nativeMessaging` | Ask the Hisn Mac app for its lock state and app-owned lists over a local native host | No remote endpoint; no data leaves the machine |
| `scripting` | Register the page-text scanner and the opt-in visual scanner on permitted pages | Does not run on browser settings pages or without site access |
| `offscreen` | Run the opted-in local visual model in a hidden extension document, because a service worker has no DOM or canvas | Only an extension page; created only when visual checks are enabled and closed when they are disabled; no page content leaves the device |
| `<all_urls>` host permission | Any page can contain adult text or media, and the block/SafeSearch rules must apply on any site | Reads only what a permitted page exposes to the scanner |

The `offscreen` permission is declared because the opt-in visual engine runs its
local model in an extension offscreen document; a service worker has no DOM or
canvas. It is created only when the person enables visual checks, and it is
closed when they are disabled. It is not used for page scraping or remote work.
There is no `tabs`, `history`, `cookies`, `webRequest` or `management`
permission.

## Privacy practices tab

- **Single purpose:** block adult content and enforce SafeSearch in the browser.
- **Data collection:** the extension does not collect or transmit browsing
  history, page text, images, video frames, identifiers or usage data. Local
  processing only.
- **Sale / unrelated use:** no sale, no advertising, no credit checks, no
  lending, no unrelated purpose.
- **Remote code:** none. JavaScript, the visual model and the rules ship in the
  package. The six-hourly request downloads signed **data**, never code.
- **Human review:** no human reads browsing data because none is collected.
- **Certification:** the developer certifies the above for the listing.

## Managed force-install

An ordinary store install can be disabled or removed. A managed policy is the
only supported way to stop that for a standard user. After the store assigns an
ID, use it in one of these:

```json
{
  "ExtensionSettings": {
    "*": { "installation_mode": "blocked" },
    "<STORE_ID>": {
      "installation_mode": "force_installed",
      "update_url": "https://clients2.google.com/service/update2/crx"
    }
  }
}
```

or the older `ExtensionInstallForcelist` policy. The repository already has:

- `profile/make_profile.py --extension-id <STORE_ID>` for a Mac configuration
  profile;
- `profile/make_windows_policy.py --extension-id <STORE_ID>` for the Windows
  Chrome/Edge/Brave registry policy;
- `macos/install_native_host.sh --extension-id <STORE_ID>` for the native host.

The extension cannot detect policy installation without a broad `management`
permission. Its settings page states that policy controls removal; it does not
claim to verify it.

## Reviewer test steps

No account, sign-in, adult content or special network is needed:

1. Load the unpacked extension (or install the store build) and open its
   options. Confirm the footer shows the version and the privacy/support links.
2. Add `example.com` under **Blocked sites**, save, and visit
   `https://example.com/`. The Hisn block page must appear, with an **Always
   allowed** action.
3. Search on Google, Bing or DuckDuckGo in a normal tab. The URL must carry
   `safe=active`, `adlt=strict` or `kp=1`; YouTube must carry the Restricted
   Mode header where the browser exposes it.
4. Open a page with ordinary text. The scanner must not block it. A benign
   medical or news page is the expected pass case.
5. Turn on visual checks only if testing them; they are opt-in and local. No
   camera or upload prompt should appear.
6. Remove the custom `example.com` rule and confirm the site loads.
7. In **Blocking Rules → Move your rules**, choose **Export file** and confirm
   a `hisn-rules.json` download appears. Import the same file back and confirm
   it reports nothing new; no account or network is involved.

Known limits the reviewer should see, not debug: a removed/disabled extension
enforces nothing; unreadable media is not verified safe; other browsers and
other devices are outside it; a list update needs GitHub reachability and keeps
the previous generation on failure.

## Post-publication wiring

1. Copy the real store ID from the dashboard.
2. Add it to `macos/Hisn/Info.plist` `HisnExtensionIDs` and the native-host
   installer call; the unpacked ID is not the store ID.
3. Generate the Mac profile with `--extension-id <STORE_ID>` and the Windows
   policy with the same ID.
4. Test the store build in a clean profile: native messaging, lock handover,
   list update, remove/disable, and a managed force-install. Private windows are the app's profile step; the extension no longer offers an incognito mode.
5. Record the result in [RELEASE_VALIDATION.md](RELEASE_VALIDATION.md) and the
   extension release record.

## Checklist

- [ ] Chrome Web Store developer account and fee.
- [ ] Upload `dist/hisn-extension-1.5.2.zip`; unlisted first.
- [ ] Fill listing, privacy practices and permission justifications from above.
- [ ] Add 128 px icon and at least one screenshot; the package already has icons.
- [ ] Read the assigned store ID and wire both native messaging and policy.
- [ ] Test a managed force-install on a standard user and confirm disable/remove
      are blocked.
- [ ] Publish, record the version/hash/ID, and keep the previous zip.
