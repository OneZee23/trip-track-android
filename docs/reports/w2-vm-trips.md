# w2-vm-trips

Перенесён слой состояния экранов в `app`: feed, trip, journey, drafts. Состояния имеют `val`-поля,
публикуются через read-only `StateFlow` и не содержат Android framework types. Запросы и подготовка
маршрута идут на переданном dispatcher. Composables, AppGraph, навигация, MainActivity и модули
designsystem/map/location не менялись. Коммитов и push нет.

## Перенесено

- `FeedViewModel.swift`, `SocialFeedStore.swift`, `FeedView.swift`: Mine с фильтрами, страницами по
  20 и загрузкой следующей страницы на последних пяти поездках; статистика по всему отфильтрованному
  набору; календарные километры, регионы и lookup машины без чтения полного трека из карточки.
  `trips/sections` сохраняют исходную пагинацию FeedViewModel, а `mineRows` сворачивает весь
  отфильтрованный набор через существующий `HistoryFolding`: страница не обрезает итог путешествия.
  Социальные карточки сохраняют
  отдельную journey row на каждом плече.
- Социальная загрузка: `loadIfNeeded` сохраняет текущий запрос и свежую непустую ленту на 15 минут;
  `refresh` отменяет предыдущий запрос. Поколение защищает от старой страницы и сброса spinner новым
  запросом. Append удаляет повторные ID. Скрытые локально поездки исключаются из серверного ответа.
  Offline останавливает загрузку сразу; пустой cold start повторяется максимум дважды через 1500 ms.
  Изменения библиотеки объединяются с окном 800 ms.
- `SocialReactions.swift`, `TripCommentsStore.swift`: оптимистические реакции, включая вытесненные
  из первой страницы поездки; rollback по ID; комментарии со страницами по 30, временной строкой,
  ответом корню thread, восстановлением удалённой строки, архивом и 404 unavailable. Создание
  комментария вызывает существующий `SocialCommentsApi`, где уже стоит `singleAttempt=true`.
- `TripDetailView.swift`, `TripDetailSections.swift`, `OwnTripPhotosModel.swift`: metadata первого
  кадра, worker-read полного detail, ревизия каждой замены поездки, отмена старой подготовки фото,
  replay, булавки, моменты, checkpoints, segments/history, чипы живых мест, графики на 200 buckets,
  энергия через существующие `EnergyEstimate.resolve/day`. Режим энергии хранится отдельно в
  состоянии экрана и меняется до завершения записи. Собственные и серверные фото объединяются по ID;
  отсутствующий локальный файл использует серверную копию либо получает missing state.
- Privacy/publish используют `TripRepository.updatePrivacy`, который сам ставит операции в sync.
  Вход проверяется перед публикацией и снятием существующей серверной копии. Удаление требует
  `HouseConfirmation`; подтверждение несёт ID запроса и захваченный предмет действия. Ожидание
  результата privacy: 40 проверок через 500 ms. GPX/CSV строятся на worker; пустой трек на экране
  даёт exportFailed. У своей remote-backed поездки экспорт использует загруженный серверный трек.
- `JourneyManager.swift`, `JourneyDetailView.swift`, `JourneyComposerSheet.swift`, `JourneyEditSheet.swift`,
  `JourneyPublishSheet.swift`: own/social detail, aggregate, календарные дни, маркеры и кэшированные
  названия мест. Foreign mode берёт плечи из ответа и не создаёт локальные поездки. Composer показывает
  соседей ±7 дней, отмечает только `chainAround`; supplied preselection виден сразу и не расширяется.
  Снятые внутри окна поездки сохраняются в исключениях. Правка использует существующий `JourneyWindow`,
  сохраняет часы нетронутых границ, считает то же окно, которое запишет, и откладывает возврат плеч
  до Save. Предел имени 60 Swift Character сохранён через grapheme matching.
- Публикация путешествия вызывает отдельный `updatePrivacy` для каждого приватного плеча, затем
  сохраняет и ставит путешествие в очередь. `JourneyManager` принимает `SyncRequestSink`; в приложении
  это должен быть существующий `SyncEnqueuer`. Hide не меняет плечи, отменяет прежние операции
  путешествия и ставит unpublish. Delete удаляет обёртку, сохраняя поездки.
- `DraftsListView.swift`, `DraftsGrouping.swift`, `DraftsBadge.swift`, `DraftsLayout.swift`: группы,
  выбор, подтверждение удаления, чистая геометрия и строгое `startDate > seenAt`. Решение сначала
  сохраняется в очереди и только затем убирает строку. Прямого подтверждения/удаления из Room нет.
  `CurrentRecordingSource`, `DraftDecisionQueue` и `DraftsSeenStore` задают границу будущего сервиса
  записи и настроек. Деталь черновика использует ту же очередь.

## Небольшие добавления в core

- `core:logic`: `GPXExporter.swift` и `CSVExporter.swift` с исходными форматами, UTC, шестью знаками,
  сортировкой по времени, пропуском interpolated points и неизвестных полей. Округление проверено
  локальным Foundation: `0.0000005 → 0.000000`, `1.2345665 → 1.234566`,
  `1.2345675 → 1.234568`, `-0.0 → -0.000000`. JVM Formatter заменён округлением точного binary double.
- `core:logic`: `TripReplayInput.swift` и `GeometryUtils.significantIndices`, бюджет 300, одни индексы
  для координат, скоростей и времени. Async/cancellation wrapper остаётся у ViewModel. То же касается
  существующего `TripPhotoPlacement.prepare`: он вызывается на worker с проверкой отмены и ревизии.
- `core:logic`: comment-часть `ContentFilter.swift`, которой ещё не было: sanitize, исходный denylist
  и предел 500. При отправке сохраняется edge-trimmed исходник, внутренние переносы строк остаются.
- `core:sync`: `ManualTripTrackLoader.loadIfNeeded` для открытия manual detail, с существующим
  validator и проверками account/cloud/snapshot; `DiscussionArchive.save` для успешных чтений
  комментариев. Добавлен app-адаптер `SyncCommentArchive`.

Новая библиотека: `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4`, только для app. Остальные
подключённые зависимости уже были в каталоге. Не менялись gradle.properties и build files других
модулей. Один online-запуск понадобился для незакэшированного lifecycle-service после добавления
ViewModel; дальнейшие проверки выполнялись offline.

## Проверка

Все команды использовали JDK 17:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Для Robolectric нужен временный `/private/tmp/w2-vm-trips-tests.gradle`, выбирающий уже скачанный
SDK jar без lock в домашнем каталоге вне sandbox:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'robolectric.offline', 'true'
        systemProperty 'robolectric.dependency.dir', System.getProperty('user.home') + '/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7'
    }
}
```

Заключительные команды:

```sh
./gradlew --no-daemon --offline \
  -I /private/tmp/w2-vm-trips-tests.gradle \
  -I /private/tmp/w2-vm-trips-lint.gradle \
  :core:logic:test :core:sync:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug

./gradlew --no-daemon --offline \
  -I /private/tmp/w2-vm-trips-tests.gradle \
  -I /private/tmp/w2-vm-trips-lint.gradle \
  :app:testDebugUnitTest :app:lintDebug
```

Первый запуск PASS, 1m 12s; после последней правки folding повторён только app: PASS, 36s.
Итог: logic 611, sync 127, app 52; всего 790 тестов, 0 failures/errors/skipped.

Перенесены `GPXExporterTests.swift`, `CSVExporterTests.swift`, `DraftsLayoutTests.swift`,
`DraftsGroupingTests.swift` вместе с badge-case, оба исходных кейса `JourneyPublishTests.swift`.
Существующий полный порт `JourneyEditWindowTests.swift` переиспользован без изменений.
`TripDetailFirstPaintTests.swift` проверяется на уровне первого состояния: 49 км и 4 ч 58 мин
известны до запуска загрузки. Добавлены проверки screen-state редактора, chain/preselection,
cold-start/refresh/pagination races, полного journey total за пределами страницы, реакций после
eviction/session change, фото-инвалидации при том же ID, remote export, hybrid energy и draft queue.
Также проверены comment sanitize/profanity fixtures из `ContentFilterTests.swift`, reply-root,
optimistic rollback, archive fallback и manual-detail hydration на настоящем Room.

Полная команда без lint-init script:

```sh
./gradlew --no-daemon --offline -I /private/tmp/w2-vm-trips-tests.gradle \
  :core:logic:test :core:sync:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
```

Тесты прошли, lint упал внутри `LeakDetector`: `Unexpected owner function: null` при анализе
`JourneyViewModels.kt`. Для остальных проверок временный `/private/tmp/w2-vm-trips-lint.gradle`
содержит:

```groovy
allprojects {
    plugins.withId('com.android.application') {
        extensions.getByName('android').lint.disable.add('StaticFieldLeak')
    }
}
```

С этим обходом lint завершился без ошибок. Три предупреждения: `ObsoleteLintCustomCheck`
(lifecycle runtime registry несовместим с API анализатора), `OldTargetApi` (заданный target 36),
`MissingApplicationIcon` (исходная оболочка). Обход и подавления в репозиторий не добавлены.
Первая попытка Robolectric без test-init script останавливалась до тестов на записи lock вне sandbox.

`git diff --check`, обязательный `git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'`
и отдельный scan новых файлов: без замечаний. Эмулятор не запускался.

## Передача следующей UI-задаче

- Подключить ViewModel к владельцам экранов; FeedViewModel удерживать между сменами вкладок.
  Передать изменения Room/sync, события connectivity/session и текущую запись. При выходе вызвать
  `clearSocial`. Detail открывается с preview и затем `load()`. Для точки черновиков удерживать
  DraftsViewModel и вызвать `refresh()` при старте; при входе в список вызвать `opened()`.
- Передать `SyncEnqueuer` в JourneyManager и существующий repository в остальные модели;
  `ManualTripTrackLoader::loadIfNeeded` в detail. `afterLoad` оставлен владельцу aftermath.
- Сервис записи реализует durable `DraftDecisionQueue`: enqueue сохраняет FIFO-решение до возврата,
  service consumer применяет голову и удаляет её после успеха. `DraftsSeenStore` хранит `drafts.seenAt`.
  Реализации сервиса здесь намеренно нет по границе задачи.
- UI разрешает AppStrings keys и TripMessage через сгенерированные ресурсы, а SI-значения через
  `Measure`. Растровые thumbnails и native map objects готовит адаптер карты/изображений.
  Экспортный адаптер пишет UTF-8 на worker в отдельный очищаемый каталог формата и передаёт файл
  через FileProvider. House dialog использует захваченный ID подтверждения, Cancel последним.
- Как в iOS, собственная remote-backed поездка без локальной строки не создаёт её при открытии;
  repository actions сохраняют этот контракт, экран privacy обновляет имеющийся снимок.
- Пиксельная часть FirstPaint, composables, native share sheet и replay animation относятся к UI/map.
  В этой задаче проверяется state-часть.

Различия платформ записаны в `docs/divergences.md`: lifecycle/state/event representation, первый
async Mine-read, export handoff и Unicode tables. Порогов, единиц хранения и wire-ключей не менялось.
