# w2-foundation

## Сделано

- `tools/parity/parity.py`: отчёт по папкам iOS, связи в обе стороны, Android-only файлы
  с причинами из таблиц `docs/divergences.md`. Учтены списки, продолжения комментариев,
  пути, `{…}`, wildcard, одинаковые имена, удаления, переименования и диапазоны `..`/`...`.
  Для Android-диапазона читаются обе ревизии, включая старые комментарии. Python 3.8+,
  стандартная библиотека и локальный Git; сеть не используется.
- `docs/parity.md` сгенерирован по iOS `a155859d`: 648 Swift-файлов, 202 со ссылками на Android,
  446 без пары. 26 Android-файлов не имеют разрешённой Swift-ссылки; 27 предупреждений
  перечисляют символы без имени файла, не-Swift источники и несуществующие ссылки.
  Счётчик показывает связи файлов, а не полноту переноса поведения.
- `.github/workflows/android.yml`: push/PR в `master`, JDK 17, Python 3.12, SDK `android-37.2`,
  Build Tools `36.0.0`, Gradle cache, оба Python-набора, `test :app:assembleDebug`, лимит
  20 минут и отмена устаревшего запуска. `core:map` сейчас не задаёт NDK/CMake; установка
  NDK не требуется. Сборка CI выполняется с `--no-daemon`, с загрузкой зависимостей.
- Версия приложения вынесена в `version.properties`: `0.9.2-dev`, code 1. Целевая линия
  взята из iOS `project.yml`; суффикс обозначает незаконченный порт. Gradle читает этот файл.
- `docs/how-to-port-a-change.md`, `docs/release.md` и русский фрагмент
  `docs/ios-claude-section.md`: передача изменений агентам, тесты, строки, wire-контракт,
  отличия, подпись, Play tracks, версия, теги и выпуск GitHub в день выхода в магазине.

Продуктовый Swift/Kotlin и новые iOS-тесты в этой задаче не портировались.
Эталон процесса: iOS `CLAUDE.md` (коммиты, выпуск, локализация), `project.yml`,
`scripts/github-release.sh`, `docs/releases/0.9.2/candidate-99.md`.
Parity читает все `.swift` в `TripTrack/` и `TripTrackShared/`; для связей с тестами также
`TripTrackTests/` и `TripTrackUITests/`. Например: `TripDistanceGate.swift`,
`TripDistanceGateTests.swift`, `LocalizationTests.swift`, `LiveActivityStrings.swift`.

## Проверки

Все команды выполнены из корня этой рабочей копии.

```sh
python3 -m unittest discover -s tools/parity -p 'test_*.py' -v
```

18 тестов, OK. Временные Git-истории проверяют изменения, удаления, переименования,
строки, чтение старых и новых комментариев, `...`, ошибки диапазона. Реальные репозитории
тесты не изменяют; фикстуры создаются через `git commit-tree` во временных каталогах.

```sh
python3 -m unittest discover -s tools/strings -p 'test_*.py' -v
```

23 теста, OK. Включая существующие ожидания из iOS `LocalizationTests.swift`.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline :app:assembleDebug
```

BUILD SUCCESSFUL, 10 секунд, 36 задач. Проверены только затронутые настройки приложения.
Были предупреждения FSEvents sandbox и устаревающих Gradle API; сборка завершилась.
Метаданные APK подтвердили `versionName=0.9.2-dev`, `versionCode=1`:

```sh
python3 -c 'import json; from pathlib import Path; values = dict(line.split("=", 1) for line in Path("version.properties").read_text().splitlines() if line and not line.startswith("#")); apk = json.loads(Path("app/build/outputs/apk/debug/output-metadata.json").read_text())["elements"][0]; assert apk["versionName"] == values["versionName"]; assert apk["versionCode"] == int(values["versionCode"]); print("APK version matches version.properties")'
```

```sh
python3 tools/parity/parity.py report
python3 tools/parity/parity.py changed v0.9.1..v0.9.2
python3 tools/parity/parity.py changed-android HEAD~1..HEAD
```

Все команды завершились с кодом 0. Первая записала отчёт. Вторая показала реальные
iOS-изменения и напоминание генерации строк. Последний Android-коммит меняет только
документы, команда показала их с `no counterpart yet`.

```sh
ruby -e 'require "yaml"; w = YAML.load_file(".github/workflows/android.yml"); events = w.fetch("on") { w.fetch(true) }; %w[push pull_request].each { |event| raise "wrong branch" unless events.fetch(event).fetch("branches") == ["master"] }; job = w.fetch("jobs").fetch("build"); raise "wrong timeout" unless job.fetch("timeout-minutes") == 20; puts "Workflow triggers and timeout: OK"'
git diff --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

YAML и настройки событий/таймаута: OK. `git diff --check`: код 0. Проверка публичных
данных: совпадений нет (код 1); новые файлы отдельно проверены тем же выражением через `rg`.

## Осталось и отличия

- Workflow на GitHub здесь не запускался. Полный `test :app:assembleDebug` и время первого
  запуска подтвердит CI; локально собран только затронутый `app`.
- Существующие предупреждения ссылок и неперенесённые области остаются задачами владельцев
  модулей. Parity их показывает; Kotlin-файлы других модулей не менялись.
- Подписание release пока выполняется владельцем через Android Studio с внешним keystore.
  Gradle не читает внешний `keystore.properties`; автоматизация подписи, загрузка AAB,
  Play closed test и production выпуск в эту задачу не входят.
- iOS-репозиторий не менялся. Оркестратор добавит подготовленный русский раздел в его `CLAUDE.md`.
- Новых отличий продуктового поведения нет, `docs/divergences.md` не менялся. Номера сборок
  и Android-теги независимы от iOS из-за отдельного магазина; правила записаны в `docs/release.md`.
  Зависимости, version catalog, `gradle.properties` и `docs/port-status.md` не менялись.
  Коммитов и push нет.
