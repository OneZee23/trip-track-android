# w0-api-contract

Создан `docs/api-contract.md`: 90 HTTP-маршрутов, схемы запросов и ответов, envelope, авторизация, ошибки, повторы, идемпотентность и правила absent/null/empty. Исходный код и `docs/port-status.md` не менялись. Gradle, Xcode и эмулятор не запускались; запросов к рабочему API не было.

## Источники

Сверены локальные ревизии iOS `a155859d` и backend `a598f6c`.

- iOS: `Networking/APIClient.swift`, `APIEndpoint.swift`, `APIEnvelope.swift`, `APIError.swift`; все DTO из `Models/Sync/` и `Models/Social/`.
- Синк: `Services/Sync/APISyncTransport.swift`, `SyncCoordinator.swift`, `PullApplier.swift`, `SyncChunkBudget.swift`, `SyncEnqueuer.swift`, `LastSyncedAtStore.swift`, `ManualTripTrackLoader.swift`, `VehiclePhotoRestore.swift`, `CompanionPhotoUploadService.swift`; `SyncQueue.swift`, `Persistence/TripRepository.swift`.
- Аккаунт: `AuthService.swift`, `TokenStore.swift`, семантика `KeychainHelper.swift`; `Services/Social/`, `CompanionsStore.swift`, `ProfilePhotoStore.swift`, `RemotePhotoStorage.swift`, `Sync/R2PhotoStorage.swift`.
- PRO, конфиг и push: `Services/Plus/PlusAttachQueue.swift`, `Networking/PlusAPI.swift`, `Services/RemoteConfig/`, `PushNotificationManager.swift`, `NotificationManager.swift`, `NotificationsInboxStore.swift`, `ProductAnalytics.swift`, `Views/Admin/AdminModel.swift`.
- Прочитаны решения `CLAUDE.md` по синку, отметкам/отрезкам, публикации путешествий, приватной зоне, PRO, гаражу, bio и pins. Правила сверены с тестами и JSON-фикстурами. Примеры с идентификаторами живых аккаунтов из CompanionsDTOTests в документацию не перенесены.
- Backend: контроллеры, DTO, validation, ошибки и затронутые сервисы `src/modules/{auth,sync,trips,photos,journeys,vehicles,settings,social,companions,groups,secrets,plus,notifications}`, общие JSON-RPC/auth и отправка push.

## Найденные расхождения

- Backend стирает точки при принятом апсерте без trackPoints; новый iOS использует отсутствие поля для незагруженной ручной поездки.
- Pull не восстанавливает отметки/отрезки на новом телефоне. Метаданные фото и отсутствующий список photos имеют разные правила на клиенте и сервере.
- JSON-RPC-фильтр теряет serverVersion/serverLastModifiedAt у конфликта.
- iOS опускает nil title/endDate/coverPhotoId путешествия, хотя backend для очистки ждёт null. Backend также очищает manualOdometerKm при отсутствии ключа.
- В backend нет profile photos, pins, новых полей гаража, profile-trips/badges, remote config/catalog/admin и usage endpoints. Отдельно отмечено отсутствие hasPlus/profileXp в публичном профиле.
- Для Android отсутствуют Google login, Apple browser callback/redirect/exchange, FCM и поля платформы/версии. Поддержка raw Apple JWT уже есть, готового браузерного потока нет.

Каждому расхождению дано конкретное предложение в последнем разделе контракта. Изменений поведения Android в этой задаче нет. Предлагаемые отличия: системное хранилище вместо Keychain, FCM вместо APNs, вход Google/Apple web, получение PRO через аккаунт без StoreKit и покупок на Android. Они пока не реализованы.

## Проверки

Поведенческие тесты не запускались: задача меняет только документацию. Имена тестов в контракте указывают будущие проверки порта, а не результат запуска.

Покрытие путей, DTO, JSON и ссылок на тесты проверено следующим точным вызовом из корня checkout:

```sh
python3 - <<'PY'
from pathlib import Path
import re, json
base = Path.home() / 'OneZeeProjects/trip-track'
doc = Path('docs/api-contract.md').read_text()
source = (base / 'TripTrack/Networking/APIEndpoint.swift').read_text()
paths = {p for p in re.findall(r'"(/[^"\n]+)"', source) if '\\(' not in p}
assert all(p in doc for p in paths)
assert len(paths) == 79
assert len(re.findall(r'^\| (?:GET|POST) `/[^`]+` \|', doc, re.M)) == 90
blocks = re.findall(r'```json\n(.*?)\n```', doc, re.S)
for block in blocks:
    json.loads(block)
assert len(blocks) == 5
for name in ['TripSyncPayload', 'TrackPointPayload', 'PhotoSyncPayload',
             'VehicleSyncPayload', 'JourneySyncPayload', 'SettingsSyncPayload']:
    source = (base / 'TripTrack/Models/Sync' / (name + '.swift')).read_text()
    source = source.split('extension ' + name)[0]
    fields = set(re.findall(r'^    (?:let|var) (\w+)\s*:', source, re.M))
    fields -= {'manualOdometerKnown', 'soldAtKnown', 'CodingKeys'}
    assert all(re.search(r'\b' + field + r'\b', doc) for field in fields)
for name in set(re.findall(r'`([A-Za-z][A-Za-z0-9/]*Tests)`', doc)):
    assert (base / 'TripTrackTests' / (name + '.swift')).exists(), name
assert doc.count('```') % 2 == 0
assert '—' not in doc and '–' not in doc
print('PASS: 79 static paths, 90 HTTP routes, 6 DTOs, 5 JSON blocks, test references')
PY
```

Результат: PASS. Семь динамических user routes и два имени remote catalog также сверены с APIEndpoint вручную. Проверка полей подтверждает наличие описаний ключей; типы, единицы и семантика сверены чтением исходников.

Остальные точные команды:

```sh
git diff --check
git diff --cached --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
rg -n 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY' docs/api-contract.md docs/reports/w0-api-contract.md
```

Проверки diff без ошибок; поиск запрещённых данных без совпадений (код возврата 1 у grep/rg означает отсутствие совпадений). Humanizer применён к тексту обоих файлов. Push не выполнялся.

Дополнительно проверены столбцы Markdown-таблиц и последний раздел:

```sh
python3 - <<'PY'
from pathlib import Path
import re
s = Path('docs/api-contract.md').read_text()
assert re.findall(r'^## (.+)$', s, re.M)[-1] == 'What Android needs that the backend lacks'
counts = []
for line in s.splitlines() + ['']:
    if line.startswith('|'):
        counts.append(len(re.findall(r'(?<!\\)\|', line)))
    elif counts:
        assert len(set(counts)) == 1, counts
        counts = []
print('PASS: table columns and final section')
PY
```

Результат: PASS.

Остаётся реализовать Android DTO/транспорт/синк и отдельно согласованные изменения backend; эта задача их не включает.

Коммит не создан: команда `git add -- docs/api-contract.md docs/reports/w0-api-contract.md` завершилась с `Operation not permitted` при создании `.git/index.lock`. Политика среды запрещает запись git metadata; оба файла остались в рабочем каталоге.

## Re-verification

Контракт повторно сверен с `origin/master`, `614f3675af41a1758f32a41bad6382fb99468c85` (26 октября 2026), в read-only checkout `trip-track-backend-identity`. Выводы выше об отсутствующих iOS-функциях относятся к устаревшему backend `a598f6c` и больше не действуют.

- Убраны ложные пробелы: profile photos, pins, семь полей гаража и публичные фото/mods, profile-trips/badges, hasPlus/profileXp, config/catalog/admin, usage. Уточнены DTO, фильтры видимости, нормализация, лимиты и ошибки этих маршрутов.
- Добавлены HTTP-204 usage, его validation/revision/tombstone/retention и лимит 8 KiB; категория push PRO_EVENT. Исправлены размер главного фото гаража, default comments=30, публичные riddle finds и сравнение UUID без учёта регистра.
- Каждый оставшийся пробел и строка таблицы потери данных получили файл и символ master. Подтверждены удаление трека при пропуске trackPoints, неполный pull (включая extended metrics), потеря conflict metadata, очистка manualOdometerKm и риск курсора. Ошибки iOS с photos и nil путешествия отмечены как клиентские; backend уже умеет очищать поля путешествия через null.

Повторены оба Python-блока проверок выше: PASS (79 статических путей, 90 HTTP-маршрутов, 6 DTO, 5 JSON-примеров, ссылки на тесты, столбцы таблиц). Дополнительно все 90 маршрутов сопоставлены с контроллерами master, пути доказательств проверены на существование. `git diff --check` прошёл; `git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'` и такой же `rg` по двум документам не нашли совпадений.

Поведенческие тесты и сборки не запускались: изменены только два документа. Запросов к production, записи в backend и git metadata не было; коммит оставлен оркестратору. Android-код и перечисленные исправления остаются вне этой задачи, новых отличий Android от iOS нет.
