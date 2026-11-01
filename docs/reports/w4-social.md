| Пункт исходного брифа | До w4-social-2 | Что обнаружено при сверке с iPhone 0.9.3 | Результат w4-social-2 |
|---|---|---|---|
| Профиль из ленты | Частично | Переход есть; нет проверок полного пути и ошибок. | Готово |
| Профиль из поездки и путешествия | Отсутствует | Автор нарисован подписью без действия. | Готово |
| Профиль из списков | Готово | Followers/following открывают профиль в своём стеке. | Готово |
| Герой, bio, обложка, оформление PRO | Частично | `hasPlus ?? isPlus` есть; обложка не открывает просмотрщик. | Готово |
| Журнал, закрепления, страницы | Частично | Pager и месяцы есть; нет тестов, обновления по жесту и защиты всех ответов при смене аккаунта. | Готово |
| Гараж и публичный паспорт машины | Частично | Нет просмотра фото, жалобы на фото, DRIVE2 и полного представления паспорта; нулевые круги показываются при скрытой карте. | Готово |
| Чужие достижения и уровни | Частично | Полный список запрашивается; экран открывает общий каталог без имени владельца, нет проверки знаменателя и секретных значков. | Готово |
| Подписка, отписка, счётчики | Частично | Оптимистичный запрос есть; при неудачной перезагрузке остаётся ложное состояние. | Готово |
| Жалоба и блокировка | Частично | House-dialog и форма есть; повторное нажатие блока не защищено. | Готово |
| Заблокированные пользователи | Частично | Экран из master; отказ разблокировки скрывается без отклика. | Готово |
| Чужая карта, регион, список поездок | Частично | Код есть; не проверены переходы, загрузка и геометрия шторки. | Готово |
| Первый кадр карты и перелёт к целым маршрутам | Готово | Числа и расчёт соответствуют `PublicMapView` / `TripSource`. | Готово |
| Полная статистика владельца и другого человека | Частично | Экран и расчёты есть; нет перенесённых тестов и снимков. | Готово |
| Комментарии: список, добавление, удаление, ответы | Отсутствует | Store из master нигде не подключён к UI. | Готово |
| Жалоба на комментарий | Отсутствует | Есть только `ReportTarget.Comment`, нет входа в форму. | Готово |
| Реакции на поездке | Частично | ViewModel работает; UI не подключён. | Готово |
| Поделиться поездкой, путешествием, профилем | Частично | Доступен профиль и карточка поездки в профиле; на самих поездке и путешествии действий нет. | Готово |
| App Links `/s/`, `/j/`, `/u/` | Частично | Manifest и парсер есть; нет тестов разрешения share-code и инструкции для сайта. | Готово |
| Схема `triptrack://` | Частично | Парсер есть; нет тестов холодного и повторного входа. | Готово |
| Inbox и граница для будущего FCM | Частично | Экран/API есть; ошибки скрываются, пагинация и выход из аккаунта допускают гонки. | Готово |
| House dialogs, отклик, пустые состояния, Back | Частично | Системных AlertDialog нет; встречаются мёртвые фото и подписи, скрытые ошибки. | Готово |
| iPhone-тесты и MockWebServer | Отсутствует | Предыдущие три коммита тестов не добавили. | Готово |
| Roborazzi в двух темах: запись, просмотр, сравнение | Отсутствует | Снимков новых экранов нет. | Готово |
| `docs/release.md`, отчёт | Отсутствует | Документация не написана. | Готово |

# w4-social

Аудит трёх исходных коммитов и завершение задачи w4-social-2. Источник: read-only `trip-track-ios-ref`, iPhone 0.9.3. Коммиты и push не выполнялись.

## Исправления

- Подключены автор поездки, публичная машина и автор путешествия к стеку, из которого пришло нажатие. Счётчик комментариев открывает обсуждение; уведомление о комментарии передаёт его ID. Back из профиля автора возвращает в обсуждение. На чужой приватной поездке действия публичного обсуждения и sharing не появляются.
- `TripDiscussion.kt`: превью двух свежих комментариев в хронологическом порядке, полная house-sheet, загрузка по 30, старые страницы, ответы к корню, создание с временным ID, удаление с подтверждением и восстановлением при отказе, жалоба, гостевой вход, архив без сетевого запроса. Поле сохраняет черновик после ошибки; отправка использует существующий `SocialCommentsApi` с `singleAttempt`. Поиск комментария из inbox ограничен пятью дополнительными страницами, подсветка 2,4 секунды, как в Swift.
- На поездке подключены существующие реакции и share-link; на путешествии появился share-link. Своя приватная поездка проходит через публикацию и ждёт появления на сервере перед sharing. Android chooser получает `text/plain`, URL и заголовок. Профиль и его карточки используют существующие share/copy действия.
- Профиль обновляется жестом; ошибки первой загрузки различают закрытый публично известный профиль, анонимную недоступность и временную ошибку. Пагинация сохраняет страницы и закрепления. Отписка не уводит счётчик ниже нуля; отказ восстанавливает предыдущее состояние. Блокировка защищена от повторного запроса. Ответы от прошлой загрузки/сессии не заменяют свежие данные.
- Публичные обложки и фото машины открывают замороженный просмотрщик. Берётся original URL; снимки из поездок не отправляются как `vehicle_photo`. Главный снимок не повторяется на полке. Добавлены DRIVE2, паспортные параметры из каталога, теги, подпись рекорда; круги не показывают ложные нули при скрытой карте. Отказ приватности убирает предзагруженную машину.
- Чужие достижения: имя владельца, полный список с сервера, фильтры, неизвестные IDs не входят в счёт, заработанный секрет входит в знаменатель. Значок открывает house overlay с Back и закрытием. Уровни используют серверные 26 400 XP, без выдуманного прогресса на старом сервере.
- Публичная карта сохраняет исходные 10 последних превью и затем кадрирует целые маршруты; регион и дорога живут в одной шторке. Высота региона `min(440, screen × 0.55)`, строки 56, предел 4,5 строки; загрузка поездок 500 × 20. Нажатие булавки отвечает. Снимок региона теперь требует настоящий регион вместо молчаливого пропуска.
- После свежего публичного снимка сбрасывается кэш полилиний по ID, как в существующем Android Atlas. Тест поймал старые координаты вместо нового маршрута. Сброс также применяется к статистике и биографии машины.
- Inbox: ошибка отличается от пустого списка; запрос страницы не дублируется, курсоры не зацикливаются, строки не повторяются. Read-on-sight один раз уменьшает badge, сохраняет подсветку строки до следующего обновления и отправляет пачки не более 200. Отложенный flush принадлежит store, смена аккаунта отменяет старый refresh. Неактивная информационная строка не притворяется кнопкой. У ответа на приглашение есть защита от повторного нажатия. FCM оставлен за `pushArrived` / `pushTapped`; transport push не добавлялся.
- Отказ разблокировки показывает обратную связь в house-sheet. Системных `AlertDialog` в новой области нет. Новые слова берутся из существующих сгенерированных ресурсов; зависимости, Gradle memory и version catalog не менялись.

## Источники и тесты

Основные Swift-файлы: `Views/Social/{PublicProfileView,FollowListView,ReportSheet,PublicGarageView,PublicVehicleView,PublicJourneysView,ShareLinkPresenter,ProfilePreviewNav}.swift`, `Views/MyMap/PublicMapView.swift`, `Views/Profile/StatsScreenView.swift`, `Views/Badges/{PublicAchievementsView,AchievementsView,BadgeDetailOverlay}.swift`, `Views/Trips/{TripCommentsScreen,TripCommentsSection,TripDetailView}.swift`, `Views/Journeys/PublicJourneyView.swift`, `Services/{TripSource,NotificationsInboxStore,CompanionsStore}.swift`, `Services/Social/TripCommentsStore.swift`, `App/TripTrackApp.swift`.

| iPhone | Android |
|---|---|
| `ProfileTripsPagerTests`, `PublicCosmeticsTests`, `ProfileUnavailableTests` | `ProfileRulesTests` |
| `PublicAchievementsCatalogueTests` | `ProfileRulesTests`; UI-фильтры и overlay в `SocialFlowTests` |
| `PublicMapStartTests`, `PublicMapRegionsTileTests`, `TripSourceTests`, адаптер `PublicJourneyAdapterTests` | `PublicMapRulesTests` |
| `MeAggregatesTests` | `MeAggregatesPortTests`, те же границы года, дни, расстояния и ожидания; календарь UTC |
| `NotificationsInviteRowTests` | `InviteRowPortTests`; приватный title проверяется на снимке invitation row |
| `TripDetailSharePrivacyTests`, четыре сценария sharing | `SharePrivacyPortTests` |
| `StatsScreenFlowTests`, `PublicAchievementsFlowTests`, `PublicLevelsFlowTests`, `ProfileTapTargetsTests` | `SocialFlowTests`, Roborazzi |
| `PublicMapFlowTests`, `PublicMapSummaryTests`, `ForeignStatsOpeningTests`, `PublicProfileCoverTests`, `StatsScreenShotTests` | `SocialScreenshotTests`, состояния карты/шторок, загрузки, ошибок и обложки |
| `SocialProfileFindsTests`, `ProfileJournalWireTests`, `PublicJourneyDTOTests`, `SocialFeedVehicleDecodingTests` | Уже перенесены в `core:model`: `ProfileWireTests`, `CompanionsDTOTests`. Их фикстуры не заменялись |
| `ProfileMonthlyJournalTests`, `ProfileHistoryFoldingTests`, `PreparedProfileHistoryTests`, `JourneyWindow.days` | Уже перенесены в `core:logic`; публичный журнал вызывает эти builders и не сворачивает неизвестные checkpoints в routine |
| `TripCommentsStore` и `NotificationsInboxStore` | Отдельных XCTest для всего store в эталоне нет. `SocialWireBehaviourTests` проверяет реальные envelope/HTTP, `InboxStoreTests` проверяет конкуренцию и read-on-sight; существующие `TripCommentsStateTests` сохранены |
| `TripTrackApp.handleDeepLink`, share-page links, `NotificationManager` | Отдельных XCTest-парсеров в эталоне нет. `DeepLinkRulesTests` и `SocialWireBehaviourTests`: custom scheme, HTTPS, неподходящий host/UUID, HTML → правильный тип экрана, приватное приглашение не открывает trip |

MockWebServer-тесты используют реальный `ApiClient`: follow/unfollow, rollback, block/unblock, все пять report target types, comments list/create/delete, сохранение черновика и один HTTP-запрос при 503, `/s/` и `/j/` с разрешением HTML в нужный экран. Секретов и обращений к production в фикстурах нет.

## Снимки

90 файлов `app/src/test/screenshots/social_*.png`: русский язык, SDK 35, 411 × 914 dp, обе темы. Проверяются профиль, обложка, журнал и сетка, закрытый/недоступный/ошибочный профиль, просмотрщик, подписчики и подписки, гараж и паспорт, публичное путешествие и список, достижения и карточка значка, уровни, своя/чужая статистика и её состояния, карта и её шторки, inbox и приглашение, жалоба, блокировка, обсуждение и ответ, архив и share-link sheet.

MapLibre не запускается под Robolectric: фон карты подменён, как в существующих Atlas-тестах. Проверяются настоящие Compose-шторки, строки, данные и переходы. Искусственные фотографии проверяют размеры, просмотрщик и порядок, не загрузку production CDN. Снимки просмотрены контактными листами; обрезание широкой фотографии и подмена региона сводкой обнаружены при этом просмотре и исправлены.

Визуальная сверка также исправила порядок секретных значков: незаработанные секреты идут после обычных закрытых. Цвет названия в overlay перенесён из `BadgeDefinitions.swift`. Фикстура карты повторяет две iOS-поездки по Франции, по 2 400 точек, 896,3 + 945,7 = 1 842 км.

## Проверки

Запись снимков на этом Mac:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" :app:testDebugUnitTest --tests 'com.onezee.triptrack.social.*' -Proborazzi.test.record=true
```

`BUILD SUCCESSFUL`, 180 тестов, 0 ошибок и пропусков; записаны 90 PNG. Все снимки просмотрены.

Итоговый запуск со сравнением снимков включённым по умолчанию в `app/build.gradle.kts` (`roborazzi.test.verify=true`, record выключен):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" :app:testDebugUnitTest :core:model:test :app:assembleDebug :app:lintDebug
```

`BUILD SUCCESSFUL in 4m 12s`. App: 738 тестов, включая 180 social; core:model: 87 тестов. Ошибок и пропусков нет, сравнение всех 90 social-снимков прошло. Debug APK собран.

Lint: 0 ошибок, 73 предупреждения и 2 подсказки. Среди предупреждений в social остаются `ConfigurationScreenWidthHeight` для высоты шторки и `ModifierParameter` для внутреннего `RoadScene`; оба участка были в исходных трёх коммитах. Остальные относятся к ресурсам, зависимостям и коду вне social. Порог высоты шторки сохранён по iOS.

`git diff --check` прошёл. Поиск `git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'` не нашёл совпадений; новые файлы отдельно проверены тем же выражением через `rg`. Новые зависимости не добавлялись. `docs/port-status.md` не менялся.

## Что проверить на устройстве

- Действительный `assetlinks.json` и Play-подпись: отпечаток даёт владелец из Play Console. Шаблон и команды adb находятся в `docs/release.md`; сайт не менялся.
- GPU карты, жесты и клавиатуру на устройстве, TalkBack, Android chooser с установленными приложениями. Эмулятор по условию задачи не запускался.
- FCM delivery не входит в задачу. Inbox работает через HTTP; seam для будущего messaging service сохранён.

Платформенные отличия перечислены в `docs/divergences.md`. Не переносились iOS simulator-only тесты пикселей MapKit, Metal и UIKit presentation: этих платформенных рендереров на Android нет.
