# Hisn shield — the protection shape, with the head

Direction dated 2026-11-11. The shield (درع) is the app's existing protection
symbol; the head from the supplied artwork goes inside it. Flat, two colours,
sized for Android.

## What changed

- Kept the shield silhouette and the skull. The head is one paper cut-out in the
  navy shield, so the jaw and the shield's base read as one shape.
- Removed the gloss, bevel, drop shadow, white sticker outline and the 86% fill
  that Android's launcher mask crops.
- Two colours only: navy `#1F3A5F` on paper `#F4F1EA`.
- The mark is 62% of the 108 dp canvas, inside the 66 dp safe circle.
- Arabic **حصن** is the primary wordmark; **Hisn** is the Latin counterpart.
  Type: Noto Naskh Arabic / Noto Serif.
- The icon is language-neutral; the wordmark carries the Arabic name.

## Files

| File | Use |
| --- | --- |
| `hisn-shield-direction.png` | 1400 × 1400 direction board |
| `hisn-shield-1024.png` | 1024 full-bleed icon proof |
| `hisn-shield-512.png` | 512 Play Store export |
| `hisn-shield-48.png`, `-32`, `-24` | real-size checks |
| `skull-mask.png` | the head cut out, for reuse in the adaptive icon |
| `hisn-shield-direction.svg` | editable board source |

## Before shipping

1. Redraw the shield and the head as vectors. The skull mask is raster from the
   supplied artwork; the PNGs are a flattened proof.
2. Build the adaptive icon: paper background, 62% foreground, monochrome layer
   for Android 13 themed icons.
3. Test 24 px and 48 px on a device. At 24 px the head reads as a face, not as
   teeth and sockets; that is the intended simplification.
4. Use the same vector in the app bar at 28–32 dp and in the notification at
   24 dp, tinted from the theme rather than baked to a second colour.
5. Keep “Adult content blocker” in the store listing; use “A fortress for your
   phone.” on the splash and feature graphic.
