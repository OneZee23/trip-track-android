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
| open land | `#eaefd0` | `#4d7266` |
| forest | `#c0e0a3` | `#336a6b` |
| lakes, rivers' water | `#a2daf5` | `#243882` |
| sea | `#5eb5f3` | `#101d4c` |
| country border | `#a26ab1` | `#7a3f8f` |
| built-up city | `#f7f5ee` | `#3a485b` |

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
