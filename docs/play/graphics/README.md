# Play graphics

| File | Specification | Content |
|---|---|---|
| `icon-512.png` | 512×512, RGB PNG, opaque | D2 production icon, full square, no DEV badge or baked platform mask |
| `feature-1024x500.png` | 1024×500, RGB PNG, opaque | D2 car/road artwork, Inter, “Your roads. Your atlas.” from the approved iPhone screenshot series |

The feature composition extends D2's paper, keeping its car, route, shadows, colours and aspect
ratio. It has no app-screen imitation, device frame, ranking claim, store badge or third-party
logo. Inspected at full size and store-thumbnail size. The icon uses the same iOS normalization
as `tools/icons/build_android_icons.py`, without the Android adaptive layer's outer motion inset.
Play applies the icon's final corner treatment.

Rebuild from the repository root:

```sh
python3 docs/play/graphics/generate.py --ios "$HOME/OneZeeProjects/trip-track-ios-ref"
```

Requires Pillow and Chromium, as does `tools/icons`. `--chrome /path/to/chromium` selects a
renderer. In a sandbox where Chromium cannot start, use an already installed resvg module:

```sh
python3 docs/play/graphics/generate.py \
  --ios "$HOME/OneZeeProjects/trip-track-ios-ref" \
  --resvg-module /path/to/node_modules/@resvg/resvg-js
```

Checked with resvg 2.6.2. These are generation-only dependencies; no app or Gradle dependencies
were added. The renderer reads local SVGs, not remote images. The source repository stays read
only. Text uses `core/designsystem/src/main/res/font/inter_{semibold,bold}.ttf`. D2 sources are
`Tools/AppIcon/D2.svg` and `Tools/build_app_icons.py` in the iPhone reference checkout.

[Google's asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en-GB)
require a 512 px icon and 1024×500 feature graphic; the latter accepts a 24-bit PNG without alpha.
Both exports are 8-bit-per-channel RGB PNGs in the expected colour space. The generator checks
their dimensions and mode before reporting success.

## Phone screenshots still to capture

Use the final Play-installed release on a real Android phone, with real rendered maps, the
production icon, synthetic routes/photos/plates and no identifying accounts. No phone captures
were produced in this task: Robolectric cannot render the map. Do not upload its empty map
areas or iPhone screens. Prepare at least two screenshots for the listing; six to eight give
room to show the diary. Capture each promoted locale if adding text overlays.

| Order | Actual Android screen | What should be visible |
|---|---|---|
| 1 | Saved trip detail | Recorded route, title, distance/time and vehicle; the map must be loaded |
| 2 | Atlas | Several recorded roads revealed on the personal map with the native summary panel |
| 3 | Recording | Live map and distance/speed/clock; show the ongoing notification in a separate optional capture |
| 4 | Trip moments/photos | Own example photos, a note and checkpoints; no private EXIF locations or people |
| 5 | Trip statistics or replay | Speed/elevation detail or the replay control on a loaded route |
| 6 | Garage / vehicle passport | A vehicle's own trips/mileage; hide any plate unless it is synthetic |
| 7 | Me / achievements | Actual earned example level and badge artwork, without account email |
| 8 | Account & Sync (optional) | Optional sign-in/sync and its off-by-default state; no third-party account identity |

Add Places or public/social screens only after their branches are merged and verified. Omit
hidden purchases, discoveries, widgets, watches and car-display claims from all store graphics.
Screenshots and the background-location/FGS declaration videos are separate upload assets.
