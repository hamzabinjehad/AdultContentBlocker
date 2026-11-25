# Unrot trees: one set for Android, iPhone and Mac (24 Nov 2026)

Twelve painted trees in one style, each on its own patch of ground, cut out on a clear background (360 px square, tree standing on the bottom edge). Painted as one sheet with Figma's image tool (file "Unrot trees"), lifted off the background with the Mac's subject mask. Source sheet: `../unrot-phone-prototype-2026-11-24/trees-src/sheet.png`.

| File | What it is | Used for |
|---|---|---|
| oak | round green oak | a clean day, from day 1 |
| pine | layered pine | earned at 7 clean days |
| cherry | cherry in blossom | earned at 14 |
| lemon | lemon tree with fruit | earned at 21 |
| olive | olive with a twisted trunk | earned at 30 |
| maple | autumn maple | earned at 45 |
| willow | weeping willow | grows where a dry tree turned to soil |
| winter | pine under snow | pines in winter |
| sapling | young sapling | today's plant, under the glass |
| gold | oak with a gold ring | a day a page was stopped and the crack sealed |
| dry | bare tree on cracked soil | a day adult content got through |
| soil | mound of soil with a sprout | a dry tree after 7 clean days; the seed stage |

## Where they go, same names as the glass paintings already in each app
- **Android:** copy `android/drawable-nodpi/unrot_tree_*.webp` to `android/app/src/main/res/drawable-nodpi/` (next to `unrot_glass_*.webp`).
- **iPhone:** copy `ios/UnrotArt.xcassets/tree_*.imageset` into `ios/Unrot/UnrotArt.xcassets/` (next to `glass_*.imageset`), then regenerate the project.
- **Mac:** copy `macos/Assets.xcassets/UnrotTree*.imageset` into `macos/Hisn/Assets.xcassets/` (next to `UnrotGlass*.imageset`).

Nothing in the apps uses them yet. They are not copied into the apps: that goes with building the wood on each platform.

## Known limits
- One size (360 px). Sharp up to about 120 pt on a 3x phone; a larger hero needs a higher-resolution repaint.
- Still images. Motion in the prototype is code (sway from the root, pop in, glow under the gold-ring tree).
- No night or dawn versions; the prototype dims the sapling for quiet hours with a filter.
