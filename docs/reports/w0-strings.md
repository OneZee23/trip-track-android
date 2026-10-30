# w0-strings

Генератор и валидатор готовы. Для каждого из 13 языков выгружено 1571 строка и
25 plural-ресурсов. Из них 7 строк имеют префикс `permission_`, 15 имеют `live_`.
Gradle-файлы и `docs/port-status.md` не менялись.

## Запуск

Из корня Android-репозитория:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/strings/generate.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/strings/validate.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/strings/generate.py --check
```

По умолчанию источник находится в `~/OneZeeProjects/trip-track`.
Другой путь: `--ios /path/to/trip-track`. Все опции и правила вызова ресурсов
описаны в [tools/strings/README.md](../../tools/strings/README.md).

## Что перенесено

- `AppStrings.swift`, `AppStringsCore.swift`, `AppStrings+Admin.swift`,
  `AppStrings+CosmeticRetention.swift`, `AppStrings+ProfileDesign.swift`,
  `AppStrings+ProfileJournal.swift`, `AppStrings+RecordingOutcome.swift`,
  `AppStrings+RemoteConfig.swift`, `AppStrings+TripHistory.swift`, `AppStrings+Usage.swift`.
- `AtlasCopy.swift`, который тоже расширяет `AppStrings`.
- Все одиннадцать `Translations/Translations+XX.swift` с порядком merge и override.
- Все тринадцать `Resources/<lang>.lproj/InfoPlist.strings`.
- Статические слова `TripTrackShared/LiveActivityStrings.swift`.

Парсер понимает обычные, raw и многострочные Swift-литералы, escape-последовательности,
Unicode, конкатенацию и комментарии. Из сложных функций извлекаются доступные
статические `tr`-шаблоны; сама функция остаётся в ручном порте. Статическая константа
`activityAchievementActor` тоже выгружена.

SHA-256 входных файлов, координаты каждого ресурса, аргументы и fallback находятся
в [manifest.json](../../tools/strings/manifest.json). Учтены 1660 функций:
1480 преобразованы полностью, 180 требуют переноса логики.

## Числа по языкам

| Язык | Каталог | strings | plurals | Английских fallback |
|---|---|---:|---:|---:|
| en | `values` | 1571 | 25 | 0 |
| ru | `values-ru` | 1571 | 25 | 0 |
| de | `values-de` | 1571 | 25 | 24 |
| es | `values-es` | 1571 | 25 | 24 |
| fr | `values-fr` | 1571 | 25 | 24 |
| it | `values-it` | 1571 | 25 | 24 |
| pl | `values-pl` | 1571 | 25 | 24 |
| id | `values-in` | 1571 | 25 | 24 |
| tr | `values-tr` | 1571 | 25 | 24 |
| fil | `values-b+fil` | 1571 | 25 | 24 |
| uk | `values-uk` | 1571 | 25 | 24 |
| kk | `values-kk` | 1571 | 25 | 24 |
| pt | `values-pt` | 1571 | 25 | 24 |

Все 24 fallback на каждом из одиннадцати дополнительных языков относятся к
админке: в iOS она намеренно переведена только на русский и английский.
Ошибок подстановок в текущих переводах нет.

## Решения и отличия Android

- Английский находится в `values/`. Ключи iOS сохранены, включая camelCase и точки.
  Точка становится `_` только в поле `R`; валидатор проверяет коллизии.
- Индонезийский: `values-in`, исторический код Android для `id`.
  Филиппинский: `values-b+fil`, BCP 47 для трёхбуквенного языка.
  В `locales_config.xml` записаны `id` и `fil`. Manifest приложения подключит другой агент.
- `{token}` получает постоянный индекс по английскому тексту. `String(Int)` даёт
  `%N$d`, остальные подстановки дают `%N$s`. Форматированные числа остаются строками.
  Foundation `%@` и `%d` нормализованы для Android, их функции перечислены ниже.
- Апострофы, кавычки, XML-символы, обратные слеши, переносы строк, начальные `@`/`?`
  и значимые пробелы экранируются. Литеральный `%` удваивается только в шаблонах
  с аргументами; остальные строки имеют `formatted="false"`.
- Plural-категории: `id` использует `other`; `ru/uk/pl` используют
  `one/few/many/other`; `es/fr/it/pt` используют `one/many/other`;
  остальные используют `one/other`. `other` берётся из `fraction ?? few ?? many`.
- У iOS упрощённое plural-правило для `fil`. В текущей выгрузке `one` и `other`
  совпадают у всех 25 филиппинских plural-ресурсов, поэтому видимый текст совпадает.
  При новых разных формах потребуется проверка выбора категории в Kotlin.
- iOS использует локаль `pt_BR`; ресурсы Android имеют общий `pt`. При переносе
  `LanguageManager` нужно сохранить бразильские правила чисел и выбора форм,
  в том числе для пользователя с устройством `pt-PT`.
- Дробные значения и видимые десятичные знаки нельзя передать в `getQuantityString(Int)`.
  `Measure`, переключение км/миль и соответствующие Live Activity-функции оставлены
  в ручном порте. Округление и правила iOS здесь не менялись.
- Тексты iOS-разрешений, iPhone, Apple и покупок сохранены дословно. Настройка
  Android-разрешений и исключение покупок относятся к будущим экранам и manifest.

Основания для квалификаторов и категорий приведены в README. Полный перенос
`LanguageManager`, `Measure` и функций форматирования эта задача не включает.

## Проверки

Выполнены команды:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/strings/generate.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/strings -p 'test_*.py' -v
PYTHONDONTWRITEBYTECODE=1 python3 tools/strings/generate.py --check
PYTHONDONTWRITEBYTECODE=1 python3 tools/strings/validate.py --aapt2 "$HOME/Library/Android/sdk/build-tools/35.0.0/aapt2" --android-jar "$HOME/Library/Android/sdk/platforms/android-36/android.jar"
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

23 теста прошли. `--check` не нашёл расхождений. Валидатор проверил наличие всех
выгруженных ключей в default, одинаковые наборы ключей, escaping, аргументы,
категории, число ресурсов и учёт ручных функций. `aapt2 compile` и `link` прошли
с Android SDK 36 без Gradle и эмулятора. Поиск секретов не дал совпадений.

В тесты перенесены значения из iOS `LocalizationTests.swift`: `cancel` для пяти
языков; русские формы для 1, 2, 5, 11, 21, 22, 111; польское отличие на 21;
французский ноль; единая индонезийская форма; `{date}` и слова Live Activity.
Тесты ресурсов проверяют слова в категориях, а не запуск Android ICU.
Правила `pluralForm` и тесты Measure остаются будущему Kotlin-порту.

Коммиты не созданы: sandbox разрешает только чтение `.git`, и `git add` завершился
ошибкой создания `index.lock` (`Operation not permitted`). Ветка остаётся `w0-strings`.

## Ручной порт

Полный список с причинами и координатами всех одиннадцати переводов находится в
[manual-porting.md](../../tools/strings/manual-porting.md). В нём 2248 записей:
180 функций и 2068 строк переводов для 188 уникальных ключей. Следующие функции
требуют логики, интерполяции, форматирования, выбора единиц или доменного типа.
Их доступные статические `tr` уже присутствуют в ресурсах.

| iOS-файл:строка | Функция |
|---|---|
| `TripTrack/Localization/AppStrings+Admin.swift:25` | `adminProSplit` |
| `TripTrack/Localization/AppStrings+Admin.swift:30` | `adminTripsLine` |
| `TripTrack/Localization/AppStrings+CosmeticRetention.swift:7` | `cosmeticRetainedChoice` |
| `TripTrack/Localization/AppStrings+ProfileJournal.swift:85` | `profileChapterSummary` |
| `TripTrack/Localization/AppStrings+RecordingOutcome.swift:59` | `recordingDiscardReason` |
| `TripTrack/Localization/AppStrings.swift:51` | `placeLastPass` |
| `TripTrack/Localization/AppStrings.swift:55` | `placeUsually` |
| `TripTrack/Localization/AppStrings.swift:195` | `placeHereTimes` |
| `TripTrack/Localization/AppStrings.swift:199` | `placeChipUsually` |
| `TripTrack/Localization/AppStrings.swift:232` | `atlasPlacesInPeriod` |
| `TripTrack/Localization/AppStrings.swift:303` | `placesCount` |
| `TripTrack/Localization/AppStrings.swift:403` | `groupsWaitlistCount` |
| `TripTrack/Localization/AppStrings.swift:424` | `formattedCount` |
| `TripTrack/Localization/AppStrings.swift:468` | `clubWaitingCount` |
| `TripTrack/Localization/AppStrings.swift:797` | `unitDistanceShort` |
| `TripTrack/Localization/AppStrings.swift:828` | `unitSpeedShort` |
| `TripTrack/Localization/AppStrings.swift:847` | `unitElevationShort` |
| `TripTrack/Localization/AppStrings.swift:874` | `unitDistanceBadge` |
| `TripTrack/Localization/AppStrings.swift:966` | `pricePerLiter` |
| `TripTrack/Localization/AppStrings.swift:1058` | `totalKm` |
| `TripTrack/Localization/AppStrings.swift:1173` | `onboardingConsentMarkdown` |
| `TripTrack/Localization/AppStrings.swift:1233` | `earnedTimes` |
| `TripTrack/Localization/AppStrings.swift:1323` | `mapOpenedSummary` |
| `TripTrack/Localization/AppStrings.swift:1339` | `mapOpenedLabel` |
| `TripTrack/Localization/AppStrings.swift:1353` | `mapSealsCount` |
| `TripTrack/Localization/AppStrings.swift:1362` | `mapRiddlesNear` |
| `TripTrack/Localization/AppStrings.swift:1381` | `sealKind` |
| `TripTrack/Localization/AppStrings.swift:1393` | `sealAccessibility` |
| `TripTrack/Localization/AppStrings.swift:1413` | `journalFindsSection` |
| `TripTrack/Localization/AppStrings.swift:1452` | `cardFoundBy` |
| `TripTrack/Localization/AppStrings.swift:1512` | `posterCaption` |
| `TripTrack/Localization/AppStrings.swift:1565` | `mapRegionSince` |
| `TripTrack/Localization/AppStrings.swift:1571` | `mapKmDriven` |
| `TripTrack/Localization/AppStrings.swift:1590` | `mapCitiesOfTotal` |
| `TripTrack/Localization/AppStrings.swift:1620` | `decimalSeparator` |
| `TripTrack/Localization/AppStrings.swift:1635` | `mapTripsSection` |
| `TripTrack/Localization/AppStrings.swift:1642` | `mapRoadTrips` |
| `TripTrack/Localization/AppStrings.swift:1672` | `monthYear` |
| `TripTrack/Localization/AppStrings.swift:1684` | `groupedNumber` |
| `TripTrack/Localization/AppStrings.swift:1688` | `regionsGenitive` |
| `TripTrack/Localization/AppStrings.swift:1691` | `citiesGenitive` |
| `TripTrack/Localization/AppStrings.swift:1694` | `tripsGenitive` |
| `TripTrack/Localization/AppStrings.swift:1714` | `publishedTripsSignOutMessage` |
| `TripTrack/Localization/AppStrings.swift:1812` | `exploredPercent` |
| `TripTrack/Localization/AppStrings.swift:1837` | `gpsAccuracyBody` |
| `TripTrack/Localization/AppStrings.swift:1872` | `movingDot` |
| `TripTrack/Localization/AppStrings.swift:1878` | `stopsDot` |
| `TripTrack/Localization/AppStrings.swift:1889` | `reactionsTitleN` |
| `TripTrack/Localization/AppStrings.swift:1927` | `chartMaxElev` |
| `TripTrack/Localization/AppStrings.swift:1941` | `chartMaxAvg` |
| `TripTrack/Localization/AppStrings.swift:1964` | `commentsTitleN` |
| `TripTrack/Localization/AppStrings.swift:1971` | `discussionSeeAllPill` |
| `TripTrack/Localization/AppStrings.swift:2008` | `relTimeMinutes` |
| `TripTrack/Localization/AppStrings.swift:2011` | `relTimeHours` |
| `TripTrack/Localization/AppStrings.swift:2014` | `relTimeDays` |
| `TripTrack/Localization/AppStrings.swift:2259` | `autoStopMinutes` |
| `TripTrack/Localization/AppStrings.swift:2296` | `notifTripStartBody` |
| `TripTrack/Localization/AppStrings.swift:2343` | `checkpointDefaultName` |
| `TripTrack/Localization/AppStrings.swift:2430` | `segmentHistory` |
| `TripTrack/Localization/AppStrings.swift:2486` | `journeyDay` |
| `TripTrack/Localization/AppStrings.swift:2490` | `journeyDays` |
| `TripTrack/Localization/AppStrings.swift:2502` | `journeyAroundTownCount` |
| `TripTrack/Localization/AppStrings.swift:2655` | `journeyPublishAlreadyPublic` |
| `TripTrack/Localization/AppStrings.swift:2665` | `journeyPublishButton` |
| `TripTrack/Localization/AppStrings.swift:2744` | `notifTripStopBody` |
| `TripTrack/Localization/AppStrings.swift:2825` | `badgeLastEarned` |
| `TripTrack/Localization/AppStrings.swift:2847` | `longDate` |
| `TripTrack/Localization/AppStrings.swift:2853` | `badgeEarnedOn` |
| `TripTrack/Localization/AppStrings.swift:2927` | `chartDistanceMark` |
| `TripTrack/Localization/AppStrings.swift:2943` | `chartMileMark` |
| `TripTrack/Localization/AppStrings.swift:2974` | `ukrainianOrdinalFeminine` |
| `TripTrack/Localization/AppStrings.swift:2994` | `chartKmMark` |
| `TripTrack/Localization/AppStrings.swift:3055` | `notifAutoStartFailedBody` |
| `TripTrack/Localization/AppStrings.swift:3086` | `notifAutoStopSummary` |
| `TripTrack/Localization/AppStrings.swift:3134` | `draftsAllMine` |
| `TripTrack/Localization/AppStrings.swift:3140` | `draftsMineCount` |
| `TripTrack/Localization/AppStrings.swift:3154` | `draftsSelectedCount` |
| `TripTrack/Localization/AppStrings.swift:3178` | `draftsDeleteTitle` |
| `TripTrack/Localization/AppStrings.swift:3265` | `deleteAccountTitle` |
| `TripTrack/Localization/AppStrings.swift:3321` | `syncAttemptsLabel` |
| `TripTrack/Localization/AppStrings.swift:3380` | `signInLegalMarkdown` |
| `TripTrack/Localization/AppStrings.swift:3479` | `entityLabel` |
| `TripTrack/Localization/AppStrings.swift:3496` | `actionLabel` |
| `TripTrack/Localization/AppStrings.swift:3624` | `vehicleSoldState` |
| `TripTrack/Localization/AppStrings.swift:3723` | `publicAboutBy` |
| `TripTrack/Localization/AppStrings.swift:3786` | `outOf` |
| `TripTrack/Localization/AppStrings.swift:3927` | `avatarColorName` |
| `TripTrack/Localization/AppStrings.swift:3941` | `avatarStyleName` |
| `TripTrack/Localization/AppStrings.swift:4022` | `inGarageSince` |
| `TripTrack/Localization/AppStrings.swift:4040` | `sinceYear` |
| `TripTrack/Localization/AppStrings.swift:4150` | `fuelPricePerUnit` |
| `TripTrack/Localization/AppStrings.swift:4210` | `vehicleLevelToNext` |
| `TripTrack/Localization/AppStrings.swift:4249` | `vehicleLevelAndAbove` |
| `TripTrack/Localization/AppStrings.swift:4345` | `activityReactedTo` |
| `TripTrack/Localization/AppStrings.swift:4366` | `activityCommented` |
| `TripTrack/Localization/AppStrings.swift:4414` | `activityAchievementUnlocked` |
| `TripTrack/Localization/AppStrings.swift:4567` | `accountAppleIdLine` |
| `TripTrack/Localization/AppStrings.swift:4582` | `syncUpdatedAgo` |
| `TripTrack/Localization/AppStrings.swift:4614` | `syncOffPublishing` |
| `TripTrack/Localization/AppStrings.swift:4622` | `syncingProgress` |
| `TripTrack/Localization/AppStrings.swift:4628` | `syncQueuedCount` |
| `TripTrack/Localization/AppStrings.swift:4721` | `syncLastAt` |
| `TripTrack/Localization/AppStrings.swift:4724` | `syncFailedCount` |
| `TripTrack/Localization/AppStrings.swift:4751` | `blockedSince` |
| `TripTrack/Localization/AppStrings.swift:4784` | `achievementsProgress` |
| `TripTrack/Localization/AppStrings.swift:4825` | `calendarFilterActive` |
| `TripTrack/Localization/AppStrings.swift:4829` | `statsKmTotal` |
| `TripTrack/Localization/AppStrings.swift:4859` | `statsKmByMonth` |
| `TripTrack/Localization/AppStrings.swift:4896` | `daysCount` |
| `TripTrack/Localization/AppStrings.swift:4900` | `tripsCount` |
| `TripTrack/Localization/AppStrings.swift:5040` | `nameHelper` |
| `TripTrack/Localization/AppStrings.swift:5058` | `followersCount` |
| `TripTrack/Localization/AppStrings.swift:5062` | `followingCountLabel` |
| `TripTrack/Localization/AppStrings.swift:5076` | `streakDaysInARow` |
| `TripTrack/Localization/AppStrings.swift:5094` | `repeatRouteTimes` |
| `TripTrack/Localization/AppStrings.swift:5234` | `commentReplyingTo` |
| `TripTrack/Localization/AppStrings.swift:5279` | `blockProfileAction` |
| `TripTrack/Localization/AppStrings.swift:5292` | `blockProfileConfirmTitle` |
| `TripTrack/Localization/AppStrings.swift:5301` | `blockProfileConfirmBody` |
| `TripTrack/Localization/AppStrings.swift:5425` | `companionsPendingSuffix` |
| `TripTrack/Localization/AppStrings.swift:5468` | `companionsAndMore` |
| `TripTrack/Localization/AppStrings.swift:5506` | `photosAdded` |
| `TripTrack/Localization/AppStrings.swift:5550` | `companionPhotoUploadPartial` |
| `TripTrack/Localization/AppStrings.swift:5676` | `achievementsTitle` |
| `TripTrack/Localization/AppStrings.swift:5682` | `achievementsOpenedOf` |
| `TripTrack/Localization/AppStrings.swift:5714` | `achievementsEarnedBy` |
| `TripTrack/Localization/AppStrings.swift:5737` | `achievementsRarityCount` |
| `TripTrack/Localization/AppStrings.swift:5782` | `achievementLockedProgress` |
| `TripTrack/Localization/AppStrings.swift:5805` | `myProfileRowName` |
| `TripTrack/Localization/AppStrings.swift:5808` | `myProfileRowUsername` |
| `TripTrack/Localization/AppStrings.swift:5811` | `myProfileRowAbout` |
| `TripTrack/Localization/AppStrings.swift:5827` | `myProfileAvatarRetired` |
| `TripTrack/Localization/AppStrings.swift:5846` | `myProfileRowStats` |
| `TripTrack/Localization/AppStrings.swift:5857` | `myProfileStatsSummary` |
| `TripTrack/Localization/AppStrings.swift:5948` | `levelsRankOf` |
| `TripTrack/Localization/AppStrings.swift:5955` | `levelsXpProgress` |
| `TripTrack/Localization/AppStrings.swift:5958` | `levelsToNextLevel` |
| `TripTrack/Localization/AppStrings.swift:5976` | `levelsNextRank` |
| `TripTrack/Localization/AppStrings.swift:6025` | `levelsRankRange` |
| `TripTrack/Localization/AppStrings.swift:6034` | `levelsXpTotal` |
| `TripTrack/Localization/AppStrings.swift:6182` | `statsRangeYear` |
| `TripTrack/Localization/AppStrings.swift:6185` | `statsRangeAll` |
| `TripTrack/Localization/AppStrings.swift:6199` | `statsVsPrevious` |
| `TripTrack/Localization/AppStrings.swift:6237` | `statsNewPlacesLine` |
| `TripTrack/Localization/AppStrings.swift:6289` | `statsRecordLongest` |
| `TripTrack/Localization/AppStrings.swift:6294` | `statsRecordBestDay` |
| `TripTrack/Localization/AppStrings.swift:6319` | `capitalizedFirst` |
| `TripTrack/Localization/AppStrings.swift:6342` | `photosCount` |
| `TripTrack/Localization/AppStrings.swift:6345` | `timesCount` |
| `TripTrack/Localization/AppStrings.swift:6349` | `statsWithTripTrackSince` |
| `TripTrack/Localization/AppStrings.swift:6371` | `statsMemoriesLine` |
| `TripTrack/Localization/AppStrings.swift:6842` | `contentFilterTooLong` |
| `TripTrack/Localization/AppStrings.swift:6860` | `relTimeMinutesAgo` |
| `TripTrack/Localization/AppStrings.swift:6879` | `relTimeTodayAt` |
| `TripTrack/Localization/AppStrings.swift:6897` | `relTimeYesterdayAt` |
| `TripTrack/Localization/AppStrings.swift:6917` | `publicProfileTripsTotal` |
| `TripTrack/Localization/AppStrings.swift:6981` | `relTimeDaysAgo` |
| `TripTrack/Localization/AppStrings.swift:7514` | `riddleLine` |
| `TripTrack/Localization/AppStrings.swift:7616` | `milestoneTitle` |
| `TripTrack/Localization/AppStrings.swift:7661` | `findRarityLabel` |
| `TripTrack/Localization/AppStrings.swift:7762` | `proManageTrialRenews` |
| `TripTrack/Localization/AppStrings.swift:7776` | `proManageRenews` |
| `TripTrack/Localization/AppStrings.swift:8845` | `garageTransmission` |
| `TripTrack/Localization/AppStrings.swift:8858` | `garageGearbox` |
| `TripTrack/Localization/AppStringsCore.swift:18` | `tr` |
| `TripTrack/Localization/AppStringsCore.swift:62` | `pluralForm` |
| `TripTrack/Localization/AppStringsCore.swift:103` | `pluralForm` |
| `TripTrack/Localization/AppStringsCore.swift:113` | `plural` |
| `TripTrack/Localization/AppStringsCore.swift:128` | `plural` |
| `TripTrack/Localization/AppStringsCore.swift:564` | `patterns` |
| `TripTrack/Localization/AppStringsCore.swift:570` | `templates` |
| `TripTrack/Localization/AppStringsCore.swift:574` | `table` |
| `TripTrack/Localization/AppStringsCore.swift:600` | `uppercased` |
| `TripTrack/Localization/AppStringsCore.swift:604` | `lowercased` |
| `TripTrack/Localization/AtlasCopy.swift:200` | `atlasCitiesOutOf` |
| `TripTrack/Localization/AtlasCopy.swift:210` | `atlasMoreInFog` |
| `TripTrackShared/LiveActivityStrings.swift:17` | `live_pick` |
| `TripTrackShared/LiveActivityStrings.swift:85` | `live_distanceShort` |
| `TripTrackShared/LiveActivityStrings.swift:99` | `live_speedShort` |
| `TripTrackShared/LiveActivityStrings.swift:116` | `live_slavic` |

188 следующих ключей есть в каждом из одиннадцати словарей, но их английский
источник не удалось получить из области сканирования. Среди них контент значков,
рангов и клубов с динамическими ключами, а также записи старых фрагментов.
Они не добавлены в default без английского текста. В таблице указана координата
немецкого словаря; координаты остальных языков есть в полном списке выше.

| Ключ | iOS-файл:строка |
|---|---|
| `badge.above_clouds.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1153` |
| `badge.above_clouds.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1152` |
| `badge.ambassador.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1131` |
| `badge.ambassador.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1130` |
| `badge.carpool_karaoke.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1193` |
| `badge.carpool_karaoke.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1192` |
| `badge.centurion.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1191` |
| `badge.centurion.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1190` |
| `badge.century.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1091` |
| `badge.century.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1090` |
| `badge.early_bird.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1149` |
| `badge.early_bird.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1148` |
| `badge.early_tester.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1199` |
| `badge.early_tester.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1198` |
| `badge.endurance.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1115` |
| `badge.endurance.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1114` |
| `badge.equator.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1097` |
| `badge.equator.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1096` |
| `badge.expedition.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1197` |
| `badge.expedition.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1196` |
| `badge.explorer_10.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1123` |
| `badge.explorer_10.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1122` |
| `badge.explorer_100.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1129` |
| `badge.explorer_100.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1128` |
| `badge.explorer_25.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1125` |
| `badge.explorer_25.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1124` |
| `badge.explorer_5.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1121` |
| `badge.explorer_5.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1120` |
| `badge.explorer_50.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1127` |
| `badge.explorer_50.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1126` |
| `badge.first_km.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1089` |
| `badge.first_km.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1088` |
| `badge.first_trip.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1074` |
| `badge.first_trip.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1073` |
| `badge.globetrotter.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1133` |
| `badge.globetrotter.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1132` |
| `badge.half_million.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1103` |
| `badge.half_million.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1102` |
| `badge.highway_wolf.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1187` |
| `badge.highway_wolf.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1186` |
| `badge.hours_50.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1117` |
| `badge.hours_50.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1116` |
| `badge.iron_butt.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1111` |
| `badge.iron_butt.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1110` |
| `badge.km_100k.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1099` |
| `badge.km_100k.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1098` |
| `badge.km_250k.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1101` |
| `badge.km_250k.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1100` |
| `badge.legion.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1087` |
| `badge.legion.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1086` |
| `badge.long_shift.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1157` |
| `badge.long_shift.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1156` |
| `badge.marathon_100.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1109` |
| `badge.marathon_100.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1108` |
| `badge.marathon_42.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1107` |
| `badge.marathon_42.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1106` |
| `badge.midnight_rider.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1185` |
| `badge.midnight_rider.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1184` |
| `badge.milestone_2000.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1141` |
| `badge.milestone_2000.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1140` |
| `badge.milestone_border.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1139` |
| `badge.milestone_border.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1138` |
| `badge.million.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1105` |
| `badge.million.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1104` |
| `badge.mountain_goat.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1151` |
| `badge.mountain_goat.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1150` |
| `badge.night_rider.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1145` |
| `badge.night_rider.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1144` |
| `badge.night_wolf.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1147` |
| `badge.night_wolf.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1146` |
| `badge.polar.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1155` |
| `badge.polar.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1154` |
| `badge.riddle_10.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1137` |
| `badge.riddle_10.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1136` |
| `badge.riddle_first.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1135` |
| `badge.riddle_first.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1134` |
| `badge.road_regular.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1076` |
| `badge.road_regular.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1075` |
| `badge.road_warrior.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1078` |
| `badge.road_warrior.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1077` |
| `badge.sea_level.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1181` |
| `badge.sea_level.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1180` |
| `badge.secret_komsomolsky.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1143` |
| `badge.secret_komsomolsky.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1142` |
| `badge.snow_leopard.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1183` |
| `badge.snow_leopard.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1182` |
| `badge.speed_demon.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1113` |
| `badge.speed_demon.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1112` |
| `badge.streak_14.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1167` |
| `badge.streak_14.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1166` |
| `badge.streak_3.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1163` |
| `badge.streak_3.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1162` |
| `badge.streak_30.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1169` |
| `badge.streak_30.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1168` |
| `badge.streak_7.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1165` |
| `badge.streak_7.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1164` |
| `badge.sunday_driver.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1159` |
| `badge.sunday_driver.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1158` |
| `badge.ten_thousand.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1095` |
| `badge.ten_thousand.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1094` |
| `badge.thousand.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1093` |
| `badge.thousand.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1092` |
| `badge.trips_200.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1080` |
| `badge.trips_200.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1079` |
| `badge.trips_500.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1082` |
| `badge.trips_500.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1081` |
| `badge.vault_keeper.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1195` |
| `badge.vault_keeper.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1194` |
| `badge.veteran.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1119` |
| `badge.veteran.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1118` |
| `badge.veteran_2026.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1201` |
| `badge.veteran_2026.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1200` |
| `badge.weekend_warrior.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1161` |
| `badge.weekend_warrior.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1160` |
| `badge.wrapped_year.desc` | `TripTrack/Localization/Translations/Translations+DE.swift:1189` |
| `badge.wrapped_year.title` | `TripTrack/Localization/Translations/Translations+DE.swift:1188` |
| `badgeCategory.distance` | `TripTrack/Localization/Translations/Translations+DE.swift:1022` |
| `badgeCategory.exclusive` | `TripTrack/Localization/Translations/Translations+DE.swift:1026` |
| `badgeCategory.exploration` | `TripTrack/Localization/Translations/Translations+DE.swift:1023` |
| `badgeCategory.special` | `TripTrack/Localization/Translations/Translations+DE.swift:1024` |
| `badgeCategory.streaks` | `TripTrack/Localization/Translations/Translations+DE.swift:1025` |
| `badgeRarity.common` | `TripTrack/Localization/Translations/Translations+DE.swift:1027` |
| `badgeRarity.epic` | `TripTrack/Localization/Translations/Translations+DE.swift:1030` |
| `badgeRarity.exclusive` | `TripTrack/Localization/Translations/Translations+DE.swift:1032` |
| `badgeRarity.legendary` | `TripTrack/Localization/Translations/Translations+DE.swift:1031` |
| `badgeRarity.rare` | `TripTrack/Localization/Translations/Translations+DE.swift:1029` |
| `badgeRarity.uncommon` | `TripTrack/Localization/Translations/Translations+DE.swift:1028` |
| `club.miata.blurb` | `TripTrack/Localization/Translations/Translations+DE.swift:1171` |
| `club.miata.name` | `TripTrack/Localization/Translations/Translations+DE.swift:1170` |
| `club.offroad.blurb` | `TripTrack/Localization/Translations/Translations+DE.swift:1177` |
| `club.offroad.name` | `TripTrack/Localization/Translations/Translations+DE.swift:1176` |
| `club.subaru.blurb` | `TripTrack/Localization/Translations/Translations+DE.swift:1179` |
| `club.subaru.name` | `TripTrack/Localization/Translations/Translations+DE.swift:1178` |
| `club.trucking.blurb` | `TripTrack/Localization/Translations/Translations+DE.swift:1175` |
| `club.trucking.name` | `TripTrack/Localization/Translations/Translations+DE.swift:1174` |
| `club.vag.blurb` | `TripTrack/Localization/Translations/Translations+DE.swift:1173` |
| `club.vag.name` | `TripTrack/Localization/Translations/Translations+DE.swift:1172` |
| `daysUnitShort` | `TripTrack/Localization/Translations/Translations+DE.swift:309` |
| `discussionSeeAllPillHead` | `TripTrack/Localization/Translations/Translations+DE.swift:300` |
| `driverRank.cartographer` | `TripTrack/Localization/Translations/Translations+DE.swift:1043` |
| `driverRank.conqueror` | `TripTrack/Localization/Translations/Translations+DE.swift:1042` |
| `driverRank.driver` | `TripTrack/Localization/Translations/Translations+DE.swift:1034` |
| `driverRank.eternal` | `TripTrack/Localization/Translations/Translations+DE.swift:1046` |
| `driverRank.explorer` | `TripTrack/Localization/Translations/Translations+DE.swift:1036` |
| `driverRank.legend` | `TripTrack/Localization/Translations/Translations+DE.swift:1039` |
| `driverRank.navigator` | `TripTrack/Localization/Translations/Translations+DE.swift:1037` |
| `driverRank.nomad` | `TripTrack/Localization/Translations/Translations+DE.swift:1041` |
| `driverRank.novice` | `TripTrack/Localization/Translations/Translations+DE.swift:1033` |
| `driverRank.odysseus` | `TripTrack/Localization/Translations/Translations+DE.swift:1044` |
| `driverRank.pioneer` | `TripTrack/Localization/Translations/Translations+DE.swift:1040` |
| `driverRank.roadSpirit` | `TripTrack/Localization/Translations/Translations+DE.swift:1045` |
| `driverRank.traveler` | `TripTrack/Localization/Translations/Translations+DE.swift:1035` |
| `driverRank.trucker` | `TripTrack/Localization/Translations/Translations+DE.swift:1038` |
| `mapTripsSectionHead` | `TripTrack/Localization/Translations/Translations+DE.swift:260` |
| `plusPaywallPlaceholder` | `TripTrack/Localization/Translations/Translations+DE.swift:1398` |
| `roadLevel.bronze` | `TripTrack/Localization/Translations/Translations+DE.swift:1063` |
| `roadLevel.discovered` | `TripTrack/Localization/Translations/Translations+DE.swift:1062` |
| `roadLevel.gold` | `TripTrack/Localization/Translations/Translations+DE.swift:1065` |
| `roadLevel.mastered` | `TripTrack/Localization/Translations/Translations+DE.swift:1066` |
| `roadLevel.silver` | `TripTrack/Localization/Translations/Translations+DE.swift:1064` |
| `roadRarity.common` | `TripTrack/Localization/Translations/Translations+DE.swift:1057` |
| `roadRarity.epic` | `TripTrack/Localization/Translations/Translations+DE.swift:1060` |
| `roadRarity.legendary` | `TripTrack/Localization/Translations/Translations+DE.swift:1061` |
| `roadRarity.rare` | `TripTrack/Localization/Translations/Translations+DE.swift:1059` |
| `roadRarity.uncommon` | `TripTrack/Localization/Translations/Translations+DE.swift:1058` |
| `signInLegalTermsWord` | `TripTrack/Localization/Translations/Translations+DE.swift:450` |
| `syncFailedCountHead` | `TripTrack/Localization/Translations/Translations+DE.swift:688` |
| `syncLastAtHead` | `TripTrack/Localization/Translations/Translations+DE.swift:687` |
| `unitGallonsFull` | `TripTrack/Localization/Translations/Translations+DE.swift:1021` |
| `unitKilometersFull` | `TripTrack/Localization/Translations/Translations+DE.swift:1018` |
| `unitLitersFull` | `TripTrack/Localization/Translations/Translations+DE.swift:1020` |
| `unitMilesFull` | `TripTrack/Localization/Translations/Translations+DE.swift:1019` |
| `vehicleSticker.bronzeFrame` | `TripTrack/Localization/Translations/Translations+DE.swift:1049` |
| `vehicleSticker.flag100km` | `TripTrack/Localization/Translations/Translations+DE.swift:1047` |
| `vehicleSticker.goldenFrame` | `TripTrack/Localization/Translations/Translations+DE.swift:1051` |
| `vehicleSticker.mountain` | `TripTrack/Localization/Translations/Translations+DE.swift:1054` |
| `vehicleSticker.night` | `TripTrack/Localization/Translations/Translations+DE.swift:1055` |
| `vehicleSticker.platinumFrame` | `TripTrack/Localization/Translations/Translations+DE.swift:1053` |
| `vehicleSticker.regionMap` | `TripTrack/Localization/Translations/Translations+DE.swift:1052` |
| `vehicleSticker.route500km` | `TripTrack/Localization/Translations/Translations+DE.swift:1048` |
| `vehicleSticker.silverFrame` | `TripTrack/Localization/Translations/Translations+DE.swift:1050` |
| `vehicleSticker.winter` | `TripTrack/Localization/Translations/Translations+DE.swift:1056` |
| `zoneStatus.conquered` | `TripTrack/Localization/Translations/Translations+DE.swift:1071` |
| `zoneStatus.discovered` | `TripTrack/Localization/Translations/Translations+DE.swift:1068` |
| `zoneStatus.explored` | `TripTrack/Localization/Translations/Translations+DE.swift:1069` |
| `zoneStatus.mapped` | `TripTrack/Localization/Translations/Translations+DE.swift:1070` |
| `zoneStatus.mastered` | `TripTrack/Localization/Translations/Translations+DE.swift:1072` |
| `zoneStatus.undiscovered` | `TripTrack/Localization/Translations/Translations+DE.swift:1067` |
