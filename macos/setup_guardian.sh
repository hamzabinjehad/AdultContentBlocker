#!/bin/bash
#
# Hand this Mac's admin rights to a second person, and drop the daily user to a
# standard account. This is the single highest-leverage step in the whole
# product — see docs/THREAT_MODEL.md, rows 10–14 — and it is not a code change.
#
#     macos/setup_guardian.sh --check                 # read-only readiness report
#     sudo macos/setup_guardian.sh --create-admin      # second person types their password
#     sudo macos/setup_guardian.sh --demote-me         # LAST step, drops you to standard
#     macos/setup_guardian.sh --create-admin --dry-run # show the plan, touch nothing
#
# WHAT THIS IS, AND WHY IT IS THE THING THAT ACTUALLY WORKS
# --------------------------------------------------------
# Every "the user cannot remove it" in this product reduces to one fact: the
# person under the lock is not an administrator, and someone else is. Deleting
# the app from /Applications, disabling the content filter, removing the
# configuration profile, uninstalling a managed browser extension — each one
# needs admin, and none of the app's own defences (re-arm on launch, fail
# closed, redundant lock stores) survives an admin who wants out. So the lock is
# only as real as the account split underneath it.
#
# This script performs that split, TRANSPARENTLY and with both people present:
# the second person chooses the admin password (this script never sees it on the
# command line, never stores it, never sends it anywhere), and only after that
# account is proven to work does the daily user step down.
#
# WHAT THIS IS NOT
# ----------------
# It is NOT a hidden, vendor-controlled admin account. No password here is held
# by the app, baked into a binary, or kept on a server. That design is a
# backdoor — one breach would own every customer's Mac, and a lost server-side
# secret would lock people out of their own encrypted disks forever. The secret
# is held by a real second human, which is the only holder that cannot be
# extracted from a binary and cannot be reversed by the person alone at 2am.
#
# THE FILEVAULT TRAP THIS SCRIPT EXISTS TO AVOID
# ----------------------------------------------
# On a FileVault Mac, a user can only unlock the disk at boot if they hold a
# *secure token*. Create a second admin without a token, demote the only
# token-holder, reboot — and NOBODY can unlock the machine. That is a bricked,
# encrypted disk. So this script:
#   * grants the new admin a secure token at creation (by authenticating as the
#     existing token-holder), and
#   * refuses --demote-me unless the new admin is verified to hold a token AND
#     can actually authenticate.
# The demoted user KEEPS their own token and login; they lose admin rights, not
# the ability to boot.
set -euo pipefail

GUARDIAN_USER="hisn_guardian"
GUARDIAN_FULLNAME="Hisn Guardian"
ACTION=""
DRY=0

usage() { sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --check)         ACTION="check"; shift ;;
        --create-admin)  ACTION="create"; shift ;;
        --demote-me)     ACTION="demote"; shift ;;
        --dry-run)       DRY=1; shift ;;
        --guardian-name) GUARDIAN_USER="$2"; shift 2 ;;
        -h|--help)       usage; exit 0 ;;
        *)               echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

[ -z "$ACTION" ] && { usage; exit 2; }

# The person running this. `sudo` sets SUDO_USER; without sudo it is whoever we
# are. Never the literal "root" — demoting root is meaningless and creating the
# guardian as root's peer is not the intent.
ME="${SUDO_USER:-$(id -un)}"

need_root() {
    if [ "$DRY" -eq 0 ] && [ "$(id -u)" -ne 0 ]; then
        echo "error: this step changes accounts and needs sudo." >&2
        echo "       Re-run with sudo, or add --dry-run to see the plan only." >&2
        exit 1
    fi
}

is_admin()  { dseditgroup -o checkmember -m "$1" admin >/dev/null 2>&1; }
exists()    { id "$1" >/dev/null 2>&1; }
has_token() { sysadminctl -secureTokenStatus "$1" 2>&1 | grep -q ENABLED; }

# Admins that can actually unlock a FileVault disk. Demoting the last of these
# is the one move that bricks the machine, so it is counted explicitly.
# `grep -x` with an optional group, not `^(a|b|)$`: macOS's grep rejects the
# empty alternative ("empty (sub)expression"), which made this list nobody,
# so --check showed no admins and --demote-me always refused.
admins() {
    dscl . -read /Groups/admin GroupMembership 2>/dev/null \
        | tr ' ' '\n' | { grep -vxE '(GroupMembership:|root)?' || true; }
}
token_admins() {
    admins | while read -r u; do if has_token "$u"; then echo "$u"; fi; done
}

# --------------------------------------------------------------------------- #
# --check : read-only readiness report
# --------------------------------------------------------------------------- #

if [ "$ACTION" = "check" ]; then
    echo "Readiness for the account split"
    echo "==============================="
    echo "Daily user:        $ME"
    echo "  is admin:        $(is_admin "$ME" && echo yes || echo no)"
    echo "  has secure token:$(has_token "$ME" && echo ' yes' || echo ' no')"
    echo
    echo "FileVault:         $(fdesetup status 2>/dev/null | head -1)"
    echo
    echo "Admins on this Mac:"
    admins | while read -r u; do
            echo "  - $u  (secure token: $(has_token "$u" && echo yes || echo no))"
          done
    echo
    if exists "$GUARDIAN_USER"; then
        echo "Guardian account '$GUARDIAN_USER': EXISTS"
        echo "  is admin:        $(is_admin "$GUARDIAN_USER" && echo yes || echo no)"
        echo "  has secure token:$(has_token "$GUARDIAN_USER" && echo ' yes' || echo ' no')"
        echo
        if is_admin "$GUARDIAN_USER" && has_token "$GUARDIAN_USER"; then
            echo "READY: you can run --demote-me once the guardian has tested login."
        else
            echo "NOT READY: guardian needs both admin rights and a secure token."
        fi
    else
        echo "Guardian account '$GUARDIAN_USER': does not exist yet."
        echo "Next: sudo $0 --create-admin   (with the second person present)."
    fi
    exit 0
fi

# --------------------------------------------------------------------------- #
# --create-admin : the second person's account, with a secure token
# --------------------------------------------------------------------------- #

if [ "$ACTION" = "create" ]; then
    need_root

    if ! is_admin "$ME"; then
        echo "error: '$ME' is not an admin, so it cannot grant a secure token." >&2
        echo "       Run this from the admin account you are handing over." >&2
        exit 1
    fi
    if ! has_token "$ME" 2>/dev/null; then
        echo "warning: '$ME' has no secure token; the new admin may not get one" >&2
        echo "         either, which would make --demote-me refuse to proceed." >&2
    fi
    if exists "$GUARDIAN_USER"; then
        echo "Account '$GUARDIAN_USER' already exists. Nothing to create."
        echo "Run --check to see whether it is ready, or --demote-me to proceed."
        exit 0
    fi

    echo "About to create an administrator account: $GUARDIAN_USER"
    echo
    echo "  * The SECOND PERSON types the password on the next prompts."
    echo "  * You do not see it, this script does not store it, nothing leaves"
    echo "    this Mac. Write it down and keep it AWAY from the daily user —"
    echo "    losing it means no one can administer this machine."
    echo
    if [ "$DRY" -eq 1 ]; then
        echo "(dry run) would run:"
        echo "  sysadminctl -addUser $GUARDIAN_USER -fullName \"$GUARDIAN_FULLNAME\" \\"
        echo "     -password - -admin -adminUser $ME -adminPassword -"
        echo "  then verify secure token, then verify the new password authenticates."
        exit 0
    fi

    # `-password -` and `-adminPassword -` make sysadminctl PROMPT, so no secret
    # ever appears on the command line or in `ps`. Authenticating as $ME (a
    # secure-token holder) is what grants the new account its own token.
    sysadminctl -addUser "$GUARDIAN_USER" -fullName "$GUARDIAN_FULLNAME" \
        -password - -admin -adminUser "$ME" -adminPassword -

    echo
    if has_token "$GUARDIAN_USER"; then
        echo "OK: '$GUARDIAN_USER' created, is admin, and holds a secure token."
        echo
        echo "NOW, BEFORE YOU DEMOTE YOURSELF:"
        echo "  1. Log out and log in as '$GUARDIAN_USER' once. Confirm it works."
        echo "  2. Come back, then run:  sudo $0 --demote-me"
        echo "Do not skip step 1 — it is your proof you are not about to be locked out."
    else
        echo "PROBLEM: '$GUARDIAN_USER' was created but has NO secure token." >&2
        echo "Do NOT run --demote-me. On this FileVault Mac that would risk a" >&2
        echo "disk no one can unlock. Delete the account and retry from a" >&2
        echo "secure-token admin, or grant the token manually before demoting." >&2
        exit 1
    fi
    exit 0
fi

# --------------------------------------------------------------------------- #
# --demote-me : the last step, guarded hard
# --------------------------------------------------------------------------- #

if [ "$ACTION" = "demote" ]; then
    need_root

    if ! exists "$GUARDIAN_USER"; then
        echo "error: guardian account '$GUARDIAN_USER' does not exist." >&2
        echo "       Run --create-admin first." >&2
        exit 1
    fi
    if ! is_admin "$GUARDIAN_USER"; then
        echo "error: '$GUARDIAN_USER' is not an admin. Refusing to demote you" >&2
        echo "       into a machine with no working administrator." >&2
        exit 1
    fi
    if ! has_token "$GUARDIAN_USER"; then
        echo "error: '$GUARDIAN_USER' has no secure token. Demoting the token" >&2
        echo "       holder on a FileVault Mac can brick the disk. Refusing." >&2
        exit 1
    fi

    # The last-admin guard: after demotion, at least one OTHER token-holding
    # admin must remain. Without this, a single-person mistake takes the whole
    # machine's administrability with it.
    # `|| true`: with no other admin grep exits 1, and under pipefail that
    # ended the script right here without a word.
    remaining=$({ token_admins | grep -vx "$ME" || true; } | wc -l | tr -d ' ')
    if [ "$remaining" -lt 1 ]; then
        echo "error: demoting '$ME' would leave no other secure-token admin." >&2
        echo "       That is the bricked-disk case. Refusing." >&2
        exit 1
    fi

    echo "This removes admin rights from '$ME'. After this:"
    echo "  * you can still log in and unlock the disk (your token is kept),"
    echo "  * you can NOT sudo, delete the app, disable the filter, or remove"
    echo "    the profile — all of that now needs '$GUARDIAN_USER'."
    echo
    echo "Proof required first: have you logged in as '$GUARDIAN_USER' and back?"
    if [ "$DRY" -eq 1 ]; then
        echo "(dry run) would ask the second person for '$GUARDIAN_USER''s password,"
        echo "          then run:  dseditgroup -o edit -d $ME -t user admin"
        exit 0
    fi
    # The header promised this and nothing did it: an admin whose password
    # nobody actually knows is no admin at all. The second person types it.
    echo
    echo "The SECOND PERSON types the password of '$GUARDIAN_USER' now:"
    if ! dscl . -authonly "$GUARDIAN_USER"; then
        echo "error: that password did not authenticate '$GUARDIAN_USER'." >&2
        echo "       Refusing to demote you until someone provably holds admin." >&2
        exit 1
    fi
    echo "OK: '$GUARDIAN_USER' authenticates."
    echo
    printf "Type EXACTLY 'demote me' to proceed: "
    read -r confirm
    [ "$confirm" = "demote me" ] || { echo "Aborted."; exit 1; }

    dseditgroup -o edit -d "$ME" -t user admin

    echo
    echo "Done. '$ME' is now a standard user."
    echo
    echo "RECOVERY (needs the guardian, on purpose): to restore admin, the second"
    echo "person logs in as '$GUARDIAN_USER' and runs:"
    echo "  sudo dseditgroup -o edit -a $ME -t user admin"
    exit 0
fi
