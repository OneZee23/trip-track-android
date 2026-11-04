# w21: hidden trip parts and filming mode

## Completed

- Replaced the onboarding placeholder with `hidden_edges_illustration.png` through the shared `OnboardingHero` treatment on onboarding and the privacy question sheet. iOS: `PrivacyEdgeQuestionSheet.swift`; Android: `OnboardingScreen.kt:234,277`, `PrivacyEdgeQuestionSheet.kt:42-46`.
- The own-trip action menu always opens the hidden-parts editor, including trips with no current mask. The action uses the spec's imperative title. Android: `TripDetailScreen.kt:548,740-743`.
- Added the generated-title warning and rename action, plus the note for trips already seen on the server. Android: `HiddenPartsEditorSheet.kt:168-184`; iOS: hidden-parts editor in `TripDetailView.swift`.
- Publish hints now check the home zone, saved places and the previous trip's overnight stop. The hint action applies the recommended edge distance in one tap. Android: `TripsFeature.kt:94-125` (`publishHint`), `TripDetailScreen.kt:230-232,674,1166-1167`; iOS: publish hint in `TripDetailView.swift`.
- Filming mode filters saved places and suggestions, Atlas place pins, own journey/history cards, own trip maps and numbers, galleries, photo viewer and replay. Atlas builds and caches a masked all-time layer with the full layer, keeps the home-zone cache current, uses masked Atlas numbers, and shows the preparing hint while the masked layer is unavailable. The recording screen retains its location dot. Android: `PlacesTabViewModel.kt:73-130`, `MyMapViewModel.kt:171-195,214-245,262-292`, `AtlasScreen.kt:274-280`, `TripFilmingPresentation.kt`, `JourneyDetailScreen.kt`, `TripDetailScreen.kt`; iOS: filming-mode presentation in `AtlasView.swift`, `PlacesView.swift`, `TripDetailView.swift` and journey/profile views.
- The Settings privacy sheet now scrolls through the filming controls and explanatory text. The regenerated settings captures show the full sheet. Android: `SettingsScreens.kt:76` privacy page.
- Resource identifiers use the spec's §12 keys; English and Russian are filled in `core/designsystem/src/main/res/values/strings.xml` and `values-ru/strings.xml`.

## Verification

JDK 17, `./gradlew --offline`, and Robolectric's `JAVA_TOOL_OPTIONS='-Duser.home=/private/tmp/w21-robolectric-home'` were used. No emulator was started.

- `:core:model:test`, `:core:logic:test`, `:core:map:test`, `:core:data:testDebugUnitTest`, and `:core:sync:test` passed in the earlier port run; this pass did not change those modules.
- `./gradlew --offline :app:testDebugUnitTest --tests '*TripsScreenshotTests*' -Proborazzi.test.record=true` — passed after changing the action label.
- `./gradlew --offline :core:designsystem:testDebugUnitTest :app:testDebugUnitTest` — passed; includes the full `:app:testDebugUnitTest` suite.
- `git diff --check` — passed.

## Screenshots re-recorded and reviewed

All changed PNGs were opened and checked for clipped or overlapping content, type roles, spacing, radii and status/navigation bar placement. The two `SettingsStackShotTests.privacyIsASheetOverTheSettings` captures were checked after the privacy page was made scrollable.

- `hidden_edge_question_light.png`, `hidden_edge_question_dark.png`
- `hidden_parts_editor_light.png`, `hidden_parts_editor_dark.png`
- `hidden_settings_privacy_light.png`, `hidden_settings_privacy_dark.png`
- `settings_stack_privacy_light.png`, `settings_stack_privacy_dark.png`
- `trips_trip_actions_light.png`, `trips_trip_actions_dark.png`
- `trips_trip_actions_public_light.png`, `trips_trip_actions_public_dark.png`

## Remaining

- The Android resource names and English/Russian copy follow spec §12, but the current read-only iOS checkout has not finished that shared naming: it uses alternatives such as `hiddenStart`, `hiddenQuestionTitle`, and `hiddenOvernightHint`. The README's generator reads iOS AppStrings and cannot generate the spec identifiers from those alternate names. I left the required Android resources in place instead of editing the read-only iOS checkout or inventing eleven translations. Once iOS exposes the spec keys, run `python3 tools/strings/generate.py --ios /Users/onezee/OneZeeProjects/trip-track-094`, then validate and run the strings tests as described in `tools/strings/README.md`.
- Backend deployment/support remains outside this Android checkout; server persistence and old-client guards still require the backend counterpart.
