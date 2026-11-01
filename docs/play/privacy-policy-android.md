# Android privacy policy additions (draft)

Publication target: the existing English `docs/privacy-policy.html` and Russian
`docs/privacy-policy-ru.html` in the iPhone website repository. That repository is read only
for this task. These paragraphs apply to Android and take precedence over iOS-only references
in the shared policy. The orchestrator publishes them and updates the policy date.

Use the Sentry diagnostics copy below for the configured release. Before publication, the owner
confirms the Sentry service-provider agreement, project region and retention in the Sentry account.
The checkout does not establish those account settings. Also correct the old shared-policy
statement about local trips surviving account deletion: both clients wipe them by default.

## English publication copy

### TripTrack on Android

This policy also covers TripTrack for Android. The purposes, rights, security, international
processing and retention terms in the policy apply to Android, with the platform details below.
You can record and keep your diary without creating an account. Cloud Sync is off by default.
Android stores the diary in the app's local database rather than iOS Core Data.

### Google sign-in

Android uses Sign in with Google. When you choose it, the app receives a Google ID token and,
when supplied, your display name and email address. Our server verifies the token and uses its
Google account identifier to create or recognise your TripTrack account. The app also sends a
random installation identifier, a one-time verification value, Android platform/app version and,
when configured, a device name to distinguish sessions. We never receive your Google password,
mailbox, contacts or calendar. The app does not import a Google profile photo into your diary.
Credentials are encrypted using a key held in Android Keystore and kept outside Android backup
and device transfer.

Signing in is optional and does not turn on private-library synchronisation. Android Google
accounts and iPhone Apple accounts are currently separate; matching email addresses do not
link them. Account authentication and the profile features you use are processed to provide the
service, under the same legal basis as their iOS equivalents in this policy.

### Location and automatic recording

TripTrack collects precise location to record your driving route, distance, speed and elevation.
If you enable automatic recording and grant background access, this includes starting and
recording a trip when the app is closed or not in use. You can pause or finish a recording,
disable auto-record, or revoke permissions in Android Settings. Android's Activity Recognition
permission supplies movement/vehicle transitions to help detect driving. These signals are
processed on the phone; we do not upload activity-recognition histories or use them to measure
health or fitness.

With Nearby devices permission on supported Android versions, TripTrack can read a linked car
stereo's name and connection state. Its saved name and local device identifier, and Android Auto
connection information used as a stereo trigger, stay in the app's local settings. TripTrack
does not upload these connection records in Cloud Sync and does not access the microphone,
record audio or scan your address book for this feature. You choose the available automatic
recording mode and can unlink a stereo or turn the feature off.

While a trip is being recorded, Android runs a location foreground service. Its ongoing
notification shows recording state and live trip numbers and provides Pause, Resume and Finish.
The notification is displayed by Android on your device; it is not an additional transmission
to our server. Finishing a trip ends that recording service.

### Maps, place names and uploads

Android maps use MapLibre with OpenFreeMap's online tile service. Loading a map sends tile
coordinates and zoom level, your network address and normal HTTP/device metadata to that
provider and its delivery infrastructure. The requested area can correspond to your trip or
home, even in guest mode with Cloud Sync off. It is not a full route upload to TripTrack.
OpenFreeMap's policy describes its own logging: normal access logs omit IP addresses, error
logs may contain an IP address and URL for seven days, and security-incident IP logs may remain
for up to 30 days. See [OpenFreeMap's privacy policy](https://openfreemap.org/privacy/).
Place names are obtained through Android's system Geocoder; the service provider depends on
the phone and may receive the coordinates needed to answer the request.

Private trips and their photos/vehicle data upload after you sign in and enable Cloud Sync.
A trip you choose to publish and its photos upload even with Cloud Sync off. Profile information
and photos you add to your profile are account features independent of that switch. Turning
Cloud Sync off stops future private-library uploads; it does not delete previously uploaded
copies. Signing out does not itself delete the account or its stored content.

If you configure a home privacy zone and enable trimming, TripTrack removes route and preview
points inside it from outgoing trip payloads and omits photo/checkpoint coordinates inside it.
The original local route remains complete. Image files are re-encoded without embedded GPS or
other EXIF metadata; separate photo time/location fields follow the trimming rules. A zone does
not hide text, visible details in a photo, trip statistics, map tile requests or geocoder queries.

Android system backup may copy database/preferences according to your phone's backup settings.
TripTrack's connection records are not sent to our API, but we cannot describe all local
preferences as excluded from the operating system's backup. Credentials and photo files stored
in the app's no-backup directory are excluded. Maps need a connection to load; recording can
continue offline.

### Optional usage counters and diagnostics

The optional “Help improve TripTrack” counters described in this policy also apply to Android.
They are off by default and independent of sign-in and Cloud Sync. A random code for each consent
period groups the counters and is stored as a hash on the server. Disabling the option clears
local counters and queues server erasure; inactive records and deletion markers expire after
90 days. Uninstalling alone cannot send an erasure request.

Release builds configured for diagnostics send JVM/native crash and app-hang reports, session
start/stop records and a 10% sample of lifecycle performance traces to Sentry, our diagnostics
processor. This also applies in guest mode and independently of Cloud Sync and optional usage
counters. Reports contain the signed-in account UUID, a random SDK installation identifier,
device/OS/app information and scrubbed technical context. Default personal-information and IP
enrichment are disabled. Sensitive field values, including names, email, tokens, home coordinates
and photo URLs, are redacted. Request bodies/cookies, credential headers, URL identifiers and
query strings are removed. Network breadcrumbs, failed-request capture and HTTP/file tracing
are disabled. We do not attach screenshots, view hierarchies, session replays, user files or
performance profiles. Native stack frames remain part of crash diagnostics.

Sentry processes reports in the region configured for our project and removes them when our
configured retention period expires. The purpose is finding and fixing defects, under the
legitimate-interest basis described in this policy. To object, contact privacy@trip-track.app.
Builds without a diagnostics DSN, including debug builds, do not start Sentry. Support logs are
shared separately only when you choose to export/send them.

### Retention and deletion on Android

The existing retention periods remain: account and synced content for the life of the account;
possible deletion after three years of inactivity with prior email notice; individual deleted
trips, photos and vehicles physically erased within 30 days; database backups retained no more
than 30 days; server access logs up to 14 days; abuse/moderation records up to two years; and a
non-identifying timestamp and opaque account-deletion audit hash that may be kept indefinitely.
Optional usage records and deletion markers expire after 90 days of inactivity. Refresh tokens
are rotated on use and revoked on sign-out or account deletion. There are no Android purchases
in this launch build.

In Android, open Me → Settings → Account & Sync → Delete account and confirm. The app first
asks the server to delete your account and associated live data immediately. After success it
also erases this phone's local library and account state. If the server request fails, local
data is retained so you can retry. Use “Clear server data” to remove uploaded library content
while keeping the account; it first disables Cloud Sync. Deleting the app alone does not delete
your account. You can also request account deletion or a copy of your data at
privacy@trip-track.app; we verify ownership and respond within 30 days. The web deletion-request
page linked from the store provides this route without reinstalling the app.

## Русский текст для публикации

### TripTrack на Android

Эта Политика также распространяется на TripTrack для Android. К нему применяются указанные
в Политике цели обработки, права, условия безопасности, трансграничной передачи и хранения
с платформенными уточнениями ниже. Записывать поездки и вести дневник можно без аккаунта.
Синхронизация в облаке по умолчанию выключена. Android хранит дневник в локальной базе
приложения вместо iOS Core Data.

### Вход через Google

Android использует вход через Google. Когда Вы выбираете его, приложение получает токен
Google ID и, если они предоставлены, Ваше имя и адрес электронной почты. Наш сервер проверяет
токен и использует идентификатор аккаунта Google, чтобы создать или узнать Ваш аккаунт
TripTrack. Приложение также отправляет случайный идентификатор установки, одноразовое значение
для проверки, платформу Android и версию приложения, а при соответствующей настройке имя
устройства для различения сессий. Мы не получаем пароль Google, содержимое почты, контакты или
календарь. Приложение не переносит фотографию профиля Google в Ваш дневник. Данные авторизации
зашифрованы ключом из Android Keystore и исключены из резервного копирования и переноса устройств.

Вход необязателен и сам по себе не включает синхронизацию личной библиотеки. Аккаунты Android
через Google и iPhone через Apple пока отдельные; совпадение почты их не связывает. Для входа
и используемых Вами функций профиля действуют те же цели и правовые основания обработки,
которые Политика устанавливает для соответствующих функций iOS.

### Геолокация и автоматическая запись

TripTrack собирает данные о точном местоположении для записи маршрута, расстояния, скорости
и высоты поездки. Если Вы включили автозапись и разрешили фоновый доступ, это включает начало
и запись поездки, когда приложение закрыто или не используется. Запись можно приостановить
или завершить, автозапись отключить, а разрешения отозвать в настройках Android. Разрешение
«Физическая активность» даёт сигналы движения и нахождения в транспорте, помогающие определять
начало поездки. Они обрабатываются на телефоне; мы не отправляем историю распознавания
активности на сервер и не используем её для оценки здоровья или физической формы.

С разрешением «Устройства поблизости» на поддерживаемых версиях Android TripTrack может читать
название привязанной магнитолы и состояние подключения. Сохранённое название, локальный
идентификатор устройства и сведения о подключении Android Auto, используемом как магнитола,
остаются в настройках приложения на телефоне. TripTrack не отправляет эти записи подключений
при синхронизации в облаке. Для этой функции приложение не обращается к микрофону, не записывает
звук и не сканирует адресную книгу. Вы выбираете доступный режим автозаписи и можете отвязать
магнитолу или выключить функцию.

Во время записи Android запускает службу геолокации переднего плана. Постоянное уведомление
показывает состояние и текущие показатели поездки, позволяет поставить запись на паузу,
продолжить или завершить её. Уведомление показывает Android на Вашем устройстве; оно не является
дополнительной передачей на наш сервер. Завершение поездки останавливает эту службу записи.

### Карты, названия мест и загрузка данных

Карты Android используют MapLibre и онлайн-сервис тайлов OpenFreeMap. При загрузке карты
провайдер и его инфраструктура доставки получают координаты тайлов и масштаб, сетевой адрес
и обычные HTTP-данные об устройстве. Запрошенная область может соответствовать поездке или
дому даже в гостевом режиме с выключенной синхронизацией. Это не загрузка полного маршрута
на сервер TripTrack. Согласно политике OpenFreeMap обычные журналы доступа не содержат
IP-адресов, журналы ошибок могут хранить IP и URL семь дней, а журналы инцидентов безопасности
с IP-адресами до 30 дней. См. [политику OpenFreeMap](https://openfreemap.org/privacy/).
Названия мест запрашиваются через системный Geocoder Android; провайдер зависит от телефона
и может получать координаты, необходимые для ответа.

Приватные поездки, их фотографии и данные машин отправляются после входа и включения
синхронизации в облаке. Опубликованная Вами поездка и её фотографии отправляются и при
выключенной синхронизации. Данные профиля и добавленные в профиль фотографии относятся
к функциям аккаунта и не зависят от этого переключателя. Выключение синхронизации останавливает
новые загрузки личной библиотеки, но не удаляет уже загруженные копии. Выход сам по себе
не удаляет аккаунт или его содержимое на сервере.

Если Вы задали приватную зону дома и включили обрезку, TripTrack убирает точки трека и превью
внутри неё из отправляемых данных поездки и не отправляет координаты фотографий и отметок
внутри зоны. Локальный маршрут остаётся полным. Файлы изображений перекодируются без встроенных
GPS-данных и других EXIF-метаданных; отдельные поля времени и координат снимка подчиняются
правилам обрезки. Зона не скрывает текст, видимые детали снимка, статистику поездки, запросы
тайлов карты или запросы названий мест.

Системное резервное копирование Android может копировать базу и настройки в соответствии
с настройками телефона. TripTrack не отправляет записи подключений в свой API, но не все
локальные настройки исключены из резервного копирования операционной системы. Данные
авторизации и файлы фотографий в каталоге приложения без резервного копирования исключены.
Для загрузки карт нужен интернет; запись может продолжаться без него.

### Добровольные счётчики и диагностика

Описанные в Политике добровольные счётчики «Помогать улучшать TripTrack» применяются и к Android.
По умолчанию они выключены и не зависят от входа или синхронизации в облаке. Случайный код
каждого периода согласия объединяет счётчики; сервер хранит его хеш. Выключение очищает
локальные счётчики и ставит удаление серверных данных в очередь. Неактивные записи и отметки
удаления истекают через 90 дней. Простое удаление приложения не отправляет запрос на удаление.

Релизные сборки с настроенной диагностикой отправляют в Sentry, нашему обработчику диагностики,
отчёты о падениях JVM/нативного кода и зависаниях, записи начала и конца сессий и выборку 10%
трассировок производительности жизненного цикла приложения. Это действует и в гостевом режиме,
независимо от синхронизации в облаке и добровольных счётчиков. События содержат UUID аккаунта
после входа, случайный идентификатор установки SDK, данные устройства, ОС и приложения
и очищенный технический контекст. Автоматическое добавление персональных данных и IP отключено.
Значения чувствительных полей, включая имена, почту, токены, координаты дома и ссылки на фото,
маскируются. Тела запросов, cookies, заголовки авторизации, идентификаторы в URL и параметры
запросов удаляются. Сетевые breadcrumbs, захват ошибочных HTTP-ответов и трассировка HTTP
и файлов отключены. Снимки экрана, иерархии интерфейса, повторы сессий, пользовательские файлы
и профили производительности не прикладываются. Стеки нативного кода остаются в отчётах.

Sentry обрабатывает отчёты в регионе нашего проекта и удаляет по окончании настроенного
срока хранения. Цель обработки: находить и исправлять ошибки на основании законного интереса,
указанного в Политике. Для возражения напишите privacy@trip-track.app. Сборки без DSN диагностики,
включая debug, не запускают Sentry. Журналы поддержки передаются отдельно по Вашему действию
экспорта или отправки.

### Хранение и удаление на Android

Действуют прежние сроки: аккаунт и синхронизированные данные хранятся, пока аккаунт активен;
после трёх лет неактивности возможно удаление с предварительным уведомлением по почте;
отдельные удалённые поездки, фотографии и машины физически стираются в течение 30 дней;
резервные копии БД хранятся не более 30 дней; журналы доступа сервера до 14 дней; жалобы
и записи о модерации до двух лет. Неидентифицирующая метка времени и непрозрачный хеш факта
удаления аккаунта могут оставаться бессрочно. Добровольные счётчики и отметки удаления истекают
через 90 дней неактивности. Refresh-токены ротируются при использовании и отзываются при выходе
или удалении аккаунта. В стартовой сборке Android покупок нет.

В Android откройте Я → Настройки → Аккаунт и синхронизация → Удалить аккаунт и подтвердите.
Приложение сначала просит сервер немедленно удалить аккаунт и связанные текущие данные.
После успеха оно также стирает локальную библиотеку и данные аккаунта на этом телефоне.
Если серверный запрос не удался, локальные данные сохраняются для повторной попытки.
«Очистить данные на сервере» удаляет загруженную библиотеку с сохранением аккаунта;
перед этим синхронизация выключается. Удаление приложения само по себе не удаляет аккаунт.
Запросить удаление аккаунта или копию данных также можно по адресу privacy@trip-track.app;
мы проверяем принадлежность аккаунта и отвечаем в течение 30 дней. Страница запроса удаления,
на которую ссылается магазин, позволяет сделать это без повторной установки приложения.
