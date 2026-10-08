#!/bin/bash
# Render the native Apple icon, appearance previews, and fallback/browser PNGs.
# Usage: bash design/app-icon/export.sh [path-to-square-master.png]
set -euo pipefail

DESIGN_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$DESIGN_DIR/../.." && pwd)"
MASTER_ICON="${1:-$DESIGN_DIR/Hisn.png}"
APPICON_DIR="$REPO_DIR/macos/Hisn/Assets.xcassets/AppIcon.appiconset"
BROWSER_ICON_DIR="$REPO_DIR/extension/icons"
ICON_DOCUMENT="$REPO_DIR/macos/Hisn/AppIcon.icon"
APPEARANCE_DIR="$DESIGN_DIR/appearances"
ICON_EXPORTER=""

# A supplied PNG skips native rendering. Without one, use Icon Composer when
# available; the committed PNG also supports machines without the exporter.
if [[ $# == 0 ]] && [[ -d "$ICON_DOCUMENT" ]] && command -v xcode-select >/dev/null; then
    XCODE_DEVELOPER_DIR="$(xcode-select -p)"
    CANDIDATE_EXPORTER="$(dirname "$XCODE_DEVELOPER_DIR")/Applications/Icon Composer.app/Contents/Executables/ictool"
    if [[ -x "$CANDIDATE_EXPORTER" ]]; then
        ICON_EXPORTER="$CANDIDATE_EXPORTER"
        "$ICON_EXPORTER" "$ICON_DOCUMENT" --export-image \
            --output-file "$MASTER_ICON" --platform macOS --rendition Default \
            --width 1024 --height 1024 --scale 1
    fi
fi

if [[ ! -f "$MASTER_ICON" ]]; then
    echo "Missing master icon: $MASTER_ICON" >&2
    exit 1
fi
mkdir -p "$APPICON_DIR" "$BROWSER_ICON_DIR"

for point_size in 16 32 128 256 512; do
    for scale in 1 2; do
        pixel_size=$((point_size * scale))
        suffix=""
        if [[ "$scale" == 2 ]]; then suffix="@2x"; fi
        sips -z "$pixel_size" "$pixel_size" "$MASTER_ICON" \
            --out "$APPICON_DIR/icon_${point_size}x${point_size}${suffix}.png" >/dev/null
    done
done

for pixel_size in 16 32 48 128; do
    sips -z "$pixel_size" "$pixel_size" "$MASTER_ICON" \
        --out "$BROWSER_ICON_DIR/icon-${pixel_size}.png" >/dev/null
done

# These PNGs are review previews. The native .icon document, compiled by Xcode,
# supplies the adaptive appearances in the app; browsers use the default PNGs.
if [[ -n "$ICON_EXPORTER" ]]; then
    mkdir -p "$APPEARANCE_DIR"
    sips -z 512 512 "$MASTER_ICON" --out "$APPEARANCE_DIR/Hisn-default.png" >/dev/null
    for rendition in Dark ClearLight ClearDark; do
        case "$rendition" in
            Dark) preview_name=dark ;;
            ClearLight) preview_name=clear-light ;;
            ClearDark) preview_name=clear-dark ;;
        esac
        "$ICON_EXPORTER" "$ICON_DOCUMENT" --export-image \
            --output-file "$APPEARANCE_DIR/Hisn-${preview_name}.png" \
            --platform macOS --rendition "$rendition" \
            --width 512 --height 512 --scale 1
    done
    for appearance in Light Dark; do
        if [[ "$appearance" == Light ]]; then preview_name=light; else preview_name=dark; fi
        "$ICON_EXPORTER" "$ICON_DOCUMENT" --export-image \
            --output-file "$APPEARANCE_DIR/Hisn-tinted-blue-${preview_name}.png" \
            --platform macOS --rendition "Tinted${appearance}" \
            --width 512 --height 512 --scale 1 \
            --tint-color 0.60 --tint-strength 0.75
    done
    echo "Exported native appearance previews to $APPEARANCE_DIR"
fi
echo "Exported macOS and browser icons from $MASTER_ICON"
