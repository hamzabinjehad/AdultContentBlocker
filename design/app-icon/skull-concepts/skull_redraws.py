import sys
sys.path.insert(0, '/Users/x/Desktop/GitHub/AdultContentBlocker/design/app-icon')
import hisn_mark as h
from PIL import Image, ImageDraw

def circle(cx, cy, r):
    k = 0.5523 * r
    return (f'M{cx-r} {cy} C{cx-r} {cy-k} {cx-k} {cy-r} {cx} {cy-r} '
            f'C{cx+k} {cy-r} {cx+r} {cy-k} {cx+r} {cy} '
            f'C{cx+r} {cy+k} {cx+k} {cy+r} {cx} {cy+r} '
            f'C{cx-k} {cy+r} {cx-r} {cy+k} {cx-r} {cy} Z')

def teeth(xs, top, bottom, w):
    """Bottom-edge notches, right to left, as path segments."""
    seg = ''
    for x in sorted(xs, reverse=True):
        seg += f' L{x+w/2} {bottom} L{x+w/2} {top} L{x-w/2} {top} L{x-w/2} {bottom}'
    return seg

OPTIONS = {
    'Current': (h.CUT, h.BACK),
    # 1. Anatomical: cheekbones flare out, narrower jaw, big tilted sockets.
    '1 Anatomical': (
        ['M54 35 C64.6 35 72 42.4 72 52.4 C72 57.6 70 61 67 63 L64 64.6 L64 76.5'
         + teeth([49, 54, 59], 70.5, 76.5, 1.8) +
         ' L44 76.5 L44 64.6 L41 63 C38 61 36 57.6 36 52.4 C36 42.4 43.4 35 54 35 Z'],
        ['M39.5 50 C40.5 47 45 46.4 48.6 47.6 C51 48.4 51.6 51 50.8 53.8 C49.8 57.2 46.4 59 43.2 58.4 C40 57.8 38.6 53.4 39.5 50 Z',
         'M68.5 50 C67.5 47 63 46.4 59.4 47.6 C57 48.4 56.4 51 57.2 53.8 C58.2 57.2 61.6 59 64.8 58.4 C68 57.8 69.4 53.4 68.5 50 Z',
         'M54 60 C55 61.5 56.6 64 56.2 65.2 C55.8 66.2 54.6 66 54 65.2 C53.4 66 52.2 66.2 51.8 65.2 C51.4 64 53 61.5 54 60 Z'],
    ),
    # 2. Geometric: flat-sided cranium, round sockets, even teeth.
    '2 Geometric': (
        ['M54 35 C64 35 71 41.5 71 51 L71 58 Q71 62 67 62 L65 62 L65 72 Q65 76 61 76'
         + teeth([50.2, 54, 57.8], 67, 76, 1.3) +
         ' L47 76 Q43 76 43 72 L43 62 L41 62 Q37 62 37 58 L37 51 C37 41.5 44 35 54 35 Z'],
        [circle(46.2, 51.8, 5), circle(61.8, 51.8, 5), 'M54 58.4 L56.2 63.2 L51.8 63.2 Z'],
    ),
    # 3. Bold: skull fills the shield, heavy brow sockets, wide jaw.
    '3 Bold': (
        ['M54 33 C66 33 74.5 41 74.5 52 C74.5 58.5 71.8 62.4 67.5 64.4 L67.5 78'
         + teeth([48.5, 54, 59.5], 71, 78, 2) +
         ' L40.5 78 L40.5 64.4 C36.2 62.4 33.5 58.5 33.5 52 C33.5 41 42 33 54 33 Z'],
        ['M38.5 49 L50 48.5 C51.6 48.5 52 50 51.4 52.4 C50.4 56.4 47.6 59 44.4 59 C40.6 59 38 55.6 38.5 49 Z',
         'M69.5 49 L58 48.5 C56.4 48.5 56 50 56.6 52.4 C57.6 56.4 60.4 59 63.6 59 C67.4 59 70 55.6 69.5 49 Z',
         'M54 61 L57 67 L51 67 Z'],
    ),
    # 4. No skull: an arched fortress gate with a raised portcullis.
    '4 Gate (no skull)': (
        ['M44 79 L44 55 C44 48.4 48.4 44 54 44 C59.6 44 64 48.4 64 55 L64 79 Z'],
        ['M48.6 46.2 h1.8 V74 h-1.8 Z', 'M53.1 44.6 h1.8 V74 h-1.8 Z', 'M57.6 46.2 h1.8 V74 h-1.8 Z',
         'M45.2 56 H62.8 V57.8 H45.2 Z'],
    ),
}

CELL, PAD, LBL = 200, 20, 140
W = LBL + 3 * (CELL + PAD) + 330
H = PAD + len(OPTIONS) * (CELL + 30)
sheet = Image.new('RGBA', (W, H), '#FBFAF7')
d = ImageDraw.Draw(sheet)
for r, (name, (cut, back)) in enumerate(OPTIONS.items()):
    h.CUT, h.BACK = cut, back
    y = PAD + r * (CELL + 30)
    d.text((PAD, y + CELL // 2), name, fill='#1b2230')
    tiles = [h.render(CELL, h.LIGHT, h.APPLE_SCALE, mask='rounded'),
             h.render(CELL, h.DARK, h.APPLE_SCALE, mask='rounded'),
             h.render(CELL, mono='#2A4A7A', scale=h.APPLE_SCALE)]
    for i, t in enumerate(tiles):
        if i == 2:
            bg = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
            ImageDraw.Draw(bg).rounded_rectangle((0, 0, CELL - 1, CELL - 1), 40, fill='#DDE6F3')
            bg.alpha_composite(t); t = bg
        sheet.alpha_composite(t, (LBL + i * (CELL + PAD), y))
    x = LBL + 3 * (CELL + PAD)
    for px in (64, 32, 16):
        t = h.render(px, h.LIGHT, 1.0, (20, 22, 68, 68), background=False)
        sheet.alpha_composite(t, (x, y + 70)); x += px + 24
    z = h.render(16, h.LIGHT, 1.0, (20, 22, 68, 68), background=False).resize((96, 96), Image.NEAREST)
    sheet.alpha_composite(z, (x, y + 50))
sheet.save(sys.argv[1])
