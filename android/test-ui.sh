#!/usr/bin/env bash
# UI and UX tests for the native shell (androidTest/.../nativeshell): tab and row navigation,
# honest home state, TalkBack labels, 48 dp touch targets, and layout at large text and RTL.
# Runs on an EMULATOR only: the tests read and may write the app's real saved rules, so they
# must never run on a phone that is actually protected. Usage:
#   bash android/test-ui.sh [emulator-serial] [play|direct]
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

SERIAL="${1:-$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1; exit}')}"
DISTRIBUTION="${2:-direct}"
if [ -z "$SERIAL" ] || [ "$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != 1 ]; then
    echo "No running emulator (got '${SERIAL}'). Start one, e.g. emulator -avd hisn_ui." >&2
    exit 1
fi

./gradlew -q -PhisnUiTests=true -PhisnDistribution="$DISTRIBUTION" assembleDebug assembleDebugAndroidTest
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$ADB" -s "$SERIAL" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
# A fresh app state, so "nothing is running" and "no rules yet" hold for every run.
"$ADB" -s "$SERIAL" shell pm clear app.hisn.android >/dev/null
OUTPUT="$("$ADB" -s "$SERIAL" shell am instrument -w -e package app.hisn.android.nativeshell \
    app.hisn.android.test/androidx.test.runner.AndroidJUnitRunner)"
echo "$OUTPUT"
echo "$OUTPUT" | grep -q "^OK (" || exit 1
