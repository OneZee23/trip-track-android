# iOS / Android file parity

Regenerate: `python3 tools/parity/parity.py report`.

Swift inventory: `TripTrack/` and `TripTrackShared/`. Android inventory: Kotlin, Java and C/C++ files under module `src/` directories, including tests. Build output and `spikes/` are excluded.

Links come from `// iOS:` comments. A link can cover part of a file; it does not prove behavioural parity. References to `TripTrackTests/` and `TripTrackUITests/` are resolved but excluded from the Swift area counts.

Swift files: 649. With Android references: 359. Missing: 290.

| iOS area | Swift files | With Android references | Missing |
|---|---:|---:|---:|
| `TripTrack/App` | 3 | 2 | 1 |
| `TripTrack/CarPlay` | 5 | 0 | 5 |
| `TripTrack/Common` | 2 | 2 | 0 |
| `TripTrack/Config` | 1 | 1 | 0 |
| `TripTrack/Localization` | 12 | 12 | 0 |
| `TripTrack/Localization/Translations` | 12 | 11 | 1 |
| `TripTrack/Models` | 86 | 82 | 4 |
| `TripTrack/Models/Social` | 7 | 6 | 1 |
| `TripTrack/Models/Sync` | 10 | 10 | 0 |
| `TripTrack/Networking` | 9 | 8 | 1 |
| `TripTrack/Persistence` | 5 | 5 | 0 |
| `TripTrack/Services` | 77 | 45 | 32 |
| `TripTrack/Services/Discoveries` | 12 | 5 | 7 |
| `TripTrack/Services/Export` | 2 | 2 | 0 |
| `TripTrack/Services/Intents` | 3 | 0 | 3 |
| `TripTrack/Services/ManualTrip` | 3 | 0 | 3 |
| `TripTrack/Services/Plus` | 14 | 10 | 4 |
| `TripTrack/Services/RemoteConfig` | 3 | 3 | 0 |
| `TripTrack/Services/Reveal` | 8 | 5 | 3 |
| `TripTrack/Services/Social` | 5 | 4 | 1 |
| `TripTrack/Services/Sync` | 10 | 9 | 1 |
| `TripTrack/Utils` | 8 | 5 | 3 |
| `TripTrack/ViewModels` | 7 | 6 | 1 |
| `TripTrack/Views` | 1 | 1 | 0 |
| `TripTrack/Views/Admin` | 2 | 1 | 1 |
| `TripTrack/Views/Auth` | 2 | 2 | 0 |
| `TripTrack/Views/Badges` | 8 | 1 | 7 |
| `TripTrack/Views/Components` | 50 | 14 | 36 |
| `TripTrack/Views/Drafts` | 6 | 4 | 2 |
| `TripTrack/Views/Feed` | 3 | 2 | 1 |
| `TripTrack/Views/Garage` | 25 | 12 | 13 |
| `TripTrack/Views/Groups` | 5 | 0 | 5 |
| `TripTrack/Views/Journeys` | 11 | 4 | 7 |
| `TripTrack/Views/ManualTrip` | 12 | 0 | 12 |
| `TripTrack/Views/Map` | 2 | 0 | 2 |
| `TripTrack/Views/MyMap` | 32 | 10 | 22 |
| `TripTrack/Views/MyMap/Atlas` | 13 | 5 | 8 |
| `TripTrack/Views/MyMap/FogMetal` | 10 | 5 | 5 |
| `TripTrack/Views/MyMap/Home` | 3 | 2 | 1 |
| `TripTrack/Views/Navigation` | 4 | 1 | 3 |
| `TripTrack/Views/Notifications` | 3 | 1 | 2 |
| `TripTrack/Views/Onboarding` | 1 | 1 | 0 |
| `TripTrack/Views/Places` | 7 | 7 | 0 |
| `TripTrack/Views/Places/Slot` | 5 | 5 | 0 |
| `TripTrack/Views/Plus` | 26 | 3 | 23 |
| `TripTrack/Views/Profile` | 43 | 32 | 11 |
| `TripTrack/Views/Recovery` | 1 | 0 | 1 |
| `TripTrack/Views/RemoteConfig` | 1 | 0 | 1 |
| `TripTrack/Views/Social` | 22 | 1 | 21 |
| `TripTrack/Views/Theme` | 3 | 3 | 0 |
| `TripTrack/Views/Tracking` | 9 | 0 | 9 |
| `TripTrack/Views/Trips` | 24 | 4 | 20 |
| `TripTrackShared` | 11 | 5 | 6 |

## TripTrack/App

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/App/AppDelegate.swift` | missing |
| `TripTrack/App/AppTab.swift` | `app/src/test/kotlin/com/onezee/triptrack/AppShellTests.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/ShellUnitTests.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/navigation/AppTab.kt` |
| `TripTrack/App/TripTrackApp.swift` | `app/src/main/kotlin/com/onezee/triptrack/AppGraph.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/TripTrackApplication.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MeFeatures.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/LocationGraph.kt` |

## TripTrack/CarPlay

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/CarPlay/CarPlayController.swift` | missing |
| `TripTrack/CarPlay/CarPlayLink.swift` | missing |
| `TripTrack/CarPlay/CarPlayLog.swift` | missing |
| `TripTrack/CarPlay/CarPlaySceneDelegate.swift` | missing |
| `TripTrack/CarPlay/CarPlayScreen.swift` | missing |

## TripTrack/Common

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Common/PIIScrubber.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PIIScrubber.kt` |
| `TripTrack/Common/PIISensitiveKeys.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PIIScrubber.kt` |

## TripTrack/Config

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Config/AppConfig.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/SignInViews.kt` |

## TripTrack/Localization

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Localization/AppStrings+Admin.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AppStrings+CosmeticRetention.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt` |
| `TripTrack/Localization/AppStrings+ProfileDesign.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AppStrings+ProfileJournal.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AppStrings+RecordingOutcome.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AppStrings+RemoteConfig.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AppStrings+TripHistory.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AppStrings+Usage.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AppStrings.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceTokenRules.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/strings/AppStringsTests.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/Measure.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProText.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/MeasureTestKit.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/AppStringsCore.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppLanguage.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/LocalizedDateFormatter.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AtlasCopy.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/LanguageManager.swift` | `app/src/test/kotlin/com/onezee/triptrack/AppShellTests.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/ShellUnitTests.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppLanguage.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/ThemeTests.kt` |

## TripTrack/Localization/Translations

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Localization/Translations/Translations+DE.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+ES.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+FIL.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+FR.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+ID.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+IT.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+KK.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+PL.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+PT.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+TR.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations+UK.swift` | `core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/Translations/Translations.swift` | missing |

## TripTrack/Models

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Models/AtlasHome.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasPlaces.kt` |
| `TripTrack/Models/AtlasPlaces.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasPlaces.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/AtlasPlaces.kt` |
| `TripTrack/Models/AtlasPresentation.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/AtlasPresentation.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasPlaces.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasPresentation.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/AtlasPresentation.kt` |
| `TripTrack/Models/AutoRecordMode.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/AutoRecordMode.kt` |
| `TripTrack/Models/AutoRecordTrigger.swift` | `core/location/src/main/kotlin/com/onezee/triptrack/location/auto/AutoRecordSettings.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/AutoRecordTrigger.kt` |
| `TripTrack/Models/AutoTripPolicy.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AutoTripPolicy.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/AutoTripPolicy.kt` |
| `TripTrack/Models/Badge.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeDefinitions.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Badge.kt` |
| `TripTrack/Models/BadgeDefinitions.swift` | `app/src/test/kotlin/com/onezee/triptrack/feature/me/BadgeCopyTests.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeDefinitions.kt` |
| `TripTrack/Models/BadgeFigure.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeManager.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/BadgeFigure.kt` |
| `TripTrack/Models/CLLocationCoordinate2D+Equatable.swift` | missing |
| `TripTrack/Models/CarHeadingPolicy.swift` | `core/map/src/main/kotlin/com/onezee/triptrack/map/replay/CarHeadingPolicy.kt` |
| `TripTrack/Models/CheckpointReading.swift` | missing |
| `TripTrack/Models/ConsumptionUnit.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ConsumptionUnit.kt` |
| `TripTrack/Models/DashboardUnits.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/DashboardUnits.kt` |
| `TripTrack/Models/Discovery.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/Discovery.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/GeoPoint.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ModelIdentity.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/GeoPointSerializer.kt` |
| `TripTrack/Models/DistanceUnit+Current.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt` |
| `TripTrack/Models/Drive2Link.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GarageRules.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Drive2Link.kt` |
| `TripTrack/Models/ElectricUnit.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ElectricUnit.kt` |
| `TripTrack/Models/EnergyEstimate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/EnergyEstimate.kt` |
| `TripTrack/Models/FixGate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FixGate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/FixGate.kt` |
| `TripTrack/Models/GamificationModels.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleEditSheet.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RoadCollection.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RewardMathTests.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/SimilarRecordedTripsTests.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/GamificationModels.kt` |
| `TripTrack/Models/GapFill.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GapFill.kt` |
| `TripTrack/Models/HistoryFolding.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/HistoryFolding.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/HistoryFolding.kt` |
| `TripTrack/Models/HomeSettings.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/HomeSettings.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/Journey.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/journey/JourneyViewModels.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyWindow.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Journey.kt` |
| `TripTrack/Models/JourneyAggregate.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/journey/JourneyViewModels.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyAggregate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/JourneyAggregate.kt` |
| `TripTrack/Models/JourneySuggester.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneySuggester.kt` |
| `TripTrack/Models/Measure.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/Measure.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Measure.kt` |
| `TripTrack/Models/OdometerField.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt` |
| `TripTrack/Models/PhotoMetadata.swift` | `app/src/main/kotlin/com/onezee/triptrack/ui/PhotoImport.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PhotoMetadata.kt` |
| `TripTrack/Models/PhotoUploadStatus.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PhotoUploadStatus.kt` |
| `TripTrack/Models/Place.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceMatcher.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ModelIdentity.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Place.kt` |
| `TripTrack/Models/PlaceChip.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceChip.kt` |
| `TripTrack/Models/PlaceLastPass.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceLastPass.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceLastPass.kt` |
| `TripTrack/Models/PlaceListItem.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceListItem.kt` |
| `TripTrack/Models/PlaceMatcher.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GapFill.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceMatcher.kt` |
| `TripTrack/Models/PlacePass.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceMatcher.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlacePass.kt` |
| `TripTrack/Models/PlaceScreen.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceScreen.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceScreen.kt` |
| `TripTrack/Models/PlaceStats.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceStats.kt` |
| `TripTrack/Models/PlaceSuggestions.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceSuggestions.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceSuggestions.kt` |
| `TripTrack/Models/PlaceUsuallyLine.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlaceDetailScreen.kt` |
| `TripTrack/Models/PlacesPresentation.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlacesPresentation.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlacesPresentation.kt` |
| `TripTrack/Models/Powertrain.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/Powertrain.kt` |
| `TripTrack/Models/PreparedProfileHistory.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProfileHistory.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PreparedProfileHistory.kt` |
| `TripTrack/Models/PrivacyZone.swift` | `core/sync/src/main/kotlin/com/onezee/triptrack/sync/TripSyncPayloadMapper.kt` |
| `TripTrack/Models/ProfileHistoryMonth.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProfileHistory.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ProfileHistoryMonth.kt` |
| `TripTrack/Models/ProfilePhotoDTO.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/ProfilePhotoDTO.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SyncSerializers.kt` |
| `TripTrack/Models/ProfileProgress.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/ProfileProgress.kt` |
| `TripTrack/Models/ProfileRoutineTrips.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/HistoryFolding.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ProfileRoutineTrips.kt` |
| `TripTrack/Models/RecordingBreaks.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RecordingBreaks.kt` |
| `TripTrack/Models/RecordingFinishPreview.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRecordingStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/RecordingFinishPreview.kt` |
| `TripTrack/Models/RecordingFixContinuity.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RecordingFixContinuity.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/RecordingFixContinuity.kt` |
| `TripTrack/Models/RecordingMovementTracker.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RecordingMovementTracker.kt` |
| `TripTrack/Models/RoadSceneTime.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/RoadSceneTime.kt` |
| `TripTrack/Models/RouteLineStyle.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/RouteLineStyle.kt` |
| `TripTrack/Models/SegmentHistory.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripSegments.kt` |
| `TripTrack/Models/SyncStatus.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SyncStatus.kt` |
| `TripTrack/Models/TimeWindowDebouncer.swift` | missing |
| `TripTrack/Models/TrackGapFinder.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TrackGapFinder.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TrackGapFinder.kt` |
| `TripTrack/Models/TrackPoint.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRecordingStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TrackPoint.kt` |
| `TripTrack/Models/Trip.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProfileHistory.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripPolyline.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRecordingStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Trip.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt` |
| `TripTrack/Models/TripCheckpoint.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripCheckpointPhotos.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripCheckpoint.kt` |
| `TripTrack/Models/TripCheckpointPhotos.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripCheckpointPhotos.kt` |
| `TripTrack/Models/TripCompanion.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripCompanion.kt` |
| `TripTrack/Models/TripConfirmation.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/WorldSql.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRecordingStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripConfirmation.kt` |
| `TripTrack/Models/TripDistanceGate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripDistanceGate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripDistanceGate.kt` |
| `TripTrack/Models/TripEnergyMode.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripEnergyMode.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/TripFilters.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripFilters.kt` |
| `TripTrack/Models/TripHistoryFilter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProfileHistory.kt` |
| `TripTrack/Models/TripLevelHistory.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt` |
| `TripTrack/Models/TripMoment.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripMoments.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripMoment.kt` |
| `TripTrack/Models/TripOrigin.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripOrigin.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/TripPhoto.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripCheckpointPhotos.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripPhoto.kt` |
| `TripTrack/Models/TripPhotoPlacement.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripPhotoPlacement.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRouteLocator.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripPhotoPlacement.kt` |
| `TripTrack/Models/TripReplayInput.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripReplay.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripReplayInput.kt` |
| `TripTrack/Models/TripRouteLocator.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRouteLocator.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripRouteLocator.kt` |
| `TripTrack/Models/TripSegment.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripSegments.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripSegment.kt` |
| `TripTrack/Models/UserSettings.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/UserSettings.kt` |
| `TripTrack/Models/Vehicle.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Vehicle.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt` |
| `TripTrack/Models/VehicleCatalogSelection.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/CarCatalog.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleCatalogSelection.kt` |
| `TripTrack/Models/VehicleFinish.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GarageRules.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleFinish.kt` |
| `TripTrack/Models/VehicleMod.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/JsonColumns.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleMod.kt` |
| `TripTrack/Models/VehicleModTags.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/JsonColumns.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/GarageRules.kt` |
| `TripTrack/Models/VehiclePlate.swift` | missing |
| `TripTrack/Models/VehicleType.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/GarageRules.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleType.kt` |
| `TripTrack/Models/VehicleYearDistance.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasPresentation.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleYearDistance.kt` |

## TripTrack/Models/Social

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Models/Social/CompanionsDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/CompanionsDTOs.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/Social/GarageDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/GarageDTOs.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SocialSerializers.kt` |
| `TripTrack/Models/Social/NotificationsDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/NotificationsDTOs.kt` |
| `TripTrack/Models/Social/PublicJourneyAdapter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyWindow.kt` |
| `TripTrack/Models/Social/PublicVehicleMod.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PublicVehicleMod.kt` |
| `TripTrack/Models/Social/SocialDTOs.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/SocialDTOs.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SocialSerializers.kt` |
| `TripTrack/Models/Social/SocialTripAdapter.swift` | missing |

## TripTrack/Models/Sync

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Models/Sync/AuthDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/AuthDTOs.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SyncSerializers.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/GoogleLoginRequest.kt` |
| `TripTrack/Models/Sync/JourneySyncPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/JourneySyncPayload.kt` |
| `TripTrack/Models/Sync/PhotoSyncPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PhotoSyncPayload.kt` |
| `TripTrack/Models/Sync/SettingsSyncPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SettingsSyncPayload.kt` |
| `TripTrack/Models/Sync/SyncPullRequest.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SyncPullRequest.kt` |
| `TripTrack/Models/Sync/SyncPullResponse.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SyncPullResponse.kt` |
| `TripTrack/Models/Sync/SyncPushModels.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SyncPushModels.kt` |
| `TripTrack/Models/Sync/TrackPointPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TrackPointPayload.kt` |
| `TripTrack/Models/Sync/TripSyncPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripSyncPayload.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/TripSyncPayloadMapper.kt` |
| `TripTrack/Models/Sync/VehicleSyncPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleSyncPayload.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SyncSerializers.kt` |

## TripTrack/Networking

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Networking/APIClient.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripSocialApi.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/ModelJson.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/ApiClient.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/ApiError.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/NetworkJson.kt`<br>`core/network/src/test/kotlin/com/onezee/triptrack/network/ApiClientWireTests.kt` |
| `TripTrack/Networking/APIEndpoint.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/ApiEndpoint.kt`<br>`core/network/src/test/kotlin/com/onezee/triptrack/network/ApiClientWireTests.kt` |
| `TripTrack/Networking/APIEnvelope.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/ApiEnvelope.kt` |
| `TripTrack/Networking/APIError.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/ApiError.kt` |
| `TripTrack/Networking/APILogger.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PIIScrubber.kt` |
| `TripTrack/Networking/DataGzip.swift` | missing |
| `TripTrack/Networking/MultipartFormDataBuilder.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/ApiClient.kt` |
| `TripTrack/Networking/PlusAPI.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PlusAPI.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SyncSerializers.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt` |
| `TripTrack/Networking/SecretsAPI.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SecretsAPI.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt` |

## TripTrack/Persistence

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Persistence/GeocodeCacheResult.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/RepositoryTypes.kt` |
| `TripTrack/Persistence/PersistenceController.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/db/TripTrackDatabase.kt` |
| `TripTrack/Persistence/PlaceStore.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/PlaceStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/PlaceDao.kt` |
| `TripTrack/Persistence/TripEntity+TrackPoints.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/TripDao.kt` |
| `TripTrack/Persistence/TripRepository.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/RepositoryTypes.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/RoomTripRepository.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/TripRepository.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/JourneyDao.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/TripDao.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/WorldSql.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripPolyline.kt` |

## TripTrack/Services

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/AudioRouteDetector.swift` | `core/location/src/main/kotlin/com/onezee/triptrack/location/auto/CarConnectionWatcher.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/auto/CarStereoConnections.kt` |
| `TripTrack/Services/AuthService.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountScopedState.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountServerActions.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/account/AuthService.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/settings/PrivacySettingsViewModel.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/TokenStore.kt`<br>`core/network/src/test/kotlin/com/onezee/triptrack/network/ApiClientWireTests.kt` |
| `TripTrack/Services/AutoTripService.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SyncSettingsEffects.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/LocationGraph.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/auto/AutoTripController.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/service/RecordingService.kt`<br>`core/location/src/test/kotlin/com/onezee/triptrack/location/AutoTripControllerTests.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/AutoTripPolicy.kt` |
| `TripTrack/Services/BadgeManager.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeManager.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/BadgeTests.kt` |
| `TripTrack/Services/BluetoothDetector.swift` | missing |
| `TripTrack/Services/CacheManager.swift` | missing |
| `TripTrack/Services/CarCatalog.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/CarCatalog.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/CarCatalog.kt` |
| `TripTrack/Services/CheckpointDefaultName.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripSegments.kt` |
| `TripTrack/Services/CompanionsStore.swift` | missing |
| `TripTrack/Services/ContentFilter.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountFormat.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripCommentsViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileTextEditor.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/CommentContentRules.kt` |
| `TripTrack/Services/DebugForeignTripFixture.swift` | missing |
| `TripTrack/Services/DebugGarageSeed.swift` | `app/src/debug/kotlin/com/onezee/triptrack/feature/me/MeLaunch.kt` |
| `TripTrack/Services/DebugLogExporter.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeDiagnostics.kt` |
| `TripTrack/Services/DebugMapSeed.swift` | missing |
| `TripTrack/Services/DebugProfileCardSeed.swift` | `app/src/debug/kotlin/com/onezee/triptrack/feature/me/MeLaunch.kt` |
| `TripTrack/Services/DraftDecisionQueue.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/drafts/DraftsViewModel.kt` |
| `TripTrack/Services/GamificationManager.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/RecordingStore.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RewardMathTests.kt` |
| `TripTrack/Services/GeohashEncoder.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GeohashEncoder.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/PlaceIdentityTests.kt` |
| `TripTrack/Services/HomeManager.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt` |
| `TripTrack/Services/HomePrivacyResync.swift` | missing |
| `TripTrack/Services/JourneyManager.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/journey/JourneyManager.kt` |
| `TripTrack/Services/KalmanLocationFilter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/KalmanLocationFilter.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt` |
| `TripTrack/Services/KeychainHelper.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/CredentialCipher.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/KeystoreTokenVault.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/TokenStore.kt` |
| `TripTrack/Services/LiveActivityManager.swift` | missing |
| `TripTrack/Services/LocalDataWipe.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/LocalDataWipe.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/WipeDao.kt`<br>`core/data/src/test/kotlin/com/onezee/triptrack/data/LocalDataWipeTests.kt` |
| `TripTrack/Services/LocationManager.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` |
| `TripTrack/Services/LocationProvider.swift` | missing |
| `TripTrack/Services/LogArchive.swift` | missing |
| `TripTrack/Services/MapExploration.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/MapExploration.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/MapExploration.kt` |
| `TripTrack/Services/MotionDetector.swift` | missing |
| `TripTrack/Services/NetworkMonitor.swift` | `core/sync/src/main/kotlin/com/onezee/triptrack/sync/NetworkMonitor.kt`<br>`core/sync/src/test/kotlin/com/onezee/triptrack/sync/SchedulingAndRasterTests.kt` |
| `TripTrack/Services/NotificationManager.swift` | missing |
| `TripTrack/Services/NotificationsInboxStore.swift` | missing |
| `TripTrack/Services/OnboardingLocationRequest.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/onboarding/OnboardingPermissions.kt` |
| `TripTrack/Services/OnboardingPermissionCoordinator.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/onboarding/OnboardingPermissions.kt` |
| `TripTrack/Services/OnboardingSystemPermissions.swift` | missing |
| `TripTrack/Services/PhoneConnectivityManager.swift` | missing |
| `TripTrack/Services/PhotoStorageService.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/files/PhotoFiles.kt` |
| `TripTrack/Services/PlaceManager.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlaceHistory.kt` |
| `TripTrack/Services/PostTripTrackProcessor.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/RecordingStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/RecordingDao.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/PostTripTrackProcessor.kt` |
| `TripTrack/Services/ProductAnalytics.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/ProductAnalytics.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/ApiClient.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/UsageDTOs.kt` |
| `TripTrack/Services/ProfilePhotoStore.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfilePhotoStore.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoDTOs.kt` |
| `TripTrack/Services/ProfileSyncLatch.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountFormat.kt` |
| `TripTrack/Services/PushNotificationManager.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PushDTOs.kt` |
| `TripTrack/Services/RatingPromptService.swift` | missing |
| `TripTrack/Services/RawFixLog.swift` | missing |
| `TripTrack/Services/RealGPSProvider.swift` | missing |
| `TripTrack/Services/RegionAtlas.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RegionAtlas.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/RegionAtlas.kt` |
| `TripTrack/Services/RemotePhotoStorage.swift` | missing |
| `TripTrack/Services/RoadCollectionManager.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/MapStores.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/MapDao.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RoadCollection.kt` |
| `TripTrack/Services/RoadFog.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RevealedLayer.kt` |
| `TripTrack/Services/RoadGapFiller.swift` | missing |
| `TripTrack/Services/SIWANonce.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/GoogleNonce.kt` |
| `TripTrack/Services/SentryService.swift` | missing |
| `TripTrack/Services/SettingsManager.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileIdentityStore.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileProgressStore.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/SettingsManagerTests.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/SettingsStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/VehicleStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/SettingsDao.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/VehicleDao.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/auto/AutoRecordSettings.kt`<br>`core/location/src/test/kotlin/com/onezee/triptrack/location/AutoRecordSettingsTests.kt` |
| `TripTrack/Services/SimilarRecordedTrips.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RoadCollection.kt` |
| `TripTrack/Services/SimulatedLocationProvider.swift` | missing |
| `TripTrack/Services/SmoothTrackManager.swift` | missing |
| `TripTrack/Services/StartupTrace.swift` | missing |
| `TripTrack/Services/StatsCache.swift` | missing |
| `TripTrack/Services/StoreHealth.swift` | `app/src/main/kotlin/com/onezee/triptrack/AppGraph.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/TripTrackApplication.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/LocationGraph.kt` |
| `TripTrack/Services/SunCalculator.swift` | missing |
| `TripTrack/Services/SyncQueue.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/sync/SyncRequest.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/QueuePersistence.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncQueue.kt` |
| `TripTrack/Services/TerritoryManager.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/MapStores.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/MapDao.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasGeometry.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/GeoBounds.kt` |
| `TripTrack/Services/TipJarService.swift` | missing |
| `TripTrack/Services/TokenStore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/TokenStore.kt` |
| `TripTrack/Services/TripManager.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/RecordingStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/RoomTripRepository.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/RecordingDao.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/recording/OrphanTriage.kt`<br>`core/location/src/main/kotlin/com/onezee/triptrack/location/recording/RecordingEnd.kt`<br>`core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RecordingDecision.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RecordingMath.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RecordingDecisionTests.kt` |
| `TripTrack/Services/TripSource.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PublicTripsDTOs.kt` |
| `TripTrack/Services/TripWorldEntry.swift` | missing |
| `TripTrack/Services/UndoActionCenter.swift` | missing |
| `TripTrack/Services/UnitsManager.swift` | missing |
| `TripTrack/Services/VehicleCatalog.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/LegacyVehicleCatalog.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/CarCatalog.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleCatalog.kt` |
| `TripTrack/Services/VehicleImageCache.swift` | missing |
| `TripTrack/Services/VehicleOdometer.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt` |
| `TripTrack/Services/VehiclePhotoStore.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/VehiclePhotoStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/VehicleDao.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/files/PhotoFiles.kt` |
| `TripTrack/Services/VehiclePhotoVisibilityAsk.swift` | missing |
| `TripTrack/Services/VisibilityNoticeLatch.swift` | missing |

## TripTrack/Services/Discoveries

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Discoveries/CachedSecretCatalog.swift` | missing |
| `TripTrack/Services/Discoveries/DiscoveriesAvailability.swift` | missing |
| `TripTrack/Services/Discoveries/DiscoveryProcessor.swift` | missing |
| `TripTrack/Services/Discoveries/DiscoveryReveal.swift` | `core/sync/src/main/kotlin/com/onezee/triptrack/sync/DiscoverySync.kt` |
| `TripTrack/Services/Discoveries/DiscoveryStore.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/DiscoveryStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/DiscoveryDao.kt` |
| `TripTrack/Services/Discoveries/MilestoneDetector.swift` | missing |
| `TripTrack/Services/Discoveries/RiddleCatalog.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/GeoPoint.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/RiddleCatalog.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/GeoPointSerializer.kt` |
| `TripTrack/Services/Discoveries/RiddleMatcher.swift` | missing |
| `TripTrack/Services/Discoveries/SecretCatalog.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SecretCatalog.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt` |
| `TripTrack/Services/Discoveries/SecretMatcher.swift` | missing |
| `TripTrack/Services/Discoveries/TrackCells.swift` | missing |
| `TripTrack/Services/Discoveries/TripDiscoveries.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripDiscoveries.kt` |

## TripTrack/Services/Export

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Export/CSVExporter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripExporters.kt` |
| `TripTrack/Services/Export/GPXExporter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripExporters.kt` |

## TripTrack/Services/Intents

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Intents/StartTripIntent.swift` | missing |
| `TripTrack/Services/Intents/TripTrackShortcuts.swift` | missing |
| `TripTrack/Services/Intents/VehicleAppEntity.swift` | missing |

## TripTrack/Services/ManualTrip

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/ManualTrip/ManualTripAftermath.swift` | missing |
| `TripTrack/Services/ManualTrip/ManualTripBuilder.swift` | missing |
| `TripTrack/Services/ManualTrip/ManualTripRouter.swift` | missing |

## TripTrack/Services/Plus

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Plus/CosmeticRetentionStore.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/CosmeticRetentionStore.kt` |
| `TripTrack/Services/Plus/PlusAccess.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/ProPoliciesTests.kt` |
| `TripTrack/Services/Plus/PlusAttachQueue.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PlusAttachQueue.kt` |
| `TripTrack/Services/Plus/PlusAvailability.swift` | missing |
| `TripTrack/Services/Plus/PlusGate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlusGate.kt` |
| `TripTrack/Services/Plus/PlusStore.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlusStore.kt` |
| `TripTrack/Services/Plus/ProContextOffer.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ProContextOffer.kt` |
| `TripTrack/Services/Plus/ProManageModel.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProText.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ProManageModel.kt` |
| `TripTrack/Services/Plus/ProOfferCoordinator.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt` |
| `TripTrack/Services/Plus/ProOfferLedger.swift` | missing |
| `TripTrack/Services/Plus/ProStatus.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProText.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ProStatus.kt` |
| `TripTrack/Services/Plus/SettingsManager+CosmeticRetention.swift` | missing |
| `TripTrack/Services/Plus/TipLedger.swift` | missing |
| `TripTrack/Services/Plus/TipMoment.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProPolicies.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TipMoment.kt` |

## TripTrack/Services/RemoteConfig

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/RemoteConfig/RemoteCatalogs.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/RemoteConfig.kt`<br>`core/network/src/test/kotlin/com/onezee/triptrack/network/RemoteConfigTests.kt` |
| `TripTrack/Services/RemoteConfig/RemoteConfig.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/RemoteConfig.kt`<br>`core/network/src/test/kotlin/com/onezee/triptrack/network/RemoteConfigTests.kt` |
| `TripTrack/Services/RemoteConfig/RemoteConfigStore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/RemoteConfig.kt` |

## TripTrack/Services/Reveal

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Reveal/FogLoadSequencer.swift` | missing |
| `TripTrack/Services/Reveal/FogRevealAnimation.swift` | missing |
| `TripTrack/Services/Reveal/RevealBuilder.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RevealBuilder.kt` |
| `TripTrack/Services/Reveal/RevealGrid.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RevealGrid.kt` |
| `TripTrack/Services/Reveal/RevealedLayer.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RevealedLayer.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RevealedLayerTests.kt` |
| `TripTrack/Services/Reveal/RevealedLayerStore.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/RevealedLayerStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/MapStores.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/MapDao.kt` |
| `TripTrack/Services/Reveal/RevealedLayerSync.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/RevealedLayerStore.kt` |
| `TripTrack/Services/Reveal/TemporalFogCache.swift` | missing |

## TripTrack/Services/Social

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Social/DiscussionArchive.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/SyncCommentArchive.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripCommentsViewModel.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/DiscussionArchive.kt`<br>`core/sync/src/test/kotlin/com/onezee/triptrack/sync/ManualDetailHydrationTests.kt` |
| `TripTrack/Services/Social/GroupsWaitlistStore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/GroupsDTOs.kt` |
| `TripTrack/Services/Social/SocialFeedStore.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripSocialApi.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/feed/FeedLoadingTests.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/trip/PresentationTestCase.kt` |
| `TripTrack/Services/Social/SocialReactions.swift` | missing |
| `TripTrack/Services/Social/TripCommentsStore.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripCommentsViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripSocialApi.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/feed/TripCommentsStateTests.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/trip/PresentationTestCase.kt` |

## TripTrack/Services/Sync

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Sync/APISyncTransport.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/SyncDTOs.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/APISyncTransport.kt`<br>`core/sync/src/test/kotlin/com/onezee/triptrack/sync/PhotoSyncTests.kt` |
| `TripTrack/Services/Sync/CompanionPhotoUploadService.swift` | missing |
| `TripTrack/Services/Sync/LastSyncedAtStore.swift` | `core/sync/src/main/kotlin/com/onezee/triptrack/sync/LastSyncedAtStore.kt` |
| `TripTrack/Services/Sync/ManualTripTrackLoader.swift` | `core/sync/src/main/kotlin/com/onezee/triptrack/sync/ManualTripTrackLoader.kt`<br>`core/sync/src/test/kotlin/com/onezee/triptrack/sync/ManualDetailHydrationTests.kt` |
| `TripTrack/Services/Sync/PullApplier.swift` | `core/sync/src/main/kotlin/com/onezee/triptrack/sync/PullApplier.kt` |
| `TripTrack/Services/Sync/R2PhotoStorage.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoDTOs.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoUploadRequest.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/R2PhotoStorage.kt` |
| `TripTrack/Services/Sync/SyncChunkBudget.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/SyncChunkBudget.kt` |
| `TripTrack/Services/Sync/SyncCoordinator.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SyncSettingsEffects.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/VehicleDao.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncCoordinator.kt`<br>`core/sync/src/test/kotlin/com/onezee/triptrack/sync/SyncCoordinatorTests.kt` |
| `TripTrack/Services/Sync/SyncEnqueuer.swift` | `core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncEnqueuer.kt` |
| `TripTrack/Services/Sync/VehiclePhotoRestore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoDTOs.kt`<br>`core/sync/src/main/kotlin/com/onezee/triptrack/sync/VehiclePhotoRestore.kt` |

## TripTrack/Utils

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Utils/GeometryUtils.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasGeometry.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RouteSimplification.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripReplay.kt` |
| `TripTrack/Utils/ISODate.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/FoundationSerializers.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/ModelJson.kt`<br>`core/model/src/test/kotlin/com/onezee/triptrack/model/FoundationCompatibilityTests.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/NetworkJson.kt` |
| `TripTrack/Utils/RandomDisplayName.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountFormat.kt` |
| `TripTrack/Utils/RegionDisplay.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileRegionDisplay.kt` |
| `TripTrack/Utils/RelativeTripDate.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncState.kt` |
| `TripTrack/Utils/SafeAreaInsets.swift` | missing |
| `TripTrack/Utils/TripAutoTitle.swift` | missing |
| `TripTrack/Utils/WindowLayoutMetrics.swift` | missing |

## TripTrack/ViewModels

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/ViewModels/FeedViewModel.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedViewModel.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/feed/FeedLoadingTests.kt` |
| `TripTrack/ViewModels/MapViewModel.swift` | `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` |
| `TripTrack/ViewModels/MyMapViewModel.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` |
| `TripTrack/ViewModels/PlaceDetailViewModel.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlaceDetailViewModel.kt` |
| `TripTrack/ViewModels/PlacesTabViewModel.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesTabViewModel.kt` |
| `TripTrack/ViewModels/ReloadCoalescer.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` |
| `TripTrack/ViewModels/TripsViewModel.swift` | missing |

## TripTrack/Views

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/ContentView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/WorldFeature.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/AppShellTests.kt` |

## TripTrack/Views/Admin

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Admin/AdminModel.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/AdminDTOs.kt` |
| `TripTrack/Views/Admin/AdminPanelView.swift` | missing |

## TripTrack/Views/Auth

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Auth/AppleSignInButton.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/GoogleCredentials.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/account/SignInViews.kt` |
| `TripTrack/Views/Auth/SignInPromptSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/SignInViews.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt` |

## TripTrack/Views/Badges

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Badges/AchievementCell.swift` | missing |
| `TripTrack/Views/Badges/AchievementDetailView.swift` | missing |
| `TripTrack/Views/Badges/AchievementsView.swift` | missing |
| `TripTrack/Views/Badges/BadgeArt.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/AchievementsScreen.kt` |
| `TripTrack/Views/Badges/BadgeCelebrationView.swift` | missing |
| `TripTrack/Views/Badges/BadgeDetailOverlay.swift` | missing |
| `TripTrack/Views/Badges/ConfettiView.swift` | missing |
| `TripTrack/Views/Badges/PublicAchievementsView.swift` | missing |

## TripTrack/Views/Components

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Components/ActionPopoverList.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/ActionPopoverList.kt` |
| `TripTrack/Views/Components/AppConfirmDialog.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/AppConfirmDialog.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/AppConfirmDialogTests.kt` |
| `TripTrack/Views/Components/AsyncThumbnailView.swift` | missing |
| `TripTrack/Views/Components/CarLoadingView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/CarLoading.kt` |
| `TripTrack/Views/Components/CardMapPreview.swift` | missing |
| `TripTrack/Views/Components/ContentSizedSheet.swift` | `core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/SheetAndToastTests.kt` |
| `TripTrack/Views/Components/EdgeScrims.swift` | missing |
| `TripTrack/Views/Components/EmptyStateIllustration.swift` | missing |
| `TripTrack/Views/Components/FullscreenMapSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripReplayControls.kt`<br>`core/map/src/main/kotlin/com/onezee/triptrack/map/replay/TripReplayEngine.kt`<br>`core/map/src/test/kotlin/com/onezee/triptrack/map/replay/TripReplayTimingTests.kt` |
| `TripTrack/Views/Components/HorizontalScrubSurface.swift` | missing |
| `TripTrack/Views/Components/IdleRing.swift` | missing |
| `TripTrack/Views/Components/KeyboardDismissTapper.swift` | missing |
| `TripTrack/Views/Components/LightRoutePreview.swift` | missing |
| `TripTrack/Views/Components/LocationTrackingButton.swift` | missing |
| `TripTrack/Views/Components/MapCarAnnotationView.swift` | missing |
| `TripTrack/Views/Components/MapCarMarker.swift` | `core/map/src/main/kotlin/com/onezee/triptrack/map/replay/CarHeadingPolicy.kt`<br>`core/map/src/main/kotlin/com/onezee/triptrack/map/replay/PlaybackLayers.kt` |
| `TripTrack/Views/Components/MapChromeButton.swift` | missing |
| `TripTrack/Views/Components/MapExpansionState.swift` | missing |
| `TripTrack/Views/Components/MapPhotoPreview.swift` | missing |
| `TripTrack/Views/Components/MapPopGestureGate.swift` | missing |
| `TripTrack/Views/Components/MapSnapshotPreview.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileMapPreview.kt` |
| `TripTrack/Views/Components/NavBackButton.swift` | missing |
| `TripTrack/Views/Components/NavCircleIcon.swift` | missing |
| `TripTrack/Views/Components/NotesEditorView.swift` | missing |
| `TripTrack/Views/Components/PhotoPickerView.swift` | missing |
| `TripTrack/Views/Components/PixelCarLoader.swift` | missing |
| `TripTrack/Views/Components/ReactionIconView.swift` | missing |
| `TripTrack/Views/Components/ReactionTallyPill.swift` | missing |
| `TripTrack/Views/Components/RecordingBanner.swift` | missing |
| `TripTrack/Views/Components/RemoteThumbnailView.swift` | missing |
| `TripTrack/Views/Components/ReplayBarState.swift` | `core/map/src/main/kotlin/com/onezee/triptrack/map/replay/ReplayBarState.kt` |
| `TripTrack/Views/Components/RouteMapView.swift` | `core/map/src/main/kotlin/com/onezee/triptrack/map/replay/MapPlayback.kt`<br>`core/map/src/main/kotlin/com/onezee/triptrack/map/replay/PlaybackProgress.kt`<br>`core/map/src/main/kotlin/com/onezee/triptrack/map/route/ReplayRoute.kt`<br>`core/map/src/main/kotlin/com/onezee/triptrack/map/route/RouteGeometry.kt`<br>`core/map/src/test/kotlin/com/onezee/triptrack/map/route/ReplayRouteTests.kt` |
| `TripTrack/Views/Components/RoutePlaybackController.swift` | `core/map/src/main/kotlin/com/onezee/triptrack/map/replay/RoutePlaybackController.kt`<br>`core/map/src/test/kotlin/com/onezee/triptrack/map/replay/PlaybackProgressTests.kt` |
| `TripTrack/Views/Components/RoutePreviewView.swift` | missing |
| `TripTrack/Views/Components/ScreenScaledDashRenderer.swift` | missing |
| `TripTrack/Views/Components/SheetCloseButton.swift` | missing |
| `TripTrack/Views/Components/SkeletonPlaceholder.swift` | missing |
| `TripTrack/Views/Components/SocialActionButtonStyle.swift` | missing |
| `TripTrack/Views/Components/SpeedColorScale.swift` | missing |
| `TripTrack/Views/Components/StatView.swift` | missing |
| `TripTrack/Views/Components/TapOnlyButtonStyle.swift` | missing |
| `TripTrack/Views/Components/TapTarget.swift` | missing |
| `TripTrack/Views/Components/ToastView.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/Toast.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/SheetAndToastTests.kt` |
| `TripTrack/Views/Components/TripMapHost.swift` | missing |
| `TripTrack/Views/Components/TripMapSlotView.swift` | missing |
| `TripTrack/Views/Components/TripPhotoPicker.swift` | `app/src/main/kotlin/com/onezee/triptrack/ui/PhotoImport.kt` |
| `TripTrack/Views/Components/UndoToastView.swift` | missing |
| `TripTrack/Views/Components/VehicleSpritePlate.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleStage.kt` |
| `TripTrack/Views/Components/VehicleStage.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleStage.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleStageState.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt` |
| `TripTrack/Views/Components/ZoomableImageView.swift` | missing |

## TripTrack/Views/Drafts

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Drafts/DraftRouteThumb.swift` | missing |
| `TripTrack/Views/Drafts/DraftsBadge.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/drafts/DraftsViewModel.kt` |
| `TripTrack/Views/Drafts/DraftsGrouping.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/drafts/DraftsViewModel.kt` |
| `TripTrack/Views/Drafts/DraftsLayout.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/drafts/DraftsViewModel.kt` |
| `TripTrack/Views/Drafts/DraftsListView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/drafts/DraftsViewModel.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/drafts/DraftsRulesTests.kt` |
| `TripTrack/Views/Drafts/ProfileDraftsRow.swift` | missing |

## TripTrack/Views/Feed

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Feed/FeedView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt` |
| `TripTrack/Views/Feed/SkeletonTripCard.swift` | missing |
| `TripTrack/Views/Feed/SocialFeedCardView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt` |

## TripTrack/Views/Garage

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Garage/AutoRecordSettingsView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/RecordingSettingsScreens.kt` |
| `TripTrack/Views/Garage/Drive2LinkSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehiclePassportSheets.kt` |
| `TripTrack/Views/Garage/Drive2LogbookRow.swift` | missing |
| `TripTrack/Views/Garage/FuelCurrencyPickerSheet.swift` | missing |
| `TripTrack/Views/Garage/GarageComponents.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/GarageScreens.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeComponents.kt` |
| `TripTrack/Views/Garage/GarageMileageCard.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/GarageScreens.kt` |
| `TripTrack/Views/Garage/GaragePhotoGrid.swift` | missing |
| `TripTrack/Views/Garage/GarageRegionChips.swift` | missing |
| `TripTrack/Views/Garage/GarageView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/GarageScreens.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/GarageViewModel.kt` |
| `TripTrack/Views/Garage/TripRowText.swift` | missing |
| `TripTrack/Views/Garage/VehicleCardStyle.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GarageRules.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleCardStyle.kt` |
| `TripTrack/Views/Garage/VehicleCatalogCaption.swift` | missing |
| `TripTrack/Views/Garage/VehicleCatalogModelSteps.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleCatalogViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleEditSheet.kt` |
| `TripTrack/Views/Garage/VehicleCatalogPickerView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleCatalogViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleEditSheet.kt` |
| `TripTrack/Views/Garage/VehicleEditFormView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleCatalogViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleEditSheet.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleEnergyDraft.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/VehicleFormTests.kt` |
| `TripTrack/Views/Garage/VehicleFace.swift` | missing |
| `TripTrack/Views/Garage/VehicleGarageCustomization.swift` | missing |
| `TripTrack/Views/Garage/VehicleLevelInfoView.swift` | missing |
| `TripTrack/Views/Garage/VehicleMapView.swift` | missing |
| `TripTrack/Views/Garage/VehicleMiniMap.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileMapPreview.kt` |
| `TripTrack/Views/Garage/VehiclePhotoImage.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehiclePhotoSheet.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MePhotoViewer.kt` |
| `TripTrack/Views/Garage/VehiclePhotosView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehiclePhotoSheet.kt` |
| `TripTrack/Views/Garage/VehiclePrivacyView.swift` | missing |
| `TripTrack/Views/Garage/VehicleTagsRow.swift` | missing |
| `TripTrack/Views/Garage/VehicleTripsView.swift` | missing |

## TripTrack/Views/Groups

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Groups/ClubCatalog.swift` | missing |
| `TripTrack/Views/Groups/ClubDetailView.swift` | missing |
| `TripTrack/Views/Groups/ClubMarkCluster.swift` | missing |
| `TripTrack/Views/Groups/ClubsCatalogView.swift` | missing |
| `TripTrack/Views/Groups/GroupsComingSoonView.swift` | missing |

## TripTrack/Views/Journeys

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Journeys/JourneyCardView.swift` | missing |
| `TripTrack/Views/Journeys/JourneyComposerSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/journey/JourneyViewModels.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/journey/JourneyPresentationTests.kt` |
| `TripTrack/Views/Journeys/JourneyDaysList.swift` | missing |
| `TripTrack/Views/Journeys/JourneyDetailView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripSocialApi.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/journey/JourneyViewModels.kt` |
| `TripTrack/Views/Journeys/JourneyEditSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/journey/JourneyViewModels.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyWindow.kt` |
| `TripTrack/Views/Journeys/JourneyFormat.swift` | missing |
| `TripTrack/Views/Journeys/JourneyLegRow.swift` | missing |
| `TripTrack/Views/Journeys/JourneyLinkRow.swift` | missing |
| `TripTrack/Views/Journeys/JourneyPublishSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/journey/JourneyViewModels.kt` |
| `TripTrack/Views/Journeys/JourneySuggestionCards.swift` | missing |
| `TripTrack/Views/Journeys/PublicJourneyView.swift` | missing |

## TripTrack/Views/ManualTrip

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/ManualTrip/ManualTripActiveField.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripDateDefaults.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripDurationPolicy.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripDurationText.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripEntry.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripFrequentPlaces.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripMapView.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripModel.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripPreset.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripSheet.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripSuggestions.swift` | missing |
| `TripTrack/Views/ManualTrip/ManualTripSummaryLine.swift` | missing |

## TripTrack/Views/Map

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Map/GlowingHeadOverlay.swift` | missing |
| `TripTrack/Views/Map/MapViewRepresentable.swift` | missing |

## TripTrack/Views/MyMap

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/MyMap/AtlasBetaChip.swift` | missing |
| `TripTrack/Views/MyMap/AtlasBetaSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSheets.kt` |
| `TripTrack/Views/MyMap/AtlasControlsSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSheets.kt` |
| `TripTrack/Views/MyMap/AtlasRegionRow.swift` | missing |
| `TripTrack/Views/MyMap/AtlasSharePoster.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/AtlasSharePosterInputs.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSharePoster.kt` |
| `TripTrack/Views/MyMap/AtlasStatTile.swift` | missing |
| `TripTrack/Views/MyMap/AtlasTheme.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/AtlasTheme.kt` |
| `TripTrack/Views/MyMap/AttributionCarve.swift` | missing |
| `TripTrack/Views/MyMap/CloudTexture.swift` | missing |
| `TripTrack/Views/MyMap/DiscoveryCardSheet.swift` | missing |
| `TripTrack/Views/MyMap/DiscoveryCopy.swift` | missing |
| `TripTrack/Views/MyMap/FingerWatch.swift` | missing |
| `TripTrack/Views/MyMap/FogVeilOverlay.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FogGeometry.kt` |
| `TripTrack/Views/MyMap/FogVeilVein.swift` | missing |
| `TripTrack/Views/MyMap/FogVeilView.swift` | missing |
| `TripTrack/Views/MyMap/JournalBuilder.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/AtlasPresentation.kt` |
| `TripTrack/Views/MyMap/MyMapOverlays.swift` | missing |
| `TripTrack/Views/MyMap/MyMapRepresentable.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasMap.kt` |
| `TripTrack/Views/MyMap/MyMapSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSheetContent.kt` |
| `TripTrack/Views/MyMap/MyMapView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasScreen.kt` |
| `TripTrack/Views/MyMap/PublicMapView.swift` | missing |
| `TripTrack/Views/MyMap/RegionLabelAnnotation.swift` | missing |
| `TripTrack/Views/MyMap/RegionLabelLOD.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FogGeometry.kt` |
| `TripTrack/Views/MyMap/RiddleHintCard.swift` | missing |
| `TripTrack/Views/MyMap/RiddleHintOverlay.swift` | missing |
| `TripTrack/Views/MyMap/RiddleHints.swift` | missing |
| `TripTrack/Views/MyMap/RouteVeinOverlay.swift` | missing |
| `TripTrack/Views/MyMap/SealAnnotation.swift` | missing |
| `TripTrack/Views/MyMap/SealPainter.swift` | missing |
| `TripTrack/Views/MyMap/VeilFrame.swift` | missing |
| `TripTrack/Views/MyMap/VeilRevealMask.swift` | missing |
| `TripTrack/Views/MyMap/VeilSeat.swift` | missing |

## TripTrack/Views/MyMap/Atlas

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/MyMap/Atlas/AtlasMapControls.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasPeriodButton.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasPlaceCard.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasRegionContent.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSheetContent.kt` |
| `TripTrack/Views/MyMap/Atlas/AtlasSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/AtlasSlot.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSheet.kt` |
| `TripTrack/Views/MyMap/Atlas/AtlasSheetDetent.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/AtlasSlot.kt` |
| `TripTrack/Views/MyMap/Atlas/AtlasSheetTitleRow.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSlot.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/AtlasSlot.kt` |
| `TripTrack/Views/MyMap/Atlas/AtlasStatTrio.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSubstateHeader.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSummaryContent.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSheetContent.kt` |
| `TripTrack/Views/MyMap/Atlas/AtlasTripRow.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/FlowRow.swift` | missing |

## TripTrack/Views/MyMap/FogMetal

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/MyMap/FogMetal/FogCarveWindow.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogCellGrid.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FogGeometry.kt`<br>`core/map/src/main/cpp/fog_layer.hpp` |
| `TripTrack/Views/MyMap/FogMetal/FogFrameEncoder.swift` | `core/map/src/main/cpp/fog_frame.hpp`<br>`core/map/src/main/cpp/fog_layer.hpp` |
| `TripTrack/Views/MyMap/FogMetal/FogMesh.swift` | `core/map/src/main/cpp/fog_state.hpp` |
| `TripTrack/Views/MyMap/FogMetal/FogMetalAvailability.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogMetalSeat.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogMetalVeil.swift` | `core/map/src/main/cpp/fog_layer.hpp`<br>`core/map/src/main/cpp/fog_state.hpp` |
| `TripTrack/Views/MyMap/FogMetal/FogOffscreen.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogPipelineCache.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogRevealCircle.swift` | `core/map/src/main/cpp/fog_state.hpp` |

## TripTrack/Views/MyMap/Home

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/MyMap/Home/HomeAnnotation.swift` | missing |
| `TripTrack/Views/MyMap/Home/HomePickerMapView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/HomeSheet.kt` |
| `TripTrack/Views/MyMap/Home/HomeSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/HomeSheet.kt` |

## TripTrack/Views/Navigation

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Navigation/CustomNavBar.swift` | missing |
| `TripTrack/Views/Navigation/CustomTabBar.swift` | `app/src/test/kotlin/com/onezee/triptrack/AppShellTests.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/navigation/CustomTabBar.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/TabBarTests.kt` |
| `TripTrack/Views/Navigation/NavBarKiller.swift` | missing |
| `TripTrack/Views/Navigation/NavFlashDebug.swift` | missing |

## TripTrack/Views/Notifications

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Notifications/NotificationInviteRowModel.swift` | missing |
| `TripTrack/Views/Notifications/NotificationPreferencesView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/NotificationPreferencesViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsScreens.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/SettingsManagerTests.kt` |
| `TripTrack/Views/Notifications/NotificationsInboxView.swift` | missing |

## TripTrack/Views/Onboarding

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Onboarding/OnboardingView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/onboarding/OnboardingScreen.kt` |

## TripTrack/Views/Places

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Places/PassCourseGlyph.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlaceCards.kt` |
| `TripTrack/Views/Places/PlaceCardView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlaceCards.kt` |
| `TripTrack/Views/Places/PlaceDetailView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlaceDetailScreen.kt` |
| `TripTrack/Views/Places/PlaceRenameSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlaceDetailScreen.kt` |
| `TripTrack/Views/Places/PlaceSuggestionCardView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlaceCards.kt` |
| `TripTrack/Views/Places/PlacesMapView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlacesMap.kt` |
| `TripTrack/Views/Places/PlacesView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesTabViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlacesScreen.kt` |

## TripTrack/Views/Places/Slot

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Places/Slot/PlacesPanel.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlacesScreen.kt` |
| `TripTrack/Views/Places/Slot/PlacesPanelHeader.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/ui/PlacesScreen.kt` |
| `TripTrack/Views/Places/Slot/PlacesScreenState.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesSlot.kt` |
| `TripTrack/Views/Places/Slot/PlacesSlot.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesSlot.kt` |
| `TripTrack/Views/Places/Slot/SlotGesture.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasSheet.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesSlot.kt` |

## TripTrack/Views/Plus

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Plus/PlusPaywallModel.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PlusPaywallModel.kt` |
| `TripTrack/Views/Plus/PlusPaywallSheet.swift` | missing |
| `TripTrack/Views/Plus/PlusRow.swift` | missing |
| `TripTrack/Views/Plus/ProBadge.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/ProBadge.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/ThemeTests.kt` |
| `TripTrack/Views/Plus/ProBoughtView.swift` | missing |
| `TripTrack/Views/Plus/ProContextSheet.swift` | missing |
| `TripTrack/Views/Plus/ProDemoContent.swift` | missing |
| `TripTrack/Views/Plus/ProDemoData.swift` | missing |
| `TripTrack/Views/Plus/ProDemoPager.swift` | missing |
| `TripTrack/Views/Plus/ProDemoPreviewView.swift` | missing |
| `TripTrack/Views/Plus/ProFeatureRow.swift` | missing |
| `TripTrack/Views/Plus/ProLayout.swift` | missing |
| `TripTrack/Views/Plus/ProManageSheet.swift` | missing |
| `TripTrack/Views/Plus/ProOfferHost.swift` | missing |
| `TripTrack/Views/Plus/ProPaywallHero.swift` | missing |
| `TripTrack/Views/Plus/ProPaywallPhase.swift` | missing |
| `TripTrack/Views/Plus/ProPaywallView.swift` | missing |
| `TripTrack/Views/Plus/ProPlanCard.swift` | missing |
| `TripTrack/Views/Plus/ProPriceMath.swift` | missing |
| `TripTrack/Views/Plus/ProShowcase.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/ProShowcase.kt` |
| `TripTrack/Views/Plus/ProShowcaseHost.swift` | missing |
| `TripTrack/Views/Plus/ProShowcaseSheet.swift` | missing |
| `TripTrack/Views/Plus/ProShowcaseTileView.swift` | missing |
| `TripTrack/Views/Plus/TipEntry.swift` | missing |
| `TripTrack/Views/Plus/TipJarSheet.swift` | missing |
| `TripTrack/Views/Plus/TripSummaryTipCard.swift` | missing |

## TripTrack/Views/Profile

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Profile/AboutEditorSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileTextEditor.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/me/ProfileAuxiliaryTests.kt` |
| `TripTrack/Views/Profile/AccountSyncComponents.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountComponents.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountFormat.kt` |
| `TripTrack/Views/Profile/AppPreferencesView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsScreens.kt` |
| `TripTrack/Views/Profile/AvatarFrame.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/AvatarFrame.kt` |
| `TripTrack/Views/Profile/BadgeRarityStyle.swift` | missing |
| `TripTrack/Views/Profile/BluetoothScanSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/RecordingSettingsScreens.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/settings/AutoRecordTriggerShotTests.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/strings/AppStringsTests.kt` |
| `TripTrack/Views/Profile/CloudSyncView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/account/CloudSyncScreen.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/settings/SyncSettingsEffects.kt` |
| `TripTrack/Views/Profile/CountryPickerView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MyProfileScreen.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileAuxiliaryScreens.kt` |
| `TripTrack/Views/Profile/DebugLogsView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeDiagnostics.kt` |
| `TripTrack/Views/Profile/LevelsView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileAuxiliaryScreens.kt` |
| `TripTrack/Views/Profile/MyProfileView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeTab.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MyProfileScreen.kt` |
| `TripTrack/Views/Profile/NameEditorSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileTextEditor.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/me/ProfileAuxiliaryTests.kt` |
| `TripTrack/Views/Profile/NotificationSwitches.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/NotificationPreferencesViewModel.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/PushDTOs.kt` |
| `TripTrack/Views/Profile/PrivacySettingsView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PrivacySettingsViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsScreens.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/PrivacySettingsTests.kt` |
| `TripTrack/Views/Profile/ProfileAchievementsSection.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/AchievementsScreen.kt` |
| `TripTrack/Views/Profile/ProfileBackground.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileBackgroundColors.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ProfileBackground.kt` |
| `TripTrack/Views/Profile/ProfileClubsRow.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeTab.kt` |
| `TripTrack/Views/Profile/ProfileComponents.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeComponents.kt` |
| `TripTrack/Views/Profile/ProfileCoverImport.swift` | missing |
| `TripTrack/Views/Profile/ProfileCoverPager.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfilePhotoScreens.kt` |
| `TripTrack/Views/Profile/ProfileCoverScrollProbe.swift` | missing |
| `TripTrack/Views/Profile/ProfileGarageHeader.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleStageState.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileHeroCard.kt` |
| `TripTrack/Views/Profile/ProfileHeroCard.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileHeroCard.kt` |
| `TripTrack/Views/Profile/ProfileHistoryCalendar.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileHistoryCalendar.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/me/MePresentationTests.kt` |
| `TripTrack/Views/Profile/ProfileHistoryMonthHeader.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileJournal.kt` |
| `TripTrack/Views/Profile/ProfileHistorySkeleton.swift` | missing |
| `TripTrack/Views/Profile/ProfilePhotoThumbnail.swift` | missing |
| `TripTrack/Views/Profile/ProfileRoadScene.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileHeroCard.kt` |
| `TripTrack/Views/Profile/ProfileRoutineTripsView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileJournal.kt` |
| `TripTrack/Views/Profile/ProfileSettingsSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsScreens.kt` |
| `TripTrack/Views/Profile/ProfileTripCardView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileJournal.kt` |
| `TripTrack/Views/Profile/ProfileTripTile.swift` | missing |
| `TripTrack/Views/Profile/ProfileView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeFeatures.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MeNavigation.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MeTab.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileIdentityStore.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileJournal.kt` |
| `TripTrack/Views/Profile/SettingsHintPopover.swift` | missing |
| `TripTrack/Views/Profile/SettingsOptionPicker.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeComponents.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/SheetAndToastTests.kt` |
| `TripTrack/Views/Profile/StatsScreenView.swift` | missing |
| `TripTrack/Views/Profile/SyncStatusMarker.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncState.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/account/AccountHelpersTests.kt` |
| `TripTrack/Views/Profile/SyncStatusSheetView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncCounts.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncStatusSheet.kt` |
| `TripTrack/Views/Profile/TipJarDebugView.swift` | missing |
| `TripTrack/Views/Profile/UsernameEditorSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/me/MyProfileScreen.kt` |
| `TripTrack/Views/Profile/VehicleDetailView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/GarageScreens.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/GarageViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehiclePassportSheets.kt` |
| `TripTrack/Views/Profile/WithMeSection.swift` | missing |
| `TripTrack/Views/Profile/WithMeSectionModel.swift` | missing |

## TripTrack/Views/Recovery

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Recovery/StoreRecoveryView.swift` | missing |

## TripTrack/Views/RemoteConfig

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/RemoteConfig/RemoteNoticeStack.swift` | missing |

## TripTrack/Views/Social

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Social/BlockedListView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/account/BlockedListSheet.kt` |
| `TripTrack/Views/Social/DiscoverView.swift` | missing |
| `TripTrack/Views/Social/FollowListView.swift` | missing |
| `TripTrack/Views/Social/PersonIdentity.swift` | missing |
| `TripTrack/Views/Social/PreviewNavigator.swift` | missing |
| `TripTrack/Views/Social/ProfilePreviewNav.swift` | missing |
| `TripTrack/Views/Social/ProfileTripsPager.swift` | missing |
| `TripTrack/Views/Social/ProfileUnavailableState.swift` | missing |
| `TripTrack/Views/Social/PublicGarageView.swift` | missing |
| `TripTrack/Views/Social/PublicJourneysView.swift` | missing |
| `TripTrack/Views/Social/PublicProfileView.swift` | missing |
| `TripTrack/Views/Social/PublicVehicleView.swift` | missing |
| `TripTrack/Views/Social/ReactionBurstSprite.swift` | missing |
| `TripTrack/Views/Social/ReactionPickerOverlay.swift` | missing |
| `TripTrack/Views/Social/ReactionsListSheet.swift` | missing |
| `TripTrack/Views/Social/ReportSheet.swift` | missing |
| `TripTrack/Views/Social/ShareLinkPresenter.swift` | missing |
| `TripTrack/Views/Social/SharePosterRenderer.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/social/ui/TripPosterView.kt` |
| `TripTrack/Views/Social/SharePosterView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/social/ui/TripPosterView.kt` |
| `TripTrack/Views/Social/SharedTripLinkSheet.swift` | missing |
| `TripTrack/Views/Social/SkeletonProfileView.swift` | missing |
| `TripTrack/Views/Social/StoryShareSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/social/ui/TripPosterSheet.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/social/ui/TripPosterView.kt` |

## TripTrack/Views/Theme

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Theme/AppFont.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/AppFont.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/AppType.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/ThemeTests.kt` |
| `TripTrack/Views/Theme/AppTheme.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/CardPress.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/Surfaces.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/AppTheme.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/Haptics.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/ThemeTests.kt` |
| `TripTrack/Views/Theme/ThemeManager.swift` | `app/src/test/kotlin/com/onezee/triptrack/AppShellTests.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/ThemeManager.kt`<br>`core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/ThemeTests.kt` |

## TripTrack/Views/Tracking

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Tracking/GPSIndicatorView.swift` | missing |
| `TripTrack/Views/Tracking/IdleHUDView.swift` | missing |
| `TripTrack/Views/Tracking/RecoveryPromptSheet.swift` | missing |
| `TripTrack/Views/Tracking/SlideToStartView.swift` | missing |
| `TripTrack/Views/Tracking/TrackingView.swift` | missing |
| `TripTrack/Views/Tracking/TripCompleteSummaryView.swift` | missing |
| `TripTrack/Views/Tracking/TripRevealedBlock.swift` | missing |
| `TripTrack/Views/Tracking/VehicleChip.swift` | missing |
| `TripTrack/Views/Tracking/VehiclePickerSheet.swift` | missing |

## TripTrack/Views/Trips

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Trips/CheckpointEditorSheet.swift` | missing |
| `TripTrack/Views/Trips/CompanionPhotoUploadController.swift` | missing |
| `TripTrack/Views/Trips/CompanionPhotoUploadModel.swift` | missing |
| `TripTrack/Views/Trips/CompanionsCardModel.swift` | missing |
| `TripTrack/Views/Trips/CompanionsPickerModel.swift` | missing |
| `TripTrack/Views/Trips/CompanionsPickerSheet.swift` | missing |
| `TripTrack/Views/Trips/CompanionsRosterSheet.swift` | missing |
| `TripTrack/Views/Trips/CompanionsSummaryModel.swift` | missing |
| `TripTrack/Views/Trips/DetailStatCard.swift` | missing |
| `TripTrack/Views/Trips/DraftTripBanner.swift` | missing |
| `TripTrack/Views/Trips/OwnTripPhotosModel.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripDetailViewModel.kt` |
| `TripTrack/Views/Trips/PhotoFullScreenView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehiclePhotoSheet.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/me/MePhotoViewer.kt` |
| `TripTrack/Views/Trips/PosterRouteCanvas.swift` | missing |
| `TripTrack/Views/Trips/SegmentEditorSheet.swift` | missing |
| `TripTrack/Views/Trips/TripCommentsScreen.swift` | missing |
| `TripTrack/Views/Trips/TripCommentsSection.swift` | missing |
| `TripTrack/Views/Trips/TripCompanionsSection.swift` | missing |
| `TripTrack/Views/Trips/TripDetailRoutePlaceholder.swift` | missing |
| `TripTrack/Views/Trips/TripDetailSections.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripDetailViewModel.kt` |
| `TripTrack/Views/Trips/TripDetailTopBar.swift` | missing |
| `TripTrack/Views/Trips/TripDetailView.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripSocialApi.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripDetailViewModel.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt`<br>`app/src/test/kotlin/com/onezee/triptrack/feature/trip/TripDetailStateTests.kt` |
| `TripTrack/Views/Trips/TripEditSheet.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripEditSheet.kt` |
| `TripTrack/Views/Trips/TripEnergyTiles.swift` | missing |
| `TripTrack/Views/Trips/TripMomentsTimeline.swift` | missing |

## TripTrackShared

| Swift file | Android counterpart files |
|---|---|
| `TripTrackShared/CheckpointIntent.swift` | missing |
| `TripTrackShared/DistanceUnit.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/DistanceUnit.kt` |
| `TripTrackShared/LiveActivityFormat.swift` | missing |
| `TripTrackShared/LiveActivityStrings.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrackShared/PauseTripIntent.swift` | missing |
| `TripTrackShared/StopTripIntent.swift` | missing |
| `TripTrackShared/TripActivityAttributes.swift` | missing |
| `TripTrackShared/TripIntentHandler.swift` | missing |
| `TripTrackShared/UnitNumber.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/Measure.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrackShared/VehicleAvatar.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleStage.kt`<br>`app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripDetailScreen.kt` |
| `TripTrackShared/VehicleSpriteMetrics.swift` | `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleStage.kt` |

## Android-only files

These files have no resolved Swift counterpart. An absent or unresolved comment also lands here; it is not evidence of an intentional product divergence.

Files: 155.

| Android file | Reason from docs/divergences.md |
|---|---|
| `app/src/debug/kotlin/com/onezee/triptrack/debug/MapDemoActivity.kt` | not documented |
| `app/src/debug/kotlin/com/onezee/triptrack/debug/MapDemoSeeds.kt` | not documented |
| `app/src/debug/kotlin/com/onezee/triptrack/debug/PlacesDemoSeed.kt` | not documented |
| `app/src/debug/kotlin/com/onezee/triptrack/debug/SeedActivity.kt` | not documented |
| `app/src/debug/kotlin/com/onezee/triptrack/debug/TripsDemoSeed.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/MainActivity.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/TripsFeature.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/TripsTabs.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountEntry.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncHost.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasComponents.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/WorldGlyphs.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/drafts/ui/DraftsListScreen.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/feed/ui/FeedScreen.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/journey/ui/JourneyDetailScreen.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/journey/ui/JourneyFormat.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/journey/ui/JourneySheets.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeArtwork.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/onboarding/OnboardingRoute.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesPanelModel.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PresentationDependencies.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripCharts.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripDetailParts.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripEditorSheets.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripMap.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripMomentsTimeline.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/recording/RecordingScreens.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/shell/AppShell.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/shell/LanguageManager.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/shell/PlaceholderScreens.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/shell/TabStore.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/DetailComponents.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/PhotoImages.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/PhotoViewer.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/PullToRefresh.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/RegionDisplay.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/RoutePreview.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/TripExportShare.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/TripMaps.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/TripMessages.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/TripText.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/cards/TripCards.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/ui/navigation/TabNavigator.kt` | not documented |
| `app/src/release/kotlin/com/onezee/triptrack/feature/me/MeLaunch.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/MeSettingsPickerTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/OnboardingDone.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/ShellScreenshotTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/MeDiagnosticsTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/WorldTest.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/account/AccountScreenshotTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/account/AccountTestCase.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/account/AccountViewModelTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/account/SyncHostTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MeInteractionTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MeScreenshotTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/replay/LaunchArtworkTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/trips/TripsBehaviourTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/trips/TripsScreenTest.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/trips/TripsScreenshotTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/world/AtlasRulesTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldBehaviourTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldEmptyScreenshotTests.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldScreenTest.kt` | not documented |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldScreenshotTests.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/DataEnvironment.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/DataFlags.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/PendingSyncRecovery.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/Converters.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/DiscoveryEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/JourneyEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/MapEntities.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/PlaceEntities.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TrackPointEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TripCheckpointEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TripEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TripPhotoEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/UserSettingsEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/VehicleEntity.kt` | not documented |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/VehiclePhotoEntity.kt` | not documented |
| `core/data/src/test/kotlin/com/onezee/triptrack/data/RoomTestCase.kt` | not documented |
| `core/data/src/test/kotlin/com/onezee/triptrack/data/TripRepositoryEditTests.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/Designsystem.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/ContentSizedSheet.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/EdgeScrims.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/Glyph.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/GlyphsMore.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/NavCircleIcon.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/SettingsOptionPicker.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/Text.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/navigation/TabBarVisibility.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/AppMetrics.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/DistanceUnitLocal.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/Shadow.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/Shapes.kt` | not documented |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/theme/TripTrackTheme.kt` | not documented |
| `core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/ComponentScreenshotTests.kt` | not documented |
| `core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/TabBarScreenshotTests.kt` | not documented |
| `core/designsystem/src/test/kotlin/com/onezee/triptrack/designsystem/TestSupport.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/auto/ActivityTransitions.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/auto/AutoTripReceiver.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/drafts/DraftDecisions.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/permissions/PermissionPlan.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/permissions/RecordingHealth.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/provider/PlatformLocationSource.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/recording/LiveSpeed.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/recording/RawFixLog.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/recording/RecorderState.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/recording/TripFinisher.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/recording/TripRecorder.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/service/NotificationActionReceiver.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/service/RecordingNotifications.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/world/StoreRoadCollector.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/world/TrackPostProcessing.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/world/TripNamer.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/world/TripRewards.kt` | not documented |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/world/TripWorldEntry.kt` | not documented |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecorderTestCase.kt` | not documented |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingTextsTests.kt` | not documented |
| `core/map/src/main/cpp/fog_jni.cpp` | not documented |
| `core/map/src/main/cpp/fog_layer.cpp` | not documented |
| `core/map/src/main/cpp/maplibre/include/mln/style/layers/custom_layer_host.hpp` | not documented |
| `core/map/src/main/cpp/maplibre/include/mln/style/layers/custom_layer_init_parameters.hpp` | not documented |
| `core/map/src/main/cpp/maplibre/include/mln/style/layers/custom_layer_render_parameters.hpp` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/MapController.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/MapHost.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/MapHostState.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/MapPoints.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/camera/CameraFraming.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/camera/MapCameraCommand.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogLayer.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogLook.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogMesh.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogRules.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/MapLibreAbi.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/NativeFog.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/marker/MapMarker.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/marker/MarkerLayers.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/route/RouteAppearance.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/route/RouteLayers.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/route/SpeedColorScale.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/snapshot/MapSnapshots.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/vein/VeinLayers.kt` | not documented |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/FogTestKit.kt` | not documented |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/fog/FogMeshTests.kt` | not documented |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/fog/FogRulesTests.kt` | not documented |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/fog/MapLibreAbiPinTest.kt` | not documented |
| `core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/IosObjectSerializer.kt` | not documented |
| `core/model/src/test/kotlin/com/onezee/triptrack/model/IosFixtures.kt` | not documented |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/PhotoEncoder.kt` | not documented |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncEngine.kt` | not documented |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncScheduler.kt` | not documented |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/PullApplierTests.kt` | not documented |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/PushPullTests.kt` | not documented |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/SyncEngineTests.kt` | not documented |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/SyncTestCase.kt` | not documented |

## Marker warnings

Use a repository-relative Swift path to resolve an ambiguous name. Name the `.swift` file explicitly for symbol-only and non-Swift references.

| Android file | Reference | Issue |
|---|---|---|
| `app/src/debug/kotlin/com/onezee/triptrack/debug/MapDemoSeeds.kt` | `DebugMapSeed.densify.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/MainActivity.kt` | `TripTrackApp gates the whole window on hasCompletedOnboarding.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountEntry.kt` | `ProfileView.guestSignInCard, ProfileSettingsSheet (the «Аккаунт и синхронизация» row with its SyncStatusMarker), CloudSyncView presentation` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountFormat.kt` | `AccountFormat.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/AccountFormat.kt` | `ContentFilter.sanitize / clampedDisplayName.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/AuthService.kt` | `prepareForIdentity. A soft expiry preserved the previous account's queue, consent and name for the SAME person; a different account must not inherit them. A guest's library (no previous account) is the phone's and is never purged.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/AuthService.kt` | `publicProfileGeneration. A /auth/me read taken before a toggle must not revert it.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/SignInViews.kt` | `AppConfig.termsURL / privacyPolicyURL. The documents the person accepts by continuing.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncHost.kt` | `TripTrackApp service wiring (SyncCoordinator.shared, SyncQueue.shared, SyncEnqueuer), AuthService.performFirstSync` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncHost.kt` | `performFirstSync. Signing in alone never mass-uploads personal content: only with Cloud Sync on is everything re-marked (the data-loss-safe full mirror), and recovery turns the flags into the same trip, car, settings, journey and photo operations iOS enqueues. Without it the pass still pushes trips made public by hand.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/account/SyncStatusSheet.kt` | `.presentationDetents([.medium, .large]): at least half the screen, taller with a long queue.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/AtlasPresentation.kt` | `RiddleHint.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `AtlasMapAppearance.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `Coordinator.bindViewModel. A map consumer handles this and acknowledges its revision.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `DiscoveryStore / RiddleHint.plan. Discovery rules stay with their owner.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `LocationManager.authorizationStatus. Atlas observes recording's source and never starts GPS.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `MapCameraCommand.userLocation. The map supplies centring on its existing location source.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `MyMapViewModel.Selection.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `MyMapViewModel.reloadDiscoveries.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/MyMapViewModel.kt` | `auto-zoom to the region only from the country view, where that IS the gesture; at street level a tap that misses the road is a miss, and flinging the camera out loses the place.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/atlas/ui/AtlasComponents.kt` | `AtlasSheetTitleRow, AtlasSubstateHeader, AtlasStatTrio, AtlasTripRow, MapTripThumb, AtlasPlaceCard, AtlasMapControls, AtlasBetaChip, FlowRow chips, SkeletonPlaceholder(.atlasStats / .placeSuggestion).` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/feed/FeedState.kt` | `SocialFeedTripJourney.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripCommentsViewModel.kt` | `TripComment.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/feed/TripSocialApi.kt` | `SocialFeedTrip.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/garage/GarageViewModel.kt` | `VehicleDetailView.Passport.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehicleEnergyDraft.kt` | `VehicleEditFormView fuelInputRow / storedConsumption / storedElectricConsumption.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/garage/VehiclePassportSheets.kt` | `VehiclePrivacySheet.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/AchievementsScreen.kt` | `BadgeDetailView.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/AchievementsScreen.kt` | `BadgesView.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeArtwork.kt` | `Assets.xcassets. Generated by tools/port_me_assets.py.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeFeatures.kt` | `SettingsManager and ProfileView StateObjects. AppGraph only supplies shared stores.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeNavigation.kt` | `MeDest / ProfileView.push. A second tap on the same subject is idempotent.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeNavigation.kt` | `ProfileView.mePath. The shared trip screens push into this same stack, never a nested one.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MePhotoViewer.kt` | `VehiclePhotoImage cache. Signed URLs rotate; the object path is stable for a photo id.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt` | `BadgesView.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MeViewModel.kt` | `SettingsManager.togglePinnedTrip / syncProfileToServer. Public identity sync is independent of Cloud Sync.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MyProfileScreen.kt` | `MyProfileView.avatars, CountryChoice.all, UsernameEditorSheet.normalize.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/MyProfileScreen.kt` | `ProfileBackgroundPicker.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileAuxiliaryScreens.kt` | `LevelsView.level / GamificationModels.DriverRank. Ranges derive from the shared level rule.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileBackgroundColors.kt` | `PlusGate. Android launch has no purchase storefront or entitlement provider.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileBackgroundColors.kt` | `ProfileBackground.effective. Saved premium choices survive; launch storefront shows the ordinary card.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileHistoryCalendar.kt` | `ProfileHistoryCalendar.handleDayTap. A full range restarts on the next tap.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileIdentityStore.kt` | `SettingsManager.profileJournalRevision. One writer for local journal and public identity sync.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileMapPreview.kt` | `MapSnapshotPreview cache. Geometry is part of the key: same-count route edits must redraw.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfilePhotoScreens.kt` | `ProfilePhotoFullScreenView.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfilePhotoScreens.kt` | `ProfilePhotosView.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfilePhotoStore.kt` | `ProfilePhotoStore network seam (MockURLProtocol in ProfilePhotoStoreTests).` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfilePhotoStore.kt` | `ProfilePhotoStore. All metadata mutations run on the supplied UI dispatcher.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfilePhotoStore.kt` | `ProfilePhotoStore.Photo. Upload object keys are never used as display URLs.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileProgressStore.kt` | `ProfileProgress.init(entity:). An unknown XP stays absent instead of becoming zero on the wire.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileProgressStore.kt` | `SettingsManager.applyRemoteProfileProgress / profileProgressForSync` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileRegionDisplay.kt` | `RegionDisplay.Entry. The generated table includes full names, short names and recognition aliases.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileTextEditor.kt` | `ContentFilter.validate(.displayName / .tripNote). Reuse the existing sanitizer and denylist.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/me/ProfileTextEditor.kt` | `NameEditorSheet.maxLength / AboutEditorSheet.maxLength.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/onboarding/OnboardingRoute.kt` | `OnboardingView.finishOnboarding, TripTrackApp (the onboarding branch of the window)` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlaceDetailViewModel.kt` | `TripFocus.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesPanelModel.kt` | `PlacesView's @State (stop, stopBeforeSelection, stopBeforeSearch, selectedPlaceId, selectedHintId)` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesTabViewModel.kt` | `PlaceManager.createPlace. The recording owner supplies reconciliation/backfill.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/places/PlacesTabViewModel.kt` | `PlaceSuggestions.buildDetached. The algorithm is the shared core:logic implementation.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeComponents.kt` | `@StateObject. A pushed screen owns its tasks; popping it cancels them.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeComponents.kt` | `GarageView.navRow / NavCircleIcon. Keep the house circle while drawing the missing plus glyph locally.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/MeDiagnostics.kt` | `DebugLogExporter.recentEntries(hoursBack:48, limit:800).` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/NotificationPreferencesViewModel.kt` | `APIClient.post. Use the already ported envelope and DTOs.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PresentationDependencies.kt` | `Notification.Name (tripRecordingEnded, syncPullCompleted, placesChanged, homeChanged).` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PresentationDependencies.kt` | `NotificationCenter publishers, UserDefaults in the screen view models.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PresentationDependencies.kt` | `NotificationCenter. Recording/sync owners supply their events without a service dependency.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PresentationDependencies.kt` | `UserDefaults. Raw keys remain the iOS storage contract.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PresentationDependencies.kt` | `UserDefaults.standard.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PresentationDependencies.kt` | `managed-object/manager change notifications. No global singleton is needed by a screen.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/PrivacySettingsViewModel.kt` | `APIClient.post.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/ProductAnalytics.kt` | `ProductAnalytics URLSession seam.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/ProductAnalytics.kt` | `ProductAnalytics. A retry replaces one bounded snapshot, never adds events twice.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/ProductAnalytics.kt` | `ProductAnalytics.flush. ApiClient's usage transport sends no credentials.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/RecordingSettingsScreens.kt` | `HomeSettingsView.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt` | `AvgSpeedMode.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt` | `HomeSettings.activeZone.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt` | `SettingsView.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SettingsManager.kt` | `cloudSyncEnabled / homePrivacyZoneChanged. Sync and recording owners implement side effects.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/settings/SyncSettingsEffects.kt` | `CloudSyncView.enableCloudSync, HomeManager.homePrivacyZoneChanged, AutoTripService settings publishers` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt` | `AppDialog.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/TripState.kt` | `ToastItem.swift` | not found |
| `app/src/main/kotlin/com/onezee/triptrack/feature/trip/ui/TripCharts.kt` | `HStack with Spacer(minLength: 8), each label shrinking to 0.8 before it truncates.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/shell/PlaceholderScreens.kt` | `an 18 pt semibold chevron.left in white.` | no Swift filename |
| `app/src/main/kotlin/com/onezee/triptrack/ui/PhotoImport.kt` | `PhotoMetadata.capturedAt. TIFF DateTime is an edit time; an unzoned shot uses the phone's zone.` | no Swift filename |
| `app/src/release/kotlin/com/onezee/triptrack/feature/me/MeLaunch.kt` | `#if DEBUG. Release never reads seed or road-time arguments.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/MeDiagnosticsTests.kt` | `DebugLogExporter.recentEntries(hoursBack:48, limit:800).` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/VehicleFormTests.kt` | `VehicleCatalogSelectionTests.swift` | not found |
| `app/src/test/kotlin/com/onezee/triptrack/feature/WorldTest.kt` | `PersistenceController(inMemory: true) screen test fixtures.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/WorldTest.kt` | `UserDefaults(suiteName:).` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/account/AccountViewModelTests.kt` | `CloudSyncView.cloudSyncBinding / signOutActions / performDeleteAccount (the screen's decisions)` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/account/SyncHostTests.kt` | `TripTrackApp service wiring, SyncEnqueuer, SyncCoordinator foreground trigger, AuthService.performFirstSync` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/BadgeCopyTests.kt` | `BadgeDetailView.swift` | not found |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MeInteractionTests.kt` | `GarageProfileShotTests (DRIVE2, picker hit targets), VehicleCatalogPickerShotTests, OdometerFieldTests` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MePresentationTests.kt` | `ProfilePreviewNavTests.testDifferentDestinationStillPushes.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MePresentationTests.kt` | `ProfilePreviewNavTests.testDoubleTapDoesNotStackTheSameScreenTwice.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MePresentationTests.kt` | `ProfilePreviewNavTests.testSameScreenDeeperInThePathStillPushes.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MeScreenshotTests.kt` | `-seed-garage-demo, -seed-profile-journal. Fixed ids and dates keep every render deterministic.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/feature/me/MeScreenshotTests.kt` | `ProfileCardShotTests, GarageProfileShotTests, ProfileMonthlyJournalTests` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/replay/LaunchArtworkTests.kt` | `Tools/AppIcon/D2.svg, Tools/build_app_icons.py, Info.plist UILaunchScreen.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/trips/TripsBehaviourTests.kt` | `TripJourneyNavigationTests, DraftsFlowShotTests, TripDetailOpeningTests (the asserted steps).` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/trips/TripsBehaviourTests.kt` | `TripJourneyNavigationTests.testOwnTripCanOpenItsJourneyAndReturnToItsMap.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/trips/TripsScreenTest.kt` | `TripTrackUITests' tours on the -seed-map-demo library, here composed under Robolectric.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/trips/TripsScreenshotTests.kt` | `TripDetailOpeningTests, DraftsFlowShotTests, JourneyComposerShotTests (the kept frames).` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/world/AtlasRulesTests.kt` | `AtlasPlaceTapTests (the target), MapZoomLevel, Coordinator.trips(near:) and handleTap after the pins, syncTripPins, PlaceUsuallyLineTests.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldBehaviourTests.kt` | `AtlasRegionTests (exits), AtlasSlotTests (the gesture's rules on screen), AtlasPlaceTapTests (the card's way to Places), PlacesStatesShotTests (search as a modal state), PlacesTabShotTests.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldEmptyScreenshotTests.kt` | `AtlasStatesShotTests (22 loading, 10 empty) and PlacesStatesShotTests (13/14 no places, 15 no trips, 23 suggestions counting).` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldScreenTest.kt` | `TripTrackUITests' Atlas and Places tours on -seed-map-demo -seed-places-rich, composed here under Robolectric.` | no Swift filename |
| `app/src/test/kotlin/com/onezee/triptrack/world/WorldScreenshotTests.kt` | `AtlasStatesShotTests, AtlasAppearanceSheetShotTests, PlacesTabShotTests, PlacesStatesShotTests.` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/DataEnvironment.kt` | `SettingsManager.shared.cloudSyncEnabled / localUserId, HomeSettings.load().activeZone, FuelCurrency.current, Date()` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/DataFlags.kt` | `UserDefaults latches (territory_backfill_done, didMigrateUserId, archiveRemovedV1, ...)` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/PendingSyncRecovery.kt` | `SyncCoordinator.recoverPendingEntities, pendingJourneyOperations, pendingVehiclePhotoOperations` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/Converters.kt` | `TripTrack.xcdatamodeld (UUID and Date attribute storage)` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/DiscoveryEntity.kt` | `TripTrack v24.xcdatamodel, entity DiscoveryEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/JourneyEntity.kt` | `TripTrack v24.xcdatamodel, entity JourneyEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/MapEntities.kt` | `TripTrack v24.xcdatamodel, entities VisitedGeohashEntity, GeocodeCacheEntity, RoadEntity, RevealedCellEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/PlaceEntities.kt` | `TripTrack v24.xcdatamodel, entities PlaceEntity, PlacePassEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TrackPointEntity.kt` | `TripTrack v24.xcdatamodel, entity TrackPointEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TripCheckpointEntity.kt` | `TripTrack v24.xcdatamodel, entity TripCheckpointEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TripEntity.kt` | `TripTrack v24.xcdatamodel, entity TripEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/TripPhotoEntity.kt` | `TripTrack v24.xcdatamodel, entity TripPhotoEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/UserSettingsEntity.kt` | `TripTrack v24.xcdatamodel, entity UserSettingsEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/VehicleEntity.kt` | `TripTrack v24.xcdatamodel, entity VehicleEntity` | no Swift filename |
| `core/data/src/main/kotlin/com/onezee/triptrack/data/db/entity/VehiclePhotoEntity.kt` | `TripTrack v24.xcdatamodel, entity VehiclePhotoEntity` | no Swift filename |
| `core/data/src/test/kotlin/com/onezee/triptrack/data/RoomTestCase.kt` | `the &#96;PersistenceController(inMemory: true)&#96; + &#96;CoreDataTripRepository&#96; setUp shared by the store tests` | no Swift filename |
| `core/data/src/test/kotlin/com/onezee/triptrack/data/TripRepositoryEditTests.kt` | `the editing half of CoreDataTripRepository (no single iOS test class: the rules live in the repository's comments, PrivacyFlowE2ETests and SyncHealTests); the inputs follow those tests.` | no Swift filename |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/GlyphsMore.kt` | `the system SF Symbols of the trip screens (feed, trip, journey, drafts), redrawn on the same 24-unit grid and at the same semibold weight as Glyph.kt. Outline symbols are strokes; &#96;fill&#96; symbols are solid, and the ones that carry a mark (the triangle, the plus disc) cut it out of the solid.` | no Swift filename |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/SettingsOptionPicker.kt` | `a 13 pt semibold symbol.` | no Swift filename |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/SettingsOptionPicker.kt` | `a 13 pt semibold xmark.` | no Swift filename |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/components/Toast.kt` | `an 18 pt semibold symbol.` | no Swift filename |
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/LocalizedDateFormatter.kt` | `LocalizedDateFormatter's per-language DateFormatter table.` | no Swift filename |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/recording/TripRecorder.kt` | `recordedLocationPublisher. Only points admitted to the saved route draw the live trail.` | no Swift filename |
| `core/location/src/main/kotlin/com/onezee/triptrack/location/world/TripWorldEntry.kt` | `SettingsManager.earnedProgressDidChange, the settings row carries XP and level.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/AutoRecordSettingsTests.kt` | `VehicleStateTests.testDeletingAVehicleTakesItsStereoBindingWithIt.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/AutoTripControllerTests.kt` | `AutoStopOnPauseTests.testAPausedTripIsNeverStoppedByBluetooth.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/AutoTripControllerTests.kt` | `testAutoModeStillEndsAfterARealDrive.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/AutoTripControllerTests.kt` | `testRemindModeAsksAndDoesNotAct, for a trip the person started.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/AutoTripControllerTests.kt` | `testTheShopStopThatBrokeTheDrive. A timer armed before the pause dies with the pause.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/PermissionPlanTests.kt` | `testDoubleTapCannotIssueAnotherPermissionRequest / testLateCompletionFromPreviousPage….` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` | `RoadCollectionManager.processTrip through TripWorldEntry.rewards.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` | `SettingsManager.recordableVehicleId, the last check at the stamp.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` | `TrackReplayTests, the tunnel: the gap is filled straight at the finish and waits for a road.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` | `TripManager.handleNewLocation, «every 10 points or every 15 seconds».` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` | `TripManager.isPaused, RecordingBreaksPersistenceTests.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` | `TripWorldEntry.route, a draft gets its question and stays out of the world.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingLifecycleTests.kt` | `the provider gate (&#96;FixGate&#96;) runs before the recording and its verdict is journaled.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecordingPiecesTests.kt` | `testCrashSecondsAgoResumesWithoutAsking / testCrashMinutesAgoStillResumesWithoutAsking.` | no Swift filename |
| `core/location/src/test/kotlin/com/onezee/triptrack/location/RecoveryAndDraftTests.kt` | `DraftDecisionQueueTests.` | no Swift filename |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasGeometry.kt` | `perpendicularDistance. Degrees, to the segment rather than the infinite line.` | no Swift filename |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FogGeometry.kt` | `CoreGraphics CGRect / MapKit MKMapRect (pure coordinate rectangle).` | no Swift filename |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt` | `CLLocation.distance(from:). WGS84 inverse distance, independent of Android APIs.` | no Swift filename |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/Measure.kt` | `GarageFormat.swift` | not found |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlacesPresentation.kt` | `Foundation CharacterSet.whitespaces / whitespacesAndNewlines. Foundation includes zero-width space; JVM trim also removes unrelated control separators.` | no Swift filename |
| `core/logic/src/test/kotlin/com/onezee/triptrack/logic/RecordingInterferenceTests.kt` | `testInterruptedSparseDriveEndingStoppedSurvivesOrphanRecovery. Durability/recovery belong to the repository; this checks its input.` | no Swift filename |
| `core/map/src/main/cpp/fog_frame.hpp` | `FogCellGrid.make. A world-anchored grid of map points that doubles until a cell is at least minScreenPoints on screen, at most maxDoublings times; side 0 means no grid in this frame.` | no Swift filename |
| `core/map/src/main/cpp/fog_state.hpp` | `FogMetalVeil.frames / encodeSeconds and the budget numbers. Read by the demo screen.` | no Swift filename |
| `core/map/src/main/cpp/fog_state.hpp` | `FogRevealCircle. The live hole around the car, tied to the GROUND: projected every frame.` | no Swift filename |
| `core/map/src/main/cpp/fog_state.hpp` | `FogVeilPainter.Palette (.mist #EEEEEC × 0.87, .night #262B36 × 0.70) and the cells flag of AtlasMapAppearance.Style.` | no Swift filename |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogLook.kt` | `AtlasMapAppearance.usesCells. The opened world is quantised into world-anchored squares.` | no Swift filename |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogLook.kt` | `AtlasMapAppearance.usesDarkFog. Only "Ночь" has a dark fog; "Клетки" shares the mist.` | no Swift filename |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogLook.kt` | `FogVeilPainter.Palette (.mist, .night): the flat colour and nominal opacity of the Metal fog. The raster palettes' other colours (haze, border, opacity range) belong to the raster veil, which Android does not have.` | no Swift filename |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/fog/FogRules.kt` | `FogMetalVeil.featherRatio, FogMesh.chunkSize, FogVeilRenderer.lod thresholds, VeilRevealMask.solidFraction, FogVeilRenderer.revealMetres. The corridor and cell numbers come from core:logic, where FogVeilRenderer and FogCellGrid are ported; the native layer receives all of them once (&#96;nativeRules&#96;), so the picture has one source of numbers.` | no Swift filename |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/replay/PlaybackLayers.kt` | `MapCarMarker.shadowDiameter, a circular shadow that does not turn with the body.` | no Swift filename |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/replay/ReplayBarState.kt` | `ReplayBarState.Event` | no Swift filename |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/camera/MapCameraPaddingTests.kt` | `GeoBounds.mapRect (300 m) and RouteMapView.floored (400 m).` | no Swift filename |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/fog/FogMeshTests.kt` | `FogMesh.build. What the GPU receives: Float32 offsets from a chunk origin, the chunk picked by the segment's midpoint, true chunk bounds for culling, one range per chunk.` | no Swift filename |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/fog/FogRulesTests.kt` | `FogVeilPainter.Palette.mist (#EEEEEC × 0.87) and .night (#262B36 × 0.70).` | no Swift filename |
| `core/map/src/test/kotlin/com/onezee/triptrack/map/route/RouteGeometryTests.kt` | `RouteMapView.makeUIView, the part before MapKit: segments, preview handling, plain lines.` | no Swift filename |
| `core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/IosObjectSerializer.kt` | `custom Codable implementations in Models/` | no Swift filename |
| `core/model/src/test/kotlin/com/onezee/triptrack/model/IosFixtures.kt` | `TripTrackTests/Fixtures and inline JSON fixtures` | no Swift filename |
| `core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt` | `Services/Social/SocialCommentsStore.swift` | not found |
| `core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt` | `SocialCommentsStore.swift` | not found |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/DiscussionArchive.kt` | `DiscussionArchive.save, also called after a successful comments first-page read.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/LastSyncedAtStore.kt` | `PersistenceController.storeIdentity (NSStoreUUIDKey). Born inside SQLite, never copied from preferences.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/ManualTripTrackLoader.kt` | `ManualTripTrackLoader.loadIfNeeded. The caller supplies its worker dispatcher.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/PhotoEncoder.kt` | `APISyncTransport.encodePhotoVariants, UIImage.resized, R2PhotoStorage.stripImageMetadata.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/PhotoEncoder.kt` | `UIImage drawing + JPEG encoder. Drawing pixels strips EXIF/GPS/IPTC/XMP.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/QueuePersistence.kt` | `SyncCoordinator.recoverPendingEntities; Android also retains failed attempts across death.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/QueuePersistence.kt` | `pending-entity recovery. Scope includes API host, account and database identity.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncEngine.kt` | `service wiring in TripTrackApp + SyncCoordinator.start. One engine per account/store/API host.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncScheduler.kt` | `SyncCoordinator.runFullSync. Created by SyncWorkerFactory from the app's manual AppGraph.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncScheduler.kt` | `SyncCoordinator.start / foregroundTimer; Android persistent scheduling uses WorkManager.` | no Swift filename |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/SyncScheduler.kt` | `application service wiring. Install in WorkManager Configuration before its initializer runs.` | no Swift filename |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/PullApplierTests.kt` | `PullApplierStatsCacheTests, TombstoneGuardTests, TripSegmentSyncTests, JourneySyncConflictTests, RecordingBreaksPersistenceTests, PullApplierDiscoveryTests.` | no Swift filename |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/PushPullTests.kt` | `APISyncTransport, CheckpointSyncFlagTests, VehicleEnergyWireTests, VehicleSyncConflictTests.` | no Swift filename |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/SchedulingAndRasterTests.kt` | `APISyncTransport.encodePhotoVariants, R2PhotoStorage.stripImageMetadata.` | no Swift filename |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/SyncEngineTests.kt` | `TripTrackApp service wiring, SyncCoordinator recovery. Exercises the production disk path.` | no Swift filename |
| `core/sync/src/test/kotlin/com/onezee/triptrack/sync/SyncTestCase.kt` | `PersistenceController(inMemory:) + MockURLProtocol; Android uses real Room and HTTP.` | no Swift filename |
