#!/bin/bash
#
# Install Hisn on this Mac for everyday use — one command, in order.
#
#     macos/install.sh                  # build, install to /Applications, keep it running
#     macos/install.sh --team ABCDE12345  # sign with your Apple team (the system
#                                         # filter needs the paid program's entitlements)
#     macos/install.sh --built-app /absolute/path/Hisn.app  # install a tested build;
#                                         # skips all builds, also under an admin prompt
#     macos/install.sh --hosts          # also merge the blocklist into /etc/hosts (sudo)
#     macos/install.sh --profile        # also build the hardening profile and open it
#     macos/install.sh --profile -- --allow-devtools   # pass options to make_profile.py
#     macos/install.sh --remove-agent   # stop relaunching the app (e.g. before removing it)
#
# WHAT IT DOES
#   1. Builds the app in Release.
#   2. Replaces /Applications/Hisn.app. /Applications, not ~/Applications: a
#      home-folder app is the user's own file and deletes without a password,
#      and the system extension will only activate from /Applications.
#   3. Installs a LaunchAgent that starts Hisn at login, in the background,
#      and brings it back after a crash or force-quit. Closing its window or
#      an ordinary Quit leaves protection running throughout the signed-in
#      session. Logout, restart and shutdown still work normally.
#   4. Launches it once, which registers the browser link (native messaging)
#      for every installed Chromium browser.
#   5. Optionally: the hosts-file blocklist and the hardening profile.
#   6. Runs verify_enforcement.sh, so you see what is enforced and what is not.
#
# WHAT IT DOES NOT DO
#   Load the browser extension — see docs/SETUP.md step 5 — or split the
#   accounts (setup_guardian.sh). Those need a person, not a script.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="$(pwd)"

TEAM=""; HOSTS=0; PROFILE=0; REMOVE_AGENT=0; BUILT=""; PROFILE_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --team)          TEAM="${2:?--team needs your Apple team id}"; shift 2 ;;
        --built-app)     BUILT="${2:?--built-app needs an absolute app path}"; shift 2 ;;
        --hosts)         HOSTS=1; shift ;;
        --profile)       PROFILE=1; shift ;;
        --remove-agent)  REMOVE_AGENT=1; shift ;;
        --)              shift; PROFILE_ARGS=("$@"); break ;;
        -h|--help)       sed -n '2,30p' "$0"; exit 0 ;;
        *)               echo "unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
done

APP="/Applications/Hisn.app"
LABEL="app.hisn.agent"
# In /Library, owned by root: one in ~/Library/LaunchAgents is the daily
# user's own file, which they could delete after the account split — and the
# app would never start again. This one survives, and reloads at every login.
AGENT="/Library/LaunchAgents/$LABEL.plist"
USER_AGENT="$HOME/Library/LaunchAgents/$LABEL.plist"   # where earlier installs put it
DOMAIN="gui/$(id -u)"

if [ "$REMOVE_AGENT" -eq 1 ]; then
    launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
    sudo rm -f "$AGENT"
    rm -f "$USER_AGENT"
    echo "LaunchAgent removed. Hisn will no longer start at login or come back when quit."
    exit 0
fi

step() { printf "\n\033[1m── %s\033[0m\n" "$1"; }

# ---- 1. build ---------------------------------------------------------------
if [ -z "$BUILT" ]; then
    [ "$EUID" -ne 0 ] || { echo "Build as your normal account first, then install with --built-app." >&2; exit 1; }
    step "Building Hisn (Release)"
    BUILD="$(mktemp -d "${TMPDIR:-/tmp}/hisn-build.XXXXXX")"
    SIGN=(CODE_SIGNING_ALLOWED=NO)
    if [ -n "$TEAM" ]; then
        SIGN=(DEVELOPMENT_TEAM="$TEAM" CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates)
    else
        echo "No --team: building a local ad-hoc app, not a signed system filter."
        echo "The system filter needs Apple Developer Program entitlements."
    fi
    xcodebuild -project macos/Hisn.xcodeproj -scheme Hisn -configuration Release \
        -derivedDataPath "$BUILD" build "${SIGN[@]}" 2>&1 \
        | tee "$BUILD.log" | grep -E "error:|\*\* BUILD" || true
    grep -q "\*\* BUILD SUCCEEDED \*\*" "$BUILD.log" || { echo "build failed — see $BUILD.log" >&2; exit 1; }
    BUILT="$BUILD/Build/Products/Release/Hisn.app"
    if [ -z "$TEAM" ]; then
        # Seal the local bundle so the installer can verify the staged copy.
        # Ad-hoc signing grants no Network Extension entitlement or notarization.
        codesign --force --sign - "$BUILT/Contents/MacOS/HisnBridge"
        codesign --force --sign - "$BUILT/Contents/Library/SystemExtensions/HisnFilter.systemextension"
        codesign --force --sign - "$BUILT"
    fi
else
    [[ "$BUILT" = /* && -z "$TEAM" ]] || { echo "--built-app requires an absolute app path and cannot be combined with --team." >&2; exit 2; }
fi

# ---- 2. install -------------------------------------------------------------
# The password first, before anything is stopped: an install interrupted at
# the prompt used to leave the app quit and its agent unloaded.
step "Updating Hisn with a recoverable backup, preserving the protected account"
if [ "$EUID" -eq 0 ]; then
    /bin/bash "$REPO/macos/install_update.sh" "$BUILT"
else
    sudo -v || { echo "the install needs an administrator's password" >&2; exit 1; }
    sudo /bin/bash "$REPO/macos/install_update.sh" "$BUILT"
fi

# ---- 4. first launch ----------------------------------------------------------
step "Registering the browser link"
sleep 3
for f in "$HOME/Library/Application Support"/*/NativeMessagingHosts/app.hisn.bridge.json \
         "$HOME/Library/Application Support"/*/*/NativeMessagingHosts/app.hisn.bridge.json; do
    [ -f "$f" ] || continue
    path=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['path'])" "$f")
    case "$path" in
        "$APP"/*) echo "  ok  $(dirname "$(dirname "$f")" | sed "s#$HOME/Library/Application Support/##")" ;;
        *)        echo "  !!  $f still points at $path" ;;
    esac
done

# ---- 5. optional layers --------------------------------------------------------
if [ "$HOSTS" -eq 1 ]; then
    step "hosts file (asks for your password)"
    sudo "$REPO/macos/block_dns.sh" --merge
fi

if [ "$PROFILE" -eq 1 ]; then
    # The profile turns off user-level browser links (a user could point one
    # at their own script), so the admin-owned one must exist first or the
    # extension loses the app. Every id the app admits is admitted here too.
    step "Admin-owned browser link (asks for your password)"
    IDS=()
    while read -r id; do IDS+=(--extension-id "$id"); done < <(
        python3 -c 'import plistlib,sys; print("\n".join(plistlib.load(open(sys.argv[1], "rb"))["HisnExtensionIDs"]))' "$APP/Contents/Info.plist")
    if [ "${#IDS[@]}" -eq 0 ]; then   # and bash 3.2 cannot expand an empty array under set -u
        echo "error: no extension ids found in Hisn Info.plist" >&2
        exit 1
    fi
    sudo "$REPO/macos/install_native_host.sh" "${IDS[@]}"

    step "Hardening profile"
    # Built in a folder only this account can read, and deleted once System
    # Settings has taken its copy: the file holds the removal password in plain
    # text, and one left behind in the repo's dist/ is the password on disk.
    PROFILE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hisn-profile.XXXXXX")"
    chmod 700 "$PROFILE_DIR"
    trap 'rm -rf "$PROFILE_DIR"' EXIT
    OUT="$PROFILE_DIR/hisn-hardening.mobileconfig"
    python3 "$REPO/profile/make_profile.py" --out "$OUT" --print-password ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"}
    echo
    echo "Give the removal password above to your accountability partner, not yourself."
    echo "Opening the profile — install it in System Settings › General › Device Management."
    open "$OUT"
    if [ -t 0 ]; then
        read -r -p "Press Return once System Settings shows the profile (the file is then deleted)… " _ || true
    else
        sleep 15
    fi
    rm -rf "$PROFILE_DIR"
    trap - EXIT
fi

# ---- 6. what is actually enforced ------------------------------------------------
step "What is enforced now"
"$REPO/macos/verify_enforcement.sh" || true
