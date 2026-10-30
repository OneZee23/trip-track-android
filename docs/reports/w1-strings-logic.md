# w1-strings-logic

Перенесены 175 из 180 записей функций ручного инвентаря. Пять пропусков с причинами
сохраняются в `tools/strings/porting-status.json` и сгенерированном `manual-porting.md`.
Дополнительно генератор создаёт 1480 типизированных методов простых строк и plurals.

## Что перенесено

- `AppStrings.swift`, `AppStringsCore.swift`, `AtlasCopy.swift`: параметры, счётные
  существительные, состояние, составные подписи, порядковые числа, Markdown-ссылки,
  Foundation-форматы и последовательные подстановки `{token}`.
- `AppStrings+Admin.swift`, `AppStrings+CosmeticRetention.swift`,
  `AppStrings+ProfileJournal.swift`, `AppStrings+RecordingOutcome.swift`: сложные функции
  расширений. Простые функции остальных `AppStrings+*.swift` входят в генерируемый фасад.
- `LanguageManager.swift`: стабильные коды, locale для 13 языков, `pt_BR`, порядок выбора
  и обнаружение поддерживаемого языка. Фасад принимает язык явно либо читает locale
  переданного configuration context. Turkish uppercase/lowercase учитывает язык.
- `AppStringsCore.swift`, `StatsScreenView.swift` (`StatsPeriodFormat`): patterns/templates,
  даты и родительный месяц. Форматтеры кешируются по шаблону и часовому поясу;
  форматирование потокобезопасно.
- `LiveActivityStrings.swift`: публичные слова и формы единиц. Android-методы используют
  общую реализацию AppStrings, чтобы не расходиться с основным интерфейсом.
- `MeasureWords` из `core:logic`: все 19 UnitWord, включая футы с дробной формой,
  полные названия расстояния и подписи приборки. Источники дополнительных ключей:
  `DistanceUnit+Current.swift`, `Badge.swift`; названия витрин сверены с `ProShowcase.swift`.

Все тексты читает Android Resources. XML производит только `tools/strings/generate.py`:
1677 strings и 30 plurals на язык. `logic_resources.py` извлекает недостающие языковые
ветки прямо из Swift; вложенные в интерполяцию вызовы `tr` также извлекаются.
`ResourceTokenRules.kt` сохраняет порядок замен, включая токен внутри предыдущего параметра.
Формы Filipino выбираются по правилу iOS перед обращением к Android plurals.

Изменён только build-файл `core:designsystem`: зависимости на `core:logic`, `core:model`
и существующие в каталоге JUnit, kotlin.test, Robolectric. Новых библиотек и изменений
version catalog нет. Можно передать готовый Robolectric runtime через системное свойство
`robolectric.dependency.dir`.

## Что осталось

- `accountAppleIdLine`: строка Apple ID относится к iOS; Android входит через Google.
- `notifAutoStartFailedBody`: тип `MapViewModel.StartRefusal` принадлежит ещё не перенесённому
  UI записи. Все три `notifStartFailed*` ресурса доступны будущему адаптеру.
- `table`: закрытый builder Foundation заменён Android ICU в LocalizedDateFormatter.
- `live_pick`: закрытый выбор слов виджета заменён language configuration context.
- `live_slavic`: закрытый дубль правила заменён общим pluralForm через live_distanceShort.

Остальные строки translation tables без самостоятельной Swift-функции помечены отдельно.
Их динамические владельцы (метаданные значков, клубов и прочее) остаются вне этой задачи.
Сохранение настройки языка и подключение фасада к экранам относится к слою приложения.

## Проверки

Источники случаев: `LocalizationTests.swift`, `UnitLabelTests.swift`,
`MeasureFormatTests.swift`, `LiveActivityUnitTests.swift`.

`python3 tools/strings/ios_fixtures.py` компилирует исходные string-функции, таблицы,
Foundation и UnitNumber без изменения. Заглушки обеспечивают только связи с типами
остального приложения. Получены 10 647 эталонов для 165 функций на 13 языках, включая
пустые/nullable значения, несколько чисел, оба состояния и токены внутри параметров.
Файл эталонов лежит в `core/designsystem/src/test/resources/ios/AppStrings.json`.

Команды из корня checkout:

```sh
python3 tools/strings/ios_fixtures.py
python3 -m unittest discover -s tools/strings
python3 tools/strings/generate.py --check
python3 tools/strings/validate.py
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:lintDebug \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  --console=plain
git diff --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

Результаты: 23 Python-теста, 93 Robolectric-теста; все 10 647 Swift-эталонов совпали.
Покрытие проверяет все 180 записей функций ручного инвентаря. Проверены ru/uk/pl/fil/id
plurals, все 20 noun-хелперов, percent/placeholder-подстановки, турецкий регистр, даты,
часовые пояса, единицы Measure и украинские порядковые с исходными expected values.
Генератор воспроизводим, валидатор проходит, diff без ошибок пробелов, проверка публичных
данных без находок. Дополнительно проверены новые файлы, которых ещё нет в индексе Git.

Gradle завершился успешно. Lint: 0 ошибок, 40 предупреждений. Сохранены исходные
пунктуация/слова iOS; также остаются предупреждения о plural-кандидатах,
locale config приложения и динамическом lookup в совместимом `tr`.
Исключения ImpliedQuantity относятся к counted words без числа; StringFormatCount
отключён только у извлечённых языковых веток с разными наборами параметров.

Первый запуск на SDK 36 остановился: Robolectric требует Java 21. Тесты выполнены на
SDK 35 с JDK 17 toolchain. Maven lock в корне пользовательского каталога недоступен
sandbox, поэтому использован уже установленный runtime через dependency.dir.
Эмулятор не запускался. Коммитов и push нет; `docs/port-status.md` не менялся.

## Отличия от iOS

Фасад и локаль хранятся в экземпляре; currency для pricePerLiter и ZoneId передаёт
вызывающий код. Date/DateFormatter заменены Instant и Android ICU. Это записано в
`docs/divergences.md`. Все проверенные строки, числа и даты совпадают с Swift на SDK 35;
таблицы ICU других версий ОС могут отличаться. Пропуски перечислены выше.
