# w3-ui-me

Заглушка «Я» заменена профилем, журналом, гаражом и настройками. Ветка `w3-ui-me`, без коммитов и push. `docs/port-status.md` не менялся. Полного совпадения всех вспомогательных экранов iOS пока нет; остаток перечислен ниже.

- Герой профиля: четыре времени дороги, платформа и пиксельная машина, порядок с основной машиной первой, фотографии вместо спрайта, имя, аватар, биография, уровень и счётчики. Время меняется при возврате в приложение, без запроса GPS.
- Журнал: главы по месяцам, список/сетка, свёрнутые повторяющиеся поездки с семью первыми и раскрытием остальных, локальный поиск, календарный диапазон, закрепление трёх поездок, снятие отсутствующих на телефоне закреплений и вход в черновики. Названия регионов перерисовываются через таблицу `RegionDisplay`; разные языки одного региона не увеличивают счётчик.
- Достижения: сетка, описание с единицами `Measure`, повторные награды, скрытые неоткрытые награды и `BadgeArt`. Добавлены 58 vector drawable с исходными SVG-путями, четыре векторные дороги и 196 PNG спрайтов/платформ. Геометрия не упрощалась. Handjet и его OFL скопированы из iOS.
- Гараж: основная машина, остальные и проданные; выделение основной, меню удержания, возврат проданной, подтверждение продажи и удаления. Лимит считает пять непроданных машин. Выбор после продажи/удаления переходит на оставшуюся непроданную машину.
- Паспорт: сцена/главное фото, данные каталога, номер, уровень, поездки/регионы/дни, пробег и годы, карта маршрутов, фотографии машины и поездок, недавние поездки, рекорды, приватность и автозапись. DRIVE2 принимает только допустимый адрес и сохраняет нормализованное значение.
- Форма: основной каталог с поколением, годом и двигателем, резервный каталог iOS для всех четырёх типов транспорта, ввод своего названия, силуэт/цвет, приборка, одометр, ДВС/электро/гибрид, расход, цены и валюта. Отмена не пишет значения. Смена имени или единиц не округляет сохранённый пробег/расход. Правка описания сохраняет ID каталога; повтор после частичной ошибки использует ту же машину.
- Настройки: единицы и средняя скорость, тема, язык, автозапись и таймаут, магнитолы, категории уведомлений, публичность, домашняя зона, существующий Account & Sync, поддержка, журнал и сведения о приложении. Используются домовые пикеры, листы и диалоги. PRO скрыт через `MePlusGate`; платных витрин, замков и paywall нет.
- Профиль и фото подключены к Room, реальным настройкам, AuthService, API и общему хранилищу фото машин. Биография, закрепления, оформление и прогресс синхронизируются отдельно от Cloud Sync. Фото профиля имеют собственную очередь, лимит девять, удаление, обложку, восстановление URL и защиту от старого импорта после смены аккаунта. Нулевой/неизвестный XP не отправляется как сброс. Фото открываются полным экраном с фиксированным набором страниц.
- Обновления TripEntity во время активной записи отфильтрованы: пересчёт журнала/наград не запускается на каждом GPS-фиксе. Back внутри настроек и каталога возвращает на предыдущий шаг. Сетевые логи пишутся вне UI-потока, ограничены 48 часами/800 строками и очищаются при смене аккаунта.

AppGraph изменён локально: сборка `MeFeatures`, общий VehiclePhotoStore, очищаемые кэши/хранилища и профильный sync callback. В оболочке заменён один renderer. Новая зависимость только в `app`: прямая ссылка на уже имеющийся `libs.maplibre.opengl`, чтобы использовать MapSnapshotter. Версии, `gradle.properties` и build-файлы других модулей не менялись.

Соответствия iOS:

| Область | Исходники и тесты |
|---|---|
| Профиль/дорога | `ProfileView.swift`, `ProfileHeroCard.swift`, `ProfileGarageHeader.swift`, `ProfileRoadScene.swift`, `MyProfileView.swift`, `ProfileBackground.swift`; `ProfileCardShotTests`, `ProfilePreviewNavTests`, `VehicleAvatarCatalogTests` |
| Журнал | `ProfileHistoryCalendar.swift`, `ProfileRoutineTripsView.swift`, `ProfileTripCardView.swift`, `ProfileHistoryMonthHeader.swift`, `RegionDisplay.swift`; `ProfileMonthlyJournalTests.swift`, `PreparedProfileHistoryTests.swift`, `RegionDisplayTests.swift`, `ProfileJournalRulesTests.swift` |
| Фото/прогресс | `ProfilePhotoStore.swift`, `ProfileCoverPager.swift`, `ProfilePhotosView.swift`, `PhotoFullScreenView.swift`, `SettingsManager.swift`, `ProfileProgress.swift`; `ProfilePhotoStoreTests.swift`, `ProfileProgressSyncTests.swift` |
| Награды | `BadgeArt.swift`, `BadgesView.swift`, `BadgeDetailView.swift`, `BadgeDefinitions.swift`; `LocalizationTests.swift`, форматирование фигур во всех 13 языках |
| Гараж/форма | `GarageView.swift`, `VehicleDetailView.swift`, `VehicleStage.swift`, `VehicleSpritePlate.swift`, `GarageMileageCard.swift`, `VehicleEditFormView.swift`, `VehicleCatalogPickerView.swift`, `VehicleCatalogModelSteps.swift`, `VehicleCatalog.swift`, `VehiclePhotosView.swift`; `GarageProfileShotTests`, `VehicleCatalogPickerShotTests`, `OdometerFieldTests.swift`, все 21 случая `VehicleCatalogTests.swift` |
| Настройки | `ProfileSettingsSheet.swift`, `AppPreferencesView.swift`, `PrivacySettingsView.swift`, `NotificationSwitches.swift`, `NotificationPreferencesView.swift`, `AutoRecordSettingsView.swift`, `BluetoothScanSheet.swift`, `HomeSettingsView.swift`, `DebugLogsView.swift`, `ProductAnalytics.swift`; `ProductAnalyticsTests.swift`, существующие `SettingsManagerTests` |

Повторное получение ресурсов:

```sh
python3 tools/port_me_assets.py "$HOME/OneZeeProjects/trip-track"
python3 tools/strings/generate.py
```

Генератор строк дополнен названиями и описаниями BadgeDefinitions. В каждом языке теперь 1849 строк и 30 plurals; Android-only словарь не менялся.

Debug extras: `seed_garage_demo=true`, вместе с ним `seed_profile_card=true`; `debug_road_time` принимает `dawn`, `day`, `sunset`, `night`. Посев использует постоянные ID, приватные поездки и вымышленные подписи. Release их не читает.

Проверки выполнялись с общим окружением:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export JAVA_TOOL_OPTIONS="-Drobolectric.dependency.dir=$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7"
```

| Команда | Результат |
|---|---|
| `./gradlew --no-daemon --offline :app:testDebugUnitTest :app:lintDebug :app:compileReleaseKotlin` | 331 тест, 0 ошибок; lint и release-компиляция прошли |
| `./gradlew --no-daemon --offline :app:testDebugUnitTest :app:lintDebug :core:designsystem:testDebugUnitTest` | Дизайн-система: 172 теста прошли. Этот промежуточный общий запуск нашёл две Compose lint-ошибки; они исправлены и проверены последующей командой выше |
| `./gradlew --no-daemon --offline :app:testDebugUnitTest --tests '*MeScreenshotTests*' --tests '*ShellScreenshotTests.me*' -Proborazzi.test.record=true` | Записаны 18 снимков Me и два обновлённых снимка оболочки; последующий полный тестовый запуск проверил эталоны |
| `python3 -m unittest discover -s tools/strings -p 'test_*.py'` | 28 тестов прошли |
| `python3 tools/strings/generate.py --check` | Генерация совпала |
| `python3 tools/strings/validate.py` | Все 13 языков прошли проверку |
| `git diff --check` | Без ошибок |

Поиск заданным шаблоном ключей/IP/SSH не дал совпадений; отдельно проверены новые файлы, ещё не входящие в git index.

Roborazzi: светлая/тёмная темы, четыре дороги, журнал, гараж, паспорт, настройки и достижения; SDK 35, native graphics, русский, `w411dp-h914dp-420dpi`. Снимки просмотрены. В Roborazzi карты намеренно рисуют статический маршрут: native MapLibre и загрузка тайлов в этот тест не входят. Lint сохраняет предупреждения о размерах дороги и длине исходных SVG-путей, а также предупреждения существующего стека; подавлений не добавлено.

Осталось для полного совпадения с iOS и объединения веток:

- `MeRoute.Trip` и `MeRoute.Journey` пока показывают локальные read-only сводки. Их renderer нужно соединить с полными экранами параллельной ветки feed/trip. Черновики уже используют настоящую очередь подтверждения/удаления, но их карточка также ведёт в эту сводку.
- В дополнительном «Мой профиль» нет подписчиков/подписок, публичного preview и подробной статистики; админская панель не перенесена. Имя и биография пока редактируются одним листом. В просмотрщике фото есть свайпы, выбор главного и удаление, но ещё нет pinch/double-tap zoom.
- Контроллер ProductAnalytics сохраняет согласие, снимок и очередь удаления, отправляет их без учётных данных и повторяет на foreground. Владельцы записи и Atlas должны подключить его `recordingStarted`, `recordingFinished` и `atlasOpened`: точную задержку сохранения нельзя брать из UI StateFlow. В debug отправки выключены.
- Нужна проверка на устройстве: системный Photo Picker, Bluetooth/возврат разрешения, шаринг файла и native MapLibre. Эмулятор по условию не запускался. Серверные сценарии нового профиля/двух телефонов проверены по локальным контрактам и unit-тестам, не живым аккаунтом.

Платформенные отличия и временные пропуски также внесены в `docs/divergences.md`: MapLibre вместо MapKit, Android URI/`noBackupFilesDir`, неизвестный `takenAt` при импорте профиля, paired Bluetooth вместо audio routes, FileProvider и ограниченный набор логов, Android emoji/ICU и debug extras. Источники для платформенных решений: [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [MapLibre MapSnapshotter](https://maplibre.org/maplibre-native/android/examples/snapshotter/).
