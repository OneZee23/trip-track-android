# w3-ui-trips

The trip screens on top of the merged view models: Лента, the trip, the journey, the drafts, and the
trip history inside Я until Я itself is ported. Branch `w3-ui-trips`, not pushed. Every test passes
offline: 174 in `app`, 54 in `core:map`, 172 in `core:designsystem`.

## Built

| iOS | Android (`app/src/main/kotlin/com/onezee/triptrack/` unless noted) |
|---|---|
| `Feed/FeedView.swift`, `Feed/SocialFeedCardView.swift`, `Social/ReactionPickerOverlay.swift`, `Components/ReactionTallyPill.swift` | `feature/feed/ui/FeedScreen.kt`, `ui/cards/TripCards.kt`, `ui/PullToRefresh.kt` |
| `Profile/ProfileTripCardView.swift`, `Journeys/JourneyCardView.swift` | `ui/cards/TripCards.kt` |
| `Trips/TripDetailView.swift`, `TripDetailSections.swift`, `TripDetailTopBar.swift`, `DetailStatCard` | `feature/trip/ui/TripDetailScreen.kt`, `TripDetailParts.kt`, `TripCharts.kt`, `ui/DetailComponents.kt` |
| `TripMapHost`, `TripMapSlotView`, `Components/FullscreenMapSheet.swift`, `TripDetailRoutePlaceholder` | `feature/trip/ui/TripMap.kt` |
| `Trips/TripMomentsTimeline.swift` | `feature/trip/ui/TripMomentsTimeline.kt` |
| `CheckpointEditorSheet`, `SegmentEditorSheet` | `feature/trip/ui/TripEditorSheets.kt` |
| `Trips/PhotoFullScreenView.swift`, `AsyncThumbnailView`, `Components/TripPhotoPicker.swift` | `ui/PhotoViewer.kt`, `ui/PhotoImages.kt`, `ui/PhotoImport.kt` |
| `Journeys/JourneyDetailView.swift` (own and someone else's), `JourneyComposerSheet`, `JourneyEditSheet`, the publish sheet | `feature/journey/ui/JourneyDetailScreen.kt`, `JourneySheets.kt`, `JourneyFormat.kt` |
| `Drafts/DraftsListView.swift`, `Drafts/ProfileDraftsRow.swift`, the dot of `DraftsBadge` | `feature/drafts/ui/DraftsListScreen.kt`; the dot through `CustomTabBar(hasUnseenDrafts)` |
| The tabs' typed `NavigationStack` paths, idempotent `push` | `ui/navigation/TabNavigator.kt`: `TabNavigator`, `TabNavHost`, `entryViewModel` |
| `RelativeTripDate`, `TripAutoTitle`, `RegionDisplay` | `ui/TripText.kt`, `ui/RegionDisplay.kt` |
| `ShareLinkPresenter` for GPX and CSV | `ui/TripExportShare.kt`, a FileProvider (`res/xml/export_paths.xml`) |
| `DebugMapSeed` with `-seed-map-demo -seed-journey-demo -seed-segment-demo -seed-drafts` | `app/src/debug/.../debug/TripsDemoSeed.kt`, `SeedActivity.kt` |

What each screen does:

- **Лента**: All and Following in a pager (the segment is remembered), social cards with reactions,
  the reaction picker, comment counts and the journey line, the own card's menu (edit, which opens the trip;
  make private and delete, each through a house dialog), skeleton, error, empty and guest states, paging at the last
  card, pull to refresh, the offline strip, the recording banner, back to the top on a second tap.
- **Trip**: the first frame from what the list knew, with Back working at once. The hero shows the
  static route, then the live map once the push has finished (180 ms fade). Top bar with the title
  that appears on scroll, the owner's «…» (publish or make private, open or combine a journey,
  export GPX and CSV, delete), the author pill on someone else's trip. Chips, the journey line, the
  draft banner, the numbers with the energy tiles and the energy mode choice, moments with the
  checkpoint and segment sheets, the elevation and speed charts with scrubbing, the movement card,
  the description, the photos (add, view, delete). The fullscreen map is a layer over the page with
  the same map in it: crossfade, zoom buttons, the checkpoint and photo cards.
- **Journey**: own and someone else's, the composer sheet (a hold on a history card, or «Объединить»
  on a trip), the edit sheet with a house date sheet, the publish sheet, removing a leg.
- **Drafts**: today, yesterday, earlier; a swipe right says «Моя», a swipe left asks before deleting;
  selection mode with its bottom bar; the empty state; opening the list clears the dot on Я.
- **Я (interim)**: the shell's settings card, then the drafts row and the history, where a journey
  folds its legs into one card. A hold on a trip opens the journey composer.

Wiring: `AppGraph` builds one `TripsFeature` (a few lines; the account wave sets `accountId`,
`socialApi`, `cloudSyncEnabled` and `remotePhotoBytes` on it). `AppShell` shows `FeedTab` for Лента
and puts `MeTab` into the Я placeholder.

The debug seed fills an empty install with the iOS demo drives around Krasnodar, Rostov and Batumi, a
journey, two checkpoints with a segment, three photos and four drafts:
`adb shell am start -n com.onezee.triptrack.dev/com.onezee.triptrack.debug.SeedActivity`.

## Found and fixed in merged code

- **No house confirmation ever ran its answer.** `AppConfirmDialog` dismisses first and runs the answer
  second, as on iOS; the view models cleared their question on the dismissal and then looked it up to
  answer. Deleting a trip, a draft or a journey did nothing. The four view models now keep a dismissed
  question until another is asked (`TripDetailViewModel`, `DraftsViewModel`, `JourneyDetailViewModel`,
  `JourneyEditViewModel`); covered by two view model tests and the screen tests below.
- **The fullscreen map stayed hero-sized** on the emulator, with the rest of the screen black: the
  renderer kept producing 1080×1080 buffers for a 1080×2400 surface. A SurfaceView also ignores the
  alpha of its parents, so the reveal and the crossfade could not fade the map. `MapHost` got an opt-in
  `textureMode` (default off, the Atlas is unchanged); the trip map uses it. Checked on the emulator:
  expand, collapse and the scroll over the hero all draw.
- **`PresentationTestCase` booted the real application.** Its location start calls Play services, which
  throws under Robolectric, and the coroutine test harness reported that in whichever test ran next
  (8 of 11 trip detail tests failed in one run). The fixture now uses a plain `Application`; nothing in
  it needs the app graph. The same throw remains for tests that do boot the app (`ActivityTransitions
  .isAvailable` in core:location); it did not fail them here, but it is timing.

## Screenshots

Roborazzi on Robolectric SDK 35, Pixel 7 size, Russian, light and dark, on the seed at a fixed «now»
(28 Oct 2026, 20:30 Moscow). Room, the screens' worker and photo decoding run on the main looper in
these tests, so one `waitForIdle` settles a screen. 34 images in `app/src/test/screenshots/trips_*`:

feed, trip_foreign, me, trip, trip_photos, trip_moments, trip_actions, trip_delete, photo_viewer,
checkpoint_editor, segment_editor, journey, journey_stay, journey_composer, journey_edit, drafts,
drafts_selecting.

Sheets, dialogs and the popover are whole-screen captures (they live in their own windows). Also
re-recorded: `shell_feed_*` (Лента is no longer a placeholder) and the design system's `glyphs_*`
(new glyphs appended, the old ones unchanged).

## Tests

From the checkout root, `JAVA_HOME` set to the Homebrew JDK 17. Robolectric runs offline from the
local runtime, as in w1-strings-logic and w2-sync:

```sh
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  :app:testDebugUnitTest :core:map:testDebugUnitTest :core:designsystem:testDebugUnitTest
./gradlew --no-daemon --offline :app:assembleDebug
```

Record the screenshots again with `-Proborazzi.test.record=true`.

`TripsBehaviourTests` (14), on the same seeded fixture:

- a history card opens its trip, the bar hides, Back comes home and the bar returns;
- the same trip pushed twice is one screen;
- delete from «…» asks first; Cancel keeps the trip; Delete removes it and leaves the screen;
- deleting a journey keeps its trips and leaves its screen;
- publishing a private trip goes through its sheet;
- renaming a checkpoint saves when the sheet closes;
- a held history card opens the journey composer;
- opening the drafts clears the dot;
- a swipe right confirms a draft; a swipe left asks, then discards; selection confirms only the chosen;
- a feed card opens the trip with the card's data and no owner menu;
- a dialog on the feed hides the tab bar and Cancel brings it back.

## Checked on the emulator

FogSpike_API35, software GL, English, seeded through `SeedActivity`: Лента's error state without an
account, Я with drafts and history, the trip's first frame, the live hero, the expanded and collapsed
map, scrolling over the hero, the drafts list, swipe right, a partial swipe that settles on the plate,
a full swipe left into the dialog. Two gaps seen there: an open row stays open when another row is
swiped, and a drag cannot start on a revealed plate.

## Needs a real phone

- Texture-mode map performance: scrolling over the hero, the expand crossfade, memory, on a low-end
  device. The emulator used software GL.
- Whether the system photo picker keeps GPS EXIF. Without it photos are placed by time on the track.
- The dark map style, haptics, TalkBack, and how the swipe and pull thresholds feel under a finger.
- The share sheet with real apps for GPX and CSV.
- The social side with an account (needs the account wave's client): reactions, paging, someone
  else's trip and journey from the server.

## Not done

Trip replay, adding a checkpoint on the map or on the go, «name this place», the trip edit sheet
(title, description, car), pinning, link and story sharing, comments and the reactions list,
companions, the achievements section, a fullscreen journey map, taps on hero markers, the author's
profile and the public car page. Kept differences are listed in `docs/divergences.md`.
