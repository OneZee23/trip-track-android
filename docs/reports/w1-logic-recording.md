# w1-logic-recording

Математика записи перенесена в `core:logic`, без Android API и новых библиотек.
`core:model`, Gradle-конфигурация и `docs/port-status.md` не менялись. Коммитов и push нет.

## Что перенесено

| iOS | Kotlin |
|---|---|
| `TripDistanceGate.swift` | Все константы, `totalDistance`, `countsForDistance`, проверка сегмента и скорости, `maximumRecordedSpeed`; используется существующий `Sample` из core:model |
| `FixGate.swift` | Шлюз фикса, одинаковый потолок записи и простоя 200 м; одометр сохраняет 65 м |
| `KalmanLocationFilter.swift` | ENU, матрица ковариации, шум манёвра, коррекция позиции/скорости, чистая проекция `predictedLocation`, сброс после >10 с, подавление повторов |
| `RecordingFixContinuity.swift` | Проверка сырого фикса до Калмана; отклонение не двигает якорь |
| `RecordingBreaks.swift`, `Trip.swift` | Нормализация, верхняя граница двоичным поиском, аннотация, `crosses`, `Trip.withRecordingBreaks` |
| `TripManager.swift`, `handleNewLocation` / `shouldStoreShapePoint` | Чистый `RecordingDecision` с двумя якорями; `RecordingMath` соединяет его с Калманом и шлюзом непрерывности в памяти |
| `AutoTripPolicy.swift` | Все константы, отключение Bluetooth, `stopMode` |
| `AutoTripService.swift`, `RecordingMovementTracker.swift` | Чистые проверки бездействия/устаревания, минуты → секунды, запрет автоматического завершения на паузе, трекер движения |
| `TripConfirmation.swift`, `Trip.swift` | Гейт допуска в мир, `isDraft`, доверенные точки, `movementSplit`, средняя в движении, классификация мусорных поездок |
| `RecordingFinishPreview.swift` | Причина удаления и проверка, осталось ли прежним подтверждаемое действие |
| `PostTripTrackProcessor.swift` | Удаление выбросов, прямая достройка, пересчёт расстояния/максимума/средней, обработка снимка Trip |
| `TrackGapFinder.swift`, `GapFill.swift` | Поиск дыр с учётом пауз, шаг 20 м, потолок 500 точек, ресэмплинг времени/высоты/курса, проверка правдоподобия и прямолинейности |
| `Support/TrackTestKit.swift`, `Support/TrackReplay.swift`, `Support/SyntheticDrives.swift` | Синтетические фиксы, городской квадрат, тоннель, усыпление; прогон записи и прямой достройки без хранилища |

Прежний шаг формы 5 м проверяется первым. Дополнительные точки требуют сырой скорости
≥0,5 м/с, смещения ≥1 м и интервала ≥1/3 с; затем достаточно секунды или поворота ≥12°.
Ручная пауза сбрасывает оба якоря и фильтр. Переход через неё не даёт метров, времени
движения, рекорда по разрыву или достройки.

## Тесты

Сохранены входы и ожидаемые значения чистых сценариев из:

- `TripDistanceGateTests.swift`, `FixGateTests.swift`;
- `KalmanLocationFilterTests.swift`, кроме двух проверок адаптера `LocationUpdate`;
- `RecordingInterferenceTests.swift`, `TrackFidelityTests.swift`;
- `TrackReplayTests.swift`, `AutoStopOnPauseTests.swift` (политика), `AutoTripDraftPolicyTests.swift`;
- `RecordingBreaksPersistenceTests.swift` (граница и аннотация), `RecordingMovementTrackerTests.swift`;
- `PostTripFillTests.swift`, `PostTripTrackProcessorTests.swift` (геометрия и статистика);
- `DistanceDoorTests.swift` (шлюз, пересчёт, доверенные точки и сплит);
- `TrackGapFinderTests.swift`, `GapFillTests.swift`;
- `TripConfirmationSchemaTests.swift` (состояния записи), `RecordingFinishPreviewTests.swift` (классификация и подтверждение).

Дополнительно проверены границы таймаутов, 1/3 с, 12°, сырой скорости, независимость
якорей, сброс при уменьшении одометра, неверные координаты/измерения и повторная аннотация.
Случайный шум в тесте сглаживания получил seed 42; диапазон ±0,0001° и условие уменьшения
дисперсии сохранены. Генератор шума TrackFidelity перенесён с тем же переполнением UInt64.

| Команда | Результат |
|---|---|
| `./gradlew :core:logic:compileKotlin` | BUILD SUCCESSFUL |
| `./gradlew :core:logic:test --info` | BUILD SUCCESSFUL; тестовый процесс использует JDK 17.0.18 |
| `./gradlew :core:logic:test` | Последний запуск: 146 тестов, 0 ошибок, 0 падений, 0 пропусков; BUILD SUCCESSFUL |
| `git diff --check` | Без замечаний |
| `git grep -nE 'ssh [a-z]+@\|[0-9]{1,3}(\.[0-9]{1,3}){3}\|BEGIN [A-Z ]*PRIVATE KEY'` | Совпадений нет |

Промежуточный запуск выявил неверное ожидание дополнительного теста: Instant округляет
1/3 с вниз до 333333333 нс. Тест теперь проверяет обе стороны порога: 333333333 и
333333334 нс. Производственный порог не менялся.

## Отличия и оставшаяся интеграция

Отличия записаны в `docs/divergences.md`: LocationFix/Instant вместо CoreLocation/Date,
WGS84 вместо платформенного расчёта Apple и явный `Trip.withRecordingBreaks` вместо
автоматической аннотации в модели. Все исходные числовые допуски перенесённых тестов проходят.

Здесь нет записи в БД, восстановления после смерти процесса, очереди синка, таймеров,
уведомлений, геокодера и генерации previewPolyline. Тесты Core Data/синка и две проверки
LocationUpdate остаются соответствующим адаптерам. В RecordingInterference сохранены
проверки математического результата перед сохранением; перезапуск хранилища не имитируется.
При загрузке Trip и замене пауз адаптер должен вызвать `withRecordingBreaks`.

TrackReplay выполняет запись и прямую достройку. В iOS тест `testEachGapIsClosedByExactlyOneFill`
дополнительно вызывает RoadGapFiller с заглушкой прямой дороги; здесь проверены те же число
открытых дыр и число участков достройки без сетевой стадии. Асинхронная замена прямой дорогой,
проверка свежих границ после запроса и флаг isTrackProcessed остаются слою хранения/маршрутизации.
`PostTripTrackProcessor.process` рассчитан на вызов под этим флагом; отдельно `fillOpenGaps`
не создаёт повторную достройку уже закрытой дыры.
