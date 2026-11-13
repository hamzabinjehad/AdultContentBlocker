#!/usr/bin/env python3
"""Hisn fortress-skull mark: single source of truth for every app icon.

The mark is drawn in a 108 x 108 canvas (Android adaptive-icon units):
  base - the battlement shield (ink)
  cut  - the skull, knocked out of the shield (paper); jaw notches form teeth
  back - eye sockets and nose, drawn back in ink

Running this script writes:
  design/app-icon/hisn-mark.svg             vector master (light colours)
  design/app-icon/Hisn-source.png           1024 light plate (full bleed)
  design/app-icon/Hisn-dark-source.png      1024 dark plate (full bleed)
  macos/Hisn/AppIcon.icon/Assets/*.png      the two plates for Icon Composer
  ios/.../AppIcon.appiconset/Hisn1024.png   opaque light plate
  android drawable/*.xml                    launcher, monochrome, splash, brand vectors
  android mipmap-*/ic_launcher(_round).png  pre-Android 8 launcher icons
  extension/icons/icon-*.png                tightly cropped toolbar icons

Requires Pillow. Usage: python3 design/app-icon/hisn_mark.py
"""
import os
import re
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

BASE = ['M28 24 H37 V29 H45.5 V24 H62.5 V29 H71 V24 H80 V53 '
        'C80 70.5 69.5 81.5 54 88 C38.5 81.5 28 70.5 28 53 Z']
CUT = ['M54 37 C64 37 71 43.6 71 53 C71 58.6 68.8 62.2 65.4 64 L65.4 72 '
       'Q65.4 74 63.4 74 L59.2 74 L59.2 68 L56.8 68 L56.8 74 L51.2 74 L51.2 68 '
       'L48.8 68 L48.8 74 L44.6 74 Q42.6 74 42.6 72 L42.6 64 '
       'C39.2 62.2 37 58.6 37 53 C37 43.6 44 37 54 37 Z']
BACK = [
    'M40.6 51 C42.2 48.6 46 48.4 48 50.2 C49.4 51.6 48.8 54.6 46.8 56.2 '
    'C44.8 57.8 41.8 57.4 40.8 55.2 C40.2 53.8 40 52 40.6 51 Z',
    'M67.4 51 C65.8 48.6 62 48.4 60 50.2 C58.6 51.6 59.2 54.6 61.2 56.2 '
    'C63.2 57.8 66.2 57.4 67.2 55.2 C67.8 53.8 68 52 67.4 51 Z',
    'M54 58.6 L56.4 63.4 Q54 64.6 51.6 63.4 Z',
]
# Bounding box of the mark: x 28..80, y 24..88, centre (54, 56).
MARK_CX, MARK_CY = 54, 56

NAVY, PEARL, CHARCOAL, BONE = '#1F3A5F', '#F4F1EA', '#121722', '#E9E4D8'
LIGHT = dict(bg=PEARL, ink=NAVY, paper=PEARL)
DARK = dict(bg=CHARCOAL, ink=BONE, paper=CHARCOAL)

# Android shows the central 72 dp of the 108 dp layer and guarantees a
# 66 dp circle; 0.8 keeps the battlement corners inside that circle.
ANDROID_SCALE = 0.8
# Apple plates: Icon Composer adds the enclosure; the mark fills ~65%.
APPLE_SCALE = 1.08


# ---------------------------------------------------------------- geometry
TOKEN = re.compile(r'[MLHVCQZmlhvcqz]|-?\d*\.?\d+')


def flatten(d, steps=32):
    """SVG path (M L H V C Q Z, h v) -> list of polygons."""
    polys, cur = [], []
    x = y = sx = sy = 0.0
    toks = TOKEN.findall(d)
    i, cmd = 0, None

    def num():
        nonlocal i
        i += 1
        return float(toks[i - 1])

    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
        if cmd in 'Zz':
            if cur:
                polys.append(cur)
            cur, x, y, cmd = [], sx, sy, None
        elif cmd == 'M':
            if cur:
                polys.append(cur)
            x, y = num(), num()
            sx, sy, cur, cmd = x, y, [(x, y)], 'L'
        elif cmd == 'L':
            x, y = num(), num()
            cur.append((x, y))
        elif cmd in 'Hh':
            x = num() + (x if cmd == 'h' else 0)
            cur.append((x, y))
        elif cmd in 'Vv':
            y = num() + (y if cmd == 'v' else 0)
            cur.append((x, y))
        elif cmd == 'C':
            x1, y1, x2, y2, x3, y3 = (num() for _ in range(6))
            for k in range(1, steps + 1):
                t = k / steps
                u = 1 - t
                cur.append((u**3 * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * x3,
                            u**3 * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * y3))
            x, y = x3, y3
        elif cmd == 'Q':
            x1, y1, x2, y2 = (num() for _ in range(4))
            for k in range(1, steps + 1):
                t = k / steps
                u = 1 - t
                cur.append((u * u * x + 2 * u * t * x1 + t * t * x2,
                            u * u * y + 2 * u * t * y1 + t * t * y2))
            x, y = x2, y2
        else:
            raise ValueError(f'unsupported path command {cmd!r}')
    if cur:
        polys.append(cur)
    return polys


def transform(scale, cx=54.0, cy=54.0):
    """Scale the mark about its own centre and place that centre at (cx, cy)."""
    return lambda p: (cx + (p[0] - MARK_CX) * scale, cy + (p[1] - MARK_CY) * scale)


# ---------------------------------------------------------------- raster
SUPERSAMPLE = 4


def render(px, pal=LIGHT, scale=1.0, view=(0, 0, 108, 108), background=True,
           mask=None, mono=None):
    """Render the mark. view = (x, y, w, h) window of the 108 canvas.
    mono = colour for a single silhouette (cut-outs become transparent)."""
    size = px * SUPERSAMPLE
    vx, vy, vw, vh = view
    place = transform(scale)

    def to_px(p):
        x, y = place(p)
        return ((x - vx) / vw * size, (y - vy) / vh * size)

    def draw(dr, paths, colour):
        for d in paths:
            for poly in flatten(d):
                dr.polygon([to_px(p) for p in poly], fill=colour)

    silhouette = Image.new('L', (size, size), 0)
    sd = ImageDraw.Draw(silhouette)
    draw(sd, BASE, 255)
    draw(sd, CUT, 0)
    draw(sd, BACK, 255)

    if mono:
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        img.paste(Image.new('RGBA', (size, size), mono), (0, 0), silhouette)
    else:
        img = Image.new('RGBA', (size, size), pal['bg'] if background else (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        draw(dr, BASE, pal['ink'])
        draw(dr, CUT, pal['paper'])
        draw(dr, BACK, pal['ink'])
        if not background:
            # keep only the shield footprint; the skull stays paper-coloured
            shield = Image.new('L', (size, size), 0)
            draw(ImageDraw.Draw(shield), BASE, 255)
            img.putalpha(shield)

    if mask:
        m = Image.new('L', (size, size), 0)
        md = ImageDraw.Draw(m)
        if mask == 'circle':
            md.ellipse((0, 0, size - 1, size - 1), fill=255)
        else:
            md.rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.2), fill=255)
        out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        out.paste(img, (0, 0), m)
        img = out
    return img.resize((px, px), Image.LANCZOS)


# ---------------------------------------------------------------- vectors
def svg(pal=LIGHT, size=1024):
    paths = (''.join(f'<path d="{d}" fill="{pal["ink"]}"/>' for d in BASE) +
             ''.join(f'<path d="{d}" fill="{pal["paper"]}"/>' for d in CUT) +
             ''.join(f'<path d="{d}" fill="{pal["ink"]}"/>' for d in BACK))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="24 20 60 72" '
            f'width="{size * 60 // 72}" height="{size}">\n'
            f'<!-- Generated by design/app-icon/hisn_mark.py -->\n{paths}\n</svg>\n')


def vector_drawable(comment, layers, scale, viewport=108, size_dp=108, offset=(0, 0)):
    """layers: list of (path list, colour, even_odd)."""
    tx = viewport / 2 - MARK_CX * scale + offset[0]
    ty = viewport / 2 - MARK_CY * scale + offset[1]
    body = []
    for paths, colour, even_odd in layers:
        fill_type = '\n            android:fillType="evenOdd"' if even_odd else ''
        body.append(f'        <path\n            android:fillColor="{colour}"{fill_type}\n'
                    f'            android:pathData="{" ".join(paths)}" />')
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} Generated by design/app-icon/hisn_mark.py. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp" android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
            f'    <group\n        android:scaleX="{scale:g}" android:scaleY="{scale:g}"\n'
            f'        android:translateX="{tx:g}" android:translateY="{ty:g}">\n'
            + '\n'.join(body) + '\n    </group>\n</vector>\n')


def colour_layers(pal):
    return [(BASE, pal['ink'], False), (CUT, pal['paper'], False), (BACK, pal['ink'], False)]


# One even-odd path: shield (fill) > skull (hole) > eyes and nose (fill).
SILHOUETTE = [(BASE + CUT + BACK, '#FFFFFFFF', True)]


def write(path, text):
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(text)


def save(img, path, opaque=False):
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    (img.convert('RGB') if opaque else img).save(path, optimize=True)


def main():
    # Masters and Apple plates.
    write('design/app-icon/hisn-mark.svg', svg())
    light = render(1024, LIGHT, APPLE_SCALE)
    dark = render(1024, DARK, APPLE_SCALE)
    save(light, 'design/app-icon/Hisn-source.png', opaque=True)
    save(dark, 'design/app-icon/Hisn-dark-source.png', opaque=True)
    save(light, 'macos/Hisn/AppIcon.icon/Assets/hisn-icon-source-1024.png', opaque=True)
    save(dark, 'macos/Hisn/AppIcon.icon/Assets/hisn-icon-dark-1024.png', opaque=True)
    save(light, 'ios/Hisn/Assets.xcassets/AppIcon.appiconset/Hisn1024.png', opaque=True)

    # Android adaptive-icon layers and in-app marks (vectors).
    res = 'android/app/src/main/res/'
    write(res + 'drawable/ic_launcher_foreground.xml', vector_drawable(
        'Launcher foreground: navy fortress shield, pearl skull.',
        colour_layers(dict(ink=NAVY, paper=PEARL)), ANDROID_SCALE))
    write(res + 'drawable/ic_launcher_monochrome.xml', vector_drawable(
        'Themed-icon layer: one silhouette, the skull is a hole.',
        SILHOUETTE, ANDROID_SCALE))
    write(res + 'drawable/ic_splash_mark.xml', vector_drawable(
        'Android 12 splash: pearl shield on the navy splash background.',
        colour_layers(dict(ink=PEARL, paper=NAVY)), ANDROID_SCALE))
    # Brand mark is tinted in code, so it is a silhouette cropped to the mark.
    write(res + 'drawable/ic_brand_mark.xml', vector_drawable(
        'In-app brand mark, tinted at runtime: one silhouette cropped to the shield.',
        SILHOUETTE, 1.0, viewport=68, size_dp=24))

    # Pre-Android 8 launcher icons: the adaptive icon's visible 72 dp window.
    for folder, px in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
        window = (18, 18, 72, 72)
        save(render(px, LIGHT, ANDROID_SCALE, window, mask='rounded'),
             f'{res}mipmap-{folder}/ic_launcher.png')
        save(render(px, LIGHT, ANDROID_SCALE, window, mask='circle'),
             f'{res}mipmap-{folder}/ic_launcher_round.png')

    # Browser toolbar: no plate, the shield fills the square.
    for px in (16, 32, 48, 128):
        save(render(px, LIGHT, 1.0, (54 - 34, 56 - 34, 68, 68), background=False),
             f'extension/icons/icon-{px}.png')
    print('Exported the Hisn mark to Apple, Android and extension assets.')


if __name__ == '__main__':
    main()
