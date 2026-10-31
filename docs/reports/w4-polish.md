# w4-polish

Портированы иконки запуска, splash и replay в полноэкранной карте поездки.
Социальные экраны не менялись. Коммитов и push нет.

## Источники и результат

- `Tools/AppIcon/D2.svg`, `D-tinted-preview.svg`, `README.md`,
  `Tools/build_app_icons.py`: adaptive и round для release/debug, Android 13
  monochrome, заметный DEV. Фон векторный; car foreground в пяти плотностях
  сохраняет размытую тень. Генератор: `tools/icons/build_android_icons.py`.
- `TripTrack/Info.plist`, пустой `UILaunchScreen`: пустой splash через
  `androidx.core:core-splashscreen:1.2.0`, светлый/тёмный фон окна и первого
  Compose-кадра. Зависимость добавлена только в каталог и `app`: она нужна для
  заказанного API. Уже была в локальном Gradle-кэше, offline не отключался.
  Выбор темы передаётся AppCompat и Android 12+ для следующего холодного старта
  ([Android guidance](https://developer.android.com/develop/ui/views/theming/darktheme)).
- `FullscreenMapSheet.swift` (`TripReplayEngine`), `ReplayBarState.swift`,
  `RoutePlaybackController.swift`: 1× по времени трека, 1/1.5/2/3/5×, пауза,
  подмотка с продолжением, сброс, последний кадр, 0.5 с после фона, 1.8 с у
  отметки. Без времён есть crawl: 1.4 с/км, 7–40 с и 1.2 с на финише.
- `RouteMapView.swift`, `CarHeadingPolicy.swift`, `MapCarMarker.swift` и три
  `map_car_*` PNG: машина в цвете транспорта, поворот по дороге, гистерезис
  24/18 м на dp, следование камеры, неизвестная скорость без пузыря. Длина
  пройденного участка управляет яркостью скоростных отрезков (впереди 0.3).
  Источники маршрута не перестраиваются на каждом кадре; отдельный источник
  двигает машину. Карточка отметки стоит над управлением. Отступы камеры и
  атрибуции разделены, чтобы не удваивать высоту панели.
- `TripReplayInput.swift`: используется существующая подготовка view model
  с общими индексами для координат, скоростей и времён и бюджетом 300 точек.
  Полная геометрия карты не прореживается этим бюджетом.

Тесты с iOS: `ReplayBarStateTests.swift`, `ReplayUnknownSpeedTests.swift`,
`PlaybackProgressTests.swift`, replay-часть `CarHeadingPolicyTests.swift` и
collapse-правила `MapCarMarkerTests.swift`. Сохранены входы и ожидания.
Дополнительно проверяются виртуальное время, все скорости, holds, фон,
повторный запуск crawl, затемнение и немедленный сброс.
UI-сценарии соответствуют `CompanionsAndReplayTests.test_replay_shows_map`
и `ReplayRelightShotTests.swift`. Roborazzi хранит 18 кадров управления в обеих
темах, включая 320 dp, и preview обычной/тематической DEV-иконки.

## Проверка

JDK 17:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Запись новых кадров и сборка:

```sh
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir=/Users/onezee/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7 \
  :core:map:testDebugUnitTest :app:testDebugUnitTest \
  --tests 'com.onezee.triptrack.replay.*' \
  --tests 'com.onezee.triptrack.feature.trip.TripDetailStateTests' \
  --tests 'com.onezee.triptrack.trips.TripsBehaviourTests' \
  -Proborazzi.test.record=true :app:assembleDebug :app:assembleRelease
```

Успешно: 115 тестов карты, 48 тестов app; оба APK собраны. Финальная
проверка сохранённых кадров, shell и обеих сборок:

```sh
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir=/Users/onezee/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7 \
  :core:map:testDebugUnitTest :app:testDebugUnitTest \
  --tests 'com.onezee.triptrack.replay.*' \
  --tests 'com.onezee.triptrack.feature.trip.TripDetailStateTests' \
  --tests 'com.onezee.triptrack.trips.TripsBehaviourTests' \
  --tests 'com.onezee.triptrack.ShellScreenshotTests' \
  :app:assembleDebug :app:assembleRelease
```

Успешно: 115 тестов карты, 52 теста app. Кадры совпадают с baseline, включая
четыре существующих кадра shell. Release проходит `lintVitalRelease`.
Артефакты: `app/build/outputs/apk/debug/app-debug.apk` и
`app/build/outputs/apk/release/app-release-unsigned.apk`.

Иконки сгенерированы командой:

```sh
python3 tools/icons/build_android_icons.py --resvg-module /private/tmp/triptrack-icon-render/node_modules/@resvg/resvg-js
```

Chromium завершается с SIGTRAP в sandbox; вместо него использован resvg 2.6.2
из временного каталога. Рендерер не входит в проект. `git diff --check` и
поиск SSH/IP/private-key-паттернов (`git grep` и проверка новых файлов) чистые.

## Различия и оставшаяся проверка

Все различия внесены в `docs/divergences.md`: adaptive safe zone, alpha-маска
monochrome, явный фон запуска, нативный рендеринг/часы и сопоставление
`holdingIndex` с отфильтрованными отметками. Тайминги и численные правила
движка совпадают с iOS.

Robolectric проверяет управление и ресурсы, без native GL и сетевых тайлов.
На устройстве остаётся проверить непрерывное движение/камеру, реальную
атрибуцию, маски разных launcher и холодный старт без вспышки. Эмулятор не
запускался по условию задачи. На Android 10/11 системный preview отключён: launcher остаётся виден
до готовности окна с сохранённой темой AppCompat, чтобы не было светлого
кадра перед тёмным интерфейсом. Изменений wire/storage нет.
