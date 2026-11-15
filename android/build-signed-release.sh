#!/usr/bin/env bash
# Builds the signed Play and direct Android artifacts from a clean commit, checks them, and
# copies them with their hashes to dist/android-<version>/. Needs the owner's key in
# keys/android/ (gitignored). Usage, from the repository root or android/:
#   bash android/build-signed-release.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EXPECTED_CERT="2fbed657e6abc71c15cad0cd991c532f59772d1695d7c37e65a114385e9470c5"

# HISN_ANDROID_KEYS_DIR lets a clean second checkout (a git worktree) sign with the key
# where it already lives, so the key is never copied into another tree.
KEYS_DIR="${HISN_ANDROID_KEYS_DIR:-$ROOT/keys/android}"
KEYSTORE="$KEYS_DIR/hisn-release.jks"
PASSWORD_FILE="$KEYS_DIR/keystore-password.txt"
[ -f "$KEYSTORE" ] && [ -f "$PASSWORD_FILE" ] || { echo "Missing key files in $KEYS_DIR." >&2; exit 1; }

# A release comes from a commit, so the APK can be rebuilt and traced.
if [ -n "$(git status --porcelain)" ]; then
    echo "The working tree has uncommitted changes; commit or stash them first." >&2
    git status --short >&2
    exit 1
fi
COMMIT="$(git rev-parse --short HEAD)"

VERSION="$(sed -n "s/^ *versionName '\(.*\)'/\1/p" android/app/build.gradle | head -1)"
CODE="$(sed -n 's/^ *versionCode \([0-9]*\)/\1/p' android/app/build.gradle | head -1)"
OUT="$ROOT/dist/android-$VERSION"
if [ -e "$OUT" ]; then
    echo "$OUT already exists; move it away before rebuilding." >&2
    exit 1
fi

export HISN_ANDROID_KEYSTORE="$KEYSTORE"
HISN_ANDROID_KEYSTORE_PASSWORD="$(cat "$PASSWORD_FILE")"
export HISN_ANDROID_KEYSTORE_PASSWORD
export HISN_ANDROID_KEY_ALIAS=hisn
export HISN_ANDROID_KEY_PASSWORD="$HISN_ANDROID_KEYSTORE_PASSWORD"

BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
APKSIGNER="$BUILD_TOOLS/apksigner"

echo "== Gates (lint, unit and core tests, strings)"
(cd android && ./gradlew -q --no-daemon lintDebug testDebugUnitTest)
(cd android && bash test-core.sh >/dev/null)
python3 android/check-strings.py

mkdir -p "$OUT/play" "$OUT/direct"
APK=android/app/build/outputs/apk/release/app-release.apk

check_apk() {  # $1 apk, $2 play|direct
    python3 android/check-release.py --build-tools "$BUILD_TOOLS" --expect-distribution "$2" "$1"
    local cert
    cert="$("$APKSIGNER" verify --print-certs "$1" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
    if [ "$cert" != "$EXPECTED_CERT" ]; then
        echo "Signed with the wrong certificate: $cert" >&2
        exit 1
    fi
}

echo "== Play build"
(cd android && ./gradlew -q --no-daemon clean assembleRelease bundleRelease)
check_apk "$APK" play
cp "$APK" "$OUT/play/hisn-$VERSION-play.apk"
cp android/app/build/outputs/bundle/release/app-release.aab "$OUT/play/hisn-$VERSION-play.aab"

echo "== Direct build"
(cd android && ./gradlew -q --no-daemon clean assembleRelease -PhisnDistribution=direct)
check_apk "$APK" direct
cp "$APK" "$OUT/direct/hisn-$VERSION-direct.apk"

(cd "$OUT" && shasum -a 256 direct/*.apk play/*.apk play/*.aab > SHA256SUMS)
cat > "$OUT/BUILD.txt" <<EOF
Hisn Android $VERSION ($CODE), built $(date -u +%Y-%m-%dT%H:%MZ) from commit $COMMIT.
Certificate SHA-256: $EXPECTED_CERT
Checked: lint, unit and core tests, string parity, check-release.py and apksigner for both builds.
Not checked by this script: the phone acceptance checklist in docs/ANDROID_1.6.0_RELEASE_CHECKLIST.md.
EOF

echo
echo "Done: $OUT"
cat "$OUT/SHA256SUMS"
