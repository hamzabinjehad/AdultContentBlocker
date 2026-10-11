#!/bin/bash
#
# Report read-only evidence for configured Hisn protection layers on this Mac.
#
#     macos/verify_enforcement.sh
#
# WHY THIS EXISTS
# ---------------
# The app's own status header (ContentView.StatusHeader) is honest about the two
# layers the app can see — its filter and its browser link. It cannot see the
# rest: whether a configuration profile is installed, whether incognito is
# actually disabled, whether iCloud Private Relay is off, whether the daily user
# is still an administrator. Every one of those is silent when absent — the
# machine looks protected and is not.
#
# It checks installed browsers in the eleven-entry native-link catalog, not
# every Chromium fork. Managed preferences are evidence of configuration, not
# proof that the browser accepted those policies. Verify its effective policy
# page and behavior before calling a deployment enforced.
#
# Incognito is the sharp example. The extension is `spanning`, so it CAN run in a
# private window — but the browser keeps it off there until the user turns on
# "Allow in Incognito", which a determined person will not. The only thing that
# closes that hole without relying on the opt-in is the profile disabling
# incognito outright (IncognitoModeAvailability = 1). Without it, an incognito
# window is a bypass of the whole page-text layer, and nothing anywhere says so.
# This script says so.
#
# The extension learns app commitment state over native messaging. Missing
# hosts remove that additional app link, not all browser-only protection. File
# presence alone does not prove a working bridge or protected private windows.
#
# Read-only. It changes nothing, needs no sudo, and is safe to run any time —
# it is a mirror, not a switch. Exit status 0 means this script found no missing
# critical evidence; it is not certification of policy acceptance or coverage.
set -uo pipefail

RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; DIM=$'\033[2m'; OFF=$'\033[0m'
[ -t 1 ] || { RED=; GRN=; YEL=; DIM=; OFF=; }

ME="${SUDO_USER:-$(id -un)}"
critical_open=0
warnings=0

# The configured native-link catalog, as
#   display name | managed-preferences domain | product dir
# The domain is where a configuration profile lands the browser's policy; the
# product dir (under ~/Library/Application Support) is both the browser's
# presence marker and where its native-messaging host manifest lives. This is
# the same subset as NativeMessagingInstaller.browsers and
# NATIVE_LINKED_BROWSER_IDS, not the wider CHROMIUM_FAMILY policy candidates.
# Detection below retains its existing Spotlight/product-directory behavior.
CHROMIUM_BROWSERS="\
Chrome|com.google.Chrome|Google/Chrome
Chrome Beta|com.google.Chrome.beta|Google/Chrome Beta
Chrome Dev|com.google.Chrome.dev|Google/Chrome Dev
Chrome Canary|com.google.Chrome.canary|Google/Chrome Canary
Edge|com.microsoft.Edge|Microsoft Edge
Brave|com.brave.Browser|BraveSoftware/Brave-Browser
Vivaldi|com.vivaldi.Vivaldi|Vivaldi
Opera|com.operasoftware.Opera|com.operasoftware.Opera
Arc|company.thebrowser.Browser|Arc/User Data
Chromium|org.chromium.Chromium|Chromium
Helium|net.imput.helium|net.imput.helium"

# System-scope native-messaging host dirs are branded exceptions for Chrome and
# Edge; every other fork resolves to /Library/Application Support/<product>/…
# (matches NativeMessagingInstaller.Browser.fork and install_native_host.sh).
nm_system_dir() {
    case "$1" in
        Chrome|"Chrome Beta"|"Chrome Dev"|"Chrome Canary") echo "/Library/Google/Chrome/NativeMessagingHosts" ;;
        Edge)   echo "/Library/Microsoft/Edge/NativeMessagingHosts" ;;
        *)      echo "/Library/Application Support/$2/NativeMessagingHosts" ;;
    esac
}

browser_installed() {   # product-dir policy-domain -> is the browser installed?
    # By its app bundle, via Spotlight. A data folder alone is not enough: an
    # uninstalled browser leaves one behind, and reporting it OPEN sends the
    # person chasing a browser they no longer have. Edge's app id differs from
    # its policy domain. Only when Spotlight indexing is off does the data
    # folder decide.
    local id="$2"
    [ "$id" = "com.microsoft.Edge" ] && id="com.microsoft.edgemac"
    [ -n "$(mdfind "kMDItemCFBundleIdentifier == '$id'" 2>/dev/null | head -1)" ] && return 0
    mdutil -s / 2>/dev/null | grep -q "Indexing enabled" && return 1
    [ -d "$HOME/Library/Application Support/$1" ]
}

ok()   { printf "  ${GRN}● observed${OFF}  %-22s ${DIM}%s${OFF}\n" "$1" "$2"; }
open() { printf "  ${RED}○ OPEN${OFF}      %-22s ${DIM}%s${OFF}\n" "$1" "$2"; critical_open=1; }
warn() { printf "  ${YEL}◐ partial${OFF}   %-22s ${DIM}%s${OFF}\n" "$1" "$2"; warnings=1; }

managed_pref() {   # domain key  -> value from device- or user-scope managed prefs
    local domain="$1" key="$2" v=""
    for base in "/Library/Managed Preferences" "/Library/Managed Preferences/$ME"; do
        v=$(defaults read "$base/$domain" "$key" 2>/dev/null) && { echo "$v"; return 0; }
    done
    return 1
}

echo "Protection evidence on this Mac — $(date '+%Y-%m-%d %H:%M')"
echo "================================================"
echo "Daily user: $ME"
echo "Scope: configured native-link browsers; preference presence is not effective-policy proof."
echo

# ---- the load-bearing fact: is the user an administrator? -------------------
if dseditgroup -o checkmember -m "$ME" admin >/dev/null 2>&1; then
    open "account split" "'$ME' is an admin — can undo everything below"
else
    ok "account split" "'$ME' is a standard user"
fi

# ---- the app's own files ------------------------------------------------------
# The admin-owned browser link runs the bridge inside the app bundle: a bundle
# the daily user owns is a program they can swap for one that says "no lock".
bridge="/Applications/Hisn.app/Contents/MacOS/HisnBridge"
if [ -e "$bridge" ]; then
    owner=$(stat -f %Su "$bridge"); bundle_owner=$(stat -f %Su /Applications/Hisn.app)
    if [ "$owner" = "root" ] && [ "$bundle_owner" = "root" ] && [ ! -w "$bridge" ]; then
        ok "app files" "owned by root — the browser link's program cannot be swapped"
    else
        open "app files" "owned by $owner — a standard user could replace the browser link's program; run install.sh"
    fi
else
    warn "app files" "Hisn is not in /Applications"
fi
agent="/Library/LaunchAgents/app.hisn.agent.plist"
if [ -f "$agent" ] && [ "$(stat -f %Su "$agent")" = "root" ]; then
    ok "login agent" "in /Library, owned by root — brings Hisn back at every login"
elif [ -f "$HOME/Library/LaunchAgents/app.hisn.agent.plist" ]; then
    open "login agent" "in your own Library — a standard user can delete it; run install.sh"
else
    warn "login agent" "none — Hisn does not start at login; run install.sh"
fi

# ---- domain layer: hosts / DNS ---------------------------------------------
hosts_count=$(grep -c '^0\.0\.0\.0' /etc/hosts 2>/dev/null || echo 0)
if [ "$hosts_count" -ge 1000 ]; then
    ok "domain blocklist" "$hosts_count entries in /etc/hosts"
else
    open "domain blocklist" "only $hosts_count hosts entries — run block_dns.sh"
fi

# ---- SafeSearch for every browser and app (block_dns.sh) --------------------
# Each engine's first name in safesearch_hosts.txt must point at the address
# its SafeSearch host resolves to now. Missing: SafeSearch is forced only where
# the extension or a browser policy runs. Stale: the engine moved and the name
# now leads nowhere — it stops loading until install.sh --hosts runs again.
ss_missing=""; ss_stale=""
if [ -f "$(dirname "$0")/safesearch_hosts.txt" ]; then
    ss_target=""
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            ''|'#'*) ;;
            @*) ss_target="${line#@}" ;;
            *)  [ -n "$ss_target" ] || continue
                case "$ss_target" in
                    *google.com) engine=Google ;; *youtube.com) engine=YouTube ;;
                    *bing.com) engine=Bing ;; *duckduckgo.com) engine=DuckDuckGo ;;
                    *brave.com) engine="Brave Search" ;;
                    *) engine="$ss_target" ;;
                esac
                have=$(awk -v n="$line" '$1 !~ /^#/ && $2 == n { print $1; exit }' /etc/hosts)
                want=$(dig +short +time=3 +tries=1 A "$ss_target" 2>/dev/null | grep -E '^[0-9.]+$' || true)
                if [ -z "$have" ]; then ss_missing="$ss_missing $engine"
                elif [ -n "$want" ] && ! printf '%s\n' "$want" | grep -qxF "$have"; then ss_stale="$ss_stale $engine"
                fi
                ss_target="" ;;   # one name per engine tells the story
        esac
    done < "$(dirname "$0")/safesearch_hosts.txt"
    if [ -n "$ss_stale" ]; then
        warn "SafeSearch (DNS)" "address changed for:$ss_stale — it stops loading; run install.sh --hosts"
    elif [ -n "$ss_missing" ]; then
        warn "SafeSearch (DNS)" "not forced for:$ss_missing outside the extension — run install.sh --hosts"
    else
        ok "SafeSearch (DNS)" "forced for Google, YouTube, Bing, DuckDuckGo and Brave Search in every browser and app"
    fi
fi

# ---- DNS routes around the hosts file (block_dns.sh, bypass_hosts.txt) -----
# A sample of the names: Private Relay's, Firefox's default DoH server, and
# Google's. The profile closes the same routes by policy; this is the layer
# that works without it.
bp_open=""
for n in mask.icloud.com mozilla.cloudflare-dns.com dns.google; do
    [ "$(awk -v n="$n" '$1 !~ /^#/ && $2 == n { print $1; exit }' /etc/hosts)" = "0.0.0.0" ] \
        || bp_open="$bp_open $n"
done
if [ -z "$bp_open" ]; then
    ok "DNS bypasses" "Private Relay and public DoH servers blocked in /etc/hosts"
else
    warn "DNS bypasses" "not blocked:$bp_open — encrypted DNS can go around the hosts file; run install.sh --hosts"
fi

# ---- the big sites' subdomains (block_dns.sh, popular_domains.txt) ---------
top=$(grep -v '^#' "$(dirname "$0")/popular_domains.txt" 2>/dev/null | grep -v '^$' | head -1)
if [ -n "$top" ]; then
    if [ "$(awk -v n="de.$top" '$1 !~ /^#/ && $2 == n { print $1; exit }' /etc/hosts)" = "0.0.0.0" ]; then
        ok "popular subdomains" "country and mobile subdomains of the most-visited sites blocked"
    else
        warn "popular subdomains" "de.$top and the like resolve outside the extension — run install.sh --hosts"
    fi
fi

# ---- profile-dependent browser controls, per installed browser -------------
# One pass over the family: for each browser that is actually installed, is
# incognito disabled, is DoH locked off, and does a native-messaging host exist?
# Aggregated so the output stays short but still names the browser that is open.
# Prefer the installed app's exact admitted IDs; use the source plist only when
# no installed plist exists. Grepping Swift comments silently misses store IDs.
# An invalid/empty installed list must remain unknown, not claim repository IDs.
EXT_IDS="$(python3 - "/Applications/Hisn.app/Contents/Info.plist" "$(dirname "$0")/Hisn/Info.plist" <<'IDS' 2>/dev/null || true
import pathlib, plistlib, re, sys
for candidate in sys.argv[1:]:
    path = pathlib.Path(candidate)
    if not path.exists():
        continue
    try:
        data = plistlib.loads(path.read_bytes())
        values = data.get("HisnExtensionIDs", [])
        if not isinstance(values, list):
            values = []
        ids = {value for value in values if isinstance(value, str) and re.fullmatch(r"[a-p]{32}", value)}
        print(" ".join(sorted(ids)))
    except Exception:
        pass
    break
IDS
)"
incog_open=""; doh_open=""; guest_open=""; safe_open=""; link_missing=""; ext_off=""; browsers_present=0
while IFS='|' read -r name domain product; do
    [ -n "$name" ] || continue
    browser_installed "$product" "$domain" || continue
    browsers_present=1

    [ "$(managed_pref "$domain" IncognitoModeAvailability || echo "")" = "1" ] \
        || incog_open="$incog_open $name"
    [ "$(managed_pref "$domain" DnsOverHttpsMode || echo "")" = "off" ] \
        || doh_open="$doh_open $name"
    # A guest window or a fresh profile runs without the extension.
    { [ "$(managed_pref "$domain" BrowserGuestModeEnabled || echo "")" = "0" ] \
      && [ "$(managed_pref "$domain" BrowserAddPersonEnabled || echo "")" = "0" ]; } \
        || guest_open="$guest_open $name"
    [ "$(managed_pref "$domain" ForceGoogleSafeSearch || echo "")" = "1" ] \
        || safe_open="$safe_open $name"

    # Read enabled-state evidence from discovered Default/Profile * directories.
    # This is not live execution, policy-install provenance, custom-data-dir or
    # guest/private-window coverage. No discovered profile must remain unknown,
    # rather than passing the all-profiles check vacuously.
    ext_state=$(python3 - "$HOME/Library/Application Support/$product" "$EXT_IDS" <<'PY' 2>/dev/null || echo "unknown"
import json, sys, pathlib
root, ids = pathlib.Path(sys.argv[1]), set(sys.argv[2].split())
if not ids:
    print("unknown")
    sys.exit(0)
off, checked = [], 0
for profile in sorted(list(root.glob("Default")) + list(root.glob("Profile *"))):
    if not (profile / "Preferences").exists():
        continue
    checked += 1
    settings, name = {}, profile.name
    for f in ("Preferences", "Secure Preferences"):
        try: d = json.loads((profile / f).read_text())
        except Exception: continue
        settings.update(d.get("extensions", {}).get("settings", {}))
        name = d.get("profile", {}).get("name") or name
    on = any(not settings[i].get("disable_reasons") and settings[i].get("state", 1) != 0
             for i in ids & settings.keys())
    if not on:
        off.append(name.replace(" ", "_"))
print("unknown" if not checked else ("on" if not off else "off:" + ",".join(off)))
PY
)
    [ "$ext_state" = "on" ] || ext_off="$ext_off $name($ext_state)"

    sysman="$(nm_system_dir "$name" "$product")/app.hisn.bridge.json"
    userman="$HOME/Library/Application Support/$product/NativeMessagingHosts/app.hisn.bridge.json"
    # With the profile's NativeMessagingUserLevelHosts off, the browser ignores
    # the user-level copy, so only the admin-owned one counts.
    if [ "$(managed_pref "$domain" NativeMessagingUserLevelHosts || echo "")" = "0" ]; then
        [ -f "$sysman" ] || link_missing="$link_missing $name(needs the admin-owned link)"
    else
        { [ -f "$sysman" ] || [ -f "$userman" ]; } || link_missing="$link_missing $name"
    fi
done <<EOF
$CHROMIUM_BROWSERS
EOF

if [ "$browsers_present" -eq 0 ]; then
    warn "chromium browsers" "no configured native-link browser detected — other browsers/apps are not inventoried"
else
    if [ -z "$incog_open" ]; then
        ok "browser incognito" "disable preference present for each detected catalog browser; verify actual policy"
    else
        open "browser incognito" "disable preference missing on:$incog_open — verify private-window protection"
    fi

    if [ -z "$doh_open" ]; then
        ok "browser DoH" "off preference present for each detected catalog browser; verify actual policy"
    else
        open "browser DoH" "not locked on:$doh_open — a DoH toggle bypasses the hosts file"
    fi

    if [ -z "$guest_open" ]; then
        ok "guest / new profiles" "disable preferences present for each detected catalog browser"
    else
        open "guest / new profiles" "disable preferences missing on:$guest_open — verify actual guest/profile behavior"
    fi

    if [ -z "$safe_open" ]; then
        ok "SafeSearch policy" "requested preference present for each detected catalog browser"
    else
        warn "SafeSearch policy" "not forced on:$safe_open — the extension still rewrites searches"
    fi

    if [ -z "$ext_off" ]; then
        ok "Hisn extension" "enabled-state evidence in each discovered standard profile; not a live/private-window check"
    else
        open "Hisn extension" "enabled-state evidence missing/unknown in:$ext_off"
    fi

    # A missing link prevents app synchronization. Sustained native loss can
    # fail closed after an app lock was recorded; standalone layers remain.
    # Manifest presence alone still needs a live native-message acceptance test.
    if [ -z "$link_missing" ]; then
        ok "extension link" "host file present for each detected catalog browser; verify the live app connection"
    else
        warn "extension link" "no host on:$link_missing — extension there cannot reach the app (run install_native_host.sh or launch the app)"
    fi
fi

# Screen Time's own adult-website filter: Apple's lock, which this tool is
# meant to sit under (docs/POSITIONING.md). Informational — the rows above
# are what Hisn itself adds.
if [ "$(managed_pref com.apple.familycontrols.contentfilter restrictWeb || echo "")" = "1" ]; then
    ok "Screen Time filter" "Limit Adult Websites is on"
else
    warn "Screen Time filter" "Limit Adult Websites is off — turn it on under Screen Time › Content & Privacy"
fi

relay=$(managed_pref com.apple.applicationaccess allowCloudPrivateRelay || echo "")
if [ "$relay" = "0" ]; then
    ok "iCloud Private Relay" "disable preference present; verify effective restriction"
else
    open "iCloud Private Relay" "not disabled — Safari can tunnel DNS past /etc/hosts"
fi

# ---- system-level content filter activation evidence -----------------------
# systemextensionsctl may be gated in some contexts; treat inability to read as
# unknown rather than as enforced.
if command -v systemextensionsctl >/dev/null 2>&1; then
    sysext=$(systemextensionsctl list 2>/dev/null | grep -i "hisn" || true)
    if echo "$sysext" | grep -qi "activated enabled"; then
        ok "system filter" "Hisn system extension reported activated/enabled; filtering and tunnel coverage need live tests"
    else
        warn "system filter" "not reported active — check authorization, signing and Network Extension entitlement"
    fi
else
    warn "system filter" "could not query system extensions"
fi

echo
if [ "$critical_open" -eq 0 ] && [ "$warnings" -eq 0 ]; then
    echo "${GRN}No critical evidence is missing from this script's scope.${OFF}"
    echo "Confirm effective browser policy and real filtering; this is not universal coverage certification."
    exit 0
elif [ "$critical_open" -eq 0 ]; then
    echo "${YEL}No critical evidence is missing; see partial/unknown rows above.${OFF}"
    echo "Confirm effective browser policy and real filtering separately."
    exit 0
else
    echo "${RED}One or more critical checks lack evidence.${OFF}"
    echo "See docs/CHROME_ENFORCEMENT.md for verification and remaining limits."
    exit 1
fi
