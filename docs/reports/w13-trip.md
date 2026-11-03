# w13-trip

Trip screens missing from Android, based on the 3 November 2026 parity audit and iOS 0.9.3.

## Ported

- `TripEditSheet.swift` → `TripEditSheet.kt`: one staged form for the trip name, description, car or passenger status, and access. The trip menu, own feed-card menu, and description prompt open it. Save uses the existing trip repository writers. Public/private changes use the existing publish and hide confirmation flows.
- `TripDetailView.swift` → `TripDetailViewModel.kt` and `TripDetailScreen.kt`: a remote `TRIP_NOT_FOUND` response for a foreign trip renders the closed-trip screen with its back action.
- `StoryShareSheet.swift`, `SharePosterView.swift`, `SharePosterRenderer.swift` → `TripPosterSheet.kt` and `TripPosterView.kt`: poster and story previews, minimal/map/photo backgrounds for local photos, the transparent sticker, PNG sharing and saving, and the optional share link. The iOS `poster_car` asset is copied to Android resources. The top-bar and own feed-card share actions open the studio. Guests share the image without a link.

## Strings

No resource keys were added. All required edit, closed-trip and poster strings were already present in the 13 Android string tables and their generated `AppStrings` wrapper.

## Tests

`TripDetailStateTests` covers staged repository updates, publishing after edits, and the remote closed-trip response. `TripsScreenshotTests` captures the edit form, access picker, car picker, closed-trip state, and poster map, minimal, story and sticker states in both themes: 20 new light/dark baselines. Screenshot recording passed. The final `-Proborazzi.test.verify=true test lintDebug` run passed: 2,126 tests across 189 suites, no failures, and lint with warnings but no errors. The exact command is in `.codex-out/result.md`.

## Differences from iOS

Android has no off-screen route-map snapshot. The map poster draws the house trip route over dark road-like lines. Local trip photos can be used as the photo background; a remote feed photo is not loaded into the poster. The Android sheet uses the platform keyboard and scroll handling.
