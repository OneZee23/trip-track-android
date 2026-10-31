# w3-me-link

«Я» открывает полные экраны поездок, путешествий и черновиков из `TripsFeature`.
Промежуточные `feature.MeTab`, `MePlaceholder` и локальные read-only экраны удалены.

## Перенесено

| Область | iOS | Android |
|---|---|---|
| Общий стек «Я» | `ProfileView.swift`: `MeDest`, `push`, `navigationDestination` | `MeNavigator` и `MeNavHost` поверх общего `TabNavHost`. Поездки из глав, сетки, закреплений, повторов и паспорта машины используют `TripsDestinationScreen`, его экраны и фабрики моделей. Переходы внутри поездки и путешествия остаются в стеке «Я» |
| Состояние навигации | `ProfileView.mePath`, `TripJourneyNavigationTests.swift` | Идемпотентность по типу и ID, превью первого кадра, сохранение scroll position. Стек принадлежит `MeFeatures`: его модели переживают уход композиции вкладки и смену конфигурации. После перезапуска восстанавливаются типы и ID |
| Черновики | `ProfileDraftsRow.swift`, `DraftsListView.swift`, `DraftsFlowShotTests.swift` | Строка ведёт в общий `DraftsListScreen`; открытие помечает список прочитанным. Поездка из списка возвращается в черновики |
| Долгое нажатие | `ProfileView.swift`, `ProfileTripCardView.swift` | Карточки журнала и закрепления открывают общий `JourneyComposerSheet`; создание обновляет журнал |
| Аккаунт | `ProfileView.guestSignInCard`, `ProfileSettingsSheet.swift` | Существующий `AccountEntry` сохранён: гость и истёкшая сессия видят карточку под героем; вошедший пользователь видит Account & Sync в настройках. Язык и тема остаются в настоящих настройках |
| Уровни | `LevelsView.swift`, `GamificationModels.swift`, `PublicLevelsFlowTests.swift` | Отдельный push из героя и профиля, текущий ранг, XP до следующего уровня, правила и 14 рангов. Используется общий `LevelSystem`; пример 21 / 26 400 XP показывает цель 28 500 |
| Страна | `CountryPickerView.swift` | Отдельный push из профиля; исходный порядок флагов, WORLD/NEUTRAL/пустое значение, сохранённый ISO-код вне каталога. Выбор сохраняет и возвращает назад; повторный выбор только закрывает экран |
| Имя и биография | `NameEditorSheet.swift`, `AboutEditorSheet.swift`, `ContentFilter.swift`, `ContentFilterTests.swift` | Раздельные редакторы, лимиты 30/140 графем, очистка биографии, фильтр имён и исходные случаи проверки. Санитайзер и denylist переиспользованы из уже перенесённых правил |
| Zoom фото | `PhotoFullScreenView.swift`, `ZoomableImageView.swift` | Общий просмотрщик профиля/гаража: pinch 1–4×, double-tap 2.5× к точке касания и обратно, pan. При zoom пейджер отключён; другая страница начинает с 1×. Набор страниц остаётся замороженным |

Достижения и карточка награды, гараж и клубы остаются типизированными переходами того же
стека. Дубликатов trip/journey/drafts моделей нет. В общих экранах заменён только конкретный
тип навигатора на интерфейс push/pop.

Генератор строк дополнен `DriverRank.titleRu/titleEn` и таблицами переводов iOS. Добавлены
14 названий во всех 13 языках: 1863 строки и 30 plurals на язык. Новых библиотек нет;
версии, Gradle-настройки и build-файлы не менялись. `feature/atlas`, places и
`docs/port-status.md` не менялись.

Дополнительно исправлены найденные lint-ошибки объединённых экранов: лишний
`BoxWithConstraints` графика заменён на `Box`; время снимка читается из публичных EXIF-тегов
вместо закрытого `getDateTimeOriginal`. Парсер следует `PhotoMetadata.swift`: оригинал,
затем оцифровка; явное смещение зоны, иначе зона телефона; время редактирования не берётся.
Добавлены пять JPEG-тестов на исходном времени `2026:09:10 14:23:05` из
`PhotoMetadataTests.swift`. Публичные теги описаны в
[AndroidX ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface).

## Проверки

Окружение всех Gradle-команд:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Итоговый запуск:

```sh
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  :app:testDebugUnitTest :app:lintDebug :app:compileReleaseKotlin \
  :core:designsystem:testDebugUnitTest
```

BUILD SUCCESSFUL: 415 тестов app и 172 теста дизайн-системы; 0 ошибок и пропусков.
Lint и release-компиляция прошли. Эталоны снимков проверены тем же запуском.

Поведенческие проверки: история → поездка → Back с тем же scroll offset; закрепление →
поездка; раскрытые повторы → поездка → Back с сохранённым раскрытием; глава → путешествие;
путешествие → плечо → его путешествие → Back к прежнему entry поездки; «Я» → черновики →
поездка → черновики; повторные push; восстановление живого стека; уровни и страна из профиля;
гостевая карточка и настройки; очистка bio; первый pinch и два double-tap.

Первый полный прогон: 408 тестов app, один отказ. Тест повторов нажимал строку под
плавающим таббаром; прокрутка исправлена по примеру `ProfileCardShotTests.align`.
Следующий полный прогон: 410 тестов app и 172 дизайн-системы прошли; lint нашёл три
старые ошибки в EXIF/графике, исправленные выше. Предыдущий отказ zoom-теста выявил перехват второго double-tap обработчиком pan:
панорамирование теперь начинается после touch slop и не поглощает отпускание пальца.

Запись снимков:

```sh
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  :app:testDebugUnitTest --tests '*MeInteractionTests*' --tests '*TripsScreenshotTests*' \
  --tests '*MeScreenshotTests*' --tests '*ShellScreenshotTests.me*' \
  -Proborazzi.test.record=true
./gradlew --no-daemon --offline \
  -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" \
  :app:testDebugUnitTest --tests '*MeScreenshotTests*' -Proborazzi.test.record=true
```

Оба запуска прошли. Из существующих изменились восемь снимков: Me, drafts, drafts selection
и journey composer в двух темах. Добавлены восемь: уровни, страна, имя и bio в двух темах.
Снимки просмотрены; shell Me перезаписан без изменения изображения. Native MapLibre в
этих снимках заменён прежним статическим маршрутом.

```sh
python3 -m unittest discover -s tools/strings -p 'test_*.py'
python3 tools/strings/generate.py --check
python3 tools/strings/validate.py
git diff --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

28 Python-тестов прошли; генерация совпала, 13 языков прошли валидацию; diff без ошибок;
поиск адресов и ключей без совпадений.

## Осталось и отличия

- Полная статистика `StatsScreenView`, подписчики/подписки, публичный preview и переходы
  внутри чужих профилей, админская панель. Это отдельные экраны и источники данных,
  перечисленные в остатке `w3-ui-me`; упрощённые замены для них не добавлены.
- Просмотрщик Me сохраняет прежнее фиксированное оформление. Пока нет инерции pan,
  rubber band, ограничения pan по фактическим краям изображения, вертикального закрытия
  жестом и выбора fill при обрезке ≤20%. Pinch и double-tap работают; оставшиеся отличия
  от `UIScrollView` записаны в `docs/divergences.md`.
- Владельцы записи и Atlas ещё подключают `ProductAnalytics.recordingStarted`,
  `recordingFinished`, `atlasOpened`; эти области не менялись.
- Сохраняются проверки на телефоне из `w3-ui-me`: Photo Picker, Bluetooth, файловый шаринг,
  native MapLibre, серверные сценарии профиля и двух телефонов. Живой возврат карты в
  `TripJourneyNavigationTests` здесь проверен только по стеку и статическому экрану.

В `docs/divergences.md` удалены временные строки про промежуточный Me и read-only
trip/journey; обновлены остаток вспомогательных экранов и жесты фото. Других новых
платформенных отличий нет. Эмулятор не запускался; коммитов и push нет.
