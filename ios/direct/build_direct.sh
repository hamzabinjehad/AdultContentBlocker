#!/bin/bash
#
# Build the direct (non-App-Store) Hisn iPhone/iPad build.
#
# This is the distribution path the app is allowed to take outside the App
# Store. It produces the same app bundle CI tests, including the DNS filter
# extension and all six Safari blockers, and packages it as an IPA that a
# sideloading tool can sign with the person's own Apple team. Nothing here
# uploads anything, contacts Apple for a profile, or submits to App Review.
#
#   ./ios/direct/build_direct.sh                     # unsigned IPA in dist/ios
#   ./ios/direct/build_direct.sh --adhoc             # ad-hoc signed with entitlements
#   ./ios/direct/build_direct.sh --source-url URL    # also write an AltStore source
#
# The build is unsigned by default: AltStore/SideStore sign it with the
# person's developer certificate, and only a profile that includes the
# capabilities in ios/Hisn/Hisn.entitlements can run the extensions. Read
# docs/IOS_DIRECT.md before publishing a download link.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/dist/ios"
ADHOC=0
SOURCE_URL=""
SOURCE_NAME="Hisn"
while [ $# -gt 0 ]; do
    case "$1" in
        --out) OUT="${2:?--out needs a directory}"; shift 2 ;;
        --adhoc) ADHOC=1; shift ;;
        --source-url) SOURCE_URL="${2:?--source-url needs a URL}"; shift 2 ;;
        --name) SOURCE_NAME="${2:?--name needs a value}"; shift 2 ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

command -v xcodebuild >/dev/null || { echo "xcodebuild not found; install Xcode" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 not found" >&2; exit 1; }

mkdir -p "$OUT"
DERIVED="$OUT/build"
STAGE="$OUT/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE/Payload"

echo "── ios direct: building Release for generic iOS"
xcodebuild build \
    -project "$ROOT/ios/HisnMobile.xcodeproj" \
    -scheme HisnMobile \
    -configuration Release \
    -destination "generic/platform=iOS" \
    -derivedDataPath "$DERIVED" \
    CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
    HISN_PYTHON="${HISN_PYTHON:-$(command -v python3)}" \
    | tail -25

APP="$DERIVED/Build/Products/Release-iphoneos/HisnMobile.app"
[ -d "$APP" ] || { echo "missing built app at $APP" >&2; exit 1; }

echo "── ios direct: checking the extensions and their data"
fail=0
for name in Hisn1 Hisn2 Hisn3 Hisn4 Hisn5 Hisn6 HisnText HisnActivityMonitor HisnDNSProxy; do
    [ -d "$APP/PlugIns/$name.appex" ] || { echo "missing $name.appex" >&2; fail=1; }
done
for name in Hisn1 Hisn2 Hisn3 Hisn4 Hisn5 Hisn6; do
    [ -f "$APP/PlugIns/$name.appex/rules.json" ] || { echo "missing $name rules" >&2; fail=1; }
    [ -f "$APP/PlugIns/$name.appex/rules-metadata.json" ] || { echo "missing $name metadata" >&2; fail=1; }
done
for file in dns-domains.txt dns-host-terms.json dns-metadata.json; do
    [ -f "$APP/PlugIns/HisnDNSProxy.appex/$file" ] || { echo "missing DNS $file" >&2; fail=1; }
done
[ -f "$APP/rules-metadata.json" ] || { echo "missing app rules metadata" >&2; fail=1; }
[ "$fail" -eq 0 ] || { echo "the build is incomplete; not packaging it" >&2; exit 1; }

DNS_COUNT=$(python3 - "$APP/PlugIns/HisnDNSProxy.appex/dns-metadata.json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["count"])
PY
)
echo "   DNS filter rules: $DNS_COUNT domains + adult TLDs + hostname terms"

cp -R "$APP" "$STAGE/Payload/HisnMobile.app"

if [ "$ADHOC" = "1" ]; then
    echo "── ios direct: ad-hoc signing with the app's entitlement requests"
    # Innermost bundles first. This produces a correctly signed bundle for
    # installers that preserve an existing signature (TrollStore and friends);
    # it is not accepted by a stock device without a provisioning profile.
    for name in Hisn1 Hisn2 Hisn3 Hisn4 Hisn5 Hisn6 HisnText; do
        /usr/bin/codesign --force --sign - "$STAGE/Payload/HisnMobile.app/PlugIns/$name.appex"
    done
    /usr/bin/codesign --force --sign - \
        --entitlements "$ROOT/ios/HisnActivityMonitor/HisnActivityMonitor.entitlements" \
        "$STAGE/Payload/HisnMobile.app/PlugIns/HisnActivityMonitor.appex"
    /usr/bin/codesign --force --sign - \
        --entitlements "$ROOT/ios/HisnDNSProxy/HisnDNSProxy.entitlements" \
        "$STAGE/Payload/HisnMobile.app/PlugIns/HisnDNSProxy.appex"
    /usr/bin/codesign --force --sign - \
        --entitlements "$ROOT/ios/HisnShieldConfig/HisnShieldConfig.entitlements" \
        "$STAGE/Payload/HisnMobile.app/PlugIns/HisnShieldConfig.appex"
    /usr/bin/codesign --force --sign - \
        --entitlements "$ROOT/ios/HisnShieldAction/HisnShieldAction.entitlements" \
        "$STAGE/Payload/HisnMobile.app/PlugIns/HisnShieldAction.appex"
    /usr/bin/codesign --force --sign - \
        --entitlements "$ROOT/ios/Hisn/Hisn.entitlements" \
        "$STAGE/Payload/HisnMobile.app"
else
    echo "── ios direct: leaving the bundle unsigned for the sideloading tool"
fi

VERSION=$(/usr/bin/plutil -extract CFBundleShortVersionString raw \
    "$STAGE/Payload/HisnMobile.app/Info.plist")
BUILD=$(/usr/bin/plutil -extract CFBundleVersion raw "$STAGE/Payload/HisnMobile.app/Info.plist")
SUFFIX=""
if [ "$ADHOC" = "1" ]; then SUFFIX="-adhoc"; fi
IPA="$OUT/Hisn-$VERSION-$BUILD$SUFFIX.ipa"
rm -f "$IPA"
( cd "$STAGE" && /usr/bin/zip -qry "$IPA" Payload )
SIZE=$(/usr/bin/stat -f%z "$IPA")
SHA=$(/usr/bin/shasum -a 256 "$IPA" | /usr/bin/awk '{print $1}')

python3 - "$OUT/build-info.json" "$VERSION" "$BUILD" "$IPA" "$SIZE" "$SHA" <<'PY'
import json, sys, datetime
path, version, build, ipa, size, sha = sys.argv[1:7]
json.dump({
    "platform": "ios",
    "version": version,
    "build": build,
    "ipa": ipa,
    "size": int(size),
    "sha256": sha,
    "signed": "adhoc" if "-adhoc" in ipa else "unsigned",
    "builtAt": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
    "bundleIdentifier": "app.hisn.mobile",
}, open(path, "w"), indent=2, sort_keys=True)
PY

echo "   IPA:      $IPA"
echo "   size:     $SIZE bytes"
echo "   sha256:   $SHA"
echo
echo "Not installable as-is on a stock iPhone: the installer must sign it with a"
echo "profile that includes Network Extensions (dns-proxy) and, for the Screen"
echo "Time layers, Family Controls. See docs/IOS_DIRECT.md."

if [ -n "$SOURCE_URL" ]; then
    python3 "$ROOT/ios/direct/make_source.py" --ipa "$IPA" --url "$SOURCE_URL" \
        --name "$SOURCE_NAME" --out "$OUT/source.json"
    echo "   AltStore source: $OUT/source.json"
fi
