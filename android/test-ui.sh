#!/usr/bin/env bash
# UI and UX tests for the native shell and onboarding (androidTest/.../nativeshell): story and
# setup steps, tab and row navigation, honest home state, TalkBack labels, 48 dp touch targets,
# and layout at large text and RTL.
#
# On an emulator the tests run against app.hisn.android. On a real phone they run against a
# separate copy, app.hisn.android.visualcheck ("Hisn Visual Test"), so the phone's real Hisn,
# its rules and its lock are never cleared or read. Usage:
#   bash android/test-ui.sh [device-serial] [play|direct]
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

SERIAL="${1:-$("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1; exit}')}"
DISTRIBUTION="${2:-direct}"
if [ -z "$SERIAL" ] || [ "$("$ADB" -s "$SERIAL" get-state 2>/dev/null)" != device ]; then
    echo "No device ready (got '${SERIAL}')." >&2
    exit 1
fi

GRADLE=(-q -PhisnUiTests=true -PhisnDistribution="$DISTRIBUTION")
PACKAGE=app.hisn.android
if [ "$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != 1 ]; then
    # A real phone: test a separate copy and leave the installed Hisn alone.
    GRADLE+=(-PhisnIsolatedVisualCheck=true)
    PACKAGE=app.hisn.android.visualcheck
    echo "Real phone $SERIAL: testing the separate copy $PACKAGE; the installed Hisn is not touched."
fi

./gradlew "${GRADLE[@]}" assembleHisnDebug assembleHisnDebugAndroidTest
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/hisn/debug/app-hisn-debug.apk >/dev/null
"$ADB" -s "$SERIAL" install -r -t app/build/outputs/apk/androidTest/hisn/debug/app-hisn-debug-androidTest.apk >/dev/null
# A fresh state for the package under test, so "nothing is running" and "no rules" hold.
"$ADB" -s "$SERIAL" shell pm clear "$PACKAGE" >/dev/null
OUTPUT="$("$ADB" -s "$SERIAL" shell am instrument -w -e package app.hisn.android.nativeshell \
    "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner")"
echo "$OUTPUT"
echo "$OUTPUT" | grep -q "^OK (" || exit 1
