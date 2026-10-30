# w0-skeleton

Статус: debug APK собран, все 83 JVM-теста проходят. Оба обязательных запуска проходят офлайн
на JDK 17.0.18. Ветка `w0-skeleton`; коммитов и push нет.

Созданы девять модулей из `docs/port-plan.md`. `core:model` и `core:logic` работают на JVM,
остальные core-модули являются Android libraries. В `app` есть `TripTrackApplication`, пустой
`AppGraph` и `MainActivity` с названием приложения. Application ID: `com.onezee.triptrack`,
debug suffix: `.dev`, minSdk 29, targetSdk 36, compileSdk 37.2. Release без minify и signing config.

Все девять модулей используют toolchain JDK 17. Сохранены настройки оркестратора:
IPv4 для Google Maven, компиляция Kotlin внутри Gradle, idle timeout 600000 мс и gitignored
`local.properties`. Лимиты памяти не менялись: Gradle 2 ГБ, metaspace 768 МБ, четыре worker.
Wrapper содержит SHA-256 дистрибутива; `spikes/`, `tools/` и `docs/port-status.md` не менялись.

## Версии

Итоговые версии из `gradle/libs.versions.toml` и локального SDK на 30 октября 2026.
Неиспользуемые зависимости объявлены только в catalog. Приложение использует Activity Compose
и Compose foundation/UI; модели используют kotlinx.serialization; JVM-тесты используют JUnit 4
и kotlin.test.

| Компонент | Версия | Источник |
|---|---|---|
| JDK | 17.0.18 | Homebrew OpenJDK 17; toolchain 17 во всех модулях |
| Android platform | 37.2, revision 2 | Локальный SDK |
| Android build-tools | 36.0.0 | Локальный SDK |
| Gradle | 9.8.2 | [metadata](https://services.gradle.org/versions/current) |
| AGP | 9.5.0 | [metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) |
| Kotlin / Compose compiler | 2.4.21 | [metadata](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml) |
| Compose BOM | 2026.09.00 | [metadata](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml) |
| KSP | 2.3.12 | [metadata](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml) |
| Room | 2.8.5 | [metadata](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml) |
| OkHttp | 5.5.1 | [metadata](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml) |
| kotlinx.serialization | 1.12.0 | [metadata](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml) |
| Coroutines | 1.11.0 | [metadata](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml) |
| WorkManager | 2.12.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/maven-metadata.xml) |
| Navigation Compose | 2.10.2 | [metadata](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml) |
| Coil | 3.6.3 | [metadata](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose/maven-metadata.xml) |
| Robolectric | 4.17 | [metadata](https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml) |
| Roborazzi | 1.77.0 | [metadata](https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml) |
| Activity Compose | 1.13.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml) |
| foojay resolver | 1.0.0 | [metadata](https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/maven-metadata.xml) |
| JUnit | 4.13.2 | JUnit 4 по port plan |

## Модели и JSON

Файлы Kotlin названы по iOS-источникам; каждый тип содержит ссылку на Swift counterpart.
Сохранены имена полей, optionality, raw values и единицы хранения. Координаты представлены
`GeoPoint`, идентификаторы — `java.util.UUID`, даты — `java.time.Instant`.

`ModelJson.storage` использует эпоху Foundation (2001), base64 для Data и uppercase UUID.
`ModelJson.wire` использует ISO UTC с миллисекундами, как APIClient/ISODate. Для полей с
CodingKeys сохранены wire names, включая `id` у CompanionCandidate, `commentCount` и короткие
ключи Riddle. Discovery хранит координаты двумя числами и восстанавливает UUID v5 из kind/key.

Сохранены терпимые декодеры Vehicle, VehicleSyncPayload, PublicVehicle, MeResponse, SocialProfile,
SocialFind, SecretRecord, SecretCatalogResponse, PlusStatusResponse, TripOrigin и TripEnergyMode.
`manualOdometerKm` и `soldAt` кодируются явным null при очистке; флаги присутствия этих ключей
не сериализуются. Неизвестные настройки машины не стирают другие поля.
`recordingSegmentIndex` остаётся производным и не попадает в JSON.

## Исправления сборки

- Во всех семи Android-модулях compileSdk задан через
  `compileSdk { version = release(37) { minorApiLevel = 2 } }`.
  Это [синтаксис AGP для minor SDK](https://developer.android.com/build).
  targetSdk остался 36, minSdk 29.
- `PlacesSort.byName` заменяет конфликтующий с `Enum.name` Kotlin-элемент.
  `rawValue` и JSON по-прежнему содержат `name`; добавлена проверка всех трёх значений.
- `VehicleSerializer` стал объектом без параметров конструктора. Неизменяемый
  `VehicleCurrencySerializer` хранит валюту в `SerializersModule` конкретного `Json`.
  `ModelJson.storage(fuelCurrency = ...)` сохраняет прежний API и JSON shape.
  Тест проверяет отсутствующий/null ключ, fallback €, разные настройки, список машин,
  сохранённую валюту и неизменность JSON при кодировании.
- В `ProfileWireTests.publicProfileWithPhotosAndPinnedTrip` восстановлена закрывающая
  кавычка у `future`, потерянная при переносе Swift raw string. Вход снова совпадает с iOS.
- `FoundationCompatibilityTests` проверяет числовой JSON и значение `721692800.0`:
  kotlinx.serialization записывает его как `7.216928E8`. Эпоха Foundation (2001) и само число
  не изменились; строковый JSON по-прежнему не принимается как результат кодирования.
- В `README.md`, `AGENTS.md`, `docs/port-plan.md` указан JDK 17.

## Проверки

83 JVM-теста, семь suites, 0 failures, 0 errors, 0 skipped. Статические JSON fixtures из iOS-тестов
находятся в `core/model/src/test/resources/ios`. Проверяются Codable round trips, raw values,
старые payload, optional/null/empty, malformed optional поля, companions, public journeys,
profile journal, visibility, Discovery/Place identity, compact Riddle JSON, unsigned hashes,
даты, UUID и base64. Persistence/network/UI-части исходных тестов остаются соответствующим волнам.
В `core:logic`, app и пустых Android libraries тестов пока нет (`NO-SOURCE`).

Все Gradle-команды запускались последовательно с:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

| Команда | Результат |
|---|---|
| `./gradlew --offline :app:assembleDebug` | PASS, exit 0, `BUILD SUCCESSFUL in 8s`; 36 tasks: 25 executed, 11 up-to-date |
| `./gradlew --offline test` (первый запуск) | FAIL, exit 1, 9s: в кэше отсутствовали `kotlin-test-junit:2.4.21` и `junit:4.13.2` |
| `./gradlew test` | Зависимости загружены; FAIL, exit 1, 10s: 83 теста, две ошибки тестовых данных/проверок, исправлены выше |
| `./gradlew --offline test` (после исправлений) | PASS, exit 0, `BUILD SUCCESSFUL in 2s`; 107 tasks: 2 executed, 105 up-to-date; 83 теста прошли |
| `sh -n gradlew` | PASS |
| `git diff --check` | PASS |

Поиск по правилу приватности через `git grep -nE` и `rg` в новых app/core/gradle/report файлах
не нашёл совпадений (exit 1 — пустая выдача).

APK: `app/build/outputs/apk/debug/app-debug.apk`. `aapt dump badging` подтвердил
`com.onezee.triptrack.dev`, versionCode 1, versionName `0.1.0`, minSdk 29, targetSdk 36.
XML-результаты: `core/model/build/test-results/test/TEST-*.xml`.
HTML-отчёт: `core/model/build/reports/tests/test/index.html`.

Первоначальный TLS-блокер снят настройками окружения оркестратора. SHA-256 `gradle.properties`
и `local.properties` совпадают до и после работы. Эмулятор не запускался;
`./gradlew --stop`, git commit и push не выполнялись.

## Границы порта и отличия

- Это формы данных. Расчёты, нормализация recording breaks, аннотация сегментов трека,
  фильтры, сортировка, подбор мест/путешествий, расход, награды, геометрия и форматирование
  остаются для core:logic и UI. Перед подключением моделей к записи эти алгоритмы ещё нужны.
- Badge содержит метаданные. SwiftUI Color и callback checkUnlocked пропущены: внешний вид и
  правила выдачи относятся к другим модулям. Каталог BadgeDefinitions не перенесён.
- CLLocation/UIImage constructors, PickedPhoto, построение map/search indexes и платформенные контейнеры
  не перенесены. Android providers и renderer появятся в своих модулях.
- Swift enum с associated values представлены sealed interfaces с data classes/objects.
  Их Kotlin-названия начинаются с заглавной буквы. Raw enums сохраняют wire values.
  Encodable/Decodable DTO получают Kotlin serializer для того же JSON shape.
- `PlacesSort.byName` соответствует Swift `PlacesSort.name`: имя `name` занято в Kotlin Enum.
  Сохранённые значения и JSON не меняются.
- GeoPoint не имеет общего wire-контракта: сериализатор родителя сохраняет исходную форму
  координаты. GeoBounds содержит четыре исходных Double без SDK-зависимостей.
- `ModelJson.storage(fuelCurrency = ...)` принимает текущую валюту явно. До загрузки настроек
  используется исходный fallback €; при создании Vehicle после загрузки настроек нужно передать
  выбранную валюту в constructor. Чтения глобального UserDefaults в модели нет.
- Kotlin data classes сравнивают сохранённые поля. Оптимизированные SwiftUI Equatable
  implementations Trip/Map models, игнорирующие часть полей, не перенесены. ByteArray нужно
  сравнивать через contentEquals. Формат JSON от этого не меняется.
- Дизайн-система, Inter, строки, карта и функции приложения здесь ещё не подключены.
  Временный экран показывает только app_name; других строковых ресурсов не добавлено.

## iOS-источники

Перенесены формы данных из следующих файлов; методы и platform state исключены:

- `TripTrack/Models/AtlasPlaces.swift`
- `TripTrack/Models/AtlasPresentation.swift`
- `TripTrack/Models/AutoRecordMode.swift`
- `TripTrack/Models/AutoTripPolicy.swift`
- `TripTrack/Models/Badge.swift`
- `TripTrack/Models/BadgeFigure.swift`
- `TripTrack/Models/ConsumptionUnit.swift`
- `TripTrack/Models/DashboardUnits.swift`
- `TripTrack/Models/Discovery.swift`
- `TripTrack/Models/Drive2Link.swift`
- `TripTrack/Models/ElectricUnit.swift`
- `TripTrack/Models/EnergyEstimate.swift`
- `TripTrack/Models/FixGate.swift`
- `TripTrack/Models/GamificationModels.swift`
- `TripTrack/Models/HistoryFolding.swift`
- `TripTrack/Models/HomeSettings.swift`
- `TripTrack/Models/Journey.swift`
- `TripTrack/Models/JourneyAggregate.swift`
- `TripTrack/Models/Measure.swift`
- `TripTrack/Models/PhotoMetadata.swift`
- `TripTrack/Models/PhotoUploadStatus.swift`
- `TripTrack/Models/Place.swift`
- `TripTrack/Models/PlaceChip.swift`
- `TripTrack/Models/PlaceLastPass.swift`
- `TripTrack/Models/PlaceListItem.swift`
- `TripTrack/Models/PlacePass.swift`
- `TripTrack/Models/PlaceScreen.swift`
- `TripTrack/Models/PlaceStats.swift`
- `TripTrack/Models/PlaceSuggestions.swift`
- `TripTrack/Models/PlacesPresentation.swift`
- `TripTrack/Models/Powertrain.swift`
- `TripTrack/Models/PreparedProfileHistory.swift`
- `TripTrack/Models/ProfileHistoryMonth.swift`
- `TripTrack/Models/ProfilePhotoDTO.swift`
- `TripTrack/Models/ProfileProgress.swift`
- `TripTrack/Models/ProfileRoutineTrips.swift`
- `TripTrack/Models/RecordingFinishPreview.swift`
- `TripTrack/Models/RecordingFixContinuity.swift`
- `TripTrack/Models/RoadSceneTime.swift`
- `TripTrack/Models/RouteLineStyle.swift`
- `TripTrack/Models/Social/CompanionsDTOs.swift`
- `TripTrack/Models/Social/GarageDTOs.swift`
- `TripTrack/Models/Social/NotificationsDTOs.swift`
- `TripTrack/Models/Social/PublicVehicleMod.swift`
- `TripTrack/Models/Social/SocialDTOs.swift`
- `TripTrack/Models/Sync/AuthDTOs.swift`
- `TripTrack/Models/Sync/JourneySyncPayload.swift`
- `TripTrack/Models/Sync/PhotoSyncPayload.swift`
- `TripTrack/Models/Sync/SettingsSyncPayload.swift`
- `TripTrack/Models/Sync/SyncPullRequest.swift`
- `TripTrack/Models/Sync/SyncPullResponse.swift`
- `TripTrack/Models/Sync/SyncPushModels.swift`
- `TripTrack/Models/Sync/TrackPointPayload.swift`
- `TripTrack/Models/Sync/TripSyncPayload.swift`
- `TripTrack/Models/Sync/VehicleSyncPayload.swift`
- `TripTrack/Models/SyncStatus.swift`
- `TripTrack/Models/TrackGapFinder.swift`
- `TripTrack/Models/TrackPoint.swift`
- `TripTrack/Models/Trip.swift`
- `TripTrack/Models/TripCheckpoint.swift`
- `TripTrack/Models/TripCompanion.swift`
- `TripTrack/Models/TripConfirmation.swift`
- `TripTrack/Models/TripDistanceGate.swift`
- `TripTrack/Models/TripEnergyMode.swift`
- `TripTrack/Models/TripFilters.swift`
- `TripTrack/Models/TripMoment.swift`
- `TripTrack/Models/TripOrigin.swift`
- `TripTrack/Models/TripPhoto.swift`
- `TripTrack/Models/TripPhotoPlacement.swift`
- `TripTrack/Models/TripReplayInput.swift`
- `TripTrack/Models/TripRouteLocator.swift`
- `TripTrack/Models/TripSegment.swift`
- `TripTrack/Models/UserSettings.swift`
- `TripTrack/Models/Vehicle.swift`
- `TripTrack/Models/VehicleCatalogSelection.swift`
- `TripTrack/Models/VehicleFinish.swift`
- `TripTrack/Models/VehicleMod.swift`
- `TripTrack/Models/VehicleType.swift`
- `TripTrack/Models/VehicleYearDistance.swift`
- `TripTrack/Networking/PlusAPI.swift`
- `TripTrack/Networking/SecretsAPI.swift`
- `TripTrack/Services/CarCatalog.swift`
- `TripTrack/Services/Discoveries/RiddleCatalog.swift`
- `TripTrack/Services/Discoveries/SecretCatalog.swift`
- `TripTrack/Services/Discoveries/TripDiscoveries.swift`
- `TripTrack/Services/MapExploration.swift`
- `TripTrack/Services/RegionAtlas.swift`
- `TripTrack/Services/Plus/CosmeticRetentionStore.swift`
- `TripTrack/Services/Plus/PlusAttachQueue.swift`
- `TripTrack/Services/Plus/PlusGate.swift`
- `TripTrack/Services/Plus/PlusStore.swift`
- `TripTrack/Services/Plus/ProContextOffer.swift`
- `TripTrack/Services/Plus/ProManageModel.swift`
- `TripTrack/Services/Plus/ProStatus.swift`
- `TripTrack/Services/Plus/TipMoment.swift`
- `TripTrack/Services/TerritoryManager.swift (GeoBounds)`
- `TripTrack/Services/VehicleCatalog.swift`
- `TripTrack/Utils/ISODate.swift`
- `TripTrack/Views/Garage/VehicleCardStyle.swift`
- `TripTrack/Views/Plus/PlusPaywallModel.swift`
- `TripTrack/Views/Plus/ProShowcase.swift`
- `TripTrack/Views/Profile/AvatarFrame.swift`
- `TripTrack/Views/Profile/ProfileBackground.swift`
- `TripTrackShared/DistanceUnit.swift`

Из Models/ целиком отложены файлы с алгоритмами и платформенными extensions:

- `AtlasHome.swift`
- `BadgeDefinitions.swift`
- `CLLocationCoordinate2D+Equatable.swift`
- `CarHeadingPolicy.swift`
- `CheckpointReading.swift`
- `DistanceUnit+Current.swift`
- `GapFill.swift`
- `JourneySuggester.swift`
- `OdometerField.swift`
- `PlaceMatcher.swift`
- `PlaceUsuallyLine.swift`
- `PrivacyZone.swift`
- `RecordingBreaks.swift`
- `RecordingMovementTracker.swift`
- `SegmentHistory.swift`
- `Social/PublicJourneyAdapter.swift`
- `Social/SocialTripAdapter.swift`
- `TimeWindowDebouncer.swift`
- `TripCheckpointPhotos.swift`
- `TripHistoryFilter.swift`
- `TripLevelHistory.swift`
- `VehicleModTags.swift`
- `VehiclePlate.swift`
