Гео-логика и места перенесены в `core:logic`. Источник: iOS `a155859d`;
прочитаны пять указанных разделов `CLAUDE.md`, исходники и тесты.
Новых библиотек и изменений Gradle нет.

| Исходник iOS | Что перенесено |
|---|---|
| `Services/GeohashEncoder.swift` | Кодирование, границы, центр, восемь соседей в исходном порядке; поведение у полюсов и линии перемены дат |
| `Models/Trip.swift` | `encodePolyline`, `decodePolyline`: пары little-endian Float32, восемь байтов на точку; превью, кэш на 200 поездок и инвалидирование |
| `Models/Place.swift`, `Models/Discovery.swift` | `Place.cell` geohash-7; существующие UUID v5 и namespace из `core:model` переиспользованы, замороженные векторы проверены |
| `Models/TripRouteLocator.swift` | `Fix`, поиск по времени, все проезды, радиусы, `distinctPassGap = 600`, индекс маршрута и `distancePrefix` |
| `Models/PlaceMatcher.swift`, `Models/PlacePass.swift` | Радиус 100 м, предфильтр geohash-5 с соседями, курс по GPS или соседним точкам, общий `passGap` |
| `Models/PlaceStats.swift` | Медианы, направления ±45° от самого свежего проезда, первые/последние даты, ≥5 проездов за 90 дней, `hasUsual` |
| `Models/PlaceListItem.swift` | Медиана главного направления, подписи состояния и стабильный порядок по последнему проезду |
| `Models/PlaceSuggestions.swift`, `Persistence/TripRepository.swift` | `TripPreviewRef`, голос поездки за ячейку один раз, центроид, пороги 1/2 при границе 5 поездок, исключение занятых id/надгробий, одна подсказка на geohash-5, лимит 5 |
| `Models/PlacesPresentation.swift` | Поиск, стабильная сортировка, группы, порог 8 мест; ключ `places.sort`, raw values и fallback `recent` |
| `Models/PlaceScreen.swift` | Пять плиток дней, пять видимых проездов, подпись маршрута, концы превью |
| `Models/TripMoment.swift` | `TripMoments.build`, нумерация отметок по времени, стопки свободных фото от первого кадра, общий интервал 900 с |
| `Models/TripCheckpointPhotos.swift` | Ручные привязки раньше автоматики; ближайшая отметка по времени ±900 с, затем по координате в 300 м; обложка → прикреплённые → время |
| `Models/TripPhotoPlacement.swift` | `place`, чистая часть `prepare`, EXIF прежде времени съёмки; общий индекс маршрута для чтений |
| `Models/TripSegment.swift` | `TripSegmentMetrics.resolve`, порядок по времени, разности отметок, `TripSegmentName` с общей нумерацией |
| `Models/SegmentHistory.swift` | Минимальная положительная разница на поездку, сортировка времён, часы:минуты с отбрасыванием неполной минуты |
| `Services/CheckpointDefaultName.swift` | Пустое имя и регулярка по словам отметки всех 13 языков, экранирование слова |

Используются готовые `TripDistanceGate`, `TrackPoint.countsForDistance`,
`RecordingBreaks`, геодезическое расстояние и доменные типы. Для расширений
`Trip.encodePolyline`, `PlaceStats.build` и `PlaceListItem.build` в трёх моделях
добавлены пустые companion-объекты. UUID, сериализаторы и wire values не менялись.

Перенесены чистые сценарии с исходными входами и ожидаемыми значениями из:

- `PolylineTests.swift`, `TripPolylineCacheTests.swift`, `PlaceIdentityTests.swift`, `DiscoveryIdentityTests.swift`;
- `TripRouteLocatorTests.swift`, `PlaceMatcherTests.swift`, `PlaceStatsTests.swift`, `PlaceListItemTests.swift`, `PlaceSuggestionsTests.swift`;
- `PlacesPresentationTests.swift` (включая `PlaceScreenTests`), `TripMomentsTests.swift`, `TripCheckpointPhotosTests.swift`, `TripPhotoPlacementTests.swift`, `TripPhotoPreparationTests.swift`;
- `TripSegmentMetricsTests.swift`, `SegmentHistoryTests.swift`, `CheckpointDefaultNameTests.swift`;
- гео-сценарии `DistanceDoorTests.swift`, проверка наличия координаты из `HomePrivacyTrimTests.swift`, чистые проверки расстановки из `PhotoMetadataTests.swift`, поиск из `PlacesScreenStateTests.swift`.

UUID- и Codable-сценарии остаются в существующих `core:model` тестах
`PlaceIdentityTests` и `DiscoveryContractTests`; добавлена недостающая проверка
самостоятельного вывода Discovery.id. Всего добавлено 140 тестов `core:logic`
и один тест `core:model`. Дополнительные проверки фиксируют точные границы
времени, Unicode-пробелы Foundation, стабильные ничьи и снимок индекса маршрута.

Проверка:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :core:logic:test :core:model:test --console=plain
```

Результат: `BUILD SUCCESSFUL`; `core:logic` — 286 тестов, `core:model` — 84,
без ошибок и пропусков. Сохранены прежние 146 тестов recording math.
Первая проверка `:core:logic:compileKotlin :core:model:test --console=plain`
с тем же `JAVA_HOME` выявила неверное объявление companion; оно исправлено,
последующие проверки проходят.

```sh
swift -module-cache-path /private/tmp/w1-logic-geo-swift-cache core/logic/src/test/swift/PolylineVectors.swift
```

Результат: четыре hex-вектора из неизменённого тела iOS-кодировщика.
`PolylineTests.bytesMatchIOSVectors` проверяет точные байты и декодирование,
включая отрицательные координаты, границы ±90/±180 и signed zero.
`git diff --check` проходит. Проверка публичности через `git grep` и `rg`
по новым файлам не нашла IP, ключей и SSH-реквизитов.

Отличия API и платформы:

- `TripRouteLocator.Index` называется `TripRouteIndex`: общий объект модели нельзя расширить вложенным классом. `Fix` сохраняет принятую в `core:model` структурную equality; в iOS она сравнивает только индекс.
- Локализованное имя отметки передаётся форматтером, слова всех языков — коллекцией из ресурсов; locale и часовой пояс передаются явно. Адаптер настроек хранит raw value по прежнему ключу. Видимые слова в production-коде не захардкожены.
- Кэш JVM имеет строгий LRU-лимит 200; у NSCache вытеснение зависит также от памяти. Поиск и колляция используют Unicode/JDK вместо Foundation; регистр, диакритика, расширение ß/SS, Foundation-пробелы и стабильные ничьи проверены.
- Расстояние использует уже принятый WGS84-порт вместо CoreLocation. Все исходные допуски сохранены.

Эти платформенные отличия отражены в `docs/divergences.md`.
Пороги, единицы и wire-значения совпадают с iOS.

За пределами этой задачи остаются хранилище, UI и управление фоновыми задачами:
`buildDetached`, `prepareAsync`, отмена и запрет публикации отменённого результата,
доступ к Android-ресурсам и настройкам, вызовы инвалидирования кэша после правок.
Поэтому `testCancelledPreparationNeverReturnsPublishableOutput` относится к будущему
асинхронному адаптеру, а `testPlaceScreenAsksTheSharedRule` — к будущему экрану места;
проверки математики из этих файлов перенесены. CoreData/ImageIO/SwiftUI-интеграции
не имитировались JVM-заглушками. Как и в recording-порте, загрузка поездки должна
применять `withRecordingBreaks` до работы с индексами сегментов записи.

`docs/port-status.md` не менялся. Работа осталась в `w1-logic-geo`, без commit и push.
