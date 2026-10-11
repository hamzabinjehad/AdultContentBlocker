#!/bin/bash
# Privileged, narrow bundle update. Build and test before requesting permission.
# No policy, DNS, profile, account role or browser preferences are changed here.
set -euo pipefail

APP=/Applications/Hisn.app
AGENT=/Library/LaunchAgents/app.hisn.agent.plist
LABEL=app.hisn.agent
BACKUP_ROOT='/Library/Application Support/Hisn Updates'
LOCK=/var/run/app.hisn.install.lock

fail() { echo "error: $*" >&2; exit 1; }
plist_value() { /usr/libexec/PlistBuddy -c "Print :$2" "$1" 2>/dev/null; }
signing_team() {
    /usr/bin/codesign -dv --verbose=4 "$1" 2>&1 | /usr/bin/awk '
        /^TeamIdentifier=/ { sub(/^TeamIdentifier=/, ""); if ($0 != "not set") print; }'
}
newer_build() {
    [[ "$1" =~ ^[0-9]+(\.[0-9]+){0,2}$ && "$2" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]] || return 1
    /usr/bin/awk -v old="$1" -v new="$2" 'BEGIN {
        split(old, a, "."); split(new, b, ".");
        for (i=1; i<=3; i++) {
            if (b[i]+0 > a[i]+0) exit 0;
            if (b[i]+0 < a[i]+0) exit 1;
        }
        exit 1;
    }'
}
validate_bundle() {
    local bundle="$1" info filter filter_info app_team
    [[ -d "$bundle" && ! -L "$bundle" ]] || fail "The app must be a real directory."
    info="$bundle/Contents/Info.plist"
    filter="$bundle/Contents/Library/SystemExtensions/HisnFilter.systemextension"
    filter_info="$filter/Contents/Info.plist"
    [[ "$(plist_value "$info" CFBundleIdentifier)" = app.hisn.Hisn ]] || fail "Unexpected app identity."
    [[ "$(plist_value "$filter_info" CFBundleIdentifier)" = app.hisn.Hisn.HisnFilter ]] || fail "Unexpected filter identity."
    [[ "$(plist_value "$info" CFBundleVersion)" = "$(plist_value "$filter_info" CFBundleVersion)" ]] || fail "App and filter build numbers differ."
    [[ "$(plist_value "$info" CFBundleShortVersionString)" = "$(plist_value "$filter_info" CFBundleShortVersionString)" ]] || fail "App and filter versions differ."
    [[ -x "$bundle/Contents/MacOS/Hisn" && -x "$bundle/Contents/MacOS/HisnBridge" && -x "$filter/Contents/MacOS/HisnFilter" ]] || fail "Incomplete app bundle."
    /usr/bin/codesign --verify --deep --strict "$bundle" || fail "App signature validation failed."
    app_team="$(signing_team "$bundle")"
    [[ "$(signing_team "$filter")" = "$app_team" && "$(signing_team "$bundle/Contents/MacOS/HisnBridge")" = "$app_team" ]] || fail "App, filter and bridge signing teams differ."
}
secure_agent_permissions() {
    [[ "$1" = 0 && "$2" =~ ^[0-7]{3,4}$ ]] || return 1
    (( (8#$2 & 0022) == 0 ))
}
protected_user() {
    local agent="$1" user
    if [[ -e "$agent" || -L "$agent" ]]; then
        [[ -f "$agent" && ! -L "$agent" ]] || fail "Unexpected existing login-agent file."
        [[ "$(plist_value "$agent" Label)" = "$LABEL" ]] || fail "Unexpected existing login-agent label."
        [[ "$(plist_value "$agent" ProgramArguments:0)" = "$APP/Contents/MacOS/Hisn" ]] || fail "The existing agent uses a different app location."
        [[ "$(plist_value "$agent" ProgramArguments:1)" = --background && "$(plist_value "$agent" ProgramArguments:2)" = --for-user ]] || fail "Cannot safely identify the existing protected account."
        user="$(plist_value "$agent" ProgramArguments:3)"
    else
        user="$(/usr/bin/stat -f '%Su' /dev/console)"
    fi
    [[ "$user" =~ ^[a-zA-Z0-9._-]+$ ]] || fail "Cannot determine the protected account."
    case "$user" in root|loginwindow|_mbsetupuser) fail "Sign in to the protected account first." ;; esac
    /usr/bin/id -u "$user" >/dev/null || fail "The protected account no longer exists."
    printf '%s\n' "$user"
}
write_agent() {
    /usr/bin/plutil -create xml1 "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c "Add :Label string $LABEL" "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :ProgramArguments array' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c "Add :ProgramArguments:0 string $APP/Contents/MacOS/Hisn" "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :ProgramArguments:1 string --background' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :ProgramArguments:2 string --for-user' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c "Add :ProgramArguments:3 string $USER_NAME" "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :RunAtLoad bool true' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :KeepAlive dict' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :KeepAlive:SuccessfulExit bool false' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :ThrottleInterval integer 10' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :LimitLoadToSessionType string Aqua' "$TEMP_AGENT"
    /usr/libexec/PlistBuddy -c 'Add :ProcessType string Interactive' "$TEMP_AGENT"
    /usr/bin/plutil -lint "$TEMP_AGENT" >/dev/null
}
stop_installed_app() {
    local pid executable
    # Do not stop another account's app, a test host, or an unrelated build.
    for pid in $(/usr/bin/pgrep -x -u "$USER_ID" Hisn || true); do
        executable="$(/bin/ps -p "$pid" -o comm= | /usr/bin/sed 's/^ *//')"
        [[ "$executable" = "$APP/Contents/MacOS/Hisn" ]] || continue
        /bin/kill -TERM "$pid" 2>/dev/null || true
    done
}

# Source only the definitions in fixture tests; never run privileged operations.
if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then return; fi
[[ $# = 1 && "$1" = /* ]] || fail "Usage: install_update.sh /absolute/path/to/Hisn.app"
[[ "$EUID" = 0 ]] || fail "Installation requires administrator permission."
BUILT="$1"
[[ "$BUILT" != "$APP" ]] || fail "Choose a separately built app, not the installed copy."
[[ ! -L /Applications && ! -L "$APP" && ! -L "$BACKUP_ROOT" && ! -L "$AGENT" ]] || fail "Unexpected symlink at an installation destination."
validate_bundle "$BUILT"

# Serialize updates. An interrupted install leaves evidence for an administrator;
# do not automatically delete somebody else's lock or backup.
/bin/mkdir "$LOCK" 2>/dev/null || fail "Another Hisn installation is in progress; inspect $LOCK before retrying."
STAGE=""; BACKUP=""; OLD_MOVED=0; NEW_MOVED=0; AGENT_CREATED=0; AGENT_STOPPED=0; COMMITTED=0
cleanup() {
    local result=$?
    trap - EXIT HUP INT TERM
    if [[ "$COMMITTED" = 0 && "$AGENT_STOPPED" = 1 ]]; then
        /bin/launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
        stop_installed_app
        if [[ "$NEW_MOVED" = 1 && -d "$APP" ]]; then
            /bin/mv "$APP" "$BACKUP/Failed-Hisn.app" || true
        fi
        if [[ "$OLD_MOVED" = 1 && -d "$BACKUP/Hisn.app" && ! -e "$APP" ]]; then
            /bin/mv "$BACKUP/Hisn.app" "$APP" || true
        fi
        if [[ "$AGENT_CREATED" = 1 ]]; then /bin/rm -f "$AGENT"; fi
        if [[ -f "$AGENT" && -d "$APP" ]]; then
            /bin/launchctl bootstrap "$DOMAIN" "$AGENT" 2>/dev/null || true
        fi
        echo "The update did not complete. Previous app restoration was attempted; backup: $BACKUP" >&2
    fi
    if [[ -n "$STAGE" && "$STAGE" = /Applications/.hisn-install.* && -d "$STAGE" ]]; then
        /bin/rm -rf "$STAGE"
    fi
    /bin/rmdir "$LOCK" 2>/dev/null || true
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM

USER_NAME="$(protected_user "$AGENT")"
if [[ -f "$AGENT" ]]; then
    secure_agent_permissions "$(/usr/bin/stat -f '%u' "$AGENT")" "$(/usr/bin/stat -f '%Lp' "$AGENT")" || fail "The existing login agent must be root-owned and not group/world writable."
fi
USER_ID="$(/usr/bin/id -u "$USER_NAME")"
DOMAIN="gui/$USER_ID"
/bin/launchctl print "$DOMAIN" >/dev/null 2>&1 || fail "The protected account must be signed in for this update."
if [[ -d "$APP" ]]; then
    OLD_TEAM="$(signing_team "$APP")"
    NEW_TEAM="$(signing_team "$BUILT")"
    [[ -z "$OLD_TEAM" || "$OLD_TEAM" = "$NEW_TEAM" ]] || fail "Refusing a signing-team change or an unsigned downgrade."
    newer_build "$(plist_value "$APP/Contents/Info.plist" CFBundleVersion)" "$(plist_value "$BUILT/Contents/Info.plist" CFBundleVersion)" || fail "The update must have a newer app build number."
fi
SYSTEM_EXTENSIONS="$(/usr/bin/systemextensionsctl list)" || fail "Cannot inspect the installed system filters."
ACTIVE_FILTER_TEAM="$(printf '%s\n' "$SYSTEM_EXTENSIONS" | /usr/bin/awk '
    $4 == "app.hisn.Hisn.HisnFilter" && $1 == "*" && $2 == "*" && /\[activated enabled\]/ { print $3 }')"
if [[ -n "$ACTIVE_FILTER_TEAM" ]]; then
    [[ "$ACTIVE_FILTER_TEAM" = "$(signing_team "$BUILT")" ]] || fail "An active Hisn system filter requires an update signed by the same team."
fi

IDS=(); INDEX=0
while ID="$(plist_value "$BUILT/Contents/Info.plist" "HisnExtensionIDs:$INDEX")"; do
    [[ "$ID" =~ ^[a-p]{32}$ ]] || fail "Invalid browser extension identity."
    IDS+=(--extension-id "$ID")
    INDEX=$((INDEX + 1))
done
[[ ${#IDS[@]} -gt 0 ]] || fail "No browser extension identities in the update."
if [[ -d "$APP" ]]; then
    INDEX=0
    while ID="$(plist_value "$APP/Contents/Info.plist" "HisnExtensionIDs:$INDEX")"; do
        FOUND=0
        for NEW_ID in "${IDS[@]}"; do [[ "$NEW_ID" != "$ID" ]] || FOUND=1; done
        [[ "$FOUND" = 1 ]] || fail "The update would remove an existing browser identity."
        INDEX=$((INDEX + 1))
    done
fi

# Complete copying and signature verification before stopping protection.
STAGE="$(/usr/bin/mktemp -d /Applications/.hisn-install.XXXXXX)"
/usr/bin/ditto "$BUILT" "$STAGE/Hisn.app"
/usr/sbin/chown -R root:wheel "$STAGE/Hisn.app"
/bin/chmod -R go-w "$STAGE/Hisn.app"
validate_bundle "$STAGE/Hisn.app"
/bin/mkdir -p "$BACKUP_ROOT"
/usr/sbin/chown root:wheel "$BACKUP_ROOT"
/bin/chmod 700 "$BACKUP_ROOT"
BACKUP="$(/usr/bin/mktemp -d "$BACKUP_ROOT/backup.XXXXXX")"
if [[ -f "$AGENT" ]]; then
    /usr/bin/ditto "$AGENT" "$BACKUP/app.hisn.agent.plist"
else
    /bin/mkdir -p /Library/LaunchAgents
    TEMP_AGENT="$STAGE/app.hisn.agent.plist"
    write_agent
fi

AGENT_STOPPED=1
/bin/launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
stop_installed_app
# A stale process must not keep serving the old bridge during the replacement.
for attempt in 1 2 3 4 5; do
    RUNNING=0
    for PID in $(/usr/bin/pgrep -x -u "$USER_ID" Hisn || true); do
        [[ "$(/bin/ps -p "$PID" -o comm= | /usr/bin/sed 's/^ *//')" != "$APP/Contents/MacOS/Hisn" ]] || RUNNING=1
    done
    [[ "$RUNNING" = 1 ]] || break
    /bin/sleep 1
done
[[ "$RUNNING" = 0 ]] || fail "The old installed app did not stop; keeping the previous app."
if [[ -d "$APP" ]]; then /bin/mv "$APP" "$BACKUP/Hisn.app"; OLD_MOVED=1; fi
/bin/mv "$STAGE/Hisn.app" "$APP"
NEW_MOVED=1
if [[ ! -f "$AGENT" ]]; then
    AGENT_CREATED=1
    /usr/bin/install -o root -g wheel -m 644 "$TEMP_AGENT" "$AGENT"
fi
/bin/launchctl bootstrap "$DOMAIN" "$AGENT"
COMMITTED=1
echo "Installed Hisn $(plist_value "$APP/Contents/Info.plist" CFBundleShortVersionString) (build $(plist_value "$APP/Contents/Info.plist" CFBundleVersion)) for $USER_NAME."
echo "Previous bundle and agent retained at: $BACKUP"
echo "Existing protection settings and protected account were not reset."
