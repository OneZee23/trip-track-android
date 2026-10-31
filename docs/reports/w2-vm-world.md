Перенесён слой состояния Atlas, Places, Me, гаража и настроек. Composable-функций нет. Каждый экран использует AndroidX ViewModel, StateFlow и переданный CoroutineDispatcher. AppGraph, навигация, MainActivity, core:designsystem, core:location и соседние feature-пакеты не менялись.

| Область | iOS-источники | Что перенесено |
|---|---|---|
| Atlas | `MyMapViewModel.swift`, `ReloadCoalescer.swift`, `AtlasPresentation.swift`, `JournalBuilder.swift`, `AtlasHome.swift`, `MyMapView.swift` | Загрузка и кеш всего периода, склейка событий 250 мс, обновление без пустого кадра, периоды, журнал, регионы и их поездки, выбор дороги, булавки мест вне периода, дом, вид карты, состояние разрешения геопозиции, возврат из региона, одноразовые команды камеры |
| Геометрия | `AtlasSlot.swift`, `AtlasSheet.swift`, `AtlasSheetDetent.swift` | Чистые функции положений, жеста, резинки, затухания, атрибуции и координат контейнера; все исходные размеры и пороги |
| Постер | `AtlasSharePoster.swift` | Входы композиции, портретный кадр, запас 10 %, минимум 4 км, размер 540×960, масштаб 3, подпись 148, печати 34; рендера нет |
| Places | `PlacesTabViewModel.swift`, `PlaceDetailViewModel.swift`, `PlaceLastPass.swift` | Одна пакетная выборка проездов списка, подсказки через общий PlaceSuggestions вне UI с задержкой 300 мс, null/пустой ответ, сохранение подсказки, последнее сравнение, модальный поиск и сортировка; статистика места, плитки дней, 20 ниток, имена направлений и маршрутов из кеша geohash-5 |
| Me | `PreparedProfileHistory.swift`, `ProfileView.swift`, `ProfileHeroCard.swift`, `ProfileGarageHeader.swift`, `ProfileRoutineTrips.swift`, `RoadSceneTime.swift` | Подготовленная библиотека и исторические уровни, главы, повторы, закрепления, календарь, локальный поиск, режим журнала, первые 7 повторов, герой и время дороги, входы сцены машины, достижения и вход в черновики |
| Гараж | `GarageView.swift`, `VehicleDetailView.swift`, `VehicleStage.swift`, `VehicleEditFormView.swift`, `VehicleCatalogPickerView.swift`, `VehicleCatalogModelSteps.swift` | Активная машина, остальные и проданные; паспорт с географией, недавними поездками, фото и рекордами; пробег и годы; локальное состояние формы и пикеров; приборка и OdometerField, DRIVE2, теги, выбор модели/поколения/года/двигателя, явная очистка ID |
| Настройки | `SettingsManager.swift`, `HomeSettings.swift`, `HomeManager.swift`, `CloudSyncView.swift`, `PrivacySettingsView.swift`, `NotificationPreferencesView.swift`, `NotificationSwitches.swift` | Единицы, стартовая догадка, тема, 13 языков, автостарт и магнитолы, домашняя зона, стек листов; первая активация облака, очередь и повтор ошибок; приватность с поколениями по полю; уведомления с задержкой 400 мс и восстановлением категорий после выключения |

Дополнения к core:

- `core:logic/CarCatalog.kt` и исходный `CarCatalog.json`: schema 2, 19 марок, 50 моделей, 144 поколения, 429 модификаций. Модели данных уже были в core:model. Добавлены поиск, алиасы, годовые ограничения, resolve и VehicleCatalogSelection.
- `core:logic/AtlasPresentation.kt`: временный AtlasPreviewLayer вызывает существующие RevealBuilder и RevealedLayer; рядом перенесён VehicleYearDistance. Постоянные клетки не меняет.
- `core:logic/PlaceLastPass.kt`: отсутствовавшее сравнение последнего проезда. Пороги 300 секунд и 10 %, два предыдущих проезда того же направления.
- `core:logic/GarageRules.kt`: эффективные площадка, покрытие и теги; сохранённый платный выбор остаётся в данных.
- `core:data/SettingsStore.seedUnits`: стартовая догадка и перенос локальных единиц сохраняются без изменения lastModifiedAt. Обычная правка по-прежнему ставит время и через 5 секунд заказывает upload.

Добавлена зависимость `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4` в каталог и app. Она нужна для AndroidX ViewModel и его отменяемого scope. App также подключает существующие core-модули, Room, coroutines и serialization; тесты используют уже выбранные JUnit, Robolectric и coroutines-test. Новые библиотеки скачивать не пришлось. gradle.properties и build-файлы других модулей не менялись.

Точки подключения для следующей работы:

- AppGraph создаёт долгоживущий MyMapViewModel и остальные модели. RoomWorldChanges даёт изменения базы; события recording/sync добавляются через Flow<WorldChange>.
- Запись реализует AtlasLocationSource и RecordingSettings; Atlas не запускает GPS. Карта принимает AtlasCameraAction через AtlasMapConsumer, использует существующие MapHost/MapFog/MapCameraCommand и подтверждает cameraRevision.
- PlaceCreator вызывает менеджер мест с его историческим backfill. Прямой insert в PlaceStore для этого интерфейса не заменяет сверку истории.
- ProfileIdentitySource передаёт текущую публичную идентичность, достижения, обложки, главные фото машин и закрепления. Его запись закреплений должна использовать существующего владельца dirty/revision и синка профиля; модель истории не отправляет неподтверждённые данные сама.
- SyncSettingsEffects связывает настройки с SyncEngine: полный upload при включении, восстановление очередей через SyncCoordinator, переотправка домашней зоны. ApiProfilePrivacyService и ApiNotificationPreferencesService используют готовые ApiClient/DTO.
- Один NotificationPreferencesViewModel используется всем деревом настроек, включая приватность. UI рисует house-dialog по состоянию и вызывает confirmEnable/cancelEnable для первого включения Cloud Sync.

Остаются отрисовка экранов, ресурсы и ассеты, привязка этих интерфейсов в запрещённом для задачи AppGraph, системный переход в настройки разрешений, рендер и системная отправка постера. Публичная чужая карта, аккаунтные операции удаления/выхода, полный StatsScreen и редакторы галерей в эту задачу не включены.

Перенесены проверки из `AtlasSlotTests`, логика `AtlasRegionTests`, `ReloadCoalescerTests`, `AtlasSharePosterTests` (входы кадра), `PlacesTabViewModelTests`, `PlaceDetailViewModelTests`, `PlaceLastPassTests`, `PreparedProfileHistoryTests`, экранная часть `ProfileMonthlyJournalTests`, `OdometerFieldTests`, `CarCatalogSearchTests`, `GarageDataTests`, `SettingsUnitWireTests`, `HomeSettingsTests`; добавлены проверки гонки приватности и восстановления категорий уведомлений. Общие правила PlacesPresentation, PlaceSuggestions, HistoryFolding, RoadSceneTime и OdometerField проверены существующими тестами core:logic.

Проверка на JDK 17:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline --init-script /private/tmp/w2-vm-world-robolectric.init.gradle :core:logic:test :app:testDebugUnitTest :core:data:testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline --init-script /private/tmp/w2-vm-world-robolectric.init.gradle :app:testDebugUnitTest :app:compileReleaseKotlin
```

Обе команды завершились BUILD SUCCESSFUL. Финальные результаты: core:logic 607 тестов, core:data 224, app 57; ошибок и пропусков нет. Последняя команда проверяет финальные изменения app и компиляцию release. Эмулятор не запускался.

Первый запуск Robolectric остановился до тестов: sandbox запретил lock-файл в домашней папке. Временный init-script выбирает уже установленный SDK 35 и отключает загрузку:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'robolectric.offline', 'true'
        systemProperty 'robolectric.dependency.dir', new File(System.getProperty('user.home'), '.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7').absolutePath
    }
}
```

Расхождения перечислены в docs/divergences.md: Android lifecycle/Flow/Room, асинхронный первый снимок Places с loaded-флагом, строковое хранение локальных настроек. Проекция постера использует уже документированную шкалу MapPoints. Других намеренных изменений чисел или поведения нет.

`git diff --check`, проверка запрещённых шаблонов через `git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'` и отдельный rg по новым файлам прошли без замечаний. Коммиты и push не выполнялись; docs/port-status.md не менялся.
