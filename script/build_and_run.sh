#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-run}"
case "$MODE" in run|--verify|--debug|--logs|--telemetry) ;; *) echo "Usage: $0 [--verify|--debug|--logs|--telemetry]" >&2; exit 2 ;; esac
BUILD="$ROOT/macos/build/local"
APP="$BUILD/Build/Products/Debug/Hisn.app"
BINARY="$APP/Contents/MacOS/Hisn"
# Stop only this development build. The installed app has its own login agent.
while read -r PID COMMAND; do
    if [ "$COMMAND" = "$BINARY" ]; then kill "$PID"; fi
done < <(ps -axo pid=,comm=)
mkdir -p "$BUILD"
xcodebuild -project "$ROOT/macos/Hisn.xcodeproj" -scheme Hisn -configuration Debug \
    -derivedDataPath "$BUILD" build CODE_SIGNING_ALLOWED=NO > "$BUILD/build.log" 2>&1 \
    || { tail -40 "$BUILD/build.log"; exit 1; }
if [ "$MODE" = --debug ]; then
    exec lldb -- "$BINARY"
fi
open -n "$APP"
case "$MODE" in
    --verify)
        sleep 2
        ps -axo comm= | sed 's/^[[:space:]]*//' | grep -Fx "$BINARY" >/dev/null
        echo "Hisn launched: $APP" ;;
    --logs|--telemetry)
        exec /usr/bin/log stream --info --style compact --predicate 'process == "Hisn"' ;;
    run) echo "Hisn launched: $APP" ;;
esac
