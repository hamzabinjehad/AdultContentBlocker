# Hisn privacy policy

**Repository copy for the 1.0 release.** Publish this page, with the support
page, before a signed build ships. It describes the code in this repository;
store listings and platform privacy labels must say the same thing.

Applies to the browser extension, the Mac app and content filter, the
iPhone/iPad app and the Android app. Contact: the repository's support route in
[SUPPORT.md](SUPPORT.md).

## The short version

- Matching happens **on your device**. Hisn does not send your browsing history,
  the pages you visit, matched text, images, video frames or screenshots to us.
- There is **no Hisn account, no analytics SDK, no advertising and no telemetry**.
- The only routine network requests are a signed block-list update and, for the
  time lock, a check of Apple's server time. Both are described below.
- An accountability partner learns that a release was **requested**, and when.
  They never learn which sites you visited.

## What stays on your device

Hisn keeps its settings, rules, selected apps, commitment deadline, installed
list version, local health state and the pause-screen count in the app's own
storage. On a Mac with the guardian edition, a root-owned local log records the
protected account name, a guardian action and a timestamp (for example,
"agent-reloaded"); it never records a site, a page or screen content. On iPhone/iPad the sealed Screen Time passcode is kept in the
device-only Keychain (`ThisDeviceOnly`), outside iCloud and outside backups.
Nothing in that storage is uploaded by Hisn. A rules file you export from the
browser extension is written to your Downloads folder only when you ask for it;
it holds your blocked sites, allowed sites, words and corrections, and Hisn
does not upload or sync it.

The following are processed locally and are not saved or sent:

- visible text read by the Android accessibility service or the Safari text
  extension, in the apps and pages you choose;
- transient screenshots and video frames sampled by the on-device media checks
  (on by default in the Chromium extension, and switchable off);
- image and video classification results;
- host and flow decisions made by the Mac content filter;
- DNS answers handled by the Android DNS service;
- Android's foreground-time totals for apps with a daily limit, read only to
  compare the current local day with the saved limit.

The visual models ship with the app and run locally. History is not retained by
the visual engine.

## What leaves your device

These are the only routine outbound requests in the product. None contains a
site you visited, a page you read or a classification result.

| Request | Who can see it | Contains | When |
| --- | --- | --- | --- |
| Signed list update (`raw.githubusercontent.com`) | GitHub and your network | The repository file requested, your IP address, the time | Mac app and browser extension, automatic or **Update** in the block-list row |
| Apple server time (`www.apple.com`) | Apple and your network | An ordinary HTTPS request; Hisn reads only the `Date` response header | iPhone/iPad time lock and vault reveal |
| DNS queries, if you enable Hisn DNS | The resolver you configured (Hisn's default is Cloudflare Families) and your network | The names your device resolves | While the DNS layer is on |
| Accountability release notice | Your chosen partner, through the mechanism you set up | That a release was requested, and when | Only when you request one |

- **No install ID.** The list updater does not send an account, a device ID or a
  unique install identifier. It reveals only which repository file was fetched,
  as any web request does.
- **No server of ours.** This repository has no backend that receives browsing
  data. The partner page verifies a signed approval locally; it does not report
  sites.
- **Your network and platform still see traffic.** Your ISP, DNS resolver,
  employer/school network and the browser or operating system may collect data
  under their own policies. Hisn cannot change that.
- **Stores and crash reporting.** If you install from a store, the store may
  collect install or crash information under its policy. Hisn does not add an
  analytics or crash-reporting SDK.

## Permissions and why they are asked for

Every permission is optional except the ones the app needs to run at all. The
in-app disclosure for each layer says what it can and cannot see.

- **Accessibility (Android):** read the visible text of the apps you select and
  recognise Hisn's own uninstall/disable screens in the direct build. It is never
  used to read passwords; input and password fields are skipped where Android
  identifies them.
- **Usage access (Android, optional):** read Android's on-device foreground-time
  totals for apps you give a daily time limit. Hisn compares the current local
  day's total with your saved limit; it does not retain a usage history or send
  the totals anywhere.
- **Screen capture / media projection (Android):** sample the selected app's
  window only when you turn on image or video checks. Samples are transient.
- **VPN (Android):** run the DNS-only local resolver. It does not forward your
  traffic to Hisn.
- **Overlay and notifications (Android):** show the direct build's boot shield
  and the DNS/health notification.
- **Device admin / device owner (Android):** add the optional uninstall step and,
  on a provisioned device, the opt-in uninstall block and managed lockdown.
- **Screen Time / Family Controls (iPhone/iPad):** apply the web filter, app
  shields and the passcode vault. Apple controls the authorization; Hisn reads
  only the configuration Apple exposes.
- **Safari extension (iPhone/iPad and Mac):** block listed pages and, if you turn
  it on, check page text or local media.
- **Content filter / Network Extension (Mac):** make host and flow decisions for
  apps on this Mac.
- **Full Disk Access or browser-profile access (Mac, optional):** inspect the
  browser guard's own configuration. Hisn does not read page content through it.

## Children and families

Hisn is intended for a person who chooses it for themselves, or for a minor's
device managed by a parent or guardian using the platform's own parental
controls. It is not a covert monitoring product. It does not read messages,
track location, record the screen continuously or report sites to anyone. A
guardian using Family Sharing or supervision has the platform's authority, and
the app discloses every lock and its exit.

## Your choices

You can turn off a layer, revoke a permission, clear the app's data or uninstall
Hisn. A running commitment can delay a change inside Hisn until its date; a
device-owner managed setup can refuse uninstall until it is released. The app
shows the level and the exit. Erasing the device removes Hisn and its local
data; a backup may carry settings but not the iPhone/iPad passcode vault.

Some data is held by platforms rather than Hisn (for example, Screen Time
authorization or store install records). Use the platform's privacy controls for
those.

## Security and changes

Hisn verifies the signed list and refuses a rollback; the iPhone/iPad passcode
vault is stored device-only; the app avoids logging browsing data. No system is
unbreakable. If you find a security problem, report it privately as described in
[SUPPORT.md](SUPPORT.md).

If this policy changes, the change is recorded in [CHANGELOG.md](CHANGELOG.md)
and the effective date below is updated. A change that adds collection would
need a new consent, not a silent edit.

**Effective:** 2026-11-02 · **Applies to:** extension 1.5.0, Mac 1.1.7, iOS
1.2.0, Android 0.9.0 and later builds that link to this policy.
