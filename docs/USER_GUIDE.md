# Using Hisn

These instructions describe the intended signed beta. A downloadable public
installer and store extension are not available yet. Developers should use
SETUP.md while the release checks in RELEASE_RUNBOOK.md are completed.

## Install

1. Sign in to the Mac account that will use Hisn.
2. Open the signed Hisn installer package. If you use a standard account, ask
   the administrator to enter their password when macOS requests it.
3. Open Hisn from Applications. It opens on Protected Setup the first time.
4. Select **Enable system filter**. Approve the extension and filter when
   macOS asks. If Hisn says a restart is required, restart the Mac, open Hisn,
   and enable the filter again.
5. Open **How to connect a browser**. Follow the store link for your browser,
   install Hisn, and visit a web page. Check Overview for a connected browser.

Safari and Firefox do not have a Hisn browser extension. The system filter
covers their domain connections when it is running; it does not scan their
page text.

## Try It

Open Lock. The initial choice is five minutes. Leave Strict mode off for the
first trial, read the end time, and confirm only when you are ready. A lock
cannot be cancelled immediately by you alone after it starts.

In Overview, check both the lock and protection status. A running countdown
does not mean that every protection layer is working. Follow any reported
setup problems before choosing a longer lock.

Closing the window or using Quit leaves Hisn running in the background, even
when no lock is active. Open its window again from the menu bar or Applications.
The installed app starts automatically when the protected account logs in and
comes back after a crash or force-quit. Logout, restart and shutdown work
normally; it starts again at the next login. Sleep pauses its work until the
Mac wakes. Other Mac accounts do not run this account's browser guard.

## Make Changes

Blocking Rules contains your extra blocked sites and the sites you allow in
Strict mode. Add work, school, and other essential sites before a strict lock.
During a lock you can strengthen rules, but cannot loosen them immediately.

Lock also contains the daily schedule. It uses the Mac's local time. A change
that weakens a saved schedule waits 24 hours; the app shows when it applies.
When the clocks change, a skipped start time uses the next available time; a
time that occurs twice uses its first occurrence.

Settings contains the language choice and accountability partner's public key.
Your partner keeps their private key. You should never ask them to share it.

## Browser Extension

The extension follows the app's navigation: Overview, Blocking Rules, Lock,
and Settings. Overview shows browser protection separately from the lock,
including whether checking is enabled in private windows. The extension cannot
check whether an administrator has disabled private browsing, so an unchecked
private-window permission is not treated as verified coverage.

In browser-only use, edit site lists in Blocking Rules and page checking in
Settings. Each section has its own Save button and unsaved-change indicator;
moving between sections does not discard edits. Once connected to the Mac app,
those settings become read-only here and are edited in the app. Corrections
remain under Blocking Rules. Timed locks are started in the Mac app.

Search protection covers matching page loads and background search requests.
Page checking also rechecks image labels and descriptions when they change,
and open pages when scoring settings or custom words change. Very long pages
are sampled and checking is heuristic: this is not an image classifier or a
guarantee that every unsuitable image will be detected.

## Protected Setup

Start with **Start with your network**. Choose what the router allows rather
than its brand: inspect unknown settings, configure filtering DNS manually,
use a filtering DNS server, or continue when the network cannot be changed.
Hisn does not automatically configure routers or verify gateway enforcement yet.
The network guide stays **Network protection not verified** and does not count
toward the Mac's progress. Ask the network administrator to verify each device,
IPv4/IPv6 DNS use, alternate DNS, outages, and recovery. Phones need independent
protection on cellular and other Wi-Fi networks.

If you chose the manual Cloudflare DNS option, select **Check Cloudflare DNS on
this Mac** after reconnecting. Hisn samples a harmless category-test name and
an ordinary name through the Mac's system DNS. It reports a blocking response,
a non-null address, or an inconclusive result for each address-record type. It opens no
website and changes no settings. Missing replies and local hosts overrides do
not count as blocking. A recorded sample can come from cache; it does not
confirm router enforcement, browser DNS, IPv6 network routing, VPN restrictions,
or other devices. The network guide remains unverified. This provider test is
separate from testing imported Hisn rules on a custom filtering DNS server.

Work through the four stages: Install and connect, Protect browsing settings,
Keep a recovery route, then Separate administrator access. Hisn rechecks while
the page is open and when you return from another app. You can also select
Check again; the page shows when the checks last finished.

The checks require the system filter, protected application files, the browser
extension in every readable profile, administrator-enforced browser protection,
and a protected browser-to-app connection. A browser that has not been opened
or whose profiles cannot be checked is not counted as verified.

Recovery and who holds the administrator password are your confirmations, not
facts Hisn can verify. Keep recovery instructions accessible without this Mac.
Complete the account change last and never remove the last administrator.
Passing the checks means this setup was checked, not that protection is
impossible to remove or that another device is protected.

The browser extension forces stricter search settings for Google, Bing,
DuckDuckGo, Yahoo, and Brave Search, and restricted mode for YouTube. Adding
one of these services to your allowed sites does not turn that protection off.
Brave image, video, and news searches are included.

Yandex's configured search hosts and a maintained set of anonymous X/Twitter
viewer services are blocked by the browser extension and the active system
filter. Those built-in blocks cannot be overridden by an allowed-site entry.
The initial viewer set is Nitter, XCancel, Sotwe, and TwStalker; other instances
and mirrors require their own entries.

The optional administrator hosts setup adds Brave's forced-safe DNS address
and the search/viewer host blocks. Existing installations need that setup
refreshed to receive the new DNS entries. This is separate from enabling the
system filter: the filter blocks domains but cannot rewrite encrypted search
queries. DNS-based search protection depends on the configured DNS path.

No filter guarantees that every search result is suitable, and unsupported
search engines are not automatically made safe. Use Strict mode with only
the essential services allowed for a more limited browsing setup.

Open **Protect browsing settings** in Protected Setup with someone you trust. It checks the
Mac's browser policies, Screen Time, app installation, and account permissions.
Complete account changes last, after the app and browser work correctly.
Administrator setup may still require the maintainer's help during beta.

A standard account with a separate administrator strengthens the setup.
Do not remove the last administrator account or give up access to a recovery
process you understand. Protection on this Mac does not protect another device.

## When Something Goes Wrong

- **Waiting for approval:** open System Settings and approve Hisn. The location
  varies with macOS; recent versions list extensions under General, Login Items
  & Extensions. A filter permission prompt may also appear.
- **Restart required:** restart before trying to enable the filter again.
- **Browser disconnected:** restart the browser and confirm Hisn is enabled in
  every profile you use. Follow Overview's reconnect action.
- **List update failed:** retry from Overview. The last verified list stays
  installed while an update fails.
- **A needed page is blocked:** use the page's report option for a text-check
  mistake. During a lock, use the partner approval or delayed early-release
  action on the Lock page when you need to end the lock.

For beta support, record the app version, macOS version, browser name, and the
visible error. Do not send browser history, private keys, or profile removal
passwords. A public support contact must be provided before the beta is shared.
