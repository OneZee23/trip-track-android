# Изменение на двух платформах

iOS задаёт поведение, числа, wire-контракт, строки и внешний вид. Android хранит связи с
исходниками в комментариях `// iOS: TripDistanceGate.swift`. При совпадении имён указывай
путь: `// iOS: TripTrack/Models/TripDistanceGate.swift`.

## Перед задачей

Оркестратор фиксирует исходный и целевой iOS commit или тег, Android-ветку и критерий
готовности. Агент работает только в назначенном checkout. Второй репозиторий читает;
правки в нём выполняет отдельная задача в своей рабочей копии.

Из корня Android-репозитория:

```sh
python3 tools/parity/parity.py changed v0.9.2..HEAD
python3 tools/parity/parity.py report
```

Диапазон относится к iOS checkout `~/OneZeeProjects/trip-track`. Другой путь задаётся
через `--ios /path/to/trip-track`; `--android` меняет Android checkout. Оба параметра
принимаются до и после команды. Скрипт требует Python 3.8+ и Git, использует только
стандартную библиотеку Python. Команды `changed` печатают результат, `report` пишет
[parity.md](parity.md). `report --output <path>` меняет файл отчёта.

Для отдельного iOS-эталона передавай тот же `--ios` также в `strings/generate.py`, его `--check` и итоговый `parity.py report`: обычный checkout может быть старее.
Если sandbox запрещает Robolectric создать `~/.robolectric-download-lock`, добавь к Gradle `-Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7"` (SDK 35 из `tools/strings/README.md`).

`changed` показывает все изменённые пути, включая удаления и оба имени при переименовании.
`A..B` сравнивает два commit, `A...B` сравнивает общий предок с B. Соответствия берутся из
текущих Android-комментариев. Нужные commit должны уже быть в локальной истории; скрипт
ничего не скачивает. `no counterpart yet` означает, что связь ещё не найдена.

В отчёте каждая папка `TripTrack/` и `TripTrackShared/` имеет отдельный счётчик. Учитываются
Kotlin, Java и C/C++ под `src/`, включая тесты; `build/`, инструменты и `spikes/` исключены.
Swift-тесты из `TripTrackTests/` и `TripTrackUITests/` участвуют в связях, но не в счётчиках
продуктовых файлов. Наличие ссылки само по себе не подтверждает перенос всего поведения.

## Порт и проверка

1. Прочитай `AGENTS.md`, [port-plan.md](port-plan.md), [divergences.md](divergences.md),
   нужные разделы iOS `CLAUDE.md`, Swift-код и его тесты. Для сетевой задачи также прочитай
   [api-contract.md](api-contract.md).
2. Перенеси изменение вместе с тестами: те же входы, ожидаемые значения, пороги и округление.
   У каждого портированного типа и теста укажи Swift-файл в `// iOS:`. Можно перечислять
   несколько файлов, продолжать список следующей строкой `//`, использовать
   `Networking/{PlusAPI,SecretsAPI}.swift` и `AppStrings+*.swift`.
3. При изменении локализации выполни команды ниже. Напоминание появляется также для
   `InfoPlist.strings` и `TripTrackShared/LiveActivityStrings.swift`. Проверь
   `tools/strings/manual-porting.md`: сложная функция может требовать правки Kotlin-фасада.

   ```sh
   python3 tools/strings/generate.py
   python3 tools/strings/generate.py --check
   python3 -m unittest discover -s tools/strings -p 'test_*.py' -v
   ```

4. Изменился payload, ключ, enum raw value, единица хранения или семантика null/отсутствия?
   Обнови [api-contract.md](api-contract.md) и тесты encode/decode обеих платформ. Сохрани
   совместимость с выпущенными клиентами и сервером; текущий iOS-клиент задаёт контракт.
5. Обоснованное отличие Android запиши в [divergences.md](divergences.md). Для файла без
   Swift-пары добавь строку таблицы `| Android file | Why |`: полный относительный путь в
   первой ячейке, причина в последней. Parity подставит её в отчёт. Уникальное имя файла
   тоже работает. Общая запись об области без имени файла не назначает причину автоматически.
6. Запусти тесты затронутых модулей с JDK 17, по одной сборке в checkout:

   ```sh
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   ./gradlew --no-daemon --offline :core:logic:test
   python3 -m unittest discover -s tools/parity -p 'test_*.py' -v
   python3 tools/parity/parity.py report
   git diff --check
   ```

   Подставь свои модули. `--offline` убирается только для загрузки новой зависимости.
   CI проверяет весь проект: `./gradlew --no-daemon -Proborazzi.test.verify=false test lintDebug
   :app:assembleDebug :app:assembleRelease`, оба Python-набора, JDK 17, SDK 37.2,
   Build Tools 36.0.0, NDK и CMake из `core/map/build.gradle.kts`. `lintDebug` из корня
   включает app и все Android-модули core. `core:model` и `core:logic` чистые JVM-модули,
   Android Lint у них нет. При смене NDK/CMake обнови SDK-шаг в `.github/workflows/android.yml`.

### Скриншоты

Записывай и проверяй Roborazzi-снимки на Mac. Эталоны в `src/test/screenshots`
созданы на macOS; Ubuntu иначе рисует шрифты и тени. Обычный запуск тестов
проверяет снимки по умолчанию в app и core:designsystem:

```sh
./gradlew --no-daemon --offline :core:designsystem:test :app:test
```

После намеренного изменения внешнего вида запиши новые эталоны на Mac, проверь дифф
изображений и повтори обычную проверку:

```sh
./gradlew --no-daemon --offline -Proborazzi.test.record=true :core:designsystem:test :app:test
./gradlew --no-daemon --offline :core:designsystem:test :app:test
```

В CI задано `-Proborazzi.test.verify=false`: те же тесты создают Compose-экраны,
выполняют layout и рисование, ловят падения, но не сравнивают пиксели с Mac.
Roborazzi сохраняет эти кадры только в `<module>/build/screenshots/<test-task>/`;
эталоны в `src/test/screenshots` не меняются. Два выключенных Roborazzi-флага
полностью пропускают захват и рисование, поэтому для CI включена запись в этот
временный каталог с относительными путями от него. Оба свойства входят во входы Gradle-тестов:
Gradle различает результаты разных режимов в кэше. При `record=true` запись имеет
приоритет над проверкой. В новых тестах передавай относительные пути в
`captureRoboImage` и `captureScreenRoboImage`: абсолютный путь обходит перенаправление
в `build/`.

## Если исправление началось на Android

```sh
python3 tools/parity/parity.py changed-android <android-base>..HEAD
```

Команда читает комментарии из обеих ревизий диапазона: удалённый файл и заменённая ссылка
сохраняют старую Swift-пару. Текущие незакоммиченные правки не входят в диапазон.
Оркестратор передаёт список iOS-агенту. Если исправлено общее поведение, перенеси исправление
и регрессионный тест в iOS; после этого iOS снова служит эталоном. Строки сначала меняются
в iOS, затем генерируются для Android. Платформенное исправление получает причину в divergences.

Неоднозначные и несуществующие ссылки видны в `Marker warnings` отчёта. Уточни путь или имя
исходного файла. Ссылки только на символ или `.xcdatamodel` не считаются Swift-парой;
добавь ссылку на владеющий поведением Swift-файл, когда она есть.

## Сдача и выпуск

Агент пишет `docs/reports/<task>.md`: iOS-файлы и тесты, что сделано и осталось, точные
команды с результатами, отличия с причинами. `docs/port-status.md` ведёт оркестратор.
В этой задаче агент не коммитит и не пушит. Оркестратор проверяет дифф и фиксирует работу.
Сообщение коммита: одна строка, максимум четыре слова вместе с префиксом, без тела и
трейлеров. Пример: `feat: add parity tooling`. `Co-Authored-By` запрещён.

Перед фиксацией проверь публичный дифф на секреты и выполни проверку из `AGENTS.md`.
Функцию выпускают на обеих платформах после проверки её парности. Номера и сроки могут
различаться из-за магазинного ревью: правило версии, Android-теги и выпуск GitHub в день
публикации описаны в [release.md](release.md). В передаче между агентами всегда называй
commit обеих платформ и оставшийся объём; более свежий iOS-тег сам по себе не повышает
подтверждённую версию Android.
