# w1-network

Перенесён `core:network`: OkHttp-клиент, сессия, защищённые токены, недостающие DTO.
Базовый адрес: `https://api.trip-track.app`, публичный host из iOS-репозитория.
Ветка `w1-network`; коммитов и push нет.

| Android | Источники iOS |
|---|---|
| ApiClient, ApiEndpoint, ApiEnvelope, ApiError, NetworkJson | `Networking/APIClient.swift`, `APIEndpoint.swift`, `APIEnvelope.swift`, `APIError.swift`, `DataGzip.swift`; `Utils/ISODate.swift` |
| TokenStore, SessionState, SecureVault, KeystoreTokenVault, CredentialCipher | `Services/TokenStore.swift`, `KeychainHelper.swift`, раздел сессии в `AuthService.swift` и CLAUDE.md |
| GoogleLoginRequest, GoogleNonce | `Models/Sync/AuthDTOs.swift`, `Services/SIWANonce.swift`; Android-добавление `/auth/google/login` |
| PIISensitiveKeys, PIIScrubber, ApiLogger | `Common/PIISensitiveKeys.swift`, `PIIScrubber.swift`, `Networking/APILogger.swift` |
| SyncChunkBudget, SyncDTOs | `Services/Sync/SyncChunkBudget.swift`, DTO из `APISyncTransport.swift` |
| PhotoDTOs, PhotoUploadRequest, MultipartFile | `Services/Sync/R2PhotoStorage.swift`, `VehiclePhotoRestore.swift`, `Services/ProfilePhotoStore.swift`, `Networking/MultipartFormDataBuilder.swift` |
| PublicTripsDTOs, GroupsDTOs, PushDTOs, AdminDTOs, UsageDTOs | `Services/TripSource.swift`, `Social/GroupsWaitlistStore.swift`, `PushNotificationManager.swift`, `Views/Profile/NotificationSwitches.swift`, `Views/Admin/AdminModel.swift`, `Services/ProductAnalytics.swift` |
| RemoteConfig, RemoteConfigResponse, RemoteCatalogResponse | `Services/RemoteConfig/RemoteConfig.swift`, `RemoteConfigStore.swift`, `RemoteCatalogs.swift` |
| PlusApi, SecretsApi, RemoteConfigApi, SocialCommentsApi | `Networking/PlusAPI.swift`, `SecretsAPI.swift`, remote-config transport, `Services/Social/SocialCommentsStore.swift` |

Из `core:model` переиспользованы все `AuthDTOs`, `Models/Sync` и `Models/Social`,
`ProfilePhotoDTO`, `PublicVehicleMod`, `PlusAttachRequest`/`PlusStatusResponse`,
`SecretCatalogResponse`/`SecretRevealRequest`/`SecretRevealResponse`, каталоги и доменные enum.
Социальные авторы, поездки, фотографии, путешествия и гараж не дублируются в network.
`AnyJSON` представлен `JsonElement`. Multipart передаёт текстовые поля и JPEG-часть, без JSON-обёртки.

В общем сериализаторе `MeResponse` исправлено попадание `bioProvided` в JSON;
явный `bio:null` сохраняется при round trip. `ProfilePhotoDTO` теперь игнорирует
некорректные необязательные поля, как `try? decodeIfPresent` в Swift. Это два
необходимых исправления `core:model`; его зависимости и build-файл не менялись.

Клиент сохраняет `x-access-token`, HTTP-200 envelope, пустой ответ только для
`EmptyResponse`, типизированные ошибки и неизвестные code/message. JSON кодируется
вне главного потока, даты UTC с миллисекундами; неизвестные поля игнорируются.
Сохранены gzip от 4096 байт, таймауты 30/90 секунд, 3/2 сетевые попытки по сжатому
размеру 10000 байт, паузы 0.8/1.2 секунды и Once для создания комментария, в том числе
после refresh. Usage отдельно принимает только HTTP 204, без токена, gzip и повторов.

Refresh общий для параллельных запросов. Потерянное соединение допускает один повтор
через секунду с прежним refresh; timeout не повторяется немедленно. Фоновая лестница:
5, 30, 120 секунд. Недоступное хранилище ждёт следующего тика; missing останавливает
восстановление. Повторный USER_NOT_AUTH проверяет refresh ещё раз без третьей отправки
исходного запроса. Поколение сессии отбрасывает старые результаты, SupervisorJob
изолирует отказ refresh и отмену отдельного ожидающего запроса.

Мягкое истечение убирает access и маркер входа, сохраняет refresh, account UUID и имя.
Полная очистка имеет три причины: пользовательский выход, успешное удаление аккаунта,
USER_BANNED. Неудачный Google login сохраняет прежнюю сессию. В Google POST уходит raw
nonce; `GoogleNonce.sha256` предназначен для Credential Manager.

Токены шифруются платформенным JCA `AES/GCM/NoPadding`, ключ 256 бит создаётся в
Android Keystore и не экспортируется. Файл лежит в `noBackupFilesDir`, scope включает
applicationId и API host, также связанный с ciphertext через AAD. Клиент отклоняет
хранилище другого host. До первой разблокировки возвращается unavailable; ошибка
чтения, потерянный ключ и повреждение файла не удаляют данные. AtomicFile заменяет
снимок после шифрования, не удаляя прежний перед записью.

Отдельная crypto-библиотека не добавлена: платформенные API покрывают minSdk 29.
AES-256-GCM входит в [рекомендации Android](https://developer.android.com/privacy-and-security/cryptography),
ключ защищает [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
Jetpack Security Crypto [deprecated с 1.1.0](https://developer.android.com/privacy-and-security/cryptography#jetpack_security-crypto_library),
поэтому EncryptedSharedPreferences не выбран.

В catalog добавлены только `okhttp-mockwebserver` и `coroutines-test` с существующими
версиями. Network подключает OkHttp, serialization и coroutines; тесты используют
MockWebServer, виртуальное время, JUnit и kotlin.test. Публичные типы зависимостей
доступны потребителям через `api`.

Проверки:

```text
./gradlew --no-daemon --project-cache-dir /private/tmp/triptrack-w1-network-gradle :core:network:testDebugUnitTest :core:network:lintDebug :core:model:test
```

Результат: 80 тестов network, 83 теста model, без ошибок и пропусков; lint: `No issues found`.
JDK 17 задан toolchain обоих модулей. `git diff --check` и проверка публичных файлов
на IP/ключи выполнены. Эмулятор и production API не использовались.

Первый запуск `./gradlew :core:network:compileDebugKotlin` остановился до компиляции:
переиспользованный daemon не имел доступа к checkout. Повтор с отдельным cache-dir
тоже не прошёл до добавления `--no-daemon`; одноразовый daemon в sandbox собрал модуль.
Предупреждение FSEvents не мешает сборке. `gradle.properties` и лимиты памяти не менялись.

Тесты перенесены из `APIClientEnvelopeTests`, `APIClientTokenRefreshTests`,
`APIClientKeychainUnavailableTests`, `AuthSessionExpiryTests`, `KeychainHelperTests`,
`KeychainWriteTests`, `SyncChunkBudgetTests`, `PIIScrubberTests`, `APILoggerRedactionTests`,
`ISODateTests`, `CompanionsDTOTests`, `ProfileJournalWireTests`, `PublicJourneyDTOTests`,
`SocialFeedVehicleDecodingTests`, `SocialProfileFindsTests`, `JourneySyncPayloadTests`,
wire-частей `VehicleSyncPayloadTests`, `SettingsSyncPayloadTests`, `PlusAttachTests` и
`MultipartFormDataBuilderTests`. Дополнительно проверены AES-GCM, nonce, границы gzip,
редкие ошибки необязательных полей, поколения сессии и отмена ожидающего refresh.
JSON-фикстуры скопированы в `core/network/src/test/resources/ios`; записанные в iOS
companion fixtures идентификаторы заменены синтетическими, числа и форма сохранены.

AppGraph должен создать по одному vault/store/client с одинаковым base URL, читать
`TokenStore.state`, вызывать hydrate в IO и revival на запуске, foreground и
ACTION_USER_UNLOCKED. `onIdentityChanged` вызывается после успешного входа; здесь
приложение очищает очередь/consent предыдущего аккаунта. `onHardSignOut` подключает
сброс аккаунтных данных по правилам приложения. Network не зависит от Room или sync.

Остаются задачи других волн: UI Credential Manager, развёртывание Google endpoint,
FCM transport/registration, PRO cache, sync/appliers, подготовка чистого растра и
проверка настоящего Keystore после reboot/unlock/переустановки на устройстве.
Здесь готовы DTO и транспорт; предложения backend из конца API-контракта не
выдаются за существующие маршруты. Покупки и identity linking не включены.
Все намеренные отличия записаны в `docs/divergences.md`; известные серверные
асимметрии journeys/trackPoints/photos не исправлялись изменением wire-формата.
