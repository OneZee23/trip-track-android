# w6-release

## Изменения

- Sentry Android 8.62.0 и NDK integration в `app`; версия и aliases добавлены в каталог, потому что crash reporting раньше отсутствовал. Replay dependency исключена. Другие module build files и `gradle.properties` не менялись.
- `Services/SentryService.swift`, `Common/PIISensitiveKeys.swift`, `Common/PIIScrubber.swift` из iOS 0.9.3: ручной запуск до app services, release name/environment, 10% traces, profiling off, запрет PII и снимков, UUID аккаунта на scope с очисткой при выходе. Исходные ключи и URL-правила уже жили в `core:network`; Sentry использует их. Повторная очистка `<id>` и Kotlin Array добавлены для SDK outbox.
- Тесты `PIIScrubberTests.swift`, `SentryDSNWiringTests.swift`, фикстуры `APILoggerRedactionTests.swift`: чувствительные значения, вложенные массивы, prefixed keys, typed SDK contexts, запросы/заголовки, breadcrumbs, native outbox и transaction spans. Wiring проверяется в скомпилированном BuildConfig и в DEX минифицированного APK, с пустым и заданным DSN.
- Release signing читает четыре `TRIPTRACK_UPLOAD_*` свойства/переменные. Без них APK/AAB unsigned; неполный набор выдаёт ошибку без значений. DSN читается из Gradle property/environment, debug всегда пустой. Секретов в checkout нет.
- R8 и resource shrinking включены; keep rules для Room, сериализаторов, MapLibre/Sentry consumer rules, WorkManager, Credential Manager и fog JNI. `-PTRIPTRACK_MINIFY=false` отключает оба shrinker на время диагностики.
- Генерируется UUID mapping в assets, native symbols `FULL`. Ручные команды `sentry-cli`, keytool, Play App Signing и команды сборки описаны в `docs/release.md`. Data safety и обе версии Android privacy copy переключены на Sentry.
- `tools/release/check_16kb.sh` проверяет ZIP alignment и каждый LOAD-сегмент каждого `.so`, включая 32-битные ABI: ELF64 требует 16 КБ, ELF32 4 КБ. MapLibre 13.7.1 содержит два 32-битных ELF с 4 КБ; поддержка этих телефонов сохранена. CI собирает release APK/AAB, проверяет DSN и запускает скрипт.
- MainActivity просит portrait как iPhone `Info.plist`; Android 16 на больших экранах сохраняет право вращать окно.

## Проверки

JDK 17 для всех Gradle-команд:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

| Команда | Результат |
|---|---|
| `./gradlew --no-daemon :app:compileReleaseKotlin` | PASS; загрузка новой зависимости Sentry. Один последующий offline build потребовал ещё четыре runtime-артефакта Sentry, загруженных повторным вызовом без offline |
| `./gradlew --no-daemon --offline -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" :app:testDebugUnitTest --tests 'com.onezee.triptrack.diagnostics.*' --tests 'com.onezee.triptrack.ApplicationIsolationTests' :core:network:testDebugUnitTest` | PASS: 14 app tests и 89 network tests; в том числе 19 shared scrubber cases |
| `./gradlew --no-daemon --offline :app:assembleRelease :app:bundleRelease :app:verifyReleaseSentryWiring` | PASS: минифицированные APK/AAB, resource shrinking, DEX DSN пуст и совпадает с входом; release lintVital прошёл |
| `./gradlew --no-daemon --offline -PTRIPTRACK_SENTRY_DSN=https://property@example.invalid/1 -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" :app:assembleRelease :app:bundleRelease :app:verifyReleaseSentryWiring :app:testDebugUnitTest --tests 'com.onezee.triptrack.diagnostics.*'` | PASS с временным upload keystore вне checkout и environment DSN `https://environment@example.invalid/2`: DEX содержит Gradle property, debug пуст; 11 tests на тот момент. Ключ и случайные пароли удалены. После этого добавлен fail-closed callback test и повторены тесты/обычная unsigned сборка |
| `$ANDROID_HOME/build-tools/36.0.0/apksigner verify app/build/outputs/apk/release/app-release.apk` | PASS на fixture-кандидате |
| `$JAVA_HOME/bin/jarsigner -verify app/build/outputs/bundle/release/app-release.aab` | PASS: `jar verified`; предупреждения о временном self-signed сертификате ожидаемы. Наличие `META-INF/*.RSA` также проверено |
| `tools/release/check_16kb.sh app/build/outputs/apk/release/app-release-unsigned.apk` | PASS: ZIP и все 20 ELF в четырёх ABI. ELF64 требует 16 КБ; ELF32 4 КБ; два MapLibre ELF32 имеют 4 КБ |
| `python3 -m unittest discover -s tools/release -p 'test_*.py' -v` | PASS: 7 tests, включая неправильное выравнивание, несовпадение смещений, отсутствие LOAD, ZIP failure, пустой/отсутствующий DSN, mismatch и нераскрытые placeholders |
| `python3 -m unittest discover -s tools/play -p 'test_*.py' -v` | PASS: 9 tests |
| `python3 tools/play/check_listing.py` | PASS: 13 языков, лимиты 30/80/4000 |
| `bash -n tools/release/check_16kb.sh` и `git diff --check` | PASS |
| `git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'` | Совпадений нет |

Дополнительно: assets `sentry-debug-meta.properties` совпадают в APK/AAB. Для каждого fog ABI
`llvm-readelf --notes` подтвердил одинаковый build ID у APK `.so` и `.dbg` из
`native-debug-symbols.zip`; `llvm-readelf --sections` нашёл `.debug_info` и `.debug_line`.
В R8 mapping сохранены исходные имена `NativeFog`, `SyncWorker`, `TripTrackDatabase_Impl`
и `SentryNdk`. Эти проверки выполнялись локальным Python через `zipfile`/`subprocess`, без устройства.

Промежуточный прогон Robolectric без `robolectric.dependency.dir` упал на sandbox-запрете
`~/.robolectric-download-lock`; локальный runtime устранил причину. Попытка вызвать
`:app:testReleaseUnitTest` остановилась до сборки: AGP этого проекта создаёт только debug
unit-test task. Release wiring поэтому проверяется прямо в DEX APK. Первый строгий ELF-прогон
нашёл 4 КБ у двух ELF32 MapLibre; ABI-aware проверка теперь учитывает поддерживаемые страницы.
Эти неудачные прогоны не объявлены зелёными проверками.

Финальный повтор после изменения fail-closed callbacks:

```sh
./gradlew --no-daemon --offline :app:assembleRelease :app:bundleRelease :app:verifyReleaseSentryWiring :app:lintRelease :core:network:lintDebug
```

PASS за 3m 11s. `app:lintRelease`: 0 ошибок, 74 warnings и 2 hints; `core:network:lintDebug`
без замечаний. Среди warnings есть ожидаемые `LockedOrientationActivity`/`DiscouragedApi`
для требуемого portrait и `LogNotTimber` для единственной строки об отключённом Sentry.
После этой сборки повторены проверка 20 ELF, сравнение UUID assets APK/AAB и проверка
отсутствия подписи AAB. На выходе `app/build/outputs/bundle/release/app-release.aab`
и `app/build/outputs/apk/release/app-release-unsigned.apk`, без fixture DSN и без временной
подписи. Gradle-тесты финального кода: 14 app + 89 network, без failures/skips.

Временный ключ существовал только в системном temporary directory. Его создание проверялось
командой `$JAVA_HOME/bin/keytool -genkeypair -noprompt -storetype JKS -keystore "$TRIPTRACK_UPLOAD_STORE_FILE" -alias upload -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=Release fixture' -storepass:env TRIPTRACK_UPLOAD_STORE_PASSWORD -keypass:env TRIPTRACK_UPLOAD_KEY_PASSWORD`.
Для настоящего ключа используется команда с 10000 днями из `docs/release.md`; fixture не
предназначен для Play. Реальные Sentry uploads и проверка доставки событий не выполнялись.

## Ручная проверка оркестратора

Эмулятор и телефон в задаче не запускались. Проверить подписанный минифицированный release,
установленный через Play internal testing, затем тот же AAB продвинуть в closed test:

1. Чистая установка и обновление поверх прошлого release: запуск, онбординг, локальная Room-библиотека, перезапуск. Ни один экран не должен падать из-за удалённого класса/ресурса.
2. Гость: все пять вкладок, дневник, карточка поездки, detail, journey и drafts; пустые и заполненные состояния. Повернуть обычный телефон на каждой вкладке: portrait. На планшете/раскрытом foldable от 600 dp проверить поворот, split screen и возврат без потери данных.
3. Google sign-in: выбор аккаунта, отмена, выход, вход повторно, холодный запуск с сохранённой сессией, soft expiry и повторная авторизация. Play app-signing certificate должен быть зарегистрирован для Google login.
4. Cloud Sync: включить с подтверждением, загрузить поездку с треком/фото/отметками/сегментами, journey, машину и настройки; скачать на втором Android того же аккаунта. Выключение синка, offline/retry и очистка серверных данных должны сохранить ожидаемые границы приватности.
5. WorkManager: поставить синк в очередь, закрыть процесс, восстановить сеть и проверить доставку после нового запуска/фонового wake. Повторить после обновления приложения, чтобы сохранённое имя worker нашлось.
6. Запись: точная/приблизительная геолокация, отказ, переход к фоновой геолокации в Settings, уведомления; старт, pause/resume, lock screen/background, notification actions, finish/save, восстановление после смерти процесса. Проверить автоматический старт по Bluetooth/Android Auto и завершение сервиса.
7. Atlas, Places и все fullscreen/detail карты: tiles, fog, reveal после поездки, pan/zoom, выбор объектов, light/dark, background/resume и memory trim; JNI fog не должен дать UnsatisfiedLinkError или native crash. Проверить подписи регионов, attribution и права после слияния работы map-агента.
8. Фото: импорт в поездку/профиль/машину, просмотр, удаление; GPX/CSV и share poster, карта snapshot, выдача URI через share sheet. Проверить отсутствие потерянных drawables/strings после resource shrinking.
9. Социальные экраны после слияния: feed pagination, foreign profile/vehicle/trip/journey, поиск, follows, reactions/comments, publish/unpublish, report/block/unblock; JSON DTO и enum raw values не должны меняться после R8.
10. Настройки: все 13 языков без загрузки дополнительных splits, тема, единицы, приватная зона и её обрезка, стерео, optional usage counters; перезапустить приложение и проверить сохранение выбора.
11. Аккаунт: Clear server data, выход с/без скрытия публикаций, удаление аккаунта. После успеха нет старого account UUID на Sentry scope; при сетевом отказе локальная библиотека остаётся.
12. Sentry с реальным release DSN: JVM crash, C++ fog/native crash и ANR на тестовых данных; холодный запуск отправляет native outbox. После ручной загрузки mapping/symbols стек читается до строк Kotlin/C++. Проверить сессию гостя, UUID после входа/его очистку после выхода, отсутствие PII, URL ids/cursors, тел, screenshots/replay/profiles. Отдельно сборка с пустым DSN даёт одну строку и не отправляет события.
13. Устройство с 16 КБ страницами: запуск, Atlas/fog, snapshot/share, background/resume и запись. Статическая ZIP/ELF проверка не заменяет проверку native кода на таком устройстве.

## Осталось владельцу

Реальные upload key/пароли и DSN не предоставлялись. Подписать финальный кандидат, подтвердить
Sentry region/retention/processor agreement, вручную загрузить символы, проверить события на
устройстве и опубликовать обе privacy-policy версии. Затем загрузить AAB в Play, пройти
перечисленные сценарии и оформить closed test. Внешние репозитории, Play и Sentry не менялись.
`version.properties` остаётся `0.9.2-dev`/code 1: оркестратор выбирает версию/код итогового
кандидата после слияния портов iOS 0.9.3 и проверок парности.

## Отличия от iOS

Все шесть решений добавлены в `docs/divergences.md`: Gradle/BuildConfig и ELF/R8 вместо
Info.plist/dSYM; ELF64 16 КБ и ELF32 4 КБ по поддерживаемым страницам Android; полный отказ от автоматических HTTP breadcrumbs/failed-request capture;
Android typed context scrub и отсутствие raw ANR/tombstone attachments; portrait с системным
исключением для больших экранов Android 16. Android также отбрасывает HTTP/file spans вместо
редактирования их immutable description. Тела запросов и лишние поля User удаляются защитно. Ошибка scrubber отбрасывает payload без его логирования. ANR следует Sentry Android: watchdog 5 с на Android 10, OS ApplicationExitInfo на Android 11+; это отличается от 2 с Cocoa App Hanging и записано как платформенное решение.
