# TripTrack Dev on the POCO F1: QA pass, 4 Nov 2026

POCOPHONE F1, Android 10 (MIUI 12), three-button navigation, 393 × 816 dp, Russian. Debug build of trip-track-android master 10e077b, demo library seeded with SeedActivity, then `seed_garage_demo` for the car. Signed out the whole time.

No crashes and no ANRs: the crash buffer and the FATAL/ANR grep were empty after every tab, and the app process logged nothing at error level. One `Slow Looper main ... 321ms late` warning on the cold start with the garage seed.

Count: 0 blockers, 3 major, 13 minor, 10 polish.

Screenshot names below are files in `qa/poco` (`<name>.png`, preview `<name>_s.png`). The `qa-90x` files are crops and side-by-side strips made from them.

## Major

### M1. Atlas: system Back on a trip opened from the Atlas does nothing visible

Steps: Атлас, pull the sheet up to the list, open «Краснодарский край», open a trip from the region's list, press system Back.

Result: the trip stays on screen while each press closes a layer of the Atlas sheet hidden underneath. In one run three presses did not leave the trip; the on-screen back button did. From a trip card (photo pin, «Открыть поездку») the first press did nothing and the second one returned.

Cause, from the code: `AtlasScreen.kt:181` registers a `BackHandler` that stays enabled while a region, a card or the expanded list is selected. The Atlas stays composed under the pushed trip, so it takes Back first.

iOS: the trip opens as a cover over the Atlas (`MyMapView.swift:197`, `:333`), and closing it returns to the Atlas as it was. divergences.md:186 describes Back for the Atlas's own layers only.

Shots: qa-109-atlas-trip, qa-110-atlas-trip-back, qa-111-atlas-trip-back2, qa-112-atlas-after-trip, qa-114-region-trip2 to qa-118-region-trip2-chevron, qa-641-atlas-pin, qa-642-atlas-card-trip, qa-643-atlas-card-trip-back, qa-644-atlas-card-trip-back2.

### M2. Map credits hidden under the sheets (Atlas, Places, a place, home)

Result: the MapLibre logo and its «i» (the way to the OpenStreetMap and OpenMapTiles credits on the map) are not visible above the Atlas summary or the Places panel: they sit behind the sheet. On the Atlas trip card they are cut in half by the card's top edge and a pin. On a place screen the sheet's rounded top covers them. On «Дом на карте» the hint «Нажмите на карту, чтобы поставить дом» lies on top of them. They are visible on the recording screen and on trip heroes.

Cause, from the code: `AtlasScreen.kt:166` builds the slot on the height above the three-button bar, and `:211-212` subtracts the navigation bar inset again. `PlacesScreen.kt:213` does the same with `safeBottom`. `PlaceDetailScreen.kt:167` passes `MapInsets.Zero` under a sheet that overlaps the map. Gesture navigation was not checked.

iOS: CLAUDE.md:1069 («Логотип Apple и «Legal» обязаны быть видны»); divergences.md:72-73 make the «i» the Android way to the providers. The credit line exists in Настройки (qa-500), but OpenStreetMap asks for it on the map.

Shots: qa-100-atlas, qa-640-atlas-again, qa-641-atlas-pin, qa-300-places, qa-308-places-map, qa-325-place-empty, qa-124-home-sheet, strip qa-901-map-credits. Visible for comparison: qa-200-record, qa-489-city-trip.

### M3. With a sheet open, the status bar turns black and the navigation bar white

Where: every house sheet (comments, sign-in prompt, «Бета», «Вид карты», «Дом на карте», place rename, trip edit, «Надёжность записи», Настройки and its pickers, «Поделиться поездкой»), and the place delete dialog.

Result: in the light theme the status bar becomes a solid black band with white icons above the dimmed page. In the dark theme the three-button bar under a dark sheet is solid white. Screens without a sheet are fine, and the badge card keeps a dimmed status bar (qa-041).

Expected: divergences.md:85 says the status bar glyphs keep the app theme while a dialog window is up. On Android 10 the dialog windows here fall back to the system's bar colours. Android 11 and later not checked.

Shots: strip qa-900-statusbars; light qa-016-comment-tap, qa-020-react-plus, qa-119-beta, qa-121-map-style, qa-314-place-delete, qa-318-place-rename, qa-475-trip-edit, qa-500-settings; dark qa-600-dark-settings, qa-606-dark-mapstyle, qa-610-dark-delete-dialog, qa-614-theme-picker-dark.

## Minor

### m1. Short pickers lose their footnote under the navigation bar

Where: Настройки, «Тема»; «Единицы и формат», «Единицы» and «Расчёт средней скорости»; car form, «Приборка».

Result: these sheets are sized to their content, and their last ~48 dp end under the three-button bar:
- The theme and units footnotes do not show at all.
- The average speed footnote stops at «…вместе со».
- The «Приборка» footnote stops at «…в которой их».

The sheets do not scroll. The long language list scrolls, and its footnote is visible (qa-501).

iOS: each SettingsOptionPicker shows its footnote (`ProfileSettingsSheet.swift:454-465`, `AppPreferencesView.swift:243-256`, `VehicleEditFormView.swift:1497-1510`). Android asks ContentSizedSheet to clear the bar (`SettingsOptionPicker.kt:85-90`); on this phone the inset it gets looks like zero (not traced further).

Shots: qa-503-theme, qa-505-units-picker, qa-506-units-picker-scroll (unchanged after a swipe), qa-507-avgspeed, qa-424-dashboard-scroll.

### m2. Drafts: Back in selection mode leaves the screen, and the mode stays on

Steps: Я, Черновики, «Выбрать», system Back. Open Черновики again.

Result: Back closes the list instead of the selection mode. The next opening shows «Выбрано 0» with «Все», «Готово» and checkboxes.

iOS: `DraftsListView.swift:23` keeps `selecting` in view state, so the list always opens normal. Android keeps it in the view model (`DraftsViewModel.kt:68`, `:110`), which outlives the screen.

Shots: qa-445-drafts-select, qa-446-drafts-select-back, qa-628-drafts3.

### m3. Statistics chart: March, June and July cut to «М…» and «И…»

Where: someone's profile, Статистика (own statistics not opened).

Result: on 393 dp the axis reads «Янв Фев М… Апр Май И… И… Авг…».

iOS: `StatsScreenView.swift:480-484` gives the label `lineLimit(1)` with `minimumScaleFactor(0.6)`, so it shrinks.

Shots: qa-026-author-stats, crop in qa-902-crops.

### m4. Achievements: not the iPhone's screen, and titles broken inside words

Where: Я, Достижения; someone's Достижения.

Result: three narrow columns. Long titles wrap inside a word («Путешественн / ик», «Исследовател / ь»), and wrapped titles are left-aligned while one-line titles are centred. The page holds a count line and the grid. Missing compared with iOS:
- the percentage ring;
- the rarity chips and the rarity line under each title;
- progress bars on locked badges;
- the «Все / Открытые / Секретные» segment on your own page (it shows only on someone else's profile).

iOS: two columns (`AchievementsView.swift:59-62`), hero and segment for own and foreign (`:64-72`, `:100-140`), centred two-line title with the rarity line (`AchievementCell.swift:69-92`). Android: `AchievementsScreen.kt:64`, `:81-86`.

Shots: qa-404-my-badges, qa-043-badges-scroll, qa-040-author-badges, strip qa-903-achievements.

### m5. Badge card says «открыто» in lower case

Steps: Я, Достижения, «Хранитель тайн».

Result: the status line under the art is a lone lower-case «открыто». There is no «Закрепить».

iOS: `AchievementDetailView.swift:228-236` capitalizes it in a chip and offers the pin. Android prints `strings.unlocked()` unchanged (`AchievementsScreen.kt:100`).

Shots: qa-405-my-badge-detail.

### m6. Country picker shows «…» instead of 🌍 and 🏳️

Steps: Я, tap the name, Мой профиль, «Страна».

Result: «Весь мир» and «Нейтральный флаг» show «…» where the globe and the white flag should be. Probably the emoji at 18 sp does not fit the 20 dp box (`ProfileAuxiliaryScreens.kt:122-125`).

iOS: `CountryPickerView.swift:237-240`.

Shots: qa-527-country.

### m7. Car passport offers «Сделать основной» for the main car

Steps: Я, the car, «…».

Result: Polo is the active car (checkmark in Гараж), and the menu still lists «Сделать основной».

iOS: `VehicleDetailView.swift:113` shows it only for a car that is neither main nor sold. Android checks only «not sold» (`GarageScreens.kt:172`).

Shots: qa-435-passport-menu, qa-623-garage-list.

### m8. Add-car form asks more, in another order

Steps: Я, empty Гараж, «Добавить транспорт».

Result: the form runs in this order:
1. «Аватар».
2. «Название».
3. «Что за машина»: make and model, «Тип транспорта» as a row that opens a picker, «Год», «Про неё».
4. «Пробег», with «Одометр» and «Приборка».
5. «Двигатель», then fuel.
6. «Валюта цены».
7. «Госномер», last.

«Год» and «Одометр» are blank areas with no placeholder.

iOS: name, four type tiles, make, model and year, plate, avatar, engine, fuel and price (`VehicleEditFormView.swift:241-262`, tiles `:623-672`). The odometer and «Приборка» exist only for an existing car (`:271-278`; CLAUDE.md:3870 «При заведении машины вопрос НЕ задаётся»). The year field shows an example year (`:530-556`).

Shots: qa-408-car-form, qa-410-form-2, qa-416-type-picker, qa-418-form-3, qa-421-form-5, strip qa-904-car-form.

### m9. Trip hero does not return to its framing after the full-screen map

Steps: dark theme, own trip «На работу», expand button, system Back.

Result: the hero keeps a different camera. Both route ends moved up by 66 px (about 24 dp), closer to the top buttons.

iOS: CLAUDE.md:235; `TripHeroAfterFullscreenTests` requires the frame before and after to match exactly.

Shots: qa-611-dark-cancelled, qa-612-dark-fullscreen, qa-613-dark-fs-back.

### m10. Journey page: no map in the hero, no full-screen route

Steps: Я, История, «Демо-путешествие».

Result: the hero is a flat beige box with a thin route line. It has no tiles, no checkpoint markers and no expand button; panning does nothing.

iOS: `JourneyDetailView.swift:376-400` (map of the legs with checkpoint markers), `:429-441` (expand button). Android: `JourneyDetailScreen.kt:276-280`. Not in divergences.md.

Shots: qa-464-journey, qa-465-journey-b, qa-466-journey-pan, strip qa-906-journey-and-heroes.

### m11. A guest's tap on the discussion opens the thread first

Steps: signed out, a public trip from Лента, «Написать первое сообщение…».

Result: the «Обсуждение · 0» sheet opens with a composer that looks writable. Only a tap on its field brings the sign-in prompt, stacked over the thread.

iOS: `TripCommentsSection.swift:210-216` sends a guest's tap straight to the sign-in sheet, and `:642-644` keeps the composer inert for guests.

Shots: qa-015-feed-trip-4, qa-016-comment-tap, qa-017-comment-field.

### m12. Photo viewer: status icons invisible, no «Отметить место»

Steps: own trip «По городу» (3 Nov), a photo in «Моменты».

Result: the clock and signal icons are dark on the black status bar; only the battery shows. The viewer offers delete and close, without the iPhone's «Отметить место» (turn the photo into a checkpoint).

iOS: `PhotoFullScreenView.swift:165` (status bar shown with the controls), `:386-391` and `TripDetailView.swift:2056-2079` (mark place). Android `PhotoViewer.kt:88-95` has no such action.

Shots: qa-493-photo-viewer, qa-494-viewer-2.

### m13. The level ignores trips already in the library

Where: Я hero, Уровни.

Result: with 61 trips, 1 994 km and 18 badges unlocked, the hero says «LVL 1 · Новичок» and Уровни says «0 / 50 XP». The seed writes trips straight to the database, which is the same as a library that arrives without XP.

iOS: at launch `MapViewModel.swift:403-406` calls `GamificationManager.backfillIfNeeded` (`GamificationManager.swift:234`), which counts XP from the trips when the profile has none. Android has the same math in `RewardMath.backfillXP` (`core/logic/.../RewardMath.kt:124`), but nothing calls it. A signed-in account also merges the server's XP (`ProfileProgressStore.kt`), so for real users this shows only when a library arrives without XP. I did not check how often that happens.

Shots: qa-616-me-light, qa-439-levels, qa-620-disclosure-car.

## Polish

### p1. Catalog: a current generation shows only its first year

«Granta, рестайлинг (FL)» reads «2018»; iOS prints `2018–2026` (`VehicleCatalogModelSteps.swift:11-14`, `:35`). Android joins only the years it has (`VehicleEditSheet.kt:171`, `:180`). Shots: qa-413-catalog-gen, strip qa-905-catalog-passport.

### p2. «Журнал» (Отправить логи) is a wall of raw text

The caption and «Поделиться файлом» sit in a white block clipped at the left edge. The log follows as unformatted lines with URLs and request bodies, and the button scrolls away with them. iOS keeps the button fixed above the scroll and shows the entries as rows in a card (`DebugLogsView.swift:25-50`, `:126-137`). Shots: qa-510-send-logs, qa-511-logs-scroll, crop in qa-902-crops.

### p3. Someone's garage: chevron floating mid-card, no XP bar

The chevron follows the name, so it lands in a different place on each card (about 80 % across for Mufasa, 64 % for Solaris). The level is a text line, «LVL 19 · 18 298 км». iOS puts the chevron at the trailing edge and shows the XP bar with the level pill (`PublicGarageView.swift:129-166`). Shot: qa-047-author-garage.

### p4. Someone's hero: the chevron touches the rank, «Легенда дорог›»

The own hero keeps the gap. `PublicProfileScreen.kt:305` draws the chevron in a 9 dp box; the own hero uses `symbolBox(9f)` (`ProfileHeroCard.kt:240`). iOS: `ProfileHeroCard.swift:292-306`. Shot: qa-022-author.

### p5. Pickers without their symbols or hints

- «Расчёт средней скорости» shows empty discs where iOS has a clock and a car (`AppPreferencesView.swift:250`).
- «Двигатель» has no hint under each option (`VehicleEditFormView.swift:1233-1243`; Android `VehicleEditSheet.kt:124`).
- «Фон профиля» lists English names beside empty grey discs, with no colour to choose by.

Shots: qa-507-avgspeed, qa-422-engine, qa-531-bg-picker.

### p6. «Мой профиль» header and end

«Сменить аватар» is a full-width settings row whose chevron does not turn when the grid opens. The avatar has no pencil badge, and the name is not shown under it. iOS shows the name and @username under the avatar and an accent «Сменить аватар» link (`MyProfileView.swift:335-363`). At the end of the scroll the «Уровень» card sits partly under the navigation bar (`MyProfileScreen.kt:84` has no bottom inset for the bar). Shots: qa-520-myprofile, qa-521-avatar-pick, qa-530-myprofile-3.

### p7. The «i» on a trip hero does nothing

It is drawn on the hero, but the hero's touch shield takes the tap. On the full-screen map it opens «Источники карты». divergences.md:168 lists only the markers as untappable on the hero. Shots: qa-646-feed-trip2, qa-647-attribution, qa-649-fs-attribution.

### p8. Card snapshots cut city labels at the edge

In Я history cards, «Краснодар» loses the top of its letters at the map's top edge and covers the start marker; «…ТНЫЙ» is cut at the left. Shots: qa-450-calendar-day-list, qa-607-dark-history, crop in qa-902-crops.

### p9. Trip page section order

Android: «Движение», «Описание», «Фото», achievements, «Попутчики», «Реакции», «Обсуждение». iOS puts «Попутчики» right after «Движение» (`TripDetailView.swift:2555-2561`). Shot: qa-473-trip-own-5.

### p10. Trip hero route under the buttons (not compared on an iPhone)

On «По городу» the top of the route runs under the back button and the share capsule, and the end photo pin sits under the expand button. I could not check what the iPhone does with the same route. Shots: qa-489-city-trip, qa-643-atlas-card-trip-back.

## Seen and left out

These match the iPhone, so they are not findings:
- «0 из 0 городов» for regions without a city table;
- «Weekend warrior» as the Russian badge title;
- «В Гараже пусто»;
- «Здесь 0 раз» on a place without passes;
- the checkered flag at the end of feed card routes;
- the Atlas map staying light in the dark theme, and the recording map always dark;
- «С TripTrack — 0 км» on someone's car whose trips the server did not return.

The garage seed (`app/src/debug/.../MeLaunch.kt`) dates its ten trips from the 1st of the month, one day apart, so on 4 Nov six of them are in the future. That shows up as «10 нояб» tiles, «Лучший день по км: 10 ноября 2026» in the passport and `3–10 нояб.` in the journey composer. Debug only.

People search for «Max» found nobody, while Discover recommends «Максим Мусин». The search runs on the server; I did not compare it with iOS.

## Checked

Light theme:

- Лента:
  - Feed: cards, the reactions row, the card «…» menu and Back on it, the poster studio (styles and formats), «Подписки» as a guest.
  - «Найти людей» with the keyboard and an empty result.
  - A trip from the feed: scrolled to the end, the comments sheet, the sign-in prompts for reacting and commenting.
  - The author's profile: its journal, Статистика, Атлас (summary, region, region trips, Back), Достижения with filters and a badge card, Гараж, a public car, the record row opening a trip, the full-screen map and its «Источники карты».
- Атлас:
  - The sheet: summary, list, a region and its trip list, a trip opened from a region, Back on all of them.
  - Controls: «Бета», «Вид карты» (Туман, Ночь, Клетки), «Дом на карте», «где я» with location denied and «Позже».
  - The Atlas share (I pulled the poster file and looked at it), the «новых дорог» and «всего» sheets.
  - A road card and a photo pin card, «Открыть поездку».
- Запись: the screen with location denied, the locate and zoom buttons, the health row and «Надёжность записи», «Автозапись», Back.
- Места:
  - The list: scroll to the end, sorting, search with the keyboard and an empty result, Back in search, map mode.
  - «Дом»: scrolled, all passes, a pass opening its trip, rename (cancelled), the delete confirmation (cancelled).
  - A place without passes.
- Я:
  - The top: hero, the «Добавить обложку» row, the «Войти» card and its prompt, the disclosure with achievements, garage and clubs, the clubs teaser, achievements and a badge card.
  - Garage: the empty garage; the add-car form with the catalog (search, generation, Back), the type, currency, engine and «Приборка» pickers, and the odometer field with the keyboard; the seeded car on the hero, its passport, «…», the edit form, the Гараж list.
  - Уровни.
  - Черновики: swipe, selection, a draft.
  - История: calendar, a day, the previous month, search, list and grid, month headers, repeated trips.
  - Own trips: the whole page, «…», the edit sheet with the keyboard, moments, the checkpoint sheet, «Отрезок до…», the segment, the place chip, the photo viewer, «Объединить в путешествие».
  - The journey page with «…» and its edit sheet.
  - Мой профиль: the avatar grid; the name, username and «О себе» editors with the keyboard; country, cover, background; Back on each.
  - Настройки: Приватность, Язык, Тема, Единицы и формат with both pickers, Журнал.

Dark theme: Настройки, the main screen of each tab, Запись, «Вид карты», history cards, a trip with «…», the delete dialog (cancelled) and the full-screen map, and the theme picker. Then I set the theme back to «Системная».

## Not checked

- Anything that needs an account, because signing in to production was ruled out. That covers posting comments, reactions, follows, publishing, companions, the notifications inbox (the bell shows only when signed in), Account & Sync, the blocked list, «Как видят другие» and admin. I only opened the sign-in prompts.
- Recording a trip (indoors, location denied), so I did not see the live HUD, slide to start or the finish summary.
- Anything that opens the owner's photos or another app:
  - «Добавить обложку», «Добавить фото» and car photos;
  - the stereo picker;
  - «Написать автору», Telegram, and the policy and terms links;
  - «Поделиться файлом» and «Сохранить» in the poster studio;
  - GPX and CSV export.
- Settings that are hard to undo or talk to the server: switching the language or to miles, the «Помогать улучшать TripTrack» switch, setting a home.
- Dark-theme sub-screens beyond the list above.
- Gesture navigation, so M2 and m1 were seen only with the three-button bar.
- Replay on the full-screen map.

At the end: theme «Системная», map style «Туман», places sorted «Недавно», history in list mode without filters, drafts out of selection, app on Лента. The demo data and the demo «Polo» stay in TripTrack Dev. Nothing was saved, published or sent, and com.onezee.triptrack was not opened.