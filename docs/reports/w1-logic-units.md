# w1-logic-units

Перенесены чистые расчёты в `core:logic`. Типы из `core:model` используются через
расширения; записывающая и геометрическая математика не копировалась.

| Область | Источники iOS | Что перенесено |
|---|---|---|
| Единицы | `TripTrackShared/DistanceUnit.swift`, `DistanceUnit+Current.swift`, `DashboardUnits.swift` | Метры, мили, скорость, футы, пороги десятых, догадка по региону, разрешение приборки, имена и подсказки через ресурсный интерфейс |
| Форматирование | `Measure.swift`, `TripTrackShared/UnitNumber.swift`, подписи из `AppStrings.swift` | Все стили, отдельные число/подпись, одометр, высота, радиус, склонение по округлённому числу, точка во всех языках, языковая группировка |
| Энергия | `ConsumptionUnit.swift`, `ElectricUnit.swift`, `EnergyEstimate.swift`, `OdometerField.swift`, `Vehicle.swift` | Американский галлон, обратный ввод, цены, смесь город/трасса, топливо/электро/PHEV, дневной запас по старту поездок, разбор одометра |
| Награды | `Trip.swift`, `GamificationManager.swift`, `GamificationModels.swift`, `VehicleOdometer.swift`, `TripLevelHistory.swift` | scoringKm/rewardKm, XP, досдача XP, уровни водителя и машины, ранги, серия дней, история уровней, отдельные суммы пробега и наградных километров |
| Значки | `BadgeDefinitions.swift`, `BadgeManager.swift`, `BadgeFigure.swift` | Все 58 правил, постоянные ID, редкость/скрытость/повторяемость, метрические пороги, ночные окна, выходные, год, внешнее открытие, пересчёт и счётчики повторений; числа описаний проходят через Measure |
| Политики | `TipMoment.swift`, `ProContextOffer.swift`, `PlusGate.swift`, `PlusAccess.swift`, `PlusStore.swift`, `ProStatus.swift`, `ProManageModel.swift` | Пороги и паузы предложений, независимое извещение об истечении, доступ, память витрины, глобальная доступность, состояния подписки, управление и заполнение ресурсных шаблонов |
| Дорога | `RoadSceneTime.swift` | Местные часы, четыре слота, границы до секунды, часовые пояса и DST |

`MeasureWords` и `ProWords` реализует приложение из сгенерированных ресурсов.
Тесты получают слова iOS. `UnitNumber.tsv` содержит 518 результатов исходного Swift
для 13 языков и неизвестного кода. В том числе сохранено отсутствие группировки
четырёхзначных чисел в испанском, итальянском и польском. Точки в больших
группированных числах записаны как `\u002e` и декодируются тестом, чтобы
фикстура не совпадала с шаблоном IPv4 при проверке публичного репозитория.

В `core:model` добавлены только companion-объекты для расширений и исправлен
`DashboardUnits.parse`: iOS обрезает пробелы и приводит строку к нижнему регистру.
Старый Android-тест, ожидавший отказ для `METRIC`, исправлен по этому контракту.
Сырые значения enum и JSON не менялись. Новых библиотек нет; настройки сборки не менялись.

## Тесты

Перенесены входы и ожидаемые значения из:

- `DistanceUnitTests`, `MeasureFormatTests`, `UnitLabelTests`, `ConsumptionUnitTests`,
  `ElectricUnitTests`, `EnergyEstimateTests`, `OdometerFieldTests`; измерительные
  случаи `GarageWordsTests`, логика вариантов из `VehicleDashboardPickerTests`.
- `ScoringStaysMetricTests`, `VehicleLevelSystemTests`, `TripLevelHistoryTests`,
  отображение ранга из `DriverRankTests`; чистые части `VehicleOdometerTests`,
  `TransferAndOdometerTests`, `ManualTripRewardsTests`.
- `BadgeDefinitionsTests`, `BadgeNightWindowTests`, пороги и числа `BadgeUnitTests`,
  фильтр доверенных точек из `DistanceDoorTests`, сохранность внешних значков из
  `DiscoveryProcessorTests`.
- `TipMomentTests`, `ProContextOfferTests`, `PlusGateTests`, `PlusAvailabilityTests`,
  чистые таблицы `PlusStoreTests`, модели и шаблоны `ProStatusTests` и `ProManageModelTests`.
- Часы, секундные границы, зоны и DST из `RoadSceneTimeTests`.

Намерение `UnitsDisciplineTests` проверяется общим форматтером и явным обязательным
параметром единицы. Намерение `VehicleUnitsStayOutOfRewardsTests` дополнено проверкой
сигнатур: функции наград не принимают машину, единицы показа или энергетическую модель.
Показ меняется между км и милями, а XP за исходную поездку остаётся 570,
пробег после неё 700 км и уровень машины 4.

Итоговая команда (JDK 17):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --project-cache-dir /tmp/triptrack-w1-logic-units-gradle :core:logic:test :core:model:test
```

Результат: `BUILD SUCCESSFUL`; `core:logic` — 408 тестов, `core:model` — 84,
ошибок и пропусков нет. Часть неизменённых задач Gradle использовала кэш.
Предупреждение FSEvents не помешало компиляции и тестам.

Первый `./gradlew :core:logic:test :core:model:test` остановился до компиляции:
среда не дала создать локальный `.gradle`. Поэтому кэш проекта вынесен в `/tmp`.
Первый прогон с этим кэшем нашёл старое ожидание `DashboardUnits.parse`; после
исправления повторные прогоны прошли.

Дополнительно: `git diff --check` без замечаний. Проверка публичного содержимого:

```sh
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

Совпадений нет; новые исходники и фикстуры также проверены через `rg`.

## Адаптация и оставшееся

- Входы `now`, `ZoneId`, `WeekFields`, форматтер даты и состояние значков явные.
  `BadgeManager` возвращает следующий ledger; сохранять его должен адаптер хранилища.
  Это замена зависимостей от Date/Calendar/UserDefaults, без смены порогов.
- Kotlin не перегружает функцию по имени аргумента: входы Measure в километрах
  названы `distanceKm`, `distanceValueKm`, `distancePartsKm`; метрические входы
  сохраняют имена `distance`, `distanceValue`, `distanceParts`.
- `BadgeRule` содержит правила и числовые фигуры. Названия, полные описания,
  рисунки, цвет, интерфейс празднования и их снимки относятся к слою приложения.
- Приложению осталось подключить ресурсные интерфейсы, локальный календарь,
  форматтер дат, сохранение ledger и применение рассчитанного прогресса.
  Репозитории, сеть, StoreKit и UI здесь не реализуются. Тесты реальных покупок,
  Core Data и загрузки векторных сцен к JVM-порту не относятся.
- Для Android на старте вызывающий передаёт `available = false` в
  `PlusStore.hidesPlus`; это скрывает PRO в любой стране. Сам гейт сохраняет
  правило iOS: действующее право открывает фичу даже при скрытой витрине.

Особенности исходника сохранены: backfill XP не начисляет бонус первой поездки дня;
число поездок и проверка выходных у BadgeManager берут весь переданный список,
хотя дистанция, скорость и остальные агрегаты исключают ручные поездки;
`countriesCount` остаётся нулём. 

Различия устройства зависимостей записаны в `docs/divergences.md`.
Изменений в `docs/port-status.md`, коммитов и push нет.
