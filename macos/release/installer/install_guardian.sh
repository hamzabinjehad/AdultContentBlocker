#!/bin/bash
# Install or refresh the root guardian daemon. Sourced by the developer updater
# and the installer package's postinstall; both run as root.
#
#   install_guardian <folder-with-hisn-guardian-and-plist> <protected-user>

GUARDIAN_DIR="/Library/Application Support/Hisn"
GUARDIAN_DAEMON="/Library/LaunchDaemons/app.hisn.guardian.plist"
GUARDIAN_LABEL="app.hisn.guardian"

install_guardian() {
    local source_dir="$1" user="$2"
    [ -f "$source_dir/hisn-guardian" ] && [ -f "$source_dir/app.hisn.guardian.plist" ] || {
        echo "guardian: installer payload is incomplete" >&2
        return 1
    }
    [[ "$user" =~ ^[a-zA-Z0-9._-]+$ ]] || {
        echo "guardian: invalid protected account" >&2
        return 1
    }
    /bin/mkdir -p "$GUARDIAN_DIR" || return 1
    /usr/sbin/chown root:wheel "$GUARDIAN_DIR" || return 1
    /bin/chmod 755 "$GUARDIAN_DIR" || return 1
    /usr/bin/install -o root -g wheel -m 755 "$source_dir/hisn-guardian" \
        "$GUARDIAN_DIR/hisn-guardian" || return 1
    printf 'USER_NAME=%s\n' "$user" > "$GUARDIAN_DIR/guardian.conf" || return 1
    /usr/sbin/chown root:wheel "$GUARDIAN_DIR/guardian.conf" || return 1
    /bin/chmod 644 "$GUARDIAN_DIR/guardian.conf" || return 1
    /usr/bin/install -o root -g wheel -m 644 "$source_dir/app.hisn.guardian.plist" \
        "$GUARDIAN_DAEMON" || return 1
    # Reload only the guardian. Other system daemons are untouched.
    /bin/launchctl bootout "system/$GUARDIAN_LABEL" >/dev/null 2>&1 || true
    /bin/launchctl bootstrap system "$GUARDIAN_DAEMON" >/dev/null 2>&1 || {
        echo "guardian: launchd did not load $GUARDIAN_DAEMON" >&2
        return 1
    }
    return 0
}

remove_guardian() {
    /bin/launchctl bootout "system/$GUARDIAN_LABEL" >/dev/null 2>&1 || true
    /bin/rm -f "$GUARDIAN_DAEMON"
    /bin/rm -f "$GUARDIAN_DIR/hisn-guardian" "$GUARDIAN_DIR/guardian.conf"
    return 0
}
