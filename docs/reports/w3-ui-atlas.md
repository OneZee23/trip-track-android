# w3-ui-atlas

The Atlas and Places tabs on top of the merged view models: the Atlas sheet and map, the place and home cards, the «Вид карты», explanation and beta sheets, the share poster, the Places tab, the place screen and the home sheet. Branch `w3-ui-atlas`, not pushed. It starts from `w3-ui-trips` because both tabs open the trip screen, so it merges after that branch. Every test passes offline: 399 in `app`, 54 in `core:map`.

## Built

| iOS | Android (`app/src/main/kotlin/com/onezee/triptrack/` unless noted) |
|---|---|
| `MyMap/MyMapView.swift`, `Atlas/AtlasSlot.swift`, `AtlasSheet.swift`, `AtlasSheetDetent.swift` | `feature/atlas/ui/AtlasScreen.kt`, `AtlasSheet.kt` |
| `Atlas/AtlasSummaryContent.swift`, `AtlasRegionContent.swift`, `AtlasStatTrio.swift`, `AtlasTripRow.swift`, `FlowRow.swift`, `MyMap/AtlasRegionRow.swift`, `MyMapSheet.swift` (trip and road cards) | `feature/atlas/ui/AtlasSheetContent.kt`, `AtlasComponents.kt` |
| `Atlas/AtlasSheetTitleRow.swift`, `AtlasSubstateHeader.swift`, `AtlasMapControls.swift`, `AtlasPlaceCard.swift`, `MyMap/AtlasBetaChip.swift` | `feature/atlas/ui/AtlasComponents.kt` |
| `MyMapRepresentable.swift`: tap rules, `trips(near:)`, `placesVisible`, `syncTripPins`, user location; `PlacePinView`, `HomeAnnotation` | `feature/atlas/ui/AtlasMap.kt`; core:map `MapController.kt`, `marker/MarkerLayers.kt` |
| `FogVeilVein.swift`, `RouteVeinOverlay.swift` (`RouteVeinRenderer` widths and colours) | core:map `vein/VeinLayers.kt` |
| `AtlasControlsSheet.swift` (appearance sheet, explanation, style previews), `AtlasBetaSheet.swift` | `feature/atlas/ui/AtlasSheets.kt` |
| `Home/HomeSheet.swift`, `Home/HomePickerMapView.swift` | `feature/atlas/ui/HomeSheet.kt` |
| `AtlasSharePoster.swift` | `feature/atlas/ui/AtlasSharePoster.kt`; core:map `snapshot/MapSnapshots.kt` |
| `Components/CarLoadingView.swift` | `feature/atlas/ui/CarLoading.kt` |
| `Services/Reveal/RevealedLayerStore.swift`, `RevealedLayerSync.swift` | `feature/atlas/RevealedLayerStore.kt` |
| `Places/PlacesView.swift`, `Places/Slot/*` | `feature/places/ui/PlacesScreen.kt`, `feature/places/PlacesSlot.kt`, `PlacesPanelModel.kt` |
| `PlaceCardView.swift`, `PlaceSuggestionCardView.swift`, `PassCourseGlyph.swift` | `feature/places/ui/PlaceCards.kt` |
| `PlacesMapView.swift` | `feature/places/ui/PlacesMap.kt` |
| `PlaceDetailView.swift`, `PlaceRenameSheet.swift` | `feature/places/ui/PlaceDetailScreen.kt` |
| `PlaceManager.createPlace(cell:coordinate:name:)` with its history match | `feature/places/PlaceHistory.kt` |
| `.openPlace → .navigateToPlace`, `.openDiscovery → .navigateToDiscovery`, `PlacesDest` | `feature/atlas/WorldFeature.kt` |
| `DebugMapSeed` with `-seed-places-rich -seed-places-many` | `app/src/debug/.../debug/PlacesDemoSeed.kt` |

What the screens do:

- Atlas. The fog, the vein and the opened-world numbers come from the real stores. The sheet has four contents:
  - a summary;
  - a list of regions and cities, with the dashed «N more in the fog» chip;
  - a region page, where back returns where it came from and × returns to the summary;
  - the trip and road cards.

  A place or home card stands over the peek row. Sheet heights are numbers per content. The attribution follows the collapsed or peek sheet, never the list, and the chrome moves by offset. Taps on the map:
  - Over the open list, the strip of map collapses it, and so does any tap on the map.
  - From afar, a tap opens the region under it and the camera flies there.
  - Closer in, a road within 26 pt answers with its trips; a tap off the roads opens the region under it and leaves the camera where it is.

  Pins:
  - Places show from the region level; places out of the period are muted.
  - Only the chosen pin carries its name, and pins take a 44 dp target around their centre.
  - Trip photos are pins while «Фото из поездок» is on.

  The locate button fits 3 km around the last fix, or follows the person when there is none. A denied permission gets the house dialog with «Open settings». The loading, empty, offline and skeleton states follow iOS.
- «Вид карты»: Туман, Ночь and Клетки with drawn previews, the photos toggle and the home row. The home sheet sets home on a map, with its radius, visibility and privacy zone. It removes home after a house dialog.
- Share: a 540 × 960 poster at 3× from a MapLibre snapshot. The fog and vein are drawn over it, with the caption and mark as on iOS. It shares text when the snapshot fails or comes back blank.
- Places:
  - The panel stops (map, half, list, selection, empty) follow the gesture rules of `PlacesSlot`, and the List/Map switch moves the panel.
  - Search is a modal state: focus enters it, the scrim or «Отмена» leaves it, and the field stays above the keyboard.
  - From eight places there are sort chips and groups. There is also the last pass card.
  - Suggestions have a skeleton and «Save as place».
  - The newcomer and no-trips states offer the last three trips to mark a place on. Offline, a caption shows.
  - A chosen pin takes the slot with its card, and × gives it back.
- Place screen:
  - The map backdrop shows the threads of the passes.
  - The back button and the passes pill fade as the sheet climbs, and a header floats in.
  - It shows «Здесь N раз», the usual time, the last days as tiles, and the passes with «show all».
  - Rename opens a sheet. Delete asks in a house dialog, then closes the screen.
- Navigation uses typed `PlacesDestination` with idempotent push. The Atlas place card opens the place in two phases: the Places tab first, then the place 300 ms later on that tab's stack, after both stacks are emptied. `openDiscovery` works the same way; discoveries stay hidden behind the iOS flag.

## Wiring

- `AppGraph`:
  - `revealedLayer` is also handed to `LocationGraph` as the finish's ingest.
  - `world` is one `WorldFeature` holding app-scoped Atlas and Places view models.
  - At launch, after the location start, it runs the territory backfill (the Atlas counts cities by it) and the one-off build of the opened world.
  - Each sync pull reconciles the fog with the trip ids it applied.
  - `LocalDataWipe` forgets the opened regions.
- `AppShell`: two lines, `AtlasTab` and `PlacesTab`. The two placeholders are gone from `PlaceholderScreens`.
- core:map, all new parameters optional with defaults: `MapHost(vein, onMapTap, showsUserLocation)`, `MapHostState.locateUser()`, `MapCamera.latitudeSpan`, `MapMarker(selected, callout)` with `PLACE_MUTED` and `HOME`, `MapSnapshots.take`.
- `SeedActivity` runs `PlacesDemoSeed` after the trips seed. It adds two more drives on the Rostov road, 40 and 66 days back, so places get a usual time. It creates the ten places of the iOS seed through the door a saved suggestion takes. Then it builds the territory and the opened world.

## Found and fixed

- `RevealedLayerStore` had not been ported, so nothing on Android wrote the opened world. The finish, the pull and the launch build now go through it. It follows the iOS rules:
  - the v18 latch is set only after real work;
  - the wipe happens only after a preview that opens something;
  - requests that arrive during a pass are merged.
- The territory backfill existed, but nothing called it, so the Atlas said «0 из 28 городов».
- The rename field started with the cursor at the start, so typing went in front of the name («у рекиДача»).
- `core:map` lint: two `MissingPermission` errors on the location dot, already guarded at runtime, now suppressed with the reason.

## Screenshots

Roborazzi on Robolectric SDK 35, at Pixel 7 size, in Russian, light and dark, at a fixed «now» (28 Oct 2026, 20:30 Moscow). There are 48 images in `app/src/test/screenshots/world_*`:

- Atlas: summary, list, region, place, home, offline, trip, road, style sheet, beta sheet, location denied, empty, loading.
- Places: half, list, search, search empty, selected place, selected suggestion, no places, skeleton, no trips.
- Place screen: detail, rename.

MapLibre does not run under Robolectric, so the map is a flat stand-in in the fog colour. These frames check the sheets, the cards and the chrome. Sheets and dialogs are whole-screen captures.

## Tests

From the checkout root, with `JAVA_HOME` set to the Homebrew JDK 17:

```sh
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  :app:testDebugUnitTest :core:map:testDebugUnitTest :app:assembleDebug
./gradlew --no-daemon --offline :core:map:lintDebug
```

Results: `app` 399 tests and `core:map` 54, with no failures and no skips; the APK builds; `core:map` lint passes. Plain runs verify the screenshots. Record them again with `-Proborazzi.test.record=true`.

`:app:lintDebug` still fails on three errors outside these screens: `ui/PhotoImport.kt` (RestrictedApi, two) and `feature/trip/ui/TripCharts.kt` (UnusedBoxWithConstraintsScope). The Atlas and Places files only have `UseKtx` warnings.

There are 107 tests in `com.onezee.triptrack.world`:

- `RevealedLayerStoreTests` (21): the cases of iOS `RevealedLayerStoreTests` and `RevealedLayerSyncTests`.
- `PlacesSlotTests` (15): iOS `PlacesSlotTests` and `PlacesScreenStateTests`, with the same numbers.
- `AtlasRulesTests` (6): zoom levels, road hits and their reach, the far and near tap rule, trip photo pins.
- `WorldBehaviourTests` (15) and `PlacesNewcomerBehaviourTests` (2) run on the seeded library:
  - a flick opens the list, and the strip above it collapses it; a slow drag springs back; the handle toggles the list;
  - a region's back and ×; a drag down closes the region; a region trip opens the trip;
  - the place card opens the place in the Places tab;
  - search as a modal state, with the scrim as its second exit;
  - the List/Map switch; a flick from half to list;
  - the same place pushed twice is one screen; a chosen pin and its ×;
  - delete and rename on the place screen;
  - the empty tab stays pinned; saving a suggestion makes a place with its history.
- Screenshot classes (48).

## Checked on the emulator

FogSpike_API35 with software GL, in English, seeded through `SeedActivity`. Checked there:

- the Atlas with fog, vein and the collapsed sheet, and the list;
- a region opened from the list and from a tap on the map;
- Ночь and Клетки;
- zoomed in: the place pins and the chosen pin's name; the place card into the Places tab;
- Places at half, the list, search with the keyboard (the scrim dims the map by about a third), the map stop, a chosen suggestion and a chosen place.

One gap found there was fixed: the Places tab labelled its chosen pin, which iOS does not. The trip photo pins and the 3 km locate fit came after that run and were not seen on the emulator.

## Needs a real phone

- Fog and vein frame time with a real GPU while panning and pinching. The emulator used software GL.
- The share poster and the system share sheet with real apps.
- Locate with a real fix.
- TalkBack, and how the sheet thresholds feel under a finger, with three-button and gesture navigation.

## Not done

- Places are created only by saving a suggestion. The trip screen does not register checkpoints into places yet (iOS `PlaceManager.registerCheckpoint`), so the place chips under moments are missing too.
- Opening a trip from a place does not scroll to the checkpoint (iOS `TripFocus.checkpoint`): the trip screen has no focus parameter yet.
- Region labels on the Atlas map (iOS `RegionLabelAnnotation`, `RegionLabelLOD`).
- Paper tiles offline on Places, and clusters of trip photos.
- Someone else's map (iOS `PublicMapView`) belongs with the profiles.
- There are no debug launch flags for the loading, offline and location-denied states (iOS `-debug-atlas-loading`, `-debug-offline`, `-debug-location-denied`); the tests reach those states with fakes.

The differences that stay are in `docs/divergences.md`: the updated «Map markers» row, and the new rows from «Atlas and Places across tabs» down.
