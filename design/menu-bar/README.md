# Hisn menu bar mark

The menu bar uses the official shield and rounded fortress gateway as a flat
black silhouette with a transparent gateway cutout. The full colored app icon
continues to be used for the app itself.

`Hisn-menu-bar-source.png` was derived from `../app-icon/Hisn.png` using the
built-in ImageGen tool. The production image set is
`../../macos/Hisn/Assets.xcassets/HisnMenuBar.imageset`, with 20 × 20 and 40 × 40
pixel exports for 1× and 2× displays and template rendering enabled.

`MenuBarIcon` in `StatusMenu.swift` caches two 22 × 20 point template images.
Both draw the same mark; the locked version adds the native `lock.fill` symbol
at the lower right, with transparent clearance around it. The entire image
uses the system's menu bar tint, including selection and light/dark appearances.
The image changes only when the existing lock-status observer changes state.

## Generation prompt

The built-in ImageGen tool used `../app-icon/Hisn.png` as its reference image,
with `transparent_background: true` and this exact prompt:

> Use case: style-transfer. Asset type: production macOS menu bar template icon for Hisn. Input image is the official Hisn app icon and the identity reference. Derive one faithful minimal flat monochrome mark from it: retain the same symmetrical shield silhouette and the distinctive rounded fortress gateway centered inside. Solid opaque BLACK shield, with the full white fortress gateway motif converted into a genuinely TRANSPARENT negative-space cutout. The gateway arch and its two wide vertical pillars must read clearly at 18 pixels; the doorway inside the arch remains filled black as part of the shield. This is a tiny macOS status icon, so use confident broad smooth shapes and simplify all rim and 3D detail. Output one single centered mark on an actually transparent square canvas, tight uniform clear padding of about 6 percent, shield fills about 88 percent of canvas height. Absolutely no white or colored pixels, no background plate, no rounded-square enclosure, no shadows, no glow, no gradients, no texture, no text, no border strokes, no other symbols and no lock badge. Flat solid-black filled silhouette and clear holes only, crisp antialiased edges. Preserve the shield proportions and rounded gateway character of the reference.

`preview.png` shows the two cached images enlarged for review. Both were
rendered with the production `MenuBarIcon` drawing code, without starting a
real lock. Build and development-app launch were verified with
`./script/build_and_run.sh --verify`.

## Export

From the repository root:

```sh
sips -z 20 20 design/menu-bar/Hisn-menu-bar-source.png --out macos/Hisn/Assets.xcassets/HisnMenuBar.imageset/hisn-menu-bar.png
sips -z 40 40 design/menu-bar/Hisn-menu-bar-source.png --out macos/Hisn/Assets.xcassets/HisnMenuBar.imageset/hisn-menu-bar@2x.png
```
