#!/bin/bash
#
# Package the extension into a zip ready for the Chrome Web Store.
#
#     extension/package.sh                 # -> dist/hisn-extension-<version>.zip
#     extension/package.sh --out /tmp/x.zip
#
# WHAT THIS STRIPS, AND WHY EACH ONE MATTERS
# ------------------------------------------
# * `test/`, `*.pem`, `.DS_Store` — never part of the extension. A private key
#   in an upload is the extension handing out its own identity; this is the
#   belt to the suspenders of having moved keys/ out of extension/ already.
#
# * the `key` field — pins the extension id for UNPACKED local loading, which
#   is exactly what native messaging needs during development. The Chrome Web
#   Store assigns its OWN id at first publish and ignores this field, so leaving
#   it in is at best noise and at worst a mismatch. It is stripped from the
#   uploaded copy and kept in the working manifest.
#
#   CONSEQUENCE, STATED LOUDLY: the published extension's id is NOT
#   hfhaffbmoeepcdolgejeidkgaoapcjig. That id belongs to the local key. After
#   the first upload, read the real id from the Web Store dashboard and wire it
#   into BOTH places that hardcode one:
#       - macos/Hisn/NativeMessagingInstaller.swift  (extensionID)
#       - the force-install profile: make_profile.py --extension-id <STORE_ID>
#   Get this wrong and native messaging silently stops: the browser can no
#   longer reach the app, and the extension fails closed to strict.
#
# * `_comment_*` keys — house documentation. Chrome tolerates unknown manifest
#   keys with a warning; Web Store review is stricter and there is no reason to
#   ship a comment. Stripped.
set -euo pipefail

EXT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$EXT_DIR/.." && pwd)"
VERSION="$(python3 -c "import json;print(json.load(open('$EXT_DIR/manifest.json'))['version'])")"
OUT="$REPO/dist/hisn-extension-$VERSION.zip"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --out) OUT="$2"; shift 2 ;;
        -h|--help) sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
BUILD="$STAGE/extension"
mkdir -p "$BUILD"

# Copy the whole extension, then remove what must not ship. Copy-then-prune
# rather than an allowlist: a new source file is included by default, which is
# the safe direction — a forgotten allowlist entry ships a broken extension,
# a forgotten prune ships a harmless extra file.
cp -R "$EXT_DIR"/. "$BUILD"/
rm -rf "$BUILD/test" "$BUILD/keys" "$BUILD/package.sh"
find "$BUILD" \( -name "*.pem" -o -name ".DS_Store" \) -delete

# Store-clean the manifest: drop `key` and every `_comment_*`.
python3 - "$BUILD/manifest.json" <<'PY'
import json, sys
path = sys.argv[1]
m = json.load(open(path))
stripped = [k for k in list(m) if k == "key" or k.startswith("_comment")]
for k in stripped:
    del m[k]
json.dump(m, open(path, "w"), indent=2, ensure_ascii=False)
print("  stripped from manifest:", ", ".join(stripped) or "(nothing)")
PY

# Fail loudly if anything that must never ship survived.
if find "$BUILD" \( -name "*.pem" -o -path "*/keys/*" -o -path "*/test/*" \) \
        | grep -q .; then
    echo "error: a forbidden file survived staging — refusing to package" >&2
    find "$BUILD" \( -name "*.pem" -o -path "*/keys/*" -o -path "*/test/*" \) >&2
    exit 1
fi
python3 -c "import json;json.load(open('$BUILD/manifest.json'))" \
    || { echo "error: packaged manifest is not valid JSON" >&2; exit 1; }

mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
( cd "$BUILD" && zip -qr -X "$OUT" . )

echo "wrote $OUT"
echo "  version:   $VERSION"
echo "  size:      $(du -h "$OUT" | cut -f1)"
echo "  files:     $(unzip -l "$OUT" | tail -1 | awk '{print $2}')"
echo
echo "Upload it at https://chrome.google.com/webstore/devconsole (one-time \$5)."
echo "After it is published, read the assigned id from the dashboard and put it"
echo "into NativeMessagingInstaller.swift and the force-install profile — the"
echo "local id hfhaffbmoeepcdolgejeidkgaoapcjig does NOT survive publishing."
