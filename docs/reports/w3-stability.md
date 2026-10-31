# w3-stability

Android: `w3-stability`, база `1c121e91ebec04d2536a1195d3eaa2a11dbf04f6`.
iOS: `a155859d4dc915003c5f0f0d5e18816d4c3ac961`, checkout прочитан без изменений.

## Изменения

- `roborazzi.test.verify` читается в app и core:designsystem, по умолчанию `true`.
  При `false` Roborazzi рисует кадры в `<module>/build/screenshots/<test-task>/`,
  без сравнения. Оба выключенных флага Roborazzi пропускают сам захват, поэтому
  этот режим использует запись в отдельный каталог. Относительные пути эталонов
  разрешаются внутри него. `record=true` по-прежнему явно обновляет эталоны на Mac.
  Режимы входят во входы Gradle; временные кадры объявлены выходами задачи.
- CI использует `-Proborazzi.test.verify=false`, запускает `test`, корневой
  `lintDebug`, `:app:assembleDebug` и `:app:assembleRelease`. Правила записи и
  проверки на Mac описаны в `docs/how-to-port-a-change.md`.
- `TripTrackApplication` отделяет создание графа от запуска сервисов.
  Robolectric использует `TestTripTrackApplication`: граф создаётся по запросу,
  recording recovery, auth start, territory backfill и revealed-layer build не
  запускаются. Production вызывает прежний `AppGraph.start()`.
- `AppGraph` принимает scope владельца; auth наследует его Job с Main dispatcher.
  Завершение тестового Application отменяет app/auth/settings и закрывает поток
  recorder через `LocationGraph.close()`. Account-фикстура дожидается отмены задач
  до закрытия HTTP/Room и освобождает dispatcher. World/presentation-фикстуры
  исполняют отмену на своём scheduler до закрытия Room.
- `PhotoImport` читает публичные EXIF-теги вместо restricted `getDateTimeOriginal`.
  Как `PhotoMetadata.swift`: original, затем digitized; явное смещение зоны,
  иначе зона телефона; время редактирования не используется. Предел 2048 px и
  JPEG 80 сохранены. Неиспользуемый `BoxWithConstraints` в chart scrub заменён
  обычным `Box`; существующие снимки совпали.

Swift-источники: `TripTrackApp.swift`, `StoreHealth.swift` (`AppBootstrap`),
`TripPhotoPicker.swift`, `PhotoMetadata.swift`, `TripDetailSections.swift`.
Тесты: `StoreHealthTests.swift`, `PhotoMetadataTests.swift`; для контекста запуска
прочитаны `BackfillLatchTests.swift` и правила фоновых миграций в `CLAUDE.md`.
`PhotoImportTests` сохраняет JPEG и читает EXIF обратно; дата
`2026:09:10 14:23:05` и смещение `+03:00` взяты из iOS-тестов.

## Утечка и регрессии

До исправления четыре полных app-прогона и пять повторов пары
`ShellUnitTests` → `TripDetailStateTests` прошли. Исходный
`UncaughtExceptionsBeforeTest` в этих прогонах не воспроизведён.

Независимая регрессия подтвердила конкретную утечку: при временном возврате старого
`CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` у auth тест
`terminatingTheApplicationCancelsItsAuthWork` падает по таймауту 2000 ms.
Auth-корутина переживает отмену Application. С наследуемым Job проверка проходит.
Также проверено, что получение графа экрана не запускает recording recovery.
После отрицательной проверки возвращён наследуемый Job.

Для режима без сравнения временный тест рисовал `Canvas` с
`error("w3-render-crash-probe")`. При двух выключенных Roborazzi-флагах он ошибочно
прошёл; при захвате в `build/` упал внутри `LayoutNodeDrawScope`, как ожидалось
(18,11 с). Временный тест удалён. Эталоны не изменены.

## Команды и результаты

Все Gradle-команды запускались с JDK 17, `--no-daemon --offline`, по одной сборке:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export JAVA_TOOL_OPTIONS="-Drobolectric.dependency.dir=$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7"
```

`JAVA_TOOL_OPTIONS` выбирает уже скачанный Robolectric SDK 35: его download-lock
в домашнем каталоге недоступен sandbox. Первая попытка без этой настройки дала
ошибку lock-файла, после настройки все runtime-зависимости использованы offline.

Для записи порядка тестов использован `/tmp/w3-stability/order.init.gradle`:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        testLogging {
            events 'started', 'failed'
            exceptionFormat 'full'
        }
    }
}
```

Пять полных app-прогонов подряд с проверкой эталонов на Mac:

```sh
./gradlew --no-daemon --offline --init-script /tmp/w3-stability/order.init.gradle :app:testDebugUnitTest --rerun
./gradlew --no-daemon --offline --init-script /tmp/w3-stability/order.init.gradle :app:testDebugUnitTest --rerun :app:test
```

Первая команда выполнена один раз, вторая четыре раза. В каждом прогоне 495 тестов,
0 failures/errors/skipped. Время: 93,43; 79,26; 97,77; 70,06; 108,93 с.
`:app:test` включает debug unit tests; отдельной `:app:testReleaseUnitTest` в
текущей конфигурации AGP нет.

Полная проверка с принудительным исполнением всех задач, включая core-тесты,
lint и оба APK:

```sh
./gradlew --no-daemon --offline --rerun-tasks test lintDebug :app:assembleDebug :app:assembleRelease
```

Успех за 240,43 с, 653 задачи исполнены без восстановления из кэша.

Команда CI на Mac с временными кадрами вместо сравнения:

```sh
./gradlew --no-daemon --offline -Proborazzi.test.verify=false test lintDebug :app:assembleDebug :app:assembleRelease
```

Успех за 128,87 с: 79 задач исполнены, 2 из кэша, 572 up-to-date. App и
core:designsystem повторно исполнили тесты после смены режима; в `build/` получены
134 и 32 временных кадра соответственно. Полная локальная
проверка укладывается в 20 минут с запасом; загрузка SDK/NDK на Ubuntu здесь
не измерялась.

| Модуль | Тестов | Результат |
|---|---:|---|
| core:model | 84 | pass |
| core:logic | 662 | pass |
| core:data | 224 | pass |
| core:network | 81 | pass |
| core:sync | 127 | pass |
| core:location | 81 | pass |
| core:designsystem | 172 | pass |
| core:map | 54 | pass |
| app | 495 | pass |
| Всего | 1980 | 0 failures/errors/skipped |

`lintDebug`: 0 ошибок во всех семи Android-модулях; остаются предупреждения
(app 70, designsystem 42, location 2, map 5). У чистых JVM-модулей model/logic нет
Android Lint. Release также прошёл `lintVitalRelease`.

APK: `app/build/outputs/apk/debug/app-debug.apk` и
`app/build/outputs/apk/release/app-release-unsigned.apk`. Release не подписан,
как предусмотрено текущим build type.

Отрицательная проверка старого auth scope (32,35 с, ожидаемые 2 теста / 1 failure):

```sh
./gradlew --no-daemon --offline --init-script /tmp/w3-stability/order.init.gradle :app:testDebugUnitTest --rerun --tests '*ApplicationIsolationTests'
```

Отрицательная проверка ошибки при рисовании в режиме CI (ожидаемый 1 failure):

```sh
./gradlew --no-daemon --offline -Proborazzi.test.verify=false --init-script /tmp/w3-stability/order.init.gradle :core:designsystem:testDebugUnitTest --rerun --tests '*RenderCrashProbeTests'
```

Проверки инструментов:

```sh
python3 tools/strings/generate.py --check
python3 -m unittest discover -s tools/strings -p 'test_*.py' -v
python3 -m unittest discover -s tools/parity -p 'test_*.py' -v
python3 tools/parity/parity.py report --output /tmp/w3-stability/parity.md
git diff --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

Генератор: pass, 2,71 с; strings: 28 тестов, pass, 1,33 с; parity: 18 тестов,
pass, 5,06 с. Отчёт parity построен вне репозитория. Проверка секрета по шаблону
из AGENTS.md не нашла совпадений.

## Отличия и оставшееся

В `docs/divergences.md` добавлены отличия тестового Application и screenshot CI.
Это инфраструктура Android; продуктовое поведение запуска и wire-контракт сохранены.
Разбор EXIF-времени исправлен до поведения iOS. Зависимости, version catalog и
`gradle.properties` не менялись.

GitHub Actions на Ubuntu сможет проверить ветку после
публикации оркестратором; удалённый workflow из этого checkout не запускался.
Me, строка профиля shell, screenshots и `docs/port-status.md` не изменены.
Коммитов и push не было, эмулятор не запускался.
