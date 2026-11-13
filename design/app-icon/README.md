# Hisn app icon

A navy fortress shield with a pearl skull knocked out of it. The battlements on the shield's top edge tie the mark to the name Hisn (حصن, "fortress"); the skull says what the app keeps out. The normal appearance is a navy shield on pearl; the dark appearance is a bone-white shield on charcoal. All platforms share the same mark.

The mark is drawn as vector paths in `hisn_mark.py`, the single source for every platform. It uses a 108 × 108 canvas (Android adaptive-icon units). The shield is the ink layer, the skull is cut out of it (the jaw notches form the teeth), and the eyes and nose are drawn back in ink. The same paths produce the colour icons and a solid one-colour silhouette for themed, tinted and clear modes. `skull-concepts/` holds the three explored directions; this mark is concept C.

| Token | Hex | Use |
| --- | --- | --- |
| Navy | `#1F3A5F` | shield (light), splash background |
| Pearl | `#F4F1EA` | background and skull (light) |
| Charcoal | `#121722` | background and skull (dark) |
| Bone | `#E9E4D8` | shield (dark) |

## Editable source and exports

Run `bash design/app-icon/export.sh` from the repository root. It runs `hisn_mark.py` (needs Pillow), then renders the native Apple icon with Xcode's Icon Composer exporter when available.

`hisn_mark.py` writes:

- `hisn-mark.svg`: vector master of the mark.
- `Hisn-source.png` and `Hisn-dark-source.png`: 1024 × 1024 full-bleed light and dark plates, also copied into `../../macos/Hisn/AppIcon.icon/Assets/`.
- `../../ios/Hisn/Assets.xcassets/AppIcon.appiconset/Hisn1024.png`: opaque light plate.
- `../../android/app/src/main/res/drawable/`: `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (themed icon, one even-odd silhouette), `ic_splash_mark.xml` (pearl on navy) and `ic_brand_mark.xml` (silhouette cropped to the shield and tinted in code). In the launcher layers the mark is scaled to 0.8, so the battlement corners stay inside Android's 66 dp safe circle.
- `../../android/app/src/main/res/mipmap-*/`: pre-Android 8 launcher PNGs, rounded-square and round.
- `../../extension/icons/`: 16, 32, 48 and 128 pixel toolbar icons. These show the shield alone, without a plate, so the mark fills the square.

`export.sh` then writes:

- `Hisn.png`: final 1024 × 1024 transparent PNG rendered by Icon Composer.
- `appearances/`: 512 × 512 review previews for Default, Dark, Clear Light/Dark, and blue Tinted Light/Dark.
- `../../macos/Hisn/Assets.xcassets/AppIcon.appiconset/`: all ten traditional macOS size/scale exports.

When the exporter is unavailable, the script uses the committed `Hisn.png` and keeps existing appearance previews. An optional PNG argument exports that file directly without updating native appearance previews.

The generated Xcode project includes both the native icon document and the PNG catalog with the `AppIcon` build setting. The app retains its macOS 13 minimum deployment target; Xcode produces the native icon assets and a legacy `AppIcon.icns`.

## Adaptive appearances on Mac

The native `AppIcon.icon` selects between the normal and dark source layers using appearance-specific opacity. The normal source is visible for Default (`light`); the dark source is visible for Dark (`dark`) and Mono (`tinted`). These lowercase names are native document appearance keys. The layer definitions use `opacity-specializations` without a scalar `opacity`, which would take precedence over the appearance values.

macOS 26 and later render the appropriate appearance from the compiled icon assets. Clear and Tinted use the dark source's Mono rendering; tint colors are chosen by the user, so the blue tinted PNGs are examples rather than a fixed app color. The PNG files in `appearances/` are previews and are not runtime switches. Browser extension icons use the default colored export.

Overall system Dark Mode and the app icon style are separate settings. In **System Settings → Appearance → Icon & widget style**, **Default** keeps the app's normal colors. **Dark → Auto** switches icons between normal and dark appearances with the system appearance; **Dark → Always** keeps them dark. **Clear** and **Tinted** follow their own appearance and color options. Changing the overall window appearance to Dark while keeping the icon style at Default does not select the native Dark icon.

For older supported macOS releases, Xcode generates static fallback icon images from the native document at build time. These releases do not provide the newer icon and widget appearance controls. The app does not monitor window appearance or replace its Dock icon programmatically.

Apple references: [Change Appearance settings on Mac](https://support.apple.com/guide/mac-help/change-appearance-settings-mchlp1225/mac) and [Creating your app icon using Icon Composer](https://developer.apple.com/documentation/xcode/creating-your-app-icon-using-icon-composer).

Design references: Apple's [app icon guidance](https://developer.apple.com/design/human-interface-guidelines/app-icons/) and [Icon Composer](https://developer.apple.com/icon-composer/).
