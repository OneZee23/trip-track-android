# w7-recording

Перенесён экран записи из iOS 0.9.3. Удалены `shell/PlaceholderScreens.kt` и прежние страницы `recording/RecordingScreens.kt`. `screen_record` и `tracking_back` сохранены.

## Что перенесено

- `TrackingView.swift`, `IdleHUDView.swift`, `SlideToStartView.swift`, `GPSIndicatorView.swift`: карта во весь экран, выход, GPS и легенда, HUD простоя и записи, ожидание GPS с объяснением и «Всё равно начать», слайдер с порогом 0.66, шиммером 1.8 с и шевронами 0.9 с. Скрытый HUD отсутствует в дереве доступности.
- `VehicleChip.swift`, `VehiclePickerSheet.swift`: только доступные машины, отдельные «без машины» и пассажир, гараж поверх записи. Выбор сохраняется до закрытия шторки; смена машины переносит всю текущую поездку и сначала снимает пассажирскую метку.
- `MapViewRepresentable.swift`, `GlowingHeadOverlay.swift`, `SmoothTrackManager.swift`, recording-части `MapViewModel.swift`: маршрут по скорости без линии через ручные паузы, короткий анимированный хвост из пяти точек, машина, её состояния/GPS-конус/точность/пульс, follow и heading-up, отмена follow жестом, блокировка жестов при записи. Отступ камеры простоя измеряется по HUD. Live reveal передаётся существующему GPU fog как маска 150 м с анимацией 0.7 с; растровой подложки нет. Черновик не открывает территорию.
- `RecoveryPromptSheet.swift`: недисмиссируемая house-шторка, продолжение и завершение. Завершение восстановленной поездки берёт время последней сохранённой точки до поступления нового GPS.
- `RecordingFinishPreview.swift`, finish-части `MapViewModel.swift` и `TripManager.swift`: вопрос описывает реальное сохранение или удаление короткой поездки. Preview не пишет в базу и учитывает ещё не сброшенные точки. Повторная проверка и остановка выполняются под одним GPS mutex; изменившийся исход требует нового ответа. Отмена сохраняет ручную паузу.
- `TripCompleteSummaryView.swift`, `TripRevealedBlock.swift`, `RevealSweep`: светлая карточка итогов, hero-карта, показатели, XP/уровень/серия/повтор дороги/значки, «Открыто», описание, приватность и фотографии. Фото и «Готово» закреплены снизу. Подтверждение черновика обновляет открытую карточку наградами; удаление закрывает её, сохраняя другую активную запись. Tips и PRO скрыты.
- Permission ledger, recovery, health и stereo остаются у `core:location`. Объяснения показаны house-шторками, health вынесен в HUD и существующие настройки автозаписи/магнитолы. Разрешения спрашивает Android.

Метры, скорость и высота отображаются через `Measure` и `LocalDistanceUnit`, включая miles/feet. Изменение единиц в настройках сразу достигает экрана. Управление очищает status/navigation insets; редактор описания учитывает клавиатуру. Строки сгенерированы для 13 языков; два буквальных iOS-текста наград вынесены в Android-ресурсы. Новых зависимостей и изменений build-файлов нет. В `AppGraph` добавлен только владелец presentation state.

## Тесты

Портированные сценарии:

- `SlideToStartTests`: нет GPS → объяснение без старта, старт вопреки ожиданию, обычный drag со свежим GPS. Дополнительно: короткий drag/тап, accessibility activation, один ответ на касание заблокированного контрола.
- `RecordingStatesTests`, `TrackingTourTests`: idle, запись, пауза, продолжение, вопрос остановки и возврат, выход без таббара; HUD поверх тёмной и светлой карты.
- `RecordingFinishFlowTests`: короткая запись, предупреждение об удалении, сохранение намеренной паузы после возврата.
- `RecordingFinishPreviewTests` / `RecordingFinishManagerTests`: неизменённые пороги/границы, идентичность исхода, trim и числа preview/stop, отсутствие побочных записей, clamp пустой поездки. Добавлены регрессии pending batch и смены исхода на границе времени.
- `RecoveryWindowTests`: неизменённые существующие JVM-кейсы `RecordingPiecesTests`; дополнены проверками времени завершения и восстановленного маршрута без дублирования.
- `TripRevealedLineTests`, `RevealSweepTests`, `CarHeadingPolicyTests`, `MapCarMarkerTests`: порядок и склонения строки, фильтрация находок, sweep/ось/время, курс/конус/масштаб и цвета машины с исходными Swift-входами.
- Дополнительно: системные insets 28/34 dp, miles/feet, неявное закрытие recovery, фильтр машин, отмена описания, сохранение выбора машины до старта и обновление итогов после решения по черновику.

Roborazzi: 30 PNG в `app/src/test/screenshots/recording_*.png`, 15 состояний в обеих темах: idle без GPS и с GPS, отказ геолокации, запись, пауза, черновик, finishing, miles, короткое удаление, сохранение, toast удаления, итоги, recovery, idle и запись поверх светлой карты. В Robolectric карта заменяется статичной подложкой, нативный GL не запускается. Итоги всегда светлые, как на iOS.

## Проверки

JDK 17; команды запускались последовательно. Локальный Android jar указан явно, чтобы Robolectric не создавал download lock вне sandbox. В командах ниже `$HOME` обозначает домашний каталог, а не дополнительную настройку проекта.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" :core:location:testDebugUnitTest :app:testDebugUnitTest --tests 'com.onezee.triptrack.recording.*' -Proborazzi.test.record=true

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Drobolectric.dependency.dir="$HOME/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7" :core:location:testDebugUnitTest :core:map:testDebugUnitTest :core:designsystem:testDebugUnitTest :app:testDebugUnitTest :core:location:lintDebug :core:map:lintDebug :core:designsystem:lintDebug :app:lintDebug

python3 tools/strings/generate.py --ios "$HOME/OneZeeProjects/trip-track-ios-ref" --check
python3 tools/strings/validate.py
git diff --check
git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'
```

Оба Gradle-прогона завершились `BUILD SUCCESSFUL`. Запись эталонов: 111 тестов движка и 67 тестов представления, включая 30 PNG. Полный прогон с проверкой эталонов:

| Модуль | Тестов | Ошибок / пропусков |
| --- | ---: | ---: |
| `core:location` | 111 | 0 / 0 |
| `core:map` | 150 | 0 / 0 |
| `core:designsystem` | 185 | 0 / 0 |
| `app` | 830 | 0 / 0 |

Всего 1 276 тестов. Четыре `lintDebug` прошли без ошибок; предупреждения: location 2, map 6, designsystem 42, app 76. Новые composable modifier warnings устранены. Генератор строк и validator прошли; `git diff --check` чистый. Privacy scan не нашёл совпадений, включая отдельную проверку новых текстовых файлов.

## Отличия и оставшаяся проверка

Новые отличия перечислены в `docs/divergences.md`: MapLibre zoom вместо расстояния камеры MapKit; отдельный ряд метки черновика на узком экране; Android photo picker с явным удалением; существующая house-карточка значка; нативные анимации и haptics. Сохраняются общие различия карты, символов и полупрозрачного стекла из дизайн-системы.

Находки используют выключенный shipped-флаг: километры и регионы живы, секреты/загадки/вехи скрыты. Runtime remote-config store и включение обработки находок на Android ещё не перенесены; серверный override из iOS здесь не подключён.

Эмулятор не запускался. Оркестратору остаётся проверить нативную карту/GPU fog, follow и heading-up, положение машины над HUD, реальную клавиатуру, Android permission/settings round trips, background service и stereo на эмуляторе и телефоне. JVM-скриншоты этих платформенных проверок не заменяют. Коммитов и push нет.
