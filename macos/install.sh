#!/bin/bash
#
# Install Hisn on this Mac for everyday use — one command, in order.
#
#     macos/install.sh                  # build, install to /Applications, keep it running
#     macos/install.sh --team ABCDE12345  # sign with your Apple team (the system
#                                         # filter needs the paid program's entitlements)
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
#      and brings it back if it is force-quit. During a lock Hisn refuses an
#      ordinary Quit (it is the browser guard); outside one it quits normally
#      and stays quit until the next login.
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

TEAM=""; HOSTS=0; PROFILE=0; REMOVE_AGENT=0; PROFILE_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --team)          TEAM="${2:?--team needs your Apple team id}"; shift 2 ;;
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
step "Building Hisn (Release)"
BUILD="${TMPDIR:-/tmp}/hisn-build"
SIGN=(CODE_SIGNING_ALLOWED=NO)
if [ -n "$TEAM" ]; then
    SIGN=(DEVELOPMENT_TEAM="$TEAM" CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates)
else
    echo "No --team: building unsigned. Everything runs except the system filter,"
    echo "which needs the paid Apple Developer Program's entitlements."
fi
xcodebuild -project macos/Hisn.xcodeproj -scheme Hisn -configuration Release \
    -derivedDataPath "$BUILD" build "${SIGN[@]}" 2>&1 \
    | tee "$BUILD.log" | grep -E "error:|\*\* BUILD" || true
grep -q "\*\* BUILD SUCCEEDED \*\*" "$BUILD.log" || { echo "build failed — see $BUILD.log" >&2; exit 1; }
BUILT="$BUILD/Build/Products/Release/Hisn.app"

# ---- 2. install -------------------------------------------------------------
# The password first, before anything is stopped: an install interrupted at
# the prompt used to leave the app quit and its agent unloaded.
sudo -v || { echo "the install needs an administrator's password" >&2; exit 1; }
step "Installing to $APP, owned by the system (asks for your password)"
launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
if pgrep -xq Hisn; then
    # A running lock refuses an ordinary Quit, by design. Replacing the app
    # while it runs is safe: the lock lives on disk and in the filter.
    pkill -x Hisn || true
    sleep 1
fi
STAGE="$(mktemp -d /Applications/.hisn-install.XXXXXX 2>/dev/null)" \
    || { echo "cannot write to /Applications — run this as an administrator" >&2; exit 1; }
# A half-finished copy must not stay behind in /Applications.
trap 'sudo rm -rf "$STAGE" 2>/dev/null || rm -rf "$STAGE"' EXIT
ditto "$BUILT" "$STAGE/Hisn.app"
# Owned by root, writable by no one else. Copied as the person running this,
# the bundle stayed theirs after the account split — and the admin-owned
# browser link in /Library runs Contents/MacOS/HisnBridge, so a standard user
# could still replace that one file with a program answering "no lock". The
# same goes for every other file in the bundle. Updating Hisn now needs an
# administrator, like everything else the lock stands on.
sudo chown -R root:wheel "$STAGE/Hisn.app"
sudo chmod -R go-w "$STAGE/Hisn.app"
sudo rm -rf "$APP"
sudo mv "$STAGE/Hisn.app" "$APP"
rmdir "$STAGE"
trap - EXIT
echo "installed $(defaults read "$APP/Contents/Info" CFBundleShortVersionString 2>/dev/null || echo "") at $APP"

# ---- 3. keep it running ------------------------------------------------------
step "LaunchAgent: start at login, come back if force-quit"
rm -f "$USER_AGENT"
AGENT_TMP="$(mktemp "${TMPDIR:-/tmp}/hisn-agent.XXXXXX")"
# /Library/LaunchAgents loads for every account that logs in — the partner's
# administrator account too — so the agent names whose session it is for, and
# the app quits quietly anywhere else (--for-user).
cat > "$AGENT_TMP" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>$LABEL</string>
    <key>ProgramArguments</key>
    <array><string>$APP/Contents/MacOS/Hisn</string><string>--background</string><string>--for-user</string><string>$(id -un)</string></array>
    <key>RunAtLoad</key><true/>
    <!-- Relaunch after a crash or a force-quit (a non-zero exit), not after
         an ordinary Quit — which Hisn refuses during a lock anyway. -->
    <key>KeepAlive</key><dict><key>SuccessfulExit</key><false/></dict>
    <key>ThrottleInterval</key><integer>10</integer>
    <key>LimitLoadToSessionType</key><string>Aqua</string>
    <key>ProcessType</key><string>Interactive</string>
</dict>
</plist>
PLIST
plutil -lint "$AGENT_TMP" >/dev/null
sudo install -o root -g wheel -m 644 "$AGENT_TMP" "$AGENT"
rm -f "$AGENT_TMP"
launchctl bootstrap "$DOMAIN" "$AGENT"
echo "loaded $LABEL (from $AGENT, owned by root)"

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
        grep -oE '"[a-p]{32}"' "$REPO/macos/Hisn/NativeMessagingInstaller.swift" | tr -d '"')
    if [ "${#IDS[@]}" -eq 0 ]; then   # and bash 3.2 cannot expand an empty array under set -u
        echo "error: no extension ids found in NativeMessagingInstaller.swift" >&2
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
