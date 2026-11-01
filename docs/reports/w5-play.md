# w5-play

Готов пакет для закрытого теста: [docs/play](../play/README.md). Работа выполнена в `w5-play`.
Коммитов, push, публикации сайта и изменений Play Console нет. Эмулятор не запускался.

## Что сделано

- 39 файлов листинга для 13 языков с кодами Play Console. Португальский: `pt-BR`, как
  `Translations+PT.swift` и локаль `pt_BR` iPhone. Термины сверены с `AppStrings*.swift`
  и `Translations/Translations+XX.swift`. Описаны доступные Android-функции; социальные
  экраны и незавершённые Places не рекламируются.
- `tools/play/check_listing.py`, только stdlib: все локали, наличие/непустота полей,
  лимиты 30/80/4000, одна строка для коротких полей, UTF-8 и невидимые символы.
  Подсчёт UTF-16 не занижает длину supplementary-символов. Девять unittest-сценариев;
  тесты и проверка реальных листингов включены в `.github/workflows/android.yml`.
- Data safety: текущий вариант без Sentry и отдельные изменения ответов при его добавлении.
  Учтены Google-вход, независимые от Cloud Sync профиль/фото, публикация поездок,
  добровольные счётчики, карты/геокодер, обрезка приватной зоны и удаление аккаунта.
- Инвентаризация всех 17 `uses-permission` из release merged manifest, в том числе
  MapLibre, WorkManager и AndroidX Biometric. Подготовлены тексты деклараций фоновой
  геолокации и location FGS, два сценария видео и аудит prominent disclosure.
- Android-замены `onboardingBackgroundSub` и
  `permission_NSLocationAlwaysAndWhenInUseUsageDescription` во всех 13 языках явно называют
  сбор точного местоположения, автозапись и работу при закрытом/неиспользуемом приложении.
  Использован `tools/strings/android.json`; ресурсы сгенерированы, вручную не правились.
  Обновлены только `onboarding_3_light.png` и `onboarding_3_dark.png`.
- Чеклист владельца: аккаунт/поля приложения, возраст/контент, декларации, доступ ревьюера,
  OAuth/backend/подпись, closed test с 12 участниками на 14 непрерывных дней.
  Гостю доступен локальный дневник; облаку и удалению аккаунта нужен вход, что отдельно
  названо в инструкции ревьюеру.
- Английские и русские дополнения к опубликованной политике, включая вариант с Sentry.
  Исправлено противоречие старой политики: текущий `AuthService` обеих платформ после
  успешного удаления аккаунта по умолчанию удаляет и локальную библиотеку.
- PNG `icon-512.png` (512×512) и `feature-1024x500.png` (1024×500), RGB без alpha,
  генератор рядом. Использованы D2, Inter и утверждённая подпись серии скриншотов iPhone.
  Составлен план реальных Android-скриншотов; сами снимки телефона в задачу не входят.

## Источники iPhone

Read-only reference: `~/OneZeeProjects/trip-track-ios-ref`, 0.9.3. Прочитаны профильные
разделы `CLAUDE.md`: приватная зона/координаты отметок, разрешения онбординга,
сессия/удаление, новые условия автозаписи.

- Маркетинг: `docs/store/aso-live.md`, `docs/store/screenshots-2026-10.md`,
  `docs/releases/0.8.4/app-store.md`, `docs/releases/0.6.2/app-store-locales.md`.
- Приватность: `docs/store/app-store-privacy-labels.md`, `docs/privacy-policy.html`,
  `docs/privacy-policy-ru.html`; сверка с Android `core/network`, `core/sync`, аккаунтом,
  профилем, счётчиками и `docs/api-contract.md`. Таблица privacy labels iPhone устарела
  относительно его опубликованной политики: Sentry и добровольные счётчики уже описаны там.
- Экран и последовательность разрешений: `Views/Onboarding/OnboardingView.swift`,
  `Services/OnboardingPermissionCoordinator.swift`, `TripTrackTests/OnboardingPermissionTests.swift`.
  Его случаи отказа, повторного нажатия и последовательного background/motion уже перенесены
  в Android `OnboardingTests`; эта работа меняет только слова.
- Контракты данных: `Models/Sync/TripSyncPayload.swift`, `Services/AuthService.swift`,
  `Views/Profile/CloudSyncView.swift`, `Services/ProductAnalytics.swift`,
  `Services/SentryService.swift`. Существующие спецификации: `HomePrivacyTrimTests`,
  `PrivacyZoneDisciplineTests`, `Sync/TripSyncPayloadMapperTests`, `ProfilePhotoStoreTests`,
  `AuthSessionExpiryTests`. Новая бизнес-логика и новые Kotlin-типы не добавлялись.
- Графика: `Tools/AppIcon/D2.svg`, `Tools/build_app_icons.py`; та же нормализация,
  которую использует Android `tools/icons/build_android_icons.py`.

Google Play требования сверены с официальной справкой; ссылки стоят рядом с ответами.
OpenFreeMap проверен отдельно: его политика распространяется и на публичные тайлы.
Обмен с независимым оператором карт консервативно заявлен как sharing, без предположения
о договоре обработки от имени TripTrack.

Генерация из указанного эталона 0.9.3 также добавила восемь ключей условий автозаписи
из iOS и сдвинула исходные номера строк в facade/manifest/manual-porting. Это механический
вывод генератора, без правок поведения. Android-формулировки Android Auto для этих ключей
готовит соседняя задача w5-ios093; её overrides нужно объединить с двумя заменами disclosure,
после чего снова выполнить генерацию. Версия Android не менялась.

## Проверки

Все команды `./gradlew` ниже запускались с префиксом
`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` перед командой.

| Команда | Результат |
|---|---|
| `python3 tools/play/check_listing.py` | PASS: 13 локалей, 39 полей в лимитах |
| `python3 -m unittest discover -s tools/play -p 'test_*.py' -v` | PASS: 9 тестов |
| `python3 -m unittest discover -s tools/strings -p 'test_*.py' -v` | PASS: 28 тестов |
| `python3 tools/strings/generate.py --ios /Users/onezee/OneZeeProjects/trip-track-ios-ref` | PASS: 1871 string + 30 plurals на язык |
| `python3 tools/strings/generate.py --ios /Users/onezee/OneZeeProjects/trip-track-ios-ref --check` | PASS: вывод воспроизводится |
| `python3 tools/strings/validate.py` | PASS: все 13 языков |
| `./gradlew --no-daemon --offline :app:processReleaseMainManifest` | BUILD SUCCESSFUL; merged manifest прочитан вместе с blame |
| `./gradlew --no-daemon --offline :app:testDebugUnitTest --tests '*AccountScreenshotTests.onboardingBackground*' -Proborazzi.test.record=true` | Первый запуск: ошибка доступа Robolectric к `~/.robolectric-download-lock`; тесты не исполнились |
| `./gradlew --no-daemon --offline -Drobolectric.dependency.dir=/Users/onezee/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7 :app:testDebugUnitTest --tests '*AccountScreenshotTests.onboardingBackground*' -Proborazzi.test.record=true` | PASS: 2 эталона записаны с установленным runtime, без загрузки зависимостей |
| `./gradlew --no-daemon --offline -Drobolectric.dependency.dir=/Users/onezee/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7 :app:testDebugUnitTest --tests '*AccountScreenshotTests*' --tests '*OnboardingTests*' --tests '*LocationPermissionUiTests*' :core:designsystem:testDebugUnitTest :core:designsystem:lintDebug` | PASS: app 42, design system 172, 0 failures/errors/skipped; lint 0 ошибок, 42 предупреждения |
| `python3 docs/play/graphics/generate.py` | Chromium получил SIGTRAP в sandbox; использован штатный альтернативный renderer |
| `python3 docs/play/graphics/generate.py --resvg-module /private/tmp/triptrack-icon-render/node_modules/@resvg/resvg-js` | PASS на resvg 2.6.2; финальный скрипт повторно дал те же PNG |
| `git diff --check` | PASS |

Проверки публичных данных, обе без совпадений:

```sh
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
rg -n 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY' docs/play tools/play -g '*.md' -g '*.txt' -g '*.py'
```

Gradle запускался по одному.
Предупреждения lint относятся к API lint, локали, динамическому lookup и plural-словам;
ошибок нет. Успешная проверка экранов выполнялась с включённым сравнением эталонов,
а не с `roborazzi.test.verify=false`.

Графика проверена визуально в полном размере, в миниатюре, в сером и с размытием;
раскрытие геолокации прочитано на светлом эталоне. PNG: иконка 39 846 байт,
feature graphic 94 486 байт. SHA-256:

```text
icon-512.png          3ea8303c1fc9b1dbe3fbb235b1ddf3df62530691a885d26b03b856c76d82a858
feature-1024x500.png  2cf018ec909cb40fbf2c00e36da5723acf4f29de6609ebb0f02e76290760f4f4
```

## Отличия и оставшаяся работа

Единственное новое отличие поведения текста: prominent disclosure Android, записано
в `docs/divergences.md`. Порядок разрешений и записи не менялся. Play-коды локалей,
формат PNG, Google-вход, отсутствие Sentry, локальные разрешения Activity Recognition/
Bluetooth и location FGS отражают платформу; уже существовавшие различия не исправлялись.
512 px иконка оставляет D2 целиком, без адаптивного inset Android; feature graphic имеет
широкую композицию из того же рисунка. Новых Gradle/app-зависимостей нет.

Оркестратору: объединить соседние ветки и overrides, пересобрать merged manifest, сверить
Data safety/контент-рейтинг с реально включёнными социальными функциями. Проверить отчёт
парности перед сменой версии. Политика и внешняя страница удаления пока черновики;
их публикует оркестратор. Деплой Google-login backend и OAuth пока требуют владельца.

Владельцу: ключ/подпись и Play App Signing, Console и проверка контактов, реальный телефон,
скриншоты, видео деклараций, проверка фоновой записи/карт/синка/удаления, приватный список
тестировщиков и сам 14-дневный тест. Носители языков должны проверить редактуру переводов
перед публичным выпуском. Серверные сроки хранения отражают опубликованную политику;
запущенные на сервере задания хранения этим клиентским аудитом не подтверждены.
`docs/port-status.md` не менялся; другие репозитории не менялись.
