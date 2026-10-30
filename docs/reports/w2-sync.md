# w2-sync

Перенесён `core:sync`, убраны временные правила из `core:data`. Коммитов и push нет.

## Перенос

| Android | iOS |
|---|---|
| `SyncQueue`, `QueuePersistence` | `Services/SyncQueue.swift`: виды операций, дедупликация, приоритет, FIFO, batch fast path, failed queue, `2^retryCount`, предел 5, ручной повтор, cancel/clear |
| `SyncEnqueuer` | `Services/Sync/SyncEnqueuer.swift`: авторизация, drafts, trip/photo/journey privacy gates, server-copy hint для удалённого фото; vehicles/settings/discoveries только с Cloud Sync |
| `APISyncTransport`, `R2PhotoStorage`, `PhotoEncoder` | `Services/Sync/{APISyncTransport,R2PhotoStorage}.swift`: отдельные запросы, потоковые чанки 25 / 16 MiB по `SyncChunkBudget`, fallback неподтверждённых записей, конфликты, garage fields, multipart с lowercase UUID, отдельные подтверждения вариантов |
| `TripSyncPayloadMapper` | `Models/Sync/TripSyncPayload.swift`, `Models/PrivacyZone.swift`: metrics по полному треку, privacy zone параметром, обрезка track/preview/EXIF/checkpoints только на отправке, явные auto/empty arrays, breaks и segments |
| `ManualTripTrackLoader` | `Services/Sync/ManualTripTrackLoader.swift`: проверка маршрута до privacy upload, максимум 10 000 точек, версия/дата/аккаунт/Cloud Sync проверяются перед записью |
| `PullApplier`, `SyncPullCompleted` | `Services/Sync/PullApplier.swift`: trips/photos/checkpoints/segments/breaks, vehicles/settings/journeys/discoveries, tombstones, очистка снимков машины, пересчёт manual odometers, `appliedTripIds` |
| `LastSyncedAtStore`, `SyncStoreIdentity`, `SyncCoordinator` | `Services/Sync/{LastSyncedAtStore,SyncCoordinator}.swift`: account/store cursor, full pull/reset, однократный heal с пятью типами библиотеки, manifest reconciliation с запретом при truncated, запрет pull во время записи и при Cloud Sync off |
| `VehiclePhotoRestore`, `DiscussionArchive`, `DiscoverySync` | `Services/Sync/VehiclePhotoRestore.swift`, `Services/Social/DiscussionArchive.swift`, `Services/Discoveries/DiscoveryReveal.swift`: восстановление фото без удаления отсутствующих, архив до unpublish, постоянные/временные отказы reveal |
| `NetworkMonitor`, `SyncScheduler`, `SyncWorker`, `SyncWorkerFactory`, `SyncEngine` | `Services/NetworkMonitor.swift`, запуск сервисов в `TripTrackApp`, foreground/network timer в `SyncCoordinator`: Android adapters и сборка зависимостей |

`unpublishTrip` сначала спасает server-only фото (original, иначе thumbnail), затем архивирует
обсуждение и удаляет серверную копию. Неудачное спасение блокирует delete. Неудачный архив его
не блокирует. Journey unpublish остаётся upsert с `isPrivate=true`.

Сохранены особенности текущего iOS-контракта: отсутствующий `photos` имеет другую семантику,
чем отсутствующие track/checkpoints/segments/breaks; trip conflict сохраняет version guard;
journey nullable поля не превращаются в явные null; settings conflict ждёт следующего pull.
`appliedTripIds` содержит входящие upsert IDs, как в Swift, включая строки под edit guard.

## Cleanup core:data

- Добавлена зависимость на `core:logic`. Journey membership, reward eligibility, car levels и
  normalization breaks теперь берутся оттуда.
- `Drive2Link`, `VehicleModTags`, `VehicleFinish.parse` и `VehicleType.hasPlate` перенесены в
  `core:logic`: общих реализаций этих garage helpers в исходном checkout ещё не было.
- `ModelRules.kt` оставлен только для декодирования storage значений.
- Чтение trip аннотирует точки через `RecordingBreaks`; wire по-прежнему не содержит segment index.
- Удалены строки divergences о временных model rules и ожидающей нормализации wave 0.
  Domain schema Room остаётся v1; sync identity хранится в отдельной служебной таблице.

## Тесты

| Android | Исходные iOS tests / проверки |
|---|---|
| `SyncQueueTests`, `CursorAndRecoveryTests`, `SyncEngineTests` | `SyncQueueTests`, `LastSyncedAtStoreTests`, `JourneySyncRecoveryTests`; рестарт AtomicFile, account/store scope, in-flight recovery, failed cap, cancel/logout/cancellation races |
| `SyncEnqueuerTests` | `DraftSyncGateTests`, `SyncEnqueuerJourneyTests`, photo-delete gate; матрица actions/privacy/server copy/cloud |
| `JourneySyncConflictTests`, `PushPullTests` | `JourneySyncConflictTests`, `VehicleSyncConflictTests`, `CheckpointSyncFlagTests`, garage/settings wire; реальные HTTP + Room |
| `PullApplierTests`, `SyncCoordinatorTests` | `PullApplierStatsCacheTests`, `PullApplierDiscoveryTests`, `TombstoneGuardTests`, `TripSegmentSyncTests`, `RecordingBreaksPersistenceTests`, `SyncHealTests`, `RevealedLayerSyncTests` |
| `TripSyncPayloadMapperTests`, `PrivacyZoneDisciplineTests` | `Sync/TripSyncPayloadMapperTests`, `HomePrivacyTrimTests`, `PrivacyZoneDisciplineTests`; проверка исходников запрещает trimming за границей отправки |
| `VehicleEnergyWireTests`, `PhotoSyncTests`, `RasterPhotoEncoderTests` | `VehicleEnergyWireTests`, `VehiclePhotoUploadWireTests`, `VehiclePhotoRecoveryTests`; thumbnail/original debt, rescue before delete, размеры JPEG и отсутствие GPS/EXIF |
| `SyncChunkBudgetTests`, `ManualTrackAndDiscoveryTests`, `SchedulingTests` | `SyncChunkBudgetTests` с настоящим wire encoder и теми же 5 000 / 89 000 / 30 000 / 20 000 точек; manual hydration, reveal, WorkManager constraints/backoff, validated connectivity |

Существующие тесты `core:data` сохраняют store-половины `CheckpointSyncFlagTests`,
`VehicleEnergyWireTests`, `JourneySyncConflictTests` и остальных merge/storage случаев.
Room и HTTP тестируются через Robolectric SDK 35 и MockWebServer, raster через native graphics.

Все команды из checkout, JDK 17:
`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.

| Команда | Результат |
|---|---|
| `./gradlew --no-daemon --offline :core:data:test :core:logic:test` | Первый запуск: Robolectric не смог создать lock в домашнем каталоге; до выполнения тестов |
| `./gradlew --no-daemon --offline -I /private/tmp/w2-sync-tests.gradle :core:data:test :core:logic:test` | PASS после cleanup: data 224, logic 572 |
| `./gradlew --no-daemon --offline :core:sync:compileDebugKotlin` | WorkManager не был в offline cache |
| `./gradlew --no-daemon :core:sync:compileDebugKotlin` | Gradle TLS handshake при загрузке транзитивного AndroidX artifact; зависимости подготовлены через официальный Maven и локальный cache |
| `./gradlew --no-daemon --offline -I /private/tmp/w2-sync-tests.gradle :core:sync:test :core:data:test :core:logic:test` | PASS: sync 118, data 224, logic 572; 0 failures/errors/skipped |
| `./gradlew --no-daemon --offline -I /private/tmp/w2-sync-tests.gradle :core:sync:test :core:sync:lintDebug :core:data:lintDebug` | PASS: sync 123, 0 failures/errors/skipped; lint data/sync без замечаний |
| `./gradlew --no-daemon --offline -I /private/tmp/w2-sync-tests.gradle :core:sync:test :core:data:test :core:logic:test :core:sync:lintDebug :core:data:lintDebug` | PASS: 919 tests, 0 failures/errors/skipped; lint 0 issues |

Временный init script выбирает скачанный `android-all-instrumented-15-robolectric-13954326-i7.jar`
через `robolectric.offline=true` / `robolectric.dependency.dir`, чтобы не создавать lock за
пределами sandbox. Для отдельных WorkManager transitives он использует локальный Maven с
`metadataSources { mavenPom(); artifact() }`: Gradle на этой машине обрывал TLS, хотя `curl`
официального `dl.google.com/dl/android/maven2/` проходил. Версии библиотек не менялись.
Список локальных координат: lifecycle livedata/core/runtime/service 2.6.2, profileinstaller 1.3.0,
tracing 1.0.0/1.2.0, tracing-ktx 1.2.0, startup-runtime 1.2.0, concurrent-futures-ktx 1.1.0;
после замены EXIF добавлен exifinterface 1.4.2.

В `gradle/libs.versions.toml` добавлен `androidx.exifinterface:exifinterface:1.4.2`, используется
только в sync: чтение ориентации фото через поддерживаемый AndroidX parser вместо platform
ExifInterface. Версия сверена с [AndroidX releases](https://developer.android.com/jetpack/androidx/releases/exifinterface).
Уже объявленный `work-runtime` подключён в sync для фоновых запусков. Остальные зависимости
используются соседними слоями. `core:data` build изменён для требуемой зависимости на `core:logic`;
другие build files не менялись.

## Подключение в приложении

`SyncEngine.create` собирает production stores, persistent queue, transport, pull и scheduler.
Файлы очереди, фото и архива лежат в `noBackupFilesDir`. AppGraph создаёт engine после загрузки
identity/settings и пересоздаёт его при смене аккаунта/API/store. Старый engine закрывается;
при явном logout вызывается `clearAccountWork`. Новые операции старого account engine не проходят
authorization guard.

Application должен предоставить `Configuration.Provider` с `SyncWorkerFactory`, чей suspend
resolver возвращает текущий coordinator (может дождаться AppGraph). Default WorkManager initializer
снят в manifest модуля по [правилу custom initialization](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration). После сборки: `start`, foreground/background lifecycle hooks; при входе и
включении Cloud Sync вызвать `requestFullPull(markLocalPending=true)` и запланировать проход;
при смене зоны вызвать `privacyZoneChanged` и запланировать проход. Callers trip/settings edits
сохраняют iOS разделение manager/store и передают sync requests после локальных изменений.

В этом checkout ещё нет app-level AuthService/profile stores и settings/Bluetooth managers.
Их операции переданы обязательными callbacks: progress/bio/pins/profile photos идут через
`syncAccountProfile` при каждом проходе независимо от Cloud Sync; vehicle tombstone вызывает
`purgeVehiclePreferences` для Bluetooth и photo visibility consent. Реализация этих feature owners,
экран sync status и подключение Application/AppGraph остаются соответствующим волнам. Поля bio,
pins и PRO cosmetics не добавлялись в settings wire.

## Отличия

Все отличия внесены в `docs/divergences.md`: персистентная очередь и восстановление на каждом
проходе, сброс retryCount при ручном повторе, защита новых edits/in-flight cancellation,
SQLite store identity, WorkManager scheduling, ConnectivityManager, Flow и явные зависимости,
Android raster/JPEG, JVM grapheme segmentation. Wire keys, raw values, числа, единицы и merge
правила сохранены.

В финальном наборе 919 тестов: sync 123, data 224, logic 572. `git diff --check` и scan IP/keys
прошли. Эмулятор и production API не запускались. Manifest merger библиотеки предупреждает о
`tools:node="remove"` без локальной декларации WorkManagerInitializer; это правило предназначено
для слияния с manifest WorkManager в приложении. Android lint после исправлений не находит issues.

### Временный init script

Для повторения sandbox-прогона содержимое `/private/tmp/w2-sync-tests.gradle`:

```groovy
def syncLocalMaven = new File(System.getProperty('user.home'), '.m2/repository')
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'robolectric.offline', 'true'
        systemProperty 'robolectric.dependency.dir', new File(syncLocalMaven,
            'org/robolectric/android-all-instrumented/15-robolectric-13954326-i7').path
    }
}
settingsEvaluated { settings ->
    def syncRepositories = settings.dependencyResolutionManagement.repositories
    syncRepositories.exclusiveContent {
        forRepository {
            syncRepositories.maven {
                url = syncLocalMaven.toURI()
                metadataSources { mavenPom(); artifact() }
            }
        }
        filter {
            includeVersion('androidx.exifinterface', 'exifinterface', '1.4.2')
            includeVersion('androidx.concurrent', 'concurrent-futures-ktx', '1.1.0')
            ['lifecycle-runtime', 'lifecycle-service', 'lifecycle-livedata', 'lifecycle-livedata-core'].each {
                includeVersion('androidx.lifecycle', it, '2.6.2')
            }
            includeVersion('androidx.profileinstaller', 'profileinstaller', '1.3.0')
            includeVersion('androidx.startup', 'startup-runtime', '1.2.0')
            includeVersion('androidx.tracing', 'tracing', '1.0.0')
            includeVersion('androidx.tracing', 'tracing', '1.2.0')
            includeVersion('androidx.tracing', 'tracing-ktx', '1.2.0')
        }
    }
}
```
