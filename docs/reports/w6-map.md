# w6-map

## Сделано

- Атрибуция на всех картах: общий `MapHost` устанавливает `HouseAttributionManager` вместо
  стандартного окна MapLibre. Кнопка «i», логотип и их отступы сохранены. Открывается
  `ContentSizedSheet` со всеми ссылками из источников текущего стиля; до загрузки TileJSON
  доступны OpenFreeMap, © OpenMapTiles и © OpenStreetMap contributors. Названия и ссылки
  провайдеров остаются лицензионными метаданными. Заголовок и ошибка отсутствующего браузера
  сгенерированы через `tools/strings/android.json` во всех 13 языках.
- Подписи Atlas: порт `RegionLabelAnnotation.swift` (`RegionLabelModel`, `RegionLabelView`),
  подключение существующего `RegionLabelLOD.swift` через `RegionLabelViews`. Подписаны только
  регионы с положительными `revealed.regionKm`, как в `FogVeilOverlay.visitedRegions`. Центроид
  и рамка берутся из bundled `MapRegions.json`. Имя локализуется и переводится в верхний регистр,
  расстояние форматирует `Measure`. Смена языка, единиц или слоя обновляет установленный список;
  каждый кадр карты, движение камеры и обновление геопозиции меняют только видимость и положение.
  Порог 140 pt, запас у синей точки 22 pt, уступание строке состояния, Inter 11/9 pt,
  разрядка 0.08 em и цвета обводки сохранены. Подписи не перехватывают нажатие на дорогу;
  TalkBack читает имя и расстояние одним элементом.
- Жилка и выбранный маршрут уже были векторными: `VeinLayers` и `RouteLayers` добавлены после
  тумана в `MapController`, Atlas передаёт оба слоя. Сверены `FogVeilVein.swift`, `VeinLayer`
  и `RouteVeinOverlay.swift`: сеть 2.2/2.8/3.0 pt, ореол mid 8 pt × 0.10 и far 10 pt × 0.14,
  выбранный маршрут 3.2 pt, обводка шире на 2 pt. Добавлены проверки этих чисел.
- Убрано `ACCESS_WIFI_STATE` через `tools:node="remove"` в manifest модуля `core:map`.
  В объединённых debug/release manifests по 16 разрешений, Wi-Fi разрешения нет.
  `docs/play/permissions.md` и `docs/play/data-safety.md` обновлены.
- У линии смены дат GL-слой рисует коридоры во всех видимых копиях мира, используя один VBO.
  Копии отбираются с запасом на ширину коридора; повёрнутый кадр и несколько миров на экране
  не ограничены соседними копиями. Короткий переход между +180° и −180° разворачивается до
  сборки mesh, чтобы не прожигать линию через весь мир. Прорезь вокруг машины тоже повторяется;
  сетка клеток остаётся привязана к миру.
- Debug Map demo получил `TripTrackTheme`, чтобы его кнопка атрибуции открывала тот же лист.

Публичные параметры `MapHost` сохранены; `regionLabels` добавлен в конце с пустым значением по
умолчанию. Социальные экраны, `PublicMapScreen`, release-конфигурация и `docs/port-status.md`
не менялись. В `core:map` добавлены зависимости на существующий `core:designsystem` для листа
и Inter, и на уже используемый Robolectric для проверки native text view. Каталог версий
и build-файлы других модулей не менялись; новые библиотеки не скачивались.

## Источники и тесты

Правило house dialogs сверено с iOS `CLAUDE.md` «Dialogs — always ours», подписи с разделами
«Ореол, облака, границы» и «Подписей ГОРОДОВ…», линии с «Жилка и выбранный маршрут — ВЕКТОРОМ».
Все семь случаев `RegionLabelModelTests.swift` перенесены с теми же исходными значениями.
Существующие 14 случаев `RegionLabelLODTests.swift` сохранены, включая уступание синей точке
и строке состояния. `VeinStyleTests` проверяет числовую часть `FogVeilVeinTests.swift`;
пиксельные тесты CoreGraphics/Metal требуют отдельной GL-проверки.

`FogWorldCopiesTests` выполняет JVM-утверждения над production-функциями из `fog_frame.hpp`:
тест собирает маленький host executable системным `c++`, без GLES/JNI. Проверены обе стороны
антимеридиана, несколько миров, дальний wrap камеры, отсечение, запас коридора и ближайшая
копия для live reveal. `FogAntimeridianMeshTests` проверяет Kotlin mesh при переходах в обе
стороны и повторных переходах; `RegionLabelGeometryTests` проверяет проекцию bbox и порог.

MapLibre 13.7.1
[ConnectivityReceiver.java](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/net/ConnectivityReceiver.java)
читает `ConnectivityManager.getActiveNetworkInfo()`. Проверка всех классов pinned `classes.jar`
не нашла ссылок на `WifiManager`, `android/net/wifi` или `ACCESS_WIFI_STATE`. Мировые копии
подтверждены исходниками
[tile_cover.cpp](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/src/mln/util/tile_cover.cpp)
и [TilePyramid.handleWrapJump](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/src/mln/renderer/tile_pyramid.cpp);
Atlas не запрещает соответствующие масштабы или перемещение через антимеридиан.
Резервная атрибуция сверена с [OpenFreeMap](https://openfreemap.org/#attribution).

## Проверки

JDK 17; все Gradle-запуски с `--no-daemon --offline`, по одному в checkout.

Запись двух baseline-изображений атрибуции и проверка map:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  -Proborazzi.test.record=true \
  :app:testDebugUnitTest --tests com.onezee.triptrack.map.MapAttributionTests \
  :core:map:testDebugUnitTest :core:map:lintDebug
```

PASS: 12 тестов атрибуции, 133 теста map, lint. Baseline-файлы
`app/src/test/screenshots/map_attribution_light.png` и `map_attribution_dark.png` просмотрены.
Проверяются все ссылки, сохранение дополнительных источников без дублей, резервные кредиты,
закрытие house-кнопкой и ошибка отсутствующего браузера.

Проверка baseline-изображений, регрессии Atlas/Places, остальных затронутых модулей и сборки:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  :app:testDebugUnitTest --tests 'com.onezee.triptrack.map.*' --tests 'com.onezee.triptrack.world.*' \
  :core:logic:test :core:map:testDebugUnitTest :core:designsystem:testDebugUnitTest \
  :core:map:lintDebug :app:assembleDebug :app:processReleaseMainManifest
```

PASS: app 119, logic 676, map 133, design system 185; всего 1113 тестов, без ошибок и пропусков.
`core:map:lintDebug` проходит; debug APK собирается, native-код собирается для arm64-v8a,
armeabi-v7a, x86 и x86_64. Проверены оба merged manifests: по 16 разрешений, `ACCESS_WIFI_STATE`
отсутствует. Полный набор тестов остальных экранов app не запускался.

```sh
python3 tools/strings/generate.py --ios ~/OneZeeProjects/trip-track-ios-ref --check
git diff --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

Генератор и whitespace-проверка проходят; поиск запрещённых данных ничего не нашёл.

## Отличия и проверка на телефоне

Отличия внесены в `docs/divergences.md`: другой провайдер атрибуции и house sheet, native
Android text views с последовательным разрешением столкновений вместо приоритетов MapKit,
явное повторение мира в custom GL layer. Числа LOD, текста, цветов и толщин линий не менялись.

Robolectric не рисует MapLibre, тайлы и GLES. На телефоне остаётся проверить:

- Атрибуцию на Atlas, поездке, местах и чужой карте; доступность кнопки над свёрнутыми листами,
  реальные переходы по ссылкам, Back и TalkBack в обеих темах.
- Подписи во время pinch/pan/rotation, переход через 140 pt, движущуюся синюю точку при
  неподвижной камере, уступание строке состояния и булавкам, попадание на дорогу под текстом.
- Коридоры и короткий маршрут с обеих сторон ±180°, несколько миров при дальнем масштабе,
  поворот, live reveal и «Клетки». Совпадение векторов с туманом и время кадра на реальном GPU.
- Загрузку тайлов и восстановление сети через Wi-Fi/мобильную сеть без `ACCESS_WIFI_STATE`.

Эмулятор не запускался. Коммитов и push не было.
