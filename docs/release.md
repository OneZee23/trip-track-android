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

Keystore и `keystore.properties` хранятся вне checkout, например в `~/.config/triptrack/`.
Свойства: `storeFile` (абсолютный путь), `storePassword`, `keyAlias`, `keyPassword`.
Пароли вводятся локально, не попадают в команды, логи, отчёты или CI. Владелец хранит
резервную копию ключа и паролей отдельно. `.gitignore` дополнительно исключает эти файлы.

Сейчас Gradle не подключает release signing и не читает `keystore.properties`.
Для подписанного кандидата владелец использует Android Studio: **Generate Signed Bundle / APK →
Android App Bundle**, выбирает внешний keystore и alias из внешних свойств, вариант `release`.
Обычный `:app:bundleRelease` пока создаёт неподписанный AAB; в Play его не загружают.
Автоматизацию подписи добавляй отдельной задачей с чтением внешнего файла/секретов, без
паролей в публичном workflow. Debug-сборка CI подписи выпуска не требует.

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
