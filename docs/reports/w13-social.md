# w13-social

Ported the missing social paths from iOS 0.9.3.

## Implemented

- `DiscoverView.swift` → `DiscoverScreen.kt`: the feed search circle and empty-feed «Найти людей» actions open Discover. Search is debounced; suggested people, results, empty states, profile navigation and guest follow prompts are connected. The iOS `empty_search` artwork is used.
- `TripCompanionsSection.swift`, `CompanionsPickerSheet.swift`, `CompanionsRosterSheet.swift` → `TripCompanionsSection.kt`: owner and read-only trip sections, roster and candidate sheets, invite/remove actions, candidate paging, cached roster refresh and profile navigation are connected.
- `FeedView.swift` → `FeedScreen.kt` and `RemoteNoticeStack.kt`: sync failure variants, feed announcements and recommended/required update cards are shown. Announcements also appear on «Я» as in `ProfileView.swift`.
- `ProfileView.swift`, `WithMeSection.swift`, `WithMeSectionModel.swift` → `ProfileJournal.kt`: the one-time visibility notice and «Со мной» section are added, including retry on load failure and opening a companion trip.
- `APIClient` call sites and `CompanionsStore.swift` → the existing `SocialApi`/`ApiSocial` methods and DTOs. Added client calls for people search, suggestions, companion list/candidates/invite/remove; reused the existing companion-trips call. Existing endpoint paths and JSON keys were retained.

## Strings and images

No string keys were added. The required keys were already present in Android's 13 language tables. The iOS `empty_search` artwork was copied to `app/src/main/res/drawable-nodpi/empty_search.png`.

## Tests

Four MockWebServer tests cover search/suggestions payloads, guest token omission, companion list/candidate/invite/remove contracts and companion-trip cursor parsing. Roborazzi captures 13 new states in light and dark: Discover suggestions, empty suggestions, results and empty results; companion empty, roster and picker; feed update/announcement, offline/server sync errors; profile visibility notice and «Со мной» content/error. That is 26 new screenshots.

The final command passed with 2,248 tests across 197 suites and `lintDebug`:

```sh
GRADLE_USER_HOME=/Users/onezee/.gradle JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home JAVA_TOOL_OPTIONS=-Duser.home=/Users/onezee/.gradle ./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```

Robolectric needed `user.home` redirected into the writable Gradle directory to create its lock file. No emulator was started.

## Differences from iOS

- The «Со мной» list currently loads its first `/companions/my-trips` page only; iOS requests more pages as the last row appears. Failure is visible and retryable.
- Update actions open Google Play on Android; iOS opens the App Store.
- The sync warning uses the existing house warning glyph instead of iOS SF Symbols. Emoji and font rendering may also vary by Android version.
- Google sign-in remains the Android sign-in path. If the backend does not accept it, guest-readable social calls stay tokenless and follow/companion actions use the existing sign-in prompt path.
