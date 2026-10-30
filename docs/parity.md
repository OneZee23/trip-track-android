# iOS / Android file parity

Regenerate: `python3 tools/parity/parity.py report`.

Swift inventory: `TripTrack/` and `TripTrackShared/`. Android inventory: Kotlin, Java and C/C++ files under module `src/` directories, including tests. Build output and `spikes/` are excluded.

Links come from `// iOS:` comments. A link can cover part of a file; it does not prove behavioural parity. References to `TripTrackTests/` and `TripTrackUITests/` are resolved but excluded from the Swift area counts.

Swift files: 648. With Android references: 202. Missing: 446.

| iOS area | Swift files | With Android references | Missing |
|---|---:|---:|---:|
| `TripTrack/App` | 3 | 0 | 3 |
| `TripTrack/CarPlay` | 5 | 0 | 5 |
| `TripTrack/Common` | 2 | 2 | 0 |
| `TripTrack/Config` | 1 | 0 | 1 |
| `TripTrack/Localization` | 12 | 12 | 0 |
| `TripTrack/Localization/Translations` | 12 | 11 | 1 |
| `TripTrack/Models` | 85 | 78 | 7 |
| `TripTrack/Models/Social` | 7 | 6 | 1 |
| `TripTrack/Models/Sync` | 10 | 10 | 0 |
| `TripTrack/Networking` | 9 | 8 | 1 |
| `TripTrack/Persistence` | 5 | 5 | 0 |
| `TripTrack/Services` | 77 | 29 | 48 |
| `TripTrack/Services/Discoveries` | 12 | 4 | 8 |
| `TripTrack/Services/Export` | 2 | 0 | 2 |
| `TripTrack/Services/Intents` | 3 | 0 | 3 |
| `TripTrack/Services/ManualTrip` | 3 | 0 | 3 |
| `TripTrack/Services/Plus` | 14 | 9 | 5 |
| `TripTrack/Services/RemoteConfig` | 3 | 3 | 0 |
| `TripTrack/Services/Reveal` | 8 | 3 | 5 |
| `TripTrack/Services/Social` | 5 | 1 | 4 |
| `TripTrack/Services/Sync` | 10 | 5 | 5 |
| `TripTrack/Utils` | 8 | 2 | 6 |
| `TripTrack/ViewModels` | 7 | 0 | 7 |
| `TripTrack/Views` | 1 | 0 | 1 |
| `TripTrack/Views/Admin` | 2 | 1 | 1 |
| `TripTrack/Views/Auth` | 2 | 0 | 2 |
| `TripTrack/Views/Badges` | 8 | 0 | 8 |
| `TripTrack/Views/Components` | 50 | 0 | 50 |
| `TripTrack/Views/Drafts` | 6 | 0 | 6 |
| `TripTrack/Views/Feed` | 3 | 0 | 3 |
| `TripTrack/Views/Garage` | 25 | 1 | 24 |
| `TripTrack/Views/Groups` | 5 | 0 | 5 |
| `TripTrack/Views/Journeys` | 11 | 1 | 10 |
| `TripTrack/Views/ManualTrip` | 12 | 0 | 12 |
| `TripTrack/Views/Map` | 2 | 0 | 2 |
| `TripTrack/Views/MyMap` | 32 | 2 | 30 |
| `TripTrack/Views/MyMap/Atlas` | 13 | 0 | 13 |
| `TripTrack/Views/MyMap/FogMetal` | 10 | 1 | 9 |
| `TripTrack/Views/MyMap/Home` | 3 | 0 | 3 |
| `TripTrack/Views/Navigation` | 4 | 0 | 4 |
| `TripTrack/Views/Notifications` | 3 | 0 | 3 |
| `TripTrack/Views/Onboarding` | 1 | 0 | 1 |
| `TripTrack/Views/Places` | 7 | 0 | 7 |
| `TripTrack/Views/Places/Slot` | 5 | 0 | 5 |
| `TripTrack/Views/Plus` | 26 | 2 | 24 |
| `TripTrack/Views/Profile` | 43 | 3 | 40 |
| `TripTrack/Views/Recovery` | 1 | 0 | 1 |
| `TripTrack/Views/RemoteConfig` | 1 | 0 | 1 |
| `TripTrack/Views/Social` | 22 | 0 | 22 |
| `TripTrack/Views/Theme` | 3 | 0 | 3 |
| `TripTrack/Views/Tracking` | 9 | 0 | 9 |
| `TripTrack/Views/Trips` | 24 | 0 | 24 |
| `TripTrackShared` | 11 | 3 | 8 |

## TripTrack/App

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/App/AppDelegate.swift` | missing |
| `TripTrack/App/AppTab.swift` | missing |
| `TripTrack/App/TripTrackApp.swift` | missing |

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
| `TripTrack/Config/AppConfig.swift` | missing |

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
| `TripTrack/Localization/AppStrings.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceTokenRules.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/Measure.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProText.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/MeasureTestKit.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/ResourcePolicyTests.kt` |
| `TripTrack/Localization/AppStringsCore.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppLanguage.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppStrings.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/LocalizedDateFormatter.kt`<br>`core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/AtlasCopy.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/ResourceAppStrings.kt` |
| `TripTrack/Localization/LanguageManager.swift` | `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/AppLanguage.kt` |

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
| `TripTrack/Models/AtlasPresentation.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasPlaces.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/AtlasPresentation.kt` |
| `TripTrack/Models/AutoRecordMode.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/AutoRecordMode.kt` |
| `TripTrack/Models/AutoTripPolicy.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AutoTripPolicy.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/AutoTripPolicy.kt` |
| `TripTrack/Models/Badge.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeDefinitions.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Badge.kt` |
| `TripTrack/Models/BadgeDefinitions.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeDefinitions.kt` |
| `TripTrack/Models/BadgeFigure.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeManager.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/BadgeFigure.kt` |
| `TripTrack/Models/CLLocationCoordinate2D+Equatable.swift` | missing |
| `TripTrack/Models/CarHeadingPolicy.swift` | missing |
| `TripTrack/Models/CheckpointReading.swift` | missing |
| `TripTrack/Models/ConsumptionUnit.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ConsumptionUnit.kt` |
| `TripTrack/Models/DashboardUnits.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/DashboardUnits.kt` |
| `TripTrack/Models/Discovery.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/Discovery.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/GeoPoint.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ModelIdentity.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/GeoPointSerializer.kt` |
| `TripTrack/Models/DistanceUnit+Current.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt` |
| `TripTrack/Models/Drive2Link.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Drive2Link.kt` |
| `TripTrack/Models/ElectricUnit.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ElectricUnit.kt` |
| `TripTrack/Models/EnergyEstimate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/EnergyEstimate.kt` |
| `TripTrack/Models/FixGate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FixGate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/FixGate.kt` |
| `TripTrack/Models/GamificationModels.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RewardMathTests.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/GamificationModels.kt` |
| `TripTrack/Models/GapFill.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GapFill.kt` |
| `TripTrack/Models/HistoryFolding.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/HistoryFolding.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/HistoryFolding.kt` |
| `TripTrack/Models/HomeSettings.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/HomeSettings.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/Journey.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyWindow.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Journey.kt` |
| `TripTrack/Models/JourneyAggregate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyAggregate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/JourneyAggregate.kt` |
| `TripTrack/Models/JourneySuggester.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneySuggester.kt` |
| `TripTrack/Models/Measure.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/Measure.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Measure.kt` |
| `TripTrack/Models/OdometerField.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt` |
| `TripTrack/Models/PhotoMetadata.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PhotoMetadata.kt` |
| `TripTrack/Models/PhotoUploadStatus.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PhotoUploadStatus.kt` |
| `TripTrack/Models/Place.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceMatcher.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/ModelIdentity.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Place.kt` |
| `TripTrack/Models/PlaceChip.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceChip.kt` |
| `TripTrack/Models/PlaceLastPass.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceLastPass.kt` |
| `TripTrack/Models/PlaceListItem.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceListItem.kt` |
| `TripTrack/Models/PlaceMatcher.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GapFill.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceMatcher.kt` |
| `TripTrack/Models/PlacePass.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceMatcher.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlacePass.kt` |
| `TripTrack/Models/PlaceScreen.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceScreen.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceScreen.kt` |
| `TripTrack/Models/PlaceStats.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceStats.kt` |
| `TripTrack/Models/PlaceSuggestions.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlaceSuggestions.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlaceSuggestions.kt` |
| `TripTrack/Models/PlaceUsuallyLine.swift` | missing |
| `TripTrack/Models/PlacesPresentation.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlacesPresentation.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PlacesPresentation.kt` |
| `TripTrack/Models/Powertrain.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/Powertrain.kt` |
| `TripTrack/Models/PreparedProfileHistory.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProfileHistory.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/PreparedProfileHistory.kt` |
| `TripTrack/Models/PrivacyZone.swift` | missing |
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
| `TripTrack/Models/SegmentHistory.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripSegments.kt` |
| `TripTrack/Models/SyncStatus.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SyncStatus.kt` |
| `TripTrack/Models/TimeWindowDebouncer.swift` | missing |
| `TripTrack/Models/TrackGapFinder.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TrackGapFinder.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TrackGapFinder.kt` |
| `TripTrack/Models/TrackPoint.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRecordingStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TrackPoint.kt` |
| `TripTrack/Models/Trip.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProfileHistory.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripPolyline.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRecordingStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Trip.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt` |
| `TripTrack/Models/TripCheckpoint.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripCheckpointPhotos.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripCheckpoint.kt` |
| `TripTrack/Models/TripCheckpointPhotos.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripCheckpointPhotos.kt` |
| `TripTrack/Models/TripCompanion.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripCompanion.kt` |
| `TripTrack/Models/TripConfirmation.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/WorldSql.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRecordingStats.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripConfirmation.kt` |
| `TripTrack/Models/TripDistanceGate.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripDistanceGate.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripDistanceGate.kt` |
| `TripTrack/Models/TripEnergyMode.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripEnergyMode.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/TripFilters.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripFilters.kt` |
| `TripTrack/Models/TripHistoryFilter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/ProfileHistory.kt` |
| `TripTrack/Models/TripLevelHistory.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt` |
| `TripTrack/Models/TripMoment.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripMoments.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripMoment.kt` |
| `TripTrack/Models/TripOrigin.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripOrigin.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/TripPhoto.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripCheckpointPhotos.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripPhoto.kt` |
| `TripTrack/Models/TripPhotoPlacement.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripPhotoPlacement.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRouteLocator.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripPhotoPlacement.kt` |
| `TripTrack/Models/TripReplayInput.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripReplayInput.kt` |
| `TripTrack/Models/TripRouteLocator.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripRouteLocator.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripRouteLocator.kt` |
| `TripTrack/Models/TripSegment.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripSegments.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/TripSegment.kt` |
| `TripTrack/Models/UserSettings.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/UserSettings.kt` |
| `TripTrack/Models/Vehicle.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/DistanceUnit.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/EnergyEstimate.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/Vehicle.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/DomainSerializers.kt` |
| `TripTrack/Models/VehicleCatalogSelection.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleCatalogSelection.kt` |
| `TripTrack/Models/VehicleFinish.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleFinish.kt` |
| `TripTrack/Models/VehicleMod.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/JsonColumns.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleMod.kt` |
| `TripTrack/Models/VehicleModTags.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/JsonColumns.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt` |
| `TripTrack/Models/VehiclePlate.swift` | missing |
| `TripTrack/Models/VehicleType.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/ModelRules.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleType.kt` |
| `TripTrack/Models/VehicleYearDistance.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleYearDistance.kt` |

## TripTrack/Models/Social

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Models/Social/CompanionsDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/CompanionsDTOs.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/EnumSerializers.kt` |
| `TripTrack/Models/Social/GarageDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/GarageDTOs.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SocialSerializers.kt` |
| `TripTrack/Models/Social/NotificationsDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/NotificationsDTOs.kt` |
| `TripTrack/Models/Social/PublicJourneyAdapter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyWindow.kt` |
| `TripTrack/Models/Social/PublicVehicleMod.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PublicVehicleMod.kt` |
| `TripTrack/Models/Social/SocialDTOs.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/SocialDTOs.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SocialSerializers.kt` |
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
| `TripTrack/Models/Sync/TripSyncPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/TripSyncPayload.kt` |
| `TripTrack/Models/Sync/VehicleSyncPayload.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleSyncPayload.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/SyncSerializers.kt` |

## TripTrack/Networking

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Networking/APIClient.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/ModelJson.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/ApiClient.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/ApiError.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/NetworkJson.kt`<br>`core/network/src/test/kotlin/com/onezee/triptrack/network/ApiClientWireTests.kt` |
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
| `TripTrack/Services/AudioRouteDetector.swift` | missing |
| `TripTrack/Services/AuthService.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/TokenStore.kt`<br>`core/network/src/test/kotlin/com/onezee/triptrack/network/ApiClientWireTests.kt` |
| `TripTrack/Services/AutoTripService.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AutoTripPolicy.kt` |
| `TripTrack/Services/BadgeManager.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/BadgeManager.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/BadgeTests.kt` |
| `TripTrack/Services/BluetoothDetector.swift` | missing |
| `TripTrack/Services/CacheManager.swift` | missing |
| `TripTrack/Services/CarCatalog.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/CarCatalog.kt` |
| `TripTrack/Services/CheckpointDefaultName.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/TripSegments.kt` |
| `TripTrack/Services/CompanionsStore.swift` | missing |
| `TripTrack/Services/ContentFilter.swift` | missing |
| `TripTrack/Services/DebugForeignTripFixture.swift` | missing |
| `TripTrack/Services/DebugGarageSeed.swift` | missing |
| `TripTrack/Services/DebugLogExporter.swift` | missing |
| `TripTrack/Services/DebugMapSeed.swift` | missing |
| `TripTrack/Services/DebugProfileCardSeed.swift` | missing |
| `TripTrack/Services/DraftDecisionQueue.swift` | missing |
| `TripTrack/Services/GamificationManager.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RewardMath.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RewardMathTests.kt` |
| `TripTrack/Services/GeohashEncoder.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/GeohashEncoder.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/PlaceIdentityTests.kt` |
| `TripTrack/Services/HomeManager.swift` | missing |
| `TripTrack/Services/HomePrivacyResync.swift` | missing |
| `TripTrack/Services/JourneyManager.swift` | missing |
| `TripTrack/Services/KalmanLocationFilter.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/KalmanLocationFilter.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt` |
| `TripTrack/Services/KeychainHelper.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/CredentialCipher.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/KeystoreTokenVault.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/TokenStore.kt` |
| `TripTrack/Services/LiveActivityManager.swift` | missing |
| `TripTrack/Services/LocalDataWipe.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/LocalDataWipe.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/WipeDao.kt`<br>`core/data/src/test/kotlin/com/onezee/triptrack/data/LocalDataWipeTests.kt` |
| `TripTrack/Services/LocationManager.swift` | missing |
| `TripTrack/Services/LocationProvider.swift` | missing |
| `TripTrack/Services/LogArchive.swift` | missing |
| `TripTrack/Services/MapExploration.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/MapExploration.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/MapExploration.kt` |
| `TripTrack/Services/MotionDetector.swift` | missing |
| `TripTrack/Services/NetworkMonitor.swift` | missing |
| `TripTrack/Services/NotificationManager.swift` | missing |
| `TripTrack/Services/NotificationsInboxStore.swift` | missing |
| `TripTrack/Services/OnboardingLocationRequest.swift` | missing |
| `TripTrack/Services/OnboardingPermissionCoordinator.swift` | missing |
| `TripTrack/Services/OnboardingSystemPermissions.swift` | missing |
| `TripTrack/Services/PhoneConnectivityManager.swift` | missing |
| `TripTrack/Services/PhotoStorageService.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/files/PhotoFiles.kt` |
| `TripTrack/Services/PlaceManager.swift` | missing |
| `TripTrack/Services/PostTripTrackProcessor.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PostTripTrackProcessor.kt` |
| `TripTrack/Services/ProductAnalytics.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/ApiClient.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/UsageDTOs.kt` |
| `TripTrack/Services/ProfilePhotoStore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoDTOs.kt` |
| `TripTrack/Services/ProfileSyncLatch.swift` | missing |
| `TripTrack/Services/PushNotificationManager.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PushDTOs.kt` |
| `TripTrack/Services/RatingPromptService.swift` | missing |
| `TripTrack/Services/RawFixLog.swift` | missing |
| `TripTrack/Services/RealGPSProvider.swift` | missing |
| `TripTrack/Services/RegionAtlas.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RegionAtlas.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/RegionAtlas.kt` |
| `TripTrack/Services/RemotePhotoStorage.swift` | missing |
| `TripTrack/Services/RoadCollectionManager.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/MapStores.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/MapDao.kt` |
| `TripTrack/Services/RoadFog.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RevealedLayer.kt` |
| `TripTrack/Services/RoadGapFiller.swift` | missing |
| `TripTrack/Services/SIWANonce.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/GoogleNonce.kt` |
| `TripTrack/Services/SentryService.swift` | missing |
| `TripTrack/Services/SettingsManager.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/SettingsStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/VehicleStore.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/SettingsDao.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/VehicleDao.kt` |
| `TripTrack/Services/SimilarRecordedTrips.swift` | missing |
| `TripTrack/Services/SimulatedLocationProvider.swift` | missing |
| `TripTrack/Services/SmoothTrackManager.swift` | missing |
| `TripTrack/Services/StartupTrace.swift` | missing |
| `TripTrack/Services/StatsCache.swift` | missing |
| `TripTrack/Services/StoreHealth.swift` | missing |
| `TripTrack/Services/SunCalculator.swift` | missing |
| `TripTrack/Services/SyncQueue.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/sync/SyncRequest.kt` |
| `TripTrack/Services/TerritoryManager.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/MapStores.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/MapDao.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasGeometry.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/GeoBounds.kt` |
| `TripTrack/Services/TipJarService.swift` | missing |
| `TripTrack/Services/TokenStore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/TokenStore.kt` |
| `TripTrack/Services/TripManager.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/RoomTripRepository.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RecordingDecision.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/RecordingMath.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RecordingDecisionTests.kt` |
| `TripTrack/Services/TripSource.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PublicTripsDTOs.kt` |
| `TripTrack/Services/TripWorldEntry.swift` | missing |
| `TripTrack/Services/UndoActionCenter.swift` | missing |
| `TripTrack/Services/UnitsManager.swift` | missing |
| `TripTrack/Services/VehicleCatalog.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleCatalog.kt` |
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
| `TripTrack/Services/Discoveries/DiscoveryReveal.swift` | missing |
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
| `TripTrack/Services/Export/CSVExporter.swift` | missing |
| `TripTrack/Services/Export/GPXExporter.swift` | missing |

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
| `TripTrack/Services/Plus/ProOfferCoordinator.swift` | missing |
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
| `TripTrack/Services/Reveal/RevealBuilder.swift` | missing |
| `TripTrack/Services/Reveal/RevealGrid.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RevealGrid.kt` |
| `TripTrack/Services/Reveal/RevealedLayer.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/RevealedLayer.kt`<br>`core/logic/src/test/kotlin/com/onezee/triptrack/logic/RevealedLayerTests.kt` |
| `TripTrack/Services/Reveal/RevealedLayerStore.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/MapStores.kt`<br>`core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/MapDao.kt` |
| `TripTrack/Services/Reveal/RevealedLayerSync.swift` | missing |
| `TripTrack/Services/Reveal/TemporalFogCache.swift` | missing |

## TripTrack/Services/Social

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Social/DiscussionArchive.swift` | missing |
| `TripTrack/Services/Social/GroupsWaitlistStore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/GroupsDTOs.kt` |
| `TripTrack/Services/Social/SocialFeedStore.swift` | missing |
| `TripTrack/Services/Social/SocialReactions.swift` | missing |
| `TripTrack/Services/Social/TripCommentsStore.swift` | missing |

## TripTrack/Services/Sync

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Services/Sync/APISyncTransport.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/SyncDTOs.kt` |
| `TripTrack/Services/Sync/CompanionPhotoUploadService.swift` | missing |
| `TripTrack/Services/Sync/LastSyncedAtStore.swift` | missing |
| `TripTrack/Services/Sync/ManualTripTrackLoader.swift` | missing |
| `TripTrack/Services/Sync/PullApplier.swift` | missing |
| `TripTrack/Services/Sync/R2PhotoStorage.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoDTOs.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoUploadRequest.kt` |
| `TripTrack/Services/Sync/SyncChunkBudget.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/SyncChunkBudget.kt` |
| `TripTrack/Services/Sync/SyncCoordinator.swift` | `core/data/src/main/kotlin/com/onezee/triptrack/data/db/dao/VehicleDao.kt` |
| `TripTrack/Services/Sync/SyncEnqueuer.swift` | missing |
| `TripTrack/Services/Sync/VehiclePhotoRestore.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PhotoDTOs.kt` |

## TripTrack/Utils

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Utils/GeometryUtils.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/AtlasGeometry.kt`<br>`core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt` |
| `TripTrack/Utils/ISODate.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/FoundationSerializers.kt`<br>`core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/ModelJson.kt`<br>`core/model/src/test/kotlin/com/onezee/triptrack/model/FoundationCompatibilityTests.kt`<br>`core/network/src/main/kotlin/com/onezee/triptrack/network/NetworkJson.kt` |
| `TripTrack/Utils/RandomDisplayName.swift` | missing |
| `TripTrack/Utils/RegionDisplay.swift` | missing |
| `TripTrack/Utils/RelativeTripDate.swift` | missing |
| `TripTrack/Utils/SafeAreaInsets.swift` | missing |
| `TripTrack/Utils/TripAutoTitle.swift` | missing |
| `TripTrack/Utils/WindowLayoutMetrics.swift` | missing |

## TripTrack/ViewModels

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/ViewModels/FeedViewModel.swift` | missing |
| `TripTrack/ViewModels/MapViewModel.swift` | missing |
| `TripTrack/ViewModels/MyMapViewModel.swift` | missing |
| `TripTrack/ViewModels/PlaceDetailViewModel.swift` | missing |
| `TripTrack/ViewModels/PlacesTabViewModel.swift` | missing |
| `TripTrack/ViewModels/ReloadCoalescer.swift` | missing |
| `TripTrack/ViewModels/TripsViewModel.swift` | missing |

## TripTrack/Views

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/ContentView.swift` | missing |

## TripTrack/Views/Admin

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Admin/AdminModel.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/AdminDTOs.kt` |
| `TripTrack/Views/Admin/AdminPanelView.swift` | missing |

## TripTrack/Views/Auth

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Auth/AppleSignInButton.swift` | missing |
| `TripTrack/Views/Auth/SignInPromptSheet.swift` | missing |

## TripTrack/Views/Badges

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Badges/AchievementCell.swift` | missing |
| `TripTrack/Views/Badges/AchievementDetailView.swift` | missing |
| `TripTrack/Views/Badges/AchievementsView.swift` | missing |
| `TripTrack/Views/Badges/BadgeArt.swift` | missing |
| `TripTrack/Views/Badges/BadgeCelebrationView.swift` | missing |
| `TripTrack/Views/Badges/BadgeDetailOverlay.swift` | missing |
| `TripTrack/Views/Badges/ConfettiView.swift` | missing |
| `TripTrack/Views/Badges/PublicAchievementsView.swift` | missing |

## TripTrack/Views/Components

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Components/ActionPopoverList.swift` | missing |
| `TripTrack/Views/Components/AppConfirmDialog.swift` | missing |
| `TripTrack/Views/Components/AsyncThumbnailView.swift` | missing |
| `TripTrack/Views/Components/CarLoadingView.swift` | missing |
| `TripTrack/Views/Components/CardMapPreview.swift` | missing |
| `TripTrack/Views/Components/ContentSizedSheet.swift` | missing |
| `TripTrack/Views/Components/EdgeScrims.swift` | missing |
| `TripTrack/Views/Components/EmptyStateIllustration.swift` | missing |
| `TripTrack/Views/Components/FullscreenMapSheet.swift` | missing |
| `TripTrack/Views/Components/HorizontalScrubSurface.swift` | missing |
| `TripTrack/Views/Components/IdleRing.swift` | missing |
| `TripTrack/Views/Components/KeyboardDismissTapper.swift` | missing |
| `TripTrack/Views/Components/LightRoutePreview.swift` | missing |
| `TripTrack/Views/Components/LocationTrackingButton.swift` | missing |
| `TripTrack/Views/Components/MapCarAnnotationView.swift` | missing |
| `TripTrack/Views/Components/MapCarMarker.swift` | missing |
| `TripTrack/Views/Components/MapChromeButton.swift` | missing |
| `TripTrack/Views/Components/MapExpansionState.swift` | missing |
| `TripTrack/Views/Components/MapPhotoPreview.swift` | missing |
| `TripTrack/Views/Components/MapPopGestureGate.swift` | missing |
| `TripTrack/Views/Components/MapSnapshotPreview.swift` | missing |
| `TripTrack/Views/Components/NavBackButton.swift` | missing |
| `TripTrack/Views/Components/NavCircleIcon.swift` | missing |
| `TripTrack/Views/Components/NotesEditorView.swift` | missing |
| `TripTrack/Views/Components/PhotoPickerView.swift` | missing |
| `TripTrack/Views/Components/PixelCarLoader.swift` | missing |
| `TripTrack/Views/Components/ReactionIconView.swift` | missing |
| `TripTrack/Views/Components/ReactionTallyPill.swift` | missing |
| `TripTrack/Views/Components/RecordingBanner.swift` | missing |
| `TripTrack/Views/Components/RemoteThumbnailView.swift` | missing |
| `TripTrack/Views/Components/ReplayBarState.swift` | missing |
| `TripTrack/Views/Components/RouteMapView.swift` | missing |
| `TripTrack/Views/Components/RoutePlaybackController.swift` | missing |
| `TripTrack/Views/Components/RoutePreviewView.swift` | missing |
| `TripTrack/Views/Components/ScreenScaledDashRenderer.swift` | missing |
| `TripTrack/Views/Components/SheetCloseButton.swift` | missing |
| `TripTrack/Views/Components/SkeletonPlaceholder.swift` | missing |
| `TripTrack/Views/Components/SocialActionButtonStyle.swift` | missing |
| `TripTrack/Views/Components/SpeedColorScale.swift` | missing |
| `TripTrack/Views/Components/StatView.swift` | missing |
| `TripTrack/Views/Components/TapOnlyButtonStyle.swift` | missing |
| `TripTrack/Views/Components/TapTarget.swift` | missing |
| `TripTrack/Views/Components/ToastView.swift` | missing |
| `TripTrack/Views/Components/TripMapHost.swift` | missing |
| `TripTrack/Views/Components/TripMapSlotView.swift` | missing |
| `TripTrack/Views/Components/TripPhotoPicker.swift` | missing |
| `TripTrack/Views/Components/UndoToastView.swift` | missing |
| `TripTrack/Views/Components/VehicleSpritePlate.swift` | missing |
| `TripTrack/Views/Components/VehicleStage.swift` | missing |
| `TripTrack/Views/Components/ZoomableImageView.swift` | missing |

## TripTrack/Views/Drafts

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Drafts/DraftRouteThumb.swift` | missing |
| `TripTrack/Views/Drafts/DraftsBadge.swift` | missing |
| `TripTrack/Views/Drafts/DraftsGrouping.swift` | missing |
| `TripTrack/Views/Drafts/DraftsLayout.swift` | missing |
| `TripTrack/Views/Drafts/DraftsListView.swift` | missing |
| `TripTrack/Views/Drafts/ProfileDraftsRow.swift` | missing |

## TripTrack/Views/Feed

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Feed/FeedView.swift` | missing |
| `TripTrack/Views/Feed/SkeletonTripCard.swift` | missing |
| `TripTrack/Views/Feed/SocialFeedCardView.swift` | missing |

## TripTrack/Views/Garage

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Garage/AutoRecordSettingsView.swift` | missing |
| `TripTrack/Views/Garage/Drive2LinkSheet.swift` | missing |
| `TripTrack/Views/Garage/Drive2LogbookRow.swift` | missing |
| `TripTrack/Views/Garage/FuelCurrencyPickerSheet.swift` | missing |
| `TripTrack/Views/Garage/GarageComponents.swift` | missing |
| `TripTrack/Views/Garage/GarageMileageCard.swift` | missing |
| `TripTrack/Views/Garage/GaragePhotoGrid.swift` | missing |
| `TripTrack/Views/Garage/GarageRegionChips.swift` | missing |
| `TripTrack/Views/Garage/GarageView.swift` | missing |
| `TripTrack/Views/Garage/TripRowText.swift` | missing |
| `TripTrack/Views/Garage/VehicleCardStyle.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/VehicleCardStyle.kt` |
| `TripTrack/Views/Garage/VehicleCatalogCaption.swift` | missing |
| `TripTrack/Views/Garage/VehicleCatalogModelSteps.swift` | missing |
| `TripTrack/Views/Garage/VehicleCatalogPickerView.swift` | missing |
| `TripTrack/Views/Garage/VehicleEditFormView.swift` | missing |
| `TripTrack/Views/Garage/VehicleFace.swift` | missing |
| `TripTrack/Views/Garage/VehicleGarageCustomization.swift` | missing |
| `TripTrack/Views/Garage/VehicleLevelInfoView.swift` | missing |
| `TripTrack/Views/Garage/VehicleMapView.swift` | missing |
| `TripTrack/Views/Garage/VehicleMiniMap.swift` | missing |
| `TripTrack/Views/Garage/VehiclePhotoImage.swift` | missing |
| `TripTrack/Views/Garage/VehiclePhotosView.swift` | missing |
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
| `TripTrack/Views/Journeys/JourneyComposerSheet.swift` | missing |
| `TripTrack/Views/Journeys/JourneyDaysList.swift` | missing |
| `TripTrack/Views/Journeys/JourneyDetailView.swift` | missing |
| `TripTrack/Views/Journeys/JourneyEditSheet.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/JourneyWindow.kt` |
| `TripTrack/Views/Journeys/JourneyFormat.swift` | missing |
| `TripTrack/Views/Journeys/JourneyLegRow.swift` | missing |
| `TripTrack/Views/Journeys/JourneyLinkRow.swift` | missing |
| `TripTrack/Views/Journeys/JourneyPublishSheet.swift` | missing |
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
| `TripTrack/Views/MyMap/AtlasBetaSheet.swift` | missing |
| `TripTrack/Views/MyMap/AtlasControlsSheet.swift` | missing |
| `TripTrack/Views/MyMap/AtlasRegionRow.swift` | missing |
| `TripTrack/Views/MyMap/AtlasSharePoster.swift` | missing |
| `TripTrack/Views/MyMap/AtlasStatTile.swift` | missing |
| `TripTrack/Views/MyMap/AtlasTheme.swift` | missing |
| `TripTrack/Views/MyMap/AttributionCarve.swift` | missing |
| `TripTrack/Views/MyMap/CloudTexture.swift` | missing |
| `TripTrack/Views/MyMap/DiscoveryCardSheet.swift` | missing |
| `TripTrack/Views/MyMap/DiscoveryCopy.swift` | missing |
| `TripTrack/Views/MyMap/FingerWatch.swift` | missing |
| `TripTrack/Views/MyMap/FogVeilOverlay.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FogGeometry.kt` |
| `TripTrack/Views/MyMap/FogVeilVein.swift` | missing |
| `TripTrack/Views/MyMap/FogVeilView.swift` | missing |
| `TripTrack/Views/MyMap/JournalBuilder.swift` | missing |
| `TripTrack/Views/MyMap/MyMapOverlays.swift` | missing |
| `TripTrack/Views/MyMap/MyMapRepresentable.swift` | missing |
| `TripTrack/Views/MyMap/MyMapSheet.swift` | missing |
| `TripTrack/Views/MyMap/MyMapView.swift` | missing |
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
| `TripTrack/Views/MyMap/Atlas/AtlasRegionContent.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSheet.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSheetDetent.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSheetTitleRow.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSlot.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasStatTrio.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSubstateHeader.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasSummaryContent.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/AtlasTripRow.swift` | missing |
| `TripTrack/Views/MyMap/Atlas/FlowRow.swift` | missing |

## TripTrack/Views/MyMap/FogMetal

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/MyMap/FogMetal/FogCarveWindow.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogCellGrid.swift` | `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FogGeometry.kt` |
| `TripTrack/Views/MyMap/FogMetal/FogFrameEncoder.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogMesh.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogMetalAvailability.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogMetalSeat.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogMetalVeil.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogOffscreen.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogPipelineCache.swift` | missing |
| `TripTrack/Views/MyMap/FogMetal/FogRevealCircle.swift` | missing |

## TripTrack/Views/MyMap/Home

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/MyMap/Home/HomeAnnotation.swift` | missing |
| `TripTrack/Views/MyMap/Home/HomePickerMapView.swift` | missing |
| `TripTrack/Views/MyMap/Home/HomeSheet.swift` | missing |

## TripTrack/Views/Navigation

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Navigation/CustomNavBar.swift` | missing |
| `TripTrack/Views/Navigation/CustomTabBar.swift` | missing |
| `TripTrack/Views/Navigation/NavBarKiller.swift` | missing |
| `TripTrack/Views/Navigation/NavFlashDebug.swift` | missing |

## TripTrack/Views/Notifications

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Notifications/NotificationInviteRowModel.swift` | missing |
| `TripTrack/Views/Notifications/NotificationPreferencesView.swift` | missing |
| `TripTrack/Views/Notifications/NotificationsInboxView.swift` | missing |

## TripTrack/Views/Onboarding

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Onboarding/OnboardingView.swift` | missing |

## TripTrack/Views/Places

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Places/PassCourseGlyph.swift` | missing |
| `TripTrack/Views/Places/PlaceCardView.swift` | missing |
| `TripTrack/Views/Places/PlaceDetailView.swift` | missing |
| `TripTrack/Views/Places/PlaceRenameSheet.swift` | missing |
| `TripTrack/Views/Places/PlaceSuggestionCardView.swift` | missing |
| `TripTrack/Views/Places/PlacesMapView.swift` | missing |
| `TripTrack/Views/Places/PlacesView.swift` | missing |

## TripTrack/Views/Places/Slot

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Places/Slot/PlacesPanel.swift` | missing |
| `TripTrack/Views/Places/Slot/PlacesPanelHeader.swift` | missing |
| `TripTrack/Views/Places/Slot/PlacesScreenState.swift` | missing |
| `TripTrack/Views/Places/Slot/PlacesSlot.swift` | missing |
| `TripTrack/Views/Places/Slot/SlotGesture.swift` | missing |

## TripTrack/Views/Plus

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Plus/PlusPaywallModel.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/PlusPaywallModel.kt` |
| `TripTrack/Views/Plus/PlusPaywallSheet.swift` | missing |
| `TripTrack/Views/Plus/PlusRow.swift` | missing |
| `TripTrack/Views/Plus/ProBadge.swift` | missing |
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
| `TripTrack/Views/Profile/AboutEditorSheet.swift` | missing |
| `TripTrack/Views/Profile/AccountSyncComponents.swift` | missing |
| `TripTrack/Views/Profile/AppPreferencesView.swift` | missing |
| `TripTrack/Views/Profile/AvatarFrame.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/AvatarFrame.kt` |
| `TripTrack/Views/Profile/BadgeRarityStyle.swift` | missing |
| `TripTrack/Views/Profile/BluetoothScanSheet.swift` | missing |
| `TripTrack/Views/Profile/CloudSyncView.swift` | missing |
| `TripTrack/Views/Profile/CountryPickerView.swift` | missing |
| `TripTrack/Views/Profile/DebugLogsView.swift` | missing |
| `TripTrack/Views/Profile/LevelsView.swift` | missing |
| `TripTrack/Views/Profile/MyProfileView.swift` | missing |
| `TripTrack/Views/Profile/NameEditorSheet.swift` | missing |
| `TripTrack/Views/Profile/NotificationSwitches.swift` | `core/network/src/main/kotlin/com/onezee/triptrack/network/PushDTOs.kt` |
| `TripTrack/Views/Profile/PrivacySettingsView.swift` | missing |
| `TripTrack/Views/Profile/ProfileAchievementsSection.swift` | missing |
| `TripTrack/Views/Profile/ProfileBackground.swift` | `core/model/src/main/kotlin/com/onezee/triptrack/model/ProfileBackground.kt` |
| `TripTrack/Views/Profile/ProfileClubsRow.swift` | missing |
| `TripTrack/Views/Profile/ProfileComponents.swift` | missing |
| `TripTrack/Views/Profile/ProfileCoverImport.swift` | missing |
| `TripTrack/Views/Profile/ProfileCoverPager.swift` | missing |
| `TripTrack/Views/Profile/ProfileCoverScrollProbe.swift` | missing |
| `TripTrack/Views/Profile/ProfileGarageHeader.swift` | missing |
| `TripTrack/Views/Profile/ProfileHeroCard.swift` | missing |
| `TripTrack/Views/Profile/ProfileHistoryCalendar.swift` | missing |
| `TripTrack/Views/Profile/ProfileHistoryMonthHeader.swift` | missing |
| `TripTrack/Views/Profile/ProfileHistorySkeleton.swift` | missing |
| `TripTrack/Views/Profile/ProfilePhotoThumbnail.swift` | missing |
| `TripTrack/Views/Profile/ProfileRoadScene.swift` | missing |
| `TripTrack/Views/Profile/ProfileRoutineTripsView.swift` | missing |
| `TripTrack/Views/Profile/ProfileSettingsSheet.swift` | missing |
| `TripTrack/Views/Profile/ProfileTripCardView.swift` | missing |
| `TripTrack/Views/Profile/ProfileTripTile.swift` | missing |
| `TripTrack/Views/Profile/ProfileView.swift` | missing |
| `TripTrack/Views/Profile/SettingsHintPopover.swift` | missing |
| `TripTrack/Views/Profile/SettingsOptionPicker.swift` | missing |
| `TripTrack/Views/Profile/StatsScreenView.swift` | missing |
| `TripTrack/Views/Profile/SyncStatusMarker.swift` | missing |
| `TripTrack/Views/Profile/SyncStatusSheetView.swift` | missing |
| `TripTrack/Views/Profile/TipJarDebugView.swift` | missing |
| `TripTrack/Views/Profile/UsernameEditorSheet.swift` | missing |
| `TripTrack/Views/Profile/VehicleDetailView.swift` | missing |
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
| `TripTrack/Views/Social/BlockedListView.swift` | missing |
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
| `TripTrack/Views/Social/SharePosterRenderer.swift` | missing |
| `TripTrack/Views/Social/SharePosterView.swift` | missing |
| `TripTrack/Views/Social/SharedTripLinkSheet.swift` | missing |
| `TripTrack/Views/Social/SkeletonProfileView.swift` | missing |
| `TripTrack/Views/Social/StoryShareSheet.swift` | missing |

## TripTrack/Views/Theme

| Swift file | Android counterpart files |
|---|---|
| `TripTrack/Views/Theme/AppFont.swift` | missing |
| `TripTrack/Views/Theme/AppTheme.swift` | missing |
| `TripTrack/Views/Theme/ThemeManager.swift` | missing |

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
| `TripTrack/Views/Trips/OwnTripPhotosModel.swift` | missing |
| `TripTrack/Views/Trips/PhotoFullScreenView.swift` | missing |
| `TripTrack/Views/Trips/PosterRouteCanvas.swift` | missing |
| `TripTrack/Views/Trips/SegmentEditorSheet.swift` | missing |
| `TripTrack/Views/Trips/TripCommentsScreen.swift` | missing |
| `TripTrack/Views/Trips/TripCommentsSection.swift` | missing |
| `TripTrack/Views/Trips/TripCompanionsSection.swift` | missing |
| `TripTrack/Views/Trips/TripDetailRoutePlaceholder.swift` | missing |
| `TripTrack/Views/Trips/TripDetailSections.swift` | missing |
| `TripTrack/Views/Trips/TripDetailTopBar.swift` | missing |
| `TripTrack/Views/Trips/TripDetailView.swift` | missing |
| `TripTrack/Views/Trips/TripEditSheet.swift` | missing |
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
| `TripTrackShared/VehicleAvatar.swift` | missing |
| `TripTrackShared/VehicleSpriteMetrics.swift` | missing |

## Android-only files

These files have no resolved Swift counterpart. An absent or unresolved comment also lands here; it is not evidence of an intentional product divergence.

Files: 26.

| Android file | Reason from docs/divergences.md |
|---|---|
| `app/src/main/kotlin/com/onezee/triptrack/AppGraph.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/MainActivity.kt` | not documented |
| `app/src/main/kotlin/com/onezee/triptrack/TripTrackApplication.kt` | not documented |
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
| `core/location/src/main/kotlin/com/onezee/triptrack/location/Location.kt` | not documented |
| `core/map/src/main/kotlin/com/onezee/triptrack/map/Map.kt` | not documented |
| `core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/IosObjectSerializer.kt` | not documented |
| `core/model/src/test/kotlin/com/onezee/triptrack/model/IosFixtures.kt` | not documented |
| `core/sync/src/main/kotlin/com/onezee/triptrack/sync/Sync.kt` | not documented |

## Marker warnings

Use a repository-relative Swift path to resolve an ambiguous name. Name the `.swift` file explicitly for symbol-only and non-Swift references.

| Android file | Reference | Issue |
|---|---|---|
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
| `core/designsystem/src/main/kotlin/com/onezee/triptrack/designsystem/strings/LocalizedDateFormatter.kt` | `LocalizedDateFormatter's per-language DateFormatter table.` | no Swift filename |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/FogGeometry.kt` | `CoreGraphics CGRect / MapKit MKMapRect (pure coordinate rectangle).` | no Swift filename |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/LocationFix.kt` | `CLLocation.distance(from:). WGS84 inverse distance, independent of Android APIs.` | no Swift filename |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/Measure.kt` | `GarageFormat.swift` | not found |
| `core/logic/src/main/kotlin/com/onezee/triptrack/logic/PlacesPresentation.kt` | `Foundation CharacterSet.whitespaces / whitespacesAndNewlines. Foundation includes zero-width space; JVM trim also removes unrelated control separators.` | no Swift filename |
| `core/logic/src/test/kotlin/com/onezee/triptrack/logic/RecordingInterferenceTests.kt` | `testInterruptedSparseDriveEndingStoppedSurvivesOrphanRecovery. Durability/recovery belong to the repository; this checks its input.` | no Swift filename |
| `core/model/src/main/kotlin/com/onezee/triptrack/model/serialization/IosObjectSerializer.kt` | `custom Codable implementations in Models/` | no Swift filename |
| `core/model/src/test/kotlin/com/onezee/triptrack/model/IosFixtures.kt` | `TripTrackTests/Fixtures and inline JSON fixtures` | no Swift filename |
| `core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt` | `Services/Social/SocialCommentsStore.swift` | not found |
| `core/network/src/main/kotlin/com/onezee/triptrack/network/FeatureApis.kt` | `SocialCommentsStore.swift` | not found |
