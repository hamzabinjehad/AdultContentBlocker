# Hisn app icon

A blue glass shield and white fortress gateway represent protection and the name Hisn (حصن). The normal appearance uses a pearl background; the dark appearance uses a charcoal-black background while retaining the blue shield and white gateway.

The artwork was generated with the built-in image generation tool. The full-bleed source plates are `Hisn-source.png` and `Hisn-dark-source.png`. Their 1024 × 1024 imports are stored in the native document's `Assets` directory. Apple's Icon Composer supplies the clean system enclosure and renders the appearance variants.

## Editable source and exports

- `../../macos/Hisn/AppIcon.icon`: editable native Icon Composer document, included in the app target.
- `Hisn-source.png` and `Hisn-dark-source.png`: generated source artwork for the normal and dark appearances.
- `Hisn.png`: final 1024 × 1024 transparent PNG rendered by Icon Composer.
- `appearances/`: 512 × 512 review previews for Default, Dark, Clear Light/Dark, and blue Tinted Light/Dark.
- `../../macos/Hisn/Assets.xcassets/AppIcon.appiconset/`: all ten traditional macOS size/scale exports.
- `../../extension/icons/`: 16, 32, 48, and 128 pixel browser-extension icons.

Run `bash design/app-icon/export.sh` from the repository root to render the native icon and its appearance previews using Xcode's bundled Icon Composer exporter, then reproduce the fallback and browser PNGs with `sips`. When the exporter is unavailable, the script uses the committed `Hisn.png` and keeps existing appearance previews. An optional PNG argument exports that file directly without updating native appearance previews.

The generated Xcode project includes both the native icon document and the PNG catalog with the `AppIcon` build setting. The app retains its macOS 13 minimum deployment target; Xcode produces the native icon assets and a legacy `AppIcon.icns`.

## Adaptive appearances on Mac

The native `AppIcon.icon` selects between the normal and dark source layers using appearance-specific opacity. The normal source is visible for Default (`light`); the dark source is visible for Dark (`dark`) and Mono (`tinted`). These lowercase names are native document appearance keys. The layer definitions use `opacity-specializations` without a scalar `opacity`, which would take precedence over the appearance values.

macOS 26 and later render the appropriate appearance from the compiled icon assets. Clear and Tinted use the dark source's Mono rendering; tint colors are chosen by the user, so the blue tinted PNGs are examples rather than a fixed app color. The PNG files in `appearances/` are previews and are not runtime switches. Browser extension icons use the default colored export.

Overall system Dark Mode and the app icon style are separate settings. In **System Settings → Appearance → Icon & widget style**, **Default** keeps the app's normal colors. **Dark → Auto** switches icons between normal and dark appearances with the system appearance; **Dark → Always** keeps them dark. **Clear** and **Tinted** follow their own appearance and color options. Changing the overall window appearance to Dark while keeping the icon style at Default does not select the native Dark icon.

For older supported macOS releases, Xcode generates static fallback icon images from the native document at build time. These releases do not provide the newer icon and widget appearance controls. The app does not monitor window appearance or replace its Dock icon programmatically.

Apple references: [Change Appearance settings on Mac](https://support.apple.com/guide/mac-help/change-appearance-settings-mchlp1225/mac) and [Creating your app icon using Icon Composer](https://developer.apple.com/documentation/xcode/creating-your-app-icon-using-icon-composer).

Design references: Apple's [app icon guidance](https://developer.apple.com/design/human-interface-guidelines/app-icons/) and [Icon Composer](https://developer.apple.com/icon-composer/).

## Final design specification

The following specification describes the committed artwork; it is not a verbatim record of the original generation and edit prompts.

Create an original Apple-inspired icon for Hisn (حصن), a protection app. Center a blue glass shield with a white fortress gateway. Use a clear silhouette, a cyan-to-deep-blue gradient, soft highlights, and restrained depth. Keep the symbol readable at small sizes and omit text.

For the normal appearance, fill the square source canvas with a pearl-white background. For the dark appearance, use a charcoal-black background and retain the bright blue shield and white gateway with subtle cyan edge highlights. Keep the protection mark centered in both source plates, without an outer rounded-square enclosure. Apple's Icon Composer supplies the enclosure and derives Clear and Tinted appearances from the dark source.
