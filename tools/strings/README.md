# Строки из iOS

Python 3.8 или новее, только стандартная библиотека. Запуск из корня Android-репозитория:

```sh
python3 tools/strings/generate.py
python3 tools/strings/validate.py
python3 -m unittest discover -s tools/strings -p 'test_*.py' -v
python3 tools/strings/generate.py --check
```

Генератор читает `~/OneZeeProjects/trip-track`. Другой checkout можно передать через
`--ios /path/to/trip-track`. `--res` меняет каталог ресурсов, `--metadata` меняет каталог
отчётов. `--check` сравнивает результат с файлами на диске и возвращает код 1 при расхождении.
Он ничего не записывает. Генератор не обращается к сети и не меняет iOS-репозиторий.

Проверка настоящим Android-компилятором без Gradle:

```sh
python3 tools/strings/validate.py \
  --aapt2 "$ANDROID_HOME/build-tools/35.0.0/aapt2" \
  --android-jar "$ANDROID_HOME/platforms/android-36/android.jar"
```

`--aapt2` включает компиляцию, `--android-jar` добавляет линковку и проверку ссылки
`android:localeConfig`. Сборка использует временный каталог и удаляет его после проверки.
Версии путей можно заменить на установленные локально.

## Источники и результат

- `TripTrack/Localization/AppStrings*.swift`: `tr`, простые языковые `switch`, счётные слова.
- Другие `.swift` в том же каталоге с `extension AppStrings`, включая `AtlasCopy.swift`.
- `Translations/Translations+XX.swift`: словари, порядок `table.merge`, поздние `table[key]`.
- `TripTrack/Resources/<lang>.lproj/InfoPlist.strings`: ключи с префиксом `permission_`.
- `TripTrackShared/LiveActivityStrings.swift`: ключи с префиксом `live_`.
- `tools/strings/android.json`: Android-тексты и замены платформенных формулировок iOS.

Ресурсы лежат в `core/designsystem/src/main/res/values*/strings.xml`.
Генератор также пишет `ResourceAppStrings.kt` с типизированными методами для простых
функций и `ResourceTokenRules.kt` с порядком подстановок. Сложные ветки разбирает
`logic_resources.py`: текст берётся из Swift, выбор числа или состояния остаётся в Kotlin.
`res/xml/locales_config.xml` содержит все 13 языков. Подключить его к manifest приложения
через `android:localeConfig="@xml/locales_config"` должен агент, который собирает приложение.

`manifest.json` содержит SHA-256 каждого входного файла, происхождение ресурсов,
подстановки, числа строк, английские fallback и учёт каждой просмотренной функции.
[manual-porting.md](manual-porting.md) перечисляет ручной порт с файлами и строками.
Генератор обновляет оба файла. Статический `tr` внутри сложной функции выгружается,
а сама функция остаётся в списке как часть проверяемого Kotlin-инвентаря.

Неизвестное выражение не вычисляется. Функция попадает в ручной порт. Неизвестная
структура таблицы, повреждённый Swift или конфликт ресурсов останавливают генерацию.
Строки переводов без доступного английского `tr` перечислены отдельно в том же списке.
У значительной части таких строк английский текст находится в моделях значков, рангов
и клубов, за пределами этой задачи. Устаревшую запись нельзя отличить от живой только
по таблице; генератор её не удаляет и не придумывает английский текст.

## Имена, языки и подстановки

Имена сохраняют iOS-ключи, включая camelCase и точки (`riddleLine.pass`). `aapt2`
принимает такие имена; в поле `R` точка становится `_`. Валидатор запрещает коллизии
между именами вида `a.b` и `a_b` в одном типе ресурсов.

Английский лежит в `values/`. Индонезийский использует `values-in`: это исторический
код Android/Java для `id`. Филиппинский использует BCP 47-квалификатор `values-b+fil`,
так как у `fil` три буквы. `tl` обозначает тагальский и не заменяет `fil`.
В `locales_config.xml` записаны современные коды `id` и `fil`.
Эти квалификаторы подходят для minSdk 29. См.
[квалификаторы Android](https://developer.android.com/guide/topics/resources/providing-resources),
[старые коды Locale](https://developer.android.com/reference/java/util/Locale) и
[настройки языка приложения](https://developer.android.com/guide/topics/resources/app-languages).

Индекс `{token}` назначается по первому появлению в английской строке. Перестановка
слов и повторение токена сохраняют индекс. `String(intParameter)` даёт `%N$d`,
остальные значения дают `%N$s`: уже отформатированное число должно остаться строкой.
Соответствие токенов и индексов записано в `manifest.json`.

Шаблоны Foundation из `String(format:)` превращаются в позиционные Java-аргументы:
`%@` в `%N$s`, `%d` в `%N$d`. Строки с аргументами получает Kotlin-фасад: он
форматирует Foundation-шаблоны и сохраняет порядок замен `{token}`.
Литеральный `%` в такой строке записан как `%%`. Строки без аргументов имеют
`formatted="false"` и сохраняют обычный `%`, чтобы `getString(id)` не показывал `%%`.
XML-символы, кавычки, апострофы, обратные слеши, переводы строк, начальные `@`/`?`
и значимые пробелы экранируются отдельно. Исходный текст не меняется.

Перевод без ключа получает английский текст, как в iOS. Если перевод потеряет токен
или изменит тип аргумента, генератор запишет проблему с координатами исходника и
выдаст английский fallback. В текущей выгрузке таких ошибок нет.

## Plurals и границы порта

Простые `noun*` и языковые ветви `plural(...)` становятся `<plurals>` под именем
функции. `profileRoutineCount` также получает plural-ресурс; исходные ключи
`profileRoutineOne/Few/Many` сохраняются как строки. Категории:

| Языки | Категории |
|---|---|
| id | other |
| ru, uk, pl | one, few, many, other |
| es, fr, it, pt | one, many, other |
| en, de, tr, fil, kk | one, other |

Для `other` берётся `fraction ?? few ?? many`, как в `AppStrings.plural`;
если `few` не указан, берётся `many`. Дополнительная категория `many` романских
языков использует iOS `many`. Набор категорий сверён с
[CLDR cardinal rules](https://github.com/unicode-org/cldr-json/blob/main/cldr-json/cldr-core/supplemental/plurals.json).

`AppStrings.plural` сначала выбирает форму по правилам iOS, затем запрашивает её
через `getQuantityString`. Для Filipino это сохраняет `abs(n) <= 1`: системный CLDR
иначе относит 2 к singular. Это уже влияет на местоимения в сообщении о выходе.
Фасад использует `pt_BR`, как iOS; каталог ресурсов остаётся общим `values-pt`.

Android `getQuantityString` принимает целое число и не знает о видимых десятичных
знаках. Фасад выбирает формы единиц по `fractionDigits`; украинские «2 фути» и
«1.5 фута» хранятся отдельно. Его интерфейс `MeasureWords` подключает ресурсы к Measure.

## Android-тексты

`android.json` добавляет ключи и заменяет формулировки, которые отличаются на Android:
названия разрешений, системные настройки, вход через Google, подсказки надёжности записи.
Пример записи:

```json
{
  "android_example": {
    "values": {"en": "Open settings", "ru": "Открыть настройки"}
  }
}
```

В примере показаны два языка; настоящая запись обязана содержать все 13 кодов из
`LANGUAGES`. Пустой перевод, пропущенный язык, повторный JSON-ключ или потерянный токен
останавливают генерацию. Английского fallback у Android-текстов нет.
`replace: true` обязателен для замены существующего iOS-ресурса; неизвестный ключ
с таким флагом считается ошибкой. Имена и типы подстановок должны совпадать с iOS.
Подстановки пишутся как `{name}`, целые параметры перечисляются в `integer_tokens`.
Для диагностических дробных чисел `foundation_format: true` сохраняет позиционные
форматы вроде `%3$.2f`; генератор сверяет номера и типы аргументов во всех языках.
Для `kind: "plurals"` каждое значение содержит все категории языка из таблицы выше,
включая `one/few/many/other` для русского и украинского.

`--android /path/to/android.json` задаёт другой файл. Хеш источника и происхождение
каждой замены входят в `manifest.json`. Ресурсы генерируются в `core:designsystem`;
в остальных модулях импортируйте его `R`, не создавайте местные копии строк.
Наличие текстов PRO в ресурсах не включает покупки на Android.

## Kotlin-фасад

```kotlin
val strings = AppStrings(context, AppLanguage.ru)
strings.journeyDays(from = 2, to = 4)
Measure.distance(8400.0, DistanceUnit.miles, strings.language.code, strings)
val german = strings.withLanguage(AppLanguage.de)
```

Выбор языка явный. Конструктор без языка читает locale переданного configuration
context. При смене настройки приложение передаёт новый язык или создаёт новый фасад.
Даты используют locale этого языка и переданный `ZoneId` (по умолчанию системный).
`LocalizedDateFormatter.patterns/templates` кеширует форматтеры по шаблону и часовому
поясу, вызовы форматирования синхронизированы.

Подстановки `{token}` сохраняют порядок вызовов `replacingOccurrences` в Swift.
Токен, попавший в значение предыдущего параметра, тоже заменится на следующем шаге.
Foundation-форматы `%@`/`%d` идут через locale-aware форматирование. В ресурсах
`logic_*` набор аргументов может различаться по языкам: например, немецкая ветка
`earnedTimes` опускает существительное. `language_signatures` в manifest проверяет
каждую такую ветку отдельно. Lint-исключения ограничены этими форматами и plural-словами,
у которых число выводит вызывающий код.

Пять пропущенных функций с причинами записаны в `porting-status.json` и выводятся
в `manual-porting.md`. Robolectric проверяет наличие остальных функций.

Эталоны для сложных функций получаются запуском исходного Swift на macOS:

```sh
python3 tools/strings/ios_fixtures.py
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:lintDebug
```

Swift-функции, таблицы переводов, Foundation и UnitNumber исполняются без изменений.
Скрипт подставляет только типы связей с остальным приложением. Robolectric работает
на SDK 35 с JDK 17; SDK 36 требует Java 21. Для sandbox с готовым runtime можно добавить
`-Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7"`.
