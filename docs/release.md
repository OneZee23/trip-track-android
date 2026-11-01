# Выпуск Android

## Версия и парность

Единственный источник `versionName` и `versionCode` приложения: [version.properties](../version.properties).
`app/build.gradle.kts` читает оба значения оттуда. Не дублируй их в Gradle, CI или release-скриптах.

Релизный `versionName` равен версии iOS, с которой проверена парность: например, `0.9.2`.
Переход на следующую iOS-версию требует перенести её изменения и тесты или записать
обоснованные платформенные отличия в [divergences.md](divergences.md). Выпуск iOS сам по себе
не повышает Android-версию. Пока порт не закончен, используем `0.9.2-dev`: это целевая линия
из iOS `project.yml`, не обещание готовности к production.

`versionCode` независим от iOS build number. Это положительное целое, которое увеличивается
при каждой новой загрузке AAB в любой Play track, в том числе после неудачного кандидата.
Перед сборкой проверь максимальный номер в Play Console. При продвижении того же AAB между
tracks номер сохраняется. Android-исправление в пределах той же линии может сохранить
`versionName` и увеличить `versionCode`; общее исправление также уходит в iOS.
См. [правила версий Android](https://developer.android.com/studio/publish/versioning).

Android-теги имеют вид `android-v0.9.2-code1`. Они не совпадают с iOS `v0.9.2`, даже если
истории когда-нибудь окажутся рядом. Тег указывает на commit исходников загруженного AAB.
Не переставляй опубликованный тег на последнюю правку ветки.

## Подпись

Включи Play App Signing. Google хранит app signing key и подписывает APK, выдаваемые
пользователям. Владелец хранит отдельный upload key и подписывает им AAB для загрузки.
Потерянный upload key можно заменить через Play Console. См.
[подпись Android](https://developer.android.com/studio/publish/app-signing) и
[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en).

Создай отдельный upload key вне checkout. Команда спросит пароли интерактивно:

```sh
mkdir -p "$HOME/.config/triptrack"
keytool -genkeypair -v -keystore "$HOME/.config/triptrack/upload.jks" \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000
chmod 600 "$HOME/.config/triptrack/upload.jks"
```

В Play Console при первом выпуске открой настройку App signing, включи Play App Signing
и выбери ключ подписи приложения, создаваемый Google. Зарегистрируй сертификат upload key
для загрузок. Если нужен PEM сертификата:

```sh
keytool -exportcert -rfc -keystore "$HOME/.config/triptrack/upload.jks" \
  -alias upload -file "$HOME/.config/triptrack/upload-certificate.pem"
```

Keystore хранится в закрытом каталоге владельца, резервная копия отдельно. Пароли находятся
в менеджере паролей. Перед сборкой передай значения через защищённое окружение или свойства
в `~/.gradle/gradle.properties` (доступ `600`). Gradle property имеет приоритет над одноимённой
переменной окружения. `local.properties` и `keystore.properties` для release-секретов не читаются.

| Gradle property / environment | Значение |
|---|---|
| `TRIPTRACK_UPLOAD_STORE_FILE` | Абсолютный путь к внешнему keystore |
| `TRIPTRACK_UPLOAD_STORE_PASSWORD` | Пароль хранилища |
| `TRIPTRACK_UPLOAD_KEY_ALIAS` | `upload` |
| `TRIPTRACK_UPLOAD_KEY_PASSWORD` | Пароль ключа |
| `TRIPTRACK_SENTRY_DSN` | DSN Android-проекта Sentry; только release |

Все четыре значения подписи обязательны вместе. Без них release APK/AAB остаются
неподписанными, чтобы CI мог собирать. Частичная конфигурация останавливает сборку с именами
нужных переменных, без их значений. Пароли и DSN не передавай в командной строке, не сохраняй
в checkout или логах. DSN клиента доступен внутри APK по назначению SDK; секрет загрузки
символов `SENTRY_AUTH_TOKEN` в приложение не входит.

## Сборка release

Из того же checkout с настроенными внешними секретами:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --no-daemon --offline :core:network:testDebugUnitTest
./gradlew --no-daemon --offline :app:testDebugUnitTest --tests 'com.onezee.triptrack.diagnostics.*'
./gradlew --no-daemon --offline :app:assembleRelease :app:bundleRelease :app:verifyReleaseSentryWiring
tools/release/check_16kb.sh app/build/outputs/apk/release/app-release.apk
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

`JAVA_HOME` выше для машины владельца; CI использует JDK 17 из setup-java. Новая зависимость
Sentry требует один раз загрузить артефакты тем же Gradle-вызовом без `--offline`.
Результаты: `app/build/outputs/bundle/release/app-release.aab` и подписанный
`app/build/outputs/apk/release/app-release.apk`. Без ключей APK называется
`app-release-unsigned.apk`; проверку 16 КБ запусти с этим именем. Такой AAB нельзя загружать в Play.
Проверка `verifyReleaseSentryWiring` читает поле из DEX готового APK через SDK `apkanalyzer`,
сверяет с Gradle-входом и не печатает DSN. Пустое поле допустимо и проверяется; на устройстве
оно даёт одну строку `Sentry disabled: TRIPTRACK_SENTRY_DSN is empty` и SDK не запускается.
Debug всегда получает пустое поле, даже с release-DSN в окружении.

R8 и shrink ресурсов включены вместе. Аварийный переключатель на время проверки устройства:

```sh
./gradlew --no-daemon --offline -PTRIPTRACK_MINIFY=false :app:assembleRelease :app:bundleRelease
```

После устранения ошибки убери переключатель и повтори весь проход по минифицированной сборке.
Keep rules зависимостей защищают Room, сериализаторы, MapLibre и Sentry; app сохраняет
JNI fog, стабильные имена WorkManager workers, Credential Manager providers и поле DSN.

## Символы Sentry

Символы загружает владелец вручную через `sentry-cli`, как dSYM на iPhone. Gradle не отправляет
их и не подключает Sentry instrumentation plugin. Для каждого кандидата сохрани вместе APK,
AAB, `app/build/outputs/mapping/release/mapping.txt`,
`app/build/generated/sentry/release/assets/sentry-debug-meta.properties` и
`app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip`.
Метаданные есть в assets APK/AAB; SDK добавляет их UUID к событию для выбора R8 mapping.
Каждый запуск сборки генерирует новый UUID. Не пересобирай только один из сохранённых
артефактов кандидата перед загрузкой символов.

Установи `sentry-cli`. Получи в Sentry токен с правом загрузки debug files, положи его
в менеджер паролей и передай через `SENTRY_AUTH_TOKEN` вместе с `SENTRY_ORG`/`SENTRY_PROJECT`.
Для self-hosted Sentry укажи `SENTRY_URL`. Загрузки для кандидата:

```sh
mapping_uuid=$(sed -n 's/^io.sentry.ProguardUuids=//p' app/build/generated/sentry/release/assets/sentry-debug-meta.properties)
sentry-cli upload-proguard --uuid "$mapping_uuid" app/build/outputs/mapping/release/mapping.txt
sentry-cli debug-files upload app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip
sentry-cli debug-files upload core/map/build/intermediates/cxx/RelWithDebInfo
```

Последняя команда также находит unstripped `libtriptrackfog.so` с DWARF. Проверь UUID/debug IDs
загруженных файлов в Sentry. Нативные библиотеки сторонних SDK могут содержать только публичные
символы; собственный fog должен иметь отладочные данные. Без minify R8 mapping не загружается.
Release события имеют имя `triptrack-android@<versionName>+<versionCode>`, environment `release`.

Перед Play владелец на тестовом устройстве проверяет доставку JVM crash, native crash и ANR,
появление читаемых строк Java/Kotlin и C++ после загрузки символов, данные гостевой сессии
и UUID после входа/его очистку после выхода. В payload не должно быть токенов, почты, координат
дома, presigned URLs или URL с идентификаторами/курсорами. Эмулятор в этой задаче не запускался.
Реальный DSN, регион проекта, срок хранения и договор обработки подтверждает владелец;
затем публикует обе версии [privacy policy](play/privacy-policy-android.md) и
[Data safety](play/data-safety.md). Диагностика независима от добровольных usage counters. ANR использует механизм Sentry Android:
5-секундный watchdog на Android 10, системный ApplicationExitInfo после перезапуска на Android 11+.
Двухсекундные App Hanging Cocoa он не повторяет; см. [отличия](divergences.md).

Проверка 16 КБ использует `zipalign -c -P 16 4` и NDK `llvm-readelf --program-headers --wide`
для каждого `.so`, включая сторонние. Она проверяет LOAD alignment и согласованность смещений.
Для ELF64 требуется 16 КБ, для ELF32 4 КБ: устройства Android с 16 КБ используют 64-битные
процессы. В MapLibre 13.7.1 обе 32-битные библиотеки имеют alignment 4 КБ; обе 64-битные
имеют 16 КБ. Скрипт читает ELF header и проверяет все ABI, поддержка 32-битных телефонов сохранена.
CI запускает её на release APK после R8; APK и AAB собираются одним вызовом Gradle.
См. [проверку 16 КБ](https://developer.android.com/guide/practices/page-sizes) и
[ручную загрузку mapping](https://docs.sentry.io/platforms/android/enhance-errors/proguard/).

MainActivity просит portrait, как `UISupportedInterfaceOrientations` iPhone. На Android 16
при target 36 система игнорирует ограничение на экранах от 600 dp; приложение не обходит
это правило. См. [Android 16](https://developer.android.com/about/versions/16/behavior-changes-16#adaptive-layouts).

Для Google sign-in в Play-сборке нужен сертификат app signing из Play Console, а для локальной
debug-сборки свой сертификат. Upload certificate и app signing certificate выполняют разные
роли; настрой их согласно требованиям выбранного Google login SDK.

## Play tracks

1. **Internal testing.** Загрузи подписанный AAB, установи через Play и проверь вход, запись
   в фоне, восстановление поездки, синк, обновление поверх предыдущего кандидата. Debug APK
   с суффиксом `.dev` не заменяет проверку release-пакета `com.onezee.triptrack`.
2. **Closed testing.** Этот личный аккаунт требует минимум 12 тестировщиков, непрерывно
   участвующих в тесте не менее 14 дней. Собери обратную связь и исправления, затем запроси
   production access в Play Console. Internal testing не заменяет этот этап; истечение 14 дней
   само по себе не открывает production. См.
   [требования Google](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
3. **Production.** После получения доступа продвинь проверенный AAB, пройди ревью и проверь
   фактическую доступность в магазине. Запиши track и статус; загрузка ещё не означает выпуск.

## Чеклист кандидата

- Сверь iOS-тег и commit эталона, выполни `parity.py changed <предыдущий-ios-тег>..<целевой-ios-тег>`.
  Проверь перенос кода и тестов, [api-contract.md](api-contract.md), [divergences.md](divergences.md),
  сгенерируй [parity.md](parity.md). Закрой предупреждения в затронутой области.
- Перегенерируй строки и проверь `tools/strings/generate.py --check`. Запусти оба Python-набора
  из [how-to-port-a-change.md](how-to-port-a-change.md). С JDK 17 выполни
  `./gradlew --no-daemon --offline test :app:assembleDebug`; после этого собери подписанный
  release AAB из того же commit. Для новой зависимости сначала разрешена загрузка без `--offline`.
- Проверь release: пакет без `.dev`, подпись upload key, versionName/code, боевой API,
  отсутствие dev/seeding-флагов, схема Room и миграции, восстановление после смерти процесса,
  фоновые разрешения и уведомление записи. При наличии crash reporting проверь сбор символов
  и mapping. Сохрани результаты реальных проверок, не только успешную сборку.
- Зафиксируй публичный manifest кандидата в `docs/releases/<version>/`: Android source commit,
  iOS tag/commit, versionName/code, SHA-256 AAB, команды и результаты тестов, track и статус
  загрузки. Проверку подписи запиши без секретов. Следующие правки требуют нового кандидата.
- В Play Console проверь listing и скриншоты, Data safety, privacy policy, удаление аккаунта,
  возрастной рейтинг, доступ для ревью, декларацию фоновой геолокации и демонстрационное видео.
  Открытые пункты из [port-plan.md](port-plan.md) закрываются до production.

## Тексты и публикация

Тексты магазина берём из iOS `docs/releases/<version>/app-store.md`, `CHANGELOG.md` и
`docs/store/`. Если у версии нет отдельного `app-store.md`, берём предыдущий актуальный
listing и изменения из changelog. Android-вариант сохраняем в `docs/releases/<version>/play-store.md`.
Он описывает реально выпущенные функции: Google-вход, отсутствие PRO-покупок, уведомление
вместо Live Activity. Убери обещания об Apple-входе и переносе аккаунта между платформами;
учти остальные [отличия](divergences.md). Проверь лимиты полей Play Console и используй
Android-скриншоты. Внутри приложения слова по-прежнему приходят из `tools/strings`.

После выхода production оркестратор в тот же день вливает выпущенную ветку в `master`,
создаёт тег `android-v<versionName>-code<versionCode>` на commit кандидата и GitHub Release
с изменениями, связью с iOS-версией и ссылкой на Play. Это повторяет правило iOS
`CLAUDE.md` и `scripts/github-release.sh`: GitHub-релиз привязан к исходникам магазинной
сборки и выходит в день публикации. iOS-скрипт здесь не запускается, он создаёт iOS-теги.
Даты выхода iOS и Android могут различаться из-за проверки магазинов и закрытого теста.
Публикацию и push выполняет оркестратор или владелец.

## App Links

Сайт должен отдавать JSON по HTTPS без авторизации и редиректов, с HTTP 200 и
`Content-Type: application/json`, на обоих хостах:

- `https://trip-track.app/.well-known/assetlinks.json`
- `https://www.trip-track.app/.well-known/assetlinks.json`

Оба хоста объявлены в manifest. Android 10/11 требуют подтверждения всех хостов.
См. [ассоциации Android](https://developer.android.com/training/app-links/configure-assetlinks).

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.onezee.triptrack",
      "sha256_cert_fingerprints": ["PLAY_APP_SIGNING_CERTIFICATE_SHA256"]
    }
  }
]
```

Владелец заменяет placeholder на SHA-256 из Play Console, App signing / App signing key
certificate. Нужен сертификат, которым Play подписывает установленный APK. Upload key
для этого файла не подходит. Отпечатка нет в исходниках; выдумывать его нельзя.
При ротации ключа перечисли все сертификаты установок, которые ещё поддерживаются.

Debug-пакет: `com.onezee.triptrack.dev`. Для него сайт может добавить второй объект
с этим `package_name` и SHA-256 конкретного debug-сертификата. Публиковать такую
ассоциацию для всех локальных ключей не требуется. Проверяй release и debug отдельно,
чтобы установленные одновременно варианты не конкурировали за ссылки.

Manifest принимает `/u/<account UUID>`, `/s/<share code>`, `/j/<share code>`.
UUID поездки и share code различаются. Для `/s/` и `/j/` приложение читает HTML сайта,
находит `triptrack://trip/<UUID>` или `triptrack://journey/<UUID>` и запрашивает доступ
у API. Сайт должен сохранять эту ссылку в HTML. Истёкший код и отказ API показывают
ошибку. Сайт в этой задаче не меняется.

На устройстве с Android 12+ и установленной Play-сборкой:

```sh
adb shell pm set-app-links --package com.onezee.triptrack 0 all
adb shell pm verify-app-links --re-verify com.onezee.triptrack
# Дай системному проверяющему несколько минут.
adb shell pm get-app-links --user cur com.onezee.triptrack
```

Оба хоста должны получить `verified`. Ручное `approved` не проверяет файл.
См. [проверку App Links](https://developer.android.com/training/app-links/verify-applinks).

Подставь действующие UUID и share codes публичного тестового аккаунта:

```sh
adb shell am force-stop com.onezee.triptrack
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://trip-track.app/u/ACCOUNT_UUID'
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://trip-track.app/s/TRIP_SHARE_CODE'
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://trip-track.app/j/JOURNEY_SHARE_CODE'
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'triptrack://profile/ACCOUNT_UUID'
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'triptrack://trip/TRIP_UUID'
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'triptrack://journey/JOURNEY_UUID'
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'triptrack://recording'
```

Повтори HTTPS-команды для `www.trip-track.app`, на холодном старте и при открытом
профиле/поездке. Проверь Back, гостя, закрытый профиль, удалённую поездку и истёкший
код. Не добавляй `-n` или `-p` к HTTPS-командам: явный выбор пакета обходит системный
выбор приложения. Для debug замени пакет в командах сброса и проверки на
`com.onezee.triptrack.dev`.
