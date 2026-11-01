# w5-places

Перенесён `Services/PlaceManager.swift` из iOS 0.9.2. Все шесть входов подключены:

- Новая отметка на экране поездки и при записи создаёт место, проставляет локальный `placeId` и пересчитывает центр. При записи история нового места ждёт финиша. Новым считается и место без проездов, заранее созданное геокодером.
- Финиш и подтверждение черновика идут через `TripWorldEntry.placesAndReveal`. Сопоставление читает окончательный трек после `PostTripTrackProcessor`.
- `AppGraph` запускает `reconcile` при старте и по завершению pull. Список мест и превью читаются один раз на основной проход; отметки сверки записываются пачками по 200. Без мест вся библиотека помечается одной пачкой, без чтения точек. Пересекающийся вызов сразу выходит.
- `forget` подключён к пользовательскому удалению. Уже существовавшая очистка проездов внутри `deleteTrip`, `deleteTripHardIfMirrored` и `deleteTripHard` сохранена и проверена тестами.
- Подсказки Places и debug seed используют тот же менеджер вместо отдельного алгоритма истории.
- `places.pendingHistory` сохраняется до возврата из регистрации отметки и переживает создание нового менеджера. Очередь дренируется на финише и сверке; удалённые места пропускаются.

Надгробие остаётся в отметке после удаления места. Сверка и геокодер не воскрешают его; новая отметка той же ячейки создаёт место с прежним UUID. Черновики и отметки без координат мест не создают.

Имена перенесены из `TripManager.swift`: `nameCheckpointFromPlace` и `applyPlaceName`. Тёплый кэш отвечает до регистрации, холодный геокодер работает отдельной задачей. Ручное имя отметки сохраняется; `rename` места перезаписывает имя, `adoptName` заполняет только пустое. Android Geocoder передаёт locality, subLocality и featureName для соответствующих полей CLPlacemark.

На экране поездки чип под строкой отметки ведёт в Places; надгробия чипов не получают. `TripFocus.Checkpoint` из экрана места доходит до моментов, прокручивает нужную отметку и подсвечивает её на 1,400 мс. Фокус потребляется один раз за жизнь экрана. Общий тип лежит в `core:model`, вместо локального `PlaceTripFocus`.

`SegmentHistory.times` и показ истории от двух проездов уже были перенесены. Их реализация сохранена; добавлена проверка истории по двум живым местам и её исчезновения после удаления одного места.

Чтение Room и запись выполняются на IO, геометрия PlaceMatcher на Default. Уведомления экранов идут через существующие Room invalidations. После очистки локальных данных кэш менеджера перечитывается. Зависимости и файлы сборки не менялись. Социальные экраны и настройки записи не затронуты.

## Проверки

Портированы все случаи `PlaceManagerTests.swift` с исходными координатами `44.30 / 38.70`, шагом `0.00045`, временем `1760000000`, 120 точками по 50 м / 10 с и теми же ожиданиями. Дополнительно проверены восстановление очереди, повторное создание места, подсказка без отметки, отсутствие координат и незавершённая поездка.

`PlaceReconcileMainThreadTests.swift` проверяет намерение исходного теста на JVM: 400 превью по 2,000 точек, 40 близких поездок, 360 дальних, 60 мест. Геометрия настоящая, чтение треков подставлено через TripRepository с синхронной задержкой на IO. Вызывающий однопоточный dispatcher отвечает в пределах исходного бюджета 200 мс. Отдельно проверены одна выборка списков, пачки `200 / 200 / 1`, немедленный выход второго вызова и освобождение гейта при отмене.

`RecordingPlacesTests`, `CheckpointNamerTests` проверяют запись, восстановление процесса, финиш, черновик, тёплый кэш и запоздалое имя. `TripPlacesTests` и `TripPlacesNavigationTests` проверяют экранные состояния и настоящую Compose-прокрутку к десятой отметке с нажатием на её чип. Существующие `PlaceIdentityTests`, `PlaceStoreTests`, `SegmentHistoryTests`, тесты Places и записи прошли.

Robolectric использует уже скачанный runtime: без этих JVM properties sandbox запрещает создавать его download lock в домашнем каталоге. Первые компиляции также выявили недостающие импорты; они исправлены. Первый UI-тест передавал фокус через адаптер профиля, который его не поддерживает; тест переведён на общий стек поездок, используемый Places.

Точный общий прогон:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
JAVA_TOOL_OPTIONS='-Drobolectric.offline=true -Drobolectric.dependency.dir=/Users/onezee/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7' \
./gradlew --no-daemon --offline \
  :core:model:test --tests '*PlaceIdentityTests' \
  :core:data:testDebugUnitTest --tests '*PlaceManagerTests' --tests '*PlaceReconcileMainThreadTests' --tests '*PlaceStoreTests' \
  :core:location:testDebugUnitTest \
  :app:testDebugUnitTest --tests '*TripPlacesTests' --tests '*TripPlacesNavigationTests' --tests '*TripDetailStateTests' --tests '*PlacesTabViewModelTests' --tests '*PlacesSlotTests' --tests '*WorldBehaviourTests.savingASuggestionMakesAPlaceWithItsHistory' \
  :core:logic:test --tests '*Place*' --tests '*SegmentHistoryTests'
```

Результат: BUILD SUCCESSFUL. `core:model`: 2, `core:data`: 34, `core:location`: 85, `app`: 38, `core:logic`: 93. Всего 252 теста, без ошибок и пропусков. Debug-приложение и все его тестовые исходники скомпилированы.

После явного подключения `forget` повторён прогон приложения:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
JAVA_TOOL_OPTIONS='-Drobolectric.offline=true -Drobolectric.dependency.dir=/Users/onezee/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7' \
./gradlew --no-daemon --offline :app:testDebugUnitTest \
  --tests '*TripPlacesTests' --tests '*TripPlacesNavigationTests' --tests '*TripDetailStateTests' \
  --tests '*PlacesTabViewModelTests' --tests '*PlacesSlotTests' \
  --tests '*WorldBehaviourTests.savingASuggestionMakesAPlaceWithItsHistory'
```

Результат: BUILD SUCCESSFUL, 38 тестов без ошибок и пропусков. Удаление сохраняет порядок iOS: транзакция репозитория, затем `forget`.

## Отличия и остаток

В `docs/divergences.md` добавлены различия адаптеров: JSON-массив в SharedPreferences вместо массива UserDefaults; Room/IO и invalidations вместо viewContext/MainActor и NotificationCenter; Compose relocation вместо центрирования SwiftUI; поля Android Address вместо CLPlacemark с тем же порядком подстановки. Ключ очереди, UUID, математические пороги и ожидаемые значения тестов сохранены.

Запрошенные пути Places подключены. Проверки на устройстве и в эмуляторе не проводились; эмулятор не запускался. Поведение системного геокодера и запись в реальной поездке требуют проверки на телефоне. Существующее ограничение тапов по маркерам на hero-карте остаётся в общей таблице различий. Коммитов и push не было.
