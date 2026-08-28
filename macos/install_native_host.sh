#!/bin/bash
#
# Install the native-messaging host manifest for the Hisn browser extension.
#
# Without this file the extension's `sendNativeMessage` call fails on every
# heartbeat. That is not a quiet degradation: `handleNativeLoss` in
# background.js reads sustained silence during an active lock as tampering and
# fails closed to strict mode. So a missing manifest does not look like a broken
# link — it looks like the extension blocking everything, for a reason nothing
# in the UI explains.
#
#   sudo ./install_native_host.sh --extension-id <chrome-web-store-id>
#
# System scope (the default) writes to /Library, which needs administrator
# rights to install *and to remove*. That is the point: under the setup this
# product is built around — the person on a standard account, a second person
# holding the admin password — the user cannot delete the manifest to cut the
# extension off from the lock clock. Use --user only for development.

set -euo pipefail

APP_PATH="/Applications/Hisn.app"
EXTENSION_ID=""
SCOPE="system"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --app-path)     APP_PATH="$2"; shift 2 ;;
        --extension-id) EXTENSION_ID="$2"; shift 2 ;;
        --user)         SCOPE="user"; shift ;;
        -h|--help)      sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *)              echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [[ -z "$EXTENSION_ID" ]]; then
    echo "error: --extension-id is required (the Chrome Web Store id)" >&2
    exit 2
fi

BRIDGE="$APP_PATH/Contents/MacOS/HisnBridge"
if [[ ! -x "$BRIDGE" ]]; then
    echo "error: no bridge executable at $BRIDGE" >&2
    echo "       build the app first, or pass --app-path" >&2
    exit 1
fi

if [[ "$SCOPE" == "system" ]]; then
    TARGETS=(
        "/Library/Google/Chrome/NativeMessagingHosts"
        "/Library/Microsoft/Edge/NativeMessagingHosts"
    )
else
    TARGETS=(
        "$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts"
        "$HOME/Library/Application Support/Microsoft Edge/NativeMessagingHosts"
    )
fi

# `allowed_origins` is the whole access-control story for a native messaging
# host: any extension listed here can talk to this process. It stays a single
# specific id — a wildcard would let any installed extension read the lock state.
MANIFEST=$(cat <<EOF
{
  "name": "app.hisn.bridge",
  "description": "Hisn lock-state bridge",
  "path": "$BRIDGE",
  "type": "stdio",
  "allowed_origins": [
    "chrome-extension://$EXTENSION_ID/"
  ]
}
EOF
)

for dir in "${TARGETS[@]}"; do
    if ! mkdir -p "$dir" 2>/dev/null; then
        echo "skipped $dir (no permission — re-run with sudo for system scope)" >&2
        continue
    fi
    printf '%s\n' "$MANIFEST" > "$dir/app.hisn.bridge.json"
    chmod 644 "$dir/app.hisn.bridge.json"
    echo "installed $dir/app.hisn.bridge.json"
done

echo
echo "Restart the browser, then confirm the link is up: the extension's popup"
echo "should show the lock state rather than falling back to strict."
