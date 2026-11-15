#!/usr/bin/env bash
# Builds a deployable copy of the static site with the signed APK staged.
# The output is dist/hisn-website-<date>/ (and a zip when `zip` is available).
set -euo pipefail
cd "$(dirname "$0")"

bash stage-downloads.sh

stamp="$(date +%Y-%m-%d)"
out="../dist/hisn-website-$stamp"
case "$out" in
  */dist/hisn-website-*) ;;
  *) echo "refusing to write outside dist/hisn-website-*: $out" >&2; exit 1 ;;
esac
rm -rf "$out"
mkdir -p "$out/downloads"
cp index.html vault.html partner.html privacy.html limits.html "$out"/
cp -R assets lib "$out"/
# Keep the vendor notice inside the source tree; the public page needs only the
# runtime and its license. The checksum file is for the release check, not the
# visitor.
rm -f "$out/lib/vendor/README.md"
cp downloads/*.apk "$out/downloads/"

if command -v zip >/dev/null 2>&1; then
  rm -f "../dist/hisn-website-$stamp.zip"
  (cd ../dist && zip -qr "hisn-website-$stamp.zip" "hisn-website-$stamp")
  echo "zip: dist/hisn-website-$stamp.zip"
fi
echo "built: $out"
echo "preview: python3 -m http.server 8766 --directory $out"
