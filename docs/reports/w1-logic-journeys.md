# w1-logic-journeys

Перенесены путешествия, сворачивание истории и данные Атласа в чистый JVM-модуль
`core:logic`. Добавлены 164 теста. В `core:model` добавлены только пустые companion
objects для функций построения; поля моделей и сериализация не менялись.

| Область | Исходники iOS | Покрытие из iOS |
|---|---|---|
| Окна, членство и правка дат | `Journey.swift`, `Social/PublicJourneyAdapter.swift` (`JourneyWindow`), чистые функции `JourneyEditSheet.swift` | Все сценарии `JourneyEditWindowTests`, календарный сценарий `PublicJourneyAdapterTests` |
| Итоги и цепочки путешествий | `JourneyAggregate.swift`, `JourneySuggester.swift` | Все `JourneyAggregateTests`, `JourneySuggesterTests` |
| История и повторы | `HistoryFolding.swift`, `ProfileRoutineTrips.swift` | Все `ProfileHistoryFoldingTests`, все 12 сценариев `ProfileMonthlyJournalTests`, длинный прогон из `PreparedProfileHistoryTests` |
| Подготовка глав | `PreparedProfileHistory.swift`, `ProfileHistoryMonth.swift`, `TripHistoryFilter.swift` | Месяцы, исходные итоги, закрепления, поиск и публичная сетка из `ProfileMonthlyJournalTests` |
| Сетка открытого | `Reveal/RevealGrid.swift` | Все `RevealGridTests` |
| Слой открытого | `Reveal/RevealedLayer.swift`, `RoadFog.swift` (`decimate`) | Все `RevealedLayerStitchTests`, два сценария открытых километров из `MyMapAtlasTests` |
| Регионы и статистика карты | `RegionAtlas.swift`, `MapExploration.swift`, `GeometryUtils.swift` (`haversineDistance`), `TerritoryManager.swift` (`GeoBounds.contains/area/intersects`) | Все `MapRegionsBundleTests`, `WorldRegionsTests`, 16 сценариев атласа и статистики из `MyMapAtlasTests` |
| Места и дом | `AtlasPlaces.swift`, `AtlasHome.swift`, периоды из `AtlasPresentation.swift` | Все `AtlasPlacesTests`, `AtlasHomeTests`, четыре сценария периодов из `AtlasPresentationTests` |
| Геометрия тумана | `FogVeilOverlay.swift` (`FogVeilRenderer.haloHalfWidth/corridorWidth/lod`), `RegionLabelLOD.swift`, `FogMetal/FogCellGrid.swift` | Все `HaloWidthTests`, `RegionLabelLODTests`, два сценария геометрии из `FogVeilRendererTests`, шесть CPU-сценариев сетки из `FogMetalCellsTests` |

`RevealedTile` сохраняет little-endian заголовки UInt32 и пары Float32 из общего
кодека `Trip`. `RevealedLayer.build` считает общий итог и километры регионов одним
проходом с одним якорем и `TripDistanceGate.minStep`. Центры подписей притягиваются
к открытым точкам. Склейка затрагивает только средний и дальний уровни.

Повторно используются `GeoPoint.distanceFrom`, `TripDistanceGate`, `GeohashEncoder`,
`Trip.encodePolyline/decodePolyline`, кеш превью и существующие модели. Сферический
расчёт Атласа сохранён отдельно от одометра: iOS использует здесь радиус 6 371 000 м,
а в путешествиях и открытом слое использует CoreLocation.

`MapRegions.json` скопирован без изменений в `core/logic/src/main/resources`.
SHA-256 обеих копий: `afce56b12574db9f3b12d10aef31d22379eec751dfda9f44cad4d8d29dcd8799`.
Поиск использует сетку 2°, наименьшую рамку региона и проверку анклавов при попытке
переиспользовать регион предыдущей точки. Есть дополнительные проверки переходов
через границы, шага 5 м, переноса якоря после скачка, знакового нуля и битой геометрии.

В `core:logic` подключлена уже существующая `libs.serialization.json` для чтения
ресурса. Каталог версий и настройки памяти не менялись.

Проверки:

- `./gradlew :core:logic:test`: успешно.
- `./gradlew :core:model:test :core:logic:test`: успешно; 84 теста моделей и
  450 тестов логики, без ошибок и пропусков. Toolchain JDK 17; байткод версии 61.
- `git diff --check`: без замечаний.
- `shasum -a 256 core/logic/src/main/resources/MapRegions.json ~/OneZeeProjects/trip-track/TripTrack/Resources/MapRegions.json`:
  суммы совпали.
- `git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'`:
  совпадений нет. Новые файлы отдельно проверены тем же выражением.

Различия записаны в `docs/divergences.md`:

- Атлас загружается синхронной JVM-функцией в неизменяемый снимок. Вызывающий адаптер
  выбирает рабочий поток; `shared` имеет ленивую загрузку и пустой резервный снимок.
- Геометрия передаётся списками координат и Double-прямоугольниками. Адаптер карты
  создаёт native paths и переводит проекцию в мировые единицы MapKit 2^28 для сетки.
- Locale, ZoneId, перевод региона и готовые исторические уровни передаются явно.
  Названия месяцев форматирует JDK; таблицы локализации могут отличаться от Foundation.
- Для расстояния CoreLocation действует уже описанная в `docs/divergences.md`
  замена на WGS84. Пороги, единицы, округления и формат хранения сохранены.

Осталось за границами этой задачи: хранение и миграция открытого слоя, синхронизация,
`RevealBuilder`, рендерер и GPU/пиксельные проверки. Три сценария камеры/масштаба из
`MyMapAtlasTests` относятся к будущему адаптеру карты. Вычисление наград
`TripLevelHistory` остаётся в своём слое; подготовка журнала передаёт готовые уровни.
Проверка локализованной строки `AppStrings.profileChapterSummary` из месячного теста
остаётся в дизайн-системе: JVM-тест проверяет её исходные километры и счётчики,
а также прежние ожидаемые названия месяцев.

`docs/port-status.md` не менялся. Коммит и push не выполнялись.
