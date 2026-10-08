#!/bin/bash
# Build a Developer ID app and installer. Nothing is installed on this Mac.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:---check}"
if [ "$#" -gt 0 ]; then shift; fi
case "$MODE" in
    --check|--build|--smoke|--notarize) ;;
    --help|-h)
        printf '%s\n' 'Usage: bash macos/release/release.sh --check|--build|--smoke|--notarize [output-directory]' \
            '--build: HISN_TEAM_ID and HISN_INSTALLER_IDENTITY must be set.' \
            '--smoke: build and inspect an unsigned test package; never install or distribute it.' \
            '--notarize: HISN_NOTARY_PROFILE must name credentials already stored in Keychain.'
        exit 0 ;;
    *) echo "Unknown mode: $MODE" >&2; exit 2 ;;
esac
if [ "$MODE" = --check ]; then
    python3 "$ROOT/macos/release/check_release.py"
    exit
fi
OUTPUT="${1:-$ROOT/dist/release}"
mkdir -p "$OUTPUT"
OUTPUT="$(cd "$OUTPUT" && pwd)"
if [ "$MODE" = --notarize ]; then
    : "${HISN_NOTARY_PROFILE:?Set HISN_NOTARY_PROFILE to your Keychain credential profile}"
    PACKAGE="$OUTPUT/Hisn.pkg"
    [ -f "$PACKAGE" ] || { echo "Build Hisn.pkg first" >&2; exit 1; }
    pkgutil --check-signature "$PACKAGE"
    xcrun notarytool submit "$PACKAGE" --keychain-profile "$HISN_NOTARY_PROFILE" \
        --wait --output-format json > "$OUTPUT/notarization.json"
    python3 -c 'import json,sys; r=json.load(open(sys.argv[1])); sys.exit(0 if r.get("status")=="Accepted" else "Notarization did not accept the package; inspect notarization.json")' "$OUTPUT/notarization.json"
    xcrun stapler staple "$PACKAGE"
    xcrun stapler validate "$PACKAGE"
    spctl --assess --type install --verbose=2 "$PACKAGE"
    echo "Notarized installer: $PACKAGE"
    exit
fi

if [ "$MODE" = --build ]; then
    if [ -e "$OUTPUT/Hisn.pkg" ] || [ -e "$OUTPUT/Hisn.xcarchive" ]; then
        echo "Use a fresh output directory to preserve the previous signed candidate." >&2
        exit 1
    fi
    python3 "$ROOT/macos/release/check_release.py" --public
    : "${HISN_TEAM_ID:?Set HISN_TEAM_ID to your Apple Developer team}"
    : "${HISN_INSTALLER_IDENTITY:?Set HISN_INSTALLER_IDENTITY to your Developer ID Installer certificate name}"
    [[ "$HISN_TEAM_ID" =~ ^[A-Z0-9]{10}$ ]] || { echo "Invalid Apple team ID" >&2; exit 2; }
    [[ "$HISN_INSTALLER_IDENTITY" == "Developer ID Installer:"* ]] \
        || { echo "Use a Developer ID Installer certificate" >&2; exit 2; }
else
    python3 "$ROOT/macos/release/check_release.py"
fi

# A fresh staging directory prevents a failed build from reusing an old app.
STAGE="$(mktemp -d "$OUTPUT/.hisn-release.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT
if [ "$MODE" = --smoke ]; then
    xcodebuild -project "$ROOT/macos/Hisn.xcodeproj" -scheme Hisn \
        -configuration DeveloperID -destination 'generic/platform=macOS' \
        -derivedDataPath "$STAGE/build" build CODE_SIGNING_ALLOWED=NO 'ARCHS=arm64 x86_64' \
        | tee "$OUTPUT/smoke-build.log"
    APP="$STAGE/build/Build/Products/DeveloperID/Hisn.app"
    python3 "$ROOT/macos/release/check_release.py" --app "$APP" --unsigned
else
    xcodebuild -project "$ROOT/macos/Hisn.xcodeproj" -scheme Hisn \
    -configuration DeveloperID -destination 'generic/platform=macOS' \
    -archivePath "$STAGE/Hisn.xcarchive" archive \
    DEVELOPMENT_TEAM="$HISN_TEAM_ID" 'ARCHS=arm64 x86_64' -allowProvisioningUpdates \
    | tee "$OUTPUT/archive.log"

python3 -c 'import plistlib,sys; plistlib.dump({"method":"developer-id", "teamID":sys.argv[2], "signingStyle":"automatic"}, open(sys.argv[1], "wb"))' \
    "$STAGE/ExportOptions.plist" "$HISN_TEAM_ID"
xcodebuild -exportArchive -archivePath "$STAGE/Hisn.xcarchive" \
    -exportPath "$STAGE/export" -exportOptionsPlist "$STAGE/ExportOptions.plist" \
    -allowProvisioningUpdates | tee "$OUTPUT/export.log"
APP="$STAGE/export/Hisn.app"
python3 "$ROOT/macos/release/check_release.py" --app "$APP" --public
fi

mkdir -p "$STAGE/scripts"
cp "$ROOT/macos/release/installer/preinstall" "$STAGE/scripts/preinstall"
cp "$ROOT/macos/release/installer/postinstall" "$STAGE/scripts/postinstall"
cp "$ROOT/macos/install_native_host.sh" "$STAGE/scripts/install_native_host.sh"
chmod 755 "$STAGE/scripts/"*
VERSION=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist")
mkdir -p "$STAGE/payload/Applications"
ditto "$APP" "$STAGE/payload/Applications/Hisn.app"
pkgbuild --root "$STAGE/payload" --install-location / \
    --component-plist "$ROOT/macos/release/Components.plist" \
    --identifier app.hisn.installer --version "$VERSION" \
    --scripts "$STAGE/scripts" --ownership recommended \
    "$STAGE/Hisn-component.pkg"
if [ "$MODE" = --smoke ]; then
    pkgutil --expand-full "$STAGE/Hisn-component.pkg" "$STAGE/expanded"
    python3 "$ROOT/macos/release/check_release.py" \
        --app "$STAGE/expanded/Payload/Applications/Hisn.app" --unsigned
    mv "$STAGE/Hisn-component.pkg" "$OUTPUT/Hisn-UNSIGNED-TEST.pkg"
    echo "Unsigned package structure passed. This artifact is for testing only."
    exit
fi
productbuild --package "$STAGE/Hisn-component.pkg" \
    --sign "$HISN_INSTALLER_IDENTITY" "$STAGE/Hisn.pkg"
pkgutil --check-signature "$STAGE/Hisn.pkg"
mv "$STAGE/Hisn.xcarchive" "$OUTPUT/Hisn.xcarchive"
mv "$STAGE/Hisn.pkg" "$OUTPUT/Hisn.pkg"
echo "Signed installer: $OUTPUT/Hisn.pkg"
echo "Next: --notarize, then validate installation on a clean Mac."
