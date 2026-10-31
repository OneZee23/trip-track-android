# w3-fixups

Исправлены строки Android, запросы геолокации и общее хранилище автозаписи.
Ветка `w3-fixups`; коммитов и push не было. Каталоги `feature/feed`, `trip`, `journey`
и `drafts` не изменялись.

- `tools/strings/android.json`: 72 ключа на 13 языках, включая 16 замен iOS-текста.
  Здесь теперь Google-вход, надёжность записи, OEM-подсказки, название приложения,
  подписи выделенных строк, LVL, логотип и debug-демо карты с названиями маркеров.
  Удалены отдельные XML-строки из `app/src/main`, `app/src/debug` и `core/location`.
  Генератор проверяет все языки, непустые значения, дубликаты, категории plurals,
  токены и позиционные числовые аргументы. Замена требует `replace: true` и существующий
  iOS-ключ. Источник и его хеш записываются в manifest. Результат: 1733 строки и
  30 plurals в каждом языке. Русские и украинские счётные формы сохранены;
  диагностические счётчики записаны как подпись и число, без склоняемых фраз.
- Исправлены упоминания iPhone, «Движение и фитнес», настроек iOS, Apple-входа и
  App Store. Обновление предлагает Google Play; скрытые строки подписок используют
  нейтральные формулировки. Apple-события серверной админки сохраняют своё название.
  В исходных ресурсах не нашлось других обещаний Live Activities, CarPlay или
  отдельной функции экрана блокировки, требующих Android-текста.
- `PermissionPlan.manifestNames` используется записью и онбордингом: точная и
  приблизительная геолокация запрашиваются вместе. Coarse-only не разрешает запись
  и не вызывает запрос фонового доступа. Экран записи и онбординг объясняют, зачем
  нужна точность, и открывают настройки приложения. Отказ позволяет закончить
  онбординг; повторного запроса нет. Возврат из настроек перечитывает разрешения.
- На Android 11+ оба экрана явно открывают настройки для фонового доступа и только
  после возврата переходят к физической активности. Подсказка называет путь
  «Разрешения → Местоположение → Разрешить в любом режиме».
  Это следует [правилам foreground-доступа](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
  и [фонового доступа](https://developer.android.com/develop/sensors-and-location/location/permissions/background).
- Семь `LocalContextGetResourceValueCall` в `RecordingScreens.kt` устранены через
  переданный `AppStrings`; исключения lint не добавлялись.
- `PreferencesAutoRecordSettings` хранит магнитолы, режим и таймаут в default preferences
  под iOS-ключами. `SettingsManager` получает того же владельца, что `LocationGraph`,
  и наблюдает его изменения. `RecordingSettings` только перевзводит датчики.
  Удаление машины очищает одну привязку вместо двух независимых копий.
- Миграция копирует отсутствующие ключи из `triptrack.autorecord`. Явные значения
  default preferences побеждают, включая Off и пустой список. Старые ключи удаляются
  после успешного синхронного сохранения. Старый строковый таймаут читается вместе
  с целочисленным; значение по умолчанию остаётся 3 минуты. Операции над списком
  используют общий lock и сравнивают Bluetooth-адреса без учёта регистра.

Соответствия iOS:

| Область | Исходники и тесты |
|---|---|
| Строки | `AppStrings*.swift`, `AppStrings+CosmeticRetention.swift`, `Translations+*.swift`, `InfoPlist.strings`, `LiveActivityStrings.swift`; `LocalizationTests.swift`, `UnitLabelTests.swift`, `MeasureFormatTests.swift` |
| Разрешения | `OnboardingPermissionCoordinator.swift`, `OnboardingLocationRequest.swift`, `OnboardingView.swift`, `RealGPSProvider.swift`, `MapViewModel.swift`; `OnboardingPermissionTests.swift` |
| Магнитолы | `SettingsManager.swift` (`loadAutoRecordSettings`, add/remove, vehicle lookup), `AutoTripService.swift`; `VehicleStateTests.testDeletingAVehicleTakesItsStereoBindingWithIt`, `VehiclePhotoRecoveryTests.testTombstoneUnbindsTheStereo` |
| Таймер автозаписи | `AutoStopOnPauseTests.swift`, `AutoTripDraftPolicyTests.swift`; входы и ожидание 180000 ms сохранены, тестовая корутина запускается детерминированно |

Добавлены проверки полноты Android-переводов, форматов и plurals; миграции, удаления
привязок и общего владельца; coarse-only и последовательности разрешений. Два UI-теста
проверяют реальный запрос fine+coarse, переход после approximate, ссылку в настройки
и открытие настроек фонового доступа до запроса физической активности.
Четыре снимка обновлены после просмотра diff: тема и фоновый онбординг в обеих темах.
Swift-эталоны не менялись; у `cosmeticRetainedChoice` проверка заменяет только название
устройства в ожидаемом тексте на соответствующее слово для телефона во всех языках.

Проверки из корня checkout:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export JAVA_TOOL_OPTIONS="-Drobolectric.dependency.dir=$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7"

python3 tools/strings/generate.py --check
python3 tools/strings/validate.py
python3 -m unittest discover -s tools/strings -p 'test_*.py' -v

./gradlew --no-daemon --offline :core:model:test :core:logic:test :core:data:testDebugUnitTest :core:network:testDebugUnitTest :core:sync:testDebugUnitTest :core:location:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:map:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

./gradlew --no-daemon --offline :core:designsystem:testDebugUnitTest --tests '*ComponentScreenshotTests.themePicker*' :app:testDebugUnitTest --tests '*AccountScreenshotTests.onboardingBackground*' -Proborazzi.test.record=true

git diff --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

Результат: `generate.py --check`, validate и 28 Python-тестов проходят.
Все модульные тесты проходят: model 84, logic 662, data 224, network 81, sync 127,
location 81, designsystem 172, map 54, app 242. Всего 1727; ошибок и пропусков нет.
Lint: 0 ошибок, 14 предупреждений (сторонние проверки, target SDK, Log, иконка и
существующие KTX-подсказки); debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Снимки записаны отдельной командой выше, затем проверены обычным прогоном.
`git diff --check` и поиск адресов/ключей без находок.

Первые прогоны: Robolectric не смог создать lock в домашнем каталоге; готовый локальный
runtime через `JAVA_TOOL_OPTIONS` устранил это без загрузок и правок сборки. Один тест
автозаписи читал список до запуска корутины на `Dispatchers.Default`; тест переведён на
`Unconfined` с прежним управляемым ожиданием. Ожидаемые ошибки эталонов после замены
текстов исправлены, последующие проверки проходят. Зависимости и build-файлы не менялись.

Отличия внесены в `docs/divergences.md`: Android-тексты, precise/approximate и переход
в системные настройки, миграция прежних хранилищ. В текущем iOS checkout нет отдельной
ветки reduced accuracy: `RealGPSProvider` сообщает denied/restricted, а `FixGate`
отбрасывает неточные фиксы. Android показывает явное объяснение для coarse-only по
условию задачи; принцип экрана отказа взят из `MapViewModel.startRecording`.

По этой задаче незавершённого кода нет. На устройстве ещё нужно проверить системные
диалоги, возврат из настроек и названия OEM-пунктов на разных версиях прошивок.
Эмулятор не запускался. Незавершённые части записи и Google-входа из предыдущих
отчётов остаются за пределами этой задачи.
