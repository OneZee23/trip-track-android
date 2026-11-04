# w16: maps drawn after Apple Maps

The owner put the iPhone's feed next to Android's on 4 Nov 2026: the iPhone's trip cards show Apple Maps (teal land
with darker forest, deep blue water and purple borders in the dark theme, pale green in the light one), Android's
showed OpenFreeMap's near-black Dark and beige Liberty. «Это две разные вещи, координально, а мне надо чтобы было как
на IOS».

## How the colours were found

`MKMapSnapshotter` on macOS with the iOS card configuration (`MKStandardMapConfiguration(elevationStyle: .flat)`, points
of interest excluded, light and dark appearance) rendered the feed's real routes plus a regional drive, a coastline and
a city centre. The same routes were rendered with MapLibre GL JS over each candidate style and compared side by side.
Colours were read from the Apple renders by pixel (forest and open land matched between the two themes pixel by pixel).

| | light | dark |
|---|---|---|
| open land | `#e9efcd` | `#417365` |
| forest | `#b8e19d` | `#156b6c` |
| lakes | `#91dcf8` | `#1d3987` |
| sea | `#36bbfa` | `#0c2051` |
| country border | `#ac67b5` | `#7b2a8a` |
| built-up city | `#f8f6ef` | `#37475d` |

The renders carry the Mac display's colour profile; the first palette (build 10) read them raw and came out greyer than
the phone. Converted to sRGB, the dark forest is `#156b6c`, the same value as in the owner's iPhone screenshot
(build 11).

## What changed

- `tools/mapstyle/build_styles.py` writes `core/map/src/main/assets/maps/triptrack-{light,dark}.json` from a snapshot of
  OpenFreeMap Liberty: same tiles, fonts, shield sprite and layer ids (so `MapLabels` keeps localising the same labels),
  recoloured, with points of interest, Natural Earth relief, 3D buildings and one-way arrows dropped, the sea coloured
  apart from lakes, every built-up land class in the city colour, district and region names in upright spaced
  capitals.
- `MapStyles.LIGHT`/`DARK` (`asset://` URIs) replace the two OpenFreeMap style URLs everywhere: trip cards and tiles,
  the trip map, the Atlas and Places (light under the fog, as on iOS), recording, the trip summary, the profile map
  and posters.

## Where it still differs

OpenMapTiles has no forest polygons below zoom 8, so a card of a 400 km drive shows even teal (or green) land where
Apple shows patches of forest; the far land takes the mean of Apple's forest and open-land colours so the card keeps
Apple's overall tone. From zoom 8 the OpenStreetMap forests draw. Label density follows OpenStreetMap, not Apple.

Tests: `MapStyleAssetsTests` (both styles ship, no POIs/relief/extrusions, the sea's own colour, every name label known
to `MapLabels`, the dark land colours). Emulator: the feed, the trip map and the Atlas in both themes.
