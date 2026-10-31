# Android launcher artwork

Sources: iOS `Tools/AppIcon/D2.svg`, `D-tinted-preview.svg` and
`Tools/build_app_icons.py`. The iOS checkout stays read only.

```sh
python3 tools/icons/build_android_icons.py --ios "$HOME/OneZeeProjects/trip-track"
```

Requires Pillow and Chromium. Pass `--chrome /path/to/chromium` when needed.
An alternative renderer is `--resvg-module /path/to/node_modules/@resvg/resvg-js`
(checked with 2.6.2). Neither renderer is an app or Gradle dependency.

D2's 1024-unit scene occupies the middle 72 dp of a 108 dp adaptive layer. The
car and DEV badge fit the 66 dp safe circle. Android supplies the outer mask;
`ic_launcher_round` references the same adaptive layers for round launchers.
The map, gradient and expanded round route dashes are vectors. The foreground
uses density PNGs to preserve the authored Gaussian shadow. Debug overrides
only foreground and monochrome resources, retaining the iOS vector DEV mark.

The Android 13 monochrome layer uses the tinted original's car and route with
grayscale luminance encoded as alpha. Android supplies both colours. DEV gets
an outlined badge so its lettering remains distinct in a single-colour mask.
`D-dark.svg` is an iOS appearance, not a separate Android launcher contract.
