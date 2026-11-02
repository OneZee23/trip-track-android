# w9-artwork: the owner's drawings on Android

The iPhone app draws the owner's Figma artwork from `TripTrack/Resources/Assets.xcassets`; Android
had replaced part of it with emoji, system-like glyphs or nothing. Every image set and every view of
`TripTrack/Views/Components` was checked against Android (the full table is in the task's
`.codex-out/inventory.md`): 263 image sets were already the same artwork (badges, road scenes, the
180 vehicle sprites, platforms, the map car, onboarding, `empty_map`, `empty_offline`), 19 were
stand-ins, 23 were missing, 18 are seals hidden on iOS, and one (`empty_photos`) has no Android state.

## Ported

Reactions (iOS `ReactionIconView.swift`, `ReactionTallyPill.swift`, `ReactionPickerOverlay.swift`,
`ReactionBurstSprite.swift`, `ReactionsListSheet.swift`, `TripDetailSections.swift`
ReactionCountChip, `TripDetailView.swift` reactionsArea, `SocialDTOs.swift` ReactionEmoji):

- The twelve `reaction_<fire|wow|finish|pass|frame|like>_<outline|fill>` SVGs as VectorDrawables,
  path for path (`tools/port_artwork.py`). They keep their authored colours and are tinted with SrcIn,
  which keeps only the alpha, as UIKit's template rendering does: the filled flag is a solid
  silhouette on both platforms.
- `ReactionIcon`: both drawings mounted, an outline-to-fill cross-fade of 0.22 s ease out, accent when
  filled, ink otherwise; legacy keys (❤️ 🏎️ 🗺️, with or without U+FE0F) draw the canon icon; only an
  unknown key falls back to the emoji. `ReactionArt` maps a key to its two drawings.
- `ReactionTallyPill` (feed and public profile cards): the icon, the count rolling up or down
  (`.numericText` with `.snappy(0.26)`), the 1.16 / 0.88 bounce as two spring segments of 0.13 s and
  0.3 s, haptics on and off, a hold of 0.35 s (or the system's longer one) opening the reactions list.
- `ReactionCountChip` and the trip screen's «Реакции · N» card: canonical tallies, «+», the owner's
  reactor rows, the empty card with «Поставить реакцию», a hold on a chip opening the list.
- `ReactionPickerOverlay`: a window over the tab bar like iOS, glass capsule, spring-in, icons
  popping up 35 ms apart, the peach disc under my reaction, fade-out on a tap outside or back. It
  replaces the emoji capsule in the feed, on the trip screen (where it now lives at the screen's root
  instead of inside the scroll) and on the public profile.
- `ReactionsListSheet`: the chips, the rows with the drawn icon, skeleton, failure and empty states,
  pull to refresh; a row opens the profile once the sheet has closed.
- `ReactionBurstSprite` is ported but, as on iOS, mounted nowhere.
- The inbox's reaction badge draws the filled icon (11 pt, accent) with the badge shadow iOS has.

Illustrations (iOS `EmptyStateIllustration.swift` and its call sites), now one component with the
22 pt continuous corner and nearest-neighbour scaling:

| Asset | Where | Size |
|---|---|---|
| `empty_followers` | follow list empty, blocked list empty | 148 |
| `error_generic` | follow list error | 140 |
| `success_sent` | report sent | 132 |
| `empty_notifications` | empty inbox | 156 |
| `empty_garage` | empty garage (whole iOS empty state), empty vehicle photos | 148, 120 |
| `empty_achievements` | achievements filter with nothing in it | 148 |
| `badge_locked` | hidden badge in the badge overlay (over the 96 disc) | 132 |
| `empty_offline` | blocked list error (feed error already had it) | 148 |

Also: `IdleRing` shared and used by the empty statistics (was a chart glyph); `Drive2LogbookRow`
with `drive2_icon` on both passports (with `Drive2Audience`, the region part included);
`telegram_icon` in the settings row; `ClubMarkCluster` with `club_miata/vag/trucking/offroad` and the
clubs teaser laid out as `GroupsComingSoonView`; `onboard_road.png` renamed to its iOS name
`loading_road.png`. The feed card's 😶 / 🙂 prompts became two original glyphs, `faceDashed` and
`faceSmiling` (SF Symbols are not copied).

## Not ported, and why

- `club_subaru`, the club chips, `ClubsCatalogView`, `ClubDetailView`: the catalogue is not ported,
  and the club names are translation-table keys the string generator does not export; the generator
  also stops on the current iOS checkout (`autoRecordTriggerStereoCaption`).
- `empty_companions`, `empty_data`, `empty_search`, `poster_car`: their screens (companions picker,
  store recovery, people search, poster share) do not exist on Android.
- `empty_photos`: the Android Photo Picker needs no permission, so «no access» cannot happen.
- `GitHubIcon`, `YouTubeIcon`, `pin_start`, `pin_finish`, `empty_trips`, `empty_roads`,
  `empty_places`, `badge_distance/exclusive/exploration/special/streaks`: no iOS view references them.
- `seal_*`: discoveries are hidden on iOS (`DiscoveriesAvailability.isEnabled = false`).
- `PixelCarLoader`, `LocationTrackingButton`, `RoutePreviewView`, `StatView`: mounted by no iOS screen.
- `TapOnlyButtonStyle`, `UndoToastView`: behaviour components, outside this task.

## Where Android differs (docs/divergences.md)

- A pill that appears on a card or reaches zero changes without the iOS scale-and-fade transition.
- The picker dims the page with a translucent wash; iOS blurs it (`.ultraThinMaterial`).
- `ReactionEmoji.canonical` also folds a palette key that lost its U+FE0F; iOS folds only the legacy keys.
- The reactions list shows the owner's «Класс» drawing and the house wifi glyph where iOS uses SF
  `hand.thumbsup` and `wifi.slash`; the feed prompts use the new `faceDashed` and `faceSmiling`.
- The clubs teaser has no example chips and no «Посмотреть что будет».
- The blocked list now loads under skeleton rows, and the reaction picker covers the tab bar instead
  of hiding it; both rows of the divergence table were updated.

## Tests

- `ReactionArtTests`: every canon and legacy key, with and without U+FE0F, resolves to its two
  drawings; each is a 24 dp vector; the outline leaves its box mostly empty where the fill covers it.
- `ReactionEmojiTests` (core:model): the iOS cases.
- Screenshots: `reactions_icons`, `reactions_pills`, `reactions_picker`, `reactions_list`,
  `reactions_trip_card`, `reactions_trip_empty`, `social_inbox_reactions`, `social_followers_empty`,
  `social_followers_error`, `social_report_sent`, `social_achievements_empty`, `social_badge_hidden`,
  `blocked_list_empty`, `me_garage_empty`, `me_vehicle_photos_empty`, `me_clubs`, plus the re-recorded
  feed, profile, inbox, passport, settings, blocked list and glyph sheets.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --no-daemon --offline -Proborazzi.test.record=true test
./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```

Final run on the committed code, `./gradlew --no-daemon --offline --rerun-tasks
-Proborazzi.test.verify=true test lintDebug`: BUILD SUCCESSFUL in 3 min 54 s, 2,544 tests, no
failures, none skipped (app 865, core:logic 678, core:data 242, core:designsystem 187, core:map 150,
core:sync 127, core:location 111, core:model 95, core:network 89). Lint: 0 errors in every module;
none of the warnings is on a line this task wrote.

`docs/parity.md` was not regenerated: the report has drifted with other waves' work, and the
orchestrator regenerates it.
