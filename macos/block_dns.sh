#!/bin/bash
#
# Apply the Hisn blocklist to this Mac at the DNS layer.
#
#     sudo macos/block_dns.sh --dnsmasq     # wildcard, handles the whole list
#     sudo macos/block_dns.sh --hosts       # no dependencies, under-blocks
#     sudo macos/block_dns.sh --undo        # remove whichever was applied
#     macos/block_dns.sh --hosts --dry-run  # write to /tmp, touch nothing
#
# WHICH ONE, AND WHY IT IS NOT A TOSS-UP
# --------------------------------------
# The published list is COLLAPSED: build.py drops cdn.example.com whenever
# example.com is present, because every consumer of the list blocks a domain
# *and all of its subdomains* and keeping the children is pure bloat.
#
# dnsmasq honours that contract exactly — `address=/example.com/0.0.0.0` is a
# wildcard covering every subdomain, which is what the list assumes.
#
# /etc/hosts cannot express a wildcard at all. Each line is one exact name, so
# a collapsed list loses every subdomain that was collapsed away: example.com
# would be blocked while www.example.com resolves fine. This script therefore
# emits a `www.` line alongside each domain in hosts mode, which recovers the
# common case and roughly doubles the file — but `cdn.`, `m.`, `videos.` and
# everything else stay open. Under-blocking is the price of having no
# dependency; it is a real price, not a footnote.
#
# WHAT NEITHER OF THESE IS
# ------------------------
# This is a filter, not a lock. Anything with admin rights on this Mac can undo
# it in one command — that is what --undo is. It also does nothing about a VPN,
# a browser using DNS-over-HTTPS, or another device. See docs/THREAT_MODEL.md:
# DNS filtering is the layer that a single toggle defeats, which is why the
# product's load-bearing control is the socket-level content filter and not
# this.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
LIST="$REPO/seed/domains_core.txt"
MODE=""
UNDO=0
DRY=0

BEGIN_MARK="# >>> hisn blocklist begin >>>"
END_MARK="# <<< hisn blocklist end <<<"
DNSMASQ_CONF="/opt/homebrew/etc/dnsmasq.d/hisn-blocklist.conf"
[ -d /opt/homebrew ] || DNSMASQ_CONF="/usr/local/etc/dnsmasq.d/hisn-blocklist.conf"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --hosts)    MODE="hosts"; shift ;;
        --merge)    MODE="merge"; shift ;;
        --dnsmasq)  MODE="dnsmasq"; shift ;;
        --undo)     UNDO=1; shift ;;
        --dry-run)  DRY=1; shift ;;
        --list)     LIST="$2"; shift 2 ;;
        -h|--help)  sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *)          echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

need_root() {
    if [ "$DRY" -eq 0 ] && [ "$(id -u)" -ne 0 ]; then
        echo "error: needs root to write system files. Re-run with sudo," >&2
        echo "       or add --dry-run to see the output without touching them." >&2
        exit 1
    fi
}

flush_dns() {
    [ "$DRY" -eq 1 ] && return 0
    # Both are needed; which one matters depends on the macOS version, and
    # neither is harmful on a version that does not need it.
    dscacheutil -flushcache 2>/dev/null || true
    killall -HUP mDNSResponder 2>/dev/null || true
    echo "flushed the DNS cache"
}

# --------------------------------------------------------------------------- #
# Undo
# --------------------------------------------------------------------------- #

if [ "$UNDO" -eq 1 ]; then
    need_root
    did_something=0

    if grep -qF "$BEGIN_MARK" /etc/hosts 2>/dev/null; then
        # sed between the markers rather than restoring a backup: the user may
        # have edited /etc/hosts for unrelated reasons since this ran, and
        # clobbering that would be a nasty surprise.
        sed -i '' "/^${BEGIN_MARK}$/,/^${END_MARK}$/d" /etc/hosts
        echo "removed the blocklist from /etc/hosts"
        did_something=1
    fi

    if [ -f "$DNSMASQ_CONF" ]; then
        rm -f "$DNSMASQ_CONF"
        echo "removed $DNSMASQ_CONF"
        echo "note: dnsmasq itself and your DNS setting are left as they are."
        echo "      To finish: sudo brew services restart dnsmasq"
        did_something=1
    fi

    [ "$did_something" -eq 0 ] && echo "nothing to undo."
    flush_dns
    exit 0
fi

# --------------------------------------------------------------------------- #
# Checks
# --------------------------------------------------------------------------- #

if [ -z "$MODE" ]; then
    if command -v dnsmasq >/dev/null 2>&1; then
        MODE="dnsmasq"
        echo "dnsmasq found — using it (wildcard subdomain blocking)."
    else
        MODE="hosts"
        echo "dnsmasq not installed — falling back to /etc/hosts."
        echo "This cannot block subdomains. 'brew install dnsmasq' if that matters."
    fi
    echo
fi

if [ ! -f "$LIST" ]; then
    echo "error: no domain list at $LIST" >&2
    echo "       build one with: cd blocklist && python3 build.py --out ../dist" >&2
    exit 1
fi

COUNT=$(grep -vc '^#' "$LIST" || true)
if [ "$COUNT" -lt 1000 ]; then
    # Same refusal the builder and both clients make: a list that has quietly
    # collapsed looks exactly like one that is working.
    echo "error: only $COUNT domains in $LIST — refusing to apply a list" >&2
    echo "       this small, it is almost certainly truncated." >&2
    exit 1
fi

# --------------------------------------------------------------------------- #
# Apply
# --------------------------------------------------------------------------- #

if [ "$MODE" = "dnsmasq" ]; then
    if ! command -v dnsmasq >/dev/null 2>&1; then
        echo "error: dnsmasq is not installed. brew install dnsmasq" >&2
        exit 1
    fi
    need_root
    OUT="$DNSMASQ_CONF"
    [ "$DRY" -eq 1 ] && OUT="/tmp/hisn-blocklist.conf"
    mkdir -p "$(dirname "$OUT")"

    {
        echo "# Hisn blocklist — $COUNT domains, generated $(date -u +%FT%TZ)"
        echo "# Wildcards: each line covers the domain and every subdomain."
        grep -v '^#' "$LIST" | grep -v '^$' | sed 's|^|address=/|; s|$|/0.0.0.0|'
    } > "$OUT"

    echo "wrote $(grep -c '^address=' "$OUT") wildcard rules to $OUT"
    if [ "$DRY" -eq 1 ]; then
        echo "(dry run — nothing installed)"
        exit 0
    fi
    echo
    echo "Now, once:"
    echo "  1. echo 'conf-dir=$(dirname "$DNSMASQ_CONF"),*.conf' | sudo tee -a \\"
    echo "       $(brew --prefix 2>/dev/null || echo /opt/homebrew)/etc/dnsmasq.conf"
    echo "  2. sudo brew services restart dnsmasq"
    echo "  3. Point this Mac's DNS at 127.0.0.1:"
    echo "       networksetup -setdnsservers Wi-Fi 127.0.0.1"
    echo "     (undo with: networksetup -setdnsservers Wi-Fi empty)"
    flush_dns
    exit 0
fi

# --- merge ---------------------------------------------------------------- #
#
# Add only what the hosts file does not already block.
#
# A machine that already runs StevenBlack (or any other hosts list) shares a
# large overlap with this one — measured at 47,295 of 148,356 on the machine
# this was written for. Appending the whole list would write those 47k twice,
# for no additional coverage and real cost: /etc/hosts is read on every lookup
# and its size is the one thing that makes DNS slow.
#
# So the block written here is the DIFFERENCE. Undo still removes exactly it,
# because it sits between the same markers, and the pre-existing list is left
# untouched either way.

if [ "$MODE" = "merge" ]; then
    need_root
    TARGET=/etc/hosts
    [ "$DRY" -eq 1 ] && TARGET=/tmp/hosts.hisn-merge-preview

    NEW_ONLY=$(mktemp)
    # Every host name already blocked, exactly as written, ignoring our own
    # previous block. Exact on both sides: this once stripped `www.` from the
    # existing entries, so a file blocking only www.example.com counted
    # example.com as covered and the apex was never added. A line may name
    # several hosts, and a sinkhole may be 127.0.0.1 or :: as well as 0.0.0.0.
    sed "/^${BEGIN_MARK}$/,/^${END_MARK}$/d" /etc/hosts \
        | awk '$1 == "0.0.0.0" || $1 == "127.0.0.1" || $1 == "::" || $1 == "::1" {
                   for (i = 2; i <= NF && $i !~ /^#/; i++) print tolower($i) }' \
        | sort -u > "$NEW_ONLY.have"
    grep -v '^#' "$LIST" | grep -v '^$' | awk '{ print $0; print "www." $0 }' \
        | sort -u > "$NEW_ONLY.want"
    comm -13 "$NEW_ONLY.have" "$NEW_ONLY.want" > "$NEW_ONLY"

    ADD=$(wc -l < "$NEW_ONLY" | tr -d ' ')
    HAVE=$(wc -l < "$NEW_ONLY.have" | tr -d ' ')
    echo "already blocked: $HAVE host names"
    echo "this list:       $COUNT domains (each with its www. name)"
    echo "new to add:      $ADD host names"
    echo

    if [ "$DRY" -eq 0 ]; then
        BACKUP="/etc/hosts.hisn-backup-$(date +%Y%m%d%H%M%S)"
        cp /etc/hosts "$BACKUP"
        echo "backed up /etc/hosts -> $BACKUP"
        if grep -qF "$BEGIN_MARK" /etc/hosts; then
            sed -i '' "/^${BEGIN_MARK}$/,/^${END_MARK}$/d" /etc/hosts
            echo "replaced the previous Hisn block"
        fi
    else
        : > "$TARGET"
        cat /etc/hosts >> "$TARGET"
    fi

    {
        echo "$BEGIN_MARK"
        echo "# $ADD host names not already present, generated $(date -u +%FT%TZ)"
        echo "# Remove with: sudo macos/block_dns.sh --undo"
        awk '{print "0.0.0.0 " $0}' "$NEW_ONLY"
        echo "$END_MARK"
    } >> "$TARGET"

    rm -f "$NEW_ONLY" "$NEW_ONLY.have" "$NEW_ONLY.want"
    echo "wrote the merged block to $TARGET"
    [ "$DRY" -eq 1 ] && { echo "(dry run — /etc/hosts untouched)"; exit 0; }
    flush_dns
    echo
    echo "Remove it with: sudo macos/block_dns.sh --undo"
    exit 0
fi

# --- hosts ---------------------------------------------------------------- #

need_root
TARGET=/etc/hosts
[ "$DRY" -eq 1 ] && TARGET=/tmp/hosts.hisn-preview

LINES=$(( COUNT * 2 ))
echo "About to add ~$LINES lines to /etc/hosts ($COUNT domains, plus a www."
echo "line each). macOS reads this file on every lookup and gets slow well"
echo "before this size — dnsmasq is the better tool if you have the option."
echo

if [ "$DRY" -eq 0 ]; then
    BACKUP="/etc/hosts.hisn-backup-$(date +%Y%m%d%H%M%S)"
    cp /etc/hosts "$BACKUP"
    echo "backed up /etc/hosts -> $BACKUP"
    # Replace any previous run rather than stacking a second copy.
    if grep -qF "$BEGIN_MARK" /etc/hosts; then
        sed -i '' "/^${BEGIN_MARK}$/,/^${END_MARK}$/d" /etc/hosts
        echo "replaced the previous Hisn block"
    fi
fi

# Truncate in dry-run: the preview path persists between runs, and appending
# to it silently produced a file with two copies of the list the first time
# this was tested twice in a row.
[ "$DRY" -eq 1 ] && : > "$TARGET"

{
    [ "$DRY" -eq 1 ] && cat /etc/hosts
    echo "$BEGIN_MARK"
    echo "# $COUNT domains, generated $(date -u +%FT%TZ) by macos/block_dns.sh"
    echo "# Remove with: sudo macos/block_dns.sh --undo"
    grep -v '^#' "$LIST" | grep -v '^$' | awk '{print "0.0.0.0 " $0 "\n0.0.0.0 www." $0}'
    echo "$END_MARK"
} >> "$TARGET"

echo "wrote the block to $TARGET"
[ "$DRY" -eq 1 ] && { echo "(dry run — /etc/hosts untouched)"; exit 0; }
flush_dns
echo
echo "Remove it all with: sudo macos/block_dns.sh --undo"
