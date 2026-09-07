#!/bin/bash
#
# Report which protection layers are ACTUALLY enforced on this Mac, right now.
#
#     macos/verify_enforcement.sh
#
# WHY THIS EXISTS
# ---------------
# The app's own status header (ContentView.StatusHeader) is honest about the two
# layers the app can see — its filter and its browser link. It cannot see the
# rest: whether a configuration profile is installed, whether Chrome incognito is
# actually disabled, whether iCloud Private Relay is off, whether the daily user
# is still an administrator. Every one of those is silent when absent — the
# machine looks protected and is not.
#
# Incognito is the sharp example. The extension is `not_allowed` in incognito, so
# it does not run there at all; the ONLY thing that closes that hole is the
# profile disabling incognito outright (IncognitoModeAvailability = 1). Without
# the profile, an incognito window is a clean bypass of the whole page-text
# layer, and nothing anywhere says so. This script says so.
#
# Read-only. It changes nothing, needs no sudo, and is safe to run any time —
# it is a mirror, not a switch. Exit status is 0 only if every critical control
# is enforced, so the second person can run it to confirm a setup actually took.
set -uo pipefail

RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; DIM=$'\033[2m'; OFF=$'\033[0m'
[ -t 1 ] || { RED=; GRN=; YEL=; DIM=; OFF=; }

ME="${SUDO_USER:-$(id -un)}"
critical_open=0
warnings=0

ok()   { printf "  ${GRN}● enforced${OFF}  %-22s ${DIM}%s${OFF}\n" "$1" "$2"; }
open() { printf "  ${RED}○ OPEN${OFF}      %-22s ${DIM}%s${OFF}\n" "$1" "$2"; critical_open=1; }
warn() { printf "  ${YEL}◐ partial${OFF}   %-22s ${DIM}%s${OFF}\n" "$1" "$2"; warnings=1; }

# Read one key from Chrome's effective managed policy. A configuration profile
# lands it in /Library/Managed Preferences, device- or user-scoped; check both.
chrome_policy() {
    local key="$1" v=""
    for domain in \
        "/Library/Managed Preferences/com.google.Chrome" \
        "/Library/Managed Preferences/$ME/com.google.Chrome"; do
        v=$(defaults read "$domain" "$key" 2>/dev/null) && { echo "$v"; return 0; }
    done
    return 1
}

managed_pref() {   # domain key  -> value from device- or user-scope managed prefs
    local domain="$1" key="$2" v=""
    for base in "/Library/Managed Preferences" "/Library/Managed Preferences/$ME"; do
        v=$(defaults read "$base/$domain" "$key" 2>/dev/null) && { echo "$v"; return 0; }
    done
    return 1
}

echo "Enforcement on this Mac — $(date '+%Y-%m-%d %H:%M')"
echo "================================================"
echo "Daily user: $ME"
echo

# ---- the load-bearing fact: is the user an administrator? -------------------
if dseditgroup -o checkmember -m "$ME" admin >/dev/null 2>&1; then
    open "account split" "'$ME' is an admin — can undo everything below"
else
    ok "account split" "'$ME' is a standard user"
fi

# ---- domain layer: hosts / DNS ---------------------------------------------
hosts_count=$(grep -c '^0\.0\.0\.0' /etc/hosts 2>/dev/null || echo 0)
if [ "$hosts_count" -ge 1000 ]; then
    ok "domain blocklist" "$hosts_count entries in /etc/hosts"
else
    open "domain blocklist" "only $hosts_count hosts entries — run block_dns.sh"
fi

# ---- the profile-dependent controls, otherwise silent ----------------------
incognito=$(chrome_policy IncognitoModeAvailability || echo "")
if [ "$incognito" = "1" ]; then
    ok "chrome incognito" "disabled by policy — no bypass window"
else
    open "chrome incognito" "AVAILABLE — the extension does not run there; the profile is not enforcing this"
fi

doh=$(chrome_policy DnsOverHttpsMode || echo "")
if [ "$doh" = "off" ]; then
    ok "chrome DoH" "off — resolution uses the system resolver"
else
    open "chrome DoH" "not locked — a browser DoH toggle bypasses the hosts file"
fi

relay=$(managed_pref com.apple.applicationaccess allowCloudPrivateRelay || echo "")
if [ "$relay" = "0" ]; then
    ok "iCloud Private Relay" "disabled — Safari DNS cannot tunnel past the filter"
else
    open "iCloud Private Relay" "not disabled — Safari can tunnel DNS past /etc/hosts"
fi

# ---- system-level content filter (browser-agnostic, VPN-proof) -------------
# systemextensionsctl may be gated in some contexts; treat inability to read as
# unknown rather than as enforced.
if command -v systemextensionsctl >/dev/null 2>&1; then
    sysext=$(systemextensionsctl list 2>/dev/null | grep -i "hisn" || true)
    if echo "$sysext" | grep -qi "activated enabled"; then
        ok "system filter" "active — every app and browser, VPN-proof"
    else
        warn "system filter" "not active — needs Apple's \$99 entitlement to run"
    fi
else
    warn "system filter" "could not query system extensions"
fi

echo
if [ "$critical_open" -eq 0 ] && [ "$warnings" -eq 0 ]; then
    echo "${GRN}All critical controls enforced.${OFF}"
    exit 0
elif [ "$critical_open" -eq 0 ]; then
    echo "${YEL}Critical controls enforced; see partials above.${OFF}"
    exit 0
else
    echo "${RED}One or more critical controls are OPEN.${OFF} A lock is only as"
    echo "real as its weakest open row above. See docs/CHROME_ENFORCEMENT.md."
    exit 1
fi
